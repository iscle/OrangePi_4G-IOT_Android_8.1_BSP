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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

public class ClearProfileOwnerNegativeTest extends AndroidTestCase {

    private DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (mDevicePolicyManager.isProfileOwnerApp(BaseDeviceAdminTest.PACKAGE_NAME)) {
            try {
                mDevicePolicyManager.clearProfileOwner(BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT);
            } catch (SecurityException e) {
                MoreAsserts.assertContainsRegex("clear profile owner", e.getMessage());
            }
        }

        super.tearDown();
    }

    public void testClearProfileOwnerNegative() {
    }
}
