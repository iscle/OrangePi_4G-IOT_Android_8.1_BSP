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

package com.android.wearable.sysapp.janktests;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.KeyEvent;
import junit.framework.Assert;

/**
 * Helper for all the system apps jank tests
 */
public class SysAppTestHelper {

    public static final int EXPECTED_FRAMES_CARDS_TEST = 20;
    public static final int EXPECTED_FRAMES_DISMISS_EXPANDED_CARDS_TEST = 15;
    public static final int EXPECTED_FRAMES_WATCHFACE_PICKER_TEST = 20;
    public static final int EXPECTED_FRAMES_SWIPERIGHT_TO_DISMISS_TEST = 20;
    public static final int EXPECTED_FRAMES_WATCHFACE_PICKER_TEST_ADD_FAVORITE = 5;
    public static final int EXPECTED_FRAMES = 100;
    public static final int LONG_TIMEOUT = 5000;
    public static final int SHORT_TIMEOUT = 500;
    public static final int FLING_SPEED = 5000;
    private static final String LOG_TAG = SysAppTestHelper.class.getSimpleName();
    private static final String RELOAD_NOTIFICATION_CARD_INTENT = "com.google.android.wearable."
            + "support.wearnotificationgenerator.SHOW_NOTIFICATION";
    private static final String HOME_INDICATOR = "charging_icon";
    private static final String NO_NOTIFICATION_ID = "no_notifications";
    private static final String STREAM_CARD_ID = "stream_card";

    private static SysAppTestHelper sysAppTestHelperInstance;
    private UiDevice mDevice = null;
    private Instrumentation instrumentation = null;
    private Intent mIntent = null;

    /**
     * @param mDevice Instance to represent the current device.
     * @param instrumentation Instance for instrumentation.
     */
    private SysAppTestHelper(UiDevice mDevice, Instrumentation instrumentation) {
        super();
        this.mDevice = mDevice;
        this.instrumentation = instrumentation;
        mIntent = new Intent();
    }

    public static SysAppTestHelper getInstance(UiDevice device, Instrumentation instrumentation) {
        if (sysAppTestHelperInstance == null) {
            sysAppTestHelperInstance = new SysAppTestHelper(device, instrumentation);
        }
        return sysAppTestHelperInstance;
    }

    // TODO: Cleanup confusion between swipe and fling.
    public void swipeRight() {
        mDevice.swipe(50,
                mDevice.getDisplayHeight() / 2, mDevice.getDisplayWidth() - 25,
                mDevice.getDisplayHeight() / 2, 30); // slow speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void swipeLeft() {
        mDevice.swipe(mDevice.getDisplayWidth() - 50, mDevice.getDisplayHeight() / 2, 50,
                mDevice.getDisplayHeight() / 2, 30); // slow speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void swipeUp() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2 + 50,
                mDevice.getDisplayWidth() / 2, 0, 30); // slow speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void swipeDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, 0, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2 + 50, 30); // slow speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    // TODO: Cleanup confusion between swipe and fling.
    public void flingLeft() {
        mDevice.swipe(mDevice.getDisplayWidth() - 50, mDevice.getDisplayHeight() / 2,
                50, mDevice.getDisplayHeight() / 2, 5); // fast speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void flingRight() {
        mDevice.swipe(50, mDevice.getDisplayHeight() / 2,
                mDevice.getDisplayWidth() - 50, mDevice.getDisplayHeight() / 2, 5); // fast speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void flingUp() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2 + 50,
                mDevice.getDisplayWidth() / 2, 0, 5); // fast speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void flingDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2, 0, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2 + 50, 5); // fast speed
        SystemClock.sleep(SHORT_TIMEOUT);
    }

    public void clickScreenCenter() {
        mDevice.click(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2);
        SystemClock.sleep(SHORT_TIMEOUT);
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

        SystemClock.sleep(LONG_TIMEOUT);
    }

    // Helper method to verify if there are any Demo cards.

    // TODO: Allow user to pass in how many cards are expected to find cause some tests may require
    // more than one card.
    public void hasDemoCards() {
        // Device should be pre-loaded with demo cards.

        goBackHome();

        // Swipe up to go to notification tray.
        swipeUp();

        if (waitForSysAppUiObject2(NO_NOTIFICATION_ID) != null) {
            Log.d(LOG_TAG, "No cards, going to reload the cards");
            // If there are no Demo cards, reload them.
            goBackHome();
            reloadDemoCards();
        }
        else if (waitForSysAppUiObject2(STREAM_CARD_ID) != null){
            goBackHome();
        }
        else {
            Assert.fail("Swipe up failed to go to notification tray.");
        }
    }

    // This will ensure to reload notification cards by launching NotificationsGeneratorWear app
    // when there are insufficient cards.
    private void reloadDemoCards() {
        mIntent.setAction(RELOAD_NOTIFICATION_CARD_INTENT);
        instrumentation.getContext().sendBroadcast(mIntent);
        SystemClock.sleep(LONG_TIMEOUT);
    }

    public void launchActivity(String appPackage, String activityToLaunch) {
        mIntent.setAction("android.intent.action.MAIN");
        mIntent.setComponent(new ComponentName(appPackage, activityToLaunch));
        mIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.getContext().startActivity(mIntent);
    }

    // Helper method to goto app launcher and verifies you are there.
    public void gotoAppLauncher() {
        goBackHome();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_BACK);
        UiObject2 appLauncher = mDevice.wait(Until.findObject(By.text("Agenda")),
                SysAppTestHelper.LONG_TIMEOUT);
        Assert.assertNotNull("App launcher not launched", appLauncher);
    }

    public UiObject2 waitForSysAppUiObject2(String resourceId) {
        String launcherPackageName = mDevice.getLauncherPackageName();
        return mDevice.wait(
                Until.findObject(By.res(launcherPackageName, resourceId)),
                SHORT_TIMEOUT);
    }
}
