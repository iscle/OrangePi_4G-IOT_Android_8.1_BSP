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

package com.android.tv.settings.device.storage;

import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import com.android.settingslib.deviceinfo.StorageMeasurement;
import com.android.tv.settings.R;
import com.android.tv.settings.device.apps.AppsFragment;

import java.util.HashMap;
import java.util.List;

public class StorageFragment extends LeanbackPreferenceFragment {
    private static final String TAG = "StorageFragment";

    private static final String KEY_MIGRATE = "migrate";
    private static final String KEY_EJECT = "eject";
    private static final String KEY_ERASE = "erase";
    private static final String KEY_APPS_USAGE = "apps_usage";
    private static final String KEY_DCIM_USAGE = "dcim_usage";
    private static final String KEY_MUSIC_USAGE = "music_usage";
    private static final String KEY_DOWNLOADS_USAGE = "downloads_usage";
    private static final String KEY_CACHE_USAGE = "cache_usage";
    private static final String KEY_MISC_USAGE = "misc_usage";
    private static final String KEY_AVAILABLE = "available";

    private StorageManager mStorageManager;
    private PackageManager mPackageManager;

    private VolumeInfo mVolumeInfo;

    private StorageMeasurement mMeasure;
    private final StorageMeasurement.MeasurementReceiver mMeasurementReceiver =
            new MeasurementReceiver();
    private final StorageEventListener mStorageEventListener = new StorageEventListener();

    private Preference mMigratePref;
    private Preference mEjectPref;
    private Preference mErasePref;
    private StoragePreference mAppsUsagePref;
    private StoragePreference mDcimUsagePref;
    private StoragePreference mMusicUsagePref;
    private StoragePreference mDownloadsUsagePref;
    private StoragePreference mCacheUsagePref;
    private StoragePreference mMiscUsagePref;
    private StoragePreference mAvailablePref;

    public static void prepareArgs(Bundle bundle, VolumeInfo volumeInfo) {
        bundle.putString(VolumeInfo.EXTRA_VOLUME_ID, volumeInfo.getId());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mStorageManager = getContext().getSystemService(StorageManager.class);
        mPackageManager = getContext().getPackageManager();

        mVolumeInfo = mStorageManager.findVolumeById(
                getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID));

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStorageManager.registerListener(mStorageEventListener);
        startMeasurement();
    }

    @Override
    public void onResume() {
        super.onResume();
        mVolumeInfo = mStorageManager.findVolumeById(
                getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID));
        if (mVolumeInfo == null || !mVolumeInfo.isMountedReadable()) {
            getFragmentManager().popBackStack();
        } else {
            refresh();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mStorageManager.unregisterListener(mStorageEventListener);
        stopMeasurement();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.storage, null);

        final PreferenceScreen screen = getPreferenceScreen();
        screen.setTitle(mStorageManager.getBestVolumeDescription(mVolumeInfo));

        mMigratePref = findPreference(KEY_MIGRATE);
        mEjectPref = findPreference(KEY_EJECT);
        mErasePref = findPreference(KEY_ERASE);

        mAppsUsagePref = (StoragePreference) findPreference(KEY_APPS_USAGE);
        mDcimUsagePref = (StoragePreference) findPreference(KEY_DCIM_USAGE);
        mMusicUsagePref = (StoragePreference) findPreference(KEY_MUSIC_USAGE);
        mDownloadsUsagePref = (StoragePreference) findPreference(KEY_DOWNLOADS_USAGE);
        mCacheUsagePref = (StoragePreference) findPreference(KEY_CACHE_USAGE);
        mMiscUsagePref = (StoragePreference) findPreference(KEY_MISC_USAGE);
        mAvailablePref = (StoragePreference) findPreference(KEY_AVAILABLE);
    }

    private void refresh() {
        boolean showMigrate = false;
        final VolumeInfo currentExternal = mPackageManager.getPrimaryStorageCurrentVolume();
        // currentExternal will be null if the drive is not mounted. Don't offer the option to
        // migrate if so.
        if (currentExternal != null
                && !TextUtils.equals(currentExternal.getId(), mVolumeInfo.getId())) {
            final List<VolumeInfo> candidates =
                    mPackageManager.getPrimaryStorageCandidateVolumes();
            for (final VolumeInfo candidate : candidates) {
                if (TextUtils.equals(candidate.getId(), mVolumeInfo.getId())) {
                    showMigrate = true;
                    break;
                }
            }
        }

        mMigratePref.setVisible(showMigrate);
        mMigratePref.setIntent(
                MigrateStorageActivity.getLaunchIntent(getContext(), mVolumeInfo.getId(), true));

        final String description = mStorageManager.getBestVolumeDescription(mVolumeInfo);

        final boolean privateInternal = VolumeInfo.ID_PRIVATE_INTERNAL.equals(mVolumeInfo.getId());
        final boolean isPrivate = mVolumeInfo.getType() == VolumeInfo.TYPE_PRIVATE;

        mEjectPref.setVisible(!privateInternal);
        mEjectPref.setIntent(UnmountActivity.getIntent(getContext(), mVolumeInfo.getId(),
                description));
        mErasePref.setVisible(!privateInternal);
        if (isPrivate) {
            mErasePref.setIntent(
                    FormatActivity.getFormatAsPublicIntent(getContext(), mVolumeInfo.getDiskId()));
            mErasePref.setTitle(R.string.storage_format_as_public);
        } else {
            mErasePref.setIntent(
                    FormatActivity.getFormatAsPrivateIntent(getContext(), mVolumeInfo.getDiskId()));
            mErasePref.setTitle(R.string.storage_format_as_private);
        }

        mAppsUsagePref.setVisible(isPrivate);
        mAppsUsagePref.setFragment(AppsFragment.class.getName());
        AppsFragment.prepareArgs(mAppsUsagePref.getExtras(), mVolumeInfo.fsUuid, description);
        mDcimUsagePref.setVisible(isPrivate);
        mMusicUsagePref.setVisible(isPrivate);
        mDownloadsUsagePref.setVisible(isPrivate);
        mCacheUsagePref.setVisible(isPrivate);
        mCacheUsagePref.setFragment(ConfirmClearCacheFragment.class.getName());
    }

    private void startMeasurement() {
        if (mVolumeInfo != null && mVolumeInfo.isMountedReadable()) {
            final VolumeInfo sharedVolume = mStorageManager.findEmulatedForPrivate(mVolumeInfo);
            mMeasure = new StorageMeasurement(getContext(), mVolumeInfo, sharedVolume);
            mMeasure.setReceiver(mMeasurementReceiver);
            mMeasure.forceMeasure();
        }
    }

    private void stopMeasurement() {
        if (mMeasure != null) {
            mMeasure.onDestroy();
        }
    }

    private void updateDetails(StorageMeasurement.MeasurementDetails details) {
        final int currentUser = ActivityManager.getCurrentUser();
        final long dcimSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_DCIM,
                Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES);

        final long musicSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_MUSIC,
                Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS,
                Environment.DIRECTORY_RINGTONES, Environment.DIRECTORY_PODCASTS);

        final long downloadsSize = totalValues(details.mediaSize.get(currentUser),
                Environment.DIRECTORY_DOWNLOADS);

        mAvailablePref.setSize(details.availSize);
        mAppsUsagePref.setSize(details.appsSize.get(currentUser));
        mDcimUsagePref.setSize(dcimSize);
        mMusicUsagePref.setSize(musicSize);
        mDownloadsUsagePref.setSize(downloadsSize);
        mCacheUsagePref.setSize(details.cacheSize);
        mMiscUsagePref.setSize(details.miscSize.get(currentUser));
    }

    private static long totalValues(HashMap<String, Long> map, String... keys) {
        long total = 0;
        if (map != null) {
            for (String key : keys) {
                if (map.containsKey(key)) {
                    total += map.get(key);
                }
            }
        } else {
            Log.w(TAG,
                    "MeasurementDetails mediaSize array does not have key for current user " +
                            ActivityManager.getCurrentUser());
        }
        return total;
    }

    private class MeasurementReceiver implements StorageMeasurement.MeasurementReceiver {

        @Override
        public void onDetailsChanged(StorageMeasurement.MeasurementDetails details) {
            updateDetails(details);
        }
    }

    private class StorageEventListener extends android.os.storage.StorageEventListener {
        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            mVolumeInfo = vol;
            if (isResumed()) {
                if (mVolumeInfo.isMountedReadable()) {
                    refresh();
                } else {
                    getFragmentManager().popBackStack();
                }
            }
        }
    }
}
