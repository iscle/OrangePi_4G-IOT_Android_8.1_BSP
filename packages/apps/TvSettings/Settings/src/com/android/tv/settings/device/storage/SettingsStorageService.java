/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.Nullable;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;

public class SettingsStorageService {

    private static final String TAG = "SettingsStorageService";

    public static final String ACTION_FORMAT_AS_PUBLIC =
            "com.android.tv.settings.device.storage.FORMAT_AS_PUBLIC";
    public static final String ACTION_FORMAT_AS_PRIVATE =
            "com.android.tv.settings.device.storage.FORMAT_AS_PRIVATE";
    public static final String ACTION_UNMOUNT = "com.android.tv.settings.device.storage.UNMOUNT";

    public static final String EXTRA_SUCCESS = "com.android.tv.settings.device.storage.SUCCESS";
    public static final String EXTRA_INTERNAL_BENCH =
            "com.android.tv.settings.device.storage.INTERNAL_BENCH";
    public static final String EXTRA_PRIVATE_BENCH =
            "com.android.tv.settings.device.storage.PRIVATE_BENCH";

    public static void formatAsPublic(Context context, String diskId) {
        final Intent intent = new Intent(context, Impl.class);
        intent.setAction(ACTION_FORMAT_AS_PUBLIC);
        intent.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        context.startService(intent);
    }

    public static void formatAsPrivate(Context context, String diskId) {
        final Intent intent = new Intent(context, Impl.class);
        intent.setAction(ACTION_FORMAT_AS_PRIVATE);
        intent.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        context.startService(intent);
    }

    public static void unmount(Context context, String volumeId) {
        final Intent intent = new Intent(context, Impl.class);
        intent.setAction(ACTION_UNMOUNT);
        intent.putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
        context.startService(intent);
    }

    public static class Impl extends IntentService {

        public Impl() {
            super(Impl.class.getName());
        }

        @Override
        protected void onHandleIntent(@Nullable Intent intent) {
            final String action = intent.getAction();
            if (TextUtils.isEmpty(action)) {
                throw new IllegalArgumentException("Empty action in intent: " + intent);
            }

            switch (action) {
                case ACTION_FORMAT_AS_PUBLIC: {
                    final String diskId = intent.getStringExtra(DiskInfo.EXTRA_DISK_ID);
                    if (TextUtils.isEmpty(diskId)) {
                        throw new IllegalArgumentException(
                                "No disk ID specified for format as public: " + intent);
                    }
                    formatAsPublic(diskId);
                    break;
                }
                case ACTION_FORMAT_AS_PRIVATE: {
                    final String diskId = intent.getStringExtra(DiskInfo.EXTRA_DISK_ID);
                    if (TextUtils.isEmpty(diskId)) {
                        throw new IllegalArgumentException(
                                "No disk ID specified for format as public: " + intent);
                    }
                    formatAsPrivate(diskId);
                    break;
                }
                case ACTION_UNMOUNT: {
                    final String volumeId = intent.getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);
                    if (TextUtils.isEmpty(volumeId)) {
                        throw new IllegalArgumentException("No volume ID specified for unmount: "
                                + intent);
                    }
                    unmount(volumeId);
                    break;
                }
            }
        }

        private void formatAsPublic(String diskId) {
            try {
                final StorageManager storageManager = getSystemService(StorageManager.class);
                final List<VolumeInfo> volumes = storageManager.getVolumes();
                for (final VolumeInfo volume : volumes) {
                    if (TextUtils.equals(diskId, volume.getDiskId()) &&
                            volume.getType() == VolumeInfo.TYPE_PRIVATE) {
                        storageManager.forgetVolume(volume.getFsUuid());
                    }
                }

                storageManager.partitionPublic(diskId);

                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(ACTION_FORMAT_AS_PUBLIC)
                                .putExtra(DiskInfo.EXTRA_DISK_ID, diskId)
                                .putExtra(EXTRA_SUCCESS, true));
            } catch (Exception e) {
                Log.e(TAG, "Failed to format " + diskId, e);
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(ACTION_FORMAT_AS_PUBLIC)
                                .putExtra(DiskInfo.EXTRA_DISK_ID, diskId)
                                .putExtra(EXTRA_SUCCESS, false));
            }
        }

        private void formatAsPrivate(String diskId) {
            try {
                final StorageManager storageManager = getSystemService(StorageManager.class);
                storageManager.partitionPrivate(diskId);
                final long internalBench = storageManager.benchmark(null);

                final VolumeInfo privateVol = findVolume(storageManager, diskId);
                final long privateBench;
                if (privateVol != null) {
                    privateBench = storageManager.benchmark(privateVol.getId());
                } else {
                    privateBench = -1;
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(ACTION_FORMAT_AS_PRIVATE)
                                .putExtra(DiskInfo.EXTRA_DISK_ID, diskId)
                                .putExtra(EXTRA_INTERNAL_BENCH, internalBench)
                                .putExtra(EXTRA_PRIVATE_BENCH, privateBench)
                                .putExtra(EXTRA_SUCCESS, true));
            } catch (Exception e) {
                Log.e(TAG, "Failed to format " + diskId, e);
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent(ACTION_FORMAT_AS_PRIVATE)
                                .putExtra(DiskInfo.EXTRA_DISK_ID, diskId)
                                .putExtra(EXTRA_SUCCESS, false));
            }
        }

        private VolumeInfo findVolume(StorageManager storageManager, String diskId) {
            final List<VolumeInfo> vols = storageManager.getVolumes();
            for (final VolumeInfo vol : vols) {
                if (TextUtils.equals(diskId, vol.getDiskId())
                        && (vol.getType() == VolumeInfo.TYPE_PRIVATE)) {
                    return vol;
                }
            }
            return null;
        }

        private void unmount(String volumeId) {
            try {
                final long minTime = System.currentTimeMillis() + 3000;

                final StorageManager storageManager = getSystemService(StorageManager.class);
                final VolumeInfo volumeInfo = storageManager.findVolumeById(volumeId);
                if (volumeInfo != null && volumeInfo.isMountedReadable()) {
                    Log.d(TAG, "Trying to unmount " + volumeId);
                    storageManager.unmount(volumeId);
                } else {
                    Log.d(TAG, "Volume not found, skipping unmount");
                }

                long waitTime = minTime - System.currentTimeMillis();
                while (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    waitTime = minTime - System.currentTimeMillis();
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_UNMOUNT)
                        .putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId)
                        .putExtra(EXTRA_SUCCESS, true));
            } catch (Exception e) {
                Log.d(TAG, "Could not unmount", e);
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(ACTION_UNMOUNT)
                        .putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId)
                        .putExtra(EXTRA_SUCCESS, false));
            }
        }
    }
}
