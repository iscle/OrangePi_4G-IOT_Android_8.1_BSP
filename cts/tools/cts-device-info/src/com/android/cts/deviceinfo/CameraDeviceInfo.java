/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.cts.deviceinfo;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.os.Build;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.util.SizeF;
import android.util.Range;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;

/**
 * Camera information collector.
 */
public final class CameraDeviceInfo extends DeviceInfo {
    private static final String TAG = "CameraDeviceInfo";

    private final static class CameraCharacteristicsStorer {
        private CameraManager mCameraManager;
        private DeviceInfoStore mStore;

        public CameraCharacteristicsStorer(CameraManager cameraManager, DeviceInfoStore store) {
            if (cameraManager == null || store == null) {
                throw new IllegalArgumentException("can not create an CameraMetadataGetter object"
                        + " with null CameraManager or null DeviceInfoStore");
            }

            mCameraManager = cameraManager;
            mStore = store;
        }

        public void storeCameraInfo(String cameraId) throws Exception {
            try {
                CameraCharacteristics chars = mCameraManager.getCameraCharacteristics(cameraId);
                mStore.startGroup(); // per camera chars
                mStore.addResult("cameraId", cameraId);
                storeCameraChars(chars);
                mStore.endGroup(); // per camera chars
            } catch (CameraAccessException e) {
                Log.e(TAG,
                        "Unable to get camera camera static info, skip this camera, error: "
                                + e.getMessage());
            }
            return;
        }

        private void storeRational(
                Rational rat, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("numerator", rat.getNumerator());
            mStore.addResult("denominator", rat.getDenominator());
            mStore.endGroup();
        }

        private void storeSize(
                Size size, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("width", size.getWidth());
            mStore.addResult("height", size.getHeight());
            mStore.endGroup();
        }

        private void storeSizeF(
                SizeF size, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("width", size.getWidth());
            mStore.addResult("height", size.getHeight());
            mStore.endGroup();
        }

        private void storeRect(
                Rect rect, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("left", rect.left);
            mStore.addResult("right", rect.right);
            mStore.addResult("top", rect.top);
            mStore.addResult("bottom", rect.bottom);
            mStore.endGroup();
        }

        private void storeStreamConfigurationMap(
                StreamConfigurationMap map, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }

            int fmts[] = map.getOutputFormats();
            if (fmts != null) {
                mStore.startArray("availableStreamConfigurations");
                for (int fi = 0; fi < Array.getLength(fmts); fi++) {
                    Size sizes[] = map.getOutputSizes(fmts[fi]);
                    if (sizes != null) {
                        for (int si = 0; si < Array.getLength(sizes); si++) {
                            mStore.startGroup();
                            mStore.addResult("format", fmts[fi]);
                            mStore.addResult("width", sizes[si].getWidth());
                            mStore.addResult("height", sizes[si].getHeight());
                            mStore.addResult("input", false);
                            mStore.addResult("minFrameDuration",
                                            map.getOutputMinFrameDuration(fmts[fi], sizes[si]));
                            mStore.endGroup();
                        }
                    }
                }
                mStore.endArray();
            }
            mStore.endGroup();
        }

        private void storeRangeInt(
                Range<Integer> range, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("lower", range.getLower());
            mStore.addResult("upper", range.getUpper());
            mStore.endGroup();
        }

        private void storeRangeLong(
                Range<Long> range, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            mStore.addResult("lower", range.getLower());
            mStore.addResult("upper", range.getUpper());
            mStore.endGroup();
        }

        private void storeColorSpaceTransform(
                ColorSpaceTransform xform, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }

            mStore.startArray("elements");
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    storeRational((Rational) xform.getElement(col, row), null);
                }
            }
            mStore.endArray();
            mStore.endGroup();
        }

        private void storeBlackLevelPattern(
                BlackLevelPattern pat, String protoName) throws Exception {
            if (protoName == null) {
                mStore.startGroup();
            } else {
                mStore.startGroup(protoName);
            }
            int patVals[] = new int[4];
            pat.copyTo(patVals, 0);
            mStore.addArrayResult("black_level_pattern", patVals);
            mStore.endGroup();
        }

        private static String getKeyName(Object keyObj) {
            return ((CameraCharacteristics.Key) keyObj).getName();
        }

        private static Object getKeyValue(CameraCharacteristics chars, Object keyObj) {
            return chars.get((CameraCharacteristics.Key) keyObj);
        }

        private void storeEntry(Type keyType, Object keyObj,
                CameraCharacteristics chars) throws Exception {
            String keyName = getKeyName(keyObj);
            String protoName = keyName.replace('.', '_');
            Object keyValue = getKeyValue(chars, keyObj);
            if (keyValue == null) {
                return;
            }

            if (keyType == int.class || keyType == Integer.class) {
                mStore.addResult(protoName, (int) keyValue);
                return;
            } else if (keyType == float.class || keyType == Float.class) {
                mStore.addResult(protoName, (float) keyValue);
                return;
            } else if (keyType == long.class || keyType == Long.class) {
                mStore.addResult(protoName, (long) keyValue);
                return;
            } else if (keyType == double.class || keyType == Double.class) {
                mStore.addResult(protoName, (double) keyValue);
                return;
            } else if (keyType == boolean.class || keyType == Boolean.class) {
                mStore.addResult(protoName, (boolean) keyValue);
                return;
            } else if (keyType == byte.class || keyType == Byte.class) {
                // Infostore does not support byte, convert to int32 and save
                int intValue = (int) ((byte) keyValue);
                mStore.addResult(protoName, intValue);
                return;
            } else if (keyType == Rational.class) {
                storeRational((Rational) keyValue, protoName);
                return;
            } else if (keyType == Size.class) {
                storeSize((Size) keyValue, protoName);
                return;
            } else if (keyType == SizeF.class) {
                storeSizeF((SizeF) keyValue, protoName);
                return;
            } else if (keyType == Rect.class) {
                storeRect((Rect) keyValue, protoName);
                return;
            } else if (keyType == StreamConfigurationMap.class) {
                storeStreamConfigurationMap(
                        (StreamConfigurationMap) keyValue, protoName);
                return;
            } else if (keyType instanceof ParameterizedType &&
                    ((ParameterizedType) keyType).getRawType() == Range.class &&
                    ((ParameterizedType) keyType).getActualTypeArguments()[0] == Integer.class) {
                storeRangeInt((Range<Integer>) keyValue, protoName);
                return;
            } else if (keyType instanceof ParameterizedType &&
                    ((ParameterizedType) keyType).getRawType() == Range.class &&
                    ((ParameterizedType) keyType).getActualTypeArguments()[0] == Long.class) {
                storeRangeLong((Range<Long>) keyValue, protoName);
                return;
            } else if (keyType == ColorSpaceTransform.class) {
                storeColorSpaceTransform((ColorSpaceTransform) keyValue, protoName);
                return;
            } else if (keyType == BlackLevelPattern.class) {
                storeBlackLevelPattern((BlackLevelPattern) keyValue, protoName);
                return;
            } else {
                Log.w(TAG, "Storing unsupported key type: " + keyType +
                        " for keyName: " + keyName);
                return;
            }
        }

        private void storeArrayEntry(Type keyType, Object keyObj,
                CameraCharacteristics chars) throws Exception {
            String keyName = getKeyName(keyObj);
            String protoName = keyName.replace('.', '_');
            Object keyValue = getKeyValue(chars, keyObj);
            if (keyValue == null) {
                return;
            }

            int arrayLen = Array.getLength(keyValue);
            if (arrayLen == 0) {
                return;
            }
            Type elmtType = ((GenericArrayType) keyType).getGenericComponentType();

            if (elmtType == int.class) {
                mStore.addArrayResult(protoName, (int[]) keyValue);
                return;
            } else if (elmtType == float.class) {
                mStore.addArrayResult(protoName, (float[]) keyValue);
                return;
            } else if (elmtType == long.class) {
                mStore.addArrayResult(protoName, (long[]) keyValue);
                return;
            } else if (elmtType == double.class) {
                mStore.addArrayResult(protoName, (double[]) keyValue);
                return;
            } else if (elmtType == boolean.class) {
                mStore.addArrayResult(protoName, (boolean[]) keyValue);
                return;
            } else if (elmtType == byte.class) {
                // Infostore does not support byte, convert to int32 and save
                int[] intValues = new int[arrayLen];
                for (int i = 0; i < arrayLen; i++) {
                    intValues[i] = (int) ((byte) Array.get(keyValue, i));
                }
                mStore.addArrayResult(protoName, intValues);
                return;
            } else if (elmtType == Rational.class) {
                mStore.startArray(protoName);
                for (int i = 0; i < arrayLen; i++) {
                    storeRational((Rational) Array.get(keyValue, i), null);
                }
                mStore.endArray();
                return;
            } else if (elmtType == Size.class) {
                mStore.startArray(protoName);
                for (int i = 0; i < arrayLen; i++) {
                    storeSize((Size) Array.get(keyValue, i), null);
                }
                mStore.endArray();
                return;
            } else if (elmtType == Rect.class) {
                mStore.startArray(protoName);
                for (int i = 0; i < arrayLen; i++) {
                    storeRect((Rect) Array.get(keyValue, i), null);
                }
                mStore.endArray();
                return;
            } else if (elmtType instanceof ParameterizedType &&
                    ((ParameterizedType) elmtType).getRawType() == Range.class &&
                    ((ParameterizedType) elmtType).getActualTypeArguments()[0] == Integer.class) {
                mStore.startArray(protoName);
                for (int i = 0; i < arrayLen; i++) {
                    storeRangeInt((Range<Integer>) Array.get(keyValue, i), null);
                }
                mStore.endArray();
                return;
            } else if (elmtType == BlackLevelPattern.class) {
                mStore.startArray(protoName);
                for (int i = 0; i < arrayLen; i++) {
                    storeBlackLevelPattern((BlackLevelPattern) Array.get(keyValue, i), null);
                }
                mStore.endArray();
                return;
            } else {
                Log.w(TAG, "Storing unsupported array type: " + elmtType +
                        " for keyName: " + keyName);
                return;
            }
        }

        private void storeCameraChars(
                CameraCharacteristics chars) throws Exception {
            HashSet<String> charsKeyNames = getAllCharacteristicsKeyNames();
            Field[] allFields = chars.getClass().getDeclaredFields();
            for (Field field : allFields) {
                if (Modifier.isPublic(field.getModifiers()) &&
                        Modifier.isStatic(field.getModifiers()) &&
                        field.getType() == CameraCharacteristics.Key.class &&
                        field.getGenericType() instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) field.getGenericType();
                    Type[] argTypes = paramType.getActualTypeArguments();
                    if (argTypes.length > 0) {
                        try {
                            Type keyType = argTypes[0];
                            Object keyObj = field.get(chars);
                            String keyName = getKeyName(keyObj);
                            if (charsKeyNames.contains(keyName)) {
                                if (keyType instanceof GenericArrayType) {
                                    storeArrayEntry(keyType, keyObj, chars);
                                } else {
                                    storeEntry(keyType, keyObj, chars);
                                }
                            }
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException(
                                    "Access error for field: " + field + ": ", e);
                        }
                    }
                }
            }
        }
    }


    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        store.addResult("profile_480p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P));
        store.addResult("profile_720p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P));
        store.addResult("profile_1080p", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P));
        store.addResult("profile_cif", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_CIF));
        store.addResult("profile_qcif", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QCIF));
        store.addResult("profile_qvga", CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_QVGA));

        CameraManager cameraManager = (CameraManager)
                getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            store.addResult("num_of_camera", cameraIdList.length);
            if (cameraIdList.length > 0) {
                CameraCharacteristicsStorer charsStorer =
                        new CameraCharacteristicsStorer(cameraManager, store);
                store.startArray("per_camera_info");
                for (int i = 0; i < cameraIdList.length; i++) {
                    charsStorer.storeCameraInfo(cameraIdList[i]);
                }
                store.endArray(); // per_camera_info
            }
        } catch (CameraAccessException e) {
            Log.e(TAG,
                    "Unable to get camera camera ID list, error: "
                            + e.getMessage());
        }
    }

    /*@O~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * The key entries below this point are generated from metadata
     * definitions in /system/media/camera/docs. Do not modify by hand or
     * modify the comment blocks at the start or end.
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~*/

    private static HashSet<String> getAllCharacteristicsKeyNames() {
        HashSet<String> charsKeyNames = new HashSet<String>();
        charsKeyNames.add(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_MAX_REGIONS_AE.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_MAX_REGIONS_AF.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AWB_LOCK_AVAILABLE.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_AVAILABLE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE.getName());
        charsKeyNames.add(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.FLASH_INFO_AVAILABLE.getName());
        charsKeyNames.add(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.JPEG_AVAILABLE_THUMBNAIL_SIZES.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_FACING.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_AVAILABLE_FILTER_DENSITIES.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE.getName());
        charsKeyNames.add(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION.getName());
        charsKeyNames.add(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_PARTIAL_RESULT_COUNT.getName());
        charsKeyNames.add(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES.getName());
        charsKeyNames.add(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM.getName());
        charsKeyNames.add(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP.getName());
        charsKeyNames.add(CameraCharacteristics.SCALER_CROPPING_TYPE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_FORWARD_MATRIX1.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_FORWARD_MATRIX2.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_ORIENTATION.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_AVAILABLE_TEST_PATTERN_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_OPTICAL_BLACK_REGIONS.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_LENS_SHADING_APPLIED.getName());
        charsKeyNames.add(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE.getName());
        charsKeyNames.add(CameraCharacteristics.SHADING_AVAILABLE_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT.getName());
        charsKeyNames.add(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS.getName());
        charsKeyNames.add(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES.getName());
        charsKeyNames.add(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL.getName());
        charsKeyNames.add(CameraCharacteristics.SYNC_MAX_LATENCY.getName());
        charsKeyNames.add(CameraCharacteristics.REPROCESS_MAX_CAPTURE_STALL.getName());
        charsKeyNames.add(CameraCharacteristics.DEPTH_DEPTH_IS_EXCLUSIVE.getName());

        return charsKeyNames;
    }

    /*~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * End generated code
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~O@*/
}
