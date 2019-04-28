/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2016 Mopria Alliance, Inc.
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

package com.android.bips.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.util.Log;

/** Reliably reports on changes to Wi-Fi connectivity state */
public class WifiMonitor implements AutoCloseable {
    private static final String TAG = WifiMonitor.class.getSimpleName();
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final Listener mListener;

    // Current connectivity state or null if not known yet
    private Boolean mConnected;

    public interface Listener {
        void onConnectionStateChanged(boolean isConnected);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                boolean isConnected = isConnected(context);
                if (mConnected == null || mConnected != isConnected) {
                    mConnected = isConnected;
                    mListener.onConnectionStateChanged(mConnected);
                }
            }
        }
    };

    /**
     * Begin listening for connectivity changes, signalling the current WiFi
     * connectivity state and any subsequent state changes to the listener.
     */
    public WifiMonitor(Context context, Listener listener) {
        if (DEBUG) Log.d(TAG, "WifiMonitor()");
        mListener = listener;
        mContext = context;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    /** Return the current connectivity state */
    public static boolean isConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        for (Network network : cm.getAllNetworks()) {
            NetworkInfo networkInfo = cm.getNetworkInfo(network);
            if (networkInfo != null && networkInfo.isConnected() &&
                    networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stop listening for connectivity state
     */
    @Override
    public void close() {
        if (DEBUG) Log.d(TAG, "close()");
        if (mBroadcastReceiver != null) {
            mContext.unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }

    /** A factory for creating instances of this object (needed for unit tests) */
    public static class Factory {
        public WifiMonitor create(Context context, Listener listener) {
            return new WifiMonitor(context, listener);
        }
    }
}