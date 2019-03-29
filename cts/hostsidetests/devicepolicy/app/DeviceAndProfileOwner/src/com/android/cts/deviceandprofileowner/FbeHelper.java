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
package com.android.cts.deviceandprofileowner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.test.uiautomator.UiDevice;
import android.view.KeyEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to lock and unlock file-based encryption (FBE) in CTS tests.
 */
public class FbeHelper extends BaseDeviceAdminTest {

    private static final String NUMERIC_PASSWORD = "12345";

    private UiDevice mUiDevice;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        assertTrue("Only numerical password is allowed", NUMERIC_PASSWORD.matches("\\d+"));

        mUiDevice = UiDevice.getInstance(getInstrumentation());
        assertNotNull(mUiDevice);
    }

    /**
     * Set password to activate FBE.
     * <p>
     * <b>Note:</b> FBE is only locked after device reboot.
     */
    public void testSetPassword() {
        assertTrue("Failed to set password " + NUMERIC_PASSWORD,
                mDevicePolicyManager.resetPassword(NUMERIC_PASSWORD, 0));
    }

    /**
     * Unlock FBE by entering the password in the Keyguard UI. This method blocks until an
     * {@code ACTION_USER_UNLOCKED} intent is received within 1 minute. Otherwise the method fails.
     */
    public void testUnlockFbe() throws Exception {
        // Register receiver for FBE unlocking broadcast intent
        final CountDownLatch latch = new CountDownLatch(1);
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        // Unlock FBE
        enterPassword(NUMERIC_PASSWORD);

        // Wait for FBE to fully unlock
        assertTrue("Failed to dismiss keyguard", latch.await(1, TimeUnit.MINUTES));
    }

    private void enterPassword(String password) throws Exception {
        mUiDevice.wakeUp();
        mUiDevice.waitForIdle();
        mUiDevice.pressMenu();
        mUiDevice.waitForIdle();
        pressNumericKeys(password);
        mUiDevice.waitForIdle();
        mUiDevice.pressEnter();
        mUiDevice.waitForIdle();
    }

    private void pressNumericKeys(String numericKeys) {
        for (char key : numericKeys.toCharArray()) {
            if (key >= '0' && key <= '9') {
                mUiDevice.pressKeyCode(KeyEvent.KEYCODE_0 + key - '0');
            } else {
                throw new IllegalArgumentException(key + " is not a numeric key.");
            }
        }
    }
}
