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

package android.telecom.cts;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.RemoteConnection;
import android.telecom.VideoProfile;
import android.view.Surface;

import android.telecom.Connection.VideoProvider;

/**
 * Implements a mock video provider implementation.
 */
public class MockVideoProvider extends VideoProvider {
    public static final String CAMERA_NONE = "none";
    public static final String CAMERA_FRONT = "front";
    public static final String CAMERA_BACK = "back";
    public static final int CAMERA_FRONT_DIMENSIONS = 1024;
    public static final int CAMERA_BACK_DIMENSIONS = 2048;
    public static final long DATA_USAGE = 1024;
    public static final long DATA_USAGE_UNDEFINED = -1;
    public static final int VIDEO_QUALITY_UNDEFINED = -1;
    public static final int SESSION_EVENT_UNDEFINED = -1;
    public static final int PEER_WIDTH_UNDEFINED = -1;
    public static final int DEVICE_ORIENTATION_UNDEFINED = -1;
    public static final float ZOOM_UNDEFINED = -1.0f;

    private Uri mPauseImageUri;
    private String mCameraId = CAMERA_NONE;
    private MockConnection mMockConnection;
    private int mDeviceOrientation = DEVICE_ORIENTATION_UNDEFINED;
    private float mZoom = ZOOM_UNDEFINED;
    private Surface mPreviewSurface = null;
    private Surface mDisplaySurface = null;
    private VideoProfile mSessionModifyResponse = null;
    private TestUtils.InvokeCounter mVideoProviderHandlerTracker;

    public MockVideoProvider(MockConnection mockConnection) {
        mMockConnection = mockConnection;
    }

    @Override
    public void onSetCamera(String cameraId) {
        handleCameraChange(cameraId);
    }

    @Override
    public void onSetPreviewSurface(Surface surface) {
        mPreviewSurface = surface;
    }

    @Override
    public void onSetDisplaySurface(Surface surface) {
        mDisplaySurface = surface;
    }

    @Override
    public void onSetDeviceOrientation(int rotation) {
        mDeviceOrientation = rotation;
    }

    @Override
    public void onSetZoom(float value) {
        if (mVideoProviderHandlerTracker != null) {
            mVideoProviderHandlerTracker.invoke();
            return;
        }
        mZoom = value;
    }

    /**
     * Handles a session modification request from the {@link MockInCallService}. Assumes the peer
     * has accepted the proposed video profile.
     *
     * @param fromProfile The video properties prior to the request.
     * @param toProfile The video properties with the requested changes made.
     */
    @Override
    public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
        super.receiveSessionModifyResponse(Connection.VideoProvider.SESSION_MODIFY_REQUEST_SUCCESS,
                toProfile, toProfile);
        mMockConnection.setVideoState(toProfile.getVideoState());
    }

    @Override
    public void onSendSessionModifyResponse(VideoProfile responseProfile) {
        mSessionModifyResponse = responseProfile;
    }

    /**
     * Responds with the current camera capabilities.
     */
    @Override
    public void onRequestCameraCapabilities() {
        handleCameraChange(mCameraId);
    }

    /**
     * Handles requests to retrieve the connection data usage by returning a fixed usage amount of
     * {@code 1024} bytes.
     */
    @Override
    public void onRequestConnectionDataUsage() {
        super.setCallDataUsage(DATA_USAGE);
    }

    @Override
    public void onSetPauseImage(Uri uri) {
        mPauseImageUri = uri;
    }

    /**
     * Handles a change to the current camera selection.  Responds by reporting the capabilities of
     * the camera.
     */
    private void handleCameraChange(String cameraId) {
        mCameraId = cameraId;
        if (CAMERA_FRONT.equals(mCameraId)) {
            super.changeCameraCapabilities(new VideoProfile.CameraCapabilities(
                    CAMERA_FRONT_DIMENSIONS, CAMERA_FRONT_DIMENSIONS));
        } else if (CAMERA_BACK.equals(mCameraId)) {
            super.changeCameraCapabilities(new VideoProfile.CameraCapabilities(
                    CAMERA_BACK_DIMENSIONS, CAMERA_BACK_DIMENSIONS));
        }
    }

    /**
     * Waits until all messages in the VideoProvider message handler up to the point of this call
     * have been cleared and processed. Use this to wait for the callback to actually register.
     */
    public void waitForVideoProviderHandler(RemoteConnection.VideoProvider remoteVideoProvider) {
        mVideoProviderHandlerTracker =
                new TestUtils.InvokeCounter("WaitForHandler");
        remoteVideoProvider.setZoom(0);
        mVideoProviderHandlerTracker.waitForCount(1);
        mVideoProviderHandlerTracker = null;
    }
    /**
     * Sends a mock video quality value from the provider.
     *
     * @param videoQuality The video quality.
     */
    public void sendMockVideoQuality(int videoQuality) {
        super.changeVideoQuality(videoQuality);
    }

    /**
     * Sends a mock call session event from the provider.
     *
     * @param event The call session event.
     */
    public void sendMockCallSessionEvent(int event) {
        super.handleCallSessionEvent(event);
    }

    /**
     * Sends a mock peer width from the provider.
     *
     * @param width The peer width.
     */
    public void sendMockPeerWidth(int width) {
        super.changePeerDimensions(width, width);
    }

    /**
     * Sends a mock session modify request from the provider.
     *
     * @param request The requested profile.
     */
    public void sendMockSessionModifyRequest(VideoProfile request) {
        super.receiveSessionModifyRequest(request);
    }

    /**
     * Sends a mock session modify response from the provider.
     *
     * @param status The response status.
     * @param requestProfile The request video profile.
     * @param responseProfile The response video profile.
     */
    public void sendMockSessionModifyResponse(int status, VideoProfile requestProfile,
            VideoProfile responseProfile) {
        super.receiveSessionModifyResponse(status, requestProfile, responseProfile);
    }

    public int getDeviceOrientation() {
        return mDeviceOrientation;
    }

    public float getZoom() {
        return mZoom;
    }

    public Surface getPreviewSurface() {
        return mPreviewSurface;
    }

    public Surface getDisplaySurface() {
        return mDisplaySurface;
    }

    public VideoProfile getSessionModifyResponse() {
        return mSessionModifyResponse;
    }

    public Uri getPauseImageUri() {
        return mPauseImageUri;
    }
}
