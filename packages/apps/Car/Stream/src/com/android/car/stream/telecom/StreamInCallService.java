/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.telecom;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.util.Log;

/**
 * {@link InCallService} to listen for incoming calls and changes in call state.
 */
public class StreamInCallService extends InCallService {
    public static final String LOCAL_INCALL_SERVICE_BIND_ACTION = "stream_incall_service_action";
    private static final String TAG = "StreamInCallService";
    private final IBinder mBinder = new StreamInCallServiceBinder();

    private InCallServiceCallback mCallback;

    /**
     * Callback interface to receive changes in the call state.
     */
    public interface InCallServiceCallback {
        void onCallAdded(Call call);

        void onCallRemoved(Call call);

        void onCallAudioStateChanged(CallAudioState audioState);
    }

    public class StreamInCallServiceBinder extends Binder {
        StreamInCallService getService() {
            return StreamInCallService.this;
        }
    }

    public void setCallback(InCallServiceCallback callback) {
        mCallback = callback;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // This service can be bound by the framework or a local stream producer.
        // Check the action and return the appropriate IBinder.
        if (LOCAL_INCALL_SERVICE_BIND_ACTION.equals(intent.getAction())) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onBind with action: LOCAL_INCALL_SERVICE_BIND_ACTION," +
                        " returning StreamInCallServiceBinder");
            }
            return mBinder;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onBind without action specified, returning InCallService");
        }
        return super.onBind(intent);
    }

    @Override
    public void onCallAdded(Call call) {
        if (mCallback != null) {
            mCallback.onCallAdded(call);
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (mCallback != null) {
            mCallback.onCallRemoved(call);
        }
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        if (mCallback != null) {
            mCallback.onCallAudioStateChanged(audioState);
        }
        super.onCallAudioStateChanged(audioState);
    }

    public boolean isMuted() {
        CallAudioState audioState = getCallAudioState();
        return audioState != null && audioState.isMuted();
    }
}
