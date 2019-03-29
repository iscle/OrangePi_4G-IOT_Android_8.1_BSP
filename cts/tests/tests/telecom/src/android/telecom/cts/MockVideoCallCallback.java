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

import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.VideoProfile;
import android.telecom.VideoProfile.CameraCapabilities;

/**
 * Mock video call Callback class.
 */
public class MockVideoCallCallback extends InCallService.VideoCall.Callback {
    private Call mCall;
    private CameraCapabilities mCameraCapabilities;
    private long mDataUsage = MockVideoProvider.DATA_USAGE_UNDEFINED;
    private int mVideoQuality = MockVideoProvider.VIDEO_QUALITY_UNDEFINED;
    private int mCallSessionEvent = MockVideoProvider.SESSION_EVENT_UNDEFINED;
    private int mPeerWidth = MockVideoProvider.PEER_WIDTH_UNDEFINED;
    private int mResponseStatus;
    private VideoProfile mRequestedProfile = null;
    private VideoProfile mResponseProfile = null;
    private VideoProfile mRequestProfile = null;

    public MockVideoCallCallback(Call call) {
        mCall = call;
    }

    /**
     * Store incoming session modify request so tests can inspect it.
     *
     * @param videoProfile The requested video profile.
     */
    @Override
    public void onSessionModifyRequestReceived(VideoProfile videoProfile) {
        mRequestProfile = videoProfile;
    }

    /**
     * Store incoming session modify response so tests can inspect it.
     *
     * @param status Status of the session modify request.
     * @param requestedProfile The original request which was sent to the peer device.
     * @param responseProfile The actual profile changes made by the peer device.
     */
    @Override
    public void onSessionModifyResponseReceived(int status, VideoProfile requestedProfile,
            VideoProfile responseProfile) {
        mResponseStatus = status;
        mRequestedProfile = requestedProfile;
        mResponseProfile = responseProfile;
    }

    /**
     * Store incoming session event so tests can inspect it.
     *
     * @param event The event.
     */
    @Override
    public void onCallSessionEvent(int event) {
        mCallSessionEvent = event;
    }

    /**
     * Store incoming peer dimensions so tests can inspect them.
     *
     * @param width  The updated peer video width.
     * @param height The updated peer video height.
     */
    @Override
    public void onPeerDimensionsChanged(int width, int height) {
        mPeerWidth = width;
    }

    /**
     * Store incoming video quality so tests can inspect them.
     *
     * @param videoQuality  The updated peer video quality.  Valid values:
     *      {@link VideoProfile#QUALITY_HIGH},
     *      {@link VideoProfile#QUALITY_MEDIUM},
     *      {@link VideoProfile#QUALITY_LOW},
     */
    @Override
    public void onVideoQualityChanged(int videoQuality) {
        mVideoQuality = videoQuality;
    }

    /**
     * Store incoming call data usage so tests can inspect it.
     *
     * @param dataUsage The updated data usage (in bytes).
     */
    @Override
    public void onCallDataUsageChanged(long dataUsage) {
        mDataUsage = dataUsage;
    }

    /**
     * Store incoming camera capabilities so tests can inspect them.
     *
     * @param cameraCapabilities The changed camera capabilities.
     */
    @Override
    public void onCameraCapabilitiesChanged(CameraCapabilities cameraCapabilities) {
        mCameraCapabilities = cameraCapabilities;
    }

    /**
     * Returns the last received {@link CameraCapabilities}.
     *
     * @return The {@link CameraCapabilities}.
     */
    public CameraCapabilities getCameraCapabilities() {
        return mCameraCapabilities;
    }

    /**
     * Returns the last received data usage.
     *
     * @return The data usage.
     */
    public long getDataUsage() {
        return mDataUsage;
    }

    /**
     * Returns the last received video quality.
     *
     * @return The video quality.
     */
    public int getVideoQuality()
    {
        return mVideoQuality;
    }

    /**
     * Returns the last received call session event.
     *
     * @return The call session event.
     */
    public int getCallSessionEvent()
    {
        return mCallSessionEvent;
    }

    /**
     * Returns the last received peer width.
     *
     * @return The call session event.
     */
    public int getPeerWidth()
    {
        return mPeerWidth;
    }

    /**
     * Returns the last {@code requestedProfile} received via onSessionModifyResponseReceived.
     *
     * @return The video profile.
     */
    public VideoProfile getRequestedProfile() {
        return mRequestedProfile;
    }

    /**
     * Returns the last {@code responseProfile} received via onSessionModifyResponseReceived.
     *
     * @return The video profile.
     */
    public VideoProfile getResponseProfile() {
        return mResponseProfile;
    }

    /**
     * Returns the last {@code status} received via onSessionModifyResponseReceived..
     *
     * @return The response status.
     */
    public int getResponseStatus() {
        return mResponseStatus;
    }

    /**
     * Returns the last requested video profile.
     *
     * @return The video profile.
     */
    public VideoProfile getRequestProfile() {
        return mRequestProfile;
    }
}
