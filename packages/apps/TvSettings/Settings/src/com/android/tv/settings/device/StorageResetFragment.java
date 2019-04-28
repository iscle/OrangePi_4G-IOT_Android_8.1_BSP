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

package com.android.tv.settings.device;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.settings.R;
import com.android.tv.settings.device.storage.MissingStorageFragment;
import com.android.tv.settings.device.storage.NewStorageActivity;
import com.android.tv.settings.device.storage.StorageFragment;
import com.android.tv.settings.device.storage.StoragePreference;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StorageResetFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "StorageResetFragment";

    private static final String KEY_DEVICE_CATEGORY = "device_storage";
    private static final String KEY_REMOVABLE_CATEGORY = "removable_storage";

    private static final int REFRESH_DELAY_MILLIS = 500;

    private StorageManager mStorageManager;
    private final StorageEventListener mStorageEventListener = new StorageEventListener();

    private final Handler mHandler = new Handler();
    private final Runnable mRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };

    public static StorageResetFragment newInstance() {
        return new StorageResetFragment();
    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mStorageManager = getContext().getSystemService(StorageManager.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.storage_reset, null);
        findPreference(KEY_REMOVABLE_CATEGORY).setVisible(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        mStorageManager.registerListener(mStorageEventListener);
    }

    @Override
    public void onResume() {
        super.onResume();
        mHandler.removeCallbacks(mRefreshRunnable);
        // Delay to allow entrance animations to complete
        mHandler.postDelayed(mRefreshRunnable, REFRESH_DELAY_MILLIS);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRefreshRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        mStorageManager.unregisterListener(mStorageEventListener);
    }

    private void refresh() {
        if (!isResumed()) {
            return;
        }
        final Context themedContext = getPreferenceManager().getContext();

        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        Collections.sort(volumes, VolumeInfo.getDescriptionComparator());

        final List<VolumeInfo> privateVolumes = new ArrayList<>(volumes.size());
        final List<VolumeInfo> publicVolumes = new ArrayList<>(volumes.size());

        // Find mounted volumes
        for (final VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PRIVATE) {
                privateVolumes.add(vol);
            } else if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                publicVolumes.add(vol);
            } else {
                Log.d(TAG, "Skipping volume " + vol.toString());
            }
        }

        // Find missing private filesystems
        final List<VolumeRecord> volumeRecords = mStorageManager.getVolumeRecords();
        final List<VolumeRecord> privateMissingVolumes = new ArrayList<>(volumeRecords.size());

        for (final VolumeRecord record : volumeRecords) {
            if (record.getType() == VolumeInfo.TYPE_PRIVATE
                    && mStorageManager.findVolumeByUuid(record.getFsUuid()) == null) {
                privateMissingVolumes.add(record);
            }
        }

        // Find unreadable disks
        final List<DiskInfo> disks = mStorageManager.getDisks();
        final List<DiskInfo> unsupportedDisks = new ArrayList<>(disks.size());
        for (final DiskInfo disk : disks) {
            if (disk.volumeCount == 0 && disk.size > 0) {
                unsupportedDisks.add(disk);
            }
        }

        // Add the prefs
        final PreferenceCategory deviceCategory =
                (PreferenceCategory) findPreference(KEY_DEVICE_CATEGORY);
        final Set<String> touchedDeviceKeys =
                new ArraySet<>(privateVolumes.size() + privateMissingVolumes.size());

        for (final VolumeInfo volumeInfo : privateVolumes) {
            final String key = VolPreference.makeKey(volumeInfo);
            touchedDeviceKeys.add(key);
            VolPreference volPreference = (VolPreference) deviceCategory.findPreference(key);
            if (volPreference == null) {
                volPreference = new VolPreference(themedContext, volumeInfo);
            }
            volPreference.refresh(themedContext, mStorageManager, volumeInfo);
            deviceCategory.addPreference(volPreference);
        }

        for (final VolumeRecord volumeRecord : privateMissingVolumes) {
            final String key = MissingPreference.makeKey(volumeRecord);
            touchedDeviceKeys.add(key);
            MissingPreference missingPreference =
                    (MissingPreference) deviceCategory.findPreference(key);
            if (missingPreference == null) {
                missingPreference = new MissingPreference(themedContext, volumeRecord);
            }
            deviceCategory.addPreference(missingPreference);
        }

        for (int i = 0; i < deviceCategory.getPreferenceCount();) {
            final Preference pref = deviceCategory.getPreference(i);
            if (touchedDeviceKeys.contains(pref.getKey())) {
                i++;
            } else {
                deviceCategory.removePreference(pref);
            }
        }

        final PreferenceCategory removableCategory =
                (PreferenceCategory) findPreference(KEY_REMOVABLE_CATEGORY);
        final int publicCount = publicVolumes.size() + unsupportedDisks.size();
        final Set<String> touchedRemovableKeys = new ArraySet<>(publicCount);
        // Only show section if there are public/unknown volumes present
        removableCategory.setVisible(publicCount > 0);

        for (final VolumeInfo volumeInfo : publicVolumes) {
            final String key = VolPreference.makeKey(volumeInfo);
            touchedRemovableKeys.add(key);
            VolPreference volPreference = (VolPreference) removableCategory.findPreference(key);
            if (volPreference == null) {
                volPreference = new VolPreference(themedContext, volumeInfo);
            }
            volPreference.refresh(themedContext, mStorageManager, volumeInfo);
            removableCategory.addPreference(volPreference);
        }
        for (final DiskInfo diskInfo : unsupportedDisks) {
            final String key = UnsupportedDiskPreference.makeKey(diskInfo);
            touchedRemovableKeys.add(key);
            UnsupportedDiskPreference unsupportedDiskPreference =
                    (UnsupportedDiskPreference) findPreference(key);
            if (unsupportedDiskPreference == null) {
                unsupportedDiskPreference = new UnsupportedDiskPreference(themedContext, diskInfo);
            }
            removableCategory.addPreference(unsupportedDiskPreference);
        }

        for (int i = 0; i < removableCategory.getPreferenceCount();) {
            final Preference pref = removableCategory.getPreference(i);
            if (touchedRemovableKeys.contains(pref.getKey())) {
                i++;
            } else {
                removableCategory.removePreference(pref);
            }
        }
    }


    private static class VolPreference extends Preference {
        public VolPreference(Context context, VolumeInfo volumeInfo) {
            super(context);
            setKey(makeKey(volumeInfo));
        }

        private void refresh(Context context, StorageManager storageManager,
                VolumeInfo volumeInfo) {
            final String description = storageManager
                    .getBestVolumeDescription(volumeInfo);
            setTitle(description);
            if (volumeInfo.isMountedReadable()) {
                setSummary(getSizeString(volumeInfo));
                setFragment(StorageFragment.class.getName());
                StorageFragment.prepareArgs(getExtras(), volumeInfo);
            } else {
                setSummary(context.getString(R.string.storage_unmount_success, description));
            }
        }

        private String getSizeString(VolumeInfo vol) {
            final File path = vol.getPath();
            if (vol.isMountedReadable() && path != null) {
                return String.format(getContext().getString(R.string.storage_size),
                        StoragePreference.formatSize(getContext(), path.getTotalSpace()));
            } else {
                return null;
            }
        }

        public static String makeKey(VolumeInfo volumeInfo) {
            return "VolPref:" + volumeInfo.getId();
        }
    }

    private static class MissingPreference extends Preference {
        public MissingPreference(Context context, VolumeRecord volumeRecord) {
            super(context);
            setKey(makeKey(volumeRecord));
            setTitle(volumeRecord.getNickname());
            setSummary(R.string.storage_not_connected);
            setFragment(MissingStorageFragment.class.getName());
            MissingStorageFragment.prepareArgs(getExtras(), volumeRecord.getFsUuid());
        }

        public static String makeKey(VolumeRecord volumeRecord) {
            return "MissingPref:" + volumeRecord.getFsUuid();
        }
    }

    private static class UnsupportedDiskPreference extends Preference {
        public UnsupportedDiskPreference(Context context, DiskInfo info) {
            super(context);
            setKey(makeKey(info));
            setTitle(info.getDescription());
            setIntent(NewStorageActivity.getNewStorageLaunchIntent(context, null, info.getId()));
        }

        public static String makeKey(DiskInfo info) {
            return "UnsupportedPref:" + info.getId();
        }
    }

    private class StorageEventListener extends android.os.storage.StorageEventListener {
        @Override
        public void onStorageStateChanged(String path, String oldState, String newState) {
            refresh();
        }

        @Override
        public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
            refresh();
        }

        @Override
        public void onVolumeRecordChanged(VolumeRecord rec) {
            refresh();
        }

        @Override
        public void onVolumeForgotten(String fsUuid) {
            refresh();
        }

        @Override
        public void onDiskScanned(DiskInfo disk, int volumeCount) {
            refresh();
        }

        @Override
        public void onDiskDestroyed(DiskInfo disk) {
            refresh();
        }

    }

}
