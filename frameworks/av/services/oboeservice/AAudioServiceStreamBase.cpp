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

#define LOG_TAG "AAudioServiceStreamBase"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <iomanip>
#include <iostream>
#include <mutex>

#include "binding/IAAudioService.h"
#include "binding/AAudioServiceMessage.h"
#include "utility/AudioClock.h"

#include "AAudioEndpointManager.h"
#include "AAudioService.h"
#include "AAudioServiceEndpoint.h"
#include "AAudioServiceStreamBase.h"
#include "TimestampScheduler.h"

using namespace android;  // TODO just import names needed
using namespace aaudio;   // TODO just import names needed

/**
 * Base class for streams in the service.
 * @return
 */

AAudioServiceStreamBase::AAudioServiceStreamBase(AAudioService &audioService)
        : mUpMessageQueue(nullptr)
        , mTimestampThread()
        , mAtomicTimestamp()
        , mAudioService(audioService) {
    mMmapClient.clientUid = -1;
    mMmapClient.clientPid = -1;
    mMmapClient.packageName = String16("");
}

AAudioServiceStreamBase::~AAudioServiceStreamBase() {
    ALOGD("AAudioServiceStreamBase::~AAudioServiceStreamBase() destroying %p", this);
    // If the stream is deleted when OPEN or in use then audio resources will leak.
    // This would indicate an internal error. So we want to find this ASAP.
    LOG_ALWAYS_FATAL_IF(!(getState() == AAUDIO_STREAM_STATE_CLOSED
                        || getState() == AAUDIO_STREAM_STATE_UNINITIALIZED
                        || getState() == AAUDIO_STREAM_STATE_DISCONNECTED),
                        "service stream still open, state = %d", getState());
}

std::string AAudioServiceStreamBase::dumpHeader() {
    return std::string("    T   Handle   UId Run State Format Burst Chan Capacity");
}

std::string AAudioServiceStreamBase::dump() const {
    std::stringstream result;

    result << "    0x" << std::setfill('0') << std::setw(8) << std::hex << mHandle
           << std::dec << std::setfill(' ') ;
    result << std::setw(6) << mMmapClient.clientUid;
    result << std::setw(4) << (isRunning() ? "yes" : " no");
    result << std::setw(6) << getState();
    result << std::setw(7) << getFormat();
    result << std::setw(6) << mFramesPerBurst;
    result << std::setw(5) << getSamplesPerFrame();
    result << std::setw(9) << getBufferCapacity();

    return result.str();
}

aaudio_result_t AAudioServiceStreamBase::open(const aaudio::AAudioStreamRequest &request,
                                              aaudio_sharing_mode_t sharingMode) {
    AAudioEndpointManager &mEndpointManager = AAudioEndpointManager::getInstance();
    aaudio_result_t result = AAUDIO_OK;

    mMmapClient.clientUid = request.getUserId();
    mMmapClient.clientPid = request.getProcessId();
    mMmapClient.packageName.setTo(String16("")); // TODO What should we do here?

    // Limit scope of lock to avoid recursive lock in close().
    {
        std::lock_guard<std::mutex> lock(mUpMessageQueueLock);
        if (mUpMessageQueue != nullptr) {
            ALOGE("AAudioServiceStreamBase::open() called twice");
            return AAUDIO_ERROR_INVALID_STATE;
        }

        mUpMessageQueue = new SharedRingBuffer();
        result = mUpMessageQueue->allocate(sizeof(AAudioServiceMessage),
                                           QUEUE_UP_CAPACITY_COMMANDS);
        if (result != AAUDIO_OK) {
            goto error;
        }

        mServiceEndpoint = mEndpointManager.openEndpoint(mAudioService,
                                                         request,
                                                         sharingMode);
        if (mServiceEndpoint == nullptr) {
            ALOGE("AAudioServiceStreamBase::open() openEndpoint() failed");
            result = AAUDIO_ERROR_UNAVAILABLE;
            goto error;
        }
        mFramesPerBurst = mServiceEndpoint->getFramesPerBurst();
        copyFrom(*mServiceEndpoint);
    }
    return result;

error:
    close();
    return result;
}

aaudio_result_t AAudioServiceStreamBase::close() {
    aaudio_result_t result = AAUDIO_OK;
    if (getState() == AAUDIO_STREAM_STATE_CLOSED) {
        return AAUDIO_OK;
    }

    stop();

    if (mServiceEndpoint == nullptr) {
        result = AAUDIO_ERROR_INVALID_STATE;
    } else {
        mServiceEndpoint->unregisterStream(this);
        AAudioEndpointManager &mEndpointManager = AAudioEndpointManager::getInstance();
        mEndpointManager.closeEndpoint(mServiceEndpoint);
        mServiceEndpoint.clear();
    }

    {
        std::lock_guard<std::mutex> lock(mUpMessageQueueLock);
        stopTimestampThread();
        delete mUpMessageQueue;
        mUpMessageQueue = nullptr;
    }

    setState(AAUDIO_STREAM_STATE_CLOSED);
    return result;
}

aaudio_result_t AAudioServiceStreamBase::startDevice() {
    mClientHandle = AUDIO_PORT_HANDLE_NONE;
    return mServiceEndpoint->startStream(this, &mClientHandle);
}

/**
 * Start the flow of audio data.
 *
 * An AAUDIO_SERVICE_EVENT_STARTED will be sent to the client when complete.
 */
aaudio_result_t AAudioServiceStreamBase::start() {
    aaudio_result_t result = AAUDIO_OK;
    if (isRunning()) {
        return AAUDIO_OK;
    }

    if (mServiceEndpoint == nullptr) {
        ALOGE("AAudioServiceStreamBase::start() missing endpoint");
        result = AAUDIO_ERROR_INVALID_STATE;
        goto error;
    }

    // Start with fresh presentation timestamps.
    mAtomicTimestamp.clear();

    mClientHandle = AUDIO_PORT_HANDLE_NONE;
    result = startDevice();
    if (result != AAUDIO_OK) goto error;

    // This should happen at the end of the start.
    sendServiceEvent(AAUDIO_SERVICE_EVENT_STARTED);
    setState(AAUDIO_STREAM_STATE_STARTED);
    mThreadEnabled.store(true);
    result = mTimestampThread.start(this);
    if (result != AAUDIO_OK) goto error;

    return result;

error:
    disconnect();
    return result;
}

aaudio_result_t AAudioServiceStreamBase::pause() {
    aaudio_result_t result = AAUDIO_OK;
    if (!isRunning()) {
        return result;
    }
    if (mServiceEndpoint == nullptr) {
        ALOGE("AAudioServiceStreamShared::pause() missing endpoint");
        return AAUDIO_ERROR_INVALID_STATE;
    }

    // Send it now because the timestamp gets rounded up when stopStream() is called below.
    // Also we don't need the timestamps while we are shutting down.
    sendCurrentTimestamp();

    result = stopTimestampThread();
    if (result != AAUDIO_OK) {
        disconnect();
        return result;
    }

    result = mServiceEndpoint->stopStream(this, mClientHandle);
    if (result != AAUDIO_OK) {
        ALOGE("AAudioServiceStreamShared::pause() mServiceEndpoint returned %d", result);
        disconnect(); // TODO should we return or pause Base first?
    }

    sendServiceEvent(AAUDIO_SERVICE_EVENT_PAUSED);
    setState(AAUDIO_STREAM_STATE_PAUSED);
    return result;
}

aaudio_result_t AAudioServiceStreamBase::stop() {
    aaudio_result_t result = AAUDIO_OK;
    if (!isRunning()) {
        return result;
    }

    if (mServiceEndpoint == nullptr) {
        ALOGE("AAudioServiceStreamShared::stop() missing endpoint");
        return AAUDIO_ERROR_INVALID_STATE;
    }

    // Send it now because the timestamp gets rounded up when stopStream() is called below.
    // Also we don't need the timestamps while we are shutting down.
    sendCurrentTimestamp(); // warning - this calls a virtual function
    result = stopTimestampThread();
    if (result != AAUDIO_OK) {
        disconnect();
        return result;
    }

    // TODO wait for data to be played out
    result = mServiceEndpoint->stopStream(this, mClientHandle);
    if (result != AAUDIO_OK) {
        ALOGE("AAudioServiceStreamShared::stop() mServiceEndpoint returned %d", result);
        disconnect();
        // TODO what to do with result here?
    }

    sendServiceEvent(AAUDIO_SERVICE_EVENT_STOPPED);
    setState(AAUDIO_STREAM_STATE_STOPPED);
    return result;
}

aaudio_result_t AAudioServiceStreamBase::stopTimestampThread() {
    aaudio_result_t result = AAUDIO_OK;
    // clear flag that tells thread to loop
    if (mThreadEnabled.exchange(false)) {
        result = mTimestampThread.stop();
    }
    return result;
}

aaudio_result_t AAudioServiceStreamBase::flush() {
    if (getState() != AAUDIO_STREAM_STATE_PAUSED) {
        ALOGE("AAudioServiceStreamBase::flush() stream not paused, state = %s",
              AAudio_convertStreamStateToText(mState));
        return AAUDIO_ERROR_INVALID_STATE;
    }
    // Data will get flushed when the client receives the FLUSHED event.
    sendServiceEvent(AAUDIO_SERVICE_EVENT_FLUSHED);
    setState(AAUDIO_STREAM_STATE_FLUSHED);
    return AAUDIO_OK;
}

// implement Runnable, periodically send timestamps to client
void AAudioServiceStreamBase::run() {
    ALOGD("AAudioServiceStreamBase::run() entering ----------------");
    TimestampScheduler timestampScheduler;
    timestampScheduler.setBurstPeriod(mFramesPerBurst, getSampleRate());
    timestampScheduler.start(AudioClock::getNanoseconds());
    int64_t nextTime = timestampScheduler.nextAbsoluteTime();
    while(mThreadEnabled.load()) {
        if (AudioClock::getNanoseconds() >= nextTime) {
            aaudio_result_t result = sendCurrentTimestamp();
            if (result != AAUDIO_OK) {
                break;
            }
            nextTime = timestampScheduler.nextAbsoluteTime();
        } else  {
            // Sleep until it is time to send the next timestamp.
            // TODO Wait for a signal with a timeout so that we can stop more quickly.
            AudioClock::sleepUntilNanoTime(nextTime);
        }
    }
    ALOGD("AAudioServiceStreamBase::run() exiting ----------------");
}

void AAudioServiceStreamBase::disconnect() {
    if (getState() != AAUDIO_STREAM_STATE_DISCONNECTED) {
        sendServiceEvent(AAUDIO_SERVICE_EVENT_DISCONNECTED);
        setState(AAUDIO_STREAM_STATE_DISCONNECTED);
    }
}

aaudio_result_t AAudioServiceStreamBase::sendServiceEvent(aaudio_service_event_t event,
                                               double  dataDouble,
                                               int64_t dataLong) {
    AAudioServiceMessage command;
    command.what = AAudioServiceMessage::code::EVENT;
    command.event.event = event;
    command.event.dataDouble = dataDouble;
    command.event.dataLong = dataLong;
    return writeUpMessageQueue(&command);
}

aaudio_result_t AAudioServiceStreamBase::writeUpMessageQueue(AAudioServiceMessage *command) {
    std::lock_guard<std::mutex> lock(mUpMessageQueueLock);
    if (mUpMessageQueue == nullptr) {
        ALOGE("writeUpMessageQueue(): mUpMessageQueue null! - stream not open");
        return AAUDIO_ERROR_NULL;
    }
    int32_t count = mUpMessageQueue->getFifoBuffer()->write(command, 1);
    if (count != 1) {
        ALOGE("writeUpMessageQueue(): Queue full. Did client die?");
        return AAUDIO_ERROR_WOULD_BLOCK;
    } else {
        return AAUDIO_OK;
    }
}

aaudio_result_t AAudioServiceStreamBase::sendCurrentTimestamp() {
    AAudioServiceMessage command;
    // Send a timestamp for the clock model.
    aaudio_result_t result = getFreeRunningPosition(&command.timestamp.position,
                                                    &command.timestamp.timestamp);
    if (result == AAUDIO_OK) {
        ALOGV("sendCurrentTimestamp() SERVICE  %8lld at %lld",
              (long long) command.timestamp.position,
              (long long) command.timestamp.timestamp);
        command.what = AAudioServiceMessage::code::TIMESTAMP_SERVICE;
        result = writeUpMessageQueue(&command);

        if (result == AAUDIO_OK) {
            // Send a hardware timestamp for presentation time.
            result = getHardwareTimestamp(&command.timestamp.position,
                                          &command.timestamp.timestamp);
            if (result == AAUDIO_OK) {
                ALOGV("sendCurrentTimestamp() HARDWARE %8lld at %lld",
                      (long long) command.timestamp.position,
                      (long long) command.timestamp.timestamp);
                command.what = AAudioServiceMessage::code::TIMESTAMP_HARDWARE;
                result = writeUpMessageQueue(&command);
            }
        }
    }

    if (result == AAUDIO_ERROR_UNAVAILABLE) { // TODO review best error code
        result = AAUDIO_OK; // just not available yet, try again later
    }
    return result;
}

/**
 * Get an immutable description of the in-memory queues
 * used to communicate with the underlying HAL or Service.
 */
aaudio_result_t AAudioServiceStreamBase::getDescription(AudioEndpointParcelable &parcelable) {
    {
        std::lock_guard<std::mutex> lock(mUpMessageQueueLock);
        if (mUpMessageQueue == nullptr) {
            ALOGE("getDescription(): mUpMessageQueue null! - stream not open");
            return AAUDIO_ERROR_NULL;
        }
        // Gather information on the message queue.
        mUpMessageQueue->fillParcelable(parcelable,
                                        parcelable.mUpMessageQueueParcelable);
    }
    return getAudioDataDescription(parcelable);
}

void AAudioServiceStreamBase::onVolumeChanged(float volume) {
    sendServiceEvent(AAUDIO_SERVICE_EVENT_VOLUME, volume);
}
