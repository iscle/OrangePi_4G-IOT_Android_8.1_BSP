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

package com.android.cts.comp.provisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.UserManager;
import android.test.AndroidTestCase;

import com.android.cts.comp.AdminReceiver;

public class UserRestrictionTest extends AndroidTestCase {

    public void testAddDisallowAddManagedProfileRestriction() {
        setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE, true);
    }

    public void testClearDisallowAddManagedProfileRestriction() {
        setUserRestriction(UserManager.DISALLOW_ADD_MANAGED_PROFILE, false);
    }

    public void testAddDisallowRemoveManagedProfileRestriction() {
        setUserRestriction(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE, true);
    }

    public void testClearDisallowRemoveManagedProfileRestriction() {
        setUserRestriction(UserManager.DISALLOW_REMOVE_MANAGED_PROFILE, false);
    }

    public void testAddDisallowRemoveUserRestriction() {
        setUserRestriction(UserManager.DISALLOW_REMOVE_USER, true);
    }

    public void testClearDisallowRemoveUserRestriction() {
        setUserRestriction(UserManager.DISALLOW_REMOVE_USER, false);
    }

    private void setUserRestriction(String restriction, boolean add) {
        DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);
        ComponentName admin = AdminReceiver.getComponentName(getContext());
        if (add) {
            dpm.addUserRestriction(admin, restriction);
        } else {
            dpm.clearUserRestriction(admin, restriction);
        }
    }
}
