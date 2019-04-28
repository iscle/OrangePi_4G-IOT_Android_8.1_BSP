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
import android.util.Log;

import com.android.tv.settings.util.bluetooth.BluetoothDeviceCriteria;

public class A2dpDeviceCriteria extends BluetoothDeviceCriteria {

    public static final String TAG = "aah.A2dpDeviceCriteria";

    @Override
    public boolean isMatchingMajorDeviceClass(int majorDeviceClass) {
        return majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO;
    }

    @Override
    public boolean isMatchingDeviceClass(int majorMinorClass) {
        Log.d(TAG, "isMatchingDeviceClass : " + majorMinorClass);
        return (majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED ||
                majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                majorMinorClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO
               );
    }
}
