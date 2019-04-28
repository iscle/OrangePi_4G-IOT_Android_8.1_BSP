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

#define LOG_TAG "android.hardware.automotive.evs@1.0-service"

#include "EvsCamera.h"
#include "EvsEnumerator.h"

#include <ui/GraphicBufferAllocator.h>
#include <ui/GraphicBufferMapper.h>


namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {
namespace implementation {


// Special camera names for which we'll initialize alternate test data
const char EvsCamera::kCameraName_Backup[]    = "backup";


// Arbitrary limit on number of graphics buffers allowed to be allocated
// Safeguards against unreasonable resource consumption and provides a testable limit
const unsigned MAX_BUFFERS_IN_FLIGHT = 100;


EvsCamera::EvsCamera(const char *id) :
        mFramesAllowed(0),
        mFramesInUse(0),
        mStreamState(STOPPED) {

    ALOGD("EvsCamera instantiated");

    mDescription.cameraId = id;

    // Set up dummy data for testing
    if (mDescription.cameraId == kCameraName_Backup) {
        mWidth  = 640;          // full NTSC/VGA
        mHeight = 480;          // full NTSC/VGA
        mDescription.vendorFlags = 0xFFFFFFFF;   // Arbitrary value
    } else {
        mWidth  = 320;          // 1/2 NTSC/VGA
        mHeight = 240;          // 1/2 NTSC/VGA
    }

    mFormat = HAL_PIXEL_FORMAT_RGBA_8888;
    mUsage  = GRALLOC_USAGE_HW_TEXTURE | GRALLOC_USAGE_HW_CAMERA_WRITE |
              GRALLOC_USAGE_SW_READ_RARELY | GRALLOC_USAGE_SW_WRITE_RARELY;
}


EvsCamera::~EvsCamera() {
    ALOGD("EvsCamera being destroyed");
    forceShutdown();
}


//
// This gets called if another caller "steals" ownership of the camera
//
void EvsCamera::forceShutdown()
{
    ALOGD("EvsCamera forceShutdown");

    // Make sure our output stream is cleaned up
    // (It really should be already)
    stopVideoStream();

    // Claim the lock while we work on internal state
    std::lock_guard <std::mutex> lock(mAccessLock);

    // Drop all the graphics buffers we've been using
    if (mBuffers.size() > 0) {
        GraphicBufferAllocator& alloc(GraphicBufferAllocator::get());
        for (auto&& rec : mBuffers) {
            if (rec.inUse) {
                ALOGE("Error - releasing buffer despite remote ownership");
            }
            alloc.free(rec.handle);
            rec.handle = nullptr;
        }
        mBuffers.clear();
    }

    // Put this object into an unrecoverable error state since somebody else
    // is going to own the underlying camera now
    mStreamState = DEAD;
}


// Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
Return<void> EvsCamera::getCameraInfo(getCameraInfo_cb _hidl_cb) {
    ALOGD("getCameraInfo");

    // Send back our self description
    _hidl_cb(mDescription);
    return Void();
}


Return<EvsResult> EvsCamera::setMaxFramesInFlight(uint32_t bufferCount) {
    ALOGD("setMaxFramesInFlight");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (mStreamState == DEAD) {
        ALOGE("ignoring setMaxFramesInFlight call when camera has been lost.");
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


Return<EvsResult> EvsCamera::startVideoStream(const ::android::sp<IEvsCameraStream>& stream)  {
    ALOGD("startVideoStream");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (mStreamState == DEAD) {
        ALOGE("ignoring startVideoStream call when camera has been lost.");
        return EvsResult::OWNERSHIP_LOST;
    }
    if (mStreamState != STOPPED) {
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

    // Record the user's callback for use when we have a frame ready
    mStream = stream;

    // Start the frame generation thread
    mStreamState = RUNNING;
    mCaptureThread = std::thread([this](){ generateFrames(); });

    return EvsResult::OK;
}


Return<void> EvsCamera::doneWithFrame(const BufferDesc& buffer)  {
    ALOGD("doneWithFrame");
    {  // lock context
        std::lock_guard <std::mutex> lock(mAccessLock);

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


Return<void> EvsCamera::stopVideoStream()  {
    ALOGD("stopVideoStream");
    std::unique_lock <std::mutex> lock(mAccessLock);

    if (mStreamState == RUNNING) {
        // Tell the GenerateFrames loop we want it to stop
        mStreamState = STOPPING;

        // Block outside the mutex until the "stop" flag has been acknowledged
        // We won't send any more frames, but the client might still get some already in flight
        ALOGD("Waiting for stream thread to end...");
        lock.unlock();
        mCaptureThread.join();
        lock.lock();

        mStreamState = STOPPED;
        mStream = nullptr;
        ALOGD("Stream marked STOPPED.");
    }

    return Void();
}


Return<int32_t> EvsCamera::getExtendedInfo(uint32_t opaqueIdentifier)  {
    ALOGD("getExtendedInfo");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // For any single digit value, return the index itself as a test value
    if (opaqueIdentifier <= 9) {
        return opaqueIdentifier;
    }

    // Return zero by default as required by the spec
    return 0;
}


Return<EvsResult> EvsCamera::setExtendedInfo(uint32_t /*opaqueIdentifier*/, int32_t /*opaqueValue*/)  {
    ALOGD("setExtendedInfo");
    std::lock_guard<std::mutex> lock(mAccessLock);

    // If we've been displaced by another owner of the camera, then we can't do anything else
    if (mStreamState == DEAD) {
        ALOGE("ignoring setExtendedInfo call when camera has been lost.");
        return EvsResult::OWNERSHIP_LOST;
    }

    // We don't store any device specific information in this implementation
    return EvsResult::INVALID_ARG;
}


bool EvsCamera::setAvailableFrames_Locked(unsigned bufferCount) {
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


unsigned EvsCamera::increaseAvailableFrames_Locked(unsigned numToAdd) {
    // Acquire the graphics buffer allocator
    GraphicBufferAllocator &alloc(GraphicBufferAllocator::get());

    unsigned added = 0;

    while (added < numToAdd) {
        buffer_handle_t memHandle = nullptr;
        status_t result = alloc.allocate(mWidth, mHeight, mFormat, 1, mUsage,
                                         &memHandle, &mStride, 0, "EvsCamera");
        if (result != NO_ERROR) {
            ALOGE("Error %d allocating %d x %d graphics buffer", result, mWidth, mHeight);
            break;
        }
        if (!memHandle) {
            ALOGE("We didn't get a buffer handle back from the allocator");
            break;
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


unsigned EvsCamera::decreaseAvailableFrames_Locked(unsigned numToRemove) {
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


// This is the asynchronous frame generation thread that runs in parallel with the
// main serving thread.  There is one for each active camera instance.
void EvsCamera::generateFrames() {
    ALOGD("Frame generation loop started");

    unsigned idx;

    while (true) {
        bool timeForFrame = false;
        nsecs_t startTime = systemTime(SYSTEM_TIME_MONOTONIC);

        // Lock scope for updating shared state
        {
            std::lock_guard<std::mutex> lock(mAccessLock);

            if (mStreamState != RUNNING) {
                // Break out of our main thread loop
                break;
            }

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
                    timeForFrame = true;
                }
            }
        }

        if (timeForFrame) {
            // Assemble the buffer description we'll transmit below
            BufferDesc buff = {};
            buff.width      = mWidth;
            buff.height     = mHeight;
            buff.stride     = mStride;
            buff.format     = mFormat;
            buff.usage      = mUsage;
            buff.bufferId   = idx;
            buff.memHandle  = mBuffers[idx].handle;

            // Write test data into the image buffer
            fillTestFrame(buff);

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

                break;
            }
        }

        // We arbitrarily choose to generate frames at 12 fps to ensure we pass the 10fps test requirement
        static const int kTargetFrameRate = 12;
        static const nsecs_t kTargetFrameTimeUs = 1000*1000 / kTargetFrameRate;
        const nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
        const nsecs_t workTimeUs = (now - startTime) / 1000;
        const nsecs_t sleepDurationUs = kTargetFrameTimeUs - workTimeUs;
        if (sleepDurationUs > 0) {
            usleep(sleepDurationUs);
        }
    }

    // If we've been asked to stop, send one last NULL frame to signal the actual end of stream
    BufferDesc nullBuff = {};
    auto result = mStream->deliverFrame(nullBuff);
    if (!result.isOk()) {
        ALOGE("Error delivering end of stream marker");
    }

    return;
}


void EvsCamera::fillTestFrame(const BufferDesc& buff) {
    // Lock our output buffer for writing
    uint32_t *pixels = nullptr;
    GraphicBufferMapper &mapper = GraphicBufferMapper::get();
    mapper.lock(buff.memHandle,
                GRALLOC_USAGE_SW_WRITE_OFTEN | GRALLOC_USAGE_SW_READ_NEVER,
                android::Rect(buff.width, buff.height),
                (void **) &pixels);

    // If we failed to lock the pixel buffer, we're about to crash, but log it first
    if (!pixels) {
        ALOGE("Camera failed to gain access to image buffer for writing");
    }

    // Fill in the test pixels
    for (unsigned row = 0; row < buff.height; row++) {
        for (unsigned col = 0; col < buff.width; col++) {
            // Index into the row to check the pixel at this column.
            // We expect 0xFF in the LSB channel, a vertical gradient in the
            // second channel, a horitzontal gradient in the third channel, and
            // 0xFF in the MSB.
            // The exception is the very first 32 bits which is used for the
            // time varying frame signature to avoid getting fooled by a static image.
            uint32_t expectedPixel = 0xFF0000FF           | // MSB and LSB
                                     ((row & 0xFF) <<  8) | // vertical gradient
                                     ((col & 0xFF) << 16);  // horizontal gradient
            if ((row | col) == 0) {
                static uint32_t sFrameTicker = 0;
                expectedPixel = (sFrameTicker) & 0xFF;
                sFrameTicker++;
            }
            pixels[col] = expectedPixel;
        }
        // Point to the next row
        // NOTE:  stride retrieved from gralloc is in units of pixels
        pixels = pixels + buff.stride;
    }

    // Release our output buffer
    mapper.unlock(buff.memHandle);
}


} // namespace implementation
} // namespace V1_0
} // namespace evs
} // namespace automotive
} // namespace hardware
} // namespace android
