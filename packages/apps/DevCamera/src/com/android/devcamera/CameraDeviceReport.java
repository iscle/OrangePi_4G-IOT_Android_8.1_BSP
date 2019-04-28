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

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class CameraDeviceReport {
    private static final String TAG = "DevCamera_INFO";

    // Note: we actually need the activity to get window information
    public static void printReport(Activity activity, boolean firstCameraOnly) {
        printDisplayInfo(activity);
        printCameraSystemInfo(activity, firstCameraOnly);
    }

    /**
     * Print out information about all cameras.
     */
    private static void printCameraSystemInfo(Activity activity, boolean firstCameraOnly) {
        CameraManager cameraMgr = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE); // "camera"
        String[] cameralist;
        try {
            cameralist = cameraMgr.getCameraIdList();
            Log.v(TAG, "Number of cameras:" + cameralist.length);
        } catch (Exception e) {
            Log.e(TAG, "Could not get camera ID list: "+e);
            return;
        }
        for (String cameraId : cameralist) {
            printCameraInfo(cameraMgr, cameraId);
            if (firstCameraOnly) {
                break;
            }
        }
    }

    /**
     * Print out information about a specific camera.
     */
    private static void printCameraInfo(CameraManager manager, String id) {
        Log.v(TAG, "============= CAMERA " + id + " INFO =============");

        CameraCharacteristics p;
        try {
            p = manager.getCameraCharacteristics(id);
        } catch (Exception e) {
            Log.e(TAG, "Could not get getCameraCharacteristics");
            return;
        }
        // dumpsys media.camera

        // Print out various CameraCharacteristics.
        Rect size = p.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (size != null) {
            Log.v(TAG, "SENSOR_INFO_ACTIVE_ARRAY_SIZE: "
                    + size.width() + "x" + size.height());
        } else {
            Log.v(TAG, "SENSOR_INFO_ACTIVE_ARRAY_SIZE: null");
        }

        Size size2 = p.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Log.v(TAG, "SENSOR_INFO_PIXEL_ARRAY_SIZE: " + size2.getWidth() + "x" + size2.getHeight());

        SizeF size3 = p.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Log.v(TAG, "SENSOR_INFO_PHYSICAL_SIZE: " + size3.getWidth() + "x" + size3.getHeight());


        int sensorOrientation = p.get(CameraCharacteristics.SENSOR_ORIENTATION);
        Log.v(TAG, "SENSOR_ORIENTATION: " + sensorOrientation);

        Log.v(TAG, "SENSOR_INFO_TIMESTAMP_SOURCE: " +
                getTimestampSourceName(p.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)));

        Log.v(TAG, "LENS_INFO_FOCUS_DISTANCE_CALIBRATION: " +
                getFocusDistanceCalibrationName(p.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)));

        int[] faceModes = p.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        Log.v(TAG, "STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES: ");
        for (int i = 0; i < faceModes.length; i++) {
            switch (faceModes[i]) {
                case CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF:
                    Log.v(TAG, "  STATISTICS_FACE_DETECT_MODE_OFF");
                    break;
                case CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE:
                    Log.v(TAG, "  STATISTICS_FACE_DETECT_MODE_SIMPLE");
                    break;
                case CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_FULL:
                    Log.v(TAG, "  STATISTICS_FACE_DETECT_MODE_FULL");
                    break;
                default:
                    Log.v(TAG, "  STATISTICS_FACE_DETECT_MODE_? (unknown)");
            }
        }

        Log.v(TAG, "STATISTICS_INFO_MAX_FACE_COUNT: " + p.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT));

        Log.v(TAG, "REQUEST_PIPELINE_MAX_DEPTH: "
                + p.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH));

        Log.v(TAG, "REQUEST_MAX_NUM_OUTPUT_RAW: "
                + p.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW));
        Log.v(TAG, "REQUEST_MAX_NUM_OUTPUT_PROC: "
                + p.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC));
        Log.v(TAG, "REQUEST_MAX_NUM_OUTPUT_PROC_STALLING: "
                + p.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING));

        Log.v(TAG, "EDGE_AVAILABLE_EDGE_MODES: "
                + intsToString(p.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)));

        Log.v(TAG, "NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES: "
                + intsToString(p.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)));

        Log.v(TAG, "REQUEST_MAX_NUM_OUTPUT_PROC_STALLING: "
                + p.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING));


        // REQUEST_AVAILABLE_CAPABILITIES
        boolean mHasReprocessing = false;
        {
            Log.v(TAG, "CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES:");
            for (int item : p.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
                Log.v(TAG, "  " + item + " = " + getCapabilityName(item));
                if (item == 4 || item == 7) {
                    mHasReprocessing = true;
                }
            }
        }

        StreamConfigurationMap map = p.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        {
            int[] formats = map.getOutputFormats();
            Log.v(TAG, "number of output formats: " + formats.length);
            for (int i = 0; i < formats.length; i++) {
                Log.v(TAG, "output sizes for format " + formats[i] +
                        " = ImageFormat." + getFormatName(formats[i]) + " = " +
                        ImageFormat.getBitsPerPixel(formats[i]) + " bits per pixel.");
                Size[] sizes = map.getOutputSizes(formats[i]);
                if (sizes != null) {
                    Log.v(TAG, "    Size      Stall duration  Min frame duration");
                    for (int j = 0; j < sizes.length; j++) {
                        Log.v(TAG, String.format("  %10s  %7d ms %7d ms \n",
                                sizes[j].toString(),
                                map.getOutputStallDuration(formats[i], sizes[j]) / 1000000,
                                map.getOutputMinFrameDuration(formats[i], sizes[j]) / 1000000
                        ));
                    }
                }
            }
        }

        if (mHasReprocessing) {
            int[] formats = map.getInputFormats();
            Log.v(TAG, "number of input formats: " + formats.length);
            for (int i = 0; i < formats.length; i++) {
                Size[] sizes = map.getInputSizes(formats[i]);
                Log.v(TAG, "input sizes for format " + formats[i] + " = ImageFormat."
                        + getFormatName(formats[i]) + " are: " + sizesToString(sizes));
            }
        }

        {
            Size[] sizes = map.getOutputSizes(SurfaceHolder.class);
            Log.v(TAG, "output sizes for SurfaceHolder.class are: " + sizesToString(sizes));
        }

        {
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            Log.v(TAG, "output sizes for SurfaceTexture.class are: " + sizesToString(sizes));
        }

        // JPEG thumbnail sizes
        {
            Size[] sizes = p.get(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES);
            Log.v(TAG, "JPEG thumbnail sizes: " + sizesToString(sizes));
        }

        // REQUEST HARDWARE LEVEL
        {
            int level = p.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            Log.v(TAG, "CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL: " + getHardwareLevelName(level));
        }


        // REQUEST CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        {
            Log.v(TAG, "CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES:");
            for (Range<Integer> item : p.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
                Log.v(TAG, "  " + item);
            }
        }
        // SENSOR_INFO_EXPOSURE_TIME_RANGE
        {
            Range<Long> rr = p.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Log.v(TAG, "CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE: " + rr);
        }


        // CAPTURE REQUEST KEYS
        {
            String keys = "";
            for (CaptureRequest.Key key : p.getAvailableCaptureRequestKeys()) {
                keys += key.getName() + "   ";
            }
            Log.v(TAG, "CameraCharacteristics.getAvailableCaptureRequestKeys() = " + keys);
        }

        // CAPTURE RESULT KEYS
        {
            String keys = "";
            for (CaptureResult.Key key : p.getAvailableCaptureResultKeys()) {
                keys += key.getName() + "   ";
            }
            Log.v(TAG, "CameraCharacteristics.getAvailableCaptureResultKeys() = " + keys);
        }

    }

    public static String sizesToString(Size[] sizes) {
        String result = "";
        if (sizes != null) {
            for (int j = 0; j < sizes.length; j++) {
                result += sizes[j].toString() + " ";
            }
        }
        return result;
    }

    public static String intsToString(int[] modes) {
        String result = "";
        if (modes != null) {
            for (int j = 0; j < modes.length; j++) {
                result += modes[j] + " ";
            }
        }
        return result;
    }

    public static String getTimestampSourceName(Integer level) {
        if (level == null) return "null";
        switch (level) {
            case CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME:
                return "SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME";
            case CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN:
                return "SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN";
        }
        return "Unknown";
    }

    public static String getFocusDistanceCalibrationName(Integer level) {
        if (level == null) return "null";
        switch (level) {
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE:
                return "LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE";
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED:
                return "LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED";
            case CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED:
                return "LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED";
        }
        return "Unknown";
    }

    public static String getCapabilityName(int format) {
        switch (format) {
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                return "REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                return "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                return "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
            case CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW:
                return "REQUEST_AVAILABLE_CAPABILITIES_RAW";
            case 4:
                return "REQUEST_AVAILABLE_CAPABILITIES_OPAQUE_REPROCESSING";
            case 5:
                return "REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
            case 6:
                return "REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE";
            case 7:
                return "REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING";
            case 8:
                return "REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT";
        }
        return "Unknown";
    }

    public static String getHardwareLevelName(int level) {
        switch (level) {
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_FULL";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                return "INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY";
        }
        return "Unknown";
    }


    public static String getFormatName(int format) {
        switch (format) {
            // Android M
            //case ImageFormat.PRIVATE:
            //    return "PRIVATE";
            // Android L
            case ImageFormat.JPEG:
                return "JPEG";
            case ImageFormat.RGB_565:
                return "RGB_565";
            case ImageFormat.NV16:
                return "NV16";
            case ImageFormat.YUY2:
                return "YUY2";
            case ImageFormat.YV12:
                return "YV12";
            case ImageFormat.NV21:
                return "NV21";
            case ImageFormat.YUV_420_888:
                return "YUV_420_888";
            case ImageFormat.RAW_SENSOR:
                return "RAW_SENSOR";
            case ImageFormat.RAW10:
                return "RAW10";
        }
        return "Unknown";
    }


    /**
     * Print out various information about the device display.
     */
    private static void printDisplayInfo(Activity activity) {
        Log.v(TAG, "============= DEVICE  INFO =============");
        Log.v(TAG, "Build.DEVICE = " + Build.DEVICE);
        Log.v(TAG, "Build.FINGERPRINT = " + Build.FINGERPRINT);
        Log.v(TAG, "Build.BRAND = " + Build.BRAND);
        Log.v(TAG, "Build.MODEL = " + Build.MODEL);
        Log.v(TAG, "Build.PRODUCT = " + Build.PRODUCT);
        Log.v(TAG, "Build.MANUFACTURER = " + Build.MANUFACTURER);
        Log.v(TAG, "Build.VERSION.CODENAME = " + Build.VERSION.CODENAME);
        Log.v(TAG, "Build.VERSION.SDK_INT = " + Build.VERSION.SDK_INT);

        Log.v(TAG, "============= DEVICE DISPLAY INFO =============");
        WindowManager windowMgr = activity.getWindowManager();

        // Nexus 5 is 360dp * 567dp
        // Each dp is 3 hardware pixels
        Log.v(TAG, "screen width dp = " + activity.getResources().getConfiguration().screenWidthDp);
        Log.v(TAG, "screen height dp = " + activity.getResources().getConfiguration().screenHeightDp);

        DisplayMetrics metrics = new DisplayMetrics();
        // With chrome subtracted.
        windowMgr.getDefaultDisplay().getMetrics(metrics);
        Log.v(TAG, "screen width pixels = " + metrics.widthPixels);
        Log.v(TAG, "screen height pixels = " + metrics.heightPixels);
        // Native.
        windowMgr.getDefaultDisplay().getRealMetrics(metrics);
        Log.v(TAG, "real screen width pixels = " + metrics.widthPixels);
        Log.v(TAG, "real screen height pixels = " + metrics.heightPixels);

        Log.v(TAG, "refresh rate = " + windowMgr.getDefaultDisplay().getRefreshRate() + " Hz");
    }



}
