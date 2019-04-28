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

import android.app.Application;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Base class for fragments which manage apps
 */
public abstract class ManageApplications extends LeanbackPreferenceFragment {
    // Use this preference key for a header pref not removed during refresh
    private static final String HEADER_KEY = "header";
    private ApplicationsState.Session mAppSession;
    protected ApplicationsState mApplicationsState;
    private final ApplicationsState.Callbacks mAppSessionCallbacks =
            new ApplicationsState.Callbacks() {

                @Override
                public void onRunningStateChanged(boolean running) {
                    updateAppList();
                }

                @Override
                public void onPackageListChanged() {
                    updateAppList();
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                    updateAppList(apps);
                }

                @Override
                public void onPackageIconChanged() {
                    updateAppList();
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                    updateAppList();
                }

                @Override
                public void onAllSizesComputed() {
                    updateAppList();
                }

                @Override
                public void onLauncherInfoChanged() {
                    updateAppList();
                }

                @Override
                public void onLoadEntriesCompleted() {
                    updateAppList();
                }
            };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApplicationsState = ApplicationsState.getInstance(
                (Application) getContext().getApplicationContext());
        mAppSession = mApplicationsState.newSession(mAppSessionCallbacks);
        updateAppList();
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppSession.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mAppSession.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAppSession.release();
    }

    protected void updateAppList() {
        ApplicationsState.AppFilter filter = new ApplicationsState.CompoundFilter(getFilter(),
                ApplicationsState.FILTER_NOT_HIDE);
        ArrayList<ApplicationsState.AppEntry> apps = mAppSession.rebuild(filter, getComparator());
        if (apps != null) {
            updateAppList(apps);
        }
    }

    private void updateAppList(ArrayList<ApplicationsState.AppEntry> apps) {
        PreferenceGroup group = getPreferenceScreen();
        // Because we're sorting the app entries, we should remove-all to ensure that sort order
        // is retained
        final List<Preference> newList = new ArrayList<>(apps.size() + 1);
        for (final ApplicationsState.AppEntry entry : apps) {
            final String packageName = entry.info.packageName;
            Preference recycle = group.findPreference(packageName);
            if (recycle == null) {
                recycle = createAppPreference();
            }
            newList.add(bindPreference(recycle, entry));
        }
        final Preference header = findPreference(HEADER_KEY);
        group.removeAll();
        if (header != null) {
            group.addPreference(header);
        }
        if (newList.size() > 0) {
            for (Preference prefToAdd : newList) {
                group.addPreference(prefToAdd);
            }
        } else {
            final Preference empty = new Preference(getPreferenceManager().getContext());
            empty.setKey("empty");
            empty.setTitle(R.string.noApplications);
            empty.setEnabled(false);
            group.addPreference(empty);
        }
    }

    /**
     * Filter for apps
     */
    public abstract @NonNull ApplicationsState.AppFilter getFilter();

    /**
     * Comparator for sorting apps
     */
    public abstract @Nullable Comparator<ApplicationsState.AppEntry> getComparator();

    /**
     * Bind preference item with data from entry
     */
    public abstract @NonNull Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry);

    /**
     * Create a new preference to later bind in
     * {@link #bindPreference(Preference, ApplicationsState.AppEntry)}
     */
    public abstract @NonNull Preference createAppPreference();
}
