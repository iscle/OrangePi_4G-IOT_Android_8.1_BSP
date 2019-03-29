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
package com.android.cts.deviceowner;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.AndroidTestCase;

/**
 * The following test can run in DeviceOwner mode or non-DeviceOwner mode.
 * Don't inherit {@link BaseDeviceOwnerTest}
 */
public class PreDeviceOwnerTest extends AndroidTestCase {

    protected DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void testIsProvisioningAllowedFalse() {
        assertFalse(mDevicePolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE));
    }

    public void testIsProvisioningAllowedTrueForManagedProfileAction() {
        assertTrue(mDevicePolicyManager
                .isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
    }

}
