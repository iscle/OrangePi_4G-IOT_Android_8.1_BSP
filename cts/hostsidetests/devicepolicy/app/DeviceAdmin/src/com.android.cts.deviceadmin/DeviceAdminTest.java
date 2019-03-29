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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * Device admin device side tests.
 */
public class DeviceAdminTest extends BaseDeviceAdminTest {
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotDeviceOwner();
    }

    public void testTargetApiLevel() throws Exception {
        final PackageManager pm = mContext.getPackageManager();

        final PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), /* flags =*/ 0);

        assertEquals(getTargetApiLevel(), pi.applicationInfo.targetSdkVersion);
    }

    public void testGetMaximumFailedPasswordsForWipe() {
        dpm.setMaximumFailedPasswordsForWipe(mAdminComponent, 3);
        assertEquals(3, dpm.getMaximumFailedPasswordsForWipe(mAdminComponent));

        dpm.setMaximumFailedPasswordsForWipe(mAdminComponent, 5);
        assertEquals(5, dpm.getMaximumFailedPasswordsForWipe(mAdminComponent));
    }

    public void testPasswordHistoryLength() {
        // Password history length restriction is only imposed if password quality is at least
        // numeric.
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_ALPHABETIC);
        int originalValue = dpm.getPasswordHistoryLength(mAdminComponent);
        try {
            dpm.setPasswordHistoryLength(mAdminComponent, 3);
            assertEquals(3, dpm.getPasswordHistoryLength(mAdminComponent));
            // Although it would make sense we cannot test if password history restrictions
            // are enforced as DevicePolicyManagerService.resetPassword fails to do so at the
            // moment. See b/17707820
        } finally {
            dpm.setPasswordHistoryLength(mAdminComponent, originalValue);
        }
    }

    public void testPasswordExpirationTimeout() {
        long originalValue = dpm.getPasswordExpirationTimeout(mAdminComponent);
        try {
            for (long testLength : new long[] {
                    0L, 864000000L /* ten days */, 8640000000L /* 100 days */}) {
                dpm.setPasswordExpirationTimeout(mAdminComponent, testLength);
                assertEquals(testLength,
                        dpm.getPasswordExpirationTimeout(mAdminComponent));
            }
        } finally {
            dpm.setPasswordExpirationTimeout(mAdminComponent, originalValue);
        }
    }
}
