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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

/**
 * Tests for {@link DevicePolicyManager#createAdminSupportIntent} API.
 */
public class PolicyTransparencyTest extends BaseDeviceAdminTest {

    private static final String TAG = "PolicyTransparencyTest";

    public void testCameraDisabled() throws Exception {
        mDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, true);

        Intent intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_CAMERA);
        assertNotNull(intent);
        assertEquals(ADMIN_RECEIVER_COMPONENT,
                (ComponentName) intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN));
        assertEquals(DevicePolicyManager.POLICY_DISABLE_CAMERA,
                intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION));

        mDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, false);
        intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_CAMERA);
        assertNull(intent);
    }

    public void testScreenCaptureDisabled() throws Exception {
        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, true);

        Intent intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE);
        assertNotNull(intent);
        assertEquals(ADMIN_RECEIVER_COMPONENT,
                (ComponentName) intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN));
        assertEquals(DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE,
                intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION));

        mDevicePolicyManager.setScreenCaptureDisabled(ADMIN_RECEIVER_COMPONENT, false);
        intent = mDevicePolicyManager.createAdminSupportIntent(
                DevicePolicyManager.POLICY_DISABLE_SCREEN_CAPTURE);
        assertNull(intent);
    }

    public void testUserRestrictions() throws Exception {
        // Test with a few random user restrictions:
        runTestForRestriction(UserManager.DISALLOW_ADJUST_VOLUME);
        runTestForRestriction(UserManager.DISALLOW_CONFIG_WIFI);
        runTestForRestriction(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE);
    }

    private void runTestForRestriction(String restriction) throws Exception {
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);

        Intent intent = mDevicePolicyManager.createAdminSupportIntent(restriction);
        assertNotNull(intent);
        assertEquals(ADMIN_RECEIVER_COMPONENT,
                (ComponentName) intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN));
        assertEquals(restriction, intent.getStringExtra(DevicePolicyManager.EXTRA_RESTRICTION));

        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT, restriction);
        intent = mDevicePolicyManager.createAdminSupportIntent(restriction);
        assertNull(intent);
    }
}

