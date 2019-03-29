/*
 * Copyright 2014 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.camera2.CameraCharacteristics.*;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.TonemapCurve;
import android.media.Image;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Basic test for camera CaptureRequest key controls.
 * </p>
 * <p>
 * Several test categories are covered: manual sensor control, 3A control,
 * manual ISP control and other per-frame control and synchronization.
 * </p>
 */
public class CaptureRequestTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "CaptureRequestTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int NUM_FRAMES_VERIFIED = 15;
    private static final int NUM_FACE_DETECTION_FRAMES_VERIFIED = 60;
    /** 30ms exposure time must be supported by full capability devices. */
    private static final long DEFAULT_EXP_TIME_NS = 30000000L; // 30ms
    private static final int DEFAULT_SENSITIVITY = 100;
    private static final int RGGB_COLOR_CHANNEL_COUNT = 4;
    private static final int MAX_SHADING_MAP_SIZE = 64 * 64 * RGGB_COLOR_CHANNEL_COUNT;
    private static final int MIN_SHADING_MAP_SIZE = 1 * 1 * RGGB_COLOR_CHANNEL_COUNT;
    private static final long IGNORE_REQUESTED_EXPOSURE_TIME_CHECK = -1L;
    private static final long EXPOSURE_TIME_BOUNDARY_50HZ_NS = 10000000L; // 10ms
    private static final long EXPOSURE_TIME_BOUNDARY_60HZ_NS = 8333333L; // 8.3ms, Approximation.
    private static final long EXPOSURE_TIME_ERROR_MARGIN_NS = 100000L; // 100us, Approximation.
    private static final float EXPOSURE_TIME_ERROR_MARGIN_RATE = 0.03f; // 3%, Approximation.
    private static final float SENSITIVITY_ERROR_MARGIN_RATE = 0.03f; // 3%, Approximation.
    private static final int DEFAULT_NUM_EXPOSURE_TIME_STEPS = 3;
    private static final int DEFAULT_NUM_SENSITIVITY_STEPS = 8;
    private static final int DEFAULT_SENSITIVITY_STEP_SIZE = 100;
    private static final int NUM_RESULTS_WAIT_TIMEOUT = 100;
    private static final int NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY = 8;
    private static final int NUM_TEST_FOCUS_DISTANCES = 10;
    private static final int NUM_FOCUS_DISTANCES_REPEAT = 3;
    // 5 percent error margin for calibrated device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_CALIBRATED = 0.05f;
    // 25 percent error margin for uncalibrated device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_UNCALIBRATED = 0.25f;
    // 10 percent error margin for approximate device
    private static final float FOCUS_DISTANCE_ERROR_PERCENT_APPROXIMATE = 0.10f;
    private static final int ANTI_FLICKERING_50HZ = 1;
    private static final int ANTI_FLICKERING_60HZ = 2;
    // 5 percent error margin for resulting crop regions
    private static final float CROP_REGION_ERROR_PERCENT_DELTA = 0.05f;
    // 1 percent error margin for centering the crop region
    private static final float CROP_REGION_ERROR_PERCENT_CENTERED = 0.01f;
    private static final float DYNAMIC_VS_FIXED_BLK_WH_LVL_ERROR_MARGIN = 0.25f;
    private static final float DYNAMIC_VS_OPTICAL_BLK_LVL_ERROR_MARGIN = 0.2f;

    // Linear tone mapping curve example.
    private static final float[] TONEMAP_CURVE_LINEAR = {0, 0, 1.0f, 1.0f};
    // Standard sRGB tone mapping, per IEC 61966-2-1:1999, with 16 control points.
    private static final float[] TONEMAP_CURVE_SRGB = {
            0.0000f, 0.0000f, 0.0667f, 0.2864f, 0.1333f, 0.4007f, 0.2000f, 0.4845f,
            0.2667f, 0.5532f, 0.3333f, 0.6125f, 0.4000f, 0.6652f, 0.4667f, 0.7130f,
            0.5333f, 0.7569f, 0.6000f, 0.7977f, 0.6667f, 0.8360f, 0.7333f, 0.8721f,
            0.8000f, 0.9063f, 0.8667f, 0.9389f, 0.9333f, 0.9701f, 1.0000f, 1.0000f
    };
    private final Rational ZERO_R = new Rational(0, 1);
    private final Rational ONE_R = new Rational(1, 1);

    private final int NUM_ALGORITHMS = 3; // AE, AWB and AF
    private final int INDEX_ALGORITHM_AE = 0;
    private final int INDEX_ALGORITHM_AWB = 1;
    private final int INDEX_ALGORITHM_AF = 2;

    private enum TorchSeqState {
        RAMPING_UP,
        FIRED,
        RAMPING_DOWN
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test black level lock when exposure value change.
     * <p>
     * When {@link CaptureRequest#BLACK_LEVEL_LOCK} is true in a request, the
     * camera device should lock the black level. When the exposure values are changed,
     * the camera may require reset black level Since changes to certain capture
     * parameters (such as exposure time) may require resetting of black level
     * compensation. However, the black level must remain locked after exposure
     * value changes (when requests have lock ON).
     * </p>
     */
    public void testBlackLevelLock() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                // Start with default manual exposure time, with black level being locked.
                requestBuilder.set(CaptureRequest.BLACK_LEVEL_LOCK, true);
                changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS, DEFAULT_SENSITIVITY);

                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager,
                        getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));

                startPreview(requestBuilder, previewSz, listener);
                waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
                // No lock OFF state is allowed as the exposure is not changed.
                verifyBlackLevelLockResults(listener, NUM_FRAMES_VERIFIED, /*maxLockOffCnt*/0);

                // Double the exposure time and gain, with black level still being locked.
                changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS * 2, DEFAULT_SENSITIVITY * 2);
                listener = new SimpleCaptureCallback();
                startPreview(requestBuilder, previewSz, listener);
                waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
                // Allow at most one lock OFF state as the exposure is changed once.
                verifyBlackLevelLockResults(listener, NUM_FRAMES_VERIFIED, /*maxLockOffCnt*/1);

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test dynamic black/white levels if they are supported.
     *
     * <p>
     * If the dynamic black and white levels are reported, test below:
     *   1. the dynamic black and white levels shouldn't deviate from the global value too much
     *   for different sensitivities.
     *   2. If the RAW_SENSOR and optical black regions are supported, capture RAW images and
     *   calculate the optical black level values. The reported dynamic black level should be
     *   close enough to the optical black level values.
     * </p>
     */
    public void testDynamicBlackWhiteLevel() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isDynamicBlackLevelSupported()) {
                    continue;
                }
                dynamicBlackWhiteLevelTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Basic lens shading map request test.
     * <p>
     * When {@link CaptureRequest#SHADING_MODE} is set to OFF, no lens shading correction will
     * be applied by the camera device, and an identity lens shading map data
     * will be provided if {@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE} is ON.
     * </p>
     * <p>
     * When {@link CaptureRequest#SHADING_MODE} is set to other modes, lens shading correction
     * will be applied by the camera device. The lens shading map data can be
     * requested by setting {@link CaptureRequest#STATISTICS_LENS_SHADING_MAP_MODE} to ON.
     * </p>
     */
    public void testLensShadingMap() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isManualLensShadingMapSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " doesn't support lens shading controls, skipping test");
                    continue;
                }

                List<Integer> lensShadingMapModes = Arrays.asList(CameraTestUtils.toObject(
                        mStaticInfo.getAvailableLensShadingMapModesChecked()));

                if (!lensShadingMapModes.contains(STATISTICS_LENS_SHADING_MAP_MODE_ON)) {
                    continue;
                }

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                requestBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                        STATISTICS_LENS_SHADING_MAP_MODE_ON);

                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager,
                        getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));
                List<Integer> lensShadingModes = Arrays.asList(CameraTestUtils.toObject(
                        mStaticInfo.getAvailableLensShadingModesChecked()));

                // Shading map mode OFF, lensShadingMapMode ON, camera device
                // should output unity maps.
                if (lensShadingModes.contains(SHADING_MODE_OFF)) {
                    requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_OFF);
                    listener = new SimpleCaptureCallback();
                    startPreview(requestBuilder, previewSz, listener);
                    waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
                    verifyShadingMap(listener, NUM_FRAMES_VERIFIED, SHADING_MODE_OFF);
                }

                // Shading map mode FAST, lensShadingMapMode ON, camera device
                // should output valid maps.
                if (lensShadingModes.contains(SHADING_MODE_FAST)) {
                    requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_FAST);

                    listener = new SimpleCaptureCallback();
                    startPreview(requestBuilder, previewSz, listener);
                    waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
                    // Allow at most one lock OFF state as the exposure is changed once.
                    verifyShadingMap(listener, NUM_FRAMES_VERIFIED, SHADING_MODE_FAST);
                }

                // Shading map mode HIGH_QUALITY, lensShadingMapMode ON, camera device
                // should output valid maps.
                if (lensShadingModes.contains(SHADING_MODE_HIGH_QUALITY)) {
                    requestBuilder.set(CaptureRequest.SHADING_MODE, SHADING_MODE_HIGH_QUALITY);

                    listener = new SimpleCaptureCallback();
                    startPreview(requestBuilder, previewSz, listener);
                    waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
                    verifyShadingMap(listener, NUM_FRAMES_VERIFIED, SHADING_MODE_HIGH_QUALITY);
                }

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test {@link CaptureRequest#CONTROL_AE_ANTIBANDING_MODE} control.
     * <p>
     * Test all available anti-banding modes, check if the exposure time adjustment is
     * correct.
     * </p>
     */
    public void testAntiBandingModes() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);

                // Without manual sensor control, exposure time cannot be verified
                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }

                int[] modes = mStaticInfo.getAeAvailableAntiBandingModesChecked();

                Size previewSz =
                        getMaxPreviewSize(mCamera.getId(), mCameraManager,
                        getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));

                for (int mode : modes) {
                    antiBandingTestByMode(previewSz, mode);
                }
            } finally {
                closeDevice();
            }
        }

    }

    /**
     * Test AE mode and lock.
     *
     * <p>
     * For AE lock, when it is locked, exposure parameters shouldn't be changed.
     * For AE modes, each mode should satisfy the per frame controls defined in
     * API specifications.
     * </p>
     */
    public void testAeModeAndLock() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }

                Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.

                // Update preview surface with given size for all sub-tests.
                updatePreviewSurface(maxPreviewSz);

                // Test aeMode and lock
                int[] aeModes = mStaticInfo.getAeAvailableModesChecked();
                for (int mode : aeModes) {
                    aeModeAndLockTestByMode(mode);
                }
            } finally {
                closeDevice();
            }
        }
    }

    /** Test {@link CaptureRequest#FLASH_MODE} control.
     * <p>
     * For each {@link CaptureRequest#FLASH_MODE} mode, test the flash control
     * and {@link CaptureResult#FLASH_STATE} result.
     * </p>
     */
    public void testFlashControl() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

                Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.

                startPreview(requestBuilder, maxPreviewSz, listener);

                // Flash control can only be used when the AE mode is ON or OFF.
                flashTestByAeMode(listener, CaptureRequest.CONTROL_AE_MODE_ON);

                // LEGACY won't support AE mode OFF
                boolean aeOffModeSupported = false;
                for (int aeMode : mStaticInfo.getAeAvailableModesChecked()) {
                    if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
                        aeOffModeSupported = true;
                    }
                }
                if (aeOffModeSupported) {
                    flashTestByAeMode(listener, CaptureRequest.CONTROL_AE_MODE_OFF);
                }

                stopPreview();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test face detection modes and results.
     */
    public void testFaceDetection() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                faceDetectionTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test tone map modes and controls.
     */
    public void testToneMapControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isManualToneMapSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support tone mapping controls, skipping test");
                    continue;
                }
                toneMapTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test color correction modes and controls.
     */
    public void testColorCorrectionControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorCorrectionSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support color correction controls, skipping test");
                    continue;
                }
                colorCorrectionTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test edge mode control for Fps not exceeding 30.
     */
    public void testEdgeModeControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isEdgeModeControlSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support EDGE_MODE controls, skipping test");
                    continue;
                }

                List<Range<Integer>> fpsRanges = getTargetFpsRangesUpTo30(mStaticInfo);
                edgeModesTestByCamera(fpsRanges);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test edge mode control for Fps greater than 30.
     */
    public void testEdgeModeControlFastFps() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isEdgeModeControlSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support EDGE_MODE controls, skipping test");
                    continue;
                }

                List<Range<Integer>> fpsRanges = getTargetFpsRangesGreaterThan30(mStaticInfo);
                edgeModesTestByCamera(fpsRanges);
            } finally {
                closeDevice();
            }
        }

    }

    /**
     * Test focus distance control.
     */
    public void testFocusDistanceControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.hasFocuser()) {
                    Log.i(TAG, "Camera " + id + " has no focuser, skipping test");
                    continue;
                }

                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    Log.i(TAG, "Camera " + id +
                            " does not support MANUAL_SENSOR, skipping test");
                    continue;
                }

                focusDistanceTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test noise reduction mode for fps ranges not exceeding 30
     */
    public void testNoiseReductionModeControl() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isNoiseReductionModeControlSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support noise reduction mode, skipping test");
                    continue;
                }

                List<Range<Integer>> fpsRanges = getTargetFpsRangesUpTo30(mStaticInfo);
                noiseReductionModeTestByCamera(fpsRanges);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test noise reduction mode for fps ranges greater than 30
     */
    public void testNoiseReductionModeControlFastFps() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isNoiseReductionModeControlSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " doesn't support noise reduction mode, skipping test");
                    continue;
                }

                List<Range<Integer>> fpsRanges = getTargetFpsRangesGreaterThan30(mStaticInfo);
                noiseReductionModeTestByCamera(fpsRanges);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test AWB lock control.
     *
     * <p>The color correction gain and transform shouldn't be changed when AWB is locked.</p>
     */
    public void testAwbModeAndLock() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                awbModeAndLockTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test different AF modes.
     */
    public void testAfModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                afModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test video and optical stabilizations.
     */
    public void testCameraStabilizations() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                List<Key<?>> keys = mStaticInfo.getCharacteristics().getKeys();
                if (!(keys.contains(
                        CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES) ||
                        keys.contains(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION))) {
                    Log.i(TAG, "Camera " + id + " doesn't support any stabilization modes");
                    continue;
                }
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                stabilizationTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test digitalZoom (center wise and non-center wise), validate the returned crop regions.
     * The max preview size is used for each camera.
     */
    public void testDigitalZoom() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                Size maxPreviewSize = mOrderedPreviewSizes.get(0);
                digitalZoomTestByCamera(maxPreviewSize);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test digital zoom and all preview size combinations.
     * TODO: this and above test should all be moved to preview test class.
     */
    public void testDigitalZoomPreviewCombinations() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                digitalZoomPreviewCombinationTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test scene mode controls.
     */
    public void testSceneModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (mStaticInfo.isSceneModeSupported()) {
                    sceneModeTestByCamera();
                }
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test effect mode controls.
     */
    public void testEffectModes() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                effectModeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    // TODO: add 3A state machine test.

    /**
     * Per camera dynamic black and white level test.
     */
    private void dynamicBlackWhiteLevelTestByCamera() throws Exception {
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = null;
        CaptureRequest.Builder previewBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder rawBuilder = null;
        Size previewSize =
                getMaxPreviewSize(mCamera.getId(), mCameraManager,
                getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));
        Size rawSize = null;
        boolean canCaptureBlackRaw =
                mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) &&
                mStaticInfo.isOpticalBlackRegionSupported();
        if (canCaptureBlackRaw) {
            // Capture Raw16, then calculate the optical black, and use it to check with the dynamic
            // black level.
            rawBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            rawSize = mStaticInfo.getRawDimensChecked();
            imageListener = new SimpleImageReaderListener();
            prepareRawCaptureAndStartPreview(previewBuilder, rawBuilder, previewSize, rawSize,
                    resultListener, imageListener);
        } else {
            startPreview(previewBuilder, previewSize, resultListener);
        }

        // Capture a sequence of frames with different sensitivities and validate the black/white
        // level values
        int[] sensitivities = getSensitivityTestValues();
        float[][] dynamicBlackLevels = new float[sensitivities.length][];
        int[] dynamicWhiteLevels = new int[sensitivities.length];
        float[][] opticalBlackLevels = new float[sensitivities.length][];
        for (int i = 0; i < sensitivities.length; i++) {
            CaptureResult result = null;
            if (canCaptureBlackRaw) {
                changeExposure(rawBuilder, DEFAULT_EXP_TIME_NS, sensitivities[i]);
                CaptureRequest rawRequest = rawBuilder.build();
                mSession.capture(rawRequest, resultListener, mHandler);
                result = resultListener.getCaptureResultForRequest(rawRequest,
                        NUM_RESULTS_WAIT_TIMEOUT);
                Image rawImage = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);

                // Get max (area-wise) optical black region
                Rect[] opticalBlackRegions = mStaticInfo.getCharacteristics().get(
                        CameraCharacteristics.SENSOR_OPTICAL_BLACK_REGIONS);
                Rect maxRegion = opticalBlackRegions[0];
                for (Rect region : opticalBlackRegions) {
                    if (region.width() * region.height() > maxRegion.width() * maxRegion.height()) {
                        maxRegion = region;
                    }
                }

                // Get average black pixel values in the region (region is multiple of 2x2)
                Image.Plane rawPlane = rawImage.getPlanes()[0];
                ByteBuffer rawBuffer = rawPlane.getBuffer();
                float[] avgBlackLevels = {0, 0, 0, 0};
                final int rowSize = rawPlane.getRowStride();
                final int bytePerPixel = rawPlane.getPixelStride();
                if (VERBOSE) {
                    Log.v(TAG, "maxRegion: " + maxRegion + ", Row stride: " +
                            rawPlane.getRowStride());
                }
                for (int row = maxRegion.top; row < maxRegion.bottom; row += 2) {
                    for (int col = maxRegion.left; col < maxRegion.right; col += 2) {
                        int startOffset = row * rowSize + col * bytePerPixel;
                        avgBlackLevels[0] += rawBuffer.getShort(startOffset);
                        avgBlackLevels[1] += rawBuffer.getShort(startOffset + bytePerPixel);
                        startOffset += rowSize;
                        avgBlackLevels[2] += rawBuffer.getShort(startOffset);
                        avgBlackLevels[3] += rawBuffer.getShort(startOffset + bytePerPixel);
                    }
                }
                int numBlackBlocks = maxRegion.width() * maxRegion.height() / (2 * 2);
                for (int m = 0; m < avgBlackLevels.length; m++) {
                    avgBlackLevels[m] /= numBlackBlocks;
                }
                opticalBlackLevels[i] = avgBlackLevels;

                if (VERBOSE) {
                    Log.v(TAG, String.format("Optical black level results for sensitivity (%d): %s",
                            sensitivities[i], Arrays.toString(avgBlackLevels)));
                }

                rawImage.close();
            } else {
                changeExposure(previewBuilder, DEFAULT_EXP_TIME_NS, sensitivities[i]);
                CaptureRequest previewRequest = previewBuilder.build();
                mSession.capture(previewRequest, resultListener, mHandler);
                result = resultListener.getCaptureResultForRequest(previewRequest,
                        NUM_RESULTS_WAIT_TIMEOUT);
            }

            dynamicBlackLevels[i] = getValueNotNull(result,
                    CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
            dynamicWhiteLevels[i] = getValueNotNull(result,
                    CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        }

        if (VERBOSE) {
            Log.v(TAG, "Different sensitivities tested: " + Arrays.toString(sensitivities));
            Log.v(TAG, "Dynamic black level results: " + Arrays.deepToString(dynamicBlackLevels));
            Log.v(TAG, "Dynamic white level results: " + Arrays.toString(dynamicWhiteLevels));
            if (canCaptureBlackRaw) {
                Log.v(TAG, "Optical black level results " +
                        Arrays.deepToString(opticalBlackLevels));
            }
        }

        // check the dynamic black level against global black level.
        // Implicit guarantee: if the dynamic black level is supported, fixed black level must be
        // supported as well (tested in ExtendedCameraCharacteristicsTest#testOpticalBlackRegions).
        BlackLevelPattern blackPattern = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
        int[] fixedBlackLevels = new int[4];
        int fixedWhiteLevel = mStaticInfo.getCharacteristics().get(
                CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        blackPattern.copyTo(fixedBlackLevels, 0);
        float maxBlackDeviation = 0;
        int maxWhiteDeviation = 0;
        for (int i = 0; i < dynamicBlackLevels.length; i++) {
            for (int j = 0; j < dynamicBlackLevels[i].length; j++) {
                if (maxBlackDeviation < Math.abs(fixedBlackLevels[j] - dynamicBlackLevels[i][j])) {
                    maxBlackDeviation = Math.abs(fixedBlackLevels[j] - dynamicBlackLevels[i][j]);
                }
            }
            if (maxWhiteDeviation < Math.abs(dynamicWhiteLevels[i] - fixedWhiteLevel)) {
                maxWhiteDeviation = Math.abs(dynamicWhiteLevels[i] - fixedWhiteLevel);
            }
        }
        mCollector.expectLessOrEqual("Max deviation of the dynamic black level vs fixed black level"
                + " exceed threshold."
                + " Dynamic black level results: " + Arrays.deepToString(dynamicBlackLevels),
                fixedBlackLevels[0] * DYNAMIC_VS_FIXED_BLK_WH_LVL_ERROR_MARGIN, maxBlackDeviation);
        mCollector.expectLessOrEqual("Max deviation of the dynamic white level exceed threshold."
                + " Dynamic white level results: " + Arrays.toString(dynamicWhiteLevels),
                fixedWhiteLevel * DYNAMIC_VS_FIXED_BLK_WH_LVL_ERROR_MARGIN,
                (float)maxWhiteDeviation);

        // Validate against optical black levels if it is available
        if (canCaptureBlackRaw) {
            maxBlackDeviation = 0;
            for (int i = 0; i < dynamicBlackLevels.length; i++) {
                for (int j = 0; j < dynamicBlackLevels[i].length; j++) {
                    if (maxBlackDeviation <
                            Math.abs(opticalBlackLevels[i][j] - dynamicBlackLevels[i][j])) {
                        maxBlackDeviation =
                                Math.abs(opticalBlackLevels[i][j] - dynamicBlackLevels[i][j]);
                    }
                }
            }

            mCollector.expectLessOrEqual("Max deviation of the dynamic black level vs optical black"
                    + " exceed threshold."
                    + " Dynamic black level results: " + Arrays.deepToString(dynamicBlackLevels)
                    + " Optical black level results: " + Arrays.deepToString(opticalBlackLevels),
                    fixedBlackLevels[0] * DYNAMIC_VS_OPTICAL_BLK_LVL_ERROR_MARGIN,
                    maxBlackDeviation);
        }
    }

    private void noiseReductionModeTestByCamera(List<Range<Integer>> fpsRanges) throws Exception {
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        int[] availableModes = mStaticInfo.getAvailableNoiseReductionModesChecked();

        for (int mode : availableModes) {
            requestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, mode);

            // Test that OFF and FAST mode should not slow down the frame rate.
            if (mode == CaptureRequest.NOISE_REDUCTION_MODE_OFF ||
                    mode == CaptureRequest.NOISE_REDUCTION_MODE_FAST) {
                verifyFpsNotSlowDown(requestBuilder, NUM_FRAMES_VERIFIED, fpsRanges);
            }

            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
            startPreview(requestBuilder, maxPrevSize, resultListener);
            mSession.setRepeatingRequest(requestBuilder.build(), resultListener, mHandler);
            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            verifyCaptureResultForKey(CaptureResult.NOISE_REDUCTION_MODE, mode,
                    resultListener, NUM_FRAMES_VERIFIED);
        }

        stopPreview();
    }

    private void focusDistanceTestByCamera() throws Exception {
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        int calibrationStatus = mStaticInfo.getFocusDistanceCalibrationChecked();
        float errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_UNCALIBRATED;
        if (calibrationStatus ==
                CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED) {
            errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_CALIBRATED;
        } else if (calibrationStatus ==
                CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE) {
            errorMargin = FOCUS_DISTANCE_ERROR_PERCENT_APPROXIMATE;
        }

        // Test changing focus distance with repeating request
        focusDistanceTestRepeating(requestBuilder, errorMargin);

        if (calibrationStatus ==
                CameraMetadata.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED)  {
            // Test changing focus distance with burst request
            focusDistanceTestBurst(requestBuilder, errorMargin);
        }
    }

    private void focusDistanceTestRepeating(CaptureRequest.Builder requestBuilder,
            float errorMargin) throws Exception {
        CaptureRequest request;
        float[] testDistances = getFocusDistanceTestValuesInOrder(0, 0);
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        startPreview(requestBuilder, maxPrevSize, resultListener);

        float[] resultDistances = new float[testDistances.length];
        int[] resultLensStates = new int[testDistances.length];

        // Collect results
        for (int i = 0; i < testDistances.length; i++) {
            requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, testDistances[i]);
            request = requestBuilder.build();
            resultListener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(request, resultListener, mHandler);
            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            waitForResultValue(resultListener, CaptureResult.LENS_STATE,
                    CaptureResult.LENS_STATE_STATIONARY, NUM_RESULTS_WAIT_TIMEOUT);
            CaptureResult result = resultListener.getCaptureResultForRequest(request,
                    NUM_RESULTS_WAIT_TIMEOUT);

            resultDistances[i] = getValueNotNull(result, CaptureResult.LENS_FOCUS_DISTANCE);
            resultLensStates[i] = getValueNotNull(result, CaptureResult.LENS_STATE);

            if (VERBOSE) {
                Log.v(TAG, "Capture repeating request focus distance: " + testDistances[i]
                        + " result: " + resultDistances[i] + " lens state " + resultLensStates[i]);
            }
        }

        verifyFocusDistance(testDistances, resultDistances, resultLensStates,
                /*ascendingOrder*/true, /*noOvershoot*/false, /*repeatStart*/0, /*repeatEnd*/0,
                errorMargin);

        if (mStaticInfo.areKeysAvailable(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)) {

            // Test hyperfocal distance optionally
            float hyperFocalDistance = mStaticInfo.getHyperfocalDistanceChecked();
            if (hyperFocalDistance > 0) {
                requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, hyperFocalDistance);
                request = requestBuilder.build();
                resultListener = new SimpleCaptureCallback();
                mSession.setRepeatingRequest(request, resultListener, mHandler);
                waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

                // Then wait for the lens.state to be stationary.
                waitForResultValue(resultListener, CaptureResult.LENS_STATE,
                        CaptureResult.LENS_STATE_STATIONARY, NUM_RESULTS_WAIT_TIMEOUT);
                CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
                Float focusDistance = getValueNotNull(result, CaptureResult.LENS_FOCUS_DISTANCE);
                mCollector.expectInRange("Focus distance for hyper focal should be close enough to" +
                        " requested value", focusDistance,
                        hyperFocalDistance * (1.0f - errorMargin),
                        hyperFocalDistance * (1.0f + errorMargin));
            }
        }
    }

    private void focusDistanceTestBurst(CaptureRequest.Builder requestBuilder,
            float errorMargin) throws Exception {

        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        float[] testDistances = getFocusDistanceTestValuesInOrder(NUM_FOCUS_DISTANCES_REPEAT,
                NUM_FOCUS_DISTANCES_REPEAT);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        startPreview(requestBuilder, maxPrevSize, resultListener);

        float[] resultDistances = new float[testDistances.length];
        int[] resultLensStates = new int[testDistances.length];

        final int maxPipelineDepth = mStaticInfo.getCharacteristics().get(
            CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH);

        // Move lens to starting position, and wait for the lens.state to be stationary.
        CaptureRequest request;
        requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, testDistances[0]);
        request = requestBuilder.build();
        mSession.setRepeatingRequest(request, resultListener, mHandler);
        waitForResultValue(resultListener, CaptureResult.LENS_STATE,
                CaptureResult.LENS_STATE_STATIONARY, NUM_RESULTS_WAIT_TIMEOUT);

        // Submit burst of requests with different focus distances
        List<CaptureRequest> burst = new ArrayList<>();
        for (int i = 0; i < testDistances.length; i ++) {
            requestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, testDistances[i]);
            burst.add(requestBuilder.build());
        }
        mSession.captureBurst(burst, resultListener, mHandler);

        for (int i = 0; i < testDistances.length; i++) {
            CaptureResult result = resultListener.getCaptureResultForRequest(
                    burst.get(i), maxPipelineDepth+1);

            resultDistances[i] = getValueNotNull(result, CaptureResult.LENS_FOCUS_DISTANCE);
            resultLensStates[i] = getValueNotNull(result, CaptureResult.LENS_STATE);

            if (VERBOSE) {
                Log.v(TAG, "Capture burst request focus distance: " + testDistances[i]
                        + " result: " + resultDistances[i] + " lens state " + resultLensStates[i]);
            }
        }

        verifyFocusDistance(testDistances, resultDistances, resultLensStates,
                /*ascendingOrder*/true, /*noOvershoot*/true,
                /*repeatStart*/NUM_FOCUS_DISTANCES_REPEAT, /*repeatEnd*/NUM_FOCUS_DISTANCES_REPEAT,
                errorMargin);

    }

    /**
     * Verify focus distance control.
     *
     * Assumption:
     * - First repeatStart+1 elements of requestedDistances share the same value
     * - Last repeatEnd+1 elements of requestedDistances share the same value
     * - All elements in between are monotonically increasing/decreasing depending on ascendingOrder.
     * - Focuser is at requestedDistances[0] at the beginning of the test.
     *
     * @param requestedDistances The requested focus distances
     * @param resultDistances The result focus distances
     * @param lensStates The result lens states
     * @param ascendingOrder The order of the expected focus distance request/output
     * @param noOvershoot Assert that focus control doesn't overshoot the requested value
     * @param repeatStart The number of times the starting focus distance is repeated
     * @param repeatEnd The number of times the ending focus distance is repeated
     * @param errorMargin The error margin between request and result
     */
    private void verifyFocusDistance(float[] requestedDistances, float[] resultDistances,
            int[] lensStates, boolean ascendingOrder, boolean noOvershoot, int repeatStart,
            int repeatEnd, float errorMargin) {

        float minValue = 0;
        float maxValue = mStaticInfo.getMinimumFocusDistanceChecked();
        float hyperfocalDistance = 0;
        if (mStaticInfo.areKeysAvailable(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)) {
            hyperfocalDistance = mStaticInfo.getHyperfocalDistanceChecked();
        }

        // Verify lens and focus distance do not change for first repeatStart
        // results.
        for (int i = 0; i < repeatStart; i ++) {
            float marginMin = requestedDistances[i] * (1.0f - errorMargin);
            // HAL may choose to use hyperfocal distance for all distances between [0, hyperfocal].
            float marginMax =
                    Math.max(requestedDistances[i], hyperfocalDistance) * (1.0f + errorMargin);

            mCollector.expectEquals("Lens moves even though focus_distance didn't change",
                    lensStates[i], CaptureResult.LENS_STATE_STATIONARY);
            if (noOvershoot) {
                mCollector.expectInRange("Focus distance in result should be close enough to " +
                        "requested value", resultDistances[i], marginMin, marginMax);
            }
            mCollector.expectInRange("Result focus distance is out of range",
                    resultDistances[i], minValue, maxValue);
        }

        for (int i = repeatStart; i < resultDistances.length-1; i ++) {
            float marginMin = requestedDistances[i] * (1.0f - errorMargin);
            // HAL may choose to use hyperfocal distance for all distances between [0, hyperfocal].
            float marginMax =
                    Math.max(requestedDistances[i], hyperfocalDistance) * (1.0f + errorMargin);
            if (noOvershoot) {
                // Result focus distance shouldn't overshoot the request
                boolean condition;
                if (ascendingOrder) {
                    condition = resultDistances[i] <= marginMax;
               } else {
                    condition = resultDistances[i] >= marginMin;
                }
                mCollector.expectTrue(String.format(
                      "Lens shouldn't move past request focus distance. result " +
                      resultDistances[i] + " vs target of " +
                      (ascendingOrder ? marginMax : marginMin)), condition);
            }

            // Verify monotonically increased focus distance setting
            boolean condition;
            float compareDistance = resultDistances[i+1] - resultDistances[i];
            if (i < resultDistances.length-1-repeatEnd) {
                condition = (ascendingOrder ? compareDistance > 0 : compareDistance < 0);
            } else {
                condition = (ascendingOrder ? compareDistance >= 0 : compareDistance <= 0);
            }
            mCollector.expectTrue(String.format("Adjacent [resultDistances, lens_state] results ["
                  + resultDistances[i] + "," + lensStates[i] + "], [" + resultDistances[i+1] + ","
                  + lensStates[i+1] + "] monotonicity is broken"), condition);
        }

        mCollector.expectTrue(String.format("All values of this array are equal: " +
                resultDistances[0] + " " + resultDistances[resultDistances.length-1]),
                resultDistances[0] != resultDistances[resultDistances.length-1]);

        // Verify lens moved to destination location.
        mCollector.expectInRange("Focus distance " + resultDistances[resultDistances.length-1] +
                " for minFocusDistance should be closed enough to requested value " +
                requestedDistances[requestedDistances.length-1],
                resultDistances[resultDistances.length-1],
                requestedDistances[requestedDistances.length-1] * (1.0f - errorMargin),
                requestedDistances[requestedDistances.length-1] * (1.0f + errorMargin));
    }

    /**
     * Verify edge mode control results for fpsRanges
     */
    private void edgeModesTestByCamera(List<Range<Integer>> fpsRanges) throws Exception {
        Size maxPrevSize = mOrderedPreviewSizes.get(0);
        int[] edgeModes = mStaticInfo.getAvailableEdgeModesChecked();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        for (int mode : edgeModes) {
            requestBuilder.set(CaptureRequest.EDGE_MODE, mode);

            // Test that OFF and FAST mode should not slow down the frame rate.
            if (mode == CaptureRequest.EDGE_MODE_OFF ||
                    mode == CaptureRequest.EDGE_MODE_FAST) {
                verifyFpsNotSlowDown(requestBuilder, NUM_FRAMES_VERIFIED, fpsRanges);
            }

            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
            startPreview(requestBuilder, maxPrevSize, resultListener);
            mSession.setRepeatingRequest(requestBuilder.build(), resultListener, mHandler);
            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            verifyCaptureResultForKey(CaptureResult.EDGE_MODE, mode, resultListener,
                    NUM_FRAMES_VERIFIED);
       }

        stopPreview();
    }

    /**
     * Test color correction controls.
     *
     * <p>Test different color correction modes. For TRANSFORM_MATRIX, only test
     * the unit gain and identity transform.</p>
     */
    private void colorCorrectionTestByCamera() throws Exception {
        CaptureRequest request;
        CaptureResult result;
        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.
        updatePreviewSurface(maxPreviewSz);
        CaptureRequest.Builder manualRequestBuilder = createRequestForPreview();
        CaptureRequest.Builder previewRequestBuilder = createRequestForPreview();
        SimpleCaptureCallback listener = new SimpleCaptureCallback();

        startPreview(previewRequestBuilder, maxPreviewSz, listener);

        // Default preview result should give valid color correction metadata.
        result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        validateColorCorrectionResult(result,
                previewRequestBuilder.get(CaptureRequest.COLOR_CORRECTION_MODE));
        int colorCorrectionMode = CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX;
        // TRANSFORM_MATRIX mode
        // Only test unit gain and identity transform
        List<Integer> availableControlModes = Arrays.asList(
                CameraTestUtils.toObject(mStaticInfo.getAvailableControlModesChecked()));
        List<Integer> availableAwbModes = Arrays.asList(
                CameraTestUtils.toObject(mStaticInfo.getAwbAvailableModesChecked()));
        boolean isManualCCSupported =
                availableControlModes.contains(CaptureRequest.CONTROL_MODE_OFF) ||
                availableAwbModes.contains(CaptureRequest.CONTROL_AWB_MODE_OFF);
        if (isManualCCSupported) {
            if (!availableControlModes.contains(CaptureRequest.CONTROL_MODE_OFF)) {
                // Only manual AWB mode is supported
                manualRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_AUTO);
                manualRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_OFF);
            } else {
                // All 3A manual controls are supported, it doesn't matter what we set for AWB mode.
                manualRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                        CaptureRequest.CONTROL_MODE_OFF);
            }

            RggbChannelVector UNIT_GAIN = new RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f);

            ColorSpaceTransform IDENTITY_TRANSFORM = new ColorSpaceTransform(
                new Rational[] {
                    ONE_R, ZERO_R, ZERO_R,
                    ZERO_R, ONE_R, ZERO_R,
                    ZERO_R, ZERO_R, ONE_R
                });

            manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, colorCorrectionMode);
            manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, UNIT_GAIN);
            manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM, IDENTITY_TRANSFORM);
            request = manualRequestBuilder.build();
            mSession.capture(request, listener, mHandler);
            result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
            RggbChannelVector gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            ColorSpaceTransform transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
            validateColorCorrectionResult(result, colorCorrectionMode);
            mCollector.expectEquals("control mode result/request mismatch",
                    CaptureResult.CONTROL_MODE_OFF, result.get(CaptureResult.CONTROL_MODE));
            mCollector.expectEquals("Color correction gain result/request mismatch",
                    UNIT_GAIN, gains);
            mCollector.expectEquals("Color correction gain result/request mismatch",
                    IDENTITY_TRANSFORM, transform);

        }

        // FAST mode
        colorCorrectionMode = CaptureRequest.COLOR_CORRECTION_MODE_FAST;
        manualRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, colorCorrectionMode);
        request = manualRequestBuilder.build();
        mSession.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        validateColorCorrectionResult(result, colorCorrectionMode);
        mCollector.expectEquals("control mode result/request mismatch",
                CaptureResult.CONTROL_MODE_AUTO, result.get(CaptureResult.CONTROL_MODE));

        // HIGH_QUALITY mode
        colorCorrectionMode = CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY;
        manualRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        manualRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, colorCorrectionMode);
        request = manualRequestBuilder.build();
        mSession.capture(request, listener, mHandler);
        result = listener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
        validateColorCorrectionResult(result, colorCorrectionMode);
        mCollector.expectEquals("control mode result/request mismatch",
                CaptureResult.CONTROL_MODE_AUTO, result.get(CaptureResult.CONTROL_MODE));
    }

    private void validateColorCorrectionResult(CaptureResult result, int colorCorrectionMode) {
        final RggbChannelVector ZERO_GAINS = new RggbChannelVector(0, 0, 0, 0);
        final int TRANSFORM_SIZE = 9;
        Rational[] zeroTransform = new Rational[TRANSFORM_SIZE];
        Arrays.fill(zeroTransform, ZERO_R);
        final ColorSpaceTransform ZERO_TRANSFORM = new ColorSpaceTransform(zeroTransform);

        RggbChannelVector resultGain;
        if ((resultGain = mCollector.expectKeyValueNotNull(result,
                CaptureResult.COLOR_CORRECTION_GAINS)) != null) {
            mCollector.expectKeyValueNotEquals(result,
                    CaptureResult.COLOR_CORRECTION_GAINS, ZERO_GAINS);
        }

        ColorSpaceTransform resultTransform;
        if ((resultTransform = mCollector.expectKeyValueNotNull(result,
                CaptureResult.COLOR_CORRECTION_TRANSFORM)) != null) {
            mCollector.expectKeyValueNotEquals(result,
                    CaptureResult.COLOR_CORRECTION_TRANSFORM, ZERO_TRANSFORM);
        }

        mCollector.expectEquals("color correction mode result/request mismatch",
                colorCorrectionMode, result.get(CaptureResult.COLOR_CORRECTION_MODE));
    }

    /**
     * Test flash mode control by AE mode.
     * <p>
     * Only allow AE mode ON or OFF, because other AE mode could run into conflict with
     * flash manual control. This function expects the camera to already have an active
     * repeating request and be sending results to the listener.
     * </p>
     *
     * @param listener The Capture listener that is used to wait for capture result
     * @param aeMode The AE mode for flash to test with
     */
    private void flashTestByAeMode(SimpleCaptureCallback listener, int aeMode) throws Exception {
        CaptureResult result;
        final int NUM_FLASH_REQUESTS_TESTED = 10;
        CaptureRequest.Builder requestBuilder = createRequestForPreview();

        if (aeMode == CaptureRequest.CONTROL_AE_MODE_ON) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
        } else if (aeMode == CaptureRequest.CONTROL_AE_MODE_OFF) {
            changeExposure(requestBuilder, DEFAULT_EXP_TIME_NS, DEFAULT_SENSITIVITY);
        } else {
            throw new IllegalArgumentException("This test only works when AE mode is ON or OFF");
        }

        mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
        waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

        // For camera that doesn't have flash unit, flash state should always be UNAVAILABLE.
        if (mStaticInfo.getFlashInfoChecked() == false) {
            for (int i = 0; i < NUM_FLASH_REQUESTS_TESTED; i++) {
                result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);
                mCollector.expectEquals("No flash unit available, flash state must be UNAVAILABLE"
                        + "for AE mode " + aeMode, CaptureResult.FLASH_STATE_UNAVAILABLE,
                        result.get(CaptureResult.FLASH_STATE));
            }

            return;
        }

        // Test flash SINGLE mode control. Wait for flash state to be READY first.
        if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
            waitForResultValue(listener, CaptureResult.FLASH_STATE, CaptureResult.FLASH_STATE_READY,
                    NUM_RESULTS_WAIT_TIMEOUT);
        } // else the settings were already waited on earlier

        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
        CaptureRequest flashSinglerequest = requestBuilder.build();

        int flashModeSingleRequests = captureRequestsSynchronized(
                flashSinglerequest, listener, mHandler);
        waitForNumResults(listener, flashModeSingleRequests - 1);
        result = listener.getCaptureResultForRequest(flashSinglerequest, NUM_RESULTS_WAIT_TIMEOUT);
        // Result mode must be SINGLE, state must be FIRED.
        mCollector.expectEquals("Flash mode result must be SINGLE",
                CaptureResult.FLASH_MODE_SINGLE, result.get(CaptureResult.FLASH_MODE));
        mCollector.expectEquals("Flash state result must be FIRED",
                CaptureResult.FLASH_STATE_FIRED, result.get(CaptureResult.FLASH_STATE));

        // Test flash TORCH mode control.
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
        CaptureRequest torchRequest = requestBuilder.build();

        int flashModeTorchRequests = captureRequestsSynchronized(torchRequest,
                NUM_FLASH_REQUESTS_TESTED, listener, mHandler);
        waitForNumResults(listener, flashModeTorchRequests - NUM_FLASH_REQUESTS_TESTED);

        // Verify the results
        TorchSeqState state = TorchSeqState.RAMPING_UP;
        for (int i = 0; i < NUM_FLASH_REQUESTS_TESTED; i++) {
            result = listener.getCaptureResultForRequest(torchRequest,
                    NUM_RESULTS_WAIT_TIMEOUT);
            int flashMode = result.get(CaptureResult.FLASH_MODE);
            int flashState = result.get(CaptureResult.FLASH_STATE);
            // Result mode must be TORCH
            mCollector.expectEquals("Flash mode result " + i + " must be TORCH",
                    CaptureResult.FLASH_MODE_TORCH, result.get(CaptureResult.FLASH_MODE));
            if (state == TorchSeqState.RAMPING_UP &&
                    flashState == CaptureResult.FLASH_STATE_FIRED) {
                state = TorchSeqState.FIRED;
            } else if (state == TorchSeqState.FIRED &&
                    flashState == CaptureResult.FLASH_STATE_PARTIAL) {
                state = TorchSeqState.RAMPING_DOWN;
            }

            if (i == 0 && mStaticInfo.isPerFrameControlSupported()) {
                mCollector.expectTrue(
                        "Per frame control device must enter FIRED state on first torch request",
                        state == TorchSeqState.FIRED);
            }

            if (state == TorchSeqState.FIRED) {
                mCollector.expectEquals("Flash state result " + i + " must be FIRED",
                        CaptureResult.FLASH_STATE_FIRED, result.get(CaptureResult.FLASH_STATE));
            } else {
                mCollector.expectEquals("Flash state result " + i + " must be PARTIAL",
                        CaptureResult.FLASH_STATE_PARTIAL, result.get(CaptureResult.FLASH_STATE));
            }
        }
        mCollector.expectTrue("Torch state FIRED never seen",
                state == TorchSeqState.FIRED || state == TorchSeqState.RAMPING_DOWN);

        // Test flash OFF mode control
        requestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        CaptureRequest flashOffrequest = requestBuilder.build();

        int flashModeOffRequests = captureRequestsSynchronized(flashOffrequest, listener, mHandler);
        waitForNumResults(listener, flashModeOffRequests - 1);
        result = listener.getCaptureResultForRequest(flashOffrequest, NUM_RESULTS_WAIT_TIMEOUT);
        mCollector.expectEquals("Flash mode result must be OFF", CaptureResult.FLASH_MODE_OFF,
                result.get(CaptureResult.FLASH_MODE));
    }

    private void verifyAntiBandingMode(SimpleCaptureCallback listener, int numFramesVerified,
            int mode, boolean isAeManual, long requestExpTime) throws Exception {
        // Skip the first a couple of frames as antibanding may not be fully up yet.
        final int NUM_FRAMES_SKIPPED = 5;
        for (int i = 0; i < NUM_FRAMES_SKIPPED; i++) {
            listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        }

        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            Long resultExpTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            assertNotNull("Exposure time shouldn't be null", resultExpTime);
            Integer flicker = result.get(CaptureResult.STATISTICS_SCENE_FLICKER);
            // Scene flicker result should be always available.
            assertNotNull("Scene flicker must not be null", flicker);
            assertTrue("Scene flicker is invalid", flicker >= STATISTICS_SCENE_FLICKER_NONE &&
                    flicker <= STATISTICS_SCENE_FLICKER_60HZ);

            if (isAeManual) {
                // First, round down not up, second, need close enough.
                validateExposureTime(requestExpTime, resultExpTime);
                return;
            }

            long expectedExpTime = resultExpTime; // Default, no exposure adjustment.
            if (mode == CONTROL_AE_ANTIBANDING_MODE_50HZ) {
                // result exposure time must be adjusted by 50Hz illuminant source.
                expectedExpTime =
                        getAntiFlickeringExposureTime(ANTI_FLICKERING_50HZ, resultExpTime);
            } else if (mode == CONTROL_AE_ANTIBANDING_MODE_60HZ) {
                // result exposure time must be adjusted by 60Hz illuminant source.
                expectedExpTime =
                        getAntiFlickeringExposureTime(ANTI_FLICKERING_60HZ, resultExpTime);
            } else if (mode == CONTROL_AE_ANTIBANDING_MODE_AUTO){
                /**
                 * Use STATISTICS_SCENE_FLICKER to tell the illuminant source
                 * and do the exposure adjustment.
                 */
                expectedExpTime = resultExpTime;
                if (flicker == STATISTICS_SCENE_FLICKER_60HZ) {
                    expectedExpTime =
                            getAntiFlickeringExposureTime(ANTI_FLICKERING_60HZ, resultExpTime);
                } else if (flicker == STATISTICS_SCENE_FLICKER_50HZ) {
                    expectedExpTime =
                            getAntiFlickeringExposureTime(ANTI_FLICKERING_50HZ, resultExpTime);
                }
            }

            if (Math.abs(resultExpTime - expectedExpTime) > EXPOSURE_TIME_ERROR_MARGIN_NS) {
                mCollector.addMessage(String.format("Result exposure time %dns diverges too much"
                        + " from expected exposure time %dns for mode %d when AE is auto",
                        resultExpTime, expectedExpTime, mode));
            }
        }
    }

    private void antiBandingTestByMode(Size size, int mode)
            throws Exception {
        if(VERBOSE) {
            Log.v(TAG, "Anti-banding test for mode " + mode + " for camera " + mCamera.getId());
        }
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        requestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, mode);

        // Test auto AE mode anti-banding behavior
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        startPreview(requestBuilder, size, resultListener);
        waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
        verifyAntiBandingMode(resultListener, NUM_FRAMES_VERIFIED, mode, /*isAeManual*/false,
                IGNORE_REQUESTED_EXPOSURE_TIME_CHECK);

        // Test manual AE mode anti-banding behavior
        // 65ms, must be supported by full capability devices.
        final long TEST_MANUAL_EXP_TIME_NS = 65000000L;
        long manualExpTime = mStaticInfo.getExposureClampToRange(TEST_MANUAL_EXP_TIME_NS);
        changeExposure(requestBuilder, manualExpTime);
        resultListener = new SimpleCaptureCallback();
        startPreview(requestBuilder, size, resultListener);
        waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
        verifyAntiBandingMode(resultListener, NUM_FRAMES_VERIFIED, mode, /*isAeManual*/true,
                manualExpTime);

        stopPreview();
    }

    /**
     * Test the all available AE modes and AE lock.
     * <p>
     * For manual AE mode, test iterates through different sensitivities and
     * exposure times, validate the result exposure time correctness. For
     * CONTROL_AE_MODE_ON_ALWAYS_FLASH mode, the AE lock and flash are tested.
     * For the rest of the AUTO mode, AE lock is tested.
     * </p>
     *
     * @param mode
     */
    private void aeModeAndLockTestByMode(int mode)
            throws Exception {
        switch (mode) {
            case CONTROL_AE_MODE_OFF:
                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    // Test manual exposure control.
                    aeManualControlTest();
                } else {
                    Log.w(TAG,
                            "aeModeAndLockTestByMode - can't test AE mode OFF without " +
                            "manual sensor control");
                }
                break;
            case CONTROL_AE_MODE_ON:
            case CONTROL_AE_MODE_ON_AUTO_FLASH:
            case CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
            case CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                // Test AE lock for above AUTO modes.
                aeAutoModeTestLock(mode);
                break;
            default:
                throw new UnsupportedOperationException("Unhandled AE mode " + mode);
        }
    }

    /**
     * Test AE auto modes.
     * <p>
     * Use single request rather than repeating request to test AE lock per frame control.
     * </p>
     */
    private void aeAutoModeTestLock(int mode) throws Exception {
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        if (mStaticInfo.isAeLockSupported()) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        }
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, mode);
        configurePreviewOutput(requestBuilder);

        final int MAX_NUM_CAPTURES_DURING_LOCK = 5;
        for (int i = 1; i <= MAX_NUM_CAPTURES_DURING_LOCK; i++) {
            autoAeMultipleCapturesThenTestLock(requestBuilder, mode, i);
        }
    }

    /**
     * Issue multiple auto AE captures, then lock AE, validate the AE lock vs.
     * the first capture result after the AE lock. The right AE lock behavior is:
     * When it is locked, it locks to the current exposure value, and all subsequent
     * request with lock ON will have the same exposure value locked.
     */
    private void autoAeMultipleCapturesThenTestLock(
            CaptureRequest.Builder requestBuilder, int aeMode, int numCapturesDuringLock)
            throws Exception {
        if (numCapturesDuringLock < 1) {
            throw new IllegalArgumentException("numCapturesBeforeLock must be no less than 1");
        }
        if (VERBOSE) {
            Log.v(TAG, "Camera " + mCamera.getId() + ": Testing auto AE mode and lock for mode "
                    + aeMode + " with " + numCapturesDuringLock + " captures before lock");
        }

        final int NUM_CAPTURES_BEFORE_LOCK = 2;
        SimpleCaptureCallback listener =  new SimpleCaptureCallback();

        CaptureResult[] resultsDuringLock = new CaptureResult[numCapturesDuringLock];
        boolean canSetAeLock = mStaticInfo.isAeLockSupported();

        // Reset the AE lock to OFF, since we are reusing this builder many times
        if (canSetAeLock) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        }

        // Just send several captures with auto AE, lock off.
        CaptureRequest request = requestBuilder.build();
        for (int i = 0; i < NUM_CAPTURES_BEFORE_LOCK; i++) {
            mSession.capture(request, listener, mHandler);
        }
        waitForNumResults(listener, NUM_CAPTURES_BEFORE_LOCK);

        if (!canSetAeLock) {
            // Without AE lock, the remaining tests items won't work
            return;
        }

        // Then fire several capture to lock the AE.
        requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);

        int requestCount = captureRequestsSynchronized(
                requestBuilder.build(), numCapturesDuringLock, listener, mHandler);

        int[] sensitivities = new int[numCapturesDuringLock];
        long[] expTimes = new long[numCapturesDuringLock];
        Arrays.fill(sensitivities, -1);
        Arrays.fill(expTimes, -1L);

        // Get the AE lock on result and validate the exposure values.
        waitForNumResults(listener, requestCount - numCapturesDuringLock);
        for (int i = 0; i < resultsDuringLock.length; i++) {
            resultsDuringLock[i] = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        }

        for (int i = 0; i < numCapturesDuringLock; i++) {
            mCollector.expectKeyValueEquals(
                    resultsDuringLock[i], CaptureResult.CONTROL_AE_LOCK, true);
        }

        // Can't read manual sensor/exposure settings without manual sensor
        if (mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
            int sensitivityLocked =
                    getValueNotNull(resultsDuringLock[0], CaptureResult.SENSOR_SENSITIVITY);
            long expTimeLocked =
                    getValueNotNull(resultsDuringLock[0], CaptureResult.SENSOR_EXPOSURE_TIME);
            for (int i = 1; i < resultsDuringLock.length; i++) {
                mCollector.expectKeyValueEquals(
                        resultsDuringLock[i], CaptureResult.SENSOR_EXPOSURE_TIME, expTimeLocked);
                mCollector.expectKeyValueEquals(
                        resultsDuringLock[i], CaptureResult.SENSOR_SENSITIVITY, sensitivityLocked);
            }
        }
    }

    /**
     * Iterate through exposure times and sensitivities for manual AE control.
     * <p>
     * Use single request rather than repeating request to test manual exposure
     * value change per frame control.
     * </p>
     */
    private void aeManualControlTest()
            throws Exception {
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        configurePreviewOutput(requestBuilder);

        // Warm up pipeline for more accurate timing
        SimpleCaptureCallback warmupListener =  new SimpleCaptureCallback();
        mSession.setRepeatingRequest(requestBuilder.build(), warmupListener, mHandler);
        warmupListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);

        // Do manual captures
        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
        SimpleCaptureCallback listener =  new SimpleCaptureCallback();

        long[] expTimesNs = getExposureTimeTestValues();
        int[] sensitivities = getSensitivityTestValues();
        // Submit single request at a time, then verify the result.
        for (int i = 0; i < expTimesNs.length; i++) {
            for (int j = 0; j < sensitivities.length; j++) {
                if (VERBOSE) {
                    Log.v(TAG, "Camera " + mCamera.getId() + ": Testing sensitivity "
                            + sensitivities[j] + ", exposure time " + expTimesNs[i] + "ns");
                }

                changeExposure(requestBuilder, expTimesNs[i], sensitivities[j]);
                mSession.capture(requestBuilder.build(), listener, mHandler);

                // make sure timeout is long enough for long exposure time - add a 2x safety margin
                // to exposure time
                long timeoutMs = WAIT_FOR_RESULT_TIMEOUT_MS + 2 * expTimesNs[i] / 1000000;
                CaptureResult result = listener.getCaptureResult(timeoutMs);
                long resultExpTimeNs = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                int resultSensitivity = getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);
                validateExposureTime(expTimesNs[i], resultExpTimeNs);
                validateSensitivity(sensitivities[j], resultSensitivity);
                validateFrameDurationForCapture(result);
            }
        }
        mSession.stopRepeating();

        // TODO: Add another case to test where we can submit all requests, then wait for
        // results, which will hide the pipeline latency. this is not only faster, but also
        // test high speed per frame control and synchronization.
    }


    /**
     * Verify black level lock control.
     */
    private void verifyBlackLevelLockResults(SimpleCaptureCallback listener, int numFramesVerified,
            int maxLockOffCnt) throws Exception {
        int noLockCnt = 0;
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            Boolean blackLevelLock = result.get(CaptureResult.BLACK_LEVEL_LOCK);
            assertNotNull("Black level lock result shouldn't be null", blackLevelLock);

            // Count the lock == false result, which could possibly occur at most once.
            if (blackLevelLock == false) {
                noLockCnt++;
            }

            if(VERBOSE) {
                Log.v(TAG, "Black level lock result: " + blackLevelLock);
            }
        }
        assertTrue("Black level lock OFF occurs " + noLockCnt + " times,  expect at most "
                + maxLockOffCnt + " for camera " + mCamera.getId(), noLockCnt <= maxLockOffCnt);
    }

    /**
     * Verify shading map for different shading modes.
     */
    private void verifyShadingMap(SimpleCaptureCallback listener, int numFramesVerified,
            int shadingMode) throws Exception {

        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Shading mode result doesn't match request",
                    shadingMode, result.get(CaptureResult.SHADING_MODE));
            LensShadingMap mapObj = result.get(
                    CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
            assertNotNull("Map object must not be null", mapObj);
            int numElementsInMap = mapObj.getGainFactorCount();
            float[] map = new float[numElementsInMap];
            mapObj.copyGainFactors(map, /*offset*/0);
            assertNotNull("Map must not be null", map);
            assertFalse(String.format(
                    "Map size %d should be less than %d", numElementsInMap, MAX_SHADING_MAP_SIZE),
                    numElementsInMap >= MAX_SHADING_MAP_SIZE);
            assertFalse(String.format("Map size %d should be no less than %d", numElementsInMap,
                    MIN_SHADING_MAP_SIZE), numElementsInMap < MIN_SHADING_MAP_SIZE);

            if (shadingMode == CaptureRequest.SHADING_MODE_FAST ||
                    shadingMode == CaptureRequest.SHADING_MODE_HIGH_QUALITY) {
                // shading mode is FAST or HIGH_QUALITY, expect to receive a map with all
                // elements >= 1.0f

                int badValueCnt = 0;
                // Detect the bad values of the map data.
                for (int j = 0; j < numElementsInMap; j++) {
                    if (Float.isNaN(map[j]) || map[j] < 1.0f) {
                        badValueCnt++;
                    }
                }
                assertEquals("Number of value in the map is " + badValueCnt + " out of "
                        + numElementsInMap, /*expected*/0, /*actual*/badValueCnt);
            } else if (shadingMode == CaptureRequest.SHADING_MODE_OFF) {
                float[] unityMap = new float[numElementsInMap];
                Arrays.fill(unityMap, 1.0f);
                // shading mode is OFF, expect to receive a unity map.
                assertTrue("Result map " + Arrays.toString(map) + " must be an unity map",
                        Arrays.equals(unityMap, map));
            }
        }
    }

    /**
     * Test face detection for a camera.
     */
    private void faceDetectionTestByCamera() throws Exception {
        int[] faceDetectModes = mStaticInfo.getAvailableFaceDetectModesChecked();

        SimpleCaptureCallback listener;
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.
        for (int mode : faceDetectModes) {
            requestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mode);
            if (VERBOSE) {
                Log.v(TAG, "Start testing face detection mode " + mode);
            }

            // Create a new listener for each run to avoid the results from one run spill
            // into another run.
            listener = new SimpleCaptureCallback();
            startPreview(requestBuilder, maxPreviewSz, listener);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            verifyFaceDetectionResults(listener, NUM_FACE_DETECTION_FRAMES_VERIFIED, mode);
        }

        stopPreview();
    }

    /**
     * Verify face detection results for different face detection modes.
     *
     * @param listener The listener to get capture result
     * @param numFramesVerified Number of results to be verified
     * @param faceDetectionMode Face detection mode to be verified against
     */
    private void verifyFaceDetectionResults(SimpleCaptureCallback listener, int numFramesVerified,
            int faceDetectionMode) {
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Result face detection mode should match the request",
                    faceDetectionMode, result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE));

            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            List<Integer> faceIds = new ArrayList<Integer>(faces.length);
            List<Integer> faceScores = new ArrayList<Integer>(faces.length);
            if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF) {
                mCollector.expectEquals("Number of detection faces should always 0 for OFF mode",
                        0, faces.length);
            } else if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                for (Face face : faces) {
                    mCollector.expectNotNull("Face rectangle shouldn't be null", face.getBounds());
                    faceScores.add(face.getScore());
                    mCollector.expectTrue("Face id is expected to be -1 for SIMPLE mode",
                            face.getId() == Face.ID_UNSUPPORTED);
                }
            } else if (faceDetectionMode == CaptureResult.STATISTICS_FACE_DETECT_MODE_FULL) {
                if (VERBOSE) {
                    Log.v(TAG, "Number of faces detected: " + faces.length);
                }

                for (Face face : faces) {
                    Rect faceBound;
                    boolean faceRectAvailable =  mCollector.expectTrue("Face rectangle "
                            + "shouldn't be null", face.getBounds() != null);
                    if (!faceRectAvailable) {
                        continue;
                    }
                    faceBound = face.getBounds();

                    faceScores.add(face.getScore());
                    faceIds.add(face.getId());

                    mCollector.expectTrue("Face id is shouldn't be -1 for FULL mode",
                            face.getId() != Face.ID_UNSUPPORTED);
                    boolean leftEyeAvailable =
                            mCollector.expectTrue("Left eye position shouldn't be null",
                                    face.getLeftEyePosition() != null);
                    boolean rightEyeAvailable =
                            mCollector.expectTrue("Right eye position shouldn't be null",
                                    face.getRightEyePosition() != null);
                    boolean mouthAvailable =
                            mCollector.expectTrue("Mouth position shouldn't be null",
                            face.getMouthPosition() != null);
                    // Eyes/mouth position should be inside of the face rect.
                    if (leftEyeAvailable) {
                        Point leftEye = face.getLeftEyePosition();
                        mCollector.expectTrue("Left eye " + leftEye + "should be"
                                + "inside of face rect " + faceBound,
                                faceBound.contains(leftEye.x, leftEye.y));
                    }
                    if (rightEyeAvailable) {
                        Point rightEye = face.getRightEyePosition();
                        mCollector.expectTrue("Right eye " + rightEye + "should be"
                                + "inside of face rect " + faceBound,
                                faceBound.contains(rightEye.x, rightEye.y));
                    }
                    if (mouthAvailable) {
                        Point mouth = face.getMouthPosition();
                        mCollector.expectTrue("Mouth " + mouth +  " should be inside of"
                                + " face rect " + faceBound,
                                faceBound.contains(mouth.x, mouth.y));
                    }
                }
            }
            mCollector.expectValuesInRange("Face scores are invalid", faceScores,
                    Face.SCORE_MIN, Face.SCORE_MAX);
            mCollector.expectValuesUnique("Face ids are invalid", faceIds);
        }
    }

    /**
     * Test tone map mode and result by camera
     */
    private void toneMapTestByCamera() throws Exception {
        if (!mStaticInfo.isManualToneMapSupported()) {
            return;
        }

        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        int[] toneMapModes = mStaticInfo.getAvailableToneMapModesChecked();
        for (int mode : toneMapModes) {
            if (VERBOSE) {
                Log.v(TAG, "Testing tonemap mode " + mode);
            }

            requestBuilder.set(CaptureRequest.TONEMAP_MODE, mode);
            switch (mode) {
                case CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE:
                    TonemapCurve toneCurve = new TonemapCurve(TONEMAP_CURVE_LINEAR,
                            TONEMAP_CURVE_LINEAR, TONEMAP_CURVE_LINEAR);
                    requestBuilder.set(CaptureRequest.TONEMAP_CURVE, toneCurve);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);

                    toneCurve = new TonemapCurve(TONEMAP_CURVE_SRGB,
                            TONEMAP_CURVE_SRGB, TONEMAP_CURVE_SRGB);
                    requestBuilder.set(CaptureRequest.TONEMAP_CURVE, toneCurve);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    break;
                case CaptureRequest.TONEMAP_MODE_GAMMA_VALUE:
                    requestBuilder.set(CaptureRequest.TONEMAP_GAMMA, 1.0f);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    requestBuilder.set(CaptureRequest.TONEMAP_GAMMA, 2.2f);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    requestBuilder.set(CaptureRequest.TONEMAP_GAMMA, 5.0f);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    break;
                case CaptureRequest.TONEMAP_MODE_PRESET_CURVE:
                    requestBuilder.set(CaptureRequest.TONEMAP_PRESET_CURVE,
                            CaptureRequest.TONEMAP_PRESET_CURVE_REC709);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    requestBuilder.set(CaptureRequest.TONEMAP_PRESET_CURVE,
                            CaptureRequest.TONEMAP_PRESET_CURVE_SRGB);
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    break;
                default:
                    testToneMapMode(NUM_FRAMES_VERIFIED, requestBuilder);
                    break;
            }
        }


    }

    /**
     * Test tonemap mode with speficied request settings
     *
     * @param numFramesVerified Number of results to be verified
     * @param requestBuilder the request builder of settings to be tested
     */
    private void testToneMapMode (int numFramesVerified,
            CaptureRequest.Builder requestBuilder)  throws Exception  {
        final int MIN_TONEMAP_CURVE_POINTS = 2;
        final Float ZERO = new Float(0);
        final Float ONE = new Float(1.0f);

        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        int tonemapMode = requestBuilder.get(CaptureRequest.TONEMAP_MODE);
        Size maxPreviewSz = mOrderedPreviewSizes.get(0); // Max preview size.
        startPreview(requestBuilder, maxPreviewSz, listener);
        waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

        int maxCurvePoints = mStaticInfo.getMaxTonemapCurvePointChecked();
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            mCollector.expectEquals("Capture result tonemap mode should match request", tonemapMode,
                    result.get(CaptureResult.TONEMAP_MODE));
            TonemapCurve tc = getValueNotNull(result, CaptureResult.TONEMAP_CURVE);
            int pointCount = tc.getPointCount(TonemapCurve.CHANNEL_RED);
            float[] mapRed = new float[pointCount * TonemapCurve.POINT_SIZE];
            pointCount = tc.getPointCount(TonemapCurve.CHANNEL_GREEN);
            float[] mapGreen = new float[pointCount * TonemapCurve.POINT_SIZE];
            pointCount = tc.getPointCount(TonemapCurve.CHANNEL_BLUE);
            float[] mapBlue = new float[pointCount * TonemapCurve.POINT_SIZE];
            tc.copyColorCurve(TonemapCurve.CHANNEL_RED, mapRed, 0);
            tc.copyColorCurve(TonemapCurve.CHANNEL_GREEN, mapGreen, 0);
            tc.copyColorCurve(TonemapCurve.CHANNEL_BLUE, mapBlue, 0);
            if (tonemapMode == CaptureResult.TONEMAP_MODE_CONTRAST_CURVE) {
                /**
                 * TODO: need figure out a good way to measure the difference
                 * between request and result, as they may have different array
                 * size.
                 */
            } else if (tonemapMode == CaptureResult.TONEMAP_MODE_GAMMA_VALUE) {
                mCollector.expectEquals("Capture result gamma value should match request",
                        requestBuilder.get(CaptureRequest.TONEMAP_GAMMA),
                        result.get(CaptureResult.TONEMAP_GAMMA));
            } else if (tonemapMode == CaptureResult.TONEMAP_MODE_PRESET_CURVE) {
                mCollector.expectEquals("Capture result preset curve should match request",
                        requestBuilder.get(CaptureRequest.TONEMAP_PRESET_CURVE),
                        result.get(CaptureResult.TONEMAP_PRESET_CURVE));
            }

            // Tonemap curve result availability and basic sanity check for all modes.
            mCollector.expectValuesInRange("Tonemap curve red values are out of range",
                    CameraTestUtils.toObject(mapRed), /*min*/ZERO, /*max*/ONE);
            mCollector.expectInRange("Tonemap curve red length is out of range",
                    mapRed.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
            mCollector.expectValuesInRange("Tonemap curve green values are out of range",
                    CameraTestUtils.toObject(mapGreen), /*min*/ZERO, /*max*/ONE);
            mCollector.expectInRange("Tonemap curve green length is out of range",
                    mapGreen.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
            mCollector.expectValuesInRange("Tonemap curve blue values are out of range",
                    CameraTestUtils.toObject(mapBlue), /*min*/ZERO, /*max*/ONE);
            mCollector.expectInRange("Tonemap curve blue length is out of range",
                    mapBlue.length, MIN_TONEMAP_CURVE_POINTS, maxCurvePoints * 2);
        }
        stopPreview();
    }

    /**
     * Test awb mode control.
     * <p>
     * Test each supported AWB mode, verify the AWB mode in capture result
     * matches request. When AWB is locked, the color correction gains and
     * transform should remain unchanged.
     * </p>
     */
    private void awbModeAndLockTestByCamera() throws Exception {
        int[] awbModes = mStaticInfo.getAwbAvailableModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        boolean canSetAwbLock = mStaticInfo.isAwbLockSupported();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        startPreview(requestBuilder, maxPreviewSize, /*listener*/null);

        for (int mode : awbModes) {
            SimpleCaptureCallback listener;
            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mode);
            listener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            // Verify AWB mode in capture result.
            verifyCaptureResultForKey(CaptureResult.CONTROL_AWB_MODE, mode, listener,
                    NUM_FRAMES_VERIFIED);

            if (mode == CameraMetadata.CONTROL_AWB_MODE_AUTO && canSetAwbLock) {
                // Verify color correction transform and gains stay unchanged after a lock.
                requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                listener = new SimpleCaptureCallback();
                mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
                waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

                if (mStaticInfo.areKeysAvailable(CaptureResult.CONTROL_AWB_STATE)) {
                    waitForResultValue(listener, CaptureResult.CONTROL_AWB_STATE,
                            CaptureResult.CONTROL_AWB_STATE_LOCKED, NUM_RESULTS_WAIT_TIMEOUT);
                }

            }
            // Don't verify auto mode result if AWB lock is not supported
            if (mode != CameraMetadata.CONTROL_AWB_MODE_AUTO || canSetAwbLock) {
                verifyAwbCaptureResultUnchanged(listener, NUM_FRAMES_VERIFIED);
            }
        }
    }

    private void verifyAwbCaptureResultUnchanged(SimpleCaptureCallback listener,
            int numFramesVerified) {
        // Skip check if cc gains/transform/mode are not available
        if (!mStaticInfo.areKeysAvailable(
                CaptureResult.COLOR_CORRECTION_GAINS,
                CaptureResult.COLOR_CORRECTION_TRANSFORM,
                CaptureResult.COLOR_CORRECTION_MODE)) {
            return;
        }

        CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        RggbChannelVector lockedGains =
                getValueNotNull(result, CaptureResult.COLOR_CORRECTION_GAINS);
        ColorSpaceTransform lockedTransform =
                getValueNotNull(result, CaptureResult.COLOR_CORRECTION_TRANSFORM);

        for (int i = 0; i < numFramesVerified; i++) {
            result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            // Color correction mode check is skipped here, as it is checked in colorCorrectionTest.
            validateColorCorrectionResult(result, result.get(CaptureResult.COLOR_CORRECTION_MODE));

            RggbChannelVector gains = getValueNotNull(result, CaptureResult.COLOR_CORRECTION_GAINS);
            ColorSpaceTransform transform =
                    getValueNotNull(result, CaptureResult.COLOR_CORRECTION_TRANSFORM);
            mCollector.expectEquals("Color correction gains should remain unchanged after awb lock",
                    lockedGains, gains);
            mCollector.expectEquals("Color correction transform should remain unchanged after"
                    + " awb lock", lockedTransform, transform);
        }
    }

    /**
     * Test AF mode control.
     * <p>
     * Test all supported AF modes, verify the AF mode in capture result matches
     * request. When AF mode is one of the CONTROL_AF_MODE_CONTINUOUS_* mode,
     * verify if the AF can converge to PASSIVE_FOCUSED or PASSIVE_UNFOCUSED
     * state within certain amount of frames.
     * </p>
     */
    private void afModeTestByCamera() throws Exception {
        int[] afModes = mStaticInfo.getAfAvailableModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        startPreview(requestBuilder, maxPreviewSize, /*listener*/null);

        for (int mode : afModes) {
            SimpleCaptureCallback listener;
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, mode);
            listener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            // Verify AF mode in capture result.
            verifyCaptureResultForKey(CaptureResult.CONTROL_AF_MODE, mode, listener,
                    NUM_FRAMES_VERIFIED);

            // Verify AF can finish a scan for CONTROL_AF_MODE_CONTINUOUS_* modes.
            // In LEGACY mode, a transition to one of the continuous AF modes does not necessarily
            // result in a passive AF call if the camera has already been focused, and the scene has
            // not changed enough to trigger an AF pass.  Skip this constraint for LEGACY.
            if (mStaticInfo.isHardwareLevelAtLeastLimited() &&
                    (mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE ||
                    mode == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
                List<Integer> afStateList = new ArrayList<Integer>();
                afStateList.add(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED);
                afStateList.add(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED);
                waitForAnyResultValue(listener, CaptureResult.CONTROL_AF_STATE, afStateList,
                        NUM_RESULTS_WAIT_TIMEOUT);
            }
        }
    }

    /**
     * Test video and optical stabilizations if they are supported by a given camera.
     */
    private void stabilizationTestByCamera() throws Exception {
        // video stabilization test.
        List<Key<?>> keys = mStaticInfo.getCharacteristics().getKeys();

        Integer[] videoStabModes = (keys.contains(CameraCharacteristics.
                CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)) ?
                CameraTestUtils.toObject(mStaticInfo.getAvailableVideoStabilizationModesChecked()) :
                    new Integer[0];
        int[] opticalStabModes = (keys.contains(
                CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)) ?
                mStaticInfo.getAvailableOpticalStabilizationChecked() : new int[0];

        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        startPreview(requestBuilder, maxPreviewSize, listener);

        for (Integer mode : videoStabModes) {
            listener = new SimpleCaptureCallback();
            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, mode);
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            // Video stabilization could return any modes.
            verifyAnyCaptureResultForKey(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE,
                    videoStabModes, listener, NUM_FRAMES_VERIFIED);
        }

        for (int mode : opticalStabModes) {
            listener = new SimpleCaptureCallback();
            requestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, mode);
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            verifyCaptureResultForKey(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE, mode,
                    listener, NUM_FRAMES_VERIFIED);
        }

        stopPreview();
    }

    private void digitalZoomTestByCamera(Size previewSize) throws Exception {
        final int ZOOM_STEPS = 15;
        final PointF[] TEST_ZOOM_CENTERS;
        final float maxZoom = mStaticInfo.getAvailableMaxDigitalZoomChecked();
        final float ZOOM_ERROR_MARGIN = 0.01f;
        if (Math.abs(maxZoom - 1.0f) < ZOOM_ERROR_MARGIN) {
            // It doesn't make much sense to test the zoom if the device effectively supports
            // no zoom.
            return;
        }

        final int croppingType = mStaticInfo.getScalerCroppingTypeChecked();
        if (croppingType == CameraCharacteristics.SCALER_CROPPING_TYPE_FREEFORM) {
            // Set the four corners in a way that the minimally allowed zoom factor is 2x.
            float normalizedLeft = 0.25f;
            float normalizedTop = 0.25f;
            float normalizedRight = 0.75f;
            float normalizedBottom = 0.75f;
            // If the max supported zoom is too small, make sure we at least test the max
            // Zoom is tested for the four corners.
            if (maxZoom < 2.0f) {
                normalizedLeft = 0.5f / maxZoom;
                normalizedTop = 0.5f / maxZoom;
                normalizedRight = 1.0f - normalizedLeft;
                normalizedBottom = 1.0f - normalizedTop;
            }
            TEST_ZOOM_CENTERS = new PointF[] {
                new PointF(0.5f, 0.5f),   // Center point
                new PointF(normalizedLeft, normalizedTop),     // top left corner zoom
                new PointF(normalizedRight, normalizedTop),    // top right corner zoom
                new PointF(normalizedLeft, normalizedBottom),  // bottom left corner zoom
                new PointF(normalizedRight, normalizedBottom), // bottom right corner zoom
            };

            if (VERBOSE) {
                Log.v(TAG, "Testing zoom with CROPPING_TYPE = FREEFORM");
            }
        } else {
            // CENTER_ONLY
            TEST_ZOOM_CENTERS = new PointF[] {
                    new PointF(0.5f, 0.5f),   // Center point
            };

            if (VERBOSE) {
                Log.v(TAG, "Testing zoom with CROPPING_TYPE = CENTER_ONLY");
            }
        }

        final Rect activeArraySize = mStaticInfo.getActiveArraySizeChecked();
        Rect[] cropRegions = new Rect[ZOOM_STEPS];
        MeteringRectangle[][] expectRegions = new MeteringRectangle[ZOOM_STEPS][];
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback listener = new SimpleCaptureCallback();

        updatePreviewSurface(previewSize);
        configurePreviewOutput(requestBuilder);

        CaptureRequest[] requests = new CaptureRequest[ZOOM_STEPS];

        // Set algorithm regions to full active region
        // TODO: test more different 3A regions
        final MeteringRectangle[] defaultMeteringRect = new MeteringRectangle[] {
                new MeteringRectangle (
                        /*x*/0, /*y*/0, activeArraySize.width(), activeArraySize.height(),
                        /*meteringWeight*/1)
        };

        for (int algo = 0; algo < NUM_ALGORITHMS; algo++) {
            update3aRegion(requestBuilder, algo,  defaultMeteringRect);
        }

        final int CAPTURE_SUBMIT_REPEAT;
        {
            int maxLatency = mStaticInfo.getSyncMaxLatency();
            if (maxLatency == CameraMetadata.SYNC_MAX_LATENCY_UNKNOWN) {
                CAPTURE_SUBMIT_REPEAT = NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY + 1;
            } else {
                CAPTURE_SUBMIT_REPEAT = maxLatency + 1;
            }
        }

        if (VERBOSE) {
            Log.v(TAG, "Testing zoom with CAPTURE_SUBMIT_REPEAT = " + CAPTURE_SUBMIT_REPEAT);
        }

        for (PointF center : TEST_ZOOM_CENTERS) {
            Rect previousCrop = null;

            for (int i = 0; i < ZOOM_STEPS; i++) {
                /*
                 * Submit capture request
                 */
                float zoomFactor = (float) (1.0f + (maxZoom - 1.0) * i / ZOOM_STEPS);
                cropRegions[i] = getCropRegionForZoom(zoomFactor, center, maxZoom, activeArraySize);
                if (VERBOSE) {
                    Log.v(TAG, "Testing Zoom for factor " + zoomFactor + " and center " +
                            center + " The cropRegion is " + cropRegions[i] +
                            " Preview size is " + previewSize);
                }
                requestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegions[i]);
                requests[i] = requestBuilder.build();
                for (int j = 0; j < CAPTURE_SUBMIT_REPEAT; ++j) {
                    if (VERBOSE) {
                        Log.v(TAG, "submit crop region " + cropRegions[i]);
                    }
                    mSession.capture(requests[i], listener, mHandler);
                }

                /*
                 * Validate capture result
                 */
                waitForNumResults(listener, CAPTURE_SUBMIT_REPEAT - 1); // Drop first few frames
                CaptureResult result = listener.getCaptureResultForRequest(
                        requests[i], NUM_RESULTS_WAIT_TIMEOUT);
                Rect cropRegion = getValueNotNull(result, CaptureResult.SCALER_CROP_REGION);

                /*
                 * Validate resulting crop regions
                 */
                if (previousCrop != null) {
                    Rect currentCrop = cropRegion;
                    mCollector.expectTrue(String.format(
                            "Crop region should shrink or stay the same " +
                                    "(previous = %s, current = %s)",
                                    previousCrop, currentCrop),
                            previousCrop.equals(currentCrop) ||
                                (previousCrop.width() > currentCrop.width() &&
                                 previousCrop.height() > currentCrop.height()));
                }

                if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
                    mCollector.expectRectsAreSimilar(
                            "Request and result crop region should be similar",
                            cropRegions[i], cropRegion, CROP_REGION_ERROR_PERCENT_DELTA);
                }

                if (croppingType == SCALER_CROPPING_TYPE_CENTER_ONLY) {
                    mCollector.expectRectCentered(
                            "Result crop region should be centered inside the active array",
                            new Size(activeArraySize.width(), activeArraySize.height()),
                            cropRegion, CROP_REGION_ERROR_PERCENT_CENTERED);
                }

                /*
                 * Validate resulting metering regions
                 */

                // Use the actual reported crop region to calculate the resulting metering region
                expectRegions[i] = getExpectedOutputRegion(
                        /*requestRegion*/defaultMeteringRect,
                        /*cropRect*/     cropRegion);

                // Verify Output 3A region is intersection of input 3A region and crop region
                for (int algo = 0; algo < NUM_ALGORITHMS; algo++) {
                    validate3aRegion(result, algo, expectRegions[i]);
                }

                previousCrop = cropRegion;
            }

            if (maxZoom > 1.0f) {
                mCollector.expectTrue(
                        String.format("Most zoomed-in crop region should be smaller" +
                                        "than active array w/h" +
                                        "(last crop = %s, active array = %s)",
                                        previousCrop, activeArraySize),
                            (previousCrop.width() < activeArraySize.width() &&
                             previousCrop.height() < activeArraySize.height()));
            }
        }
    }

    private void digitalZoomPreviewCombinationTestByCamera() throws Exception {
        final double ASPECT_RATIO_THRESHOLD = 0.001;
        List<Double> aspectRatiosTested = new ArrayList<Double>();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        aspectRatiosTested.add((double)(maxPreviewSize.getWidth()) / maxPreviewSize.getHeight());

        for (Size size : mOrderedPreviewSizes) {
            // Max preview size was already tested in testDigitalZoom test. skip it.
            if (size.equals(maxPreviewSize)) {
                continue;
            }

            // Only test the largest size for each aspect ratio.
            double aspectRatio = (double)(size.getWidth()) / size.getHeight();
            if (isAspectRatioContained(aspectRatiosTested, aspectRatio, ASPECT_RATIO_THRESHOLD)) {
                continue;
            }

            if (VERBOSE) {
                Log.v(TAG, "Test preview size " + size.toString() + " digital zoom");
            }

            aspectRatiosTested.add(aspectRatio);
            digitalZoomTestByCamera(size);
        }
    }

    private static boolean isAspectRatioContained(List<Double> aspectRatioList,
            double aspectRatio, double delta) {
        for (Double ratio : aspectRatioList) {
            if (Math.abs(ratio - aspectRatio) < delta) {
                return true;
            }
        }

        return false;
    }

    private void sceneModeTestByCamera() throws Exception {
        int[] sceneModes = mStaticInfo.getAvailableSceneModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
        startPreview(requestBuilder, maxPreviewSize, listener);

        for(int mode : sceneModes) {
            requestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, mode);
            listener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            verifyCaptureResultForKey(CaptureResult.CONTROL_SCENE_MODE,
                    mode, listener, NUM_FRAMES_VERIFIED);
            // This also serves as purpose of showing preview for NUM_FRAMES_VERIFIED
            verifyCaptureResultForKey(CaptureResult.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_USE_SCENE_MODE, listener, NUM_FRAMES_VERIFIED);
        }
    }

    private void effectModeTestByCamera() throws Exception {
        int[] effectModes = mStaticInfo.getAvailableEffectModesChecked();
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        startPreview(requestBuilder, maxPreviewSize, listener);

        for(int mode : effectModes) {
            requestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, mode);
            listener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(requestBuilder.build(), listener, mHandler);
            waitForSettingsApplied(listener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            verifyCaptureResultForKey(CaptureResult.CONTROL_EFFECT_MODE,
                    mode, listener, NUM_FRAMES_VERIFIED);
            // This also serves as purpose of showing preview for NUM_FRAMES_VERIFIED
            verifyCaptureResultForKey(CaptureResult.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_AUTO, listener, NUM_FRAMES_VERIFIED);
        }
    }

    //----------------------------------------------------------------
    //---------Below are common functions for all tests.--------------
    //----------------------------------------------------------------

    /**
     * Enable exposure manual control and change exposure and sensitivity and
     * clamp the value into the supported range.
     */
    private void changeExposure(CaptureRequest.Builder requestBuilder,
            long expTime, int sensitivity) {
        // Check if the max analog sensitivity is available and no larger than max sensitivity.
        // The max analog sensitivity is not actually used here. This is only an extra sanity check.
        mStaticInfo.getMaxAnalogSensitivityChecked();

        expTime = mStaticInfo.getExposureClampToRange(expTime);
        sensitivity = mStaticInfo.getSensitivityClampToRange(sensitivity);

        requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF);
        requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTime);
        requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
    }
    /**
     * Enable exposure manual control and change exposure time and
     * clamp the value into the supported range.
     *
     * <p>The sensitivity is set to default value.</p>
     */
    private void changeExposure(CaptureRequest.Builder requestBuilder, long expTime) {
        changeExposure(requestBuilder, expTime, DEFAULT_SENSITIVITY);
    }

    /**
     * Get the exposure time array that contains multiple exposure time steps in
     * the exposure time range, in nanoseconds.
     */
    private long[] getExposureTimeTestValues() {
        long[] testValues = new long[DEFAULT_NUM_EXPOSURE_TIME_STEPS + 1];
        long maxExpTime = mStaticInfo.getExposureMaximumOrDefault(DEFAULT_EXP_TIME_NS);
        long minExpTime = mStaticInfo.getExposureMinimumOrDefault(DEFAULT_EXP_TIME_NS);

        long range = maxExpTime - minExpTime;
        double stepSize = range / (double)DEFAULT_NUM_EXPOSURE_TIME_STEPS;
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = maxExpTime - (long)(stepSize * i);
            testValues[i] = mStaticInfo.getExposureClampToRange(testValues[i]);
        }

        return testValues;
    }

    /**
     * Generate test focus distances in range of [0, minFocusDistance] in increasing order.
     *
     * @param repeatMin number of times minValue will be repeated.
     * @param repeatMax number of times maxValue will be repeated.
     */
    private float[] getFocusDistanceTestValuesInOrder(int repeatMin, int repeatMax) {
        int totalCount = NUM_TEST_FOCUS_DISTANCES + 1 + repeatMin + repeatMax;
        float[] testValues = new float[totalCount];
        float minValue = 0;
        float maxValue = mStaticInfo.getMinimumFocusDistanceChecked();

        float range = maxValue - minValue;
        float stepSize = range / NUM_TEST_FOCUS_DISTANCES;

        for (int i = 0; i < repeatMin; i++) {
            testValues[i] = minValue;
        }
        for (int i = 0; i <= NUM_TEST_FOCUS_DISTANCES; i++) {
            testValues[repeatMin+i] = minValue + stepSize * i;
        }
        for (int i = 0; i < repeatMax; i++) {
            testValues[repeatMin+NUM_TEST_FOCUS_DISTANCES+1+i] =
                    maxValue;
        }

        return testValues;
    }

    /**
     * Get the sensitivity array that contains multiple sensitivity steps in the
     * sensitivity range.
     * <p>
     * Sensitivity number of test values is determined by
     * {@value #DEFAULT_SENSITIVITY_STEP_SIZE} and sensitivity range, and
     * bounded by {@value #DEFAULT_NUM_SENSITIVITY_STEPS}.
     * </p>
     */
    private int[] getSensitivityTestValues() {
        int maxSensitivity = mStaticInfo.getSensitivityMaximumOrDefault(
                DEFAULT_SENSITIVITY);
        int minSensitivity = mStaticInfo.getSensitivityMinimumOrDefault(
                DEFAULT_SENSITIVITY);

        int range = maxSensitivity - minSensitivity;
        int stepSize = DEFAULT_SENSITIVITY_STEP_SIZE;
        int numSteps = range / stepSize;
        // Bound the test steps to avoid supper long test.
        if (numSteps > DEFAULT_NUM_SENSITIVITY_STEPS) {
            numSteps = DEFAULT_NUM_SENSITIVITY_STEPS;
            stepSize = range / numSteps;
        }
        int[] testValues = new int[numSteps + 1];
        for (int i = 0; i < testValues.length; i++) {
            testValues[i] = maxSensitivity - stepSize * i;
            testValues[i] = mStaticInfo.getSensitivityClampToRange(testValues[i]);
        }

        return testValues;
    }

    /**
     * Validate the AE manual control exposure time.
     *
     * <p>Exposure should be close enough, and only round down if they are not equal.</p>
     *
     * @param request Request exposure time
     * @param result Result exposure time
     */
    private void validateExposureTime(long request, long result) {
        long expTimeDelta = request - result;
        long expTimeErrorMargin = (long)(Math.max(EXPOSURE_TIME_ERROR_MARGIN_NS, request
                * EXPOSURE_TIME_ERROR_MARGIN_RATE));
        // First, round down not up, second, need close enough.
        mCollector.expectTrue("Exposture time is invalid for AE manaul control test, request: "
                + request + " result: " + result,
                expTimeDelta < expTimeErrorMargin && expTimeDelta >= 0);
    }

    /**
     * Validate AE manual control sensitivity.
     *
     * @param request Request sensitivity
     * @param result Result sensitivity
     */
    private void validateSensitivity(int request, int result) {
        float sensitivityDelta = request - result;
        float sensitivityErrorMargin = request * SENSITIVITY_ERROR_MARGIN_RATE;
        // First, round down not up, second, need close enough.
        mCollector.expectTrue("Sensitivity is invalid for AE manaul control test, request: "
                + request + " result: " + result,
                sensitivityDelta < sensitivityErrorMargin && sensitivityDelta >= 0);
    }

    /**
     * Validate frame duration for a given capture.
     *
     * <p>Frame duration should be longer than exposure time.</p>
     *
     * @param result The capture result for a given capture
     */
    private void validateFrameDurationForCapture(CaptureResult result) {
        long expTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
        long frameDuration = getValueNotNull(result, CaptureResult.SENSOR_FRAME_DURATION);
        if (VERBOSE) {
            Log.v(TAG, "frame duration: " + frameDuration + " Exposure time: " + expTime);
        }

        mCollector.expectTrue(String.format("Frame duration (%d) should be longer than exposure"
                + " time (%d) for a given capture", frameDuration, expTime),
                frameDuration >= expTime);

        validatePipelineDepth(result);
    }

    /**
     * Basic verification for the control mode capture result.
     *
     * @param key The capture result key to be verified against
     * @param requestMode The request mode for this result
     * @param listener The capture listener to get capture results
     * @param numFramesVerified The number of capture results to be verified
     */
    private <T> void verifyCaptureResultForKey(CaptureResult.Key<T> key, T requestMode,
            SimpleCaptureCallback listener, int numFramesVerified) {
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            validatePipelineDepth(result);
            T resultMode = getValueNotNull(result, key);
            if (VERBOSE) {
                Log.v(TAG, "Expect value: " + requestMode.toString() + " result value: "
                        + resultMode.toString());
            }
            mCollector.expectEquals("Key " + key.getName() + " result should match request",
                    requestMode, resultMode);
        }
    }

    /**
     * Basic verification that the value of a capture result key should be one of the expected
     * values.
     *
     * @param key The capture result key to be verified against
     * @param expectedModes The list of any possible expected modes for this result
     * @param listener The capture listener to get capture results
     * @param numFramesVerified The number of capture results to be verified
     */
    private <T> void verifyAnyCaptureResultForKey(CaptureResult.Key<T> key, T[] expectedModes,
            SimpleCaptureCallback listener, int numFramesVerified) {
        for (int i = 0; i < numFramesVerified; i++) {
            CaptureResult result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            validatePipelineDepth(result);
            T resultMode = getValueNotNull(result, key);
            if (VERBOSE) {
                Log.v(TAG, "Expect values: " + Arrays.toString(expectedModes) + " result value: "
                        + resultMode.toString());
            }
            // Capture result should be one of the expected values.
            mCollector.expectContains(expectedModes, resultMode);
        }
    }

    /**
     * Verify if the fps is slow down for given input request with certain
     * controls inside.
     * <p>
     * This method selects a max preview size for each fps range, and then
     * configure the preview stream. Preview is started with the max preview
     * size, and then verify if the result frame duration is in the frame
     * duration range.
     * </p>
     *
     * @param requestBuilder The request builder that contains post-processing
     *            controls that could impact the output frame rate, such as
     *            {@link CaptureRequest.NOISE_REDUCTION_MODE}. The value of
     *            these controls must be set to some values such that the frame
     *            rate is not slow down.
     * @param numFramesVerified The number of frames to be verified
     * @param fpsRanges The fps ranges to be verified
     */
    private void verifyFpsNotSlowDown(CaptureRequest.Builder requestBuilder,
            int numFramesVerified, List<Range<Integer>> fpsRanges )  throws Exception {
        boolean frameDurationAvailable = true;
        // Allow a few frames for AE to settle on target FPS range
        final int NUM_FRAME_TO_SKIP = 6;
        float frameDurationErrorMargin = FRAME_DURATION_ERROR_MARGIN;
        if (!mStaticInfo.areKeysAvailable(CaptureResult.SENSOR_FRAME_DURATION)) {
            frameDurationAvailable = false;
            // Allow a larger error margin (1.5%) for timestamps
            frameDurationErrorMargin = 0.015f;
        }

        boolean antiBandingOffIsSupported = mStaticInfo.isAntiBandingOffModeSupported();
        Range<Integer> fpsRange;
        SimpleCaptureCallback resultListener;

        for (int i = 0; i < fpsRanges.size(); i += 1) {
            fpsRange = fpsRanges.get(i);
            Size previewSz = getMaxPreviewSizeForFpsRange(fpsRange);
            // If unable to find a preview size, then log the failure, and skip this run.
            if (previewSz == null) {
                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    mCollector.addMessage(String.format(
                            "Unable to find a preview size supporting given fps range %s",
                            fpsRange));
                }
                continue;
            }

            if (VERBOSE) {
                Log.v(TAG, String.format("Test fps range %s for preview size %s",
                        fpsRange, previewSz.toString()));
            }
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            // Turn off auto antibanding to avoid exposure time and frame duration interference
            // from antibanding algorithm.
            if (antiBandingOffIsSupported) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF);
            } else {
                // The device doesn't implement the OFF mode, test continues. It need make sure
                // that the antibanding algorithm doesn't slow down the fps.
                Log.i(TAG, "OFF antibanding mode is not supported, the camera device output must" +
                        " not slow down the frame rate regardless of its current antibanding" +
                        " mode");
            }

            resultListener = new SimpleCaptureCallback();
            startPreview(requestBuilder, previewSz, resultListener);
            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            // Wait several more frames for AE to settle on target FPS range
            waitForNumResults(resultListener, NUM_FRAME_TO_SKIP);

            long[] frameDurationRange = new long[]{
                    (long) (1e9 / fpsRange.getUpper()), (long) (1e9 / fpsRange.getLower())};
            long captureTime = 0, prevCaptureTime = 0;
            for (int j = 0; j < numFramesVerified; j++) {
                long frameDuration = frameDurationRange[0];
                CaptureResult result =
                        resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
                validatePipelineDepth(result);
                if (frameDurationAvailable) {
                    frameDuration = getValueNotNull(result, CaptureResult.SENSOR_FRAME_DURATION);
                } else {
                    // if frame duration is not available, check timestamp instead
                    captureTime = getValueNotNull(result, CaptureResult.SENSOR_TIMESTAMP);
                    if (j > 0) {
                        frameDuration = captureTime - prevCaptureTime;
                    }
                    prevCaptureTime = captureTime;
                }
                mCollector.expectInRange(
                        "Frame duration must be in the range of " +
                                Arrays.toString(frameDurationRange),
                        frameDuration,
                        (long) (frameDurationRange[0] * (1 - frameDurationErrorMargin)),
                        (long) (frameDurationRange[1] * (1 + frameDurationErrorMargin)));
            }
        }

        stopPreview();
    }

    /**
     * Validate the pipeline depth result.
     *
     * @param result The capture result to get pipeline depth data
     */
    private void validatePipelineDepth(CaptureResult result) {
        final byte MIN_PIPELINE_DEPTH = 1;
        byte maxPipelineDepth = mStaticInfo.getPipelineMaxDepthChecked();
        Byte pipelineDepth = getValueNotNull(result, CaptureResult.REQUEST_PIPELINE_DEPTH);
        mCollector.expectInRange(String.format("Pipeline depth must be in the range of [%d, %d]",
                MIN_PIPELINE_DEPTH, maxPipelineDepth), pipelineDepth, MIN_PIPELINE_DEPTH,
                maxPipelineDepth);
    }

    /**
     * Calculate the anti-flickering corrected exposure time.
     * <p>
     * If the input exposure time is very short (shorter than flickering
     * boundary), which indicate the scene is bright and very likely at outdoor
     * environment, skip the correction, as it doesn't make much sense by doing so.
     * </p>
     * <p>
     * For long exposure time (larger than the flickering boundary), find the
     * exposure time that is closest to the flickering boundary.
     * </p>
     *
     * @param flickeringMode The flickering mode
     * @param exposureTime The input exposureTime to be corrected
     * @return anti-flickering corrected exposure time
     */
    private long getAntiFlickeringExposureTime(int flickeringMode, long exposureTime) {
        if (flickeringMode != ANTI_FLICKERING_50HZ && flickeringMode != ANTI_FLICKERING_60HZ) {
            throw new IllegalArgumentException("Input anti-flickering mode must be 50 or 60Hz");
        }
        long flickeringBoundary = EXPOSURE_TIME_BOUNDARY_50HZ_NS;
        if (flickeringMode == ANTI_FLICKERING_60HZ) {
            flickeringBoundary = EXPOSURE_TIME_BOUNDARY_60HZ_NS;
        }

        if (exposureTime <= flickeringBoundary) {
            return exposureTime;
        }

        // Find the closest anti-flickering corrected exposure time
        long correctedExpTime = exposureTime + (flickeringBoundary / 2);
        correctedExpTime = correctedExpTime - (correctedExpTime % flickeringBoundary);
        return correctedExpTime;
    }

    /**
     * Update one 3A region in capture request builder if that region is supported. Do nothing
     * if the specified 3A region is not supported by camera device.
     * @param requestBuilder The request to be updated
     * @param algoIdx The index to the algorithm. (AE: 0, AWB: 1, AF: 2)
     * @param regions The 3A regions to be set
     */
    private void update3aRegion(
            CaptureRequest.Builder requestBuilder, int algoIdx, MeteringRectangle[] regions)
    {
        int maxRegions;
        CaptureRequest.Key<MeteringRectangle[]> key;

        if (regions == null || regions.length == 0) {
            throw new IllegalArgumentException("Invalid input 3A region!");
        }

        switch (algoIdx) {
            case INDEX_ALGORITHM_AE:
                maxRegions = mStaticInfo.getAeMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AE_REGIONS;
                break;
            case INDEX_ALGORITHM_AWB:
                maxRegions = mStaticInfo.getAwbMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AWB_REGIONS;
                break;
            case INDEX_ALGORITHM_AF:
                maxRegions = mStaticInfo.getAfMaxRegionsChecked();
                key = CaptureRequest.CONTROL_AF_REGIONS;
                break;
            default:
                throw new IllegalArgumentException("Unknown 3A Algorithm!");
        }

        if (maxRegions >= regions.length) {
            requestBuilder.set(key, regions);
        }
    }

    /**
     * Validate one 3A region in capture result equals to expected region if that region is
     * supported. Do nothing if the specified 3A region is not supported by camera device.
     * @param result The capture result to be validated
     * @param algoIdx The index to the algorithm. (AE: 0, AWB: 1, AF: 2)
     * @param expectRegions The 3A regions expected in capture result
     */
    private void validate3aRegion(
            CaptureResult result, int algoIdx, MeteringRectangle[] expectRegions)
    {
        int maxRegions;
        CaptureResult.Key<MeteringRectangle[]> key;
        MeteringRectangle[] actualRegion;

        switch (algoIdx) {
            case INDEX_ALGORITHM_AE:
                maxRegions = mStaticInfo.getAeMaxRegionsChecked();
                key = CaptureResult.CONTROL_AE_REGIONS;
                break;
            case INDEX_ALGORITHM_AWB:
                maxRegions = mStaticInfo.getAwbMaxRegionsChecked();
                key = CaptureResult.CONTROL_AWB_REGIONS;
                break;
            case INDEX_ALGORITHM_AF:
                maxRegions = mStaticInfo.getAfMaxRegionsChecked();
                key = CaptureResult.CONTROL_AF_REGIONS;
                break;
            default:
                throw new IllegalArgumentException("Unknown 3A Algorithm!");
        }

        if (maxRegions > 0)
        {
            actualRegion = getValueNotNull(result, key);
            mCollector.expectEquals(
                    "Expected 3A regions: " + Arrays.toString(expectRegions) +
                    " does not match actual one: " + Arrays.toString(actualRegion),
                    expectRegions, actualRegion);
        }
    }
}
