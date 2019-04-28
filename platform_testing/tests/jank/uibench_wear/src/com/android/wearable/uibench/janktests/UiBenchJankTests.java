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

package com.android.wearable.uibench.janktests;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.widget.ListView;

import com.android.wearable.uibench.janktests.UiBenchJankTestsHelper;
import static com.android.wearable.uibench.janktests.UiBenchJankTestsHelper.PACKAGE_NAME;
import static com.android.wearable.uibench.janktests.UiBenchJankTestsHelper.EXPECTED_FRAMES;
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
        mHelper = UiBenchJankTestsHelper.getInstance(mDevice,
                this.getInstrumentation().getContext());
        mDevice.wakeUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    // Open General Components
    public void openGeneralComponents(String componentName) {
        mHelper.launchUiBench();
        mHelper.openTextInList("General");
        mHelper.openTextInList(componentName);
    }

    // Open Fullscreen Overdraw from General
    public void openFullscreenOverdraw() {
        openGeneralComponents("Fullscreen Overdraw");
    }

    // Measure fullscreen overdraw jank
    @JankTest(beforeTest="openFullscreenOverdraw", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testFullscreenOverdraw() {
        UiObject2 fullscreenOverdrawScreen = mDevice.wait(Until.findObject(
                By.res("android", "content")), mHelper.TIMEOUT);
        Assert.assertNotNull("Fullscreen Overdraw isn't found", fullscreenOverdrawScreen);
        SystemClock.sleep(mHelper.LONG_TIMEOUT);
    }

    // Open GL TextureView from General
    public void openGLTextureView() {
        openGeneralComponents("GL TextureView");
    }

    // Measure GL TextureView jank metrics
    @JankTest(beforeTest="openGLTextureView", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testGLTextureView() {
        SystemClock.sleep(mHelper.LONG_TIMEOUT);
    }

    // Open Invalidate from General
    public void openInvalidate() {
        openGeneralComponents("Invalidate");
    }

    // Measure Invalidate jank metrics
    @JankTest(beforeTest="openInvalidate", afterTest="goBackHome", expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testInvalidate() {
        UiObject2 invalidateScreen = mDevice.wait(Until.findObject(
                By.res("android", "content")), mHelper.TIMEOUT);
        Assert.assertNotNull("Invalidate screen isn't found", invalidateScreen);
        SystemClock.sleep(mHelper.LONG_TIMEOUT);
    }

    // Open Trivial Animation from General
    public void openTrivialAnimation() {
        openGeneralComponents("Trivial Animation");
    }

    // Measure TrivialAnimation jank metrics
    @JankTest(beforeTest="openTrivialAnimation", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testTrivialAnimation() {
        UiObject2 trivialAnimationScreen = mDevice.wait(Until.findObject(
                By.res("android", "content")), mHelper.TIMEOUT);
        Assert.assertNotNull("Trivial Animation isn't found", trivialAnimationScreen);
        SystemClock.sleep(mHelper.LONG_TIMEOUT);
    }

    // Open Trivial listview from General
    public void openTrivialListView() {
        openGeneralComponents("Trivial ListView");
    }

    // Test trivialListView fling
    @JankTest(beforeTest="openTrivialListView", afterTest="goBackHome",
           expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testTrivialListViewFling() {
        UiObject2 trivialListViewContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
        Assert.assertNotNull("Trivial ListView isn't found in General", trivialListViewContents);
        trivialListViewContents.setGestureMargins(mDevice.getDisplayWidth() / 2 - 40,
            mDevice.getDisplayHeight() / 2 + 100,
            mDevice.getDisplayWidth() / 2 + 40,
            mDevice.getDisplayHeight() / 2 - 100);

        for (int i = 0; i < mHelper.INNER_LOOP; i++) {
            trivialListViewContents = mDevice.wait(Until.findObject(
                    By.clazz(ListView.class)), mHelper.TIMEOUT);
            trivialListViewContents.fling(Direction.DOWN, mHelper.CW_FLING_RATE);

            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
            trivialListViewContents = mDevice.wait(Until.findObject(
                    By.clazz(ListView.class)), mHelper.TIMEOUT);
            trivialListViewContents.fling(Direction.UP, mHelper.CW_FLING_RATE);
            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
         }
    }

    // Open Trivial Recycler List View from General
    public void openTrivialRecyclerListView() {
        openGeneralComponents("Trivial Recycler ListView");
    }

    // Test trivialRecyclerListView fling
    @JankTest(beforeTest="openTrivialRecyclerListView", afterTest="goBackHome",
        expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testTrivialRecyclerListViewFling() {
        UiObject2 trivialRecyclerViewContents = mDevice.wait(Until.findObject(
                By.res("android", "content")), mHelper.TIMEOUT);
        Assert.assertNotNull("Trivial Recycler ListView isn't found in General",
             trivialRecyclerViewContents);

        trivialRecyclerViewContents.setGestureMargins(mDevice.getDisplayWidth() / 2 - 40,
            mDevice.getDisplayHeight() / 2 + 100,
            mDevice.getDisplayWidth() / 2 + 40,
            mDevice.getDisplayHeight() / 2 - 100);

        for (int i = 0; i < mHelper.INNER_LOOP; i++) {
            trivialRecyclerViewContents = mDevice.wait(Until.findObject(
                    By.res("android", "content")), mHelper.TIMEOUT);
            trivialRecyclerViewContents.fling(Direction.DOWN, mHelper.CW_FLING_RATE);

            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
            trivialRecyclerViewContents = mDevice.wait(Until.findObject(
                    By.res("android", "content")), mHelper.TIMEOUT);
            trivialRecyclerViewContents.fling(Direction.UP, mHelper.CW_FLING_RATE);
            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
         }
    }

    // Open Inflation Listview contents
    public void openInflatingListView() {
        mHelper.launchUiBench();
        mHelper.openTextInList("Inflation");
        mHelper.openTextInList("Inflating ListView");
    }

    // Test Inflating List View fling
    @JankTest(beforeTest="openInflatingListView", afterTest="goBackHome",
        expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testInflatingListViewFling() {
        UiObject2 inflatingListViewContents = mDevice.wait(Until.findObject(
                By.res("android", "content")), mHelper.TIMEOUT);
        Assert.assertNotNull("Inflating ListView isn't found in Inflation",
             inflatingListViewContents);

        inflatingListViewContents.setGestureMargins(mDevice.getDisplayWidth() / 2 - 40,
            mDevice.getDisplayHeight() / 2 + 100,
            mDevice.getDisplayWidth() / 2 + 40,
            mDevice.getDisplayHeight() / 2 - 100);

        for (int i = 0; i < mHelper.INNER_LOOP; i++) {
            inflatingListViewContents = mDevice.wait(Until.findObject(
                    By.res("android", "content")), mHelper.TIMEOUT);
            inflatingListViewContents.fling(Direction.DOWN, mHelper.CW_FLING_RATE);

            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
            inflatingListViewContents = mDevice.wait(Until.findObject(
                    By.res("android", "content")), mHelper.TIMEOUT);
            inflatingListViewContents.fling(Direction.UP, mHelper.CW_FLING_RATE);
            SystemClock.sleep(mHelper.SHORT_TIMEOUT);
         }
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) throws UiObjectNotFoundException {
        mHelper.goBackHome();
        super.afterTest(metrics);
    }
}
