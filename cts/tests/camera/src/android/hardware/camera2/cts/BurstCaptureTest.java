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

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import java.util.List;
import java.util.ArrayList;

public class BurstCaptureTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "BurstCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /**
     * Test YUV burst capture with full-AUTO control.
     * Also verifies sensor settings operation if READ_SENSOR_SETTINGS is available.
     */
    public void testYuvBurst() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                String id = mCameraIds[i];
                Log.i(TAG, "Testing YUV Burst for camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                }
                if (!mStaticInfo.isAeLockSupported() || !mStaticInfo.isAwbLockSupported()) {
                    Log.i(TAG, "AE/AWB lock is not supported in camera " + id +
                            ". Skip the test");
                    continue;
                }

                if (mStaticInfo.isHardwareLevelLegacy()) {
                    Log.i(TAG, "Legacy camera doesn't report min frame duration" +
                            ". Skip the test");
                    continue;
                }

                yuvBurstTestByCamera(id);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    private void yuvBurstTestByCamera(String cameraId) throws Exception {
        // Parameters
        final int MAX_CONVERGENCE_FRAMES = 150; // 5 sec at 30fps
        final long MAX_PREVIEW_RESULT_TIMEOUT_MS = 1000;
        final int BURST_SIZE = 100;
        final float FRAME_DURATION_MARGIN_FRACTION = 0.1f;

        // Find a good preview size (bound to 1080p)
        final Size previewSize = mOrderedPreviewSizes.get(0);

        // Get maximum YUV_420_888 size
        final Size stillSize = getSortedSizesForFormat(
                cameraId, mCameraManager, ImageFormat.YUV_420_888, /*bound*/null).get(0);

        // Find max pipeline depth and sync latency
        final int maxPipelineDepth = mStaticInfo.getCharacteristics().get(
            CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH);
        final int maxSyncLatency = mStaticInfo.getCharacteristics().get(
            CameraCharacteristics.SYNC_MAX_LATENCY);

        // Find minimum frame duration for full-res YUV_420_888
        StreamConfigurationMap config = mStaticInfo.getCharacteristics().get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        final long minStillFrameDuration =
                config.getOutputMinFrameDuration(ImageFormat.YUV_420_888, stillSize);


        Range<Integer> targetRange = getSuitableFpsRangeForDuration(cameraId, minStillFrameDuration);

        Log.i(TAG, String.format("Selected frame rate range %d - %d for YUV burst",
                        targetRange.getLower(), targetRange.getUpper()));

        // Check if READ_SENSOR_SETTINGS is supported
        final boolean checkSensorSettings = mStaticInfo.isCapabilitySupported(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);

        // Configure basic preview and burst settings

        CaptureRequest.Builder previewBuilder =
            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder burstBuilder =
            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                targetRange);
        burstBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                targetRange);
        burstBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        burstBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);

        // Create session and start up preview

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        ImageDropperListener imageDropper = new ImageDropperListener();

        prepareCaptureAndStartPreview(
            previewBuilder, burstBuilder,
            previewSize, stillSize,
            ImageFormat.YUV_420_888, resultListener,
            /*maxNumImages*/ 3, imageDropper);

        // Create burst

        List<CaptureRequest> burst = new ArrayList<>();
        for (int i = 0; i < BURST_SIZE; i++) {
            burst.add(burstBuilder.build());
        }

        // Converge AE/AWB

        int frameCount = 0;
        while (true) {
            CaptureResult result = resultListener.getCaptureResult(MAX_PREVIEW_RESULT_TIMEOUT_MS);
            int aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            int awbState = result.get(CaptureResult.CONTROL_AWB_STATE);

            if (DEBUG) {
                Log.d(TAG, "aeState: " + aeState + ". awbState: " + awbState);
            }

            if ((aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                    aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) &&
                    awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED) {
                break;
            }
            frameCount++;
            assertTrue(String.format("Cam %s: Can not converge AE and AWB within %d frames",
                    cameraId, MAX_CONVERGENCE_FRAMES),
                frameCount < MAX_CONVERGENCE_FRAMES);
        }

        // Lock AF if there's a focuser

        if (mStaticInfo.hasFocuser()) {
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
            mSession.capture(previewBuilder.build(), resultListener, mHandler);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_IDLE);

            frameCount = 0;
            while (true) {
                CaptureResult result = resultListener.getCaptureResult(MAX_PREVIEW_RESULT_TIMEOUT_MS);
                int afState = result.get(CaptureResult.CONTROL_AF_STATE);

                if (DEBUG) {
                    Log.d(TAG, "afState: " + afState);
                }

                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                    break;
                }
                frameCount++;
                assertTrue(String.format("Cam %s: Cannot lock AF within %d frames", cameraId,
                        MAX_CONVERGENCE_FRAMES),
                    frameCount < MAX_CONVERGENCE_FRAMES);
            }
        }

        // Lock AE/AWB

        previewBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        previewBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);

        CaptureRequest lockedRequest = previewBuilder.build();
        mSession.setRepeatingRequest(lockedRequest, resultListener, mHandler);

        // Wait for first result with locking
        resultListener.drain();
        CaptureResult lockedResult =
                resultListener.getCaptureResultForRequest(lockedRequest, maxPipelineDepth);

        int pipelineDepth = lockedResult.get(CaptureResult.REQUEST_PIPELINE_DEPTH);

        // Then start waiting on results to get the first result that should be synced
        // up, and also fire the burst as soon as possible

        if (maxSyncLatency == CameraCharacteristics.SYNC_MAX_LATENCY_PER_FRAME_CONTROL) {
            // The locked result we have is already synchronized so start the burst
            mSession.captureBurst(burst, resultListener, mHandler);
        } else {
            // Need to get a synchronized result, and may need to start burst later to
            // be synchronized correctly

            boolean burstSent = false;

            // Calculate how many requests we need to still send down to camera before we
            // know the settings have settled for the burst

            int numFramesWaited = maxSyncLatency;
            if (numFramesWaited == CameraCharacteristics.SYNC_MAX_LATENCY_UNKNOWN) {
                numFramesWaited = NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY;
            }

            int requestsNeededToSync = numFramesWaited - pipelineDepth;
            for (int i = 0; i < numFramesWaited; i++) {
                if (!burstSent && requestsNeededToSync <= 0) {
                    mSession.captureBurst(burst, resultListener, mHandler);
                    burstSent = true;
                }
                lockedResult = resultListener.getCaptureResult(MAX_PREVIEW_RESULT_TIMEOUT_MS);
                requestsNeededToSync--;
            }

            assertTrue("Cam " + cameraId + ": Burst failed to fire!", burstSent);
        }

        // Read in locked settings if supported

        long burstExposure = 0;
        long burstFrameDuration = 0;
        int burstSensitivity = 0;
        if (checkSensorSettings) {
            burstExposure = lockedResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            burstFrameDuration = lockedResult.get(CaptureResult.SENSOR_FRAME_DURATION);
            burstSensitivity = lockedResult.get(CaptureResult.SENSOR_SENSITIVITY);

            assertTrue(String.format("Cam %s: Frame duration %d ns too short compared to " +
                    "exposure time %d ns", cameraId, burstFrameDuration, burstExposure),
                burstFrameDuration >= burstExposure);

            assertTrue(String.format("Cam %s: Exposure time is not valid: %d",
                    cameraId, burstExposure),
                burstExposure > 0);
            assertTrue(String.format("Cam %s: Frame duration is not valid: %d",
                    cameraId, burstFrameDuration),
                burstFrameDuration > 0);
            assertTrue(String.format("Cam %s: Sensitivity is not valid: %d",
                    cameraId, burstSensitivity),
                burstSensitivity > 0);
        }

        // Process burst results
        int burstIndex = 0;
        CaptureResult burstResult =
                resultListener.getCaptureResultForRequest(burst.get(burstIndex),
                    maxPipelineDepth + 1);
        long prevTimestamp = -1;
        final long frameDurationBound = (long)
                (minStillFrameDuration * (1 + FRAME_DURATION_MARGIN_FRACTION) );

        List<Long> frameDurations = new ArrayList<>();

        while(true) {
            // Verify the result
            assertTrue("Cam " + cameraId + ": Result doesn't match expected request",
                    burstResult.getRequest() == burst.get(burstIndex));

            // Verify locked settings
            if (checkSensorSettings) {
                long exposure = burstResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                int sensitivity = burstResult.get(CaptureResult.SENSOR_SENSITIVITY);
                assertTrue("Cam " + cameraId + ": Exposure not locked!",
                    exposure == burstExposure);
                assertTrue("Cam " + cameraId + ": Sensitivity not locked!",
                    sensitivity == burstSensitivity);
            }

            // Collect inter-frame durations
            long timestamp = burstResult.get(CaptureResult.SENSOR_TIMESTAMP);
            if (prevTimestamp != -1) {
                long frameDuration = timestamp - prevTimestamp;
                frameDurations.add(frameDuration);
                if (DEBUG) {
                    Log.i(TAG, String.format("Frame %03d    Duration %.2f ms", burstIndex,
                            frameDuration/1e6));
                }
            }
            prevTimestamp = timestamp;

            // Get next result
            burstIndex++;
            if (burstIndex == BURST_SIZE) break;
            burstResult = resultListener.getCaptureResult(MAX_PREVIEW_RESULT_TIMEOUT_MS);
        }

        // Verify inter-frame durations

        long meanFrameSum = 0;
        for (Long duration : frameDurations) {
            meanFrameSum += duration;
        }
        float meanFrameDuration = (float) meanFrameSum / frameDurations.size();

        float stddevSum = 0;
        for (Long duration : frameDurations) {
            stddevSum += (duration - meanFrameDuration) * (duration - meanFrameDuration);
        }
        float stddevFrameDuration = (float)
                Math.sqrt(1.f / (frameDurations.size() - 1 ) * stddevSum);

        Log.i(TAG, String.format("Cam %s: Burst frame duration mean: %.1f, stddev: %.1f", cameraId,
                meanFrameDuration, stddevFrameDuration));

        assertTrue(
            String.format("Cam %s: Burst frame duration mean %.1f ns is larger than acceptable, " +
                "expecting below %d ns, allowing below %d", cameraId,
                meanFrameDuration, minStillFrameDuration, frameDurationBound),
            meanFrameDuration <= frameDurationBound);

        // Calculate upper 97.5% bound (assuming durations are normally distributed...)
        float limit95FrameDuration = meanFrameDuration + 2 * stddevFrameDuration;

        // Don't enforce this yet, but warn
        if (limit95FrameDuration > frameDurationBound) {
            Log.w(TAG,
                String.format("Cam %s: Standard deviation is too large compared to limit: " +
                    "mean: %.1f ms, stddev: %.1f ms: 95%% bound: %f ms", cameraId,
                    meanFrameDuration/1e6, stddevFrameDuration/1e6,
                    limit95FrameDuration/1e6));
        }
    }
}
