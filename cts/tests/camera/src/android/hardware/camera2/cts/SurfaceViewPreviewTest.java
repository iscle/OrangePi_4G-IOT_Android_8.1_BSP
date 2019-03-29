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
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Size;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.OutputConfiguration;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import org.mockito.ArgumentCaptor;
import org.mockito.compat.ArgumentMatcher;

import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CameraDevice preview test by using SurfaceView.
 */
public class SurfaceViewPreviewTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "SurfaceViewPreviewTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int FRAME_TIMEOUT_MS = 1000;
    private static final int NUM_FRAMES_VERIFIED = 30;
    private static final int NUM_TEST_PATTERN_FRAMES_VERIFIED = 60;
    private static final float FRAME_DURATION_ERROR_MARGIN = 0.01f; // 1 percent error margin.
    private static final int PREPARE_TIMEOUT_MS = 10000; // 10 s

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test all supported preview sizes for each camera device.
     * <p>
     * For the first  {@link #NUM_FRAMES_VERIFIED}  of capture results,
     * the {@link CaptureCallback} callback availability and the capture timestamp
     * (monotonically increasing) ordering are verified.
     * </p>
     */
    public void testCameraPreview() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing preview for Camera " + mCameraIds[i]);
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                previewTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Basic test pattern mode preview.
     * <p>
     * Only test the test pattern preview and capture result, the image buffer
     * is not validated.
     * </p>
     */
    public void testBasicTestPatternPreview() throws Exception{
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing preview for Camera " + mCameraIds[i]);
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                previewTestPatternTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test {@link CaptureRequest#CONTROL_AE_TARGET_FPS_RANGE} for preview, validate the preview
     * frame duration and exposure time.
     */
    public void testPreviewFpsRange() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                previewFpsRangeTestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test surface set streaming use cases.
     *
     * <p>
     * The test sets output configuration with increasing surface set IDs for preview and YUV
     * streams. The max supported preview size is selected for preview stream, and the max
     * supported YUV size (depending on hw supported level) is selected for YUV stream. This test
     * also exercises the prepare API.
     * </p>
     */
    public void testSurfaceSet() throws Exception {
        for (String id : mCameraIds) {
            try {
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                surfaceSetTestByCamera(id);
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * Test to verify the {@link CameraCaptureSession#prepare} method works correctly, and has the
     * expected effects on performance.
     *
     * - Ensure that prepare() results in onSurfacePrepared() being invoked
     * - Ensure that prepare() does not cause preview glitches while operating
     * - Ensure that starting to use a newly-prepared output does not cause additional
     *   preview glitches to occur
     */
    public void testPreparePerformance() throws Throwable {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                preparePerformanceTestByCamera(mCameraIds[i]);
            }
            finally {
                closeDevice();
            }
        }
    }

    private void preparePerformanceTestByCamera(String cameraId) throws Exception {
        final int MAX_IMAGES_TO_PREPARE = 10;
        final int UNKNOWN_LATENCY_RESULT_WAIT = 5;
        final int MAX_RESULTS_TO_WAIT = 10;
        final int FRAMES_FOR_AVERAGING = 100;
        final float PREPARE_FRAME_RATE_BOUNDS = 0.05f; // fraction allowed difference
        final float PREPARE_PEAK_RATE_BOUNDS = 0.5f; // fraction allowed difference

        Size maxYuvSize = getSupportedPreviewSizes(cameraId, mCameraManager, null).get(0);
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);

        // Don't need image data, just drop it right away to minimize overhead
        ImageDropperListener imageListener = new ImageDropperListener();

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

        // Configure outputs and session

        updatePreviewSurface(maxPreviewSize);

        createImageReader(maxYuvSize, ImageFormat.YUV_420_888, MAX_IMAGES_TO_PREPARE, imageListener);

        List<Surface> outputSurfaces = new ArrayList<Surface>();
        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(mReaderSurface);

        CameraCaptureSession.StateCallback mockSessionListener =
                mock(CameraCaptureSession.StateCallback.class);

        mSession = configureCameraSession(mCamera, outputSurfaces, mockSessionListener, mHandler);

        previewRequest.addTarget(mPreviewSurface);
        Range<Integer> maxFpsTarget = mStaticInfo.getAeMaxTargetFpsRange();
        previewRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, maxFpsTarget);

        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);

        // Converge AE
        waitForAeStable(resultListener, UNKNOWN_LATENCY_RESULT_WAIT);

        if (mStaticInfo.isAeLockSupported()) {
            // Lock AE if possible to improve stability
            previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, true);
            mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
            if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
                // Legacy mode doesn't output AE state
                waitForResultValue(resultListener, CaptureResult.CONTROL_AE_STATE,
                        CaptureResult.CONTROL_AE_STATE_LOCKED, MAX_RESULTS_TO_WAIT);
            }
        }

        // Measure frame rate for a bit
        Pair<Long, Long> frameDurationStats =
                measureMeanFrameInterval(resultListener, FRAMES_FOR_AVERAGING, /*prevTimestamp*/ 0);

        Log.i(TAG, String.format("Frame interval avg during normal preview: %f ms, peak %f ms",
                        frameDurationStats.first / 1e6, frameDurationStats.second / 1e6));

        // Drain results, do prepare
        resultListener.drain();

        mSession.prepare(mReaderSurface);

        verify(mockSessionListener,
                timeout(PREPARE_TIMEOUT_MS).times(1)).
                onSurfacePrepared(eq(mSession), eq(mReaderSurface));

        // Calculate frame rate during prepare

        int resultsReceived = (int) resultListener.getTotalNumFrames();
        if (resultsReceived > 2) {
            // Only verify frame rate if there are a couple of results
            Pair<Long, Long> whilePreparingFrameDurationStats =
                    measureMeanFrameInterval(resultListener, resultsReceived, /*prevTimestamp*/ 0);

            Log.i(TAG, String.format("Frame interval during prepare avg: %f ms, peak %f ms",
                            whilePreparingFrameDurationStats.first / 1e6,
                            whilePreparingFrameDurationStats.second / 1e6));

            if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
                mCollector.expectTrue(
                    String.format("Camera %s: Preview peak frame interval affected by prepare " +
                            "call: preview avg frame duration: %f ms, peak during prepare: %f ms",
                            cameraId,
                            frameDurationStats.first / 1e6,
                            whilePreparingFrameDurationStats.second / 1e6),
                    (whilePreparingFrameDurationStats.second <=
                            frameDurationStats.first * (1 + PREPARE_PEAK_RATE_BOUNDS)));
                mCollector.expectTrue(
                    String.format("Camera %s: Preview average frame interval affected by prepare " +
                            "call: preview avg frame duration: %f ms, during prepare: %f ms",
                            cameraId,
                            frameDurationStats.first / 1e6,
                            whilePreparingFrameDurationStats.first / 1e6),
                    (whilePreparingFrameDurationStats.first <=
                            frameDurationStats.first * (1 + PREPARE_FRAME_RATE_BOUNDS)));
            }
        }

        resultListener.drain();

        // Get at least one more preview result without prepared target
        CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        long prevTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);

        // Now use the prepared stream and ensure there are no hiccups from using it
        previewRequest.addTarget(mReaderSurface);

        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);

        Pair<Long, Long> preparedFrameDurationStats =
                measureMeanFrameInterval(resultListener, MAX_IMAGES_TO_PREPARE*2, prevTimestamp);

        Log.i(TAG, String.format("Frame interval with prepared stream added avg: %f ms, peak %f ms",
                        preparedFrameDurationStats.first / 1e6,
                        preparedFrameDurationStats.second / 1e6));

        if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
            mCollector.expectTrue(
                String.format("Camera %s: Preview peak frame interval affected by use of new " +
                        " stream: preview avg frame duration: %f ms, peak with new stream: %f ms",
                        cameraId,
                        frameDurationStats.first / 1e6, preparedFrameDurationStats.second / 1e6),
                (preparedFrameDurationStats.second <=
                        frameDurationStats.first * (1 + PREPARE_PEAK_RATE_BOUNDS)));
            mCollector.expectTrue(
                String.format("Camera %s: Preview average frame interval affected by use of new " +
                        "stream: preview avg frame duration: %f ms, with new stream: %f ms",
                        cameraId,
                        frameDurationStats.first / 1e6, preparedFrameDurationStats.first / 1e6),
                (preparedFrameDurationStats.first <=
                        frameDurationStats.first * (1 + PREPARE_FRAME_RATE_BOUNDS)));
        }
    }

    /**
     * Test to verify correct behavior with the same Surface object being used repeatedly with
     * different native internals, and multiple Surfaces pointing to the same actual consumer object
     */
    public void testSurfaceEquality() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                surfaceEqualityTestByCamera(mCameraIds[i]);
            }
            finally {
                closeDevice();
            }
        }
    }

    private void surfaceEqualityTestByCamera(String cameraId) throws Exception {
        final int SOME_FRAMES = 10;

        Size maxPreviewSize = mOrderedPreviewSizes.get(0);

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        // Create a SurfaceTexture for a second output
        SurfaceTexture sharedOutputTexture = new SurfaceTexture(/*random texture ID*/ 5);
        sharedOutputTexture.setDefaultBufferSize(maxPreviewSize.getWidth(),
                maxPreviewSize.getHeight());
        Surface sharedOutputSurface1 = new Surface(sharedOutputTexture);

        updatePreviewSurface(maxPreviewSize);

        List<Surface> outputSurfaces = new ArrayList<Surface>();
        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(sharedOutputSurface1);

        BlockingSessionCallback sessionListener =
                new BlockingSessionCallback();

        mSession = configureCameraSession(mCamera, outputSurfaces, sessionListener, mHandler);
        sessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_READY,
                SESSION_CONFIGURE_TIMEOUT_MS);

        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequest.addTarget(mPreviewSurface);
        previewRequest.addTarget(sharedOutputSurface1);

        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);

        // Wait to get some frames out
        waitForNumResults(resultListener, SOME_FRAMES);

        // Drain
        mSession.abortCaptures();
        sessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_READY,
                SESSION_CONFIGURE_TIMEOUT_MS);

        // Hide / unhide the SurfaceView to get a new target Surface
        recreatePreviewSurface();

        // And resize it again
        updatePreviewSurface(maxPreviewSize);

        // Create a second surface that targets the shared SurfaceTexture
        Surface sharedOutputSurface2 = new Surface(sharedOutputTexture);

        // Use the new Surfaces for a new session
        outputSurfaces.clear();
        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(sharedOutputSurface2);

        sessionListener = new BlockingSessionCallback();

        mSession = configureCameraSession(mCamera, outputSurfaces, sessionListener, mHandler);

        previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        previewRequest.addTarget(mPreviewSurface);
        previewRequest.addTarget(sharedOutputSurface2);

        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);

        // Wait to get some frames out
        waitForNumResults(resultListener, SOME_FRAMES);
    }

    /*
     * Verify creation of deferred surface capture sessions
     */
    public void testDeferredSurfaces() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                openDevice(mCameraIds[i]);
                if (mStaticInfo.isHardwareLevelLegacy()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] + " is legacy, skipping");
                    continue;
                }
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }

                testDeferredSurfacesByCamera(mCameraIds[i]);
            }
            finally {
                closeDevice();
            }
        }
    }

    private void testDeferredSurfacesByCamera(String cameraId) throws Exception {
        Size maxPreviewSize = m1080pBoundedOrderedPreviewSizes.get(0);

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        // Create a SurfaceTexture for a second output
        SurfaceTexture sharedOutputTexture = new SurfaceTexture(/*random texture ID*/ 5);
        sharedOutputTexture.setDefaultBufferSize(maxPreviewSize.getWidth(),
                maxPreviewSize.getHeight());
        Surface sharedOutputSurface1 = new Surface(sharedOutputTexture);

        class TextureAvailableListener implements SurfaceTexture.OnFrameAvailableListener {
            @Override
            public void onFrameAvailable(SurfaceTexture t) {
                mGotFrame = true;
            }
            public boolean gotFrame() { return mGotFrame; }

            private volatile boolean mGotFrame = false;
        }
        TextureAvailableListener textureAvailableListener = new TextureAvailableListener();

        sharedOutputTexture.setOnFrameAvailableListener(textureAvailableListener, mHandler);

        updatePreviewSurface(maxPreviewSize);

        // Create deferred outputs for surface view and surface texture
        OutputConfiguration surfaceViewOutput = new OutputConfiguration(maxPreviewSize,
                SurfaceHolder.class);
        OutputConfiguration surfaceTextureOutput = new OutputConfiguration(maxPreviewSize,
                SurfaceTexture.class);

        List<OutputConfiguration> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(surfaceViewOutput);
        outputSurfaces.add(surfaceTextureOutput);

        // Create non-deferred ImageReader output (JPEG for LIMITED-level compatibility)
        ImageDropperListener imageListener = new ImageDropperListener();
        createImageReader(mOrderedStillSizes.get(0), ImageFormat.JPEG, /*maxImages*/ 3,
                imageListener);
        OutputConfiguration jpegOutput =
                new OutputConfiguration(OutputConfiguration.SURFACE_GROUP_ID_NONE, mReaderSurface);
        outputSurfaces.add(jpegOutput);

        // Confirm that other surface types aren't supported for OutputConfiguration
        Class[] unsupportedClasses =
                {android.media.ImageReader.class, android.media.MediaCodec.class,
                 android.renderscript.Allocation.class, android.media.MediaRecorder.class};

        for (Class klass : unsupportedClasses) {
            try {
                OutputConfiguration bad = new OutputConfiguration(maxPreviewSize, klass);
                fail("OutputConfiguration allowed use of unsupported class " + klass);
            } catch (IllegalArgumentException e) {
                // expected
            }
        }

        // Confirm that zero surface size isn't supported for OutputConfiguration
        Size[] sizeZeros = { new Size(0, 0), new Size(1, 0), new Size(0, 1) };
        for (Size size : sizeZeros) {
            try {
                OutputConfiguration bad = new OutputConfiguration(size, SurfaceHolder.class);
                fail("OutputConfiguration allowed use of zero surfaceSize");
            } catch (IllegalArgumentException e) {
                //expected
            }
        }

        // Create session

        BlockingSessionCallback sessionListener =
                new BlockingSessionCallback();

        mSession = configureCameraSessionWithConfig(mCamera, outputSurfaces, sessionListener,
                mHandler);
        sessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_READY,
                SESSION_CONFIGURE_TIMEOUT_MS);

        // Submit JPEG requests

        CaptureRequest.Builder request = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        request.addTarget(mReaderSurface);

        final int SOME_FRAMES = 10;
        for (int i = 0; i < SOME_FRAMES; i++) {
            mSession.capture(request.build(), resultListener, mHandler);
        }

        // Wait to get some frames out to ensure we can operate just the one expected surface
        waitForNumResults(resultListener, SOME_FRAMES);
        assertTrue("No images received", imageListener.getImageCount() > 0);

        // Ensure we can't use the deferred surfaces yet
        request.addTarget(sharedOutputSurface1);
        try {
            mSession.capture(request.build(), resultListener, mHandler);
            fail("Should have received IAE for trying to use a deferred target " +
                    "that's not yet configured");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Add deferred surfaces to their configurations
        surfaceViewOutput.addSurface(mPreviewSurface);
        surfaceTextureOutput.addSurface(sharedOutputSurface1);

        // Verify bad inputs to addSurface
        try {
            surfaceViewOutput.addSurface(null);
            fail("No error from setting a null deferred surface");
        } catch (NullPointerException e) {
            // expected
        }
        try {
            surfaceViewOutput.addSurface(mPreviewSurface);
            fail("Shouldn't be able to set deferred surface twice");
        } catch (IllegalStateException e) {
            // expected
        }

        // Add first deferred surface to session
        List<OutputConfiguration> deferredSurfaces = new ArrayList<>();
        deferredSurfaces.add(surfaceTextureOutput);

        mSession.finalizeOutputConfigurations(deferredSurfaces);

        // Try a second time, this should error

        try {
            mSession.finalizeOutputConfigurations(deferredSurfaces);
            fail("Should have received ISE for trying to finish a deferred output twice");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Use new deferred surface for a bit
        imageListener.resetImageCount();
        for (int i = 0; i < SOME_FRAMES; i++) {
            mSession.capture(request.build(), resultListener, mHandler);
        }
        waitForNumResults(resultListener, SOME_FRAMES);
        assertTrue("No images received", imageListener.getImageCount() > 0);
        assertTrue("No texture update received", textureAvailableListener.gotFrame());

        // Ensure we can't use the last deferred surface yet
        request.addTarget(mPreviewSurface);
        try {
            mSession.capture(request.build(), resultListener, mHandler);
            fail("Should have received IAE for trying to use a deferred target that's" +
                    " not yet configured");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Add final deferred surface
        deferredSurfaces.clear();
        deferredSurfaces.add(surfaceViewOutput);

        mSession.finalizeOutputConfigurations(deferredSurfaces);

        // Use final deferred surface for a bit
        imageListener.resetImageCount();
        for (int i = 0; i < SOME_FRAMES; i++) {
            mSession.capture(request.build(), resultListener, mHandler);
        }
        waitForNumResults(resultListener, SOME_FRAMES);
        assertTrue("No images received", imageListener.getImageCount() > 0);
        // Can't check GL output since we don't have a context to call updateTexImage on, and
        // the callback only fires once per updateTexImage call.
        // And there's no way to verify data is going to a SurfaceView

        // Check for invalid output configurations being handed to a session
        OutputConfiguration badConfig =
                new OutputConfiguration(maxPreviewSize, SurfaceTexture.class);
        deferredSurfaces.clear();
        try {
            mSession.finalizeOutputConfigurations(deferredSurfaces);
            fail("No error for empty list passed to finalizeOutputConfigurations");
        } catch (IllegalArgumentException e) {
            // expected
        }

        deferredSurfaces.add(badConfig);
        try {
            mSession.finalizeOutputConfigurations(deferredSurfaces);
            fail("No error for invalid output config being passed to finalizeOutputConfigurations");
        } catch (IllegalArgumentException e) {
            // expected
        }

    }

    /**
     * Measure the inter-frame interval based on SENSOR_TIMESTAMP for frameCount frames from the
     * provided capture listener.  If prevTimestamp is positive, it is used for the first interval
     * calculation; otherwise, the first result is used to establish the starting time.
     *
     * Returns the mean interval in the first pair entry, and the largest interval in the second
     * pair entry
     */
    Pair<Long, Long> measureMeanFrameInterval(SimpleCaptureCallback resultListener, int frameCount,
            long prevTimestamp) throws Exception {
        long summedIntervals = 0;
        long maxInterval = 0;
        int measurementCount = frameCount - ((prevTimestamp > 0) ? 0 : 1);

        for (int i = 0; i < frameCount; i++) {
            CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            if (prevTimestamp > 0) {
                long interval = timestamp - prevTimestamp;
                if (interval > maxInterval) maxInterval = interval;
                summedIntervals += interval;
            }
            prevTimestamp = timestamp;
        }
        return new Pair<Long, Long>(summedIntervals / measurementCount, maxInterval);
    }


    /**
     * Test preview fps range for all supported ranges. The exposure time are frame duration are
     * validated.
     */
    private void previewFpsRangeTestByCamera() throws Exception {
        Size maxPreviewSz;
        Range<Integer>[] fpsRanges = getDescendingTargetFpsRanges(mStaticInfo);
        boolean antiBandingOffIsSupported = mStaticInfo.isAntiBandingOffModeSupported();
        Range<Integer> fpsRange;
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        for (int i = 0; i < fpsRanges.length; i += 1) {
            fpsRange = fpsRanges[i];
            if (mStaticInfo.isHardwareLevelLegacy()) {
                // Legacy devices don't report minimum frame duration for preview sizes. The FPS
                // range should be valid for any supported preview size.
                maxPreviewSz = mOrderedPreviewSizes.get(0);
            } else {
                maxPreviewSz = getMaxPreviewSizeForFpsRange(fpsRange);
            }

            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            // Turn off auto antibanding to avoid exposure time and frame duration interference
            // from antibanding algorithm.
            if (antiBandingOffIsSupported) {
                requestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_OFF);
            } else {
                // The device doesn't implement the OFF mode, test continues. It need make sure
                // that the antibanding algorithm doesn't interfere with the fps range control.
                Log.i(TAG, "OFF antibanding mode is not supported, the camera device output must" +
                        " satisfy the specified fps range regardless of its current antibanding" +
                        " mode");
            }

            startPreview(requestBuilder, maxPreviewSz, resultListener);
            resultListener = new SimpleCaptureCallback();
            mSession.setRepeatingRequest(requestBuilder.build(), resultListener, mHandler);

            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

            verifyPreviewTargetFpsRange(resultListener, NUM_FRAMES_VERIFIED, fpsRange,
                    maxPreviewSz);
            stopPreview();
            resultListener.drain();
        }
    }

    private void verifyPreviewTargetFpsRange(SimpleCaptureCallback resultListener,
            int numFramesVerified, Range<Integer> fpsRange, Size previewSz) {
        CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        List<Integer> capabilities = mStaticInfo.getAvailableCapabilitiesChecked();

        if (capabilities.contains(CaptureRequest.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
            long frameDuration = getValueNotNull(result, CaptureResult.SENSOR_FRAME_DURATION);
            long[] frameDurationRange =
                    new long[]{(long) (1e9 / fpsRange.getUpper()), (long) (1e9 / fpsRange.getLower())};
            mCollector.expectInRange(
                    "Frame duration must be in the range of " + Arrays.toString(frameDurationRange),
                    frameDuration, (long) (frameDurationRange[0] * (1 - FRAME_DURATION_ERROR_MARGIN)),
                    (long) (frameDurationRange[1] * (1 + FRAME_DURATION_ERROR_MARGIN)));
            long expTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
            mCollector.expectTrue(String.format("Exposure time %d must be no larger than frame"
                    + "duration %d", expTime, frameDuration), expTime <= frameDuration);

            Long minFrameDuration = mMinPreviewFrameDurationMap.get(previewSz);
            boolean findDuration = mCollector.expectTrue("Unable to find minFrameDuration for size "
                    + previewSz.toString(), minFrameDuration != null);
            if (findDuration) {
                mCollector.expectTrue("Frame duration " + frameDuration + " must be no smaller than"
                        + " minFrameDuration " + minFrameDuration, frameDuration >= minFrameDuration);
            }
        } else {
            Log.i(TAG, "verifyPreviewTargetFpsRange - MANUAL_SENSOR control is not supported," +
                    " skipping duration and exposure time check.");
        }
    }

    /**
     * Test all supported preview sizes for a camera device
     *
     * @throws Exception
     */
    private void previewTestByCamera() throws Exception {
        List<Size> previewSizes = getSupportedPreviewSizes(
                mCamera.getId(), mCameraManager, PREVIEW_SIZE_BOUND);

        for (final Size sz : previewSizes) {
            if (VERBOSE) {
                Log.v(TAG, "Testing camera preview size: " + sz.toString());
            }

            // TODO: vary the different settings like crop region to cover more cases.
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);

            startPreview(requestBuilder, sz, mockCaptureCallback);
            verifyCaptureResults(mSession, mockCaptureCallback, NUM_FRAMES_VERIFIED,
                    NUM_FRAMES_VERIFIED * FRAME_TIMEOUT_MS);
            stopPreview();
        }
    }

    private void previewTestPatternTestByCamera() throws Exception {
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        int[] testPatternModes = mStaticInfo.getAvailableTestPatternModesChecked();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureCallback mockCaptureCallback;

        final int[] TEST_PATTERN_DATA = {0, 0xFFFFFFFF, 0xFFFFFFFF, 0}; // G:100%, RB:0.
        for (int mode : testPatternModes) {
            if (VERBOSE) {
                Log.v(TAG, "Test pattern mode: " + mode);
            }
            requestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, mode);
            if (mode == CaptureRequest.SENSOR_TEST_PATTERN_MODE_SOLID_COLOR) {
                // Assign color pattern to SENSOR_TEST_PATTERN_MODE_DATA
                requestBuilder.set(CaptureRequest.SENSOR_TEST_PATTERN_DATA, TEST_PATTERN_DATA);
            }
            mockCaptureCallback = mock(CaptureCallback.class);
            startPreview(requestBuilder, maxPreviewSize, mockCaptureCallback);
            verifyCaptureResults(mSession, mockCaptureCallback, NUM_TEST_PATTERN_FRAMES_VERIFIED,
                    NUM_TEST_PATTERN_FRAMES_VERIFIED * FRAME_TIMEOUT_MS);
        }

        stopPreview();
    }

    private void surfaceSetTestByCamera(String cameraId) throws Exception {
        final int MAX_SURFACE_GROUP_ID = 10;
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        Size yuvSizeBound = maxPreviewSz; // Default case: legacy device
        if (mStaticInfo.isHardwareLevelLimited()) {
            yuvSizeBound = mOrderedVideoSizes.get(0);
        } else if (mStaticInfo.isHardwareLevelAtLeastFull()) {
            yuvSizeBound = null;
        }
        Size maxYuvSize = getSupportedPreviewSizes(cameraId, mCameraManager, yuvSizeBound).get(0);

        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        ImageDropperListener imageListener = new ImageDropperListener();

        updatePreviewSurface(maxPreviewSz);
        createImageReader(maxYuvSize, ImageFormat.YUV_420_888, MAX_READER_IMAGES, imageListener);
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        OutputConfiguration previewConfig = new OutputConfiguration(mPreviewSurface);
        OutputConfiguration yuvConfig = new OutputConfiguration(mReaderSurface);
        assertEquals(OutputConfiguration.SURFACE_GROUP_ID_NONE, previewConfig.getSurfaceGroupId());
        assertEquals(OutputConfiguration.SURFACE_GROUP_ID_NONE, yuvConfig.getSurfaceGroupId());
        assertEquals(mPreviewSurface, previewConfig.getSurface());
        assertEquals(mReaderSurface, yuvConfig.getSurface());
        outputConfigs.add(previewConfig);
        outputConfigs.add(yuvConfig);
        requestBuilder.addTarget(mPreviewSurface);
        requestBuilder.addTarget(mReaderSurface);

        // Test different stream set ID.
        for (int surfaceGroupId = OutputConfiguration.SURFACE_GROUP_ID_NONE;
                surfaceGroupId < MAX_SURFACE_GROUP_ID; surfaceGroupId++) {
            if (VERBOSE) {
                Log.v(TAG, "test preview with surface group id: ");
            }

            previewConfig = new OutputConfiguration(surfaceGroupId, mPreviewSurface);
            yuvConfig = new OutputConfiguration(surfaceGroupId, mReaderSurface);
            outputConfigs.clear();
            outputConfigs.add(previewConfig);
            outputConfigs.add(yuvConfig);

            for (OutputConfiguration config : outputConfigs) {
                assertEquals(surfaceGroupId, config.getSurfaceGroupId());
            }

            CameraCaptureSession.StateCallback mockSessionListener =
                    mock(CameraCaptureSession.StateCallback.class);

            mSession = configureCameraSessionWithConfig(mCamera, outputConfigs,
                    mockSessionListener, mHandler);


            mSession.prepare(mPreviewSurface);
            verify(mockSessionListener,
                    timeout(PREPARE_TIMEOUT_MS).times(1)).
                    onSurfacePrepared(eq(mSession), eq(mPreviewSurface));

            mSession.prepare(mReaderSurface);
            verify(mockSessionListener,
                    timeout(PREPARE_TIMEOUT_MS).times(1)).
                    onSurfacePrepared(eq(mSession), eq(mReaderSurface));

            CaptureRequest request = requestBuilder.build();
            CaptureCallback mockCaptureCallback =
                    mock(CameraCaptureSession.CaptureCallback.class);
            mSession.setRepeatingRequest(request, mockCaptureCallback, mHandler);
            verifyCaptureResults(mSession, mockCaptureCallback, NUM_FRAMES_VERIFIED,
                    NUM_FRAMES_VERIFIED * FRAME_TIMEOUT_MS);
        }
    }

    private class IsCaptureResultValid extends ArgumentMatcher<TotalCaptureResult> {
        @Override
        public boolean matchesObject(Object obj) {
            TotalCaptureResult result = (TotalCaptureResult)obj;
            Long timeStamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            if (timeStamp != null && timeStamp.longValue() > 0L) {
                return true;
            }
            return false;
        }
    }

    private void verifyCaptureResults(
            CameraCaptureSession session,
            CaptureCallback mockListener,
            int expectResultCount,
            int timeOutMs) {
        // Should receive expected number of onCaptureStarted callbacks.
        ArgumentCaptor<Long> timestamps = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> frameNumbers = ArgumentCaptor.forClass(Long.class);
        verify(mockListener,
                timeout(timeOutMs).atLeast(expectResultCount))
                        .onCaptureStarted(
                                eq(session),
                                isA(CaptureRequest.class),
                                timestamps.capture(),
                                frameNumbers.capture());

        // Validate timestamps: all timestamps should be larger than 0 and monotonically increase.
        long timestamp = 0;
        for (Long nextTimestamp : timestamps.getAllValues()) {
            assertNotNull("Next timestamp is null!", nextTimestamp);
            assertTrue("Captures are out of order", timestamp < nextTimestamp);
            timestamp = nextTimestamp;
        }

        // Validate framenumbers: all framenumbers should be consecutive and positive
        long frameNumber = -1;
        for (Long nextFrameNumber : frameNumbers.getAllValues()) {
            assertNotNull("Next frame number is null!", nextFrameNumber);
            assertTrue("Captures are out of order",
                    (frameNumber == -1) || (frameNumber + 1 == nextFrameNumber));
            frameNumber = nextFrameNumber;
        }

        // Should receive expected number of capture results.
        verify(mockListener,
                timeout(timeOutMs).atLeast(expectResultCount))
                        .onCaptureCompleted(
                                eq(session),
                                isA(CaptureRequest.class),
                                argThat(new IsCaptureResultValid()));

        // Should not receive any capture failed callbacks.
        verify(mockListener, never())
                        .onCaptureFailed(
                                eq(session),
                                isA(CaptureRequest.class),
                                isA(CaptureFailure.class));
    }

}
