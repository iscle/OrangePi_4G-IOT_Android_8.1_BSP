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

import android.bluetooth.BluetoothClass;

import com.android.tv.settings.util.bluetooth.BluetoothDeviceCriteria;

public class InputDeviceCriteria extends BluetoothDeviceCriteria {

    public static final int MINOR_DEVICE_CLASS_POINTING =
            Integer.parseInt("0000010000000", 2);
    public static final int MINOR_DEVICE_CLASS_JOYSTICK =
            Integer.parseInt("0000000000100", 2);
    public static final int MINOR_DEVICE_CLASS_GAMEPAD =
            Integer.parseInt("0000000001000", 2);
    public static final int MINOR_DEVICE_CLASS_KEYBOARD =
            Integer.parseInt("0000001000000", 2);
    public static final int MINOR_DEVICE_CLASS_REMOTE =
            Integer.parseInt("0000000001100", 2);

    @Override
    public boolean isMatchingMajorDeviceClass(int majorDeviceClass) {
        return majorDeviceClass == BluetoothClass.Device.Major.PERIPHERAL;
    }

    @Override
    public boolean isMatchingDeviceClass(int majorMinorClass) {
        int acceptableDevicesMask = MINOR_DEVICE_CLASS_POINTING | MINOR_DEVICE_CLASS_JOYSTICK |
                MINOR_DEVICE_CLASS_GAMEPAD | MINOR_DEVICE_CLASS_KEYBOARD |
                MINOR_DEVICE_CLASS_REMOTE;

        return (acceptableDevicesMask & majorMinorClass) != 0;
    }

    public boolean isInputDevice(BluetoothClass bluetoothClass) {
        return isMatchingMajorDeviceClass(bluetoothClass.getMajorDeviceClass()) &&
                isMatchingDeviceClass(bluetoothClass.getDeviceClass());
    }
}
