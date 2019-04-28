/*
 * Copyright 2016, The Android Open Source Project
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

#ifndef ANDROID_HARDWARE_MEDIA_OMX_V1_0_WGRAPHICBUFFERPRODUCER_H
#define ANDROID_HARDWARE_MEDIA_OMX_V1_0_WGRAPHICBUFFERPRODUCER_H

#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>

#include <binder/Binder.h>
#include <gui/IGraphicBufferProducer.h>
#include <gui/IProducerListener.h>

#include <android/hardware/graphics/bufferqueue/1.0/IGraphicBufferProducer.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {
namespace implementation {

using ::android::hardware::graphics::common::V1_0::PixelFormat;
using ::android::hardware::media::V1_0::AnwBuffer;
using ::android::hidl::base::V1_0::IBase;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

typedef ::android::hardware::graphics::bufferqueue::V1_0::
        IGraphicBufferProducer HGraphicBufferProducer;
typedef ::android::hardware::graphics::bufferqueue::V1_0::
        IProducerListener HProducerListener;

typedef ::android::IGraphicBufferProducer BGraphicBufferProducer;
typedef ::android::IProducerListener BProducerListener;
using ::android::BnGraphicBufferProducer;

struct TWGraphicBufferProducer : public HGraphicBufferProducer {
    sp<BGraphicBufferProducer> mBase;
    TWGraphicBufferProducer(sp<BGraphicBufferProducer> const& base);
    Return<void> requestBuffer(int32_t slot, requestBuffer_cb _hidl_cb)
            override;
    Return<int32_t> setMaxDequeuedBufferCount(int32_t maxDequeuedBuffers)
            override;
    Return<int32_t> setAsyncMode(bool async) override;
    Return<void> dequeueBuffer(
            uint32_t width, uint32_t height, PixelFormat format, uint32_t usage,
            bool getFrameTimestamps, dequeueBuffer_cb _hidl_cb) override;
    Return<int32_t> detachBuffer(int32_t slot) override;
    Return<void> detachNextBuffer(detachNextBuffer_cb _hidl_cb) override;
    Return<void> attachBuffer(const AnwBuffer& buffer, attachBuffer_cb _hidl_cb)
            override;
    Return<void> queueBuffer(
            int32_t slot, const HGraphicBufferProducer::QueueBufferInput& input,
            queueBuffer_cb _hidl_cb) override;
    Return<int32_t> cancelBuffer(int32_t slot, const hidl_handle& fence)
            override;
    Return<void> query(int32_t what, query_cb _hidl_cb) override;
    Return<void> connect(const sp<HProducerListener>& listener,
            int32_t api, bool producerControlledByApp,
            connect_cb _hidl_cb) override;
    Return<int32_t> disconnect(
            int32_t api,
            HGraphicBufferProducer::DisconnectMode mode) override;
    Return<int32_t> setSidebandStream(const hidl_handle& stream) override;
    Return<void> allocateBuffers(
            uint32_t width, uint32_t height,
            PixelFormat format, uint32_t usage) override;
    Return<int32_t> allowAllocation(bool allow) override;
    Return<int32_t> setGenerationNumber(uint32_t generationNumber) override;
    Return<void> getConsumerName(getConsumerName_cb _hidl_cb) override;
    Return<int32_t> setSharedBufferMode(bool sharedBufferMode) override;
    Return<int32_t> setAutoRefresh(bool autoRefresh) override;
    Return<int32_t> setDequeueTimeout(int64_t timeoutNs) override;
    Return<void> getLastQueuedBuffer(getLastQueuedBuffer_cb _hidl_cb) override;
    Return<void> getFrameTimestamps(getFrameTimestamps_cb _hidl_cb) override;
    Return<void> getUniqueId(getUniqueId_cb _hidl_cb) override;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_MEDIA_OMX_V1_0_WOMXBUFFERPRODUCER_H
