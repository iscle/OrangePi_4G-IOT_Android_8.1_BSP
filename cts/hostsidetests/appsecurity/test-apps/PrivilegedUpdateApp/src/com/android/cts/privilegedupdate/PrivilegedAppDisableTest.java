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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.test.InstrumentationTestCase;

/**
 * Tests for system app type and enabled state
 */
public class PrivilegedAppDisableTest extends InstrumentationTestCase {
    /** Package name of the privileged CTS shim */
    private static final String PRIVILEGED_SHIM_PKG = "com.android.cts.priv.ctsshim";

    public void testPrivAppAndEnabled() throws Exception {
        assertEquals((getApplicationFlags() & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP),
                0);
        assertPackageEnabledState(PRIVILEGED_SHIM_PKG,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    public void testPrivAppAndDisabled() throws Exception {
        assertEquals((getApplicationFlags() & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP),
                0);
        assertPackageEnabledState(PRIVILEGED_SHIM_PKG,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
    }

    public void testUpdatedPrivAppAndEnabled() throws Exception {
        assertEquals((getApplicationFlags() & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP),
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
        assertPackageEnabledState(PRIVILEGED_SHIM_PKG,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
    }

    public void testUpdatedPrivAppAndDisabled() throws Exception {
        assertEquals((getApplicationFlags() & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP),
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP);
        assertPackageEnabledState(PRIVILEGED_SHIM_PKG,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
    }

    private int getApplicationFlags() {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        try {
            ApplicationInfo aInfo = pm.getApplicationInfo(PRIVILEGED_SHIM_PKG,
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            return aInfo.flags;
        } catch (NameNotFoundException e) {
            return 0;
        }
    }

    private void assertPackageEnabledState(String packageName, int expectedState) {
        PackageManager pm = getInstrumentation().getContext().getPackageManager();
        int state = pm.getApplicationEnabledSetting(packageName);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        }
        assertEquals(expectedState, state);
    }
}
