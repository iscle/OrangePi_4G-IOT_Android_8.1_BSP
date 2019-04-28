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
package com.android.car.dialer.telecom;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An implementation of {@link InCallService}. This service is bounded by android telecom and
 * {@link UiCallManager}. For incoming calls it will launch Dialer app.
 */
public class InCallServiceImpl extends InCallService {
    private static final String TAG = "Em.InCallService";

    static final String ACTION_LOCAL_BIND = "local_bind";

    private CopyOnWriteArrayList<Callback> mCallbacks = new CopyOnWriteArrayList<>();

    private TelecomManager mTelecomManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mTelecomManager = getApplicationContext().getSystemService(TelecomManager.class);
    }

    @Override
    public void onCallAdded(Call telecomCall) {
        super.onCallAdded(telecomCall);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCallAdded: " + telecomCall + ", state: " + telecomCall);
        }

        telecomCall.registerCallback(mCallListener);
        mCallListener.onStateChanged(telecomCall, telecomCall.getState());

        for (Callback callback : mCallbacks) {
            callback.onTelecomCallAdded(telecomCall);
        }
    }

    @Override
    public void onCallRemoved(Call telecomCall) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onCallRemoved: " + telecomCall);
        }
        for (Callback callback : mCallbacks) {
            callback.onTelecomCallRemoved(telecomCall);
        }
        telecomCall.unregisterCallback(mCallListener);
        super.onCallRemoved(telecomCall);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onBind: " + intent);
        }

        return ACTION_LOCAL_BIND.equals(intent.getAction())
                ? new LocalBinder()
                : super.onBind(intent);
    }

    private final Call.Callback mCallListener = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onStateChanged call: " + call + ", state: " + state );
            }

            if (state == Call.STATE_RINGING || state == Call.STATE_DIALING) {
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "Incoming/outgoing call: " + call);
                }

                // TODO(b/25190782): here we should show heads-up notification for incoming call,
                // however system notifications are disabled by System UI and we haven't implemented
                // a way to show heads-up notifications in embedded mode.
                Intent launchIntent = getPackageManager()
                        .getLaunchIntentForPackage(mTelecomManager.getDefaultDialerPackage());
                startActivity(launchIntent);
            }
        }
    };

    @Override
    public boolean onUnbind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onUnbind, intent: " + intent);
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        for (Callback callback : mCallbacks) {
            callback.onCallAudioStateChanged(audioState);
        }
    }

    public void registerCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    public void unregisterCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    interface Callback {
        void onTelecomCallAdded(Call telecomCall);
        void onTelecomCallRemoved(Call telecomCall);
        void onCallAudioStateChanged(CallAudioState audioState);
    }

    class LocalBinder extends Binder {
        InCallServiceImpl getService() {
            return InCallServiceImpl.this;
        }
    }
}
