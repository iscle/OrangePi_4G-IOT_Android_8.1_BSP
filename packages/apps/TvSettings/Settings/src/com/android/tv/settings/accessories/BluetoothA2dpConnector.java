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
 * limitations under the License.
 */

package com.android.tv.settings.accessories;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;


public class BluetoothA2dpConnector implements BluetoothDevicePairer.BluetoothConnector {

    public static final String TAG = "BluetoothA2dpConnector";

    private static final boolean DEBUG = false;

    private Context mContext;
    private BluetoothDevice mTarget;
    private BluetoothDevicePairer.OpenConnectionCallback mOpenConnectionCallback;
    private BluetoothA2dp mA2dpProfile;
    private boolean mConnectionStateReceiverRegistered = false;

    private BroadcastReceiver mConnectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE);
            if (DEBUG) {
                Log.d(TAG, "There was a connection status change for: " + device.getAddress());
            }

            if (device.equals(mTarget)) {
                int previousState = intent.getIntExtra(
                        BluetoothA2dp.EXTRA_PREVIOUS_STATE,
                        BluetoothA2dp.STATE_CONNECTING);
                int state = intent.getIntExtra(BluetoothA2dp.EXTRA_STATE,
                        BluetoothA2dp.STATE_CONNECTING);

                if (DEBUG) {
                    Log.d(TAG, "Connection states: old = " + previousState + ", new = " + state);
                }

                if (previousState == BluetoothA2dp.STATE_CONNECTING) {
                    if (state == BluetoothA2dp.STATE_CONNECTED) {
                        mOpenConnectionCallback.succeeded();
                    } else if (state == BluetoothA2dp.STATE_DISCONNECTED) {
                        Log.d(TAG, "Failed to connect");
                        mOpenConnectionCallback.failed();
                    }

                    unregisterConnectionStateReceiver();
                    closeA2dpProfileProxy();
                }
            }
        }
    };

    private BluetoothProfile.ServiceListener mServiceConnection =
            new BluetoothProfile.ServiceListener() {

        @Override
        public void onServiceDisconnected(int profile) {
            Log.w(TAG, "Service disconnected, perhaps unexpectedly");
            unregisterConnectionStateReceiver();
            closeA2dpProfileProxy();
            mOpenConnectionCallback.failed();
        }

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DEBUG) {
                Log.d(TAG, "Connection made to bluetooth proxy." );
            }
            BluetoothA2dp mA2dpProfile = (BluetoothA2dp) proxy;
            if (DEBUG) {
                Log.d(TAG, "Connecting to target: " + mTarget.getAddress());
            }

            registerConnectionStateReceiver();

            // TODO need to start a timer, otherwise if the connection fails we might be
            // stuck here forever
            mA2dpProfile.connect(mTarget);

            // must set PRIORITY_AUTO_CONNECT or auto-connection will not
            // occur, however this setting does not appear to be sticky
            // across a reboot
            mA2dpProfile.setPriority(mTarget, BluetoothProfile.PRIORITY_AUTO_CONNECT);
        }
    };

    private BluetoothA2dpConnector() {
    }

    public BluetoothA2dpConnector(Context context, BluetoothDevice target,
                                  BluetoothDevicePairer.OpenConnectionCallback callback) {
        mContext = context;
        mTarget = target;
        mOpenConnectionCallback = callback;
    }

    @Override
    public void openConnection(BluetoothAdapter adapter) {
        if (DEBUG) {
            Log.d(TAG, "opening connection");
        }
        if (!adapter.getProfileProxy(mContext, mServiceConnection, BluetoothProfile.A2DP)) {
            mOpenConnectionCallback.failed();
        }
    }

    private void closeA2dpProfileProxy() {
        if (mA2dpProfile != null) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                adapter.closeProfileProxy(BluetoothProfile.A2DP, mA2dpProfile);
                mA2dpProfile = null;
            } catch (Throwable t) {
                Log.w(TAG, "Error cleaning up A2DP proxy", t);
            }
        }
    }

    private void registerConnectionStateReceiver() {
        if (DEBUG) Log.d(TAG, "registerConnectionStateReceiver()");
        IntentFilter filter = new IntentFilter(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        mContext.registerReceiver(mConnectionStateReceiver, filter);
        mConnectionStateReceiverRegistered = true;
    }

    private void unregisterConnectionStateReceiver() {
        if (mConnectionStateReceiverRegistered) {
            if (DEBUG) Log.d(TAG, "unregisterConnectionStateReceiver()");
            mContext.unregisterReceiver(mConnectionStateReceiver);
            mConnectionStateReceiverRegistered = false;
        }
    }

}
