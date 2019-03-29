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

package com.android.cts.privilegedupdate;

import java.util.List;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.test.InstrumentationTestCase;

/**
 * Tests for intent filter.
 */
public class PrivilegedUpdateTest extends InstrumentationTestCase {
    /** Package name of the privileged CTS shim */
    private static final String PRIVILEGED_SHIM_PKG = "com.android.cts.priv.ctsshim";
    /** Package name of the system CTS shim */
    private static final String SYSTEM_SHIM_PKG = "com.android.cts.ctsshim";
    /** Class name for the install tests */
    private static final String INSTALL_CLASS = ".InstallPriority";

    /**
     * Tests the filter priorities for a system package are set correctly.
     * <p>
     * System packages can NOT obtain higher priorities for any action.
     */
    public void testSystemAppPriorities() throws Exception {
        final ComponentName testComponent =
                new ComponentName(SYSTEM_SHIM_PKG, SYSTEM_SHIM_PKG + INSTALL_CLASS);
        assertFilterPriority(testComponent, Intent.ACTION_SEARCH, 0);
        assertFilterPriority(testComponent, Intent.ACTION_VIEW, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND_MULTIPLE, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SENDTO, 0);
    }

    /**
     * Tests the filter priorities for a privileged package are set correctly.
     * <p>
     * Privileged packages can obtain higher priorities except for those on
     * protected actions. 
     */
    public void testPrivilegedAppPriorities() throws Exception {
        final ComponentName testComponent =
                new ComponentName(PRIVILEGED_SHIM_PKG, PRIVILEGED_SHIM_PKG + INSTALL_CLASS);
        assertFilterPriority(testComponent, Intent.ACTION_SEARCH, 100);
        assertFilterPriority(testComponent, Intent.ACTION_VIEW, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND_MULTIPLE, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SENDTO, 0);
    }

    /**
     * Tests the filter priorities for a privileged package are set correctly after update.
     * <p>
     * Test various forms of filter equivalency [eg. action, category, scheme and host].
     * Also, don't allow any filter obtain a higher priority than what was defined on
     * system image.
     */
    public void testPrivilegedAppUpgradePriorities() throws Exception {
        final ComponentName testComponent =
                new ComponentName(PRIVILEGED_SHIM_PKG, PRIVILEGED_SHIM_PKG + INSTALL_CLASS);
        assertFilterPriority(testComponent, Intent.ACTION_VIEW, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SEND_MULTIPLE, 0);
        assertFilterPriority(testComponent, Intent.ACTION_SENDTO, 0);

        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeMatch"),
                "com.android.cts.action.MATCH", 100);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeMatchMultiple"),
                "com.android.cts.action.MATCH_MULTIPLE", 150);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeLowerPriority"),
                "com.android.cts.action.LOWER_PRIORITY", 75);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeActionSubset"),
                "com.android.cts.action.ACTION_SUB_2", 100);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeCategorySubset"),
                "com.android.cts.action.CATEGORY_SUB", 100);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeSchemeSubset"),
                "com.android.cts.action.SCHEME_SUB", "flubber:", 100);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeAuthoritySubset"),
                "com.android.cts.action.AUTHORITY_SUB", 100);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeNewActivity"),
                "com.android.cts.action.NEW_ACTIVITY", 0);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeNewAction"),
                "com.android.cts.action.NEW_ACTION", 0);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeNewCategory"),
                "com.android.cts.action.NEW_CATEGORY", 0);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeNewScheme"),
                "com.android.cts.action.NEW_SCHEME", "zowie:", 0);
        assertFilterPriority(
                new ComponentName(PRIVILEGED_SHIM_PKG,
                        PRIVILEGED_SHIM_PKG + ".UpgradeNewAuthority"),
                "com.android.cts.action.NEW_AUTHORITY", 0);
    }

    private void assertFilterPriority(ComponentName component, String action, int priority) {
        assertFilterPriority(component, action, null /*data*/, priority);
    }
    private void assertFilterPriority(
            ComponentName component, String action, String data, int priority) {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        final String className = component.getClassName();
        final Intent intent = new Intent(action);
        intent.setPackage(component.getPackageName());
        if (data != null) {
            intent.setData(Uri.parse(data));
        }
        final List<ResolveInfo> entries =
                pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER);
        assertNotNull(entries);
        ResolveInfo foundInfo = null;
        for (ResolveInfo ri : entries) {
            if (ri.activityInfo.name.equals(className)) {
                foundInfo = ri;
                break;
            }
        }
        assertTrue(action + "; didn't find class \"" + className + "\"", foundInfo != null);
        assertEquals(action + "; wrong priority", priority, foundInfo.filter.getPriority());
    }
}
