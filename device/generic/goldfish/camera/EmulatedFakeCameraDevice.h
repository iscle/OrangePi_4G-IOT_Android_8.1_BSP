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

#ifndef HW_EMULATOR_CAMERA_EMULATED_FAKE_CAMERA_DEVICE_H
#define HW_EMULATOR_CAMERA_EMULATED_FAKE_CAMERA_DEVICE_H

/*
 * Contains declaration of a class EmulatedFakeCameraDevice that encapsulates
 * a fake camera device.
 */

#include "Converters.h"
#include "EmulatedCameraDevice.h"

/* This is used for debugging format / conversion issues. If EFCD_ROTATE_FRAME is
 * set to 0, the frame content will be always the "checkerboard". Otherwise, if
 * EFCD_ROTATE_FRAME is set to a non-zero value, the frame content will "rotate"
 * from a "checkerboard" frame to a "white/red/green/blue stripes" frame, to a
 * "white/red/green/blue" frame. Frame content rotation helps finding bugs in
 * format conversions.
 */
#define EFCD_ROTATE_FRAME   0

namespace android {

class EmulatedFakeCamera;

/* Encapsulates a fake camera device.
 * Fake camera device emulates a camera device by providing frames containing
 * a black and white checker board, moving diagonally towards the 0,0 corner.
 * There is also a green, or red square that bounces inside the frame, changing
 * its color when bouncing off the 0,0 corner.
 */
class EmulatedFakeCameraDevice : public EmulatedCameraDevice {
public:
    /* Constructs EmulatedFakeCameraDevice instance. */
    explicit EmulatedFakeCameraDevice(EmulatedFakeCamera* camera_hal);

    /* Destructs EmulatedFakeCameraDevice instance. */
    ~EmulatedFakeCameraDevice();

    /***************************************************************************
     * Emulated camera device abstract interface implementation.
     * See declarations of these methods in EmulatedCameraDevice class for
     * information on each of these methods.
     **************************************************************************/

public:
    /* Connects to the camera device.
     * Since there is no real device to connect to, this method does nothing,
     * but changes the state.
     */
    status_t connectDevice();

    /* Disconnects from the camera device.
     * Since there is no real device to disconnect from, this method does
     * nothing, but changes the state.
     */
    status_t disconnectDevice();

    /* Starts the camera device. */
    status_t startDevice(int width, int height, uint32_t pix_fmt);

    /* Stops the camera device. */
    status_t stopDevice();

    /***************************************************************************
     * Worker thread management overrides.
     * See declarations of these methods in EmulatedCameraDevice class for
     * information on each of these methods.
     **************************************************************************/

protected:
    /* Implementation of the frame production routine. */
    bool produceFrame(void* buffer) override;

    /****************************************************************************
     * Fake camera device private API
     ***************************************************************************/

private:

    /* Draws a black and white checker board in |buffer| with the assumption
     * that the size of buffer matches the current frame buffer size. */
    void drawCheckerboard(void* buffer);

    /* Draws a square of the given color in the current frame buffer.
     * Param:
     *  x, y - Coordinates of the top left corner of the square in the buffer.
     *  size - Size of the square's side.
     *  color - Square's color.
     */
    void drawSquare(void* buffer, int x, int y, int size, const YUVPixel* color);

#if EFCD_ROTATE_FRAME
    void drawSolid(void* buffer, YUVPixel* color);
    void drawStripes(void* buffer);
    int rotateFrame();
#endif  // EFCD_ROTATE_FRAME

    /****************************************************************************
     * Fake camera device data members
     ***************************************************************************/

private:
    /*
     * Pixel colors in YUV format used when drawing the checker board.
     */

    YUVPixel    mBlackYUV;
    YUVPixel    mWhiteYUV;
    YUVPixel    mRedYUV;
    YUVPixel    mGreenYUV;
    YUVPixel    mBlueYUV;
    YUVPixel*   mSquareColor;

    /* Last time the frame has been redrawn. */
    nsecs_t     mLastRedrawn;

    /*
     * Precalculated values related to U/V panes.
     */

    /* U pane inside the framebuffer. */
    ptrdiff_t   mFrameUOffset;

    /* V pane inside the framebuffer. */
    ptrdiff_t   mFrameVOffset;

    /* Defines byte distance between adjacent U, and V values. */
    int         mUVStep;

    /* Defines number of Us and Vs in a row inside the U/V panes.
     * Note that if U/V panes are interleaved, this value reflects the total
     * number of both, Us and Vs in a single row in the interleaved UV pane. */
    int         mUVInRow;

    /*
     * Checkerboard drawing related stuff
     */
    nsecs_t     mLastColorChange;

    double      mCheckX;
    double      mCheckY;
    double      mSquareX;
    double      mSquareY;
    double      mSquareXSpeed;
    double      mSquareYSpeed;

#if EFCD_ROTATE_FRAME
    /* Frame rotation frequency in nanosec (currently - 3 sec) */
    static const nsecs_t    mRotateFreq = 3000000000LL;

    /* Last time the frame has rotated. */
    nsecs_t     mLastRotatedAt;

    /* Type of the frame to display in the current rotation:
     *  0 - Checkerboard.
     *  1 - White/Red/Green/Blue horisontal stripes
     *  2 - Solid color. */
    int         mCurrentFrameType;

    /* Color to use to paint the solid color frame. Colors will rotate between
     * white, red, gree, and blue each time rotation comes to the solid color
     * frame. */
    YUVPixel*   mCurrentColor;
#endif  // EFCD_ROTATE_FRAME
};

}; /* namespace android */

#endif  /* HW_EMULATOR_CAMERA_EMULATED_FAKE_CAMERA_DEVICE_H */
