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
package com.android.cts.managedprofile;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;

public class ResetPasswordWithTokenTest extends BaseManagedProfileTest {

    private static final String PASSWORD0 = "1234";
    // This needs to be in sync with ManagedProfileTest.RESET_PASSWORD_TEST_DEFAULT_PASSWORD
    private static final String PASSWORD1 = "123456";

    private static final byte[] token = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();

    /**
     * A dummy receiver marked as direct boot aware in manifest to make this test app
     * runnable by instrumentation before FBE unlock.
     */
    public static class DummyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
        }
    }

    /**
     * Set a reset password token and work challenge on the work profile, and then lock it
     * with CE evicted. This is the preparation step for {@link #testResetPasswordBeforeUnlock}
     * to put the profile in RUNNING_LOCKED state, and will be called by the hostside logic before
     * {@link #testResetPasswordBeforeUnlock} is exercised.
     */
    public void testSetupWorkProfileAndLock() {
        testSetResetPasswordToken();
        // Reset password on the work profile will enable separate work challenge for it.
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, PASSWORD0,
                token, 0));

        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 6);

        testLockWorkProfile();
    }

    public void testResetPasswordBeforeUnlock() {
        UserManager um = mContext.getSystemService(UserManager.class);
        assertFalse(um.isUserUnlocked());
        assertTrue(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, PASSWORD1,
                token, 0));
        try {
            mDevicePolicyManager.isActivePasswordSufficient();
            fail("Did not throw expected exception.");
        } catch (IllegalStateException expected) {}
    }

    public void testSetResetPasswordToken() {
        assertTrue(mDevicePolicyManager.setResetPasswordToken(ADMIN_RECEIVER_COMPONENT, token));
        assertTrue(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));
    }

    public void testLockWorkProfile() {
        mDevicePolicyManager.lockNow(DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY);
    }
}