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

package com.android.wearable.sysapp.janktests;

import android.os.Bundle;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObjectNotFoundException;

/**
 * Jank tests for WatchFace on clockwork device
 */
public class WatchFaceJankTest extends JankTestBase {

    private static final String WEARABLE_APP_PACKAGE = "com.google.android.wearable.app";

    private UiDevice mDevice;
    private SysAppTestHelper mHelper;
    private WatchFaceHelper mWfHelper;

    /*
     * (non-Javadoc)
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mHelper = SysAppTestHelper.getInstance(mDevice, getInstrumentation());
        mWfHelper = WatchFaceHelper.getInstance(mDevice, getInstrumentation());
    }

    /**
     * Test the jank by pulling down quick settings on top of specific watch face
     */
    @JankTest(beforeLoop = "prepareForPullDownQuickSettings",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testPullDownQuickSettings() {
        mHelper.swipeDown();
        mHelper.swipeUp();
    }

    /**
     * Test the jank by scrolling cards on top of specific watch face
     */
    @JankTest(beforeLoop = "prepareForScrollCards",
            afterTest = "removeAllButOneWatchFace",
            expectedFrames = SysAppTestHelper.EXPECTED_FRAMES_CARDS_TEST)
    @GfxMonitor(processName = WEARABLE_APP_PACKAGE)
    public void testScrollCards() {
        mHelper.swipeUp();
    }

    public void selectWatchFace() throws UiObjectNotFoundException {
        String watchFaceName = getArguments().getString("watchface_name");
        mHelper.goBackHome();
        mWfHelper.openPicker();
        mWfHelper.openPickerAllList();
        mWfHelper.selectWatchFaceFromFullList(watchFaceName);
    }

    public void removeAllButOneWatchFace(Bundle metrics) {
        mWfHelper.removeAllButOneWatchFace();
        super.afterTest(metrics);
    }

    public void prepareForPullDownQuickSettings() throws UiObjectNotFoundException {
        selectWatchFace();
    }

    public void prepareForScrollCards() throws UiObjectNotFoundException {
        selectWatchFace();
        mHelper.hasDemoCards();
    }
}
