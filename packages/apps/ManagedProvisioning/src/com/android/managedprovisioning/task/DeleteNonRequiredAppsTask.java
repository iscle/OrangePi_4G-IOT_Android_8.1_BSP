/*
 * Copyright 2014, The Android Open Source Project
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

package com.android.managedprovisioning.task;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.nonrequiredapps.NonRequiredAppsLogic;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deletes all non-required apps.
 *
 * This task may be run when a profile (both for managed device and managed profile) is created.
 * In that case the firstTimeCreation flag should be true.
 *
 * It should also be run after a system update with firstTimeCreation false. Note that only
 * newly installed system apps will be deleted.
 */
public class DeleteNonRequiredAppsTask extends AbstractProvisioningTask {
    private final PackageManager mPm;
    private final NonRequiredAppsLogic mLogic;

    public DeleteNonRequiredAppsTask(
            boolean firstTimeCreation,
            Context context,
            ProvisioningParams params,
            Callback callback) {
        this(
                context,
                params,
                callback,
                new NonRequiredAppsLogic(context, firstTimeCreation, params));
    }

    @VisibleForTesting
    DeleteNonRequiredAppsTask(
            Context context,
            ProvisioningParams params,
            Callback callback,
            NonRequiredAppsLogic logic) {
        super(context, params, callback);

        mPm = checkNotNull(context.getPackageManager());
        mLogic = checkNotNull(logic);
    }

    @Override
    public void run(int userId) {
        Set<String> packagesToDelete = mLogic.getSystemAppsToRemove(userId);
        mLogic.maybeTakeSystemAppsSnapshot(userId);

        // Remove all packages that are not currently installed
        removeNonInstalledPackages(packagesToDelete, userId);

        if (packagesToDelete.isEmpty()) {
            success();
            return;
        }

        PackageDeleteObserver packageDeleteObserver =
                new PackageDeleteObserver(packagesToDelete.size());
        for (String packageName : packagesToDelete) {
            ProvisionLogger.logd("Deleting package [" + packageName + "] as user " + userId);
            mPm.deletePackageAsUser(packageName, packageDeleteObserver,
                    PackageManager.DELETE_SYSTEM_APP, userId);
        }
    }

    private void removeNonInstalledPackages(Set<String> packages, int userId) {
        Set<String> toBeRemoved = new HashSet<>();
        for (String packageName : packages) {
            try {
                PackageInfo info = mPm.getPackageInfoAsUser(
                    packageName, 0 /* default flags */,
                    userId);
                if (info == null) {
                    toBeRemoved.add(packageName);
                }
            } catch (PackageManager.NameNotFoundException e) {
                toBeRemoved.add(packageName);
            }
        }
        packages.removeAll(toBeRemoved);
    }

    @Override
    public int getStatusMsgId() {
        return R.string.progress_delete_non_required_apps;
    }

    /**
     * Runs the next task when all packages have been deleted or shuts down the activity if package
     * deletion fails.
     */
    class PackageDeleteObserver extends IPackageDeleteObserver.Stub {
        private final AtomicInteger mPackageCount = new AtomicInteger(0);

        public PackageDeleteObserver(int packageCount) {
            this.mPackageCount.set(packageCount);
        }

        @Override
        public void packageDeleted(String packageName, int returnCode) {
            if (returnCode != PackageManager.DELETE_SUCCEEDED) {
                ProvisionLogger.logw(
                        "Could not finish the provisioning: package deletion failed");
                error(0);
                return;
            }
            int currentPackageCount = mPackageCount.decrementAndGet();
            if (currentPackageCount == 0) {
                ProvisionLogger.logi("All non-required system apps with launcher icon, "
                        + "and all disallowed apps have been uninstalled.");
                success();
            }
        }
    }
}
