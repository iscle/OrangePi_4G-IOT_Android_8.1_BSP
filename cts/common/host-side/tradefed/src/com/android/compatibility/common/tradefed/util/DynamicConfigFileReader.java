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
package com.android.compatibility.common.tradefed.util;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.DynamicConfig;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility to read the data from a dynamic config file.
 */
public class DynamicConfigFileReader {

    /**
     * Returns the value of a key from a downloaded file.
     *
     * @param file The file downloaded, can be retrieve via
     *        {@link CompatibilityBuildHelper#getDynamicConfigFiles()}
     * @param key the key inside the file which value we want to return
     * @return the value associated to the key in the config file provided.
     */
    public static String getValueFromConfig(File file, String key)
            throws XmlPullParserException, IOException {
        Map<String, List<String>> configMap = DynamicConfig.createConfigMap(file);
        List<String> singleValue = configMap.get(key);
        if (singleValue == null || singleValue.size() == 0 || singleValue.size() > 1) {
            // key must exist in the map, and map to a list containing exactly one string
            return null;
        }
        return singleValue.get(0);
    }

    /**
     * Returns the value of a key from the build info and module targeted.
     *
     * @param info the {@link IBuildInfo} of the run.
     * @param moduleName the name of the module we need the dynamic file from.
     * @param key the key inside the file which value we want to return
     * @return the value associated to the key in the dynamic config associated with the module.
     */
    public static String getValueFromConfig(IBuildInfo info, String moduleName, String key)
            throws XmlPullParserException, IOException {
        CompatibilityBuildHelper helper = new CompatibilityBuildHelper(info);
        File dynamicConfig = helper.getDynamicConfigFiles().get(moduleName);
        if (dynamicConfig == null) {
            CLog.d("Config file %s, not found in the map of dynamic configs.", moduleName);
            return null;
        }
        return getValueFromConfig(dynamicConfig, key);
    }
}
