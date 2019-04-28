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
 * limitations under the License
 */

package com.android.tv.settings.device.apps;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.TextUtils;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.List;

public class AppStoragePreference extends AppActionPreference {
    private final PackageManager mPackageManager;
    private final StorageManager mStorageManager;

    public AppStoragePreference(Context context, ApplicationsState.AppEntry entry) {
        super(context, entry);
        mPackageManager = context.getPackageManager();
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        refresh();
    }

    public void refresh() {
        final ApplicationInfo applicationInfo = mEntry.info;
        final VolumeInfo volumeInfo = mPackageManager.getPackageCurrentVolume(applicationInfo);
        final List<VolumeInfo> candidates =
                mPackageManager.getPackageCandidateVolumes(applicationInfo);
        if (candidates.size() > 1 ||
                (candidates.size() == 1 && !candidates.contains(volumeInfo))) {
            setIntent(MoveAppActivity
                    .getLaunchIntent(getContext(), mEntry.info.packageName, getAppName()));
        }

        setTitle(R.string.device_apps_app_management_storage_used);

        final String volumeDesc = mStorageManager.getBestVolumeDescription(volumeInfo);
        final String size = mEntry.sizeStr;
        if (TextUtils.isEmpty(size)) {
            setSummary(R.string.storage_calculating_size);
        } else {
            setSummary(getContext().getString(R.string.device_apps_app_management_storage_used_desc,
                    mEntry.sizeStr, volumeDesc));
        }

    }

    private String getAppName() {
        mEntry.ensureLabel(getContext());
        return mEntry.label;
    }
}
