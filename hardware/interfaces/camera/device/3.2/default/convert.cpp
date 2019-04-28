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

#define LOG_TAG "android.hardware.camera.device@3.2-convert-impl"
#include <log/log.h>

#include "include/convert.h"

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_2 {
namespace implementation {

using ::android::hardware::graphics::common::V1_0::Dataspace;
using ::android::hardware::graphics::common::V1_0::PixelFormat;
using ::android::hardware::camera::device::V3_2::BufferUsageFlags;

bool convertFromHidl(const CameraMetadata &src, const camera_metadata_t** dst) {
    if (src.size() == 0) {
        // Special case for null metadata
        *dst = nullptr;
        return true;
    }

    const uint8_t* data = src.data();
    // sanity check the size of CameraMetadata match underlying camera_metadata_t
    if (get_camera_metadata_size((camera_metadata_t*)data) != src.size()) {
        ALOGE("%s: input CameraMetadata is corrupt!", __FUNCTION__);
        return false;
    }
    *dst = (camera_metadata_t*) data;
    return true;
}

// Note: existing data in dst will be gone. Caller still owns the memory of src
void convertToHidl(const camera_metadata_t *src, CameraMetadata* dst) {
    if (src == nullptr) {
        return;
    }
    size_t size = get_camera_metadata_size(src);
    dst->setToExternal((uint8_t *) src, size);
    return;
}

void convertFromHidl(const Stream &src, Camera3Stream* dst) {
    dst->mId = src.id;
    dst->stream_type = (int) src.streamType;
    dst->width = src.width;
    dst->height = src.height;
    dst->format = (int) src.format;
    dst->data_space = (android_dataspace_t) src.dataSpace;
    dst->rotation = (int) src.rotation;
    dst->usage = (uint32_t) src.usage;
    // Fields to be filled by HAL (max_buffers, priv) are initialized to 0
    dst->max_buffers = 0;
    dst->priv = 0;
    return;
}

void convertToHidl(const Camera3Stream* src, HalStream* dst) {
    dst->id = src->mId;
    dst->overrideFormat = (PixelFormat) src->format;
    dst->maxBuffers = src->max_buffers;
    if (src->stream_type == CAMERA3_STREAM_OUTPUT) {
        dst->consumerUsage = (BufferUsageFlags)0;
        dst->producerUsage = (BufferUsageFlags)src->usage;
    } else if (src->stream_type == CAMERA3_STREAM_INPUT) {
        dst->producerUsage = (BufferUsageFlags)0;
        dst->consumerUsage = (BufferUsageFlags)src->usage;
    } else {
        //Should not reach here per current HIDL spec, but we might end up adding
        // bi-directional stream to HIDL.
        ALOGW("%s: Stream type %d is not currently supported!",
                __FUNCTION__, src->stream_type);
    }
}

void convertToHidl(const camera3_stream_configuration_t& src, HalStreamConfiguration* dst) {
    dst->streams.resize(src.num_streams);
    for (uint32_t i = 0; i < src.num_streams; i++) {
        convertToHidl(static_cast<Camera3Stream*>(src.streams[i]), &dst->streams[i]);
    }
    return;
}

void convertFromHidl(
        buffer_handle_t* bufPtr, BufferStatus status, camera3_stream_t* stream, int acquireFence,
        camera3_stream_buffer_t* dst) {
    dst->stream = stream;
    dst->buffer = bufPtr;
    dst->status = (int) status;
    dst->acquire_fence = acquireFence;
    dst->release_fence = -1; // meant for HAL to fill in
}

void convertToHidl(const camera3_notify_msg* src, NotifyMsg* dst) {
    dst->type = (MsgType) src->type;
    switch (src->type) {
        case CAMERA3_MSG_ERROR:
            {
                // The camera3_stream_t* must be the same as what wrapper HAL passed to conventional
                // HAL, or the ID lookup will return garbage. Caller should validate the ID here is
                // indeed one of active stream IDs
                Camera3Stream* stream = static_cast<Camera3Stream*>(
                        src->message.error.error_stream);
                dst->msg.error.frameNumber = src->message.error.frame_number;
                dst->msg.error.errorStreamId = (stream != nullptr) ? stream->mId : -1;
                dst->msg.error.errorCode = (ErrorCode) src->message.error.error_code;
            }
            break;
        case CAMERA3_MSG_SHUTTER:
            dst->msg.shutter.frameNumber = src->message.shutter.frame_number;
            dst->msg.shutter.timestamp = src->message.shutter.timestamp;
            break;
        default:
            ALOGE("%s: HIDL type converion failed. Unknown msg type 0x%x",
                    __FUNCTION__, src->type);
    }
    return;
}

}  // namespace implementation
}  // namespace V3_2
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
