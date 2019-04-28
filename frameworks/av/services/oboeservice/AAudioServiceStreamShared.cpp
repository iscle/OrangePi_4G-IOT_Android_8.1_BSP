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

#define LOG_TAG "AAudioServiceStreamShared"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <iomanip>
#include <iostream>
#include <mutex>

#include <aaudio/AAudio.h>

#include "binding/IAAudioService.h"

#include "binding/AAudioServiceMessage.h"
#include "AAudioServiceStreamBase.h"
#include "AAudioServiceStreamShared.h"
#include "AAudioEndpointManager.h"
#include "AAudioService.h"
#include "AAudioServiceEndpoint.h"

using namespace android;
using namespace aaudio;

#define MIN_BURSTS_PER_BUFFER       2
#define DEFAULT_BURSTS_PER_BUFFER   16
// This is an arbitrary range. TODO review.
#define MAX_FRAMES_PER_BUFFER       (32 * 1024)

AAudioServiceStreamShared::AAudioServiceStreamShared(AAudioService &audioService)
    : AAudioServiceStreamBase(audioService)
    , mTimestampPositionOffset(0)
    , mXRunCount(0) {
}

std::string AAudioServiceStreamShared::dumpHeader() {
    std::stringstream result;
    result << AAudioServiceStreamBase::dumpHeader();
    result << "    Write#     Read#   Avail   XRuns";
    return result.str();
}

std::string AAudioServiceStreamShared::dump() const {
    std::stringstream result;

    result << AAudioServiceStreamBase::dump();

    auto fifo = mAudioDataQueue->getFifoBuffer();
    int32_t readCounter = fifo->getReadCounter();
    int32_t writeCounter = fifo->getWriteCounter();
    result << std::setw(10) << writeCounter;
    result << std::setw(10) << readCounter;
    result << std::setw(8) << (writeCounter - readCounter);
    result << std::setw(8) << getXRunCount();

    return result.str();
}

int32_t AAudioServiceStreamShared::calculateBufferCapacity(int32_t requestedCapacityFrames,
                                                           int32_t framesPerBurst) {

    if (requestedCapacityFrames > MAX_FRAMES_PER_BUFFER) {
        ALOGE("AAudioServiceStreamShared::calculateBufferCapacity() requested capacity %d > max %d",
              requestedCapacityFrames, MAX_FRAMES_PER_BUFFER);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }

    // Determine how many bursts will fit in the buffer.
    int32_t numBursts;
    if (requestedCapacityFrames == AAUDIO_UNSPECIFIED) {
        // Use fewer bursts if default is too many.
        if ((DEFAULT_BURSTS_PER_BUFFER * framesPerBurst) > MAX_FRAMES_PER_BUFFER) {
            numBursts = MAX_FRAMES_PER_BUFFER / framesPerBurst;
        } else {
            numBursts = DEFAULT_BURSTS_PER_BUFFER;
        }
    } else {
        // round up to nearest burst boundary
        numBursts = (requestedCapacityFrames + framesPerBurst - 1) / framesPerBurst;
    }

    // Clip to bare minimum.
    if (numBursts < MIN_BURSTS_PER_BUFFER) {
        numBursts = MIN_BURSTS_PER_BUFFER;
    }
    // Check for numeric overflow.
    if (numBursts > 0x8000 || framesPerBurst > 0x8000) {
        ALOGE("AAudioServiceStreamShared::calculateBufferCapacity() overflow, capacity = %d * %d",
              numBursts, framesPerBurst);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }
    int32_t capacityInFrames = numBursts * framesPerBurst;

    // Final sanity check.
    if (capacityInFrames > MAX_FRAMES_PER_BUFFER) {
        ALOGE("AAudioServiceStreamShared::calculateBufferCapacity() calc capacity %d > max %d",
              capacityInFrames, MAX_FRAMES_PER_BUFFER);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }
    ALOGD("AAudioServiceStreamShared::calculateBufferCapacity() requested %d frames, actual = %d",
          requestedCapacityFrames, capacityInFrames);
    return capacityInFrames;
}

aaudio_result_t AAudioServiceStreamShared::open(const aaudio::AAudioStreamRequest &request)  {

    sp<AAudioServiceStreamShared> keep(this);

    aaudio_result_t result = AAudioServiceStreamBase::open(request, AAUDIO_SHARING_MODE_SHARED);
    if (result != AAUDIO_OK) {
        ALOGE("AAudioServiceStreamBase open() returned %d", result);
        return result;
    }

    const AAudioStreamConfiguration &configurationInput = request.getConstantConfiguration();


    // Is the request compatible with the shared endpoint?
    setFormat(configurationInput.getFormat());
    if (getFormat() == AAUDIO_FORMAT_UNSPECIFIED) {
        setFormat(AAUDIO_FORMAT_PCM_FLOAT);
    } else if (getFormat() != AAUDIO_FORMAT_PCM_FLOAT) {
        ALOGE("AAudioServiceStreamShared::open() mAudioFormat = %d, need FLOAT", getFormat());
        result = AAUDIO_ERROR_INVALID_FORMAT;
        goto error;
    }

    setSampleRate(configurationInput.getSampleRate());
    if (getSampleRate() == AAUDIO_UNSPECIFIED) {
        setSampleRate(mServiceEndpoint->getSampleRate());
    } else if (getSampleRate() != mServiceEndpoint->getSampleRate()) {
        ALOGE("AAudioServiceStreamShared::open() mSampleRate = %d, need %d",
              getSampleRate(), mServiceEndpoint->getSampleRate());
        result = AAUDIO_ERROR_INVALID_RATE;
        goto error;
    }

    setSamplesPerFrame(configurationInput.getSamplesPerFrame());
    if (getSamplesPerFrame() == AAUDIO_UNSPECIFIED) {
        setSamplesPerFrame(mServiceEndpoint->getSamplesPerFrame());
    } else if (getSamplesPerFrame() != mServiceEndpoint->getSamplesPerFrame()) {
        ALOGE("AAudioServiceStreamShared::open() mSamplesPerFrame = %d, need %d",
              getSamplesPerFrame(), mServiceEndpoint->getSamplesPerFrame());
        result = AAUDIO_ERROR_OUT_OF_RANGE;
        goto error;
    }

    setBufferCapacity(calculateBufferCapacity(configurationInput.getBufferCapacity(),
                                     mFramesPerBurst));
    if (getBufferCapacity() < 0) {
        result = getBufferCapacity(); // negative error code
        setBufferCapacity(0);
        goto error;
    }

    {
        std::lock_guard<std::mutex> lock(mAudioDataQueueLock);
        // Create audio data shared memory buffer for client.
        mAudioDataQueue = new SharedRingBuffer();
        result = mAudioDataQueue->allocate(calculateBytesPerFrame(), getBufferCapacity());
        if (result != AAUDIO_OK) {
            ALOGE("AAudioServiceStreamShared::open() could not allocate FIFO with %d frames",
                  getBufferCapacity());
            result = AAUDIO_ERROR_NO_MEMORY;
            goto error;
        }
    }

    ALOGD("AAudioServiceStreamShared::open() actual rate = %d, channels = %d, deviceId = %d",
          getSampleRate(), getSamplesPerFrame(), mServiceEndpoint->getDeviceId());

    result = mServiceEndpoint->registerStream(keep);
    if (result != AAUDIO_OK) {
        goto error;
    }

    setState(AAUDIO_STREAM_STATE_OPEN);
    return AAUDIO_OK;

error:
    close();
    return result;
}


aaudio_result_t AAudioServiceStreamShared::close()  {
    aaudio_result_t result = AAudioServiceStreamBase::close();

    {
        std::lock_guard<std::mutex> lock(mAudioDataQueueLock);
        delete mAudioDataQueue;
        mAudioDataQueue = nullptr;
    }

    return result;
}

/**
 * Get an immutable description of the data queue created by this service.
 */
aaudio_result_t AAudioServiceStreamShared::getAudioDataDescription(
        AudioEndpointParcelable &parcelable)
{
    std::lock_guard<std::mutex> lock(mAudioDataQueueLock);
    if (mAudioDataQueue == nullptr) {
        ALOGE("getAudioDataDescription(): mUpMessageQueue null! - stream not open");
        return AAUDIO_ERROR_NULL;
    }
    // Gather information on the data queue.
    mAudioDataQueue->fillParcelable(parcelable,
                                    parcelable.mDownDataQueueParcelable);
    parcelable.mDownDataQueueParcelable.setFramesPerBurst(getFramesPerBurst());
    return AAUDIO_OK;
}

void AAudioServiceStreamShared::markTransferTime(Timestamp &timestamp) {
    mAtomicTimestamp.write(timestamp);
}

// Get timestamp that was written by mixer or distributor.
aaudio_result_t AAudioServiceStreamShared::getFreeRunningPosition(int64_t *positionFrames,
                                                                  int64_t *timeNanos) {
    // TODO Get presentation timestamp from the HAL
    if (mAtomicTimestamp.isValid()) {
        Timestamp timestamp = mAtomicTimestamp.read();
        *positionFrames = timestamp.getPosition();
        *timeNanos = timestamp.getNanoseconds();
        return AAUDIO_OK;
    } else {
        return AAUDIO_ERROR_UNAVAILABLE;
    }
}

// Get timestamp from lower level service.
aaudio_result_t AAudioServiceStreamShared::getHardwareTimestamp(int64_t *positionFrames,
                                                                int64_t *timeNanos) {

    int64_t position = 0;
    aaudio_result_t result = mServiceEndpoint->getTimestamp(&position, timeNanos);
    if (result == AAUDIO_OK) {
        int64_t offset = mTimestampPositionOffset.load();
        // TODO, do not go below starting value
        position -= offset; // Offset from shared MMAP stream
        ALOGV("getHardwareTimestamp() %8lld = %8lld - %8lld",
              (long long) position, (long long) (position + offset), (long long) offset);
    }
    *positionFrames = position;
    return result;
}
