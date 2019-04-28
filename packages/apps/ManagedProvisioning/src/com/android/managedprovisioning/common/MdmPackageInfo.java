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

package com.android.managedprovisioning.common;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import com.android.internal.annotations.Immutable;

/**
 * Information relating to the currently installed MDM package manager.
 */
@Immutable
public final class MdmPackageInfo {
    // ToDo: add toString and equals for better testability.

    public final Drawable packageIcon;
    public final String appLabel;

    /**
     * Default constructor.
     */
    public MdmPackageInfo(@NonNull Drawable packageIcon, @NonNull String appLabel) {
        this.packageIcon = checkNotNull(packageIcon, "package icon must not be null");
        this.appLabel = checkNotNull(appLabel, "app label must not be null");
    }

    /**
     * Constructs an {@link MdmPackageInfo} object by reading the data from the package manager.
     *
     * @param context a {@link Context} object
     * @param packageName the name of the mdm package
     * @return an {@link MdMPackageInfo} object for the given package, or {@code null} if the
     *         package does not exist on the device
     */
    @Nullable
    public static MdmPackageInfo createFromPackageName(@NonNull Context context,
            @Nullable String packageName) {
        if (packageName != null) {
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo ai = pm.getApplicationInfo(packageName, /* default flags */ 0);
                return new MdmPackageInfo(pm.getApplicationIcon(packageName),
                        pm.getApplicationLabel(ai).toString());
            } catch (PackageManager.NameNotFoundException e) {
                // Package does not exist, ignore. Should never happen.
                ProvisionLogger.logw("Package not currently installed: " + packageName);
            }
        }
        return null;
    }
}
