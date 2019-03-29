/*
 * Copyright (C) 2011 The Android Open Source Project
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

/*
 * Contains implementation of a class EmulatedFakeCameraDevice that encapsulates
 * fake camera device.
 */

#define LOG_NDEBUG 0
#define LOG_TAG "EmulatedCamera_FakeDevice"
#include <cutils/log.h>
#include "EmulatedFakeCamera.h"
#include "EmulatedFakeCameraDevice.h"

#undef min
#undef max
#include <algorithm>

namespace android {

static const double kCheckXSpeed = 0.00000000096;
static const double kCheckYSpeed = 0.00000000032;

static const double kSquareXSpeed = 0.000000000096;
static const double kSquareYSpeed = 0.000000000160;

static const nsecs_t kSquareColorChangeIntervalNs = seconds(5);

EmulatedFakeCameraDevice::EmulatedFakeCameraDevice(EmulatedFakeCamera* camera_hal)
    : EmulatedCameraDevice(camera_hal),
      mBlackYUV(kBlack32),
      mWhiteYUV(kWhite32),
      mRedYUV(kRed8),
      mGreenYUV(kGreen8),
      mBlueYUV(kBlue8),
      mSquareColor(&mRedYUV),
      mLastRedrawn(0),
      mLastColorChange(0),
      mCheckX(0),
      mCheckY(0),
      mSquareX(0),
      mSquareY(0),
      mSquareXSpeed(kSquareXSpeed),
      mSquareYSpeed(kSquareYSpeed)
#if EFCD_ROTATE_FRAME
      , mLastRotatedAt(0),
        mCurrentFrameType(0),
        mCurrentColor(&mWhiteYUV)
#endif  // EFCD_ROTATE_FRAME
{
    // Makes the image with the original exposure compensation darker.
    // So the effects of changing the exposure compensation can be seen.
    mBlackYUV.Y = mBlackYUV.Y / 2;
    mWhiteYUV.Y = mWhiteYUV.Y / 2;
    mRedYUV.Y = mRedYUV.Y / 2;
    mGreenYUV.Y = mGreenYUV.Y / 2;
    mBlueYUV.Y = mBlueYUV.Y / 2;
}

EmulatedFakeCameraDevice::~EmulatedFakeCameraDevice()
{
}

/****************************************************************************
 * Emulated camera device abstract interface implementation.
 ***************************************************************************/

status_t EmulatedFakeCameraDevice::connectDevice()
{
    ALOGV("%s", __FUNCTION__);

    Mutex::Autolock locker(&mObjectLock);
    if (!isInitialized()) {
        ALOGE("%s: Fake camera device is not initialized.", __FUNCTION__);
        return EINVAL;
    }
    if (isConnected()) {
        ALOGW("%s: Fake camera device is already connected.", __FUNCTION__);
        return NO_ERROR;
    }

    /* There is no device to connect to. */
    mState = ECDS_CONNECTED;

    return NO_ERROR;
}

status_t EmulatedFakeCameraDevice::disconnectDevice()
{
    ALOGV("%s", __FUNCTION__);

    Mutex::Autolock locker(&mObjectLock);
    if (!isConnected()) {
        ALOGW("%s: Fake camera device is already disconnected.", __FUNCTION__);
        return NO_ERROR;
    }
    if (isStarted()) {
        ALOGE("%s: Cannot disconnect from the started device.", __FUNCTION__);
        return EINVAL;
    }

    /* There is no device to disconnect from. */
    mState = ECDS_INITIALIZED;

    return NO_ERROR;
}

status_t EmulatedFakeCameraDevice::startDevice(int width,
                                               int height,
                                               uint32_t pix_fmt)
{
    ALOGV("%s", __FUNCTION__);

    Mutex::Autolock locker(&mObjectLock);
    if (!isConnected()) {
        ALOGE("%s: Fake camera device is not connected.", __FUNCTION__);
        return EINVAL;
    }
    if (isStarted()) {
        ALOGE("%s: Fake camera device is already started.", __FUNCTION__);
        return EINVAL;
    }

    /* Initialize the base class. */
    const status_t res =
        EmulatedCameraDevice::commonStartDevice(width, height, pix_fmt);
    if (res == NO_ERROR) {
        /* Calculate U/V panes inside the framebuffer. */
        switch (mPixelFormat) {
            case V4L2_PIX_FMT_YVU420:
                mFrameVOffset = mYStride * mFrameHeight;
                mFrameUOffset = mFrameVOffset + mUVStride * (mFrameHeight / 2);
                mUVStep = 1;
                break;

            case V4L2_PIX_FMT_YUV420:
                mFrameUOffset = mYStride * mFrameHeight;
                mFrameVOffset = mFrameUOffset + mUVStride * (mFrameHeight / 2);
                mUVStep = 1;
                break;

            case V4L2_PIX_FMT_NV21:
                /* Interleaved UV pane, V first. */
                mFrameVOffset = mYStride * mFrameHeight;
                mFrameUOffset = mFrameVOffset + 1;
                mUVStep = 2;
                break;

            case V4L2_PIX_FMT_NV12:
                /* Interleaved UV pane, U first. */
                mFrameUOffset = mYStride * mFrameHeight;
                mFrameVOffset = mFrameUOffset + 1;
                mUVStep = 2;
                break;

            default:
                ALOGE("%s: Unknown pixel format %.4s", __FUNCTION__,
                     reinterpret_cast<const char*>(&mPixelFormat));
                return EINVAL;
        }
        mLastRedrawn = systemTime(SYSTEM_TIME_MONOTONIC);
        mLastColorChange = mLastRedrawn;
        /* Number of items in a single row inside U/V panes. */
        mUVInRow = (width / 2) * mUVStep;
        mState = ECDS_STARTED;
    } else {
        ALOGE("%s: commonStartDevice failed", __FUNCTION__);
    }

    return res;
}

status_t EmulatedFakeCameraDevice::stopDevice()
{
    ALOGV("%s", __FUNCTION__);

    Mutex::Autolock locker(&mObjectLock);
    if (!isStarted()) {
        ALOGW("%s: Fake camera device is not started.", __FUNCTION__);
        return NO_ERROR;
    }

    EmulatedCameraDevice::commonStopDevice();
    mState = ECDS_CONNECTED;

    return NO_ERROR;
}

/****************************************************************************
 * Worker thread management overrides.
 ***************************************************************************/

bool EmulatedFakeCameraDevice::produceFrame(void* buffer)
{
#if EFCD_ROTATE_FRAME
    const int frame_type = rotateFrame();
    switch (frame_type) {
        case 0:
            drawCheckerboard(buffer);
            break;
        case 1:
            drawStripes(buffer);
            break;
        case 2:
            drawSolid(buffer, mCurrentColor);
            break;
    }
#else
    drawCheckerboard(buffer);
#endif  // EFCD_ROTATE_FRAME
    return true;
}

/****************************************************************************
 * Fake camera device private API
 ***************************************************************************/

void EmulatedFakeCameraDevice::drawCheckerboard(void* buffer)
{
    nsecs_t now = systemTime(SYSTEM_TIME_MONOTONIC);
    nsecs_t elapsed = now - mLastRedrawn;
    uint8_t* currentFrame = reinterpret_cast<uint8_t*>(buffer);
    uint8_t* frameU = currentFrame + mFrameUOffset;
    uint8_t* frameV = currentFrame + mFrameVOffset;

    const int size = std::min(mFrameWidth, mFrameHeight) / 10;
    bool black = true;

    if (size == 0) {
        // When this happens, it happens at a very high rate,
        //     so don't log any messages and just return.
        return;
    }

    mCheckX += kCheckXSpeed * elapsed;
    mCheckY += kCheckYSpeed * elapsed;

    // Allow the X and Y values to transition across two checkerboard boxes
    // before resetting it back. This allows for the gray to black transition.
    // Note that this is in screen size independent coordinates so that frames
    // will look similar regardless of resolution
    if (mCheckX > 2.0) {
        mCheckX -= 2.0;
    }
    if (mCheckY > 2.0) {
        mCheckY -= 2.0;
    }

    // Are we in the gray or black zone?
    if (mCheckX >= 1.0)
        black = false;
    if (mCheckY >= 1.0)
        black = !black;

    int county = static_cast<int>(mCheckY * size) % size;
    int checkxremainder = static_cast<int>(mCheckX * size) % size;

    YUVPixel adjustedWhite = YUVPixel(mWhiteYUV);
    changeWhiteBalance(adjustedWhite.Y, adjustedWhite.U, adjustedWhite.V);
    adjustedWhite.Y = changeExposure(adjustedWhite.Y);
    YUVPixel adjustedBlack = YUVPixel(mBlackYUV);
    adjustedBlack.Y = changeExposure(adjustedBlack.Y);

    for(int y = 0; y < mFrameHeight; y++) {
        int countx = checkxremainder;
        bool current = black;
        uint8_t* Y = currentFrame + mYStride * y;
        uint8_t* U = frameU + mUVStride * (y / 2);
        uint8_t* V = frameV + mUVStride * (y / 2);
        for(int x = 0; x < mFrameWidth; x += 2) {
            if (current) {
                adjustedBlack.get(Y, U, V);
            } else {
                adjustedWhite.get(Y, U, V);
            }
            Y[1] = *Y;
            Y += 2; U += mUVStep; V += mUVStep;
            countx += 2;
            if(countx >= size) {
                countx = 0;
                current = !current;
            }
        }
        if(county++ >= size) {
            county = 0;
            black = !black;
        }
    }

    /* Run the square. */
    const int squareSize = std::min(mFrameWidth, mFrameHeight) / 4;
    mSquareX += mSquareXSpeed * elapsed;
    mSquareY += mSquareYSpeed * elapsed;
    int squareX = mSquareX * mFrameWidth;
    int squareY = mSquareY * mFrameHeight;
    if (squareX + squareSize > mFrameWidth) {
        mSquareXSpeed = -mSquareXSpeed;
        double relativeWidth = static_cast<double>(squareSize) / mFrameWidth;
        mSquareX -= 2.0 * (mSquareX + relativeWidth - 1.0);
        squareX = mSquareX * mFrameWidth;
    } else if (squareX < 0) {
        mSquareXSpeed = -mSquareXSpeed;
        mSquareX = -mSquareX;
        squareX = mSquareX * mFrameWidth;
    }
    if (squareY + squareSize > mFrameHeight) {
        mSquareYSpeed = -mSquareYSpeed;
        double relativeHeight = static_cast<double>(squareSize) / mFrameHeight;
        mSquareY -= 2.0 * (mSquareY + relativeHeight - 1.0);
        squareY = mSquareY * mFrameHeight;
    } else if (squareY < 0) {
        mSquareYSpeed = -mSquareYSpeed;
        mSquareY = -mSquareY;
        squareY = mSquareY * mFrameHeight;
    }

    if (now - mLastColorChange > kSquareColorChangeIntervalNs) {
        mLastColorChange = now;
        mSquareColor = mSquareColor == &mRedYUV ? &mGreenYUV : &mRedYUV;
    }

    drawSquare(buffer, squareX, squareY, squareSize, mSquareColor);
    mLastRedrawn = now;
}

void EmulatedFakeCameraDevice::drawSquare(void* buffer,
                                          int x,
                                          int y,
                                          int size,
                                          const YUVPixel* color)
{
    uint8_t* currentFrame = reinterpret_cast<uint8_t*>(buffer);
    uint8_t* frameU = currentFrame + mFrameUOffset;
    uint8_t* frameV = currentFrame + mFrameVOffset;

    const int square_xstop = std::min(mFrameWidth, x + size);
    const int square_ystop = std::min(mFrameHeight, y + size);
    uint8_t* Y_pos = currentFrame + y * mYStride + x;

    YUVPixel adjustedColor = *color;
    changeWhiteBalance(adjustedColor.Y, adjustedColor.U, adjustedColor.V);

    // Draw the square.
    for (; y < square_ystop; y++) {
        const int iUV = (y / 2) * mUVStride + (x / 2) * mUVStep;
        uint8_t* sqU = frameU + iUV;
        uint8_t* sqV = frameV + iUV;
        uint8_t* sqY = Y_pos;
        for (int i = x; i < square_xstop; i += 2) {
            adjustedColor.get(sqY, sqU, sqV);
            *sqY = changeExposure(*sqY);
            sqY[1] = *sqY;
            sqY += 2; sqU += mUVStep; sqV += mUVStep;
        }
        Y_pos += mYStride;
    }
}

#if EFCD_ROTATE_FRAME

void EmulatedFakeCameraDevice::drawSolid(void* buffer, YUVPixel* color)
{
    YUVPixel adjustedColor = *color;
    changeWhiteBalance(adjustedColor.Y, adjustedColor.U, adjustedColor.V);

    /* All Ys are the same, will fill any alignment padding but that's OK */
    memset(mCurrentFrame, changeExposure(adjustedColor.Y),
           mFrameHeight * mYStride);

    /* Fill U, and V panes. */
    for (int y = 0; y < mFrameHeight / 2; ++y) {
        uint8_t* U = mFrameU + y * mUVStride;
        uint8_t* V = mFrameV + y * mUVStride;

        for (int x = 0; x < mFrameWidth / 2; ++x, U += mUVStep, V += mUVStep) {
            *U = color->U;
            *V = color->V;
        }
    }
}

void EmulatedFakeCameraDevice::drawStripes(void* buffer)
{
    /* Divide frame into 4 stripes. */
    const int change_color_at = mFrameHeight / 4;
    const int each_in_row = mUVInRow / mUVStep;
    uint8_t* pY = mCurrentFrame;
    for (int y = 0; y < mFrameHeight; y++, pY += mYStride) {
        /* Select the color. */
        YUVPixel* color;
        const int color_index = y / change_color_at;
        if (color_index == 0) {
            /* White stripe on top. */
            color = &mWhiteYUV;
        } else if (color_index == 1) {
            /* Then the red stripe. */
            color = &mRedYUV;
        } else if (color_index == 2) {
            /* Then the green stripe. */
            color = &mGreenYUV;
        } else {
            /* And the blue stripe at the bottom. */
            color = &mBlueYUV;
        }
        changeWhiteBalance(color->Y, color->U, color->V);

        /* All Ys at the row are the same. */
        memset(pY, changeExposure(color->Y), mFrameWidth);

        /* Offset of the current row inside U/V panes. */
        const int uv_off = (y / 2) * mUVStride;
        /* Fill U, and V panes. */
        uint8_t* U = mFrameU + uv_off;
        uint8_t* V = mFrameV + uv_off;
        for (int k = 0; k < each_in_row; k++, U += mUVStep, V += mUVStep) {
            *U = color->U;
            *V = color->V;
        }
    }
}

int EmulatedFakeCameraDevice::rotateFrame()
{
    if ((systemTime(SYSTEM_TIME_MONOTONIC) - mLastRotatedAt) >= mRotateFreq) {
        mLastRotatedAt = systemTime(SYSTEM_TIME_MONOTONIC);
        mCurrentFrameType++;
        if (mCurrentFrameType > 2) {
            mCurrentFrameType = 0;
        }
        if (mCurrentFrameType == 2) {
            ALOGD("********** Rotated to the SOLID COLOR frame **********");
            /* Solid color: lets rotate color too. */
            if (mCurrentColor == &mWhiteYUV) {
                ALOGD("----- Painting a solid RED frame -----");
                mCurrentColor = &mRedYUV;
            } else if (mCurrentColor == &mRedYUV) {
                ALOGD("----- Painting a solid GREEN frame -----");
                mCurrentColor = &mGreenYUV;
            } else if (mCurrentColor == &mGreenYUV) {
                ALOGD("----- Painting a solid BLUE frame -----");
                mCurrentColor = &mBlueYUV;
            } else {
                /* Back to white. */
                ALOGD("----- Painting a solid WHITE frame -----");
                mCurrentColor = &mWhiteYUV;
            }
        } else if (mCurrentFrameType == 0) {
            ALOGD("********** Rotated to the CHECKERBOARD frame **********");
        } else if (mCurrentFrameType == 1) {
            ALOGD("********** Rotated to the STRIPED frame **********");
        }
    }

    return mCurrentFrameType;
}

#endif  // EFCD_ROTATE_FRAME

}; /* namespace android */
