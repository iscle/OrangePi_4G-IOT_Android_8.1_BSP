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

package android.accessibilityservice.cts;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.concurrent.TimeoutException;

/**
 * Test global actions
 */
public class AccessibilityGlobalActionsTest extends InstrumentationTestCase {
    /**
     * Timeout required for pending Binder calls or event processing to
     * complete.
     */
    private static final long TIMEOUT_ASYNC_PROCESSING = 5000;

    /**
     * The timeout since the last accessibility event to consider the device idle.
     */
    private static final long TIMEOUT_ACCESSIBILITY_STATE_IDLE = 500;

    @MediumTest
    public void testPerformGlobalActionBack() throws Exception {
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK));

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    @MediumTest
    public void testPerformGlobalActionHome() throws Exception {
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME));

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    @MediumTest
    public void testPerformGlobalActionRecents() throws Exception {
        // Perform the action.
        boolean actionWasPerformed =
                getInstrumentation().getUiAutomation().performGlobalAction(
                        AccessibilityService.GLOBAL_ACTION_RECENTS);

        // Sleep a bit so the UI is settled.
        waitForIdle();

        if (actionWasPerformed) {
            // This is a necessary since the back action does not
            // dismiss recents until they stop animating. Sigh...
            SystemClock.sleep(5000);

            // Clean up.
            getInstrumentation().getUiAutomation().performGlobalAction(
                    AccessibilityService.GLOBAL_ACTION_BACK);
        }

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    @MediumTest
    public void testPerformGlobalActionNotifications() throws Exception {
        // Perform the action under test
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));

        // Sleep a bit so the UI is settled.
        waitForIdle();

        // Clean up.
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK));

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    @MediumTest
    public void testPerformGlobalActionQuickSettings() throws Exception {
        // Check whether the action succeeded.
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS));

        // Sleep a bit so the UI is settled.
        waitForIdle();

        // Clean up.
        // The GLOBAL_ACTION_HOME is needed in the case where additional
        // notifications showing up, we need to clear them as well for the upcoming
        // new tests to work properly.
        getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME);

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    @MediumTest
    public void testPerformGlobalActionPowerDialog() throws Exception {
        // Check whether the action succeeded.
        assertTrue(getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_POWER_DIALOG));

        // Sleep a bit so the UI is settled.
        waitForIdle();

        // Clean up.
        getInstrumentation().getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK);

        // Sleep a bit so the UI is settled.
        waitForIdle();
    }

    private void waitForIdle() throws TimeoutException {
        getInstrumentation().getUiAutomation().waitForIdle(
                TIMEOUT_ACCESSIBILITY_STATE_IDLE,
                TIMEOUT_ASYNC_PROCESSING);
    }

}
