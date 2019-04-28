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

package com.android.wearable.uibench.janktests;

import android.R;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.Until;

import java.io.IOException;

import junit.framework.Assert;

/**
 * Jank benchmark tests helper for UiBench app
 */

public class UiBenchJankTestsHelper {
    public static final int LONG_TIMEOUT = 5000;
    public static final int TIMEOUT = 250;
    public static final int SHORT_TIMEOUT = 250;
    public static final int INNER_LOOP = 3;
    public static final int EXPECTED_FRAMES = 100;
    public static final int CW_FLING_RATE = 5000;

    public static final String LOG_TAG = "UiBenchJankTestsHelper";
    public static final String RES_PACKAGE_NAME = "android";
    public static final String PACKAGE_NAME = "com.android.test.uibench";
    public static final String ROOT_NAME = "root";
    public static final String LAUNCHER_VIEW_NAME = "launcher_view";
    public static final String TEXT_OBJECT_NAME = "text1";
    public static final String UIBENCH_OBJECT_NAME = "UiBench";
    public static final String KEYBOARD_SERVICE_NAME = "com.google.android.inputmethod.latin/"
            + "com.google.android.apps.inputmethod.wear.latin.WearLatinIME";

    private static UiBenchJankTestsHelper mInstance;
    private UiDevice mDevice;
    private Context mContext;

    private UiBenchJankTestsHelper(UiDevice device, Context context) {
        mDevice = device;
        mContext = context;
    }

    public static UiBenchJankTestsHelper getInstance(UiDevice device) {
        return new UiBenchJankTestsHelper(device, null);
    }

    public static UiBenchJankTestsHelper getInstance(UiDevice device, Context context) {
        if (mInstance == null) {
            mInstance = new UiBenchJankTestsHelper(device, context);
        }
        return mInstance;
    }

    // Launch UiBench app
    public void launchUiBench() {
        Intent intent = mContext.getPackageManager()
                .getLaunchIntentForPackage(PACKAGE_NAME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mDevice.waitForIdle();
        // ensure test starts from home despite last failed test left UiBench in weird state
        UiObject2 initScreen = mDevice.wait(Until.findObject(By.text(UIBENCH_OBJECT_NAME)), 2000);
        int counter = 3;
        while (initScreen == null && --counter > 0) {
            mDevice.pressBack();
            initScreen = mDevice.wait(Until.findObject(By.text(UIBENCH_OBJECT_NAME)), 2000);
        }
    }

    // Helper method to go back to home screen
    public void goBackHome() throws UiObjectNotFoundException {
        String launcherPackage = mDevice.getLauncherPackageName();
        UiObject2 homeScreen = mDevice.findObject(By.res(launcherPackage, ROOT_NAME));
        int count = 0;
        while (homeScreen == null && count < 5) {
            mDevice.pressBack();
            homeScreen = mDevice.findObject(By.res(launcherPackage, ROOT_NAME));
            count ++;
        }
        // Make sure we're not in the launcher
        homeScreen = mDevice.findObject(By.res(launcherPackage, LAUNCHER_VIEW_NAME));
        if (homeScreen != null) {
            mDevice.pressBack();
        }
    }

    public void openTextInList(String itemName) {
        int count = 0;
        UiObject2 component = mDevice.wait(Until.findObject(
                By.res(RES_PACKAGE_NAME, TEXT_OBJECT_NAME).text(itemName)), TIMEOUT);
        while (component == null && count < 5) {
            swipeDown();
            component = mDevice.wait(Until.findObject(
                    By.res(RES_PACKAGE_NAME, TEXT_OBJECT_NAME).text(itemName)), TIMEOUT);
            count ++;
        }
        while (component == null && count < 10) {
            swipeUp();
            component = mDevice.wait(Until.findObject(
                    By.res(RES_PACKAGE_NAME, TEXT_OBJECT_NAME).text(itemName)), TIMEOUT);
            count ++;
        }
        Assert.assertNotNull(itemName + ": isn't found", component);
        component.clickAndWait(Until.newWindow(), LONG_TIMEOUT);
        SystemClock.sleep(TIMEOUT);
    }

    public void swipeRight() {
        mDevice.swipe(50,
            mDevice.getDisplayHeight() / 2,
            mDevice.getDisplayWidth() - 50,
            mDevice.getDisplayHeight() / 2,
            40); // slow speed
    }

    public void swipeDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
            mDevice.getDisplayHeight() / 2 + 100,
            mDevice.getDisplayWidth() / 2,
            mDevice.getDisplayHeight() / 2 - 100,
            40); // slow speed
    }

    public void swipeUp() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
            mDevice.getDisplayHeight() / 2 - 50,
            mDevice.getDisplayWidth() / 2,
            mDevice.getDisplayHeight() / 2 + 100,
            40); // slow speed
    }

    // Helper method to turn on/off keyboard IME
    public void enableKeyboardIME(Boolean turnOn) {
        try {
            String cmd = null;
            if (turnOn) {
                cmd = "ime enable %s";
            } else {
                cmd = "ime disable %s";
            }
            mDevice.executeShellCommand(String.format(cmd, KEYBOARD_SERVICE_NAME));
        } catch (IOException e) {
            Log.w(LOG_TAG, String.format("Exception when toggling keyboard. %s", e.toString()));
        }
    }
}
