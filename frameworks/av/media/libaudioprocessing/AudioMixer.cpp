/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "AudioMixer"
//#define LOG_NDEBUG 0

#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>
#include <sys/types.h>

#include <utils/Errors.h>
#include <utils/Log.h>

#include <cutils/compiler.h>
#include <utils/Debug.h>

#include <system/audio.h>

#include <audio_utils/primitives.h>
#include <audio_utils/format.h>
#include <media/AudioMixer.h>

#include "AudioMixerOps.h"

#if defined(MTK_AUDIO_DEBUG)
#include <media/AudioParameter.h>
#include <media/AudioUtilmtk.h>
#endif // MTK_AUDIO_DEBUG
#include <media/MtkLogger.h>

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
#include "AudioCompFltCustParam.h"
int android::AudioMixer::MTK_BLOCK_SIZE = 512;
#define BLOCKSIZE MTK_BLOCK_SIZE
#endif // MTK_AUDIOMIXER_ENABLE_DRC

// The FCC_2 macro refers to the Fixed Channel Count of 2 for the legacy integer mixer.
#ifndef FCC_2
#define FCC_2 2
#endif

// Look for MONO_HACK for any Mono hack involving legacy mono channel to
// stereo channel conversion.

/* VERY_VERY_VERBOSE_LOGGING will show exactly which process hook and track hook is
 * being used. This is a considerable amount of log spam, so don't enable unless you
 * are verifying the hook based code.
 */
//#define VERY_VERY_VERBOSE_LOGGING
#ifdef VERY_VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
//define ALOGVV printf  // for test-mixer.cpp
#else
#define ALOGVV(a...) do { } while (0)
#endif

#ifndef ARRAY_SIZE
#define ARRAY_SIZE(x) (sizeof(x)/sizeof((x)[0]))
#endif

// TODO: Move these macro/inlines to a header file.
template <typename T>
static inline
T max(const T& x, const T& y) {
    return x > y ? x : y;
}

// Set kUseNewMixer to true to use the new mixer engine always. Otherwise the
// original code will be used for stereo sinks, the new mixer for multichannel.
static const bool kUseNewMixer = true;

// Set kUseFloat to true to allow floating input into the mixer engine.
// If kUseNewMixer is false, this is ignored or may be overridden internally
// because of downmix/upmix support.
static const bool kUseFloat = true;

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
static const size_t kCopyBufferFrameCount = 512 * 4;
#else
// Set to default copy buffer size in frames for input processing.
static const size_t kCopyBufferFrameCount = 256;
#endif

namespace android {

// ----------------------------------------------------------------------------
// <MTK_AUDIO
#if defined(MTK_AUDIO_DEBUG)
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
static inline int32_t clamp4_27(int32_t sample);
#endif // MTK_AUDIOMIXER_ENABLE_DRC
#endif // MTK_AUDIO_DEBUG
// MTK_AUDIO>
// ----------------------------------------------------------------------------

template <typename T>
T min(const T& a, const T& b)
{
    return a < b ? a : b;
}

// ----------------------------------------------------------------------------

// Ensure mConfiguredNames bitmask is initialized properly on all architectures.
// The value of 1 << x is undefined in C when x >= 32.

AudioMixer::AudioMixer(size_t frameCount, uint32_t sampleRate, uint32_t maxNumTracks)
    :   mTrackNames(0), mConfiguredNames((maxNumTracks >= 32 ? 0 : 1 << maxNumTracks) - 1),
        mSampleRate(sampleRate)
{
    ALOG_ASSERT(maxNumTracks <= MAX_NUM_TRACKS, "maxNumTracks %u > MAX_NUM_TRACKS %u",
            maxNumTracks, MAX_NUM_TRACKS);

    // AudioMixer is not yet capable of more than 32 active track inputs
    ALOG_ASSERT(32 >= MAX_NUM_TRACKS, "bad MAX_NUM_TRACKS %d", MAX_NUM_TRACKS);

    pthread_once(&sOnceControl, &sInitRoutine);

    InitializeMTKLogLevel("af.audioflinger.log");

    mState.enabledTracks= 0;
    mState.needsChanged = 0;
    mState.frameCount   = frameCount;
    mState.hook         = process__nop;
    mState.outputTemp   = NULL;
    mState.resampleTemp = NULL;
    mState.mNBLogWriter = &mDummyLogWriter;
// <MTK_AUDIO
    mState.nonResampleTemp = NULL;
    mState.pDRCTempBuffer = NULL;
// MTK_AUDIO>
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    mState.nonResampleTemp = new int32_t[MAX_NUM_CHANNELS * mState.frameCount];
    mState.pDRCTempBuffer = new int32_t[FCC_2 * mState.frameCount];
    if ((size_t)BLOCKSIZE > mState.frameCount) {
        BLOCKSIZE = 16;  // google default
    }
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    // mState.reserved

    // FIXME Most of the following initialization is probably redundant since
    // tracks[i] should only be referenced if (mTrackNames & (1 << i)) != 0
    // and mTrackNames is initially 0.  However, leave it here until that's verified.
    track_t* t = mState.tracks;
    for (unsigned i=0 ; i < MAX_NUM_TRACKS ; i++) {
        t->resampler = NULL;
        t->downmixerBufferProvider = NULL;
        t->mReformatBufferProvider = NULL;
        t->mTimestretchBufferProvider = NULL;
// <MTK_AUDIO
        t->mDRCEnable = false;
        t->mpDRCObj = NULL;
        t->mState = NULL;
// MTK_AUDIO>
        t++;
    }

}

AudioMixer::~AudioMixer()
{
    track_t* t = mState.tracks;
    for (unsigned i=0 ; i < MAX_NUM_TRACKS ; i++) {
        delete t->resampler;
        delete t->downmixerBufferProvider;
        delete t->mReformatBufferProvider;
        delete t->mTimestretchBufferProvider;
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
        if (t->mpDRCObj) {
            t->mpDRCObj->close();
            delete t->mpDRCObj;
            t->mpDRCObj = NULL;
        }
#endif // MTK_AUDIOMIXER_ENABLE_DRC
        t++;
    }
    delete [] mState.outputTemp;
    delete [] mState.resampleTemp;
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    delete [] mState.nonResampleTemp;
    delete [] mState.pDRCTempBuffer;
#endif // MTK_AUDIOMIXER_ENABLE_DRC
}

void AudioMixer::setNBLogWriter(NBLog::Writer *logWriter)
{
    mState.mNBLogWriter = logWriter;
}

static inline audio_format_t selectMixerInFormat(audio_format_t inputFormat __unused) {
    return kUseFloat && kUseNewMixer ? AUDIO_FORMAT_PCM_FLOAT : AUDIO_FORMAT_PCM_16_BIT;
}

int AudioMixer::getTrackName(audio_channel_mask_t channelMask,
        audio_format_t format, int sessionId)
{
    if (!isValidPcmTrackFormat(format)) {
        ALOGE("AudioMixer::getTrackName invalid format (%#x)", format);
        return -1;
    }
    uint32_t names = (~mTrackNames) & mConfiguredNames;
    if (names != 0) {
        int n = __builtin_ctz(names);
        ALOGV("add track (%d)", n);
        // assume default parameters for the track, except where noted below
        track_t* t = &mState.tracks[n];
        t->needs = 0;

        // Integer volume.
        // Currently integer volume is kept for the legacy integer mixer.
        // Will be removed when the legacy mixer path is removed.
        t->volume[0] = UNITY_GAIN_INT;
        t->volume[1] = UNITY_GAIN_INT;
        t->prevVolume[0] = UNITY_GAIN_INT << 16;
        t->prevVolume[1] = UNITY_GAIN_INT << 16;
        t->volumeInc[0] = 0;
        t->volumeInc[1] = 0;
        t->auxLevel = 0;
        t->auxInc = 0;
        t->prevAuxLevel = 0;

        // Floating point volume.
        t->mVolume[0] = UNITY_GAIN_FLOAT;
        t->mVolume[1] = UNITY_GAIN_FLOAT;
        t->mPrevVolume[0] = UNITY_GAIN_FLOAT;
        t->mPrevVolume[1] = UNITY_GAIN_FLOAT;
        t->mVolumeInc[0] = 0.;
        t->mVolumeInc[1] = 0.;
        t->mAuxLevel = 0.;
        t->mAuxInc = 0.;
        t->mPrevAuxLevel = 0.;

        // no initialization needed
        // t->frameCount
        t->channelCount = audio_channel_count_from_out_mask(channelMask);
        t->enabled = false;
        ALOGV_IF(audio_channel_mask_get_bits(channelMask) != AUDIO_CHANNEL_OUT_STEREO,
                "Non-stereo channel mask: %d\n", channelMask);
        t->channelMask = channelMask;
        t->sessionId = sessionId;
        // setBufferProvider(name, AudioBufferProvider *) is required before enable(name)
        t->bufferProvider = NULL;
        t->buffer.raw = NULL;
        // no initialization needed
        // t->buffer.frameCount
        t->hook = NULL;
        t->in = NULL;
        t->resampler = NULL;
        t->sampleRate = mSampleRate;
        // setParameter(name, TRACK, MAIN_BUFFER, mixBuffer) is required before enable(name)
        t->mainBuffer = NULL;
        t->auxBuffer = NULL;
        t->mInputBufferProvider = NULL;
        t->mReformatBufferProvider = NULL;
        t->downmixerBufferProvider = NULL;
        t->mPostDownmixReformatBufferProvider = NULL;
        t->mTimestretchBufferProvider = NULL;
        t->mMixerFormat = AUDIO_FORMAT_PCM_16_BIT;
        t->mFormat = format;
        t->mMixerInFormat = selectMixerInFormat(format);
        t->mDownmixRequiresFormat = AUDIO_FORMAT_INVALID; // no format required
        t->mMixerChannelMask = audio_channel_mask_from_representation_and_bits(
                AUDIO_CHANNEL_REPRESENTATION_POSITION, AUDIO_CHANNEL_OUT_STEREO);
        t->mMixerChannelCount = audio_channel_count_from_out_mask(t->mMixerChannelMask);
        t->mPlaybackRate = AUDIO_PLAYBACK_RATE_DEFAULT;
// <MTK_AUDIO
        t->mStreamType = AUDIO_STREAM_DEFAULT;
        t->mDevSampleRate = mSampleRate;
// MTK_AUDIO>
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
        t->mState = &mState;
#endif // MTK_AUDIOMIXER_ENABLE_DRC

        // Check the downmixing (or upmixing) requirements.
        status_t status = t->prepareForDownmix();
        if (status != OK) {
            ALOGE("AudioMixer::getTrackName invalid channelMask (%#x)", channelMask);
            return -1;
        }
        // prepareForDownmix() may change mDownmixRequiresFormat
        ALOGVV("mMixerFormat:%#x  mMixerInFormat:%#x\n", t->mMixerFormat, t->mMixerInFormat);
        t->prepareForReformat();
        mTrackNames |= 1 << n;
        return TRACK0 + n;
    }
    ALOGE("AudioMixer::getTrackName out of available tracks");
    return -1;
}

void AudioMixer::invalidateState(uint32_t mask)
{
    if (mask != 0) {
        mState.needsChanged |= mask;
        mState.hook = process__validate;
    }
 }

// Called when channel masks have changed for a track name
// TODO: Fix DownmixerBufferProvider not to (possibly) change mixer input format,
// which will simplify this logic.
bool AudioMixer::setChannelMasks(int name,
        audio_channel_mask_t trackChannelMask, audio_channel_mask_t mixerChannelMask) {
    track_t &track = mState.tracks[name];

    if (trackChannelMask == track.channelMask
            && mixerChannelMask == track.mMixerChannelMask) {
        return false;  // no need to change
    }
    // always recompute for both channel masks even if only one has changed.
    const uint32_t trackChannelCount = audio_channel_count_from_out_mask(trackChannelMask);
    const uint32_t mixerChannelCount = audio_channel_count_from_out_mask(mixerChannelMask);
    const bool mixerChannelCountChanged = track.mMixerChannelCount != mixerChannelCount;

    ALOG_ASSERT((trackChannelCount <= MAX_NUM_CHANNELS_TO_DOWNMIX)
            && trackChannelCount
            && mixerChannelCount);
    track.channelMask = trackChannelMask;
    track.channelCount = trackChannelCount;
    track.mMixerChannelMask = mixerChannelMask;
    track.mMixerChannelCount = mixerChannelCount;

    // channel masks have changed, does this track need a downmixer?
    // update to try using our desired format (if we aren't already using it)
    const audio_format_t prevDownmixerFormat = track.mDownmixRequiresFormat;
    const status_t status = mState.tracks[name].prepareForDownmix();
    ALOGE_IF(status != OK,
            "prepareForDownmix error %d, track channel mask %#x, mixer channel mask %#x",
            status, track.channelMask, track.mMixerChannelMask);

    if (prevDownmixerFormat != track.mDownmixRequiresFormat) {
        track.prepareForReformat(); // because of downmixer, track format may change!
    }

    if (track.resampler && mixerChannelCountChanged) {
        // resampler channels may have changed.
        const uint32_t resetToSampleRate = track.sampleRate;
        delete track.resampler;
        track.resampler = NULL;
        track.sampleRate = mSampleRate; // without resampler, track rate is device sample rate.
        // recreate the resampler with updated format, channels, saved sampleRate.
        track.setResampler(resetToSampleRate /*trackSampleRate*/, mSampleRate /*devSampleRate*/);
    }
    return true;
}

void AudioMixer::track_t::unprepareForDownmix() {
    ALOGV("AudioMixer::unprepareForDownmix(%p)", this);

    if (mPostDownmixReformatBufferProvider != nullptr) {
        // release any buffers held by the mPostDownmixReformatBufferProvider
        // before deallocating the downmixerBufferProvider.
        mPostDownmixReformatBufferProvider->reset();
    }

    mDownmixRequiresFormat = AUDIO_FORMAT_INVALID;
    if (downmixerBufferProvider != NULL) {
        // this track had previously been configured with a downmixer, delete it
        ALOGV(" deleting old downmixer");
        delete downmixerBufferProvider;
        downmixerBufferProvider = NULL;
        reconfigureBufferProviders();
    } else {
        ALOGV(" nothing to do, no downmixer to delete");
    }
}

status_t AudioMixer::track_t::prepareForDownmix()
{
    ALOGV("AudioMixer::prepareForDownmix(%p) with mask 0x%x",
            this, channelMask);

    // discard the previous downmixer if there was one
    unprepareForDownmix();
    // MONO_HACK Only remix (upmix or downmix) if the track and mixer/device channel masks
    // are not the same and not handled internally, as mono -> stereo currently is.
    if (channelMask == mMixerChannelMask
            || (channelMask == AUDIO_CHANNEL_OUT_MONO
                    && mMixerChannelMask == AUDIO_CHANNEL_OUT_STEREO)) {
        return NO_ERROR;
    }
    // DownmixerBufferProvider is only used for position masks.
    if (audio_channel_mask_get_representation(channelMask)
                == AUDIO_CHANNEL_REPRESENTATION_POSITION
            && DownmixerBufferProvider::isMultichannelCapable()) {
        DownmixerBufferProvider* pDbp = new DownmixerBufferProvider(channelMask,
                mMixerChannelMask,
                AUDIO_FORMAT_PCM_16_BIT /* TODO: use mMixerInFormat, now only PCM 16 */,
                sampleRate, sessionId, kCopyBufferFrameCount);

        if (pDbp->isValid()) { // if constructor completed properly
            mDownmixRequiresFormat = AUDIO_FORMAT_PCM_16_BIT; // PCM 16 bit required for downmix
            downmixerBufferProvider = pDbp;
            reconfigureBufferProviders();
            return NO_ERROR;
        }
        delete pDbp;
    }

    // Effect downmixer does not accept the channel conversion.  Let's use our remixer.
    RemixBufferProvider* pRbp = new RemixBufferProvider(channelMask,
            mMixerChannelMask, mMixerInFormat, kCopyBufferFrameCount);
    // Remix always finds a conversion whereas Downmixer effect above may fail.
    downmixerBufferProvider = pRbp;
    reconfigureBufferProviders();
    return NO_ERROR;
}

void AudioMixer::track_t::unprepareForReformat() {
    ALOGV("AudioMixer::unprepareForReformat(%p)", this);
    bool requiresReconfigure = false;
    if (mReformatBufferProvider != NULL) {
        delete mReformatBufferProvider;
        mReformatBufferProvider = NULL;
        requiresReconfigure = true;
    }
    if (mPostDownmixReformatBufferProvider != NULL) {
        delete mPostDownmixReformatBufferProvider;
        mPostDownmixReformatBufferProvider = NULL;
        requiresReconfigure = true;
    }
    if (requiresReconfigure) {
        reconfigureBufferProviders();
    }
}

status_t AudioMixer::track_t::prepareForReformat()
{
    ALOGV("AudioMixer::prepareForReformat(%p) with format %#x", this, mFormat);
    // discard previous reformatters
    unprepareForReformat();
    // only configure reformatters as needed
    const audio_format_t targetFormat = mDownmixRequiresFormat != AUDIO_FORMAT_INVALID
            ? mDownmixRequiresFormat : mMixerInFormat;
    bool requiresReconfigure = false;
    if (mFormat != targetFormat) {
        mReformatBufferProvider = new ReformatBufferProvider(
                audio_channel_count_from_out_mask(channelMask),
                mFormat,
                targetFormat,
                kCopyBufferFrameCount);
        requiresReconfigure = true;
    }
    if (targetFormat != mMixerInFormat) {
        mPostDownmixReformatBufferProvider = new ReformatBufferProvider(
                audio_channel_count_from_out_mask(mMixerChannelMask),
                targetFormat,
                mMixerInFormat,
                kCopyBufferFrameCount);
        requiresReconfigure = true;
    }
    if (requiresReconfigure) {
        reconfigureBufferProviders();
    }
    return NO_ERROR;
}

void AudioMixer::track_t::reconfigureBufferProviders()
{
    bufferProvider = mInputBufferProvider;

    if (mReformatBufferProvider) {
        mReformatBufferProvider->setBufferProvider(bufferProvider);
        bufferProvider = mReformatBufferProvider;
    }
    if (downmixerBufferProvider) {
        downmixerBufferProvider->setBufferProvider(bufferProvider);
        bufferProvider = downmixerBufferProvider;
    }
    if (mPostDownmixReformatBufferProvider) {
        mPostDownmixReformatBufferProvider->setBufferProvider(bufferProvider);
        bufferProvider = mPostDownmixReformatBufferProvider;
    }
    if (mTimestretchBufferProvider) {
        mTimestretchBufferProvider->setBufferProvider(bufferProvider);
        bufferProvider = mTimestretchBufferProvider;
    }
}

void AudioMixer::deleteTrackName(int name)
{
    ALOGV("AudioMixer::deleteTrackName(%d)", name);
    name -= TRACK0;
    LOG_ALWAYS_FATAL_IF(name < 0 || name >= (int)MAX_NUM_TRACKS, "bad track name %d", name);
    ALOGV("deleteTrackName(%d)", name);
    track_t& track(mState.tracks[ name ]);
    if (track.enabled) {
        track.enabled = false;
        invalidateState(1<<name);
    }
    // delete the resampler
    delete track.resampler;
    track.resampler = NULL;
    // delete the downmixer
    mState.tracks[name].unprepareForDownmix();
    // delete the reformatter
    mState.tracks[name].unprepareForReformat();
    // delete the timestretch provider
    delete track.mTimestretchBufferProvider;
    track.mTimestretchBufferProvider = NULL;

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    track.mDRCEnable = false;
    if (track.mpDRCObj) {
        track.mpDRCObj->close();
        delete track.mpDRCObj;
        track.mpDRCObj = NULL;
    }
#endif // MTK_AUDIOMIXER_ENABLE_DRC

    mTrackNames &= ~(1<<name);
}

void AudioMixer::enable(int name)
{
    name -= TRACK0;
    ALOG_ASSERT(uint32_t(name) < MAX_NUM_TRACKS, "bad track name %d", name);
    track_t& track = mState.tracks[name];

    if (!track.enabled) {
        track.enabled = true;
        ALOGV("enable(%d)", name);
        invalidateState(1 << name);
    }
}

void AudioMixer::disable(int name)
{
    name -= TRACK0;
    ALOG_ASSERT(uint32_t(name) < MAX_NUM_TRACKS, "bad track name %d", name);
    track_t& track = mState.tracks[name];

    if (track.enabled) {
        track.enabled = false;
        ALOGV("disable(%d)", name);
        invalidateState(1 << name);
    }
}

/* Sets the volume ramp variables for the AudioMixer.
 *
 * The volume ramp variables are used to transition from the previous
 * volume to the set volume.  ramp controls the duration of the transition.
 * Its value is typically one state framecount period, but may also be 0,
 * meaning "immediate."
 *
 * FIXME: 1) Volume ramp is enabled only if there is a nonzero integer increment
 * even if there is a nonzero floating point increment (in that case, the volume
 * change is immediate).  This restriction should be changed when the legacy mixer
 * is removed (see #2).
 * FIXME: 2) Integer volume variables are used for Legacy mixing and should be removed
 * when no longer needed.
 *
 * @param newVolume set volume target in floating point [0.0, 1.0].
 * @param ramp number of frames to increment over. if ramp is 0, the volume
 * should be set immediately.  Currently ramp should not exceed 65535 (frames).
 * @param pIntSetVolume pointer to the U4.12 integer target volume, set on return.
 * @param pIntPrevVolume pointer to the U4.28 integer previous volume, set on return.
 * @param pIntVolumeInc pointer to the U4.28 increment per output audio frame, set on return.
 * @param pSetVolume pointer to the float target volume, set on return.
 * @param pPrevVolume pointer to the float previous volume, set on return.
 * @param pVolumeInc pointer to the float increment per output audio frame, set on return.
 * @return true if the volume has changed, false if volume is same.
 */
static inline bool setVolumeRampVariables(float newVolume, int32_t ramp,
        int16_t *pIntSetVolume, int32_t *pIntPrevVolume, int32_t *pIntVolumeInc,
        float *pSetVolume, float *pPrevVolume, float *pVolumeInc) {
    // check floating point volume to see if it is identical to the previously
    // set volume.
    // We do not use a tolerance here (and reject changes too small)
    // as it may be confusing to use a different value than the one set.
    // If the resulting volume is too small to ramp, it is a direct set of the volume.
    if (newVolume == *pSetVolume) {
        return false;
    }
    if (newVolume < 0) {
        newVolume = 0; // should not have negative volumes
    } else {
        switch (fpclassify(newVolume)) {
        case FP_SUBNORMAL:
        case FP_NAN:
            newVolume = 0;
            break;
        case FP_ZERO:
            break; // zero volume is fine
        case FP_INFINITE:
            // Infinite volume could be handled consistently since
            // floating point math saturates at infinities,
            // but we limit volume to unity gain float.
            // ramp = 0; break;
            //
            newVolume = AudioMixer::UNITY_GAIN_FLOAT;
            break;
        case FP_NORMAL:
        default:
            // Floating point does not have problems with overflow wrap
            // that integer has.  However, we limit the volume to
            // unity gain here.
            // TODO: Revisit the volume limitation and perhaps parameterize.
            if (newVolume > AudioMixer::UNITY_GAIN_FLOAT) {
                newVolume = AudioMixer::UNITY_GAIN_FLOAT;
            }
            break;
        }
    }

    // set floating point volume ramp
    if (ramp != 0) {
        // when the ramp completes, *pPrevVolume is set to *pSetVolume, so there
        // is no computational mismatch; hence equality is checked here.
        ALOGD_IF(*pPrevVolume != *pSetVolume, "previous float ramp hasn't finished,"
                " prev:%f  set_to:%f", *pPrevVolume, *pSetVolume);
        const float inc = (newVolume - *pPrevVolume) / ramp; // could be inf, nan, subnormal
        const float maxv = max(newVolume, *pPrevVolume); // could be inf, cannot be nan, subnormal

        if (isnormal(inc) // inc must be a normal number (no subnormals, infinite, nan)
                && maxv + inc != maxv) { // inc must make forward progress
            *pVolumeInc = inc;
            // ramp is set now.
            // Note: if newVolume is 0, then near the end of the ramp,
            // it may be possible that the ramped volume may be subnormal or
            // temporarily negative by a small amount or subnormal due to floating
            // point inaccuracies.
        } else {
            ramp = 0; // ramp not allowed
        }
    }

    // compute and check integer volume, no need to check negative values
    // The integer volume is limited to "unity_gain" to avoid wrapping and other
    // audio artifacts, so it never reaches the range limit of U4.28.
    // We safely use signed 16 and 32 bit integers here.
    const float scaledVolume = newVolume * AudioMixer::UNITY_GAIN_INT; // not neg, subnormal, nan
    const int32_t intVolume = (scaledVolume >= (float)AudioMixer::UNITY_GAIN_INT) ?
            AudioMixer::UNITY_GAIN_INT : (int32_t)scaledVolume;

    // set integer volume ramp
    if (ramp != 0) {
        // integer volume is U4.12 (to use 16 bit multiplies), but ramping uses U4.28.
        // when the ramp completes, *pIntPrevVolume is set to *pIntSetVolume << 16, so there
        // is no computational mismatch; hence equality is checked here.
        ALOGD_IF(*pIntPrevVolume != *pIntSetVolume << 16, "previous int ramp hasn't finished,"
                " prev:%d  set_to:%d", *pIntPrevVolume, *pIntSetVolume << 16);
        const int32_t inc = ((intVolume << 16) - *pIntPrevVolume) / ramp;

        if (inc != 0) { // inc must make forward progress
            *pIntVolumeInc = inc;
        } else {
            ramp = 0; // ramp not allowed
        }
    }

    // if no ramp, or ramp not allowed, then clear float and integer increments
    if (ramp == 0) {
        *pVolumeInc = 0;
        *pPrevVolume = newVolume;
        *pIntVolumeInc = 0;
        *pIntPrevVolume = intVolume << 16;
    }
    *pSetVolume = newVolume;
    *pIntSetVolume = intVolume;
    return true;
}

void AudioMixer::setParameter(int name, int target, int param, void *value)
{
    name -= TRACK0;
    ALOG_ASSERT(uint32_t(name) < MAX_NUM_TRACKS, "bad track name %d", name);
    track_t& track = mState.tracks[name];

    int valueInt = static_cast<int>(reinterpret_cast<uintptr_t>(value));
    int32_t *valueBuf = reinterpret_cast<int32_t*>(value);

    switch (target) {

    case TRACK:
        switch (param) {
        case CHANNEL_MASK: {
            const audio_channel_mask_t trackChannelMask =
                static_cast<audio_channel_mask_t>(valueInt);
            if (setChannelMasks(name, trackChannelMask, track.mMixerChannelMask)) {
                ALOGV("setParameter(TRACK, CHANNEL_MASK, %x)", trackChannelMask);
                invalidateState(1 << name);
            }
            } break;
        case MAIN_BUFFER:
            if (track.mainBuffer != valueBuf) {
                track.mainBuffer = valueBuf;
                ALOGV("setParameter(TRACK, MAIN_BUFFER, %p)", valueBuf);
                invalidateState(1 << name);
            }
            break;
        case AUX_BUFFER:
            if (track.auxBuffer != valueBuf) {
                track.auxBuffer = valueBuf;
                ALOGV("setParameter(TRACK, AUX_BUFFER, %p)", valueBuf);
                invalidateState(1 << name);
            }
            break;
        case FORMAT: {
            audio_format_t format = static_cast<audio_format_t>(valueInt);
            if (track.mFormat != format) {
                ALOG_ASSERT(audio_is_linear_pcm(format), "Invalid format %#x", format);
                track.mFormat = format;
                ALOGV("setParameter(TRACK, FORMAT, %#x)", format);
                track.prepareForReformat();
                invalidateState(1 << name);
            }
            } break;
        // FIXME do we want to support setting the downmix type from AudioFlinger?
        //         for a specific track? or per mixer?
        /* case DOWNMIX_TYPE:
            break          */
        case MIXER_FORMAT: {
            audio_format_t format = static_cast<audio_format_t>(valueInt);
            if (track.mMixerFormat != format) {
                track.mMixerFormat = format;
                ALOGV("setParameter(TRACK, MIXER_FORMAT, %#x)", format);
            }
            } break;
        case MIXER_CHANNEL_MASK: {
            const audio_channel_mask_t mixerChannelMask =
                    static_cast<audio_channel_mask_t>(valueInt);
            if (setChannelMasks(name, track.channelMask, mixerChannelMask)) {
                ALOGV("setParameter(TRACK, MIXER_CHANNEL_MASK, %#x)", mixerChannelMask);
                invalidateState(1 << name);
            }
            } break;
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
        case STREAM_TYPE:
            track.mStreamType = (audio_stream_type_t)valueInt;
            break;
#endif // MTK_AUDIOMIXER_ENABLE_DRC
        default:
            LOG_ALWAYS_FATAL("setParameter track: bad param %d", param);
        }
        break;

    case RESAMPLE:
        switch (param) {
        case SAMPLE_RATE:
            ALOG_ASSERT(valueInt > 0, "bad sample rate %d", valueInt);
            if (track.setResampler(uint32_t(valueInt), mSampleRate)) {
                ALOGV("setParameter(RESAMPLE, SAMPLE_RATE, %u)",
                        uint32_t(valueInt));
                invalidateState(1 << name);
            }
            break;
        case RESET:
            track.resetResampler();
            invalidateState(1 << name);
            break;
        case REMOVE:
            delete track.resampler;
            track.resampler = NULL;
            track.sampleRate = mSampleRate;
            invalidateState(1 << name);
            break;
        default:
            LOG_ALWAYS_FATAL("setParameter resample: bad param %d", param);
        }
        break;

    case RAMP_VOLUME:
    case VOLUME:
        switch (param) {
        case AUXLEVEL:
            if (setVolumeRampVariables(*reinterpret_cast<float*>(value),
                    target == RAMP_VOLUME ? mState.frameCount : 0,
                    &track.auxLevel, &track.prevAuxLevel, &track.auxInc,
                    &track.mAuxLevel, &track.mPrevAuxLevel, &track.mAuxInc)) {
                ALOGV("setParameter(%s, AUXLEVEL: %04x)",
                        target == VOLUME ? "VOLUME" : "RAMP_VOLUME", track.auxLevel);
                invalidateState(1 << name);
            }
            break;
        default:
            if ((unsigned)param >= VOLUME0 && (unsigned)param < VOLUME0 + MAX_NUM_VOLUMES) {
                if (setVolumeRampVariables(*reinterpret_cast<float*>(value),
                        target == RAMP_VOLUME ? mState.frameCount : 0,
                        &track.volume[param - VOLUME0], &track.prevVolume[param - VOLUME0],
                        &track.volumeInc[param - VOLUME0],
                        &track.mVolume[param - VOLUME0], &track.mPrevVolume[param - VOLUME0],
                        &track.mVolumeInc[param - VOLUME0])) {
                    ALOGV("setParameter(%s, VOLUME%d: %04x)",
                            target == VOLUME ? "VOLUME" : "RAMP_VOLUME", param - VOLUME0,
                                    track.volume[param - VOLUME0]);
                    invalidateState(1 << name);
                }
            } else {
                LOG_ALWAYS_FATAL("setParameter volume: bad param %d", param);
            }
        }
        break;
        case TIMESTRETCH:
            switch (param) {
            case PLAYBACK_RATE: {
                const AudioPlaybackRate *playbackRate =
                        reinterpret_cast<AudioPlaybackRate*>(value);
                ALOGW_IF(!isAudioPlaybackRateValid(*playbackRate),
                        "bad parameters speed %f, pitch %f",playbackRate->mSpeed,
                        playbackRate->mPitch);
                if (track.setPlaybackRate(*playbackRate)) {
                    ALOGV("setParameter(TIMESTRETCH, PLAYBACK_RATE, STRETCH_MODE, FALLBACK_MODE "
                            "%f %f %d %d",
                            playbackRate->mSpeed,
                            playbackRate->mPitch,
                            playbackRate->mStretchMode,
                            playbackRate->mFallbackMode);
                    // invalidateState(1 << name);
                }
            } break;
            default:
                LOG_ALWAYS_FATAL("setParameter timestretch: bad param %d", param);
            }
            break;
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    case DRC:
        switch (param) {
        case DEVICE:
            track.setDRCHandler(valueInt, mSampleRate);
            break;
        case UPDATE:
            track.updateDRCParam(mSampleRate);
            break;
        case RESET:
            track.resetDRC();
            break;
        default:
            LOG_FATAL("bad param");
        }
        break;
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    default:
        LOG_ALWAYS_FATAL("setParameter: bad target %d", target);
    }
}

bool AudioMixer::track_t::setResampler(uint32_t trackSampleRate, uint32_t devSampleRate)
{
#if defined(MTK_AUDIO_DEBUG)
    mDevSampleRate = devSampleRate;
#endif
    if (trackSampleRate != devSampleRate || resampler != NULL) {
        if (sampleRate != trackSampleRate) {
            sampleRate = trackSampleRate;
            if (resampler == NULL) {
                ALOGV("Creating resampler from track %d Hz to device %d Hz",
                        trackSampleRate, devSampleRate);
                AudioResampler::src_quality quality;
                // force lowest quality level resampler if use case isn't music or video
                // FIXME this is flawed for dynamic sample rates, as we choose the resampler
                // quality level based on the initial ratio, but that could change later.
                // Should have a way to distinguish tracks with static ratios vs. dynamic ratios.
                if (isMusicRate(trackSampleRate)) {
                    quality = AudioResampler::DEFAULT_QUALITY;
                } else {
                    quality = AudioResampler::DYN_LOW_QUALITY;
                }
#if defined(MTK_AUDIO_FIX_DEFAULT_DEFECT)
                quality = AudioResampler::MTK_DYN_HIGH_QUALITY; // Fixed to high quality
#endif // MTK_AUDIO_FIX_DEFAULT_DEFECT
                // TODO: Remove MONO_HACK. Resampler sees #channels after the downmixer
                // but if none exists, it is the channel count (1 for mono).
                const int resamplerChannelCount = downmixerBufferProvider != NULL
                        ? mMixerChannelCount : channelCount;
                ALOGVV("Creating resampler:"
                        " format(%#x) channels(%d) devSampleRate(%u) quality(%d)\n",
                        mMixerInFormat, resamplerChannelCount, devSampleRate, quality);
                resampler = AudioResampler::create(
                        mMixerInFormat,
                        resamplerChannelCount,
                        devSampleRate, quality);
            }
            return true;
        }
    }
    return false;
}

bool AudioMixer::track_t::setPlaybackRate(const AudioPlaybackRate &playbackRate)
{
    if ((mTimestretchBufferProvider == NULL &&
            fabs(playbackRate.mSpeed - mPlaybackRate.mSpeed) < AUDIO_TIMESTRETCH_SPEED_MIN_DELTA &&
            fabs(playbackRate.mPitch - mPlaybackRate.mPitch) < AUDIO_TIMESTRETCH_PITCH_MIN_DELTA) ||
            isAudioPlaybackRateEqual(playbackRate, mPlaybackRate)) {
        return false;
    }
    mPlaybackRate = playbackRate;
    if (mTimestretchBufferProvider == NULL) {
        // TODO: Remove MONO_HACK. Resampler sees #channels after the downmixer
        // but if none exists, it is the channel count (1 for mono).
        const int timestretchChannelCount = downmixerBufferProvider != NULL
                ? mMixerChannelCount : channelCount;
        mTimestretchBufferProvider = new TimestretchBufferProvider(timestretchChannelCount,
                mMixerInFormat, sampleRate, playbackRate);
        reconfigureBufferProviders();
    } else {
        reinterpret_cast<TimestretchBufferProvider*>(mTimestretchBufferProvider)
                ->setPlaybackRate(playbackRate);
    }
    return true;
}

/* Checks to see if the volume ramp has completed and clears the increment
 * variables appropriately.
 *
 * FIXME: There is code to handle int/float ramp variable switchover should it not
 * complete within a mixer buffer processing call, but it is preferred to avoid switchover
 * due to precision issues.  The switchover code is included for legacy code purposes
 * and can be removed once the integer volume is removed.
 *
 * It is not sufficient to clear only the volumeInc integer variable because
 * if one channel requires ramping, all channels are ramped.
 *
 * There is a bit of duplicated code here, but it keeps backward compatibility.
 */
inline void AudioMixer::track_t::adjustVolumeRamp(bool aux, bool useFloat)
{
    if (useFloat) {
        for (uint32_t i = 0; i < MAX_NUM_VOLUMES; i++) {
            if ((mVolumeInc[i] > 0 && mPrevVolume[i] + mVolumeInc[i] >= mVolume[i]) ||
                     (mVolumeInc[i] < 0 && mPrevVolume[i] + mVolumeInc[i] <= mVolume[i])) {
                volumeInc[i] = 0;
                prevVolume[i] = volume[i] << 16;
                mVolumeInc[i] = 0.;
                mPrevVolume[i] = mVolume[i];
            } else {
                //ALOGV("ramp: %f %f %f", mVolume[i], mPrevVolume[i], mVolumeInc[i]);
                prevVolume[i] = u4_28_from_float(mPrevVolume[i]);
            }
        }
    } else {
        for (uint32_t i = 0; i < MAX_NUM_VOLUMES; i++) {
            if (((volumeInc[i]>0) && (((prevVolume[i]+volumeInc[i])>>16) >= volume[i])) ||
                    ((volumeInc[i]<0) && (((prevVolume[i]+volumeInc[i])>>16) <= volume[i]))) {
                volumeInc[i] = 0;
                prevVolume[i] = volume[i] << 16;
                mVolumeInc[i] = 0.;
                mPrevVolume[i] = mVolume[i];
            } else {
                //ALOGV("ramp: %d %d %d", volume[i] << 16, prevVolume[i], volumeInc[i]);
                mPrevVolume[i]  = float_from_u4_28(prevVolume[i]);
            }
        }
    }
    /* TODO: aux is always integer regardless of output buffer type */
    if (aux) {
        if (((auxInc>0) && (((prevAuxLevel+auxInc)>>16) >= auxLevel)) ||
                ((auxInc<0) && (((prevAuxLevel+auxInc)>>16) <= auxLevel))) {
            auxInc = 0;
            prevAuxLevel = auxLevel << 16;
            mAuxInc = 0.;
            mPrevAuxLevel = mAuxLevel;
        } else {
            //ALOGV("aux ramp: %d %d %d", auxLevel << 16, prevAuxLevel, auxInc);
        }
    }
}

size_t AudioMixer::getUnreleasedFrames(int name) const
{
    name -= TRACK0;
    if (uint32_t(name) < MAX_NUM_TRACKS) {
        return mState.tracks[name].getUnreleasedFrames();
    }
    return 0;
}

void AudioMixer::setBufferProvider(int name, AudioBufferProvider* bufferProvider)
{
    name -= TRACK0;
    ALOG_ASSERT(uint32_t(name) < MAX_NUM_TRACKS, "bad track name %d", name);

    if (mState.tracks[name].mInputBufferProvider == bufferProvider) {
        return; // don't reset any buffer providers if identical.
    }
    if (mState.tracks[name].mReformatBufferProvider != NULL) {
        mState.tracks[name].mReformatBufferProvider->reset();
    } else if (mState.tracks[name].downmixerBufferProvider != NULL) {
        mState.tracks[name].downmixerBufferProvider->reset();
    } else if (mState.tracks[name].mPostDownmixReformatBufferProvider != NULL) {
        mState.tracks[name].mPostDownmixReformatBufferProvider->reset();
    } else if (mState.tracks[name].mTimestretchBufferProvider != NULL) {
        mState.tracks[name].mTimestretchBufferProvider->reset();
    }

    mState.tracks[name].mInputBufferProvider = bufferProvider;
    mState.tracks[name].reconfigureBufferProviders();
}


void AudioMixer::process()
{
    mState.hook(&mState);
}


void AudioMixer::process__validate(state_t* state)
{
    ALOGW_IF(!state->needsChanged,
        "in process__validate() but nothing's invalid");

    uint32_t changed = state->needsChanged;
    state->needsChanged = 0; // clear the validation flag

    // recompute which tracks are enabled / disabled
    uint32_t enabled = 0;
    uint32_t disabled = 0;
    while (changed) {
        const int i = 31 - __builtin_clz(changed);
        const uint32_t mask = 1<<i;
        changed &= ~mask;
        track_t& t = state->tracks[i];
        (t.enabled ? enabled : disabled) |= mask;
    }
    state->enabledTracks &= ~disabled;
    state->enabledTracks |=  enabled;

    // compute everything we need...
    int countActiveTracks = 0;
    // TODO: fix all16BitsStereNoResample logic to
    // either properly handle muted tracks (it should ignore them)
    // or remove altogether as an obsolete optimization.
    bool all16BitsStereoNoResample = true;
    bool resampling = false;
    bool volumeRamp = false;
    uint32_t en = state->enabledTracks;
    while (en) {
        const int i = 31 - __builtin_clz(en);
        en &= ~(1<<i);

        countActiveTracks++;
        track_t& t = state->tracks[i];
        uint32_t n = 0;
        // FIXME can overflow (mask is only 3 bits)
        n |= NEEDS_CHANNEL_1 + t.channelCount - 1;
        if (t.doesResample()) {
            n |= NEEDS_RESAMPLE;
        }
        if (t.auxLevel != 0 && t.auxBuffer != NULL) {
            n |= NEEDS_AUX;
        }

        if (t.volumeInc[0]|t.volumeInc[1]) {
            volumeRamp = true;
        } else if (!t.doesResample() && t.volumeRL == 0) {
            n |= NEEDS_MUTE;
        }
        t.needs = n;

        if (n & NEEDS_MUTE) {
            t.hook = track__nop;
        } else {
            if (n & NEEDS_AUX) {
                all16BitsStereoNoResample = false;
            }
            if (n & NEEDS_RESAMPLE) {
                all16BitsStereoNoResample = false;
                resampling = true;
                t.hook = getTrackHook(TRACKTYPE_RESAMPLE, t.mMixerChannelCount,
                        t.mMixerInFormat, t.mMixerFormat);
                ALOGV_IF((n & NEEDS_CHANNEL_COUNT__MASK) > NEEDS_CHANNEL_2,
                        "Track %d needs downmix + resample", i);
            } else {
                if ((n & NEEDS_CHANNEL_COUNT__MASK) == NEEDS_CHANNEL_1){
                    t.hook = getTrackHook(
                            (t.mMixerChannelMask == AUDIO_CHANNEL_OUT_STEREO  // TODO: MONO_HACK
                                    && t.channelMask == AUDIO_CHANNEL_OUT_MONO)
                                ? TRACKTYPE_NORESAMPLEMONO : TRACKTYPE_NORESAMPLE,
                            t.mMixerChannelCount,
                            t.mMixerInFormat, t.mMixerFormat);
                    all16BitsStereoNoResample = false;
                }
                if ((n & NEEDS_CHANNEL_COUNT__MASK) >= NEEDS_CHANNEL_2){
                    t.hook = getTrackHook(TRACKTYPE_NORESAMPLE, t.mMixerChannelCount,
                            t.mMixerInFormat, t.mMixerFormat);
                    ALOGV_IF((n & NEEDS_CHANNEL_COUNT__MASK) > NEEDS_CHANNEL_2,
                            "Track %d needs downmix", i);
                }
            }
        }
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
        resampling = true;
        all16BitsStereoNoResample = false;
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    }

    // select the processing hooks
    state->hook = process__nop;
    if (countActiveTracks > 0) {
        if (resampling) {
            if (!state->outputTemp) {
                state->outputTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            if (!state->resampleTemp) {
                state->resampleTemp = new int32_t[MAX_NUM_CHANNELS * state->frameCount];
            }
            state->hook = process__genericResampling;
        } else {
#if !defined(MTK_AUDIOMIXER_ENABLE_DRC)
// Use MTK_AUDIOMIXER_ENABLE_DRC will not enter here
            if (state->outputTemp) {
                delete [] state->outputTemp;
                state->outputTemp = NULL;
            }
            if (state->resampleTemp) {
                delete [] state->resampleTemp;
                state->resampleTemp = NULL;
            }
            state->hook = process__genericNoResampling;
            if (all16BitsStereoNoResample && !volumeRamp) {
                if (countActiveTracks == 1) {
                    const int i = 31 - __builtin_clz(state->enabledTracks);
                    track_t& t = state->tracks[i];
                    if ((t.needs & NEEDS_MUTE) == 0) {
                        // The check prevents a muted track from acquiring a process hook.
                        //
                        // This is dangerous if the track is MONO as that requires
                        // special case handling due to implicit channel duplication.
                        // Stereo or Multichannel should actually be fine here.
                        state->hook = getProcessHook(PROCESSTYPE_NORESAMPLEONETRACK,
                                t.mMixerChannelCount, t.mMixerInFormat, t.mMixerFormat);
                    }
                }
            }
#endif // MTK_AUDIOMIXER_ENABLE_DRC
        }
    }

    ALOGV("mixer configuration change: %d activeTracks (%08x) "
        "all16BitsStereoNoResample=%d, resampling=%d, volumeRamp=%d",
        countActiveTracks, state->enabledTracks,
        all16BitsStereoNoResample, resampling, volumeRamp);

   state->hook(state);

    // Now that the volume ramp has been done, set optimal state and
    // track hooks for subsequent mixer process
    if (countActiveTracks > 0) {
        bool allMuted = true;
        uint32_t en = state->enabledTracks;
        while (en) {
            const int i = 31 - __builtin_clz(en);
            en &= ~(1<<i);
            track_t& t = state->tracks[i];
            if (!t.doesResample() && t.volumeRL == 0) {
                t.needs |= NEEDS_MUTE;
                t.hook = track__nop;
            } else {
                allMuted = false;
            }
        }
        if (allMuted) {
            state->hook = process__nop;
        }
#if !defined(MTK_AUDIOMIXER_ENABLE_DRC)
        // Use MTK_AUDIOMIXER_ENABLE_DRC will not enter here
        else if (all16BitsStereoNoResample) {
            if (countActiveTracks == 1) {
                const int i = 31 - __builtin_clz(state->enabledTracks);
                track_t& t = state->tracks[i];
                // Muted single tracks handled by allMuted above.
                state->hook = getProcessHook(PROCESSTYPE_NORESAMPLEONETRACK,
                        t.mMixerChannelCount, t.mMixerInFormat, t.mMixerFormat);
            }
        }
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    }
}


void AudioMixer::track__genericResample(track_t* t, int32_t* out, size_t outFrameCount,
        int32_t* temp, int32_t* aux)
{
    ALOGVV("track__genericResample\n");
    t->resampler->setSampleRate(t->sampleRate);

    // ramp gain - resample to temp buffer and scale/mix in 2nd step
    if (aux != NULL) {
        // always resample with unity gain when sending to auxiliary buffer to be able
        // to apply send level after resampling
        t->resampler->setVolume(UNITY_GAIN_FLOAT, UNITY_GAIN_FLOAT);
        memset(temp, 0, outFrameCount * t->mMixerChannelCount * sizeof(int32_t));
        t->resampler->resample(temp, outFrameCount, t->bufferProvider);
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc)) {
            volumeRampStereo(t, out, outFrameCount, temp, aux);
        } else {
            volumeStereo(t, out, outFrameCount, temp, aux);
        }
    } else {
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1])) {
            t->resampler->setVolume(UNITY_GAIN_FLOAT, UNITY_GAIN_FLOAT);
            memset(temp, 0, outFrameCount * MAX_NUM_CHANNELS * sizeof(int32_t));
            t->resampler->resample(temp, outFrameCount, t->bufferProvider);
            volumeRampStereo(t, out, outFrameCount, temp, aux);
        }

        // constant gain
        else {
            t->resampler->setVolume(t->mVolume[0], t->mVolume[1]);
            t->resampler->resample(out, outFrameCount, t->bufferProvider);
        }
    }
}

void AudioMixer::track__nop(track_t* t __unused, int32_t* out __unused,
        size_t outFrameCount __unused, int32_t* temp __unused, int32_t* aux __unused)
{
}

void AudioMixer::volumeRampStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp,
        int32_t* aux)
{
    int32_t vl = t->prevVolume[0];
    int32_t vr = t->prevVolume[1];
    const int32_t vlInc = t->volumeInc[0];
    const int32_t vrInc = t->volumeInc[1];

    //ALOGD("[0] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
    //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
    //       (vl + vlInc*frameCount)/65536.0f, frameCount);

    // ramp volume
    if (CC_UNLIKELY(aux != NULL)) {
        int32_t va = t->prevAuxLevel;
        const int32_t vaInc = t->auxInc;
        int32_t l;
        int32_t r;

        do {
            l = (*temp++ >> 12);
            r = (*temp++ >> 12);
            *out++ += (vl >> 16) * l;
            *out++ += (vr >> 16) * r;
            *aux++ += (va >> 17) * (l + r);
            vl += vlInc;
            vr += vrInc;
            va += vaInc;
        } while (--frameCount);
        t->prevAuxLevel = va;
    } else {
        do {
            *out++ += (vl >> 16) * (*temp++ >> 12);
            *out++ += (vr >> 16) * (*temp++ >> 12);
            vl += vlInc;
            vr += vrInc;
        } while (--frameCount);
    }
    t->prevVolume[0] = vl;
    t->prevVolume[1] = vr;
    t->adjustVolumeRamp(aux != NULL);
}

void AudioMixer::volumeStereo(track_t* t, int32_t* out, size_t frameCount, int32_t* temp,
        int32_t* aux)
{
    const int16_t vl = t->volume[0];
    const int16_t vr = t->volume[1];

    if (CC_UNLIKELY(aux != NULL)) {
        const int16_t va = t->auxLevel;
        do {
            int16_t l = (int16_t)(*temp++ >> 12);
            int16_t r = (int16_t)(*temp++ >> 12);
            out[0] = mulAdd(l, vl, out[0]);
            int16_t a = (int16_t)(((int32_t)l + r) >> 1);
            out[1] = mulAdd(r, vr, out[1]);
            out += 2;
            aux[0] = mulAdd(a, va, aux[0]);
            aux++;
        } while (--frameCount);
    } else {
        do {
            int16_t l = (int16_t)(*temp++ >> 12);
            int16_t r = (int16_t)(*temp++ >> 12);
            out[0] = mulAdd(l, vl, out[0]);
            out[1] = mulAdd(r, vr, out[1]);
            out += 2;
        } while (--frameCount);
    }
}

void AudioMixer::track__16BitsStereo(track_t* t, int32_t* out, size_t frameCount,
        int32_t* temp __unused, int32_t* aux)
{
    ALOGVV("track__16BitsStereo\n");
    const int16_t *in = static_cast<const int16_t *>(t->in);

    if (CC_UNLIKELY(aux != NULL)) {
        int32_t l;
        int32_t r;
        // ramp gain
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc)) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            int32_t va = t->prevAuxLevel;
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];
            const int32_t vaInc = t->auxInc;
            // ALOGD("[1] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //        (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                l = (int32_t)*in++;
                r = (int32_t)*in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * r;
                *aux++ += (va >> 17) * (l + r);
                vl += vlInc;
                vr += vrInc;
                va += vaInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->prevAuxLevel = va;
            t->adjustVolumeRamp(true);
        }

        // constant gain
        else {
            const uint32_t vrl = t->volumeRL;
            const int16_t va = (int16_t)t->auxLevel;
            do {
                uint32_t rl = *reinterpret_cast<const uint32_t *>(in);
                int16_t a = (int16_t)(((int32_t)in[0] + in[1]) >> 1);
                in += 2;
                out[0] = mulAddRL(1, rl, vrl, out[0]);
                out[1] = mulAddRL(0, rl, vrl, out[1]);
                out += 2;
                aux[0] = mulAdd(a, va, aux[0]);
                aux++;
            } while (--frameCount);
        }
    } else {
        // ramp gain
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1])) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];

            // ALOGD("[1] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //        t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //        (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                *out++ += (vl >> 16) * (int32_t) *in++;
                *out++ += (vr >> 16) * (int32_t) *in++;
                vl += vlInc;
                vr += vrInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->adjustVolumeRamp(false);
        }

        // constant gain
        else {
            const uint32_t vrl = t->volumeRL;
            do {
                uint32_t rl = *reinterpret_cast<const uint32_t *>(in);
                in += 2;
                out[0] = mulAddRL(1, rl, vrl, out[0]);
                out[1] = mulAddRL(0, rl, vrl, out[1]);
                out += 2;
            } while (--frameCount);
        }
    }
    t->in = in;
}

void AudioMixer::track__16BitsMono(track_t* t, int32_t* out, size_t frameCount,
        int32_t* temp __unused, int32_t* aux)
{
    ALOGVV("track__16BitsMono\n");
    const int16_t *in = static_cast<int16_t const *>(t->in);

    if (CC_UNLIKELY(aux != NULL)) {
        // ramp gain
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1]|t->auxInc)) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            int32_t va = t->prevAuxLevel;
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];
            const int32_t vaInc = t->auxInc;

            // ALOGD("[2] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //         t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //         (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                int32_t l = *in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * l;
                *aux++ += (va >> 16) * l;
                vl += vlInc;
                vr += vrInc;
                va += vaInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->prevAuxLevel = va;
            t->adjustVolumeRamp(true);
        }
        // constant gain
        else {
            const int16_t vl = t->volume[0];
            const int16_t vr = t->volume[1];
            const int16_t va = (int16_t)t->auxLevel;
            do {
                int16_t l = *in++;
                out[0] = mulAdd(l, vl, out[0]);
                out[1] = mulAdd(l, vr, out[1]);
                out += 2;
                aux[0] = mulAdd(l, va, aux[0]);
                aux++;
            } while (--frameCount);
        }
    } else {
        // ramp gain
        if (CC_UNLIKELY(t->volumeInc[0]|t->volumeInc[1])) {
            int32_t vl = t->prevVolume[0];
            int32_t vr = t->prevVolume[1];
            const int32_t vlInc = t->volumeInc[0];
            const int32_t vrInc = t->volumeInc[1];

            // ALOGD("[2] %p: inc=%f, v0=%f, v1=%d, final=%f, count=%d",
            //         t, vlInc/65536.0f, vl/65536.0f, t->volume[0],
            //         (vl + vlInc*frameCount)/65536.0f, frameCount);

            do {
                int32_t l = *in++;
                *out++ += (vl >> 16) * l;
                *out++ += (vr >> 16) * l;
                vl += vlInc;
                vr += vrInc;
            } while (--frameCount);

            t->prevVolume[0] = vl;
            t->prevVolume[1] = vr;
            t->adjustVolumeRamp(false);
        }
        // constant gain
        else {
            const int16_t vl = t->volume[0];
            const int16_t vr = t->volume[1];
            do {
                int16_t l = *in++;
                out[0] = mulAdd(l, vl, out[0]);
                out[1] = mulAdd(l, vr, out[1]);
                out += 2;
            } while (--frameCount);
        }
    }
    t->in = in;
}

// no-op case
void AudioMixer::process__nop(state_t* state)
{
    ALOGVV("process__nop\n");
    uint32_t e0 = state->enabledTracks;
    while (e0) {
        // process by group of tracks with same output buffer to
        // avoid multiple memset() on same buffer
        uint32_t e1 = e0, e2 = e0;
        int i = 31 - __builtin_clz(e1);
        {
            track_t& t1 = state->tracks[i];
            e2 &= ~(1<<i);
            while (e2) {
                i = 31 - __builtin_clz(e2);
                e2 &= ~(1<<i);
                track_t& t2 = state->tracks[i];
                if (CC_UNLIKELY(t2.mainBuffer != t1.mainBuffer)) {
                    e1 &= ~(1<<i);
                }
            }
            e0 &= ~(e1);

            memset(t1.mainBuffer, 0, state->frameCount * t1.mMixerChannelCount
                    * audio_bytes_per_sample(t1.mMixerFormat));
        }

        while (e1) {
            i = 31 - __builtin_clz(e1);
            e1 &= ~(1<<i);
            {
                track_t& t3 = state->tracks[i];
                size_t outFrames = state->frameCount;
                while (outFrames) {
                    t3.buffer.frameCount = outFrames;
                    t3.bufferProvider->getNextBuffer(&t3.buffer);
                    if (t3.buffer.raw == NULL) break;
                    outFrames -= t3.buffer.frameCount;
                    t3.bufferProvider->releaseBuffer(&t3.buffer);
                }
            }
        }
    }
}

// generic code without resampling
void AudioMixer::process__genericNoResampling(state_t* state)
{
    ALOGVV("process__genericNoResampling\n");
    int32_t outTemp[BLOCKSIZE * MAX_NUM_CHANNELS] __attribute__((aligned(32)));

    // acquire each track's buffer
    uint32_t enabledTracks = state->enabledTracks;
    uint32_t e0 = enabledTracks;
    while (e0) {
        const int i = 31 - __builtin_clz(e0);
        e0 &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.buffer.frameCount = state->frameCount;
        t.bufferProvider->getNextBuffer(&t.buffer);
        t.frameCount = t.buffer.frameCount;
        t.in = t.buffer.raw;
    }

    e0 = enabledTracks;
    while (e0) {
        // process by group of tracks with same output buffer to
        // optimize cache use
        uint32_t e1 = e0, e2 = e0;
        int j = 31 - __builtin_clz(e1);
        track_t& t1 = state->tracks[j];
        e2 &= ~(1<<j);
        while (e2) {
            j = 31 - __builtin_clz(e2);
            e2 &= ~(1<<j);
            track_t& t2 = state->tracks[j];
            if (CC_UNLIKELY(t2.mainBuffer != t1.mainBuffer)) {
                e1 &= ~(1<<j);
            }
        }
        e0 &= ~(e1);
        // this assumes output 16 bits stereo, no resampling
        int32_t *out = t1.mainBuffer;
        size_t numFrames = 0;
        do {
            memset(outTemp, 0, sizeof(outTemp));
            e2 = e1;
            while (e2) {
                const int i = 31 - __builtin_clz(e2);
                e2 &= ~(1<<i);
                track_t& t = state->tracks[i];
                size_t outFrames = BLOCKSIZE;
                int32_t *aux = NULL;
                if (CC_UNLIKELY(t.needs & NEEDS_AUX)) {
                    aux = t.auxBuffer + numFrames;
                }
                while (outFrames) {
                    // t.in == NULL can happen if the track was flushed just after having
                    // been enabled for mixing.
                   if (t.in == NULL) {
                        enabledTracks &= ~(1<<i);
                        e1 &= ~(1<<i);
                        break;
                    }
                    size_t inFrames = (t.frameCount > outFrames)?outFrames:t.frameCount;
                    if (inFrames > 0) {
                        t.hook(&t, outTemp + (BLOCKSIZE - outFrames) * t.mMixerChannelCount,
                                inFrames, state->resampleTemp, aux);
                        t.frameCount -= inFrames;
                        outFrames -= inFrames;
                        if (CC_UNLIKELY(aux != NULL)) {
                            aux += inFrames;
                        }
                    }
                    if (t.frameCount == 0 && outFrames) {
                        t.bufferProvider->releaseBuffer(&t.buffer);
                        t.buffer.frameCount = (state->frameCount - numFrames) -
                                (BLOCKSIZE - outFrames);
                        t.bufferProvider->getNextBuffer(&t.buffer);
                        t.in = t.buffer.raw;
                        if (t.in == NULL) {
                            enabledTracks &= ~(1<<i);
                            e1 &= ~(1<<i);
                            break;
                        }
                        t.frameCount = t.buffer.frameCount;
                    }
                }
            }

            convertMixerFormat(out, t1.mMixerFormat, outTemp, t1.mMixerInFormat,
                    BLOCKSIZE * t1.mMixerChannelCount);
#if defined(MTK_AUDIO_DEBUG)
            if (AudioDump::getProperty(AudioParameter::PROP_AUDIO_DUMP_MIXEREND)) {
                String8 fileName = String8::format("%s_pid%d_tid%d.pcm", AudioParameter::af_mixer_end_pcm,
                    getpid(), gettid());
                AudioDump::threadDump(fileName, out,
                    BLOCKSIZE * t1.mMixerChannelCount * audio_bytes_per_sample(t1.mMixerFormat),
                    t1.mMixerFormat, t1.sampleRate, t1.mMixerChannelCount);
            }
#endif // MTK_AUDIO_DEBUG

            // TODO: fix ugly casting due to choice of out pointer type
            out = reinterpret_cast<int32_t*>((uint8_t*)out
                    + BLOCKSIZE * t1.mMixerChannelCount
                        * audio_bytes_per_sample(t1.mMixerFormat));
            numFrames += BLOCKSIZE;
        } while (numFrames < state->frameCount);
    }

    // release each track's buffer
    e0 = enabledTracks;
    while (e0) {
        const int i = 31 - __builtin_clz(e0);
        e0 &= ~(1<<i);
        track_t& t = state->tracks[i];
        t.bufferProvider->releaseBuffer(&t.buffer);
    }
}


// generic code with resampling
void AudioMixer::process__genericResampling(state_t* state)
{
    ALOGVV("process__genericResampling\n");
    // this const just means that local variable outTemp doesn't change
    int32_t* const outTemp = state->outputTemp;
    size_t numFrames = state->frameCount;

    uint32_t e0 = state->enabledTracks;
    while (e0) {
        // process by group of tracks with same output buffer
        // to optimize cache use
        uint32_t e1 = e0, e2 = e0;
        int j = 31 - __builtin_clz(e1);
        track_t& t1 = state->tracks[j];
        e2 &= ~(1<<j);
        while (e2) {
            j = 31 - __builtin_clz(e2);
            e2 &= ~(1<<j);
            track_t& t2 = state->tracks[j];
            if (CC_UNLIKELY(t2.mainBuffer != t1.mainBuffer)) {
                e1 &= ~(1<<j);
            }
        }
        e0 &= ~(e1);
        int32_t *out = t1.mainBuffer;
        memset(outTemp, 0, sizeof(*outTemp) * t1.mMixerChannelCount * state->frameCount);
        while (e1) {
            const int i = 31 - __builtin_clz(e1);
            e1 &= ~(1<<i);
            track_t& t = state->tracks[i];
            int32_t *aux = NULL;
            if (CC_UNLIKELY(t.needs & NEEDS_AUX)) {
                aux = t.auxBuffer;
            }

            // this is a little goofy, on the resampling case we don't
            // acquire/release the buffers because it's done by
            // the resampler.
            if (t.needs & NEEDS_RESAMPLE) {
                t.hook(&t, outTemp, numFrames, state->resampleTemp, aux);
            } else {

                size_t outFrames = 0;

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
                int8_t *tempBuffer = reinterpret_cast<int8_t*>(state->nonResampleTemp);
                int channelCount = ((1 == t.channelCount) && (2 == t.mMixerChannelCount)) ? 1 : t.mMixerChannelCount;
                int32_t channelSize = channelCount * audio_bytes_per_sample(t.mMixerInFormat);
                memset(tempBuffer, 0, numFrames * channelSize);
#endif // MTK_AUDIOMIXER_ENABLE_DRC
                while (outFrames < numFrames) {
                    t.buffer.frameCount = numFrames - outFrames;
                    t.bufferProvider->getNextBuffer(&t.buffer);
                    t.in = t.buffer.raw;
                    // t.in == NULL can happen if the track was flushed just after having
                    // been enabled for mixing.
                    if (t.in == NULL) break;
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
                    int32_t sampleSize = t.buffer.frameCount * channelSize;
                    memcpy(tempBuffer+ outFrames * channelSize, t.in, sampleSize);
#else
                    if (CC_UNLIKELY(aux != NULL)) {
                        aux += outFrames;
                    }
                    t.hook(&t, outTemp + outFrames * t.mMixerChannelCount, t.buffer.frameCount,
                            state->resampleTemp, aux);
#endif // MTK_AUDIOMIXER_ENABLE_DRC
                    outFrames += t.buffer.frameCount;
                    t.bufferProvider->releaseBuffer(&t.buffer);
                }
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
                t.hook(&t, outTemp, numFrames, state->nonResampleTemp, aux);
                if (CC_UNLIKELY(aux != NULL)) {
                    aux += numFrames;
                }
#endif // MTK_AUDIOMIXER_ENABLE_DRC
            }
        }
        convertMixerFormat(out, t1.mMixerFormat,
                outTemp, t1.mMixerInFormat, numFrames * t1.mMixerChannelCount);
#if defined(MTK_AUDIO_DEBUG)
        if (AudioDump::getProperty(AudioParameter::PROP_AUDIO_DUMP_MIXEREND)) {
            String8 fileName = String8::format("%s_pid%d_tid%d.pcm", AudioParameter::af_mixer_end_pcm,
                getpid(), gettid());
            AudioDump::threadDump(fileName, out,
                numFrames * t1.mMixerChannelCount * audio_bytes_per_sample(t1.mMixerFormat),
                t1.mMixerFormat, t1.mDevSampleRate, t1.mMixerChannelCount);
        }
#endif // MTK_AUDIO_DEBUG
    }
}

// one track, 16 bits stereo without resampling is the most common case
void AudioMixer::process__OneTrack16BitsStereoNoResampling(state_t* state)
{
    ALOGVV("process__OneTrack16BitsStereoNoResampling\n");
    // This method is only called when state->enabledTracks has exactly
    // one bit set.  The asserts below would verify this, but are commented out
    // since the whole point of this method is to optimize performance.
    //ALOG_ASSERT(0 != state->enabledTracks, "no tracks enabled");
    const int i = 31 - __builtin_clz(state->enabledTracks);
    //ALOG_ASSERT((1 << i) == state->enabledTracks, "more than 1 track enabled");
    const track_t& t = state->tracks[i];

    AudioBufferProvider::Buffer& b(t.buffer);

    int32_t* out = t.mainBuffer;
    float *fout = reinterpret_cast<float*>(out);
    size_t numFrames = state->frameCount;

    const int16_t vl = t.volume[0];
    const int16_t vr = t.volume[1];
    const uint32_t vrl = t.volumeRL;
    while (numFrames) {
        b.frameCount = numFrames;
        t.bufferProvider->getNextBuffer(&b);
        const int16_t *in = b.i16;

        // in == NULL can happen if the track was flushed just after having
        // been enabled for mixing.
        if (in == NULL || (((uintptr_t)in) & 3)) {
            if ( AUDIO_FORMAT_PCM_FLOAT == t.mMixerFormat ) {
                 memset((char*)fout, 0, numFrames
                         * t.mMixerChannelCount * audio_bytes_per_sample(t.mMixerFormat));
            } else {
                 memset((char*)out, 0, numFrames
                         * t.mMixerChannelCount * audio_bytes_per_sample(t.mMixerFormat));
            }
            ALOGE_IF((((uintptr_t)in) & 3),
                    "process__OneTrack16BitsStereoNoResampling: misaligned buffer"
                    " %p track %d, channels %d, needs %08x, volume %08x vfl %f vfr %f",
                    in, i, t.channelCount, t.needs, vrl, t.mVolume[0], t.mVolume[1]);
            return;
        }
        size_t outFrames = b.frameCount;

        switch (t.mMixerFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            do {
                uint32_t rl = *reinterpret_cast<const uint32_t *>(in);
                in += 2;
                int32_t l = mulRL(1, rl, vrl);
                int32_t r = mulRL(0, rl, vrl);
                *fout++ = float_from_q4_27(l);
                *fout++ = float_from_q4_27(r);
                // Note: In case of later int16_t sink output,
                // conversion and clamping is done by memcpy_to_i16_from_float().
            } while (--outFrames);
            break;
        case AUDIO_FORMAT_PCM_16_BIT:
            if (CC_UNLIKELY(uint32_t(vl) > UNITY_GAIN_INT || uint32_t(vr) > UNITY_GAIN_INT)) {
                // volume is boosted, so we might need to clamp even though
                // we process only one track.
                do {
                    uint32_t rl = *reinterpret_cast<const uint32_t *>(in);
                    in += 2;
                    int32_t l = mulRL(1, rl, vrl) >> 12;
                    int32_t r = mulRL(0, rl, vrl) >> 12;
                    // clamping...
                    l = clamp16(l);
                    r = clamp16(r);
                    *out++ = (r<<16) | (l & 0xFFFF);
                } while (--outFrames);
            } else {
                do {
                    uint32_t rl = *reinterpret_cast<const uint32_t *>(in);
                    in += 2;
                    int32_t l = mulRL(1, rl, vrl) >> 12;
                    int32_t r = mulRL(0, rl, vrl) >> 12;
                    *out++ = (r<<16) | (l & 0xFFFF);
                } while (--outFrames);
            }
            break;
        default:
            LOG_ALWAYS_FATAL("bad mixer format: %d", t.mMixerFormat);
        }
        numFrames -= b.frameCount;
        t.bufferProvider->releaseBuffer(&b);
    }
}

/*static*/ pthread_once_t AudioMixer::sOnceControl = PTHREAD_ONCE_INIT;

/*static*/ void AudioMixer::sInitRoutine()
{
    DownmixerBufferProvider::init(); // for the downmixer
}

/* TODO: consider whether this level of optimization is necessary.
 * Perhaps just stick with a single for loop.
 */

// Needs to derive a compile time constant (constexpr).  Could be targeted to go
// to a MONOVOL mixtype based on MAX_NUM_VOLUMES, but that's an unnecessary complication.
#define MIXTYPE_MONOVOL(mixtype) ((mixtype) == MIXTYPE_MULTI ? MIXTYPE_MULTI_MONOVOL : \
        (mixtype) == MIXTYPE_MULTI_SAVEONLY ? MIXTYPE_MULTI_SAVEONLY_MONOVOL : (mixtype))

/* MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE,
        typename TO, typename TI, typename TV, typename TA, typename TAV>
static void volumeRampMulti(uint32_t channels, TO* out, size_t frameCount,
        const TI* in, TA* aux, TV *vol, const TV *volinc, TAV *vola, TAV volainc)
{
    switch (channels) {
    case 1:
        volumeRampMulti<MIXTYPE, 1>(out, frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 2:
        volumeRampMulti<MIXTYPE, 2>(out, frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 3:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 3>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 4:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 4>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 5:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 5>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 6:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 6>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 7:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 7>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    case 8:
        volumeRampMulti<MIXTYPE_MONOVOL(MIXTYPE), 8>(out,
                frameCount, in, aux, vol, volinc, vola, volainc);
        break;
    }
}

/* MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE,
        typename TO, typename TI, typename TV, typename TA, typename TAV>
static void volumeMulti(uint32_t channels, TO* out, size_t frameCount,
        const TI* in, TA* aux, const TV *vol, TAV vola)
{
    switch (channels) {
    case 1:
        volumeMulti<MIXTYPE, 1>(out, frameCount, in, aux, vol, vola);
        break;
    case 2:
        volumeMulti<MIXTYPE, 2>(out, frameCount, in, aux, vol, vola);
        break;
    case 3:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 3>(out, frameCount, in, aux, vol, vola);
        break;
    case 4:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 4>(out, frameCount, in, aux, vol, vola);
        break;
    case 5:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 5>(out, frameCount, in, aux, vol, vola);
        break;
    case 6:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 6>(out, frameCount, in, aux, vol, vola);
        break;
    case 7:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 7>(out, frameCount, in, aux, vol, vola);
        break;
    case 8:
        volumeMulti<MIXTYPE_MONOVOL(MIXTYPE), 8>(out, frameCount, in, aux, vol, vola);
        break;
    }
}

/* MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * USEFLOATVOL (set to true if float volume is used)
 * ADJUSTVOL   (set to true if volume ramp parameters needs adjustment afterwards)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE, bool USEFLOATVOL, bool ADJUSTVOL,
    typename TO, typename TI, typename TA>
void AudioMixer::volumeMix(TO *out, size_t outFrames,
        const TI *in, TA *aux, bool ramp, AudioMixer::track_t *t)
{
    if (USEFLOATVOL) {
        if (ramp) {
            volumeRampMulti<MIXTYPE>(t->mMixerChannelCount, out, outFrames, in, aux,
                    t->mPrevVolume, t->mVolumeInc, &t->prevAuxLevel, t->auxInc);
            if (ADJUSTVOL) {
                t->adjustVolumeRamp(aux != NULL, true);
            }
        } else {
            volumeMulti<MIXTYPE>(t->mMixerChannelCount, out, outFrames, in, aux,
                    t->mVolume, t->auxLevel);
        }
    } else {
        if (ramp) {
            volumeRampMulti<MIXTYPE>(t->mMixerChannelCount, out, outFrames, in, aux,
                    t->prevVolume, t->volumeInc, &t->prevAuxLevel, t->auxInc);
            if (ADJUSTVOL) {
                t->adjustVolumeRamp(aux != NULL);
            }
        } else {
            volumeMulti<MIXTYPE>(t->mMixerChannelCount, out, outFrames, in, aux,
                    t->volume, t->auxLevel);
        }
    }
}

/* This process hook is called when there is a single track without
 * aux buffer, volume ramp, or resampling.
 * TODO: Update the hook selection: this can properly handle aux and ramp.
 *
 * MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE, typename TO, typename TI, typename TA>
void AudioMixer::process_NoResampleOneTrack(state_t* state)
{
    ALOGVV("process_NoResampleOneTrack\n");
    // CLZ is faster than CTZ on ARM, though really not sure if true after 31 - clz.
    const int i = 31 - __builtin_clz(state->enabledTracks);
    ALOG_ASSERT((1 << i) == state->enabledTracks, "more than 1 track enabled");
    track_t *t = &state->tracks[i];
    const uint32_t channels = t->mMixerChannelCount;
    TO* out = reinterpret_cast<TO*>(t->mainBuffer);
    TA* aux = reinterpret_cast<TA*>(t->auxBuffer);
    const bool ramp = t->needsRamp();

    for (size_t numFrames = state->frameCount; numFrames; ) {
        AudioBufferProvider::Buffer& b(t->buffer);
        // get input buffer
        b.frameCount = numFrames;
        t->bufferProvider->getNextBuffer(&b);
        const TI *in = reinterpret_cast<TI*>(b.raw);

        // in == NULL can happen if the track was flushed just after having
        // been enabled for mixing.
        if (in == NULL || (((uintptr_t)in) & 3)) {
            memset(out, 0, numFrames
                    * channels * audio_bytes_per_sample(t->mMixerFormat));
            ALOGE_IF((((uintptr_t)in) & 3), "process_NoResampleOneTrack: bus error: "
                    "buffer %p track %p, channels %d, needs %#x",
                    in, t, t->channelCount, t->needs);
            return;
        }

        const size_t outFrames = b.frameCount;
        volumeMix<MIXTYPE, is_same<TI, float>::value, false> (
                out, outFrames, in, aux, ramp, t);
#if defined(MTK_AUDIO_DEBUG)
        if (AudioDump::getProperty(AudioParameter::PROP_AUDIO_DUMP_MIXEREND)) {
            String8 fileName = String8::format("%s_pid%d_tid%d.pcm", AudioParameter::af_mixer_end_pcm,
                getpid(), gettid());
            AudioDump::threadDump(fileName, out,
                outFrames * t->mMixerChannelCount * audio_bytes_per_sample(t->mMixerFormat),
                t->mMixerFormat, t->sampleRate, t->mMixerChannelCount);
        }
#endif // MTK_AUDIO_DEBUG

        out += outFrames * channels;
        if (aux != NULL) {
            aux += channels;
        }
        numFrames -= b.frameCount;

        // release buffer
        t->bufferProvider->releaseBuffer(&b);
    }
    if (ramp) {
        t->adjustVolumeRamp(aux != NULL, is_same<TI, float>::value);
    }
}

/* This track hook is called to do resampling then mixing,
 * pulling from the track's upstream AudioBufferProvider.
 *
 * MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE, typename TO, typename TI, typename TA>
void AudioMixer::track__Resample(track_t* t, TO* out, size_t outFrameCount, TO* temp, TA* aux)
{
    ALOGVV("track__Resample\n");
    t->resampler->setSampleRate(t->sampleRate);
    const bool ramp = t->needsRamp();

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    if (1)
#else
    if (ramp || aux != NULL)
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    {
        // if ramp:        resample with unity gain to temp buffer and scale/mix in 2nd step.
        // if aux != NULL: resample with unity gain to temp buffer then apply send level.

        t->resampler->setVolume(UNITY_GAIN_FLOAT, UNITY_GAIN_FLOAT);
        memset(temp, 0, outFrameCount * t->mMixerChannelCount * sizeof(TO));
        t->resampler->resample((int32_t*)temp, outFrameCount, t->bufferProvider);

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
        t->volumeClamp(t, outFrameCount, &temp, true);
        t->doPostProcessing<MIXTYPE>(temp, (AUDIO_FORMAT_PCM_16_BIT == t->mMixerInFormat
            ? AUDIO_FORMAT_PCM_32_BIT : t->mMixerInFormat), outFrameCount);
        t->volumeClamp(t, outFrameCount, &temp, false);
#endif // MTK_AUDIOMIXER_ENABLE_DRC

        volumeMix<MIXTYPE, is_same<TI, float>::value, true>(
                out, outFrameCount, temp, aux, ramp, t);

    } else { // constant volume gain
        t->resampler->setVolume(t->mVolume[0], t->mVolume[1]);
        t->resampler->resample((int32_t*)out, outFrameCount, t->bufferProvider);
    }
}

/* This track hook is called to mix a track, when no resampling is required.
 * The input buffer should be present in t->in.
 *
 * MIXTYPE     (see AudioMixerOps.h MIXTYPE_* enumeration)
 * TO: int32_t (Q4.27) or float
 * TI: int32_t (Q4.27) or int16_t (Q0.15) or float
 * TA: int32_t (Q4.27)
 */
template <int MIXTYPE, typename TO, typename TI, typename TA>
void AudioMixer::track__NoResample(track_t* t, TO* out, size_t frameCount,
        TO* temp __unused, TA* aux)
{
    ALOGVV("track__NoResample\n");

#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    const TI *in = reinterpret_cast<const TI *>(temp);
    void *buffer = static_cast<void *>(temp);
    t->doPostProcessing<MIXTYPE>(buffer, t->mMixerInFormat, frameCount);
#else
    const TI *in = static_cast<const TI *>(t->in);
#endif // MTK_AUDIOMIXER_ENABLE_DRC

    volumeMix<MIXTYPE, is_same<TI, float>::value, true>(
            out, frameCount, in, aux, t->needsRamp(), t);

    // MIXTYPE_MONOEXPAND reads a single input channel and expands to NCHAN output channels.
    // MIXTYPE_MULTI reads NCHAN input channels and places to NCHAN output channels.
#if !defined(MTK_AUDIOMIXER_ENABLE_DRC)
    in += (MIXTYPE == MIXTYPE_MONOEXPAND) ? frameCount : frameCount * t->mMixerChannelCount;
    t->in = in;
#endif // MTK_AUDIOMIXER_ENABLE_DRC
}

/* The Mixer engine generates either int32_t (Q4_27) or float data.
 * We use this function to convert the engine buffers
 * to the desired mixer output format, either int16_t (Q.15) or float.
 */
void AudioMixer::convertMixerFormat(void *out, audio_format_t mixerOutFormat,
        void *in, audio_format_t mixerInFormat, size_t sampleCount)
{
    switch (mixerInFormat) {
    case AUDIO_FORMAT_PCM_FLOAT:
        switch (mixerOutFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            memcpy(out, in, sampleCount * sizeof(float)); // MEMCPY. TODO optimize out
            break;
        case AUDIO_FORMAT_PCM_16_BIT:
            memcpy_to_i16_from_float((int16_t*)out, (float*)in, sampleCount);
            break;
        default:
            LOG_ALWAYS_FATAL("bad mixerOutFormat: %#x", mixerOutFormat);
            break;
        }
        break;
    case AUDIO_FORMAT_PCM_16_BIT:
        switch (mixerOutFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            memcpy_to_float_from_q4_27((float*)out, (int32_t*)in, sampleCount);
            break;
        case AUDIO_FORMAT_PCM_16_BIT:
            // two int16_t are produced per iteration
            ditherAndClamp((int32_t*)out, (int32_t*)in, sampleCount >> 1);
            break;
        default:
            LOG_ALWAYS_FATAL("bad mixerOutFormat: %#x", mixerOutFormat);
            break;
        }
        break;
    default:
        LOG_ALWAYS_FATAL("bad mixerInFormat: %#x", mixerInFormat);
        break;
    }
}

/* Returns the proper track hook to use for mixing the track into the output buffer.
 */
AudioMixer::hook_t AudioMixer::getTrackHook(int trackType, uint32_t channelCount,
        audio_format_t mixerInFormat, audio_format_t mixerOutFormat __unused)
{
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    if (0)
#else
    if (!kUseNewMixer && channelCount == FCC_2 && mixerInFormat == AUDIO_FORMAT_PCM_16_BIT)
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    {
        switch (trackType) {
        case TRACKTYPE_NOP:
            return track__nop;
        case TRACKTYPE_RESAMPLE:
            return track__genericResample;
        case TRACKTYPE_NORESAMPLEMONO:
            return track__16BitsMono;
        case TRACKTYPE_NORESAMPLE:
            return track__16BitsStereo;
        default:
            LOG_ALWAYS_FATAL("bad trackType: %d", trackType);
            break;
        }
    }
    LOG_ALWAYS_FATAL_IF(channelCount > MAX_NUM_CHANNELS);
    switch (trackType) {
    case TRACKTYPE_NOP:
        return track__nop;
    case TRACKTYPE_RESAMPLE:
        switch (mixerInFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            return (AudioMixer::hook_t)
                    track__Resample<MIXTYPE_MULTI, float /*TO*/, float /*TI*/, int32_t /*TA*/>;
        case AUDIO_FORMAT_PCM_16_BIT:
            return (AudioMixer::hook_t)\
                    track__Resample<MIXTYPE_MULTI, int32_t, int16_t, int32_t>;
        default:
            LOG_ALWAYS_FATAL("bad mixerInFormat: %#x", mixerInFormat);
            break;
        }
        break;
    case TRACKTYPE_NORESAMPLEMONO:
        switch (mixerInFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            return (AudioMixer::hook_t)
                    track__NoResample<MIXTYPE_MONOEXPAND, float, float, int32_t>;
        case AUDIO_FORMAT_PCM_16_BIT:
            return (AudioMixer::hook_t)
                    track__NoResample<MIXTYPE_MONOEXPAND, int32_t, int16_t, int32_t>;
        default:
            LOG_ALWAYS_FATAL("bad mixerInFormat: %#x", mixerInFormat);
            break;
        }
        break;
    case TRACKTYPE_NORESAMPLE:
        switch (mixerInFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            return (AudioMixer::hook_t)
                    track__NoResample<MIXTYPE_MULTI, float, float, int32_t>;
        case AUDIO_FORMAT_PCM_16_BIT:
            return (AudioMixer::hook_t)
                    track__NoResample<MIXTYPE_MULTI, int32_t, int16_t, int32_t>;
        default:
            LOG_ALWAYS_FATAL("bad mixerInFormat: %#x", mixerInFormat);
            break;
        }
        break;
    default:
        LOG_ALWAYS_FATAL("bad trackType: %d", trackType);
        break;
    }
    return NULL;
}

/* Returns the proper process hook for mixing tracks. Currently works only for
 * PROCESSTYPE_NORESAMPLEONETRACK, a mix involving one track, no resampling.
 *
 * TODO: Due to the special mixing considerations of duplicating to
 * a stereo output track, the input track cannot be MONO.  This should be
 * prevented by the caller.
 */
AudioMixer::process_hook_t AudioMixer::getProcessHook(int processType, uint32_t channelCount,
        audio_format_t mixerInFormat, audio_format_t mixerOutFormat)
{
    if (processType != PROCESSTYPE_NORESAMPLEONETRACK) { // Only NORESAMPLEONETRACK
        LOG_ALWAYS_FATAL("bad processType: %d", processType);
        return NULL;
    }
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
    if (0)
#else
    if (!kUseNewMixer && channelCount == FCC_2 && mixerInFormat == AUDIO_FORMAT_PCM_16_BIT)
#endif // MTK_AUDIOMIXER_ENABLE_DRC
    {
        return process__OneTrack16BitsStereoNoResampling;
    }
    LOG_ALWAYS_FATAL_IF(channelCount > MAX_NUM_CHANNELS);
    switch (mixerInFormat) {
    case AUDIO_FORMAT_PCM_FLOAT:
        switch (mixerOutFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            return process_NoResampleOneTrack<MIXTYPE_MULTI_SAVEONLY,
                    float /*TO*/, float /*TI*/, int32_t /*TA*/>;
        case AUDIO_FORMAT_PCM_16_BIT:
            return process_NoResampleOneTrack<MIXTYPE_MULTI_SAVEONLY,
                    int16_t, float, int32_t>;
        default:
            LOG_ALWAYS_FATAL("bad mixerOutFormat: %#x", mixerOutFormat);
            break;
        }
        break;
    case AUDIO_FORMAT_PCM_16_BIT:
        switch (mixerOutFormat) {
        case AUDIO_FORMAT_PCM_FLOAT:
            return process_NoResampleOneTrack<MIXTYPE_MULTI_SAVEONLY,
                    float, int16_t, int32_t>;
        case AUDIO_FORMAT_PCM_16_BIT:
            return process_NoResampleOneTrack<MIXTYPE_MULTI_SAVEONLY,
                    int16_t, int16_t, int32_t>;
        default:
            LOG_ALWAYS_FATAL("bad mixerOutFormat: %#x", mixerOutFormat);
            break;
        }
        break;
    default:
        LOG_ALWAYS_FATAL("bad mixerInFormat: %#x", mixerInFormat);
        break;
    }
    return NULL;
}

// ----------------------------------------------------------------------------
// <MTK_AUDIO
#if defined(MTK_AUDIOMIXER_ENABLE_DRC)
bool AudioMixer::mUIDRCEnable = true;

template <typename TO>
void AudioMixer::track_t::volumeClamp(track_t* t, size_t outFrameCount, TO** temp, bool isClamp)
{
    if ((t->mMixerInFormat == AUDIO_FORMAT_PCM_16_BIT) && t->checkDRCExist()) {
        size_t i;
        int32_t n1;
        int* ptr = (int*)(*temp);
        int* ptr_out = (int*)(*temp);
        int resamplerChannelCount = t->downmixerBufferProvider != NULL
            ? t->mMixerChannelCount : t->channelCount;
        resamplerChannelCount = resamplerChannelCount == 1 ? 2 : resamplerChannelCount;
        for (i = 0; i < outFrameCount * resamplerChannelCount; i++) {
            n1 = (*ptr++);
            if (isClamp) {
                n1 = (clamp4_27(n1) << 4);
            } else {
                n1 = (n1 + (1 << 3)) >> 4;
            }
            *ptr_out++ = n1;
        }
    }
}

template <int MIXTYPE>
bool AudioMixer::track_t::doPostProcessing(void *buffer, audio_format_t format, size_t frameCount)
{
    if (mStreamType == AUDIO_STREAM_PATCH) {
        return true;
    }

    int32_t process_channel = (MIXTYPE == MIXTYPE_MONOEXPAND ? channelCount : mMixerChannelCount);

    bool DRC_enable = checkDRCExist();

    if (FCC_2 == mMixerChannelCount && DRC_enable) {
        if (!((AUDIO_FORMAT_PCM_32_BIT == format) || (AUDIO_FORMAT_PCM_16_BIT == format) ||
              (AUDIO_FORMAT_PCM_FLOAT == format))) {
            ALOGE("%s, format not support!!", __FUNCTION__);
            return false;
        }

        // format wrapper start, if need
        audio_format_t process_format = format;
        void* processBuffer = buffer;

        if (AUDIO_FORMAT_PCM_FLOAT == process_format) {
            ALOGV("%s, frameCount (%zu), t->channelCount(%d) process_channel (%d) process_format (%d)",
                __FUNCTION__, frameCount, channelCount, process_channel, process_format);
            memcpy_by_audio_format(processBuffer, AUDIO_FORMAT_PCM_32_BIT, buffer, process_format,
                frameCount * process_channel);
            process_format = AUDIO_FORMAT_PCM_32_BIT;
        }

        // DRC process
        if ((frameCount >= 512) && (frameCount % 512 == 0)) { // DRC need at least 512 frameCount.
            int32_t sampleSize = frameCount * process_channel * audio_bytes_per_sample(process_format);
            applyDRC((void *)buffer, sampleSize, mState->pDRCTempBuffer, process_format, process_channel);
        }

        // format wrapper end, if need
        if (process_format != format) {
            int32_t sampleCount = frameCount * (MIXTYPE == MIXTYPE_MONOEXPAND ? channelCount : mMixerChannelCount);

            ALOGVV("frameCount(%d), sampleCount (%d) format(%d)", frameCount, sampleCount, format);
            memcpy_by_audio_format(buffer, format, buffer, process_format, sampleCount);
        }
    }
    return true;
}

void AudioMixer::releaseDRC(int name)
{
    name -= TRACK0;
    track_t& track(mState.tracks[name]);

    if (track.mpDRCObj) {
        track.mpDRCObj->close();
        delete track.mpDRCObj;
        track.mpDRCObj = NULL;
    }
}

void AudioMixer::track_t::resetDRC()
{
    if (mpDRCObj) {
        mpDRCObj->resetBuffer();
    }
}

void AudioMixer::track_t::updateDRCParam(int sampleRate)
{
    if (mpDRCObj) {
        mpDRCObj->resetBuffer();
        mpDRCObj->close();

        // set true when mStreamType == AUDIO_STREAM_VOICE_CALL
        mpDRCObj->setParameter(BLOUD_PAR_SET_NOISE_FILTER, (void *)(mStreamType == AUDIO_STREAM_VOICE_CALL));
        mpDRCObj->setParameter(BLOUD_PAR_SET_SAMPLE_RATE, (void *)(uintptr_t)sampleRate);

        // DRC will always receive 2ch data.
        if (!doesResample() &&
            mMixerChannelMask == AUDIO_CHANNEL_OUT_STEREO &&
            channelMask == AUDIO_CHANNEL_OUT_MONO) {
            mpDRCObj->setParameter(BLOUD_PAR_SET_CHANNEL_NUMBER, (void *)BLOUD_HD_MONO);
        } else {
            mpDRCObj->setParameter(BLOUD_PAR_SET_CHANNEL_NUMBER, (void *)BLOUD_HD_STEREO);
        }

        if (AUDIO_FORMAT_PCM_16_BIT == mMixerInFormat && !doesResample()) {
            mpDRCObj->setParameter(BLOUD_PAR_SET_PCM_FORMAT, (void *)BLOUD_IN_Q1P15_OUT_Q1P15);
        } else {
            mpDRCObj->setParameter(BLOUD_PAR_SET_PCM_FORMAT, (void *)BLOUD_IN_Q1P31_OUT_Q1P31);
        }

        mpDRCObj->setParameter(BLOUD_PAR_SET_USE_DEFAULT_PARAM_FORCE_RELOAD, (void *)NULL);
        mpDRCObj->setParameter(BLOUD_PAR_SET_WORK_MODE, (void *)AUDIO_CMP_FLT_LOUDNESS_LITE);
#if defined(MTK_ENABLE_STEREO_SPEAKER)
        mpDRCObj->setParameter(BLOUD_PAR_SET_STEREO_TO_MONO_MODE, (void *)BLOUD_S2M_MODE_ST2MO2ST);
#else
        mpDRCObj->setParameter(BLOUD_PAR_SET_STEREO_TO_MONO_MODE, (void *)BLOUD_S2M_MODE_NONE);
#endif // MTK_ENABLE_STEREO_SPEAKER

        // initial set to BLOUD_HD_BYPASS_STATE for ramp up
        mpDRCObj->setParameter(BLOUD_PAR_SET_RAMP_UP, (void *)(intptr_t)(1));
        mpDRCObj->open();
    }
}

void AudioMixer::track_t::setDRCHandler(audio_devices_t device, uint32_t sampleRate)
{
    if ((device & AUDIO_DEVICE_OUT_SPEAKER) && (mStreamType != AUDIO_STREAM_DTMF)
#ifdef MTK_AUDIOMIXER_DRC_VOIP_ONLY
        && (mStreamType == AUDIO_STREAM_VOICE_CALL)
#endif
    ) {
        // Need DRC
        if (mUIDRCEnable && mpDRCObj == NULL) {
            if (mStreamType == AUDIO_STREAM_RING) {
                mpDRCObj = new MtkAudioLoud(AUDIO_COMP_FLT_DRC_FOR_RINGTONE);
            } else {
                mpDRCObj = new MtkAudioLoud(AUDIO_COMP_FLT_DRC_FOR_MUSIC);
            }
            updateDRCParam(sampleRate);
        }

        if (mpDRCObj && mUIDRCEnable != mDRCEnable) {
            mDRCEnable = mUIDRCEnable;
            if (mDRCEnable) {
                mpDRCObj->change2Normal();
            } else {
                mpDRCObj->change2ByPass();
            }
        }
    } else {
        // No need DRC
        if (mpDRCObj) {
            mpDRCObj->close();
            delete mpDRCObj;
            mpDRCObj = NULL;
        }
    }
}

void AudioMixer::track_t::applyDRC(void *ioBuffer, uint32_t SampleSize, int32_t *tempBuffer,
    audio_format_t process_format, int process_channel)
{
    if (!checkDRCExist()) {
       return;
    }

    uint32_t inputSampleSize, outputSampleSize;

    inputSampleSize = outputSampleSize = SampleSize;

#if defined(MTK_AUDIO_DEBUG)
    if (AudioDump::getProperty(AudioParameter::PROP_AUDIO_DUMP_DRC)) {
        String8 fileName = String8::format("%s_pid%d_tid%d_%p.pcm", AudioParameter::af_mixer_drc_pcm_before,
            getpid(), gettid(), ioBuffer);
        AudioDump::threadDump(fileName, ioBuffer, SampleSize, process_format, mDevSampleRate,
            process_channel);
    }
#endif // MTK_AUDIO_DEBUG

    mpDRCObj->process((void *)ioBuffer, &inputSampleSize, (void *)tempBuffer, &outputSampleSize);

#if defined(MTK_AUDIO_DEBUG)
    if (AudioDump::getProperty(AudioParameter::PROP_AUDIO_DUMP_DRC)) {
        String8 fileName = String8::format("%s_pid%d_tid%d_%p.pcm", AudioParameter::af_mixer_drc_pcm_after,
            getpid(), gettid(), tempBuffer);
        AudioDump::threadDump(fileName, tempBuffer, SampleSize, process_format, mDevSampleRate,
            process_channel);
    }
#endif // MTK_AUDIO_DEBUG

    memcpy(ioBuffer, tempBuffer, SampleSize);
}

bool AudioMixer::track_t::checkDRCExist()
{
    return (mpDRCObj != NULL);
}

static inline int32_t clamp4_27(int32_t sample)
{
    if ((sample >> 27) ^ (sample >> 31)) {
        sample = 0x7FFFFFF ^ (sample >> 31);
    }
    return sample;
}
#else
template <typename TO>
void AudioMixer::track_t::volumeClamp(track_t* t __unused, size_t outFrameCount __unused, TO** temp __unused, bool isClamp __unused)
{
}

template <int MIXTYPE>
bool AudioMixer::track_t::doPostProcessing(void *buffer __unused, audio_format_t format __unused, size_t frameCount __unused)
{
    return true;
}

void AudioMixer::releaseDRC(int name __unused)
{
}

void AudioMixer::track_t::resetDRC()
{
}

void AudioMixer::track_t::updateDRCParam(int sampleRate __unused)
{
}

void AudioMixer::track_t::setDRCHandler(audio_devices_t device __unused, uint32_t sampleRate __unused)
{
}

void AudioMixer::track_t::applyDRC(void *ioBuffer __unused, uint32_t SampleSize __unused, int32_t *tempBuffer __unused,
    audio_format_t process_format __unused, int process_channel __unused)
{
}

bool AudioMixer::track_t::checkDRCExist()
{
    return false;
}
#endif // MTK_AUDIOMIXER_ENABLE_DRC
// MTK_AUDIO>
} // namespace android
