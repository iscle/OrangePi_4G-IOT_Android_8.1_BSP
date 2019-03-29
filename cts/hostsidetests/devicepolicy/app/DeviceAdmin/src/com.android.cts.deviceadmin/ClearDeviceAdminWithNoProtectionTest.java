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
package com.android.cts.deviceadmin;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.test.AndroidTestCase;

/**
 * Remove {@link DeviceAdminReceiverWithNoProtection} as active admin.
 */
public class ClearDeviceAdminWithNoProtectionTest extends AndroidTestCase {

    public void testRemoveActiveAdmin() throws Exception {

        final ComponentName cn = new ComponentName(getContext(),
                DeviceAdminReceiverWithNoProtection.class);

        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        if (dpm.isAdminActive(cn)) {
            dpm.removeActiveAdmin(cn);
            for (int i = 0; i < 6000 && dpm.isAdminActive(cn); i++) {
                Thread.sleep(10);
            }
        }
        assertFalse("Still active admin", dpm.isAdminActive(cn));
    }
}
