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

#define LOG_TAG "WGraphicBufferProducer-impl"

#include <android-base/logging.h>

#include <media/stagefright/omx/1.0/WGraphicBufferProducer.h>
#include <media/stagefright/omx/1.0/WProducerListener.h>
#include <media/stagefright/omx/1.0/Conversion.h>
#include <system/window.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {
namespace implementation {

// TWGraphicBufferProducer
TWGraphicBufferProducer::TWGraphicBufferProducer(
        sp<BGraphicBufferProducer> const& base):
    mBase(base) {
}

Return<void> TWGraphicBufferProducer::requestBuffer(
        int32_t slot, requestBuffer_cb _hidl_cb) {
    sp<GraphicBuffer> buf;
    status_t status = mBase->requestBuffer(slot, &buf);
    AnwBuffer anwBuffer;
    if (buf != nullptr) {
        wrapAs(&anwBuffer, *buf);
    }
    _hidl_cb(static_cast<int32_t>(status), anwBuffer);
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::setMaxDequeuedBufferCount(
        int32_t maxDequeuedBuffers) {
    return static_cast<int32_t>(mBase->setMaxDequeuedBufferCount(
            static_cast<int>(maxDequeuedBuffers)));
}

Return<int32_t> TWGraphicBufferProducer::setAsyncMode(bool async) {
    return static_cast<int32_t>(mBase->setAsyncMode(async));
}

Return<void> TWGraphicBufferProducer::dequeueBuffer(
        uint32_t width, uint32_t height,
        PixelFormat format, uint32_t usage,
        bool getFrameTimestamps, dequeueBuffer_cb _hidl_cb) {
    int slot;
    sp<Fence> fence;
    ::android::FrameEventHistoryDelta outTimestamps;
    status_t status = mBase->dequeueBuffer(
        &slot, &fence, width, height,
        static_cast<::android::PixelFormat>(format), usage, nullptr,
        getFrameTimestamps ? &outTimestamps : nullptr);
    hidl_handle tFence;
    FrameEventHistoryDelta tOutTimestamps;

    native_handle_t* nh = nullptr;
    if ((fence == nullptr) || !wrapAs(&tFence, &nh, *fence)) {
        LOG(ERROR) << "TWGraphicBufferProducer::dequeueBuffer - "
                "Invalid output fence";
        _hidl_cb(static_cast<int32_t>(status),
                 static_cast<int32_t>(slot),
                 tFence,
                 tOutTimestamps);
        return Void();
    }
    std::vector<std::vector<native_handle_t*> > nhAA;
    if (getFrameTimestamps && !wrapAs(&tOutTimestamps, &nhAA, outTimestamps)) {
        LOG(ERROR) << "TWGraphicBufferProducer::dequeueBuffer - "
                "Invalid output timestamps";
        _hidl_cb(static_cast<int32_t>(status),
                 static_cast<int32_t>(slot),
                 tFence,
                 tOutTimestamps);
        native_handle_delete(nh);
        return Void();
    }

    _hidl_cb(static_cast<int32_t>(status),
            static_cast<int32_t>(slot),
            tFence,
            tOutTimestamps);
    native_handle_delete(nh);
    if (getFrameTimestamps) {
        for (auto& nhA : nhAA) {
            for (auto& handle : nhA) {
                native_handle_delete(handle);
            }
        }
    }
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::detachBuffer(int32_t slot) {
    return static_cast<int32_t>(mBase->detachBuffer(slot));
}

Return<void> TWGraphicBufferProducer::detachNextBuffer(
        detachNextBuffer_cb _hidl_cb) {
    sp<GraphicBuffer> outBuffer;
    sp<Fence> outFence;
    status_t status = mBase->detachNextBuffer(&outBuffer, &outFence);
    AnwBuffer tBuffer;
    hidl_handle tFence;

    if (outBuffer == nullptr) {
        LOG(ERROR) << "TWGraphicBufferProducer::detachNextBuffer - "
                "Invalid output buffer";
        _hidl_cb(static_cast<int32_t>(status), tBuffer, tFence);
        return Void();
    }
    wrapAs(&tBuffer, *outBuffer);
    native_handle_t* nh = nullptr;
    if ((outFence != nullptr) && !wrapAs(&tFence, &nh, *outFence)) {
        LOG(ERROR) << "TWGraphicBufferProducer::detachNextBuffer - "
                "Invalid output fence";
        _hidl_cb(static_cast<int32_t>(status), tBuffer, tFence);
        return Void();
    }

    _hidl_cb(static_cast<int32_t>(status), tBuffer, tFence);
    native_handle_delete(nh);
    return Void();
}

Return<void> TWGraphicBufferProducer::attachBuffer(
        const AnwBuffer& buffer,
        attachBuffer_cb _hidl_cb) {
    int outSlot;
    sp<GraphicBuffer> lBuffer = new GraphicBuffer();
    if (!convertTo(lBuffer.get(), buffer)) {
        LOG(ERROR) << "TWGraphicBufferProducer::attachBuffer - "
                "Invalid input native window buffer";
        _hidl_cb(static_cast<int32_t>(BAD_VALUE), -1);
        return Void();
    }
    status_t status = mBase->attachBuffer(&outSlot, lBuffer);

    _hidl_cb(static_cast<int32_t>(status), static_cast<int32_t>(outSlot));
    return Void();
}

Return<void> TWGraphicBufferProducer::queueBuffer(
        int32_t slot, const QueueBufferInput& input,
        queueBuffer_cb _hidl_cb) {
    QueueBufferOutput tOutput;
    BGraphicBufferProducer::QueueBufferInput lInput(
            0, false, HAL_DATASPACE_UNKNOWN,
            ::android::Rect(0, 0, 1, 1),
            NATIVE_WINDOW_SCALING_MODE_FREEZE,
            0, ::android::Fence::NO_FENCE);
    if (!convertTo(&lInput, input)) {
        LOG(ERROR) << "TWGraphicBufferProducer::queueBuffer - "
                "Invalid input";
        _hidl_cb(static_cast<int32_t>(BAD_VALUE), tOutput);
        return Void();
    }
    BGraphicBufferProducer::QueueBufferOutput lOutput;
    status_t status = mBase->queueBuffer(
            static_cast<int>(slot), lInput, &lOutput);

    std::vector<std::vector<native_handle_t*> > nhAA;
    if (!wrapAs(&tOutput, &nhAA, lOutput)) {
        LOG(ERROR) << "TWGraphicBufferProducer::queueBuffer - "
                "Invalid output";
        _hidl_cb(static_cast<int32_t>(BAD_VALUE), tOutput);
        return Void();
    }

    _hidl_cb(static_cast<int32_t>(status), tOutput);
    for (auto& nhA : nhAA) {
        for (auto& nh : nhA) {
            native_handle_delete(nh);
        }
    }
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::cancelBuffer(
        int32_t slot, const hidl_handle& fence) {
    sp<Fence> lFence = new Fence();
    if (!convertTo(lFence.get(), fence)) {
        LOG(ERROR) << "TWGraphicBufferProducer::cancelBuffer - "
                "Invalid input fence";
        return static_cast<int32_t>(BAD_VALUE);
    }
    return static_cast<int32_t>(mBase->cancelBuffer(static_cast<int>(slot), lFence));
}

Return<void> TWGraphicBufferProducer::query(int32_t what, query_cb _hidl_cb) {
    int lValue;
    int lReturn = mBase->query(static_cast<int>(what), &lValue);
    _hidl_cb(static_cast<int32_t>(lReturn), static_cast<int32_t>(lValue));
    return Void();
}

Return<void> TWGraphicBufferProducer::connect(
        const sp<HProducerListener>& listener,
        int32_t api, bool producerControlledByApp, connect_cb _hidl_cb) {
    sp<BProducerListener> lListener = listener == nullptr ?
            nullptr : new LWProducerListener(listener);
    BGraphicBufferProducer::QueueBufferOutput lOutput;
    status_t status = mBase->connect(lListener,
            static_cast<int>(api),
            producerControlledByApp,
            &lOutput);

    QueueBufferOutput tOutput;
    std::vector<std::vector<native_handle_t*> > nhAA;
    if (!wrapAs(&tOutput, &nhAA, lOutput)) {
        LOG(ERROR) << "TWGraphicBufferProducer::connect - "
                "Invalid output";
        _hidl_cb(static_cast<int32_t>(status), tOutput);
        return Void();
    }

    _hidl_cb(static_cast<int32_t>(status), tOutput);
    for (auto& nhA : nhAA) {
        for (auto& nh : nhA) {
            native_handle_delete(nh);
        }
    }
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::disconnect(
        int32_t api, DisconnectMode mode) {
    return static_cast<int32_t>(mBase->disconnect(
            static_cast<int>(api),
            toGuiDisconnectMode(mode)));
}

Return<int32_t> TWGraphicBufferProducer::setSidebandStream(const hidl_handle& stream) {
    return static_cast<int32_t>(mBase->setSidebandStream(NativeHandle::create(
            stream ? native_handle_clone(stream) : NULL, true)));
}

Return<void> TWGraphicBufferProducer::allocateBuffers(
        uint32_t width, uint32_t height, PixelFormat format, uint32_t usage) {
    mBase->allocateBuffers(
            width, height,
            static_cast<::android::PixelFormat>(format),
            usage);
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::allowAllocation(bool allow) {
    return static_cast<int32_t>(mBase->allowAllocation(allow));
}

Return<int32_t> TWGraphicBufferProducer::setGenerationNumber(uint32_t generationNumber) {
    return static_cast<int32_t>(mBase->setGenerationNumber(generationNumber));
}

Return<void> TWGraphicBufferProducer::getConsumerName(getConsumerName_cb _hidl_cb) {
    _hidl_cb(mBase->getConsumerName().string());
    return Void();
}

Return<int32_t> TWGraphicBufferProducer::setSharedBufferMode(bool sharedBufferMode) {
    return static_cast<int32_t>(mBase->setSharedBufferMode(sharedBufferMode));
}

Return<int32_t> TWGraphicBufferProducer::setAutoRefresh(bool autoRefresh) {
    return static_cast<int32_t>(mBase->setAutoRefresh(autoRefresh));
}

Return<int32_t> TWGraphicBufferProducer::setDequeueTimeout(int64_t timeoutNs) {
    return static_cast<int32_t>(mBase->setDequeueTimeout(timeoutNs));
}

Return<void> TWGraphicBufferProducer::getLastQueuedBuffer(
        getLastQueuedBuffer_cb _hidl_cb) {
    sp<GraphicBuffer> lOutBuffer = new GraphicBuffer();
    sp<Fence> lOutFence = new Fence();
    float lOutTransformMatrix[16];
    status_t status = mBase->getLastQueuedBuffer(
            &lOutBuffer, &lOutFence, lOutTransformMatrix);

    AnwBuffer tOutBuffer;
    if (lOutBuffer != nullptr) {
        wrapAs(&tOutBuffer, *lOutBuffer);
    }
    hidl_handle tOutFence;
    native_handle_t* nh = nullptr;
    if ((lOutFence == nullptr) || !wrapAs(&tOutFence, &nh, *lOutFence)) {
        LOG(ERROR) << "TWGraphicBufferProducer::getLastQueuedBuffer - "
                "Invalid output fence";
        _hidl_cb(static_cast<int32_t>(status),
                tOutBuffer,
                tOutFence,
                hidl_array<float, 16>());
        return Void();
    }
    hidl_array<float, 16> tOutTransformMatrix(lOutTransformMatrix);

    _hidl_cb(static_cast<int32_t>(status), tOutBuffer, tOutFence, tOutTransformMatrix);
    native_handle_delete(nh);
    return Void();
}

Return<void> TWGraphicBufferProducer::getFrameTimestamps(
        getFrameTimestamps_cb _hidl_cb) {
    ::android::FrameEventHistoryDelta lDelta;
    mBase->getFrameTimestamps(&lDelta);

    FrameEventHistoryDelta tDelta;
    std::vector<std::vector<native_handle_t*> > nhAA;
    if (!wrapAs(&tDelta, &nhAA, lDelta)) {
        LOG(ERROR) << "TWGraphicBufferProducer::getFrameTimestamps - "
                "Invalid output frame timestamps";
        _hidl_cb(tDelta);
        return Void();
    }

    _hidl_cb(tDelta);
    for (auto& nhA : nhAA) {
        for (auto& nh : nhA) {
            native_handle_delete(nh);
        }
    }
    return Void();
}

Return<void> TWGraphicBufferProducer::getUniqueId(getUniqueId_cb _hidl_cb) {
    uint64_t outId;
    status_t status = mBase->getUniqueId(&outId);
    _hidl_cb(static_cast<int32_t>(status), outId);
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android
