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

package com.android.tv.settings.device.apps.specialaccess;

import android.app.ActivityThread;
import android.app.AppOpsManager;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.internal.util.ArrayUtils;
import com.android.settingslib.applications.ApplicationsState;

/**
 * Base class for managing app ops
 */
public abstract class ManageAppOp extends ManageApplications {
    private static final String TAG = "ManageAppOps";

    private IPackageManager mIPackageManager;
    private AppOpsManager mAppOpsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mIPackageManager = ActivityThread.getPackageManager();
        mAppOpsManager = getContext().getSystemService(AppOpsManager.class);
        super.onCreate(savedInstanceState);
    }

    @NonNull
    @Override
    public ApplicationsState.AppFilter getFilter() {
        return new ApplicationsState.AppFilter() {
            @Override
            public void init() {
            }

            @Override
            public boolean filterApp(ApplicationsState.AppEntry entry) {
                entry.extraInfo = createPermissionStateFor(entry.info.packageName, entry.info.uid);
                return !shouldIgnorePackage(entry.info.packageName)
                        && ((PermissionState) entry.extraInfo).isPermissible();
            }
        };
    }

    /**
     * @return AppOps code
     */
    public abstract int getAppOpsOpCode();

    /**
     * @return Manifest permission string
     */
    public abstract String getPermission();

    private boolean hasRequestedAppOpPermission(String permission, String packageName) {
        try {
            String[] packages = mIPackageManager.getAppOpPermissionPackages(permission);
            return ArrayUtils.contains(packages, packageName);
        } catch (RemoteException exc) {
            Log.e(TAG, "PackageManager dead. Cannot get permission info");
            return false;
        }
    }

    private boolean hasPermission(int uid) {
        try {
            int result = mIPackageManager.checkUidPermission(getPermission(), uid);
            return result == PackageManager.PERMISSION_GRANTED;
        } catch (RemoteException e) {
            Log.e(TAG, "PackageManager dead. Cannot get permission info");
            return false;
        }
    }

    private int getAppOpMode(int uid, String packageName) {
        return mAppOpsManager.checkOpNoThrow(getAppOpsOpCode(), uid, packageName);
    }

    private PermissionState createPermissionStateFor(String packageName, int uid) {
        return new PermissionState(
                hasRequestedAppOpPermission(getPermission(), packageName),
                hasPermission(uid),
                getAppOpMode(uid, packageName));
    }

    /*
     * Checks for packages that should be ignored for further processing
     */
    private boolean shouldIgnorePackage(String packageName) {
        return packageName.equals("android") || packageName.equals(getContext().getPackageName());
    }

    /**
     * Collection of information to be used as {@link ApplicationsState.AppEntry#extraInfo} objects
     */
    protected static class PermissionState {
        public final boolean permissionRequested;
        public final boolean permissionGranted;
        public final int appOpMode;

        private PermissionState(boolean permissionRequested, boolean permissionGranted,
                int appOpMode) {
            this.permissionRequested = permissionRequested;
            this.permissionGranted = permissionGranted;
            this.appOpMode = appOpMode;
        }

        /**
         * @return True if the permission is granted
         */
        public boolean isAllowed() {
            if (appOpMode == AppOpsManager.MODE_DEFAULT) {
                return permissionGranted;
            } else {
                return appOpMode == AppOpsManager.MODE_ALLOWED;
            }
        }

        /**
         * @return True if the permission is relevant
         */
        public boolean isPermissible() {
            return appOpMode != AppOpsManager.MODE_DEFAULT || permissionRequested;
        }

        @Override
        public String toString() {
            return "[permissionGranted: " + permissionGranted
                    + ", permissionRequested: " + permissionRequested
                    + ", appOpMode: " + appOpMode
                    + "]";
        }
    }
}
