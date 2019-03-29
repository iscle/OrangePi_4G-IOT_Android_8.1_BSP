/*
 * Copyright 2013 The Android Open Source Project
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

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.SystemClock;
import android.util.Pair;
import android.util.Size;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.camera2.cts.helpers.CameraSessionUtils.*;

import android.util.Log;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class CaptureResultTest extends Camera2AndroidTestCase {
    private static final String TAG = "CaptureResultTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int MAX_NUM_IMAGES = MAX_READER_IMAGES;
    private static final int NUM_FRAMES_VERIFIED = 30;
    private static final long WAIT_FOR_RESULT_TIMEOUT_MS = 3000;

    // List that includes all public keys from CaptureResult
    List<CaptureResult.Key<?>> mAllKeys;

    // List tracking the failed test keys.

    @Override
    public void setContext(Context context) {
        mAllKeys = getAllCaptureResultKeys();
        super.setContext(context);

        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
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
     * <p>
     * Basic non-null check test for multiple capture results.
     * </p>
     * <p>
     * When capturing many frames, some camera devices may return some results that have null keys
     * randomly, which is an API violation and could cause application crash randomly. This test
     * runs a typical flexible yuv capture many times, and checks if there is any null entries in
     * a capture result.
     * </p>
     */
    public void testCameraCaptureResultAllKeys() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (mStaticInfo.isColorOutputSupported()) {
                    // Create image reader and surface.
                    Size size = mOrderedPreviewSizes.get(0);
                    createDefaultImageReader(size, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                            new ImageDropperListener());
                } else {
                    Size size = getMaxDepthSize(id, mCameraManager);
                    createDefaultImageReader(size, ImageFormat.DEPTH16, MAX_NUM_IMAGES,
                            new ImageDropperListener());
                }

                // Configure output streams.
                List<Surface> outputSurfaces = new ArrayList<Surface>(1);
                outputSurfaces.add(mReaderSurface);
                createSession(outputSurfaces);

                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                assertNotNull("Failed to create capture request", requestBuilder);
                requestBuilder.addTarget(mReaderSurface);

                // Start capture
                SimpleCaptureCallback captureListener = new SimpleCaptureCallback();
                startCapture(requestBuilder.build(), /*repeating*/true, captureListener, mHandler);

                // Get the waived keys for current camera device
                List<CaptureResult.Key<?>> waiverkeys = getWaiverKeysForCamera();

                // Verify results
                validateCaptureResult(captureListener, waiverkeys, requestBuilder,
                        NUM_FRAMES_VERIFIED);

                stopCapture(/*fast*/false);
            } finally {
                closeDevice(id);
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Check partial results conform to its specification.
     * <p>
     * The test is skipped if partial result is not supported on device. </p>
     * <p>Test summary:<ul>
     * <li>1. Number of partial results is less than or equal to
     * {@link CameraCharacteristics#REQUEST_PARTIAL_RESULT_COUNT}.
     * <li>2. Each key appeared in partial results must be unique across all partial results.
     * <li>3. All keys appeared in partial results must be present in TotalCaptureResult
     * <li>4. Also test onCaptureComplete callback always happen after onCaptureStart or
     * onCaptureProgressed callbacks.
     * </ul></p>
     */
    public void testPartialResult() throws Exception {
        final int NUM_FRAMES_TESTED = 30;
        final int WAIT_FOR_RESULT_TIMOUT_MS = 2000;
        for (String id : mCameraIds) {
            try {
                openDevice(id);

                // Skip the test if partial result is not supported
                int partialResultCount = mStaticInfo.getPartialResultCount();
                if (partialResultCount == 1) {
                    continue;
                }

                // Create image reader and surface.
                if (mStaticInfo.isColorOutputSupported()) {
                    Size size = mOrderedPreviewSizes.get(0);
                    createDefaultImageReader(size, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                            new ImageDropperListener());
                } else {
                    Size size = getMaxDepthSize(id, mCameraManager);
                    createDefaultImageReader(size, ImageFormat.DEPTH16, MAX_NUM_IMAGES,
                            new ImageDropperListener());
                }

                // Configure output streams.
                List<Surface> outputSurfaces = new ArrayList<Surface>(1);
                outputSurfaces.add(mReaderSurface);
                createSession(outputSurfaces);

                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                assertNotNull("Failed to create capture request", requestBuilder);
                requestBuilder.addTarget(mReaderSurface);
                TotalAndPartialResultListener listener =
                        new TotalAndPartialResultListener();

                // Start capture
                for (Integer frame = 0; frame < NUM_FRAMES_TESTED; frame++) {
                    // Set a different tag for each request so the listener can group
                    // partial results by each request
                    requestBuilder.setTag(frame);
                    startCapture(
                            requestBuilder.build(), /*repeating*/false,
                            listener, mHandler);
                }

                // Verify capture results
                for (int frame = 0; frame < NUM_FRAMES_TESTED; frame++) {
                    Pair<TotalCaptureResult, List<CaptureResult>> resultPair =
                            listener.getCaptureResultPairs(WAIT_FOR_RESULT_TIMOUT_MS);

                    List<CaptureResult> partialResults = resultPair.second;

                    if (partialResults == null) {
                        // HAL only sends total result is legal
                        partialResults = new ArrayList<>();
                    }

                    TotalCaptureResult totalResult = resultPair.first;

                    mCollector.expectLessOrEqual("Too many partial results",
                            partialResultCount, partialResults.size());
                    Set<CaptureResult.Key<?>> appearedPartialKeys =
                            new HashSet<CaptureResult.Key<?>>();
                    for (CaptureResult partialResult : partialResults) {
                        List<CaptureResult.Key<?>> partialKeys = partialResult.getKeys();
                        mCollector.expectValuesUnique("Partial result keys: ", partialKeys);
                        for (CaptureResult.Key<?> key : partialKeys) {
                            mCollector.expectTrue(
                                    String.format("Key %s appears in multiple partial results",
                                            key.getName()),
                                    !appearedPartialKeys.contains(key));
                        }
                        appearedPartialKeys.addAll(partialKeys);
                    }

                    // Test total result against the partial results
                    List<CaptureResult.Key<?>> totalResultKeys = totalResult.getKeys();
                    mCollector.expectTrue(
                            "TotalCaptureResult must be a super set of partial capture results",
                            totalResultKeys.containsAll(appearedPartialKeys));

                    List<CaptureResult> totalResultPartials = totalResult.getPartialResults();
                    mCollector.expectEquals("TotalCaptureResult's partial results must match " +
                            "the ones observed by #onCaptureProgressed",
                            partialResults, totalResultPartials);

                    if (VERBOSE) {
                        Log.v(TAG, "testPartialResult - Observed " +
                                partialResults.size() + "; queried for " +
                                totalResultPartials.size());
                    }
                }

                int errorCode = listener.getErrorCode();
                if ((errorCode & TotalAndPartialResultListener.ERROR_DUPLICATED_REQUEST) != 0) {
                    mCollector.addMessage("Listener received multiple onCaptureComplete" +
                            " callback for the same request");
                }
                if ((errorCode & TotalAndPartialResultListener.ERROR_WRONG_CALLBACK_ORDER) != 0) {
                    mCollector.addMessage("Listener received onCaptureStart or" +
                            " onCaptureProgressed after onCaptureComplete");
                }

                stopCapture(/*fast*/false);
            } finally {
                closeDevice(id);
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Check that the timestamps passed in the results, buffers, and capture callbacks match for
     * a single request, and increase monotonically
     */
    public void testResultTimestamps() throws Exception {
        for (String id : mCameraIds) {
            ImageReader previewReader = null;
            ImageReader jpegReader = null;

            SimpleImageReaderListener jpegListener = new SimpleImageReaderListener();
            SimpleImageReaderListener prevListener = new SimpleImageReaderListener();
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                CaptureRequest.Builder previewBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                CaptureRequest.Builder multiBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

                // Create image reader and surface.
                Size previewSize = mOrderedPreviewSizes.get(0);
                Size jpegSize = mOrderedStillSizes.get(0);

                // Create ImageReaders.
                previewReader = makeImageReader(previewSize, ImageFormat.YUV_420_888,
                        MAX_NUM_IMAGES, prevListener, mHandler);
                jpegReader = makeImageReader(jpegSize, ImageFormat.JPEG,
                        MAX_NUM_IMAGES, jpegListener, mHandler);

                // Configure output streams with preview and jpeg streams.
                List<Surface> outputSurfaces = new ArrayList<>(Arrays.asList(
                        previewReader.getSurface(), jpegReader.getSurface()));

                SessionListener mockSessionListener = getMockSessionListener();

                CameraCaptureSession session = configureAndVerifySession(mockSessionListener,
                        mCamera, outputSurfaces, mHandler);

                // Configure the requests.
                previewBuilder.addTarget(previewReader.getSurface());
                multiBuilder.addTarget(previewReader.getSurface());
                multiBuilder.addTarget(jpegReader.getSurface());

                CaptureCallback mockCaptureCallback = getMockCaptureListener();

                // Capture targeting only preview
                Pair<TotalCaptureResult, Long> result = captureAndVerifyResult(mockCaptureCallback,
                        session, previewBuilder.build(), mHandler);

                // Check if all timestamps are the same
                Image prevImage = prevListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                validateTimestamps("Result 1", result.first,
                        prevImage, result.second);
                prevImage.close();

                // Capture targeting both jpeg and preview
                Pair<TotalCaptureResult, Long> result2 = captureAndVerifyResult(mockCaptureCallback,
                        session, multiBuilder.build(), mHandler);

                // Check if all timestamps are the same
                prevImage = prevListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                Image jpegImage = jpegListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                validateTimestamps("Result 2 Preview", result2.first,
                        prevImage, result2.second);
                validateTimestamps("Result 2 Jpeg", result2.first,
                        jpegImage, result2.second);
                prevImage.close();
                jpegImage.close();

                // Check if timestamps are increasing
                mCollector.expectGreater("Timestamps must be increasing.", result.second,
                        result2.second);

                // Capture two preview frames
                long startTime = SystemClock.elapsedRealtimeNanos();
                Pair<TotalCaptureResult, Long> result3 = captureAndVerifyResult(mockCaptureCallback,
                        session, previewBuilder.build(), mHandler);
                Pair<TotalCaptureResult, Long> result4 = captureAndVerifyResult(mockCaptureCallback,
                        session, previewBuilder.build(), mHandler);
                long clockDiff = SystemClock.elapsedRealtimeNanos() - startTime;
                long resultDiff = result4.second - result3.second;

                // Check if all timestamps are the same
                prevImage = prevListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                validateTimestamps("Result 3", result3.first,
                        prevImage, result3.second);
                prevImage.close();
                prevImage = prevListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                validateTimestamps("Result 4", result4.first,
                        prevImage, result4.second);
                prevImage.close();

                // Check that the timestamps monotonically increase at a reasonable rate
                mCollector.expectGreaterOrEqual("Timestamps increase faster than system clock.",
                        resultDiff, clockDiff);
                mCollector.expectGreater("Timestamps must be increasing.", result3.second,
                        result4.second);
            } finally {
                closeDevice(id);
                closeImageReader(previewReader);
                closeImageReader(jpegReader);
            }
        }
    }

    private void validateTimestamps(String msg, TotalCaptureResult result, Image resultImage,
                                    long captureTime) {
        mCollector.expectKeyValueEquals(result, CaptureResult.SENSOR_TIMESTAMP, captureTime);
        mCollector.expectEquals(msg + ": Capture timestamp must be same as resultImage timestamp",
                resultImage.getTimestamp(), captureTime);
    }

    private void validateCaptureResult(SimpleCaptureCallback captureListener,
            List<CaptureResult.Key<?>> skippedKeys, CaptureRequest.Builder requestBuilder,
            int numFramesVerified) throws Exception {
        CaptureResult result = null;
        for (int i = 0; i < numFramesVerified; i++) {
            String failMsg = "Failed capture result " + i + " test ";
            result = captureListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);

            for (CaptureResult.Key<?> key : mAllKeys) {
                if (!skippedKeys.contains(key)) {
                    /**
                     * Check the critical tags here.
                     * TODO: Can use the same key for request and result when request/result
                     * becomes symmetric (b/14059883). Then below check can be wrapped into
                     * a generic function.
                     */
                    String msg = failMsg + "for key " + key.getName();
                    if (key.equals(CaptureResult.CONTROL_AE_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.CONTROL_AE_MODE),
                                result.get(CaptureResult.CONTROL_AE_MODE));
                    } else if (key.equals(CaptureResult.CONTROL_AF_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.CONTROL_AF_MODE),
                                result.get(CaptureResult.CONTROL_AF_MODE));
                    } else if (key.equals(CaptureResult.CONTROL_AWB_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.CONTROL_AWB_MODE),
                                result.get(CaptureResult.CONTROL_AWB_MODE));
                    } else if (key.equals(CaptureResult.CONTROL_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.CONTROL_MODE),
                                result.get(CaptureResult.CONTROL_MODE));
                    } else if (key.equals(CaptureResult.STATISTICS_FACE_DETECT_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.STATISTICS_FACE_DETECT_MODE),
                                result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE));
                    } else if (key.equals(CaptureResult.NOISE_REDUCTION_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE),
                                result.get(CaptureResult.NOISE_REDUCTION_MODE));
                    } else if (key.equals(CaptureResult.NOISE_REDUCTION_MODE)) {
                        mCollector.expectEquals(msg,
                                requestBuilder.get(CaptureRequest.NOISE_REDUCTION_MODE),
                                result.get(CaptureResult.NOISE_REDUCTION_MODE));
                    } else if (key.equals(CaptureResult.REQUEST_PIPELINE_DEPTH)) {

                    } else {
                        // Only do non-null check for the rest of keys.
                        mCollector.expectKeyValueNotNull(failMsg, result, key);
                    }
                } else {
                    // These keys should always be null
                    if (key.equals(CaptureResult.CONTROL_AE_REGIONS)) {
                        mCollector.expectNull(
                                "Capture result contains AE regions but aeMaxRegions is 0",
                                result.get(CaptureResult.CONTROL_AE_REGIONS));
                    } else if (key.equals(CaptureResult.CONTROL_AWB_REGIONS)) {
                        mCollector.expectNull(
                                "Capture result contains AWB regions but awbMaxRegions is 0",
                                result.get(CaptureResult.CONTROL_AWB_REGIONS));
                    } else if (key.equals(CaptureResult.CONTROL_AF_REGIONS)) {
                        mCollector.expectNull(
                                "Capture result contains AF regions but afMaxRegions is 0",
                                result.get(CaptureResult.CONTROL_AF_REGIONS));
                    }
                }
            }
        }
    }

    /*
     * Add waiver keys per camera device hardware level and capability.
     *
     * Must be called after camera device is opened.
     */
    private List<CaptureResult.Key<?>> getWaiverKeysForCamera() {
        List<CaptureResult.Key<?>> waiverKeys = new ArrayList<>();

        // Global waiver keys
        waiverKeys.add(CaptureResult.JPEG_GPS_LOCATION);
        waiverKeys.add(CaptureResult.JPEG_ORIENTATION);
        waiverKeys.add(CaptureResult.JPEG_QUALITY);
        waiverKeys.add(CaptureResult.JPEG_THUMBNAIL_QUALITY);
        waiverKeys.add(CaptureResult.JPEG_THUMBNAIL_SIZE);

        // Keys only present when corresponding control is on are being
        // verified in its own functional test
        // Only present in certain tonemap mode. Test in CaptureRequestTest.
        waiverKeys.add(CaptureResult.TONEMAP_CURVE);
        waiverKeys.add(CaptureResult.TONEMAP_GAMMA);
        waiverKeys.add(CaptureResult.TONEMAP_PRESET_CURVE);
        // Only present when test pattern mode is SOLID_COLOR.
        // TODO: verify this key in test pattern test later
        waiverKeys.add(CaptureResult.SENSOR_TEST_PATTERN_DATA);
        // Only present when STATISTICS_LENS_SHADING_MAP_MODE is ON
        waiverKeys.add(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        // Only present when STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES is ON
        waiverKeys.add(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        // Only present when face detection is on
        waiverKeys.add(CaptureResult.STATISTICS_FACES);
        // Only present in reprocessing capture result.
        waiverKeys.add(CaptureResult.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR);

        //Keys not required if RAW is not supported
        if (!mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
            waiverKeys.add(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
            waiverKeys.add(CaptureResult.SENSOR_GREEN_SPLIT);
            waiverKeys.add(CaptureResult.SENSOR_NOISE_PROFILE);
        }

        //Keys for depth output capability
        if (!mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT)) {
            waiverKeys.add(CaptureResult.LENS_POSE_ROTATION);
            waiverKeys.add(CaptureResult.LENS_POSE_TRANSLATION);
            waiverKeys.add(CaptureResult.LENS_INTRINSIC_CALIBRATION);
            waiverKeys.add(CaptureResult.LENS_RADIAL_DISTORTION);
        }

        // Waived if RAW output is not supported
        int[] outputFormats = mStaticInfo.getAvailableFormats(
                StaticMetadata.StreamDirection.Output);
        boolean supportRaw = false;
        for (int format : outputFormats) {
            if (format == ImageFormat.RAW_SENSOR || format == ImageFormat.RAW10 ||
                    format == ImageFormat.RAW12 || format == ImageFormat.RAW_PRIVATE) {
                supportRaw = true;
                break;
            }
        }
        if (!supportRaw) {
            waiverKeys.add(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
        }

        if (mStaticInfo.getAeMaxRegionsChecked() == 0) {
            waiverKeys.add(CaptureResult.CONTROL_AE_REGIONS);
        }
        if (mStaticInfo.getAwbMaxRegionsChecked() == 0) {
            waiverKeys.add(CaptureResult.CONTROL_AWB_REGIONS);
        }
        if (mStaticInfo.getAfMaxRegionsChecked() == 0) {
            waiverKeys.add(CaptureResult.CONTROL_AF_REGIONS);
        }

        // Keys for dynamic black/white levels
        if (!mStaticInfo.isOpticalBlackRegionSupported()) {
            waiverKeys.add(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
            waiverKeys.add(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        }

        if (!mStaticInfo.isEnableZslSupported()) {
            waiverKeys.add(CaptureResult.CONTROL_ENABLE_ZSL);
        }

        if (mStaticInfo.isHardwareLevelAtLeastFull()) {
            return waiverKeys;
        }

        /*
         * Hardware Level = LIMITED or LEGACY
         */
        // Key not present if certain control is not supported
        if (!mStaticInfo.isColorCorrectionSupported()) {
            waiverKeys.add(CaptureResult.COLOR_CORRECTION_GAINS);
            waiverKeys.add(CaptureResult.COLOR_CORRECTION_MODE);
            waiverKeys.add(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        }

        if (!mStaticInfo.isManualColorAberrationControlSupported()) {
            waiverKeys.add(CaptureResult.COLOR_CORRECTION_ABERRATION_MODE);
        }

        if (!mStaticInfo.isManualToneMapSupported()) {
            waiverKeys.add(CaptureResult.TONEMAP_MODE);
        }

        if (!mStaticInfo.isEdgeModeControlSupported()) {
            waiverKeys.add(CaptureResult.EDGE_MODE);
        }

        if (!mStaticInfo.isHotPixelMapModeControlSupported()) {
            waiverKeys.add(CaptureResult.HOT_PIXEL_MODE);
        }

        if (!mStaticInfo.isNoiseReductionModeControlSupported()) {
            waiverKeys.add(CaptureResult.NOISE_REDUCTION_MODE);
        }

        if (!mStaticInfo.isManualLensShadingMapSupported()) {
            waiverKeys.add(CaptureResult.SHADING_MODE);
        }

        //Keys not required if neither MANUAL_SENSOR nor READ_SENSOR_SETTINGS is supported
        if (!mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) &&
            !mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
            waiverKeys.add(CaptureResult.SENSOR_EXPOSURE_TIME);
            waiverKeys.add(CaptureResult.SENSOR_SENSITIVITY);
            waiverKeys.add(CaptureResult.LENS_FOCUS_DISTANCE);
            waiverKeys.add(CaptureResult.LENS_APERTURE);
        }

        if (!mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
            waiverKeys.add(CaptureResult.SENSOR_FRAME_DURATION);
            waiverKeys.add(CaptureResult.BLACK_LEVEL_LOCK);
            waiverKeys.add(CaptureResult.LENS_FOCUS_RANGE);
            waiverKeys.add(CaptureResult.LENS_STATE);
            waiverKeys.add(CaptureResult.LENS_FILTER_DENSITY);
        }

        if (mStaticInfo.isHardwareLevelLimited() && mStaticInfo.isColorOutputSupported()) {
            return waiverKeys;
        }

        /*
         * Hardware Level = LEGACY or no regular output is supported
         */
        waiverKeys.add(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);
        waiverKeys.add(CaptureResult.CONTROL_AE_STATE);
        waiverKeys.add(CaptureResult.CONTROL_AWB_STATE);
        waiverKeys.add(CaptureResult.FLASH_STATE);
        waiverKeys.add(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE);
        waiverKeys.add(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
        waiverKeys.add(CaptureResult.STATISTICS_LENS_SHADING_MAP_MODE);
        waiverKeys.add(CaptureResult.STATISTICS_SCENE_FLICKER);
        waiverKeys.add(CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
        waiverKeys.add(CaptureResult.CONTROL_AF_TRIGGER);

        if (mStaticInfo.isHardwareLevelLegacy()) {
            return waiverKeys;
        }

        /*
         * Regular output not supported, only depth, waive color-output-related keys
         */
        waiverKeys.add(CaptureResult.CONTROL_SCENE_MODE);
        waiverKeys.add(CaptureResult.CONTROL_EFFECT_MODE);
        waiverKeys.add(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
        waiverKeys.add(CaptureResult.SENSOR_TEST_PATTERN_MODE);
        waiverKeys.add(CaptureResult.NOISE_REDUCTION_MODE);
        waiverKeys.add(CaptureResult.COLOR_CORRECTION_ABERRATION_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AE_ANTIBANDING_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
        waiverKeys.add(CaptureResult.CONTROL_AE_LOCK);
        waiverKeys.add(CaptureResult.CONTROL_AE_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AF_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AWB_MODE);
        waiverKeys.add(CaptureResult.CONTROL_AWB_LOCK);
        waiverKeys.add(CaptureResult.STATISTICS_FACE_DETECT_MODE);
        waiverKeys.add(CaptureResult.FLASH_MODE);
        waiverKeys.add(CaptureResult.SCALER_CROP_REGION);

        return waiverKeys;
    }

    /**
     * A capture listener implementation for collecting both partial and total results.
     *
     * <p> This is not a full-blown class and has some implicit assumptions. The class groups
     * capture results by capture request, so the user must guarantee each request this listener
     * is listening is unique. This class is not thread safe, so don't attach an instance object
     * with multiple handlers.</p>
     * */
    private static class TotalAndPartialResultListener
            extends CameraCaptureSession.CaptureCallback {
        static final int ERROR_DUPLICATED_REQUEST = 1 << 0;
        static final int ERROR_WRONG_CALLBACK_ORDER = 1 << 1;

        private final LinkedBlockingQueue<Pair<TotalCaptureResult, List<CaptureResult>> > mQueue =
                new LinkedBlockingQueue<>();
        private final HashMap<CaptureRequest, List<CaptureResult>> mPartialResultsMap =
                new HashMap<CaptureRequest, List<CaptureResult>>();
        private final HashSet<CaptureRequest> completedRequests = new HashSet<>();
        private int errorCode = 0;

        @Override
        public void onCaptureStarted(
            CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber)
        {
            checkCallbackOrder(request);
            createMapEntryIfNecessary(request);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                List<CaptureResult> partialResultsList = mPartialResultsMap.get(request);
                if (partialResultsList == null) {
                    Log.w(TAG, "onCaptureCompleted: unknown request");
                }
                mQueue.put(new Pair<TotalCaptureResult, List<CaptureResult>>(
                        result, partialResultsList));
                mPartialResultsMap.remove(request);
                boolean newEntryAdded = completedRequests.add(request);
                if (!newEntryAdded) {
                    Integer frame = (Integer) request.getTag();
                    Log.e(TAG, "Frame " + frame + "ERROR_DUPLICATED_REQUEST");
                    errorCode |= ERROR_DUPLICATED_REQUEST;
                }
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onCaptureCompleted");
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                CaptureResult partialResult) {
            createMapEntryIfNecessary(request);
            List<CaptureResult> partialResultsList = mPartialResultsMap.get(request);
            partialResultsList.add(partialResult);
        }

        private void createMapEntryIfNecessary(CaptureRequest request) {
            if (!mPartialResultsMap.containsKey(request)) {
                // create a new entry in the map
                mPartialResultsMap.put(request, new ArrayList<CaptureResult>());
            }
        }

        private void checkCallbackOrder(CaptureRequest request) {
            if (completedRequests.contains(request)) {
                Integer frame = (Integer) request.getTag();
                Log.e(TAG, "Frame " + frame + "ERROR_WRONG_CALLBACK_ORDER");
                errorCode |= ERROR_WRONG_CALLBACK_ORDER;
            }
        }

        public Pair<TotalCaptureResult, List<CaptureResult>> getCaptureResultPairs(long timeout) {
            try {
                Pair<TotalCaptureResult, List<CaptureResult>> result =
                        mQueue.poll(timeout, TimeUnit.MILLISECONDS);
                assertNotNull("Wait for a capture result timed out in " + timeout + "ms", result);
                return result;
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException("Unhandled interrupted exception", e);
            }
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    /**
     * TODO: Use CameraCharacteristics.getAvailableCaptureResultKeys() once we can filter out
     * @hide keys.
     *
     */

    /*@O~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * The key entries below this point are generated from metadata
     * definitions in /system/media/camera/docs. Do not modify by hand or
     * modify the comment blocks at the start or end.
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~*/

    private static List<CaptureResult.Key<?>> getAllCaptureResultKeys() {
        ArrayList<CaptureResult.Key<?>> resultKeys = new ArrayList<CaptureResult.Key<?>>();
        resultKeys.add(CaptureResult.COLOR_CORRECTION_MODE);
        resultKeys.add(CaptureResult.COLOR_CORRECTION_TRANSFORM);
        resultKeys.add(CaptureResult.COLOR_CORRECTION_GAINS);
        resultKeys.add(CaptureResult.COLOR_CORRECTION_ABERRATION_MODE);
        resultKeys.add(CaptureResult.CONTROL_AE_ANTIBANDING_MODE);
        resultKeys.add(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
        resultKeys.add(CaptureResult.CONTROL_AE_LOCK);
        resultKeys.add(CaptureResult.CONTROL_AE_MODE);
        resultKeys.add(CaptureResult.CONTROL_AE_REGIONS);
        resultKeys.add(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE);
        resultKeys.add(CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);
        resultKeys.add(CaptureResult.CONTROL_AF_MODE);
        resultKeys.add(CaptureResult.CONTROL_AF_REGIONS);
        resultKeys.add(CaptureResult.CONTROL_AF_TRIGGER);
        resultKeys.add(CaptureResult.CONTROL_AWB_LOCK);
        resultKeys.add(CaptureResult.CONTROL_AWB_MODE);
        resultKeys.add(CaptureResult.CONTROL_AWB_REGIONS);
        resultKeys.add(CaptureResult.CONTROL_CAPTURE_INTENT);
        resultKeys.add(CaptureResult.CONTROL_EFFECT_MODE);
        resultKeys.add(CaptureResult.CONTROL_MODE);
        resultKeys.add(CaptureResult.CONTROL_SCENE_MODE);
        resultKeys.add(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
        resultKeys.add(CaptureResult.CONTROL_AE_STATE);
        resultKeys.add(CaptureResult.CONTROL_AF_STATE);
        resultKeys.add(CaptureResult.CONTROL_AWB_STATE);
        resultKeys.add(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
        resultKeys.add(CaptureResult.CONTROL_ENABLE_ZSL);
        resultKeys.add(CaptureResult.EDGE_MODE);
        resultKeys.add(CaptureResult.FLASH_MODE);
        resultKeys.add(CaptureResult.FLASH_STATE);
        resultKeys.add(CaptureResult.HOT_PIXEL_MODE);
        resultKeys.add(CaptureResult.JPEG_GPS_LOCATION);
        resultKeys.add(CaptureResult.JPEG_ORIENTATION);
        resultKeys.add(CaptureResult.JPEG_QUALITY);
        resultKeys.add(CaptureResult.JPEG_THUMBNAIL_QUALITY);
        resultKeys.add(CaptureResult.JPEG_THUMBNAIL_SIZE);
        resultKeys.add(CaptureResult.LENS_APERTURE);
        resultKeys.add(CaptureResult.LENS_FILTER_DENSITY);
        resultKeys.add(CaptureResult.LENS_FOCAL_LENGTH);
        resultKeys.add(CaptureResult.LENS_FOCUS_DISTANCE);
        resultKeys.add(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE);
        resultKeys.add(CaptureResult.LENS_POSE_ROTATION);
        resultKeys.add(CaptureResult.LENS_POSE_TRANSLATION);
        resultKeys.add(CaptureResult.LENS_FOCUS_RANGE);
        resultKeys.add(CaptureResult.LENS_STATE);
        resultKeys.add(CaptureResult.LENS_INTRINSIC_CALIBRATION);
        resultKeys.add(CaptureResult.LENS_RADIAL_DISTORTION);
        resultKeys.add(CaptureResult.NOISE_REDUCTION_MODE);
        resultKeys.add(CaptureResult.REQUEST_PIPELINE_DEPTH);
        resultKeys.add(CaptureResult.SCALER_CROP_REGION);
        resultKeys.add(CaptureResult.SENSOR_EXPOSURE_TIME);
        resultKeys.add(CaptureResult.SENSOR_FRAME_DURATION);
        resultKeys.add(CaptureResult.SENSOR_SENSITIVITY);
        resultKeys.add(CaptureResult.SENSOR_TIMESTAMP);
        resultKeys.add(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        resultKeys.add(CaptureResult.SENSOR_NOISE_PROFILE);
        resultKeys.add(CaptureResult.SENSOR_GREEN_SPLIT);
        resultKeys.add(CaptureResult.SENSOR_TEST_PATTERN_DATA);
        resultKeys.add(CaptureResult.SENSOR_TEST_PATTERN_MODE);
        resultKeys.add(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
        resultKeys.add(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        resultKeys.add(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        resultKeys.add(CaptureResult.SHADING_MODE);
        resultKeys.add(CaptureResult.STATISTICS_FACE_DETECT_MODE);
        resultKeys.add(CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE);
        resultKeys.add(CaptureResult.STATISTICS_FACES);
        resultKeys.add(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);
        resultKeys.add(CaptureResult.STATISTICS_SCENE_FLICKER);
        resultKeys.add(CaptureResult.STATISTICS_HOT_PIXEL_MAP);
        resultKeys.add(CaptureResult.STATISTICS_LENS_SHADING_MAP_MODE);
        resultKeys.add(CaptureResult.TONEMAP_CURVE);
        resultKeys.add(CaptureResult.TONEMAP_MODE);
        resultKeys.add(CaptureResult.TONEMAP_GAMMA);
        resultKeys.add(CaptureResult.TONEMAP_PRESET_CURVE);
        resultKeys.add(CaptureResult.BLACK_LEVEL_LOCK);
        resultKeys.add(CaptureResult.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR);

        return resultKeys;
    }

    /*~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~
     * End generated code
     *~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~@~O@*/
}
