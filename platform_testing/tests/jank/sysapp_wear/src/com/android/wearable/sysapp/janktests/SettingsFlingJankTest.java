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

package com.android.wearable.sysapp.janktests;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.jank.WindowAnimationFrameStatsMonitor;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import java.util.concurrent.TimeoutException;

/**
 * Jank tests to fling through Settings app on clockwork device
 */
public class SettingsFlingJankTest extends JankTestBase {

    // Settings app resources
    private static final String CLOCK_SETTINGS_PACKAGE =
            "com.google.android.apps.wearable.settings";
    private static final String CLOCK_SETTINGS_ACTIVITY =
            "com.google.android.clockwork.settings.MainSettingsActivity";
    private UiDevice mDevice;
    private SysAppTestHelper mHelper;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, this.getInstrumentation());
        mDevice.wakeUp();
        super.setUp();
    }

    // Prepare device to launch Settings app and scroll through bottom to start fling test
    public void openSettingsApp() {
        mHelper.launchActivity(CLOCK_SETTINGS_PACKAGE, CLOCK_SETTINGS_ACTIVITY);
        SystemClock.sleep(SysAppTestHelper.SHORT_TIMEOUT);
    }

    /**
     * Test the jank by flinging in settings screen.
     */
    @JankTest(beforeTest = "openSettingsApp", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES)
    @GfxMonitor(processName = CLOCK_SETTINGS_PACKAGE)
    public void testSettingsApp() throws TimeoutException {
        UiObject2 listViewContents = mDevice.wait(Until.findObject(
                By.res("android", "list")),
                SysAppTestHelper.SHORT_TIMEOUT);
        for (int i = 0; i < 3; i++) {
            listViewContents.fling(Direction.DOWN, SysAppTestHelper.FLING_SPEED);
            listViewContents.fling(Direction.UP, SysAppTestHelper.FLING_SPEED);
        }
    }

    @JankTest(beforeLoop = "openSettingsApp", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_SWIPERIGHT_TO_DISMISS_TEST)
    @WindowAnimationFrameStatsMonitor
    public void testSwipeRightToDismissApp() {
        mHelper.swipeRight();
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        mHelper.goBackHome();
        super.afterTest(metrics);
    }

    /*
     * (non-Javadoc)
     * @see android.test.InstrumentationTestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
