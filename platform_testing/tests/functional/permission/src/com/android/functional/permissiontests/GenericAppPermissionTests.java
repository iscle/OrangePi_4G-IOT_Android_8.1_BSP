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

package com.android.functional.permissiontests;

import android.app.UiAutomation;
import android.content.Context;
import android.os.SystemClock;
import android.support.test.launcherhelper.ILauncherStrategy;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.system.helpers.PackageHelper;
import android.system.helpers.PermissionHelper;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Arrays;
import java.util.List;

public class GenericAppPermissionTests extends InstrumentationTestCase {
    protected final String PACKAGE_INSTALLER = "com.android.packageinstaller";
    protected final String TARGET_APP_PKG = "com.android.functional.permissiontests";
    private final String PERMISSION_TEST_APP_PKG = "com.android.permissiontestappmv1";
    private final String PERMISSION_TEST_APP = "PermissionTestAppMV1";
    private UiDevice mDevice = null;
    private Context mContext = null;
    private UiAutomation mUiAutomation = null;
    private PermissionHelper pHelper = null;
    private PackageHelper pkgHelper = null;
    private ILauncherStrategy mILauncherStrategy = null;
    private final String[] mDefaultPermittedGroups = new String[] {
            "CONTACTS", "SMS", "STORAGE"
    };

    private final String[] mDefaultPermittedDangerousPermissions = new String[] {
            "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.WRITE_CONTACTS",
            "android.permission.SEND_SMS", "android.permission.RECEIVE_SMS"
    };

    private List<String> mDefaultGrantedPermissions;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mUiAutomation = getInstrumentation().getUiAutomation();
        mDevice.setOrientationNatural();
        pHelper = PermissionHelper.getInstance();
        pkgHelper = PackageHelper.getInstance(getInstrumentation());
        mDefaultGrantedPermissions = pHelper.getPermissionByPackage(TARGET_APP_PKG, Boolean.TRUE);
    }

    @SmallTest
    public void testDefaultDangerousPermissionGranted() {
        pHelper.verifyDefaultDangerousPermissionGranted(TARGET_APP_PKG,
                mDefaultPermittedDangerousPermissions, Boolean.FALSE);
    }

    @SmallTest
    public void testExtraDangerousPermissionNotGranted() {
        pHelper.verifyExtraDangerousPermissionNotGranted(TARGET_APP_PKG, mDefaultPermittedGroups);
    }

    @SmallTest
    public void testNormalPermissionsAutoGranted() {
        pHelper.verifyNormalPermissionsAutoGranted(TARGET_APP_PKG);
    }

    public void testToggleAppPermisssionOFF() {
        pHelper.togglePermissionSetting(PERMISSION_TEST_APP, "Contacts", Boolean.FALSE);
        pHelper.verifyPermissionSettingStatus(
                PERMISSION_TEST_APP, "Contacts", PermissionHelper.PermissionStatus.OFF);
    }

    public void testToggleAppPermisssionON() {
        pHelper.togglePermissionSetting(PERMISSION_TEST_APP, "Contacts", Boolean.TRUE);
        pHelper.verifyPermissionSettingStatus(PERMISSION_TEST_APP, "Contacts",
                PermissionHelper.PermissionStatus.ON);
    }

    @MediumTest
    public void testViewPermissionDescription() {
        List<String> permissionDescGrpNamesList = pHelper
                .getPermissionDescGroupNames(TARGET_APP_PKG);
        permissionDescGrpNamesList.removeAll(Arrays.asList(mDefaultPermittedGroups));
        assertTrue("Still there are more", permissionDescGrpNamesList.isEmpty());
    }

    public void testPermissionDialogAllow() {
        pkgHelper.cleanPackage(PERMISSION_TEST_APP_PKG);
        if (!mDevice.hasObject(By.pkg(PERMISSION_TEST_APP_PKG).depth(0))) {
            mILauncherStrategy.launch(PERMISSION_TEST_APP, PERMISSION_TEST_APP_PKG);
        }
        mDevice.wait(Until.findObject(By.text("GET CONTACT PERMISSION")), pHelper.TIMEOUT).click();
        mDevice.wait(Until.findObject(
                By.res(PACKAGE_INSTALLER, "permission_allow_button")), pHelper.TIMEOUT).click();
        assertTrue("Permission hasn't been granted",
                pHelper.getAllDangerousPermissionsByPermGrpNames(
                        new String[] {"Contacts"} ).size() > 0);
    }

    public void testPermissionDialogDenyFlow() {
        pkgHelper.cleanPackage(PERMISSION_TEST_APP_PKG);
        if (!mDevice.hasObject(By.pkg(PERMISSION_TEST_APP_PKG).depth(0))) {
            mILauncherStrategy.launch(PERMISSION_TEST_APP, PERMISSION_TEST_APP_PKG);
        }
        pHelper.grantOrRevokePermissionViaAdb(
                PERMISSION_TEST_APP_PKG, "android.permission.READ_CONTACTS",
                PermissionHelper.PermissionOp.REVOKE);
        BySelector getContactSelector = By.text("GET CONTACT PERMISSION");
        BySelector dontAskChkSelector = By.res(PACKAGE_INSTALLER, "do_not_ask_checkbox");
        BySelector denySelctor = By.res(PACKAGE_INSTALLER, "permission_deny_button");

        mDevice.wait(Until.findObject(getContactSelector), pHelper.TIMEOUT).click();
        UiObject2 dontAskChk = mDevice.wait(Until.findObject(dontAskChkSelector), pHelper.TIMEOUT);
        assertNull(dontAskChk);
        mDevice.wait(Until.findObject(denySelctor), pHelper.TIMEOUT).click();
        mDevice.wait(Until.findObject(getContactSelector), pHelper.TIMEOUT).click();
        mDevice.wait(Until.findObject(dontAskChkSelector), pHelper.TIMEOUT).click();
        mDevice.wait(Until.findObject(denySelctor), pHelper.TIMEOUT).click();
        mDevice.wait(Until.findObject(getContactSelector), pHelper.TIMEOUT).click();;
        assertNull(mDevice.wait(Until.findObject(denySelctor), pHelper.TIMEOUT));
    }

    @Override
    protected void tearDown() throws Exception {
        mDevice.unfreezeRotation();
        super.tearDown();
    }
}
