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

package com.android.permissionutils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionInfo;
import android.os.UserHandle;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * A utility to dump or grant all revoked runtime permissions
 */
public class GrantPermissionUtil {
    private static final String LOG_TAG = GrantPermissionUtil.class.getSimpleName();

    public static void grantAllPermissions (Context context) {
        PackageManager pm = context.getPackageManager();
        for (PackageInfo pkgInfo : getPackageInfos(context)) {
            List<String> missingPermissions = getMissingPermissions(context, pkgInfo);
            if (!missingPermissions.isEmpty()) {
                for (String permission : missingPermissions) {
                    pm.grantRuntimePermission(pkgInfo.packageName, permission, UserHandle.OWNER);
                }
            }
        }
    }

    public static void dumpMissingPermissions (Context context) {
        for (PackageInfo pkgInfo : getPackageInfos(context)) {
            List<String> missingPermissions = getMissingPermissions(context, pkgInfo);
            if (!missingPermissions.isEmpty()) {
                Log.e(LOG_TAG, String.format("Missing permissions for %s", pkgInfo.packageName));
                for (String permission : missingPermissions) {
                    Log.e(LOG_TAG, "    " + permission);
                }
            }
        }
    }

    private static List<PackageInfo> getPackageInfos(Context context) {
        return context.getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
    }

    private static List<String> getMissingPermissions(Context context, PackageInfo info) {
        // No requested permissions
        if (info.requestedPermissions == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        // Iterate through requested permissions for denied ones
        for (String permission : info.requestedPermissions) {
            PermissionInfo pi = null;
            try {
                pi = pm.getPermissionInfo(permission, 0);
            } catch (NameNotFoundException nnfe) {
                // ignore
            }
            if (pi == null) {
                continue;
            }
            if (!isRuntime(pi)) {
                continue;
            }
            int flag = pm.checkPermission(permission, info.packageName);
            if (flag == PackageManager.PERMISSION_DENIED) {
                result.add(permission);
            }
        }
        return result;
    }

    private static boolean isRuntime(PermissionInfo pi) {
        return (pi.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE)
                == PermissionInfo.PROTECTION_DANGEROUS;
    }
}
