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

package com.android.cts.comp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;

/**
 * Testing various scenarios where a device owner attempts to use network / security logging or
 * request a bug report. Those features should only be available if the primary and managed
 * profiles are affiliated.
 */
public class DeviceWideLoggingFeaturesTest extends AndroidTestCase {

    private static final int NETWORK_LOGGING_BATCH_TOKEN = 123;

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdminComponent;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAdminComponent = AdminReceiver.getComponentName(mContext);
    }

    /**
     * Test: retrieving network/security logs can only be done if there's one user on the device or
     * all secondary users / profiles are affiliated.
     */
    public void testRetrievingLogsThrowsSecurityException() {
        try {
            mDevicePolicyManager.retrieveSecurityLogs(mAdminComponent);
            fail("retrieveSecurityLogs did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mDevicePolicyManager.retrievePreRebootSecurityLogs(mAdminComponent);
            fail("retrievePreRebootSecurityLogs did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mDevicePolicyManager.retrieveNetworkLogs(mAdminComponent, NETWORK_LOGGING_BATCH_TOKEN);
            fail("retrieveNetworkLogs did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testRetrievingLogsDoesNotThrowException() {
        mDevicePolicyManager.retrieveSecurityLogs(mAdminComponent);
        mDevicePolicyManager.retrievePreRebootSecurityLogs(mAdminComponent);
        mDevicePolicyManager.retrieveNetworkLogs(mAdminComponent, NETWORK_LOGGING_BATCH_TOKEN);
    }

    public void testRequestBugreportThrowsSecurityException() {
        try {
            mDevicePolicyManager.requestBugreport(mAdminComponent);
            fail("requestBugreport did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    public void testRequestBugreportDoesNotThrowException() {
        mDevicePolicyManager.requestBugreport(mAdminComponent);
    }

    public void testEnablingNetworkAndSecurityLogging() {
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(mAdminComponent));
        assertFalse(mDevicePolicyManager.isNetworkLoggingEnabled(mAdminComponent));

        mDevicePolicyManager.setSecurityLoggingEnabled(mAdminComponent, true);
        assertTrue(mDevicePolicyManager.isSecurityLoggingEnabled(mAdminComponent));
        mDevicePolicyManager.setNetworkLoggingEnabled(mAdminComponent, true);
        assertTrue(mDevicePolicyManager.isNetworkLoggingEnabled(mAdminComponent));
    }

    public void testDisablingNetworkAndSecurityLogging() {
        mDevicePolicyManager.setSecurityLoggingEnabled(mAdminComponent, false);
        mDevicePolicyManager.setNetworkLoggingEnabled(mAdminComponent, false);
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(mAdminComponent));
        assertFalse(mDevicePolicyManager.isNetworkLoggingEnabled(mAdminComponent));
    }
}
