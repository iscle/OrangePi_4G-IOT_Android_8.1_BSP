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
package com.android.cts.managedprofile;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;

/**
 * This test executes helper tasks as active device admin in the primary user.
 */
public class PrimaryUserAdminHelper extends AndroidTestCase {

    private DevicePolicyManager mDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDpm = (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    /**
     * Device admin can only be deactivated by itself and this test should be executed before the
     * device admin package can be uninstalled.
     */
    public void testClearDeviceAdmin() throws Exception {
        ComponentName cn = PrimaryUserDeviceAdmin.ADMIN_RECEIVER_COMPONENT;
        if (mDpm.isAdminActive(cn)) {
            mDpm.removeActiveAdmin(cn);
            // Wait until device admin is not active (with 2 minutes timeout).
            for (int i = 0; i < 2 * 60 && mDpm.isAdminActive(cn); i++) {
                Thread.sleep(1000);  // 1 second.
            }
        }
        assertFalse("Clear device admin failed", mDpm.isAdminActive(cn));
    }
}
