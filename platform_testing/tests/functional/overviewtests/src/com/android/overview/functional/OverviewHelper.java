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

import android.app.Instrumentation;
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
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import org.junit.Assert;

public class OverviewHelper {

    private static final int TIMEOUT = 3000;
    private static final String RECENTS = "com.android.systemui:id/recents_view";

    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private PackageManager mPackageManager;
    private Context mContext = null;

    public OverviewHelper(UiDevice device, Instrumentation inst) {
        this.mDevice = device;
        mInstrumentation = inst;
        mPackageManager = inst.getContext().getPackageManager();
    }

    public String getLauncherPackage() {
        return mDevice.getLauncherPackageName();
    }

    /*
     * Launches an app with the given package name
     * @param appPackageName Name of package to be launched
     */
    public void launchAppWithIntent(String appPackageName) {
        Intent appIntent = mPackageManager.getLaunchIntentForPackage(appPackageName);
        Assert.assertNotNull("App intent to launch package " + appPackageName +
                " is null, please check package name", appIntent);
        appIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getContext().startActivity(appIntent);
        // Adding a sleep here to wait for app to finish launching
        // This allows it to be present in the UI before moving on.
        SystemClock.sleep(TIMEOUT);
    }

    /*
     * Navigates to the recents screen
     * @returns recents object
     * @throws UiObjectNotFoundException
     */
    public UiObject2 navigateToRecents() throws Exception {
        mDevice.pressRecentApps();
        mDevice.waitForIdle();
        return mDevice.wait(Until.findObject(By.res(RECENTS)), TIMEOUT);
    }

    /*
     * Populates recents by launching six apps
     */
    public void populateRecents() {
        // We launch six apps, since five is the maximum number
        // of apps under Recents
        String[] appPackages = {"com.google.android.gm",
                "com.google.android.deskclock", "com.android.settings",
                "com.google.android.youtube", "com.google.android.contacts",
                "com.google.android.apps.maps"};
        for (String appPackage : appPackages) {
            launchAppWithIntent(appPackage);
        }
    }

    /*
     * Scrolls through given recents object to the top.
     * @param recents Recents object
     */
    public void scrollToTopOfRecents(UiObject2 recents) {
        Rect r = recents.getVisibleBounds();
        // decide the top & bottom edges for scroll gesture
        int top = r.top + r.height() / 4; // top edge = top + 25% height
        int bottom = r.bottom - 200; // bottom edge = bottom & shift up 200px
        mDevice.swipe(r.width() / 2, top, r.width() / 2, bottom, 5);
        mDevice.waitForIdle();
    }

    /*
     * Docks an app to the top half of the multiwindow screen
     * @param appPackageName name of app package
     * @param appName Name of app to verify on screen
     * @throws UiObjectNotFoundException, InterruptedException
     */
    public void dockAppToTopMultiwindowSlot(String appPackageName, String appName)
            throws Exception {
        mDevice.pressRecentApps();
        mDevice.waitForIdle();
        UiObject2 recentsView = mDevice.wait(Until.findObject
                (By.res("com.android.systemui:id/recents_view")),TIMEOUT);
        // Check if recents isn't already empty, if not, clear it.
        if (!mDevice.wait(Until.hasObject(By.text("No recent items")),TIMEOUT)) {
            scrollToTopOfRecents(recentsView);
            // click clear all
            UiObject2 clearAll = mDevice.wait(Until.findObject(By.text("CLEAR ALL")),TIMEOUT);
            if (!clearAll.equals(null)) {
                clearAll.click();
            }
            Thread.sleep(TIMEOUT);
        }
        // Open app
        launchAppWithIntent(appPackageName);
        // Go to overview
        mDevice.pressRecentApps();
        mDevice.waitForIdle();
        // Long press on app
        UiObject2 appObject = mDevice.wait(Until.findObject
                (By.desc(appName)),TIMEOUT);
        int yCoordinate = mDevice.getDisplayHeight() / 12;
        int xCoordinate = mDevice.getDisplayWidth() / 2;
        // Drag and drop the app object to the multiwindow area
        appObject.drag(new Point(xCoordinate, yCoordinate), 1000);
        // Adding a sleep to allow the drag and drop animation to finish.
        Thread.sleep(TIMEOUT);
        mDevice.click(mDevice.getDisplayHeight() / 4, mDevice.getDisplayWidth() / 2);
        Assert.assertTrue("App not correctly docked to top multiwindow slot",
                mDevice.wait(Until.hasObject(By.pkg(appPackageName)
                .res("android:id/content")), TIMEOUT));
    }

    /*
     * Docks two apps, one to the each half of the multiwindow screen
     * @param topAppPackageName name of app package for top half
     * @param topAppName Name of top app to verify on screen
     * @param bottomAppPackageName name of app package for bottom half
     * @throws UiObjectNotFoundException, InterruptedException
     */
    public void dockAppsToBothMultiwindowAreas(String topAppPackageName,
            String topAppName, String bottomAppPackageName) throws Exception {
        dockAppToTopMultiwindowSlot(topAppPackageName, topAppName);
        mDevice.pressHome();
        mDevice.waitForIdle();
        // After docking the top app, simply launching another app
        // will launch it in the bottom half in docked mode. This
        // results in two apps being docked to multiwindow.
        launchAppWithIntent(bottomAppPackageName);
    }

    /*
     * Undocks apps from multiwindow. Only the package for the upper app
     * is needed.
     * @param topAppPackageName name of app package for top half
     * @throws UiObjectNotFoundException, InterruptedException
     */
    public void undockAppFromMultiwindow(String topAppPackageName) throws Exception {
        mDevice.click(mDevice.getDisplayHeight() / 4, mDevice.getDisplayWidth() / 2);
        UiObject2 appArea = mDevice.wait(Until.findObject(By.pkg(topAppPackageName)
                .res("android:id/content")), TIMEOUT);
        Rect appBounds = appArea.getVisibleBounds();
        int xCoordinate = mDevice.getDisplayWidth() / 2;
        mDevice.drag(xCoordinate, appBounds.bottom, xCoordinate,
                mDevice.getDisplayHeight() - 120, 4);
        // Adding a sleep to allow the drag and drop animation to finish.
        Thread.sleep(TIMEOUT);
    }

}
