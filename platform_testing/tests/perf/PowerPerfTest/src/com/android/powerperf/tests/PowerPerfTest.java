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

package com.android.powerperf.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.android.helper.PowerTestHelper;

/**
 * Test to start the script file at a given path. Script file in turn starts the binary and
 * redirects the output to result file
 */
public class PowerPerfTest extends PowerTestHelper {

    private static final String TAG = "PowerPerfInstrumentation";
    private static final String RESULT_FILE = "result";
    private String mScriptFilePath;
    private Bundle mParams;

    public void testPowerPerf() throws FileNotFoundException, IOException, Exception {
        mParams = getParams();
        mScriptFilePath = mParams.getString("script_filepath");
        assertNotNull("Script file path not set", mScriptFilePath);
        writePowerLogStart(getPropertyString("TestCase"));
        String result = getUiDevice().executeShellCommand(mScriptFilePath);
        Log.i(TAG, result);
        writePowerLogEnd(getPropertyString("TestCase"));
    }
}

