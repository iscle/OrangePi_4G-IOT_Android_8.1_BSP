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

package android.platform.systemui.tests.jank;

import java.io.File;
import java.io.IOException;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Environment;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTest;
import android.support.test.jank.JankTestBase;
import android.support.test.jank.WindowAnimationFrameStatsMonitor;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.timeresulthelper.TimeResultLogger;
import android.os.Bundle;

/*
 * LauncherTwoJankTests cover the old launcher, and
 * LauncherJankTests cover the new GEL Launcher.
 */
public class LauncherJankTests extends JankTestBase {

    private static final int TIMEOUT = 5000;
    // short transitions should be repeated within the test function, otherwise frame stats
    // captured are not really meaningful in a statistical sense
    private static final int INNER_LOOP = 3;
    private static final int FLING_SPEED = 12000;
    private UiDevice mDevice;
    private PackageManager pm;
    private ILauncherStrategy mLauncherStrategy = null;
    private static final File TIMESTAMP_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(),"autotester.log");
    private static final File RESULTS_FILE = new File(Environment.getExternalStorageDirectory()
            .getAbsolutePath(),"results.log");

    @Override
    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());
        pm = getInstrumentation().getContext().getPackageManager();
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientaion", e);
        }
        mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    public void goHome() throws UiObjectNotFoundException {
        mLauncherStrategy.open();
    }

    public void resetAllApps() throws UiObjectNotFoundException {
        mLauncherStrategy.openAllApps(true);
        mLauncherStrategy.open();
    }

    public void prepareOpenAllAppsContainer() throws IOException {
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterTestOpenAllAppsContainer(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Starts from the home screen, and measures jank while opening the all apps container. */
    @JankTest(expectedFrames=100, beforeTest="prepareOpenAllAppsContainer",
            beforeLoop="resetAllApps", afterTest="afterTestOpenAllAppsContainer")
    @GfxMonitor(processName="#getLauncherPackage")
    public void testOpenAllAppsContainer() throws UiObjectNotFoundException {
        for (int i = 0; i < INNER_LOOP * 2; i++) {
            mLauncherStrategy.openAllApps(false);
            mDevice.waitForIdle();
            mLauncherStrategy.open();
            mDevice.waitForIdle();
        }
    }

    public void openAllApps() throws UiObjectNotFoundException, IOException {
        mLauncherStrategy.openAllApps(false);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterTestAllAppsContainerSwipe(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Starts from the all apps container, and measures jank while swiping between pages */
    @JankTest(beforeTest="openAllApps", afterTest="afterTestAllAppsContainerSwipe",
            expectedFrames=100)
    @GfxMonitor(processName="#getLauncherPackage")
    public void testAllAppsContainerSwipe() {
        UiObject2 allApps = mDevice.findObject(mLauncherStrategy.getAllAppsSelector());
        allApps.setGestureMargin(150);
        Direction dir = mLauncherStrategy.getAllAppsScrollDirection();
        for (int i = 0; i < INNER_LOOP * 2; i++) {
            allApps.fling(dir, FLING_SPEED);
            allApps.fling(Direction.reverse(dir), FLING_SPEED);
        }
    }

    public void makeHomeScrollable() throws UiObjectNotFoundException, IOException {
        mLauncherStrategy.open();
        UiObject2 homeScreen = mDevice.findObject(mLauncherStrategy.getWorkspaceSelector());
        Rect r = homeScreen.getVisibleBounds();
        if (!homeScreen.isScrollable()) {
            // Add the Chrome icon to the first launcher screen.
            // This is specifically for Bullhead, where you can't add an icon
            // to the second launcher screen without one on the first.
            UiObject2 chrome = mDevice.findObject(By.text("Chrome"));
            Point dest = new Point(mDevice.getDisplayWidth()/2, r.centerY());
            chrome.drag(dest, 2000);
            // Drag Camera icon to next screen
            UiObject2 camera = mDevice.findObject(By.text("Camera"));
            dest = new Point(mDevice.getDisplayWidth(), r.centerY());
            camera.drag(dest, 2000);
        }
        mDevice.waitForIdle();
        assertTrue("home screen workspace still not scrollable", homeScreen.isScrollable());
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterTestHomeScreenSwipe(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Starts from the home screen, and measures jank while swiping between pages */
    @JankTest(beforeTest="makeHomeScrollable", afterTest="afterTestHomeScreenSwipe",
              expectedFrames=100)
    @GfxMonitor(processName="#getLauncherPackage")
    public void testHomeScreenSwipe() {
        UiObject2 workspace = mDevice.findObject(mLauncherStrategy.getWorkspaceSelector());
        Direction dir = mLauncherStrategy.getWorkspaceScrollDirection();
        for (int i = 0; i < INNER_LOOP * 2; i++) {
            workspace.fling(dir);
            workspace.fling(Direction.reverse(dir));
        }
    }

    public void openAllWidgets() throws UiObjectNotFoundException, IOException {
        mLauncherStrategy.openAllWidgets(true);
        TimeResultLogger.writeTimeStampLogStart(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
    }

    public void afterTestWidgetsContainerFling(Bundle metrics) throws IOException {
        TimeResultLogger.writeTimeStampLogEnd(String.format("%s-%s",
                getClass().getSimpleName(), getName()), TIMESTAMP_FILE);
        TimeResultLogger.writeResultToFile(String.format("%s-%s",
                getClass().getSimpleName(), getName()), RESULTS_FILE, metrics);
        super.afterTest(metrics);
    }

    /** Starts from the widgets container, and measures jank while swiping between pages */
    @JankTest(beforeTest="openAllWidgets", afterTest="afterTestWidgetsContainerFling",
              expectedFrames=100)
    @GfxMonitor(processName="#getLauncherPackage")
    public void testWidgetsContainerFling() {
        UiObject2 allWidgets = mDevice.findObject(mLauncherStrategy.getAllWidgetsSelector());
        Direction dir = mLauncherStrategy.getAllWidgetsScrollDirection();
        for (int i = 0; i < INNER_LOOP; i++) {
            allWidgets.fling(dir, FLING_SPEED);
            allWidgets.fling(Direction.reverse(dir), FLING_SPEED);
        }
    }
}
