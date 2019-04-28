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

#define LOG_TAG "CamDevSession@3.3-impl"
#include <android/log.h>

#include <set>
#include <utils/Trace.h>
#include <hardware/gralloc.h>
#include <hardware/gralloc1.h>
#include "CameraDeviceSession.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_3 {
namespace implementation {

CameraDeviceSession::CameraDeviceSession(
    camera3_device_t* device,
    const camera_metadata_t* deviceInfo,
    const sp<V3_2::ICameraDeviceCallback>& callback) :
        V3_2::implementation::CameraDeviceSession(device, deviceInfo, callback) {
}

CameraDeviceSession::~CameraDeviceSession() {
}

Return<void> CameraDeviceSession::configureStreams_3_3(
        const StreamConfiguration& requestedConfiguration,
        ICameraDeviceSession::configureStreams_3_3_cb _hidl_cb)  {
    Status status = initStatus();
    HalStreamConfiguration outStreams;

    // hold the inflight lock for entire configureStreams scope since there must not be any
    // inflight request/results during stream configuration.
    Mutex::Autolock _l(mInflightLock);
    if (!mInflightBuffers.empty()) {
        ALOGE("%s: trying to configureStreams while there are still %zu inflight buffers!",
                __FUNCTION__, mInflightBuffers.size());
        _hidl_cb(Status::INTERNAL_ERROR, outStreams);
        return Void();
    }

    if (!mInflightAETriggerOverrides.empty()) {
        ALOGE("%s: trying to configureStreams while there are still %zu inflight"
                " trigger overrides!", __FUNCTION__,
                mInflightAETriggerOverrides.size());
        _hidl_cb(Status::INTERNAL_ERROR, outStreams);
        return Void();
    }

    if (!mInflightRawBoostPresent.empty()) {
        ALOGE("%s: trying to configureStreams while there are still %zu inflight"
                " boost overrides!", __FUNCTION__,
                mInflightRawBoostPresent.size());
        _hidl_cb(Status::INTERNAL_ERROR, outStreams);
        return Void();
    }

    if (status != Status::OK) {
        _hidl_cb(status, outStreams);
        return Void();
    }

    camera3_stream_configuration_t stream_list;
    hidl_vec<camera3_stream_t*> streams;

    stream_list.operation_mode = (uint32_t) requestedConfiguration.operationMode;
    stream_list.num_streams = requestedConfiguration.streams.size();
    streams.resize(stream_list.num_streams);
    stream_list.streams = streams.data();

    for (uint32_t i = 0; i < stream_list.num_streams; i++) {
        int id = requestedConfiguration.streams[i].id;

        if (mStreamMap.count(id) == 0) {
            Camera3Stream stream;
            V3_2::implementation::convertFromHidl(requestedConfiguration.streams[i], &stream);
            mStreamMap[id] = stream;
            mStreamMap[id].data_space = mapToLegacyDataspace(
                    mStreamMap[id].data_space);
            mCirculatingBuffers.emplace(stream.mId, CirculatingBuffers{});
        } else {
            // width/height/format must not change, but usage/rotation might need to change
            if (mStreamMap[id].stream_type !=
                    (int) requestedConfiguration.streams[i].streamType ||
                    mStreamMap[id].width != requestedConfiguration.streams[i].width ||
                    mStreamMap[id].height != requestedConfiguration.streams[i].height ||
                    mStreamMap[id].format != (int) requestedConfiguration.streams[i].format ||
                    mStreamMap[id].data_space !=
                            mapToLegacyDataspace( static_cast<android_dataspace_t> (
                                    requestedConfiguration.streams[i].dataSpace))) {
                ALOGE("%s: stream %d configuration changed!", __FUNCTION__, id);
                _hidl_cb(Status::INTERNAL_ERROR, outStreams);
                return Void();
            }
            mStreamMap[id].rotation = (int) requestedConfiguration.streams[i].rotation;
            mStreamMap[id].usage = (uint32_t) requestedConfiguration.streams[i].usage;
        }
        streams[i] = &mStreamMap[id];
    }

    ATRACE_BEGIN("camera3->configure_streams");
    status_t ret = mDevice->ops->configure_streams(mDevice, &stream_list);
    ATRACE_END();

    // In case Hal returns error most likely it was not able to release
    // the corresponding resources of the deleted streams.
    if (ret == OK) {
        // delete unused streams, note we do this after adding new streams to ensure new stream
        // will not have the same address as deleted stream, and HAL has a chance to reference
        // the to be deleted stream in configure_streams call
        for(auto it = mStreamMap.begin(); it != mStreamMap.end();) {
            int id = it->first;
            bool found = false;
            for (const auto& stream : requestedConfiguration.streams) {
                if (id == stream.id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Unmap all buffers of deleted stream
                // in case the configuration call succeeds and HAL
                // is able to release the corresponding resources too.
                cleanupBuffersLocked(id);
                it = mStreamMap.erase(it);
            } else {
                ++it;
            }
        }

        // Track video streams
        mVideoStreamIds.clear();
        for (const auto& stream : requestedConfiguration.streams) {
            if (stream.streamType == V3_2::StreamType::OUTPUT &&
                stream.usage &
                    graphics::common::V1_0::BufferUsage::VIDEO_ENCODER) {
                mVideoStreamIds.push_back(stream.id);
            }
        }
        mResultBatcher.setBatchedStreams(mVideoStreamIds);
    }

    if (ret == -EINVAL) {
        status = Status::ILLEGAL_ARGUMENT;
    } else if (ret != OK) {
        status = Status::INTERNAL_ERROR;
    } else {
        convertToHidl(stream_list, &outStreams);
        mFirstRequest = true;
    }

    _hidl_cb(status, outStreams);
    return Void();
}

} // namespace implementation
}  // namespace V3_3
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
