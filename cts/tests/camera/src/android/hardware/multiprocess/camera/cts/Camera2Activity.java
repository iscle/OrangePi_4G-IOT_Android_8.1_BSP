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
 * limitations under the License.
 */

package android.hardware.multiprocess.camera.cts;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

/**
 * Activity implementing basic access of the Camera2 API.
 *
 * <p />
 * This will log all errors to {@link android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class Camera2Activity extends Activity {
    private static final String TAG = "Camera2Activity";

    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause called.");
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();

        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

            if (manager == null) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                        " could not connect camera service");
                return;
            }
            String[] cameraIds = manager.getCameraIdList();

            if (cameraIds == null || cameraIds.length == 0) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                        " device reported having no cameras");
                return;
            }

            manager.registerAvailabilityCallback(new CameraManager.AvailabilityCallback() {
                @Override
                public void onCameraAvailable(String cameraId) {
                    super.onCameraAvailable(cameraId);
                    mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_AVAILABLE,
                            cameraId);
                    Log.i(TAG, "Camera " + cameraId + " is available");
                }

                @Override
                public void onCameraUnavailable(String cameraId) {
                    super.onCameraUnavailable(cameraId);
                    mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_UNAVAILABLE,
                            cameraId);
                    Log.i(TAG, "Camera " + cameraId + " is unavailable");
                }
            }, null);

            final String chosen = cameraIds[0];

            manager.openCamera(chosen, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cameraDevice) {
                    mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT,
                            chosen);
                    Log.i(TAG, "Camera " + chosen + " is opened");
                }

                @Override
                public void onDisconnected(CameraDevice cameraDevice) {
                    mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_EVICTED,
                            chosen);
                    Log.i(TAG, "Camera " + chosen + " is disconnected");
                }

                @Override
                public void onError(CameraDevice cameraDevice, int i) {
                    mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                            " Camera " + chosen + " experienced error " + i);
                    Log.e(TAG, "Camera " + chosen + " onError called with error " + i);
                }
            }, null);
        } catch (CameraAccessException e) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                    " camera exception during connection: " + e);
            Log.e(TAG, "Access exception: " + e);
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called.");
        super.onDestroy();
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }
    }
}
