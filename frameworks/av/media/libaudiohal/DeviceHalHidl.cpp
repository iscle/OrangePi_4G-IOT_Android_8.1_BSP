/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <stdio.h>

#define LOG_TAG "DeviceHalHidl"
//#define LOG_NDEBUG 0

#include <android/hardware/audio/2.0/IPrimaryDevice.h>
#include <cutils/native_handle.h>
#include <hwbinder/IPCThreadState.h>
#include <utils/Log.h>

#include "DeviceHalHidl.h"
#include "HidlUtils.h"
#include "StreamHalHidl.h"

using ::android::hardware::audio::common::V2_0::AudioConfig;
using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::audio::common::V2_0::AudioInputFlag;
using ::android::hardware::audio::common::V2_0::AudioOutputFlag;
using ::android::hardware::audio::common::V2_0::AudioPatchHandle;
using ::android::hardware::audio::common::V2_0::AudioPort;
using ::android::hardware::audio::common::V2_0::AudioPortConfig;
using ::android::hardware::audio::common::V2_0::AudioMode;
using ::android::hardware::audio::common::V2_0::AudioSource;
using ::android::hardware::audio::V2_0::DeviceAddress;
using ::android::hardware::audio::V2_0::IPrimaryDevice;
using ::android::hardware::audio::V2_0::ParameterValue;
using ::android::hardware::audio::V2_0::Result;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;

namespace android {

namespace {

status_t deviceAddressFromHal(
        audio_devices_t device, const char* halAddress, DeviceAddress* address) {
    address->device = AudioDevice(device);

    if (address == nullptr || strnlen(halAddress, AUDIO_DEVICE_MAX_ADDRESS_LEN) == 0) {
        return OK;
    }
    const bool isInput = (device & AUDIO_DEVICE_BIT_IN) != 0;
    if (isInput) device &= ~AUDIO_DEVICE_BIT_IN;
    if ((!isInput && (device & AUDIO_DEVICE_OUT_ALL_A2DP) != 0)
            || (isInput && (device & AUDIO_DEVICE_IN_BLUETOOTH_A2DP) != 0)) {
        int status = sscanf(halAddress,
                "%hhX:%hhX:%hhX:%hhX:%hhX:%hhX",
                &address->address.mac[0], &address->address.mac[1], &address->address.mac[2],
                &address->address.mac[3], &address->address.mac[4], &address->address.mac[5]);
        return status == 6 ? OK : BAD_VALUE;
    } else if ((!isInput && (device & AUDIO_DEVICE_OUT_IP) != 0)
            || (isInput && (device & AUDIO_DEVICE_IN_IP) != 0)) {
        int status = sscanf(halAddress,
                "%hhu.%hhu.%hhu.%hhu",
                &address->address.ipv4[0], &address->address.ipv4[1],
                &address->address.ipv4[2], &address->address.ipv4[3]);
        return status == 4 ? OK : BAD_VALUE;
    } else if ((!isInput && (device & AUDIO_DEVICE_OUT_ALL_USB)) != 0
            || (isInput && (device & AUDIO_DEVICE_IN_ALL_USB)) != 0) {
        int status = sscanf(halAddress,
                "card=%d;device=%d",
                &address->address.alsa.card, &address->address.alsa.device);
        return status == 2 ? OK : BAD_VALUE;
    } else if ((!isInput && (device & AUDIO_DEVICE_OUT_BUS) != 0)
            || (isInput && (device & AUDIO_DEVICE_IN_BUS) != 0)) {
        if (halAddress != NULL) {
            address->busAddress = halAddress;
            return OK;
        }
        return BAD_VALUE;
    } else if ((!isInput && (device & AUDIO_DEVICE_OUT_REMOTE_SUBMIX)) != 0
            || (isInput && (device & AUDIO_DEVICE_IN_REMOTE_SUBMIX) != 0)) {
        if (halAddress != NULL) {
            address->rSubmixAddress = halAddress;
            return OK;
        }
        return BAD_VALUE;
    }
    return OK;
}

}  // namespace

DeviceHalHidl::DeviceHalHidl(const sp<IDevice>& device)
        : ConversionHelperHidl("Device"), mDevice(device),
          mPrimaryDevice(IPrimaryDevice::castFrom(device)) {
}

DeviceHalHidl::~DeviceHalHidl() {
    if (mDevice != 0) {
        mDevice.clear();
        hardware::IPCThreadState::self()->flushCommands();
    }
}

status_t DeviceHalHidl::getSupportedDevices(uint32_t*) {
    // Obsolete.
    return INVALID_OPERATION;
}

status_t DeviceHalHidl::initCheck() {
    if (mDevice == 0) return NO_INIT;
    return processReturn("initCheck", mDevice->initCheck());
}

status_t DeviceHalHidl::setVoiceVolume(float volume) {
    if (mDevice == 0) return NO_INIT;
    if (mPrimaryDevice == 0) return INVALID_OPERATION;
    return processReturn("setVoiceVolume", mPrimaryDevice->setVoiceVolume(volume));
}

status_t DeviceHalHidl::setMasterVolume(float volume) {
    if (mDevice == 0) return NO_INIT;
    if (mPrimaryDevice == 0) return INVALID_OPERATION;
    return processReturn("setMasterVolume", mPrimaryDevice->setMasterVolume(volume));
}

status_t DeviceHalHidl::getMasterVolume(float *volume) {
    if (mDevice == 0) return NO_INIT;
    if (mPrimaryDevice == 0) return INVALID_OPERATION;
    Result retval;
    Return<void> ret = mPrimaryDevice->getMasterVolume(
            [&](Result r, float v) {
                retval = r;
                if (retval == Result::OK) {
                    *volume = v;
                }
            });
    return processReturn("getMasterVolume", ret, retval);
}

status_t DeviceHalHidl::setMode(audio_mode_t mode) {
    if (mDevice == 0) return NO_INIT;
    if (mPrimaryDevice == 0) return INVALID_OPERATION;
    return processReturn("setMode", mPrimaryDevice->setMode(AudioMode(mode)));
}

status_t DeviceHalHidl::setMicMute(bool state) {
    if (mDevice == 0) return NO_INIT;
    return processReturn("setMicMute", mDevice->setMicMute(state));
}

status_t DeviceHalHidl::getMicMute(bool *state) {
    if (mDevice == 0) return NO_INIT;
    Result retval;
    Return<void> ret = mDevice->getMicMute(
            [&](Result r, bool mute) {
                retval = r;
                if (retval == Result::OK) {
                    *state = mute;
                }
            });
    return processReturn("getMicMute", ret, retval);
}

status_t DeviceHalHidl::setMasterMute(bool state) {
    if (mDevice == 0) return NO_INIT;
    return processReturn("setMasterMute", mDevice->setMasterMute(state));
}

status_t DeviceHalHidl::getMasterMute(bool *state) {
    if (mDevice == 0) return NO_INIT;
    Result retval;
    Return<void> ret = mDevice->getMasterMute(
            [&](Result r, bool mute) {
                retval = r;
                if (retval == Result::OK) {
                    *state = mute;
                }
            });
    return processReturn("getMasterMute", ret, retval);
}

status_t DeviceHalHidl::setParameters(const String8& kvPairs) {
    if (mDevice == 0) return NO_INIT;
    hidl_vec<ParameterValue> hidlParams;
    status_t status = parametersFromHal(kvPairs, &hidlParams);
    if (status != OK) return status;
    return processReturn("setParameters", mDevice->setParameters(hidlParams));
}

status_t DeviceHalHidl::getParameters(const String8& keys, String8 *values) {
    values->clear();
    if (mDevice == 0) return NO_INIT;
    hidl_vec<hidl_string> hidlKeys;
    status_t status = keysFromHal(keys, &hidlKeys);
    if (status != OK) return status;
    Result retval;
    Return<void> ret = mDevice->getParameters(
            hidlKeys,
            [&](Result r, const hidl_vec<ParameterValue>& parameters) {
                retval = r;
                if (retval == Result::OK) {
                    parametersToHal(parameters, values);
                }
            });
    return processReturn("getParameters", ret, retval);
}

status_t DeviceHalHidl::getInputBufferSize(
        const struct audio_config *config, size_t *size) {
    if (mDevice == 0) return NO_INIT;
    AudioConfig hidlConfig;
    HidlUtils::audioConfigFromHal(*config, &hidlConfig);
    Result retval;
    Return<void> ret = mDevice->getInputBufferSize(
            hidlConfig,
            [&](Result r, uint64_t bufferSize) {
                retval = r;
                if (retval == Result::OK) {
                    *size = static_cast<size_t>(bufferSize);
                }
            });
    return processReturn("getInputBufferSize", ret, retval);
}

status_t DeviceHalHidl::openOutputStream(
        audio_io_handle_t handle,
        audio_devices_t devices,
        audio_output_flags_t flags,
        struct audio_config *config,
        const char *address,
        sp<StreamOutHalInterface> *outStream) {
    if (mDevice == 0) return NO_INIT;
    DeviceAddress hidlDevice;
    status_t status = deviceAddressFromHal(devices, address, &hidlDevice);
    if (status != OK) return status;
    AudioConfig hidlConfig;
    HidlUtils::audioConfigFromHal(*config, &hidlConfig);
    Result retval = Result::NOT_INITIALIZED;
    Return<void> ret = mDevice->openOutputStream(
            handle,
            hidlDevice,
            hidlConfig,
            AudioOutputFlag(flags),
            [&](Result r, const sp<IStreamOut>& result, const AudioConfig& suggestedConfig) {
                retval = r;
                if (retval == Result::OK) {
                    *outStream = new StreamOutHalHidl(result);
                }
                HidlUtils::audioConfigToHal(suggestedConfig, config);
            });
    return processReturn("openOutputStream", ret, retval);
}

status_t DeviceHalHidl::openInputStream(
        audio_io_handle_t handle,
        audio_devices_t devices,
        struct audio_config *config,
        audio_input_flags_t flags,
        const char *address,
        audio_source_t source,
        sp<StreamInHalInterface> *inStream) {
    if (mDevice == 0) return NO_INIT;
    DeviceAddress hidlDevice;
    status_t status = deviceAddressFromHal(devices, address, &hidlDevice);
    if (status != OK) return status;
    AudioConfig hidlConfig;
    HidlUtils::audioConfigFromHal(*config, &hidlConfig);
    Result retval = Result::NOT_INITIALIZED;
    Return<void> ret = mDevice->openInputStream(
            handle,
            hidlDevice,
            hidlConfig,
            AudioInputFlag(flags),
            AudioSource(source),
            [&](Result r, const sp<IStreamIn>& result, const AudioConfig& suggestedConfig) {
                retval = r;
                if (retval == Result::OK) {
                    *inStream = new StreamInHalHidl(result);
                }
                HidlUtils::audioConfigToHal(suggestedConfig, config);
            });
    return processReturn("openInputStream", ret, retval);
}

status_t DeviceHalHidl::supportsAudioPatches(bool *supportsPatches) {
    if (mDevice == 0) return NO_INIT;
    return processReturn("supportsAudioPatches", mDevice->supportsAudioPatches(), supportsPatches);
}

status_t DeviceHalHidl::createAudioPatch(
        unsigned int num_sources,
        const struct audio_port_config *sources,
        unsigned int num_sinks,
        const struct audio_port_config *sinks,
        audio_patch_handle_t *patch) {
    if (mDevice == 0) return NO_INIT;
    hidl_vec<AudioPortConfig> hidlSources, hidlSinks;
    HidlUtils::audioPortConfigsFromHal(num_sources, sources, &hidlSources);
    HidlUtils::audioPortConfigsFromHal(num_sinks, sinks, &hidlSinks);
    Result retval;
    Return<void> ret = mDevice->createAudioPatch(
            hidlSources, hidlSinks,
            [&](Result r, AudioPatchHandle hidlPatch) {
                retval = r;
                if (retval == Result::OK) {
                    *patch = static_cast<audio_patch_handle_t>(hidlPatch);
                }
            });
    return processReturn("createAudioPatch", ret, retval);
}

status_t DeviceHalHidl::releaseAudioPatch(audio_patch_handle_t patch) {
    if (mDevice == 0) return NO_INIT;
    return processReturn("releaseAudioPatch", mDevice->releaseAudioPatch(patch));
}

status_t DeviceHalHidl::getAudioPort(struct audio_port *port) {
    if (mDevice == 0) return NO_INIT;
    AudioPort hidlPort;
    HidlUtils::audioPortFromHal(*port, &hidlPort);
    Result retval;
    Return<void> ret = mDevice->getAudioPort(
            hidlPort,
            [&](Result r, const AudioPort& p) {
                retval = r;
                if (retval == Result::OK) {
                    HidlUtils::audioPortToHal(p, port);
                }
            });
    return processReturn("getAudioPort", ret, retval);
}

status_t DeviceHalHidl::setAudioPortConfig(const struct audio_port_config *config) {
    if (mDevice == 0) return NO_INIT;
    AudioPortConfig hidlConfig;
    HidlUtils::audioPortConfigFromHal(*config, &hidlConfig);
    return processReturn("setAudioPortConfig", mDevice->setAudioPortConfig(hidlConfig));
}

status_t DeviceHalHidl::dump(int fd) {
    if (mDevice == 0) return NO_INIT;
    native_handle_t* hidlHandle = native_handle_create(1, 0);
    hidlHandle->data[0] = fd;
    Return<void> ret = mDevice->debugDump(hidlHandle);
    native_handle_delete(hidlHandle);
    return processReturn("dump", ret);
}

} // namespace android
