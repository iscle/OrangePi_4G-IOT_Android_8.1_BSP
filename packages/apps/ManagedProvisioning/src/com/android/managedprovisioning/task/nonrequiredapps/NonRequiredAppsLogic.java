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

package com.android.managedprovisioning.task.nonrequiredapps;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.IntDef;
import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Set;

/**
 * Logic that calculates which system apps should be removed during profile creation and subsequent
 * OTAs. It also decides whether a snapshot should be taken or not.
 */
public class NonRequiredAppsLogic {

    @IntDef({
            Case.OTA_LEAVE_APPS,
            Case.OTA_REMOVE_APPS,
            Case.NEW_PROFILE_LEAVE_APPS,
            Case.NEW_PROFILE_REMOVE_APPS
    })
    @Retention(RetentionPolicy.SOURCE)
    private @interface Case {
        int OTA_LEAVE_APPS = 0;
        int OTA_REMOVE_APPS = 1;
        int NEW_PROFILE_LEAVE_APPS = 2;
        int NEW_PROFILE_REMOVE_APPS = 3;
    }

    private final PackageManager mPackageManager;
    private final IPackageManager mIPackageManager;
    private final boolean mNewProfile;
    private final ProvisioningParams mParams;
    private final SystemAppsSnapshot mSnapshot;
    private final OverlayPackagesProvider mProvider;
    private final Utils mUtils;

    public NonRequiredAppsLogic(
            Context context,
            boolean newProfile,
            ProvisioningParams params) {
        this(
                context.getPackageManager(),
                AppGlobals.getPackageManager(),
                newProfile,
                params,
                new SystemAppsSnapshot(context),
                new OverlayPackagesProvider(context, params),
                new Utils());
    }

    @VisibleForTesting
    NonRequiredAppsLogic(
            PackageManager packageManager,
            IPackageManager iPackageManager,
            boolean newProfile,
            ProvisioningParams params,
            SystemAppsSnapshot snapshot,
            OverlayPackagesProvider provider,
            Utils utils) {
        mPackageManager = checkNotNull(packageManager);
        mIPackageManager = checkNotNull(iPackageManager);
        mNewProfile = newProfile;
        mParams = checkNotNull(params);
        mSnapshot = checkNotNull(snapshot);
        mProvider = checkNotNull(provider);
        mUtils = checkNotNull(utils);
    }

    public Set<String> getSystemAppsToRemove(int userId) {
        if (!shouldDeleteSystemApps(userId)) {
            return Collections.emptySet();
        }

        // Start with all system apps
        Set<String> newSystemApps = mUtils.getCurrentSystemApps(mIPackageManager, userId);

        // Remove the ones that were already present in the last snapshot only when OTA
        if (!mNewProfile) {
            newSystemApps.removeAll(mSnapshot.getSnapshot(userId));
        }

        // Get the packages from the black/white lists
        Set<String> packagesToDelete = mProvider.getNonRequiredApps(userId);

        // Retain only new system apps
        packagesToDelete.retainAll(newSystemApps);

        return packagesToDelete;
    }

    public void maybeTakeSystemAppsSnapshot(int userId) {
        if (shouldDeleteSystemApps(userId)) {
            mSnapshot.takeNewSnapshot(userId);
        }
    }

    private boolean shouldDeleteSystemApps(int userId) {
        @Case int which = getCase(userId);
        return (Case.NEW_PROFILE_REMOVE_APPS == which) || (Case.OTA_REMOVE_APPS == which);
    }

    private @Case int getCase(int userId) {
        if (mNewProfile) {
            if (mParams.leaveAllSystemAppsEnabled) {
                return Case.NEW_PROFILE_LEAVE_APPS;
            } else {
                return Case.NEW_PROFILE_REMOVE_APPS;
            }
        } else {
            if (mSnapshot.hasSnapshot(userId)) {
                return Case.OTA_REMOVE_APPS;
            } else {
                return Case.OTA_LEAVE_APPS;
            }
        }
    }
}
