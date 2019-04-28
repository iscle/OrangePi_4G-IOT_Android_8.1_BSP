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

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper functions and constants for reading the information passed via the Bluetooth
 * name of the hub.
 */
public class BluetoothNameUtils {
    /*
     * matches string that
     *   - may or may not start with a one- or two-digit number followed by a space
     *   - a string surrounded by quotes
     *   - a string surrounded by parentheses
     */
    private static final Pattern NAME_PATTERN = Pattern.compile(
            "\"([0-9]{0,3}) ?(.*)\" \\((.*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_PATTERN = Pattern.compile(
            "#([0-9a-f]{6})-#([0-9a-f]{6})(p?)(t?)(.*)", Pattern.CASE_INSENSITIVE);

    /**
     * Decode the setup type integer from the Bluetooth device name.
     * @param bluetoothName
     * @return The integer value of the setup code, or -1 if no code is present.
     */
    public static int getSetupType(String bluetoothName) {
        Matcher matcher = NAME_PATTERN.matcher(bluetoothName);
        if (!matcher.matches()) {
            return -1;
        }

        String typeStr = matcher.group(1);

        if (typeStr != null) {
            try {
                return Integer.parseInt(typeStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        } else {
            return -1;
        }
    }

    /**
     * Decode the LED configuration contained in the input string.
     * @param bluetoothName
     * @return The LedConfiguration or none if one can not be parsed from the string.
     */
    public static LedConfiguration getColorConfiguration(String bluetoothName) {
        Matcher matcher = NAME_PATTERN.matcher(bluetoothName);
        if (!matcher.matches()) {
            return null;
        }

        final String cs = matcher.group(3);
        if (TextUtils.isEmpty(cs)) {
            return null;
        } else {
            final Matcher cm = COLOR_PATTERN.matcher(cs);
            if (!cm.matches()) {
                return null;
            }
            LedConfiguration config = new LedConfiguration(
                    0xff000000 | Integer.parseInt(cm.group(1), 16),
                    0xff000000 | Integer.parseInt(cm.group(2), 16),
                    "p".equals(cm.group(3)));
            config.isTransient = "t".equals(cm.group(4));
            return config;
        }
    }

    /**
     * Check if the name matches the expected format for a hub Bluetooth name.
     * @param name
     * @return true if the pattern matches, false if it doesn't.
     */
    public static boolean isValidName(String name) {
        Matcher matcher = NAME_PATTERN.matcher(name);
        return matcher.matches();
    }

    private BluetoothNameUtils() {
        // DO NOT INSTANTIATE
    }
}
