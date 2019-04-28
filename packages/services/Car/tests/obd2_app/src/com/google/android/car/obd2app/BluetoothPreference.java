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
 * limitations under the License.
 */

package com.google.android.car.obd2app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.List;

public class BluetoothPreference extends ListPreference {
    private static final class DeviceEntry {
        private final String mName;
        private final String mAddress;

        DeviceEntry(BluetoothDevice device) {
            mAddress = device.getAddress();
            if (device.getName() == null) {
                mName = mAddress;
            } else {
                mName = device.getName();
            }
        }

        String getName() {
            return mName;
        }

        String getAddress() {
            return mAddress;
        }
    }

    public BluetoothPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        List<DeviceEntry> pairedDevices = new ArrayList<>();
        defaultAdapter
                .getBondedDevices()
                .forEach((BluetoothDevice device) -> pairedDevices.add(new DeviceEntry(device)));
        setEntries(pairedDevices.stream().map(DeviceEntry::getName).toArray(String[]::new));
        setEntryValues(pairedDevices.stream().map(DeviceEntry::getAddress).toArray(String[]::new));
    }

    public BluetoothPreference(Context context) {
        this(context, null);
    }
}
