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

package android.packageinstaller.admin.cts;

/**
 * This class tests silent package install and uninstall by a device owner.
 */
public class SilentPackageInstallTest extends BasePackageInstallTest {
    public void testSilentInstallUninstall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // install the app
        assertInstallPackage();

        // uninstall the app again
        assertTrue(tryUninstallPackage());
        assertFalse(isPackageInstalled(TEST_APP_PKG));
    }

    public void testUninstallBlocked() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // install the app
        assertInstallPackage();

        mDevicePolicyManager.setUninstallBlocked(getWho(), TEST_APP_PKG, true);
        assertTrue(mDevicePolicyManager.isUninstallBlocked(getWho(), TEST_APP_PKG));
        assertTrue(mDevicePolicyManager.isUninstallBlocked(null, TEST_APP_PKG));
        assertFalse(tryUninstallPackage());
        assertTrue(isPackageInstalled(TEST_APP_PKG));

        mDevicePolicyManager.setUninstallBlocked(getWho(), TEST_APP_PKG, false);
        assertFalse(mDevicePolicyManager.isUninstallBlocked(getWho(), TEST_APP_PKG));
        assertFalse(mDevicePolicyManager.isUninstallBlocked(null, TEST_APP_PKG));
        assertTrue(tryUninstallPackage());
        assertFalse(isPackageInstalled(TEST_APP_PKG));
    }
}
