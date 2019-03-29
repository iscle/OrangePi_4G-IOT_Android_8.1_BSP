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

import android.os.Environment;
import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Storage device info collector.
 */
public class StorageDeviceInfo extends DeviceInfo {
    private static final String TAG = "StorageDeviceInfo";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        int total = 0;
        total = Math.max(total, getContext().getExternalCacheDirs().length);
        total = Math.max(total, getContext().getExternalFilesDirs(null).length);
        total = Math.max(
                total, getContext().getExternalFilesDirs(Environment.DIRECTORY_PICTURES).length);
        total = Math.max(total, getContext().getObbDirs().length);

        int emulated = 0;
        int physical = 0;
        if (Environment.isExternalStorageEmulated()) {
            if (total == 1) {
                emulated = 1;
            } else {
                emulated = 1;
                physical = total - 1;
            }
        } else {
            physical = total;
        }

        store.addResult("num_physical", physical);
        store.addResult("num_emulated", emulated);

        store.addListResult("raw_partition", scanPartitions());
    }

    private List<String> scanPartitions() {
        List<String> partitionList = new ArrayList<>();
        try {
            Process df = new ProcessBuilder("df -k").start();
            Scanner scanner = new Scanner(df.getInputStream());
            try {
                while (scanner.hasNextLine()) {
                    partitionList.add(scanner.nextLine());
                }
            } finally {
                scanner.close();
            }
        } catch (Exception e) {
            Log.w(TAG, Log.getStackTraceString(e));
        }
        return partitionList;
    }

}
