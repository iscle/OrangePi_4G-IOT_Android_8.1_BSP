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
package com.android.cts.packageaccessapp;

import static junit.framework.Assert.assertFalse;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;

import org.junit.Before;
import org.junit.Test;
import org.junit.internal.runners.statements.Fail;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class PackageAccessTest {

    private String TINY_PKG = "android.appsecurity.cts.tinyapp";

    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    @Test
    public void testPackageAccess_inUser() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(TINY_PKG, 0);
            assertNotNull(pi);
        } catch (NameNotFoundException e) {
            fail("Package must be found");
        }
    }

    @Test
    public void testPackageAccess_inUserUninstalled() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(TINY_PKG, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            assertNotNull(pi);
        } catch (NameNotFoundException e) {
            fail("Package must be found");
        }
    }

    @Test
    public void testPackageAccess_notInOtherUser() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(TINY_PKG, 0);
            // If it doesn't throw an exception, then at least it should be null
            assertNull(pi); 
        } catch (NameNotFoundException e) {
        }
    }

    @Test
    public void testPackageAccess_notInOtherUserUninstalled() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(TINY_PKG, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            // If it doesn't throw an exception, then at least it should be null
            assertNull(pi);
        } catch (NameNotFoundException e) {
        }
    }

    @Test
    public void testPackageAccess_getPackagesCantSeeTiny() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(
                PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : packages) {
            if (TINY_PKG.equals(pi.packageName)) {
                fail(TINY_PKG + " visible in user");
            }
        }
    }

    @Test
    public void testPackageAccess_getPackagesCanSeeTiny() throws Exception {
        PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(
                PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : packages) {
            if (TINY_PKG.equals(pi.packageName)) {
                return;
            }
        }
        fail(TINY_PKG + " not found in getInstalledPackages()");
    }
}