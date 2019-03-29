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
 * limitations under the License
 */

package android.permission2.cts;

import com.android.compatibility.common.util.SystemUtil;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static android.content.pm.PackageManager.GET_PERMISSIONS;

/**
 * Tests enforcement of signature|privileged permission whitelist:
 * <ul>
 * <li>Report what is granted into the CTS log
 * <li>Ensure all priv permissions are exclusively granted to applications declared in
 * &lt;privapp-permissions&gt;
 * </ul>
 */
public class PrivappPermissionsTest extends AndroidTestCase {

    private static final String TAG = "PrivappPermissionsTest";

    private static final String PLATFORM_PACKAGE_NAME = "android";

    public void testPrivappPermissionsEnforcement() throws Exception {
        Set<String> platformPrivPermissions = new HashSet<>();
        PackageManager pm = getContext().getPackageManager();
        PackageInfo platformPackage = pm.getPackageInfo(PLATFORM_PACKAGE_NAME,
                PackageManager.GET_PERMISSIONS);

        for (PermissionInfo permission : platformPackage.permissions) {
            int protectionLevel = permission.protectionLevel;
            if ((protectionLevel & PermissionInfo.PROTECTION_FLAG_PRIVILEGED) != 0) {
                platformPrivPermissions.add(permission.name);
            }
        }

        List<PackageInfo> installedPackages = pm
                .getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES | GET_PERMISSIONS);

        for (PackageInfo pkg : installedPackages) {
            Set<String> requestedPrivPermissions = new TreeSet<>();
            Set<String> grantedPrivPermissions = new TreeSet<>();
            String[] requestedPermissions = pkg.requestedPermissions;
            if (!pkg.applicationInfo.isPrivilegedApp()
                    || PLATFORM_PACKAGE_NAME.equals(pkg.packageName)) {
                continue;
            }
            if (requestedPermissions == null || requestedPermissions.length == 0) {
                continue;
            }
            // Collect 2 sets: requestedPermissions and grantedPrivPermissions
            for (int i = 0; i < requestedPermissions.length; i++) {
                String permission = requestedPermissions[i];
                if (platformPrivPermissions.contains(permission)) {
                    requestedPrivPermissions.add(permission);
                    if ((pkg.requestedPermissionsFlags[i]
                            & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        grantedPrivPermissions.add(permission);
                    }
                }
            }
            // If an app is requesting any privileged permissions, log the details and verify
            // that granted permissions are whitelisted
            if (!requestedPrivPermissions.isEmpty()) {
                Set<String> notGranted = new TreeSet<>(requestedPrivPermissions);
                notGranted.removeAll(grantedPrivPermissions);
                Set<String> whitelist = getPrivAppPermissions(pkg.packageName);
                Set<String> denylist = getPrivAppDenyPermissions(pkg.packageName);
                Log.i(TAG, "Application " + pkg.packageName + "."
                        + " Requested permissions: " + requestedPrivPermissions + "."
                        + " Granted permissions: " + grantedPrivPermissions + "."
                        + " Not granted: " + notGranted + "."
                        + " Whitelisted: " + whitelist + "."
                        + " Denylisted: " + denylist);

                Set<String> grantedNotInWhitelist = new TreeSet<>(grantedPrivPermissions);
                grantedNotInWhitelist.removeAll(whitelist);
                Set<String> notGrantedNotInDenylist = new TreeSet<>(notGranted);
                notGrantedNotInDenylist.removeAll(denylist);

                assertTrue("Not whitelisted permissions are granted for package "
                                + pkg.packageName + ": " + grantedNotInWhitelist,
                        grantedNotInWhitelist.isEmpty());

                assertTrue("Requested permissions not granted for package "
                                + pkg.packageName + ": " + notGrantedNotInDenylist,
                        notGrantedNotInDenylist.isEmpty());
            }
        }
    }

    private Set<String> getPrivAppPermissions(String packageName) throws IOException {
        String output = SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(),
                "cmd package get-privapp-permissions " + packageName).trim();
        if (output.startsWith("{") && output.endsWith("}")) {
            String[] split = output.substring(1, output.length() - 1).split("\\s*,\\s*");
            return new LinkedHashSet<>(Arrays.asList(split));
        }
        return Collections.emptySet();
    }

    private Set<String> getPrivAppDenyPermissions(String packageName) throws IOException {
        String output = SystemUtil.runShellCommand(
                InstrumentationRegistry.getInstrumentation(),
                "cmd package get-privapp-deny-permissions " + packageName).trim();
        if (output.startsWith("{") && output.endsWith("}")) {
            String[] split = output.substring(1, output.length() - 1).split("\\s*,\\s*");
            return new LinkedHashSet<>(Arrays.asList(split));
        }
        return Collections.emptySet();
    }

}
