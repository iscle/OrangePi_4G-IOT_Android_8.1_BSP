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
package com.android.devcamera;

import android.util.Size;
import android.view.Surface;

/**
 * This is a simple camera interface not specific to API1 or API2.
 */
public interface CameraInterface {
    /**
     * Return preview size to use pass thru from camera API.
     */
    Size getPreviewSize();

    /**
     * Get camera field of view, in degrees. Entry 0 is horizontal, entry 1 is vertical FOV.
     */
    float[] getFieldOfView();

    /**
     * Get the camera sensor orientation relative to device native orientation
     * Typically 90 or 270 for phones, 0 or 180 for tablets, though many tables are also
     * portrait-native.
     */
    int getOrientation();

    /**
     * Open the camera. Call startPreview() to actually see something.
     */
    void openCamera();

    /**
     * Start preview to a surface. Also need to call openCamera().
     * @param surface
     */
    void startPreview(Surface surface);

    /**
     * Close the camera.
     */
    void closeCamera();

    /**
     * Take a picture and return data with provided callback.
     * Preview must be started.
     */
    void takePicture();

    /**
     * Set whether we are continuously taking pictures, or not.
     */
    void setBurst(boolean go);

    /**
     * Take a picture and return data with provided callback.
     * Preview must be started.
     */
    void setCallback(MyCameraCallback callback);

    /**
     * Is a raw stream available.
     */
    boolean isRawAvailable();

    /**
     * Is a reprocessing available.
     */
    boolean isReprocessingAvailable();

    /**
     * Triggers an AF scan. Leaves camera in AUTO.
     */
    void triggerAFScan();

    /**
     * Runs CAF (continuous picture).
     */
    void setCAF();

    /**
     * Camera picture callbacks.
     */
    interface MyCameraCallback {
        /**
         * What text to display on the Edge and NR mode buttons.
         */
        void setNoiseEdgeText(String s1, String s2);

        /**
         * What text to display on the Edge and NR mode buttons (reprocessing flow).
         */
        void setNoiseEdgeTextForReprocessing(String s1, String s2);

        /**
         * Full size JPEG is available.
         * @param jpegData
         * @param x
         * @param y
         */
        void jpegAvailable(byte[] jpegData, int x, int y);

        /**
         * Metadata from an image frame.
         *
         * @param info Info string we print just under viewfinder.
         *
         *             fps, mLastIso, af, ae, awb
         * @param faces Face coordinates.
         * @param normExposure Exposure value normalized from 0 to 1.
         * @param normLensPos Lens position value normalized from 0 to 1.
         * @param fps
         * @param iso
         * @param afState
         * @param aeState
         * @param awbState
         *
         */
        void frameDataAvailable(NormalizedFace[] faces, float normExposure, float normLensPos, float fps, int iso, int afState, int aeState, int awbState);

        /**
         * Misc performance data.
         */
        void performanceDataAvailable(Integer timeToFirstFrame, Integer halWaitTime, Float droppedFrameCount);

        /**
         * Called when camera2 FULL not available.
         */
        void noCamera2Full();

        /**
         * Used to set the preview SurfaceView background color from black to transparent.
         */
        void receivedFirstFrame();
    }

    void setCaptureFlow(Boolean yuv1, Boolean yuv2, Boolean raw10, Boolean nr, Boolean edge, Boolean face);

    void setReprocessingFlow(Boolean nr, Boolean edge);

}
