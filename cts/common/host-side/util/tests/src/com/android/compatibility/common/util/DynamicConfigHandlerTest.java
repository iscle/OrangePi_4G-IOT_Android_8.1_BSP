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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link DynamicConfigHandler}
 */
public class DynamicConfigHandlerTest extends TestCase {

    private static final String LOCAL_CONFIG =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<dynamicConfig>\n" +
            "    <entry key=\"test-config-1\">\n" +
            "        <value>test config 1</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"test-config-2\">\n" +
            "        <value>test config 2</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"override-config-2\">\n" +
            "        <value>test config 3</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"config-list\">\n" +
            "        <value>config0</value>\n" +
            "        <value>config1</value>\n" +
            "        <value>config2</value>\n" +
            "        <value>config3</value>\n" +
            "        <value>config4</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"override-config-list-2\">\n" +
            "        <value>A</value>\n" +
            "        <value>B</value>\n" +
            "        <value>C</value>\n" +
            "        <value>D</value>\n" +
            "        <value>E</value>\n" +
            "    </entry>\n" +
            "</dynamicConfig>\n";

    private static final String OVERRIDE_JSON =
            "{\n" +
            "  \"dynamicConfigEntries\": {\n" +
            "    \"override-config-1\": {\n" +
            "      \"configValues\": [\n" +
            "        \"override-config-val-1\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"override-config-2\": {\n" +
            "      \"configValues\": [\n" +
            "        \"override-config-val-2\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"override-config-list-1\": {\n" +
            "      \"configValues\": [\n" +
            "        \"override-config-list-val-1-1\",\n" +
            "        \"override-config-list-val-1-2\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"override-config-list-2\": {\n" +
            "      \"configValues\": [\n" +
            "        \"override-config-list-val-2-1\"\n" +
            "      ]\n" +
            "    },\n" +
            "    \"override-config-list-3\": {\n" +
            "      \"configValues\": [\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public void testDynamicConfigHandler() throws Exception {
        String module = "test1";
        File localConfigFile = createFileFromStr(LOCAL_CONFIG, module);
        File mergedFile = null;
        try {
            mergedFile = DynamicConfigHandler
                    .getMergedDynamicConfigFile(localConfigFile, OVERRIDE_JSON, module);

            Map<String, List<String>> configMap = DynamicConfig.createConfigMap(mergedFile);

            assertEquals("override-config-val-1", configMap.get("override-config-1").get(0));
            assertTrue(configMap.get("override-config-list-1")
                    .contains("override-config-list-val-1-1"));
            assertTrue(configMap.get("override-config-list-1")
                    .contains("override-config-list-val-1-2"));
            assertTrue(configMap.get("override-config-list-3").size() == 0);

            assertEquals("test config 1", configMap.get("test-config-1").get(0));
            assertTrue(configMap.get("config-list").contains("config2"));

            assertEquals("override-config-val-2", configMap.get("override-config-2").get(0));
            assertEquals(1, configMap.get("override-config-list-2").size());
            assertTrue(configMap.get("override-config-list-2")
                    .contains("override-config-list-val-2-1"));
        } finally {
            FileUtil.deleteFile(localConfigFile);
            FileUtil.recursiveDelete(mergedFile);
        }
    }

    private File createFileFromStr(String configStr, String module) throws IOException {
        File file = File.createTempFile(module, "dynamic");
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(configStr.getBytes());
            stream.flush();
        } finally {
            StreamUtil.close(stream);
        }
        return file;
    }
}
