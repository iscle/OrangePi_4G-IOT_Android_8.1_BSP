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
package com.android.compatibility.common.util;

import com.android.compatibility.common.util.HostInfoStore;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.util.FileUtil;

import java.io.File;

/**
 * Collect device information from host and write to a JSON file.
 */
public abstract class DeviceInfo extends DeviceTestCase {

    // Temporary folder must match the temp-dir value configured in DeviceInfoCollector target
    // preparer in cts/tools/cts-tradefed/res/config/cts-preconditions.xml
    private static final String TEMPORARY_REPORT_FOLDER = "temp-device-info-files/";

    private HostInfoStore mStore;

    public void testCollectDeviceInfo() throws Exception {
        String collectionName = getClass().getSimpleName();
        try {
            final File dir = FileUtil.createNamedTempDir(TEMPORARY_REPORT_FOLDER);
            File jsonFile = new File(dir, collectionName + ".deviceinfo.json");
            mStore = new HostInfoStore(jsonFile);
            mStore.open();
            collectDeviceInfo(mStore);
            mStore.close();
        } catch (Exception e) {
            e.printStackTrace();
            fail(String.format("Failed to collect device info (%s): %s",
                    collectionName, e.getMessage()));
        }
    }

    /**
     * Method to collect device information.
     */
    protected abstract void collectDeviceInfo(HostInfoStore store) throws Exception;
}