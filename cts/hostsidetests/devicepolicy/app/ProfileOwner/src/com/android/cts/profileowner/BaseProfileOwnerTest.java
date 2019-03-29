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
package com.android.cts.profileowner;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;

public abstract class BaseProfileOwnerTest extends AndroidTestCase {

    public static class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    public static final String PACKAGE_NAME = BaseProfileOwnerTest.class.getPackage().getName();

    protected DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertProfileOwner(mDevicePolicyManager);
    }

    static void assertProfileOwner(DevicePolicyManager dpm) {
        assertNotNull(dpm);
        assertTrue(dpm.isAdminActive(getWho()));
        assertTrue(dpm.isProfileOwnerApp(PACKAGE_NAME));
    }

    protected static ComponentName getWho() {
        return new ComponentName(PACKAGE_NAME, BasicAdminReceiver.class.getName());
    }
}
