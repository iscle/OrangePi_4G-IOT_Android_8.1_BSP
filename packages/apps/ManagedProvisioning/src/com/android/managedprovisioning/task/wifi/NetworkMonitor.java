/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.task.wifi;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;

/**
 * Monitor the state of the data network and the checkin service. Invoke a callback when the network
 * is connected and checkin has succeeded.
 */
public class NetworkMonitor {

    @VisibleForTesting
    static final IntentFilter FILTER;
    static {
        FILTER = new IntentFilter();
        FILTER.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        // Listen to immediate connectivity changes which are 3 seconds
        // earlier than CONNECTIVITY_ACTION and may not have IPv6 routes
        // setup. However, this may allow us to start up services like
        // the CheckinService a bit earlier.
        FILTER.addAction(ConnectivityManager.INET_CONDITION_ACTION);
    }

    /** State notification callback. Expect some duplicate notifications. */
    public interface NetworkConnectedCallback {
        void onNetworkConnected();
    }

    private final Context mContext;
    private final Utils mUtils ;

    private NetworkConnectedCallback mCallback = null;

    /**
     * Start watching the network and monitoring the checkin service. Immediately invokes one of the
     * callback methods to report the current state, and then invokes callback methods over time as
     * the state changes.
     *
     * @param context to use for intent observers and such
     */
    public NetworkMonitor(Context context) {
        this(context, new Utils());
    }

    @VisibleForTesting
    NetworkMonitor(Context context, Utils utils) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
    }

    /**
     * Start listening for connectivity changes.
     * @param callback Callback to inform about those changes.
     */
    public synchronized void startListening(NetworkConnectedCallback callback) {
        mCallback = checkNotNull(callback);
        mContext.registerReceiver(mBroadcastReceiver, FILTER);
    }

    /**
     * Stop listening for connectivity changes.
     */
    public synchronized void stopListening() {
        if (mCallback == null) {
            return;
        }

        mCallback = null;
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ProvisionLogger.logd("onReceive " + intent.toString());
            if (FILTER.matchAction(intent.getAction())) {
                synchronized (NetworkMonitor.this) {
                    if (mUtils.isConnectedToWifi(context) && mCallback != null) {
                        mCallback.onNetworkConnected();
                    }
                }
            }
        }
    };
}
