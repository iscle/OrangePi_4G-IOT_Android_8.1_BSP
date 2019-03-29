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

import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.cts.helpers.Preconditions.*;
import static android.hardware.camera2.cts.helpers.AssertHelpers.*;
import static android.hardware.camera2.cts.CameraTestUtils.*;
import static com.android.ex.camera2.blocking.BlockingStateCallback.*;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Size;
import android.hardware.camera2.cts.helpers.MaybeNull;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.rs.RenderScriptSingleton;
import android.hardware.camera2.cts.rs.ScriptGraph;
import android.hardware.camera2.cts.rs.ScriptYuvCrop;
import android.hardware.camera2.cts.rs.ScriptYuvMeans1d;
import android.hardware.camera2.cts.rs.ScriptYuvMeans2dTo1d;
import android.hardware.camera2.cts.rs.ScriptYuvToRgb;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Script.LaunchOptions;
import android.test.AndroidTestCase;
import android.util.Log;
import android.util.Rational;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Suite of tests for camera2 -> RenderScript APIs.
 *
 * <p>It uses CameraDevice as producer, camera sends the data to the surface provided by
 * Allocation. Only the below format is tested:</p>
 *
 * <p>YUV_420_888: flexible YUV420, it is a mandatory format for camera.</p>
 */
public class AllocationTest extends AndroidTestCase {
    private static final String TAG = "AllocationTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private CameraManager mCameraManager;
    private CameraDevice mCamera;
    private CameraCaptureSession mSession;
    private BlockingStateCallback mCameraListener;
    private BlockingSessionCallback mSessionListener;

    private String[] mCameraIds;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private CameraIterable mCameraIterable;
    private SizeIterable mSizeIterable;
    private ResultIterable mResultIterable;

    @Override
    public synchronized void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager!", mCameraManager);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCameraIds = mCameraManager.getCameraIdList();
        mHandlerThread = new HandlerThread("AllocationTest");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCameraListener = new BlockingStateCallback();

        mCameraIterable = new CameraIterable();
        mSizeIterable = new SizeIterable();
        mResultIterable = new ResultIterable();

        RenderScriptSingleton.setContext(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        MaybeNull.close(mCamera);
        RenderScriptSingleton.clearContext();
        mHandlerThread.quitSafely();
        mHandler = null;
        super.tearDown();
    }

    /**
     * Update the request with a default manual request template.
     *
     * @param request A builder for a CaptureRequest
     * @param sensitivity ISO gain units (e.g. 100)
     * @param expTimeNs Exposure time in nanoseconds
     */
    private static void setManualCaptureRequest(CaptureRequest.Builder request, int sensitivity,
            long expTimeNs) {
        final Rational ONE = new Rational(1, 1);
        final Rational ZERO = new Rational(0, 1);

        if (VERBOSE) {
            Log.v(TAG, String.format("Create manual capture request, sensitivity = %d, expTime = %f",
                    sensitivity, expTimeNs / (1000.0 * 1000)));
        }

        request.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
        request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        request.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        request.set(CaptureRequest.CONTROL_EFFECT_MODE, CaptureRequest.CONTROL_EFFECT_MODE_OFF);
        request.set(CaptureRequest.SENSOR_FRAME_DURATION, 0L);
        request.set(CaptureRequest.SENSOR_SENSITIVITY, sensitivity);
        request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expTimeNs);
        request.set(CaptureRequest.COLOR_CORRECTION_MODE,
                CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);

        // Identity transform
        request.set(CaptureRequest.COLOR_CORRECTION_TRANSFORM,
            new ColorSpaceTransform(new Rational[] {
                ONE, ZERO, ZERO,
                ZERO, ONE, ZERO,
                ZERO, ZERO, ONE
            }));

        // Identity gains
        request.set(CaptureRequest.COLOR_CORRECTION_GAINS,
                new RggbChannelVector(1.0f, 1.0f, 1.0f, 1.0f ));
        request.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_FAST);
    }

    /**
     * Calculate the absolute crop window from a {@link Size},
     * and configure {@link LaunchOptions} for it.
     */
    // TODO: split patch crop window and the application against a particular size into 2 classes
    public static class Patch {
        /**
         * Create a new {@link Patch} from relative crop coordinates.
         *
         * <p>All float values must be normalized coordinates between [0, 1].</p>
         *
         * @param size Size of the original rectangle that is being cropped.
         * @param xNorm The X coordinate defining the left side of the rectangle (in [0, 1]).
         * @param yNorm The Y coordinate defining the top side of the rectangle (in [0, 1]).
         * @param wNorm The width of the crop rectangle (normalized between [0, 1]).
         * @param hNorm The height of the crop rectangle (normalized between [0, 1]).
         *
         * @throws NullPointerException if size was {@code null}.
         * @throws AssertionError if any of the normalized coordinates were out of range
         */
        public Patch(Size size, float xNorm, float yNorm, float wNorm, float hNorm) {
            checkNotNull("size", size);

            assertInRange(xNorm, 0.0f, 1.0f);
            assertInRange(yNorm, 0.0f, 1.0f);
            assertInRange(wNorm, 0.0f, 1.0f);
            assertInRange(hNorm, 0.0f, 1.0f);

            wFull = size.getWidth();
            hFull = size.getWidth();

            xTile = (int)Math.ceil(xNorm * wFull);
            yTile = (int)Math.ceil(yNorm * hFull);

            wTile = (int)Math.ceil(wNorm * wFull);
            hTile = (int)Math.ceil(hNorm * hFull);

            mSourceSize = size;
        }

        /**
         * Get the original size used to create this {@link Patch}.
         *
         * @return source size
         */
        public Size getSourceSize() {
            return mSourceSize;
        }

        /**
         * Get the cropped size after applying the normalized crop window.
         *
         * @return cropped size
         */
        public Size getSize() {
            return new Size(wFull, hFull);
        }

        /**
         * Get the {@link LaunchOptions} that can be used with a {@link android.renderscript.Script}
         * to apply a kernel over a subset of an {@link Allocation}.
         *
         * @return launch options
         */
        public LaunchOptions getLaunchOptions() {
            return (new LaunchOptions())
                    .setX(xTile, xTile + wTile)
                    .setY(yTile, yTile + hTile);
        }

        /**
         * Get the cropped width after applying the normalized crop window.
         *
         * @return cropped width
         */
        public int getWidth() {
            return wTile;
        }

        /**
         * Get the cropped height after applying the normalized crop window.
         *
         * @return cropped height
         */
        public int getHeight() {
            return hTile;
        }

        /**
         * Convert to a {@link RectF} where each corner is represented by a
         * normalized coordinate in between [0.0, 1.0] inclusive.
         *
         * @return a new rectangle
         */
        public RectF toRectF() {
            return new RectF(
                    xTile * 1.0f / wFull,
                    yTile * 1.0f / hFull,
                    (xTile + wTile) * 1.0f / wFull,
                    (yTile + hTile) * 1.0f / hFull);
        }

        private final Size mSourceSize;
        private final int wFull;
        private final int hFull;
        private final int xTile;
        private final int yTile;
        private final int wTile;
        private final int hTile;
    }

    /**
     * Convert a single YUV pixel (3 byte elements) to an RGB pixel.
     *
     * <p>The color channels must be in the following order:
     * <ul><li>Y - 0th channel
     * <li>U - 1st channel
     * <li>V - 2nd channel
     * </ul></p>
     *
     * <p>Each channel has data in the range 0-255.</p>
     *
     * <p>Output data is a 3-element pixel with each channel in the range of [0,1].
     * Each channel is saturated to avoid over/underflow.</p>
     *
     * <p>The conversion is done using JFIF File Interchange Format's "Conversion to and from RGB":
     * <ul>
     * <li>R = Y + 1.042 (Cr - 128)
     * <li>G = Y - 0.34414 (Cb - 128) - 0.71414 (Cr - 128)
     * <li>B = Y + 1.772 (Cb - 128)
     * </ul>
     *
     * Where Cr and Cb are aliases of V and U respectively.
     * </p>
     *
     * @param yuvData An array of a YUV pixel (at least 3 bytes large)
     *
     * @return an RGB888 pixel with each channel in the range of [0,1]
     */
    private static float[] convertPixelYuvToRgb(byte[] yuvData) {
        final int CHANNELS = 3; // yuv
        final float COLOR_RANGE = 255f;

        assertTrue("YUV pixel must be at least 3 bytes large", CHANNELS <= yuvData.length);

        float[] rgb = new float[CHANNELS];

        float y = yuvData[0] & 0xFF;  // Y channel
        float cb = yuvData[1] & 0xFF; // U channel
        float cr = yuvData[2] & 0xFF; // V channel

        // convert YUV -> RGB (from JFIF's "Conversion to and from RGB" section)
        float r = y + 1.402f * (cr - 128);
        float g = y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128);
        float b = y + 1.772f * (cb - 128);

        // normalize [0,255] -> [0,1]
        rgb[0] = r / COLOR_RANGE;
        rgb[1] = g / COLOR_RANGE;
        rgb[2] = b / COLOR_RANGE;

        // Clamp to range [0,1]
        for (int i = 0; i < CHANNELS; ++i) {
            rgb[i] = Math.max(0.0f, Math.min(1.0f, rgb[i]));
        }

        if (VERBOSE) {
            Log.v(TAG, String.format("RGB calculated (r,g,b) = (%f, %f, %f)", rgb[0], rgb[1],
                    rgb[2]));
        }

        return rgb;
    }

    /**
     * Configure the camera with the target surface;
     * create a capture request builder with {@code cameraTarget} as the sole surface target.
     *
     * <p>Outputs are configured with the new surface targets, and this function blocks until
     * the camera has finished configuring.</p>
     *
     * <p>The capture request is created from the {@link CameraDevice#TEMPLATE_PREVIEW} template.
     * No other keys are set.
     * </p>
     */
    private CaptureRequest.Builder configureAndCreateRequestForSurface(Surface cameraTarget)
            throws CameraAccessException {
        List<Surface> outputSurfaces = new ArrayList<Surface>(/*capacity*/1);
        assertNotNull("Failed to get Surface", cameraTarget);
        outputSurfaces.add(cameraTarget);

        mSessionListener = new BlockingSessionCallback();
        mCamera.createCaptureSession(outputSurfaces, mSessionListener, mHandler);
        mSession = mSessionListener.waitAndGetSession(SESSION_CONFIGURE_TIMEOUT_MS);
        CaptureRequest.Builder captureBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        assertNotNull("Fail to create captureRequest", captureBuilder);
        captureBuilder.addTarget(cameraTarget);

        if (VERBOSE) Log.v(TAG, "configureAndCreateRequestForSurface - done");

        return captureBuilder;
    }

    /**
     * Submit a single request to the camera, block until the buffer is available.
     *
     * <p>Upon return from this function, script has been executed against the latest buffer.
     * </p>
     */
    private void captureSingleShotAndExecute(CaptureRequest request, ScriptGraph graph)
            throws CameraAccessException {
        checkNotNull("request", request);
        checkNotNull("graph", graph);

        mSession.capture(request, new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                    TotalCaptureResult result) {
                if (VERBOSE) Log.v(TAG, "Capture completed");
            }
        }, mHandler);

        if (VERBOSE) Log.v(TAG, "Waiting for single shot buffer");
        graph.advanceInputWaiting();
        if (VERBOSE) Log.v(TAG, "Got the buffer");
        graph.execute();
    }

    private void stopCapture() throws CameraAccessException {
        if (VERBOSE) Log.v(TAG, "Stopping capture and waiting for idle");
        // Stop repeat, wait for captures to complete, and disconnect from surfaces
        mSession.close();
        mSessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_CLOSED,
                SESSION_CLOSE_TIMEOUT_MS);
        mSession = null;
        mSessionListener = null;
    }

    /**
     * Extremely dumb validator. Makes sure there is at least one non-zero RGB pixel value.
     */
    private void validateInputOutputNotZeroes(ScriptGraph scriptGraph, Size size) {
        final int BPP = 8; // bits per pixel

        int width = size.getWidth();
        int height = size.getHeight();
        /**
         * Check the input allocation is sane.
         * - Byte size matches what we expect.
         * - The input is not all zeroes.
         */

        // Check that input data was updated first. If it wasn't, the rest of the test will fail.
        byte[] data = scriptGraph.getInputData();
        assertArrayNotAllZeroes("Input allocation data was not updated", data);

        // Minimal required size to represent YUV 4:2:0 image
        int packedSize =
                width * height * ImageFormat.getBitsPerPixel(YUV_420_888) / BPP;
        if (VERBOSE) Log.v(TAG, "Expected image size = " + packedSize);
        int actualSize = data.length;
        // Actual size may be larger due to strides or planes being non-contiguous
        assertTrue(
                String.format(
                        "YUV 420 packed size (%d) should be at least as large as the actual size " +
                        "(%d)", packedSize, actualSize), packedSize <= actualSize);
        /**
         * Check the output allocation by converting to RGBA.
         * - Byte size matches what we expect
         * - The output is not all zeroes
         */
        final int RGBA_CHANNELS = 4;

        int actualSizeOut = scriptGraph.getOutputAllocation().getBytesSize();
        int packedSizeOut = width * height * RGBA_CHANNELS;

        byte[] dataOut = scriptGraph.getOutputData();
        assertEquals("RGB mismatched byte[] and expected size",
                packedSizeOut, dataOut.length);

        if (VERBOSE) {
            Log.v(TAG, "checkAllocationByConvertingToRgba - RGB data size " + dataOut.length);
        }

        assertArrayNotAllZeroes("RGBA data was not updated", dataOut);
        // RGBA8888 stride should be equal to the width
        assertEquals("RGBA 8888 mismatched byte[] and expected size", packedSizeOut, actualSizeOut);

        if (VERBOSE) Log.v(TAG, "validating Buffer , size = " + actualSize);
    }

    public void testAllocationFromCameraFlexibleYuv() throws Exception {

        /** number of frame (for streaming requests) to be verified. */
        final int NUM_FRAME_VERIFIED = 1;

        mCameraIterable.forEachCamera(new CameraBlock() {
            @Override
            public void run(CameraDevice camera) throws CameraAccessException {

                // Iterate over each size in the camera
                mSizeIterable.forEachSize(YUV_420_888, new SizeBlock() {
                    @Override
                    public void run(final Size size) throws CameraAccessException {
                        // Create a script graph that converts YUV to RGB
                        try (ScriptGraph scriptGraph = ScriptGraph.create()
                                .configureInputWithSurface(size, YUV_420_888)
                                .chainScript(ScriptYuvToRgb.class)
                                .buildGraph()) {

                            if (VERBOSE) Log.v(TAG, "Prepared ScriptYuvToRgb for size " + size);

                            // Run the graph against camera input and validate we get some input
                            CaptureRequest request =
                                    configureAndCreateRequestForSurface(scriptGraph.getInputSurface()).build();

                            // Block until we get 1 result, then iterate over the result
                            mResultIterable.forEachResultRepeating(
                                    request, NUM_FRAME_VERIFIED, new ResultBlock() {
                                @Override
                                public void run(CaptureResult result) throws CameraAccessException {
                                    scriptGraph.advanceInputWaiting();
                                    scriptGraph.execute();
                                    validateInputOutputNotZeroes(scriptGraph, size);
                                    scriptGraph.advanceInputAndDrop();
                                }
                            });

                            stopCapture();
                            if (VERBOSE) Log.v(TAG, "Cleanup Renderscript cache");
                            scriptGraph.close();
                            RenderScriptSingleton.clearContext();
                            RenderScriptSingleton.setContext(getContext());
                        }
                    }
                });
            }
        });
    }

    /**
     * Take two shots and ensure per-frame-control with exposure/gain is working correctly.
     *
     * <p>Takes a shot with very low ISO and exposure time. Expect it to be black.</p>
     *
     * <p>Take a shot with very high ISO and exposure time. Expect it to be white.</p>
     *
     * @throws Exception
     */
    public void testBlackWhite() throws CameraAccessException {

        /** low iso + low exposure (first shot) */
        final float THRESHOLD_LOW = 0.025f;
        /** high iso + high exposure (second shot) */
        final float THRESHOLD_HIGH = 0.975f;

        mCameraIterable.forEachCamera(/*fullHwLevel*/false, new CameraBlock() {
            @Override
            public void run(CameraDevice camera) throws CameraAccessException {
                final StaticMetadata staticInfo =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(camera.getId()));

                // This test requires PFC and manual sensor control
                if (!staticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ||
                        !staticInfo.isPerFrameControlSupported()) {
                    return;
                }

                final Size maxSize = getMaxSize(
                        getSupportedSizeForFormat(YUV_420_888, camera.getId(), mCameraManager));

                try (ScriptGraph scriptGraph = createGraphForYuvCroppedMeans(maxSize)) {

                    CaptureRequest.Builder req =
                            configureAndCreateRequestForSurface(scriptGraph.getInputSurface());

                    // Take a shot with very low ISO and exposure time. Expect it to be black.
                    int minimumSensitivity = staticInfo.getSensitivityMinimumOrDefault();
                    long minimumExposure = staticInfo.getExposureMinimumOrDefault();
                    setManualCaptureRequest(req, minimumSensitivity, minimumExposure);

                    CaptureRequest lowIsoExposureShot = req.build();
                    captureSingleShotAndExecute(lowIsoExposureShot, scriptGraph);

                    float[] blackMeans = convertPixelYuvToRgb(scriptGraph.getOutputData());

                    // Take a shot with very high ISO and exposure time. Expect it to be white.
                    int maximumSensitivity = staticInfo.getSensitivityMaximumOrDefault();
                    long maximumExposure = staticInfo.getExposureMaximumOrDefault();
                    setManualCaptureRequest(req, maximumSensitivity, maximumExposure);

                    CaptureRequest highIsoExposureShot = req.build();
                    captureSingleShotAndExecute(highIsoExposureShot, scriptGraph);

                    float[] whiteMeans = convertPixelYuvToRgb(scriptGraph.getOutputData());

                    // Low iso + low exposure (first shot), just check and log the error.
                    for (int i = 0; i < blackMeans.length; ++i) {
                        if (blackMeans[i] >= THRESHOLD_LOW) {
                            Log.e(TAG,
                                    String.format("Black means too high: (%s should be greater"
                                            + " than %s; item index %d in %s)", blackMeans[i],
                                            THRESHOLD_LOW, i,
                                            Arrays.toString(blackMeans)));
                        }
                    }

                    // High iso + high exposure (second shot), just check and log the error
                    for (int i = 0; i < whiteMeans.length; ++i) {
                        if (whiteMeans[i] <= THRESHOLD_HIGH) {
                            Log.e(TAG,
                                    String.format("White means too low: (%s should be less than"
                                            + " %s; item index %d in %s)", whiteMeans[i],
                                            THRESHOLD_HIGH, i,
                                            Arrays.toString(whiteMeans)));
                        }
                    }
                }
            }
        });
    }

    /**
     * Test that the android.sensitivity.parameter is applied.
     */
    public void testParamSensitivity() throws CameraAccessException {
        final float THRESHOLD_MAX_MIN_DIFF = 0.3f;
        final float THRESHOLD_MAX_MIN_RATIO = 2.0f;
        final int NUM_STEPS = 5;
        final long EXPOSURE_TIME_NS = 2000000; // 2 ms
        final int RGB_CHANNELS = 3;

        mCameraIterable.forEachCamera(/*fullHwLevel*/false, new CameraBlock() {


            @Override
            public void run(CameraDevice camera) throws CameraAccessException {
                final StaticMetadata staticInfo =
                        new StaticMetadata(mCameraManager.getCameraCharacteristics(camera.getId()));
                // This test requires PFC and manual sensor control
                if (!staticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) ||
                        !staticInfo.isPerFrameControlSupported()) {
                    return;
                }

                final List<float[]> rgbMeans = new ArrayList<float[]>();
                final Size maxSize = getMaxSize(
                        getSupportedSizeForFormat(YUV_420_888, camera.getId(), mCameraManager));

                final int sensitivityMin = staticInfo.getSensitivityMinimumOrDefault();
                final int sensitivityMax = staticInfo.getSensitivityMaximumOrDefault();

                // List each sensitivity from min-max in NUM_STEPS increments
                int[] sensitivities = new int[NUM_STEPS];
                for (int i = 0; i < NUM_STEPS; ++i) {
                    int delta = (sensitivityMax - sensitivityMin) / (NUM_STEPS - 1);
                    sensitivities[i] = sensitivityMin + delta * i;
                }

                try (ScriptGraph scriptGraph = createGraphForYuvCroppedMeans(maxSize)) {

                    CaptureRequest.Builder req =
                            configureAndCreateRequestForSurface(scriptGraph.getInputSurface());

                    // Take burst shots with increasing sensitivity one after other.
                    for (int i = 0; i < NUM_STEPS; ++i) {
                        setManualCaptureRequest(req, sensitivities[i], EXPOSURE_TIME_NS);
                        captureSingleShotAndExecute(req.build(), scriptGraph);
                        float[] means = convertPixelYuvToRgb(scriptGraph.getOutputData());
                        rgbMeans.add(means);

                        if (VERBOSE) {
                            Log.v(TAG, "testParamSensitivity - captured image " + i +
                                    " with RGB means: " + Arrays.toString(means));
                        }
                    }

                    // Test that every consecutive image gets brighter.
                    for (int i = 0; i < rgbMeans.size() - 1; ++i) {
                        float[] curMeans = rgbMeans.get(i);
                        float[] nextMeans = rgbMeans.get(i+1);

                        float[] left = curMeans;
                        float[] right = nextMeans;
                        String leftString = Arrays.toString(left);
                        String rightString = Arrays.toString(right);

                        String msgHeader =
                                String.format("Shot with sensitivity %d should not have higher " +
                                "average means than shot with sensitivity %d",
                                sensitivities[i], sensitivities[i+1]);
                        for (int m = 0; m < left.length; ++m) {
                            String msg = String.format(
                                    "%s: (%s should be less than or equal to %s; item index %d;"
                                    + " left = %s; right = %s)",
                                    msgHeader, left[m], right[m], m, leftString, rightString);
                            if (left[m] > right[m]) {
                                Log.e(TAG, msg);
                            }
                        }
                    }

                    // Test the min-max diff and ratios are within expected thresholds
                    float[] lastMeans = rgbMeans.get(NUM_STEPS - 1);
                    float[] firstMeans = rgbMeans.get(/*location*/0);
                    for (int i = 0; i < RGB_CHANNELS; ++i) {
                        if (lastMeans[i] - firstMeans[i] <= THRESHOLD_MAX_MIN_DIFF) {
                            Log.w(TAG, String.format("Sensitivity max-min diff too small"
                                    + "(max=%f, min=%f)", lastMeans[i], firstMeans[i]));
                        }
                        if (lastMeans[i] / firstMeans[i] <= THRESHOLD_MAX_MIN_RATIO) {
                            Log.w(TAG, String.format("Sensitivity max-min ratio too small"
                                    + "(max=%f, min=%f)", lastMeans[i], firstMeans[i]));
                        }
                    }
                }
            }
        });

    }

    /**
     * Common script graph for manual-capture based tests that determine the average pixel
     * values of a cropped sub-region.
     *
     * <p>Processing chain:
     *
     * <pre>
     * input:  YUV_420_888 surface
     * output: mean YUV value of a central section of the image,
     *         YUV 4:4:4 encoded as U8_3
     * steps:
     *      1) crop [0.45,0.45] - [0.55, 0.55]
     *      2) average columns
     *      3) average rows
     * </pre>
     * </p>
     */
    private static ScriptGraph createGraphForYuvCroppedMeans(final Size size) {
        ScriptGraph scriptGraph = ScriptGraph.create()
                .configureInputWithSurface(size, YUV_420_888)
                .configureScript(ScriptYuvCrop.class)
                    .set(ScriptYuvCrop.CROP_WINDOW,
                            new Patch(size, /*x*/0.45f, /*y*/0.45f, /*w*/0.1f, /*h*/0.1f).toRectF())
                    .buildScript()
                .chainScript(ScriptYuvMeans2dTo1d.class)
                .chainScript(ScriptYuvMeans1d.class)
                // TODO: Make a script for YUV 444 -> RGB 888 conversion
                .buildGraph();
        return scriptGraph;
    }

    /*
     * TODO: Refactor below code into separate classes and to not depend on AllocationTest
     * inner variables.
     *
     * TODO: add javadocs to below methods
     *
     * TODO: Figure out if there's some elegant way to compose these forEaches together, so that
     * the callers don't have to do a ton of nesting
     */

    interface CameraBlock {
        void run(CameraDevice camera) throws CameraAccessException;
    }

    class CameraIterable {
        public void forEachCamera(CameraBlock runnable)
                throws CameraAccessException {
            forEachCamera(/*fullHwLevel*/false, runnable);
        }

        public void forEachCamera(boolean fullHwLevel, CameraBlock runnable)
                throws CameraAccessException {
            assertNotNull("No camera manager", mCameraManager);
            assertNotNull("No camera IDs", mCameraIds);

            for (int i = 0; i < mCameraIds.length; i++) {
                // Don't execute the runnable against non-FULL cameras if FULL is required
                CameraCharacteristics properties =
                        mCameraManager.getCameraCharacteristics(mCameraIds[i]);
                StaticMetadata staticInfo = new StaticMetadata(properties);
                if (fullHwLevel && !staticInfo.isHardwareLevelAtLeastFull()) {
                    Log.i(TAG, String.format(
                            "Skipping this test for camera %s, needs FULL hw level",
                            mCameraIds[i]));
                    continue;
                }
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, String.format(
                        "Skipping this test for camera %s, does not support regular outputs",
                        mCameraIds[i]));
                    continue;
                }
                // Open camera and execute test
                Log.i(TAG, "Testing Camera " + mCameraIds[i]);
                try {
                    openDevice(mCameraIds[i]);

                    runnable.run(mCamera);
                } finally {
                    closeDevice(mCameraIds[i]);
                }
            }
        }

        private void openDevice(String cameraId) {
            if (mCamera != null) {
                throw new IllegalStateException("Already have open camera device");
            }
            try {
                mCamera = openCamera(
                    mCameraManager, cameraId, mCameraListener, mHandler);
            } catch (CameraAccessException e) {
                fail("Fail to open camera synchronously, " + Log.getStackTraceString(e));
            } catch (BlockingOpenException e) {
                fail("Fail to open camera asynchronously, " + Log.getStackTraceString(e));
            }
            mCameraListener.waitForState(STATE_OPENED, CAMERA_OPEN_TIMEOUT_MS);
        }

        private void closeDevice(String cameraId) {
            if (mCamera != null) {
                mCamera.close();
                mCameraListener.waitForState(STATE_CLOSED, CAMERA_CLOSE_TIMEOUT_MS);
                mCamera = null;
            }
        }
    }

    interface SizeBlock {
        void run(Size size) throws CameraAccessException;
    }

    class SizeIterable {
        public void forEachSize(int format, SizeBlock runnable) throws CameraAccessException {
            assertNotNull("No camera opened", mCamera);
            assertNotNull("No camera manager", mCameraManager);

            CameraCharacteristics properties =
                    mCameraManager.getCameraCharacteristics(mCamera.getId());

            assertNotNull("Can't get camera properties!", properties);

            StreamConfigurationMap config =
                    properties.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] availableOutputFormats = config.getOutputFormats();
            assertArrayNotEmpty(availableOutputFormats,
                    "availableOutputFormats should not be empty");
            Arrays.sort(availableOutputFormats);
            assertTrue("Can't find the format " + format + " in supported formats " +
                    Arrays.toString(availableOutputFormats),
                    Arrays.binarySearch(availableOutputFormats, format) >= 0);

            Size[] availableSizes = getSupportedSizeForFormat(format, mCamera.getId(),
                    mCameraManager);
            assertArrayNotEmpty(availableSizes, "availableSizes should not be empty");

            for (Size size : availableSizes) {

                if (VERBOSE) {
                    Log.v(TAG, "Testing size " + size.toString() +
                            " for camera " + mCamera.getId());
                }
                runnable.run(size);
            }
        }
    }

    interface ResultBlock {
        void run(CaptureResult result) throws CameraAccessException;
    }

    class ResultIterable {
        public void forEachResultOnce(CaptureRequest request, ResultBlock block)
                throws CameraAccessException {
            forEachResult(request, /*count*/1, /*repeating*/false, block);
        }

        public void forEachResultRepeating(CaptureRequest request, int count, ResultBlock block)
                throws CameraAccessException {
            forEachResult(request, count, /*repeating*/true, block);
        }

        public void forEachResult(CaptureRequest request, int count, boolean repeating,
                ResultBlock block) throws CameraAccessException {

            // TODO: start capture, i.e. configureOutputs

            SimpleCaptureCallback listener = new SimpleCaptureCallback();

            if (!repeating) {
                for (int i = 0; i < count; ++i) {
                    mSession.capture(request, listener, mHandler);
                }
            } else {
                mSession.setRepeatingRequest(request, listener, mHandler);
            }

            // Assume that the device is already IDLE.
            mSessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_ACTIVE,
                    CAMERA_ACTIVE_TIMEOUT_MS);

            for (int i = 0; i < count; ++i) {
                if (VERBOSE) {
                    Log.v(TAG, String.format("Testing with result %d of %d for camera %s",
                            i, count, mCamera.getId()));
                }

                CaptureResult result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);
                block.run(result);
            }

            if (repeating) {
                mSession.stopRepeating();
                mSessionListener.getStateWaiter().waitForState(
                    BlockingSessionCallback.SESSION_READY, CAMERA_IDLE_TIMEOUT_MS);
            }

            // TODO: Make a Configure decorator or some such for configureOutputs
        }
    }
}
