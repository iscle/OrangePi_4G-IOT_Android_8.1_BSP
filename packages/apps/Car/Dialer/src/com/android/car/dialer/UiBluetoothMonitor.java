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
package com.android.car.dialer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class that responsible for getting status of bluetooth connections.
 */
public class UiBluetoothMonitor {
    private static String TAG = "Em.BtMonitor";

    private List<Listener> mListeners = new CopyOnWriteArrayList<>();
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothBroadcastReceiver mBluetoothBroadcastReceiver;

    UiBluetoothMonitor(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();
    }

    public void tearDown() {
        mBluetoothBroadcastReceiver.tearDown();
    }

    public boolean isBluetoothEnabled() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    public boolean isHfpConnected() {
        if (mBluetoothAdapter == null) {
            return false;
        }
        return mBluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET_CLIENT)
                == BluetoothProfile.STATE_CONNECTED;
    }
    public boolean isBluetoothPaired() {
        return mBluetoothAdapter != null && mBluetoothAdapter.getBondedDevices().size() > 0;
    }

    public void addListener(Listener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "addListener: " + listener);
        }
        mListeners.add(listener);
    }

    protected void notifyListeners() {
        for (Listener listener : mListeners) {
            listener.onStateChanged();
        }
    }

    public void removeListener(Listener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "removeListener: " + listener);
        }
        mListeners.remove(listener);
    }

    public interface Listener {
        /**
         * Calls when state of Bluetooth was changed, for example when Bluetooth was turned off or
         * on, connection state was changed.
         */
        void onStateChanged();
    }

    private final class BluetoothBroadcastReceiver extends BroadcastReceiver {
        BluetoothBroadcastReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
            filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            filter.addAction(BluetoothHeadsetClient.ACTION_CONNECTION_STATE_CHANGED);
            mContext.registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Bluetooth broadcast intent action received: " + intent.getAction());
            }
            notifyListeners();
        }

        void tearDown() {
            mContext.unregisterReceiver(this);
        }
    }
}
