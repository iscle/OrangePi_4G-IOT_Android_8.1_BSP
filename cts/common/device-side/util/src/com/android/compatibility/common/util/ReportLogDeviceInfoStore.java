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

import android.util.JsonWriter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ReportLogDeviceInfoStore extends DeviceInfoStore {

    private final String mStreamName;
    private File tempJsonFile;

    public ReportLogDeviceInfoStore(File jsonFile, String streamName) throws Exception {
        mJsonFile = jsonFile;
        mStreamName = streamName;
    }

    /**
     * Creates the writer and starts the JSON Object for the metric stream.
     */
    @Override
    public void open() throws IOException {
        // Write new metrics to a temp file to avoid invalid JSON files due to failed tests.
        BufferedWriter formatWriter;
        tempJsonFile = File.createTempFile(mStreamName, "-temp-report-log");
        formatWriter = new BufferedWriter(new FileWriter(tempJsonFile));
        if (mJsonFile.exists()) {
            BufferedReader jsonReader = new BufferedReader(new FileReader(mJsonFile));
            String currentLine;
            String nextLine = jsonReader.readLine();
            while ((currentLine = nextLine) != null) {
                nextLine = jsonReader.readLine();
                if (nextLine == null && currentLine.charAt(currentLine.length() - 1) == '}') {
                    // Reopen overall JSON object to write new metrics.
                    currentLine = currentLine.substring(0, currentLine.length() - 1) + ",";
                }
                // Copy to temp file directly to avoid large metrics string in memory.
                formatWriter.write(currentLine, 0, currentLine.length());
            }
            jsonReader.close();
        } else {
            formatWriter.write("{", 0 , 1);
        }
        // Start new JSON object for new metrics.
        formatWriter.write("\"" + mStreamName + "\":", 0, mStreamName.length() + 3);
        formatWriter.flush();
        formatWriter.close();
        mJsonWriter = new JsonWriter(new FileWriter(tempJsonFile, true));
        mJsonWriter.beginObject();
    }

    /**
     * Closes the writer.
     */
    @Override
    public void close() throws IOException {
        // Close JSON Writer.
        mJsonWriter.endObject();
        mJsonWriter.close();
        // Close overall JSON Object.
        try (BufferedWriter formatWriter = new BufferedWriter(new FileWriter(tempJsonFile, true))) {
            formatWriter.write("}", 0, 1);
        }
        // Copy metrics from temp file and delete temp file.
        mJsonFile.createNewFile();
        try (
                BufferedReader jsonReader = new BufferedReader(new FileReader(tempJsonFile));
                BufferedWriter metricsWriter = new BufferedWriter(new FileWriter(mJsonFile))
        ) {
            String line;
            while ((line = jsonReader.readLine()) != null) {
                // Copy from temp file directly to avoid large metrics string in memory.
                metricsWriter.write(line, 0, line.length());
            }
        }
    }
}
