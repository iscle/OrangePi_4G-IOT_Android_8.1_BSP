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

package com.android.server.telecom.tests;

import org.mockito.Mockito;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.Connection.VideoProvider;
import android.telecom.InCallService;
import android.telecom.InCallService.VideoCall;
import android.telecom.Log;
import android.telecom.VideoProfile;
import android.view.Surface;

/**
 * Provides a mock implementation of a video provider.
 */
public class MockVideoProvider extends VideoProvider {
    public static final String CAMERA_NONE = "none";
    public static final String CAMERA_FRONT = "front";
    public static final String CAMERA_BACK = "back";
    public static final int CAMERA_FRONT_DIMENSIONS = 1024;
    public static final int CAMERA_BACK_DIMENSIONS = 2048;
    public static final long DATA_USAGE = 1024;
    public static final int PEER_DIMENSIONS = 4096;
    public static final int DEVICE_ORIENTATION_UNDEFINED = -1;
    public static final float ZOOM_UNDEFINED = -1.0f;

    private Surface mPreviewSurface = null;
    private Surface mDisplaySurface = null;
    private int mDeviceOrientation = DEVICE_ORIENTATION_UNDEFINED;
    private float mZoom = ZOOM_UNDEFINED;
    private VideoProfile mSessionModifyResponse = null;
    private Uri mPauseImage = null;

    /**
     * Responds to a request to set the camera by reporting the new camera information via the
     * {@link #changeCameraCapabilities(VideoProfile.CameraCapabilities)} API.
     *
     * @param cameraId The id of the camera (use ids as reported by
     */
    @Override
    public void onSetCamera(String cameraId) {
        handleCameraChange(cameraId);
    }

    /**
     * Stores the preview surface set via the {@link VideoCall#setPreviewSurface(Surface)} API for
     * retrieval using {@link #getPreviewSurface()}.
     *
     * @param surface The {@link Surface}.
     */
    @Override
    public void onSetPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
    }

    /**
     * Stores the display surface set via the {@link VideoCall#setDisplaySurface(Surface)} API for
     * retrieval using {@link #getDisplaySurface()}.
     *
     * @param surface The {@link Surface}.
     */
    @Override
    public void onSetDisplaySurface(Surface surface) {
        mDisplaySurface = surface;
    }

    /**
     * Stores the device orientation set via the {@link VideoCall#setDeviceOrientation(int)} API for
     * retrieval using {@link #getDeviceOrientation()}.
     *
     * @param rotation The device orientation, in degrees.
     */
    @Override
    public void onSetDeviceOrientation(int rotation) {
        mDeviceOrientation = rotation;
    }

    /**
     * Stores the zoom level set via the {@link VideoCall#setZoom(float)} API for retrieval using
     * {@link #getZoom()}.
     *
     * @param value The camera zoom.
     */
    @Override
    public void onSetZoom(float value) {
        mZoom = value;
    }

    /**
     * Responds to any incoming session modify request by accepting the requested profile.
     *
     * @param fromProfile The video profile prior to the request.
     * @param toProfile The video profile with the requested changes made.
     */
    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        super.receiveSessionModifyResponse(VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS,
                fromProfile, toProfile);
    }

    /**
     * Stores session modify responses received via the
     * {@link VideoCall#sendSessionModifyResponse(VideoProfile)} API for retrieval via
     * {@link #getSessionModifyResponse()}.
     *
     * @param responseProfile The response video profile.
     */
    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
        mSessionModifyResponse = responseProfile;
    }

    /**
     * Responds to requests for camera capabilities by reporting the front camera capabilities.
     */
    @Override
    public void onRequestCameraCapabilities() {
        handleCameraChange(CAMERA_FRONT);
    }

    /**
     * Responds to all requests for data usage by reporting {@link #DATA_USAGE}.
     */
    @Override
    public void onRequestConnectionDataUsage() {
        super.setCallDataUsage(DATA_USAGE);
    }

    /**
     * Stores pause image URIs received via the {@link VideoCall#setPauseImage(Uri)} API for
     * retrieval via {@link #getPauseImage()}.
     *
     * @param uri URI of image to display.
     */
    @Override
    public void onSetPauseImage(Uri uri) {
        mPauseImage = uri;
    }

    /**
     * Handles a change to the current camera selection.  Responds by reporting the capabilities of
     * the camera.
     */
    private void handleCameraChange(String cameraId) {
        if (CAMERA_FRONT.equals(cameraId)) {
            super.changeCameraCapabilities(new VideoProfile.CameraCapabilities(
                    CAMERA_FRONT_DIMENSIONS, CAMERA_FRONT_DIMENSIONS));
        } else if (CAMERA_BACK.equals(cameraId)) {
            super.changeCameraCapabilities(new VideoProfile.CameraCapabilities(
                    CAMERA_BACK_DIMENSIONS, CAMERA_BACK_DIMENSIONS));
        } else {
            // If the camera is nulled, we will send back a "camera ready" event so that the unit
            // test has something to wait for.
            super.handleCallSessionEvent(VideoProvider.SESSION_EVENT_CAMERA_READY);
        }
    }

    /**
     * Retrieves the last preview surface sent to the provider.
     *
     * @return the surface.
     */
    public Surface getPreviewSurface() {
        return mPreviewSurface;
    }

    /**
     * Retrieves the last display surface sent to the provider.
     *
     * @return the surface.
     */
    public Surface getDisplaySurface() {
        return mDisplaySurface;
    }

    /**
     * Retrieves the last device orientation sent to the provider.
     *
     * @return the orientation.
     */
    public int getDeviceOrientation() {
        return mDeviceOrientation;
    }

    /**
     * Retrieves the last zoom sent to the provider.
     *
     * @return the zoom.
     */
    public float getZoom() {
        return mZoom;
    }

    /**
     * Retrieves the last session modify response sent to the provider.
     *
     * @return the session modify response.
     */
    public VideoProfile getSessionModifyResponse() {
        return mSessionModifyResponse;
    }

    /**
     * Retrieves the last pause image sent to the provider.
     *
     * @return the pause image URI.
     */
    public Uri getPauseImage() {
        return mPauseImage;
    }

    /**
     * Sends a mock session modify request via the provider.
     */
    public void sendMockSessionModifyRequest() {
        super.receiveSessionModifyRequest(new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));
    }

    /**
     * Sends a mock session event via the provider.
     *
     * @param event the event.
     */
    public void sendMockSessionEvent(int event) {
        super.handleCallSessionEvent(event);
    }

    /**
     * Sends a mock peer dimension change via the provider.
     *
     * @param width The new width.
     * @param height The new height.
     */
    public void sendMockPeerDimensions(int width, int height) {
        super.changePeerDimensions(width, height);
    }

    /**
     * Sends a mock video quality change via the provider.
     *
     * @param videoQuality the video quality.
     */
    public void sendMockVideoQuality(int videoQuality) {
        super.changeVideoQuality(videoQuality);
    }
}
