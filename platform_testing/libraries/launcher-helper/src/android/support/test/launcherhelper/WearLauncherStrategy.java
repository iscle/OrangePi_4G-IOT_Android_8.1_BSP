/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.support.test.launcherhelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.widget.TextView;
import android.util.Log;

import junit.framework.Assert;

public class WearLauncherStrategy implements ILauncherStrategy {

    private static final String LAUNCHER_PKG = "com.google.android.wearable.app";
    private static final String LOG_TAG = WearLauncherStrategy.class.getSimpleName();
    protected UiDevice mDevice;
    protected Context mContext;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSupportedLauncherPackage() {
        return LAUNCHER_PKG;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    /**
     * Shows the home screen of launcher
     */
    @Override
    public void open() {
        if (!mDevice.hasObject(getHotSeatSelector())) {
            mDevice.pressHome();
            if (!mDevice.wait(Until.hasObject(getHotSeatSelector()), 5000)) {
                dumpScreen("Return Watch face");
                Assert.fail("Failed to open launcher");
            }
            mDevice.waitForIdle();
        }
    }

    /**
     * Opens the all apps drawer of launcher
     * @param reset if the all apps drawer should be reset to the beginning
     * @return {@link UiObject2} representation of the all apps drawer
     */
    @Override
    public UiObject2 openAllApps(boolean reset) {
        if (!mDevice.hasObject(getAllAppsSelector())) {
            mDevice.pressHome();
            mDevice.waitForIdle();
            if (!mDevice.wait(Until.hasObject(getAllAppsSelector()), 5000)) {
                dumpScreen("Open launcher");
                Assert.fail("Failed to open launcher");
            }
        }
        UiObject2 allAppsContainer = mDevice.wait(Until.findObject(getAllAppsSelector()), 2000);
        if (reset) {
            CommonLauncherHelper.getInstance(mDevice).scrollBackToBeginning(
                    allAppsContainer, Direction.reverse(getAllAppsScrollDirection()));
        }
        if (allAppsContainer == null) {
            Assert.fail("Failed to find launcher");
        }
        return allAppsContainer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getHotSeatSelector() {
         return By.res(getSupportedLauncherPackage(), "watchface_overlay");
    }


    /**
     * Returns a {@link BySelector} describing the all apps drawer
     * @return
     */
    @Override
    public BySelector getAllAppsSelector() {
        return By.res(getSupportedLauncherPackage(), "launcher_view");
    }

    /**
     * Retrieves the all apps drawer forward scroll direction as implemented by the launcher
     * @return
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.DOWN;
    }

    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new UnsupportedOperationException(
                "The 'All Apps' button is not available on Android Wear Launcher.");
    }

    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public BySelector getAllWidgetsSelector() {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new UnsupportedOperationException(
                "The 'All Widgets' button is not available on Android Wear Launcher.");
    }

    @Override
    public BySelector getWorkspaceSelector() {
        throw new UnsupportedOperationException(
                "The 'Work space' is not available on Android Wear Launcher.");
    }

    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new UnsupportedOperationException(
                "The 'Work Space' is not available on Android Wear Launcher.");
    }

    /**
     * Launch the named application
     * @param appName the name of the application to launch as shown in launcher
     * @param packageName the expected package name to verify that the application has been launched
     *                    into foreground. If <code>null</code> is provided, no verification is
     *                    performed.
     * @return <code>true</code> if application is verified to be in foreground after launch, or the
     *   verification is skipped; <code>false</code> otherwise.
     */
    @Override
    public long launch(String appName, String packageName) {
        BySelector app = By.res(
                getSupportedLauncherPackage(), "title").clazz(TextView.class).text(appName);
        return CommonLauncherHelper.getInstance(mDevice).launchApp(this, app, packageName);
    }

    private void dumpScreen(String description) {
        // DEBUG: dump hierarchy to logcat
        Log.d(LOG_TAG, "Dump Screen at " + description);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()){
            mDevice.dumpWindowHierarchy(baos);
            baos.flush();
            String[] lines = baos.toString().split(System.lineSeparator());
            for (String line : lines) {
                Log.d(LOG_TAG, line.trim());
            }
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "error dumping XML to logcat", ioe);
        }
    }
}
