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

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Build;
import android.test.AndroidTestCase;

public class BaseDeviceAdminTest extends AndroidTestCase {

    public static class AdminReceiver extends DeviceAdminReceiver {
    }

    protected String mPackageName;
    protected ComponentName mAdminComponent;

    public DevicePolicyManager dpm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        dpm = mContext.getSystemService(DevicePolicyManager.class);
        mPackageName = mContext.getPackageName();
        mAdminComponent = new ComponentName(mContext, AdminReceiver.class);
    }

    /**
     * @return the target API level.  Note we don't get it from the package manager information
     * but we just parse the last two digits of the package name.  This is to catch a potential
     * issue where we forget to change the target API level in the manifest.  (Conversely,
     * if we forget to change the package name, we'll catch that in the caller side.)
     */
    protected int getTargetApiLevel() {
        final String packageName = mContext.getPackageName();
        return Integer.parseInt(packageName.substring(packageName.length() - 2));
    }

    protected boolean isDeviceOwner() {
        return dpm.isDeviceOwnerApp(mAdminComponent.getPackageName());
    }

    protected void assertDeviceOwner() {
        assertTrue("Not device owner", isDeviceOwner());
    }

    protected void assertNotDeviceOwner() {
        assertFalse("Must not be device owner", isDeviceOwner());
    }

    protected void assertNotActiveAdmin() throws Exception {
        for (int i = 0; i < 1000 && dpm.isAdminActive(mAdminComponent); i++) {
            Thread.sleep(10);
        }
        assertFalse("Still active admin", dpm.isAdminActive(mAdminComponent));
    }

    protected boolean shouldResetPasswordThrow() {
        return getTargetApiLevel() > Build.VERSION_CODES.M;
    }

    protected void resetComplexPasswordRestrictions() {
        dpm.setPasswordMinimumLength(mAdminComponent, 0);
        dpm.setPasswordMinimumUpperCase(mAdminComponent, 0);
        dpm.setPasswordMinimumLowerCase(mAdminComponent, 0);
        dpm.setPasswordMinimumLetters(mAdminComponent, 0);
        dpm.setPasswordMinimumNumeric(mAdminComponent, 0);
        dpm.setPasswordMinimumSymbols(mAdminComponent, 0);
        dpm.setPasswordMinimumNonLetter(mAdminComponent, 0);
    }

    protected void clearPassword() {
        assertDeviceOwner();

        resetComplexPasswordRestrictions();

        dpm.setPasswordQuality(mAdminComponent, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        assertTrue(dpm.resetPassword("", /* flags =*/ 0));
    }
}
