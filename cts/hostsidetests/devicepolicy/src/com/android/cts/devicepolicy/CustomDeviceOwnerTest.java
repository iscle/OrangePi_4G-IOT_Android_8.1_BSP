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

package com.android.cts.devicepolicy;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.cts.devicepolicy.BaseDevicePolicyTest.Settings;

import java.io.File;
import java.lang.Exception;

/**
 * This class is used for tests that need to do something special before setting the device
 * owner, so they cannot use the regular DeviceOwnerTest class
 */
public class CustomDeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_ADMIN
            = DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";
    private static final String DEVICE_OWNER_ADMIN_COMPONENT
            = DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN;

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String ACCOUNT_MANAGEMENT_PKG
            = "com.android.cts.devicepolicy.accountmanagement";
    protected static final String ACCOUNT_MANAGEMENT_APK
            = "CtsAccountManagementDevicePolicyApp.apk";

    // Dequeue time of PACKAGE_ADDED intent for two test packages.
    private static final int BROADCAST_WAIT_TIME_MILLIS = 10000; // 10 seconds

    @Override
    public void tearDown() throws Exception {
        if (mHasFeature) {
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            getDevice().uninstallPackage(ACCOUNT_MANAGEMENT_PKG);
        }

        super.tearDown();
    }

    public void testOwnerChangedBroadcast() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            installAppAsUser(INTENT_RECEIVER_APK, mPrimaryUserId);

            String testClass = INTENT_RECEIVER_PKG + ".OwnerChangedBroadcastTest";

            // Running this test also gets the intent receiver app out of the stopped state, so it
            // can receive broadcast intents.
            runDeviceTestsAsUser(INTENT_RECEIVER_PKG, testClass,
                    "testOwnerChangedBroadcastNotReceived", mPrimaryUserId);

            // Setting the device owner should send the owner changed broadcast.
            assertTrue(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ false));

            // Waiting for the broadcast idle state.
            Thread.sleep(BROADCAST_WAIT_TIME_MILLIS);
            runDeviceTestsAsUser(INTENT_RECEIVER_PKG, testClass,
                    "testOwnerChangedBroadcastReceived", mPrimaryUserId);
        } finally {
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
            assertTrue("Failed to remove device owner.",
                    removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId));
        }
    }

    public void testCannotSetDeviceOwnerWhenSecondaryUserPresent() throws Exception {
        if (!mHasFeature || getMaxNumberOfUsersSupported() < 2) {
            return;
        }
        int userId = -1;
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            userId = createUser();
            assertFalse(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            removeUser(userId);
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId);
        }
    }

    public void testCannotSetDeviceOwnerWhenAccountPresent() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mPrimaryUserId);
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            runDeviceTestsAsUser(ACCOUNT_MANAGEMENT_PKG, ".AccountUtilsTest",
                    "testAddAccountExplicitly", mPrimaryUserId);
            assertFalse(setDeviceOwner(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_ADMIN_COMPONENT, mPrimaryUserId);
            runDeviceTestsAsUser(ACCOUNT_MANAGEMENT_PKG, ".AccountUtilsTest",
                    "testRemoveAccountExplicitly", mPrimaryUserId);
        }
    }

    public void testIsProvisioningAllowed() throws Exception {
        // Must install the apk since the test runs in the DO apk.
        installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
        try {
            // When CTS runs, setupwizard is complete. Expects it has to return false as DO can
            // only be provisioned before setupwizard is completed.

            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".PreDeviceOwnerTest",
                    "testIsProvisioningAllowedFalse", /* deviceOwnerUserId */ 0);
        } finally {
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        }
    }
}
