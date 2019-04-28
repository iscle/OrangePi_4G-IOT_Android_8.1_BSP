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

package com.android.androidtv.janktests;

import android.os.Bundle;
import android.platform.test.utils.DPadUtil;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.util.Log;

import junit.framework.Assert;

import java.io.IOException;

/*
 * This class contains the tests for Android TV jank.
 */
public class SystemUiJankTests extends JankTestBase {

    private static final String TAG = SystemUiJankTests.class.getSimpleName();
    private static final int SHORT_TIMEOUT = 1000;
    private static final int INNER_LOOP = 4;
    private static final int INNER_LOOP_SETTINGS = 8;
    private static final String TVLAUNCHER_PACKAGE = "com.google.android.tvlauncher";
    private static final String SETTINGS_PACKAGE = "com.android.tv.settings";
    private static final BySelector SELECTOR_TOP_ROW = By.res(TVLAUNCHER_PACKAGE, "top_row");
    private UiDevice mDevice;
    private DPadUtil mDPadUtil;

    @Override
    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDPadUtil = new DPadUtil(getInstrumentation());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void goHome() {
        mDevice.pressHome();
        UiObject2 homeScreen = mDevice
            .wait(Until.findObject(By.res(TVLAUNCHER_PACKAGE, "home_view_container")),
                SHORT_TIMEOUT);
        Assert.assertNotNull("Ensure that Home screen is being displayed", homeScreen);
    }

    public void goTopRow() {
        Assert.assertNotNull(select(SELECTOR_TOP_ROW.hasDescendant(By.focused(true)), Direction.UP,
                SHORT_TIMEOUT));
    }

    public void afterTestHomeScreenNavigation(Bundle metrics) throws IOException {
        super.afterTest(metrics);
    }

    // Measures jank while navigating up and down the Home screen
    @JankTest(expectedFrames=100, beforeTest = "goHome",
            afterTest="afterTestHomeScreenNavigation")
    @GfxMonitor(processName=TVLAUNCHER_PACKAGE)
    public void testHomeScreenNavigation() throws UiObjectNotFoundException {
        // We've already verified that Home screen is being displayed.
        // Navigate up and down the home screen.
        navigateDownAndUpCurrentScreen(INNER_LOOP);
    }

    // Navigates to the Settings button on the Top row
    public void goToSettingsButton() {
        // Navigate to Home screen and verify that it is being displayed.
        goHome();
        goTopRow();
        Assert.assertNotNull("Ensure that Settings button is focused",
            selectBidirect(By.res(TVLAUNCHER_PACKAGE, "settings").focused(true), Direction.RIGHT));
    }

    public void afterTestSettings(Bundle metrics) throws IOException {
        // Navigate back home
        goHome();
        super.afterTest(metrics);
    }

    // Measures jank while navigating to Settings from Home and back
    @JankTest(expectedFrames=100, beforeTest="goToSettingsButton",
            afterTest="afterTestSettings")
    @GfxMonitor(processName=SETTINGS_PACKAGE)
    public void testNavigateToSettings() throws UiObjectNotFoundException {
        for (int i = 0; i < INNER_LOOP * 10; i++) {
            // Press DPad center button to navigate to settings.
            mDPadUtil.pressDPadCenter();
            mDevice.wait(Until.hasObject(
                    By.res(SETTINGS_PACKAGE, "settings_preference_fragment_container")),
                    SHORT_TIMEOUT);
            // Press Back button to go back to the Home screen with focus on Settings
            mDPadUtil.pressBack();
        }
    }

    // Navigates to the Settings Screen
    public void goToSettings() {
        goToSettingsButton();
        mDPadUtil.pressDPadCenter();
        Assert.assertNotNull("Ensure that Settings is being displayed",
            mDevice.wait(
                Until.hasObject(By.res(SETTINGS_PACKAGE, "settings_preference_fragment_container")),
                SHORT_TIMEOUT));
    }

    // Measures jank while scrolling on the Settings screen
    @JankTest(expectedFrames=100, beforeTest="goToSettings",
            afterTest="afterTestSettings")
    @GfxMonitor(processName=SETTINGS_PACKAGE)
    public void testSettingsScreenNavigation() throws UiObjectNotFoundException {
        navigateDownAndUpCurrentScreen(INNER_LOOP_SETTINGS);
    }

    public void navigateDownAndUpCurrentScreen(int iterations) {
        for (int i = 0; i < iterations; i++) {
            // Press DPad button down eight times in succession
            mDPadUtil.pressDPadDown();
        }
        for (int i = 0; i < iterations; i++) {
            // Press DPad button up eight times in succession.
            mDPadUtil.pressDPadUp();
        }
    }

    /**
     * Select an UI element with given {@link BySelector}. This action keeps moving a focus
     * in a given {@link Direction} until it finds a matched element.
     * @param selector the search criteria to match an element
     * @param direction the direction to find
     * @param timeoutMs timeout in milliseconds to select
     * @return a UiObject2 which represents the matched element
     */
    public UiObject2 select(BySelector selector, Direction direction, long timeoutMs) {
        UiObject2 focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_TIMEOUT);
        while (!mDevice.wait(Until.hasObject(selector), timeoutMs)) {
            Log.d(TAG, String.format("select: moving a focus from %s to %s", focus, direction));
            UiObject2 focused = focus;
            mDPadUtil.pressDPad(direction);
            focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_TIMEOUT);
            // Hack: A focus might be lost in some UI. Take one more step forward.
            if (focus == null) {
                mDPadUtil.pressDPad(direction);
                focus = mDevice.wait(Until.findObject(By.focused(true)), SHORT_TIMEOUT);
            }
            // Check if it reaches to an end where it no longer moves a focus to next element
            if (focused.equals(focus)) {
                Log.d(TAG, "select: not found until it reaches to an end.");
                return null;
            }
        }
        Log.i(TAG, String.format("select: %s is selected", focus));
        return focus;
    }

    /**
     * Select an element with a given {@link BySelector} in both given direction and reverse.
     */
    public UiObject2 selectBidirect(BySelector selector, Direction direction) {
        Log.d(TAG, String.format("selectBidirect [direction]%s", direction));
        UiObject2 object = select(selector, direction, SHORT_TIMEOUT);
        if (object == null) {
            object = select(selector, Direction.reverse(direction), SHORT_TIMEOUT);
        }
        return object;
    }
}
