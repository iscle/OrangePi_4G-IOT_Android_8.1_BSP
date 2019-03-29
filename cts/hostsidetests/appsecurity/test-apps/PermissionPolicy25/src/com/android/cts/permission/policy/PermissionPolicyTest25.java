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

package com.android.cts.permission.policy;

import static junit.framework.Assert.fail;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.support.test.InstrumentationRegistry;
import org.junit.Test;

/**
 * Tests for the platform permission policy around apps targeting API 25
 */
public class PermissionPolicyTest25 {
    private static final String PLATFORM_PACKAGE_NAME = "android";

    @Test
    public void testNoProtectionFlagsAddedToNonSignatureProtectionPermissions() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final PackageInfo platformPackage = context.getPackageManager()
                .getPackageInfo(PLATFORM_PACKAGE_NAME, PackageManager.GET_PERMISSIONS);
        String errorMessage = null;
        for (PermissionInfo declaredPermission : platformPackage.permissions) {
            PermissionInfo permissionInfo = context.getPackageManager()
                    .getPermissionInfo(declaredPermission.name, 0);
            final int protectionLevel = permissionInfo.protectionLevel
                    & (PermissionInfo.PROTECTION_NORMAL
                    | PermissionInfo.PROTECTION_DANGEROUS
                    | PermissionInfo.PROTECTION_SIGNATURE);
            final int protectionFlags = permissionInfo.protectionLevel & ~protectionLevel;
            if (protectionLevel == PermissionInfo.PROTECTION_NORMAL && protectionFlags != 0) {
                errorMessage += "\nCannot add protection flags: "
                        + protectionFlagsToString(permissionInfo.protectionLevel)
                        + " to a normal protection permission: " + permissionInfo.name;
            }
            if (protectionLevel == PermissionInfo.PROTECTION_DANGEROUS && protectionFlags != 0) {
                errorMessage += "\nCannot add protection flags: "
                        + protectionFlagsToString(permissionInfo.protectionLevel)
                        + " to a dangerous protection permission: " + permissionInfo.name;
            }
        }
        if (errorMessage != null) {
            fail(errorMessage);
        }
    }

    private static String protectionFlagsToString(int protectionLevel) {
        String flagsToString = "";
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY) != 0) {
            flagsToString += flagsToString.isEmpty() ? "runtimeOnly" : "|runtimeOnly";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_INSTANT) != 0) {
            flagsToString += flagsToString.isEmpty() ? "ephemeral" : "|ephemeral";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_INSTANT;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_APPOP) != 0) {
            flagsToString += flagsToString.isEmpty() ? "appop" : "|appop";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_APPOP;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
            flagsToString += flagsToString.isEmpty() ? "development" : "|development";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_DEVELOPMENT;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_INSTALLER) != 0) {
            flagsToString += flagsToString.isEmpty() ? "installer" : "|installer";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_INSTALLER;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_PRE23) != 0) {
            flagsToString += flagsToString.isEmpty() ? "pre23" : "|pre23";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_PRE23;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0) {
            flagsToString += flagsToString.isEmpty() ? "privileged" : "|privileged";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_PRIVILEGED;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_PREINSTALLED) != 0) {
            flagsToString += flagsToString.isEmpty() ? "preinstalled" : "|preinstalled";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_PREINSTALLED;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0) {
            flagsToString += flagsToString.isEmpty() ? "system" : "|system";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_SYSTEM;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_SETUP) != 0) {
            flagsToString += flagsToString.isEmpty() ? "setup" : "|setup";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_SETUP;
        }
        if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_VERIFIER) != 0) {
            flagsToString += flagsToString.isEmpty() ? "verifier" : "|verifier";
            protectionLevel &= ~PermissionInfo.PROTECTION_FLAG_VERIFIER;
        }
        protectionLevel &= ~(PermissionInfo.PROTECTION_NORMAL
                | PermissionInfo.PROTECTION_DANGEROUS
                | PermissionInfo.PROTECTION_SIGNATURE);
        if (protectionLevel != 0) {
            flagsToString += flagsToString.isEmpty() ? Integer.toHexString(protectionLevel)
                    : "|" + Integer.toHexString(protectionLevel);
        }
        return flagsToString;
    }
}
