/*
 * Copyright 2015 The Android Open Source Project
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

#define LOG_TAG "AAudioStream"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <atomic>
#include <stdint.h>
#include <aaudio/AAudio.h>

#include "AudioStreamBuilder.h"
#include "AudioStream.h"
#include "AudioClock.h"

using namespace aaudio;

AudioStream::AudioStream()
        : mPlayerBase(new MyPlayerBase(this))
{
    // mThread is a pthread_t of unknown size so we need memset.
    memset(&mThread, 0, sizeof(mThread));
    setPeriodNanoseconds(0);
}

AudioStream::~AudioStream() {
    ALOGD("destroying %p, state = %s", this, AAudio_convertStreamStateToText(getState()));
    // If the stream is deleted when OPEN or in use then audio resources will leak.
    // This would indicate an internal error. So we want to find this ASAP.
    LOG_ALWAYS_FATAL_IF(!(getState() == AAUDIO_STREAM_STATE_CLOSED
                          || getState() == AAUDIO_STREAM_STATE_UNINITIALIZED
                          || getState() == AAUDIO_STREAM_STATE_DISCONNECTED),
                        "aaudio stream still in use, state = %s",
                        AAudio_convertStreamStateToText(getState()));

    mPlayerBase->clearParentReference(); // remove reference to this AudioStream
}

static const char *AudioStream_convertSharingModeToShortText(aaudio_sharing_mode_t sharingMode) {
    const char *result;
    switch (sharingMode) {
        case AAUDIO_SHARING_MODE_EXCLUSIVE:
            result = "EX";
            break;
        case AAUDIO_SHARING_MODE_SHARED:
            result = "SH";
            break;
        default:
            result = "?!";
            break;
    }
    return result;
}

aaudio_result_t AudioStream::open(const AudioStreamBuilder& builder)
{
    // Call here as well because the AAudioService will call this without calling build().
    aaudio_result_t result = builder.validate();
    if (result != AAUDIO_OK) {
        return result;
    }

    // Copy parameters from the Builder because the Builder may be deleted after this call.
    mSamplesPerFrame = builder.getSamplesPerFrame();
    mSampleRate = builder.getSampleRate();
    mDeviceId = builder.getDeviceId();
    mFormat = builder.getFormat();
    mSharingMode = builder.getSharingMode();
    mSharingModeMatchRequired = builder.isSharingModeMatchRequired();

    mPerformanceMode = builder.getPerformanceMode();

    // callbacks
    mFramesPerDataCallback = builder.getFramesPerDataCallback();
    mDataCallbackProc = builder.getDataCallbackProc();
    mErrorCallbackProc = builder.getErrorCallbackProc();
    mDataCallbackUserData = builder.getDataCallbackUserData();
    mErrorCallbackUserData = builder.getErrorCallbackUserData();

    // This is very helpful for debugging in the future. Please leave it in.
    ALOGI("AudioStream::open() rate = %d, channels = %d, format = %d, sharing = %s, dir = %s",
          mSampleRate, mSamplesPerFrame, mFormat,
          AudioStream_convertSharingModeToShortText(mSharingMode),
          (getDirection() == AAUDIO_DIRECTION_OUTPUT) ? "OUTPUT" : "INPUT");
    ALOGI("AudioStream::open() device = %d, perfMode = %d, callback: %s with frames = %d",
          mDeviceId, mPerformanceMode,
          (mDataCallbackProc == nullptr ? "OFF" : "ON"),
          mFramesPerDataCallback);

    return AAUDIO_OK;
}


aaudio_result_t AudioStream::waitForStateChange(aaudio_stream_state_t currentState,
                                                aaudio_stream_state_t *nextState,
                                                int64_t timeoutNanoseconds)
{
    aaudio_result_t result = updateStateMachine();
    if (result != AAUDIO_OK) {
        return result;
    }

    int64_t durationNanos = 20 * AAUDIO_NANOS_PER_MILLISECOND; // arbitrary
    aaudio_stream_state_t state = getState();
    while (state == currentState && timeoutNanoseconds > 0) {
        if (durationNanos > timeoutNanoseconds) {
            durationNanos = timeoutNanoseconds;
        }
        AudioClock::sleepForNanos(durationNanos);
        timeoutNanoseconds -= durationNanos;

        aaudio_result_t result = updateStateMachine();
        if (result != AAUDIO_OK) {
            return result;
        }

        state = getState();
    }
    if (nextState != nullptr) {
        *nextState = state;
    }
    return (state == currentState) ? AAUDIO_ERROR_TIMEOUT : AAUDIO_OK;
}

// This registers the callback thread with the server before
// passing control to the app. This gives the server an opportunity to boost
// the thread's performance characteristics.
void* AudioStream::wrapUserThread() {
    void* procResult = nullptr;
    mThreadRegistrationResult = registerThread();
    if (mThreadRegistrationResult == AAUDIO_OK) {
        // Run callback loop. This may take a very long time.
        procResult = mThreadProc(mThreadArg);
        mThreadRegistrationResult = unregisterThread();
    }
    return procResult;
}

// This is the entry point for the new thread created by createThread().
// It converts the 'C' function call to a C++ method call.
static void* AudioStream_internalThreadProc(void* threadArg) {
    AudioStream *audioStream = (AudioStream *) threadArg;
    return audioStream->wrapUserThread();
}

// This is not exposed in the API.
// But it is still used internally to implement callbacks for MMAP mode.
aaudio_result_t AudioStream::createThread(int64_t periodNanoseconds,
                                     aaudio_audio_thread_proc_t threadProc,
                                     void* threadArg)
{
    if (mHasThread) {
        ALOGE("AudioStream::createThread() - mHasThread already true");
        return AAUDIO_ERROR_INVALID_STATE;
    }
    if (threadProc == nullptr) {
        return AAUDIO_ERROR_NULL;
    }
    // Pass input parameters to the background thread.
    mThreadProc = threadProc;
    mThreadArg = threadArg;
    setPeriodNanoseconds(periodNanoseconds);
    int err = pthread_create(&mThread, nullptr, AudioStream_internalThreadProc, this);
    if (err != 0) {
        return AAudioConvert_androidToAAudioResult(-errno);
    } else {
        mHasThread = true;
        return AAUDIO_OK;
    }
}

aaudio_result_t AudioStream::joinThread(void** returnArg, int64_t timeoutNanoseconds)
{
    if (!mHasThread) {
        ALOGE("AudioStream::joinThread() - but has no thread");
        return AAUDIO_ERROR_INVALID_STATE;
    }
#if 0
    // TODO implement equivalent of pthread_timedjoin_np()
    struct timespec abstime;
    int err = pthread_timedjoin_np(mThread, returnArg, &abstime);
#else
    int err = pthread_join(mThread, returnArg);
#endif
    mHasThread = false;
    return err ? AAudioConvert_androidToAAudioResult(-errno) : mThreadRegistrationResult;
}


#if AAUDIO_USE_VOLUME_SHAPER
android::media::VolumeShaper::Status AudioStream::applyVolumeShaper(
        const android::media::VolumeShaper::Configuration& configuration __unused,
        const android::media::VolumeShaper::Operation& operation __unused) {
    ALOGW("applyVolumeShaper() is not supported");
    return android::media::VolumeShaper::Status::ok();
}
#endif

AudioStream::MyPlayerBase::MyPlayerBase(AudioStream *parent) : mParent(parent) {
}

AudioStream::MyPlayerBase::~MyPlayerBase() {
    ALOGV("MyPlayerBase::~MyPlayerBase(%p) deleted", this);
}

void AudioStream::MyPlayerBase::registerWithAudioManager() {
    if (!mRegistered) {
        init(android::PLAYER_TYPE_AAUDIO, AUDIO_USAGE_MEDIA);
        mRegistered = true;
    }
}

void AudioStream::MyPlayerBase::unregisterWithAudioManager() {
    if (mRegistered) {
        baseDestroy();
        mRegistered = false;
    }
}


void AudioStream::MyPlayerBase::destroy() {
    unregisterWithAudioManager();
}
