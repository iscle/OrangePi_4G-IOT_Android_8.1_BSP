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
import static com.android.ex.camera2.blocking.BlockingSessionCallback.*;
import static com.android.ex.camera2.blocking.BlockingStateCallback.*;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.cts.Camera2MultiViewCtsActivity;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.helpers.StaticMetadata.CheckLevel;
import android.hardware.camera2.params.OutputConfiguration;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;

import junit.framework.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.HashMap;

/**
 * Camera2 test case base class by using mixed SurfaceView and TextureView as rendering target.
 */
public class Camera2MultiViewTestCase extends
        ActivityInstrumentationTestCase2<Camera2MultiViewCtsActivity> {
    private static final String TAG = "MultiViewTestCase";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);


    private static final long SHORT_SLEEP_WAIT_TIME_MS = 100;

    protected TextureView[] mTextureView = new TextureView[2];
    protected String[] mCameraIds;
    protected Handler mHandler;

    private CameraManager mCameraManager;
    private BlockingStateCallback mCameraListener;
    private HandlerThread mHandlerThread;
    private Context mContext;

    private CameraHolder[] mCameraHolders;
    private HashMap<String, Integer> mCameraIdMap;

    protected WindowManager mWindowManager;

    public Camera2MultiViewTestCase() {
        super(Camera2MultiViewCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getActivity();
        assertNotNull("Unable to get activity", mContext);
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Unable to get CameraManager", mCameraManager);
        mCameraIds = mCameraManager.getCameraIdList();
        assertNotNull("Unable to get camera ids", mCameraIds);
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCameraListener = new BlockingStateCallback();
        Camera2MultiViewCtsActivity activity = (Camera2MultiViewCtsActivity) mContext;
        mTextureView[0] = activity.getTextureView(0);
        mTextureView[1] = activity.getTextureView(1);
        assertNotNull("Unable to get texture view", mTextureView);
        mCameraIdMap = new HashMap<String, Integer>();
        int numCameras = mCameraIds.length;
        mCameraHolders = new CameraHolder[numCameras];
        for (int i = 0; i < numCameras; i++) {
            mCameraHolders[i] = new CameraHolder(mCameraIds[i]);
            mCameraIdMap.put(mCameraIds[i], i);
        }
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;
        mCameraListener = null;
        for (CameraHolder camera : mCameraHolders) {
            if (camera.isOpened()) {
                camera.close();
                camera = null;
            }
        }
        super.tearDown();
    }

    /**
     * Update preview TextureView rotation to accommodate discrepancy between preview
     * buffer and the view window orientation.
     *
     * Assumptions:
     * - Aspect ratio for the sensor buffers is in landscape orientation,
     * - Dimensions of buffers received are rotated to the natural device orientation.
     * - The contents of each buffer are rotated by the inverse of the display rotation.
     * - Surface scales the buffer to fit the current view bounds.
     * TODO: Make this method works for all orientations
     *
     */
    protected void updatePreviewDisplayRotation(Size previewSize, TextureView textureView) {
        int rotationDegrees = 0;
        Camera2MultiViewCtsActivity activity = (Camera2MultiViewCtsActivity) mContext;
        int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Configuration config = activity.getResources().getConfiguration();

        // Get UI display rotation
        switch (displayRotation) {
            case Surface.ROTATION_0:
                rotationDegrees = 0;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90;
            break;
            case Surface.ROTATION_180:
                rotationDegrees = 180;
            break;
            case Surface.ROTATION_270:
                rotationDegrees = 270;
            break;
        }

        // Get device natural orientation
        int deviceOrientation = Configuration.ORIENTATION_PORTRAIT;
        if ((rotationDegrees % 180 == 0 &&
                config.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
                ((rotationDegrees % 180 != 0 &&
                config.orientation == Configuration.ORIENTATION_PORTRAIT))) {
            deviceOrientation = Configuration.ORIENTATION_LANDSCAPE;
        }

        // Rotate the buffer dimensions if device orientation is portrait.
        int effectiveWidth = previewSize.getWidth();
        int effectiveHeight = previewSize.getHeight();
        if (deviceOrientation == Configuration.ORIENTATION_PORTRAIT) {
            effectiveWidth = previewSize.getHeight();
            effectiveHeight = previewSize.getWidth();
        }

        // Find and center view rect and buffer rect
        Matrix transformMatrix =  textureView.getTransform(null);
        int viewWidth = textureView.getWidth();
        int viewHeight = textureView.getHeight();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufRect = new RectF(0, 0, effectiveWidth, effectiveHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        bufRect.offset(centerX - bufRect.centerX(), centerY - bufRect.centerY());

        // Undo ScaleToFit.FILL done by the surface
        transformMatrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL);

        // Rotate buffer contents to proper orientation
        transformMatrix.postRotate((360 - rotationDegrees) % 360, centerX, centerY);
        if ((rotationDegrees % 180) == 90) {
            int temp = effectiveWidth;
            effectiveWidth = effectiveHeight;
            effectiveHeight = temp;
        }

        // Scale to fit view, cropping the longest dimension
        float scale =
                Math.max(viewWidth / (float) effectiveWidth, viewHeight / (float) effectiveHeight);
        transformMatrix.postScale(scale, scale, centerX, centerY);

        Handler handler = new Handler(Looper.getMainLooper());
        class TransformUpdater implements Runnable {
            TextureView mView;
            Matrix mTransformMatrix;
            TransformUpdater(TextureView view, Matrix matrix) {
                mView = view;
                mTransformMatrix = matrix;
            }

            @Override
            public void run() {
                mView.setTransform(mTransformMatrix);
            }
        }
        handler.post(new TransformUpdater(textureView, transformMatrix));
    }

    protected void openCamera(String cameraId) throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        assertFalse("Camera has already opened", camera.isOpened());
        camera.open();
        return;
    }

    protected void closeCamera(String cameraId) throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        camera.close();
    }

    protected void createSessionWithConfigs(String cameraId, List<OutputConfiguration> configs)
            throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        camera.createSessionWithConfigs(configs);
    }

    protected void startPreview(
            String cameraId, List<Surface> outputSurfaces, CaptureCallback listener)
            throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera " + cameraId + " is not openned", camera.isOpened());
        camera.startPreview(outputSurfaces, listener);
    }

    protected void startPreviewWithConfigs(String cameraId,
            List<OutputConfiguration> outputConfigs,
            CaptureCallback listener)
            throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera " + cameraId + " is not openned", camera.isOpened());
        camera.startPreviewWithConfigs(outputConfigs, listener);
    }

    protected void stopPreview(String cameraId) throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera " + cameraId + " preview is not running", camera.isPreviewStarted());
        camera.stopPreview();
    }

    protected void updateOutputConfigs(String cameraId, List<OutputConfiguration> configs,
            CaptureCallback listener) throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera " + cameraId + " is not opened", camera.isOpened());
        camera.updateOutputConfigs(configs, listener);
    }

    protected StaticMetadata getStaticInfo(String cameraId) {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera is not openned", camera.isOpened());
        return camera.getStaticInfo();
    }

    protected List<Size> getOrderedPreviewSizes(String cameraId) {
        CameraHolder camera = getCameraHolder(cameraId);
        assertTrue("Camera is not openned", camera.isOpened());
        return camera.getOrderedPreviewSizes();
    }

    protected void verifyCreateSessionWithConfigsFailure(String cameraId,
            List<OutputConfiguration> configs) throws Exception {
        CameraHolder camera = getCameraHolder(cameraId);
        camera.verifyCreateSessionWithConfigsFailure(configs);
    }

    /**
     * Wait until the SurfaceTexture available from the TextureView, then return it.
     * Return null if the wait times out.
     *
     * @param timeOutMs The timeout value for the wait
     * @return The available SurfaceTexture, return null if the wait times out.
     */
    protected SurfaceTexture getAvailableSurfaceTexture(long timeOutMs, TextureView view) {
        long waitTime = timeOutMs;

        while (!view.isAvailable() && waitTime > 0) {
            long startTimeMs = SystemClock.elapsedRealtime();
            SystemClock.sleep(SHORT_SLEEP_WAIT_TIME_MS);
            waitTime -= (SystemClock.elapsedRealtime() - startTimeMs);
        }

        if (view.isAvailable()) {
            return view.getSurfaceTexture();
        } else {
            Log.w(TAG, "Wait for SurfaceTexture available timed out after " + timeOutMs + "ms");
            return null;
        }
    }

    public static class CameraPreviewListener implements TextureView.SurfaceTextureListener {
        private boolean mFirstPreviewAvailable = false;
        private final ConditionVariable mPreviewDone = new ConditionVariable();

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Ignored. The SurfaceTexture is polled by getAvailableSurfaceTexture.
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Ignored. The CameraDevice should already know the changed size.
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            /**
             * Return true, assume that client detaches the surface before it is
             * destroyed. For example, CameraDevice should detach this surface when
             * stopping preview. No need to release the SurfaceTexture here as it
             * is released by TextureView after onSurfaceTextureDestroyed is called.
             */
            Log.i(TAG, "onSurfaceTextureDestroyed called.");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Invoked every time there's a new Camera preview frame
            if (!mFirstPreviewAvailable) {
                mFirstPreviewAvailable = true;
                mPreviewDone.open();
            }
        }

        /** Waits until the camera preview is up running */
        public boolean waitForPreviewDone(long timeOutMs) {
            if (!mPreviewDone.block(timeOutMs)) {
                // timeout could be expected or unexpected. The caller will decide.
                Log.w(TAG, "waitForPreviewDone timed out after " + timeOutMs + "ms");
                return false;
            }
            mPreviewDone.close();
            return true;
        }

        /** Reset the Listener */
        public void reset() {
            mFirstPreviewAvailable = false;
            mPreviewDone.close();
        }
    }

    private CameraHolder getCameraHolder(String cameraId) {
        Integer cameraIdx = mCameraIdMap.get(cameraId);
        if (cameraIdx == null) {
            Assert.fail("Unknown camera Id");
        }
        return mCameraHolders[cameraIdx];
    }

    // Per device fields
    private class CameraHolder {
        private String mCameraId;
        private CameraCaptureSession mSession;
        private CameraDevice mCamera;
        private StaticMetadata mStaticInfo;
        private List<Size> mOrderedPreviewSizes;
        private BlockingSessionCallback mSessionListener;

        public CameraHolder(String id){
            mCameraId = id;
        }

        public StaticMetadata getStaticInfo() {
            return mStaticInfo;
        }

        public List<Size> getOrderedPreviewSizes() {
            return mOrderedPreviewSizes;
        }

        public void open() throws Exception {
            assertNull("Camera is already opened", mCamera);
            mCamera = (new BlockingCameraManager(mCameraManager)).openCamera(
                    mCameraId, mCameraListener, mHandler);
            mStaticInfo = new StaticMetadata(mCameraManager.getCameraCharacteristics(mCameraId),
                    CheckLevel.ASSERT, /*collector*/null);
            if (mStaticInfo.isColorOutputSupported()) {
                mOrderedPreviewSizes = getSupportedPreviewSizes(
                        mCameraId, mCameraManager,
                        getPreviewSizeBound(mWindowManager, PREVIEW_SIZE_BOUND));
            }
            assertNotNull(String.format("Failed to open camera device ID: %s", mCameraId), mCamera);
        }

        public boolean isOpened() {
            return (mCamera != null);
        }

        public void close() throws Exception {
            if (!isOpened()) {
                return;
            }
            mCamera.close();
            mCameraListener.waitForState(STATE_CLOSED, CAMERA_CLOSE_TIMEOUT_MS);
            mCamera = null;
            mSession = null;
            mStaticInfo = null;
            mOrderedPreviewSizes = null;
        }

        public void startPreview(List<Surface> outputSurfaces, CaptureCallback listener)
                throws Exception {
            mSessionListener = new BlockingSessionCallback();
            mSession = configureCameraSession(mCamera, outputSurfaces, mSessionListener, mHandler);

            // TODO: vary the different settings like crop region to cover more cases.
            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (Surface surface : outputSurfaces) {
                captureBuilder.addTarget(surface);
            }
            mSession.setRepeatingRequest(captureBuilder.build(), listener, mHandler);
        }

        public void createSessionWithConfigs(List<OutputConfiguration> outputConfigs)
                throws Exception {
            mSessionListener = new BlockingSessionCallback();
            mSession = configureCameraSessionWithConfig(mCamera, outputConfigs, mSessionListener, mHandler);
        }

        public void verifyCreateSessionWithConfigsFailure(List<OutputConfiguration> configs)
                throws Exception {
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            CameraCaptureSession session = configureCameraSessionWithConfig(
                    mCamera, configs, sessionListener, mHandler);

            Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                    BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
            int state = sessionListener.getStateWaiter().waitForAnyOfStates(
                    Arrays.asList(sessionStates), SESSION_CONFIGURE_TIMEOUT_MS);
            assertTrue("Expecting a createSessionWithConfig failure.",
                    state == BlockingSessionCallback.SESSION_CONFIGURE_FAILED);
        }

        public void startPreviewWithConfigs(List<OutputConfiguration> outputConfigs,
                CaptureCallback listener)
                throws Exception {
            createSessionWithConfigs(outputConfigs);

            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (OutputConfiguration config : outputConfigs) {
                for (Surface surface : config.getSurfaces()) {
                    captureBuilder.addTarget(surface);
                }
            }
            mSession.setRepeatingRequest(captureBuilder.build(), listener, mHandler);
        }

        public void updateOutputConfigs(List<OutputConfiguration> configs,
                CaptureCallback listener) throws Exception {
            mSession.finalizeOutputConfigurations(configs);

            CaptureRequest.Builder captureBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            for (OutputConfiguration config : configs) {
                for (Surface surface : config.getSurfaces()) {
                    captureBuilder.addTarget(surface);
                }
            }
            mSession.setRepeatingRequest(captureBuilder.build(), listener, mHandler);
        }

        public boolean isPreviewStarted() {
            return (mSession != null);
        }

        public void stopPreview() throws Exception {
            if (VERBOSE) Log.v(TAG,
                    "Stopping camera " + mCameraId +" preview and waiting for idle");
            // Stop repeat, wait for captures to complete, and disconnect from surfaces
            mSession.close();
            mSessionListener.getStateWaiter().waitForState(
                    SESSION_CLOSED, SESSION_CLOSE_TIMEOUT_MS);
            mSessionListener = null;
        }
    }
}
