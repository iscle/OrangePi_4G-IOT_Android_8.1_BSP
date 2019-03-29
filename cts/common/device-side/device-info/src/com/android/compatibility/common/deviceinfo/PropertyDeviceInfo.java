/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.deviceinfo;

import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * System property info collector.
 */
public final class PropertyDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "PropertyDeviceInfo";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        try {
            collectRoProperties(store);
        } catch (IOException e) {
            Log.w(LOG_TAG, "Failed to collect properties", e);
        }
    }

    private void collectRoProperties(DeviceInfoStore store) throws IOException {
        store.startArray("ro_property");
        Pattern pattern = Pattern.compile("\\[(ro.+)\\]: \\[(.+)\\]");
        Scanner scanner = null;
        try {
            Process getprop = new ProcessBuilder("getprop").start();
            scanner = new Scanner(getprop.getInputStream());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    String name = matcher.group(1);
                    String value = matcher.group(2);

                    store.startGroup();
                    store.addResult("name", name);
                    store.addResult("value", value);
                    store.endGroup();
                }
            }
        } finally {
            store.endArray();
            if (scanner != null) {
                scanner.close();
            }
        }
    }
}
