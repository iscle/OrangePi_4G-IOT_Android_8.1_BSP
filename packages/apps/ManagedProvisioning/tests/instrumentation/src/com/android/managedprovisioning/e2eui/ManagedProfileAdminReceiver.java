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
package com.android.managedprovisioning.e2eui;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.android.managedprovisioning.TestInstrumentationRunner;

public class ManagedProfileAdminReceiver extends DeviceAdminReceiver {
    public static final ComponentName COMPONENT_NAME = new ComponentName(
            TestInstrumentationRunner.TEST_PACKAGE_NAME,
            ManagedProfileAdminReceiver.class.getName());

    public static final Intent INTENT_PROVISION_MANAGED_PROFILE =
            E2eUiTestUtils.insertProvisioningExtras(new Intent(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                            COMPONENT_NAME)
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true));

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        // Verify that managed profile has been successfully created.
        boolean testResult = E2eUiTestUtils.verifyProfile(context, intent, getManager(context));
        // Informs the result to provisioning result listener.
        E2eUiTestUtils.sendResult(ProvisioningResultListener.ACTION_PROVISION_RESULT_BROADCAST, context,
                testResult);
    }
}
