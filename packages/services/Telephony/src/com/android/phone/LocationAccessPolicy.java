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

package com.android.phone;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import java.util.List;

/**
 * Helper for performing location access checks.
 */
final class LocationAccessPolicy {

    private LocationAccessPolicy() {
        /* do nothing - hide ctor */
    }

    /**
     * API to determine if the caller has permissions to get cell location.
     *
     * @param pkgName Package name of the application requesting access
     * @param uid The uid of the package
     * @param message Message to add to the exception if no location permission
     * @return boolean true or false if permissions is granted
     */
    static boolean canAccessCellLocation(@NonNull Context context, @NonNull String pkgName,
            int uid, String message) throws SecurityException {
        context.getSystemService(AppOpsManager.class).checkPackage(uid, pkgName);
        // We always require the location permission and also require the
        // location mode to be on for non-legacy apps. Legacy apps are
        // required to be in the foreground to at least mitigate the case
        // where a legacy app the user is not using tracks their location.

        // Grating ACCESS_FINE_LOCATION to an app automatically grants it ACCESS_COARSE_LOCATION.
        context.enforceCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION, message);
        final int opCode = AppOpsManager.permissionToOpCode(
                Manifest.permission.ACCESS_COARSE_LOCATION);
        if (opCode != AppOpsManager.OP_NONE && context.getSystemService(AppOpsManager.class)
                .noteOp(opCode, uid, pkgName) != AppOpsManager.MODE_ALLOWED) {
            return false;
        }
        if (!isLocationModeEnabled(context, UserHandle.getUserId(uid))
                && !isLegacyForeground(context, pkgName)) {
            return false;
        }
        // If the user or profile is current, permission is granted.
        // Otherwise, uid must have INTERACT_ACROSS_USERS_FULL permission.
        return isCurrentProfile(context, uid) || checkInteractAcrossUsersFull(context);
    }

    private static boolean isLocationModeEnabled(@NonNull Context context, @UserIdInt int userId) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF, userId)
                != Settings.Secure.LOCATION_MODE_OFF;
    }

    private static boolean isLegacyForeground(@NonNull Context context, @NonNull String pkgName) {
        return isLegacyVersion(context, pkgName) && isForegroundApp(context, pkgName);
    }

    private static boolean isLegacyVersion(@NonNull Context context, @NonNull String pkgName) {
        try {
            if (context.getPackageManager().getApplicationInfo(pkgName, 0)
                    .targetSdkVersion <= Build.VERSION_CODES.O) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            // In case of exception, assume known app (more strict checking)
            // Note: This case will never happen since checkPackage is
            // called to verify validity before checking app's version.
        }
        return false;
    }

    private static boolean isForegroundApp(@NonNull Context context, @NonNull String pkgName) {
        final ActivityManager am = context.getSystemService(ActivityManager.class);
        final List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            return pkgName.equals(tasks.get(0).topActivity.getPackageName());
        }
        return false;
    }

    private static boolean checkInteractAcrossUsersFull(@NonNull Context context) {
        return context.checkCallingOrSelfPermission(
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isCurrentProfile(@NonNull Context context, int uid) {
        final int currentUser = ActivityManager.getCurrentUser();
        final int callingUserId = UserHandle.getUserId(uid);
        if (callingUserId == currentUser) {
            return true;
        } else {
            List<UserInfo> userProfiles = context.getSystemService(
                    UserManager.class).getProfiles(currentUser);
            for (UserInfo user: userProfiles) {
                if (user.id == callingUserId) {
                    return true;
                }
            }
        }
        return false;
    }
}
