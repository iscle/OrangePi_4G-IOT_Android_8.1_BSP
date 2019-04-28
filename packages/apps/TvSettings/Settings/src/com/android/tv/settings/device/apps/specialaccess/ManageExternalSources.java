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
import android.os.UserHandle;
import android.os.UserManager;
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
 * Fragment for controlling if apps can install other apps
 */
@Keep
public class ManageExternalSources extends ManageAppOp {
    private AppOpsManager mAppOpsManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mAppOpsManager = getContext().getSystemService(AppOpsManager.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public int getAppOpsOpCode() {
        return AppOpsManager.OP_REQUEST_INSTALL_PACKAGES;
    }

    @Override
    public String getPermission() {
        return Manifest.permission.REQUEST_INSTALL_PACKAGES;
    }

    @Nullable
    @Override
    public Comparator<ApplicationsState.AppEntry> getComparator() {
        return ApplicationsState.ALPHA_COMPARATOR;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.manage_external_sources, null);
    }

    @NonNull
    @Override
    public Preference createAppPreference() {
        return new SwitchPreference(getPreferenceManager().getContext());
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
            setCanInstallApps(entry, (Boolean) newValue);
            return true;
        });

        PermissionState state = (PermissionState) entry.extraInfo;
        switchPref.setChecked(state.isAllowed());
        switchPref.setSummary(getPreferenceSummary(entry));
        switchPref.setEnabled(canChange(entry));
        return switchPref;
    }

    private boolean canChange(ApplicationsState.AppEntry entry) {
        final UserManager um = UserManager.get(getContext());
        final int userRestrictionSource = um.getUserRestrictionSource(
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserHandle.getUserHandleForUid(entry.info.uid));
        switch (userRestrictionSource) {
            case UserManager.RESTRICTION_SOURCE_DEVICE_OWNER:
            case UserManager.RESTRICTION_SOURCE_PROFILE_OWNER:
            case UserManager.RESTRICTION_SOURCE_SYSTEM:
                return false;
            default:
                return true;
        }
    }

    private CharSequence getPreferenceSummary(ApplicationsState.AppEntry entry) {
        final UserManager um = UserManager.get(getContext());
        final int userRestrictionSource = um.getUserRestrictionSource(
                UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                UserHandle.getUserHandleForUid(entry.info.uid));
        switch (userRestrictionSource) {
            case UserManager.RESTRICTION_SOURCE_DEVICE_OWNER:
            case UserManager.RESTRICTION_SOURCE_PROFILE_OWNER:
                return getContext().getString(R.string.disabled_by_admin);
            case UserManager.RESTRICTION_SOURCE_SYSTEM:
                return getContext().getString(R.string.disabled);
        }

        return getContext().getString(((PermissionState) entry.extraInfo).isAllowed()
                ? R.string.external_source_trusted
                : R.string.external_source_untrusted);
    }

    private void setCanInstallApps(ApplicationsState.AppEntry entry, boolean newState) {
        mAppOpsManager.setMode(AppOpsManager.OP_REQUEST_INSTALL_PACKAGES,
                entry.info.uid, entry.info.packageName,
                newState ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_ERRORED);
        updateAppList();
    }
}
