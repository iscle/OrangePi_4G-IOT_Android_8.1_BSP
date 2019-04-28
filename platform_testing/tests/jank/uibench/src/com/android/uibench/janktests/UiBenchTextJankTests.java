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

package com.android.uibench.janktests;

import static com.android.uibench.janktests.UiBenchJankTestsHelper.EXPECTED_FRAMES;
import static com.android.uibench.janktests.UiBenchJankTestsHelper.PACKAGE_NAME;

import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.widget.ListView;
import junit.framework.Assert;

/**
 * Jank benchmark Text tests for UiBench app
 */

public class UiBenchTextJankTests extends JankTestBase {

    private UiDevice mDevice;
    private UiBenchJankTestsHelper mHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.setOrientationNatural();
        mHelper = UiBenchJankTestsHelper.getInstance(
                this.getInstrumentation().getContext(), mDevice);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    // Open EditText Typing
    public void openEditTextTyping() {
        mHelper.launchActivity("EditTextTypeActivity",
                "Text/EditText Typing");
    }

    // Measure jank metrics for EditText Typing
    // Reenable the test after b/62917134 is fixed
    // @JankTest(beforeTest = "openEditTextTyping", expectedFrames = EXPECTED_FRAMES)
    // @GfxMonitor(processName = PACKAGE_NAME)
    // public void testEditTextTyping() {
    //    SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    //}

    // Open Layout Cache High Hitrate
    public void openLayoutCacheHighHitrate() {
        mHelper.launchActivity("TextCacheHighHitrateActivity",
                "Text/Layout Cache High Hitrate");
        mHelper.mContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
        Assert.assertNotNull("LayoutCacheHighHitrateContents isn't found",
                mHelper.mContents);
    }

    // Test Layout Cache High Hitrate fling
    @JankTest(beforeTest = "openLayoutCacheHighHitrate", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testLayoutCacheHighHitrateFling() {
        mHelper.flingUpDown(mHelper.mContents, 3);
    }

    // Open Layout Cache Low Hitrate
    public void openLayoutCacheLowHitrate() {
        mHelper.launchActivity("TextCacheLowHitrateActivity",
                "Text/Layout Cache Low Hitrate");
        mHelper.mContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
        Assert.assertNotNull("LayoutCacheLowHitrateContents isn't found",
                mHelper.mContents);
    }

    // Test Layout Cache Low Hitrate fling
    @JankTest(beforeTest = "openLayoutCacheLowHitrate", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testLayoutCacheLowHitrateFling() {
        mHelper.flingUpDown(mHelper.mContents, 3);
    }

}
