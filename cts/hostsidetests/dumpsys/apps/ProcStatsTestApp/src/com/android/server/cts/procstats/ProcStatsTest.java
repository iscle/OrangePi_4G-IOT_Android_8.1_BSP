/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.procstats;

import static junit.framework.Assert.assertEquals;
import static junit.framework.TestCase.fail;

import android.content.ComponentName;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Used by NetstatsIncidentTest.  Makes some network requests so "dumpsys netstats" will have
 * something to show.
 */
@RunWith(AndroidJUnit4.class)
public class ProcStatsTest {
    private static final String TAG = "ProcStatsTest";
    private static final String HELPER_PACKAGE = "com.android.server.cts.procstatshelper";

    @After
    public void tearDown() {
        runCommand("dumpsys procstats --stop-pretend-screen", "^$");
    }

    private static final Intent buildIntent(String component) {
        return new Intent()
                .setComponent(ComponentName.unflattenFromString(component));
    }

    @Test
    public void testLaunchApp() throws Exception {

        InstrumentationRegistry.getContext().startActivity(
                buildIntent(HELPER_PACKAGE + "/.MainActivity"));

        Thread.sleep(4000);

        InstrumentationRegistry.getContext().startService(
                buildIntent(HELPER_PACKAGE + "/.ProcStatsHelperServiceMain"));

        Thread.sleep(4000);

        // Now run something with the screen off.
        runCommand("dumpsys procstats --pretend-screen-off", "^$");

        InstrumentationRegistry.getContext().startActivity(
                buildIntent(HELPER_PACKAGE + "/.MainActivity"));

        Thread.sleep(4000);

        InstrumentationRegistry.getContext().startService(
                buildIntent(HELPER_PACKAGE + "/.ProcStatsHelperServiceSub"));

        Thread.sleep(4000);

        // run "dumpsys meminfo" to update the PSS stats.
        runCommand("dumpsys meminfo " + HELPER_PACKAGE,
                "MEMINFO in pid");
        runCommand("dumpsys meminfo " + HELPER_PACKAGE + ":proc2",
                "MEMINFO in pid");
    }

    static List<String> readAll(ParcelFileDescriptor pfd) {
        try {
            try {
                final ArrayList<String> ret = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(
                        new FileReader(pfd.getFileDescriptor()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        ret.add(line);
                    }
                    r.readLine();
                }
                return ret;
            } finally {
                pfd.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String concatResult(List<String> result) {
        final StringBuilder sb = new StringBuilder();
        for (String s : result) {
            sb.append(s);
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private void runCommand(String command, String expectedOutputRegex) {
        Log.i(TAG, "Running comamnd: " + command);
        final String result = concatResult(readAll(
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                        command)));
        Log.i(TAG, "Output:");
        Log.i(TAG, result);
        if (!Pattern.compile(expectedOutputRegex).matcher(result).find()) {
            fail("Expected=" + expectedOutputRegex + "\nBut was=" + result);
        }
    }
}

