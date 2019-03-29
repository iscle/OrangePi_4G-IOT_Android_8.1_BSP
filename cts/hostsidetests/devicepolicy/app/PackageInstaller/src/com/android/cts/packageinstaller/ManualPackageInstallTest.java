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

package com.android.cts.packageinstaller;

import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;

/**
 * This class tests manual package install and uninstall by a device owner.
 */
public class ManualPackageInstallTest extends BasePackageInstallTest {
    private static final int AUTOMATOR_WAIT_TIMEOUT = 5000;
    private static final int INSTALL_WAIT_TIME = 5000;

    private static final BySelector POPUP_BUTTON_SELECTOR = By
            .clazz(android.widget.Button.class.getName())
            .res("android:id/button1")
            .pkg("com.android.settings");
    private static final BySelector POPUP_IMAGE_SELECTOR = By
            .clazz(android.widget.ImageView.class.getName())
            .res("com.android.settings:id/admin_support_icon")
            .pkg("com.android.settings");
    private static final BySelector INSTALL_BUTTON_SELECTOR = By
            .clazz(android.widget.Button.class.getName())
            .res("com.android.packageinstaller:id/ok_button");

    public void testManualInstallSucceeded() throws Exception {
        assertInstallPackage();
    }

    public void testManualInstallBlocked() throws Exception {
        synchronized (mPackageInstallerTimeoutLock) {
            mCallbackReceived = false;
            mCallbackStatus = PACKAGE_INSTALLER_STATUS_UNDEFINED;
        }
        // Calls the original installPackage which does not click through the install button.
        super.installPackage(TEST_APP_LOCATION);
        synchronized (mPackageInstallerTimeoutLock) {
            try {
                mPackageInstallerTimeoutLock.wait(PACKAGE_INSTALLER_TIMEOUT_MS);
            } catch (InterruptedException e) {
            }
            assertTrue(mCallbackReceived);
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, mCallbackStatus);
        }

        mCallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(mCallbackIntent);

        automateDismissInstallBlockedDialog();

        // Assuming installation is not synchronous, we should wait a while before checking.
        Thread.sleep(INSTALL_WAIT_TIME);
        assertFalse(isPackageInstalled(TEST_APP_PKG));
    }

    @Override
    protected void installPackage(String packageLocation) throws Exception {
        super.installPackage(packageLocation);

        synchronized (mPackageInstallerTimeoutLock) {
            try {
                mPackageInstallerTimeoutLock.wait(PACKAGE_INSTALLER_TIMEOUT_MS);
            } catch (InterruptedException e) {
            }
            assertTrue(mCallbackReceived);
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, mCallbackStatus);
        }

        // Use a receiver to listen for package install.
        synchronized (mPackageInstallerTimeoutLock) {
            mCallbackReceived = false;
            mCallbackStatus = PACKAGE_INSTALLER_STATUS_UNDEFINED;
        }

        mCallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(mCallbackIntent);

        automateInstallClick();
    }

    private void automateInstallClick() {
        mDevice.wait(Until.hasObject(INSTALL_BUTTON_SELECTOR), AUTOMATOR_WAIT_TIMEOUT);
        UiObject2 button = mDevice.findObject(INSTALL_BUTTON_SELECTOR);
        assertNotNull("Install button not found", button);
        button.click();
    }

    private void automateDismissInstallBlockedDialog() {
        mDevice.wait(Until.hasObject(POPUP_IMAGE_SELECTOR), AUTOMATOR_WAIT_TIMEOUT);
        UiObject2 icon = mDevice.findObject(POPUP_IMAGE_SELECTOR);
        assertNotNull("Policy transparency dialog icon not found", icon);
        // "OK" button only present in the dialog if it is blocked by policy.
        UiObject2 button = mDevice.findObject(POPUP_BUTTON_SELECTOR);
        assertNotNull("OK button not found", button);
        button.click();
    }
}
