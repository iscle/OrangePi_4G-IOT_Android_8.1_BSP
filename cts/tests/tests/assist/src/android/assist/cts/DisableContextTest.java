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

package android.assist.cts;

import android.assist.common.Utils;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

/** Test we receive proper assist data when context is disabled or enabled */

public class DisableContextTest extends AssistTestBase {
    static final String TAG = "DisableContextTest";

    private static final String TEST_CASE_TYPE = Utils.DISABLE_CONTEXT;

    public DisableContextTest() {
        super();
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        startTestActivity(TEST_CASE_TYPE);
    }

    @Override
    public void tearDown() throws Exception {
        SystemUtil.runShellCommand(getInstrumentation(),
                "settings put secure assist_structure_enabled 1");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 1");
        logContextAndScreenshotSetting();
        super.tearDown();
    }

    public void testContextAndScreenshotOff() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            Log.d(TAG, "Not running assist tests on low-RAM device.");
            return;
        }
        // Both settings off
        Log.i(TAG, "DisableContext: Screenshot OFF, Context OFF");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_structure_enabled 0");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 0");
        waitForBroadcast();

        logContextAndScreenshotSetting();

        verifyAssistDataNullness(true, true, true, true);

        // Screenshot off, context on
        Log.i(TAG, "DisableContext: Screenshot OFF, Context ON");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_structure_enabled 1");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 0");
        waitForBroadcast();

        logContextAndScreenshotSetting();

        verifyAssistDataNullness(false, false, false, true);

        // Context off, screenshot on
        Log.i(TAG, "DisableContext: Screenshot ON, Context OFF");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_structure_enabled 0");
        SystemUtil.runShellCommand(getInstrumentation(),
            "settings put secure assist_screenshot_enabled 1");
        waitForBroadcast();

        logContextAndScreenshotSetting();

        verifyAssistDataNullness(true, true, true, true);
    }
}
