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

package com.android.tv.settings.device.storage;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.settings.R;

import java.util.List;

/**
 * Broadcast receiver invoked when a USB device is connected/disconnected/scanned.
 */
public class DiskReceiver extends BroadcastReceiver {
    private static final String TAG = "DiskReceiver";

    private StorageManager mStorageManager;

    @Override
    public void onReceive(Context context, Intent intent) {
        final UserManager userManager =
                (UserManager) context.getSystemService(Context.USER_SERVICE);
        final UserInfo userInfo = userManager.getUserInfo(UserHandle.myUserId());

        if (userInfo.isRestricted()
                || ActivityManager.getCurrentUser() != UserHandle.myUserId()) {
            Log.d(TAG, "Ignoring storage notification: wrong user");
            return;
        }

        if (Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) == 0) {
            Log.d(TAG, "Ignoring storage notification: setup not complete");
            return;
        }

        mStorageManager = context.getSystemService(StorageManager.class);

        if (TextUtils.equals(intent.getAction(), VolumeInfo.ACTION_VOLUME_STATE_CHANGED)) {
            final int state = intent.getIntExtra(VolumeInfo.EXTRA_VOLUME_STATE, -1);
            if (state == VolumeInfo.STATE_MOUNTED
                    || state == VolumeInfo.STATE_MOUNTED_READ_ONLY) {
                handleMount(context, intent);
            } else if (state == VolumeInfo.STATE_UNMOUNTED
                    || state == VolumeInfo.STATE_BAD_REMOVAL) {
                handleUnmount(context, intent);
            }
        } else if (TextUtils.equals(intent.getAction(),
                "com.google.android.tungsten.setupwraith.TV_SETTINGS_POST_SETUP")) {
            handleSetupComplete(context);
        }
    }

    private void handleMount(Context context, Intent intent) {
        final String volumeId = intent.getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);

        final List<VolumeInfo> volumeInfos = mStorageManager.getVolumes();
        for (final VolumeInfo info : volumeInfos) {
            if (!TextUtils.equals(info.getId(), volumeId)) {
                continue;
            }
            final String uuid = info.getFsUuid();
            Log.d(TAG, "Scanning volume: " + info);
            if (info.getType() == VolumeInfo.TYPE_PRIVATE
                    && !TextUtils.equals(volumeId, VolumeInfo.ID_PRIVATE_INTERNAL)) {
                Toast.makeText(context, R.string.storage_mount_adopted, Toast.LENGTH_SHORT)
                    .show();
                return;
            }
        }
    }

    private void handleUnmount(Context context, Intent intent) {
        final String fsUuid = intent.getStringExtra(VolumeRecord.EXTRA_FS_UUID);
        if (TextUtils.isEmpty(fsUuid)) {
            Log.e(TAG, "Missing fsUuid, not launching activity.");
            return;
        }
        VolumeRecord volumeRecord = null;
        try {
            volumeRecord = mStorageManager.findRecordByUuid(fsUuid);
        } catch (Exception e) {
            Log.e(TAG, "Error finding volume record", e);
        }
        if (volumeRecord == null) {
            return;
        }
        Log.d(TAG, "Found ejected volume: " + volumeRecord + " for FSUUID: " + fsUuid);
        if (volumeRecord.getType() == VolumeInfo.TYPE_PRIVATE) {
            final Intent i = NewStorageActivity.getMissingStorageLaunchIntent(context, fsUuid);
            setPopupLaunchFlags(i);
            context.startActivity(i);
        }
    }

    private void handleSetupComplete(Context context) {
        Log.d(TAG, "Scanning for storage post-setup");

        final List<DiskInfo> diskInfos = mStorageManager.getDisks();
        for (DiskInfo diskInfo : diskInfos) {
            Log.d(TAG, "Scanning disk: " + diskInfo);
            if (diskInfo.size <= 0) {
                Log.d(TAG, "Disk ID " + diskInfo.id + " has no media");
                continue;
            }
            if (diskInfo.volumeCount != 0) {
                Log.d(TAG, "Disk ID " + diskInfo.id + " has usable volumes, deferring");
                continue;
            }
            // No usable volumes, prompt the user to erase the disk
            final Intent i =
                    NewStorageActivity.getNewStorageLaunchIntent(context, null, diskInfo.id);
            setPopupLaunchFlags(i);
            context.startActivity(i);
            return;
        }

        final List<VolumeInfo> volumeInfos = mStorageManager.getVolumes();
        for (final VolumeInfo info : volumeInfos) {
            final String uuid = info.getFsUuid();
            Log.d(TAG, "Scanning volume: " + info);
            if (info.getType() != VolumeInfo.TYPE_PUBLIC || TextUtils.isEmpty(uuid)) {
                continue;
            }
            final VolumeRecord record = mStorageManager.findRecordByUuid(uuid);
            if (record.isInited() || record.isSnoozed()) {
                continue;
            }
            final DiskInfo disk = info.getDisk();
            if (disk.isAdoptable()) {
                final Intent i = NewStorageActivity.getNewStorageLaunchIntent(context,
                        info.getId(), disk.getId());
                setPopupLaunchFlags(i);
                context.startActivity(i);
                return;
            }
        }
    }

    private void setPopupLaunchFlags(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}
