/*
**
** Copyright (C) 2008 The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef ANDROID_VIDEO_FRAME_H
#define ANDROID_VIDEO_FRAME_H

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <utils/Log.h>

namespace android {

// Represents a color converted (RGB-based) video frame
// with bitmap pixels stored in FrameBuffer
class VideoFrame
{
public:
    // Construct a VideoFrame object with the specified parameters,
    // will allocate frame buffer if |allocate| is set to true, will
    // allocate buffer to hold ICC data if |iccData| and |iccSize|
    // indicate its presence.
    VideoFrame(uint32_t width, uint32_t height,
            uint32_t displayWidth, uint32_t displayHeight,
            uint32_t angle, uint32_t bpp, bool allocate,
            const void *iccData, size_t iccSize):
        mWidth(width), mHeight(height),
        mDisplayWidth(displayWidth), mDisplayHeight(displayHeight),
        mRotationAngle(angle), mBytesPerPixel(bpp), mRowBytes(bpp * width),
        mSize(0), mIccSize(0), mReserved(0), mData(0), mIccData(0) {
        if (allocate) {
            mSize = mRowBytes * mHeight;
            mData = new uint8_t[mSize];
            if (mData == NULL) {
                mSize = 0;
            }
        }

        if (iccData != NULL && iccSize > 0) {
            mIccSize = iccSize;
            mIccData = new uint8_t[iccSize];
            if (mIccData != NULL) {
                memcpy(mIccData, iccData, iccSize);
            } else {
                mIccSize = 0;
            }
        }
    }

    // Deep copy of both the information fields and the frame data
    VideoFrame(const VideoFrame& copy) {
        copyInfoOnly(copy);

        mSize = copy.mSize;
        mData = NULL;  // initialize it first
        if (mSize > 0 && copy.mData != NULL) {
            mData = new uint8_t[mSize];
            if (mData != NULL) {
                memcpy(mData, copy.mData, mSize);
            } else {
                mSize = 0;
            }
        }

        mIccSize = copy.mIccSize;
        mIccData = NULL;  // initialize it first
        if (mIccSize > 0 && copy.mIccData != NULL) {
            mIccData = new uint8_t[mIccSize];
            if (mIccData != NULL) {
                memcpy(mIccData, copy.mIccData, mIccSize);
            } else {
                mIccSize = 0;
            }
        }
    }

    ~VideoFrame() {
        if (mData != 0) {
            delete[] mData;
        }
        if (mIccData != 0) {
            delete[] mIccData;
        }
    }

    // Copy |copy| to a flattened VideoFrame in IMemory, 'this' must point to
    // a chunk of memory back by IMemory of size at least getFlattenedSize()
    // of |copy|.
    void copyFlattened(const VideoFrame& copy) {
        copyInfoOnly(copy);

        mSize = copy.mSize;
        mData = NULL;  // initialize it first
        if (copy.mSize > 0 && copy.mData != NULL) {
            memcpy(getFlattenedData(), copy.mData, copy.mSize);
        }

        mIccSize = copy.mIccSize;
        mIccData = NULL;  // initialize it first
        if (copy.mIccSize > 0 && copy.mIccData != NULL) {
            memcpy(getFlattenedIccData(), copy.mIccData, copy.mIccSize);
        }
    }

    // Calculate the flattened size to put it in IMemory
    size_t getFlattenedSize() const {
        return sizeof(VideoFrame) + mSize + mIccSize;
    }

    // Get the pointer to the frame data in a flattened VideoFrame in IMemory
    uint8_t* getFlattenedData() const {
        return (uint8_t*)this + sizeof(VideoFrame);
    }

    // Get the pointer to the ICC data in a flattened VideoFrame in IMemory
    uint8_t* getFlattenedIccData() const {
        return (uint8_t*)this + sizeof(VideoFrame) + mSize;
    }

    // Intentional public access modifier:
    uint32_t mWidth;           // Decoded image width before rotation
    uint32_t mHeight;          // Decoded image height before rotation
    uint32_t mDisplayWidth;    // Display width before rotation
    uint32_t mDisplayHeight;   // Display height before rotation
    int32_t  mRotationAngle;   // Rotation angle, clockwise, should be multiple of 90
    uint32_t mBytesPerPixel;   // Number of bytes per pixel
    uint32_t mRowBytes;        // Number of bytes per row before rotation
    uint32_t mSize;            // Number of bytes in mData
    uint32_t mIccSize;         // Number of bytes in mIccData
    uint32_t mReserved;        // (padding to make mData 64-bit aligned)

    // mData should be 64-bit aligned to prevent additional padding
    uint8_t* mData;            // Actual binary data
    // pad structure so it's the same size on 64-bit and 32-bit
    char     mPadding[8 - sizeof(mData)];

    // mIccData should be 64-bit aligned to prevent additional padding
    uint8_t* mIccData;            // Actual binary data
    // pad structure so it's the same size on 64-bit and 32-bit
    char     mIccPadding[8 - sizeof(mIccData)];

private:
    //
    // Utility methods used only within VideoFrame struct
    //

    // Copy the information fields only
    void copyInfoOnly(const VideoFrame& copy) {
        mWidth = copy.mWidth;
        mHeight = copy.mHeight;
        mDisplayWidth = copy.mDisplayWidth;
        mDisplayHeight = copy.mDisplayHeight;
        mRotationAngle = copy.mRotationAngle;
        mBytesPerPixel = copy.mBytesPerPixel;
        mRowBytes = copy.mRowBytes;
    }
};

}; // namespace android

#endif // ANDROID_VIDEO_FRAME_H
