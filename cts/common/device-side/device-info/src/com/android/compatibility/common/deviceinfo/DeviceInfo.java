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

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.JsonWriter;
import android.util.Log;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Collect device information on target device and write to a JSON file.
 */
public abstract class DeviceInfo extends InstrumentationTestCase {

    private enum ResultCode {
        // Collection started.
        STARTED,
        // Collection completed.
        COMPLETED,
        // Collection completed with error.
        ERROR,
        // Collection failed to complete.
        FAILED
    }

    private static final int MAX_STRING_VALUE_LENGTH = 1000;
    private static final int MAX_ARRAY_LENGTH = 1000;

    private static final String LOG_TAG = "ExtendedDeviceInfo";

    private JsonWriter mJsonWriter = null;
    private String mResultFilePath = null;
    private String mErrorMessage = null;
    private ResultCode mResultCode = ResultCode.STARTED;

    Set<String> mActivityList = new HashSet<String>();

    public void testCollectDeviceInfo() throws Exception {
        if (!mActivityList.contains(getClass().getName())) {
            return;
        }

        final File dir = new File(Environment.getExternalStorageDirectory(), "device-info-files");
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            failed("External storage is not mounted");
        } else if (!dir.mkdirs() && !dir.isDirectory()) {
            failed("Cannot create directory for device info files");
        } else {
            try {
                File jsonFile = new File(dir, getClass().getSimpleName() + ".deviceinfo.json");
                jsonFile.createNewFile();
                mResultFilePath = jsonFile.getAbsolutePath();
                DeviceInfoStore store = new DeviceInfoStore(jsonFile);
                store.open();
                collectDeviceInfo(store);
                store.close();
                if (mResultCode == ResultCode.STARTED) {
                    mResultCode = ResultCode.COMPLETED;
                }
            } catch (Exception e) {
                failed("Could not collect device info", e);
            }
        }

        String message = getClass().getSimpleName() + " collection completed.";
        assertEquals(message, ResultCode.COMPLETED, mResultCode);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Build the list of supported activities that can run collection.
        ActivityInfo[] activities = null;
        try {
            activities = getContext().getPackageManager().getPackageInfo(
                getContext().getPackageName(), PackageManager.GET_ACTIVITIES).activities;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception occurred while getting activities.", e);
            return;
        }

        for (ActivityInfo activityInfo : activities) {
            mActivityList.add(activityInfo.name);
        }
    }

    /**
     * Method to collect device information.
     */
    protected abstract void collectDeviceInfo(DeviceInfoStore store) throws Exception;

    protected Context getContext() {
        return getInstrumentation().getContext();
    }

    /**
     * Returns the path to the json file if collector completed successfully.
     */
    String getResultFilePath() {
        return mResultFilePath;
    }

    private void error(String message, Throwable exception) {
        mResultCode = ResultCode.ERROR;
        mErrorMessage = message;
        Log.e(LOG_TAG, message, exception);
    }

    private void failed(String message, Throwable exception) {
        mResultCode = ResultCode.FAILED;
        mErrorMessage = message;
        Log.e(LOG_TAG, message, exception);
    }

    private void failed(String message) {
        mResultCode = ResultCode.FAILED;
        mErrorMessage = message;
        Log.e(LOG_TAG, message);
    }
}
