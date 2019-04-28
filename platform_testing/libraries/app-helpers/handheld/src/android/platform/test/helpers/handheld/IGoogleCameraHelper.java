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

package android.platform.test.helpers;

import android.app.Instrumentation;
import android.support.test.uiautomator.Direction;

public interface IGoogleCameraHelper extends IStandardAppHelper {
    public static final int HDR_MODE_AUTO = -1;
    public static final int HDR_MODE_OFF  =  0;
    public static final int HDR_MODE_ON   =  1;

    public static final int VIDEO_SD_480     = -2;
    public static final int VIDEO_HD_720     = -1;
    public static final int VIDEO_HD_1080    =  0;
    public static final int VIDEO_4K_MODE_ON =  1;

    public static final int VIDEO_30FPS = 0;
    public static final int VIDEO_60FPS = 1;

    public static final int HFR_MODE_OFF     = 0;
    public static final int HFR_MODE_120_FPS = 1;
    public static final int HFR_MODE_240_FPS = 2;

    public static final int FLASH_AUTO = -1;
    public static final int FLASH_OFF = 0;
    public static final int FLASH_ON = 1;
    public static final int NUM_FLASH_MODES = 3;

    /**
     * Setup expectations: GoogleCamera is open and idle in video mode.
     *
     * This method will change to camera mode and block until the transition is complete.
     */
    public void goToCameraMode();

    /**
     * Setup expectations: GoogleCamera is open and idle in camera mode.
     *
     * This method will change to video mode and block until the transition is complete.
     */
    public void goToVideoMode();

    /**
     * Setup expectations: GoogleCamera is open and idle in either camera/video mode.
     *
     * This method will change to back camera and block until the transition is complete.
     */
    public void goToBackCamera();

    /**
     * Setup expectations: GoogleCamera is open and idle in either camera/video mode.
     *
     * This method will change to front camera and block until the transition is complete.
     */
    public void goToFrontCamera();

    /**
     * Setup expectation: in Camera mode with the capture button present.
     *
     * This method will capture a photo and block until the transaction is complete.
     */
    public void capturePhoto();

    /**
     * Setup expectation: in Video mode with the capture button present.
     *
     * This method will capture a video of length timeInMs and block until the transaction is
     * complete.
     * @param time duration of video in milliseconds
     */
    public void captureVideo(long time);

    /**
     * Setup expectation:
     *   1. in Video mode with the capture button present.
     *   2. videoTime > snapshotStartTime
     *
     * This method will capture a video of length videoTime, and take a picture at snapshotStartTime.
     * It will block until the the video is captured and the device is again idle in video mode.
     * @param time duration of video in milliseconds
     */
    public void snapshotVideo(long videoTime, long snapshotStartTime);

    /**
     * Setup expectation: GoogleCamera is open and idle in camera mode.
     *
     * This method will set HDR to one of the following:
     * - on   (mode == HDR_MODE_ON)
     * - auto (mode == HDR_MODE_AUTO)
     * - off  (mode == HDR_MODE_OFF)
     * @param mode the integer value of the mode denoted above.
     */
    public void setHdrMode(int mode);

    /**
     * Setup expectation: GoogleCamera is open and idle in video mode.
     *
     * This method will set 4K mode to one of the following:
     * - on  (mode == VIDEO_4K_MODE_ON)
     * - off (mode != VIDEO_4K_MODE_ON)
     * @param mode the integer value of the mode denoted above.
     */
    public void set4KMode(int mode);

    /**
     * Setup expectation: GoogleCamera is open and idle in video mode.
     *
     * This method will set HFR mode to one of the following:
     * - off     (mode == HFR_MODE_OFF)
     * - 120 fps (mode == HFR_MODE_120_FPS)
     * - 240 fps (mode == HFR_MODE_240_FPS)
     * @param mode the integer value of the mode denoted above.
     */
    public void setHFRMode(int mode);

    /**
     *
     * Setup expectations: GoogleCamera is open and idle in either camera/video mode.
     *
     * This method will set EIS to on(true), or off(false).
     * @param mode the boolean value of the mode denoted above.
     */
    public void setEIS(boolean mode);

    /**
     * Setup expectation: GoogleCamera is open and idle in either camera/video mode.
     *
     * This method will set front video capture resolution to one of the following:
     * - SD 480p  (mode == VIDEO_SD_480)
     * - HD 720p  (mode == VIDEO_HD_720)
     * - HD 1080p (mode == VIDEO_HD_1080)
     * - UHD 4K   (mode == VIDEO_4K_MODE_ON)
     * @param mode the integer value of the mode denoted above.
     */
    public void selectFrontVideoResolution(int mode);

    /**
     * Setup expectation: GoogleCamera is open and idle in either camera/video mode.
     *
     * This method will set back video capture resolution to one of the following:
     * - SD 480p  (mode == VIDEO_SD_480)
     * - HD 720p  (mode == VIDEO_HD_720)
     * - HD 1080p (mode == VIDEO_HD_1080)
     * - UHD 4K   (mode == VIDEO_4K_MODE_ON)
     * @param mode the integer value of the mode denoted above.
     */
    public void selectBackVideoResolution(int mode);

    /**
     *
     * Setup expectations: GoogleCamera is open, idle, in video mode,
     * using back camera, and not in 4k mode
     *
     * This method will set video capture framerate to one of the following:
     * - 30 fps (mode == VIDEO_30FPS)
     * - 60 fps (mode == VIDEO_60FPS)
     * @param mode the integer value of the mode denoted above.
     */
    public void setFrameRate(int mode);

    /**
     * Setup expectation: GoogleCamera is open and idle in camera or video mode.
     *
     * This method will set flash to one of the following:
     * - on   (mode == FLASH_ON)
     * - auto (mode == FLASH_AUTO)
     * - off  (mode == FLASH_OFF)
     * @param mode the integer value of the mode denoted above.
     */
    public void setFlashMode(int mode);

    /**
     * Setup expectation: in Camera mode with the capture button present.
     *
     * This method will block until the capture button is enabled for pressing.
     */
    public void waitForCameraShutterEnabled();

    /**
     * Setup expectation: in Video mode with the capture button present.
     *
     * This method will block until the capture button is enabled for pressing.
     */
    public void waitForVideoShutterEnabled();

    /**
     * Temporary function.
     */
    public String openWithShutterTimeString();

    /**
     * Setup expectations: in Camera mode or in Video mode
     */
    public void goToAlbum();

    /**
     * Setup expectations:
     *   1. in album view
     *   2. scroll direction is either LEFT or RIGHT
     *
     * @param direction scroll direction, either LEFT or RIGHT
     */
    public void scrollAlbum(Direction direction);
}
