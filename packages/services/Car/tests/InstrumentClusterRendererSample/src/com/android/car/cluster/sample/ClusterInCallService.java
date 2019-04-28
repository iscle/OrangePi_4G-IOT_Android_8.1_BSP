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

package com.android.car.cluster.sample;

import static com.android.car.cluster.sample.DebugUtil.DEBUG;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.Call.Callback;
import android.telecom.InCallService;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * Monitors call state and reports it to the listeners that was registered using
 * {@link #registerListener} method.
 */
public class ClusterInCallService extends InCallService {

    private static final String TAG = DebugUtil.getTag(ClusterInCallService.class);

    static final String ACTION_LOCAL_BINDING = "local_binding";

    private final PhoneCallback mPhoneCallback = new PhoneCallback(this);
    private volatile Callback mListener;

    @Override
    public void onCallAdded(Call call) {
        if (DEBUG) {
            Log.d(TAG, "onCallAdded");
        }
        call.registerCallback(mPhoneCallback);
        mPhoneCallback.onStateChanged(call, call.getState());
    }

    @Override
    public void onCallRemoved(Call call) {
        if (DEBUG) {
            Log.d(TAG, "onCallRemoved");
        }
        call.unregisterCallback(mPhoneCallback);
    }

    private void doStateChanged(Call call, int state) {
        if (DEBUG) {
            Log.d(TAG, "doStateChanged, call: " + call + ", state: " + state);
        }
        if (mListener != null) {
            mListener.onStateChanged(call, state);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind, intent:" + intent);
        return ACTION_LOCAL_BINDING.equals(intent.getAction())
            ? new LocalBinder() : super.onBind(intent);
    }

    public class LocalBinder extends Binder {
        ClusterInCallService getService() {
            return ClusterInCallService.this;
        }
    }

    public void registerListener(Callback listener) {
        Log.d(TAG, "registerListener, listener: " + listener);
        mListener = listener;
    }

    private static class PhoneCallback extends Callback {
        private final WeakReference<ClusterInCallService> mServiceRef;

        private PhoneCallback(ClusterInCallService service) {
            mServiceRef = new WeakReference<>(service);
        }

        @Override
        public void onStateChanged(Call call, int state) {
            Log.d(TAG, "PhoneCallback#onStateChanged, call: " + call + ", state: " + state);
            ClusterInCallService service = mServiceRef.get();
            if (service != null) {
                service.doStateChanged(call, state);
            }
        }
    }
}
