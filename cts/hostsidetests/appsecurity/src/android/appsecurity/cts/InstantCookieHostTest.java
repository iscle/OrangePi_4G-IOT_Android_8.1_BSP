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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests for the instant cookie APIs
 */
public class InstantCookieHostTest extends DeviceTestCase implements IBuildReceiver {
    private static final String INSTANT_COOKIE_APP_APK = "CtsInstantCookieApp.apk";
    private static final String INSTANT_COOKIE_APP_PKG = "test.instant.cookie";

    private static final String INSTANT_COOKIE_APP_APK_2 = "CtsInstantCookieApp2.apk";
    private static final String INSTANT_COOKIE_APP_PKG_2 = "test.instant.cookie";

    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        uninstallPackage(INSTANT_COOKIE_APP_PKG);
        clearAppCookieData();
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallPackage(INSTANT_COOKIE_APP_PKG);
        clearAppCookieData();
    }

    public void testCookieUpdateAndRetrieval() throws Exception {
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookieUpdateAndRetrieval");
    }

    public void testCookiePersistedAcrossInstantInstalls() throws Exception {
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookiePersistedAcrossInstantInstalls1");
        uninstallPackage(INSTANT_COOKIE_APP_PKG);
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookiePersistedAcrossInstantInstalls2");
    }

    public void testCookiePersistedUpgradeFromInstant() throws Exception {
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookiePersistedUpgradeFromInstant1");
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, true, false));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookiePersistedUpgradeFromInstant2");
    }

    public void testCookieResetOnNonInstantReinstall() throws Exception {
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, false));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookieResetOnNonInstantReinstall1");
        uninstallPackage(INSTANT_COOKIE_APP_PKG);
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, true, false));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookieResetOnNonInstantReinstall2");
    }

    public void testCookieValidWhenSingedWithTwoCerts() throws Exception {
        assertNull(installPackage(INSTANT_COOKIE_APP_APK, false, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG, "test.instant.cookie.CookieTest",
                "testCookiePersistedAcrossInstantInstalls1");
        uninstallPackage(INSTANT_COOKIE_APP_PKG);
        assertNull(installPackage(INSTANT_COOKIE_APP_APK_2, true, true));
        runDeviceTests(INSTANT_COOKIE_APP_PKG_2, "test.instant.cookie.CookieTest",
                "testCookiePersistedAcrossInstantInstalls2");
    }

    private String installPackage(String apk, boolean replace, boolean instant) throws Exception {
        return getDevice().installPackage(mBuildHelper.getTestFile(apk), replace,
                instant ? "--instant" : "--full");
    }

    private String uninstallPackage(String packageName) throws DeviceNotAvailableException {
        return getDevice().uninstallPackage(packageName);
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    private void clearAppCookieData() throws Exception {
        getDevice().executeShellCommand("pm clear " + INSTANT_COOKIE_APP_PKG);
    }
}
