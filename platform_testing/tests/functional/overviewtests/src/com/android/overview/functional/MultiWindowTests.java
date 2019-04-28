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

package android.overview.functional;

import java.io.File;
import java.io.IOException;

import android.app.UiAutomation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.KeyEvent;

public class MultiWindowTests extends InstrumentationTestCase {

    private static final int TIMEOUT = 3000;
    private static final String HOTSEAT = "hotseat";
    private static final String CALCULATOR_PACKAGE = "com.google.android.calculator";
    private static final String GMAIL_PACKAGE = "com.google.android.gm";
    private UiDevice mDevice;
    private PackageManager mPackageManager;
    private ILauncherStrategy mLauncherStrategy = null;
    private UiAutomation mUiAutomation = null;
    private OverviewHelper mOverviewHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mUiAutomation = getInstrumentation().getUiAutomation();
        mPackageManager = getInstrumentation().getContext().getPackageManager();
        mDevice.setOrientationNatural();
        mLauncherStrategy = LauncherStrategyFactory.getInstance(mDevice).getLauncherStrategy();
        mOverviewHelper = new OverviewHelper(mDevice, getInstrumentation());
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.pressHome();
        mDevice.unfreezeRotation();
        mDevice.waitForIdle();
        super.tearDown();
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    @MediumTest
    public void testDockingUndockingOnMultiwindow()  throws Exception {
        mOverviewHelper.dockAppToTopMultiwindowSlot(CALCULATOR_PACKAGE, "Calculator");
        // Click to check for split windows
        mDevice.click(mDevice.getDisplayHeight() * 3/4, mDevice.getDisplayWidth() / 2);
        assertTrue("Bottom half of screen doesn't have empty recents list",
                mDevice.wait(Until.hasObject(By.text("No recent items")), TIMEOUT));
        // Calculate midpoint for Calculator window and click
        mDevice.click(mDevice.getDisplayHeight() / 4, mDevice.getDisplayWidth() / 2);
        UiObject2 calcArea = mDevice.wait(Until.findObject(By.res("android:id/content")), TIMEOUT);
        assertNotNull("Top half of screen doesn't have Calculator", calcArea);
        mOverviewHelper.undockAppFromMultiwindow(CALCULATOR_PACKAGE);
        assertFalse("Calculator isn't full screen after dragging",
                mDevice.wait(Until.hasObject(By.text("No recent items")), TIMEOUT));
    }

    @MediumTest
    public void testResizeHandleOnMultiwindow() throws Exception {
        mOverviewHelper.dockAppsToBothMultiwindowAreas(CALCULATOR_PACKAGE, "Calculator",
                GMAIL_PACKAGE);
        // Adding a sleep here to make sure the test fetches the bounds of the
        // elements on the multiwindow screen instead of the home screen.
        Thread.sleep(TIMEOUT);
        // verify initial bounds for top and bottom
        mDevice.click(mDevice.getDisplayHeight() / 4, mDevice.getDisplayWidth() / 2);
        UiObject2 calcArea = mDevice.wait(Until.findObject
                (By.pkg(CALCULATOR_PACKAGE).res("android:id/content")), TIMEOUT);
        Rect initialCalcBounds = calcArea.getVisibleBounds();
        mDevice.click(mDevice.getDisplayHeight() * 3/4, mDevice.getDisplayWidth() / 2);
        UiObject2 gmailArea = mDevice.wait(Until.findObject
                (By.pkg(GMAIL_PACKAGE).res("android:id/content")), TIMEOUT);
        Rect initialGmailBounds = gmailArea.getVisibleBounds();
        // Move handle so Calculator occupies about 3/4 of the screen
        int xCoordinate = mDevice.getDisplayWidth() / 2;
        mDevice.drag(xCoordinate, initialCalcBounds.bottom,
                xCoordinate, mDevice.getDisplayHeight() * 3/4, 2);
        // Verify that final area for calculator is larger than initial
        calcArea = mDevice.wait(Until.findObject
                (By.pkg(CALCULATOR_PACKAGE).res("android:id/content")), TIMEOUT);
        Rect finalCalcBounds = calcArea.getVisibleBounds();
        mDevice.click(mDevice.getDisplayHeight() * 3/4, mDevice.getDisplayWidth() / 2);
        gmailArea = mDevice.wait(Until.findObject
                (By.pkg(GMAIL_PACKAGE).res("android:id/content")), TIMEOUT);
        Rect finalGmailBounds = gmailArea.getVisibleBounds();
        // Verify that final area for gmail is smaller than initial
        assertTrue("Calculator area not larger than before resize",
                finalCalcBounds.bottom > initialCalcBounds.bottom);
        assertTrue("GMail area not smaller than before resize",
                finalGmailBounds.top > initialGmailBounds.top);
        mOverviewHelper.undockAppFromMultiwindow(CALCULATOR_PACKAGE);
    }

    @MediumTest
    public void testLandscapeModeMultiwindow() throws Exception {
        mOverviewHelper.dockAppsToBothMultiwindowAreas(CALCULATOR_PACKAGE, "Calculator",
                GMAIL_PACKAGE);
        mDevice.setOrientationLeft();
        // Adding a sleep here as otherwise the test attempts to click too quickly
        // before the orientation has a chance to change.
        Thread.sleep(TIMEOUT);
        // Click on the left of the device
        mDevice.click(mDevice.getDisplayHeight() / 2, mDevice.getDisplayWidth() / 4);
        assertNotNull("Calculator not found on left in landscape mode",
                mDevice.wait(Until.hasObject
                (By.pkg(CALCULATOR_PACKAGE).res("android:id/content")), TIMEOUT));
        mDevice.click(mDevice.getDisplayHeight() / 2, mDevice.getDisplayWidth() * 3/4);
        assertNotNull("Gmail not found on right in landscape mode",
                mDevice.wait(Until.hasObject
                (By.pkg(GMAIL_PACKAGE).res("android:id/content")), TIMEOUT));
        mDevice.setOrientationNatural();
        mOverviewHelper.undockAppFromMultiwindow(CALCULATOR_PACKAGE);
    }
}
