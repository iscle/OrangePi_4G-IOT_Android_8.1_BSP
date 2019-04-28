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

import android.Manifest;
import android.app.AppOpsManager;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.Comparator;

/**
 * Fragment for controlling if apps can monitor app usage
 */
@Keep
public class ManageAppUsageAccess extends ManageAppOp {

    private AppOpsManager mAppOpsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppOpsManager = getContext().getSystemService(AppOpsManager.class);
    }

    @Override
    public int getAppOpsOpCode() {
        return AppOpsManager.OP_GET_USAGE_STATS;
    }

    @Override
    public String getPermission() {
        return Manifest.permission.PACKAGE_USAGE_STATS;
    }

    @Nullable
    @Override
    public Comparator<ApplicationsState.AppEntry> getComparator() {
        return ApplicationsState.ALPHA_COMPARATOR;
    }

    @NonNull
    @Override
    public Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        final TwoStatePreference switchPref = (SwitchPreference) preference;
        switchPref.setTitle(entry.label);
        switchPref.setKey(entry.info.packageName);
        mApplicationsState.ensureIcon(entry);
        switchPref.setIcon(entry.icon);
        switchPref.setOnPreferenceChangeListener((pref, newValue) -> {
            setAppUsageAccess(entry, (Boolean) newValue);
            return true;
        });

        switchPref.setSummary(getPreferenceSummary(entry));
        switchPref.setChecked(((PermissionState) entry.extraInfo).isAllowed());

        return switchPref;
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchPreference(getPreferenceManager().getContext());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.app_usage_access, null);
    }

    private CharSequence getPreferenceSummary(ApplicationsState.AppEntry entry) {
        if (entry.extraInfo instanceof PermissionState) {
            return getContext().getText(((PermissionState) entry.extraInfo).isPermissible()
                    ? R.string.app_permission_summary_allowed
                    : R.string.app_permission_summary_not_allowed);
        } else {
            return null;
        }
    }

    private void setAppUsageAccess(ApplicationsState.AppEntry entry, boolean grant) {
        mAppOpsManager.setMode(AppOpsManager.OP_GET_USAGE_STATS,
                entry.info.uid, entry.info.packageName,
                grant ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
        updateAppList();
    }
}
