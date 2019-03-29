/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;

/**
 * Base class for profile-owner based tests.
 *
 * This class handles making sure that the test is the profile owner and that it has an active admin
 * registered, so that all tests may assume these are done.
 */
public class BaseManagedProfileTest extends InstrumentationTestCase {

    public static class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            BasicAdminReceiver.class.getPackage().getName(), BasicAdminReceiver.class.getName());

    protected DevicePolicyManager mDevicePolicyManager;
    protected DevicePolicyManager mParentDevicePolicyManager;
    protected Context mContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mParentDevicePolicyManager =
                mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);
        assertNotNull(mDevicePolicyManager);

        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue(mDevicePolicyManager.isProfileOwnerApp(
                ADMIN_RECEIVER_COMPONENT.getPackageName()));
        assertTrue(mDevicePolicyManager.isManagedProfile(ADMIN_RECEIVER_COMPONENT));
    }

    protected DevicePolicyManager getDevicePolicyManager(boolean isParent) {
        return isParent ? mParentDevicePolicyManager : mDevicePolicyManager;
    }
}
