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

import com.android.tradefed.util.FileUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicConfigHandler {

    private final static String FILE_EXT = ".dynamic";
    private static final String NS = null; //xml constant representing null namespace
    private static final String ENCODING = "UTF-8";

    public static File getMergedDynamicConfigFile(File localConfigFile, String apbsConfigJson,
            String moduleName) throws IOException, XmlPullParserException, JSONException {

        Map<String, List<String>> localConfig = DynamicConfig.createConfigMap(localConfigFile);
        Map<String, List<String>> apbsConfig = parseJsonToConfigMap(apbsConfigJson);
        localConfig.putAll(apbsConfig);
        return storeMergedConfigFile(localConfig, moduleName);
    }

    private static Map<String, List<String>> parseJsonToConfigMap(String apbsConfigJson)
            throws JSONException {

        Map<String, List<String>> configMap = new HashMap<String, List<String>>();
        if (apbsConfigJson == null) {
            return configMap;
        }

        JSONObject rootObj  = new JSONObject(new JSONTokener(apbsConfigJson));
        JSONObject configObject = rootObj.getJSONObject("dynamicConfigEntries");
        JSONArray keys = configObject.names();
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i);
            JSONArray jsonValues = configObject.getJSONObject(key).getJSONArray("configValues");
            List<String> values = new ArrayList<>();
            for (int j = 0; j < jsonValues.length(); j ++) {
                values.add(jsonValues.getString(j));
            }
            configMap.put(key, values);
        }
        return configMap;
    }

    private static File storeMergedConfigFile(Map<String, List<String>> configMap,
            String moduleName) throws XmlPullParserException, IOException {

        File mergedConfigFile = FileUtil.createTempFile(moduleName, FILE_EXT);
        OutputStream stream = new FileOutputStream(mergedConfigFile);
        XmlSerializer serializer = XmlPullParserFactory.newInstance().newSerializer();
        serializer.setOutput(stream, ENCODING);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startDocument(ENCODING, false);

        serializer.startTag(NS, DynamicConfig.CONFIG_TAG);
        for (String key : configMap.keySet()) {
            serializer.startTag(NS, DynamicConfig.ENTRY_TAG);
            serializer.attribute(NS, DynamicConfig.KEY_ATTR, key);
            for (String value : configMap.get(key)) {
                serializer.startTag(NS, DynamicConfig.VALUE_TAG);
                serializer.text(value);
                serializer.endTag(NS, DynamicConfig.VALUE_TAG);
            }
            serializer.endTag(NS, DynamicConfig.ENTRY_TAG);
        }
        serializer.endTag(NS, DynamicConfig.CONFIG_TAG);
        serializer.endDocument();
        return mergedConfigFile;
    }
}
