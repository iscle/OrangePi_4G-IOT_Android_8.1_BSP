/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import android.os.Bundle;
import android.os.Environment;

import android.os.SystemClock;
import android.support.test.uiautomator.UiAutomatorTestCase;
import android.test.InstrumentationTestRunner;
import android.util.Log;

public class PowerTestHelper extends UiAutomatorTestCase {
    private final static String PARAM_CONFIG = "conf";
    private final static String SD_CARD_PATH =
        Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private final static String POWER_OUTPUT = SD_CARD_PATH + "autotester.log";
    private final static String PROPERTY_FILE_NAME = SD_CARD_PATH + "PowerTests.conf";
    private final static long SYNC_DELAY = 10 * 1000; // 10 secs
    private final static String TAG = "PowerTestHelper";

    private Bundle mParams;

    @Override
    public Bundle getParams() {
        if (mParams == null) {
            mParams = ((InstrumentationTestRunner) getInstrumentation()).getArguments();
        }
        return mParams;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mParams = getParams();
        assertNotNull("mParams is null", mParams);

        // Wait for USB to be disconnected by the test harness
        SystemClock.sleep(SYNC_DELAY);
    }

    /**
     * Expects a file from the command line via conf param or default following
     * format each on its own line. <code>
     *    key=Value
     *    Browser_URL1=cnn.com
     *    Browser_URL2=google.com
     *    Camera_ShutterDelay=1000
     *    etc...
     * </code>
     * @param Bundle params
     * @param key
     * @return the value of the property else defaultValue
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected String getPropertyString(String key)
            throws FileNotFoundException, IOException {
        String value = getProperty(key);
        if (value != null && !value.isEmpty())
            return value;
        return null;
    }

    /**
     * Expects a file from the command line via conf param or default following
     * format each on its own line. <code>
     *    key=Value
     *    Browser_URL1=cnn.com
     *    Browser_URL2=google.com
     *    Camera_ShutterDelay=1000
     *    etc...
     * </code>
     * @param Bundle params
     * @param key
     * @return the value of the property else defaultValue
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected long getPropertyLong(String key)
            throws FileNotFoundException, IOException {
        String value = getProperty(key);
        if (value != null && !value.trim().isEmpty())
            return Long.valueOf(value.trim());
        return 0;
    }

    private String getProperty(String key)
            throws FileNotFoundException, IOException {
        String value;

        Properties prop = new Properties();
        FileInputStream inputStream = new FileInputStream(mParams.getString(PARAM_CONFIG,
                PROPERTY_FILE_NAME));
        prop.load(inputStream);
        value = prop.getProperty(key);
        inputStream.close();

        return value;
    }

    /**
     * The power log capture when the measuremnt start and end. It will be
     * merged with the monsoon raw power data to get the average power usage in
     * that particular time frame.
     * @param logType
     * @param testCase
     * @param delay
     * @throws IOException
     */
    protected void writePowerLog(String logType, String testCase, long delay)
            throws IOException {
        writePowerLog(logType, testCase, System.currentTimeMillis(), delay);
    }

    /**
     * Power log capture the time when the measurement start and end. It will be
     * merged with the monsoon raw power data to get the average power usage in
     * that particular time frame.
     * @param logType
     * @param testCase : Test case name
     * @param time : Specific time stamp
     * @param delay : Delay for the actual log time.
     * @throws IOException
     */
    protected void writePowerLog(String logType, String testCase, long time,
            long delay) throws IOException {
        FileWriter outputWriter = new FileWriter(new File(POWER_OUTPUT), true);
        outputWriter.write(String.format("%d %s %s\n", (time + delay),
                logType, testCase));
        outputWriter.close();
    }

    protected void writePowerLogStart(String testCase) throws IOException {
        writePowerLog("AUTOTEST_TEST_BEGIN", testCase, 5 * 1000);
    }

    protected void writePowerLogEnd(String testCase) throws IOException {
        writePowerLog("AUTOTEST_TEST_SUCCESS", testCase, 0);
    }

    protected void writePowerLogIdleStart(String testCase, long delay) throws IOException {
        writePowerLog("AUTOTEST_TEST_BEGIN", testCase, delay);
    }

    protected void writePowerLogIdleEnd(String testCase, long delay) throws IOException {
        writePowerLog("AUTOTEST_TEST_SUCCESS", testCase, delay);
    }
}
