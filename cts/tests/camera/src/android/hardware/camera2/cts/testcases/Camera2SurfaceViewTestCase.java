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

package android.hardware.camera2.cts.testcases;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static com.android.ex.camera2.blocking.BlockingStateCallback.STATE_CLOSED;

import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.util.Size;
import android.util.Range;
import android.hardware.camera2.cts.Camera2SurfaceViewCtsActivity;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;

import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Camera2 Preview test case base class by using SurfaceView as rendering target.
 *
 * <p>This class encapsulates the SurfaceView based preview common functionalities.
 * The setup and teardown of CameraManager, test HandlerThread, Activity, Camera IDs
 * and CameraStateCallback are handled in this class. Some basic preview related utility
 * functions are provided to facilitate the derived preview-based test classes.
 * </p>
 */

public class Camera2SurfaceViewTestCase extends
        ActivityInstrumentationTestCase2<Camera2SurfaceViewCtsActivity> {
    private static final String TAG = "SurfaceViewTestCase";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS = 1000;

    // TODO: Use internal storage for this to make sure the file is only visible to test.
    protected static final String DEBUG_FILE_NAME_BASE =
            Environment.getExternalStorageDirectory().getPath();
    protected static final int WAIT_FOR_RESULT_TIMEOUT_MS = 3000;
    protected static final float FRAME_DURATION_ERROR_MARGIN = 0.01f; // 1 percent error margin.
    protected static final int NUM_RESULTS_WAIT_TIMEOUT = 100;
    protected static final int NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY = 8;
    protected static final int MIN_FRAME_DURATION_ERROR_MARGIN = 100; // ns

    protected Context mContext;
    protected CameraManager mCameraManager;
    protected String[] mCameraIds;
    protected HandlerThread mHandlerThread;
    protected Handler mHandler;
    protected BlockingStateCallback mCameraListener;
    protected BlockingSessionCallback mSessionListener;
    protected CameraErrorCollector mCollector;
    // Per device fields:
    protected StaticMetadata mStaticInfo;
    protected CameraDevice mCamera;
    protected CameraCaptureSession mSession;
    protected ImageReader mReader;
    protected Surface mReaderSurface;
    protected Surface mPreviewSurface;
    protected SurfaceHolder mPreviewHolder;
    protected Size mPreviewSize;
    protected List<Size> mOrderedPreviewSizes; // In descending order.
    protected List<Size> m1080pBoundedOrderedPreviewSizes; // In descending order.
    protected List<Size> mOrderedVideoSizes; // In descending order.
    protected List<Size> mOrderedStillSizes; // In descending order.
    protected HashMap<Size, Long> mMinPreviewFrameDurationMap;

    protected WindowManager mWindowManager;

    public Camera2SurfaceViewTestCase() {
        super(Camera2SurfaceViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        /**
         * Set up the camera preview required environments, including activity,
         * CameraManager, HandlerThread, Camera IDs, and CameraStateCallback.
         */
        super.setUp();
        mContext = getActivity();
        /**
         * Workaround for mockito and JB-MR2 incompatibility
         *
         * Avoid java.lang.IllegalArgumentException: dexcache == null
         * https://code.google.com/p/dexmaker/issues/detail?id=2
         */
        System.setProperty("dexmaker.dexcache", mContext.getCacheDir().toString());
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Unable to get CameraManager", mCameraManager);
        mCameraIds = mCameraManager.getCameraIdList();
        assertNotNull("Unable to get camera ids", mCameraIds);
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCameraListener = new BlockingStateCallback();
        mCollector = new CameraErrorCollector();

        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        // Teardown the camera preview required environments.
        mHandlerThread.quitSafely();
        mHandler = null;
        mCameraListener = null;

        try {
            mCollector.verify();
        } catch (Throwable e) {
            // When new Exception(e) is used, exception info will be printed twice.
            throw new Exception(e.getMessage());
        } finally {
            super.tearDown();
        }
    }

    /**
     * Start camera preview by using the given request, preview size and capture
     * listener.
     * <p>
     * If preview is already started, calling this function will stop the
     * current preview stream and start a new preview stream with given
     * parameters. No need to call stopPreview between two startPreview calls.
     * </p>
     *
     * @param request The request builder used to start the preview.
     * @param previewSz The size of the camera device output preview stream.
     * @param listener The callbacks the camera device will notify when preview
     *            capture is available.
     */
    protected void startPreview(CaptureRequest.Builder request, Size previewSz,
            CaptureCallback listener) throws Exception {
        // Update preview size.
        updatePreviewSurface(previewSz);
        if (VERBOSE) {
            Log.v(TAG, "start preview with size " + mPreviewSize.toString());
        }

        configurePreviewOutput(request);

        mSession.setRepeatingRequest(request.build(), listener, mHandler);
    }

    /**
     * Configure the preview output stream.
     *
     * @param request The request to be configured with preview surface
     */
    protected void configurePreviewOutput(CaptureRequest.Builder request)
            throws CameraAccessException {
        List<Surface> outputSurfaces = new ArrayList<Surface>(/*capacity*/1);
        outputSurfaces.add(mPreviewSurface);
        mSessionListener = new BlockingSessionCallback();
        mSession = configureCameraSession(mCamera, outputSurfaces, mSessionListener, mHandler);

        request.addTarget(mPreviewSurface);
    }

    /**
     * Create a {@link CaptureRequest#Builder} and add the default preview surface.
     *
     * @return The {@link CaptureRequest#Builder} to be created
     * @throws CameraAccessException When create capture request from camera fails
     */
    protected CaptureRequest.Builder createRequestForPreview() throws CameraAccessException {
        if (mPreviewSurface == null) {
            throw new IllegalStateException(
                    "Preview surface is not set yet, call updatePreviewSurface or startPreview"
                    + "first to set the preview surface properly.");
        }
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        requestBuilder.addTarget(mPreviewSurface);
        return requestBuilder;
    }

    /**
     * Stop preview for current camera device by closing the session.
     * Does _not_ wait for the device to go idle
     */
    protected void stopPreview() throws Exception {
        // Stop repeat, wait for captures to complete, and disconnect from surfaces
        if (mSession != null) {
            if (VERBOSE) Log.v(TAG, "Stopping preview");
            mSession.close();
        }
    }

    /**
     * Stop preview for current camera device by closing the session and waiting for it to close,
     * resulting in an idle device.
     */
    protected void stopPreviewAndDrain() throws Exception {
        // Stop repeat, wait for captures to complete, and disconnect from surfaces
        if (mSession != null) {
            if (VERBOSE) Log.v(TAG, "Stopping preview and waiting for idle");
            mSession.close();
            mSessionListener.getStateWaiter().waitForState(BlockingSessionCallback.SESSION_CLOSED,
                    /*timeoutMs*/WAIT_FOR_RESULT_TIMEOUT_MS);
        }
    }

    /**
     * Setup still (JPEG) capture configuration and start preview.
     * <p>
     * The default max number of image is set to image reader.
     * </p>
     *
     * @param previewRequest The capture request to be used for preview
     * @param stillRequest The capture request to be used for still capture
     * @param previewSz Preview size
     * @param stillSz The still capture size
     * @param resultListener Capture result listener
     * @param imageListener The still capture image listener
     */
    protected void prepareStillCaptureAndStartPreview(CaptureRequest.Builder previewRequest,
            CaptureRequest.Builder stillRequest, Size previewSz, Size stillSz,
            CaptureCallback resultListener,
            ImageReader.OnImageAvailableListener imageListener) throws Exception {
        prepareCaptureAndStartPreview(previewRequest, stillRequest, previewSz, stillSz,
                ImageFormat.JPEG, resultListener, MAX_READER_IMAGES, imageListener);
    }

    /**
     * Setup still (JPEG) capture configuration and start preview.
     *
     * @param previewRequest The capture request to be used for preview
     * @param stillRequest The capture request to be used for still capture
     * @param previewSz Preview size
     * @param stillSz The still capture size
     * @param resultListener Capture result listener
     * @param maxNumImages The max number of images set to the image reader
     * @param imageListener The still capture image listener
     */
    protected void prepareStillCaptureAndStartPreview(CaptureRequest.Builder previewRequest,
            CaptureRequest.Builder stillRequest, Size previewSz, Size stillSz,
            CaptureCallback resultListener, int maxNumImages,
            ImageReader.OnImageAvailableListener imageListener) throws Exception {
        prepareCaptureAndStartPreview(previewRequest, stillRequest, previewSz, stillSz,
                ImageFormat.JPEG, resultListener, maxNumImages, imageListener);
    }

    /**
     * Setup raw capture configuration and start preview.
     *
     * <p>
     * The default max number of image is set to image reader.
     * </p>
     *
     * @param previewRequest The capture request to be used for preview
     * @param rawRequest The capture request to be used for raw capture
     * @param previewSz Preview size
     * @param rawSz The raw capture size
     * @param resultListener Capture result listener
     * @param imageListener The raw capture image listener
     */
    protected void prepareRawCaptureAndStartPreview(CaptureRequest.Builder previewRequest,
            CaptureRequest.Builder rawRequest, Size previewSz, Size rawSz,
            CaptureCallback resultListener,
            ImageReader.OnImageAvailableListener imageListener) throws Exception {
        prepareCaptureAndStartPreview(previewRequest, rawRequest, previewSz, rawSz,
                ImageFormat.RAW_SENSOR, resultListener, MAX_READER_IMAGES, imageListener);
    }

    /**
     * Wait for expected result key value available in a certain number of results.
     *
     * <p>
     * Check the result immediately if numFramesWait is 0.
     * </p>
     *
     * @param listener The capture listener to get capture result
     * @param resultKey The capture result key associated with the result value
     * @param expectedValue The result value need to be waited for
     * @param numResultsWait Number of frame to wait before times out
     * @throws TimeoutRuntimeException If more than numResultsWait results are
     * seen before the result matching myRequest arrives, or each individual wait
     * for result times out after {@value #WAIT_FOR_RESULT_TIMEOUT_MS}ms.
     */
    protected static <T> void waitForResultValue(SimpleCaptureCallback listener,
            CaptureResult.Key<T> resultKey,
            T expectedValue, int numResultsWait) {
        List<T> expectedValues = new ArrayList<T>();
        expectedValues.add(expectedValue);
        waitForAnyResultValue(listener, resultKey, expectedValues, numResultsWait);
    }

    /**
     * Wait for any expected result key values available in a certain number of results.
     *
     * <p>
     * Check the result immediately if numFramesWait is 0.
     * </p>
     *
     * @param listener The capture listener to get capture result.
     * @param resultKey The capture result key associated with the result value.
     * @param expectedValues The list of result value need to be waited for,
     * return immediately if the list is empty.
     * @param numResultsWait Number of frame to wait before times out.
     * @throws TimeoutRuntimeException If more than numResultsWait results are.
     * seen before the result matching myRequest arrives, or each individual wait
     * for result times out after {@value #WAIT_FOR_RESULT_TIMEOUT_MS}ms.
     */
    protected static <T> void waitForAnyResultValue(SimpleCaptureCallback listener,
            CaptureResult.Key<T> resultKey,
            List<T> expectedValues, int numResultsWait) {
        if (numResultsWait < 0 || listener == null || expectedValues == null) {
            throw new IllegalArgumentException(
                    "Input must be non-negative number and listener/expectedValues "
                    + "must be non-null");
        }

        int i = 0;
        CaptureResult result;
        do {
            result = listener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            T value = result.get(resultKey);
            for ( T expectedValue : expectedValues) {
                if (VERBOSE) {
                    Log.v(TAG, "Current result value for key " + resultKey.getName() + " is: "
                            + value.toString());
                }
                if (value.equals(expectedValue)) {
                    return;
                }
            }
        } while (i++ < numResultsWait);

        throw new TimeoutRuntimeException(
                "Unable to get the expected result value " + expectedValues + " for key " +
                        resultKey.getName() + " after waiting for " + numResultsWait + " results");
    }

    /**
     * Submit a capture once, then submit additional captures in order to ensure that
     * the camera will be synchronized.
     *
     * <p>
     * The additional capture count is determined by android.sync.maxLatency (or
     * a fixed {@value #NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY}) captures if maxLatency is unknown).
     * </p>
     *
     * <p>Returns the number of captures that were submitted (at least 1), which is useful
     * with {@link #waitForNumResults}.</p>
     *
     * @param request capture request to forward to {@link CameraDevice#capture}
     * @param listener request listener to forward to {@link CameraDevice#capture}
     * @param handler handler to forward to {@link CameraDevice#capture}
     *
     * @return the number of captures that were submitted
     *
     * @throws CameraAccessException if capturing failed
     */
    protected int captureRequestsSynchronized(
            CaptureRequest request, CaptureCallback listener, Handler handler)
                    throws CameraAccessException {
        return captureRequestsSynchronized(request, /*count*/1, listener, handler);
    }

    /**
     * Submit a capture {@code count} times, then submit additional captures in order to ensure that
     * the camera will be synchronized.
     *
     * <p>
     * The additional capture count is determined by android.sync.maxLatency (or
     * a fixed {@value #NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY}) captures if maxLatency is unknown).
     * </p>
     *
     * <p>Returns the number of captures that were submitted (at least 1), which is useful
     * with {@link #waitForNumResults}.</p>
     *
     * @param request capture request to forward to {@link CameraDevice#capture}
     * @param count the number of times to submit the request (minimally), must be at least 1
     * @param listener request listener to forward to {@link CameraDevice#capture}
     * @param handler handler to forward to {@link CameraDevice#capture}
     *
     * @return the number of captures that were submitted
     *
     * @throws IllegalArgumentException if {@code count} was not at least 1
     * @throws CameraAccessException if capturing failed
     */
    protected int captureRequestsSynchronized(
            CaptureRequest request, int count, CaptureCallback listener, Handler handler)
                    throws CameraAccessException {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }

        int maxLatency = mStaticInfo.getSyncMaxLatency();
        if (maxLatency == CameraMetadata.SYNC_MAX_LATENCY_UNKNOWN) {
            maxLatency = NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY;
        }

        assertTrue("maxLatency is non-negative", maxLatency >= 0);

        int numCaptures = maxLatency + count;

        for (int i = 0; i < numCaptures; ++i) {
            mSession.capture(request, listener, handler);
        }

        return numCaptures;
    }

    /**
     * Wait for numResultWait frames
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultsWait Number of frame to wait
     *
     * @return the last result, or {@code null} if there was none
     */
    protected static CaptureResult waitForNumResults(SimpleCaptureCallback resultListener,
            int numResultsWait) {
        if (numResultsWait < 0 || resultListener == null) {
            throw new IllegalArgumentException(
                    "Input must be positive number and listener must be non-null");
        }

        CaptureResult result = null;
        for (int i = 0; i < numResultsWait; i++) {
            result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        }

        return result;
    }

    /**
     * Wait for enough results for settings to be applied
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultWaitForUnknownLatency Number of frame to wait if camera device latency is
     *                                       unknown.
     */
    protected void waitForSettingsApplied(SimpleCaptureCallback resultListener,
            int numResultWaitForUnknownLatency) {
        int maxLatency = mStaticInfo.getSyncMaxLatency();
        if (maxLatency == CameraMetadata.SYNC_MAX_LATENCY_UNKNOWN) {
            maxLatency = numResultWaitForUnknownLatency;
        }
        // Wait for settings to take effect
        waitForNumResults(resultListener, maxLatency);
    }


    /**
     * Wait for AE to be stabilized before capture: CONVERGED or FLASH_REQUIRED.
     *
     * <p>Waits for {@code android.sync.maxLatency} number of results first, to make sure
     * that the result is synchronized (or {@code numResultWaitForUnknownLatency} if the latency
     * is unknown.</p>
     *
     * <p>This is a no-op for {@code LEGACY} devices since they don't report
     * the {@code aeState} result.</p>
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultWaitForUnknownLatency Number of frame to wait if camera device latency is
     *                                       unknown.
     */
    protected void waitForAeStable(SimpleCaptureCallback resultListener,
            int numResultWaitForUnknownLatency) {
        waitForSettingsApplied(resultListener, numResultWaitForUnknownLatency);

        if (!mStaticInfo.isHardwareLevelAtLeastLimited()) {
            // No-op for metadata
            return;
        }
        List<Integer> expectedAeStates = new ArrayList<Integer>();
        expectedAeStates.add(new Integer(CaptureResult.CONTROL_AE_STATE_CONVERGED));
        expectedAeStates.add(new Integer(CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED));
        waitForAnyResultValue(resultListener, CaptureResult.CONTROL_AE_STATE, expectedAeStates,
                NUM_RESULTS_WAIT_TIMEOUT);
    }

    /**
     * Wait for AE to be: LOCKED
     *
     * <p>Waits for {@code android.sync.maxLatency} number of results first, to make sure
     * that the result is synchronized (or {@code numResultWaitForUnknownLatency} if the latency
     * is unknown.</p>
     *
     * <p>This is a no-op for {@code LEGACY} devices since they don't report
     * the {@code aeState} result.</p>
     *
     * @param resultListener The capture listener to get capture result back.
     * @param numResultWaitForUnknownLatency Number of frame to wait if camera device latency is
     *                                       unknown.
     */
    protected void waitForAeLocked(SimpleCaptureCallback resultListener,
            int numResultWaitForUnknownLatency) {

        waitForSettingsApplied(resultListener, numResultWaitForUnknownLatency);

        if (!mStaticInfo.isHardwareLevelAtLeastLimited()) {
            // No-op for legacy devices
            return;
        }

        List<Integer> expectedAeStates = new ArrayList<Integer>();
        expectedAeStates.add(new Integer(CaptureResult.CONTROL_AE_STATE_LOCKED));
        waitForAnyResultValue(resultListener, CaptureResult.CONTROL_AE_STATE, expectedAeStates,
                NUM_RESULTS_WAIT_TIMEOUT);
    }

    /**
     * Create an {@link ImageReader} object and get the surface.
     *
     * @param size The size of this ImageReader to be created.
     * @param format The format of this ImageReader to be created
     * @param maxNumImages The max number of images that can be acquired simultaneously.
     * @param listener The listener used by this ImageReader to notify callbacks.
     */
    protected void createImageReader(Size size, int format, int maxNumImages,
            ImageReader.OnImageAvailableListener listener) throws Exception {
        closeImageReader();

        ImageReader r = makeImageReader(size, format, maxNumImages, listener,
                mHandler);
        mReader = r;
        mReaderSurface = r.getSurface();
    }

    /**
     * Close the pending images then close current active {@link ImageReader} object.
     */
    protected void closeImageReader() {
        CameraTestUtils.closeImageReader(mReader);
        mReader = null;
        mReaderSurface = null;
    }

    /**
     * Open a camera device and get the StaticMetadata for a given camera id.
     *
     * @param cameraId The id of the camera device to be opened.
     */
    protected void openDevice(String cameraId) throws Exception {
        mCamera = CameraTestUtils.openCamera(
                mCameraManager, cameraId, mCameraListener, mHandler);
        mCollector.setCameraId(cameraId);
        mStaticInfo = new StaticMetadata(mCameraManager.getCameraCharacteristics(cameraId),
                CheckLevel.ASSERT, /*collector*/null);
        if (mStaticInfo.isColorOutputSupported()) {
            mOrderedPreviewSizes = getSupportedPreviewSizes(cameraId, mCameraManager,
                    getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));
            m1080pBoundedOrderedPreviewSizes = getSupportedPreviewSizes(cameraId, mCameraManager,
                    PREVIEW_SIZE_BOUND);
            mOrderedVideoSizes = getSupportedVideoSizes(cameraId, mCameraManager, PREVIEW_SIZE_BOUND);
            mOrderedStillSizes = getSupportedStillSizes(cameraId, mCameraManager, null);
            // Use ImageFormat.YUV_420_888 for now. TODO: need figure out what's format for preview
            // in public API side.
            mMinPreviewFrameDurationMap =
                mStaticInfo.getAvailableMinFrameDurationsForFormatChecked(ImageFormat.YUV_420_888);
        }
    }

    /**
     * Close the current actively used camera device.
     */
    protected void closeDevice() {
        if (mCamera != null) {
            mCamera.close();
            mCameraListener.waitForState(STATE_CLOSED, CAMERA_CLOSE_TIMEOUT_MS);
            mCamera = null;
            mSession = null;
            mSessionListener = null;
            mStaticInfo = null;
            mOrderedPreviewSizes = null;
            mOrderedVideoSizes = null;
            mOrderedStillSizes = null;
        }
    }

    /**
     * Update the preview surface size.
     *
     * @param size The preview size to be updated.
     */
    protected void updatePreviewSurface(Size size) {
        if (size.equals(mPreviewSize) && mPreviewSurface != null) {
            Log.w(TAG, "Skipping update preview surface size...");
            return;
        }

        mPreviewSize = size;
        Camera2SurfaceViewCtsActivity ctsActivity = getActivity();
        final SurfaceHolder holder = ctsActivity.getSurfaceView().getHolder();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                holder.setFixedSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            }
        });

        boolean res = ctsActivity.waitForSurfaceSizeChanged(
                WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS, mPreviewSize.getWidth(),
                mPreviewSize.getHeight());
        assertTrue("wait for surface change to " + mPreviewSize.toString() + " timed out", res);
        mPreviewHolder = holder;
        mPreviewSurface = holder.getSurface();
        assertNotNull("Preview surface is null", mPreviewSurface);
        assertTrue("Preview surface is invalid", mPreviewSurface.isValid());
    }

    /**
     * Recreate the SurfaceView's Surface
     *
     * Hide and unhide the activity's preview SurfaceView, so that its backing Surface is
     * recreated
     */
    protected void recreatePreviewSurface() {
        Camera2SurfaceViewCtsActivity ctsActivity = getActivity();
        setPreviewVisibility(View.GONE);
        boolean res = ctsActivity.waitForSurfaceState(
            WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS, /*valid*/ false);
        assertTrue("wait for surface destroyed timed out", res);
        setPreviewVisibility(View.VISIBLE);
        res = ctsActivity.waitForSurfaceState(
            WAIT_FOR_SURFACE_CHANGE_TIMEOUT_MS, /*valid*/ true);
        assertTrue("wait for surface created timed out", res);
    }

    /**
     * Show/hide the preview SurfaceView.
     *
     * If set to View.GONE, the surfaceDestroyed callback will fire
     * @param visibility the new new visibility to set, one of View.VISIBLE / INVISIBLE / GONE
     */
    protected void setPreviewVisibility(int visibility) {
        final Camera2SurfaceViewCtsActivity ctsActivity = getActivity();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                ctsActivity.getSurfaceView().setVisibility(visibility);
            }
        });
    }

    /**
     * Setup single capture configuration and start preview.
     *
     * @param previewRequest The capture request to be used for preview
     * @param stillRequest The capture request to be used for still capture
     * @param previewSz Preview size
     * @param captureSz Still capture size
     * @param format The single capture image format
     * @param resultListener Capture result listener
     * @param maxNumImages The max number of images set to the image reader
     * @param imageListener The single capture capture image listener
     */
    protected void prepareCaptureAndStartPreview(CaptureRequest.Builder previewRequest,
            CaptureRequest.Builder stillRequest, Size previewSz, Size captureSz, int format,
            CaptureCallback resultListener, int maxNumImages,
            ImageReader.OnImageAvailableListener imageListener) throws Exception {
        prepareCaptureAndStartPreview(previewRequest, stillRequest, previewSz, captureSz,
            format, resultListener, null, maxNumImages, imageListener);
    }

    /**
     * Setup single capture configuration and start preview.
     *
     * @param previewRequest The capture request to be used for preview
     * @param stillRequest The capture request to be used for still capture
     * @param previewSz Preview size
     * @param captureSz Still capture size
     * @param format The single capture image format
     * @param resultListener Capture result listener
     * @param sessionListener Session listener
     * @param maxNumImages The max number of images set to the image reader
     * @param imageListener The single capture capture image listener
     */
    protected void prepareCaptureAndStartPreview(CaptureRequest.Builder previewRequest,
            CaptureRequest.Builder stillRequest, Size previewSz, Size captureSz, int format,
            CaptureCallback resultListener, CameraCaptureSession.StateCallback sessionListener,
            int maxNumImages, ImageReader.OnImageAvailableListener imageListener) throws Exception {
        if (VERBOSE) {
            Log.v(TAG, String.format("Prepare single capture (%s) and preview (%s)",
                    captureSz.toString(), previewSz.toString()));
        }

        // Update preview size.
        updatePreviewSurface(previewSz);

        // Create ImageReader.
        createImageReader(captureSz, format, maxNumImages, imageListener);

        // Configure output streams with preview and jpeg streams.
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(mReaderSurface);
        if (sessionListener == null) {
            mSessionListener = new BlockingSessionCallback();
        } else {
            mSessionListener = new BlockingSessionCallback(sessionListener);
        }
        mSession = configureCameraSession(mCamera, outputSurfaces, mSessionListener, mHandler);

        // Configure the requests.
        previewRequest.addTarget(mPreviewSurface);
        stillRequest.addTarget(mPreviewSurface);
        stillRequest.addTarget(mReaderSurface);

        // Start preview.
        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
    }

    /**
     * Get the max preview size that supports the given fpsRange.
     *
     * @param fpsRange The fps range the returned size must support.
     * @return max size that support the given fps range.
     */
    protected Size getMaxPreviewSizeForFpsRange(Range<Integer> fpsRange) {
        if (fpsRange == null || fpsRange.getLower() <= 0 || fpsRange.getUpper() <= 0) {
            throw new IllegalArgumentException("Invalid fps range argument");
        }
        if (mOrderedPreviewSizes == null || mMinPreviewFrameDurationMap == null) {
            throw new IllegalStateException("mOrderedPreviewSizes and mMinPreviewFrameDurationMap"
                    + " must be initialized");
        }

        long[] frameDurationRange =
                new long[]{(long) (1e9 / fpsRange.getUpper()), (long) (1e9 / fpsRange.getLower())};
        for (Size size : mOrderedPreviewSizes) {
            Long minDuration = mMinPreviewFrameDurationMap.get(size);
            if (minDuration == null ||
                    minDuration == 0) {
                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    throw new IllegalArgumentException(
                            "No min frame duration available for the size " + size);
                }
                continue;
            }
            if (minDuration <= (frameDurationRange[0] + MIN_FRAME_DURATION_ERROR_MARGIN)) {
                return size;
            }
        }

        // Search again for sizes not bounded by display size
        for (Size size : m1080pBoundedOrderedPreviewSizes) {
            Long minDuration = mMinPreviewFrameDurationMap.get(size);
            if (minDuration == null ||
                    minDuration == 0) {
                if (mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    throw new IllegalArgumentException(
                            "No min frame duration available for the size " + size);
                }
                continue;
            }
            if (minDuration <= (frameDurationRange[0] + MIN_FRAME_DURATION_ERROR_MARGIN)) {
                return size;
            }
        }
        return null;
    }

    protected boolean isReprocessSupported(String cameraId, int format)
            throws CameraAccessException {
        if (format != ImageFormat.YUV_420_888 && format != ImageFormat.PRIVATE) {
            throw new IllegalArgumentException(
                    "format " + format + " is not supported for reprocessing");
        }

        StaticMetadata info =
                new StaticMetadata(mCameraManager.getCameraCharacteristics(cameraId),
                                   CheckLevel.ASSERT, /*collector*/ null);
        int cap = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING;
        if (format == ImageFormat.PRIVATE) {
            cap = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING;
        }
        return info.isCapabilitySupported(cap);
    }

    protected Range<Integer> getSuitableFpsRangeForDuration(String cameraId, long frameDuration) {
        // Add 0.05 here so Fps like 29.99 evaluated to 30
        int minBurstFps = (int) Math.floor(1e9 / frameDuration + 0.05f);
        boolean foundConstantMaxYUVRange = false;
        boolean foundYUVStreamingRange = false;

        // Find suitable target FPS range - as high as possible that covers the max YUV rate
        // Also verify that there's a good preview rate as well
        List<Range<Integer> > fpsRanges = Arrays.asList(
                mStaticInfo.getAeAvailableTargetFpsRangesChecked());
        Range<Integer> targetRange = null;
        for (Range<Integer> fpsRange : fpsRanges) {
            if (fpsRange.getLower() == minBurstFps && fpsRange.getUpper() == minBurstFps) {
                foundConstantMaxYUVRange = true;
                targetRange = fpsRange;
            }
            if (fpsRange.getLower() <= 15 && fpsRange.getUpper() == minBurstFps) {
                foundYUVStreamingRange = true;
            }
        }

        assertTrue(String.format("Cam %s: Target FPS range of (%d, %d) must be supported",
                cameraId, minBurstFps, minBurstFps), foundConstantMaxYUVRange);
        assertTrue(String.format(
                "Cam %s: Target FPS range of (x, %d) where x <= 15 must be supported",
                cameraId, minBurstFps), foundYUVStreamingRange);
        return targetRange;
    }
}
