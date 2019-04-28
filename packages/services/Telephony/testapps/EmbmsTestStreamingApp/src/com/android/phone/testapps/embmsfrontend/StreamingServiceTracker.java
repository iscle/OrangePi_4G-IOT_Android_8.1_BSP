/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.phone.testapps.embmsfrontend;

import android.net.Uri;
import android.telephony.MbmsStreamingSession;
import android.telephony.mbms.StreamingService;
import android.telephony.mbms.StreamingServiceCallback;
import android.telephony.mbms.StreamingServiceInfo;
import android.widget.Toast;

public class StreamingServiceTracker {
    private class Callback extends StreamingServiceCallback {
        @Override
        public void onError(int errorCode, String message) {
            String toastMessage = "Error: " + errorCode + ": " + message;
            mActivity.runOnUiThread(() ->
                    Toast.makeText(mActivity, toastMessage, Toast.LENGTH_SHORT).show());
        }

        @Override
        public void onStreamStateUpdated(int state, int reason) {
            StreamingServiceTracker.this.onStreamStateUpdated(state, reason);
        }

        @Override
        public void onStreamMethodUpdated(int method) {
            StreamingServiceTracker.this.onStreamMethodUpdated(method);
        }
    }

    private final EmbmsTestStreamingApp mActivity;
    private final StreamingServiceInfo mStreamingServiceInfo;
    private StreamingService mStreamingService;

    private int mState = StreamingService.STATE_STOPPED;
    private Uri mStreamingUri = Uri.EMPTY;
    private int mMethod = StreamingService.UNICAST_METHOD;

    public StreamingServiceTracker(EmbmsTestStreamingApp appActivity, StreamingServiceInfo info) {
        mActivity = appActivity;
        mStreamingServiceInfo = info;
    }

    /**
     * Start streaming using the provided streaming session
     */
    public boolean startStreaming(MbmsStreamingSession streamingManager) {
        mStreamingService =
                streamingManager.startStreaming(mStreamingServiceInfo, new Callback(), null);
        return true;
    }

    public void stopStreaming() {
        mStreamingService.stopStreaming();
    }

    public String getServiceId() {
        return mStreamingServiceInfo.getServiceId();
    }

    public int getState() {
        return mState;
    }

    public Uri getUri() {
        return mStreamingUri;
    }

    public int getMethod() {
        return mMethod;
    }

    private void onStreamStateUpdated(int state, int reason) {
        if (state == StreamingService.STATE_STARTED && mState != StreamingService.STATE_STARTED) {
            mStreamingUri = mStreamingService.getPlaybackUri();
            mActivity.updateUri();
        }
        mState = state;
        mActivity.updateStreamingState();
        mActivity.runOnUiThread(() ->
                Toast.makeText(mActivity, "State change reason: " + reason, Toast.LENGTH_SHORT)
                        .show());
    }

    private void onStreamMethodUpdated(int method) {
        if (mMethod != method) {
            mMethod = method;
            mActivity.updateMethod();
        }
    }

    @Override
    public String toString() {
        return "Tracked service with ID " + getServiceId();
    }
}
