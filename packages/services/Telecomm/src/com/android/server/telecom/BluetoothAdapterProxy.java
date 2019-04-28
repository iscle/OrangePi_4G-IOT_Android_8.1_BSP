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
 * limitations under the License
 */

package com.android.server.telecom;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

/**
 * Proxy class used so that BluetoothAdapter can be mocked for testing.
 */
public class BluetoothAdapterProxy {
    private BluetoothAdapter mBluetoothAdapter;

    public BluetoothAdapterProxy() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener,
            int profile) {
        if (mBluetoothAdapter == null) {
            return false;
        }
        return mBluetoothAdapter.getProfileProxy(context, listener, profile);
    }
}
