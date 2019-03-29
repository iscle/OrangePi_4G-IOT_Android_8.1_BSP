/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.cts.packageinstaller;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.InstrumentationTestCase;

/**
 * Base class for profile and device based tests.
 *
 * This class handles making sure that the test is the profile or device owner and that it has an
 * active admin registered, so that all tests may assume these are done.
 */
public class ClearDeviceOwnerTest extends InstrumentationTestCase {

    public static class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    public static final String PACKAGE_NAME = BasicAdminReceiver.class.getPackage().getName();
    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            PACKAGE_NAME, BasicAdminReceiver.class.getName());

    private DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevicePolicyManager = (DevicePolicyManager)
                getInstrumentation().getContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        assertNotNull(mDevicePolicyManager);

        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue("App is not device owner", mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME));
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearDeviceOwnerApp(PACKAGE_NAME);
        assertFalse(mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME));
        waitForActiveAdminRemoved(ADMIN_RECEIVER_COMPONENT);

        super.tearDown();
    }

    // This test clears the device owner and active admin on tearDown(). To be called from the host
    // side test once a test case is finished.
    public void testClearDeviceOwner() {
    }

    private void waitForActiveAdminRemoved(ComponentName cn) throws InterruptedException {
        for (int i = 0; i < 1000 && mDevicePolicyManager.isAdminActive(cn); i++) {
            Thread.sleep(100);
        }
        assertFalse(mDevicePolicyManager.isAdminActive(cn));
    }
}
