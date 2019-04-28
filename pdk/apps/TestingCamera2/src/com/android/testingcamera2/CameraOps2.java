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

package com.android.testingcamera2;

import java.util.HashSet;
import java.util.Set;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraAccessException;

/**
 * A central manager of camera devices and current clients for them.
 *
 */
public class CameraOps2 extends CameraManager.AvailabilityCallback {

    private final CameraManager mCameraManager;
    private final Activity mActivity;
    private final Set<CameraDevice> mOpenCameras = new HashSet<CameraDevice>();

    // For persisting values for permission requests
    private static final int PERMISSIONS_REQUEST_CAMERA = 1;
    private String mDelayedOpenId = null;
    private CameraDevice.StateCallback mDelayedOpenListener = null;

    public CameraOps2(Activity activity) {
        mActivity = activity;
        mCameraManager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        if (mCameraManager == null) {
            throw new AssertionError("Can't connect to camera manager!");
        }
        try {
            String[] cameraIds = mCameraManager.getCameraIdList();
            TLog.i("Camera count: %d", cameraIds.length);
            for (String cameraId : cameraIds) {
                TLog.i("  Camera %s", cameraId);
            }
        } catch (CameraAccessException e) {
            TLog.e("Unable to get camera list: %s", e);
        }

        mCameraManager.registerAvailabilityCallback(this, /*handler*/null);
    }

    /**
     * Add a listener for new camera addition events, and retrieve the list of
     * current cameras
     *
     * @param listener
     *            A listener to notify on changes to camera availability
     * @return the current list of available cameras
     * @throws CameraAccessException
     *             if the camera manager cannot be queried
     */
    public String[] getCamerasAndListen(CameraManager.AvailabilityCallback listener)
            throws CameraAccessException {

        mCameraManager.registerAvailabilityCallback(listener, /*handler*/null);

        return mCameraManager.getCameraIdList();
    }

    public void removeAvailabilityCallback(CameraManager.AvailabilityCallback listener) {
        mCameraManager.unregisterAvailabilityCallback(listener);
    }

    @Override
    public void onCameraAvailable(String cameraId) {
        TLog.i("Camera %s is now available", cameraId);
    }

    @Override
    public void onCameraUnavailable(String cameraId) {
        TLog.i("Camera %s is now unavailable", cameraId);
    }

    /**
     * Attempt to open a camera device. Returns false if the open call cannot be
     * made or the device is already open
     *
     * @param cameraId id of the camera to open
     * @param listener listener to notify of camera device state changes
     * @return true if open call was sent successfully. The client needs to wait
     *         for its listener to be called to determine if open will succeed.
     */
    public boolean openCamera(String cameraId, CameraDevice.StateCallback listener) {
        for (CameraDevice camera : mOpenCameras) {
            if (camera.getId() == cameraId) {
                TLog.e("Camera %s is already open", cameraId);
                return false;
            }
        }
        if ((mActivity.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
            || (mActivity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            TLog.i("Requesting camera/storage permissions");

            mDelayedOpenId = cameraId;
            mDelayedOpenListener = listener;

            mActivity.requestPermissions(new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE },
                    PERMISSIONS_REQUEST_CAMERA);
            return false;
        }

        return doOpenCamera(cameraId, listener);
    }

    private boolean doOpenCamera(String cameraId, CameraDevice.StateCallback listener) {
        try {
            DeviceStateCallback proxyListener = new DeviceStateCallback(listener);
            mCameraManager.openCamera(cameraId, proxyListener, null);
        } catch (CameraAccessException e) {
            TLog.e("Unable to open camera %s.", e, cameraId);
            return false;
        }

        return true;
    }

    public void onRequestPermissionsResult (int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                TLog.i("Camera permission granted");
                if (mDelayedOpenId != null && mDelayedOpenListener != null) {
                    doOpenCamera(mDelayedOpenId, mDelayedOpenListener);
                }
                mDelayedOpenId = null;
                mDelayedOpenListener = null;
            } else {
                TLog.i("Camera permission denied, not opening camera");
                if (mDelayedOpenId != null && mDelayedOpenListener != null) {
                    mDelayedOpenListener.onError(null,
                            CameraDevice.StateCallback.ERROR_CAMERA_DISABLED);
                    mDelayedOpenId = null;
                    mDelayedOpenListener = null;
                }
            }
            if (grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                TLog.i("Storage permission granted");
            } else {
                TLog.i("Storage permission not granted; saving will not work");
            }
        }
    }

    public CameraCharacteristics getCameraInfo(String cameraId) {
        try {
            return mCameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            TLog.e("Unable to get camera characteristics for camera %s.", e, cameraId);
        }
        return null;
    }

    private class DeviceStateCallback extends CameraDevice.StateCallback {

        private final CameraDevice.StateCallback mClientListener;

        public DeviceStateCallback(CameraDevice.StateCallback clientListener) {
            mClientListener = clientListener;
        }

        @Override
        public void onClosed(CameraDevice camera) {
            mOpenCameras.remove(camera);
            TLog.i("Camera %s now closed", camera.getId());
            mClientListener.onClosed(camera);
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            TLog.i("Camera %s now disconnected", camera.getId());
            mClientListener.onDisconnected(camera);
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            TLog.i("Camera %s encountered error: %d", camera.getId(), error);
            mClientListener.onError(camera, error);
        }

        @Override
        public void onOpened(CameraDevice camera) {
            mOpenCameras.add(camera);
            TLog.i("Camera %s now open", camera.getId());
            mClientListener.onOpened(camera);
        }

    }
}
