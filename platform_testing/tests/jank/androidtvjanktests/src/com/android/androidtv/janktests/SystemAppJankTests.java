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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;

import java.io.IOException;

/*
 * This class contains the tests for key system apps on Android TV jank.
 */
public class SystemAppJankTests extends JankTestBase {

    private static final int LONG_TIMEOUT = 5000;
    private static final int INNER_LOOP = 8;
    private static final int FLING_SPEED = 12000;
    private static final String YOUTUBE_PACKAGE = "com.google.android.youtube.tv";
    private UiDevice mDevice;

    @Override
    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void afterTestSystemApp(Bundle metrics) throws IOException {
        mDevice.pressHome();
        super.afterTest(metrics);
    }

    public void launchYoutube() throws UiObjectNotFoundException {
        mDevice.pressHome();
        launchApp(YOUTUBE_PACKAGE);
        SystemClock.sleep(LONG_TIMEOUT);
        // Ensure that Youtube has loaded on Android TV with nav bar in focus
        UiObject2 youtubeScreen = mDevice.wait(
                Until.findObject(By.scrollable(true).res(YOUTUBE_PACKAGE, "guide")), LONG_TIMEOUT);
    }

    // Measures jank while scrolling down the Youtube Navigation Bar
    @JankTest(expectedFrames=100, beforeTest = "launchYoutube",
            afterTest="afterTestSystemApp")
    @GfxMonitor(processName=YOUTUBE_PACKAGE)
    public void testYoutubeGuideNavigation() throws UiObjectNotFoundException {
        // As of launching Youtube, we're already at the screen where
        // the navigation bar is in focus, so we only need to scroll.
        navigateDownAndUpCurrentScreen();
    }

    public void goToYoutubeContainer() throws UiObjectNotFoundException {
        launchYoutube();
        // Move focus from Youtube navigation bar to content
        mDevice.pressDPadRight();
        SystemClock.sleep(LONG_TIMEOUT);
        // Ensure that Youtube content is in focus
        UiObject2 youtubeScreen = mDevice.wait( Until.findObject(By.scrollable(true)
                .res(YOUTUBE_PACKAGE, "container_list")), LONG_TIMEOUT);
    }

    // Measures jank while scrolling down the Youtube Navigation Bar
    @JankTest(expectedFrames=100, beforeTest = "goToYoutubeContainer",
            afterTest="afterTestSystemApp")
    @GfxMonitor(processName=YOUTUBE_PACKAGE)
    public void testYoutubeContainerListNavigation() throws UiObjectNotFoundException {
        // The gotoYouTubeContainer method confirms that the focus is
        // on the content, so we only need to scroll.
        navigateDownAndUpCurrentScreen();
    }

    public void navigateDownAndUpCurrentScreen() {
        for (int i = 0; i < INNER_LOOP; i++) {
            // Press DPad button down eight times in succession to scroll down.
            mDevice.pressDPadDown();
        }
        for (int i = 0; i < INNER_LOOP; i++) {
            // Press DPad button up eight times in succession to scroll up.
            mDevice.pressDPadUp();
        }
    }

    public void launchApp(String packageName) throws UiObjectNotFoundException {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        Intent appIntent = pm.getLaunchIntentForPackage(packageName);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getContext().startActivity(appIntent);
        SystemClock.sleep(LONG_TIMEOUT);
    }
}