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

#ifndef ANDROID_HARDWARE_MEDIA_OMX_V1_0__CONVERSION_H
#define ANDROID_HARDWARE_MEDIA_OMX_V1_0__CONVERSION_H

#include <vector>
#include <list>

#include <unistd.h>

#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <hidlmemory/mapping.h>

#include <binder/Binder.h>
#include <binder/Status.h>
#include <ui/FenceTime.h>
#include <cutils/native_handle.h>
#include <gui/IGraphicBufferProducer.h>

#include <media/OMXFenceParcelable.h>
#include <media/OMXBuffer.h>
#include <media/hardware/VideoAPI.h>

#include <android/hidl/memory/1.0/IMemory.h>
#include <android/hardware/graphics/bufferqueue/1.0/IProducerListener.h>
#include <android/hardware/media/omx/1.0/types.h>
#include <android/hardware/media/omx/1.0/IOmx.h>
#include <android/hardware/media/omx/1.0/IOmxNode.h>
#include <android/hardware/media/omx/1.0/IOmxBufferSource.h>
#include <android/hardware/media/omx/1.0/IGraphicBufferSource.h>
#include <android/hardware/media/omx/1.0/IOmxObserver.h>

#include <android/IGraphicBufferSource.h>
#include <android/IOMXBufferSource.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {
namespace implementation {

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_handle;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

using ::android::String8;
using ::android::OMXFenceParcelable;

using ::android::hardware::media::omx::V1_0::Message;
using ::android::omx_message;

using ::android::hardware::media::omx::V1_0::ColorAspects;
using ::android::hardware::media::V1_0::Rect;
using ::android::hardware::media::V1_0::Region;

using ::android::hardware::graphics::common::V1_0::Dataspace;

using ::android::hardware::graphics::common::V1_0::PixelFormat;

using ::android::OMXBuffer;

using ::android::hardware::media::V1_0::AnwBuffer;
using ::android::GraphicBuffer;

using ::android::hardware::media::omx::V1_0::IOmx;
using ::android::IOMX;

using ::android::hardware::media::omx::V1_0::IOmxNode;
using ::android::IOMXNode;

using ::android::hardware::media::omx::V1_0::IOmxObserver;
using ::android::IOMXObserver;

using ::android::hardware::media::omx::V1_0::IOmxBufferSource;
using ::android::IOMXBufferSource;

typedef ::android::hardware::graphics::bufferqueue::V1_0::IGraphicBufferProducer
        HGraphicBufferProducer;
typedef ::android::IGraphicBufferProducer
        BGraphicBufferProducer;

// native_handle_t helper functions.

/**
 * \brief Take an fd and create a native handle containing only the given fd.
 * The created handle will need to be deleted manually with
 * `native_handle_delete()`.
 *
 * \param[in] fd The source file descriptor (of type `int`).
 * \return The create `native_handle_t*` that contains the given \p fd. If the
 * supplied \p fd is negative, the created native handle will contain no file
 * descriptors.
 *
 * If the native handle cannot be created, the return value will be
 * `nullptr`.
 *
 * This function does not duplicate the file descriptor.
 */
inline native_handle_t* native_handle_create_from_fd(int fd) {
    if (fd < 0) {
        return native_handle_create(0, 0);
    }
    native_handle_t* nh = native_handle_create(1, 0);
    if (nh == nullptr) {
        return nullptr;
    }
    nh->data[0] = fd;
    return nh;
}

/**
 * \brief Extract a file descriptor from a native handle.
 *
 * \param[in] nh The source `native_handle_t*`.
 * \param[in] index The index of the file descriptor in \p nh to read from. This
 * input has the default value of `0`.
 * \return The `index`-th file descriptor in \p nh. If \p nh does not have
 * enough file descriptors, the returned value will be `-1`.
 *
 * This function does not duplicate the file descriptor.
 */
inline int native_handle_read_fd(native_handle_t const* nh, int index = 0) {
    return ((nh == nullptr) || (nh->numFds == 0) ||
            (nh->numFds <= index) || (index < 0)) ?
            -1 : nh->data[index];
}

/**
 * Conversion functions
 * ====================
 *
 * There are two main directions of conversion:
 * - `inTargetType(...)`: Create a wrapper whose lifetime depends on the
 *   input. The wrapper has type `TargetType`.
 * - `toTargetType(...)`: Create a standalone object of type `TargetType` that
 *   corresponds to the input. The lifetime of the output does not depend on the
 *   lifetime of the input.
 * - `wrapIn(TargetType*, ...)`: Same as `inTargetType()`, but for `TargetType`
 *   that cannot be copied and/or moved efficiently, or when there are multiple
 *   output arguments.
 * - `convertTo(TargetType*, ...)`: Same as `toTargetType()`, but for
 *   `TargetType` that cannot be copied and/or moved efficiently, or when there
 *   are multiple output arguments.
 *
 * `wrapIn()` and `convertTo()` functions will take output arguments before
 * input arguments. Some of these functions might return a value to indicate
 * success or error.
 *
 * In converting or wrapping something as a Treble type that contains a
 * `hidl_handle`, `native_handle_t*` will need to be created and returned as
 * an additional output argument, hence only `wrapIn()` or `convertTo()` would
 * be available. The caller must call `native_handle_delete()` to deallocate the
 * returned native handle when it is no longer needed.
 *
 * For types that contain file descriptors, `inTargetType()` and `wrapAs()` do
 * not perform duplication of file descriptors, while `toTargetType()` and
 * `convertTo()` do.
 */

/**
 * \brief Convert `Return<void>` to `binder::Status`.
 *
 * \param[in] t The source `Return<void>`.
 * \return The corresponding `binder::Status`.
 */
// convert: Return<void> -> ::android::binder::Status
inline ::android::binder::Status toBinderStatus(
        Return<void> const& t) {
    return ::android::binder::Status::fromExceptionCode(
            t.isOk() ? OK : UNKNOWN_ERROR,
            t.description().c_str());
}

/**
 * \brief Convert `Return<Status>` to `status_t`. This is for legacy binder
 * calls.
 *
 * \param[in] t The source `Return<Status>`.
 * \return The corresponding `status_t`.
 *
 * This function first check if \p t has a transport error. If it does, then the
 * return value is the transport error code. Otherwise, the return value is
 * converted from `Status` contained inside \p t.
 *
 * Note:
 * - This `Status` is omx-specific. It is defined in `types.hal`.
 * - The name of this function is not `convert`.
 */
// convert: Status -> status_t
inline status_t toStatusT(Return<Status> const& t) {
    return t.isOk() ? static_cast<status_t>(static_cast<Status>(t)) : UNKNOWN_ERROR;
}

/**
 * \brief Convert `Return<void>` to `status_t`. This is for legacy binder calls.
 *
 * \param[in] t The source `Return<void>`.
 * \return The corresponding `status_t`.
 */
// convert: Return<void> -> status_t
inline status_t toStatusT(Return<void> const& t) {
    return t.isOk() ? OK : UNKNOWN_ERROR;
}

/**
 * \brief Convert `Status` to `status_t`. This is for legacy binder calls.
 *
 * \param[in] t The source `Status`.
 * \return the corresponding `status_t`.
 */
// convert: Status -> status_t
inline status_t toStatusT(Status const& t) {
    return static_cast<status_t>(t);
}

/**
 * \brief Convert `status_t` to `Status`.
 *
 * \param[in] l The source `status_t`.
 * \return The corresponding `Status`.
 */
// convert: status_t -> Status
inline Status toStatus(status_t l) {
    return static_cast<Status>(l);
}

/**
 * \brief Wrap `native_handle_t*` in `hidl_handle`.
 *
 * \param[in] nh The source `native_handle_t*`.
 * \return The `hidl_handle` that points to \p nh.
 */
// wrap: native_handle_t* -> hidl_handle
inline hidl_handle inHidlHandle(native_handle_t const* nh) {
    return hidl_handle(nh);
}

/**
 * \brief Wrap an `omx_message` and construct the corresponding `Message`.
 *
 * \param[out] t The wrapper of type `Message`.
 * \param[out] nh The native_handle_t referred to by `t->fence`.
 * \param[in] l The source `omx_message`.
 * \return `true` if the wrapping is successful; `false` otherwise.
 *
 * Upon success, \p nh will be created to hold the file descriptor stored in
 * `l.fenceFd`, and `t->fence` will point to \p nh. \p nh will need to be
 * destroyed manually by `native_handle_delete()` when \p t is no longer needed.
 *
 * Upon failure, \p nh will not be created and will not need to be deleted. \p t
 * will be invalid.
 */
// wrap, omx_message -> Message, native_handle_t*
inline bool wrapAs(Message* t, native_handle_t** nh, omx_message const& l) {
    *nh = native_handle_create_from_fd(l.fenceFd);
    if (!*nh) {
        return false;
    }
    t->fence = *nh;
    switch (l.type) {
        case omx_message::EVENT:
            t->type = Message::Type::EVENT;
            t->data.eventData.event = uint32_t(l.u.event_data.event);
            t->data.eventData.data1 = l.u.event_data.data1;
            t->data.eventData.data2 = l.u.event_data.data2;
            t->data.eventData.data3 = l.u.event_data.data3;
            t->data.eventData.data4 = l.u.event_data.data4;
            break;
        case omx_message::EMPTY_BUFFER_DONE:
            t->type = Message::Type::EMPTY_BUFFER_DONE;
            t->data.bufferData.buffer = l.u.buffer_data.buffer;
            break;
        case omx_message::FILL_BUFFER_DONE:
            t->type = Message::Type::FILL_BUFFER_DONE;
            t->data.extendedBufferData.buffer = l.u.extended_buffer_data.buffer;
            t->data.extendedBufferData.rangeOffset =
                    l.u.extended_buffer_data.range_offset;
            t->data.extendedBufferData.rangeLength =
                    l.u.extended_buffer_data.range_length;
            t->data.extendedBufferData.flags = l.u.extended_buffer_data.flags;
            t->data.extendedBufferData.timestampUs =
                    l.u.extended_buffer_data.timestamp;
            break;
        case omx_message::FRAME_RENDERED:
            t->type = Message::Type::FRAME_RENDERED;
            t->data.renderData.timestampUs = l.u.render_data.timestamp;
            t->data.renderData.systemTimeNs = l.u.render_data.nanoTime;
            break;
        default:
            native_handle_delete(*nh);
            return false;
    }
    return true;
}

/**
 * \brief Wrap a `Message` inside an `omx_message`.
 *
 * \param[out] l The wrapper of type `omx_message`.
 * \param[in] t The source `Message`.
 * \return `true` if the wrapping is successful; `false` otherwise.
 */
// wrap: Message -> omx_message
inline bool wrapAs(omx_message* l, Message const& t) {
    l->fenceFd = native_handle_read_fd(t.fence);
    switch (t.type) {
        case Message::Type::EVENT:
            l->type = omx_message::EVENT;
            l->u.event_data.event = OMX_EVENTTYPE(t.data.eventData.event);
            l->u.event_data.data1 = t.data.eventData.data1;
            l->u.event_data.data2 = t.data.eventData.data2;
            l->u.event_data.data3 = t.data.eventData.data3;
            l->u.event_data.data4 = t.data.eventData.data4;
            break;
        case Message::Type::EMPTY_BUFFER_DONE:
            l->type = omx_message::EMPTY_BUFFER_DONE;
            l->u.buffer_data.buffer = t.data.bufferData.buffer;
            break;
        case Message::Type::FILL_BUFFER_DONE:
            l->type = omx_message::FILL_BUFFER_DONE;
            l->u.extended_buffer_data.buffer = t.data.extendedBufferData.buffer;
            l->u.extended_buffer_data.range_offset =
                    t.data.extendedBufferData.rangeOffset;
            l->u.extended_buffer_data.range_length =
                    t.data.extendedBufferData.rangeLength;
            l->u.extended_buffer_data.flags = t.data.extendedBufferData.flags;
            l->u.extended_buffer_data.timestamp =
                    t.data.extendedBufferData.timestampUs;
            break;
        case Message::Type::FRAME_RENDERED:
            l->type = omx_message::FRAME_RENDERED;
            l->u.render_data.timestamp = t.data.renderData.timestampUs;
            l->u.render_data.nanoTime = t.data.renderData.systemTimeNs;
            break;
        default:
            return false;
    }
    return true;
}

/**
 * \brief Similar to `wrapTo(omx_message*, Message const&)`, but the output will
 * have an extended lifetime.
 *
 * \param[out] l The output `omx_message`.
 * \param[in] t The source `Message`.
 * \return `true` if the conversion is successful; `false` otherwise.
 *
 * This function calls `wrapto()`, then attempts to duplicate the file
 * descriptor for the fence if it is not `-1`. If duplication fails, `false`
 * will be returned.
 */
// convert: Message -> omx_message
inline bool convertTo(omx_message* l, Message const& t) {
    if (!wrapAs(l, t)) {
        return false;
    }
    if (l->fenceFd == -1) {
        return true;
    }
    l->fenceFd = dup(l->fenceFd);
    return l->fenceFd != -1;
}

/**
 * \brief Wrap an `OMXFenceParcelable` inside a `hidl_handle`.
 *
 * \param[out] t The wrapper of type `hidl_handle`.
 * \param[out] nh The native handle created to hold the file descriptor inside
 * \p l.
 * \param[in] l The source `OMXFenceParcelable`, which essentially contains one
 * file descriptor.
 * \return `true` if \p t and \p nh are successfully created to wrap around \p
 * l; `false` otherwise.
 *
 * On success, \p nh needs to be deleted by the caller with
 * `native_handle_delete()` after \p t and \p nh are no longer needed.
 *
 * On failure, \p nh will not need to be deleted, and \p t will hold an invalid
 * value.
 */
// wrap: OMXFenceParcelable -> hidl_handle, native_handle_t*
inline bool wrapAs(hidl_handle* t, native_handle_t** nh,
        OMXFenceParcelable const& l) {
    *nh = native_handle_create_from_fd(l.get());
    if (!*nh) {
        return false;
    }
    *t = *nh;
    return true;
}

/**
 * \brief Wrap a `hidl_handle` inside an `OMXFenceParcelable`.
 *
 * \param[out] l The wrapper of type `OMXFenceParcelable`.
 * \param[in] t The source `hidl_handle`.
 */
// wrap: hidl_handle -> OMXFenceParcelable
inline void wrapAs(OMXFenceParcelable* l, hidl_handle const& t) {
    l->mFenceFd = native_handle_read_fd(t);
}

/**
 * \brief Convert a `hidl_handle` to `OMXFenceParcelable`. If `hidl_handle`
 * contains file descriptors, the first file descriptor will be duplicated and
 * stored in the output `OMXFenceParcelable`.
 *
 * \param[out] l The output `OMXFenceParcelable`.
 * \param[in] t The input `hidl_handle`.
 * \return `false` if \p t contains a valid file descriptor but duplication
 * fails; `true` otherwise.
 */
// convert: hidl_handle -> OMXFenceParcelable
inline bool convertTo(OMXFenceParcelable* l, hidl_handle const& t) {
    int fd = native_handle_read_fd(t);
    if (fd != -1) {
        fd = dup(fd);
        if (fd == -1) {
            return false;
        }
    }
    l->mFenceFd = fd;
    return true;
}

/**
 * \brief Convert `::android::ColorAspects` to `ColorAspects`.
 *
 * \param[in] l The source `::android::ColorAspects`.
 * \return The corresponding `ColorAspects`.
 */
// convert: ::android::ColorAspects -> ColorAspects
inline ColorAspects toHardwareColorAspects(::android::ColorAspects const& l) {
    return ColorAspects{
            static_cast<ColorAspects::Range>(l.mRange),
            static_cast<ColorAspects::Primaries>(l.mPrimaries),
            static_cast<ColorAspects::Transfer>(l.mTransfer),
            static_cast<ColorAspects::MatrixCoeffs>(l.mMatrixCoeffs)};
}

/**
 * \brief Convert `int32_t` to `ColorAspects`.
 *
 * \param[in] l The source `int32_t`.
 * \return The corresponding `ColorAspects`.
 */
// convert: int32_t -> ColorAspects
inline ColorAspects toHardwareColorAspects(int32_t l) {
    return ColorAspects{
            static_cast<ColorAspects::Range>((l >> 24) & 0xFF),
            static_cast<ColorAspects::Primaries>((l >> 16) & 0xFF),
            static_cast<ColorAspects::Transfer>(l & 0xFF),
            static_cast<ColorAspects::MatrixCoeffs>((l >> 8) & 0xFF)};
}

/**
 * \brief Convert `ColorAspects` to `::android::ColorAspects`.
 *
 * \param[in] t The source `ColorAspects`.
 * \return The corresponding `::android::ColorAspects`.
 */
// convert: ColorAspects -> ::android::ColorAspects
inline int32_t toCompactColorAspects(ColorAspects const& t) {
    return static_cast<int32_t>(
            (static_cast<uint32_t>(t.range) << 24) |
            (static_cast<uint32_t>(t.primaries) << 16) |
            (static_cast<uint32_t>(t.transfer)) |
            (static_cast<uint32_t>(t.matrixCoeffs) << 8));
}

/**
 * \brief Convert `int32_t` to `Dataspace`.
 *
 * \param[in] l The source `int32_t`.
 * \result The corresponding `Dataspace`.
 */
// convert: int32_t -> Dataspace
inline Dataspace toHardwareDataspace(int32_t l) {
    return static_cast<Dataspace>(l);
}

/**
 * \brief Convert `Dataspace` to `int32_t`.
 *
 * \param[in] t The source `Dataspace`.
 * \result The corresponding `int32_t`.
 */
// convert: Dataspace -> int32_t
inline int32_t toRawDataspace(Dataspace const& t) {
    return static_cast<int32_t>(t);
}

/**
 * \brief Wrap an opaque buffer inside a `hidl_vec<uint8_t>`.
 *
 * \param[in] l The pointer to the beginning of the opaque buffer.
 * \param[in] size The size of the buffer.
 * \return A `hidl_vec<uint8_t>` that points to the buffer.
 */
// wrap: void*, size_t -> hidl_vec<uint8_t>
inline hidl_vec<uint8_t> inHidlBytes(void const* l, size_t size) {
    hidl_vec<uint8_t> t;
    t.setToExternal(static_cast<uint8_t*>(const_cast<void*>(l)), size, false);
    return t;
}

/**
 * \brief Create a `hidl_vec<uint8_t>` that is a copy of an opaque buffer.
 *
 * \param[in] l The pointer to the beginning of the opaque buffer.
 * \param[in] size The size of the buffer.
 * \return A `hidl_vec<uint8_t>` that is a copy of the input buffer.
 */
// convert: void*, size_t -> hidl_vec<uint8_t>
inline hidl_vec<uint8_t> toHidlBytes(void const* l, size_t size) {
    hidl_vec<uint8_t> t;
    t.resize(size);
    uint8_t const* src = static_cast<uint8_t const*>(l);
    std::copy(src, src + size, t.data());
    return t;
}

/**
 * \brief Wrap `GraphicBuffer` in `AnwBuffer`.
 *
 * \param[out] t The wrapper of type `AnwBuffer`.
 * \param[in] l The source `GraphicBuffer`.
 */
// wrap: GraphicBuffer -> AnwBuffer
inline void wrapAs(AnwBuffer* t, GraphicBuffer const& l) {
    t->attr.width = l.getWidth();
    t->attr.height = l.getHeight();
    t->attr.stride = l.getStride();
    t->attr.format = static_cast<PixelFormat>(l.getPixelFormat());
    t->attr.layerCount = l.getLayerCount();
    t->attr.usage = l.getUsage();
    t->attr.id = l.getId();
    t->attr.generationNumber = l.getGenerationNumber();
    t->nativeHandle = hidl_handle(l.handle);
}

/**
 * \brief Convert `AnwBuffer` to `GraphicBuffer`.
 *
 * \param[out] l The destination `GraphicBuffer`.
 * \param[in] t The source `AnwBuffer`.
 *
 * This function will duplicate all file descriptors in \p t.
 */
// convert: AnwBuffer -> GraphicBuffer
// Ref: frameworks/native/libs/ui/GraphicBuffer.cpp: GraphicBuffer::flatten
inline bool convertTo(GraphicBuffer* l, AnwBuffer const& t) {
    native_handle_t* handle = t.nativeHandle == nullptr ?
            nullptr : native_handle_clone(t.nativeHandle);

    size_t const numInts = 12 + (handle ? handle->numInts : 0);
    int32_t* ints = new int32_t[numInts];

    size_t numFds = static_cast<size_t>(handle ? handle->numFds : 0);
    int* fds = new int[numFds];

    ints[0] = 'GBFR';
    ints[1] = static_cast<int32_t>(t.attr.width);
    ints[2] = static_cast<int32_t>(t.attr.height);
    ints[3] = static_cast<int32_t>(t.attr.stride);
    ints[4] = static_cast<int32_t>(t.attr.format);
    ints[5] = static_cast<int32_t>(t.attr.layerCount);
    ints[6] = static_cast<int32_t>(t.attr.usage);
    ints[7] = static_cast<int32_t>(t.attr.id >> 32);
    ints[8] = static_cast<int32_t>(t.attr.id & 0xFFFFFFFF);
    ints[9] = static_cast<int32_t>(t.attr.generationNumber);
    ints[10] = 0;
    ints[11] = 0;
    if (handle) {
        ints[10] = static_cast<int32_t>(handle->numFds);
        ints[11] = static_cast<int32_t>(handle->numInts);
        int* intsStart = handle->data + handle->numFds;
        std::copy(handle->data, intsStart, fds);
        std::copy(intsStart, intsStart + handle->numInts, &ints[12]);
    }

    void const* constBuffer = static_cast<void const*>(ints);
    size_t size = numInts * sizeof(int32_t);
    int const* constFds = static_cast<int const*>(fds);
    status_t status = l->unflatten(constBuffer, size, constFds, numFds);

    delete [] fds;
    delete [] ints;
    native_handle_delete(handle);
    return status == NO_ERROR;
}

/**
 * \brief Wrap `GraphicBuffer` in `CodecBuffer`.
 *
 * \param[out] t The wrapper of type `CodecBuffer`.
 * \param[in] l The source `GraphicBuffer`.
 */
// wrap: OMXBuffer -> CodecBuffer
inline CodecBuffer *wrapAs(CodecBuffer *t, sp<GraphicBuffer> const& graphicBuffer) {
    t->sharedMemory = hidl_memory();
    t->nativeHandle = hidl_handle();
    t->type = CodecBuffer::Type::ANW_BUFFER;
    if (graphicBuffer == nullptr) {
        t->attr.anwBuffer.width = 0;
        t->attr.anwBuffer.height = 0;
        t->attr.anwBuffer.stride = 0;
        t->attr.anwBuffer.format = static_cast<PixelFormat>(1);
        t->attr.anwBuffer.layerCount = 0;
        t->attr.anwBuffer.usage = 0;
        return t;
    }
    t->attr.anwBuffer.width = graphicBuffer->getWidth();
    t->attr.anwBuffer.height = graphicBuffer->getHeight();
    t->attr.anwBuffer.stride = graphicBuffer->getStride();
    t->attr.anwBuffer.format = static_cast<PixelFormat>(
            graphicBuffer->getPixelFormat());
    t->attr.anwBuffer.layerCount = graphicBuffer->getLayerCount();
    t->attr.anwBuffer.usage = graphicBuffer->getUsage();
    t->nativeHandle = graphicBuffer->handle;
    return t;
}

/**
 * \brief Wrap `OMXBuffer` in `CodecBuffer`.
 *
 * \param[out] t The wrapper of type `CodecBuffer`.
 * \param[in] l The source `OMXBuffer`.
 * \return `true` if the wrapping is successful; `false` otherwise.
 */
// wrap: OMXBuffer -> CodecBuffer
inline bool wrapAs(CodecBuffer* t, OMXBuffer const& l) {
    t->sharedMemory = hidl_memory();
    t->nativeHandle = hidl_handle();
    switch (l.mBufferType) {
        case OMXBuffer::kBufferTypeInvalid: {
            t->type = CodecBuffer::Type::INVALID;
            return true;
        }
        case OMXBuffer::kBufferTypePreset: {
            t->type = CodecBuffer::Type::PRESET;
            t->attr.preset.rangeLength = static_cast<uint32_t>(l.mRangeLength);
            t->attr.preset.rangeOffset = static_cast<uint32_t>(l.mRangeOffset);
            return true;
        }
        case OMXBuffer::kBufferTypeHidlMemory: {
            t->type = CodecBuffer::Type::SHARED_MEM;
            t->sharedMemory = l.mHidlMemory;
            return true;
        }
        case OMXBuffer::kBufferTypeSharedMem: {
            // This is not supported.
            return false;
        }
        case OMXBuffer::kBufferTypeANWBuffer: {
            wrapAs(t, l.mGraphicBuffer);
            return true;
        }
        case OMXBuffer::kBufferTypeNativeHandle: {
            t->type = CodecBuffer::Type::NATIVE_HANDLE;
            t->nativeHandle = l.mNativeHandle->handle();
            return true;
        }
    }
    return false;
}

/**
 * \brief Convert `CodecBuffer` to `OMXBuffer`.
 *
 * \param[out] l The destination `OMXBuffer`.
 * \param[in] t The source `CodecBuffer`.
 * \return `true` if successful; `false` otherwise.
 */
// convert: CodecBuffer -> OMXBuffer
inline bool convertTo(OMXBuffer* l, CodecBuffer const& t) {
    switch (t.type) {
        case CodecBuffer::Type::INVALID: {
            *l = OMXBuffer();
            return true;
        }
        case CodecBuffer::Type::PRESET: {
            *l = OMXBuffer(
                    t.attr.preset.rangeOffset,
                    t.attr.preset.rangeLength);
            return true;
        }
        case CodecBuffer::Type::SHARED_MEM: {
            *l = OMXBuffer(t.sharedMemory);
            return true;
        }
        case CodecBuffer::Type::ANW_BUFFER: {
            if (t.nativeHandle.getNativeHandle() == nullptr) {
                *l = OMXBuffer(sp<GraphicBuffer>(nullptr));
                return true;
            }
            AnwBuffer anwBuffer;
            anwBuffer.nativeHandle = t.nativeHandle;
            anwBuffer.attr = t.attr.anwBuffer;
            sp<GraphicBuffer> graphicBuffer = new GraphicBuffer();
            if (!convertTo(graphicBuffer.get(), anwBuffer)) {
                return false;
            }
            *l = OMXBuffer(graphicBuffer);
            return true;
        }
        case CodecBuffer::Type::NATIVE_HANDLE: {
            *l = OMXBuffer(NativeHandle::create(
                    native_handle_clone(t.nativeHandle), true));
            return true;
        }
    }
    return false;
}

/**
 * \brief Convert `IOMX::ComponentInfo` to `IOmx::ComponentInfo`.
 *
 * \param[out] t The destination `IOmx::ComponentInfo`.
 * \param[in] l The source `IOMX::ComponentInfo`.
 */
// convert: IOMX::ComponentInfo -> IOmx::ComponentInfo
inline bool convertTo(IOmx::ComponentInfo* t, IOMX::ComponentInfo const& l) {
    t->mName = l.mName.string();
    t->mRoles.resize(l.mRoles.size());
    size_t i = 0;
    for (auto& role : l.mRoles) {
        t->mRoles[i++] = role.string();
    }
    return true;
}

/**
 * \brief Convert `IOmx::ComponentInfo` to `IOMX::ComponentInfo`.
 *
 * \param[out] l The destination `IOMX::ComponentInfo`.
 * \param[in] t The source `IOmx::ComponentInfo`.
 */
// convert: IOmx::ComponentInfo -> IOMX::ComponentInfo
inline bool convertTo(IOMX::ComponentInfo* l, IOmx::ComponentInfo const& t) {
    l->mName = t.mName.c_str();
    l->mRoles.clear();
    for (size_t i = 0; i < t.mRoles.size(); ++i) {
        l->mRoles.push_back(String8(t.mRoles[i].c_str()));
    }
    return true;
}

/**
 * \brief Convert `OMX_BOOL` to `bool`.
 *
 * \param[in] l The source `OMX_BOOL`.
 * \return The destination `bool`.
 */
// convert: OMX_BOOL -> bool
inline bool toRawBool(OMX_BOOL l) {
    return l == OMX_FALSE ? false : true;
}

/**
 * \brief Convert `bool` to `OMX_BOOL`.
 *
 * \param[in] t The source `bool`.
 * \return The destination `OMX_BOOL`.
 */
// convert: bool -> OMX_BOOL
inline OMX_BOOL toEnumBool(bool t) {
    return t ? OMX_TRUE : OMX_FALSE;
}

/**
 * \brief Convert `OMX_COMMANDTYPE` to `uint32_t`.
 *
 * \param[in] l The source `OMX_COMMANDTYPE`.
 * \return The underlying value of type `uint32_t`.
 *
 * `OMX_COMMANDTYPE` is an enum type whose underlying type is `uint32_t`.
 */
// convert: OMX_COMMANDTYPE -> uint32_t
inline uint32_t toRawCommandType(OMX_COMMANDTYPE l) {
    return static_cast<uint32_t>(l);
}

/**
 * \brief Convert `uint32_t` to `OMX_COMMANDTYPE`.
 *
 * \param[in] t The source `uint32_t`.
 * \return The corresponding enum value of type `OMX_COMMANDTYPE`.
 *
 * `OMX_COMMANDTYPE` is an enum type whose underlying type is `uint32_t`.
 */
// convert: uint32_t -> OMX_COMMANDTYPE
inline OMX_COMMANDTYPE toEnumCommandType(uint32_t t) {
    return static_cast<OMX_COMMANDTYPE>(t);
}

/**
 * \brief Convert `OMX_INDEXTYPE` to `uint32_t`.
 *
 * \param[in] l The source `OMX_INDEXTYPE`.
 * \return The underlying value of type `uint32_t`.
 *
 * `OMX_INDEXTYPE` is an enum type whose underlying type is `uint32_t`.
 */
// convert: OMX_INDEXTYPE -> uint32_t
inline uint32_t toRawIndexType(OMX_INDEXTYPE l) {
    return static_cast<uint32_t>(l);
}

/**
 * \brief Convert `uint32_t` to `OMX_INDEXTYPE`.
 *
 * \param[in] t The source `uint32_t`.
 * \return The corresponding enum value of type `OMX_INDEXTYPE`.
 *
 * `OMX_INDEXTYPE` is an enum type whose underlying type is `uint32_t`.
 */
// convert: uint32_t -> OMX_INDEXTYPE
inline OMX_INDEXTYPE toEnumIndexType(uint32_t t) {
    return static_cast<OMX_INDEXTYPE>(t);
}

/**
 * \brief Convert `IOMX::PortMode` to `PortMode`.
 *
 * \param[in] l The source `IOMX::PortMode`.
 * \return The destination `PortMode`.
 */
// convert: IOMX::PortMode -> PortMode
inline PortMode toHardwarePortMode(IOMX::PortMode l) {
    return static_cast<PortMode>(l);
}

/**
 * \brief Convert `PortMode` to `IOMX::PortMode`.
 *
 * \param[in] t The source `PortMode`.
 * \return The destination `IOMX::PortMode`.
 */
// convert: PortMode -> IOMX::PortMode
inline IOMX::PortMode toIOMXPortMode(PortMode t) {
    return static_cast<IOMX::PortMode>(t);
}

/**
 * \brief Convert `OMX_TICKS` to `uint64_t`.
 *
 * \param[in] l The source `OMX_TICKS`.
 * \return The destination `uint64_t`.
 */
// convert: OMX_TICKS -> uint64_t
inline uint64_t toRawTicks(OMX_TICKS l) {
#ifndef OMX_SKIP64BIT
    return static_cast<uint64_t>(l);
#else
    return static_cast<uint64_t>(l.nLowPart) |
            static_cast<uint64_t>(l.nHighPart << 32);
#endif
}

/**
 * \brief Convert `uint64_t` to `OMX_TICKS`.
 *
 * \param[in] l The source `uint64_t`.
 * \return The destination `OMX_TICKS`.
 */
// convert: uint64_t -> OMX_TICKS
inline OMX_TICKS toOMXTicks(uint64_t t) {
#ifndef OMX_SKIP64BIT
    return static_cast<OMX_TICKS>(t);
#else
    return OMX_TICKS{
            static_cast<uint32_t>(t & 0xFFFFFFFF),
            static_cast<uint32_t>(t >> 32)};
#endif
}

/**
 * Conversion functions for types outside media
 * ============================================
 *
 * Some objects in libui and libgui that were made to go through binder calls do
 * not expose ways to read or write their fields to the public. To pass an
 * object of this kind through the HIDL boundary, translation functions need to
 * work around the access restriction by using the publicly available
 * `flatten()` and `unflatten()` functions.
 *
 * All `flatten()` and `unflatten()` overloads follow the same convention as
 * follows:
 *
 *     status_t flatten(ObjectType const& object,
 *                      [OtherType const& other, ...]
 *                      void*& buffer, size_t& size,
 *                      int*& fds, size_t& numFds)
 *
 *     status_t unflatten(ObjectType* object,
 *                        [OtherType* other, ...,]
 *                        void*& buffer, size_t& size,
 *                        int*& fds, size_t& numFds)
 *
 * The number of `other` parameters varies depending on the `ObjectType`. For
 * example, in the process of unflattening an object that contains
 * `hidl_handle`, `other` is needed to hold `native_handle_t` objects that will
 * be created.
 *
 * The last four parameters always work the same way in all overloads of
 * `flatten()` and `unflatten()`:
 * - For `flatten()`, `buffer` is the pointer to the non-fd buffer to be filled,
 *   `size` is the size (in bytes) of the non-fd buffer pointed to by `buffer`,
 *   `fds` is the pointer to the fd buffer to be filled, and `numFds` is the
 *   size (in ints) of the fd buffer pointed to by `fds`.
 * - For `unflatten()`, `buffer` is the pointer to the non-fd buffer to be read
 *   from, `size` is the size (in bytes) of the non-fd buffer pointed to by
 *   `buffer`, `fds` is the pointer to the fd buffer to be read from, and
 *   `numFds` is the size (in ints) of the fd buffer pointed to by `fds`.
 * - After a successful call to `flatten()` or `unflatten()`, `buffer` and `fds`
 *   will be advanced, while `size` and `numFds` will be decreased to reflect
 *   how much storage/data of the two buffers (fd and non-fd) have been used.
 * - After an unsuccessful call, the values of `buffer`, `size`, `fds` and
 *   `numFds` are invalid.
 *
 * The return value of a successful `flatten()` or `unflatten()` call will be
 * `OK` (also aliased as `NO_ERROR`). Any other values indicate a failure.
 *
 * For each object type that supports flattening, there will be two accompanying
 * functions: `getFlattenedSize()` and `getFdCount()`. `getFlattenedSize()` will
 * return the size of the non-fd buffer that the object will need for
 * flattening. `getFdCount()` will return the size of the fd buffer that the
 * object will need for flattening.
 *
 * The set of these four functions, `getFlattenedSize()`, `getFdCount()`,
 * `flatten()` and `unflatten()`, are similar to functions of the same name in
 * the abstract class `Flattenable`. The only difference is that functions in
 * this file are not member functions of the object type. For example, we write
 *
 *     flatten(x, buffer, size, fds, numFds)
 *
 * instead of
 *
 *     x.flatten(buffer, size, fds, numFds)
 *
 * because we cannot modify the type of `x`.
 *
 * There is one exception to the naming convention: `hidl_handle` that
 * represents a fence. The four functions for this "Fence" type have the word
 * "Fence" attched to their names because the object type, which is
 * `hidl_handle`, does not carry the special meaning that the object itself can
 * only contain zero or one file descriptor.
 */

// Ref: frameworks/native/libs/ui/Fence.cpp

/**
 * \brief Return the size of the non-fd buffer required to flatten a fence.
 *
 * \param[in] fence The input fence of type `hidl_handle`.
 * \return The required size of the flat buffer.
 *
 * The current version of this function always returns 4, which is the number of
 * bytes required to store the number of file descriptors contained in the fd
 * part of the flat buffer.
 */
inline size_t getFenceFlattenedSize(hidl_handle const& /* fence */) {
    return 4;
};

/**
 * \brief Return the number of file descriptors contained in a fence.
 *
 * \param[in] fence The input fence of type `hidl_handle`.
 * \return `0` if \p fence does not contain a valid file descriptor, or `1`
 * otherwise.
 */
inline size_t getFenceFdCount(hidl_handle const& fence) {
    return native_handle_read_fd(fence) == -1 ? 0 : 1;
}

/**
 * \brief Unflatten `Fence` to `hidl_handle`.
 *
 * \param[out] fence The destination `hidl_handle`.
 * \param[out] nh The underlying native handle.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * If the return value is `NO_ERROR`, \p nh will point to a newly created
 * native handle, which needs to be deleted with `native_handle_delete()`
 * afterwards.
 */
inline status_t unflattenFence(hidl_handle* fence, native_handle_t** nh,
        void const*& buffer, size_t& size, int const*& fds, size_t& numFds) {
    if (size < 4) {
        return NO_MEMORY;
    }

    uint32_t numFdsInHandle;
    FlattenableUtils::read(buffer, size, numFdsInHandle);

    if (numFdsInHandle > 1) {
        return BAD_VALUE;
    }

    if (numFds < numFdsInHandle) {
        return NO_MEMORY;
    }

    if (numFdsInHandle) {
        *nh = native_handle_create_from_fd(*fds);
        if (*nh == nullptr) {
            return NO_MEMORY;
        }
        *fence = *nh;
        ++fds;
        --numFds;
    } else {
        *nh = nullptr;
        *fence = hidl_handle();
    }

    return NO_ERROR;
}

/**
 * \brief Flatten `hidl_handle` as `Fence`.
 *
 * \param[in] t The source `hidl_handle`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 */
inline status_t flattenFence(hidl_handle const& fence,
        void*& buffer, size_t& size, int*& fds, size_t& numFds) {
    if (size < getFenceFlattenedSize(fence) ||
            numFds < getFenceFdCount(fence)) {
        return NO_MEMORY;
    }
    // Cast to uint32_t since the size of a size_t can vary between 32- and
    // 64-bit processes
    FlattenableUtils::write(buffer, size,
            static_cast<uint32_t>(getFenceFdCount(fence)));
    int fd = native_handle_read_fd(fence);
    if (fd != -1) {
        *fds = fd;
        ++fds;
        --numFds;
    }
    return NO_ERROR;
}

/**
 * \brief Wrap `Fence` in `hidl_handle`.
 *
 * \param[out] t The wrapper of type `hidl_handle`.
 * \param[out] nh The native handle pointed to by \p t.
 * \param[in] l The source `Fence`.
 *
 * On success, \p nh will hold a newly created native handle, which must be
 * deleted manually with `native_handle_delete()` afterwards.
 */
// wrap: Fence -> hidl_handle
inline bool wrapAs(hidl_handle* t, native_handle_t** nh, Fence const& l) {
    size_t const baseSize = l.getFlattenedSize();
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    size_t const baseNumFds = l.getFdCount();
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = static_cast<int*>(baseFds.get());
    size_t numFds = baseNumFds;
    if (l.flatten(buffer, size, fds, numFds) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (unflattenFence(t, nh, constBuffer, size, constFds, numFds)
            != NO_ERROR) {
        return false;
    }

    return true;
}

/**
 * \brief Convert `hidl_handle` to `Fence`.
 *
 * \param[out] l The destination `Fence`. `l` must not have been used
 * (`l->isValid()` must return `false`) before this function is called.
 * \param[in] t The source `hidl_handle`.
 *
 * If \p t contains a valid file descriptor, it will be duplicated.
 */
// convert: hidl_handle -> Fence
inline bool convertTo(Fence* l, hidl_handle const& t) {
    int fd = native_handle_read_fd(t);
    if (fd != -1) {
        fd = dup(fd);
        if (fd == -1) {
            return false;
        }
    }
    native_handle_t* nh = native_handle_create_from_fd(fd);
    if (nh == nullptr) {
        if (fd != -1) {
            close(fd);
        }
        return false;
    }

    size_t const baseSize = getFenceFlattenedSize(t);
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        native_handle_delete(nh);
        return false;
    }

    size_t const baseNumFds = getFenceFdCount(t);
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        native_handle_delete(nh);
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = static_cast<int*>(baseFds.get());
    size_t numFds = baseNumFds;
    if (flattenFence(hidl_handle(nh), buffer, size, fds, numFds) != NO_ERROR) {
        native_handle_delete(nh);
        return false;
    }
    native_handle_delete(nh);

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (l->unflatten(constBuffer, size, constFds, numFds) != NO_ERROR) {
        return false;
    }

    return true;
}

// Ref: frameworks/native/libs/ui/FenceTime.cpp: FenceTime::Snapshot

/**
 * \brief Return the size of the non-fd buffer required to flatten
 * `FenceTimeSnapshot`.
 *
 * \param[in] t The input `FenceTimeSnapshot`.
 * \return The required size of the flat buffer.
 */
inline size_t getFlattenedSize(
        HGraphicBufferProducer::FenceTimeSnapshot const& t) {
    constexpr size_t min = sizeof(t.state);
    switch (t.state) {
        case HGraphicBufferProducer::FenceTimeSnapshot::State::EMPTY:
            return min;
        case HGraphicBufferProducer::FenceTimeSnapshot::State::FENCE:
            return min + getFenceFlattenedSize(t.fence);
        case HGraphicBufferProducer::FenceTimeSnapshot::State::SIGNAL_TIME:
            return min + sizeof(
                    ::android::FenceTime::Snapshot::signalTime);
    }
    return 0;
}

/**
 * \brief Return the number of file descriptors contained in
 * `FenceTimeSnapshot`.
 *
 * \param[in] t The input `FenceTimeSnapshot`.
 * \return The number of file descriptors contained in \p snapshot.
 */
inline size_t getFdCount(
        HGraphicBufferProducer::FenceTimeSnapshot const& t) {
    return t.state ==
            HGraphicBufferProducer::FenceTimeSnapshot::State::FENCE ?
            getFenceFdCount(t.fence) : 0;
}

/**
 * \brief Flatten `FenceTimeSnapshot`.
 *
 * \param[in] t The source `FenceTimeSnapshot`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * This function will duplicate the file descriptor in `t.fence` if `t.state ==
 * FENCE`.
 */
inline status_t flatten(HGraphicBufferProducer::FenceTimeSnapshot const& t,
        void*& buffer, size_t& size, int*& fds, size_t& numFds) {
    if (size < getFlattenedSize(t)) {
        return NO_MEMORY;
    }

    switch (t.state) {
        case HGraphicBufferProducer::FenceTimeSnapshot::State::EMPTY:
            FlattenableUtils::write(buffer, size,
                    ::android::FenceTime::Snapshot::State::EMPTY);
            return NO_ERROR;
        case HGraphicBufferProducer::FenceTimeSnapshot::State::FENCE:
            FlattenableUtils::write(buffer, size,
                    ::android::FenceTime::Snapshot::State::FENCE);
            return flattenFence(t.fence, buffer, size, fds, numFds);
        case HGraphicBufferProducer::FenceTimeSnapshot::State::SIGNAL_TIME:
            FlattenableUtils::write(buffer, size,
                    ::android::FenceTime::Snapshot::State::SIGNAL_TIME);
            FlattenableUtils::write(buffer, size, t.signalTimeNs);
            return NO_ERROR;
    }
    return NO_ERROR;
}

/**
 * \brief Unflatten `FenceTimeSnapshot`.
 *
 * \param[out] t The destination `FenceTimeSnapshot`.
 * \param[out] nh The underlying native handle.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * If the return value is `NO_ERROR` and the constructed snapshot contains a
 * file descriptor, \p nh will be created to hold that file descriptor. In this
 * case, \p nh needs to be deleted with `native_handle_delete()` afterwards.
 */
inline status_t unflatten(
        HGraphicBufferProducer::FenceTimeSnapshot* t, native_handle_t** nh,
        void const*& buffer, size_t& size, int const*& fds, size_t& numFds) {
    if (size < sizeof(t->state)) {
        return NO_MEMORY;
    }

    *nh = nullptr;
    ::android::FenceTime::Snapshot::State state;
    FlattenableUtils::read(buffer, size, state);
    switch (state) {
        case ::android::FenceTime::Snapshot::State::EMPTY:
            t->state = HGraphicBufferProducer::FenceTimeSnapshot::State::EMPTY;
            return NO_ERROR;
        case ::android::FenceTime::Snapshot::State::FENCE:
            t->state = HGraphicBufferProducer::FenceTimeSnapshot::State::FENCE;
            return unflattenFence(&t->fence, nh, buffer, size, fds, numFds);
        case ::android::FenceTime::Snapshot::State::SIGNAL_TIME:
            t->state = HGraphicBufferProducer::FenceTimeSnapshot::State::SIGNAL_TIME;
            if (size < sizeof(t->signalTimeNs)) {
                return NO_MEMORY;
            }
            FlattenableUtils::read(buffer, size, t->signalTimeNs);
            return NO_ERROR;
    }
    return NO_ERROR;
}

// Ref: frameworks/native/libs/gui/FrameTimestamps.cpp: FrameEventsDelta

/**
 * \brief Return a lower bound on the size of the non-fd buffer required to
 * flatten `FrameEventsDelta`.
 *
 * \param[in] t The input `FrameEventsDelta`.
 * \return A lower bound on the size of the flat buffer.
 */
constexpr size_t minFlattenedSize(
        HGraphicBufferProducer::FrameEventsDelta const& /* t */) {
    return sizeof(uint64_t) + // mFrameNumber
            sizeof(uint8_t) + // mIndex
            sizeof(uint8_t) + // mAddPostCompositeCalled
            sizeof(uint8_t) + // mAddRetireCalled
            sizeof(uint8_t) + // mAddReleaseCalled
            sizeof(nsecs_t) + // mPostedTime
            sizeof(nsecs_t) + // mRequestedPresentTime
            sizeof(nsecs_t) + // mLatchTime
            sizeof(nsecs_t) + // mFirstRefreshStartTime
            sizeof(nsecs_t); // mLastRefreshStartTime
}

/**
 * \brief Return the size of the non-fd buffer required to flatten
 * `FrameEventsDelta`.
 *
 * \param[in] t The input `FrameEventsDelta`.
 * \return The required size of the flat buffer.
 */
inline size_t getFlattenedSize(
        HGraphicBufferProducer::FrameEventsDelta const& t) {
    return minFlattenedSize(t) +
            getFlattenedSize(t.gpuCompositionDoneFence) +
            getFlattenedSize(t.displayPresentFence) +
            getFlattenedSize(t.displayRetireFence) +
            getFlattenedSize(t.releaseFence);
};

/**
 * \brief Return the number of file descriptors contained in
 * `FrameEventsDelta`.
 *
 * \param[in] t The input `FrameEventsDelta`.
 * \return The number of file descriptors contained in \p t.
 */
inline size_t getFdCount(
        HGraphicBufferProducer::FrameEventsDelta const& t) {
    return getFdCount(t.gpuCompositionDoneFence) +
            getFdCount(t.displayPresentFence) +
            getFdCount(t.displayRetireFence) +
            getFdCount(t.releaseFence);
};

/**
 * \brief Unflatten `FrameEventsDelta`.
 *
 * \param[out] t The destination `FrameEventsDelta`.
 * \param[out] nh The underlying array of native handles.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * If the return value is `NO_ERROR`, \p nh will have length 4, and it will be
 * populated with `nullptr` or newly created handles. Each non-null slot in \p
 * nh will need to be deleted manually with `native_handle_delete()`.
 */
inline status_t unflatten(HGraphicBufferProducer::FrameEventsDelta* t,
        std::vector<native_handle_t*>* nh,
        void const*& buffer, size_t& size, int const*& fds, size_t& numFds) {
    if (size < minFlattenedSize(*t)) {
        return NO_MEMORY;
    }
    FlattenableUtils::read(buffer, size, t->frameNumber);

    // These were written as uint8_t for alignment.
    uint8_t temp = 0;
    FlattenableUtils::read(buffer, size, temp);
    size_t index = static_cast<size_t>(temp);
    if (index >= ::android::FrameEventHistory::MAX_FRAME_HISTORY) {
        return BAD_VALUE;
    }
    t->index = static_cast<uint32_t>(index);

    FlattenableUtils::read(buffer, size, temp);
    t->addPostCompositeCalled = static_cast<bool>(temp);
    FlattenableUtils::read(buffer, size, temp);
    t->addRetireCalled = static_cast<bool>(temp);
    FlattenableUtils::read(buffer, size, temp);
    t->addReleaseCalled = static_cast<bool>(temp);

    FlattenableUtils::read(buffer, size, t->postedTimeNs);
    FlattenableUtils::read(buffer, size, t->requestedPresentTimeNs);
    FlattenableUtils::read(buffer, size, t->latchTimeNs);
    FlattenableUtils::read(buffer, size, t->firstRefreshStartTimeNs);
    FlattenableUtils::read(buffer, size, t->lastRefreshStartTimeNs);
    FlattenableUtils::read(buffer, size, t->dequeueReadyTime);

    // Fences
    HGraphicBufferProducer::FenceTimeSnapshot* tSnapshot[4];
    tSnapshot[0] = &t->gpuCompositionDoneFence;
    tSnapshot[1] = &t->displayPresentFence;
    tSnapshot[2] = &t->displayRetireFence;
    tSnapshot[3] = &t->releaseFence;
    nh->resize(4);
    for (size_t snapshotIndex = 0; snapshotIndex < 4; ++snapshotIndex) {
        status_t status = unflatten(
                tSnapshot[snapshotIndex], &((*nh)[snapshotIndex]),
                buffer, size, fds, numFds);
        if (status != NO_ERROR) {
            while (snapshotIndex > 0) {
                --snapshotIndex;
                if ((*nh)[snapshotIndex] != nullptr) {
                    native_handle_delete((*nh)[snapshotIndex]);
                }
            }
            return status;
        }
    }
    return NO_ERROR;
}

/**
 * \brief Flatten `FrameEventsDelta`.
 *
 * \param[in] t The source `FrameEventsDelta`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * This function will duplicate file descriptors contained in \p t.
 */
// Ref: frameworks/native/libs/gui/FrameTimestamp.cpp:
//      FrameEventsDelta::flatten
inline status_t flatten(HGraphicBufferProducer::FrameEventsDelta const& t,
        void*& buffer, size_t& size, int*& fds, size_t numFds) {
    // Check that t.index is within a valid range.
    if (t.index >= static_cast<uint32_t>(FrameEventHistory::MAX_FRAME_HISTORY)
            || t.index > std::numeric_limits<uint8_t>::max()) {
        return BAD_VALUE;
    }

    FlattenableUtils::write(buffer, size, t.frameNumber);

    // These are static_cast to uint8_t for alignment.
    FlattenableUtils::write(buffer, size, static_cast<uint8_t>(t.index));
    FlattenableUtils::write(
            buffer, size, static_cast<uint8_t>(t.addPostCompositeCalled));
    FlattenableUtils::write(
            buffer, size, static_cast<uint8_t>(t.addRetireCalled));
    FlattenableUtils::write(
            buffer, size, static_cast<uint8_t>(t.addReleaseCalled));

    FlattenableUtils::write(buffer, size, t.postedTimeNs);
    FlattenableUtils::write(buffer, size, t.requestedPresentTimeNs);
    FlattenableUtils::write(buffer, size, t.latchTimeNs);
    FlattenableUtils::write(buffer, size, t.firstRefreshStartTimeNs);
    FlattenableUtils::write(buffer, size, t.lastRefreshStartTimeNs);
    FlattenableUtils::write(buffer, size, t.dequeueReadyTime);

    // Fences
    HGraphicBufferProducer::FenceTimeSnapshot const* tSnapshot[4];
    tSnapshot[0] = &t.gpuCompositionDoneFence;
    tSnapshot[1] = &t.displayPresentFence;
    tSnapshot[2] = &t.displayRetireFence;
    tSnapshot[3] = &t.releaseFence;
    for (size_t snapshotIndex = 0; snapshotIndex < 4; ++snapshotIndex) {
        status_t status = flatten(
                *(tSnapshot[snapshotIndex]), buffer, size, fds, numFds);
        if (status != NO_ERROR) {
            return status;
        }
    }
    return NO_ERROR;
}

// Ref: frameworks/native/libs/gui/FrameTimestamps.cpp: FrameEventHistoryDelta

/**
 * \brief Return the size of the non-fd buffer required to flatten
 * `HGraphicBufferProducer::FrameEventHistoryDelta`.
 *
 * \param[in] t The input `HGraphicBufferProducer::FrameEventHistoryDelta`.
 * \return The required size of the flat buffer.
 */
inline size_t getFlattenedSize(
        HGraphicBufferProducer::FrameEventHistoryDelta const& t) {
    size_t size = 4 + // mDeltas.size()
            sizeof(t.compositorTiming);
    for (size_t i = 0; i < t.deltas.size(); ++i) {
        size += getFlattenedSize(t.deltas[i]);
    }
    return size;
}

/**
 * \brief Return the number of file descriptors contained in
 * `HGraphicBufferProducer::FrameEventHistoryDelta`.
 *
 * \param[in] t The input `HGraphicBufferProducer::FrameEventHistoryDelta`.
 * \return The number of file descriptors contained in \p t.
 */
inline size_t getFdCount(
        HGraphicBufferProducer::FrameEventHistoryDelta const& t) {
    size_t numFds = 0;
    for (size_t i = 0; i < t.deltas.size(); ++i) {
        numFds += getFdCount(t.deltas[i]);
    }
    return numFds;
}

/**
 * \brief Unflatten `FrameEventHistoryDelta`.
 *
 * \param[out] t The destination `FrameEventHistoryDelta`.
 * \param[out] nh The underlying array of arrays of native handles.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * If the return value is `NO_ERROR`, \p nh will be populated with `nullptr` or
 * newly created handles. The second dimension of \p nh will be 4. Each non-null
 * slot in \p nh will need to be deleted manually with `native_handle_delete()`.
 */
inline status_t unflatten(
        HGraphicBufferProducer::FrameEventHistoryDelta* t,
        std::vector<std::vector<native_handle_t*> >* nh,
        void const*& buffer, size_t& size, int const*& fds, size_t& numFds) {
    if (size < 4) {
        return NO_MEMORY;
    }

    FlattenableUtils::read(buffer, size, t->compositorTiming);

    uint32_t deltaCount = 0;
    FlattenableUtils::read(buffer, size, deltaCount);
    if (static_cast<size_t>(deltaCount) >
            ::android::FrameEventHistory::MAX_FRAME_HISTORY) {
        return BAD_VALUE;
    }
    t->deltas.resize(deltaCount);
    nh->resize(deltaCount);
    for (size_t deltaIndex = 0; deltaIndex < deltaCount; ++deltaIndex) {
        status_t status = unflatten(
                &(t->deltas[deltaIndex]), &((*nh)[deltaIndex]),
                buffer, size, fds, numFds);
        if (status != NO_ERROR) {
            return status;
        }
    }
    return NO_ERROR;
}

/**
 * \brief Flatten `FrameEventHistoryDelta`.
 *
 * \param[in] t The source `FrameEventHistoryDelta`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * This function will duplicate file descriptors contained in \p t.
 */
inline status_t flatten(
        HGraphicBufferProducer::FrameEventHistoryDelta const& t,
        void*& buffer, size_t& size, int*& fds, size_t& numFds) {
    if (t.deltas.size() > ::android::FrameEventHistory::MAX_FRAME_HISTORY) {
        return BAD_VALUE;
    }
    if (size < getFlattenedSize(t)) {
        return NO_MEMORY;
    }

    FlattenableUtils::write(buffer, size, t.compositorTiming);

    FlattenableUtils::write(buffer, size, static_cast<uint32_t>(t.deltas.size()));
    for (size_t deltaIndex = 0; deltaIndex < t.deltas.size(); ++deltaIndex) {
        status_t status = flatten(t.deltas[deltaIndex], buffer, size, fds, numFds);
        if (status != NO_ERROR) {
            return status;
        }
    }
    return NO_ERROR;
}

/**
 * \brief Wrap `::android::FrameEventHistoryData` in
 * `HGraphicBufferProducer::FrameEventHistoryDelta`.
 *
 * \param[out] t The wrapper of type
 * `HGraphicBufferProducer::FrameEventHistoryDelta`.
 * \param[out] nh The array of array of native handles that are referred to by
 * members of \p t.
 * \param[in] l The source `::android::FrameEventHistoryDelta`.
 *
 * On success, each member of \p nh will be either `nullptr` or a newly created
 * native handle. All the non-`nullptr` elements must be deleted individually
 * with `native_handle_delete()`.
 */
inline bool wrapAs(HGraphicBufferProducer::FrameEventHistoryDelta* t,
        std::vector<std::vector<native_handle_t*> >* nh,
        ::android::FrameEventHistoryDelta const& l) {

    size_t const baseSize = l.getFlattenedSize();
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    size_t const baseNumFds = l.getFdCount();
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = baseFds.get();
    size_t numFds = baseNumFds;
    if (l.flatten(buffer, size, fds, numFds) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (unflatten(t, nh, constBuffer, size, constFds, numFds) != NO_ERROR) {
        return false;
    }

    return true;
}

/**
 * \brief Convert `HGraphicBufferProducer::FrameEventHistoryDelta` to
 * `::android::FrameEventHistoryDelta`.
 *
 * \param[out] l The destination `::android::FrameEventHistoryDelta`.
 * \param[in] t The source `HGraphicBufferProducer::FrameEventHistoryDelta`.
 *
 * This function will duplicate all file descriptors contained in \p t.
 */
inline bool convertTo(
        ::android::FrameEventHistoryDelta* l,
        HGraphicBufferProducer::FrameEventHistoryDelta const& t) {

    size_t const baseSize = getFlattenedSize(t);
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    size_t const baseNumFds = getFdCount(t);
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = static_cast<int*>(baseFds.get());
    size_t numFds = baseNumFds;
    if (flatten(t, buffer, size, fds, numFds) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (l->unflatten(constBuffer, size, constFds, numFds) != NO_ERROR) {
        return false;
    }

    return true;
}

// Ref: frameworks/native/libs/ui/Region.cpp

/**
 * \brief Return the size of the buffer required to flatten `Region`.
 *
 * \param[in] t The input `Region`.
 * \return The required size of the flat buffer.
 */
inline size_t getFlattenedSize(Region const& t) {
    return sizeof(uint32_t) + t.size() * sizeof(::android::Rect);
}

/**
 * \brief Unflatten `Region`.
 *
 * \param[out] t The destination `Region`.
 * \param[in,out] buffer The pointer to the flat buffer.
 * \param[in,out] size The size of the flat buffer.
 * \return `NO_ERROR` on success; other value on failure.
 */
inline status_t unflatten(Region* t, void const*& buffer, size_t& size) {
    if (size < sizeof(uint32_t)) {
        return NO_MEMORY;
    }

    uint32_t numRects = 0;
    FlattenableUtils::read(buffer, size, numRects);
    if (size < numRects * sizeof(Rect)) {
        return NO_MEMORY;
    }
    if (numRects > (UINT32_MAX / sizeof(Rect))) {
        return NO_MEMORY;
    }

    t->resize(numRects);
    for (size_t r = 0; r < numRects; ++r) {
        ::android::Rect rect(::android::Rect::EMPTY_RECT);
        status_t status = rect.unflatten(buffer, size);
        if (status != NO_ERROR) {
            return status;
        }
        FlattenableUtils::advance(buffer, size, sizeof(rect));
        (*t)[r] = Rect{
                static_cast<int32_t>(rect.left),
                static_cast<int32_t>(rect.top),
                static_cast<int32_t>(rect.right),
                static_cast<int32_t>(rect.bottom)};
    }
    return NO_ERROR;
}

/**
 * \brief Flatten `Region`.
 *
 * \param[in] t The source `Region`.
 * \param[in,out] buffer The pointer to the flat buffer.
 * \param[in,out] size The size of the flat buffer.
 * \return `NO_ERROR` on success; other value on failure.
 */
inline status_t flatten(Region const& t, void*& buffer, size_t& size) {
    if (size < getFlattenedSize(t)) {
        return NO_MEMORY;
    }

    FlattenableUtils::write(buffer, size, static_cast<uint32_t>(t.size()));
    for (size_t r = 0; r < t.size(); ++r) {
        ::android::Rect rect(
                static_cast<int32_t>(t[r].left),
                static_cast<int32_t>(t[r].top),
                static_cast<int32_t>(t[r].right),
                static_cast<int32_t>(t[r].bottom));
        status_t status = rect.flatten(buffer, size);
        if (status != NO_ERROR) {
            return status;
        }
        FlattenableUtils::advance(buffer, size, sizeof(rect));
    }
    return NO_ERROR;
}

/**
 * \brief Convert `::android::Region` to `Region`.
 *
 * \param[out] t The destination `Region`.
 * \param[in] l The source `::android::Region`.
 */
// convert: ::android::Region -> Region
inline bool convertTo(Region* t, ::android::Region const& l) {
    size_t const baseSize = l.getFlattenedSize();
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    if (l.flatten(buffer, size) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    if (unflatten(t, constBuffer, size) != NO_ERROR) {
        return false;
    }

    return true;
}

/**
 * \brief Convert `Region` to `::android::Region`.
 *
 * \param[out] l The destination `::android::Region`.
 * \param[in] t The source `Region`.
 */
// convert: Region -> ::android::Region
inline bool convertTo(::android::Region* l, Region const& t) {
    size_t const baseSize = getFlattenedSize(t);
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    if (flatten(t, buffer, size) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    if (l->unflatten(constBuffer, size) != NO_ERROR) {
        return false;
    }

    return true;
}

// Ref: frameworks/native/libs/gui/BGraphicBufferProducer.cpp:
//      BGraphicBufferProducer::QueueBufferInput

/**
 * \brief Return a lower bound on the size of the buffer required to flatten
 * `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[in] t The input `HGraphicBufferProducer::QueueBufferInput`.
 * \return A lower bound on the size of the flat buffer.
 */
constexpr size_t minFlattenedSize(
        HGraphicBufferProducer::QueueBufferInput const& /* t */) {
    return sizeof(int64_t) + // timestamp
            sizeof(int) + // isAutoTimestamp
            sizeof(android_dataspace) + // dataSpace
            sizeof(::android::Rect) + // crop
            sizeof(int) + // scalingMode
            sizeof(uint32_t) + // transform
            sizeof(uint32_t) + // stickyTransform
            sizeof(bool); // getFrameTimestamps
}

/**
 * \brief Return the size of the buffer required to flatten
 * `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[in] t The input `HGraphicBufferProducer::QueueBufferInput`.
 * \return The required size of the flat buffer.
 */
inline size_t getFlattenedSize(HGraphicBufferProducer::QueueBufferInput const& t) {
    return minFlattenedSize(t) +
            getFenceFlattenedSize(t.fence) +
            getFlattenedSize(t.surfaceDamage);
}

/**
 * \brief Return the number of file descriptors contained in
 * `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[in] t The input `HGraphicBufferProducer::QueueBufferInput`.
 * \return The number of file descriptors contained in \p t.
 */
inline size_t getFdCount(
        HGraphicBufferProducer::QueueBufferInput const& t) {
    return getFenceFdCount(t.fence);
}

/**
 * \brief Flatten `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[in] t The source `HGraphicBufferProducer::QueueBufferInput`.
 * \param[out] nh The native handle cloned from `t.fence`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * This function will duplicate the file descriptor in `t.fence`. */
inline status_t flatten(HGraphicBufferProducer::QueueBufferInput const& t,
        native_handle_t** nh,
        void*& buffer, size_t& size, int*& fds, size_t& numFds) {
    if (size < getFlattenedSize(t)) {
        return NO_MEMORY;
    }

    FlattenableUtils::write(buffer, size, t.timestamp);
    FlattenableUtils::write(buffer, size, static_cast<int>(t.isAutoTimestamp));
    FlattenableUtils::write(buffer, size,
            static_cast<android_dataspace_t>(t.dataSpace));
    FlattenableUtils::write(buffer, size, ::android::Rect(
            static_cast<int32_t>(t.crop.left),
            static_cast<int32_t>(t.crop.top),
            static_cast<int32_t>(t.crop.right),
            static_cast<int32_t>(t.crop.bottom)));
    FlattenableUtils::write(buffer, size, static_cast<int>(t.scalingMode));
    FlattenableUtils::write(buffer, size, t.transform);
    FlattenableUtils::write(buffer, size, t.stickyTransform);
    FlattenableUtils::write(buffer, size, t.getFrameTimestamps);

    *nh = t.fence.getNativeHandle() == nullptr ?
            nullptr : native_handle_clone(t.fence);
    status_t status = flattenFence(hidl_handle(*nh), buffer, size, fds, numFds);
    if (status != NO_ERROR) {
        return status;
    }
    return flatten(t.surfaceDamage, buffer, size);
}

/**
 * \brief Unflatten `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[out] t The destination `HGraphicBufferProducer::QueueBufferInput`.
 * \param[out] nh The underlying native handle for `t->fence`.
 * \param[in,out] buffer The pointer to the flat non-fd buffer.
 * \param[in,out] size The size of the flat non-fd buffer.
 * \param[in,out] fds The pointer to the flat fd buffer.
 * \param[in,out] numFds The size of the flat fd buffer.
 * \return `NO_ERROR` on success; other value on failure.
 *
 * If the return value is `NO_ERROR` and `t->fence` contains a valid file
 * descriptor, \p nh will be a newly created native handle holding that file
 * descriptor. \p nh needs to be deleted with `native_handle_delete()`
 * afterwards.
 */
inline status_t unflatten(
        HGraphicBufferProducer::QueueBufferInput* t, native_handle_t** nh,
        void const*& buffer, size_t& size, int const*& fds, size_t& numFds) {
    if (size < minFlattenedSize(*t)) {
        return NO_MEMORY;
    }

    FlattenableUtils::read(buffer, size, t->timestamp);
    int lIsAutoTimestamp;
    FlattenableUtils::read(buffer, size, lIsAutoTimestamp);
    t->isAutoTimestamp = static_cast<int32_t>(lIsAutoTimestamp);
    android_dataspace_t lDataSpace;
    FlattenableUtils::read(buffer, size, lDataSpace);
    t->dataSpace = static_cast<Dataspace>(lDataSpace);
    Rect lCrop;
    FlattenableUtils::read(buffer, size, lCrop);
    t->crop = Rect{
            static_cast<int32_t>(lCrop.left),
            static_cast<int32_t>(lCrop.top),
            static_cast<int32_t>(lCrop.right),
            static_cast<int32_t>(lCrop.bottom)};
    int lScalingMode;
    FlattenableUtils::read(buffer, size, lScalingMode);
    t->scalingMode = static_cast<int32_t>(lScalingMode);
    FlattenableUtils::read(buffer, size, t->transform);
    FlattenableUtils::read(buffer, size, t->stickyTransform);
    FlattenableUtils::read(buffer, size, t->getFrameTimestamps);

    status_t status = unflattenFence(&(t->fence), nh,
            buffer, size, fds, numFds);
    if (status != NO_ERROR) {
        return status;
    }
    return unflatten(&(t->surfaceDamage), buffer, size);
}

/**
 * \brief Wrap `BGraphicBufferProducer::QueueBufferInput` in
 * `HGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[out] t The wrapper of type
 * `HGraphicBufferProducer::QueueBufferInput`.
 * \param[out] nh The underlying native handle for `t->fence`.
 * \param[in] l The source `BGraphicBufferProducer::QueueBufferInput`.
 *
 * If the return value is `true` and `t->fence` contains a valid file
 * descriptor, \p nh will be a newly created native handle holding that file
 * descriptor. \p nh needs to be deleted with `native_handle_delete()`
 * afterwards.
 */
inline bool wrapAs(
        HGraphicBufferProducer::QueueBufferInput* t,
        native_handle_t** nh,
        BGraphicBufferProducer::QueueBufferInput const& l) {

    size_t const baseSize = l.getFlattenedSize();
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    size_t const baseNumFds = l.getFdCount();
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = baseFds.get();
    size_t numFds = baseNumFds;
    if (l.flatten(buffer, size, fds, numFds) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (unflatten(t, nh, constBuffer, size, constFds, numFds) != NO_ERROR) {
        return false;
    }

    return true;
}

/**
 * \brief Convert `HGraphicBufferProducer::QueueBufferInput` to
 * `BGraphicBufferProducer::QueueBufferInput`.
 *
 * \param[out] l The destination `BGraphicBufferProducer::QueueBufferInput`.
 * \param[in] t The source `HGraphicBufferProducer::QueueBufferInput`.
 *
 * If `t.fence` has a valid file descriptor, it will be duplicated.
 */
inline bool convertTo(
        BGraphicBufferProducer::QueueBufferInput* l,
        HGraphicBufferProducer::QueueBufferInput const& t) {

    size_t const baseSize = getFlattenedSize(t);
    std::unique_ptr<uint8_t[]> baseBuffer(
            new (std::nothrow) uint8_t[baseSize]);
    if (!baseBuffer) {
        return false;
    }

    size_t const baseNumFds = getFdCount(t);
    std::unique_ptr<int[]> baseFds(
            new (std::nothrow) int[baseNumFds]);
    if (!baseFds) {
        return false;
    }

    void* buffer = static_cast<void*>(baseBuffer.get());
    size_t size = baseSize;
    int* fds = baseFds.get();
    size_t numFds = baseNumFds;
    native_handle_t* nh;
    if (flatten(t, &nh, buffer, size, fds, numFds) != NO_ERROR) {
        return false;
    }

    void const* constBuffer = static_cast<void const*>(baseBuffer.get());
    size = baseSize;
    int const* constFds = static_cast<int const*>(baseFds.get());
    numFds = baseNumFds;
    if (l->unflatten(constBuffer, size, constFds, numFds) != NO_ERROR) {
        if (nh != nullptr) {
            native_handle_close(nh);
            native_handle_delete(nh);
        }
        return false;
    }

    native_handle_delete(nh);
    return true;
}

// Ref: frameworks/native/libs/gui/BGraphicBufferProducer.cpp:
//      BGraphicBufferProducer::QueueBufferOutput

/**
 * \brief Wrap `BGraphicBufferProducer::QueueBufferOutput` in
 * `HGraphicBufferProducer::QueueBufferOutput`.
 *
 * \param[out] t The wrapper of type
 * `HGraphicBufferProducer::QueueBufferOutput`.
 * \param[out] nh The array of array of native handles that are referred to by
 * members of \p t.
 * \param[in] l The source `BGraphicBufferProducer::QueueBufferOutput`.
 *
 * On success, each member of \p nh will be either `nullptr` or a newly created
 * native handle. All the non-`nullptr` elements must be deleted individually
 * with `native_handle_delete()`.
 */
// wrap: BGraphicBufferProducer::QueueBufferOutput ->
// HGraphicBufferProducer::QueueBufferOutput
inline bool wrapAs(HGraphicBufferProducer::QueueBufferOutput* t,
        std::vector<std::vector<native_handle_t*> >* nh,
        BGraphicBufferProducer::QueueBufferOutput const& l) {
    if (!wrapAs(&(t->frameTimestamps), nh, l.frameTimestamps)) {
        return false;
    }
    t->width = l.width;
    t->height = l.height;
    t->transformHint = l.transformHint;
    t->numPendingBuffers = l.numPendingBuffers;
    t->nextFrameNumber = l.nextFrameNumber;
    t->bufferReplaced = l.bufferReplaced;
    return true;
}

/**
 * \brief Convert `HGraphicBufferProducer::QueueBufferOutput` to
 * `BGraphicBufferProducer::QueueBufferOutput`.
 *
 * \param[out] l The destination `BGraphicBufferProducer::QueueBufferOutput`.
 * \param[in] t The source `HGraphicBufferProducer::QueueBufferOutput`.
 *
 * This function will duplicate all file descriptors contained in \p t.
 */
// convert: HGraphicBufferProducer::QueueBufferOutput ->
// BGraphicBufferProducer::QueueBufferOutput
inline bool convertTo(
        BGraphicBufferProducer::QueueBufferOutput* l,
        HGraphicBufferProducer::QueueBufferOutput const& t) {
    if (!convertTo(&(l->frameTimestamps), t.frameTimestamps)) {
        return false;
    }
    l->width = t.width;
    l->height = t.height;
    l->transformHint = t.transformHint;
    l->numPendingBuffers = t.numPendingBuffers;
    l->nextFrameNumber = t.nextFrameNumber;
    l->bufferReplaced = t.bufferReplaced;
    return true;
}

/**
 * \brief Convert `BGraphicBufferProducer::DisconnectMode` to
 * `HGraphicBufferProducer::DisconnectMode`.
 *
 * \param[in] l The source `BGraphicBufferProducer::DisconnectMode`.
 * \return The corresponding `HGraphicBufferProducer::DisconnectMode`.
 */
inline HGraphicBufferProducer::DisconnectMode toOmxDisconnectMode(
        BGraphicBufferProducer::DisconnectMode l) {
    switch (l) {
        case BGraphicBufferProducer::DisconnectMode::Api:
            return HGraphicBufferProducer::DisconnectMode::API;
        case BGraphicBufferProducer::DisconnectMode::AllLocal:
            return HGraphicBufferProducer::DisconnectMode::ALL_LOCAL;
    }
    return HGraphicBufferProducer::DisconnectMode::API;
}

/**
 * \brief Convert `HGraphicBufferProducer::DisconnectMode` to
 * `BGraphicBufferProducer::DisconnectMode`.
 *
 * \param[in] l The source `HGraphicBufferProducer::DisconnectMode`.
 * \return The corresponding `BGraphicBufferProducer::DisconnectMode`.
 */
inline BGraphicBufferProducer::DisconnectMode toGuiDisconnectMode(
        HGraphicBufferProducer::DisconnectMode t) {
    switch (t) {
        case HGraphicBufferProducer::DisconnectMode::API:
            return BGraphicBufferProducer::DisconnectMode::Api;
        case HGraphicBufferProducer::DisconnectMode::ALL_LOCAL:
            return BGraphicBufferProducer::DisconnectMode::AllLocal;
    }
    return BGraphicBufferProducer::DisconnectMode::Api;
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_MEDIA_OMX_V1_0__CONVERSION_H
