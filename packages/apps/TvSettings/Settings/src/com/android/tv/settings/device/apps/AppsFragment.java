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

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class AppsFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "AppsFragment";

    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSessionSystem;
    private ApplicationsState.AppFilter mFilterSystem;
    private ApplicationsState.Session mSessionDownloaded;
    private ApplicationsState.AppFilter mFilterDownloaded;

    private PreferenceGroup mSystemPreferenceGroup;
    private PreferenceGroup mDownloadedPreferenceGroup;

    private final Handler mHandler = new Handler();
    private final Map<PreferenceGroup,
            ArrayList<ApplicationsState.AppEntry>> mUpdateMap = new ArrayMap<>(3);
    private long mRunAt = Long.MIN_VALUE;
    private final Runnable mUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            for (final PreferenceGroup group : mUpdateMap.keySet()) {
                final ArrayList<ApplicationsState.AppEntry> entries = mUpdateMap.get(group);
                updateAppListInternal(group, entries);
            }
            mUpdateMap.clear();
            mRunAt = 0;
        }
    };

    public static void prepareArgs(Bundle b, String volumeUuid, String volumeName) {
        b.putString(AppsActivity.EXTRA_VOLUME_UUID, volumeUuid);
        b.putString(AppsActivity.EXTRA_VOLUME_NAME, volumeName);
    }

    public static AppsFragment newInstance(String volumeUuid, String volumeName) {
        final Bundle b = new Bundle(2);
        prepareArgs(b, volumeUuid, volumeName);
        final AppsFragment f = new AppsFragment();
        f.setArguments(b);
        return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mApplicationsState = ApplicationsState.getInstance(getActivity().getApplication());

        final String volumeUuid = getArguments().getString(AppsActivity.EXTRA_VOLUME_UUID);
        final String volumeName = getArguments().getString(AppsActivity.EXTRA_VOLUME_NAME);

        // The UUID of internal storage is null, so we check if there's a volume name to see if we
        // should only be showing the apps on the internal storage or all apps.
        if (!TextUtils.isEmpty(volumeUuid) || !TextUtils.isEmpty(volumeName)) {
            ApplicationsState.AppFilter volumeFilter =
                    new ApplicationsState.VolumeFilter(volumeUuid);

            mFilterSystem =
                    new ApplicationsState.CompoundFilter(FILTER_SYSTEM, volumeFilter);
            mFilterDownloaded =
                    new ApplicationsState.CompoundFilter(FILTER_DOWNLOADED, volumeFilter);
        } else {
            mFilterSystem = FILTER_SYSTEM;
            mFilterDownloaded = FILTER_DOWNLOADED;
        }

        mSessionSystem = mApplicationsState.newSession(new RowUpdateCallbacks() {
            @Override
            protected void doRebuild() {
                rebuildSystem();
            }

            @Override
            public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                updateAppList(mSystemPreferenceGroup, apps);
            }
        });
        rebuildSystem();

        mSessionDownloaded = mApplicationsState.newSession(new RowUpdateCallbacks() {
            @Override
            protected void doRebuild() {
                rebuildDownloaded();
            }

            @Override
            public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                updateAppList(mDownloadedPreferenceGroup, apps);
            }
        });
        rebuildDownloaded();

    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // TODO: show volume name somewhere?

        setPreferencesFromResource(R.xml.apps, null);

        final String volumeUuid = getArguments().getString(AppsActivity.EXTRA_VOLUME_UUID);

        final Preference permissionPreference = findPreference("Permissions");
        permissionPreference.setVisible(TextUtils.isEmpty(volumeUuid));

        mDownloadedPreferenceGroup = (PreferenceGroup) findPreference("DownloadedPreferenceGroup");

        mSystemPreferenceGroup = (PreferenceGroup) findPreference("SystemPreferenceGroup");
        mSystemPreferenceGroup.setVisible(TextUtils.isEmpty(volumeUuid));
    }

    @Override
    public void onResume() {
        super.onResume();
        mSessionSystem.resume();
        mSessionDownloaded.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mSessionSystem.pause();
        mSessionDownloaded.pause();
    }

    private void rebuildSystem() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSessionSystem.rebuild(mFilterSystem, ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(mSystemPreferenceGroup, apps);
        }
    }

    private void rebuildDownloaded() {
        ArrayList<ApplicationsState.AppEntry> apps =
                mSessionDownloaded.rebuild(mFilterDownloaded, ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            updateAppList(mDownloadedPreferenceGroup, apps);
        }
    }

    private void updateAppList(PreferenceGroup group,
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (group == null) {
            Log.d(TAG, "Not updating list for null group");
            return;
        }
        mUpdateMap.put(group, entries);

        // We can get spammed with updates, so coalesce them to reduce jank and flicker
        if (mRunAt == Long.MIN_VALUE) {
            // First run, no delay
            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.post(mUpdateRunnable);
        } else {
            if (mRunAt == 0) {
                mRunAt = SystemClock.uptimeMillis() + 1000;
            }
            int delay = (int) (mRunAt - SystemClock.uptimeMillis());
            delay = delay < 0 ? 0 : delay;

            mHandler.removeCallbacks(mUpdateRunnable);
            mHandler.postDelayed(mUpdateRunnable, delay);
        }
    }

    private void updateAppListInternal(PreferenceGroup group,
            ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            final Set<String> touched = new ArraySet<>(entries.size());
            for (final ApplicationsState.AppEntry entry : entries) {
                final String packageName = entry.info.packageName;
                Preference recycle = group.findPreference(packageName);
                if (recycle == null) {
                    recycle = new Preference(getPreferenceManager().getContext());
                }
                final Preference newPref = bindPreference(recycle, entry);
                group.addPreference(newPref);
                touched.add(packageName);
            }
            for (int i = 0; i < group.getPreferenceCount();) {
                final Preference pref = group.getPreference(i);
                if (touched.contains(pref.getKey())) {
                    i++;
                } else {
                    group.removePreference(pref);
                }
            }
        }
    }

    /**
     * Creates or updates a preference according to an {@link ApplicationsState.AppEntry} object
     * @param preference If non-null, updates this preference object, otherwise creates a new one
     * @param entry Info to populate preference
     * @return Updated preference entry
     */
    private Preference bindPreference(@NonNull Preference preference,
            ApplicationsState.AppEntry entry) {
        preference.setKey(entry.info.packageName);
        entry.ensureLabel(getContext());
        preference.setTitle(entry.label);
        preference.setSummary(entry.sizeStr);
        preference.setFragment(AppManagementFragment.class.getName());
        AppManagementFragment.prepareArgs(preference.getExtras(), entry.info.packageName);
        preference.setIcon(entry.icon);
        return preference;
    }

    private abstract class RowUpdateCallbacks implements ApplicationsState.Callbacks {

        protected abstract void doRebuild();

        @Override
        public void onRunningStateChanged(boolean running) {
            doRebuild();
        }

        @Override
        public void onPackageListChanged() {
            doRebuild();
        }

        @Override
        public void onPackageIconChanged() {
            doRebuild();
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            doRebuild();
        }

        @Override
        public void onAllSizesComputed() {
            doRebuild();
        }

        @Override
        public void onLauncherInfoChanged() {
            doRebuild();
        }

        @Override
        public void onLoadEntriesCompleted() {
            doRebuild();
        }
    }

    private static final ApplicationsState.AppFilter FILTER_SYSTEM =
            new ApplicationsState.AppFilter() {

                @Override
                public void init() {}

                @Override
                public boolean filterApp(ApplicationsState.AppEntry info) {
                    return (info.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                }
            };

    private static final ApplicationsState.AppFilter FILTER_DOWNLOADED =
            new ApplicationsState.AppFilter() {

                @Override
                public void init() {}

                @Override
                public boolean filterApp(ApplicationsState.AppEntry info) {
                    return (info.info.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
                }
            };

}
