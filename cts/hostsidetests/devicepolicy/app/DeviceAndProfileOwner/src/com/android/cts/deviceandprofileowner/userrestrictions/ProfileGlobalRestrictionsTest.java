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
package com.android.cts.deviceandprofileowner.userrestrictions;

import static android.os.UserManager.ENSURE_VERIFY_APPS;

import com.android.cts.deviceandprofileowner.BaseDeviceAdminTest;

public class ProfileGlobalRestrictionsTest extends BaseDeviceAdminTest {
    private void assertRestriction(String restriction, boolean expected) {
        assertEquals("Wrong restriction value",
                expected, mUserManager.hasUserRestriction(restriction));
    }

    public void testSetProfileGlobalRestrictions() throws Exception {
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, ENSURE_VERIFY_APPS);
    }

    public void testClearProfileGlobalRestrictions() throws Exception  {
        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, ENSURE_VERIFY_APPS);
    }

    public void testProfileGlobalRestrictionsEnforced() {
        assertRestriction(ENSURE_VERIFY_APPS, true);
    }

    public void testProfileGlobalRestrictionsNotEnforced() {
        assertRestriction(ENSURE_VERIFY_APPS, false);
    }
}