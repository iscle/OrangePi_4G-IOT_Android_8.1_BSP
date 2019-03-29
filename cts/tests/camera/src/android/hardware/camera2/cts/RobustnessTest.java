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
import static android.hardware.camera2.cts.RobustnessTest.MaxStreamSizes.*;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests exercising edge cases in camera setup, configuration, and usage.
 */
public class RobustnessTest extends Camera2AndroidTestCase {
    private static final String TAG = "RobustnessTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final int CONFIGURE_TIMEOUT = 5000; //ms
    private static final int CAPTURE_TIMEOUT = 1000; //ms

    // For testTriggerInteractions
    private static final int PREVIEW_WARMUP_FRAMES = 60;
    private static final int MAX_RESULT_STATE_CHANGE_WAIT_FRAMES = 100;
    private static final int MAX_TRIGGER_SEQUENCE_FRAMES = 180; // 6 sec at 30 fps
    private static final int MAX_RESULT_STATE_POSTCHANGE_WAIT_FRAMES = 10;

    /**
     * Test that a {@link CameraCaptureSession} can be configured with a {@link Surface} containing
     * a dimension other than one of the supported output dimensions.  The buffers produced into
     * this surface are expected have the dimensions of the closest possible buffer size in the
     * available stream configurations for a surface with this format.
     */
    public void testBadSurfaceDimensions() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);

                List<Size> testSizes = null;
                int format = mStaticInfo.isColorOutputSupported() ?
                    ImageFormat.YUV_420_888 : ImageFormat.DEPTH16;

                testSizes = CameraTestUtils.getSortedSizesForFormat(id, mCameraManager,
                        format, null);

                // Find some size not supported by the camera
                Size weirdSize = new Size(643, 577);
                int count = 0;
                while(testSizes.contains(weirdSize)) {
                    // Really, they can't all be supported...
                    weirdSize = new Size(weirdSize.getWidth() + 1, weirdSize.getHeight() + 1);
                    count++;
                    assertTrue("Too many exotic YUV_420_888 resolutions supported.", count < 100);
                }

                // Setup imageReader with invalid dimension
                ImageReader imageReader = ImageReader.newInstance(weirdSize.getWidth(),
                        weirdSize.getHeight(), format, 3);

                // Setup ImageReaderListener
                SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
                imageReader.setOnImageAvailableListener(imageListener, mHandler);

                Surface surface = imageReader.getSurface();
                List<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);

                // Setup a capture request and listener
                CaptureRequest.Builder request =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(surface);

                // Check that correct session callback is hit.
                CameraCaptureSession.StateCallback sessionListener =
                        mock(CameraCaptureSession.StateCallback.class);
                CameraCaptureSession session = CameraTestUtils.configureCameraSession(mCamera,
                        surfaces, sessionListener, mHandler);

                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onConfigured(any(CameraCaptureSession.class));
                verify(sessionListener, timeout(CONFIGURE_TIMEOUT).atLeastOnce()).
                        onReady(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onConfigureFailed(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onActive(any(CameraCaptureSession.class));
                verify(sessionListener, never()).onClosed(any(CameraCaptureSession.class));

                CameraCaptureSession.CaptureCallback captureListener =
                        mock(CameraCaptureSession.CaptureCallback.class);
                session.capture(request.build(), captureListener, mHandler);

                verify(captureListener, timeout(CAPTURE_TIMEOUT).atLeastOnce()).
                        onCaptureCompleted(any(CameraCaptureSession.class),
                                any(CaptureRequest.class), any(TotalCaptureResult.class));
                verify(captureListener, never()).onCaptureFailed(any(CameraCaptureSession.class),
                        any(CaptureRequest.class), any(CaptureFailure.class));

                Image image = imageListener.getImage(CAPTURE_TIMEOUT);
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();
                Size actualSize = new Size(imageWidth, imageHeight);

                assertTrue("Camera does not contain outputted image resolution " + actualSize,
                        testSizes.contains(actualSize));
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test for making sure the required output combinations for each hardware level and capability
     * work as expected.
     */
    public void testMandatoryOutputCombinations() throws Exception {
        /**
         * Tables for maximum sizes to try for each hardware level and capability.
         *
         * Keep in sync with the tables in
         * frameworks/base/core/java/android/hardware/camera2/CameraDevice.java#createCaptureSession
         *
         * Each row of the table is a set of (format, max resolution) pairs, using the below consts
         */

        // Enum values are defined in MaxStreamSizes
        final int[][] LEGACY_COMBINATIONS = {
            // Simple preview, GPU video processing, or no-preview video recording
            {PRIV, MAXIMUM},
            // No-viewfinder still image capture
            {JPEG, MAXIMUM},
            // In-application video/image processing
            {YUV,  MAXIMUM},
            // Standard still imaging.
            {PRIV, PREVIEW,  JPEG, MAXIMUM},
            // In-app processing plus still capture.
            {YUV,  PREVIEW,  JPEG, MAXIMUM},
            // Standard recording.
            {PRIV, PREVIEW,  PRIV, PREVIEW},
            // Preview plus in-app processing.
            {PRIV, PREVIEW,  YUV,  PREVIEW},
            // Still capture plus in-app processing.
            {PRIV, PREVIEW,  YUV,  PREVIEW,  JPEG, MAXIMUM}
        };

        final int[][] LIMITED_COMBINATIONS = {
            // High-resolution video recording with preview.
            {PRIV, PREVIEW,  PRIV, RECORD },
            // High-resolution in-app video processing with preview.
            {PRIV, PREVIEW,  YUV , RECORD },
            // Two-input in-app video processing.
            {YUV , PREVIEW,  YUV , RECORD },
            // High-resolution recording with video snapshot.
            {PRIV, PREVIEW,  PRIV, RECORD,   JPEG, RECORD  },
            // High-resolution in-app processing with video snapshot.
            {PRIV, PREVIEW,  YUV,  RECORD,   JPEG, RECORD  },
            // Two-input in-app processing with still capture.
            {YUV , PREVIEW,  YUV,  PREVIEW,  JPEG, MAXIMUM }
        };

        final int[][] BURST_COMBINATIONS = {
            // Maximum-resolution GPU processing with preview.
            {PRIV, PREVIEW,  PRIV, MAXIMUM },
            // Maximum-resolution in-app processing with preview.
            {PRIV, PREVIEW,  YUV,  MAXIMUM },
            // Maximum-resolution two-input in-app processsing.
            {YUV,  PREVIEW,  YUV,  MAXIMUM },
        };

        final int[][] FULL_COMBINATIONS = {
            // Video recording with maximum-size video snapshot.
            {PRIV, PREVIEW,  PRIV, PREVIEW,  JPEG, MAXIMUM },
            // Standard video recording plus maximum-resolution in-app processing.
            {YUV,  VGA,      PRIV, PREVIEW,  YUV,  MAXIMUM },
            // Preview plus two-input maximum-resolution in-app processing.
            {YUV,  VGA,      YUV,  PREVIEW,  YUV,  MAXIMUM }
        };

        final int[][] RAW_COMBINATIONS = {
            // No-preview DNG capture.
            {RAW,  MAXIMUM },
            // Standard DNG capture.
            {PRIV, PREVIEW,  RAW,  MAXIMUM },
            // In-app processing plus DNG capture.
            {YUV,  PREVIEW,  RAW,  MAXIMUM },
            // Video recording with DNG capture.
            {PRIV, PREVIEW,  PRIV, PREVIEW,  RAW, MAXIMUM},
            // Preview with in-app processing and DNG capture.
            {PRIV, PREVIEW,  YUV,  PREVIEW,  RAW, MAXIMUM},
            // Two-input in-app processing plus DNG capture.
            {YUV,  PREVIEW,  YUV,  PREVIEW,  RAW, MAXIMUM},
            // Still capture with simultaneous JPEG and DNG.
            {PRIV, PREVIEW,  JPEG, MAXIMUM,  RAW, MAXIMUM},
            // In-app processing with simultaneous JPEG and DNG.
            {YUV,  PREVIEW,  JPEG, MAXIMUM,  RAW, MAXIMUM}
        };

        final int[][] LEVEL_3_COMBINATIONS = {
            // In-app viewfinder analysis with dynamic selection of output format
            {PRIV, PREVIEW, PRIV, VGA, YUV, MAXIMUM, RAW, MAXIMUM},
            // In-app viewfinder analysis with dynamic selection of output format
            {PRIV, PREVIEW, PRIV, VGA, JPEG, MAXIMUM, RAW, MAXIMUM}
        };

        final int[][][] TABLES =
                { LEGACY_COMBINATIONS, LIMITED_COMBINATIONS, BURST_COMBINATIONS, FULL_COMBINATIONS,
                  RAW_COMBINATIONS, LEVEL_3_COMBINATIONS };

        sanityCheckConfigurationTables(TABLES);

        for (String id : mCameraIds) {
            openDevice(id);

            // Find the concrete max sizes for each format/resolution combination
            MaxStreamSizes maxSizes = new MaxStreamSizes(mStaticInfo, id, getContext());

            String streamConfigurationMapString =
                    mStaticInfo.getCharacteristics().get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).toString();
            if (VERBOSE) {
                Log.v(TAG, "StreamConfigurationMap: " + streamConfigurationMapString);
            }

            // Always run legacy-level tests for color-supporting devices

            if (mStaticInfo.isColorOutputSupported()) {
                for (int[] config : LEGACY_COMBINATIONS) {
                    testOutputCombination(id, config, maxSizes);
                }
            }

            // Then run higher-level tests if applicable

            if (!mStaticInfo.isHardwareLevelLegacy()) {

                // If not legacy, at least limited, so run limited-level tests

                if (mStaticInfo.isColorOutputSupported()) {
                    for (int[] config : LIMITED_COMBINATIONS) {
                        testOutputCombination(id, config, maxSizes);
                    }
                }

                // Check for BURST_CAPTURE, FULL and RAW and run those if appropriate

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE)) {
                    for (int[] config : BURST_COMBINATIONS) {
                        testOutputCombination(id, config, maxSizes);
                    }
                }

                if (mStaticInfo.isHardwareLevelAtLeastFull()) {
                    for (int[] config : FULL_COMBINATIONS) {
                        testOutputCombination(id, config, maxSizes);
                    }
                }

                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    for (int[] config : RAW_COMBINATIONS) {
                        testOutputCombination(id, config, maxSizes);
                    }
                }

                if (mStaticInfo.isHardwareLevelAtLeast(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                    for (int[] config: LEVEL_3_COMBINATIONS) {
                        testOutputCombination(id, config, maxSizes);
                    }
                }
            }

            closeDevice(id);
        }
    }

    /**
     * Test for making sure the required reprocess input/output combinations for each hardware
     * level and capability work as expected.
     */
    public void testMandatoryReprocessConfigurations() throws Exception {

        /**
         * For each stream combination, verify that
         *    1. A reprocessable session can be created using the stream combination.
         *    2. Reprocess capture requests targeting YUV and JPEG outputs are successful.
         */
        final int[][] LIMITED_COMBINATIONS = {
            // Input           Outputs
            {PRIV, MAXIMUM,    JPEG, MAXIMUM},
            {YUV , MAXIMUM,    JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV,  MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
        };

        final int[][] FULL_COMBINATIONS = {
            // Input           Outputs
            {YUV , MAXIMUM,    PRIV, PREVIEW},
            {YUV , MAXIMUM,    YUV , PREVIEW},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , RECORD},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , RECORD},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, JPEG, MAXIMUM},
        };

        final int[][] RAW_COMBINATIONS = {
            // Input           Outputs
            {PRIV, MAXIMUM,    YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, YUV , PREVIEW, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {YUV , MAXIMUM,    PRIV, PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {PRIV, MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
            {YUV , MAXIMUM,    YUV , PREVIEW, JPEG, MAXIMUM, RAW , MAXIMUM},
        };

        final int[][] LEVEL_3_COMBINATIONS = {
            // Input          Outputs
            // In-app viewfinder analysis with YUV->YUV ZSL and RAW
            {YUV , MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM},
            // In-app viewfinder analysis with PRIV->JPEG ZSL and RAW
            {PRIV, MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM, JPEG, MAXIMUM},
            // In-app viewfinder analysis with YUV->JPEG ZSL and RAW
            {YUV , MAXIMUM,   PRIV, PREVIEW, PRIV, VGA, RAW, MAXIMUM, JPEG, MAXIMUM},
        };

        final int[][][] TABLES =
                { LIMITED_COMBINATIONS, FULL_COMBINATIONS, RAW_COMBINATIONS, LEVEL_3_COMBINATIONS };

        sanityCheckConfigurationTables(TABLES);

        for (String id : mCameraIds) {
            CameraCharacteristics cc = mCameraManager.getCameraCharacteristics(id);
            StaticMetadata staticInfo = new StaticMetadata(cc);
            MaxStreamSizes maxSizes = new MaxStreamSizes(staticInfo, id, getContext());

            // Skip the test for legacy devices.
            if (staticInfo.isHardwareLevelLegacy()) {
                continue;
            }

            openDevice(id);

            try {
                for (int[] config : LIMITED_COMBINATIONS) {
                    testReprocessStreamCombination(id, config, maxSizes, staticInfo);
                }

                // Check FULL devices
                if (staticInfo.isHardwareLevelAtLeastFull()) {
                    for (int[] config : FULL_COMBINATIONS) {
                        testReprocessStreamCombination(id, config, maxSizes, staticInfo);
                    }
                }

                // Check devices with RAW capability.
                if (staticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    for (int[] config : RAW_COMBINATIONS) {
                        testReprocessStreamCombination(id, config, maxSizes, staticInfo);
                    }
                }

                if (mStaticInfo.isHardwareLevelAtLeast(
                        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
                    for (int[] config: LEVEL_3_COMBINATIONS) {
                        testReprocessStreamCombination(id, config, maxSizes, staticInfo);
                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testBasicTriggerSequence() throws Exception {

        for (String id : mCameraIds) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            openDevice(id);
            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                if (mStaticInfo.isHardwareLevelLegacy() || !mStaticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // Standard sequence - AF trigger then AE trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AF"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        boolean focusComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES && !focusComplete;
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);

                            CaptureResult focusResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = focusResult.get(CaptureResult.CONTROL_AF_STATE);
                        }

                        assertTrue("Focusing never completed!", focusComplete);

                        // Standard sequence - Part 2 AE trigger

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AE"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);

                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        boolean precaptureComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES && !precaptureComplete;
                             i++) {

                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult precaptureResult = captureListener.getCaptureResult(
                                CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            aeState = precaptureResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);

                        for (int i = 0; i < MAX_RESULT_STATE_POSTCHANGE_WAIT_FRAMES; i++) {
                            CaptureResult postPrecaptureResult = captureListener.getCaptureResult(
                                CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            aeState = postPrecaptureResult.get(CaptureResult.CONTROL_AE_STATE);
                            assertTrue("Late transition to PRECAPTURE state seen",
                                    aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
                        }

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();
                    }

                }

            } finally {
                closeDevice(id);
            }
        }

    }

    public void testSimultaneousTriggers() throws Exception {
        for (String id : mCameraIds) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            openDevice(id);
            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                if (mStaticInfo.isHardwareLevelLegacy() || !mStaticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // Trigger AF and AE together

                        if (VERBOSE) {
                            Log.v(TAG, String.format("Triggering AF and AE together"));
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testAfThenAeTrigger() throws Exception {
        for (String id : mCameraIds) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            openDevice(id);
            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                if (mStaticInfo.isHardwareLevelLegacy() || !mStaticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // AF with AE a request later

                        if (VERBOSE) {
                            Log.v(TAG, "Trigger AF, then AE trigger on next request");
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest2 = previewRequest.build();
                        mCameraSession.capture(triggerRequest2, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        focusComplete = verifyAfSequence(afMode, afState, focusComplete);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest2, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testAeThenAfTrigger() throws Exception {
        for (String id : mCameraIds) {
            Log.i(TAG, String.format("Testing Camera %s", id));

            openDevice(id);
            try {
                // Legacy devices do not support precapture trigger; don't test devices that
                // can't focus
                if (mStaticInfo.isHardwareLevelLegacy() || !mStaticInfo.hasFocuser()) {
                    continue;
                }
                // Depth-only devices won't support AE
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                int[] availableAfModes = mStaticInfo.getAfAvailableModesChecked();
                int[] availableAeModes = mStaticInfo.getAeAvailableModesChecked();

                for (int afMode : availableAfModes) {
                    if (afMode == CameraCharacteristics.CONTROL_AF_MODE_OFF ||
                            afMode == CameraCharacteristics.CONTROL_AF_MODE_EDOF) {
                        // Only test AF modes that have meaningful trigger behavior
                        continue;
                    }

                    for (int aeMode : availableAeModes) {
                        if (aeMode ==  CameraCharacteristics.CONTROL_AE_MODE_OFF) {
                            // Only test AE modes that have meaningful trigger behavior
                            continue;
                        }

                        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);

                        CaptureRequest.Builder previewRequest =
                                prepareTriggerTestSession(preview, aeMode, afMode);

                        SimpleCaptureCallback captureListener =
                                new CameraTestUtils.SimpleCaptureCallback();

                        mCameraSession.setRepeatingRequest(previewRequest.build(), captureListener,
                                mHandler);

                        // Cancel triggers

                        cancelTriggersAndWait(previewRequest, captureListener, afMode);

                        //
                        // AE with AF a request later

                        if (VERBOSE) {
                            Log.v(TAG, "Trigger AE, then AF trigger on next request");
                        }

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

                        CaptureRequest triggerRequest = previewRequest.build();
                        mCameraSession.capture(triggerRequest, captureListener, mHandler);

                        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                CaptureRequest.CONTROL_AF_TRIGGER_START);
                        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

                        CaptureRequest triggerRequest2 = previewRequest.build();
                        mCameraSession.capture(triggerRequest2, captureListener, mHandler);

                        CaptureResult triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        boolean precaptureComplete = false;
                        boolean focusComplete = false;

                        precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                        triggerResult = captureListener.getCaptureResultForRequest(
                                triggerRequest2, MAX_RESULT_STATE_CHANGE_WAIT_FRAMES);
                        int afState = triggerResult.get(CaptureResult.CONTROL_AF_STATE);
                        aeState = triggerResult.get(CaptureResult.CONTROL_AE_STATE);

                        for (int i = 0;
                             i < MAX_TRIGGER_SEQUENCE_FRAMES &&
                                     !(focusComplete && precaptureComplete);
                             i++) {

                            focusComplete = verifyAfSequence(afMode, afState, focusComplete);
                            precaptureComplete = verifyAeSequence(aeState, precaptureComplete);

                            CaptureResult sequenceResult = captureListener.getCaptureResult(
                                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
                            afState = sequenceResult.get(CaptureResult.CONTROL_AF_STATE);
                            aeState = sequenceResult.get(CaptureResult.CONTROL_AE_STATE);
                        }

                        assertTrue("Precapture sequence never completed!", precaptureComplete);
                        assertTrue("Focus sequence never completed!", focusComplete);

                        // Done

                        stopCapture(/*fast*/ false);
                        preview.release();

                    }
                }
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testAbandonRepeatingRequestSurface() throws Exception {
        for (String id : mCameraIds) {
            Log.i(TAG, String.format(
                    "Testing Camera %s for abandoning surface of a repeating request", id));

            openDevice(id);
            try {
                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);

                CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
                SimpleCaptureCallback captureListener = new CameraTestUtils.SimpleCaptureCallback();

                int sequenceId = mCameraSession.setRepeatingRequest(previewRequest.build(),
                        captureListener, mHandler);

                for (int i = 0; i < PREVIEW_WARMUP_FRAMES; i++) {
                    captureListener.getTotalCaptureResult(CAPTURE_TIMEOUT);
                }

                // Abandon preview surface.
                preview.release();

                // Check onCaptureSequenceCompleted is received.
                long sequenceLastFrameNumber = captureListener.getCaptureSequenceLastFrameNumber(
                        sequenceId, CAPTURE_TIMEOUT);

                mCameraSession.stopRepeating();

                // Find the last frame number received in results and failures.
                long lastFrameNumber = -1;
                while (captureListener.hasMoreResults()) {
                    TotalCaptureResult result = captureListener.getTotalCaptureResult(
                            CAPTURE_TIMEOUT);
                    if (lastFrameNumber < result.getFrameNumber()) {
                        lastFrameNumber = result.getFrameNumber();
                    }
                }

                while (captureListener.hasMoreFailures()) {
                    ArrayList<CaptureFailure> failures = captureListener.getCaptureFailures(
                            /*maxNumFailures*/ 1);
                    for (CaptureFailure failure : failures) {
                        if (lastFrameNumber < failure.getFrameNumber()) {
                            lastFrameNumber = failure.getFrameNumber();
                        }
                    }
                }

                // Verify the last frame number received from capture sequence completed matches the
                // the last frame number of the results and failures.
                assertEquals(String.format("Last frame number from onCaptureSequenceCompleted " +
                        "(%d) doesn't match the last frame number received from " +
                        "results/failures (%d)", sequenceLastFrameNumber, lastFrameNumber),
                        sequenceLastFrameNumber, lastFrameNumber);
            } finally {
                closeDevice(id);
            }
        }
    }

    private CaptureRequest.Builder preparePreviewTestSession(SurfaceTexture preview)
            throws Exception {
        Surface previewSurface = new Surface(preview);

        preview.setDefaultBufferSize(640, 480);

        ArrayList<Surface> sessionOutputs = new ArrayList<>();
        sessionOutputs.add(previewSurface);

        createSession(sessionOutputs);

        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        previewRequest.addTarget(previewSurface);

        return previewRequest;
    }

    private CaptureRequest.Builder prepareTriggerTestSession(
            SurfaceTexture preview, int aeMode, int afMode) throws Exception {
        Log.i(TAG, String.format("Testing AE mode %s, AF mode %s",
                        StaticMetadata.getAeModeName(aeMode),
                        StaticMetadata.getAfModeName(afMode)));

        CaptureRequest.Builder previewRequest = preparePreviewTestSession(preview);
        previewRequest.set(CaptureRequest.CONTROL_AE_MODE, aeMode);
        previewRequest.set(CaptureRequest.CONTROL_AF_MODE, afMode);

        return previewRequest;
    }

    private void cancelTriggersAndWait(CaptureRequest.Builder previewRequest,
            SimpleCaptureCallback captureListener, int afMode) throws Exception {
        previewRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);

        CaptureRequest triggerRequest = previewRequest.build();
        mCameraSession.capture(triggerRequest, captureListener, mHandler);

        // Wait for a few frames to initialize 3A

        CaptureResult previewResult = null;
        int afState;
        int aeState;

        for (int i = 0; i < PREVIEW_WARMUP_FRAMES; i++) {
            previewResult = captureListener.getCaptureResult(
                    CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS);
            if (VERBOSE) {
                afState = previewResult.get(CaptureResult.CONTROL_AF_STATE);
                aeState = previewResult.get(CaptureResult.CONTROL_AE_STATE);
                Log.v(TAG, String.format("AF state: %s, AE state: %s",
                                StaticMetadata.AF_STATE_NAMES[afState],
                                StaticMetadata.AE_STATE_NAMES[aeState]));
            }
        }

        // Verify starting states

        afState = previewResult.get(CaptureResult.CONTROL_AF_STATE);
        aeState = previewResult.get(CaptureResult.CONTROL_AE_STATE);

        switch (afMode) {
            case CaptureResult.CONTROL_AF_MODE_AUTO:
            case CaptureResult.CONTROL_AF_MODE_MACRO:
                assertTrue(String.format("AF state not INACTIVE, is %s",
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_INACTIVE);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                // After several frames, AF must no longer be in INACTIVE state
                assertTrue(String.format("In AF mode %s, AF state not PASSIVE_SCAN" +
                                ", PASSIVE_FOCUSED, or PASSIVE_UNFOCUSED, is %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED);
                break;
            default:
                fail("unexpected af mode");
        }

        // After several frames, AE must no longer be in INACTIVE state
        assertTrue(String.format("AE state must be SEARCHING, CONVERGED, " +
                        "or FLASH_REQUIRED, is %s", StaticMetadata.AE_STATE_NAMES[aeState]),
                aeState == CaptureResult.CONTROL_AE_STATE_SEARCHING ||
                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED);
    }

    private boolean verifyAfSequence(int afMode, int afState, boolean focusComplete) {
        if (focusComplete) {
            assertTrue(String.format("AF Mode %s: Focus lock lost after convergence: AF state: %s",
                            StaticMetadata.getAfModeName(afMode),
                            StaticMetadata.AF_STATE_NAMES[afState]),
                    afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState ==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
            return focusComplete;
        }
        if (VERBOSE) {
            Log.v(TAG, String.format("AF mode: %s, AF state: %s",
                            StaticMetadata.getAfModeName(afMode),
                            StaticMetadata.AF_STATE_NAMES[afState]));
        }
        switch (afMode) {
            case CaptureResult.CONTROL_AF_MODE_AUTO:
            case CaptureResult.CONTROL_AF_MODE_MACRO:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete =
                        (afState != CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN ||
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete =
                        (afState != CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN);
                break;
            case CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO:
                assertTrue(String.format("AF mode %s: Unexpected AF state %s",
                                StaticMetadata.getAfModeName(afMode),
                                StaticMetadata.AF_STATE_NAMES[afState]),
                        afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED);
                focusComplete = true;
                break;
            default:
                fail("Unexpected AF mode: " + StaticMetadata.getAfModeName(afMode));
        }
        return focusComplete;
    }

    private boolean verifyAeSequence(int aeState, boolean precaptureComplete) {
        if (precaptureComplete) {
            assertTrue("Precapture state seen after convergence",
                    aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE);
            return precaptureComplete;
        }
        if (VERBOSE) {
            Log.v(TAG, String.format("AE state: %s", StaticMetadata.AE_STATE_NAMES[aeState]));
        }
        switch (aeState) {
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                // scan still continuing
                break;
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                // completed
                precaptureComplete = true;
                break;
            default:
                fail(String.format("Precapture sequence transitioned to "
                                + "state %s incorrectly!", StaticMetadata.AE_STATE_NAMES[aeState]));
                break;
        }
        return precaptureComplete;
    }

    /**
     * Sanity check the configuration tables.
     */
    private void sanityCheckConfigurationTables(final int[][][] tables) throws Exception {
        int tableIdx = 0;
        for (int[][] table : tables) {
            int rowIdx = 0;
            for (int[] row : table) {
                assertTrue(String.format("Odd number of entries for table %d row %d: %s ",
                                tableIdx, rowIdx, Arrays.toString(row)),
                        (row.length % 2) == 0);
                for (int i = 0; i < row.length; i += 2) {
                    int format = row[i];
                    int maxSize = row[i + 1];
                    assertTrue(String.format("table %d row %d index %d format not valid: %d",
                                    tableIdx, rowIdx, i, format),
                            format == PRIV || format == JPEG || format == YUV || format == RAW);
                    assertTrue(String.format("table %d row %d index %d max size not valid: %d",
                                    tableIdx, rowIdx, i + 1, maxSize),
                            maxSize == PREVIEW || maxSize == RECORD ||
                            maxSize == MAXIMUM || maxSize == VGA);
                }
                rowIdx++;
            }
            tableIdx++;
        }
    }

    /**
     * Simple holder for resolutions to use for different camera outputs and size limits.
     */
    static class MaxStreamSizes {
        // Format shorthands
        static final int PRIV = ImageFormat.PRIVATE;
        static final int JPEG = ImageFormat.JPEG;
        static final int YUV  = ImageFormat.YUV_420_888;
        static final int RAW  = ImageFormat.RAW_SENSOR;

        // Max resolution indices
        static final int PREVIEW = 0;
        static final int RECORD  = 1;
        static final int MAXIMUM = 2;
        static final int VGA = 3;
        static final int RESOLUTION_COUNT = 4;

        public MaxStreamSizes(StaticMetadata sm, String cameraId, Context context) {
            Size[] privSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.PRIVATE,
                    StaticMetadata.StreamDirection.Output);
            Size[] yuvSizes = sm.getAvailableSizesForFormatChecked(ImageFormat.YUV_420_888,
                    StaticMetadata.StreamDirection.Output);
            Size[] jpegSizes = sm.getJpegOutputSizesChecked();
            Size[] rawSizes = sm.getRawOutputSizesChecked();

            Size maxPreviewSize = getMaxPreviewSize(context, cameraId);

            maxRawSize = (rawSizes.length != 0) ? CameraTestUtils.getMaxSize(rawSizes) : null;

            if (sm.isColorOutputSupported()) {
                maxPrivSizes[PREVIEW] = getMaxSize(privSizes, maxPreviewSize);
                maxYuvSizes[PREVIEW]  = getMaxSize(yuvSizes, maxPreviewSize);
                maxJpegSizes[PREVIEW] = getMaxSize(jpegSizes, maxPreviewSize);

                maxPrivSizes[RECORD] = getMaxRecordingSize(cameraId);
                maxYuvSizes[RECORD]  = getMaxRecordingSize(cameraId);
                maxJpegSizes[RECORD] = getMaxRecordingSize(cameraId);

                maxPrivSizes[MAXIMUM] = CameraTestUtils.getMaxSize(privSizes);
                maxYuvSizes[MAXIMUM] = CameraTestUtils.getMaxSize(yuvSizes);
                maxJpegSizes[MAXIMUM] = CameraTestUtils.getMaxSize(jpegSizes);

                // Must always be supported, add unconditionally
                final Size vgaSize = new Size(640, 480);
                maxPrivSizes[VGA] = vgaSize;
                maxYuvSizes[VGA] = vgaSize;
                maxJpegSizes[VGA] = vgaSize;
            }

            StreamConfigurationMap configs = sm.getCharacteristics().get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] privInputSizes = configs.getInputSizes(ImageFormat.PRIVATE);
            maxInputPrivSize = privInputSizes != null ?
                    CameraTestUtils.getMaxSize(privInputSizes) : null;
            Size[] yuvInputSizes = configs.getInputSizes(ImageFormat.YUV_420_888);
            maxInputYuvSize = yuvInputSizes != null ?
                    CameraTestUtils.getMaxSize(yuvInputSizes) : null;

        }

        public final Size[] maxPrivSizes = new Size[RESOLUTION_COUNT];
        public final Size[] maxJpegSizes = new Size[RESOLUTION_COUNT];
        public final Size[] maxYuvSizes = new Size[RESOLUTION_COUNT];
        public final Size maxRawSize;
        // TODO: support non maximum reprocess input.
        public final Size maxInputPrivSize;
        public final Size maxInputYuvSize;

        static public String configToString(int[] config) {
            StringBuilder b = new StringBuilder("{ ");
            for (int i = 0; i < config.length; i += 2) {
                int format = config[i];
                int sizeLimit = config[i + 1];

                appendFormatSize(b, format, sizeLimit);
                b.append(" ");
            }
            b.append("}");
            return b.toString();
        }

        static public String reprocessConfigToString(int[] reprocessConfig) {
            // reprocessConfig[0..1] is the input configuration
            StringBuilder b = new StringBuilder("Input: ");
            appendFormatSize(b, reprocessConfig[0], reprocessConfig[1]);

            // reprocessConfig[0..1] is also output configuration to be captured as reprocess input.
            b.append(", Outputs: { ");
            for (int i = 0; i < reprocessConfig.length; i += 2) {
                int format = reprocessConfig[i];
                int sizeLimit = reprocessConfig[i + 1];

                appendFormatSize(b, format, sizeLimit);
                b.append(" ");
            }
            b.append("}");
            return b.toString();
        }

        static private void appendFormatSize(StringBuilder b, int format, int Size) {
            switch (format) {
                case PRIV:
                    b.append("[PRIV, ");
                    break;
                case JPEG:
                    b.append("[JPEG, ");
                    break;
                case YUV:
                    b.append("[YUV, ");
                    break;
                case RAW:
                    b.append("[RAW, ");
                    break;
                default:
                    b.append("[UNK, ");
                    break;
            }

            switch (Size) {
                case PREVIEW:
                    b.append("PREVIEW]");
                    break;
                case RECORD:
                    b.append("RECORD]");
                    break;
                case MAXIMUM:
                    b.append("MAXIMUM]");
                    break;
                case VGA:
                    b.append("VGA]");
                    break;
                default:
                    b.append("UNK]");
                    break;
            }
        }
    }

    /**
     * Return an InputConfiguration for a given reprocess configuration.
     */
    private InputConfiguration getInputConfig(int[] reprocessConfig, MaxStreamSizes maxSizes) {
        int format;
        Size size;

        if (reprocessConfig[1] != MAXIMUM) {
            throw new IllegalArgumentException("Test only supports MAXIMUM input");
        }

        switch (reprocessConfig[0]) {
            case PRIV:
                format = ImageFormat.PRIVATE;
                size = maxSizes.maxInputPrivSize;
                break;
            case YUV:
                format = ImageFormat.YUV_420_888;
                size = maxSizes.maxInputYuvSize;
                break;
            default:
                throw new IllegalArgumentException("Input format not supported: " +
                        reprocessConfig[0]);
        }

        return new InputConfiguration(size.getWidth(), size.getHeight(), format);
    }

    private void testReprocessStreamCombination(String cameraId, int[] reprocessConfig,
            MaxStreamSizes maxSizes, StaticMetadata staticInfo) throws Exception {

        Log.i(TAG, String.format("Testing Camera %s, reprocess config: %s", cameraId,
                MaxStreamSizes.reprocessConfigToString(reprocessConfig)));

        final int TIMEOUT_FOR_RESULT_MS = 3000;
        final int NUM_REPROCESS_CAPTURES_PER_CONFIG = 3;

        List<SurfaceTexture> privTargets = new ArrayList<>();
        List<ImageReader> jpegTargets = new ArrayList<>();
        List<ImageReader> yuvTargets = new ArrayList<>();
        List<ImageReader> rawTargets = new ArrayList<>();
        List<Surface> outputSurfaces = new ArrayList<>();
        ImageReader inputReader = null;
        ImageWriter inputWriter = null;
        SimpleImageReaderListener inputReaderListener = new SimpleImageReaderListener();
        SimpleCaptureCallback inputCaptureListener = new SimpleCaptureCallback();
        SimpleCaptureCallback reprocessOutputCaptureListener = new SimpleCaptureCallback();

        boolean supportYuvReprocess = staticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING);
        boolean supportOpaqueReprocess = staticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING);

        // Skip the configuration if the format is not supported for reprocessing.
        if ((reprocessConfig[0] == YUV && !supportYuvReprocess) ||
                (reprocessConfig[0] == PRIV && !supportOpaqueReprocess)) {
            return;
        }

        try {
            // reprocessConfig[2..] are additional outputs
            setupConfigurationTargets(
                    Arrays.copyOfRange(reprocessConfig, 2, reprocessConfig.length),
                    maxSizes, privTargets, jpegTargets, yuvTargets, rawTargets, outputSurfaces,
                    NUM_REPROCESS_CAPTURES_PER_CONFIG);

            // reprocessConfig[0:1] is input
            InputConfiguration inputConfig = getInputConfig(
                    Arrays.copyOfRange(reprocessConfig, 0, 2), maxSizes);

            // For each config, YUV and JPEG outputs will be tested. (For YUV reprocessing,
            // the YUV ImageReader for input is also used for output.)
            final int totalNumReprocessCaptures =  NUM_REPROCESS_CAPTURES_PER_CONFIG * (
                    (inputConfig.getFormat() == ImageFormat.YUV_420_888 ? 1 : 0) +
                    jpegTargets.size() + yuvTargets.size());

            // It needs 1 input buffer for each reprocess capture + the number of buffers
            // that will be used as outputs.
            inputReader = ImageReader.newInstance(inputConfig.getWidth(), inputConfig.getHeight(),
                    inputConfig.getFormat(),
                    totalNumReprocessCaptures + NUM_REPROCESS_CAPTURES_PER_CONFIG);
            inputReader.setOnImageAvailableListener(inputReaderListener, mHandler);
            outputSurfaces.add(inputReader.getSurface());

            // Verify we can create a reprocessable session with the input and all outputs.
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            CameraCaptureSession session = configureReprocessableCameraSession(mCamera,
                    inputConfig, outputSurfaces, sessionListener, mHandler);
            inputWriter = ImageWriter.newInstance(session.getInputSurface(),
                    totalNumReprocessCaptures);

            // Prepare a request for reprocess input
            CaptureRequest.Builder builder = mCamera.createCaptureRequest(
                    CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG);
            builder.addTarget(inputReader.getSurface());

            for (int i = 0; i < totalNumReprocessCaptures; i++) {
                session.capture(builder.build(), inputCaptureListener, mHandler);
            }

            List<CaptureRequest> reprocessRequests = new ArrayList<>();
            List<Surface> reprocessOutputs = new ArrayList<>();
            if (inputConfig.getFormat() == ImageFormat.YUV_420_888) {
                reprocessOutputs.add(inputReader.getSurface());
            }

            for (ImageReader reader : jpegTargets) {
                reprocessOutputs.add(reader.getSurface());
            }

            for (ImageReader reader : yuvTargets) {
                reprocessOutputs.add(reader.getSurface());
            }

            for (int i = 0; i < NUM_REPROCESS_CAPTURES_PER_CONFIG; i++) {
                for (Surface output : reprocessOutputs) {
                    TotalCaptureResult result = inputCaptureListener.getTotalCaptureResult(
                            TIMEOUT_FOR_RESULT_MS);
                    builder =  mCamera.createReprocessCaptureRequest(result);
                    inputWriter.queueInputImage(
                            inputReaderListener.getImage(TIMEOUT_FOR_RESULT_MS));
                    builder.addTarget(output);
                    reprocessRequests.add(builder.build());
                }
            }

            session.captureBurst(reprocessRequests, reprocessOutputCaptureListener, mHandler);

            for (int i = 0; i < reprocessOutputs.size() * NUM_REPROCESS_CAPTURES_PER_CONFIG; i++) {
                TotalCaptureResult result = reprocessOutputCaptureListener.getTotalCaptureResult(
                        TIMEOUT_FOR_RESULT_MS);
            }
        } catch (Throwable e) {
            mCollector.addMessage(String.format("Reprocess stream combination %s failed due to: %s",
                    MaxStreamSizes.reprocessConfigToString(reprocessConfig), e.getMessage()));
        } finally {
            inputReaderListener.drain();
            reprocessOutputCaptureListener.drain();

            for (SurfaceTexture target : privTargets) {
                target.release();
            }

            for (ImageReader target : jpegTargets) {
                target.close();
            }

            for (ImageReader target : yuvTargets) {
                target.close();
            }

            for (ImageReader target : rawTargets) {
                target.close();
            }

            if (inputReader != null) {
                inputReader.close();
            }

            if (inputWriter != null) {
                inputWriter.close();
            }
        }
    }

    private void testOutputCombination(String cameraId, int[] config, MaxStreamSizes maxSizes)
            throws Exception {

        Log.i(TAG, String.format("Testing Camera %s, config %s",
                        cameraId, MaxStreamSizes.configToString(config)));

        // Timeout is relaxed by 1 second for LEGACY devices to reduce false positive rate in CTS
        final int TIMEOUT_FOR_RESULT_MS = (mStaticInfo.isHardwareLevelLegacy()) ? 2000 : 1000;
        final int MIN_RESULT_COUNT = 3;

        // Set up outputs
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        List<SurfaceTexture> privTargets = new ArrayList<SurfaceTexture>();
        List<ImageReader> jpegTargets = new ArrayList<ImageReader>();
        List<ImageReader> yuvTargets = new ArrayList<ImageReader>();
        List<ImageReader> rawTargets = new ArrayList<ImageReader>();

        setupConfigurationTargets(config, maxSizes, privTargets, jpegTargets, yuvTargets,
                rawTargets, outputSurfaces, MIN_RESULT_COUNT);

        boolean haveSession = false;
        try {
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (Surface s : outputSurfaces) {
                requestBuilder.addTarget(s);
            }

            CameraCaptureSession.CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            createSession(outputSurfaces);
            haveSession = true;
            CaptureRequest request = requestBuilder.build();
            mCameraSession.setRepeatingRequest(request, mockCaptureCallback, mHandler);

            verify(mockCaptureCallback,
                    timeout(TIMEOUT_FOR_RESULT_MS * MIN_RESULT_COUNT).atLeast(MIN_RESULT_COUNT))
                    .onCaptureCompleted(
                        eq(mCameraSession),
                        eq(request),
                        isA(TotalCaptureResult.class));
            verify(mockCaptureCallback, never()).
                    onCaptureFailed(
                        eq(mCameraSession),
                        eq(request),
                        isA(CaptureFailure.class));

        } catch (Throwable e) {
            mCollector.addMessage(String.format("Output combination %s failed due to: %s",
                    MaxStreamSizes.configToString(config), e.getMessage()));
        }
        if (haveSession) {
            try {
                Log.i(TAG, String.format("Done with camera %s, config %s, closing session",
                                cameraId, MaxStreamSizes.configToString(config)));
                stopCapture(/*fast*/false);
            } catch (Throwable e) {
                mCollector.addMessage(
                    String.format("Closing down for output combination %s failed due to: %s",
                            MaxStreamSizes.configToString(config), e.getMessage()));
            }
        }

        for (SurfaceTexture target : privTargets) {
            target.release();
        }
        for (ImageReader target : jpegTargets) {
            target.close();
        }
        for (ImageReader target : yuvTargets) {
            target.close();
        }
        for (ImageReader target : rawTargets) {
            target.close();
        }
    }

    private void setupConfigurationTargets(int[] outputConfigs, MaxStreamSizes maxSizes,
            List<SurfaceTexture> privTargets, List<ImageReader> jpegTargets,
            List<ImageReader> yuvTargets, List<ImageReader> rawTargets,
            List<Surface> outputSurfaces, int numBuffers) {

        ImageDropperListener imageDropperListener = new ImageDropperListener();

        for (int i = 0; i < outputConfigs.length; i += 2) {
            int format = outputConfigs[i];
            int sizeLimit = outputConfigs[i + 1];

            switch (format) {
                case PRIV: {
                    Size targetSize = maxSizes.maxPrivSizes[sizeLimit];
                    SurfaceTexture target = new SurfaceTexture(/*random int*/1);
                    target.setDefaultBufferSize(targetSize.getWidth(), targetSize.getHeight());
                    outputSurfaces.add(new Surface(target));
                    privTargets.add(target);
                    break;
                }
                case JPEG: {
                    Size targetSize = maxSizes.maxJpegSizes[sizeLimit];
                    ImageReader target = ImageReader.newInstance(
                        targetSize.getWidth(), targetSize.getHeight(), JPEG, numBuffers);
                    target.setOnImageAvailableListener(imageDropperListener, mHandler);
                    outputSurfaces.add(target.getSurface());
                    jpegTargets.add(target);
                    break;
                }
                case YUV: {
                    Size targetSize = maxSizes.maxYuvSizes[sizeLimit];
                    ImageReader target = ImageReader.newInstance(
                        targetSize.getWidth(), targetSize.getHeight(), YUV, numBuffers);
                    target.setOnImageAvailableListener(imageDropperListener, mHandler);
                    outputSurfaces.add(target.getSurface());
                    yuvTargets.add(target);
                    break;
                }
                case RAW: {
                    Size targetSize = maxSizes.maxRawSize;
                    ImageReader target = ImageReader.newInstance(
                        targetSize.getWidth(), targetSize.getHeight(), RAW, numBuffers);
                    target.setOnImageAvailableListener(imageDropperListener, mHandler);
                    outputSurfaces.add(target.getSurface());
                    rawTargets.add(target);
                    break;
                }
                default:
                    fail("Unknown output format " + format);
            }
        }
    }

    private static Size getMaxRecordingSize(String cameraId) {
        int id = Integer.valueOf(cameraId);

        int quality =
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_2160P) ?
                    CamcorderProfile.QUALITY_2160P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_1080P) ?
                    CamcorderProfile.QUALITY_1080P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_720P) ?
                    CamcorderProfile.QUALITY_720P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_480P) ?
                    CamcorderProfile.QUALITY_480P :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_QVGA) ?
                    CamcorderProfile.QUALITY_QVGA :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_CIF) ?
                    CamcorderProfile.QUALITY_CIF :
                CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_QCIF) ?
                    CamcorderProfile.QUALITY_QCIF :
                    -1;

        assertTrue("No recording supported for camera id " + cameraId, quality != -1);

        CamcorderProfile maxProfile = CamcorderProfile.get(id, quality);
        return new Size(maxProfile.videoFrameWidth, maxProfile.videoFrameHeight);
    }

    /**
     * Get maximum size in list that's equal or smaller to than the bound.
     * Returns null if no size is smaller than or equal to the bound.
     */
    private static Size getMaxSize(Size[] sizes, Size bound) {
        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }

        Size sz = null;
        for (Size size : sizes) {
            if (size.getWidth() <= bound.getWidth() && size.getHeight() <= bound.getHeight()) {

                if (sz == null) {
                    sz = size;
                } else {
                    long curArea = sz.getWidth() * (long) sz.getHeight();
                    long newArea = size.getWidth() * (long) size.getHeight();
                    if ( newArea > curArea ) {
                        sz = size;
                    }
                }
            }
        }

        assertTrue("No size under bound found: " + Arrays.toString(sizes) + " bound " + bound,
                sz != null);

        return sz;
    }

    private static Size getMaxPreviewSize(Context context, String cameraId) {
        try {
            WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();

            int width = display.getWidth();
            int height = display.getHeight();

            if (height > width) {
                height = width;
                width = display.getHeight();
            }

            CameraManager camMgr =
                (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            List<Size> orderedPreviewSizes = CameraTestUtils.getSupportedPreviewSizes(
                cameraId, camMgr, PREVIEW_SIZE_BOUND);

            if (orderedPreviewSizes != null) {
                for (Size size : orderedPreviewSizes) {
                    if (width >= size.getWidth() &&
                        height >= size.getHeight())
                        return size;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getMaxPreviewSize Failed. "+e.toString());
        }
        return PREVIEW_SIZE_BOUND;
    }
}
