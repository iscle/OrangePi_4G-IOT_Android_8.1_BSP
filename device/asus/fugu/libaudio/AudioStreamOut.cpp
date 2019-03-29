/*
**
** Copyright 2012, The Android Open Source Project
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

#define LOG_TAG "AudioHAL_AudioStreamOut"

#include <inttypes.h>
#include <utils/Log.h>

#include "AudioHardwareOutput.h"
#include "AudioStreamOut.h"

// Set to 1 to print timestamp data in CSV format.
#ifndef HAL_PRINT_TIMESTAMP_CSV
#define HAL_PRINT_TIMESTAMP_CSV 0
#endif

//#define VERY_VERBOSE_LOGGING
#ifdef VERY_VERBOSE_LOGGING
#define ALOGVV ALOGV
#else
#define ALOGVV(a...) do { } while(0)
#endif

namespace android {

AudioStreamOut::AudioStreamOut(AudioHardwareOutput& owner, bool mcOut, bool isIec958NonAudio)
    : mRenderPosition(0)
    , mFramesPresented(0)
    , mLastPresentationPosition(0)
    , mLastPresentationValid(false)
    , mOwnerHAL(owner)
    , mFramesWritten(0)
    , mTgtDevices(0)
    , mAudioFlingerTgtDevices(0)
    , mIsMCOutput(mcOut)
    , mInStandby(false)
    , mIsIec958NonAudio(isIec958NonAudio)
    , mReportedAvailFail(false)
{
    assert(mLocalClock.initCheck());

    mPhysOutputs.setCapacity(3);

    // Set some reasonable defaults for these.  All of this should eventually
    // be overwritten by a specific audio flinger configuration, but it does not
    // hurt to have something here by default.
    mInputSampleRate = 48000;
    mInputChanMask = AUDIO_CHANNEL_OUT_STEREO;
    mInputFormat = AUDIO_FORMAT_PCM_16_BIT;
    mInputNominalChunksInFlight = 4; // pcm_open() fails if not 4!
    updateInputNums();

    mThrottleValid = false;

    memset(&mUSecToLocalTime, 0, sizeof(mUSecToLocalTime));
    mUSecToLocalTime.a_to_b_numer = mLocalClock.getLocalFreq();
    mUSecToLocalTime.a_to_b_denom = 1000000;
    LinearTransform::reduce(&mUSecToLocalTime.a_to_b_numer,
                            &mUSecToLocalTime.a_to_b_denom);
}

AudioStreamOut::~AudioStreamOut()
{
    releaseAllOutputs();
}

status_t AudioStreamOut::set(
        audio_format_t *pFormat,
        uint32_t *pChannels,
        uint32_t *pRate)
{
    Mutex::Autolock _l(mRoutingLock);
    audio_format_t lFormat   = pFormat ? *pFormat : AUDIO_FORMAT_DEFAULT;
    uint32_t       lChannels = pChannels ? *pChannels : 0;
    uint32_t       lRate     = pRate ? *pRate : 0;

    // fix up defaults
    if (lFormat == AUDIO_FORMAT_DEFAULT) lFormat = format();
    if (lChannels == 0)                  lChannels = chanMask();
    if (lRate == 0)                      lRate = sampleRate();

    if (pFormat)   *pFormat   = lFormat;
    if (pChannels) *pChannels = lChannels;
    if (pRate)     *pRate     = lRate;

    if (!audio_has_proportional_frames(lFormat)) {
        ALOGW("set: format 0x%08X needs to be wrapped in SPDIF data burst", lFormat);
        return BAD_VALUE;
    }

    if (!mIsMCOutput) {
        // If this is the primary stream out, then demand our defaults.
        if ((lFormat != AUDIO_FORMAT_PCM_16_BIT && lFormat != AUDIO_FORMAT_PCM_8_24_BIT) ||
            (lChannels != chanMask()) ||
            (lRate     != sampleRate())) {
            ALOGW("set: parameters incompatible with defaults");
            return BAD_VALUE;
        }
    } else {
        // Else check to see if our HDMI sink supports this format before proceeding.
        if (!mOwnerHAL.getHDMIAudioCaps().supportsFormat(
                lFormat, lRate, audio_channel_count_from_out_mask(lChannels),
                mIsIec958NonAudio)) {
            ALOGW("set: parameters incompatible with hdmi capabilities");
            return BAD_VALUE;
        }
    }

    mInputFormat = lFormat;
    mInputChanMask = lChannels;
    mInputSampleRate = lRate;
    ALOGI("AudioStreamOut::set: rate = %u, format = 0x%08X\n", lRate, lFormat);
    updateInputNums();

    return NO_ERROR;
}

void AudioStreamOut::setTgtDevices(uint32_t tgtDevices)
{
    Mutex::Autolock _l(mRoutingLock);
    if (mTgtDevices != tgtDevices) {
        mTgtDevices = tgtDevices;
    }
}

status_t AudioStreamOut::standbyHardware()
{
    releaseAllOutputs();
    mOwnerHAL.standbyStatusUpdate(true, mIsMCOutput);
    mInStandby = true;
    return NO_ERROR;
}

status_t AudioStreamOut::standby()
{
    ALOGI("standby: ==========================");
    mRenderPosition = 0;
    mLastPresentationValid = false;
    // Don't reset the presentation position.
    return standbyHardware();
}

void AudioStreamOut::releaseAllOutputs() {
    Mutex::Autolock _l(mRoutingLock);
    ALOGI("releaseAllOutputs: releasing %d mPhysOutputs", mPhysOutputs.size());
    AudioOutputList::iterator I;
    for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I)
        mOwnerHAL.releaseOutput(*this, *I);

    mPhysOutputs.clear();
}

status_t AudioStreamOut::pause()
{
    ALOGI("pause: ==========================");
    mLastPresentationValid = false;
    return standbyHardware();
}

status_t AudioStreamOut::resume()
{
    ALOGI("resume: ==========================");
    return NO_ERROR;
}

status_t AudioStreamOut::flush()
{
    ALOGI("flush: ==========================");
    mRenderPosition = 0;
    mFramesPresented = 0;
    Mutex::Autolock _l(mPresentationLock);
    mLastPresentationPosition = 0;
    mLastPresentationValid = false;
    return NO_ERROR;
}

void AudioStreamOut::updateInputNums()
{
    assert(mLocalClock.initCheck());

    mInputChanCount = audio_channel_count_from_out_mask(mInputChanMask);

    // 512 is good for AC3 and DTS passthrough.
    mInputChunkFrames = 512 * ((outputSampleRate() + 48000 - 1) / 48000);

    ALOGV("updateInputNums: chunk size %u from output rate %u\n",
        mInputChunkFrames, outputSampleRate());

    mInputFrameSize = mInputChanCount * audio_bytes_per_sample(mInputFormat);

    // Buffer size is just the frame size multiplied by the number of
    // frames per chunk.
    mInputBufSize = mInputChunkFrames * mInputFrameSize;

    // The nominal latency is just the duration of a chunk * the number of
    // chunks we nominally keep in flight at any given point in time.
    mInputNominalLatencyUSec = static_cast<uint32_t>(((
                    static_cast<uint64_t>(mInputChunkFrames)
                    * 1000000 * mInputNominalChunksInFlight)
                    / mInputSampleRate));

    memset(&mLocalTimeToFrames, 0, sizeof(mLocalTimeToFrames));
    mLocalTimeToFrames.a_to_b_numer = mInputSampleRate;
    mLocalTimeToFrames.a_to_b_denom = mLocalClock.getLocalFreq();
    LinearTransform::reduce(
            &mLocalTimeToFrames.a_to_b_numer,
            &mLocalTimeToFrames.a_to_b_denom);
}

void AudioStreamOut::finishedWriteOp(size_t framesWritten,
                                     bool needThrottle)
{
    assert(mLocalClock.initCheck());

    int64_t now = mLocalClock.getLocalTime();

    if (!mThrottleValid || !needThrottle) {
        mThrottleValid = true;
        mWriteStartLT  = now;
        mFramesWritten = 0;
    }

    mFramesWritten += framesWritten;
    mFramesPresented += framesWritten;
    mRenderPosition += framesWritten;

    if (needThrottle) {
        int64_t deltaLT;
        mLocalTimeToFrames.doReverseTransform(mFramesWritten, &deltaLT);
        deltaLT += mWriteStartLT;
        deltaLT -= now;

        int64_t deltaUSec;
        mUSecToLocalTime.doReverseTransform(deltaLT, &deltaUSec);

        if (deltaUSec > 0) {
            useconds_t sleep_time;

            // We should never be a full second ahead of schedule; sanity check
            // our throttle time and cap the max sleep time at 1 second.
            if (deltaUSec > 1000000) {
                ALOGW("throttle time clipped! deltaLT = %" PRIi64 " deltaUSec = %" PRIi64,
                    deltaLT, deltaUSec);
                sleep_time = 1000000;
            } else {
                sleep_time = static_cast<useconds_t>(deltaUSec);
            }
            usleep(sleep_time);
        }
    }
}

static const String8 keyRouting(AudioParameter::keyRouting);
static const String8 keyFormat(AudioParameter::keyFormat);
static const String8 keySupSampleRates(AudioParameter::keyStreamSupportedSamplingRates);
static const String8 keySupFormats(AudioParameter::keyStreamSupportedFormats);
static const String8 keySupChannels(AudioParameter::keyStreamSupportedChannels);
status_t AudioStreamOut::setParameters(__unused struct audio_stream *stream, const char *kvpairs)
{
    AudioParameter param = AudioParameter(String8(kvpairs));
    String8 key = String8(AudioParameter::keyRouting);
    int tmpInt;

    if (param.getInt(key, tmpInt) == NO_ERROR) {
        // The audio HAL handles routing to physical devices entirely
        // internally and mostly ignores what audio flinger tells it to do.  JiC
        // there is something (now or in the future) in audio flinger which
        // cares about the routing value in a call to getParameters, we hang on
        // to the last routing value set by audio flinger so we can at least be
        // consistent when we lie to the upper levels about doing what they told
        // us to do.
        mAudioFlingerTgtDevices = static_cast<uint32_t>(tmpInt);
    }

    return NO_ERROR;
}

char* AudioStreamOut::getParameters(const char* k)
{
    AudioParameter param = AudioParameter(String8(k));
    String8 stringValue;
    int intValue;

    if (param.get(keyRouting, stringValue) == NO_ERROR) {
        param.addInt(keyRouting, (int)mAudioFlingerTgtDevices);
    }

    HDMIAudioCaps& hdmiCaps = mOwnerHAL.getHDMIAudioCaps();

    if (param.get(keySupFormats, stringValue) == NO_ERROR) {
        if (mIsMCOutput) {
            hdmiCaps.getFmtsForAF(stringValue);
            param.add(keySupFormats, stringValue);
        } else {
            param.add(keySupFormats, String8("AUDIO_FORMAT_PCM_16_BIT|AUDIO_FORMAT_PCM_8_24_BIT"));
        }
    }

    audio_format_t format = AUDIO_FORMAT_INVALID;
    if (param.getInt(keyFormat, intValue) == NO_ERROR) {
        format = (audio_format_t)intValue;
    }

    if (param.get(keySupSampleRates, stringValue) == NO_ERROR) {
        if (mIsMCOutput) {
            hdmiCaps.getRatesForAF(stringValue, format);
            param.add(keySupSampleRates, stringValue);
        } else {
            param.add(keySupSampleRates, String8("48000"));
        }
    }

    if (param.get(keySupChannels, stringValue) == NO_ERROR) {
        if (mIsMCOutput) {
            hdmiCaps.getChannelMasksForAF(stringValue, format);
            param.add(keySupChannels, stringValue);
        } else {
            param.add(keySupChannels, String8("AUDIO_CHANNEL_OUT_STEREO"));
        }
    }

    return strdup(param.toString().string());
}

uint32_t AudioStreamOut::outputSampleRate() const
{
    return mInputSampleRate;
}

uint32_t AudioStreamOut::latency() const {
    uint32_t uSecLatency = mInputNominalLatencyUSec;
    uint32_t vcompDelay = mOwnerHAL.getVideoDelayCompUsec();

    if (uSecLatency < vcompDelay)
        return 0;

    return ((uSecLatency - vcompDelay) / 1000);
}

// Used to implement get_presentation_position() for Audio HAL.
// According to the prototype in audio.h, the frame count should not get
// reset on standby().
status_t AudioStreamOut::getPresentationPosition(uint64_t *frames,
        struct timespec *timestamp)
{
    status_t result = -ENODEV;
    // If we cannot get a lock then try to return a cached position and timestamp.
    // It is better to return an old timestamp then to wait for a fresh one.
    if (mRoutingLock.tryLock() != OK) {
        // We failed to get the lock. It is probably held by a blocked write().
        if (mLastPresentationValid) {
            // Use cached position.
            // Use mutex because this cluster of variables may be getting
            // updated by the write thread.
            Mutex::Autolock _l(mPresentationLock);
            *frames = mLastPresentationPosition;
            *timestamp = mLastPresentationTime;
            result = NO_ERROR;
        }
        return result;
    }

    // Lock succeeded so it is safe to call this.
    result = getPresentationPosition_l(frames, timestamp);

    mRoutingLock.unlock();
    return result;
}

// Used to implement get_presentation_position() for Audio HAL.
// According to the prototype in audio.h, the frame count should not get
// reset on standby().
// mRoutingLock should be locked before calling this method.
status_t AudioStreamOut::getPresentationPosition_l(uint64_t *frames,
        struct timespec *timestamp)
{
    status_t result = -ENODEV;
    // The presentation timestamp should be the same for all devices.
    // Also Molly only has one output device at the moment.
    // So just use the first one in the list.
    if (!mPhysOutputs.isEmpty()) {
        unsigned int avail = 0;
        sp<AudioOutput> audioOutput = mPhysOutputs.itemAt(0);
        if (audioOutput->getHardwareTimestamp(&avail, timestamp) == OK) {

            int64_t framesInDriverBuffer = (int64_t)audioOutput->getKernelBufferSize() - (int64_t)avail;
            if (framesInDriverBuffer >= 0) {
                // FIXME av sync fudge factor
                // Use a fudge factor to account for hidden buffering in the
                // HDMI output path. This is a hack until we can determine the
                // actual buffer sizes.
                // Increasing kFudgeMSec will move the audio earlier in
                // relation to the video.
                const int kFudgeMSec = 50;
                int fudgeFrames = kFudgeMSec * sampleRate() / 1000;
                int64_t pendingFrames = framesInDriverBuffer + fudgeFrames;

                int64_t signedFrames = mFramesPresented - pendingFrames;
                if (signedFrames < 0) {
                    ALOGV("getPresentationPosition: playing silent preroll"
                            ", mFramesPresented = %" PRIu64 ", pendingFrames = %" PRIi64,
                            mFramesPresented, pendingFrames);
                } else {
    #if HAL_PRINT_TIMESTAMP_CSV
                    // Print comma separated values for spreadsheet analysis.
                    uint64_t nanos = (((uint64_t)timestamp->tv_sec) * 1000000000L)
                            + timestamp->tv_nsec;
                    ALOGI("getPresentationPosition, %" PRIu64 ", %4u, %" PRIi64 ", %" PRIu64,
                            mFramesPresented, avail, signedFrames, nanos);
    #endif
                    uint64_t unsignedFrames = (uint64_t) signedFrames;

                    {
                        Mutex::Autolock _l(mPresentationLock);
                        // Check for retrograde timestamps.
                        if (unsignedFrames < mLastPresentationPosition) {
                            ALOGW("getPresentationPosition: RETROGRADE timestamp, diff = %" PRId64,
                                (int64_t)(unsignedFrames - mLastPresentationPosition));
                            if (mLastPresentationValid) {
                                // Use previous presentation position and time.
                                *timestamp = mLastPresentationTime;
                                *frames = mLastPresentationPosition;
                                result = NO_ERROR;
                            }
                            // else return error
                        } else {
                            *frames = unsignedFrames;
                            // Save cached data that we can use when the HAL is locked.
                            mLastPresentationPosition = unsignedFrames;
                            mLastPresentationTime = *timestamp;
                            result = NO_ERROR;
                        }
                    }
                }
            } else {
                ALOGE("getPresentationPosition: avail too large = %u", avail);
            }
            mReportedAvailFail = false;
        } else {
            ALOGW_IF(!mReportedAvailFail,
                    "getPresentationPosition: getHardwareTimestamp returned non-zero");
            mReportedAvailFail = true;
        }
    } else {
        ALOGVV("getPresentationPosition: no physical outputs! This HAL is inactive!");
    }
    mLastPresentationValid = result == NO_ERROR;
    return result;
}

status_t AudioStreamOut::getRenderPosition(__unused uint32_t *dspFrames)
{
    if (dspFrames == NULL) {
        return -EINVAL;
    }
    *dspFrames = (uint32_t) mRenderPosition;
    return NO_ERROR;
}

void AudioStreamOut::updateTargetOutputs()
{
    Mutex::Autolock _l(mRoutingLock);
    AudioOutputList::iterator I;
    uint32_t cur_outputs = 0;

    for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I)
        cur_outputs |= (*I)->devMask();

    if (cur_outputs == mTgtDevices)
        return;

    uint32_t outputsToObtain  = mTgtDevices & ~cur_outputs;
    uint32_t outputsToRelease = cur_outputs & ~mTgtDevices;

    // Start by releasing any outputs we should no longer have back to the HAL.
    if (outputsToRelease) {

        I = mPhysOutputs.begin();
        while (I != mPhysOutputs.end()) {
            if (!(outputsToRelease & (*I)->devMask())) {
                ++I;
                continue;
            }

            outputsToRelease &= ~((*I)->devMask());
            mOwnerHAL.releaseOutput(*this, *I);
            I = mPhysOutputs.erase(I);
        }
    }

    if (outputsToRelease) {
        ALOGW("Bookkeeping error!  Still have outputs to release (%08x), but"
              " none of them appear to be in the mPhysOutputs list!",
              outputsToRelease);
    }

    // Now attempt to obtain any outputs we should be using, but are not
    // currently.
    if (outputsToObtain) {
        uint32_t mask;

        // Buffer configuration may need updating now that we have decoded
        // the start of a stream. For example, EAC3, needs 4X sampleRate.
        updateInputNums();

        for (mask = 0x1; outputsToObtain; mask <<= 1) {
            if (!(mask & outputsToObtain))
                continue;

            sp<AudioOutput> newOutput;
            status_t res;

            res = mOwnerHAL.obtainOutput(*this, mask, &newOutput);
            outputsToObtain &= ~mask;

            if (OK != res) {
                // If we get an error back from obtain output, it means that
                // something went really wrong at a lower level (probably failed
                // to open the driver).  We should not try to obtain this output
                // again, at least until the next routing change.
                ALOGW("Failed to obtain output %08x for %s audio stream out."
                      " (res %d)", mask, getName(), res);
                mTgtDevices &= ~mask;
                continue;
            }

            if (newOutput != NULL) {
                // If we actually got an output, go ahead and add it to our list
                // of physical outputs.  The rest of the system will handle
                // starting it up.  If we didn't get an output, but also got no
                // error code, it just means that the output is currently busy
                // and should become available soon.
                ALOGI("updateTargetOutputs: adding output back to mPhysOutputs");
                mPhysOutputs.push_back(newOutput);
            }
        }
    }
}

void AudioStreamOut::adjustOutputs(int64_t maxTime)
{
    int64_t a_zero_original = mLocalTimeToFrames.a_zero;
    int64_t b_zero_original = mLocalTimeToFrames.b_zero;
    AudioOutputList::iterator I;

    // Check to see if any outputs are active and see what their buffer levels
    // are.
    for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I) {
        if ((*I)->getState() == AudioOutput::DMA_START) {
            int64_t lastWriteTS = (*I)->getLastNextWriteTS();
            int64_t padAmt;

            mLocalTimeToFrames.a_zero = lastWriteTS;
            mLocalTimeToFrames.b_zero = 0;
            if (mLocalTimeToFrames.doForwardTransform(maxTime,
                                                      &padAmt)) {
                (*I)->adjustDelay(((int32_t)padAmt));
            }
        }
    }
    // Restore original offset so that the sleep time calculation for
    // throttling is not broken in finishedWriteOp().
    mLocalTimeToFrames.a_zero = a_zero_original;
    mLocalTimeToFrames.b_zero = b_zero_original;
}

ssize_t AudioStreamOut::write(const void* buffer, size_t bytes)
{
    uint8_t *data = (uint8_t *)buffer;
    ALOGVV("AudioStreamOut::write_l(%u) 0x%02X, 0x%02X, 0x%02X, 0x%02X,"
          " 0x%02X, 0x%02X, 0x%02X, 0x%02X,"
          " 0x%02X, 0x%02X, 0x%02X, 0x%02X,"
          " 0x%02X, 0x%02X, 0x%02X, 0x%02X",
        bytes, data[0], data[1], data[2], data[3],
        data[4], data[5], data[6], data[7],
        data[8], data[9], data[10], data[11],
        data[12], data[13], data[14], data[15]
        );

    //
    // Note that only calls to write change the contents of the mPhysOutputs
    // collection (during the call to updateTargetOutputs).  updateTargetOutputs
    // will hold the routing lock during the operation, as should any reader of
    // mPhysOutputs, unless the reader is a call to write or
    // getNextWriteTimestamp (we know that it is safe for write and gnwt to read
    // the collection because the only collection mutator is the same thread
    // which calls write and gnwt).

    // If the stream is in standby, then the first write should bring it out
    // of standby
    if (mInStandby) {
        mOwnerHAL.standbyStatusUpdate(false, mIsMCOutput);
        mInStandby = false;
    }

    updateTargetOutputs(); // locks mRoutingLock

    // If any of our outputs is in the PRIMED state when ::write is called, it
    // means one of two things.  First, it could be that the DMA output really
    // has not started yet.  This is odd, but certainly not impossible.  The
    // other possibility is that AudioFlinger is in its silence-pushing mode and
    // is not calling getNextWriteTimestamp.  After an output is primed, its in
    // GNWTS where the amount of padding to compensate for different DMA start
    // times is taken into account.  Go ahead and force a call to GNWTS, just to
    // be certain that we have checked recently and are not stuck in silence
    // fill mode.  Failure to do this will cause the AudioOutput state machine
    // to eventually give up on DMA starting and reset the output over and over
    // again (spamming the log and producing general confusion).
    //
    // While we are in the process of checking our various output states, check
    // to see if any outputs have made it to the ACTIVE state.  Pass this
    // information along to the call to processOneChunk.  If any of our outputs
    // are waiting to be primed while other outputs have made it to steady
    // state, we need to change our priming behavior slightly.  Instead of
    // filling an output's buffer completely, we want to fill it to slightly
    // less than full and let the adjustDelay mechanism take care of the rest.
    //
    // Failure to do this during steady state operation will almost certainly
    // lead to the new output being over-filled relative to the other outputs
    // causing it to be slightly out of sync.
    AudioOutputList::iterator I;
    bool checkDMAStart = false;
    bool hasActiveOutputs = false;
    {
        Mutex::Autolock _l(mRoutingLock);
        for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I) {
            if (AudioOutput::PRIMED == (*I)->getState())
                checkDMAStart = true;

            if ((*I)->getState() == AudioOutput::ACTIVE)
                hasActiveOutputs = true;
        }
    }
    if (checkDMAStart) {
        int64_t junk;
        getNextWriteTimestamp_internal(&junk);
    }

    // We always call processOneChunk on the outputs, as it is the
    // tick for their state machines.
    {
        Mutex::Autolock _l(mRoutingLock);
        for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I) {
            (*I)->processOneChunk((uint8_t *)buffer, bytes, hasActiveOutputs, mInputFormat);
        }

        // If we don't actually have any physical outputs to write to, just sleep
        // for the proper amount of time in order to simulate the throttle that writing
        // to the hardware would impose.
        uint32_t framesWritten = bytes / mInputFrameSize;
        finishedWriteOp(framesWritten, (0 == mPhysOutputs.size()));
    }

    // Load presentation position cache because we will normally be locked when it is called.
    {
        Mutex::Autolock _l(mRoutingLock);
        uint64_t frames;
        struct timespec timestamp;
        getPresentationPosition_l(&frames, &timestamp);
    }
    return static_cast<ssize_t>(bytes);
}

status_t AudioStreamOut::getNextWriteTimestamp(int64_t *timestamp)
{
    return getNextWriteTimestamp_internal(timestamp);
}

status_t AudioStreamOut::getNextWriteTimestamp_internal(
        int64_t *timestamp)
{
    int64_t max_time = LLONG_MIN;
    bool    max_time_valid = false;
    bool    need_adjust = false;

    // Across all of our physical outputs, figure out the max time when
    // a write operation will hit the speakers.  Assume that if an
    // output cannot answer the question, its because it has never
    // started or because it has recently underflowed and needs to be
    // restarted.  If this is the case, we will need to prime the
    // pipeline with a chunk's worth of data before proceeding.
    // If any of the outputs indicate a discontinuity (meaning that the
    // DMA start time was valid and is now invalid, or was and is valid
    // but was different from before; almost certainly caused by a low
    // level underfow), then just stop now.  We will need to reset and
    // re-prime all of the outputs in order to make certain that the
    // lead-times on all of the outputs match.

    AudioOutputList::iterator I;
    bool discon = false;

    // Find the largest next write timestamp. The goal is to make EVERY
    // output have the same value, but we also need this to pass back
    // up the layers.
    for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I) {
        int64_t tmp;
        if (OK == (*I)->getNextWriteTimestamp(&tmp, &discon)) {
            if (!max_time_valid || (max_time < tmp)) {
                max_time = tmp;
                max_time_valid = true;
            }
        }
    }

    // Check the state of each output and determine if we need to align them.
    // Make sure to do this after we have called each outputs'
    // getNextWriteTimestamp as the transition from PRIMED to DMA_START happens
    // there.
    for (I = mPhysOutputs.begin(); I != mPhysOutputs.end(); ++I) {
        if ((*I)->getState() == AudioOutput::DMA_START) {
            need_adjust = true;
            break;
        }
    }

    // At this point, if we still have not found at least one output
    // who knows when their data is going to hit the speakers, then we
    // just can't answer the getNextWriteTimestamp question and we
    // should give up.
    if (!max_time_valid) {
        return INVALID_OPERATION;
    }

    // Stuff silence into the non-aligned outputs so that the effective
    // timestamp is the same for all the outputs.
    if (need_adjust)
        adjustOutputs(max_time);

    // We are done. The time at which the next written audio should
    // hit the speakers is just max_time plus the maximum amt of delay
    // compensation in the system.
    *timestamp = max_time;
    return OK;
}

#define DUMP(a...) \
    snprintf(buffer, SIZE, a); \
    buffer[SIZE - 1] = 0; \
    result.append(buffer);
#define B2STR(b) b ? "true" : "false"

status_t AudioStreamOut::dump(int fd)
{
    const size_t SIZE = 256;
    char buffer[SIZE];
    String8 result;
    DUMP("\n%s AudioStreamOut::dump\n", getName());
    DUMP("\tsample rate            : %d\n", sampleRate());
    DUMP("\tbuffer size            : %d\n", bufferSize());
    DUMP("\tchannel mask           : 0x%04x\n", chanMask());
    DUMP("\tformat                 : %d\n", format());
    DUMP("\tdevice mask            : 0x%04x\n", mTgtDevices);
    DUMP("\tIn standby             : %s\n", mInStandby? "yes" : "no");

    mRoutingLock.lock();
    AudioOutputList outSnapshot(mPhysOutputs);
    mRoutingLock.unlock();

    AudioOutputList::iterator I;
    for (I = outSnapshot.begin(); I != outSnapshot.end(); ++I)
        (*I)->dump(result);

    ::write(fd, result.string(), result.size());

    return NO_ERROR;
}

#undef B2STR
#undef DUMP

}  // android
