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

package com.android.tv.settings.util.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

import java.util.regex.Pattern;

/**
 * Class that decides whether a BluetoothDevice matches the parameters of the
 * type of device that is being looked for.
 *
 * For example, does the device MAC address match the expected pattern and
 * does the device provide the types of services (audio, video, input, etc) that
 * are needed.
 */
public class BluetoothDeviceCriteria {

    // TODO add ability to determine matching device based on name

    public static final String GOOGLE_MAC_PATTERN = "^(00:1A:11|F8:8F:CA).*";

    private final Pattern mAddressPattern;

    public BluetoothDeviceCriteria() {
        this(".*");
    }

    public BluetoothDeviceCriteria(String macAddressPattern) {
        mAddressPattern = Pattern.compile(macAddressPattern, Pattern.CASE_INSENSITIVE);
    }

    public final boolean isMatchingDevice(BluetoothDevice device) {
        if (device == null) {
            return false;
        }

        if (device.getAddress() == null || !isMatchingMacAddress(device.getAddress())) {
            return false;
        }

        if (!isMatchingMajorDeviceClass(device.getBluetoothClass().getMajorDeviceClass())) {
            return false;
        }

        if (!isMatchingDeviceClass(device.getBluetoothClass().getDeviceClass())) {
            return false;
        }

        return true;
    }

    public boolean isMatchingMacAddress(String mac) {
        return mAddressPattern.matcher(mac).matches();
    }

    /**
     * Override this method to restrict the major device classes that match.
     * @param majorDeviceClass constant from {@link BluetoothClass.Device.Major}.
     */
    public boolean isMatchingMajorDeviceClass(int majorDeviceClass) {
        return true;
    }

    /**
     * Override this method to restrict specific device classes that match.
     * @param majorMinorClass constant from {@link BluetoothClass.Device}
     */
    public boolean isMatchingDeviceClass(int majorMinorClass) {
        return true;
    }
}
