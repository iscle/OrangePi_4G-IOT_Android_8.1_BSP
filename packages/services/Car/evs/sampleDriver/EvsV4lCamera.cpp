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

#include "EvsV4lCamera.h"
#include "EvsEnumerator.h"
#include "bufferCopy.h"

#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


// Arbitrary limit on number of graphics buffers allowed to be allocated
// Safeguards against unreasonable resource consumption and provides a testable limit
static const unsigned MAX_BUFFERS_IN_FLIGHT = 100;


EvsV4lCamera::EvsV4lCamera(const char *deviceName) :
        mFramesAllowed(0),
        mFramesInUse(0) {
    ALOGD("EvsV4lCamera instantiated");

    mDescription.cameraId = deviceName;

    // Initialize the video device
    if (!mVideo.open(deviceName)) {
        ALOGE("Failed to open v4l device %s\n", deviceName);
    }

    // NOTE:  Our current spec says only support NV21 -- can we stick to that with software
    // conversion?  Will this work with the hardware texture units?
    // TODO:  Settle on the one official format that works on all platforms
    // TODO:  Get NV21 working?  It is scrambled somewhere along the way right now.
//    mFormat = HAL_PIXEL_FORMAT_YCRCB_420_SP;    // 420SP == NV21
//    mFormat = HAL_PIXEL_FORMAT_RGBA_8888;
    mFormat = HAL_PIXEL_FORMAT_YCBCR_422_I;

    // How we expect to use the gralloc buffers we'll exchange with our client
    mUsage  = GRALLOC_USAGE_HW_TEXTURE     |
              GRALLOC_USAGE_SW_READ_RARELY |
              GRALLOC_USAGE_SW_WRITE_OFTEN;
}


EvsV4lCamera::~EvsV4lCamera() {
    ALOGD("EvsV4lCamera being destroyed");
    shutdown();
}


//
// This gets called if another caller "steals" ownership of the camera
//
void EvsV4lCamera::shutdown()
{
    ALOGD("EvsV4lCamera shutdown");

    // Make sure our output stream is cleaned up
    // (It really should be already)
    stopVideoStream();

    // Note:  Since stopVideoStream is blocking, no other threads can now be running

    // Close our video capture device
    mVideo.close();

    // Drop all the graphics buffers we've been using
    if (mBuffers.size() > 0) {
        GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
        for (auto&& rec : mBuffers) {
            if (rec.inUse) {
                ALOGW("Error - releasing buffer despite remote ownership");
            }
            alloc.free(rec.handle);
            rec.handle = nullptr;
        }
        mBuffers.clear();
    }
}


// Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
Return<void> EvsV4lCamera::getCameraInfo(getCameraInfo_cb _hidl_cb) {
    ALOGD("getCameraInfo");

    // Send back our self description
    _hidl_cb(mDescription);
    return Void();
}


Return<EvsResult> EvsV4lCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    ALOGD("setMaxFramesInFlight");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo.isOpen()) {
        ALOGW("ignoring setMaxFramesInFlight call when camera has been lost.");
        return EvsResult::OWNERSHIP_LOST;
    }

    // We cannot function without at least one video buffer to send data
    if (bufferCount < 1) {
        ALOGE("Ignoring setMaxFramesInFlight with less than one buffer requested");
        return EvsResult::INVALID_ARG;
    }

    // Update our internal state
    if (setAvailableFrames_Locked(bufferCount)) {
        return EvsResult::OK;
    } else {
        return EvsResult::BUFFER_NOT_AVAILABLE;
    }
}


Return<EvsResult> EvsV4lCamera::startVideoStream(const ::android::sp<IEvsCameraStream>& stream)  {
    ALOGD("startVideoStream");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo.isOpen()) {
        ALOGW("ignoring startVideoStream call when camera has been lost.");
        return EvsResult::OWNERSHIP_LOST;
    }
    if (mStream.get() != nullptr) {
        ALOGE("ignoring startVideoStream call when a stream is already running.");
        return EvsResult::STREAM_ALREADY_RUNNING;
    }

    // If the client never indicated otherwise, configure ourselves for a single streaming buffer
    if (mFramesAllowed < 1) {
        if (!setAvailableFrames_Locked(1)) {
            ALOGE("Failed to start stream because we couldn't get a graphics buffer");
            return EvsResult::BUFFER_NOT_AVAILABLE;
        }
    }

    // Choose which image transfer function we need
    // Map from V4L2 to Android graphic buffer format
    const uint32_t videoSrcFormat = mVideo.getV4LFormat();
    ALOGI("Configuring to accept %4.4s camera data and convert to %4.4s",
          (char*)&videoSrcFormat, (char*)&mFormat);

    // TODO:  Simplify this by supporting only ONE fixed output format
    switch (mFormat) {
    case HAL_PIXEL_FORMAT_YCRCB_420_SP:
        switch (videoSrcFormat) {
        case V4L2_PIX_FMT_NV21:     mFillBufferFromVideo = fillNV21FromNV21;    break;
    //  case V4L2_PIX_FMT_YV12:     mFillBufferFromVideo = fillNV21FromYV12;    break;
        case V4L2_PIX_FMT_YUYV:     mFillBufferFromVideo = fillNV21FromYUYV;    break;
    //  case V4L2_PIX_FORMAT_NV16:  mFillBufferFromVideo = fillNV21FromNV16;    break;
        default:
            // TODO:  Are there other V4L2 formats we must support?
            ALOGE("Unhandled camera output format %c%c%c%c (0x%8X)\n",
                  ((char*)&videoSrcFormat)[0],
                  ((char*)&videoSrcFormat)[1],
                  ((char*)&videoSrcFormat)[2],
                  ((char*)&videoSrcFormat)[3],
                  videoSrcFormat);
        }
        break;
    case HAL_PIXEL_FORMAT_RGBA_8888:
        switch (videoSrcFormat) {
        case V4L2_PIX_FMT_YUYV:     mFillBufferFromVideo = fillRGBAFromYUYV;    break;
        default:
            // TODO:  Are there other V4L2 formats we must support?
            ALOGE("Unhandled camera format %4.4s", (char*)&videoSrcFormat);
        }
        break;
    case HAL_PIXEL_FORMAT_YCBCR_422_I:
        switch (videoSrcFormat) {
        case V4L2_PIX_FMT_YUYV:     mFillBufferFromVideo = fillYUYVFromYUYV;    break;
        case V4L2_PIX_FMT_UYVY:     mFillBufferFromVideo = fillYUYVFromUYVY;    break;
        default:
            // TODO:  Are there other V4L2 formats we must support?
            ALOGE("Unhandled camera format %4.4s", (char*)&videoSrcFormat);
        }
        break;
    default:
        // TODO:  Why have we told ourselves to output something we don't understand!?
        ALOGE("Unhandled output format %4.4s", (char*)&mFormat);
    }


    // Record the user's callback for use when we have a frame ready
    mStream = stream;

    // Set up the video stream with a callback to our member function forwardFrame()
    if (!mVideo.startStream([this](VideoCapture*, imageBuffer* tgt, void* data) {
                                this->forwardFrame(tgt, data);
                            })
    ) {
        mStream = nullptr;  // No need to hold onto this if we failed to start
        ALOGE("underlying camera start stream failed");
        return EvsResult::UNDERLYING_SERVICE_ERROR;
    }

    return EvsResult::OK;
}


Return<void> EvsV4lCamera::doneWithFrame(const BufferDesc& buffer)  {
    ALOGD("doneWithFrame");
    std::lock_guard <std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo.isOpen()) {
        ALOGW("ignoring doneWithFrame call when camera has been lost.");
    } else {
        if (buffer.memHandle == nullptr) {
            ALOGE("ignoring doneWithFrame called with null handle");
        } else if (buffer.bufferId >= mBuffers.size()) {
            ALOGE("ignoring doneWithFrame called with invalid bufferId %d (max is %zu)",
                  buffer.bufferId, mBuffers.size()-1);
        } else if (!mBuffers[buffer.bufferId].inUse) {
            ALOGE("ignoring doneWithFrame called on frame %d which is already free",
                  buffer.bufferId);
        } else {
            // Mark the frame as available
            mBuffers[buffer.bufferId].inUse = false;
            mFramesInUse--;

            // If this frame's index is high in the array, try to move it down
            // to improve locality after mFramesAllowed has been reduced.
            if (buffer.bufferId >= mFramesAllowed) {
                // Find an empty slot lower in the array (which should always exist in this case)
                for (auto&& rec : mBuffers) {
                    if (rec.handle == nullptr) {
                        rec.handle = mBuffers[buffer.bufferId].handle;
                        mBuffers[buffer.bufferId].handle = nullptr;
                        break;
                    }
                }
            }
        }
    }

    return Void();
}


Return<void> EvsV4lCamera::stopVideoStream()  {
    ALOGD("stopVideoStream");

    // Tell the capture device to stop (and block until it does)
    mVideo.stopStream();

    if (mStream != nullptr) {
        std::unique_lock <std::mutex> lock(mAccessLock);

        // Send one last NULL frame to signal the actual end of stream
        BufferDesc nullBuff = {};
        auto result = mStream->deliverFrame(nullBuff);
        if (!result.isOk()) {
            ALOGE("Error delivering end of stream marker");
        }

        // Drop our reference to the client's stream receiver
        mStream = nullptr;
    }

    return Void();
}


Return<int32_t> EvsV4lCamera::getExtendedInfo(uint32_t /*opaqueIdentifier*/)  {
    ALOGD("getExtendedInfo");
    // Return zero by default as required by the spec
    return 0;
}


Return<EvsResult> EvsV4lCamera::setExtendedInfo(uint32_t /*opaqueIdentifier*/,
                                                int32_t /*opaqueValue*/)  {
    ALOGD("setExtendedInfo");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (!mVideo.isOpen()) {
        ALOGW("ignoring setExtendedInfo call when camera has been lost.");
        return EvsResult::OWNERSHIP_LOST;
    }

    // We don't store any device specific information in this implementation
    return EvsResult::INVALID_ARG;
}


bool EvsV4lCamera::setAvailableFrames_Locked(unsigned bufferCount) {
    if (bufferCount < 1) {
        ALOGE("Ignoring request to set buffer count to zero");
        return false;
    }
    if (bufferCount > MAX_BUFFERS_IN_FLIGHT) {
        ALOGE("Rejecting buffer request in excess of internal limit");
        return false;
    }

    // Is an increase required?
    if (mFramesAllowed < bufferCount) {
        // An increase is required
        unsigned needed = bufferCount - mFramesAllowed;
        ALOGI("Allocating %d buffers for camera frames", needed);

        unsigned added = increaseAvailableFrames_Locked(needed);
        if (added != needed) {
            // If we didn't add all the frames we needed, then roll back to the previous state
            ALOGE("Rolling back to previous frame queue size");
            decreaseAvailableFrames_Locked(added);
            return false;
        }
    } else if (mFramesAllowed > bufferCount) {
        // A decrease is required
        unsigned framesToRelease = mFramesAllowed - bufferCount;
        ALOGI("Returning %d camera frame buffers", framesToRelease);

        unsigned released = decreaseAvailableFrames_Locked(framesToRelease);
        if (released != framesToRelease) {
            // This shouldn't happen with a properly behaving client because the client
            // should only make this call after returning sufficient outstanding buffers
            // to allow a clean resize.
            ALOGE("Buffer queue shrink failed -- too many buffers currently in use?");
        }
    }

    return true;
}


unsigned EvsV4lCamera::increaseAvailableFrames_Locked(unsigned numToAdd) {
    // Acquire the graphics buffer allocator
    GraphicBufferAllocator &alloc(GraphicBufferAllocator::get());

    unsigned added = 0;


    while (added < numToAdd) {
        unsigned pixelsPerLine;
        buffer_handle_t memHandle = nullptr;
        status_t result = alloc.allocate(mVideo.getWidth(), mVideo.getHeight(),
                                         mFormat, 1,
                                         mUsage,
                                         &memHandle, &pixelsPerLine, 0, "EvsV4lCamera");
        if (result != NO_ERROR) {
            ALOGE("Error %d allocating %d x %d graphics buffer",
                  result,
                  mVideo.getWidth(),
                  mVideo.getHeight());
            break;
        }
        if (!memHandle) {
            ALOGE("We didn't get a buffer handle back from the allocator");
            break;
        }
        if (mStride) {
            if (mStride != pixelsPerLine) {
                ALOGE("We did not expect to get buffers with different strides!");
            }
        } else {
            // Gralloc defines stride in terms of pixels per line
            mStride = pixelsPerLine;
        }

        // Find a place to store the new buffer
        bool stored = false;
        for (auto&& rec : mBuffers) {
            if (rec.handle == nullptr) {
                // Use this existing entry
                rec.handle = memHandle;
                rec.inUse = false;
                stored = true;
                break;
            }
        }
        if (!stored) {
            // Add a BufferRecord wrapping this handle to our set of available buffers
            mBuffers.emplace_back(memHandle);
        }

        mFramesAllowed++;
        added++;
    }

    return added;
}


unsigned EvsV4lCamera::decreaseAvailableFrames_Locked(unsigned numToRemove) {
    // Acquire the graphics buffer allocator
    GraphicBufferAllocator &alloc(GraphicBufferAllocator::get());

    unsigned removed = 0;

    for (auto&& rec : mBuffers) {
        // Is this record not in use, but holding a buffer that we can free?
        if ((rec.inUse == false) && (rec.handle != nullptr)) {
            // Release buffer and update the record so we can recognize it as "empty"
            alloc.free(rec.handle);
            rec.handle = nullptr;

            mFramesAllowed--;
            removed++;

            if (removed == numToRemove) {
                break;
            }
        }
    }

    return removed;
}


// This is the async callback from the video camera that tells us a frame is ready
void EvsV4lCamera::forwardFrame(imageBuffer* /*pV4lBuff*/, void* pData) {
    bool readyForFrame = false;
    size_t idx = 0;

    // Lock scope for updating shared state
    {
        std::lock_guard<std::mutex> lock(mAccessLock);

        // Are we allowed to issue another buffer?
        if (mFramesInUse >= mFramesAllowed) {
            // Can't do anything right now -- skip this frame
            ALOGW("Skipped a frame because too many are in flight\n");
        } else {
            // Identify an available buffer to fill
            for (idx = 0; idx < mBuffers.size(); idx++) {
                if (!mBuffers[idx].inUse) {
                    if (mBuffers[idx].handle != nullptr) {
                        // Found an available record, so stop looking
                        break;
                    }
                }
            }
            if (idx >= mBuffers.size()) {
                // This shouldn't happen since we already checked mFramesInUse vs mFramesAllowed
                ALOGE("Failed to find an available buffer slot\n");
            } else {
                // We're going to make the frame busy
                mBuffers[idx].inUse = true;
                mFramesInUse++;
                readyForFrame = true;
            }
        }
    }

    if (!readyForFrame) {
        // We need to return the vide buffer so it can capture a new frame
        mVideo.markFrameConsumed();
    } else {
        // Assemble the buffer description we'll transmit below
        BufferDesc buff = {};
        buff.width      = mVideo.getWidth();
        buff.height     = mVideo.getHeight();
        buff.stride     = mStride;
        buff.format     = mFormat;
        buff.usage      = mUsage;
        buff.bufferId   = idx;
        buff.memHandle  = mBuffers[idx].handle;

        // Lock our output buffer for writing
        void *targetPixels = nullptr;
        GraphicBufferMapper &mapper = GraphicBufferMapper::get();
        mapper.lock(buff.memHandle,
                    GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_SW_READ_NEVER,
                    android::Rect(buff.width, buff.height),
                    (void **) &targetPixels);

        // If we failed to lock the pixel buffer, we're about to crash, but log it first
        if (!targetPixels) {
            ALOGE("Camera failed to gain access to image buffer for writing");
        }

        // Transfer the video image into the output buffer, making any needed
        // format conversion along the way
        mFillBufferFromVideo(buff, (uint8_t*)targetPixels, pData, mVideo.getStride());

        // Unlock the output buffer
        mapper.unlock(buff.memHandle);


        // Give the video frame back to the underlying device for reuse
        // Note that we do this before making the client callback to give the underlying
        // camera more time to capture the next frame.
        mVideo.markFrameConsumed();

        // Issue the (asynchronous) callback to the client -- can't be holding the lock
        auto result = mStream->deliverFrame(buff);
        if (result.isOk()) {
            ALOGD("Delivered %p as id %d", buff.memHandle.getNativeHandle(), buff.bufferId);
        } else {
            // This can happen if the client dies and is likely unrecoverable.
            // To avoid consuming resources generating failing calls, we stop sending
            // frames.  Note, however, that the stream remains in the "STREAMING" state
            // until cleaned up on the main thread.
            ALOGE("Frame delivery call failed in the transport layer.");

            // Since we didn't actually deliver it, mark the frame as available
            std::lock_guard<std::mutex> lock(mAccessLock);
            mBuffers[idx].inUse = false;
            mFramesInUse--;
        }
    }
}

} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android
