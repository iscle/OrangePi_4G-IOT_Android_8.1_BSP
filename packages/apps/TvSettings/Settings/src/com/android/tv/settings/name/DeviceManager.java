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

package com.android.tv.settings.name;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class DeviceManager {

    private static final String TAG = "DeviceManager";

    public static final String ACTION_DEVICE_NAME_UPDATE =
            "com.android.tv.settings.name.DeviceManager.DEVICE_NAME_UPDATE";
    /**
     * Retrieves the name from Settings.Global.DEVICE_NAME
     *
     * @param context A context that can access Settings.Global
     * @return The device name.
     */
    public static String getDeviceName(Context context) {
        return Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
    }

    /**
     * Sets the system device name.
     *
     * For now it will explicitly call the different discoverable services that haven't been ported
     * to use the Settings.Global.DEVICE_NAME entry.
     *
     * @param context A context that can access Settings.Global
     * @param name The new device name.
     */
    public static void setDeviceName(Context context, String name) {
        Settings.Global.putString(context.getContentResolver(), Settings.Global.DEVICE_NAME, name);
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            btAdapter.setName(name);
        } else {
            Log.v(TAG, "Bluetooth adapter is null. Running on device without bluetooth?");
        }
        LocalBroadcastManager.getInstance(context)
                .sendBroadcast(new Intent(ACTION_DEVICE_NAME_UPDATE));
    }
}
