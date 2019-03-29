/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.managedprofile;

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A util class to help open camera in a blocking way.
 */
class CameraUtils {

    private static final String TAG = "CameraUtils";

    /**
     * @return true if success to open camera, false otherwise.
     */
    public static boolean blockUntilOpenCamera(CameraManager cameraManager, Handler handler) {
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            if (cameraIdList == null || cameraIdList.length == 0) {
                return false;
            }
            String cameraId = cameraIdList[0];
            CameraCallback callback = new CameraCallback();
            cameraManager.openCamera(cameraId, callback, handler);
            return callback.waitForResult();
        } catch (Exception ex) {
            // No matter what is going wrong, it means fail to open camera.
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private static class CameraCallback extends CameraDevice.StateCallback {

        private static final int OPEN_TIMEOUT_SECONDS = 5;

        private final CountDownLatch mLatch = new CountDownLatch(1);

        private AtomicBoolean mResult = new AtomicBoolean(false);

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.d(TAG, "open camera successfully");
            mResult.set(true);
            if (cameraDevice != null) {
                cameraDevice.close();
            }
            mLatch.countDown();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.d(TAG, "disconnect camera");
            mLatch.countDown();
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            Log.e(TAG, "Fail to open camera, error code = " + error);
            mLatch.countDown();
        }

        public boolean waitForResult() throws InterruptedException {
            mLatch.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return mResult.get();
        }
    }
}
