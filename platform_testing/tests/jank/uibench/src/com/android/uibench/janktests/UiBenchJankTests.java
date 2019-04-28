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
import static com.android.uibench.janktests.UiBenchJankTestsHelper.SHORT_EXPECTED_FRAMES;

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
 * Jank benchmark General tests for UiBench app
 */

public class UiBenchJankTests extends JankTestBase {

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

    public void openDialogList() {
        mHelper.launchActivity("DialogListActivity", "Dialog");
        mHelper.mContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), UiBenchJankTestsHelper.TIMEOUT);
        Assert.assertNotNull("Dialog List View isn't found", mHelper.mContents);
    }

    @JankTest(beforeTest = "openDialogList", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testDialogListFling() {
        mHelper.flingUpDown(mHelper.mContents, 1);
    }

    public void openFullscreenOverdraw() {
        mHelper.launchActivity("FullscreenOverdrawActivity",
                "General/Fullscreen Overdraw");
    }

    @JankTest(beforeTest = "openFullscreenOverdraw", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testFullscreenOverdraw() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openGLTextureView() {
        mHelper.launchActivity("GlTextureViewActivity",
                "General/GL TextureView");
    }

    @JankTest(beforeTest = "openGLTextureView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testGLTextureView() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openInvalidate() {
        mHelper.launchActivity("InvalidateActivity",
                "General/Invalidate");
    }

    @JankTest(beforeTest = "openInvalidate", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testInvalidate() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openInvalidateTree() {
        mHelper.launchActivity("InvalidateTreeActivity",
                "General/Invalidate Tree");
    }

    @JankTest(beforeTest = "openInvalidateTree", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testInvalidateTree() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openTrivialAnimation() {
        mHelper.launchActivity("TrivialAnimationActivity",
                "General/Trivial Animation");
    }

    @JankTest(beforeTest = "openTrivialAnimation", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testTrivialAnimation() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openTrivialListView() {
        mHelper.launchActivityAndAssert("TrivialListActivity", "General/Trivial ListView");
    }

    @JankTest(beforeTest = "openTrivialListView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testTrivialListViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    public void openFadingEdgeListView() {
        mHelper.launchActivityAndAssert("FadingEdgeListActivity", "General/Fading Edge ListView");
    }

    @JankTest(beforeTest = "openFadingEdgeListView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testFadingEdgeListViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    public void openSaveLayerInterleaveActivity() {
        mHelper.launchActivityAndAssert("SaveLayerInterleaveActivity", "General/SaveLayer Animation");
    }

    @JankTest(beforeTest = "openSaveLayerInterleaveActivity", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testSaveLayerAnimation() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openTrivialRecyclerView() {
        mHelper.launchActivityAndAssert("TrivialRecyclerViewActivity",
                "General/Trivial RecyclerView");
    }

    @JankTest(beforeTest = "openTrivialRecyclerView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testTrivialRecyclerListViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    public void openSlowBindRecyclerView() {
        mHelper.launchActivityAndAssert("SlowBindRecyclerViewActivity",
                "General/Slow Bind RecyclerView");
    }

    @JankTest(beforeTest = "openSlowBindRecyclerView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testSlowBindRecyclerViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    public void openSlowNestedRecyclerView() {
        mHelper.launchActivityAndAssert("SlowNestedRecyclerViewActivity",
                "General/Slow Nested RecyclerView");
    }

    @JankTest(beforeTest = "openSlowNestedRecyclerView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testSlowNestedRecyclerViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    @JankTest(/* NOTE: relaunch between loops */ beforeLoop = "openSlowNestedRecyclerView",
            expectedFrames = SHORT_EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testSlowNestedRecyclerViewInitialFling() {
        mHelper.slowSingleFlingDown(mHelper.mContents);
    }

    public void openInflatingListView() {
        mHelper.launchActivityAndAssert("InflatingListActivity",
                "Inflation/Inflating ListView");
    }

    @JankTest(beforeTest = "openInflatingListView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testInflatingListViewFling() {
        mHelper.flingUpDown(mHelper.mContents, 2);
    }

    public void openNavigationDrawerActivity() {
        mHelper.launchActivityAndAssert("NavigationDrawerActivity", "Navigation Drawer Activity");
        mHelper.mContents.setGestureMargins(0, 0, 10, 0);
    }

    @JankTest(beforeTest = "openNavigationDrawerActivity", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testOpenNavigationDrawer() {
        mHelper.swipeRightLeft(mHelper.mContents, 4);
    }

    public void openNotificationShade() {
        mHelper.launchActivityAndAssert("NotificationShadeActivity", "Notification Shade");
    }

    @JankTest(beforeTest = "openNotificationShade", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testOpenNotificationShade() {
        mHelper.flingUpDown(mHelper.mContents, 2, true);
    }

    public void openResizeHWLayer() {
        mHelper.launchActivity("ResizeHWLayerActivity", "General/Resize HW Layer");
    }

    @JankTest(beforeTest = "openResizeHWLayer", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testResizeHWLayer() {
        SystemClock.sleep(UiBenchJankTestsHelper.FULL_TEST_DURATION);
    }

    public void openClippedListView() {
        mHelper.launchActivityAndAssert("ClippedListActivity", "General/Clipped ListView");
    }

    @JankTest(beforeTest = "openClippedListView", expectedFrames = EXPECTED_FRAMES)
    @GfxMonitor(processName = PACKAGE_NAME)
    public void testClippedListView() {
        mHelper.swipeRightLeft(mHelper.mContents, 4);
    }
}
