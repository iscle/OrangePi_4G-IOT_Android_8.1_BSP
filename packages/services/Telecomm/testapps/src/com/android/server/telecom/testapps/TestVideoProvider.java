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
 * limitations under the License
 */

package com.android.server.telecom.testapps;

import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.server.telecom.testapps.R;
import com.android.server.telecom.testapps.TestConnectionService.TestConnection;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.telecom.Connection;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.lang.IllegalArgumentException;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Implements the VideoCallProvider.
 */
public class TestVideoProvider extends Connection.VideoProvider {
    private TestConnection mConnection;
    private CameraCapabilities mCameraCapabilities;
    private Random random;
    private Surface mDisplaySurface;
    private Surface mPreviewSurface;
    private Context mContext;
    /** Used to play incoming video during a call. */
    private MediaPlayer mIncomingMediaPlayer;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraSession;
    private CameraThread mLooperThread;

    private final Handler mHandler = new Handler();

    private String mCameraId;

    private static final long SESSION_TIMEOUT_MS = 2000;

    public TestVideoProvider(Context context, TestConnection connection) {
        mConnection = connection;
        mContext = context;
        random = new Random();
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public void onSetCamera(String cameraId) {
        log("Set camera to " + cameraId);
        mCameraId = cameraId;

        stopCamera();
        // Get the capabilities of the camera
        setCameraCapabilities(mCameraId);
    }

    @Override
    public void onSetPreviewSurface(Surface surface) {
        log("Set preview surface " + (surface == null ? "unset" : "set"));
        if (mPreviewSurface != null) {
            stopCamera();
        }

        mPreviewSurface = surface;

        if (!TextUtils.isEmpty(mCameraId) && mPreviewSurface != null) {
            startCamera(mCameraId);
        }
    }

    @Override
    public void onSetDisplaySurface(Surface surface) {
        log("Set display surface " + (surface == null ? "unset" : "set"));
        mDisplaySurface = surface;

        if (mDisplaySurface != null) {
            if (mIncomingMediaPlayer == null) {
                // For a Rick-Rolling good time use R.raw.test_video
                mIncomingMediaPlayer = createMediaPlayer(mDisplaySurface, R.raw.test_pattern);
            }
            mIncomingMediaPlayer.setSurface(mDisplaySurface);
            if (!mIncomingMediaPlayer.isPlaying()) {
                mIncomingMediaPlayer.start();
            }
        } else {
            if (mIncomingMediaPlayer != null) {
                mIncomingMediaPlayer.stop();
                mIncomingMediaPlayer.setSurface(null);
            }
        }
    }

    @Override
    public void onSetDeviceOrientation(int rotation) {
        log("Set device orientation " + rotation);
    }

    /**
     * Sets the zoom value, creating a new CallCameraCapabalities object. If the zoom value is
     * non-positive, assume that zoom is not supported.
     */
    @Override
    public void onSetZoom(float value) {
        log("Set zoom to " + value);
    }

    /**
     * "Sends" a request with a video call profile. Assumes that this response succeeds and sends
     * the response back via the CallVideoClient.
     */
    @Override
    public void onSendSessionModifyRequest(final VideoProfile fromProfile,
            final VideoProfile requestProfile) {
        log("Sent session modify request");

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final VideoProfile responseProfile = new VideoProfile(
                        requestProfile.getVideoState(), requestProfile.getQuality());
                mConnection.setVideoState(requestProfile.getVideoState());

                receiveSessionModifyResponse(
                        SESSION_MODIFY_REQUEST_SUCCESS,
                        requestProfile,
                        responseProfile);
            }
        }, 2000);
    }

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {

    }

    /**
     * Returns a CallCameraCapabilities object without supporting zoom.
     */
    @Override
    public void onRequestCameraCapabilities() {
        log("Requested camera capabilities");
        changeCameraCapabilities(mCameraCapabilities);
    }

    /**
     * Randomly reports data usage of value ranging from 10MB to 60MB.
     */
    @Override
    public void onRequestConnectionDataUsage() {
        log("Requested connection data usage");
        long dataUsageKb = (10 *1024) + random.nextInt(50 * 1024);
        changeCallDataUsage(dataUsageKb);
    }

    /**
     * We do not have a need to set a paused image.
     */
    @Override
    public void onSetPauseImage(Uri uri) {
        // Not implemented.
    }

    /**
     * Stop and cleanup the media players used for test video playback.
     */
    public void stopAndCleanupMedia() {
        if (mIncomingMediaPlayer != null) {
            mIncomingMediaPlayer.setSurface(null);
            mIncomingMediaPlayer.stop();
            mIncomingMediaPlayer.release();
            mIncomingMediaPlayer = null;
        }
    }

    private static void log(String msg) {
        Log.w("TestCallVideoProvider", "[TestCallServiceProvider] " + msg);
    }

    /**
     * Creates a media player to play a video resource on a surface.
     * @param surface The surface.
     * @param videoResource The video resource.
     * @return The {@code MediaPlayer}.
     */
    private MediaPlayer createMediaPlayer(Surface surface, int videoResource) {
        MediaPlayer mediaPlayer = MediaPlayer.create(mContext.getApplicationContext(),
                videoResource);
        mediaPlayer.setSurface(surface);
        mediaPlayer.setLooping(true);
        return mediaPlayer;
    }

    /**
     * Starts displaying the camera image on the preview surface.
     *
     * @param cameraId
     */
    private void startCamera(String cameraId) {
        stopCamera();

        if (mPreviewSurface == null) {
            return;
        }

        // Configure a looper thread.
        mLooperThread = new CameraThread();
        Handler mHandler;
        try {
            mHandler = mLooperThread.start();
        } catch (Exception e) {
            log("Exception: " + e);
            return;
        }

        // Get the camera device.
        try {
            BlockingCameraManager blockingCameraManager = new BlockingCameraManager(mCameraManager);
            mCameraDevice = blockingCameraManager.openCamera(cameraId, null /* listener */,
                    mHandler);
        } catch (CameraAccessException e) {
            log("CameraAccessException: " + e);
            return;
        } catch (BlockingOpenException be) {
            log("BlockingOpenException: " + be);
            return;
        }

        // Create a capture session to get the preview and display it on the surface.
        List<Surface> surfaces = new ArrayList<Surface>();
        surfaces.add(mPreviewSurface);
        CaptureRequest.Builder mCaptureRequest = null;
        try {
            BlockingSessionCallback blkSession = new BlockingSessionCallback();
            mCameraDevice.createCaptureSession(surfaces, blkSession, mHandler);
            mCaptureRequest = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequest.addTarget(mPreviewSurface);
            mCameraSession = blkSession.waitAndGetSession(SESSION_TIMEOUT_MS);
        } catch (CameraAccessException e) {
            log("CameraAccessException: " + e);
            return;
        }

        // Keep repeating
        try {
            mCameraSession.setRepeatingRequest(mCaptureRequest.build(), new CameraCaptureCallback(),
                    mHandler);
        } catch (CameraAccessException e) {
            log("CameraAccessException: " + e);
            return;
        }
    }

    /**
     * Stops the camera and looper thread.
     */
    public void stopCamera() {
        try {
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mLooperThread != null) {
                mLooperThread.close();
                mLooperThread = null;
            }
        } catch (Exception e) {
           log("stopCamera Exception: "+e.toString());
        }
    }

    /**
     * Required listener for camera capture events.
     */
    private class CameraCaptureCallback extends CameraCaptureSession.CaptureCallback {
        @Override
        public void onCaptureCompleted(CameraCaptureSession camera, CaptureRequest request,
                TotalCaptureResult result) {
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession camera, CaptureRequest request,
                CaptureFailure failure) {
        }
    }

    /**
     * Uses the camera manager to retrieve the camera capabilities for the chosen camera.
     *
     * @param cameraId The camera ID to get the capabilities for.
     */
    private void setCameraCapabilities(String cameraId) {
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(
                Context.CAMERA_SERVICE);

        CameraCharacteristics c = null;
        try {
            c = cameraManager.getCameraCharacteristics(cameraId);
        } catch (IllegalArgumentException | CameraAccessException e) {
            // Ignoring camera problems.
        }
        if (c != null) {
            // Get the video size for the camera
            StreamConfigurationMap map = c.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size previewSize = map.getOutputSizes(SurfaceTexture.class)[0];

            mCameraCapabilities = new CameraCapabilities(previewSize.getWidth(),
                    previewSize.getHeight());
        }
    }
}
