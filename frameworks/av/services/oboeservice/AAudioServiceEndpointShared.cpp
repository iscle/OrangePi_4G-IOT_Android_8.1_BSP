/*
 * Copyright (C) 2017 The Android Open Source Project
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


#define LOG_TAG "AAudioServiceEndpointShared"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <iomanip>
#include <iostream>
#include <sstream>

#include "binding/AAudioServiceMessage.h"
#include "client/AudioStreamInternal.h"
#include "client/AudioStreamInternalPlay.h"
#include "core/AudioStreamBuilder.h"

#include "AAudioServiceEndpointShared.h"
#include "AAudioServiceStreamShared.h"
#include "AAudioServiceStreamMMAP.h"
#include "AAudioMixer.h"
#include "AAudioService.h"

using namespace android;
using namespace aaudio;

// This is the maximum size in frames. The effective size can be tuned smaller at runtime.
#define DEFAULT_BUFFER_CAPACITY   (48 * 8)

std::string AAudioServiceEndpointShared::dump() const {
    std::stringstream result;

    result << "  SHARED: sharing exclusive stream with handle = 0x"
           << std::setfill('0') << std::setw(8)
           << std::hex << mStreamInternal->getServiceHandle()
           << std::dec << std::setfill(' ');
    result << "\n";
    result << "    Running Stream Count: " << mRunningStreamCount << "\n";

    result << AAudioServiceEndpoint::dump();
    return result.str();
}

// Share an AudioStreamInternal.
aaudio_result_t AAudioServiceEndpointShared::open(const aaudio::AAudioStreamRequest &request) {
    aaudio_result_t result = AAUDIO_OK;
    const AAudioStreamConfiguration &configuration = request.getConstantConfiguration();

    mRequestedDeviceId = configuration.getDeviceId();
    setDirection(configuration.getDirection());

    AudioStreamBuilder builder;
    builder.setSharingMode(AAUDIO_SHARING_MODE_EXCLUSIVE);
    // Don't fall back to SHARED because that would cause recursion.
    builder.setSharingModeMatchRequired(true);
    builder.setDeviceId(mRequestedDeviceId);
    builder.setFormat(configuration.getFormat());
    builder.setSampleRate(configuration.getSampleRate());
    builder.setSamplesPerFrame(configuration.getSamplesPerFrame());
    builder.setDirection(configuration.getDirection());
    builder.setBufferCapacity(DEFAULT_BUFFER_CAPACITY);

    result = mStreamInternal->open(builder);

    setSampleRate(mStreamInternal->getSampleRate());
    setSamplesPerFrame(mStreamInternal->getSamplesPerFrame());
    setDeviceId(mStreamInternal->getDeviceId());
    mFramesPerBurst = mStreamInternal->getFramesPerBurst();

    return result;
}

aaudio_result_t AAudioServiceEndpointShared::close() {
    return getStreamInternal()->close();
}

// Glue between C and C++ callbacks.
static void *aaudio_endpoint_thread_proc(void *context) {
    AAudioServiceEndpointShared *endpoint = (AAudioServiceEndpointShared *) context;
    if (endpoint != NULL) {
        return endpoint->callbackLoop();
    } else {
        return NULL;
    }
}

aaudio_result_t aaudio::AAudioServiceEndpointShared::startSharingThread_l() {
    // Launch the callback loop thread.
    int64_t periodNanos = getStreamInternal()->getFramesPerBurst()
                          * AAUDIO_NANOS_PER_SECOND
                          / getSampleRate();
    mCallbackEnabled.store(true);
    return getStreamInternal()->createThread(periodNanos, aaudio_endpoint_thread_proc, this);
}

aaudio_result_t aaudio::AAudioServiceEndpointShared::stopSharingThread() {
    mCallbackEnabled.store(false);
    aaudio_result_t result = getStreamInternal()->joinThread(NULL);
    return result;
}

aaudio_result_t AAudioServiceEndpointShared::startStream(sp<AAudioServiceStreamBase> sharedStream,
                                                         audio_port_handle_t *clientHandle) {
    aaudio_result_t result = AAUDIO_OK;

    {
        std::lock_guard<std::mutex> lock(mLockStreams);
        if (++mRunningStreamCount == 1) { // atomic
            result = getStreamInternal()->requestStart();
            if (result != AAUDIO_OK) {
                --mRunningStreamCount;
            } else {
                result = startSharingThread_l();
                if (result != AAUDIO_OK) {
                    getStreamInternal()->requestStop();
                    --mRunningStreamCount;
                }
            }
        }
    }

    if (result == AAUDIO_OK) {
        result = getStreamInternal()->startClient(sharedStream->getAudioClient(), clientHandle);
        if (result != AAUDIO_OK) {
            if (--mRunningStreamCount == 0) { // atomic
                stopSharingThread();
                getStreamInternal()->requestStop();
            }
        }
    }

    return result;
}

aaudio_result_t AAudioServiceEndpointShared::stopStream(sp<AAudioServiceStreamBase> sharedStream,
                                                        audio_port_handle_t clientHandle) {
    // Don't lock here because the disconnectRegisteredStreams also uses the lock.

    // Ignore result.
    (void) getStreamInternal()->stopClient(clientHandle);

    if (--mRunningStreamCount == 0) { // atomic
        stopSharingThread();
        getStreamInternal()->requestStop();
    }
    return AAUDIO_OK;
}

// Get timestamp that was written by the real-time service thread, eg. mixer.
aaudio_result_t AAudioServiceEndpointShared::getFreeRunningPosition(int64_t *positionFrames,
                                                                  int64_t *timeNanos) {
    if (mAtomicTimestamp.isValid()) {
        Timestamp timestamp = mAtomicTimestamp.read();
        *positionFrames = timestamp.getPosition();
        *timeNanos = timestamp.getNanoseconds();
        return AAUDIO_OK;
    } else {
        return AAUDIO_ERROR_UNAVAILABLE;
    }
}

aaudio_result_t AAudioServiceEndpointShared::getTimestamp(int64_t *positionFrames,
                                                          int64_t *timeNanos) {
    return mStreamInternal->getTimestamp(CLOCK_MONOTONIC, positionFrames, timeNanos);
}
