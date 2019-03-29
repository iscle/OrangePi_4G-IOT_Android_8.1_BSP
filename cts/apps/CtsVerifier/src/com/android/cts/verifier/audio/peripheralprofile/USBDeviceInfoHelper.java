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
package com.android.cts.verifier.audio.peripheralprofile;

import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.util.Log;

public class USBDeviceInfoHelper {
    @SuppressWarnings("unused")
    private static final String TAG = "USBDeviceInfoHelper";

    private static final String kUSBPrefix = "USB-Audio - ";

    // TODO - we can't treat the maximum channel count the same for inputs and outputs
    public static int calcMaxChannelCount(AudioDeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return 2;   // for testing....
        }

        int maxChanCount = 0;
        int[] counts = deviceInfo.getChannelCounts();
        for (int chanCount : counts) {
            if (chanCount > maxChanCount) {
                maxChanCount = chanCount;
            }
        }
        return maxChanCount;
    }

    // TODO This should be in a library module devoted to channel management, not USB.
    public static int getPlayChanMask(AudioDeviceInfo deviceInfo) {
        int numChans = calcMaxChannelCount(deviceInfo);
        switch (numChans) {
            case 1:
                return AudioFormat.CHANNEL_OUT_MONO;

            case 2:
                return AudioFormat.CHANNEL_OUT_STEREO;

            case 4:
                return AudioFormat.CHANNEL_OUT_QUAD;

            default:
                // Huh!
                Log.e(TAG, "getPlayChanMask() Unsupported number of channels: " + numChans);
                return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }

    // TODO This should be in a library module devoted to channel management, not USB.
    public static int getIndexedChanMask(int numChannels) {
        return 0x80000000 | numChannels;
    }

    // TODO This should be in a library module devoted to channel management, not USB.
    public static int getRecordChanMask(AudioDeviceInfo deviceInfo) {
        int numChans = calcMaxChannelCount(deviceInfo);
        switch (numChans) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;

            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;

            default:
                // Huh!
                return AudioFormat.CHANNEL_OUT_STEREO;
        }
    }

    public static String extractDeviceName(String productName) {
        return productName.startsWith(kUSBPrefix)
                ? productName.substring(kUSBPrefix.length())
                : productName;
    }
}
