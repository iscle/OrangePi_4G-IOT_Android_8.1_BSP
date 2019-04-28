/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "AudioPolicyManagerCustomImpl"
//#define LOG_NDEBUG 0

//#define VERY_VERBOSE_LOGGING
#if defined(MTK_AUDIO_DEBUG)
#if defined(CONFIG_MT_ENG_BUILD)
#define LOG_NDEBUG 0
#define VERY_VERBOSE_LOGGING
#endif
#endif
#ifdef VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
#else
#define ALOGVV(a...) do { } while(0)
#endif

#define AUDIO_POLICY_XML_CONFIG_FILE_PATH_MAX_LENGTH 128
#define AUDIO_POLICY_XML_CONFIG_FILE_NAME "audio_policy_configuration.xml"

#include <inttypes.h>
#include <math.h>

#include <AudioPolicyManagerInterface.h>
//#include <AudioPolicyEngineInstance.h>
#include <cutils/atomic.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <media/AudioParameter.h>
#include <media/AudioPolicyHelper.h>
#include <soundtrigger/SoundTrigger.h>
#include <system/audio.h>
#include <audio_policy_conf.h>
#ifndef USE_XML_AUDIO_POLICY_CONF
#include <ConfigParsingUtils.h>
#include <StreamDescriptor.h>
#endif
#include <Serializer.h>
#include "TypeConverter.h"
#include <policy.h>
#include "AudioPolicyManagerCustomImpl.h"
#include "AudioPolicyServiceCustomImpl.h"
#if defined(MTK_AUDIO)
#include <media/mediarecorder.h>
#include "AudioDef.h"
#include "audio_custom_exp.h"
#include "AudioPolicyParameters.h"
#endif
#include <media/MtkLogger.h>
#define MTK_LOG_LEVEL_SILENCE 4 // only enable if necessary

#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
// total 63.5 dB
static const float KeydBPerStep = 0.25f;
static const float KeyvolumeStep = 255.0f;
// shouldn't need to touch these
static const float KeydBConvert = -KeydBPerStep * 2.302585093f / 20.0f;
static const float KeydBConvertInverse = 1.0f / KeydBPerStep;
#endif

namespace android {
AudioPolicyManagerCustomImpl::AudioPolicyManagerCustomImpl()
{
    mAudioPolicyManager = NULL;
    mVolumeStream = AUDIO_STREAM_DEFAULT;
    mVolumeIndex = -1;
    mVolumeDevice = AUDIO_DEVICE_NONE;
    mDeviceOfCheckAndSetVolume = AUDIO_DEVICE_NONE;
    memset(&mGainTable, 0, sizeof(mGainTable));
    mGainTableSceneIndex = 0;
    mGainTableSceneCount = 1;
    mTty_Ctm = AUD_TTY_OFF;
    mFMDirectAudioPatchEnable = false;
    mSkipFMVolControl = false;
    mUsbHeadsetConnect = false;
    mNeedRemapVoiceVolumeIndex = false;
    memset(&mAudioCustVolumeTable, 0, sizeof(mAudioCustVolumeTable));
    InitializeMTKLogLevel("af.policy.debug");
}


AudioPolicyManagerCustomImpl::~AudioPolicyManagerCustomImpl()
{
    ALOGD("%s()", __FUNCTION__);
    freeGainTable();
}

status_t AudioPolicyManagerCustomImpl::common_set(AudioPolicyManager *audioPolicyManger)
{
    mAudioPolicyManager = audioPolicyManger;
    ALOGD("Set mAudioPolicyManager with %p", audioPolicyManger);
    return NO_ERROR;
}
audio_stream_type_t AudioPolicyManagerCustomImpl::gainTable_getVolumeStream()
{
    return mVolumeStream;
}
int AudioPolicyManagerCustomImpl::gainTable_getVolumeIndex()
{
    return mVolumeIndex;
}
audio_devices_t AudioPolicyManagerCustomImpl::gainTable_getVolumeDevice()
{
    return mVolumeDevice;
}
status_t AudioPolicyManagerCustomImpl::gainTable_setVolumeStream(audio_stream_type_t stream)
{
    mVolumeStream = stream;
    return NO_ERROR;
}
status_t AudioPolicyManagerCustomImpl::gainTable_setVolumeIndex(int index)
{
    mVolumeIndex = index;
    return NO_ERROR;
}
status_t AudioPolicyManagerCustomImpl::gainTable_setVolumeDevice(audio_devices_t device)
{
    mVolumeDevice = device;
    return NO_ERROR;
}
status_t AudioPolicyManagerCustomImpl::gainTable_getCustomAudioVolume(void)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return NO_INIT;
    }

    if (allocateGainTable()) {
        ALOGE("error, load GainTable failed!!");
        mAudioPolicyManager->mAudioPolicyVendorControl.setCustomVolumeStatus(false);
    } else {
        mAudioPolicyManager->mAudioPolicyVendorControl.setCustomVolumeStatus(true);
    }
    return NO_ERROR;
#elif defined(MTK_AUDIO_GAIN_NVRAM)
    mAudioCustVolumeTable.bRev = CUSTOM_VOLUME_REV_1;
    mAudioCustVolumeTable.bReady = 0;
    mAudioPolicyManager->mpClientInterface->getCustomAudioVolume(&mAudioCustVolumeTable);
    if (mAudioCustVolumeTable.bReady != 0) {
        ALOGD("mUseCustomVolume true");
        mAudioPolicyManager->mAudioPolicyVendorControl.setCustomVolumeStatus(true);
    } else {
        ALOGD("mUseCustomVolume false");
       mAudioPolicyManager-> mAudioPolicyVendorControl.setCustomVolumeStatus(false);
    }
    return NO_ERROR;
#else
    return INVALID_OPERATION;
#endif
}
float AudioPolicyManagerCustomImpl::gainTable_getVolumeDbFromComputeVolume(audio_stream_type_t stream, int index, audio_devices_t device, float volumeDB)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return 0.0;
    }

    if (mAudioPolicyManager->mAudioPolicyVendorControl.getCustomVolumeStatus()) {
#if defined(MTK_AUDIO_GAIN_TABLE)
        volumeDB = Volume::AmplToDb(computeGainTableCustomVolume(stream, index, device));
#else
        volumeDB = Volume::AmplToDb(computeCustomVolume(stream, index, device));
#endif
    } else {
        ALOGW("%s,not Customer Volume, Using Android Volume Curve", __FUNCTION__);
        volumeDB = mAudioPolicyManager->mVolumeCurves->volIndexToDb(stream, Volume::getDeviceCategory(device), index);
    }
#else
    (void) stream;
    (void) index;
    (void) device;
#endif
    return volumeDB;
}
audio_devices_t AudioPolicyManagerCustomImpl::gainTable_getDeviceFromComputeVolume(audio_stream_type_t stream, int index, audio_devices_t device)
{
    (void) index;
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return device;
    }
    audio_devices_t Streamdevices = mAudioPolicyManager->getDeviceForStrategy(mAudioPolicyManager->getStrategy(stream), true /*fromCache*/);
    if ((device & Streamdevices) && (mAudioPolicyManager->isStreamActive(AUDIO_STREAM_MUSIC, SONIFICATION_HEADSET_MUSIC_DELAY) ||
            mAudioPolicyManager->mLimitRingtoneVolume)) {
        device = Streamdevices;
    }
#else
    (void) stream;
#endif
    return device;
}

float AudioPolicyManagerCustomImpl::gainTable_getCorrectVolumeDbFromComputeVolume(audio_stream_type_t stream, float volumeDB, audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return volumeDB;
    }
    const routing_strategy stream_strategy = mAudioPolicyManager->getStrategy(stream);
    if ((device & AUDIO_DEVICE_OUT_AUX_DIGITAL) &&
        ((stream_strategy == STRATEGY_SONIFICATION) || (stream_strategy == STRATEGY_SONIFICATION_RESPECTFUL))) {
        ALOGD("AUDIO_DEVICE_OUT_AUX_DIGITAL device = 0x%x stream_strategy = %d", device, stream_strategy);
        if (mAudioPolicyManager->isStreamActive(AUDIO_STREAM_MUSIC, SONIFICATION_HEADSET_MUSIC_DELAY)) {
            if (volumeDB < SONIFICATION_AUX_DIGITAL_VOLUME_FACTOR_DB) {
                while(volumeDB < SONIFICATION_AUX_DIGITAL_VOLUME_FACTOR_DB)
                    volumeDB = volumeDB + (SONIFICATION_AUX_DIGITAL_VOLUME_FACTOR_DB*(-1));
            }
        }
    }
#else
    (void) stream;
    (void) device;
#endif
    return volumeDB;
}

audio_devices_t AudioPolicyManagerCustomImpl::gainTable_checkInvalidDeviceFromCheckAndSetVolume(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return device;
    }
    mDeviceOfCheckAndSetVolume = device;
    if ((device != AUDIO_DEVICE_NONE) && !outputDesc->isDuplicated()) {
        if ((device & outputDesc->supportedDevices()) == 0) {
            ALOGE("%s invalid set device [0x%x] volume to mId[%d](device support 0x%x), use [0x%x]",
                __FUNCTION__, device, outputDesc->getId(), outputDesc->supportedDevices(), outputDesc->device());
            device = AUDIO_DEVICE_NONE;
        }
    }
#else
    (void) outputDesc;
#endif
    return device;
}

status_t AudioPolicyManagerCustomImpl::gainTable_applyAnalogGainFromCheckAndSetVolume(audio_stream_type_t stream, int index,
                                                                                                   const sp<AudioOutputDescriptor>& outputDesc,
                                                                                                   audio_devices_t device,
                                                                                                   int delayMs, bool force)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    (void) device;
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return NO_INIT;
    } else if (mAudioPolicyManager->mPrimaryOutput == NULL) {
        ALOGVV("mPrimaryOutput is not ready");
        return NO_INIT;
    }
    audio_devices_t originDevice = mDeviceOfCheckAndSetVolume;
    if (outputDesc->isDuplicated()) {
        if (!(outputDesc->subOutput2()->sharesHwModuleWith(mAudioPolicyManager->mPrimaryOutput))) {
            originDevice = mDeviceOfCheckAndSetVolume & (mAudioPolicyManager->mPrimaryOutput->supportedDevices());
        }
    }
    if (outputDesc->sharesHwModuleWith(mAudioPolicyManager->mPrimaryOutput) && originDevice != AUDIO_DEVICE_NONE) {
        checkAndSetGainTableAnalogGain(stream, index, outputDesc, outputDesc->device(), delayMs, force);
    }
    return NO_ERROR;
#elif defined(MTK_AUDIO_GAIN_NVRAM)
    (void) force;
    if (stream == AUDIO_STREAM_VOICE_CALL ||
        stream == AUDIO_STREAM_BLUETOOTH_SCO) {
        float voiceVolume;
        // Force voice volume to max for bluetooth SCO as volume is managed by the headset
        if (stream == AUDIO_STREAM_VOICE_CALL) {
            if ((mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_COMMUNICATION)) {
                return NO_ERROR;
            }
            if (mAudioPolicyManager->mAudioPolicyVendorControl.getCustomVolumeStatus()) {
                voiceVolume = computeCustomVolume(stream, index, device);
            } else {
                voiceVolume = (float)index / (float)mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(stream);
            }
        } else {
            voiceVolume = 1.0;
            // it should mute BT if changing to BT from HP/SPK. But hal desn't support 0
            // keep the same value, at least original won't burst before changing device (ALPS02474519)
            // if(outputDesc->mMuteTid[stream] == gettid()) {
                // voiceVolume = mLastVoiceVolume;
            // }
        }

        if (voiceVolume != mAudioPolicyManager->mLastVoiceVolume && outputDesc == mAudioPolicyManager->mPrimaryOutput) {
            mAudioPolicyManager->mpClientInterface->setVoiceVolume(voiceVolume, delayMs);
            mAudioPolicyManager->mLastVoiceVolume = voiceVolume;
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) index;
    (void) outputDesc;
    (void) device;
    (void) delayMs;
    (void) force;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::gainTable_setVolumeFromCheckAndSetVolume(audio_stream_type_t stream, int index,
                                           const sp<AudioOutputDescriptor>& outputDesc,
                                           audio_devices_t device,
                                           int delayMs, bool force, float volumeDb)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return NO_INIT;
    }
    // for VT notify tone when incoming call. it's volume will be adusted in hardware.
    if ((stream == AUDIO_STREAM_BLUETOOTH_SCO) && outputDesc->mRefCount[stream] != 0 && mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_CALL && index != 0) {
        volumeDb = 0.0f;
    }
    if (outputDesc->setVolume(volumeDb, stream, device, delayMs, force)) {
        if (outputDesc == mAudioPolicyManager->mPrimaryOutput) {
            MTK_ALOGS(MT_AUDIO_ENG_BUILD_LEVEL, "checkAndSetVolume stream = %d index = %d mId = %d device = 0x%x(0x%x) delayMs = %d force = %d [%d/0x%x/%d]"
            , stream, index, outputDesc->getId(), device, mDeviceOfCheckAndSetVolume, delayMs, force, mVolumeStream, mVolumeDevice, mVolumeIndex);
        } else {
            ALOGV("checkAndSetVolume stream = %d index = %d mId = %d device = 0x%x(0x%x) delayMs = %d force = %d [%d/0x%x/%d]"
            , stream, index, outputDesc->getId(), device, mDeviceOfCheckAndSetVolume, delayMs, force, mVolumeStream, mVolumeDevice, mVolumeIndex);
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) index;
    (void) outputDesc;
    (void) device;
    (void) delayMs;
    (void) force;
    (void) volumeDb;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::gainTable_routeAndApplyVolumeFromStopSource(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device,
                                           audio_stream_type_t stream, bool force)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    (void) stream;
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return NO_INIT;
    }
    audio_devices_t prevDevice = outputDesc->mDevice;
    mAudioPolicyManager->setOutputDevice(outputDesc, device, force, outputDesc->latency()*2);
    //gain table need apply analog volume after stopOutput
    if (prevDevice == device) {
        mAudioPolicyManager->applyStreamVolumes(outputDesc, device, outputDesc->latency()*2);
    }
    return NO_ERROR;
#else
    (void) outputDesc;
    (void) device;
    (void) stream;
    (void) force;
    return INVALID_OPERATION;
#endif
}

bool AudioPolicyManagerCustomImpl::gainTable_skipAdjustGainFromSetStreamVolumeIndex(audio_devices_t curDevice, audio_devices_t wantDevice)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    if ((curDevice & wantDevice) == 0) {
        return true;
    }
#else
    (void) curDevice;
    (void) wantDevice;
#endif
    return false;
}

audio_devices_t AudioPolicyManagerCustomImpl::gainTable_replaceApplyDeviceFromSetStreamVolumeIndex(audio_devices_t outputDevice, audio_devices_t curDevice)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    (void) curDevice;
    return outputDevice;
#else
    (void) outputDevice;
    return curDevice;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_initOutputIdForApp(void)
{
#if defined(MTK_FM_SUPPORT)
    // We set primary output or deepbuffer output as fmradio ref output id
    // Current AOSP trends to playback music with deep buffer output
    bool propertySupportDeepBuffer = property_get_bool("audio.deep_buffer.media", false /* default_value */);
    bool configSupportDeepBuffer = false;
    audio_port_handle_t handleOfDeepBuffer = 0;
    audio_port_handle_t handleOfPrimary = 0;
    audio_port_handle_t handleOfFast = 0;
    sp<SwAudioOutputDescriptor> outputForPrimary = 0;
    sp<SwAudioOutputDescriptor> outputForFast = 0;
    const size_t SIZE = 32;
    char buffer[SIZE];
    mFMOutput = mAudioPolicyManager->mPrimaryOutput;
    ALOGV("propertySupportDeepBuffer %d", propertySupportDeepBuffer);

    if (propertySupportDeepBuffer == false) {
        for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
            sp<SwAudioOutputDescriptor> desc = mAudioPolicyManager->mOutputs.valueAt(i);
            if ((desc->mFlags & AUDIO_OUTPUT_FLAG_PRIMARY) != 0) {
                break;
            }
            if ((desc->mFlags & AUDIO_OUTPUT_FLAG_DEEP_BUFFER) != 0) {
                propertySupportDeepBuffer = true;
                break;
            }
        }
    }

    for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
        sp<SwAudioOutputDescriptor> desc = mAudioPolicyManager->mOutputs.valueAt(i);
        if ((desc->mFlags & AUDIO_OUTPUT_FLAG_PRIMARY) != 0) {
            struct audio_patch patch;
            desc->toAudioPortConfig(&patch.sources[0]);
            handleOfPrimary = patch.sources[0].id;
            outputForPrimary = desc;
            ALOGD("check outputID [%d] Primary", handleOfPrimary);
        }
        if (propertySupportDeepBuffer) {
            if ((desc->mFlags & AUDIO_OUTPUT_FLAG_DEEP_BUFFER) != 0) {
                struct audio_patch patch;
                configSupportDeepBuffer = true;
                desc->toAudioPortConfig(&patch.sources[0]);
                handleOfDeepBuffer = patch.sources[0].id;
                mFMOutput = desc;
                snprintf(buffer, SIZE, "%d", handleOfDeepBuffer);
                ALOGD("select outputID [%d] DeepBuffer as FMRadio reference", handleOfDeepBuffer);
                break;
            }
        }
        if ((desc->mFlags & AUDIO_OUTPUT_FLAG_FAST) != 0) {
            struct audio_patch patch;
            desc->toAudioPortConfig(&patch.sources[0]);
            handleOfFast = patch.sources[0].id;
            outputForFast = desc;
            ALOGD("check outputID [%d] Fast", handleOfFast);
        }
    }
    if (configSupportDeepBuffer == false) {
        snprintf(buffer, SIZE, "%d", handleOfPrimary);
        mFMOutput = outputForPrimary;
        ALOGD("select ID [%d] Primary as FMRadio reference", handleOfPrimary);
    }
    ALOGD("af.music.outputid = %s", buffer);
    property_set("af.music.outputid", buffer);
    return NO_ERROR;
#else
    return INVALID_OPERATION;
#endif
}

audio_devices_t AudioPolicyManagerCustomImpl::fm_correctDeviceFromSetDeviceConnectionStateInt(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device, bool force)
{
#if defined(MTK_FM_SUPPORT)
    // For FM Radio to detect BT/USB Connected
    if (outputDesc == mFMOutput && force) {
        device = device & outputDesc->supportedDevices();
    }
#else
    (void) outputDesc;
    (void) force;
#endif
    return device;
}

status_t AudioPolicyManagerCustomImpl::common_setPolicyManagerCustomParameters(int par1, int par2, int par3, int par4)
{
#if defined(MTK_AUDIO)
    if (mAudioPolicyManager == NULL) {
        ALOGE("FatalErr on %s, mAudioPolicyManager unint ", __FUNCTION__);
        return NO_INIT;
    }
    audio_devices_t primaryOutDevices = mAudioPolicyManager->mPrimaryOutput->device();
    audio_devices_t curDevice =Volume::getDeviceForVolume(mAudioPolicyManager->mPrimaryOutput->device());
    MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "setPolicyManagerCustomParameters par1 = %d par2 = %d par3 = %d par4 = %d curDevice = 0x%x", par1, par2, par3, par4, curDevice);
    status_t volStatus;
    switch(par1) {
        case POLICY_SET_NUM_HS_POLE: {
            mAudioPolicyManager->mAudioPolicyVendorControl.setNumOfHeadsetPole(par2);
            break;
        }
        case POLICY_SET_FM_PRESTOP: {
#if defined(MTK_FM_SUPPORT)
            for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
                sp<AudioOutputDescriptor> desc = mAudioPolicyManager->mOutputs.valueAt(i);
                if (desc->sharesHwModuleWith(mFMOutput) && !desc->isDuplicated()) {
                    if (par2) {
                        ALOGD("mute for FM app with Handle %d", mAudioPolicyManager->mOutputs.keyAt(i));
                        mAudioPolicyManager->setStreamMute(AUDIO_STREAM_MUSIC, true, desc);
                    } else {
                        ALOGD("unmute for FM app with Handle %d", mAudioPolicyManager->mOutputs.keyAt(i));
                        mAudioPolicyManager->setStreamMute(AUDIO_STREAM_MUSIC, false, desc);
                    }
                }
            }
#endif
            break;
        }
        case POLICY_SET_SCENE_GAIN:
        case POLICY_LOAD_VOLUME: {
#if defined(MTK_AUDIO_GAIN_TABLE)
            ALOGD("%s(), reload GainTable param", __FUNCTION__);
            if (par1 == POLICY_LOAD_VOLUME) {
                if (allocateGainTable()) {
                ALOGE("error, load GainTable failed!!");
            }
            } else {
                updateCurrentSceneIndexOfGainTable();
            }
#elif defined(MTK_AUDIO_GAIN_NVRAM)
            if (par1 == POLICY_SET_SCENE_GAIN) {
                ALOGW("Don't support POLICY_SET_SCENE_GAIN on MTK_AUDIO_GAIN_NVRAM");
                break;
            }
            loadCustomVolume();
#endif
            for(int i = 0; i < AUDIO_STREAM_CNT; i++) {
                if (i == AUDIO_STREAM_PATCH) {
                    continue;
                }
                volStatus = mAudioPolicyManager->checkAndSetVolume((audio_stream_type_t)i, mAudioPolicyManager->mVolumeCurves->getVolumeIndex((audio_stream_type_t) i, primaryOutDevices), mAudioPolicyManager->mPrimaryOutput, primaryOutDevices, 50, true);
            }
            break;
        }
        case POLICY_SET_TTY_MODE: {
#if defined(MTK_TTY_SUPPORT)
            ALOGD("SetTtyMode = %d", par2);
            mTty_Ctm = (tty_mode_t) par2;
#endif
            break;
        }
#if defined(MTK_HIFIAUDIO_SUPPORT)
        case POLICY_SET_AUDIO_RATE:{
            /* do mute for hifi setting */
            sp<SwAudioOutputDescriptor> outputDescmute = mAudioPolicyManager->mPrimaryOutput;
            for (size_t strategy = 0; strategy < NUM_STRATEGIES ; strategy++ ) {
                if(mAudioPolicyManager->isStrategyActive(outputDescmute, (routing_strategy)strategy)){
                    audio_devices_t newDevice = mAudioPolicyManager->getDeviceForStrategy((routing_strategy)strategy, false );
                    mAudioPolicyManager->setStrategyMute((routing_strategy)strategy, true, outputDescmute, 0 ,newDevice);
                    mAudioPolicyManager->setStrategyMute((routing_strategy)strategy, false, outputDescmute, outputDescmute->latency()*4,newDevice);
                }
            }

            AudioParameter param = AudioParameter();
            param.addInt(String8(AudioParameter::keySamplingRate), (int)par2);

            /* if par2(sample_rate) > 48K, set FM as indirect mode, else, set direct mode */
            setFMIndirectMode(par2);

            for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
                sp<SwAudioOutputDescriptor> outputDesc = mAudioPolicyManager->mOutputs.valueAt(i);
                if (!outputDesc->isDuplicated() &&  outputDesc->sharesHwModuleWith(mAudioPolicyManager->mPrimaryOutput) &&
                    !(outputDesc->mFlags & AUDIO_OUTPUT_FLAG_FAST)){
                    ALOGD("POLICY_SET_AUDIO_RATE output = %zd rate = %d", i,par2);
                    mAudioPolicyManager->mpClientInterface->setParameters(mAudioPolicyManager->mOutputs.keyAt(i), param.toString());
                }
            }

            /*
             * tell fm apk to restart
            */
            mAudioPolicyManager->nextAudioPortGeneration();
            mAudioPolicyManager->mpClientInterface->onAudioPatchListUpdate();
            break;
        }
#endif
        default:
            break;
    }
    return NO_ERROR;
#else
    ALOGD("setPolicyManagerParameters (invalid) par1 = %d par2 = %d par3 = %d par4 = %d", par1, par2, par3, par4);
    return INVALID_OPERATION;
#endif
}


float AudioPolicyManagerCustomImpl::computeGainTableCustomVolume(audio_stream_type_t stream, int index, audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    float volume = 1.0;
    device_category deviceCategory = Volume::getDeviceCategory(device);

    if (stream > GAIN_MAX_STREAM_TYPE) {
        // It will pass AUDIO_STREAM_REROUTING into this function, however we don't need this type volume
        return 1.0;
    }

    if (mAudioPolicyManager->mAudioPolicyVendorControl.getVoiceReplaceDTMFStatus() && stream == AUDIO_STREAM_DTMF) {
        // normalize new index from 0~15(audio) to 0~7(voice)
        // int tempindex = index;
        float DTMFvolInt = (fCUSTOM_VOLUME_MAPPING_STEP * (index - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_DTMF))) / (mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(AUDIO_STREAM_DTMF) - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_DTMF));
        index = (DTMFvolInt * (mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(AUDIO_STREAM_VOICE_CALL) - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_VOICE_CALL))/ (fCUSTOM_VOLUME_MAPPING_STEP)) + mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_VOICE_CALL);
        //MTK_ALOGVV("volumecheck refine DTMF index [%d] to Voice index [%d]", tempindex, index);
        stream = AUDIO_STREAM_VOICE_CALL;
    }

#if defined(MTK_TTY_SUPPORT)
    if(mAudioPolicyManager->isInCall() == true && mTty_Ctm != AUD_TTY_OFF) {
        deviceCategory = Volume::getDeviceCategory(getNewDeviceForTty(device, mTty_Ctm));
        stream = AUDIO_STREAM_VOICE_CALL;
    }
#endif
    GAIN_DEVICE gainDevice;
    if (deviceCategory == DEVICE_CATEGORY_SPEAKER) {
        gainDevice = GAIN_DEVICE_SPEAKER;
        if ((device & AUDIO_DEVICE_OUT_WIRED_HEADSET)||
             (device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE)) {
            gainDevice = GAIN_DEVICE_HSSPK;
        } else if (device & AUDIO_DEVICE_OUT_BUS) {
            gainDevice = GAIN_DEVICE_USB;
        }
    } else if (deviceCategory == DEVICE_CATEGORY_HEADSET) {
        if (device & AUDIO_DEVICE_OUT_WIRED_HEADSET) {
            if (mAudioPolicyManager->mAudioPolicyVendorControl.getNumOfHeadsetPole() == 5)
                gainDevice = GAIN_DEVICE_HEADSET_5POLE;
            else
                gainDevice = GAIN_DEVICE_HEADSET;
        } else if (device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE) {
            gainDevice = GAIN_DEVICE_HEADPHONE;
        } else if (device & AUDIO_DEVICE_OUT_USB_HEADSET) {
            gainDevice = GAIN_DEVICE_USB;
        } else {
            gainDevice = GAIN_DEVICE_HEADSET;
        }
    } else if (deviceCategory == DEVICE_CATEGORY_EARPIECE) {
        gainDevice = GAIN_DEVICE_EARPIECE ;
    } else if (deviceCategory == DEVICE_CATEGORY_EXT_MEDIA) {
        if (device & AUDIO_DEVICE_OUT_USB_DEVICE)
            gainDevice = GAIN_DEVICE_USB;
        else
            gainDevice = GAIN_DEVICE_SPEAKER;
    } else {
        gainDevice = GAIN_DEVICE_SPEAKER;
    }

    ALOG_ASSERT(index >= 0 && index < GAIN_VOL_INDEX_SIZE, "invalid index");
    uint8_t customGain;
    if (mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_CALL && stream == AUDIO_STREAM_VOICE_CALL) {
        customGain = KeyvolumeStep - mGainTable.nonSceneGain.ringbackToneGain[gainDevice][index].digital;
    } else {
        customGain = KeyvolumeStep - mGainTable.sceneGain[mGainTableSceneIndex].streamGain[stream][gainDevice][index].digital;
    }
    volume = linearToLog(customGain);
    ALOGV("-computeGainTableCustomVolume customGain 0x%x, volume %f stream %d, index %d, device 0x%x [%d/0x%x/%d]", customGain, volume, stream, index, device, mVolumeStream, mVolumeDevice, mVolumeIndex);
    return volume;
#else
    ALOGW("%s unsupport, stream %d, index %d, device %d", __FUNCTION__, stream, index, device);
    return 0.0;
#endif
}

audio_stream_type_t AudioPolicyManagerCustomImpl::selectGainTableActiveStream(audio_stream_type_t requestStream)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    audio_stream_type_t activeStream = requestStream;

    if (mAudioPolicyManager->isInCall()) {
        if (requestStream == AUDIO_STREAM_BLUETOOTH_SCO)
            activeStream = AUDIO_STREAM_BLUETOOTH_SCO;
        else if (requestStream == AUDIO_STREAM_VOICE_CALL)
            activeStream = AUDIO_STREAM_VOICE_CALL;
        else
            activeStream = AUDIO_STREAM_DEFAULT;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_BLUETOOTH_SCO)) {
        activeStream = AUDIO_STREAM_BLUETOOTH_SCO;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_VOICE_CALL)) {
        activeStream = AUDIO_STREAM_VOICE_CALL;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_RING)) {
        activeStream = AUDIO_STREAM_RING;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_ALARM)) {
        activeStream = AUDIO_STREAM_ALARM;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_ACCESSIBILITY)) {
        activeStream = AUDIO_STREAM_ACCESSIBILITY;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_NOTIFICATION)) {
        activeStream = AUDIO_STREAM_NOTIFICATION;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_ENFORCED_AUDIBLE)) {
        activeStream = AUDIO_STREAM_ENFORCED_AUDIBLE;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_MUSIC)) {
      activeStream = AUDIO_STREAM_MUSIC;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_TTS)) {
        activeStream = AUDIO_STREAM_TTS;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_SYSTEM)) {
        activeStream = AUDIO_STREAM_SYSTEM;
    }
    else if (mAudioPolicyManager->mOutputs.isStreamActive(AUDIO_STREAM_DTMF)) {
        activeStream = AUDIO_STREAM_DTMF;
    }
    else {
        activeStream = AUDIO_STREAM_DEFAULT;
    }
    return activeStream;
#else
    ALOGW("%s unsupport, requestStream %d", __FUNCTION__, requestStream);
    return AUDIO_STREAM_DEFAULT;
#endif
}

status_t AudioPolicyManagerCustomImpl::checkAndSetGainTableAnalogGain(audio_stream_type_t stream, int index, const sp<AudioOutputDescriptor>& outputDesc,audio_devices_t device,
                                                           int delayMs, bool force)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    audio_stream_type_t activeStream = selectGainTableActiveStream(stream);
    if (activeStream <= AUDIO_STREAM_DEFAULT) {
        return NO_ERROR;
    }
    // audio_devices_t curDevice  = getDeviceForVolume(device);

    if (!mAudioPolicyManager->isInCall() && !outputDesc->mRefCount[stream]) {
        return NO_ERROR;
    }

    if (activeStream != stream) {
        index = mAudioPolicyManager->mVolumeCurves->getVolumeIndex(activeStream, device); // mStreams.valueFor((audio_stream_type_t)activeStream).getVolumeIndex(device);
    }

    if ((activeStream == AUDIO_STREAM_VOICE_CALL || activeStream == AUDIO_STREAM_BLUETOOTH_SCO) &&
        outputDesc != mAudioPolicyManager->mPrimaryOutput) {
        // in voice, set to primary only once, skip others
        return NO_ERROR;
    }

    if (outputDesc->mMuteCount[activeStream] != 0) {    //ALPS02455793. If music stream muted, don't pass music stream volume
        ALOGVV("checkAndSetGainTableAnalogGain() active %d stream %d muted count %d",
              activeStream, stream, outputDesc->mMuteCount[activeStream]);
        return NO_ERROR;
    }

    if (mVolumeStream != activeStream || mVolumeIndex != index || mVolumeDevice != device || force ) {
        MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "computeAndSetAnalogGain stream %d(%d), device 0x%x(0x%x), index %d(%d)", activeStream, mVolumeStream, device, mVolumeDevice, index, mVolumeIndex);
        mVolumeStream = activeStream;
        mVolumeIndex  = index ;
        mVolumeDevice = device;
        AudioParameter param = AudioParameter();
        param.addInt(String8("volumeStreamType"), activeStream);
        param.addInt(String8("volumeDevice"), device);
        param.addInt(String8("volumeIndex"), index);
        mAudioPolicyManager->mpClientInterface->setParameters(mAudioPolicyManager->mPrimaryOutput->mIoHandle, param.toString(),delayMs);
    }
    return NO_ERROR;
#else
    ALOGW("%s unsupport, stream %d, index %d, device %d, mId %d delayMs %d force %d", __FUNCTION__, stream, index, device, outputDesc->getId(), delayMs, force);
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_addAudioPatch(audio_patch_handle_t handle, const sp<AudioPatch>& patch)
{
#if defined(MTK_FM_SUPPORT)
    ssize_t index = mAudioPolicyManager->mAudioPatches.indexOfKey(handle);

    if (index >= 0) {
        ALOGW("addAudioPatch() patch %d already in", handle);
        return ALREADY_EXISTS;
    }
    bool bFMeable = false;
    sp<SwAudioOutputDescriptor> outputDesc = mFMOutput;
    if (isFMDirectMode(patch)) {
        if (outputDesc != NULL) {
            ALOGV("audiopatch Music+");
            outputDesc->changeRefCount(AUDIO_STREAM_MUSIC, 1);
            bFMeable = true;
            mFMDirectAudioPatchEnable = true;
            audio_devices_t currentDevice = mAudioPolicyManager->getNewOutputDevice(outputDesc, false /*fromCache*/);
            audio_devices_t patchDevice = patch->mPatch.sinks[0].ext.device.type;
            if (patch->mPatch.num_sinks == 2) { // force flag is from ALPS03443673
                patchDevice = patchDevice | patch->mPatch.sinks[1].ext.device.type;
            }
            // It will auto correct the right routing device ALPS02988442. Alarm stop before 80002000->0x0a
            mAudioPolicyManager->setOutputDevice(outputDesc, currentDevice, (currentDevice != patchDevice));
        }
    }
#endif

    status_t status = mAudioPolicyManager->mAudioPatches.addAudioPatch(handle, patch);

#if defined(MTK_FM_SUPPORT)
    if (bFMeable && status == NO_ERROR) {
        // Change to 500 ms from 2 * Latency in order to covers FM dirty signal
        mAudioPolicyManager->applyStreamVolumes(outputDesc, mAudioPolicyManager->getNewOutputDevice(outputDesc, false /*fromCache*/), 500, true);
    }
#endif
    return status;
}

status_t AudioPolicyManagerCustomImpl::fm_removeAudioPatch(audio_patch_handle_t handle)
{
#if defined(MTK_FM_SUPPORT)
    ssize_t index = mAudioPolicyManager->mAudioPatches.indexOfKey(handle);
    if (index < 0) {
        ALOGW("removeAudioPatch() patch %d not in", handle);
        return ALREADY_EXISTS;
    }
    ALOGV("removeAudioPatch() handle %d af handle %d", handle,
                      mAudioPolicyManager->mAudioPatches.valueAt(index)->mAfPatchHandle);
    sp<SwAudioOutputDescriptor> outputDesc = mFMOutput;
    const sp<AudioPatch> patch = mAudioPolicyManager->mAudioPatches.valueAt(index);
    if (isFMDirectMode(patch)) {
        if (outputDesc != NULL) {
            if (outputDesc->mRefCount[AUDIO_STREAM_MUSIC] > 0) {
                ALOGV("audiopatch Music-");
                outputDesc->changeRefCount(AUDIO_STREAM_MUSIC, -1);
                mFMDirectAudioPatchEnable = false;
                audio_devices_t newDevice = mAudioPolicyManager->getNewOutputDevice(outputDesc, false /*fromCache*/);
                mAudioPolicyManager->setOutputDevice(outputDesc, newDevice, false, outputDesc->latency()*2);
            }
        }
    }
#endif
    return mAudioPolicyManager->mAudioPatches.removeAudioPatch(handle);
}

status_t AudioPolicyManagerCustomImpl::fm_applyGainFromCheckAndSetVolume(audio_stream_type_t stream, int index, const sp<AudioOutputDescriptor>& outputDesc,audio_devices_t device,
                                                           int delayMs, bool force)
{
#if defined(MTK_FM_SUPPORT)
    (void) force;
    if (!mSkipFMVolControl && stream == AUDIO_STREAM_MUSIC && outputDesc == mFMOutput && (device & (AUDIO_DEVICE_OUT_WIRED_HEADSET | AUDIO_DEVICE_OUT_WIRED_HEADPHONE | AUDIO_DEVICE_OUT_SPEAKER))) {
        for (ssize_t i = 0; i < (ssize_t)mAudioPolicyManager->mAudioPatches.size(); i++) {
            //MTK_ALOGV("%s size %zu/%zu", __FUNCTION__, i, mAudioPatches.size());
            sp<AudioPatch> patchDesc = mAudioPolicyManager->mAudioPatches.valueAt(i);
            if (isFMDirectMode(patchDesc)) {
                ALOGV("Do modify audiopatch volume");
                struct audio_port_config *config;
                sp<AudioPortConfig> audioPortConfig;
                sp<DeviceDescriptor> deviceDesc;
                config = &(patchDesc->mPatch.sinks[0]);
                bool bOrignalDeviceRemoved = false;
                if (config->role == AUDIO_PORT_ROLE_SINK) {
                    deviceDesc = mAudioPolicyManager->mAvailableOutputDevices.getDeviceFromId(config->id);
                } else {
                    ALOGV("1st deviceDesc NULL");
                    break;
                }
                if (deviceDesc == NULL) {
                    bOrignalDeviceRemoved = true;// Headset is removed
                    ALOGV("bOrignalDeviceRemoved Device %x replace %x", device, config->ext.device.type);
                    deviceDesc = mAudioPolicyManager->mAvailableOutputDevices.getDevice(device, String8(""));
                    if (deviceDesc == NULL) {
                        ALOGV("2nd deviceDesc NULL");
                        break;
                    }
                }
                audioPortConfig = deviceDesc;
                struct audio_port_config newConfig;
                audioPortConfig->toAudioPortConfig(&newConfig, config);
                if (bOrignalDeviceRemoved == true)
                    newConfig.ext.device.type = config->ext.device.type;
                newConfig.config_mask = AUDIO_PORT_CONFIG_GAIN | newConfig.config_mask;
                newConfig.gain.mode = AUDIO_GAIN_MODE_JOINT | newConfig.gain.mode;
#if defined(MTK_AUDIO_GAIN_NVRAM)
                newConfig.gain.values[0] = -300 * (getStreamMaxLevels(stream) - index);
#else
                newConfig.gain.values[0] = index;   // pass volume index directly
#endif
                if ((device != newConfig.ext.device.type || bOrignalDeviceRemoved) && index != 0)// For switch and pop between hp and speaker
                    newConfig.ext.device.type = device; // Device change, Don't un-mute, wait next createAudioPatch
                mAudioPolicyManager->mpClientInterface->setAudioPortConfig(&newConfig, delayMs);
            }
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) index;
    (void) outputDesc;
    (void) device;
    (void) delayMs;
    (void) force;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_muteStrategyFromCheckOutputForStrategy(routing_strategy strategy, audio_devices_t oldDevice, audio_devices_t newDevice)
{
#if defined(MTK_FM_SUPPORT)
    if (strategy == STRATEGY_MEDIA) {
        // ALPS03221274, when playback FM, route to Headset from BT. there is a missing sound of track from BT track Headset.
        // And then it will mute and the unmute when entering direct mode
        // If input device is disconnected first, FM active information will disappear, so remove FMActive
        if ((oldDevice & (~(AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE)))
            && (newDevice & ((AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE)))) {
            MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "FM mute in-direct primary first, oldDevice 0x%x -> newDevice 0x%x", oldDevice, newDevice);
            mAudioPolicyManager->setStrategyMute(strategy, true, mFMOutput);
            mAudioPolicyManager->setStrategyMute(strategy, false, mFMOutput, MUTE_TIME_MS, newDevice);
        }
    }
    return NO_ERROR;
#else
    (void) strategy;
    (void) oldDevice;
    (void) newDevice;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_checkSkipVolumeFromCheckOutputForStrategy(routing_strategy strategy, audio_devices_t oldDevice, audio_devices_t newDevice)
{
#if defined(MTK_FM_SUPPORT)
    bool FMcaseBetweenSPKHP = false;
    if (strategy == STRATEGY_MEDIA && isFMActive()) {
        if (((oldDevice & (~(AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE))) == 0)
            && ((newDevice & (~(AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET|AUDIO_DEVICE_OUT_WIRED_HEADPHONE))) == 0)) {
                FMcaseBetweenSPKHP = true;
            }
    }
    mSkipFMVolControl = FMcaseBetweenSPKHP;
    return NO_ERROR;
#else
    (void) strategy;
    (void) oldDevice;
    (void) newDevice;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_releaseSkipVolumeFromCheckOutputForStrategy(void)
{
#if defined(MTK_FM_SUPPORT)
    mSkipFMVolControl = false;
    return NO_ERROR;
#else
    return INVALID_OPERATION;
#endif
}

bool AudioPolicyManagerCustomImpl::fm_checkFirstMusicFromStartSource(const sp<AudioOutputDescriptor>& outputDesc, audio_stream_type_t stream)
{
#if defined(MTK_FM_SUPPORT)
    if (mFMDirectAudioPatchEnable && stream == AUDIO_STREAM_MUSIC &&
        outputDesc->mRefCount[AUDIO_STREAM_MUSIC] == 2) {
        return true;
    }
#else
    (void) outputDesc;
    (void) stream;
#endif
    return false;
}

uint32_t AudioPolicyManagerCustomImpl::fm_extendMuteFromCheckDeviceMuteStrategies(const sp<AudioOutputDescriptor>& outputDesc, routing_strategy strategy, uint32_t muteDurationMs, uint32_t extendDurationMs)
{
#if defined(MTK_FM_SUPPORT)
    if (outputDesc == mFMOutput && strategy == STRATEGY_MEDIA && isFMActive() && muteDurationMs < extendDurationMs) {
        return extendDurationMs;
    } else {
        return muteDurationMs;
    }
#else
    (void) outputDesc;
    (void) extendDurationMs;
    (void) strategy;
    return muteDurationMs;
#endif
}

status_t AudioPolicyManagerCustomImpl::fm_signalAPProutingFromSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, bool force)
{
#if defined(MTK_FM_SUPPORT)
    if (force && outputDesc == mFMOutput && isFMActive()) {
        mAudioPolicyManager->nextAudioPortGeneration();
        mAudioPolicyManager->mpClientInterface->onAudioPatchListUpdate();
        return NO_ERROR;
    } else {
        return INVALID_OPERATION;
    }
#else
    (void) outputDesc;
    (void) force;
    return INVALID_OPERATION;
#endif
}

uint32_t AudioPolicyManagerCustomImpl::fm_extendSleepFromCheckDeviceMuteStrategies(const sp<AudioOutputDescriptor>& outputDesc, uint32_t muteWaitMs)
{
#if defined(MTK_FM_SUPPORT)
    // 430 is the experimental value
    #define WAIT_HW_GAIN_MUTE_TIME (430)
    if (outputDesc == mFMOutput && isFMDirectActive()) {
        if (muteWaitMs < WAIT_HW_GAIN_MUTE_TIME) {
            usleep((WAIT_HW_GAIN_MUTE_TIME - muteWaitMs) * 1000);
            return WAIT_HW_GAIN_MUTE_TIME;
        } else {
            return muteWaitMs;
        }
    } else {
        return muteWaitMs;
    }
#else
    (void) outputDesc;
    return muteWaitMs;
#endif
}

status_t AudioPolicyManagerCustomImpl::usbPhoneCall_connectFromSetDeviceConnectionState(audio_devices_t device,
                                                                                      audio_policy_dev_state_t state,
                                                                                      const char *device_address,
                                                                                      const char *device_name)
{
#if defined(MTK_USB_PHONECALL)
    status_t status;
    audio_devices_t autodevice = AUDIO_DEVICE_NONE;
    if (device == AUDIO_DEVICE_OUT_USB_DEVICE || device == AUDIO_DEVICE_OUT_USB_HEADSET) {
        autodevice = AUDIO_DEVICE_OUT_BUS;
    } else if (device == AUDIO_DEVICE_IN_USB_DEVICE || device == AUDIO_DEVICE_IN_USB_HEADSET) {
        autodevice = AUDIO_DEVICE_IN_BUS;
    }
    status = mAudioPolicyManager->setDeviceConnectionStateInt(device, state, device_address, device_name);
    if (autodevice != AUDIO_DEVICE_NONE && status == NO_ERROR) {
        if (device == AUDIO_DEVICE_OUT_USB_HEADSET) {
            if (state == AUDIO_POLICY_DEVICE_STATE_AVAILABLE) {
                mUsbHeadsetConnect = true;
            } else if (state == AUDIO_POLICY_DEVICE_STATE_UNAVAILABLE) {
                mUsbHeadsetConnect = false;
            }
        }
        ALOGV("Auto trigger device: 0x%x connect/disconnect", autodevice);
        mAudioPolicyManager->setDeviceConnectionStateInt(autodevice, state, String8("usb_phone_call"), "primary-usb bus");
    }
    return status;
#else
    return mAudioPolicyManager->setDeviceConnectionStateInt(device, state, device_address, device_name);
#endif
}

status_t AudioPolicyManagerCustomImpl::usbPhoneCall_setOutputDeviceFromUpdateCallRouting(const sp<AudioOutputDescriptor>& outputDesc,
                                             audio_devices_t rxDevice,
                                             bool force,
                                             int delayMs,
                                             audio_devices_t *txDevice,
                                             uint32_t *muteWaitMs)
{
#if defined(MTK_USB_PHONECALL)
    (void) force;
    bool usbOutputChanged = false;
    if (rxDevice == AUDIO_DEVICE_OUT_BUS) {
        usbOutputChanged = checkUsbSuspend(rxDevice);
        *txDevice = mAudioPolicyManager->getDeviceAndMixForInputSource(AUDIO_SOURCE_VOICE_COMMUNICATION);
        if (usbOutputChanged && *txDevice == AUDIO_DEVICE_IN_BUS) {
            // closeUsbInputs is better
            mAudioPolicyManager->closeAllInputs();
        }
    }
    *muteWaitMs = mAudioPolicyManager->setOutputDevice(outputDesc, rxDevice, true, delayMs);
    if (rxDevice != AUDIO_DEVICE_OUT_BUS) {
        usbOutputChanged = checkUsbSuspend();
        *txDevice = mAudioPolicyManager->getDeviceAndMixForInputSource(AUDIO_SOURCE_VOICE_COMMUNICATION);
        if (usbOutputChanged && *txDevice != AUDIO_DEVICE_IN_BUS) {
            // closePrimaryUsbInputs is better
            mAudioPolicyManager->closeAllInputs();
        }
    }
    return NO_ERROR;
#else
    (void) outputDesc;
    (void) rxDevice;
    (void) force;
    (void) delayMs;
    (void) txDevice;
    (void) muteWaitMs;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::usbPhoneCall_setPrevModeFromSetPhoneState(audio_mode_t state)
{
#if defined(MTK_USB_PHONECALL)
    mAudioPolicyManager->mAudioPolicyVendorControl.setPrevMode(state);
    return NO_ERROR;
#else
    (void) state;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::usbPhoneCall_closeAllInputsFromSetPhoneState(void)
{
#if defined(MTK_USB_PHONECALL)
    if (checkUsbSuspend()) {
        // closePrimaryUsbInputs is better
        mAudioPolicyManager->closeAllInputs();
    }
    return NO_ERROR;
#else
    return INVALID_OPERATION;
#endif
}

audio_devices_t AudioPolicyManagerCustomImpl::usbPhoneCall_addCurrentVolumeIndexFromSetStreamVolumeIndex(audio_stream_type_t stream,
                                                  int index,
                                                  audio_devices_t device)
{
#if defined(MTK_USB_PHONECALL)
    if ((device == AUDIO_DEVICE_OUT_USB_DEVICE && !mUsbHeadsetConnect) ||
        (device == AUDIO_DEVICE_OUT_USB_HEADSET && mUsbHeadsetConnect)) {
        // USB_PRIMARY is an alias of USB_DEVICE for purposes of volume index, however it uses SPK mapping table for digital gain
        device |= AUDIO_DEVICE_OUT_BUS;
        for (int curStream = 0; curStream < AUDIO_STREAM_FOR_POLICY_CNT; curStream++) {
            if (!mAudioPolicyManager->streamsMatchForvolume(stream, (audio_stream_type_t)curStream)) {
                continue;
            }
            mAudioPolicyManager->mVolumeCurves->addCurrentVolumeIndex((audio_stream_type_t)curStream, AUDIO_DEVICE_OUT_BUS, index);
        }
    }
    return device;
#else
    (void) stream;
    (void) index;
    return device;
#endif
}

audio_devices_t AudioPolicyManagerCustomImpl::usbPhoneCall_correctDeviceFromGetDevicesForStream(audio_devices_t devices)
{
#if defined(MTK_USB_PHONECALL)
    /*Filter USB_PRIMARY out of results, as AudioService doesn't know about it
      and doesn't really need to.*/
    if (devices & AUDIO_DEVICE_OUT_BUS) {
        if (!mUsbHeadsetConnect) {
            devices |= AUDIO_DEVICE_OUT_USB_DEVICE;
        } else {
            devices |= AUDIO_DEVICE_OUT_USB_HEADSET;
        }
        devices &= ~AUDIO_DEVICE_OUT_BUS;
    }
    return devices;
#else
    return devices;
#endif
}

bool AudioPolicyManagerCustomImpl::usbPhoneCall_isSupportUSBPhoneCallDevice(const String8& address, audio_devices_t device)
{
#if defined(MTK_USB_PHONECALL)
    return (device_distinguishes_on_address(device) && address == String8("usb_phone_call"));
#else
    (void) address;
    (void) device;
    return false;
#endif
}

status_t AudioPolicyManagerCustomImpl::gainNvram_remapIndexRangeFromInitStreamVolume(audio_stream_type_t stream,
                                            int *indexMin,
                                            int *indexMax)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    if (stream == AUDIO_STREAM_VOICE_CALL) {
        if (*indexMin > 0) {
            mNeedRemapVoiceVolumeIndex = true;
            *indexMin = *indexMin - 1;
            *indexMax = *indexMax - 1;
            ALOGV("Correct stream %d, min %d, max %d", stream , *indexMin, *indexMax);
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) indexMin;
    (void) indexMax;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::gainNvram_remapIndexFromSetStreamVolumeIndex(audio_stream_type_t stream,
                                                  int *index,
                                                  audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)   //  Remapping M min index = 1 to MTK min index = 0
    (void) device;
    if (stream == AUDIO_STREAM_VOICE_CALL) {
        if (mNeedRemapVoiceVolumeIndex == true) {
            *index = *index - 1;
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) index;
    (void) device;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::gainNvram_remapIndexFromGetStreamVolumeIndex(audio_stream_type_t stream,
                                                      int *index,
                                                      audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)   //  Remapping M min index = 1 to MTK min index = 0
    (void) device;
    if (stream == AUDIO_STREAM_VOICE_CALL) {
        if (mNeedRemapVoiceVolumeIndex == true) {
            *index = *index + 1;
            ALOGV("Correct stream %d device %08x index %d", stream, device, *index);
        }
    }
    return NO_ERROR;
#else
    (void) stream;
    (void) index;
    (void) device;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::lowLatency_updatePrimaryModuleDeviceFromSetPhoneState(audio_mode_t state, audio_devices_t rxDevice)
{
#if defined(MTK_LOW_LATENCY)
    if (state == AUDIO_MODE_NORMAL) {
        updatePrimaryModuleDevice(rxDevice);
    }
    return NO_ERROR;
#else
    (void) state;
    (void) rxDevice;
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::lowLatency_CheckSpeakerProtectionDevice(const sp<IOProfile>& outProfile)
{
#if defined(MTK_LOW_LATENCY)    // Skip SPK when supports smart PA which runs algo on AP (It's a MTK HAL hard rule)
    if (!(AUDIO_OUTPUT_FLAG_PRIMARY & outProfile->getFlags())) {
        sp<DeviceDescriptor> devDesc = outProfile->getSupportedDeviceByAddress(AUDIO_DEVICE_OUT_SPEAKER, String8(""));
        if (devDesc != NULL) {
            String8 command = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetSpeakerProtection"));
            AudioParameter param = AudioParameter(command);
            int valueInt;
            if (param.getInt(String8("GetSpeakerProtection"), valueInt) == NO_ERROR &&
            valueInt == 1) {
                if (outProfile->removeSupportedDevice(devDesc) >= 0) {
                    ALOGD("Remove SPK From the output(Flag = 0x%x)", outProfile->getFlags());
                } else {
                    ALOGW("Remove SPK Fail from the output(Flag = 0x%x)", outProfile->getFlags());
                }
            } else {
                ALOGD("Not support GetSpeakerProtection");
            }
        }
    }
    return NO_ERROR;
#else
    (void) outProfile;
    return INVALID_OPERATION;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_shareModuleActiveFromIsStrategyActive(const sp<AudioOutputDescriptor>& outputDesc,
                                          routing_strategy strategy, uint32_t inPastMs,
                                          nsecs_t sysTime, bool bShareHwModule, audio_stream_type_t stream)
{
#if defined(MTK_LOW_LATENCY)
    if (bShareHwModule && !outputDesc->isDuplicated()) {
        for (size_t j = 0; j < mAudioPolicyManager->mOutputs.size(); j++) {
            sp<AudioOutputDescriptor> desc = mAudioPolicyManager->mOutputs.valueAt(j);
            if (desc != outputDesc && !desc->isDuplicated() && outputDesc->sharesHwModuleWith(desc)) {
                if (((mAudioPolicyManager->getStrategy(stream) == strategy) ||
                     (NUM_STRATEGIES == strategy)) &&
                     desc->isStreamActive(stream, inPastMs, sysTime)) {
                    return true;
                }
            }
        }
    }
    return false;
#else
    (void) outputDesc;
    (void) strategy;
    (void) inPastMs;
    (void) sysTime;
    (void) bShareHwModule;
    (void) stream;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_skipSelectedDeviceFormSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device)
{
#if defined(MTK_LOW_LATENCY)  //  For fast mixer doesn't support Speaker
    if ((device == (AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADSET) ||
        device == (AUDIO_DEVICE_OUT_SPEAKER|AUDIO_DEVICE_OUT_WIRED_HEADPHONE)) &&
            ((device & outputDesc->supportedDevices()) != device)) {
        ALOGV("The Output Support 0x%x, but not support 0x%x", outputDesc->supportedDevices(), device);
        return true;
    }
    return false;
#else
    (void) outputDesc;
    (void) device;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_skipOutputCheckFromGetOutputsForDevice(audio_devices_t device, const SwAudioOutputCollection& openOutputs, size_t i)
{
#if defined(MTK_LOW_LATENCY)
    // fast output don't support VOIP ALPS03398659/ALPS02401994
    if (AUDIO_MODE_IN_COMMUNICATION == mAudioPolicyManager->mEngine->getPhoneState()) {
        if((openOutputs.valueAt(i)->mProfile != 0) &&
           (openOutputs.valueAt(i)->mProfile->getFlags() & AUDIO_OUTPUT_FLAG_FAST) &&
           !(openOutputs.valueAt(i)->mProfile->getFlags() & AUDIO_OUTPUT_FLAG_PRIMARY)) {
           ALOGV("fast output don't support VOIP, outout flags 0x%x,phone state 0x%x, device 0x%x",
                openOutputs.valueAt(i)->mProfile->getFlags(), mAudioPolicyManager->mEngine->getPhoneState(), device);
            return true;
        }
    }
    return false;
#else
    (void) i;
    (void) device;
    (void) openOutputs;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_stopToRouteFromStopSource(audio_devices_t newDevice, const sp<AudioOutputDescriptor>& outputDesc)
{
#if defined(MTK_LOW_LATENCY)  //  ALPS02626190, should reroute by active output
    return (newDevice != outputDesc->device());
#else
    (void) newDevice;
    (void) outputDesc;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_startToRouteFromStartSource(const sp<AudioOutputDescriptor>& outputDesc, bool beFirstActive)
{
#if defined(MTK_LOW_LATENCY)    // Skip that Primary output is stopped on different device
    return (outputDesc->isActive() || beFirstActive);
#else
    (void) outputDesc;
    (void) beFirstActive;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::lowLatency_isOutputActiveFromStartSource(const sp<AudioOutputDescriptor>& outputDesc)
{
#if defined(MTK_LOW_LATENCY)
    return !outputDesc->isActive();
#else
    (void) outputDesc;
    return false;
#endif
}

status_t AudioPolicyManagerCustomImpl::besLoudness_signalDupOutputFromSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc,
                                             audio_devices_t device,
                                             int delayMs)
{
#if defined(MTK_BESLOUDNESS_SUPPORT)
    AudioParameter param;
    param.addInt(String8("AudioFlinger_routing"), (int)device);
    for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
        sp<SwAudioOutputDescriptor> outputdesc = mAudioPolicyManager->mOutputs.valueAt(i);
        if (outputDesc == outputdesc) {
            mAudioPolicyManager->mpClientInterface->setParameters(outputdesc->mIoHandle, param.toString(), delayMs);
            break;
        }
    }
    return NO_ERROR;
#else
    (void) outputDesc;
    (void) device;
    (void) delayMs;
    return INVALID_OPERATION;
#endif
}

bool AudioPolicyManagerCustomImpl::debug_skipShowLog()
{
#if defined(MTK_AUDIO_DEBUG)
    return true;
#else
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::debug_showGetOutputForAttr(audio_devices_t device, const audio_config_t *config, audio_output_flags_t flags, audio_stream_type_t stream, audio_io_handle_t output)
{
#if defined(MTK_AUDIO_DEBUG)
    MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "getOutputForAttr() device 0x%x, sample_rate %d, format %x, channel_mask %x, flags %x stream %d, output %d",
          device, config->sample_rate, config->format, config->channel_mask, flags, stream, output);
    return true;
#else
    (void) device;
    (void) config;
    (void) flags;
    (void) stream;
    (void) output;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::debug_showGetInputForAttr(AudioPolicyInterface::input_type_t inputType, audio_devices_t device, audio_io_handle_t input)
{
#if defined(MTK_AUDIO_DEBUG)
    MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "getInputForAttr() returns input type = %d device =0x%x *input = %d", inputType, device, input);
    return true;
#else
    (void) inputType;
    (void) device;
    (void) input;
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::debug_showSetOutputDevice(const sp<AudioOutputDescriptor>& outputDesc, audio_devices_t device, bool force, int delayMs)
{
#if defined(MTK_AUDIO_DEBUG)
    for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
        sp<AudioOutputDescriptor> outputdesc = mAudioPolicyManager->mOutputs.valueAt(i);
        if (outputDesc == outputdesc) {
            if (device == AUDIO_DEVICE_NONE && !force) {
                ALOGVV("setOutputDevice() mIoHandle %d mId %d device %04x(%04x)(%04x) delayMs %d force %d size %zu", mAudioPolicyManager->mOutputs.keyAt(i), outputDesc->getId(), device, outputDesc->mDevice, outputDesc->supportedDevices(), delayMs, force, mAudioPolicyManager->mOutputs.size());
            } else {
                MTK_ALOGS(MT_AUDIO_USERDEBUG_BUILD_LEVEL, "setOutputDevice() mIoHandle %d mId %d device %04x(%04x)(%04x) delayMs %d force %d size %zu", mAudioPolicyManager->mOutputs.keyAt(i), outputDesc->getId(), device, outputDesc->mDevice, outputDesc->supportedDevices(), delayMs, force, mAudioPolicyManager->mOutputs.size());
            }
            break;
        }
        if (i == mAudioPolicyManager->mOutputs.size())
            ALOGV("setOutputDevice() device %04x delayMs %d force %d outputsize %zu", device, delayMs, force, mAudioPolicyManager->mOutputs.size());
    }
    return true;
#else
    (void) outputDesc;
    (void) device;
    (void) force;
    (void) delayMs;
    return false;
#endif
}

float AudioPolicyManagerCustomImpl::linearToLog(int volume)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    return volume ? exp(float(KeyvolumeStep - volume) * KeydBConvert) : 0;
#else
    ALOGW("%s unsupport, volume %d", __FUNCTION__, volume);
    return 0.0;
#endif
}

int AudioPolicyManagerCustomImpl::logToLinear(float volume)
{
#if defined(MTK_AUDIO_GAIN_TABLE) || defined(MTK_AUDIO_GAIN_NVRAM)
    return volume ? KeyvolumeStep - int(KeydBConvertInverse * log(volume) + 0.5) : 0;
#else
    ALOGW("%s unsupport, volume %f", __FUNCTION__, volume);
    return 0;
#endif
}

audio_devices_t AudioPolicyManagerCustomImpl::getNewDeviceForTty(audio_devices_t device, tty_mode_t tty_mode)
{
#if defined(MTK_TTY_SUPPORT)
    audio_devices_t OutputDeviceForTty = AUDIO_DEVICE_NONE;

    if (device & AUDIO_DEVICE_OUT_SPEAKER) {
        if (tty_mode == AUD_TTY_VCO) {
            ALOGV("%s(), speaker, TTY_VCO", __FUNCTION__);
#if defined(ENABLE_EXT_DAC)
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
#else
            OutputDeviceForTty = AUDIO_DEVICE_OUT_WIRED_HEADSET;
#endif
        } else if (tty_mode == AUD_TTY_HCO) {
            ALOGV("%s(), speaker, TTY_HCO", __FUNCTION__);
#if defined(ENABLE_EXT_DAC)
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
#else
            OutputDeviceForTty = AUDIO_DEVICE_OUT_SPEAKER;
#endif
        } else if (tty_mode == AUD_TTY_FULL) {
            ALOGV("%s(), speaker, TTY_FULL", __FUNCTION__);
#if defined(ENABLE_EXT_DAC)
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
#else
            OutputDeviceForTty = AUDIO_DEVICE_OUT_WIRED_HEADSET;
#endif
        }
    } else if ((device == AUDIO_DEVICE_OUT_WIRED_HEADSET) ||
             (device == AUDIO_DEVICE_OUT_WIRED_HEADPHONE)) {
        if (tty_mode == AUD_TTY_VCO) {
            ALOGV("%s(), headset, TTY_VCO", __FUNCTION__);
#if defined(ENABLE_EXT_DAC)
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
#else
            OutputDeviceForTty = AUDIO_DEVICE_OUT_WIRED_HEADSET;
#endif
        } else if (tty_mode == AUD_TTY_HCO) {
            ALOGV("%s(), headset, TTY_HCO", __FUNCTION__);
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
        } else if (tty_mode == AUD_TTY_FULL) {
            ALOGV("%s(), headset, TTY_FULL", __FUNCTION__);
#if defined(ENABLE_EXT_DAC)
            OutputDeviceForTty = AUDIO_DEVICE_OUT_EARPIECE;
#else
            OutputDeviceForTty = AUDIO_DEVICE_OUT_WIRED_HEADSET;
#endif
        }
    }
    ALOGV("getNewDeviceForTty() tty_mode=%d, device=0x%x, OutputDeviceForTty=0x%x", tty_mode, device, OutputDeviceForTty);
    return OutputDeviceForTty;
#else
    ALOGW("%s unsupport device 0x%x tty_mode %d", __FUNCTION__, device, tty_mode);
    return device;
#endif
}

bool AudioPolicyManagerCustomImpl::isFMDirectMode(const sp<AudioPatch>& patch)
{
#if defined(MTK_FM_SUPPORT)
    if (patch->mPatch.sources[0].type == AUDIO_PORT_TYPE_DEVICE &&
        patch->mPatch.sinks[0].type == AUDIO_PORT_TYPE_DEVICE &&
        (patch->mPatch.sources[0].ext.device.type == AUDIO_DEVICE_IN_FM_TUNER)) {
        return true;
    } else {
        return false;
    }
#else
    (void) patch;
    ALOGW("%s unsupport", __FUNCTION__);
    return false;
#endif
}

bool AudioPolicyManagerCustomImpl::setFMIndirectMode(uint32_t sampleRate)
{
#if defined(MTK_FM_SUPPORT)
    ALOGV("setFMIndirectMode SampleRate = %d ", sampleRate);
    AudioParameter param;
    if (sampleRate > 48000) {
        param.addInt(String8("FM_DIRECT_CONTROL"), 0);
        mAudioPolicyManager->mpClientInterface->setParameters(mFMOutput->mIoHandle, param.toString(),0);
        return true;
    } else {
        param.addInt(String8("FM_DIRECT_CONTROL"), 1);
        mAudioPolicyManager->mpClientInterface->setParameters(mFMOutput->mIoHandle, param.toString(),0);
        return false;
    }
#else
    ALOGV("setFMIndirectMode SampleRate = %d ", sampleRate);
#endif
    return true;
}


bool AudioPolicyManagerCustomImpl::isFMActive(void)
{
#if defined(MTK_FM_SUPPORT)
    for (ssize_t i = 0; i < (ssize_t)mAudioPolicyManager->mAudioPatches.size(); i++) {
        ALOGVV("%s size %zu/ %zu", __FUNCTION__, i, mAudioPolicyManager->mAudioPatches.size());
        sp<AudioPatch> patchDesc = mAudioPolicyManager->mAudioPatches.valueAt(i);
        if (isFMDirectMode(patchDesc)||
            (patchDesc->mPatch.sources[0].type == AUDIO_PORT_TYPE_DEVICE
            &&patchDesc->mPatch.sources[0].ext.device.type == AUDIO_DEVICE_IN_FM_TUNER)) {
            ALOGV("FM Active");
            return true;
        }
    }
#endif
    return false;
}

bool AudioPolicyManagerCustomImpl::isFMDirectActive(void)
{
#if defined(MTK_FM_SUPPORT)
    for (ssize_t i = 0; i < (ssize_t)mAudioPolicyManager->mAudioPatches.size(); i++) {
        sp<AudioPatch> patchDesc = mAudioPolicyManager->mAudioPatches.valueAt(i);
        if (isFMDirectMode(patchDesc)) {
            ALOGV("FM Direct Active");
            return true;
        }
    }
#endif
    return false;
}


bool AudioPolicyManagerCustomImpl::checkUsbSuspend(audio_devices_t device)
{
#if defined(MTK_USB_PHONECALL)
    audio_io_handle_t usbOutput = mAudioPolicyManager->mOutputs.getUsbOutput();
    if (usbOutput == 0) {
        mAudioPolicyManager->mAudioPolicyVendorControl.setUsbSuspended(false);
        return false;
    }

    if (mAudioPolicyManager->mAudioPolicyVendorControl.getUsbSuspended()) {
        if (mAudioPolicyManager->mPrimaryOutput->device() != AUDIO_DEVICE_OUT_BUS ||
            mAudioPolicyManager->mEngine->getPhoneState() != AUDIO_MODE_IN_CALL) {
            if (mAudioPolicyManager->mAudioPolicyVendorControl.getPrevMode() ==  AUDIO_MODE_IN_CALL &&
                mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_COMMUNICATION) {
                    ALOGD("For USB Phone Call do nothing when mode change to [%d] from [%d]", AUDIO_MODE_IN_CALL, AUDIO_MODE_IN_COMMUNICATION);
                    return false;
            }
            // Restore USB HAL, after leaving Primary USB
            mAudioPolicyManager->mpClientInterface->restoreOutput(usbOutput);
            mAudioPolicyManager->mAudioPolicyVendorControl.setUsbSuspended(false);
            ALOGD("mUsbSuspended = false");
            return true;
        }
    } else {
        if ((mAudioPolicyManager->mPrimaryOutput->device() == AUDIO_DEVICE_OUT_BUS || device == AUDIO_DEVICE_OUT_BUS) &&
            mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_CALL) {
            // Suspend USB HAL, before routing to Primary USB
            mAudioPolicyManager->mpClientInterface->suspendOutput(usbOutput);
            mAudioPolicyManager->mAudioPolicyVendorControl.setUsbSuspended(true);
            ALOGD("mUsbSuspended = true");
            return true;
        }
    }
#else
    ALOGW("%s unsupport, device 0x%x", __FUNCTION__, device);
#endif
    return false;
}

int AudioPolicyManagerCustomImpl::mapVol(float &vol, float unitstep)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    int index = (vol + 0.5)/unitstep;
    vol -= (index * unitstep);
    return index;
#else
    ALOGW("%s unsupport, vol %f unitstep %f", __FUNCTION__, vol, unitstep);
    return 0;
#endif
}

int AudioPolicyManagerCustomImpl::mappingVoiceVol(float &vol, float unitstep)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)

    #define ROUNDING_NUM (1)

    if (vol < unitstep) {
        return 1;
    }
    if (vol < (unitstep * 2 + ROUNDING_NUM)) {
        vol -= unitstep;
        return 2;
    } else if (vol < (unitstep * 3 + ROUNDING_NUM)) {
        vol -= unitstep * 2;
        return 3;
    } else if (vol < (unitstep * 4 + ROUNDING_NUM)) {
        vol -= unitstep * 3;
        return 4;
    } else if (vol < (unitstep * 5 + ROUNDING_NUM)) {
        vol -= unitstep * 4;
        return 5;
    } else if (vol < (unitstep * 6 + ROUNDING_NUM)) {
        vol -= unitstep * 5;
        return 6;
    } else if (vol < (unitstep * 7 + ROUNDING_NUM)) {
        vol -= unitstep * 6;
        return 7;
    } else {
        ALOGW("vole = %f unitstep = %f", vol, unitstep);
        return 0;
    }
#else
    ALOGW("%s unsupport, vol %f unitstep %f", __FUNCTION__, vol, unitstep);
    return 0;
#endif
}


int AudioPolicyManagerCustomImpl::getStreamMaxLevels(int stream)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    return (int) mAudioCustVolumeTable.audiovolume_level[stream];
#else
    ALOGW("%s unsupport, stream %d", __FUNCTION__, stream);
    return 0;
#endif
}

// this function will map vol 0~100 , base on customvolume map to 0~255 , and do linear calculation to set mastervolume
float AudioPolicyManagerCustomImpl::mapVoltoCustomVol(unsigned char array[], int volmin, int volmax, float &vol ,int stream)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    //ALOGVV("+MapVoltoCustomVol vol = %f stream = %d volmin = %d volmax = %d", vol, stream, volmin, volmax);
    CustomVolumeType vol_stream = (CustomVolumeType) stream;
    audio_stream_type_t audio_stream = (audio_stream_type_t) stream;

    if (vol_stream == CUSTOM_VOL_TYPE_VOICE_CALL || vol_stream == CUSTOM_VOL_TYPE_SIP) {
        return mapVoiceVoltoCustomVol(array, volmin, volmax, vol, stream);
    } else if (vol_stream >= CUSTOM_NUM_OF_VOL_TYPE || vol_stream < CUSTOM_VOL_TYPE_VOICE_CALL) {
        ALOGE("%s %d Error : stream = %d", __FUNCTION__, __LINE__, stream);
        audio_stream = AUDIO_STREAM_MUSIC;
        vol_stream = CUSTOM_VOL_TYPE_MUSIC;
    }

    float volume =0.0;
    if (vol == 0) {
        volume = vol;
        return 0;
    } else {    // map volume value to custom volume
        int dMaxLevels = getStreamMaxLevels(vol_stream);
        int streamDescmIndexMax = mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(audio_stream);// streamDesc.getVolumeIndexMax();
        if (dMaxLevels <= 0) {
            ALOGE("%s %d Error : dMaxLevels = %d", __FUNCTION__, __LINE__, dMaxLevels);
            dMaxLevels = 1;
        }
        if (streamDescmIndexMax <= 0) {
            ALOGE("%s %d Error : streamDescmIndexMax = %d", __FUNCTION__, __LINE__, streamDescmIndexMax);
            streamDescmIndexMax = 1;
        }

        float unitstep = fCUSTOM_VOLUME_MAPPING_STEP / dMaxLevels;
        if (vol < (fCUSTOM_VOLUME_MAPPING_STEP / streamDescmIndexMax)) {
            volume = array[0];
            vol = volume;
            return volume;
        }
        int Index = mapVol(vol, unitstep);
        float Remind = (1.0 - (vol / unitstep));
        if (Index != 0) {
            volume = ((array[Index] - (array[Index] - array[Index-1]) * Remind) + 0.5);
        } else {
            volume = 0;
        }
        //ALOGVV("%s vol [%f] unitstep [%f] Index [%d] Remind [%f] volume [%f]", __FUNCTION__, vol, unitstep, Index, Remind, volume);
    }
    // -----clamp for volume
    if (volume > 253.0) {
        volume = fCUSTOM_VOLUME_MAPPING_STEP;
    } else if (volume <= array[0]) {
        volume = array[0];
    }
    vol = volume;
    //ALOGVV("%s volume [%f] vol [%f]", __FUNCTION__, volume, vol);
    return volume;
#else
    ALOGW("%s unsupport, array[0] %d volmin %d volmax %d vol %f stream %d", __FUNCTION__, array[0], volmin, volmax, vol, stream);
    return 0.0;
#endif
}

// this function will map vol 0~100 , base on customvolume map to 0~255 , and do linear calculation to set mastervolume
float AudioPolicyManagerCustomImpl::mapVoiceVoltoCustomVol(unsigned char array[], int volmin __unused, int volmax __unused, float &vol, int vol_stream_type)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    vol = (int)vol;
    float volume = 0.0;
//  StreamDescriptor &streamDesc = mStreams.valueFor((audio_stream_type_t)AUDIO_STREAM_VOICE_CALL);//mStreams[AUDIO_STREAM_VOICE_CALL];
    if (vol == 0) {
        volume = array[0];
    } else {
        int dMaxIndex = getStreamMaxLevels(AUDIO_STREAM_VOICE_CALL)-1;
        if (dMaxIndex < 0) {
            ALOGE("%s %d Error : dMaxIndex = %d", __FUNCTION__, __LINE__, dMaxIndex);
            dMaxIndex = 1;
        }
        if (vol >= fCUSTOM_VOLUME_MAPPING_STEP) {
            volume = array[dMaxIndex];
            //ALOGVV("%s volumecheck stream = %d index = %d volume = %f", __FUNCTION__, AUDIO_STREAM_VOICE_CALL, dMaxIndex, volume);
        } else {
            double unitstep = fCUSTOM_VOLUME_MAPPING_STEP / dMaxIndex;
            int Index = mappingVoiceVol(vol, unitstep);
            // boundary for array
            if (Index >= dMaxIndex) {
                Index = dMaxIndex;
            }
            float Remind = (1.0 - (float)vol/unitstep) ;
            if (Index != 0) {
                volume = (array[Index]  - (array[Index] - array[Index- 1]) * Remind)+0.5;
            } else {
                volume =0;
            }
            //ALOGVV("%s volumecheck stream = %d index = %d volume = %f", __FUNCTION__, AUDIO_STREAM_VOICE_CALL, Index, volume);
            //ALOGVV("%s dMaxIndex [%d] vol [%f] unitstep [%f] Index [%d] Remind [%f] volume [%f]", __FUNCTION__, dMaxIndex, vol, unitstep, Index, Remind, volume);
        }
    }

     if (volume > CUSTOM_VOICE_VOLUME_MAX && vol_stream_type == CUSTOM_VOL_TYPE_VOICE_CALL) {
         volume = CUSTOM_VOICE_VOLUME_MAX;
     }
     else if (volume > 253.0) {
        volume = fCUSTOM_VOLUME_MAPPING_STEP;
     }
     else if (volume <= array[0]) {
         volume = array[0];
     }

     vol = volume;
     if (vol_stream_type == CUSTOM_VOL_TYPE_VOICE_CALL) {
         float degradeDb = (CUSTOM_VOICE_VOLUME_MAX - vol) / CUSTOM_VOICE_ONEDB_STEP;
         //ALOGVV("%s volume [%f] degradeDb [%f]", __FUNCTION__, volume, degradeDb);
         vol = fCUSTOM_VOLUME_MAPPING_STEP - (degradeDb * 4);
         volume = vol;
     }
     //ALOGVV("%s volume [%f] vol [%f]", __FUNCTION__, volume, vol);
     return volume;
#else
    ALOGW("%s unsupport, array[0] %d vol %f vol_stream_type %d", __FUNCTION__, array[0], vol, vol_stream_type);
    return 0.0;
#endif
}

float AudioPolicyManagerCustomImpl::computeCustomVolume(int stream, int index, audio_devices_t device)
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    // check if force use exist , get output device for certain mode
    device_category deviceCategory = Volume::getDeviceCategory(device);
    // compute custom volume
    float volume = 0.0;
    int volmax = 0, volmin = 0; // volumeindex = 0;
    int custom_vol_device_mode, audiovolume_steamtype;
    int dMaxStepIndex = 0;

    //ALOGVV("%s volumecheck stream = %d index = %d device = %d", __FUNCTION__, stream, index, device);

    if (mAudioPolicyManager->mAudioPolicyVendorControl.getVoiceReplaceDTMFStatus() && stream == AUDIO_STREAM_DTMF) {
        // normalize new index from 0~15(audio) to 0~6(voice)
        // int tempindex = index;
        float DTMFvolInt = (fCUSTOM_VOLUME_MAPPING_STEP * (index - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_DTMF))) /
            (mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(AUDIO_STREAM_DTMF) - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_DTMF));
        index = (DTMFvolInt * (mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax(AUDIO_STREAM_VOICE_CALL) - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_VOICE_CALL)) /
            (fCUSTOM_VOLUME_MAPPING_STEP)) + mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin(AUDIO_STREAM_VOICE_CALL);
        //MTK_ALOGVV("volumecheck refine DTMF index [%d] to Voice index [%d]", tempindex, index);
        stream = (int) AUDIO_STREAM_VOICE_CALL;
    }

#if defined(MTK_TTY_SUPPORT)
    if(mAudioPolicyManager->isInCall() == true && mTty_Ctm != AUD_TTY_OFF) {
        deviceCategory = Volume::getDeviceCategory(getNewDeviceForTty(device, mTty_Ctm));
        stream = (int) AUDIO_STREAM_VOICE_CALL;
    }
#endif

    float volInt = (fCUSTOM_VOLUME_MAPPING_STEP * (index - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin((audio_stream_type_t)stream))) / (mAudioPolicyManager->mVolumeCurves->getVolumeIndexMax((audio_stream_type_t)stream) - mAudioPolicyManager->mVolumeCurves->getVolumeIndexMin((audio_stream_type_t)stream));

    if (deviceCategory == DEVICE_CATEGORY_SPEAKER) {
        custom_vol_device_mode = CUSTOM_VOLUME_SPEAKER_MODE;
        if ((device & AUDIO_DEVICE_OUT_WIRED_HEADSET) ||
             (device & AUDIO_DEVICE_OUT_WIRED_HEADPHONE))
                custom_vol_device_mode = CUSTOM_VOLUME_HEADSET_SPEAKER_MODE;
    } else if (deviceCategory == DEVICE_CATEGORY_HEADSET) {
        custom_vol_device_mode = CUSTOM_VOLUME_HEADSET_MODE;
    } else if (deviceCategory == DEVICE_CATEGORY_EARPIECE) {
        custom_vol_device_mode = CUSTOM_VOLUME_NORMAL_MODE;
    } else {
        custom_vol_device_mode = CUSTOM_VOLUME_HEADSET_SPEAKER_MODE;
    }

    if ((stream == (int) AUDIO_STREAM_VOICE_CALL) && (mAudioPolicyManager->mEngine->getPhoneState() == AUDIO_MODE_IN_COMMUNICATION)) {
        audiovolume_steamtype = (int) CUSTOM_VOL_TYPE_SIP;
    } else if (stream >= (int) AUDIO_STREAM_VOICE_CALL && (stream < (int) AUDIO_STREAM_CNT)) {
        audiovolume_steamtype = stream;
    } else {
        audiovolume_steamtype = (int) CUSTOM_VOL_TYPE_MUSIC;
        ALOGE("%s %d Error : audiovolume_steamtype = %d", __FUNCTION__, __LINE__, audiovolume_steamtype);
    }

    dMaxStepIndex = getStreamMaxLevels(audiovolume_steamtype) - 1;

    if (dMaxStepIndex > CUSTOM_AUDIO_MAX_VOLUME_STEP - 1) {
        ALOGE("%s %d Error : dMaxStepIndex = %d", __FUNCTION__, __LINE__, dMaxStepIndex);
        dMaxStepIndex = CUSTOM_AUDIO_MAX_VOLUME_STEP - 1;
    } else if (dMaxStepIndex < 0) {
        ALOGE("%s %d Error : dMaxStepIndex = %d", __FUNCTION__, __LINE__, dMaxStepIndex);
        dMaxStepIndex = 0;
    }

    volmax = mAudioCustVolumeTable.audiovolume_steamtype[audiovolume_steamtype][custom_vol_device_mode][dMaxStepIndex];
    volmin = mAudioCustVolumeTable.audiovolume_steamtype[audiovolume_steamtype][custom_vol_device_mode][0];
    //ALOGVV("%s audiovolume_steamtype %d custom_vol_device_mode %d stream %d", __FUNCTION__, audiovolume_steamtype, custom_vol_device_mode, audiovolume_steamtype);
    //ALOGVV("%s getStreamMaxLevels(stream) %d volmax %d volmin %d volInt %f index %d", __FUNCTION__, getStreamMaxLevels(audiovolume_steamtype), volmax, volmin, volInt, index);
    volume = mapVoltoCustomVol(mAudioCustVolumeTable.audiovolume_steamtype[audiovolume_steamtype][custom_vol_device_mode], volmin, volmax, volInt, audiovolume_steamtype);

    volume = linearToLog(volume);
    //ALOGVV("stream = %d after computeCustomVolume , volInt = %f volume = %f volmin = %d volmax = %d", audiovolume_steamtype, volInt, volume, volmin, volmax);
    return volume;
#else
    ALOGW("%s unsupport, stream %d index %d device %d", __FUNCTION__, stream, index, device);
    return 0.0;
#endif
}

void AudioPolicyManagerCustomImpl::loadCustomVolume()
{
#if defined(MTK_AUDIO_GAIN_NVRAM)
    mAudioCustVolumeTable.bRev = CUSTOM_VOLUME_REV_1;
    mAudioCustVolumeTable.bReady = 0;
#if 0
    //MTK_ALOGVV("B4 Update");
    for (int i = 0; i < CUSTOM_NUM_OF_VOL_TYPE; i++) {
        //MTK_ALOGVV("StreamType %d", i);
        for (int j = 0; j < CUSTOM_NUM_OF_VOL_MODE; j++) {
            //MTK_ALOGVV("DeviceType %d", j);
            for (int k = 0; k < CUSTOM_AUDIO_MAX_VOLUME_STEP; k++) {
                //MTK_ALOGVV("[IDX]:[Value] %d, %d", k, mAudioCustVolumeTable.audiovolume_steamtype[i][j][k]);
            }
        }
    }
#endif
    mAudioPolicyManager->mpClientInterface->getCustomAudioVolume(&mAudioCustVolumeTable);
    if (mAudioCustVolumeTable.bReady != 0) {
        ALOGD("mUseCustomVolume true");
        mAudioPolicyManager->mAudioPolicyVendorControl.setCustomVolumeStatus(true);
    } else {
        ALOGD("mUseCustomVolume false");
        mAudioPolicyManager->mAudioPolicyVendorControl.setCustomVolumeStatus(false);
    }
#if 0
    //MTK_ALOGVV("After Update");
    for (int i = 0; i < CUSTOM_NUM_OF_VOL_TYPE; i++) {
        //MTK_ALOGVV("StreamType %d", i);
        for (int j = 0; j < CUSTOM_NUM_OF_VOL_MODE; j++) {
            //MTK_ALOGVV("DeviceType %d", j);
            for (int k = 0; k < CUSTOM_AUDIO_MAX_VOLUME_STEP; k++) {
                //MTK_ALOGVV("[IDX]:[Value] %d, %d", k, mAudioCustVolumeTable.audiovolume_steamtype[i][j][k]);
            }
        }
    }
#endif
#else
    ALOGW("%s unsupport", __FUNCTION__);
#endif
}

status_t AudioPolicyManagerCustomImpl::updatePrimaryModuleDevice(audio_devices_t rxDevice)
{
#if defined(MTK_LOW_LATENCY)
    // force restoring the device selection on other active outputs if it differs from the
    // one being selected for this output. Must run checkDeviceMuteStrategies if any stream is active, ALPS03074028
    for (size_t i = 0; i < mAudioPolicyManager->mOutputs.size(); i++) {
        sp<AudioOutputDescriptor> desc = mAudioPolicyManager->mOutputs.valueAt(i);
        if (desc != mAudioPolicyManager->mPrimaryOutput &&
                desc->isActive() &&
                mAudioPolicyManager->mPrimaryOutput->sharesHwModuleWith(desc) &&
                rxDevice != desc->device()) {
            mAudioPolicyManager->setOutputDevice(desc,
                            mAudioPolicyManager->getNewOutputDevice(desc, false /*fromCache*/),
                            true,
                            desc->latency()*2);
        }
    }
    return NO_ERROR;
#else
    ALOGW("%s unsupport, device 0x%x", __FUNCTION__, rxDevice);
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::freeGainTable(void)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    if (mGainTable.sceneGain != NULL) {
        delete[] mGainTable.sceneGain;
        mGainTable.sceneGain = NULL;
        mGainTable.sceneCount = 0;
    }
    return NO_ERROR;
#else
    ALOGW("%s unsupport", __FUNCTION__);
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::allocateGainTable(void)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
    String8 sceneCount = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetGainTableSceneCount"));
    String8 sceneGain = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetGainTableSceneTable="));
    String8 nonSceneGain = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetGainTableNonSceneTable="));
    String8 newvalSceneGain;
    String8 newvalNonSceneGain;
    newvalSceneGain.appendFormat("%s", sceneGain.string() + String8("GetGainTableSceneTable=").size());
    newvalNonSceneGain.appendFormat("%s", nonSceneGain.string() + String8("GetGainTableNonSceneTable=").size());
    AudioParameter param = AudioParameter(sceneCount);
    int valueInt;
    status_t ret;
    if (param.getInt(String8("GetGainTableSceneCount"), valueInt) == NO_ERROR) {
        mGainTableSceneCount = valueInt;
        ALOGD("getGainTable, mGainTableSceneCount %d", mGainTableSceneCount);
    } else {
        ALOGE("error, load GetGainTableSceneIndex failed!!");
        mGainTableSceneCount = 1;
    }
    freeGainTable();
    mGainTable.sceneGain = new GainTableForScene[mGainTableSceneCount];
    mGainTable.sceneCount = mGainTableSceneCount;
    // Load sceneGain
    ret = AudioPolicyServiceCustomImpl::common_getDecodedData(newvalSceneGain, sizeof(GainTableForScene) * mGainTableSceneCount, mGainTable.sceneGain);
    if (ret) {
        ALOGE("Load GetGainTableSceneTable Fail ret = %d", ret);
    } else {
    // Load nonSceneGain
        ret = AudioPolicyServiceCustomImpl::common_getDecodedData(newvalNonSceneGain, sizeof(mGainTable.nonSceneGain), &(mGainTable.nonSceneGain));
        if (ret) {
            ALOGE("Load GetGainTableNonSceneTable Fail ret = %d", ret);
        }
    }

    updateCurrentSceneIndexOfGainTable();
    return ret;
#else
    ALOGW("%s unsupport", __FUNCTION__);
    return INVALID_OPERATION;
#endif
}

status_t AudioPolicyManagerCustomImpl::updateCurrentSceneIndexOfGainTable(void)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
//  mGainTableSceneCount = mGainTable.sceneCount;
    String8 command = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetGainTableCurrentSceneIndex"));
    AudioParameter param = AudioParameter(command);
    int valueInt;
    if (param.getInt(String8("GetGainTableCurrentSceneIndex"), valueInt) == NO_ERROR) {
        if (valueInt < mGainTableSceneCount) {
            mGainTableSceneIndex = valueInt;
            ALOGD("Using scene [%d]/[%d]", valueInt, mGainTableSceneCount);
        } else {
            ALOGE("error, load valueInt failed [%d]/[%d]", valueInt, mGainTableSceneCount);
        }
    } else {
        ALOGE("error, load GetGainTableSceneIndex failed!!");
    }
    return NO_ERROR;
#else
    ALOGW("%s unsupport", __FUNCTION__);
    return INVALID_OPERATION;
#endif
}

int AudioPolicyManagerCustomImpl::getSceneIndexOfGainTable(String8 sceneName)
{
#if defined(MTK_AUDIO_GAIN_TABLE)
//  mGainTableSceneCount = mGainTable.sceneCount;
    String8 command = mAudioPolicyManager->mpClientInterface->getParameters(0, String8("GetGainTableSceneIndex=") + sceneName);
    AudioParameter param = AudioParameter(command);
    int valueInt = 0;
    if (param.getInt(String8("GetGainTableSceneIndex"), valueInt) == NO_ERROR) {
        if (valueInt < mGainTableSceneCount) {
            ALOGD("Using scene [%d]/[%d]", valueInt, mGainTableSceneCount);
        } else {
            ALOGE("error, load valueInt failed [%d]/[%d]", valueInt, mGainTableSceneCount);
        }
    } else {
        ALOGE("error, load GetGainTableSceneIndex failed!!");
    }
    return valueInt;
#else
    (void) sceneName;
    ALOGW("%s unsupport", __FUNCTION__);
    return 0;
#endif
}

};
