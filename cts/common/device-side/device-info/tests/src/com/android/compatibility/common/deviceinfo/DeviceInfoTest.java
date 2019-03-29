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

import android.test.AndroidTestCase;
import android.test.ActivityInstrumentationTestCase2;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Test for {@link DeviceInfo}.
 */
public class DeviceInfoTest extends AndroidTestCase {

    private static final String EXPECTED_FILE_PATH =
            "/storage/emulated/0/device-info-files/TestDeviceInfo.deviceinfo.json";

    private TestDeviceInfo testDeviceInfo = new TestDeviceInfo();

    public void testJsonFile() throws Exception {
        testDeviceInfo.setUp();
        testDeviceInfo.testCollectDeviceInfo();
        String resultFilePath = testDeviceInfo.getResultFilePath();
        // Check file path exist
        assertNotNull("Expected a non-null resultFilePath", resultFilePath);
        // Check file path location
        assertEquals("Incorrect file path", EXPECTED_FILE_PATH, resultFilePath);
        // Check json file content
        String jsonContent = readFile(resultFilePath);
        assertEquals("Incorrect json output", ExampleObjects.testDeviceInfoJson(), jsonContent);
    }

    private String readFile(String filePath) throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(
                new FileInputStream(filePath), "UTF-8");
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        while((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
            stringBuilder.append('\n');
        }
        bufferedReader.close();
        return stringBuilder.toString();
    }
}