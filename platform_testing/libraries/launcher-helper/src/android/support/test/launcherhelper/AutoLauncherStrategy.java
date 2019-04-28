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
package android.support.test.launcherhelper;

import android.app.Instrumentation;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.system.helpers.CommandsHelper;

public class AutoLauncherStrategy  implements IAutoLauncherStrategy {

    private static final String LOG_TAG = AutoLauncherStrategy.class.getSimpleName();
    private static final String CAR_LENSPICKER = "com.android.support.car.lenspicker";

    private static final long APP_INIT_WAIT = 10000;

    //todo: Remove x and y axis and use resource ID's.
    private static final int FACET_APPS = 560;
    private static final int MAP_FACET = 250;
    private static final int DIAL_FACET = 380;
    private static final int HOME_FACET = 530;
    private static final int MEDIA_FACET = 680;
    private static final int SETTINGS_FACET = 810;

    private static final BySelector R_ID_LENSPICKER_PAGEDOWN =
            By.res(CAR_LENSPICKER, "page_down");

    protected UiDevice mDevice;
    private Instrumentation mInstrumentation;

    @Override
    public String getSupportedLauncherPackage() {
        return CAR_LENSPICKER;
    }

    @Override
    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    @Override
    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    @Override
    public void open() {

    }

    @Override
    public void openDialFacet() {
        CommandsHelper.getInstance(mInstrumentation).executeShellCommand(
                "input tap " + DIAL_FACET + " " + FACET_APPS);
    }

    @Override
    public  void openMediaFacet(String appName) {
        openApp(appName, MEDIA_FACET, FACET_APPS);
    }

    @Override
    public void openSettingsFacet(String appName) {
        openApp(appName, SETTINGS_FACET, FACET_APPS);
    }

    @Override
    public void openMapsFacet(String appName) {
        CommandsHelper.getInstance(mInstrumentation).executeShellCommand(
                "input tap " + MAP_FACET + " " + FACET_APPS);
    }

    @Override
    public void openHomeFacet() {
        CommandsHelper.getInstance(mInstrumentation).executeShellCommand(
                "input tap " + HOME_FACET + " " + FACET_APPS);
    }

    public void openApp(String appName, int x, int y) {
        do {
            CommandsHelper.getInstance(mInstrumentation).executeShellCommand(
                    "input tap " + x + " " + y);
        }
        while (!mDevice.hasObject(R_ID_LENSPICKER_PAGEDOWN));

        UiObject2 scrollContainer = mDevice.findObject(R_ID_LENSPICKER_PAGEDOWN);

        if (scrollContainer == null) {
            throw new UnsupportedOperationException("Cannot find scroll container");
        }

        if (!mDevice.hasObject(By.text(appName))) {
            do {
                scrollContainer.scroll(Direction.DOWN, 1.0f);
            }
            while (!mDevice.hasObject(By.text(appName)) && scrollContainer.isEnabled());
        }

        if (!scrollContainer.isEnabled()) {
            throw new UnsupportedOperationException("Unable to find application " + appName);
        }

        UiObject2 application = mDevice.wait(Until.findObject(By.text(appName)), APP_INIT_WAIT);
        if (application != null) {
            application.click();
            mDevice.waitForIdle();
        } else {
            throw new RuntimeException("Unable to find permission text ok button");
        }
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 openAllApps(boolean reset) {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllAppsButtonSelector() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllAppsSelector() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getAllAppsScrollDirection() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public UiObject2 openAllWidgets(boolean reset) {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getAllWidgetsSelector() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getAllWidgetsScrollDirection() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getWorkspaceSelector() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public BySelector getHotSeatSelector() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public Direction getWorkspaceScrollDirection() {
        throw new UnsupportedOperationException(
                "The feature not supported on Auto");
    }

    @SuppressWarnings("unused")
    @Override
    public long launch(String appName, String packageName) {
        return 0;
    }
}
