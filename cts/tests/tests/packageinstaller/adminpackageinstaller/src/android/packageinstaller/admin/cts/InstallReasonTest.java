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

package android.packageinstaller.admin.cts;

import android.content.pm.PackageManager;

/**
 * This class tests that the install reason is correctly recorded for packages.
 */
public class InstallReasonTest extends BasePackageInstallTest {
    public void testInstallReason() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Verify that since the Device Owner was sideloaded, its install reason is unknown.
        assertEquals(PackageManager.INSTALL_REASON_UNKNOWN, getInstallReason(PACKAGE_NAME));

        // Verify that when the Device Owner installs another package, its install reason is
        // recorded as enterprise policy.
        assertInstallPackage();
        assertEquals(PackageManager.INSTALL_REASON_POLICY, getInstallReason(TEST_APP_PKG));
        tryUninstallPackage();
    }
}
