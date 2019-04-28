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

#ifndef C2BUFFER_H_
#define C2BUFFER_H_

#include <C2.h>
#include <C2Param.h> // for C2Info

#include <list>
#include <memory>

typedef int C2Fence;

#ifdef __ANDROID__

// #include <system/window.h>
#include <cutils/native_handle.h>
#include <hardware/gralloc.h> // TODO: remove

typedef native_handle_t C2Handle;

#else

typedef void* C2Handle;

#endif

namespace android {

/// \defgroup buffer Buffers
/// @{

/// \defgroup buffer_sync Synchronization
/// @{

/**
 * Synchronization is accomplished using event and fence objects.
 *
 * These are cross-process extensions of promise/future infrastructure.
 * Events are analogous to std::promise<void>, whereas fences are to std::shared_future<void>.
 *
 * Fences and events are shareable/copyable.
 *
 * Fences are used in two scenarios, and all copied instances refer to the same event.
 * \todo do events need to be copyable or should they be unique?
 *
 * acquire sync fence object: signaled when it is safe for the component or client to access
 * (the contents of) an object.
 *
 * release sync fence object: \todo
 *
 * Fences can be backed by hardware. Hardware fences are guaranteed to signal NO MATTER WHAT within
 * a short (platform specific) amount of time; this guarantee is usually less than 15 msecs.
 */

/**
 * Fence object used by components and the framework.
 *
 * Implements the waiting for an event, analogous to a 'future'.
 *
 * To be implemented by vendors if using HW fences.
 */
class C2Fence {
public:
    /**
     * Waits for a fence to be signaled with a timeout.
     *
     * \todo a mechanism to cancel a wait - for now the only way to do this is to abandon the
     * event, but fences are shared so canceling a wait will cancel all waits.
     *
     * \param timeoutNs           the maximum time to wait in nsecs
     *
     * \retval C2_OK            the fence has been signaled
     * \retval C2_TIMED_OUT     the fence has not been signaled within the timeout
     * \retval C2_BAD_STATE     the fence has been abandoned without being signaled (it will never
     *                          be signaled)
     * \retval C2_NO_PERMISSION no permission to wait for the fence (unexpected - system)
     * \retval C2_CORRUPTED     some unknown error prevented waiting for the fence (unexpected)
     */
    C2Error wait(nsecs_t timeoutNs);

    /**
     * Used to check if this fence is valid (if there is a chance for it to be signaled.)
     * A fence becomes invalid if the controling event is destroyed without it signaling the fence.
     *
     * \return whether this fence is valid
     */
    bool valid() const;

    /**
     * Used to check if this fence has been signaled (is ready).
     *
     * \return whether this fence has been signaled
     */
    bool ready() const;

    /**
     * Returns a file descriptor that can be used to wait for this fence in a select system call.
     * \note The returned file descriptor, if valid, must be closed by the caller.
     *
     * This can be used in e.g. poll() system calls. This file becomes readable (POLLIN) when the
     * fence is signaled, and bad (POLLERR) if the fence is abandoned.
     *
     * \return a file descriptor representing this fence (with ownership), or -1 if the fence
     * has already been signaled (\todo or abandoned).
     *
     * \todo this must be compatible with fences used by gralloc
     */
    int fd() const;

    /**
     * Returns whether this fence is a hardware-backed fence.
     * \return whether this is a hardware fence
     */
    bool isHW() const;

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/**
 * Event object used by components and the framework.
 *
 * Implements the signaling of an event, analogous to a 'promise'.
 *
 * Hardware backed events do not go through this object, and must be exposed directly as fences
 * by vendors.
 */
class C2Event {
public:
    /**
     * Returns a fence for this event.
     */
    C2Fence fence() const;

    /**
     * Signals (all) associated fence(s).
     * This has no effect no effect if the event was already signaled or abandoned.
     *
     * \retval C2_OK            the fence(s) were successfully signaled
     * \retval C2_BAD_STATE     the fence(s) have already been abandoned or merged (caller error)
     * \retval C2_ALREADY_EXISTS the fence(s) have already been signaled (caller error)
     * \retval C2_NO_PERMISSION no permission to signal the fence (unexpected - system)
     * \retval C2_CORRUPTED     some unknown error prevented signaling the fence(s) (unexpected)
     */
    C2Error fire();

    /**
     * Trigger this event from the merging of the supplied fences. This means that it will be
     * abandoned if any of these fences have been abandoned, and it will be fired if all of these
     * fences have been signaled.
     *
     * \retval C2_OK            the merging was successfully done
     * \retval C2_NO_MEMORY     not enough memory to perform the merging
     * \retval C2_ALREADY_EXISTS    the fence have already been merged (caller error)
     * \retval C2_BAD_STATE     the fence have already been signaled or abandoned (caller error)
     * \retval C2_NO_PERMISSION no permission to merge the fence (unexpected - system)
     * \retval C2_CORRUPTED     some unknown error prevented merging the fence(s) (unexpected)
     */
    C2Error merge(std::vector<C2Fence> fences);

    /**
     * Abandons the event and any associated fence(s).
     * \note Call this to explicitly abandon an event before it is destructed to avoid a warning.
     *
     * This has no effect no effect if the event was already signaled or abandoned.
     *
     * \retval C2_OK            the fence(s) were successfully signaled
     * \retval C2_BAD_STATE     the fence(s) have already been signaled or merged (caller error)
     * \retval C2_ALREADY_EXISTS    the fence(s) have already been abandoned (caller error)
     * \retval C2_NO_PERMISSION no permission to abandon the fence (unexpected - system)
     * \retval C2_CORRUPTED     some unknown error prevented signaling the fence(s) (unexpected)
     */
    C2Error abandon();

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/// \addtogroup buf_internal Internal
/// @{

/**
 * Interface for objects that encapsulate an updatable error value.
 */
struct _C2InnateError {
    inline C2Error error() const { return mError; }

protected:
    _C2InnateError(C2Error error) : mError(error) { }

    C2Error mError; // this error is updatable by the object
};

/// @}

/**
 * This is a utility template for objects protected by an acquire fence, so that errors during
 * acquiring the object are propagated to the object itself.
 */
template<typename T>
class C2Acquirable : public C2Fence {
public:
    /**
     * Acquires the object protected by an acquire fence. Any errors during the mapping will be
     * passed to the object.
     *
     * \return acquired object potentially invalidated if waiting for the fence failed.
     */
    T get();

protected:
    C2Acquirable(C2Error error, C2Fence fence, T t) : C2Fence(fence), mInitialError(error), mT(t) { }

private:
    C2Error mInitialError;
    T mT; // TODO: move instead of copy
};

/// @}

/// \defgroup linear Linear Data Blocks
/// @{

/**************************************************************************************************
  LINEAR ASPECTS, BLOCKS AND VIEWS
**************************************************************************************************/

/**
 * Common aspect for all objects that have a linear capacity.
 */
class _C2LinearCapacityAspect {
/// \name Linear capacity interface
/// @{
public:
    inline uint32_t capacity() const { return mCapacity; }

protected:

#if UINTPTR_MAX == 0xffffffff
    static_assert(sizeof(size_t) == sizeof(uint32_t), "size_t is too big");
#else
    static_assert(sizeof(size_t) > sizeof(uint32_t), "size_t is too small");
    // explicitly disable construction from size_t
    inline explicit _C2LinearCapacityAspect(size_t capacity) = delete;
#endif

    inline explicit _C2LinearCapacityAspect(uint32_t capacity)
      : mCapacity(capacity) { }

    inline explicit _C2LinearCapacityAspect(const _C2LinearCapacityAspect *parent)
        : mCapacity(parent == nullptr ? 0 : parent->capacity()) { }

private:
    const uint32_t mCapacity;
/// @}
};

/**
 * Aspect for objects that have a linear range.
 *
 * This class is copiable.
 */
class _C2LinearRangeAspect : public _C2LinearCapacityAspect {
/// \name Linear range interface
/// @{
public:
    inline uint32_t offset() const { return mOffset; }
    inline uint32_t size() const { return mSize; }

protected:
    inline explicit _C2LinearRangeAspect(const _C2LinearCapacityAspect *parent)
        : _C2LinearCapacityAspect(parent),
          mOffset(0),
          mSize(capacity()) { }

    inline _C2LinearRangeAspect(const _C2LinearCapacityAspect *parent, size_t offset, size_t size)
        : _C2LinearCapacityAspect(parent),
          mOffset(c2_min(offset, capacity())),
          mSize(c2_min(size, capacity() - mOffset)) { }

    // subsection of the two [offset, offset + size] ranges
    inline _C2LinearRangeAspect(const _C2LinearRangeAspect *parent, size_t offset, size_t size)
        : _C2LinearCapacityAspect(parent == nullptr ? 0 : parent->capacity()),
          mOffset(c2_min(c2_max(offset, parent == nullptr ? 0 : parent->offset()), capacity())),
          mSize(c2_min(c2_min(size, parent == nullptr ? 0 : parent->size()), capacity() - mOffset)) { }

private:
    friend class _C2EditableLinearRange;
    // invariants 0 <= mOffset <= mOffset + mSize <= capacity()
    uint32_t mOffset;
    uint32_t mSize;
/// @}
};

/**
 * Aspect for objects that have an editable linear range.
 *
 * This class is copiable.
 */
class _C2EditableLinearRange : public _C2LinearRangeAspect {
protected:
    inline explicit _C2EditableLinearRange(const _C2LinearCapacityAspect *parent)
        : _C2LinearRangeAspect(parent) { }

    inline _C2EditableLinearRange(const _C2LinearCapacityAspect *parent, size_t offset, size_t size)
        : _C2LinearRangeAspect(parent, offset, size) { }

    // subsection of the two [offset, offset + size] ranges
    inline _C2EditableLinearRange(const _C2LinearRangeAspect *parent, size_t offset, size_t size)
        : _C2LinearRangeAspect(parent, offset, size) { }

/// \name Editable linear range interface
/// @{

    /**
     * Sets the offset to |offset|, while trying to keep the end of the buffer unchanged (e.g.
     * size will grow if offset is decreased, and may shrink if offset is increased.) Returns
     * true if successful, which is equivalent to if 0 <= |offset| <= capacity().
     *
     * Note: setting offset and size will yield different result depending on the order of the
     * operations. Always set offset first to ensure proper size.
     */
    inline bool setOffset(uint32_t offset) {
        if (offset > capacity()) {
            return false;
        }

        if (offset > mOffset + mSize) {
            mSize = 0;
        } else {
            mSize = mOffset + mSize - offset;
        }
        mOffset = offset;
        return true;
    }
    /**
     * Sets the size to |size|. Returns true if successful, which is equivalent to
     * if 0 <= |size| <= capacity() - offset().
     *
     * Note: setting offset and size will yield different result depending on the order of the
     * operations. Always set offset first to ensure proper size.
     */
    inline bool setSize(uint32_t size) {
        if (size > capacity() - mOffset) {
            return false;
        } else {
            mSize = size;
            return true;
        }
    }
    /**
     * Sets the offset to |offset| with best effort. Same as setOffset() except that offset will
     * be clamped to the buffer capacity.
     *
     * Note: setting offset and size (even using best effort) will yield different result depending
     * on the order of the operations. Always set offset first to ensure proper size.
     */
    inline void setOffset_be(uint32_t offset) {
        if (offset > capacity()) {
            offset = capacity();
        }
        if (offset > mOffset + mSize) {
            mSize = 0;
        } else {
            mSize = mOffset + mSize - offset;
        }
        mOffset = offset;
    }
    /**
     * Sets the size to |size| with best effort. Same as setSize() except that the selected region
     * will be clamped to the buffer capacity (e.g. size is clamped to [0, capacity() - offset()]).
     *
     * Note: setting offset and size (even using best effort) will yield different result depending
     * on the order of the operations. Always set offset first to ensure proper size.
     */
    inline void setSize_be(uint32_t size) {
        mSize = std::min(size, capacity() - mOffset);
    }
/// @}
};

// ================================================================================================
//  BLOCKS
// ================================================================================================

/**
 * Blocks are sections of allocations. They can be either 1D or 2D.
 */

class C2LinearAllocation;

class C2Block1D : public _C2LinearRangeAspect {
public:
    const C2Handle *handle() const;

protected:
    C2Block1D(std::shared_ptr<C2LinearAllocation> alloc);
    C2Block1D(std::shared_ptr<C2LinearAllocation> alloc, size_t offset, size_t size);

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/**
 * Read view provides read-only access for a linear memory segment.
 *
 * This class is copiable.
 */
class C2ReadView : public _C2LinearCapacityAspect {
public:
    /**
     * \return pointer to the start of the block or nullptr on error.
     */
    const uint8_t *data();

    /**
     * Returns a portion of this view.
     *
     * \param offset  the start offset of the portion. \note This is clamped to the capacity of this
     *              view.
     * \param size    the size of the portion. \note This is clamped to the remaining data from offset.
     *
     * \return a read view containing a portion of this view
     */
    C2ReadView subView(size_t offset, size_t size) const;

    /**
     * \return error during the creation/mapping of this view.
     */
    C2Error error();

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/**
 * Write view provides read/write access for a linear memory segment.
 *
 * This class is copiable. \todo movable only?
 */
class C2WriteView : public _C2EditableLinearRange {
public:
    /**
     * Start of the block.
     *
     * \return pointer to the start of the block or nullptr on error.
     */
    uint8_t *base();

    /**
     * \return pointer to the block at the current offset or nullptr on error.
     */
    uint8_t *data();

    /**
     * \return error during the creation/mapping of this view.
     */
    C2Error error();

private:
    class Impl;
    /// \todo should this be unique_ptr to make this movable only - to avoid inconsistent regions
    /// between copies.
    std::shared_ptr<Impl> mImpl;
};

/**
 * A constant (read-only) linear block (portion of an allocation) with an acquire fence.
 * Blocks are unmapped when created, and can be mapped into a read view on demand.
 *
 * This class is copiable and contains a reference to the allocation that it is based on.
 */
class C2ConstLinearBlock : public C2Block1D {
public:
    /**
     * Maps this block into memory and returns a read view for it.
     *
     * \return a read view for this block.
     */
    C2Acquirable<C2ReadView> map() const;

    /**
     * Returns a portion of this block.
     *
     * \param offset  the start offset of the portion. \note This is clamped to the capacity of this
     *              block.
     * \param size    the size of the portion. \note This is clamped to the remaining data from offset.
     *
     * \return a constant linear block containing a portion of this block
     */
    C2ConstLinearBlock subBlock(size_t offset, size_t size) const;

    /**
     * Returns the acquire fence for this block.
     *
     * \return a fence that must be waited on before reading the block.
     */
    C2Fence fence() const { return mFence; }

private:
    C2Fence mFence;
};

/**
 * Linear block is a writeable 1D block. Once written, it can be shared in whole or in parts with
 * consumers/readers as read-only const linear block(s).
 */
class C2LinearBlock : public C2Block1D {
public:
    /**
     * Maps this block into memory and returns a write view for it.
     *
     * \return a write view for this block.
     */
    C2Acquirable<C2WriteView> map();

    /**
     * Creates a read-only const linear block for a portion of this block; optionally protected
     * by an acquire fence. There are two ways to use this:
     *
     * 1) share ready block after writing data into the block. In this case no fence shall be
     *    supplied, and the block shall not be modified after calling this method.
     * 2) share block metadata before actually (finishing) writing the data into the block. In
     *    this case a fence must be supplied that will be triggered when the data is written.
     *    The block shall be modified only until firing the event for the fence.
     */
    C2ConstLinearBlock share(size_t offset, size_t size, C2Fence fence);
};

/// @}

/**************************************************************************************************
  CIRCULAR BLOCKS AND VIEWS
**************************************************************************************************/

/// \defgroup circular Circular buffer support
/// @{

/**
 * Circular blocks can be used to share data between a writer and a reader (and/or other consumers)-
 * in a memory-efficient way by reusing a section of memory. Circular blocks are a bit more complex
 * than single reader/single writer schemes to facilitate block-based consuming of data.
 *
 * They can operate in two modes:
 *
 * 1) one writer that creates blocks to be consumed (this model can be used by components)
 *
 * 2) one writer that writes continuously, and one reader that can creates blocks to be consumed
 *    by further recipients (this model is used by the framework, and cannot be used by components.)
 *
 * Circular blocks have four segments with running pointers:
 *  - reserved: data reserved and available for the writer
 *  - committed: data committed by the writer and available to the reader (if present)
 *  - used: data used by consumers (if present)
 *  - available: unused data available to be reserved
 */
class C2CircularBlock : public C2Block1D {
    // TODO: add methods

private:
    size_t mReserved __unused;   // end of reserved section
    size_t mCommitted __unused;  // end of committed section
    size_t mUsed __unused;       // end of used section
    size_t mFree __unused;       // end of free section
};

class _C2CircularBlockSegment : public _C2LinearCapacityAspect {
public:
    /**
     * Returns the available size for this segment.
     *
     * \return currently available size for this segment
     */
    size_t available() const;

    /**
     * Reserve some space for this segment from its current start.
     *
     * \param size    desired space in bytes
     * \param fence   a pointer to an acquire fence. If non-null, the reservation is asynchronous and
     *              a fence will be stored here that will be signaled when the reservation is
     *              complete. If null, the reservation is synchronous.
     *
     * \retval C2_OK            the space was successfully reserved
     * \retval C2_NO_MEMORY     the space requested cannot be reserved
     * \retval C2_TIMED_OUT     the reservation timed out \todo when?
     * \retval C2_CORRUPTED     some unknown error prevented reserving space. (unexpected)
     */
    C2Error reserve(size_t size, C2Fence *fence /* nullable */);

    /**
     * Abandons a portion of this segment. This will move to the beginning of this segment.
     *
     * \note This methods is only allowed if this segment is producing blocks.
     *
     * \param size    number of bytes to abandon
     *
     * \retval C2_OK            the data was successfully abandoned
     * \retval C2_TIMED_OUT     the operation timed out (unexpected)
     * \retval C2_CORRUPTED     some unknown error prevented abandoning the data (unexpected)
     */
    C2Error abandon(size_t size);

    /**
     * Share a portion as block(s) with consumers (these are moved to the used section).
     *
     * \note This methods is only allowed if this segment is producing blocks.
     * \note Share does not move the beginning of the segment. (\todo add abandon/offset?)
     *
     * \param size    number of bytes to share
     * \param fence   fence to be used for the section
     * \param blocks  list where the blocks of the section are appended to
     *
     * \retval C2_OK            the portion was successfully shared
     * \retval C2_NO_MEMORY     not enough memory to share the portion
     * \retval C2_TIMED_OUT     the operation timed out (unexpected)
     * \retval C2_CORRUPTED     some unknown error prevented sharing the data (unexpected)
     */
    C2Error share(size_t size, C2Fence fence, std::list<C2ConstLinearBlock> &blocks);

    /**
     * Returns the beginning offset of this segment from the start of this circular block.
     *
     * @return beginning offset
     */
    size_t begin();

    /**
     * Returns the end offset of this segment from the start of this circular block.
     *
     * @return end offset
     */
    size_t end();
};

/**
 * A circular write-view is a dynamic mapped view for a segment of a circular block. Care must be
 * taken when using this view so that only the section owned by the segment is modified.
 */
class C2CircularWriteView : public _C2LinearCapacityAspect {
public:
    /**
     * Start of the circular block.
     * \note the segment does not own this pointer.
     *
     * \return pointer to the start of the circular block or nullptr on error.
     */
    uint8_t *base();

    /**
     * \return error during the creation/mapping of this view.
     */
    C2Error error();
};

/**
 * The writer of a circular buffer.
 *
 * Can commit data to a reader (not supported for components) OR share data blocks directly with a
 * consumer.
 *
 * If a component supports outputting data into circular buffers, it must allocate a circular
 * block and use a circular writer.
 */
class C2CircularWriter : public _C2CircularBlockSegment {
public:
    /**
     * Commits a portion of this segment to the next segment. This moves the beginning of the
     * segment.
     *
     * \param size    number of bytes to commit to the next segment
     * \param fence   fence used for the commit (the fence must signal before the data is committed)
     */
    C2Error commit(size_t size, C2Fence fence);

    /**
     * Maps this block into memory and returns a write view for it.
     *
     * \return a write view for this block.
     */
    C2Acquirable<C2CircularWriteView> map();
};

/// @}

/// \defgroup graphic Graphic Data Blocks
/// @{

/**
 * Interface for objects that have a width and height (planar capacity).
 */
class _C2PlanarCapacityAspect {
/// \name Planar capacity interface
/// @{
public:
    inline uint32_t width() const { return mWidth; }
    inline uint32_t height() const { return mHeight; }

protected:
    inline _C2PlanarCapacityAspect(uint32_t width, uint32_t height)
      : mWidth(width), mHeight(height) { }

    inline _C2PlanarCapacityAspect(const _C2PlanarCapacityAspect *parent)
        : mWidth(parent == nullptr ? 0 : parent->width()),
          mHeight(parent == nullptr ? 0 : parent->height()) { }

private:
    const uint32_t mWidth;
    const uint32_t mHeight;
/// @}
};

/**
 * C2Rect: rectangle type with non-negative coordinates.
 *
 * \note This struct has public fields without getters/setters. All methods are inline.
 */
struct C2Rect {
// public:
    uint32_t mLeft;
    uint32_t mTop;
    uint32_t mWidth;
    uint32_t mHeight;

    inline C2Rect(uint32_t width, uint32_t height)
        : C2Rect(width, height, 0, 0) { }

    inline C2Rect(uint32_t width, uint32_t height, uint32_t left, uint32_t top)
        : mLeft(left), mTop(top), mWidth(width), mHeight(height) { }

    // utility methods

    inline bool isEmpty() const {
        return mWidth == 0 || mHeight == 0;
    }

    inline bool isValid() const {
        return mLeft <= ~mWidth && mTop <= ~mHeight;
    }

    inline operator bool() const {
        return isValid() && !isEmpty();
    }

    inline bool operator!() const {
        return !bool(*this);
    }

    inline bool contains(const C2Rect &other) const {
        if (!isValid() || !other.isValid()) {
            return false;
        } else if (other.isEmpty()) {
            return true;
        } else {
            return mLeft <= other.mLeft && mTop <= other.mTop
                    && mLeft + mWidth >= other.mLeft + other.mWidth
                    && mTop + mHeight >= other.mTop + other.mHeight;
        }
    }

    inline bool operator==(const C2Rect &other) const {
        if (!isValid()) {
            return !other.isValid();
        } else if (isEmpty()) {
            return other.isEmpty();
        } else {
            return mLeft == other.mLeft && mTop == other.mTop
                    && mWidth == other.mWidth && mHeight == other.mHeight;
        }
    }

    inline bool operator!=(const C2Rect &other) const {
        return !operator==(other);
    }

    inline bool operator>=(const C2Rect &other) const {
        return contains(other);
    }

    inline bool operator>(const C2Rect &other) const {
        return contains(other) && !operator==(other);
    }

    inline bool operator<=(const C2Rect &other) const {
        return other.contains(*this);
    }

    inline bool operator<(const C2Rect &other) const {
        return other.contains(*this) && !operator==(other);
    }
};

/**
 * C2PlaneInfo: information on the layout of flexible planes.
 *
 * Public fields without getters/setters.
 */
struct C2PlaneInfo {
// public:
    enum Channel : uint32_t {
        Y,
        R,
        G,
        B,
        A,
        Cr,
        Cb,
    } mChannel;

    int32_t mColInc;               // column increment in bytes. may be negative
    int32_t mRowInc;               // row increment in bytes. may be negative
    uint32_t mHorizSubsampling;    // subsampling compared to width
    uint32_t mVertSubsampling;     // subsampling compared to height

    uint32_t mBitDepth;
    uint32_t mAllocatedDepth;

    inline ssize_t minOffset(uint32_t width, uint32_t height) {
        ssize_t offs = 0;
        if (width > 0 && mColInc < 0) {
            offs += mColInc * (ssize_t)(width - 1);
        }
        if (height > 0 && mRowInc < 0) {
            offs += mRowInc * (ssize_t)(height - 1);
        }
        return offs;
    }

    inline ssize_t maxOffset(uint32_t width, uint32_t height, uint32_t allocatedDepth) {
        ssize_t offs = (allocatedDepth + 7) >> 3;
        if (width > 0 && mColInc > 0) {
            offs += mColInc * (ssize_t)(width - 1);
        }
        if (height > 0 && mRowInc > 0) {
            offs += mRowInc * (ssize_t)(height - 1);
        }
        return offs;
    }
};

struct C2PlaneLayout {
public:
    enum Type : uint32_t {
        MEDIA_IMAGE_TYPE_UNKNOWN = 0,
        MEDIA_IMAGE_TYPE_YUV = 0x100,
        MEDIA_IMAGE_TYPE_YUVA,
        MEDIA_IMAGE_TYPE_RGB,
        MEDIA_IMAGE_TYPE_RGBA,
    };

    Type mType;
    uint32_t mNumPlanes;               // number of planes

    enum PlaneIndex : uint32_t {
        Y = 0,
        U = 1,
        V = 2,
        R = 0,
        G = 1,
        B = 2,
        A = 3,
        MAX_NUM_PLANES = 4,
    };

    C2PlaneInfo mPlanes[MAX_NUM_PLANES];
};

/**
 * Aspect for objects that have a planar section (crop rectangle).
 *
 * This class is copiable.
 */
class _C2PlanarSection : public _C2PlanarCapacityAspect {
/// \name Planar section interface
/// @{
public:
    // crop can be an empty rect, does not have to line up with subsampling
    // NOTE: we do not support floating-point crop
    inline const C2Rect crop() { return mCrop; }

    /**
     *  Sets crop to crop intersected with [(0,0) .. (width, height)]
     */
    inline void setCrop_be(const C2Rect &crop);

    /**
     * If crop is within the dimensions of this object, it sets crop to it.
     *
     * \return true iff crop is within the dimensions of this object
     */
    inline bool setCrop(const C2Rect &crop);

private:
    C2Rect mCrop;
/// @}
};

class C2Block2D : public _C2PlanarSection {
public:
    const C2Handle *handle() const;

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/**
 * Graphic view provides read or read-write access for a graphic block.
 *
 * This class is copiable.
 *
 * \note Due to the subsampling of graphic buffers, a read view must still contain a crop rectangle
 * to ensure subsampling is followed. This results in nearly identical interface between read and
 * write views, so C2GraphicView can encompass both of them.
 */
class C2GraphicView : public _C2PlanarSection {
public:
    /**
     * \return pointer to the start of the block or nullptr on error.
     */
    const uint8_t *data() const;

    /**
     * \return pointer to the start of the block or nullptr on error.
     */
    uint8_t *data();

    /**
     * Returns a section of this view.
     *
     * \param rect    the dimension of the section. \note This is clamped to the crop of this view.
     *
     * \return a read view containing the requested section of this view
     */
    const C2GraphicView subView(const C2Rect &rect) const;
    C2GraphicView subView(const C2Rect &rect);

    /**
     * \return error during the creation/mapping of this view.
     */
    C2Error error() const;

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;
};

/**
 * A constant (read-only) graphic block (portion of an allocation) with an acquire fence.
 * Blocks are unmapped when created, and can be mapped into a read view on demand.
 *
 * This class is copiable and contains a reference to the allocation that it is based on.
 */
class C2ConstGraphicBlock : public C2Block2D {
public:
    /**
     * Maps this block into memory and returns a read view for it.
     *
     * \return a read view for this block.
     */
    C2Acquirable<const C2GraphicView> map() const;

    /**
     * Returns a section of this block.
     *
     * \param rect    the coordinates of the section. \note This is clamped to the crop rectangle of
     *              this block.
     *
     * \return a constant graphic block containing a portion of this block
     */
    C2ConstGraphicBlock subBlock(const C2Rect &rect) const;

    /**
     * Returns the acquire fence for this block.
     *
     * \return a fence that must be waited on before reading the block.
     */
    C2Fence fence() const { return mFence; }

private:
    C2Fence mFence;
};

/**
 * Graphic block is a writeable 2D block. Once written, it can be shared in whole or in part with
 * consumers/readers as read-only const graphic block.
 */
class C2GraphicBlock : public C2Block2D {
public:
    /**
     * Maps this block into memory and returns a write view for it.
     *
     * \return a write view for this block.
     */
    C2Acquirable<C2GraphicView> map();

    /**
     * Creates a read-only const linear block for a portion of this block; optionally protected
     * by an acquire fence. There are two ways to use this:
     *
     * 1) share ready block after writing data into the block. In this case no fence shall be
     *    supplied, and the block shall not be modified after calling this method.
     * 2) share block metadata before actually (finishing) writing the data into the block. In
     *    this case a fence must be supplied that will be triggered when the data is written.
     *    The block shall be modified only until firing the event for the fence.
     */
    C2ConstGraphicBlock share(const C2Rect &crop, C2Fence fence);
};

/// @}

/// \defgroup buffer_onj Buffer objects
/// @{

// ================================================================================================
//  BUFFERS
// ================================================================================================

/// \todo: Do we still need this?
///
// There are 2 kinds of buffers: linear or graphic. Linear buffers can contain a single block, or
// a list of blocks (LINEAR_CHUNKS). Support for list of blocks is optional, and can allow consuming
// data from circular buffers or scattered data sources without extra memcpy. Currently, list of
// graphic blocks is not supported.

class C2LinearBuffer;   // read-write buffer
class C2GraphicBuffer;  // read-write buffer
class C2LinearChunksBuffer;

/**
 * C2BufferData: the main, non-meta data of a buffer. A buffer can contain either linear blocks
 * or graphic blocks, and can contain either a single block or multiple blocks. This is determined
 * by its type.
 */
class C2BufferData {
public:
    /**
     *  The type of buffer data.
     */
    enum Type : uint32_t {
        LINEAR,             ///< the buffer contains a single linear block
        LINEAR_CHUNKS,      ///< the buffer contains one or more linear blocks
        GRAPHIC,            ///< the buffer contains a single graphic block
        GRAPHIC_CHUNKS,     ///< the buffer contains one of more graphic blocks
    };

    /**
     * Gets the type of this buffer (data).
     * \return the type of this buffer data.
     */
    Type type() const;

    /**
     * Gets the linear blocks of this buffer.
     * \return a constant list of const linear blocks of this buffer.
     * \retval empty list if this buffer does not contain linear block(s).
     */
    const std::list<C2ConstLinearBlock> linearBlocks() const;

    /**
     * Gets the graphic blocks of this buffer.
     * \return a constant list of const graphic blocks of this buffer.
     * \retval empty list if this buffer does not contain graphic block(s).
     */
    const std::list<C2ConstGraphicBlock> graphicBlocks() const;

private:
    class Impl;
    std::shared_ptr<Impl> mImpl;

protected:
    // no public constructor
    // C2BufferData(const std::shared_ptr<const Impl> &impl) : mImpl(impl) {}
};

/**
 * C2Buffer: buffer base class. These are always used as shared_ptrs. Though the underlying buffer
 * objects (native buffers, ion buffers, or dmabufs) are reference-counted by the system,
 * C2Buffers hold only a single reference.
 *
 * These objects cannot be used on the stack.
 */
class C2Buffer {
public:
    /**
     * Gets the buffer's data.
     *
     * \return the buffer's data.
     */
    const C2BufferData data() const;

    /**
     * These will still work if used in onDeathNotify.
     */
#if 0
    inline std::shared_ptr<C2LinearBuffer> asLinearBuffer() const {
        return mType == LINEAR ? std::shared_ptr::reinterpret_cast<C2LinearBuffer>(this) : nullptr;
    }

    inline std::shared_ptr<C2GraphicBuffer> asGraphicBuffer() const {
        return mType == GRAPHIC ? std::shared_ptr::reinterpret_cast<C2GraphicBuffer>(this) : nullptr;
    }

    inline std::shared_ptr<C2CircularBuffer> asCircularBuffer() const {
        return mType == CIRCULAR ? std::shared_ptr::reinterpret_cast<C2CircularBuffer>(this) : nullptr;
    }
#endif

    ///@name Pre-destroy notification handling
    ///@{

    /**
     * Register for notification just prior to the destruction of this object.
     */
    typedef void (*OnDestroyNotify) (const C2Buffer *buf, void *arg);

    /**
     * Registers for a pre-destroy notification. This is called just prior to the destruction of
     * this buffer (when this buffer is no longer valid.)
     *
     * \param onDestroyNotify   the notification callback
     * \param arg               an arbitrary parameter passed to the callback
     *
     * \retval C2_OK        the registration was successful.
     * \retval C2_DUPLICATE a notification was already registered for this callback and argument
     * \retval C2_NO_MEMORY not enough memory to register for this callback
     * \retval C2_CORRUPTED an unknown error prevented the registration (unexpected)
     */
    C2Error registerOnDestroyNotify(OnDestroyNotify *onDestroyNotify, void *arg = nullptr);

    /**
     * Unregisters a previously registered pre-destroy notification.
     *
     * \param onDestroyNotify   the notification callback
     * \param arg               an arbitrary parameter passed to the callback
     *
     * \retval C2_OK        the unregistration was successful.
     * \retval C2_NOT_FOUND the notification was not found
     * \retval C2_CORRUPTED an unknown error prevented the registration (unexpected)
     */
    C2Error unregisterOnDestroyNotify(OnDestroyNotify *onDestroyNotify, void *arg = nullptr);

    ///@}

    virtual ~C2Buffer() = default;

    ///@name Buffer-specific arbitrary metadata handling
    ///@{

    /**
     * Gets the list of metadata associated with this buffer.
     *
     * \return a constant list of info objects associated with this buffer.
     */
    const std::list<std::shared_ptr<const C2Info>> infos() const;

    /**
     * Attaches (or updates) an (existing) metadata for this buffer.
     * If the metadata is stream specific, the stream information will be reset.
     *
     * \param info Metadata to update
     *
     * \retval C2_OK        the metadata was successfully attached/updated.
     * \retval C2_NO_MEMORY not enough memory to attach the metadata (this return value is not
     *                      used if the same kind of metadata is already attached to the buffer).
     */
    C2Error setInfo(const std::shared_ptr<C2Info> &info);

    /**
     * Checks if there is a certain type of metadata attached to this buffer.
     *
     * \param index the parameter type of the metadata
     *
     * \return true iff there is a metadata with the parameter type attached to this buffer.
     */
    bool hasInfo(C2Param::Type index) const;
    std::shared_ptr<C2Info> removeInfo(C2Param::Type index) const;
    ///@}

protected:
    // no public constructor
    inline C2Buffer() = default;

private:
//    Type _mType;
};

/**
 * An extension of C2Info objects that can contain arbitrary buffer data.
 *
 * \note This object is not describable and contains opaque data.
 */
class C2InfoBuffer {
public:
    /**
     * Gets the index of this info object.
     *
     * \return the parameter index.
     */
    const C2Param::Index index() const;

    /**
     * Gets the buffer's data.
     *
     * \return the buffer's data.
     */
    const C2BufferData data() const;
};

/// @}

/**************************************************************************************************
  ALLOCATIONS
**************************************************************************************************/

/// \defgroup allocator Allocation and memory placement
/// @{

/**
 * Buffer/memory usage bits. These are used by the allocators to select optimal memory type/pool and
 * buffer layout.
 *
 * \note This struct has public fields without getters/setters. All methods are inline.
 */
struct C2MemoryUsage {
// public:
    // TODO: match these to gralloc1.h
    enum Consumer : uint64_t {
        kSoftwareRead        = GRALLOC_USAGE_SW_READ_OFTEN,
        kRenderScriptRead    = GRALLOC_USAGE_RENDERSCRIPT,
        kTextureRead         = GRALLOC_USAGE_HW_TEXTURE,
        kHardwareComposer    = GRALLOC_USAGE_HW_COMPOSER,
        kHardwareEncoder     = GRALLOC_USAGE_HW_VIDEO_ENCODER,
        kProtectedRead       = GRALLOC_USAGE_PROTECTED,
    };

    enum Producer : uint64_t {
        kSoftwareWrite       = GRALLOC_USAGE_SW_WRITE_OFTEN,
        kRenderScriptWrite   = GRALLOC_USAGE_RENDERSCRIPT,
        kTextureWrite        = GRALLOC_USAGE_HW_RENDER,
        kCompositionTarget   = GRALLOC_USAGE_HW_COMPOSER | GRALLOC_USAGE_HW_RENDER,
        kHardwareDecoder     = GRALLOC_USAGE_HW_VIDEO_ENCODER,
        kProtectedWrite      = GRALLOC_USAGE_PROTECTED,
    };

    uint64_t mConsumer; // e.g. input
    uint64_t mProducer; // e.g. output
};

/**
 * \ingroup linear allocator
 * 1D allocation interface.
 */
class C2LinearAllocation : public _C2LinearCapacityAspect {
public:
    /**
     * Maps a portion of an allocation starting from |offset| with |size| into local process memory.
     * Stores the starting address into |addr|, or NULL if the operation was unsuccessful.
     * |fenceFd| is a file descriptor referring to an acquire sync fence object. If it is already
     * safe to access the buffer contents, then -1.
     *
     * \param offset          starting position of the portion to be mapped (this does not have to
     *                      be page aligned)
     * \param size            size of the portion to be mapped (this does not have to be page
     *                      aligned)
     * \param usage           the desired usage. \todo this must be kSoftwareRead and/or
     *                      kSoftwareWrite.
     * \param fenceFd         a pointer to a file descriptor if an async mapping is requested. If
     *                      not-null, and acquire fence FD will be stored here on success, or -1
     *                      on failure. If null, the mapping will be synchronous.
     * \param addr            a pointer to where the starting address of the mapped portion will be
     *                      stored. On failure, nullptr will be stored here.
     *
     * \todo Only one portion can be mapped at the same time - this is true for gralloc, but there
     *       is no need for this for 1D buffers.
     * \todo Do we need to support sync operation as we could just wait for the fence?
     *
     * \retval C2_OK        the operation was successful
     * \retval C2_NO_PERMISSION no permission to map the portion
     * \retval C2_TIMED_OUT the operation timed out
     * \retval C2_NO_MEMORY not enough memory to complete the operation
     * \retval C2_BAD_VALUE the parameters (offset/size) are invalid or outside the allocation, or
     *                      the usage flags are invalid (caller error)
     * \retval C2_CORRUPTED some unknown error prevented the operation from completing (unexpected)
     */
    virtual C2Error map(
            size_t offset, size_t size, C2MemoryUsage usage, int *fenceFd /* nullable */,
            void **addr /* nonnull */) = 0;

    /**
     * Unmaps a portion of an allocation at |addr| with |size|. These must be parameters previously
     * passed to |map|; otherwise, this operation is a no-op.
     *
     * \param addr            starting address of the mapped region
     * \param size            size of the mapped region
     * \param fenceFd         a pointer to a file descriptor if an async unmapping is requested. If
     *                      not-null, a release fence FD will be stored here on success, or -1
     *                      on failure. This fence signals when the original allocation contains
     *                      any changes that happened to the mapped region. If null, the unmapping
     *                      will be synchronous.
     *
     * \retval C2_OK        the operation was successful
     * \retval C2_TIMED_OUT the operation timed out
     * \retval C2_BAD_VALUE the parameters (addr/size) do not correspond to previously mapped
     *                      regions (caller error)
     * \retval C2_CORRUPTED some unknown error prevented the operation from completing (unexpected)
     * \retval C2_NO_PERMISSION no permission to unmap the portion (unexpected - system)
     */
    virtual C2Error unmap(void *addr, size_t size, int *fenceFd /* nullable */) = 0;

    /**
     * Returns true if this is a valid allocation.
     *
     * \todo remove?
     */
    virtual bool isValid() const = 0;

    /**
     * Returns a pointer to the allocation handle.
     */
    virtual const C2Handle *handle() const = 0;

    /**
     * Returns true if this is the same allocation as |other|.
     */
    virtual bool equals(const std::shared_ptr<C2LinearAllocation> &other) const = 0;

protected:
    // \todo should we limit allocation directly?
    C2LinearAllocation(size_t capacity) : _C2LinearCapacityAspect(c2_min(capacity, UINT32_MAX)) {}
    virtual ~C2LinearAllocation() = default;
};

/**
 * \ingroup graphic allocator
 * 2D allocation interface.
 */
class C2GraphicAllocation : public _C2PlanarCapacityAspect {
public:
    /**
     * Maps a rectangular section (as defined by |rect|) of a 2D allocation into local process
     * memory for flexible access. On success, it fills out |layout| with the plane specifications
     * and fills the |addr| array with pointers to the first byte of the top-left pixel of each
     * plane used. Otherwise, it leaves |layout| and |addr| untouched. |fenceFd| is a file
     * descriptor referring to an acquire sync fence object. If it is already safe to access the
     * buffer contents, then -1.
     *
     * \note Only one portion of the graphic allocation can be mapped at the same time. (This is
     * from gralloc1 limitation.)
     *
     * \param rect            section to be mapped (this does not have to be aligned)
     * \param usage           the desired usage. \todo this must be kSoftwareRead and/or
     *                      kSoftwareWrite.
     * \param fenceFd         a pointer to a file descriptor if an async mapping is requested. If
     *                      not-null, and acquire fence FD will be stored here on success, or -1
     *                      on failure. If null, the mapping will be synchronous.
     * \param layout          a pointer to where the mapped planes' descriptors will be
     *                      stored. On failure, nullptr will be stored here.
     *
     * \todo Do we need to support sync operation as we could just wait for the fence?
     *
     * \retval C2_OK        the operation was successful
     * \retval C2_NO_PERMISSION no permission to map the section
     * \retval C2_ALREADY_EXISTS there is already a mapped region (caller error)
     * \retval C2_TIMED_OUT the operation timed out
     * \retval C2_NO_MEMORY not enough memory to complete the operation
     * \retval C2_BAD_VALUE the parameters (rect) are invalid or outside the allocation, or the
     *                      usage flags are invalid (caller error)
     * \retval C2_CORRUPTED some unknown error prevented the operation from completing (unexpected)

     */
    virtual C2Error map(
            C2Rect rect, C2MemoryUsage usage, int *fenceFd,
            // TODO: return <addr, size> buffers with plane sizes
            C2PlaneLayout *layout /* nonnull */, uint8_t **addr /* nonnull */) = 0;

    /**
     * Unmaps the last mapped rectangular section.
     *
     * \param fenceFd         a pointer to a file descriptor if an async unmapping is requested. If
     *                      not-null, a release fence FD will be stored here on success, or -1
     *                      on failure. This fence signals when the original allocation contains
     *                      any changes that happened to the mapped section. If null, the unmapping
     *                      will be synchronous.
     *
     * \retval C2_OK        the operation was successful
     * \retval C2_TIMED_OUT the operation timed out
     * \retval C2_NOT_FOUND there is no mapped region (caller error)
     * \retval C2_CORRUPTED some unknown error prevented the operation from completing (unexpected)
     * \retval C2_NO_PERMISSION no permission to unmap the section (unexpected - system)
     */
    virtual C2Error unmap(C2Fence *fenceFd /* nullable */) = 0;

    /**
     * Returns true if this is a valid allocation.
     *
     * \todo remove?
     */
    virtual bool isValid() const = 0;

    /**
     * Returns a pointer to the allocation handle.
     */
    virtual const C2Handle *handle() const = 0;

    /**
     * Returns true if this is the same allocation as |other|.
     */
    virtual bool equals(const std::shared_ptr<const C2GraphicAllocation> &other) = 0;

protected:
    virtual ~C2GraphicAllocation();
};

/**
 *  Allocators are used by the framework to allocate memory (allocations) for buffers. They can
 *  support either 1D or 2D allocations.
 *
 *  \note In theory they could support both, but in practice, we will use only one or the other.
 *
 *  Never constructed on stack.
 *
 *  Allocators are provided by vendors.
 */
class C2Allocator {
public:
    /**
     * Allocates a 1D allocation of given |capacity| and |usage|. If successful, the allocation is
     * stored in |allocation|. Otherwise, |allocation| is set to 'nullptr'.
     *
     * \param capacity        the size of requested allocation (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param usage           the memory usage info for the requested allocation. \note that the
     *                      returned allocation may be later used/mapped with different usage.
     *                      The allocator should layout the buffer to be optimized for this usage,
     *                      but must support any usage. One exception: protected buffers can
     *                      only be used in a protected scenario.
     * \param allocation      pointer to where the allocation shall be stored on success. nullptr
     *                      will be stored here on failure
     *
     * \retval C2_OK        the allocation was successful
     * \retval C2_NO_MEMORY not enough memory to complete the allocation
     * \retval C2_TIMED_OUT the allocation timed out
     * \retval C2_NO_PERMISSION     no permission to complete the allocation
     * \retval C2_BAD_VALUE capacity or usage are not supported (invalid) (caller error)
     * \retval C2_UNSUPPORTED       this allocator does not support 1D allocations
     * \retval C2_CORRUPTED some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error allocateLinearBuffer(
            uint32_t capacity __unused, C2MemoryUsage usage __unused,
            std::shared_ptr<C2LinearAllocation> *allocation /* nonnull */) {
        *allocation = nullptr;
        return C2_UNSUPPORTED;
    }

    /**
     * (Re)creates a 1D allocation from a native |handle|. If successful, the allocation is stored
     * in |allocation|. Otherwise, |allocation| is set to 'nullptr'.
     *
     * \param handle      the handle for the existing allocation
     * \param allocation  pointer to where the allocation shall be stored on success. nullptr
     *                  will be stored here on failure
     *
     * \retval C2_OK        the allocation was recreated successfully
     * \retval C2_NO_MEMORY not enough memory to recreate the allocation
     * \retval C2_TIMED_OUT the recreation timed out (unexpected)
     * \retval C2_NO_PERMISSION     no permission to recreate the allocation
     * \retval C2_BAD_VALUE invalid handle (caller error)
     * \retval C2_UNSUPPORTED       this allocator does not support 1D allocations
     * \retval C2_CORRUPTED some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error recreateLinearBuffer(
            const C2Handle *handle __unused,
            std::shared_ptr<C2LinearAllocation> *allocation /* nonnull */) {
        *allocation = nullptr;
        return C2_UNSUPPORTED;
    }

    /**
     * Allocates a 2D allocation of given |width|, |height|, |format| and |usage|. If successful,
     * the allocation is stored in |allocation|. Otherwise, |allocation| is set to 'nullptr'.
     *
     * \param width           the width of requested allocation (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param height          the height of requested allocation (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param format          the pixel format of requested allocation. This could be a vendor
     *                      specific format.
     * \param usage           the memory usage info for the requested allocation. \note that the
     *                      returned allocation may be later used/mapped with different usage.
     *                      The allocator should layout the buffer to be optimized for this usage,
     *                      but must support any usage. One exception: protected buffers can
     *                      only be used in a protected scenario.
     * \param allocation      pointer to where the allocation shall be stored on success. nullptr
     *                      will be stored here on failure
     *
     * \retval C2_OK        the allocation was successful
     * \retval C2_NO_MEMORY not enough memory to complete the allocation
     * \retval C2_TIMED_OUT the allocation timed out
     * \retval C2_NO_PERMISSION     no permission to complete the allocation
     * \retval C2_BAD_VALUE width, height, format or usage are not supported (invalid) (caller error)
     * \retval C2_UNSUPPORTED       this allocator does not support 2D allocations
     * \retval C2_CORRUPTED some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error allocateGraphicBuffer(
            uint32_t width __unused, uint32_t height __unused, uint32_t format __unused,
            C2MemoryUsage usage __unused,
            std::shared_ptr<C2GraphicAllocation> *allocation /* nonnull */) {
        *allocation = nullptr;
        return C2_UNSUPPORTED;
    }

    /**
     * (Re)creates a 2D allocation from a native handle.  If successful, the allocation is stored
     * in |allocation|. Otherwise, |allocation| is set to 'nullptr'.
     *
     * \param handle      the handle for the existing allocation
     * \param allocation  pointer to where the allocation shall be stored on success. nullptr
     *                  will be stored here on failure
     *
     * \retval C2_OK        the allocation was recreated successfully
     * \retval C2_NO_MEMORY not enough memory to recreate the allocation
     * \retval C2_TIMED_OUT the recreation timed out (unexpected)
     * \retval C2_NO_PERMISSION     no permission to recreate the allocation
     * \retval C2_BAD_VALUE invalid handle (caller error)
     * \retval C2_UNSUPPORTED       this allocator does not support 2D allocations
     * \retval C2_CORRUPTED some unknown, unrecoverable error occured during recreation (unexpected)
     */
    virtual C2Error recreateGraphicBuffer(
            const C2Handle *handle __unused,
            std::shared_ptr<C2GraphicAllocation> *allocation /* nonnull */) {
        *allocation = nullptr;
        return C2_UNSUPPORTED;
    }

protected:
    C2Allocator() = default;

    virtual ~C2Allocator() = default;
};

/**
 *  Block allocators are used by components to allocate memory for output buffers. They can
 *  support either linear (1D), circular (1D) or graphic (2D) allocations.
 *
 *  Never constructed on stack.
 *
 *  Block allocators are provided by the framework.
 */
class C2BlockAllocator {
public:
    /**
     * Allocates a linear writeable block of given |capacity| and |usage|. If successful, the
     * block is stored in |block|. Otherwise, |block| is set to 'nullptr'.
     *
     * \param capacity        the size of requested block.
     * \param usage           the memory usage info for the requested allocation. \note that the
     *                      returned allocation may be later used/mapped with different usage.
     *                      The allocator shall lay out the buffer to be optimized for this usage,
     *                      but must support any usage. One exception: protected buffers can
     *                      only be used in a protected scenario.
     * \param block      pointer to where the allocated block shall be stored on success. nullptr
     *                      will be stored here on failure
     *
     * \retval C2_OK        the allocation was successful
     * \retval C2_NO_MEMORY not enough memory to complete the allocation
     * \retval C2_TIMED_OUT the allocation timed out
     * \retval C2_NO_PERMISSION     no permission to complete the allocation
     * \retval C2_BAD_VALUE capacity or usage are not supported (invalid) (caller error)
     * \retval C2_UNSUPPORTED       this allocator does not support linear allocations
     * \retval C2_CORRUPTED some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error allocateLinearBlock(
            uint32_t capacity __unused, C2MemoryUsage usage __unused,
            std::shared_ptr<C2LinearBlock> *block /* nonnull */) {
        *block = nullptr;
        return C2_UNSUPPORTED;
    }

    /**
     * Allocates a circular writeable block of given |capacity| and |usage|. If successful, the
     * block is stored in |block|. Otherwise, |block| is set to 'nullptr'.
     *
     * \param capacity        the size of requested circular block. (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param usage           the memory usage info for the requested allocation. \note that the
     *                      returned allocation may be later used/mapped with different usage.
     *                      The allocator shall lay out the buffer to be optimized for this usage,
     *                      but must support any usage. One exception: protected buffers can
     *                      only be used in a protected scenario.
     * \param block      pointer to where the allocated block shall be stored on success. nullptr
     *                      will be stored here on failure
     *
     * \retval C2_OK            the allocation was successful
     * \retval C2_NO_MEMORY     not enough memory to complete the allocation
     * \retval C2_TIMED_OUT     the allocation timed out
     * \retval C2_NO_PERMISSION     no permission to complete the allocation
     * \retval C2_BAD_VALUE     capacity or usage are not supported (invalid) (caller error)
     * \retval C2_UNSUPPORTED   this allocator does not support circular allocations
     * \retval C2_CORRUPTED     some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error allocateCircularBlock(
            uint32_t capacity __unused, C2MemoryUsage usage __unused,
            std::shared_ptr<C2CircularBlock> *block /* nonnull */) {
        *block = nullptr;
        return C2_UNSUPPORTED;
    }

    /**
     * Allocates a 2D graphic block of given |width|, |height|, |format| and |usage|. If successful,
     * the allocation is stored in |block|. Otherwise, |block| is set to 'nullptr'.
     *
     * \param width           the width of requested allocation (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param height          the height of requested allocation (the allocation could be slightly
     *                      larger, e.g. to account for any system-required alignment)
     * \param format          the pixel format of requested allocation. This could be a vendor
     *                      specific format.
     * \param usage           the memory usage info for the requested allocation. \note that the
     *                      returned allocation may be later used/mapped with different usage.
     *                      The allocator should layout the buffer to be optimized for this usage,
     *                      but must support any usage. One exception: protected buffers can
     *                      only be used in a protected scenario.
     * \param block      pointer to where the allocation shall be stored on success. nullptr
     *                      will be stored here on failure
     *
     * \retval C2_OK            the allocation was successful
     * \retval C2_NO_MEMORY     not enough memory to complete the allocation
     * \retval C2_TIMED_OUT     the allocation timed out
     * \retval C2_NO_PERMISSION     no permission to complete the allocation
     * \retval C2_BAD_VALUE     width, height, format or usage are not supported (invalid) (caller error)
     * \retval C2_UNSUPPORTED   this allocator does not support 2D allocations
     * \retval C2_CORRUPTED     some unknown, unrecoverable error occured during allocation (unexpected)
     */
    virtual C2Error allocateGraphicBlock(
            uint32_t width __unused, uint32_t height __unused, uint32_t format __unused,
            C2MemoryUsage usage __unused,
            std::shared_ptr<C2GraphicBlock> *block /* nonnull */) {
        *block = nullptr;
        return C2_UNSUPPORTED;
    }

protected:
    C2BlockAllocator() = default;

    virtual ~C2BlockAllocator() = default;
};

/// @}

/// \cond INTERNAL

/// \todo These are no longer used

/// \addtogroup linear
/// @{

/** \deprecated */
class C2LinearBuffer
    : public C2Buffer, public _C2LinearRangeAspect,
      public std::enable_shared_from_this<C2LinearBuffer> {
public:
    /** \todo what is this? */
    const C2Handle *handle() const;

protected:
    inline C2LinearBuffer(const C2ConstLinearBlock &block);

private:
    class Impl;
    Impl *mImpl;
};

class C2ReadCursor;

class C2WriteCursor {
public:
    uint32_t remaining() const; // remaining data to be read
    void commit(); // commits the current position. discard data before current position
    void reset() const;  // resets position to the last committed position
    // slices off at most |size| bytes, and moves cursor ahead by the number of bytes
    // sliced off.
    C2ReadCursor slice(uint32_t size) const;
    // slices off at most |size| bytes, and moves cursor ahead by the number of bytes
    // sliced off.
    C2WriteCursor reserve(uint32_t size);
    // bool read(T&);
    // bool write(T&);
    C2Fence waitForSpace(uint32_t size);
};

/// @}

/// \addtogroup graphic
/// @{

struct C2ColorSpace {
//public:
    enum Standard {
        BT601,
        BT709,
        BT2020,
        // TODO
    };

    enum Range {
        LIMITED,
        FULL,
        // TODO
    };

    enum TransferFunction {
        BT709Transfer,
        BT2020Transfer,
        HybridLogGamma2,
        HybridLogGamma4,
        // TODO
    };
};

/** \deprecated */
class C2GraphicBuffer : public C2Buffer {
public:
    // constant attributes
    inline uint32_t width() const  { return mWidth; }
    inline uint32_t height() const { return mHeight; }
    inline uint32_t format() const { return mFormat; }
    inline const C2MemoryUsage usage() const { return mUsage; }

    // modifiable attributes


    virtual const C2ColorSpace colorSpace() const = 0;
    // best effort
    virtual void setColorSpace_be(const C2ColorSpace &colorSpace) = 0;
    virtual bool setColorSpace(const C2ColorSpace &colorSpace) = 0;

    const C2Handle *handle() const;

protected:
    uint32_t mWidth;
    uint32_t mHeight;
    uint32_t mFormat;
    C2MemoryUsage mUsage;

    class Impl;
    Impl *mImpl;
};

/// @}

/// \endcond

/// @}

}  // namespace android

#endif  // C2BUFFER_H_
