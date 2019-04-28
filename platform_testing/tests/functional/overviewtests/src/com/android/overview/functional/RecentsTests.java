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
import android.view.KeyEvent;

public class RecentsTests extends InstrumentationTestCase {

    private static final int TIMEOUT = 3000;
    private static final String RECENTS = "com.android.systemui:id/recents_view";
    private UiDevice mDevice;
    private PackageManager mPackageManager;
    private ILauncherStrategy mLauncherStrategy = null;
    private OverviewHelper mOverviewHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
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

    @MediumTest
    public void testNavigateToRecents() throws Exception {
        UiObject2 recents = mOverviewHelper.navigateToRecents();
        assertNotNull("Recents view not reached on tapping recents", recents);
    }

    @MediumTest
    public void testAddAndDismissItemInRecents() throws Exception {
        mOverviewHelper.launchAppWithIntent("com.google.android.calculator");
        mOverviewHelper.navigateToRecents();
        UiObject2 dismissCalculator = mDevice.wait(Until.findObject
                (By.desc("Dismiss Calculator.")),TIMEOUT);
        assertNotNull("Calculator not found in Recents", dismissCalculator);
        dismissCalculator.click();
        mDevice.waitForIdle();
        // Adding an extra sleep here so UiAutomator picks up
        // the refreshed UI and not the old one, otherwise the
        // test fails because it thinks the 'Dismiss Calculator'
        // button is still present.
        Thread.sleep(TIMEOUT);
        UiObject2 afterDismissCalculator = mDevice.wait(Until.findObject
                (By.desc("Dismiss Calculator.")),TIMEOUT);
        assertNull("Calculator not dismissed from Recents", afterDismissCalculator);
    }

    @MediumTest
    public void testScrollThroughRecents() throws Exception {
        mOverviewHelper.populateRecents();
        mOverviewHelper.navigateToRecents();
        UiObject2 recentsView = mDevice.wait(Until.findObject
                (By.res(RECENTS)),TIMEOUT);
        mOverviewHelper.scrollToTopOfRecents(recentsView);
        // After scrolling we look for the 'Clear All' button on the top
        // right. This ensures a successful scroll.
        UiObject2 clearAll = mDevice.wait(Until.findObject
                (By.text("CLEAR ALL")),TIMEOUT);
        assertNotNull("Unable to scroll to top of recents", clearAll);
        clearAll.click();
        Thread.sleep(TIMEOUT);
    }

    @MediumTest
    public void testSwipeItemAwayFromRecents() throws Exception {
        mOverviewHelper.launchAppWithIntent("com.google.android.calculator");
        Thread.sleep(TIMEOUT);
        mOverviewHelper.navigateToRecents();
        UiObject2 calculatorText = mDevice.wait(Until.findObject
                (By.desc("Calculator")),TIMEOUT);
        // Get the bounds of the text 'Calculator' which is
        // easily identifiable and swipe all the way from the
        // left to the right to dismiss.
        Rect calcBounds = calculatorText.getVisibleBounds();
        mDevice.swipe(calcBounds.left, calcBounds.top + calcBounds.height() / 2,
                calcBounds.right, calcBounds.top + calcBounds.height() / 2, 5);
        // Try to refetch Calculator text
        calculatorText = mDevice.wait(Until.findObject
                (By.desc("Calculator")),TIMEOUT);
        assertNull("Calculator app still present after swiping away",
                calculatorText);
    }

    @MediumTest
    public void testClearAllFromRecents() throws Exception {
        mOverviewHelper.populateRecents();
        mOverviewHelper.navigateToRecents();
        UiObject2 recentsView = mDevice.wait(Until.findObject
                            (By.res(RECENTS)),TIMEOUT);
        // fling to top
        mOverviewHelper.scrollToTopOfRecents(recentsView);
        // click clear all
        UiObject2 clearAll = mDevice.wait(Until.findObject
                (By.text("CLEAR ALL")),TIMEOUT);
        clearAll.click();
        Thread.sleep(TIMEOUT);
        mOverviewHelper.navigateToRecents();
        // verify empty recents list
        assertTrue("Recent items not empty", mDevice.wait
                (Until.hasObject(By.text("No recent items")), TIMEOUT));
    }

    @MediumTest
    public void testDoubleTapToSwitchRecents() throws Exception {
        mOverviewHelper.launchAppWithIntent("com.google.android.calculator");
        mOverviewHelper.launchAppWithIntent("com.google.android.gm");
        // Literally tapping twice as there's no 'double tap'
        // method.
        mDevice.pressRecentApps();
        mDevice.pressRecentApps();
        mDevice.waitForIdle();
        // Verify that the app has switched to calculator after
        // the double tap
        UiObject2 calculatorText = mDevice.wait(Until.findObject
                (By.desc("Calculator")),TIMEOUT);
    }
}
