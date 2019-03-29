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

package android.media.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import android.util.Log;

/* package */ class DeviceUtils {
    private static final String TAG = "DeviceUtils";

    /* package */ static boolean hasOutputDevice(AudioManager audioMgr) {
        AudioDeviceInfo[] devices = audioMgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        return devices.length != 0;
    }

    /* package */ static boolean hasInputDevice(AudioManager audioMgr) {
        AudioDeviceInfo[] devices = audioMgr.getDevices(AudioManager.GET_DEVICES_INPUTS);
        return devices.length != 0;
    }

    /* package */ static boolean isTVDevice(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    /*
     * HDMI
     */
    /* package */ static boolean isHDMIConnected(Context context) {
        // configure the IntentFilter
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        Intent intent = context.registerReceiver(null, intentFilter);

        return intent != null && intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, 0) != 0;
    }
}
