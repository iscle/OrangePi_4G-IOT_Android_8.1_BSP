/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.camera2.cts;

import static android.hardware.camera2.CameraCharacteristics.*;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CameraCharacteristics.Key;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>
 * This class covers the {@link CameraCharacteristics} tests that are not
 * covered by {@link CaptureRequestTest} and {@link ExtendedCameraCharacteristicsTest}
 * </p>
 * <p>
 * Note that most of the tests in this class don't require camera open.
 * </p>
 */
public class StaticMetadataTest extends Camera2AndroidTestCase {
    private static final String TAG = "StaticMetadataTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final float MIN_FPS_FOR_FULL_DEVICE = 20.0f;
    private String mCameraId;

    // Last defined capability enum, for iterating over all of them
    private static final int LAST_CAPABILITY_ENUM = REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE;

    /**
     * Test the available capability for different hardware support level devices.
     */
    public void testHwSupportedLevel() throws Exception {
        Key<StreamConfigurationMap> key =
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
        final float SIZE_ERROR_MARGIN = 0.03f;
        for (String id : mCameraIds) {
            initStaticMetadata(id);
            StreamConfigurationMap configs = mStaticInfo.getValueFromKeyNonNull(key);
            Rect activeRect = mStaticInfo.getActiveArraySizeChecked();
            Size sensorSize = new Size(activeRect.width(), activeRect.height());
            List<Integer> availableCaps = mStaticInfo.getAvailableCapabilitiesChecked();

            mCollector.expectTrue("All devices must contains BACKWARD_COMPATIBLE capability or " +
                    "DEPTH_OUTPUT capabillity" ,
                    availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) ||
                    availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) );

            if (mStaticInfo.isHardwareLevelAtLeast(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                mCollector.expectTrue("Level 3 device must contain YUV_REPROCESSING capability",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING));
                mCollector.expectTrue("Level 3 device must contain RAW capability",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_RAW));
            }

            if (mStaticInfo.isHardwareLevelAtLeastFull()) {
                // Capability advertisement must be right.
                mCollector.expectTrue("Full device must contain MANUAL_SENSOR capability",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR));
                mCollector.expectTrue("Full device must contain MANUAL_POST_PROCESSING capability",
                        availableCaps.contains(
                                REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING));
                mCollector.expectTrue("Full device must contain BURST_CAPTURE capability",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE));

                // Need support per frame control
                mCollector.expectTrue("Full device must support per frame control",
                        mStaticInfo.isPerFrameControlSupported());
            }

            if (mStaticInfo.isHardwareLevelLegacy()) {
                mCollector.expectTrue("Legacy devices must contain BACKWARD_COMPATIBLE capability",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE));
            }

            if (availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                mCollector.expectTrue("MANUAL_SENSOR capability always requires " +
                        "READ_SENSOR_SETTINGS capability as well",
                        availableCaps.contains(REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS));
            }

            if (mStaticInfo.isColorOutputSupported()) {
                // Max jpeg resolution must be very close to  sensor resolution
                Size[] jpegSizes = mStaticInfo.getJpegOutputSizesChecked();
                Size maxJpegSize = CameraTestUtils.getMaxSize(jpegSizes);
                float croppedWidth = (float)sensorSize.getWidth();
                float croppedHeight = (float)sensorSize.getHeight();
                float sensorAspectRatio = (float)sensorSize.getWidth() / (float)sensorSize.getHeight();
                float maxJpegAspectRatio = (float)maxJpegSize.getWidth() / (float)maxJpegSize.getHeight();
                if (sensorAspectRatio < maxJpegAspectRatio) {
                    croppedHeight = (float)sensorSize.getWidth() / maxJpegAspectRatio;
                } else if (sensorAspectRatio > maxJpegAspectRatio) {
                    croppedWidth = (float)sensorSize.getHeight() * maxJpegAspectRatio;
                }
                Size croppedSensorSize = new Size((int)croppedWidth, (int)croppedHeight);
                mCollector.expectSizesAreSimilar(
                    "Active array size or cropped active array size and max JPEG size should be similar",
                    croppedSensorSize, maxJpegSize, SIZE_ERROR_MARGIN);
            }

            // TODO: test all the keys mandatory for all capability devices.
        }
    }

    /**
     * Test max number of output stream reported by device
     */
    public void testMaxNumOutputStreams() throws Exception {
        for (String id : mCameraIds) {
            initStaticMetadata(id);
            int maxNumStreamsRaw = mStaticInfo.getMaxNumOutputStreamsRawChecked();
            int maxNumStreamsProc = mStaticInfo.getMaxNumOutputStreamsProcessedChecked();
            int maxNumStreamsProcStall = mStaticInfo.getMaxNumOutputStreamsProcessedStallChecked();

            mCollector.expectTrue("max number of raw output streams must be a non negative number",
                    maxNumStreamsRaw >= 0);
            mCollector.expectTrue("max number of processed (stalling) output streams must be >= 1",
                    maxNumStreamsProcStall >= 1);

            if (mStaticInfo.isHardwareLevelAtLeastFull()) {
                mCollector.expectTrue("max number of processed (non-stalling) output streams" +
                        "must be >= 3 for FULL device",
                        maxNumStreamsProc >= 3);
            } else if (mStaticInfo.isColorOutputSupported()) {
                mCollector.expectTrue("max number of processed (non-stalling) output streams" +
                        "must be >= 2 for devices that support color output",
                        maxNumStreamsProc >= 2);
            }
        }

    }

    /**
     * Test advertised capability does match available keys and vice versa
     */
    public void testCapabilities() throws Exception {
        for (String id : mCameraIds) {
            initStaticMetadata(id);
            List<Integer> availableCaps = mStaticInfo.getAvailableCapabilitiesChecked();

            for (Integer capability = REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE;
                    capability <= LAST_CAPABILITY_ENUM; capability++) {
                boolean isCapabilityAvailable = availableCaps.contains(capability);
                validateCapability(capability, isCapabilityAvailable);
            }
            // Note: Static metadata for capabilities is tested in ExtendedCameraCharacteristicsTest
        }
    }

    /**
     * Check if request keys' presence match expectation.
     *
     * @param capabilityName The name string of capability being tested. Used for output messages.
     * @param requestKeys The capture request keys to be checked
     * @param expectedPresence Expected presence of {@code requestKeys}. {@code true} for expecting
     *        all keys are available. Otherwise {@code false}
     * @return {@code true} if request keys' presence match expectation. Otherwise {@code false}
     */
    private boolean validateRequestKeysPresence(String capabilityName,
            Collection<CaptureRequest.Key<?>> requestKeys, boolean expectedPresence) {
        boolean actualPresence = mStaticInfo.areRequestKeysAvailable(requestKeys);
        if (expectedPresence != actualPresence) {
            if (expectedPresence) {
                for (CaptureRequest.Key<?> key : requestKeys) {
                    if (!mStaticInfo.areKeysAvailable(key)) {
                        mCollector.addMessage(String.format(
                                "Camera %s list capability %s but doesn't contain request key %s",
                                mCameraId, capabilityName, key.getName()));
                    }
                }
            } else {
                Log.w(TAG, String.format(
                        "Camera %s doesn't list capability %s but contain all required keys",
                        mCameraId, capabilityName));
            }
            return false;
        }
        return true;
    }

    /**
     * Check if result keys' presence match expectation.
     *
     * @param capabilityName The name string of capability being tested. Used for output messages.
     * @param resultKeys The capture result keys to be checked
     * @param expectedPresence Expected presence of {@code resultKeys}. {@code true} for expecting
     *        all keys are available. Otherwise {@code false}
     * @return {@code true} if result keys' presence match expectation. Otherwise {@code false}
     */
    private boolean validateResultKeysPresence(String capabilityName,
            Collection<CaptureResult.Key<?>> resultKeys, boolean expectedPresence) {
        boolean actualPresence = mStaticInfo.areResultKeysAvailable(resultKeys);
        if (expectedPresence != actualPresence) {
            if (expectedPresence) {
                for (CaptureResult.Key<?> key : resultKeys) {
                    if (!mStaticInfo.areKeysAvailable(key)) {
                        mCollector.addMessage(String.format(
                                "Camera %s list capability %s but doesn't contain result key %s",
                                mCameraId, capabilityName, key.getName()));
                    }
                }
            } else {
                Log.w(TAG, String.format(
                        "Camera %s doesn't list capability %s but contain all required keys",
                        mCameraId, capabilityName));
            }
            return false;
        }
        return true;
    }

    /**
     * Check if characteristics keys' presence match expectation.
     *
     * @param capabilityName The name string of capability being tested. Used for output messages.
     * @param characteristicsKeys The characteristics keys to be checked
     * @param expectedPresence Expected presence of {@code characteristicsKeys}. {@code true} for
     *        expecting all keys are available. Otherwise {@code false}
     * @return {@code true} if characteristics keys' presence match expectation.
     *         Otherwise {@code false}
     */
    private boolean validateCharacteristicsKeysPresence(String capabilityName,
            Collection<CameraCharacteristics.Key<?>> characteristicsKeys,
            boolean expectedPresence) {
        boolean actualPresence = mStaticInfo.areCharacteristicsKeysAvailable(characteristicsKeys);
        if (expectedPresence != actualPresence) {
            if (expectedPresence) {
                for (CameraCharacteristics.Key<?> key : characteristicsKeys) {
                    if (!mStaticInfo.areKeysAvailable(key)) {
                        mCollector.addMessage(String.format(
                                "Camera %s list capability %s but doesn't contain" +
                                "characteristics key %s",
                                mCameraId, capabilityName, key.getName()));
                    }
                }
            } else {
                Log.w(TAG, String.format(
                        "Camera %s doesn't list capability %s but contain all required keys",
                        mCameraId, capabilityName));
            }
            return false;
        }
        return true;
    }

    private void validateCapability(Integer capability, boolean isCapabilityAvailable) {
        List<CaptureRequest.Key<?>> requestKeys = new ArrayList<>();
        Set<CaptureResult.Key<?>> resultKeys = new HashSet<>();
        // Capability requirements other than key presences
        List<Pair<String, Boolean>> additionalRequirements = new ArrayList<>();

        /* For available capabilities, only check request keys in this test
           Characteristics keys are tested in ExtendedCameraCharacteristicsTest
           Result keys are tested in CaptureResultTest */
        String capabilityName;
        switch (capability) {
            case REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE:
                capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE";
                requestKeys.add(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE);
                requestKeys.add(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION);
                requestKeys.add(CaptureRequest.CONTROL_AE_MODE);
                requestKeys.add(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);
                requestKeys.add(CaptureRequest.CONTROL_AF_MODE);
                requestKeys.add(CaptureRequest.CONTROL_AF_TRIGGER);
                requestKeys.add(CaptureRequest.CONTROL_AWB_MODE);
                requestKeys.add(CaptureRequest.CONTROL_CAPTURE_INTENT);
                requestKeys.add(CaptureRequest.CONTROL_EFFECT_MODE);
                requestKeys.add(CaptureRequest.CONTROL_MODE);
                requestKeys.add(CaptureRequest.CONTROL_SCENE_MODE);
                requestKeys.add(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE);
                requestKeys.add(CaptureRequest.FLASH_MODE);
                requestKeys.add(CaptureRequest.JPEG_GPS_LOCATION);
                requestKeys.add(CaptureRequest.JPEG_ORIENTATION);
                requestKeys.add(CaptureRequest.JPEG_QUALITY);
                requestKeys.add(CaptureRequest.JPEG_THUMBNAIL_QUALITY);
                requestKeys.add(CaptureRequest.JPEG_THUMBNAIL_SIZE);
                requestKeys.add(CaptureRequest.SCALER_CROP_REGION);
                requestKeys.add(CaptureRequest.STATISTICS_FACE_DETECT_MODE);
                if (mStaticInfo.getAeMaxRegionsChecked() > 0) {
                    requestKeys.add(CaptureRequest.CONTROL_AE_REGIONS);
                } else {
                    mCollector.expectTrue(
                            "CONTROL_AE_REGIONS is available but aeMaxRegion is 0",
                            !mStaticInfo.areKeysAvailable(CaptureRequest.CONTROL_AE_REGIONS));
                }
                if (mStaticInfo.getAwbMaxRegionsChecked() > 0) {
                    requestKeys.add(CaptureRequest.CONTROL_AWB_REGIONS);
                } else {
                    mCollector.expectTrue(
                            "CONTROL_AWB_REGIONS is available but awbMaxRegion is 0",
                            !mStaticInfo.areKeysAvailable(CaptureRequest.CONTROL_AWB_REGIONS));
                }
                if (mStaticInfo.getAfMaxRegionsChecked() > 0) {
                    requestKeys.add(CaptureRequest.CONTROL_AF_REGIONS);
                } else {
                    mCollector.expectTrue(
                            "CONTROL_AF_REGIONS is available but afMaxRegion is 0",
                            !mStaticInfo.areKeysAvailable(CaptureRequest.CONTROL_AF_REGIONS));
                }
                break;
            case REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING:
                capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING";
                requestKeys.add(CaptureRequest.TONEMAP_MODE);
                requestKeys.add(CaptureRequest.COLOR_CORRECTION_GAINS);
                requestKeys.add(CaptureRequest.COLOR_CORRECTION_TRANSFORM);
                requestKeys.add(CaptureRequest.SHADING_MODE);
                requestKeys.add(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE);
                requestKeys.add(CaptureRequest.TONEMAP_CURVE);
                requestKeys.add(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE);
                requestKeys.add(CaptureRequest.CONTROL_AWB_LOCK);

                // Legacy mode always doesn't support these requirements
                Boolean contrastCurveModeSupported = false;
                Boolean gammaAndPresetModeSupported = false;
                Boolean offColorAberrationModeSupported = false;
                if (mStaticInfo.isHardwareLevelAtLeastLimited() && mStaticInfo.isColorOutputSupported()) {
                    int[] tonemapModes = mStaticInfo.getAvailableToneMapModesChecked();
                    List<Integer> modeList = (tonemapModes.length == 0) ?
                            new ArrayList<Integer>() :
                            Arrays.asList(CameraTestUtils.toObject(tonemapModes));
                    contrastCurveModeSupported =
                            modeList.contains(CameraMetadata.TONEMAP_MODE_CONTRAST_CURVE);
                    gammaAndPresetModeSupported =
                            modeList.contains(CameraMetadata.TONEMAP_MODE_GAMMA_VALUE) &&
                            modeList.contains(CameraMetadata.TONEMAP_MODE_PRESET_CURVE);

                    int[] colorAberrationModes =
                            mStaticInfo.getAvailableColorAberrationModesChecked();
                    modeList = (colorAberrationModes.length == 0) ?
                            new ArrayList<Integer>() :
                            Arrays.asList(CameraTestUtils.toObject(colorAberrationModes));
                    offColorAberrationModeSupported =
                            modeList.contains(CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_OFF);
                }
                Boolean tonemapModeQualified =
                        contrastCurveModeSupported || gammaAndPresetModeSupported;
                additionalRequirements.add(new Pair<String, Boolean>(
                        "Tonemap mode must include {CONTRAST_CURVE} and/or " +
                        "{GAMMA_VALUE, PRESET_CURVE}",
                        tonemapModeQualified));
                additionalRequirements.add(new Pair<String, Boolean>(
                        "Color aberration mode must include OFF", offColorAberrationModeSupported));
                additionalRequirements.add(new Pair<String, Boolean>(
                        "Must support AWB lock", mStaticInfo.isAwbLockSupported()));
                break;
            case REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR:
                capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR";
                requestKeys.add(CaptureRequest.CONTROL_AE_LOCK);
                requestKeys.add(CaptureRequest.SENSOR_FRAME_DURATION);
                requestKeys.add(CaptureRequest.SENSOR_EXPOSURE_TIME);
                requestKeys.add(CaptureRequest.SENSOR_SENSITIVITY);
                if (mStaticInfo.hasFocuser()) {
                    requestKeys.add(CaptureRequest.LENS_APERTURE);
                    requestKeys.add(CaptureRequest.LENS_FOCUS_DISTANCE);
                    requestKeys.add(CaptureRequest.LENS_FILTER_DENSITY);
                    requestKeys.add(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE);
                }
                requestKeys.add(CaptureRequest.BLACK_LEVEL_LOCK);
                additionalRequirements.add(new Pair<String, Boolean>(
                        "Must support AE lock", mStaticInfo.isAeLockSupported()));
                break;
            case REQUEST_AVAILABLE_CAPABILITIES_RAW:
                // RAW_CAPABILITY needs to check for not just capture request keys
                validateRawCapability(isCapabilityAvailable);
                return;
            case REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE:
                // Tested in ExtendedCameraCharacteristicsTest
                return;
            case REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS:
                capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS";
                resultKeys.add(CaptureResult.SENSOR_FRAME_DURATION);
                resultKeys.add(CaptureResult.SENSOR_EXPOSURE_TIME);
                resultKeys.add(CaptureResult.SENSOR_SENSITIVITY);
                if (mStaticInfo.hasFocuser()) {
                    resultKeys.add(CaptureResult.LENS_APERTURE);
                    resultKeys.add(CaptureResult.LENS_FOCUS_DISTANCE);
                    resultKeys.add(CaptureResult.LENS_FILTER_DENSITY);
                }
                break;

            case REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING:
            case REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING:
                // Tested in ExtendedCameraCharacteristicsTest
                return;
            case REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT:
                // Tested in ExtendedCameracharacteristicsTest
                return;
            default:
                capabilityName = "Unknown";
                assertTrue(String.format("Unknown capability set: %d", capability),
                           !isCapabilityAvailable);
                return;
        }

        // Check additional requirements and exit early if possible
        if (!isCapabilityAvailable) {
            for (Pair<String, Boolean> p : additionalRequirements) {
                String requirement = p.first;
                Boolean meetRequirement = p.second;
                // No further check is needed if we've found why capability cannot be advertised
                if (!meetRequirement) {
                    Log.v(TAG, String.format(
                            "Camera %s doesn't list capability %s because of requirement: %s",
                            mCameraId, capabilityName, requirement));
                    return;
                }
            }
        }

        boolean matchExpectation = true;
        if (!requestKeys.isEmpty()) {
            matchExpectation &= validateRequestKeysPresence(
                    capabilityName, requestKeys, isCapabilityAvailable);
        }
        if(!resultKeys.isEmpty()) {
            matchExpectation &= validateResultKeysPresence(
                    capabilityName, resultKeys, isCapabilityAvailable);
        }

        // Check additional requirements
        for (Pair<String, Boolean> p : additionalRequirements) {
            String requirement = p.first;
            Boolean meetRequirement = p.second;
            if (isCapabilityAvailable && !meetRequirement) {
                mCollector.addMessage(String.format(
                        "Camera %s list capability %s but does not meet the requirement: %s",
                        mCameraId, capabilityName, requirement));
            }
        }

        // In case of isCapabilityAvailable == true, error has been filed in
        // validateRequest/ResultKeysPresence
        if (!matchExpectation && !isCapabilityAvailable) {
            mCollector.addMessage(String.format(
                    "Camera %s doesn't list capability %s but meets all requirements",
                    mCameraId, capabilityName));
        }
    }

    private void validateRawCapability(boolean isCapabilityAvailable) {
        String capabilityName = "REQUEST_AVAILABLE_CAPABILITIES_RAW";

        Set<CaptureRequest.Key<?>> requestKeys = new HashSet<>();
        requestKeys.add(CaptureRequest.HOT_PIXEL_MODE);
        requestKeys.add(CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE);

        Set<CameraCharacteristics.Key<?>> characteristicsKeys = new HashSet<>();
        characteristicsKeys.add(HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES);
        characteristicsKeys.add(SENSOR_BLACK_LEVEL_PATTERN);
        characteristicsKeys.add(SENSOR_CALIBRATION_TRANSFORM1);
        characteristicsKeys.add(SENSOR_COLOR_TRANSFORM1);
        characteristicsKeys.add(SENSOR_FORWARD_MATRIX1);
        characteristicsKeys.add(SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        characteristicsKeys.add(SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        characteristicsKeys.add(SENSOR_INFO_WHITE_LEVEL);
        characteristicsKeys.add(SENSOR_REFERENCE_ILLUMINANT1);
        characteristicsKeys.add(STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES);

        Set<CaptureResult.Key<?>> resultKeys = new HashSet<>();
        resultKeys.add(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        resultKeys.add(CaptureResult.SENSOR_GREEN_SPLIT);
        resultKeys.add(CaptureResult.SENSOR_NOISE_PROFILE);

        boolean rawOutputSupported = mStaticInfo.getRawOutputSizesChecked().length > 0;
        boolean requestKeysPresent = mStaticInfo.areRequestKeysAvailable(requestKeys);
        boolean characteristicsKeysPresent =
                mStaticInfo.areCharacteristicsKeysAvailable(characteristicsKeys);
        boolean resultKeysPresent = mStaticInfo.areResultKeysAvailable(resultKeys);
        boolean expectCapabilityPresent = rawOutputSupported && requestKeysPresent &&
                characteristicsKeysPresent && resultKeysPresent;

        if (isCapabilityAvailable != expectCapabilityPresent) {
            if (isCapabilityAvailable) {
                mCollector.expectTrue(
                        "REQUEST_AVAILABLE_CAPABILITIES_RAW should support RAW_SENSOR output",
                        rawOutputSupported);
                validateRequestKeysPresence(capabilityName, requestKeys, isCapabilityAvailable);
                validateResultKeysPresence(capabilityName, resultKeys, isCapabilityAvailable);
                validateCharacteristicsKeysPresence(capabilityName, characteristicsKeys,
                        isCapabilityAvailable);
            } else {
                mCollector.addMessage(String.format(
                        "Camera %s doesn't list capability %s but contain all required keys" +
                        " and RAW format output",
                        mCameraId, capabilityName));
            }
        }
    }

    /**
     * Test lens facing.
     */
    public void testLensFacing() throws Exception {
        for (String id : mCameraIds) {
            initStaticMetadata(id);
            mStaticInfo.getLensFacingChecked();
        }
    }

    private float getFpsForMaxSize(String cameraId) throws Exception {
        HashMap<Size, Long> minFrameDurationMap =
                mStaticInfo.getAvailableMinFrameDurationsForFormatChecked(ImageFormat.YUV_420_888);

        Size[] sizes = CameraTestUtils.getSupportedSizeForFormat(ImageFormat.YUV_420_888,
                cameraId, mCameraManager);
        Size maxSize = CameraTestUtils.getMaxSize(sizes);
        Long minDuration = minFrameDurationMap.get(maxSize);
        if (VERBOSE) {
            Log.v(TAG, "min frame duration for size " + maxSize + " is " + minDuration);
        }
        assertTrue("min duration for max size must be postive number",
                minDuration != null && minDuration > 0);

        return 1e9f / minDuration;
    }

    /**
     * Initialize static metadata for a given camera id.
     */
    private void initStaticMetadata(String cameraId) throws Exception {
        mCameraId = cameraId;
        mCollector.setCameraId(cameraId);
        mStaticInfo = new StaticMetadata(mCameraManager.getCameraCharacteristics(cameraId),
                CheckLevel.COLLECT, /* collector */mCollector);
    }
}
