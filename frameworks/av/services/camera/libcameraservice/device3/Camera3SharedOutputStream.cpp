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

#include "Camera3SharedOutputStream.h"

namespace android {

namespace camera3 {

Camera3SharedOutputStream::Camera3SharedOutputStream(int id,
        const std::vector<sp<Surface>>& surfaces,
        uint32_t width, uint32_t height, int format,
        uint64_t consumerUsage, android_dataspace dataSpace,
        camera3_stream_rotation_t rotation,
        nsecs_t timestampOffset, int setId) :
        Camera3OutputStream(id, CAMERA3_STREAM_OUTPUT, width, height,
                            format, dataSpace, rotation, consumerUsage,
                            timestampOffset, setId),
        mSurfaces(surfaces) {
}

Camera3SharedOutputStream::~Camera3SharedOutputStream() {
    disconnectLocked();
}

status_t Camera3SharedOutputStream::connectStreamSplitterLocked() {
    status_t res = OK;

    mStreamSplitter = new Camera3StreamSplitter();

    uint64_t usage;
    getEndpointUsage(&usage);

    res = mStreamSplitter->connect(mSurfaces, usage, camera3_stream::max_buffers, &mConsumer);
    if (res != OK) {
        ALOGE("%s: Failed to connect to stream splitter: %s(%d)",
                __FUNCTION__, strerror(-res), res);
        return res;
    }

    return res;
}

status_t Camera3SharedOutputStream::notifyBufferReleased(ANativeWindowBuffer *anwBuffer) {
    Mutex::Autolock l(mLock);
    status_t res = OK;
    const sp<GraphicBuffer> buffer(static_cast<GraphicBuffer*>(anwBuffer));

    if (mStreamSplitter != nullptr) {
        res = mStreamSplitter->notifyBufferReleased(buffer);
    }

    return res;
}

bool Camera3SharedOutputStream::isConsumerConfigurationDeferred(size_t surface_id) const {
    Mutex::Autolock l(mLock);
    return (surface_id >= mSurfaces.size());
}

status_t Camera3SharedOutputStream::setConsumers(const std::vector<sp<Surface>>& surfaces) {
    Mutex::Autolock l(mLock);
    if (surfaces.size() == 0) {
        ALOGE("%s: it's illegal to set zero consumer surfaces!", __FUNCTION__);
        return INVALID_OPERATION;
    }

    status_t ret = OK;
    for (auto& surface : surfaces) {
        if (surface == nullptr) {
            ALOGE("%s: it's illegal to set a null consumer surface!", __FUNCTION__);
            return INVALID_OPERATION;
        }

        mSurfaces.push_back(surface);

        // Only call addOutput if the splitter has been connected.
        if (mStreamSplitter != nullptr) {
            ret = mStreamSplitter->addOutput(surface);
            if (ret != OK) {
                ALOGE("%s: addOutput failed with error code %d", __FUNCTION__, ret);
                return ret;

            }
        }
    }
    return ret;
}

status_t Camera3SharedOutputStream::getBufferLocked(camera3_stream_buffer *buffer,
        const std::vector<size_t>& surface_ids) {
    ANativeWindowBuffer* anb;
    int fenceFd = -1;

    status_t res;
    res = getBufferLockedCommon(&anb, &fenceFd);
    if (res != OK) {
        return res;
    }

    // Attach the buffer to the splitter output queues. This could block if
    // the output queue doesn't have any empty slot. So unlock during the course
    // of attachBufferToOutputs.
    sp<Camera3StreamSplitter> splitter = mStreamSplitter;
    mLock.unlock();
    res = splitter->attachBufferToOutputs(anb, surface_ids);
    mLock.lock();
    if (res != OK) {
        ALOGE("%s: Stream %d: Cannot attach stream splitter buffer to outputs: %s (%d)",
                __FUNCTION__, mId, strerror(-res), res);
        // Only transition to STATE_ABANDONED from STATE_CONFIGURED. (If it is STATE_PREPARING,
        // let prepareNextBuffer handle the error.)
        if (res == NO_INIT && mState == STATE_CONFIGURED) {
            mState = STATE_ABANDONED;
        }

        return res;
    }

    /**
     * FenceFD now owned by HAL except in case of error,
     * in which case we reassign it to acquire_fence
     */
    handoutBufferLocked(*buffer, &(anb->handle), /*acquireFence*/fenceFd,
                        /*releaseFence*/-1, CAMERA3_BUFFER_STATUS_OK, /*output*/true);

    return OK;
}

status_t Camera3SharedOutputStream::queueBufferToConsumer(sp<ANativeWindow>& consumer,
            ANativeWindowBuffer* buffer, int anwReleaseFence) {
    status_t res = consumer->queueBuffer(consumer.get(), buffer, anwReleaseFence);

    // After queuing buffer to the internal consumer queue, check whether the buffer is
    // successfully queued to the output queues.
    if (res == OK) {
        res = mStreamSplitter->getOnFrameAvailableResult();
        if (res != OK) {
            ALOGE("%s: getOnFrameAvailable returns %d", __FUNCTION__, res);
        }
    } else {
        ALOGE("%s: queueBufer failed %d", __FUNCTION__, res);
    }

    return res;
}

status_t Camera3SharedOutputStream::configureQueueLocked() {
    status_t res;

    if ((res = Camera3IOStreamBase::configureQueueLocked()) != OK) {
        return res;
    }

    res = connectStreamSplitterLocked();
    if (res != OK) {
        ALOGE("Cannot connect to stream splitter: %s(%d)", strerror(-res), res);
        return res;
    }

    res = configureConsumerQueueLocked();
    if (res != OK) {
        ALOGE("Failed to configureConsumerQueueLocked: %s(%d)", strerror(-res), res);
        return res;
    }

    return OK;
}

status_t Camera3SharedOutputStream::disconnectLocked() {
    status_t res;
    res = Camera3OutputStream::disconnectLocked();

    if (mStreamSplitter != nullptr) {
        mStreamSplitter->disconnect();
    }

    return res;
}

status_t Camera3SharedOutputStream::getEndpointUsage(uint64_t *usage) const {

    status_t res = OK;
    uint64_t u = 0;

    if (mConsumer == nullptr) {
        // Called before shared buffer queue is constructed.
        *usage = getPresetConsumerUsage();

        for (auto surface : mSurfaces) {
            if (surface != nullptr) {
                res = getEndpointUsageForSurface(&u, surface);
                *usage |= u;
            }
        }
    } else {
        // Called after shared buffer queue is constructed.
        res = getEndpointUsageForSurface(&u, mConsumer);
        *usage |= u;
    }

    return res;
}

} // namespace camera3

} // namespace android
