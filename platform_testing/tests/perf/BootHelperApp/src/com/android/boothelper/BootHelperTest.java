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

package com.android.boothelper;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.KeyEvent;

import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;



public class BootHelperTest {

    private static final long TIMEOUT = 10000;
    private static final String TAG = "BootHelperTest";
    private static final String SETTINGS_PKG = "com.android.settings";
    private static final String LOCK_PIN_ID = "lock_pin";
    private static final String REQUIRE_PWD_ID = "encrypt_dont_require_password";
    private static final String PWD_ENTRY = "password_entry";
    private UiDevice mDevice;
    private Context mProtectedContext;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mProtectedContext = getInstrumentation().getContext()
                .createDeviceProtectedStorageContext();
    }

    @Test
    public void setupLockScreenPin() throws Exception {
        Activity activity = launchActivity(getInstrumentation().getTargetContext()
                .getPackageName(), AwareActivity.class, new Intent(Intent.ACTION_MAIN));
        mDevice.waitForIdle();

        // Set a PIN for this user
        final Intent intent = new Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        activity.startActivity(intent);
        mDevice.waitForIdle();

        // Pick PIN from the option list
        selectOption(LOCK_PIN_ID);

        // Ignore any interstitial options
        selectOption(REQUIRE_PWD_ID);

        // Set our PIN
        selectOption(PWD_ENTRY);

        // Enter it twice to confirm
        enterTestPin();
        enterTestPin();
        mDevice.pressBack();

    }

    @Test
    public void unlockScreenWithPin() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mProtectedContext.registerReceiver(receiver, new IntentFilter(
                Intent.ACTION_USER_UNLOCKED));
        dismissKeyguard();
    }

    private void dismissKeyguard() throws Exception {
        mDevice.wakeUp();
        mDevice.waitForIdle();
        mDevice.pressMenu();
        mDevice.waitForIdle();
        enterTestPin();
    }

    private void enterTestPin() throws Exception {
        mDevice.waitForIdle();
        mDevice.pressKeyCode(KeyEvent.KEYCODE_1);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_2);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_3);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_4);
        mDevice.pressKeyCode(KeyEvent.KEYCODE_5);
        mDevice.waitForIdle();
        mDevice.pressEnter();
        Log.i(TAG, "Screen Unlocked");
        mDevice.waitForIdle();
    }

    /**
     * Return the instrumentation from the registry.
     *
     * @return
     */
    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /**
     * Click on the option based on the resource id in the settings package.
     *
     * @param optionId
     */
    public void selectOption(String optionId) {
        UiObject2 tos = mDevice.wait(Until.findObject(By.res(SETTINGS_PKG, optionId)),
                TIMEOUT);
        if (tos != null) {
            tos.click();
        }
    }

    /**
     * To launch an activity
     * @param pkg
     * @param activityCls
     * @param intent
     * @return
     */
    public Activity launchActivity(String pkg, Class activityCls, Intent intent) {
        intent.setClassName(pkg, activityCls.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return getInstrumentation().startActivitySync(intent);
    }


}
