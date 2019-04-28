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

#define LOG_TAG "VtsHalEvsTest"

#include "FrameHandler.h"
#include "FormatConvert.h"

#include <stdio.h>
#include <string.h>

#include <android/log.h>
#include <cutils/native_handle.h>
#include <ui/GraphicBuffer.h>


FrameHandler::FrameHandler(android::sp <IEvsCamera> pCamera, CameraDesc cameraInfo,
                           android::sp <IEvsDisplay> pDisplay,
                           BufferControlFlag mode) :
    mCamera(pCamera),
    mCameraInfo(cameraInfo),
    mDisplay(pDisplay),
    mReturnMode(mode) {
    // Nothing but member initialization here...
}


void FrameHandler::shutdown()
{
    // Make sure we're not still streaming
    blockingStopStream();

    // At this point, the receiver thread is no longer running, so we can safely drop
    // our remote object references so they can be freed
    mCamera = nullptr;
    mDisplay = nullptr;
}


bool FrameHandler::startStream() {
    // Tell the camera to start streaming
    Return<EvsResult> result = mCamera->startVideoStream(this);
    if (result != EvsResult::OK) {
        return false;
    }

    // Mark ourselves as running
    mLock.lock();
    mRunning = true;
    mLock.unlock();

    return true;
}


void FrameHandler::asyncStopStream() {
    // Tell the camera to stop streaming.
    // This will result in a null frame being delivered when the stream actually stops.
    mCamera->stopVideoStream();
}


void FrameHandler::blockingStopStream() {
    // Tell the stream to stop
    asyncStopStream();

    // Wait until the stream has actually stopped
    std::unique_lock<std::mutex> lock(mLock);
    if (mRunning) {
        mSignal.wait(lock, [this]() { return !mRunning; });
    }
}


bool FrameHandler::returnHeldBuffer() {
    std::unique_lock<std::mutex> lock(mLock);

    // Return the oldest buffer we're holding
    if (mHeldBuffers.empty()) {
        // No buffers are currently held
        return false;
    }

    BufferDesc buffer = mHeldBuffers.front();
    mHeldBuffers.pop();
    mCamera->doneWithFrame(buffer);

    return true;
}


bool FrameHandler::isRunning() {
    std::unique_lock<std::mutex> lock(mLock);
    return mRunning;
}


void FrameHandler::waitForFrameCount(unsigned frameCount) {
    // Wait until we've seen at least the requested number of frames (could be more)
    std::unique_lock<std::mutex> lock(mLock);
    mSignal.wait(lock, [this, frameCount](){ return mFramesReceived >= frameCount; });
}


void FrameHandler::getFramesCounters(unsigned* received, unsigned* displayed) {
    std::unique_lock<std::mutex> lock(mLock);

    if (received) {
        *received = mFramesReceived;
    }
    if (displayed) {
        *displayed = mFramesDisplayed;
    }
}


Return<void> FrameHandler::deliverFrame(const BufferDesc& bufferArg) {
    ALOGD("Received a frame from the camera (%p)", bufferArg.memHandle.getNativeHandle());

    // Local flag we use to keep track of when the stream is stopping
    bool timeToStop = false;

    if (bufferArg.memHandle.getNativeHandle() == nullptr) {
        // Signal that the last frame has been received and the stream is stopped
        timeToStop = true;
    } else {
        // If we were given an opened display at construction time, then send the received
        // image back down the camera.
        if (mDisplay.get()) {
            // Get the output buffer we'll use to display the imagery
            BufferDesc tgtBuffer = {};
            mDisplay->getTargetBuffer([&tgtBuffer](const BufferDesc& buff) {
                                          tgtBuffer = buff;
                                      }
            );

            if (tgtBuffer.memHandle == nullptr) {
                printf("Didn't get target buffer - frame lost\n");
                ALOGE("Didn't get requested output buffer -- skipping this frame.");
            } else {
                // Copy the contents of the of buffer.memHandle into tgtBuffer
                copyBufferContents(tgtBuffer, bufferArg);

                // Send the target buffer back for display
                Return <EvsResult> result = mDisplay->returnTargetBufferForDisplay(tgtBuffer);
                if (!result.isOk()) {
                    printf("HIDL error on display buffer (%s)- frame lost\n",
                           result.description().c_str());
                    ALOGE("Error making the remote function call.  HIDL said %s",
                          result.description().c_str());
                } else if (result != EvsResult::OK) {
                    printf("Display reported error - frame lost\n");
                    ALOGE("We encountered error %d when returning a buffer to the display!",
                          (EvsResult) result);
                } else {
                    // Everything looks good!
                    // Keep track so tests or watch dogs can monitor progress
                    mLock.lock();
                    mFramesDisplayed++;
                    mLock.unlock();
                }
            }
        }


        switch (mReturnMode) {
        case eAutoReturn:
            // Send the camera buffer back now that the client has seen it
            ALOGD("Calling doneWithFrame");
            // TODO:  Why is it that we get a HIDL crash if we pass back the cloned buffer?
            mCamera->doneWithFrame(bufferArg);
            break;
        case eNoAutoReturn:
            // Hang onto the buffer handle for now -- the client will return it explicitly later
            mHeldBuffers.push(bufferArg);
        }


        ALOGD("Frame handling complete");
    }


    // Update our received frame count and notify anybody who cares that things have changed
    mLock.lock();
    if (timeToStop) {
        mRunning = false;
    } else {
        mFramesReceived++;
    }
    mLock.unlock();
    mSignal.notify_all();


    return Void();
}


bool FrameHandler::copyBufferContents(const BufferDesc& tgtBuffer,
                                      const BufferDesc& srcBuffer) {
    bool success = true;

    // Make sure we don't run off the end of either buffer
    const unsigned width     = std::min(tgtBuffer.width,
                                        srcBuffer.width);
    const unsigned height    = std::min(tgtBuffer.height,
                                        srcBuffer.height);

    sp<android::GraphicBuffer> tgt = new android::GraphicBuffer(
        tgtBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
        tgtBuffer.width, tgtBuffer.height, tgtBuffer.format, 1, tgtBuffer.usage,
        tgtBuffer.stride);
    sp<android::GraphicBuffer> src = new android::GraphicBuffer(
        srcBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
        srcBuffer.width, srcBuffer.height, srcBuffer.format, 1, srcBuffer.usage,
        srcBuffer.stride);

    // Lock our source buffer for reading (current expectation are for this to be NV21 format)
    uint8_t* srcPixels = nullptr;
    src->lock(GRALLOC_USAGE_SW_READ_OFTEN, (void**)&srcPixels);

    // Lock our target buffer for writing (should be RGBA8888 format)
    uint32_t* tgtPixels = nullptr;
    tgt->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void**)&tgtPixels);

    if (srcPixels && tgtPixels) {
        if (tgtBuffer.format != HAL_PIXEL_FORMAT_RGBA_8888) {
            // We always expect 32 bit RGB for the display output for now.  Is there a need for 565?
            ALOGE("Diplay buffer is always expected to be 32bit RGBA");
            success = false;
        } else {
            if (srcBuffer.format == HAL_PIXEL_FORMAT_YCRCB_420_SP) {   // 420SP == NV21
                copyNV21toRGB32(width, height,
                                srcPixels,
                                tgtPixels, tgtBuffer.stride);
            } else if (srcBuffer.format == HAL_PIXEL_FORMAT_YV12) { // YUV_420P == YV12
                copyYV12toRGB32(width, height,
                                srcPixels,
                                tgtPixels, tgtBuffer.stride);
            } else if (srcBuffer.format == HAL_PIXEL_FORMAT_YCBCR_422_I) { // YUYV
                copyYUYVtoRGB32(width, height,
                                srcPixels, srcBuffer.stride,
                                tgtPixels, tgtBuffer.stride);
            } else if (srcBuffer.format == tgtBuffer.format) {  // 32bit RGBA
                copyMatchedInterleavedFormats(width, height,
                                              srcPixels, srcBuffer.stride,
                                              tgtPixels, tgtBuffer.stride,
                                              tgtBuffer.pixelSize);
            }
        }
    } else {
        ALOGE("Failed to lock buffer contents for contents transfer");
        success = false;
    }

    if (srcPixels) {
        src->unlock();
    }
    if (tgtPixels) {
        tgt->unlock();
    }

    return success;
}
