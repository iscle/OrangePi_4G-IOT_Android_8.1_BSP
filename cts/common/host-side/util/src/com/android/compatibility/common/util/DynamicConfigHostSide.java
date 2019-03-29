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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.build.VersionedFile;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Utility to get value from a dynamic config file.
 * @deprecated use DynamicConfigFileReader instead.
 */
@Deprecated
public class DynamicConfigHostSide {

    public static final String CONFIG_PATH_PREFIX = "DYNAMIC_CONFIG_FILE:";

    /**
     * Returns the value of a key from a downloaded file.
     *
     * @param file The file downloaded, can be retrieve via
     *        {@link #getDynamicConfigFile(IBuildInfo, String)}
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
     * Returns a dynamic config file downloaded in {@link DynamicConfigPusher} the path is stored
     * in the build info under a module name.
     *
     * @param info the invocation {@link IBuildInfo}
     * @param moduleName the name of the module of the file.
     * @return a {@link File} created from the downloaded file.
     * @deprecated use CompatibilityBuildHelper#getDynamicConfigFiles
     */
    @Deprecated
    public static File getDynamicConfigFile(IBuildInfo info, String moduleName) {
        for (VersionedFile vFile : info.getFiles()) {
            if (vFile.getVersion().equals(CONFIG_PATH_PREFIX + moduleName)) {
                return vFile.getFile();
            }
        }
        return null;
    }
}
