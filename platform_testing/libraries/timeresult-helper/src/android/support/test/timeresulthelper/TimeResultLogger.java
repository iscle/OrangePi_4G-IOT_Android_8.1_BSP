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

package android.support.test.timeresulthelper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.os.Bundle;

/**
 * Class contains helper methods to log the start and stop time and backup the results to a file.
 */
public class TimeResultLogger {

    public static void writeTimeStamp(String logType, String testCase, long delay, File destFile)
            throws IOException {
        writeTimeStamp(logType, testCase, System.currentTimeMillis(), delay, destFile);
    }

    public static void writeTimeStamp(String logType, String testCase, long time,
            long delay, File destFile) throws IOException {
        FileWriter outputWriter = new FileWriter(destFile, true);
        outputWriter.write(String.format("%d %s %s\n", (time + delay),
                logType, testCase));
        outputWriter.close();
    }

    public static void writeTimeStampLogStart(String testCase, File destFile) throws IOException {
        writeTimeStamp("AUTOTEST_TEST_BEGIN", testCase, 5 * 1000, destFile);
    }

    public static void writeTimeStampLogEnd(String testCase, File destFile) throws IOException {
        writeTimeStamp("AUTOTEST_TEST_SUCCESS", testCase, 0, destFile);
    }

    public static void writeResultToFile(String testCase, File destFile, Bundle result)
            throws IOException {
        FileWriter outputWriter = new FileWriter(destFile, true);
        outputWriter.write(String.format("Result %s\n", testCase));
        for (String key : result.keySet()) {
            outputWriter.write(String.format("%s %s\n", key,
                    result.get(key)));
        }
        outputWriter.close();
    }

}

