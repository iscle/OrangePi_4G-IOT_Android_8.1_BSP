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
package android.support.test.launcherhelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.widget.TextView;
import junit.framework.Assert;

public abstract class BaseLauncher3Strategy implements ILauncherStrategy {
    private static final String LOG_TAG = BaseLauncher3Strategy.class.getSimpleName();
    protected UiDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open() {
        // if we see hotseat, assume at home screen already
        if (!mDevice.hasObject(getHotSeatSelector())) {
            mDevice.pressHome();
            // ensure launcher is shown
            if (!mDevice.wait(Until.hasObject(getHotSeatSelector()), 5000)) {
                // HACK: dump hierarchy to logcat
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    mDevice.dumpWindowHierarchy(baos);
                    baos.flush();
                    baos.close();
                    String[] lines = baos.toString().split("\\r?\\n");
                    for (String line : lines) {
                        Log.d(LOG_TAG, line.trim());
                    }
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "error dumping XML to logcat", ioe);
                }
                Assert.fail("Failed to open launcher");
            }
            mDevice.waitForIdle();
        }
        dismissHomeScreenCling();
    }

    /**
     * Checks and dismisses home screen cling
     */
    protected void dismissHomeScreenCling() {
        // empty default implementation
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 openAllApps(boolean reset) {
        // if we see all apps container, skip the opening step
        if (!mDevice.hasObject(getAllAppsSelector())) {
            open();
            // taps on the "apps" button at the bottom of the screen
            UiObject2 allAppsButton =
                    mDevice.wait(Until.findObject(getAllAppsButtonSelector()), 2000);
            Assert.assertNotNull("openAllApps: did not find open all apps button.", allAppsButton);
            allAppsButton.click();
            // wait until hotseat disappears, so that we know that we are no longer on home screen
            mDevice.wait(Until.gone(getHotSeatSelector()), 2000);
            mDevice.waitForIdle();
        }
        UiObject2 allAppsContainer = mDevice.wait(Until.findObject(getAllAppsSelector()), 2000);
        Assert.assertNotNull("openAllApps: did not find all apps container", allAppsContainer);
        if (reset) {
            CommonLauncherHelper.getInstance(mDevice).scrollBackToBeginning(
                    allAppsContainer, Direction.reverse(getAllAppsScrollDirection()));
        }
        return allAppsContainer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getAllAppsScrollDirection() {
        return Direction.DOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        if (!mDevice.hasObject(getAllWidgetsSelector())) {
            open();
            // trigger the wallpapers/widgets/settings view
            mDevice.pressMenu();
            mDevice.waitForIdle();
            mDevice.findObject(By.res(getSupportedLauncherPackage(), "widget_button")).click();
        }
        UiObject2 allWidgetsContainer = mDevice.wait(
                Until.findObject(getAllWidgetsSelector()), 2000);
        Assert.assertNotNull("openAllWidgets: did not find all widgets container",
                allWidgetsContainer);
        if (reset) {
            CommonLauncherHelper.getInstance(mDevice).scrollBackToBeginning(
                    allWidgetsContainer, Direction.reverse(getAllWidgetsScrollDirection()));
        }
        return allWidgetsContainer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getAllWidgetsScrollDirection() {
        return Direction.DOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long launch(String appName, String packageName) {
        BySelector app = By.res(
                getSupportedLauncherPackage(), "icon").clazz(TextView.class).desc(appName);
        return CommonLauncherHelper.getInstance(mDevice).launchApp(this, app, packageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAllAppsSelector() {
        return By.res(getSupportedLauncherPackage(), "apps_list_view");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getAllWidgetsSelector() {
        return By.res(getSupportedLauncherPackage(), "widgets_list_view");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getWorkspaceSelector() {
        return By.res(getSupportedLauncherPackage(), "workspace");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BySelector getHotSeatSelector() {
        return By.res(getSupportedLauncherPackage(), "hotseat");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Direction getWorkspaceScrollDirection() {
        return Direction.RIGHT;
    }
}
