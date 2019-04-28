/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.devcamera;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.util.Log;

/**
 * Static utility class that logs various camera2 callbacks.
 *
 * The only reason this exists as a separate class is void cluttering up Api2Camera.
 */

public class LoggingCallbacks {
    private static final String TAG = "DevCamera_LOG2";
    private static final Boolean LOG_EVERY_FRAME = false;
    private static final Boolean LOG_NON_ERRORS = false;

    public static class DeviceStateCallback extends CameraDevice.StateCallback {
        @Override
        public void onOpened(CameraDevice camera) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Camera opened.");
            }
        }

        @Override
        public void onClosed(CameraDevice camera) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Camera closed.");
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            Log.v(TAG, "Camera disconnected.");
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            Log.v(TAG, "Camera error: " + error);
        }
    }

    public static class SessionStateCallback extends CameraCaptureSession.StateCallback {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Capture session callback onConfigured("+session+")");
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.v(TAG, "Capture session callback onConfigureFailed("+session+")");
            super.onReady(session);
        }

        @Override
        public void onReady(CameraCaptureSession session) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Capture session callback onReady("+session+")");
            }
            super.onReady(session);
        }

        @Override
        public void onActive(CameraCaptureSession session) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Capture session callback onActive("+session+")");
            }
            super.onActive(session);
        }

        @Override
        public void onClosed(CameraCaptureSession session) {
            if (LOG_NON_ERRORS) {
                Log.v(TAG, "Capture session callback onClosed("+session+")");
            }
            super.onClosed(session);
        }
    }

    public static class SessionCaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            if (LOG_EVERY_FRAME) {
                Log.v(TAG, "Capture started.");
            }
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
            if (LOG_EVERY_FRAME) {
                Log.v(TAG, "Capture progressed.");
            }
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (LOG_EVERY_FRAME) {
                Log.v(TAG, "Capture completed.");
            }
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

    }
}
