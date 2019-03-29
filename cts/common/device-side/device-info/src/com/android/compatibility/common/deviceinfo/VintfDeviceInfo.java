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
package com.android.compatibility.common.deviceinfo;

import android.os.Build;
import android.os.VintfObject;
import android.os.VintfRuntimeInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * VINTF device info collector.
 */
public final class VintfDeviceInfo extends DeviceInfo {

    private static final String[] sEmptyStringArray = new String[0];

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        // VintfRuntimeInfo is available Android O onward.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
           return;
        }
        store.addResult("cpu_info", VintfRuntimeInfo.getCpuInfo());
        store.addResult("os_name", VintfRuntimeInfo.getOsName());
        store.addResult("node_name", VintfRuntimeInfo.getNodeName());
        store.addResult("os_release", VintfRuntimeInfo.getOsRelease());
        store.addResult("os_version", VintfRuntimeInfo.getOsVersion());
        store.addResult("hardware_id", VintfRuntimeInfo.getHardwareId());
        store.addResult("kernel_version", VintfRuntimeInfo.getKernelVersion());
        store.addResult("kernel_sepolicy_version", VintfRuntimeInfo.getKernelSepolicyVersion());
        store.addResult("sepolicy_version", VintfObject.getSepolicyVersion());

        String[] hals = VintfObject.getHalNamesAndVersions();
        store.addListResult("hals", hals == null
                ? Collections.emptyList() : Arrays.<String>asList(hals));

        Map<String, String[]> vndks = VintfObject.getVndkSnapshots();
        if (vndks == null) vndks = Collections.emptyMap();
        store.startArray("vndk_snapshots");
        for (Map.Entry<String, String[]> e : vndks.entrySet()) {
            store.startGroup();
            store.addResult("version", e.getKey());
            String[] libraries = e.getValue();
            store.addListResult("libraries", libraries == null
                    ? Collections.emptyList() : Arrays.<String>asList(libraries));
            store.endGroup();
        }
        store.endArray();
    }
}
