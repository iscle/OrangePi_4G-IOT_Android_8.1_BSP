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

import static com.android.wearable.uibench.janktests.UiBenchJankTestsHelper.EXPECTED_FRAMES;
import static com.android.wearable.uibench.janktests.UiBenchJankTestsHelper.PACKAGE_NAME;

import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.StaleObjectException;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.view.KeyEvent;
import android.widget.ListView;

import com.android.wearable.uibench.janktests.UiBenchJankTestsHelper;

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
        mHelper = UiBenchJankTestsHelper.getInstance(mDevice,
             this.getInstrumentation().getContext());
        mHelper.enableKeyboardIME(false);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        mHelper.enableKeyboardIME(true);
        super.tearDown();
    }

    // Open Text Components
    public void openTextComponents(String componentName) throws RemoteException {
        mHelper.launchUiBench();
        mHelper.openTextInList("Text");
        mHelper.openTextInList(componentName);
    }

    // Open EditText Typing
    public void openEditTextTyping() throws RemoteException {
        openTextComponents("EditText Typing");
    }

    // Measure jank metrics for EditText Typing
    @JankTest(beforeTest="openEditTextTyping", afterTest="goBackHome",
        expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testEditTextTyping() {
        SystemClock.sleep(mHelper.LONG_TIMEOUT);
    }

    // Open Layout Cache High Hitrate
    public void openLayoutCacheHighHitrate() throws RemoteException {
        openTextComponents("Layout Cache High Hitrate");
    }

    // Test Layout Cache High Hitrate fling
    @JankTest(beforeTest="openLayoutCacheHighHitrate", afterTest="goBackHome",
        expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testLayoutCacheHighHitrateFling() {
        UiObject2 layoutCacheHighHitrateContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
        Assert.assertNotNull("LayoutCacheHighHitrateContents isn't found",
                layoutCacheHighHitrateContents);

        for (int i = 0; i < mHelper.INNER_LOOP; i++) {
            try {
                layoutCacheHighHitrateContents = mDevice.wait(Until.findObject(
                        By.clazz(ListView.class)), mHelper.TIMEOUT);
                layoutCacheHighHitrateContents.fling(Direction.DOWN, 5000);
                SystemClock.sleep(mHelper.SHORT_TIMEOUT);

                layoutCacheHighHitrateContents = mDevice.wait(Until.findObject(
                        By.clazz(ListView.class)), mHelper.TIMEOUT);
                layoutCacheHighHitrateContents.fling(Direction.UP, 5000);
                SystemClock.sleep(mHelper.SHORT_TIMEOUT);
            } catch (StaleObjectException soex) {
                layoutCacheHighHitrateContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
            }
        }
    }

    // Open Layout Cache Low Hitrate
    public void openLayoutCacheLowHitrate() throws RemoteException {
        openTextComponents("Layout Cache Low Hitrate");
    }

    // Test Layout Cache Low Hitrate fling
    @JankTest(beforeTest="openLayoutCacheLowHitrate", afterTest="goBackHome",
        expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testLayoutCacheLowHitrateFling() {
        UiObject2 layoutCacheLowHitrateContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
        Assert.assertNotNull("LayoutCacheLowHitrateContents isn't found",
                layoutCacheLowHitrateContents);

        for (int i = 0; i < mHelper.INNER_LOOP; i++) {
            try {
                layoutCacheLowHitrateContents = mDevice.wait(Until.findObject(
                        By.clazz(ListView.class)), mHelper.TIMEOUT);
                layoutCacheLowHitrateContents.fling(Direction.DOWN, 5000);
                SystemClock.sleep(mHelper.SHORT_TIMEOUT);

                layoutCacheLowHitrateContents = mDevice.wait(Until.findObject(
                        By.clazz(ListView.class)), mHelper.TIMEOUT);
                layoutCacheLowHitrateContents.fling(Direction.UP, 5000);
                SystemClock.sleep(mHelper.SHORT_TIMEOUT);
            } catch (StaleObjectException soex) {
                layoutCacheLowHitrateContents = mDevice.wait(Until.findObject(
                By.clazz(ListView.class)), mHelper.TIMEOUT);
            }
         }
    }

    // Ensuring that we head back to the first screen before launching the app again
    public void goBackHome(Bundle metrics) throws UiObjectNotFoundException {
            mHelper.goBackHome();
            super.afterTest(metrics);
    }

}
