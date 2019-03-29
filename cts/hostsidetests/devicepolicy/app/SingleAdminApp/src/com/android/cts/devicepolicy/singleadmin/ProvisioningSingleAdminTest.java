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
 * limitations under the License
 */
package com.android.cts.devicepolicy.singleadmin;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;

import static org.junit.Assert.assertTrue;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import com.android.compatibility.common.util.devicepolicy.provisioning.SilentProvisioningTestManager;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ProvisioningSingleAdminTest {

    private static final String ADMIN_RECEIVER_PACKAGE = AdminReceiver.class.getPackage().getName();
    private static final String ADMIN_RECEIVER_NAME = AdminReceiver.class.getName();

    private static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            ADMIN_RECEIVER_PACKAGE, ADMIN_RECEIVER_NAME);

    private Context mContext;

    public static class AdminReceiver extends DeviceAdminReceiver {
        @Override
        public void onProfileProvisioningComplete(Context context, Intent intent) {
            super.onProfileProvisioningComplete(context, intent);
            getManager(context).setProfileName(ADMIN_RECEIVER_COMPONENT, "Managed Profile");
            getManager(context).setProfileEnabled(ADMIN_RECEIVER_COMPONENT);
        }
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testManagedProfileProvisioning() throws InterruptedException {
        assertProvisioningSucceeds(createProvisioningIntent());
    }

    private Intent createProvisioningIntent() {
        return new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, ADMIN_RECEIVER_PACKAGE);
    }

    private void assertProvisioningSucceeds(Intent intent) throws InterruptedException {
        SilentProvisioningTestManager provisioningMgr = new SilentProvisioningTestManager(mContext);
        assertTrue(provisioningMgr.startProvisioningAndWait(intent));
    }
}
