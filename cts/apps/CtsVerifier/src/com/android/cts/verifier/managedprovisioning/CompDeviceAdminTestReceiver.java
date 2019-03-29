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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Profile owner receiver for COMP tests. Sets up cross-profile intent filters that allow the
 * CtsVerifier running in the primary user to send it commands after successful provisioning.
 */
public class CompDeviceAdminTestReceiver extends DeviceAdminReceiver {
        private static final ComponentName RECEIVER_COMPONENT_NAME = new ComponentName(
                "com.android.cts.verifier", CompDeviceAdminTestReceiver.class.getName());

        public static ComponentName getReceiverComponentName() {
            return RECEIVER_COMPONENT_NAME;
        }

        @Override
        public void onProfileProvisioningComplete(Context context, Intent intent) {
            final DevicePolicyManager dpm =
                    (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            dpm.setProfileEnabled(new ComponentName(context.getApplicationContext(), getClass()));

            // Set up cross-profile intent filter to allow the CtsVerifier running in the primary
            // user to send us commands.
            final IntentFilter filter = new IntentFilter();
            filter.addAction(CompHelperActivity.ACTION_SET_ALWAYS_ON_VPN);
            filter.addAction(CompHelperActivity.ACTION_INSTALL_CA_CERT);
            filter.addAction(CompHelperActivity.ACTION_SET_MAXIMUM_PASSWORD_ATTEMPTS);
            dpm.addCrossProfileIntentFilter(getWho(context), filter,
                    DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT);
        }
}
