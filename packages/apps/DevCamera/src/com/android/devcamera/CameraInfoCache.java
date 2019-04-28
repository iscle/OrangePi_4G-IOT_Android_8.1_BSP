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

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

/**
 * Caches (static) information about the first/main camera.
 * Convenience functions represent data from CameraCharacteristics.
 */

public class CameraInfoCache {
    private static final String TAG = "DevCamera_CAMINFO";

    public static final boolean IS_NEXUS_6 = "shamu".equalsIgnoreCase(Build.DEVICE);

    public int[] noiseModes;
    public int[] edgeModes;

    private CameraCharacteristics mCameraCharacteristics;
    private String mCameraId;
    private Size mLargestYuvSize;
    private Size mLargestJpegSize;
    private Size mRawSize;
    private Rect mActiveArea;
    private Integer mSensorOrientation;
    private Integer mRawFormat;
    private int mBestFaceMode;
    private int mHardwareLevel;
    private Size mDepthCloudSize = null;

    /**
     * Constructor.
     */
    public CameraInfoCache(CameraManager cameraMgr, boolean useFrontCamera) {
        String[] cameralist;
        try {
            cameralist = cameraMgr.getCameraIdList();
            for (String id : cameralist) {
                mCameraCharacteristics = cameraMgr.getCameraCharacteristics(id);
                Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == (useFrontCamera ? CameraMetadata.LENS_FACING_FRONT : CameraMetadata.LENS_FACING_BACK)) {
                    mCameraId = id;
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "ERROR: Could not get camera ID list / no camera information is available: " + e);
            return;
        }
        // Should have mCameraId as this point.
        if (mCameraId == null) {
            Log.e(TAG, "ERROR: Could not find a suitable rear or front camera.");
            return;
        }

        // Store YUV_420_888, JPEG, Raw info
        StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        int[] formats = map.getOutputFormats();
        long lowestStall = Long.MAX_VALUE;
        for (int i = 0; i < formats.length; i++) {
            if (formats[i] == ImageFormat.YUV_420_888) {
                mLargestYuvSize = returnLargestSize(map.getOutputSizes(formats[i]));
            }
            if (formats[i] == ImageFormat.JPEG) {
                mLargestJpegSize = returnLargestSize(map.getOutputSizes(formats[i]));
            }
            if (formats[i] == ImageFormat.RAW10 || formats[i] == ImageFormat.RAW_SENSOR) { // TODO: Add RAW12
                Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                long stall = map.getOutputStallDuration(formats[i], size);
                if (stall < lowestStall) {
                    mRawFormat = formats[i];
                    mRawSize = size;
                    lowestStall = stall;
                }
            }
            if (formats[i] == ImageFormat.DEPTH_POINT_CLOUD) {
                Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                mDepthCloudSize = size;
            }
        }

        mActiveArea = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        // Compute best face mode.
        int[] faceModes = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        for (int i=0; i<faceModes.length; i++) {
            if (faceModes[i] > mBestFaceMode) {
                mBestFaceMode = faceModes[i];
            }
        }
        edgeModes = mCameraCharacteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES);
        noiseModes = mCameraCharacteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES);

        // Misc stuff.
        mHardwareLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
    }

    boolean supportedModesContains(int[] modes, int mode) {
        for (int m : modes) {
            if (m == mode) return true;
        }
        return false;
    }

    public int sensorOrientation() {
        return mSensorOrientation;
    }

    public boolean isCamera2FullModeAvailable() {
        return isHardwareLevelAtLeast(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
    }

    public boolean isHardwareLevelAtLeast(int level) {
        // Special-case LEGACY since it has numerical value 2
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            // All devices are at least LEGACY
            return true;
        }
        if (mHardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            // Since level isn't LEGACY
            return false;
        }
        // All other levels can be compared numerically
        return mHardwareLevel >= level;
    }

    public boolean isCapabilitySupported(int capability) {
        int[] caps = mCameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        for (int c: caps) {
            if (c == capability) return true;
        }
        return false;
    }

    public float getDiopterLow() {
        return 0f; // Infinity
    }

    public float getDiopterHi() {
        Float minFocusDistance =
                mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        // LEGACY devices don't report this, but they won't report focus distance anyway, so just
        // default to zero
        return (minFocusDistance == null) ? 0.0f : minFocusDistance;
    }

    /**
     * Calculate camera device horizontal and vertical fields of view.
     *
     * @return horizontal and vertical field of view, in degrees.
     */
    public float[] getFieldOfView() {
        float[] availableFocalLengths =
                mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        float focalLength = 4.5f; // mm, default from Nexus 6P
        if (availableFocalLengths == null || availableFocalLengths.length == 0) {
            Log.e(TAG, "No focal length reported by camera device, assuming default " +
                    focalLength);
        } else {
            focalLength = availableFocalLengths[0];
        }
        SizeF physicalSize =
                mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (physicalSize == null) {
            physicalSize = new SizeF(6.32f, 4.69f); // mm, default from Nexus 6P
            Log.e(TAG, "No physical sensor dimensions reported by camera device, assuming default "
                    + physicalSize);
        }

        // Only active array is actually visible, so calculate fraction of physicalSize that it takes up
        Size pixelArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Rect activeArraySize = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        float activeWidthFraction = activeArraySize.width() / (float) pixelArraySize.getWidth();
        float activeHeightFraction = activeArraySize.height() / (float) pixelArraySize.getHeight();

        // Simple rectilinear lens field of view formula:
        //   angle of view = 2 * arctan ( active size / (2 * focal length) )
        float[] fieldOfView = new float[2];
        fieldOfView[0] = (float) Math.toDegrees(
                2 * Math.atan(physicalSize.getWidth() * activeWidthFraction / 2 / focalLength));
        fieldOfView[1] = (float) Math.toDegrees(
                2 * Math.atan(physicalSize.getHeight() * activeHeightFraction / 2 / focalLength));

        return fieldOfView;
    }
    /**
     * Private utility function.
     */
    private Size returnLargestSize(Size[] sizes) {
        Size largestSize = null;
        int area = 0;
        for (int j = 0; j < sizes.length; j++) {
            if (sizes[j].getHeight() * sizes[j].getWidth() > area) {
                area = sizes[j].getHeight() * sizes[j].getWidth();
                largestSize = sizes[j];
            }
        }
        return largestSize;
    }

    public int bestFaceDetectionMode() {
        return mBestFaceMode;
    }

    public int faceOffsetX() {
        return (mActiveArea.width() - mLargestYuvSize.getWidth()) / 2;
    }

    public int faceOffsetY() {
        return (mActiveArea.height() - mLargestYuvSize.getHeight()) / 2;
    }

    public int activeAreaWidth() {
        return mActiveArea.width();
    }

    public int activeAreaHeight() {
        return mActiveArea.height();
    }

    public Rect getActiveAreaRect() {
        return mActiveArea;
    }

    public String getCameraId() {
        return mCameraId;
    }

    public Size getPreviewSize() {
        float aspect = mLargestYuvSize.getWidth() / mLargestYuvSize.getHeight();
        aspect = aspect > 1f ? aspect : 1f / aspect;
        if (aspect > 1.6) {
            return new Size(1920, 1080); // TODO: Check available resolutions.
        }
        if (isHardwareLevelAtLeast(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
            // Bigger preview size for more advanced devices
            return new Size(1440, 1080);
        }
        return new Size(1280, 960); // TODO: Check available resolutions.
    }

    public Size getJpegStreamSize() {
        return mLargestJpegSize;
    }

    public Size getYuvStream1Size() {
        return mLargestYuvSize;
    }

    public Size getYuvStream2Size() {
        return new Size(320, 240);
    }

    public boolean rawAvailable() {
        return mRawSize != null;
    }
    public boolean isYuvReprocessingAvailable() {
        return isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
    }

    public Integer getRawFormat() {
        return mRawFormat;
    }

    public Size getRawStreamSize() {
        return mRawSize;
    }

    public Size getDepthCloudSize() {
        return mDepthCloudSize;
    }
}
