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

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.compatibility.common.deviceinfo.DeviceInfo;
import com.android.compatibility.common.util.DeviceInfoStore;

/**
 * PackageDeviceInfo collector.
 */
public class PackageDeviceInfo extends DeviceInfo {

    private static final String PACKAGE = "package";
    private static final String NAME = "name";
    private static final String VERSION_NAME = "version_name";
    private static final String SYSTEM_PRIV = "system_priv";
    private static final String PRIV_APP_DIR = "/system/priv-app";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        PackageManager pm = getContext().getPackageManager();
        store.startArray(PACKAGE);
        for (PackageInfo pkg : pm.getInstalledPackages(0)) {
            store.startGroup();
            store.addResult(NAME, pkg.packageName);
            store.addResult(VERSION_NAME, pkg.versionName);

            if (pkg.applicationInfo != null) {
                String dir = pkg.applicationInfo.sourceDir;
                store.addResult(SYSTEM_PRIV, dir != null && dir.startsWith(PRIV_APP_DIR));
            }
            store.endGroup();
        }
        store.endArray(); // Package
    }
}
