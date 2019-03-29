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

package com.android.cts.cpptools;

import com.android.tradefed.testtype.DeviceTestCase;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Test to check the host can execute commands via "adb shell run-as".
 */
public class RunAsHostTest extends DeviceTestCase {

    /**
     * Tests that host can execute shell commands as a debuggable app via adb.
     *
     * @throws Exception
     */
    public void testRunAs() throws Exception {
        String runAsResult = getDevice().executeShellCommand("run-as android.cpptools.app id -u");
        assertNotNull("adb shell command failed", runAsResult);
        runAsResult = runAsResult.trim();
        Matcher appIdMatcher = Pattern.compile("^([0-9]+)$").matcher(runAsResult);
        assertTrue("unexpected result returned by adb shell command: \"" + runAsResult + "\"",
                   appIdMatcher.matches());
        String appIdString = appIdMatcher.group(1);
        assertTrue("invalid app id " + appIdString, Integer.parseInt(appIdString) > 10000);
    }
}
