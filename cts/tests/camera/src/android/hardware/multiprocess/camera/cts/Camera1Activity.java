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
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;

/**
 * Activity implementing basic access of the Camera1 API.
 *
 * <p />
 * This will log all errors to {@link android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class Camera1Activity extends Activity {
    private static final String TAG = "Camera1Activity";

    Camera mCamera;
    ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();
        try {
            mCamera = Camera.open();
            if (mCamera == null) {
                mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                        " no cameras available.");
            }
            mCamera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int i, Camera camera) {
                    if (i == Camera.CAMERA_ERROR_EVICTED) {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_EVICTED,
                                TAG + " camera evicted");
                        Log.e(TAG, "onError called with event " + i + ", camera evicted");
                    } else {
                        mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR,
                                TAG + " camera experienced error: " + i);
                        Log.e(TAG, "onError called with event " + i + ", camera error");
                    }
                }
            });
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT,
                    TAG + " camera connected");
        } catch (RuntimeException e) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                    " camera exception during connection: " + e);
            Log.e(TAG, "Runtime error: " + e);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause called.");
        super.onPause();
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
