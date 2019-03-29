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

package com.android.compatibility.common.util;

import android.os.Environment;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;

/**
 * Load dynamic config for device side test cases
 */
public class DynamicConfigDeviceSide extends DynamicConfig {

    private static String LOG_TAG = DynamicConfigDeviceSide.class.getSimpleName();

    public DynamicConfigDeviceSide(String moduleName) throws XmlPullParserException, IOException {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("External storage is not mounted");
        }
        File configFile = getConfigFile(new File(CONFIG_FOLDER_ON_DEVICE), moduleName);
        initializeConfig(configFile);
    }
}
