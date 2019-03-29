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

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.os.Bundle;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * MemoryDeviceInfo collector.
 */
public final class MemoryDeviceInfo extends DeviceInfo {

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        ActivityManager activityManager = (ActivityManager)getInstrumentation()
                .getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
        store.addResult("low_ram_device", activityManager.isLowRamDevice());
        store.addResult("memory_class", activityManager.getMemoryClass());
        store.addResult("large_memory_class", activityManager.getLargeMemoryClass());

        MemoryInfo memoryInfo = new MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        store.addResult("total_memory", memoryInfo.totalMem);
    }
}
