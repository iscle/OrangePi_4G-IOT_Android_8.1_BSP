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
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.UiDevice;

/**
 * Jank tests for scrolling & swiping off notification cards on wear
 */
public class CardsJankTest extends JankTestBase {

    private UiDevice mDevice;
    private SysAppTestHelper mHelper;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, this.getInstrumentation());
        mDevice.wakeUp();
    }

    // Prepare device to start scrolling by tapping on the screen
    // As this is done using demo cards a tap on screen will stop animation and show
    // home screen
    public void openScrollCard() throws Exception {
        mHelper.hasDemoCards();
        mHelper.swipeUp();
    }

    // Measure card scroll jank

    @JankTest(beforeLoop = "openScrollCard", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = "com.google.android.wearable.app")
    public void testScrollCard() {
        mHelper.swipeUp();
    }

    // Preparing the cards to full view before dismissing them

    public void openSwipeCard() throws Exception {
        mHelper.hasDemoCards();
        mHelper.swipeUp();
    }

    // Measure jank when dismissing a card

    @JankTest(beforeLoop = "openSwipeCard", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = "com.google.android.wearable.app")
    public void testSwipeCard() {
        mHelper.swipeRight();
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) {
        mHelper.goBackHome();
        super.afterTest(metrics);
    }

    // Measure jank when dismissing on an expanded card
    @JankTest(beforeLoop = "openSwipeExpandedCard", afterTest = "goBackHome",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_DISMISS_EXPANDED_CARDS_TEST)
    @GfxMonitor(processName = "com.google.android.wearable.app")
    public void testSwipeExpandedCard() {
        mHelper.swipeRight();
    }

    // Preparing the expanded card
    public void openSwipeExpandedCard() throws Exception {
        openSwipeCard();
        mHelper.clickScreenCenter();
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
