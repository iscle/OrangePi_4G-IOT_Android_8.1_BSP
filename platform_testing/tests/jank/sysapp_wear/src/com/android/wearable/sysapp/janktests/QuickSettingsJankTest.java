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
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

import junit.framework.Assert;

import java.util.concurrent.TimeoutException;

/**
 * Jank tests for Quick settings when pulling down, pulling up the shade. And also when swiping in
 * quick settings options.
 */
public class QuickSettingsJankTest extends JankTestBase {

    private UiDevice mDevice;
    private SysAppTestHelper mHelper;

    private static final String WEARABLE_APP_PACKAGE = "com.google.android.wearable.app";
    private static final String QUICK_SETTINGS_LAUNCHED_INDICATOR = "Quick settings";

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

    private void isQuickSettingShadeLaunched() throws TimeoutException {
        //Wait until date & battery info transitions to page indicator
        SystemClock.sleep(SysAppTestHelper.SHORT_TIMEOUT + SysAppTestHelper.SHORT_TIMEOUT);
        UiObject2 quickSettingsShade = mDevice.wait(
                Until.findObject(By.desc(QUICK_SETTINGS_LAUNCHED_INDICATOR)),
                SysAppTestHelper.SHORT_TIMEOUT);
        Assert.assertNotNull("Quick settings shade not launched", quickSettingsShade);

    }

    // Prepare device to be on Home before pulling down Quick settings shade
    public void startFromHome() {
        mHelper.goBackHome();
    }

    // Verify jank while pulling down quick settings
    @JankTest(beforeLoop = "startFromHome", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testPullDownQuickSettings() {
        mHelper.swipeDown();
    }

    // Prepare device by pulling down the quick settings shade.
    public void openPullUpQuickSettings() throws TimeoutException {
        mHelper.goBackHome();
        mHelper.swipeDown();
        isQuickSettingShadeLaunched();
    }

    // Verify jank while pulling up quick settings
    @JankTest(beforeLoop = "openPullUpQuickSettings", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testPullUpQuickSettings() {
        mHelper.swipeUp();
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        mHelper.goBackHome();
        super.afterTest(metrics);
    }
}
