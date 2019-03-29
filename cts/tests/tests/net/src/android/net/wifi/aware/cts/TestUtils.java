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

package android.net.wifi.aware.cts;

import android.content.Context;
import android.content.pm.PackageManager;

import java.util.Arrays;

/**
 * Test utilities for Wi-Fi Aware CTS test suite.
 */
class TestUtils {
    static final String TAG = "WifiAwareCtsTests";

    /**
     * Returns a flag indicating whether or not Wi-Fi Aware should be tested. Wi-Fi Aware
     * should be tested if the feature is supported on the current device.
     */
    static boolean shouldTestWifiAware(Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    /**
     * Wraps a byte[] (MAC address representation). Intended to provide hash and equality operators
     * so that the MAC address can be used in containers.
     */
    static class MacWrapper {
        private byte[] mMac;

        MacWrapper(byte[] mac) {
            mMac = mac;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof MacWrapper)) {
                return false;
            }

            MacWrapper lhs = (MacWrapper) o;
            return Arrays.equals(mMac, lhs.mMac);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mMac);
        }
    }
}
