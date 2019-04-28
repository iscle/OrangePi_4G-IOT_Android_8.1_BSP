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

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.settings.R;

import java.util.List;

public class FormatActivity extends Activity
        implements FormatAsPrivateStepFragment.Callback,
        FormatAsPublicStepFragment.Callback, SlowDriveStepFragment.Callback {

    private static final String TAG = "FormatActivity";

    public static final String INTENT_ACTION_FORMAT_AS_PRIVATE =
            "com.android.tv.settings.device.storage.FormatActivity.formatAsPrivate";
    public static final String INTENT_ACTION_FORMAT_AS_PUBLIC =
            "com.android.tv.settings.device.storage.FormatActivity.formatAsPublic";

    private static final String SAVE_STATE_FORMAT_PRIVATE_DISK_ID =
            "StorageResetActivity.formatPrivateDiskId";
    private static final String SAVE_STATE_FORMAT_DISK_DESC =
            "StorageResetActivity.formatDiskDesc";
    private static final String SAVE_STATE_FORMAT_PUBLIC_DISK_ID =
            "StorageResetActivity.formatPrivateDiskId";

    // Non-null means we're in the process of formatting this volume as private
    private String mFormatAsPrivateDiskId;
    // Non-null means we're in the process of formatting this volume as public
    private String mFormatAsPublicDiskId;

    private String mFormatDiskDesc;

    private final BroadcastReceiver mFormatReceiver = new FormatReceiver();
    private PackageManager mPackageManager;
    private StorageManager mStorageManager;

    public static Intent getFormatAsPublicIntent(Context context, String diskId) {
        final Intent i = new Intent(context, FormatActivity.class);
        i.setAction(INTENT_ACTION_FORMAT_AS_PUBLIC);
        i.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        return i;
    }

    public static Intent getFormatAsPrivateIntent(Context context, String diskId) {
        final Intent i = new Intent(context, FormatActivity.class);
        i.setAction(INTENT_ACTION_FORMAT_AS_PRIVATE);
        i.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPackageManager = getPackageManager();
        mStorageManager = getSystemService(StorageManager.class);

        final IntentFilter filter = new IntentFilter();
        filter.addAction(SettingsStorageService.ACTION_FORMAT_AS_PRIVATE);
        filter.addAction(SettingsStorageService.ACTION_FORMAT_AS_PUBLIC);
        LocalBroadcastManager.getInstance(this).registerReceiver(mFormatReceiver, filter);

        if (savedInstanceState != null) {
            mFormatAsPrivateDiskId =
                    savedInstanceState.getString(SAVE_STATE_FORMAT_PRIVATE_DISK_ID);
            mFormatAsPublicDiskId = savedInstanceState.getString(SAVE_STATE_FORMAT_PUBLIC_DISK_ID);
            mFormatDiskDesc = savedInstanceState.getString(SAVE_STATE_FORMAT_DISK_DESC);
        } else {
            final String diskId = getIntent().getStringExtra(DiskInfo.EXTRA_DISK_ID);
            final String action = getIntent().getAction();
            final Fragment f;
            if (TextUtils.equals(action, INTENT_ACTION_FORMAT_AS_PRIVATE)) {
                f = FormatAsPrivateStepFragment.newInstance(diskId);
            } else if (TextUtils.equals(action, INTENT_ACTION_FORMAT_AS_PUBLIC)) {
                f = FormatAsPublicStepFragment.newInstance(diskId);
            } else {
                throw new IllegalStateException("No known action specified");
            }
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, f)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(mFormatAsPrivateDiskId)) {
            final VolumeInfo volumeInfo = findVolume(mFormatAsPrivateDiskId);
            if (volumeInfo != null && volumeInfo.getType() == VolumeInfo.TYPE_PRIVATE) {
                // Formatting must have completed while we were paused
                // We've lost the benchmark data, so just assume the drive is fast enough
                handleFormatAsPrivateComplete(-1, -1);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mFormatReceiver);
    }

    private VolumeInfo findVolume(String diskId) {
        final List<VolumeInfo> vols = mStorageManager.getVolumes();
        for (final VolumeInfo vol : vols) {
            if (TextUtils.equals(diskId, vol.getDiskId())
                    && (vol.getType() == VolumeInfo.TYPE_PRIVATE)) {
                return vol;
            }
        }
        return null;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVE_STATE_FORMAT_PRIVATE_DISK_ID, mFormatAsPrivateDiskId);
        outState.putString(SAVE_STATE_FORMAT_PUBLIC_DISK_ID, mFormatAsPublicDiskId);
        outState.putString(SAVE_STATE_FORMAT_DISK_DESC, mFormatDiskDesc);
    }

    private void handleFormatAsPrivateComplete(float privateBench, float internalBench) {
        if (Math.abs(-1 - privateBench) < 0.1) {
            final float frac = privateBench / internalBench;
            Log.d(TAG, "New volume is " + frac + "x the speed of internal");

            // TODO: better threshold
            if (privateBench > 2000000000) {
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content,
                                SlowDriveStepFragment.newInstance())
                        .commit();
                return;
            }
        }
        launchMigrateStorageAndFinish(mFormatAsPrivateDiskId);
    }

    private class FormatReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(),
                    SettingsStorageService.ACTION_FORMAT_AS_PRIVATE)
                    && !TextUtils.isEmpty(mFormatAsPrivateDiskId)) {
                final String diskId = intent.getStringExtra(DiskInfo.EXTRA_DISK_ID);
                if (TextUtils.equals(mFormatAsPrivateDiskId, diskId)) {
                    final boolean success =
                            intent.getBooleanExtra(SettingsStorageService.EXTRA_SUCCESS, false);
                    if (success) {
                        if (isResumed()) {
                            final float privateBench = intent.getFloatExtra(
                                    SettingsStorageService.EXTRA_PRIVATE_BENCH, -1);
                            final float internalBench = intent.getFloatExtra(
                                    SettingsStorageService.EXTRA_INTERNAL_BENCH, -1);
                            handleFormatAsPrivateComplete(privateBench, internalBench);
                        }

                        Toast.makeText(context, getString(
                                R.string.storage_format_success, mFormatDiskDesc),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context,
                                getString(R.string.storage_format_failure, mFormatDiskDesc),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }
            } else if (TextUtils.equals(intent.getAction(),
                    SettingsStorageService.ACTION_FORMAT_AS_PUBLIC)
                    && !TextUtils.isEmpty(mFormatAsPublicDiskId)) {
                final String diskId = intent.getStringExtra(DiskInfo.EXTRA_DISK_ID);
                if (TextUtils.equals(mFormatAsPublicDiskId, diskId)) {
                    final boolean success =
                            intent.getBooleanExtra(SettingsStorageService.EXTRA_SUCCESS, false);
                    if (success) {
                        Toast.makeText(context,
                                getString(R.string.storage_format_success,
                                        mFormatDiskDesc), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context,
                                getString(R.string.storage_format_failure,
                                        mFormatDiskDesc), Toast.LENGTH_SHORT).show();
                    }
                    finish();
                }
            }
        }
    }

    @Override
    public void onRequestFormatAsPrivate(String diskId) {
        final FormattingProgressFragment fragment = FormattingProgressFragment.newInstance();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();

        mFormatAsPrivateDiskId = diskId;
        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        for (final VolumeInfo volume : volumes) {
            if ((volume.getType() == VolumeInfo.TYPE_PRIVATE ||
                    volume.getType() == VolumeInfo.TYPE_PUBLIC) &&
                    TextUtils.equals(volume.getDiskId(), diskId)) {
                mFormatDiskDesc = mStorageManager.getBestVolumeDescription(volume);
            }
        }
        if (TextUtils.isEmpty(mFormatDiskDesc)) {
            final DiskInfo info = mStorageManager.findDiskById(diskId);
            if (info != null) {
                mFormatDiskDesc = info.getDescription();
            }
        }
        SettingsStorageService.formatAsPrivate(this, diskId);
    }

    private void launchMigrateStorageAndFinish(String diskId) {
        final List<VolumeInfo> candidates =
                mPackageManager.getPrimaryStorageCandidateVolumes();
        VolumeInfo moveTarget = null;
        for (final VolumeInfo candidate : candidates) {
            if (TextUtils.equals(candidate.getDiskId(), diskId)) {
                moveTarget = candidate;
                break;
            }
        }

        if (moveTarget != null) {
            startActivity(MigrateStorageActivity.getLaunchIntent(this, moveTarget.getId(), true));
        }

        finish();
    }

    @Override
    public void onRequestFormatAsPublic(String diskId, String volumeId) {
        final FormattingProgressFragment fragment = FormattingProgressFragment.newInstance();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();

        mFormatAsPublicDiskId = diskId;
        if (!TextUtils.isEmpty(volumeId)) {
            final VolumeInfo info = mStorageManager.findVolumeById(volumeId);
            if (info != null) {
                mFormatDiskDesc = mStorageManager.getBestVolumeDescription(info);
            }
        }
        if (TextUtils.isEmpty(mFormatDiskDesc)) {
            final DiskInfo info = mStorageManager.findDiskById(diskId);
            if (info != null) {
                mFormatDiskDesc = info.getDescription();
            }
        }
        SettingsStorageService.formatAsPublic(this, diskId);
    }

    @Override
    public void onCancelFormatDialog() {
        finish();
    }

    @Override
    public void onSlowDriveWarningComplete() {
        launchMigrateStorageAndFinish(mFormatAsPrivateDiskId);
    }
}
