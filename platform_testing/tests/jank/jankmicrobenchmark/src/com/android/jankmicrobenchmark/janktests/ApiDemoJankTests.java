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

package com.android.jankmicrobenchmark.janktests;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.widget.Button;

import junit.framework.Assert;

/**
 * Jank micro benchmark tests
 * App : ApiDemos
 */

public class ApiDemoJankTests extends JankTestBase {
    private static final int LONG_TIMEOUT = 5000;
    private static final int SHORT_TIMEOUT = 500;
    private static final int INNER_LOOP = 5;
    private static final int EXPECTED_FRAMES = 100;
    private static final String PACKAGE_NAME = "com.example.android.apis";
    private static final String RES_PACKAGE_NAME = "android";
    private static final String LEANBACK_LAUNCHER = "com.google.android.leanbacklauncher";
    private UiDevice mDevice;
    private UiObject2 mListView;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.setOrientationNatural();
        LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy().open();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    // This method distinguishes between home screen for handheld devices
    // and home screen for Android TV, both of whom have different Home elements.
    public UiObject2 getHomeScreen() throws UiObjectNotFoundException {
        if (mDevice.getProductName().equals("fugu")) {
            return mDevice.wait(Until.findObject(By.res(LEANBACK_LAUNCHER, "main_list_view")),
                    LONG_TIMEOUT);
        }
        else {
            String launcherPackage = mDevice.getLauncherPackageName();
            return mDevice.wait(Until.findObject(By.res(launcherPackage,"workspace")),
                    LONG_TIMEOUT);
        }
    }

    public void launchApiDemos() throws UiObjectNotFoundException {
        UiObject2 homeScreen = getHomeScreen();
        if (homeScreen == null)
            navigateToHome();
        Intent intent = getInstrumentation().getContext().getPackageManager()
                .getLaunchIntentForPackage(PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getContext().startActivity(intent);
        mDevice.waitForIdle();
    }

    public void selectAnimation(String optionName) throws UiObjectNotFoundException {
        launchApiDemos();
        UiObject2 animation = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME, "text1").text("Animation")), LONG_TIMEOUT);
        Assert.assertNotNull("Animation isn't found in ApiDemos", animation);
        animation.click();
        UiObject2 option = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME, "text1").text(optionName)), LONG_TIMEOUT);
        int maxAttempt = 3;
        while (option == null && maxAttempt > 0) {
            mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "content")), LONG_TIMEOUT)
            .scroll(Direction.DOWN, 1.0f);
            option = mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "text1")
                    .text(optionName)), LONG_TIMEOUT);
            --maxAttempt;
        }
        Assert.assertNotNull("Target option in APiDemos animation for test isn't found", option);
        option.click();
    }

    // Since afterTest only runs when the test has passed, there's no way of going
    // back to the Home Screen if a test fails. This method is a workaround. A feature
    // request has been filed to have a per test tearDown method - b/25673300
    public void navigateToHome() throws UiObjectNotFoundException {
        UiObject2 homeScreen = getHomeScreen();
        int count = 0;
        while (homeScreen == null && count <= 10) {
            mDevice.pressBack();
            homeScreen = getHomeScreen();
            count++;
        }
        Assert.assertNotNull("Hit maximum retries and couldn't find Home Screen", homeScreen);
    }

    // Since the app doesn't start at the first page when reloaded after the first time,
    // ensuring that we head back to the first screen before going Home so we're always
    // on screen one.
    public void goBackHome(Bundle metrics) throws UiObjectNotFoundException {
        navigateToHome();
        super.afterTest(metrics);
    }

    // Loads the 'activity transition' animation
    public void selectActivityTransitionAnimation() throws UiObjectNotFoundException {
        selectAnimation("Activity Transition");
    }

    // Measures jank for activity transition animation
    @JankTest(beforeTest="selectActivityTransitionAnimation", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testActivityTransitionAnimation() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 redBallTile = mDevice.wait(Until.findObject(By.res(PACKAGE_NAME, "ball")),
                    LONG_TIMEOUT);
            redBallTile.click();
            SystemClock.sleep(LONG_TIMEOUT);
            mDevice.pressBack();
        }
    }

    // Loads the 'view flip' animation
    public void selectViewFlipAnimation() throws UiObjectNotFoundException {
        selectAnimation("View Flip");
    }

    // Measures jank for view flip animation
    @JankTest(beforeTest="selectViewFlipAnimation", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testViewFlipAnimation() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 flipButton = mDevice.findObject(By.res(PACKAGE_NAME, "button"));
            flipButton.click();
            SystemClock.sleep(LONG_TIMEOUT);
        }
    }

    // Loads the 'cloning' animation
    public void selectCloningAnimation() throws UiObjectNotFoundException {
        selectAnimation("Cloning");
    }

    // Measures jank for cloning animation
    @JankTest(beforeTest="selectCloningAnimation", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testCloningAnimation() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 runCloningButton = mDevice.findObject(By.res(PACKAGE_NAME, "startButton"));
            runCloningButton.click();
            SystemClock.sleep(LONG_TIMEOUT);
        }
    }

    // Loads the 'loading' animation
    public void selectLoadingOption() throws UiObjectNotFoundException {
        selectAnimation("Loading");
    }

    // Measures jank for 'loading' animation
    @JankTest(beforeTest="selectLoadingOption", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testLoadingJank() {
        UiObject2 runButton = mDevice.wait(Until.findObject(
                By.res(PACKAGE_NAME, "startButton").text("RUN")), LONG_TIMEOUT);
        Assert.assertNotNull("Run button is null", runButton);
        for (int i = 0; i < INNER_LOOP; i++) {
            runButton.click();
            SystemClock.sleep(SHORT_TIMEOUT * 2);
        }
    }

    // Loads the 'simple transition' animation
    public void selectSimpleTransitionOption() throws UiObjectNotFoundException {
        selectAnimation("Simple Transitions");
    }

    // Measures jank for 'simple transition' animation
    @JankTest(beforeTest="selectSimpleTransitionOption", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testSimpleTransitionJank() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 scene2 = mDevice.wait(Until.findObject(
                    By.res(PACKAGE_NAME, "scene2")), LONG_TIMEOUT);
            Assert.assertNotNull("Scene2 button can't be found", scene2);
            scene2.click();
            SystemClock.sleep(SHORT_TIMEOUT);

            UiObject2 scene1 = mDevice.wait(Until.findObject(
                    By.res(PACKAGE_NAME, "scene1")), LONG_TIMEOUT);
            Assert.assertNotNull("Scene1 button can't be found", scene1);
            scene1.click();
            SystemClock.sleep(SHORT_TIMEOUT);
        }
    }

    // Loads the 'hide/show' animation
    public void selectHideShowAnimationOption() throws UiObjectNotFoundException {
        selectAnimation("Hide-Show Animations");
    }

    // Measures jank for 'hide/show' animation
    @JankTest(beforeTest="selectHideShowAnimationOption", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testHideShowAnimationJank() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 showButton = mDevice.wait(Until.findObject(By.res(
                    PACKAGE_NAME, "addNewButton").text("SHOW BUTTONS")), LONG_TIMEOUT);
            Assert.assertNotNull("'Show Buttons' button can't be found", showButton);
            showButton.click();
            SystemClock.sleep(SHORT_TIMEOUT);

            UiObject2 button0 = mDevice.wait(Until.findObject(
                    By.clazz(Button.class).text("0")), LONG_TIMEOUT);
            Assert.assertNotNull("Button0 isn't found", button0);
            button0.click();
            SystemClock.sleep(SHORT_TIMEOUT);

            UiObject2 button1 = mDevice.wait(Until.findObject(
                    By.clazz(Button.class).text("1")), LONG_TIMEOUT);
            Assert.assertNotNull("Button1 isn't found", button1);
            button1.click();
            SystemClock.sleep(SHORT_TIMEOUT);

            UiObject2 button2 = mDevice.wait(Until.findObject(
                    By.clazz(Button.class).text("2")), LONG_TIMEOUT);
            Assert.assertNotNull("Button2 isn't found", button2);
            button2.click();
            SystemClock.sleep(SHORT_TIMEOUT);

            UiObject2 button3 = mDevice.wait(Until.findObject(
                    By.clazz(Button.class).text("3")), LONG_TIMEOUT);
            Assert.assertNotNull("Button3 isn't found", button3);
            button3.click();
            SystemClock.sleep(SHORT_TIMEOUT);
        }
    }

    public void selectViews(String optionName) throws UiObjectNotFoundException {
        launchApiDemos();
        UiObject2 views = null;
        short maxAttempt = 4;
        while (views == null && maxAttempt > 0) {
            views = mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "text1")
                    .text("Views")), LONG_TIMEOUT);
            if (views == null) {
                mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "content")), LONG_TIMEOUT)
                .scroll(Direction.DOWN, 1.0f);
            }
            --maxAttempt;
        }
        Assert.assertNotNull("Views item can't be found", views);
        views.click();
        // Opens selective view (provided as param) from different 'ApiDemos Views' options
        UiObject2 option = null;
        maxAttempt = 4;
        while (option == null && maxAttempt > 0) {
            option = mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "text1")
                    .text(optionName)), LONG_TIMEOUT);
            if (option == null) {
                mDevice.wait(Until.findObject(By.res(RES_PACKAGE_NAME, "content")), LONG_TIMEOUT)
                .scroll(Direction.DOWN, 1.0f);
            }
            --maxAttempt;
        }
        Assert.assertNotNull("Target option to be tested in ApiDemos Views can't be found", option);
        option.click();
    }

    // Loads  simple listview
    public void selectListsArray() throws UiObjectNotFoundException {
        selectViews("Lists");
        UiObject2 array = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME, "text1").text("01. Array")), LONG_TIMEOUT);
        Assert.assertNotNull("Array listview can't be found", array);
        array.click();
        mListView = mDevice.wait(Until.findObject(By.res(
                   RES_PACKAGE_NAME, "content")), LONG_TIMEOUT);
        Assert.assertNotNull("Content pane isn't found to move up", mListView);
    }

    // Measures jank for simple listview fling
    @JankTest(beforeTest="selectListsArray", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testListViewJank() {
        for (int i = 0; i < INNER_LOOP; i++) {
            mListView.fling(Direction.DOWN);
            SystemClock.sleep(SHORT_TIMEOUT);
            mListView.fling(Direction.UP);
            SystemClock.sleep(SHORT_TIMEOUT);
        }
    }

    // Loads simple expandable list view
    public void selectExpandableListsSimpleAdapter() throws UiObjectNotFoundException {
        selectViews("Expandable Lists");
        UiObject2 simpleAdapter = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME, "text1").text("3. Simple Adapter")), LONG_TIMEOUT);
        Assert.assertNotNull("Simple adapter can't be found", simpleAdapter);
        simpleAdapter.click();
    }

    // Measures jank for simple expandable list view expansion
    // Expansion group1, group3 and group4 arbitrarily selected
    @JankTest(beforeTest="selectExpandableListsSimpleAdapter", afterTest="goBackHome",
            expectedFrames=EXPECTED_FRAMES)
    @GfxMonitor(processName=PACKAGE_NAME)
    public void testExapandableListViewJank() {
        for (int i = 0; i < INNER_LOOP; i++) {
            UiObject2 group1 = mDevice.wait(Until.findObject(By.res(
                    RES_PACKAGE_NAME, "text1").text("Group 1")), LONG_TIMEOUT);
            Assert.assertNotNull("Group 1 isn't found to be expanded", group1);
            group1.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            group1.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            UiObject2 group3 = mDevice.wait(Until.findObject(By.res(
                    RES_PACKAGE_NAME, "text1").text("Group 3")), LONG_TIMEOUT);
            Assert.assertNotNull("Group 3 isn't found to be expanded", group3);
            group3.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            group3.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            UiObject2 group4 = mDevice.wait(Until.findObject(By.res(
                    RES_PACKAGE_NAME, "text1").text("Group 4")), LONG_TIMEOUT);
            Assert.assertNotNull("Group 4 isn't found to be expanded", group4);
            group4.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            group4.click();
            SystemClock.sleep(SHORT_TIMEOUT);
            UiObject2 content = mDevice.wait(Until.findObject(By.res(
                    RES_PACKAGE_NAME, "content")), LONG_TIMEOUT);
            Assert.assertNotNull("Content pane isn't found to move up", content);
            content.fling(Direction.UP);
            SystemClock.sleep(SHORT_TIMEOUT);
        }
    }
}
