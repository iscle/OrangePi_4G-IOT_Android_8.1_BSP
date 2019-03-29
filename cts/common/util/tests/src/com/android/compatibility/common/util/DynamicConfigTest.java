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

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Unit tests for {@link DynamicConfig}
 */
public class DynamicConfigTest extends TestCase {
    private static final String CORRECT_CONFIG =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<dynamicConfig>\n" +
            "    <entry key=\"test-config-1\">\n" +
            "        <value>test config 1</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"test-config-2\">\n" +
            "        <value>testconfig2</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"config-list\">\n" +
            "        <value>config0</value>\n" +
            "        <value>config1</value>\n" +
            "        <value>config2</value>\n" +
            "        <value>config3</value>\n" +
            "        <value>config4</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"config-list-2\">\n" +
            "        <value>A</value>\n" +
            "        <value>B</value>\n" +
            "        <value>C</value>\n" +
            "        <value>D</value>\n" +
            "        <value>E</value>\n" +
            "    </entry>\n" +
            "</dynamicConfig>\n";

    private static final String CONFIG_WRONG_NODE_NAME =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<dynamicCsonfig>\n" +  //The node name dynamicConfig is intentionally mistyped
            "    <entry key=\"test-config-1\">\n" +
            "        <value>test config 1</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"test-config-2\">\n" +
            "        <value>testconfig2</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"config-list\">\n" +
            "        <value>Nevermore</value>\n" +
            "        <value>Puck</value>\n" +
            "        <value>Zeus</value>\n" +
            "        <value>Earth Shaker</value>\n" +
            "        <value>Vengeful Spirit</value>\n" +
            "    </entry>\n" +
            "    <entry key=\"config-list-2\">\n" +
            "        <value>A</value>\n" +
            "        <value>B</value>\n" +
            "        <value>C</value>\n" +
            "        <value>D</value>\n" +
            "        <value>E</value>\n" +
            "    </entry>\n" +
            "</dynamicConfig>\n";

    public void testCorrectConfig() throws Exception {
        DynamicConfig config = new DynamicConfig();
        File file = createFileFromStr(CORRECT_CONFIG);
        try {
            config.initializeConfig(file);
            assertEquals("Wrong Config", config.getValue("test-config-1"), "test config 1");
            assertEquals("Wrong Config", config.getValue("test-config-2"), "testconfig2");
            assertEquals("Wrong Config List", config.getValues("config-list").get(0), "config0");
            assertEquals("Wrong Config List", config.getValues("config-list").get(2), "config2");
            assertEquals("Wrong Config List", config.getValues("config-list-2").get(2), "C");
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    public void testConfigWithWrongNodeName() throws Exception {
        DynamicConfig config = new DynamicConfig();
        File file = createFileFromStr(CONFIG_WRONG_NODE_NAME);
        try {
            config.initializeConfig(file);
            fail("Cannot detect error when config file has wrong node name");
        } catch (XmlPullParserException e) {
            //expected
        } finally {
            FileUtil.deleteFile(file);
        }
    }

    private File createFileFromStr(String configStr) throws IOException {
        File file = File.createTempFile("test", "dynamic");
        FileOutputStream stream = new FileOutputStream(file);
        stream.write(configStr.getBytes());
        stream.flush();
        stream.close();
        return file;
    }
}
