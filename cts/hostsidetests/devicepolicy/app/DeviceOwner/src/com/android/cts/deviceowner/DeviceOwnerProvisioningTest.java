/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static java.util.stream.Collectors.toList;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.android.compatibility.common.util.devicepolicy.provisioning.SilentProvisioningTestManager;
import java.util.ArrayList;
import java.util.List;


public class DeviceOwnerProvisioningTest extends BaseDeviceOwnerTest {
    private static final String TAG = "DeviceOwnerProvisioningTest";

    private List<String> mEnabledAppsBeforeTest;
    private PackageManager mPackageManager;
    private DevicePolicyManager mDpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mPackageManager = getContext().getPackageManager();
        mDpm = getContext().getSystemService(DevicePolicyManager.class);
        mEnabledAppsBeforeTest = getPackageNameList();
    }

    @Override
    protected void tearDown() throws Exception {
        enableUninstalledApp();
        super.tearDown();
    }

    public void testProvisionDeviceOwner() throws Exception {
        deviceOwnerProvision(getBaseProvisioningIntent());
    }

    public void testProvisionDeviceOwner_withAllSystemAppsEnabled() throws Exception {
        List<String> systemAppsBefore = getSystemPackageNameList();

        Intent intent = getBaseProvisioningIntent()
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED, true);
        deviceOwnerProvision(intent);

        List<String> systemAppsAfter = getSystemPackageNameList();
        assertTrue(systemAppsBefore.equals(systemAppsAfter));
    }

    private void enableUninstalledApp() {
        final List<String> currentEnabledApps = getPackageNameList();

        final List<String> disabledApps = new ArrayList<String>(mEnabledAppsBeforeTest);
        disabledApps.removeAll(currentEnabledApps);

        for (String disabledSystemApp : disabledApps) {
            Log.i(TAG, "enable app : " + disabledSystemApp);
            mDevicePolicyManager.enableSystemApp(BasicAdminReceiver.getComponentName(getContext()),
                    disabledSystemApp);
        }
    }

    private Intent getBaseProvisioningIntent() {
        return new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                        BasicAdminReceiver.getComponentName(getContext()))
                .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true);
    }

    private void deviceOwnerProvision(Intent intent) throws Exception {
        SilentProvisioningTestManager provisioningManager =
                new SilentProvisioningTestManager(getContext());
        assertTrue(provisioningManager.startProvisioningAndWait(intent));
        Log.i(TAG, "device owner provisioning successful");
        assertTrue(mDpm.isDeviceOwnerApp(getContext().getPackageName()));
        Log.i(TAG, "device owner app: " + getContext().getPackageName());
    }

    private List<String> getPackageNameList() {
        return getPackageNameList(0 /* Default flags */);
    }

    private List<String> getSystemPackageNameList() {
        return getPackageNameList(MATCH_SYSTEM_ONLY);
    }

    private List<String> getPackageNameList(int flags) {
        return mPackageManager.getInstalledApplications(flags)
                .stream()
                .map((ApplicationInfo appInfo) -> appInfo.packageName)
                .sorted()
                .collect(toList());
    }
}
