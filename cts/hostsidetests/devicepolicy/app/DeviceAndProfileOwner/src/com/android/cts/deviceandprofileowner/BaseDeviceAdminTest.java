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
package com.android.cts.deviceandprofileowner;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

/**
 * Base class for profile and device based tests.
 *
 * This class handles making sure that the test is the profile or device owner and that it has an
 * active admin registered, so that all tests may assume these are done.
 */
public class BaseDeviceAdminTest extends InstrumentationTestCase {

    public static class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    public static final String PACKAGE_NAME = BasicAdminReceiver.class.getPackage().getName();
    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            PACKAGE_NAME, BasicAdminReceiver.class.getName());

    protected DevicePolicyManager mDevicePolicyManager;
    protected UserManager mUserManager;
    protected Context mContext;

    private final String mTag = getClass().getSimpleName();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        assertNotNull(mDevicePolicyManager);

        mUserManager = mContext.getSystemService(UserManager.class);
        assertNotNull(mUserManager);

        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue("App is neither device nor profile owner",
                mDevicePolicyManager.isProfileOwnerApp(PACKAGE_NAME) ||
                mDevicePolicyManager.isDeviceOwnerApp(PACKAGE_NAME));
    }

    protected int getTargetApiLevel() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), /* flags =*/ 0);
        return pi.applicationInfo.targetSdkVersion;
    }

    /**
     * Runs a Shell command, returning a trimmed response.
     */
    protected String runShellCommand(String template, Object...args) {
        final String command = String.format(template, args);
        Log.d(mTag, "runShellCommand(): " + command);
        try {
            final String result = SystemUtil.runShellCommand(getInstrumentation(), command);
            return TextUtils.isEmpty(result) ? "" : result.trim();
        } catch (Exception e) {
            throw new RuntimeException("Command '" + command + "' failed: ", e);
        }
    }

    protected void assertPasswordSufficiency(boolean expectPasswordSufficient) {
        int retries = 15;
        // isActivePasswordSufficient() gets the result asynchronously so let's retry a few times
        while (retries >= 0
                && mDevicePolicyManager.isActivePasswordSufficient() != expectPasswordSufficient) {
            retries--;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                break;
            }
        }
        assertEquals(expectPasswordSufficient, mDevicePolicyManager.isActivePasswordSufficient());
    }
}
