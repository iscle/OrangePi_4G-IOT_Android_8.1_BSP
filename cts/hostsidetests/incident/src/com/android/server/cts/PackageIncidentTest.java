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
package com.android.server.cts;

import android.service.pm.PackageProto;
import android.service.pm.PackageServiceDumpProto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test for "dumpsys package --proto" */
public class PackageIncidentTest extends ProtoDumpTestCase {
    // Use the test apk from the NetstatsIncidentTest
    private static final String DEVICE_SIDE_TEST_APK = "CtsNetStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.netstats";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        super.tearDown();
    }

    private void assertPositive(String name, long value) {
        if (value > 0) return;
        fail(name + " expected to be positive, but was: " + value);
    }

    private void assertNotNegative(String name, long value) {
        if (value >= 0) return;
        fail(name + " expected to be zero or positive, but was: " + value);
    }

    /** Parse the output of "dumpsys package --proto" and make sure the values are probable. */
    public void testPackageServiceDump() throws Exception {
        final long st = System.currentTimeMillis();

        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        // Find the package UID, version code, and version string.
        final Matcher matcher =
                execCommandAndFind(
                        "dumpsys package " + DEVICE_SIDE_TEST_PACKAGE,
                        "userId=(\\d+).*versionCode=(\\d+).*versionName=([^\\n]*)",
                        Pattern.DOTALL);
        final int uid = Integer.parseInt(matcher.group(1));
        final int versionCode = Integer.parseInt(matcher.group(2));
        final String versionString = matcher.group(3).trim();

        final PackageServiceDumpProto dump =
                getDump(PackageServiceDumpProto.parser(), "dumpsys package --proto");

        assertNotNull(dump.getVerifierPackage().getName());
        assertPositive("verifier_package uid", dump.getVerifierPackage().getUid());
        assertNotNull(dump.getSharedLibraries(0).getName());
        if (dump.getSharedLibraries(0).getIsJar()) {
            assertNotNull(dump.getSharedLibraries(0).getPath());
        } else {
            assertNotNull(dump.getSharedLibraries(0).getApk());
        }
        assertNotNull(dump.getFeatures(0).getName());
        PackageProto testPackage = null;
        for (PackageProto pkg : dump.getPackagesList()) {
            if (pkg.getName().equals(DEVICE_SIDE_TEST_PACKAGE)) {
                testPackage = pkg;
                break;
            }
        }
        assertNotNull(testPackage);
        assertEquals(testPackage.getName(), DEVICE_SIDE_TEST_PACKAGE);
        assertEquals(testPackage.getUid(), uid);
        assertEquals(testPackage.getVersionCode(), versionCode);
        assertEquals(testPackage.getVersionString(), versionString);
        assertPositive("install_time_ms", testPackage.getInstallTimeMs());
        assertEquals(testPackage.getInstallTimeMs(), testPackage.getUpdateTimeMs());
        assertEquals(testPackage.getSplits(0).getName(), "base");
        assertEquals(testPackage.getSplits(0).getRevisionCode(), 0);
        assertEquals(testPackage.getUsers(0).getId(), 0);
        assertEquals(
                testPackage.getUsers(0).getInstallType(),
                PackageProto.UserInfoProto.InstallType.FULL_APP_INSTALL);
        assertFalse(testPackage.getUsers(0).getIsHidden());
        assertFalse(testPackage.getUsers(0).getIsLaunched());
        assertFalse(
                testPackage.getUsers(0).getEnabledState()
                        == PackageProto.UserInfoProto.EnabledState
                                .COMPONENT_ENABLED_STATE_DISABLED_USER);

        PackageServiceDumpProto.SharedUserProto systemUser = null;
        for (PackageServiceDumpProto.SharedUserProto user : dump.getSharedUsersList()) {
            if (user.getUserId() == 1000) {
                systemUser = user;
                break;
            }
        }
        assertNotNull(systemUser);
        assertEquals(systemUser.getName(), "android.uid.system");
    }
}
