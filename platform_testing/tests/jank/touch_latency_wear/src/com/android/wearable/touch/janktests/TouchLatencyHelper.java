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

package com.android.wearable.touch.janktests;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import junit.framework.Assert;

/**
 * Helper for all the system apps jank tests
 */
public class TouchLatencyHelper {

    private static final String LOG_TAG = TouchLatencyHelper.class.getSimpleName();
    private static final String ACTION_SETTINGS_ID = "action_settings";
    private static final String HOME_INDICATOR = "charging_icon";
    private static final String LAUNCHER_VIEW_NAME = "launcher_view";
    private static final String CARD_VIEW_NAME = "activity_view";
    private static final String QUICKSETTING_VIEW_NAME = "settings_icon";
    private static final String WATCHFACE_PREVIEW_NAME = "preview_image";

    public static final int LONG_TIMEOUT_MS = 5000;
    public static final int SHORT_TIMEOUT_MS = 500;
    public static final int EXPECTED_FRAMES = 100;
    public static final String TOUCH_LATENCY_PKG = "com.prefabulated.touchlatency";

    private static TouchLatencyHelper touchLatencyHelper;
    private UiDevice mDevice = null;
    private Instrumentation mInstrumentation = null;

    /**
     * @param mDevice Instance to represent the current device.
     * @param instrumentation Instance for instrumentation.
     */
    private TouchLatencyHelper(UiDevice mDevice, Instrumentation instrumentation) {
        this.mDevice = mDevice;
        this.mInstrumentation = instrumentation;
    }

    public static TouchLatencyHelper getInstance(UiDevice device, Instrumentation instrumentation) {
        if (touchLatencyHelper == null) {
            touchLatencyHelper = new TouchLatencyHelper(device, instrumentation);
        }
        return touchLatencyHelper;
    }

    public void openBouncingBallActivity() {
        launchActivityFromLauncher(TOUCH_LATENCY_PKG);
        UiObject2 actionSettings = mDevice.wait(Until.findObject(
                By.res(TOUCH_LATENCY_PKG, ACTION_SETTINGS_ID)), LONG_TIMEOUT_MS);
        Assert.assertNotNull("Touch latency activity not started.", actionSettings);
        actionSettings.click();
    }

    private void launchActivityFromLauncher(String appPackage) {
        Intent intent = mInstrumentation.getContext()
                .getPackageManager().getLaunchIntentForPackage(appPackage);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mInstrumentation.getContext().startActivity(intent);
    }

    // Helper method to go back to home screen
    public void goBackHome() {
        int count = 0;
        do {
            UiObject2 homeScreen = waitForSysAppUiObject2(HOME_INDICATOR);
            if (homeScreen != null) {
                break;
            }
            mDevice.pressBack();
            count++;
        } while (count < 5);

        // TODO (yuanlang@) Delete the following hacky codes after charging icon issue fixed
        // Make sure we're not in the launcher
        if (waitForSysAppUiObject2(LAUNCHER_VIEW_NAME) != null) {
            mDevice.pressBack();
        }
        // Make sure we're not in cards view
        if (waitForSysAppUiObject2(CARD_VIEW_NAME) != null) {
            mDevice.pressBack();
        }
        // Make sure we're not in the quick settings
        if (waitForSysAppUiObject2(QUICKSETTING_VIEW_NAME) != null) {
            mDevice.pressBack();
        }
        // Make sure we're not in watch face picker
        if (waitForSysAppUiObject2(WATCHFACE_PREVIEW_NAME) != null) {
            mDevice.pressBack();
        }
        SystemClock.sleep(LONG_TIMEOUT_MS);
    }

    public UiObject2 waitForSysAppUiObject2(String resourceId) {
        String launcherPackageName = mDevice.getLauncherPackageName();
        return mDevice.wait(
                Until.findObject(By.res(launcherPackageName, resourceId)),
                SHORT_TIMEOUT_MS);
    }

}
