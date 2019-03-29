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

package android.permission.cts;

import static com.android.ex.camera2.blocking.BlockingStateCallback.*;

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.platform.test.annotations.Presubmit;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingStateCallback;

/**
 * Tests for Camera2 API related Permissions. Currently, this means
 * android.permission.CAMERA.
 */
public class Camera2PermissionTest extends AndroidTestCase {
    private static final String TAG = "CameraDeviceTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final int CAMERA_CLOSE_TIMEOUT_MS = 2000;

    private CameraManager mCameraManager;
    private CameraDevice mCamera;
    private BlockingStateCallback mCameraListener;
    private String[] mCameraIds;
    protected Handler mHandler;
    protected HandlerThread mHandlerThread;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        assertNotNull("Can't connect to camera manager!", mCameraManager);
    }

    /**
     * Set up the camera2 test case required environments, including CameraManager,
     * HandlerThread, Camera IDs, and CameraStateCallback etc.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCameraIds = mCameraManager.getCameraIdList();
        assertNotNull("Camera ids shouldn't be null", mCameraIds);
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mCameraListener = new BlockingStateCallback();
    }

    @Override
    protected void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;

        super.tearDown();
    }

    /**
     * Attempt to open camera. Requires Permission:
     * {@link android.Manifest.permission#CAMERA}.
     */
    public void testCameraOpen() throws Exception {
        for (String id : mCameraIds) {
            try {
                openCamera(id);
                fail("Was able to open camera " + id + " with no permission");
            }
            catch (SecurityException e) {
                // expected
            } finally {
                closeCamera();
            }
        }
    }

    /**
     * Add and remove availability listeners should work without permission.
     */
    @Presubmit
    public void testAvailabilityCallback() throws Exception {
        DummyCameraListener availabilityListener = new DummyCameraListener();
        // Remove a not-registered listener is a no-op.
        mCameraManager.unregisterAvailabilityCallback(availabilityListener);
        mCameraManager.registerAvailabilityCallback(availabilityListener, mHandler);
        mCameraManager.unregisterAvailabilityCallback(availabilityListener);
        mCameraManager.registerAvailabilityCallback(availabilityListener, mHandler);
        mCameraManager.registerAvailabilityCallback(availabilityListener, mHandler);
        mCameraManager.unregisterAvailabilityCallback(availabilityListener);
        // Remove a previously-added listener second time is a no-op.
        mCameraManager.unregisterAvailabilityCallback(availabilityListener);
    }

    private class DummyCameraListener extends CameraManager.AvailabilityCallback {
        @Override
        public void onCameraAvailable(String cameraId) {
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
        }
    }

    private void openCamera(String cameraId) throws Exception {
        mCamera = (new BlockingCameraManager(mCameraManager)).openCamera(
                cameraId, mCameraListener, mHandler);
    }

    private void closeCamera() {
        if (mCamera != null) {
            mCamera.close();
            mCameraListener.waitForState(STATE_CLOSED, CAMERA_CLOSE_TIMEOUT_MS);
            mCamera = null;
        }
    }
}
