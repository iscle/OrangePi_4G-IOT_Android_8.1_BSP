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
package com.android.cts.comp.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.test.AndroidTestCase;

import com.android.compatibility.common.util.devicepolicy.provisioning.SilentProvisioningTestManager;
import com.android.cts.comp.AdminReceiver;

public class ManagedProfileProvisioningTest extends AndroidTestCase {
    private static final String TAG = "ManagedProfileProvisioningTest";

    public void testProvisioningCorpOwnedManagedProfile() throws Exception {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    AdminReceiver.getComponentName(getContext()))
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            // this flag for Corp owned only
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT, true);

        SilentProvisioningTestManager provisioningManager =
                new SilentProvisioningTestManager(getContext());
        assertTrue(provisioningManager.startProvisioningAndWait(intent));
        assertTrue(isExtraUserPresent(provisioningManager.getReceviedProfileProvisionedIntent()));
    }

    // This is only necessary if the profile is created via managed provisioning flow.
    public void testEnableProfile() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        dpm.setProfileEnabled(AdminReceiver.getComponentName(getContext()));
    }

    private boolean isExtraUserPresent(Intent intent) {
        return intent != null && intent.getExtras().containsKey(Intent.EXTRA_USER);
    }
}
