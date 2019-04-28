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

#include "RenderPixelCopy.h"
#include "FormatConvert.h"

#include <log/log.h>


RenderPixelCopy::RenderPixelCopy(sp<IEvsEnumerator> enumerator,
                                   const ConfigManager::CameraInfo& cam) {
    mEnumerator = enumerator;
    mCameraInfo = cam;
}


bool RenderPixelCopy::activate() {
    // Set up the camera to feed this texture
    sp<IEvsCamera> pCamera = mEnumerator->openCamera(mCameraInfo.cameraId.c_str());
    if (pCamera.get() == nullptr) {
        ALOGE("Failed to allocate new EVS Camera interface");
        return false;
    }

    // Initialize the stream that will help us update this texture's contents
    sp<StreamHandler> pStreamHandler = new StreamHandler(pCamera);
    if (pStreamHandler.get() == nullptr) {
        ALOGE("failed to allocate FrameHandler");
        return false;
    }

    // Start the video stream
    if (!pStreamHandler->startStream()) {
        ALOGE("start stream failed");
        return false;
    }

    mStreamHandler = pStreamHandler;

    return true;
}


void RenderPixelCopy::deactivate() {
    mStreamHandler = nullptr;
}


bool RenderPixelCopy::drawFrame(const BufferDesc& tgtBuffer) {
    bool success = true;

    sp<android::GraphicBuffer> tgt = new android::GraphicBuffer(
            tgtBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
            tgtBuffer.width, tgtBuffer.height, tgtBuffer.format, 1, tgtBuffer.usage,
            tgtBuffer.stride);

    // Lock our target buffer for writing (should be RGBA8888 format)
    uint32_t* tgtPixels = nullptr;
    tgt->lock(GRALLOC_USAGE_SW_WRITE_OFTEN, (void**)&tgtPixels);

    if (tgtPixels) {
        if (tgtBuffer.format != HAL_PIXEL_FORMAT_RGBA_8888) {
            // We always expect 32 bit RGB for the display output for now.  Is there a need for 565?
            ALOGE("Diplay buffer is always expected to be 32bit RGBA");
            success = false;
        } else {
            // Make sure we have the latest frame data
            if (mStreamHandler->newFrameAvailable()) {
                const BufferDesc& srcBuffer = mStreamHandler->getNewFrame();

                // Lock our source buffer for reading (current expectation are for this to be NV21 format)
                sp<android::GraphicBuffer> src = new android::GraphicBuffer(
                        srcBuffer.memHandle, android::GraphicBuffer::CLONE_HANDLE,
                        srcBuffer.width, srcBuffer.height, srcBuffer.format, 1, srcBuffer.usage,
                        srcBuffer.stride);
                unsigned char* srcPixels = nullptr;
                src->lock(GRALLOC_USAGE_SW_READ_OFTEN, (void**)&srcPixels);
                if (!srcPixels) {
                    ALOGE("Failed to get pointer into src image data");
                }

                // Make sure we don't run off the end of either buffer
                const unsigned width     = std::min(tgtBuffer.width,
                                                    srcBuffer.width);
                const unsigned height    = std::min(tgtBuffer.height,
                                                    srcBuffer.height);

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

                mStreamHandler->doneWithFrame(srcBuffer);
            }
        }
    } else {
        ALOGE("Failed to lock buffer contents for contents transfer");
        success = false;
    }

    if (tgtPixels) {
        tgt->unlock();
    }

    return success;
}
