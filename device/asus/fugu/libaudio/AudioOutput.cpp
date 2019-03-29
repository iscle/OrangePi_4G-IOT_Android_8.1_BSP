/*
**
** Copyright 2011, The Android Open Source Project
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

#define LOG_TAG "AudioHAL:AudioOutput"
// #define LOG_NDEBUG 0

#include <utils/Log.h>

#include <assert.h>
#include <limits.h>
#include <semaphore.h>
#include <sys/ioctl.h>

#include <audio_utils/primitives.h>
#include <common_time/local_clock.h>

#define __DO_FUNCTION_IMPL__
#include "alsa_utils.h"
#undef __DO_FUNCTION_IMPL__
#include "AudioOutput.h"

// TODO: Consider using system/media/alsa_utils for the future.

namespace android {

const uint32_t AudioOutput::kMaxDelayCompensationMSec = 300;
const uint32_t AudioOutput::kPrimeTimeoutChunks = 10; // 100ms

AudioOutput::AudioOutput(const char* alsa_name,
                         enum pcm_format alsa_pcm_format)
        : mState(OUT_OF_SYNC)
        , mFramesPerChunk(0)
        , mFramesPerSec(0)
        , mBufferChunks(0)
        , mChannelCnt(0)
        , mALSAName(alsa_name)
        , mALSAFormat(alsa_pcm_format)
        , mBytesPerSample(0)
        , mBytesPerFrame(0)
        , mBytesPerChunk(0)
        , mStagingSize(0)
        , mStagingBuf(NULL)
        , mSilenceSize(0)
        , mSilenceBuf(NULL)
        , mPrimeTimeoutChunks(0)
        , mReportedWriteFail(false)
        , mVolume(0.0)
        , mFixedLvl(0.0)
        , mMute(false)
        , mOutputFixed(false)
        , mVolParamsDirty(true)
{
    mLastNextWriteTimeValid = false;

    mMaxDelayCompFrames = 0;
    mExternalDelayUSec = 0;

    mDevice = NULL;
    mDeviceExtFd = -1;
    mALSACardID = -1;
    mFramesQueuedToDriver = 0;
}

AudioOutput::~AudioOutput() {
    cleanupResources();
    free(mStagingBuf);
    free(mSilenceBuf);
}

status_t AudioOutput::initCheck() {
    if (!mDevice) {
        ALOGE("Unable to open PCM device for %s output.", getOutputName());
        return NO_INIT;
    }
    if (!pcm_is_ready(mDevice)) {
        ALOGE("PCM device %s is not ready.", getOutputName());
        ALOGE("PCM error: %s", pcm_get_error(mDevice));
        return NO_INIT;
    }

    return OK;
}

void AudioOutput::setupInternal() {
    LocalClock lc;

    mMaxDelayCompFrames = kMaxDelayCompensationMSec * mFramesPerSec / 1000;

    switch (mALSAFormat) {
    case PCM_FORMAT_S16_LE:
        mBytesPerSample = 2;
        break;
    case PCM_FORMAT_S24_3LE:
        mBytesPerSample = 3;
        break;
    case PCM_FORMAT_S24_LE: // fall through
    case PCM_FORMAT_S32_LE:
        mBytesPerSample = 4;
        break;
    default:
        LOG_ALWAYS_FATAL("Unexpected alsa format %d", mALSAFormat);
        break;
    }

    mBytesPerFrame = mBytesPerSample * mChannelCnt;
    mBytesPerChunk = mBytesPerFrame * mFramesPerChunk;

    memset(&mFramesToLocalTime, 0, sizeof(mFramesToLocalTime));
    mFramesToLocalTime.a_to_b_numer = lc.getLocalFreq();
    mFramesToLocalTime.a_to_b_denom = mFramesPerSec ? mFramesPerSec : 1;
    LinearTransform::reduce(
            &mFramesToLocalTime.a_to_b_numer,
            &mFramesToLocalTime.a_to_b_denom);

    openPCMDevice();
}

void AudioOutput::primeOutput(bool hasActiveOutputs) {
    ALOGI("primeOutput %s", getOutputName());

    if (hasFatalError())
        return;

    // See comments in AudioStreamOut::write for the reasons behind the
    // different priming levels.
    uint32_t primeAmt = mFramesPerChunk * mBufferChunks;
    if (hasActiveOutputs)
        primeAmt /= 2;

    pushSilence(primeAmt);
    mPrimeTimeoutChunks = 0;
    mState = PRIMED;
}

void AudioOutput::adjustDelay(int32_t nFrames) {
    if (hasFatalError())
        return;

    if (nFrames >= 0) {
        ALOGI("adjustDelay %s %d", getOutputName(), nFrames);
        pushSilence(nFrames);
        mState = ACTIVE;
    } else {
        ALOGW("adjustDelay %s %d, ignoring negative adjustment",
              getOutputName(), nFrames);
    }
}

void AudioOutput::pushSilence(uint32_t nFrames)
{
    if (nFrames == 0 || hasFatalError())
        return;
    // choose 8_24_BIT instead of 16_BIT as it is native to Fugu
    const audio_format_t format = AUDIO_FORMAT_PCM_8_24_BIT;
    const size_t frameSize = audio_bytes_per_sample(format) * mChannelCnt;
    const size_t writeSize = nFrames * frameSize;
    if (mSilenceSize < writeSize) {
        // for zero initialized memory calloc is much faster than malloc or realloc.
        void *sbuf = calloc(nFrames, frameSize);
        if (sbuf == NULL) return;
        free(mSilenceBuf);
        mSilenceBuf = sbuf;
        mSilenceSize = writeSize;
    }
    doPCMWrite((const uint8_t*)mSilenceBuf, writeSize, format);
    mFramesQueuedToDriver += nFrames;
}

void AudioOutput::cleanupResources() {

    Mutex::Autolock _l(mDeviceLock);

    if (NULL != mDevice)
        pcm_close(mDevice);

    mDevice = NULL;
    mDeviceExtFd = -1;
    mALSACardID = -1;
}

void AudioOutput::openPCMDevice() {

    Mutex::Autolock _l(mDeviceLock);
    if (NULL == mDevice) {
        struct pcm_config config;
        int dev_id = 0;
        int retry = 0;
        static const int MAX_RETRY_COUNT = 3;

        mALSACardID = find_alsa_card_by_name(mALSAName);
        if (mALSACardID < 0)
            return;

        memset(&config, 0, sizeof(config));
        config.channels        = mChannelCnt;
        config.rate            = mFramesPerSec;
        config.period_size     = mFramesPerChunk;
        config.period_count    = mBufferChunks;
        config.format          = mALSAFormat;
        // start_threshold is in audio frames. The default behavior
        // is to fill period_size*period_count frames before outputing
        // audio. Setting to 1 will start the DMA immediately. Our first
        // write is a full chunk, so we have 10ms to get back with the next
        // chunk before we underflow. This number could be increased if
        // problems arise.
        config.start_threshold = 1;

        ALOGI("calling pcm_open() for output, mALSACardID = %d, dev_id %d, rate = %u, "
            "%d channels, framesPerChunk = %d, alsaFormat = %d",
              mALSACardID, dev_id, config.rate, config.channels, config.period_size, config.format);
        while (1) {
            // Use PCM_MONOTONIC clock for get_presentation_position.
            mDevice = pcm_open(mALSACardID, dev_id,
                    PCM_OUT | PCM_NORESTART | PCM_MONOTONIC, &config);
            if (initCheck() == OK)
                break;
            if (retry++ >= MAX_RETRY_COUNT) {
                ALOGI("out of retries, giving up");
                break;
            }
            /* try again after a delay.  on hotplug, there appears to
             * be a race where the pcm device node isn't available on
             * first open try.
             */
            pcm_close(mDevice);
            mDevice = NULL;
            sleep(1);
            ALOGI("retrying pcm_open() after delay");
        }
        mDeviceExtFd = mDevice
                        ? *(reinterpret_cast<int*>(mDevice))
                        : -1;
        mState = OUT_OF_SYNC;
    }
}

status_t AudioOutput::getNextWriteTimestamp(int64_t* timestamp,
                                            bool* discon) {
    int64_t  dma_start_time;
    int64_t  frames_queued_to_driver;
    status_t ret;

    *discon = false;
    if (hasFatalError())
        return UNKNOWN_ERROR;

    ret = getDMAStartData(&dma_start_time,
                          &frames_queued_to_driver);
    if (OK != ret) {
        if (mLastNextWriteTimeValid) {
            if (!hasFatalError())
                ALOGE("Underflow detected for output \"%s\"", getOutputName());
            *discon = true;
        }

        goto bailout;
    }

    if (mLastNextWriteTimeValid && (mLastDMAStartTime != dma_start_time)) {
        *discon = true;
        ret = UNKNOWN_ERROR;

        ALOGE("Discontinuous DMA start time detected for output \"%s\"."
              "DMA start time is %lld, but last DMA start time was %lld.",
              getOutputName(), dma_start_time, mLastDMAStartTime);

        goto bailout;
    }

    mLastDMAStartTime = dma_start_time;

    mFramesToLocalTime.a_zero = 0;
    mFramesToLocalTime.b_zero = dma_start_time;

    if (!mFramesToLocalTime.doForwardTransform(frames_queued_to_driver,
                                               timestamp)) {
        ALOGE("Overflow when attempting to compute next write time for output"
              " \"%s\".  Frames Queued To Driver = %lld, DMA Start Time = %lld",
              getOutputName(), frames_queued_to_driver, dma_start_time);
        ret = UNKNOWN_ERROR;
        goto bailout;
    }

    mLastNextWriteTime = *timestamp;
    mLastNextWriteTimeValid = true;

    // If we have a valuid timestamp, DMA has started so advance the state.
    if (mState == PRIMED)
        mState = DMA_START;

    return OK;

bailout:
    mLastNextWriteTimeValid = false;
    // If we underflow, reset this output now.
    if (mState > PRIMED) {
        reset();
    }

    return ret;
}

bool AudioOutput::getLastNextWriteTSValid() const {
    return mLastNextWriteTimeValid;
}

int64_t AudioOutput::getLastNextWriteTS() const {
    return mLastNextWriteTime;
}

uint32_t AudioOutput::getExternalDelay_uSec() const {
    return mExternalDelayUSec;
}

void AudioOutput::setExternalDelay_uSec(uint32_t delay_usec) {
    mExternalDelayUSec = delay_usec;
}

void AudioOutput::reset() {
    if (hasFatalError())
        return;

    // Flush the driver level.
    cleanupResources();
    openPCMDevice();
    mFramesQueuedToDriver = 0;
    mLastNextWriteTimeValid = false;

    if (OK == initCheck()) {
        ALOGE("Reset %s", mALSAName);
    } else {
        ALOGE("Reset for %s failed, device is a zombie pending cleanup.", mALSAName);
        cleanupResources();
        mState = FATAL;
    }
}

status_t AudioOutput::getDMAStartData(
        int64_t* dma_start_time,
        int64_t* frames_queued_to_driver) {
    int ret;
#if 1 /* not implemented in driver yet, just fake it */
    *dma_start_time = mLastDMAStartTime;
    ret = 0;
#endif

    // If the get start time ioctl fails with an error of EBADFD, then our
    // underlying audio device is in the DISCONNECTED state.  The only reason
    // this should happen is that HDMI was unplugged while we were running, and
    // the audio driver needed to immediately shut down the driver without
    // involving the application level.  We should enter the fatal state, and
    // wait until the app level catches up to our view of the world (at which
    // point in time we will go through a plug/unplug cycle which should clean
    // things up).
    if (ret < 0) {
        if (EBADFD == errno) {
            ALOGI("Failed to ioctl to %s, output is probably disconnected."
                  " Going into zombie state to await cleanup.", mALSAName);
            cleanupResources();
            mState = FATAL;
        }

        return UNKNOWN_ERROR;
    }

    *frames_queued_to_driver = mFramesQueuedToDriver;
    return OK;
}

void AudioOutput::processOneChunk(const uint8_t* data, size_t len,
                                  bool hasActiveOutputs, audio_format_t format) {
    switch (mState) {
    case OUT_OF_SYNC:
        primeOutput(hasActiveOutputs);
        break;
    case PRIMED:
        if (mPrimeTimeoutChunks < kPrimeTimeoutChunks)
            mPrimeTimeoutChunks++;
        else
            // Uh-oh, DMA didn't start. Reset and try again.
            reset();

        break;
    case DMA_START:
        // Don't push data when primed and waiting for buffer alignment.
        // We need to align the ALSA buffers first.
        break;
    case ACTIVE: {
        doPCMWrite(data, len, format);
        // we use input frame size here (mBytesPerFrame is alsa device frame size)
        const size_t frameSize = mChannelCnt * audio_bytes_per_sample(format);
        mFramesQueuedToDriver += len / frameSize;
        } break;
    default:
        // Do nothing.
        break;
    }

}

void AudioOutput::doPCMWrite(const uint8_t* data, size_t len, audio_format_t format) {
    if (len == 0 || hasFatalError())
        return;

    // If write fails with an error of EBADFD, then our underlying audio
    // device is in a pretty bad state.  This common cause of this is
    // that HDMI was unplugged while we were running, and the audio
    // driver needed to immediately shut down the driver without
    // involving the application level.  When this happens, the HDMI
    // audio device is put into the DISCONNECTED state, and calls to
    // write will return EBADFD.
#if 1
    /* Intel HDMI appears to be locked at 24bit PCM, but Android
     * will send data in the format specified in adev_open_output_stream().
     */
    LOG_ALWAYS_FATAL_IF(mALSAFormat != PCM_FORMAT_S24_LE,
            "Fugu alsa device format(%d) must be PCM_FORMAT_S24_LE", mALSAFormat);

    int err = BAD_VALUE;
    switch(format) {
    case AUDIO_FORMAT_IEC61937:
    case AUDIO_FORMAT_PCM_16_BIT: {
        const size_t outputSize = len * 2;
        if (outputSize > mStagingSize) {
            void *buf = realloc(mStagingBuf, outputSize);
            if (buf == NULL) {
                ALOGE("%s: memory allocation for conversion buffer failed", __func__);
                return;
            }
            mStagingBuf = buf;
            mStagingSize = outputSize;
        }
        memcpy_to_q8_23_from_i16((int32_t*)mStagingBuf, (const int16_t*)data, len >> 1);
        err = pcm_write(mDevice, mStagingBuf, outputSize);
    } break;
    case AUDIO_FORMAT_PCM_8_24_BIT:
        err = pcm_write(mDevice, data, len);
        break;
    default:
        LOG_ALWAYS_FATAL("Fugu input format(%#x) should be 16 bit or 8_24 bit pcm", format);
        break;
    }

#else

    int err = pcm_write(mDevice, data, len);
#endif
    if ((err < 0) && (EBADFD == errno)) {
        ALOGI("Failed to write to %s, output is probably disconnected."
              " Going into zombie state to await cleanup.", mALSAName);
        cleanupResources();
        mState = FATAL;
    }
    else if (err < 0) {
        ALOGW_IF(!mReportedWriteFail, "pcm_write failed err %d", err);
        mReportedWriteFail = true;
    }
    else {
        mReportedWriteFail = false;
#if 1 /* not implemented in driver yet, just fake it */
        LocalClock lc;
        mLastDMAStartTime = lc.getLocalTime();
#endif
    }
}

void AudioOutput::setVolume(float vol) {
    Mutex::Autolock _l(mVolumeLock);
    if (mVolume != vol) {
        mVolume = vol;
        mVolParamsDirty = true;
    }
}

void AudioOutput::setMute(bool mute) {
    Mutex::Autolock _l(mVolumeLock);
    if (mMute != mute) {
        mMute = mute;
        mVolParamsDirty = true;
    }
}

void AudioOutput::setOutputIsFixed(bool fixed) {
    Mutex::Autolock _l(mVolumeLock);
    if (mOutputFixed != fixed) {
        mOutputFixed = fixed;
        mVolParamsDirty = true;
    }
}

void AudioOutput::setFixedOutputLevel(float level) {
    Mutex::Autolock _l(mVolumeLock);
    if (mFixedLvl != level) {
        mFixedLvl = level;
        mVolParamsDirty = true;
    }
}

int  AudioOutput::getHardwareTimestamp(size_t *pAvail,
                            struct timespec *pTimestamp)
{
    Mutex::Autolock _l(mDeviceLock);
    if (!mDevice) {
       ALOGW("pcm device unavailable - reinitialize  timestamp");
       return -1;
    }
    return pcm_get_htimestamp(mDevice, pAvail, pTimestamp);
}

}  // namespace android
