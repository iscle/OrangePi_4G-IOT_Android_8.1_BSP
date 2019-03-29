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

package com.android.compatibility.common.tradefed.util;

import com.android.compatibility.common.tradefed.targetprep.DeviceInfoCollector;
import com.android.compatibility.common.tradefed.targetprep.ReportLogCollector;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for {@link ReportLogCollector} and {@link DeviceInfoCollector}.
 */
public class CollectorUtil {

    private CollectorUtil() {
    }

    private static final String ADB_LS_PATTERN = "([^\\s]+)\\s*";
    private static final String TEST_METRICS_PATTERN = "\\\"([a-z0-9_]*)\\\":(\\{[^{}]*\\})";

    /**
     * Copy files from device to host.
     * @param device The device reference.
     * @param src The source directory on the device.
     * @param dest The destination directory.
     */
    public static void pullFromDevice(ITestDevice device, String src, String dest) {
        try {
            if (device.doesFileExist(src)) {
                String listCommand = String.format("ls %s", src);
                String fileList = device.executeShellCommand(listCommand);
                Pattern p = Pattern.compile(ADB_LS_PATTERN);
                Matcher m = p.matcher(fileList);
                while (m.find()) {
                    String fileName = m.group(1);
                    String srcPath = String.format("%s%s", src, fileName);
                    File destFile = new File(String.format("%s/%s", dest, fileName));
                    device.pullFile(srcPath, destFile);
                }
            }
        } catch (DeviceNotAvailableException e) {
            CLog.e("Caught exception during pull.");
            CLog.e(e);
        }
    }

    /**
     * Copy files from host and delete from source.
     * @param src The source directory.
     * @param dest The destination directory.
     */
    public static void pullFromHost(File src, File dest) {
        try {
            if (src.listFiles() != null) {
                FileUtil.recursiveCopy(src, dest);
            }
            FileUtil.recursiveDelete(src);
        } catch (IOException e) {
            CLog.e("Caught exception during pull.");
            CLog.e(e);
        }
    }

    /**
     * Reformat test metrics jsons to convert multiple json objects with identical stream names into
     * arrays of objects (b/28790467).
     *
     * @param resultDir The directory containing test metrics.
     */
    public static void reformatRepeatedStreams(File resultDir) {
        try {
            File[] reportLogs = resultDir.listFiles();
            for (File reportLog : reportLogs) {
                writeFile(reportLog, reformatJsonString(readFile(reportLog)));
            }
        } catch (IOException e) {
            CLog.e("Caught exception during reformatting.");
            CLog.e(e);
        }
    }

    /**
     * Helper function to read a file.
     *
     * @throws IOException
     */
    private static String readFile(File file) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        }
        return stringBuilder.toString();
    }

    /**
     * Helper function to write to a file.
     *
     * @param file {@link File} to write to.
     * @param jsonString String to be written.
     * @throws IOException
     */
    private static void writeFile(File file, String jsonString) throws IOException {
        file.createNewFile();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(jsonString, 0, jsonString.length());
        }
    }

    /**
     * Helper function to reformat JSON string.
     *
     * @param jsonString
     * @return the reformatted JSON string.
     */
    public static String reformatJsonString(String jsonString) {
        StringBuilder newJsonBuilder = new StringBuilder();
        // Create map of stream names and json objects.
        HashMap<String, List<String>> jsonMap = new HashMap<>();
        Pattern p = Pattern.compile(TEST_METRICS_PATTERN);
        Matcher m = p.matcher(jsonString);
        if (!m.find()) {
            return jsonString;
        }
        do {
            String key = m.group(1);
            String value = m.group(2);
            if (!jsonMap.containsKey(key)) {
                jsonMap.put(key, new ArrayList<String>());
            }
            jsonMap.get(key).add(value);
        } while (m.find());
        // Rewrite json string as arrays.
        newJsonBuilder.append("{");
        boolean firstLine = true;
        for (String key : jsonMap.keySet()) {
            if (!firstLine) {
                newJsonBuilder.append(",");
            } else {
                firstLine = false;
            }
            newJsonBuilder.append("\"").append(key).append("\":[");
            boolean firstValue = true;
            for (String stream : jsonMap.get(key)) {
                if (!firstValue) {
                    newJsonBuilder.append(",");
                } else {
                    firstValue = false;
                }
                newJsonBuilder.append(stream);
            }
            newJsonBuilder.append("]");
        }
        newJsonBuilder.append("}");
        return newJsonBuilder.toString();
    }
}
