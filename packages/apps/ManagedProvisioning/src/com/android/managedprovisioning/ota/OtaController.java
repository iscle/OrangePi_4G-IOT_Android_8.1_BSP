/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.ota;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.CrossProfileIntentFiltersSetter;
import com.android.managedprovisioning.task.DeleteNonRequiredAppsTask;
import com.android.managedprovisioning.task.DisableInstallShortcutListenersTask;
import com.android.managedprovisioning.task.DisallowAddUserTask;
import com.android.managedprovisioning.task.InstallExistingPackageTask;

/**
 * After a system update, this class resets the cross-profile intent filters and performs any
 * tasks necessary to bring the system up to date.
 */
public class OtaController {

    private static final String TELECOM_PACKAGE = "com.android.server.telecom";

    private final Context mContext;
    private final TaskExecutor mTaskExecutor;
    private final CrossProfileIntentFiltersSetter mCrossProfileIntentFiltersSetter;

    private final UserManager mUserManager;
    private final DevicePolicyManager mDevicePolicyManager;

    public OtaController(Context context) {
        this(context, new TaskExecutor(), new CrossProfileIntentFiltersSetter(context));
    }

    @VisibleForTesting
    OtaController(Context context, TaskExecutor taskExecutor,
            CrossProfileIntentFiltersSetter crossProfileIntentFiltersSetter) {
        mContext = checkNotNull(context);
        mTaskExecutor = checkNotNull(taskExecutor);
        mCrossProfileIntentFiltersSetter = checkNotNull(crossProfileIntentFiltersSetter);

        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
        mDevicePolicyManager = (DevicePolicyManager) context.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
    }

    public void run() {
        if (mContext.getUserId() != UserHandle.USER_SYSTEM) {
            return;
        }

        // Check for device owner.
        final int deviceOwnerUserId = mDevicePolicyManager.getDeviceOwnerUserId();
        if (deviceOwnerUserId != UserHandle.USER_NULL) {
            addDeviceOwnerTasks(deviceOwnerUserId, mContext);
        }

        for (UserInfo userInfo : mUserManager.getUsers()) {
            if (userInfo.isManagedProfile()) {
                addManagedProfileTasks(userInfo.id, mContext);
            } else {
                // if this user has managed profiles, reset the cross-profile intent filters between
                // this user and its managed profiles.
                mCrossProfileIntentFiltersSetter.resetFilters(userInfo.id);
            }
        }
    }

    void addDeviceOwnerTasks(final int userId, Context context) {
        ComponentName deviceOwner = mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser();
        if (deviceOwner == null) {
            // Shouldn't happen
            ProvisionLogger.loge("No device owner found.");
            return;
        }

        // Build a set of fake params to be able to run the tasks
        ProvisioningParams fakeParams = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(deviceOwner)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .build();

        mTaskExecutor.execute(userId,
                new DeleteNonRequiredAppsTask(false, context, fakeParams, mTaskExecutor));
        mTaskExecutor.execute(userId,
                new DisallowAddUserTask(context, fakeParams, mTaskExecutor));
    }

    void addManagedProfileTasks(final int userId, Context context) {
        mUserManager.setUserRestriction(UserManager.DISALLOW_WALLPAPER, true,
                UserHandle.of(userId));
        // Enabling telecom package as it supports managed profiles from N.
        mTaskExecutor.execute(userId,
                new InstallExistingPackageTask(TELECOM_PACKAGE, context, null, mTaskExecutor));

        ComponentName profileOwner = mDevicePolicyManager.getProfileOwnerAsUser(userId);
        if (profileOwner == null) {
            // Shouldn't happen.
            ProvisionLogger.loge("No profile owner on managed profile " + userId);
            return;
        }

        // Build a set of fake params to be able to run the tasks
        ProvisioningParams fakeParams = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(profileOwner)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .build();
        mTaskExecutor.execute(userId,
                new DisableInstallShortcutListenersTask(context, fakeParams, mTaskExecutor));
        mTaskExecutor.execute(userId,
                new DeleteNonRequiredAppsTask(false, context, fakeParams, mTaskExecutor));
    }
}
