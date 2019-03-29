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

package com.android.cts.deviceandprofileowner;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.HashSet;

public class SuspendPackageTest extends BaseDeviceAdminTest {
    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";

    public void testSetPackagesSuspended() throws NameNotFoundException {
        String[] notHandledPackages =
                mDevicePolicyManager.setPackagesSuspended(ADMIN_RECEIVER_COMPONENT, new String[]
                        {INTENT_RECEIVER_PKG}, true);
        // all packages should be handled.
        assertEquals(0, notHandledPackages.length);
        // test isPackageSuspended
        boolean isSuspended =
                mDevicePolicyManager.isPackageSuspended(
                        ADMIN_RECEIVER_COMPONENT, INTENT_RECEIVER_PKG);
        assertTrue(isSuspended);
    }

    public void testSetPackagesNotSuspended() throws NameNotFoundException {
        String[] notHandledPackages = mDevicePolicyManager.setPackagesSuspended(
                ADMIN_RECEIVER_COMPONENT,
                new String[] {INTENT_RECEIVER_PKG},
                false);
        // all packages should be handled.
        assertEquals(0, notHandledPackages.length);
        // test isPackageSuspended
        boolean isSuspended =
                mDevicePolicyManager.isPackageSuspended(
                        ADMIN_RECEIVER_COMPONENT, INTENT_RECEIVER_PKG);
        assertFalse(isSuspended);
    }

    /**
     * Verify that we cannot suspend launcher and dpc app.
     */
    public void testSuspendNotSuspendablePackages() {
        String launcherPackage = getLauncherPackage();
        String dpcPackage = ADMIN_RECEIVER_COMPONENT.getPackageName();
        String[] unsuspendablePackages = new String[] {launcherPackage, dpcPackage};
        String[] notHandledPackages = mDevicePolicyManager.setPackagesSuspended(
                ADMIN_RECEIVER_COMPONENT,
                unsuspendablePackages,
                true);
        // no package should be handled.
        assertArrayEqualIgnoreOrder(unsuspendablePackages, notHandledPackages);
    }

    /**
     * @return the package name of launcher.
     */
    private String getLauncherPackage() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = mContext.getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo.activityInfo.packageName;
    }

    private static <T> void assertArrayEqualIgnoreOrder(T[] a, T[] b) {
        assertEquals(a.length, b.length);
        assertTrue(new HashSet(Arrays.asList(a)).containsAll(new HashSet(Arrays.asList(b))));
    }

}
