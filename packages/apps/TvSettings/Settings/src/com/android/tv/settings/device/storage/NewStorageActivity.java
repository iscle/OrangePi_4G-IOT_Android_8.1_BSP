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
 * limitations under the License.
 */

package com.android.tv.settings.device.storage;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;

import com.android.tv.settings.R;
import com.android.tv.settings.device.StorageResetActivity;

import java.util.List;

public class NewStorageActivity extends Activity {

    private static final String TAG = "NewStorageActivity";

    private static final String ACTION_NEW_STORAGE =
            "com.android.tv.settings.device.storage.NewStorageActivity.NEW_STORAGE";
    private static final String ACTION_MISSING_STORAGE =
            "com.android.tv.settings.device.storage.NewStorageActivity.MISSING_STORAGE";

    public static Intent getNewStorageLaunchIntent(Context context, String volumeId,
            String diskId) {
        final Intent i = new Intent(context, NewStorageActivity.class);
        i.setAction(ACTION_NEW_STORAGE);
        i.putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
        i.putExtra(DiskInfo.EXTRA_DISK_ID, diskId);
        return i;
    }

    public static Intent getMissingStorageLaunchIntent(Context context, String fsUuid) {
        final Intent i = new Intent(context, NewStorageActivity.class);
        i.setAction(ACTION_MISSING_STORAGE);
        i.putExtra(VolumeRecord.EXTRA_FS_UUID, fsUuid);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final String action = getIntent().getAction();

            if (TextUtils.equals(action, ACTION_MISSING_STORAGE)) {
                // Launched from a notification, see com.android.systemui.usb.StorageNotification
                final String fsUuid = getIntent().getStringExtra(VolumeRecord.EXTRA_FS_UUID);
                if (TextUtils.isEmpty(fsUuid)) {
                    throw new IllegalStateException(
                            "NewStorageActivity launched without specifying missing storage");
                }

                getFragmentManager().beginTransaction()
                        .add(android.R.id.content, MissingStorageFragment.newInstance(fsUuid))
                        .commit();
            } else {
                final String volumeId = getIntent().getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);
                final String diskId = getIntent().getStringExtra(DiskInfo.EXTRA_DISK_ID);
                if (TextUtils.isEmpty(volumeId) && TextUtils.isEmpty(diskId)) {
                    throw new IllegalStateException(
                            "NewStorageActivity launched without specifying new storage");
                }

                getFragmentManager().beginTransaction()
                        .add(android.R.id.content, NewStorageFragment.newInstance(volumeId, diskId))
                        .commit();
            }
        }
    }

    public static class NewStorageFragment extends GuidedStepFragment {

        private static final int ACTION_BROWSE = 1;
        private static final int ACTION_FORMAT_AS_PRIVATE = 2;
        private static final int ACTION_UNMOUNT = 3;
        private static final int ACTION_FORMAT_AS_PUBLIC = 4;

        private String mVolumeId;
        private String mDiskId;
        private String mDescription;

        private final StorageEventListener mStorageEventListener = new StorageEventListener() {
            @Override
            public void onDiskDestroyed(DiskInfo disk) {
                checkForUnmount();
            }

            @Override
            public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
                checkForUnmount();
            }
        };

        public static NewStorageFragment newInstance(String volumeId, String diskId) {
            final Bundle b = new Bundle(1);
            b.putString(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
            b.putString(DiskInfo.EXTRA_DISK_ID, diskId);
            final NewStorageFragment fragment = new NewStorageFragment();
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            StorageManager storageManager = getActivity().getSystemService(StorageManager.class);
            mVolumeId = getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID);
            mDiskId = getArguments().getString(DiskInfo.EXTRA_DISK_ID);
            if (TextUtils.isEmpty(mVolumeId) && TextUtils.isEmpty(mDiskId)) {
                throw new IllegalStateException(
                        "NewStorageFragment launched without specifying new storage");
            }
            if (!TextUtils.isEmpty(mVolumeId)) {
                final VolumeInfo info = storageManager.findVolumeById(mVolumeId);
                mDescription = storageManager.getBestVolumeDescription(info);
                mDiskId = info.getDiskId();
            } else {
                final DiskInfo info = storageManager.findDiskById(mDiskId);
                mDescription = info.getDescription();
            }

            super.onCreate(savedInstanceState);
        }

        @Override
        public void onStart() {
            super.onStart();
            checkForUnmount();
            getActivity().getSystemService(StorageManager.class)
                    .registerListener(mStorageEventListener);
        }

        @Override
        public void onStop() {
            super.onStop();
            getActivity().getSystemService(StorageManager.class)
                    .unregisterListener(mStorageEventListener);
        }

        @Override
        public @NonNull GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_new_title),
                    mDescription,
                    null,
                    getActivity().getDrawable(R.drawable.ic_storage_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            if (TextUtils.isEmpty(mVolumeId)) {
                actions.add(new GuidedAction.Builder(getContext())
                        .title(R.string.storage_new_action_format_public)
                        .id(ACTION_FORMAT_AS_PUBLIC)
                        .build());
            } else {
                actions.add(new GuidedAction.Builder(getContext())
                        .title(R.string.storage_new_action_browse)
                        .id(ACTION_BROWSE)
                        .build());
            }
            actions.add(new GuidedAction.Builder(getContext())
                    .title(R.string.storage_new_action_adopt)
                    .id(ACTION_FORMAT_AS_PRIVATE)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .title(R.string.storage_new_action_eject)
                    .id(ACTION_UNMOUNT)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            switch ((int) action.getId()) {
                case ACTION_FORMAT_AS_PUBLIC:
                    startActivity(FormatActivity.getFormatAsPublicIntent(getActivity(), mDiskId));
                    break;
                case ACTION_BROWSE:
                    startActivity(new Intent(getActivity(), StorageResetActivity.class));
                    break;
                case ACTION_FORMAT_AS_PRIVATE:
                    startActivity(FormatActivity.getFormatAsPrivateIntent(getActivity(), mDiskId));
                    break;
                case ACTION_UNMOUNT:
                    // If we've mounted a volume, eject it. Otherwise just treat eject as cancel
                    if (!TextUtils.isEmpty(mVolumeId)) {
                        startActivity(
                                UnmountActivity.getIntent(getActivity(), mVolumeId, mDescription));
                    }
                    break;
            }
            getActivity().finish();
        }

        private void checkForUnmount() {
            if (!isAdded()) {
                return;
            }

            final StorageManager storageManager =
                    getContext().getSystemService(StorageManager.class);

            if (!TextUtils.isEmpty(mDiskId)) {
                // If the disk disappears, assume we're done
                final List<DiskInfo> diskInfos = storageManager.getDisks();
                boolean found = false;
                for (DiskInfo diskInfo : diskInfos) {
                    if (TextUtils.equals(diskInfo.getId(), mDiskId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    getActivity().finish();
                }
            } else if (!TextUtils.isEmpty(mVolumeId)) {
                final List<VolumeInfo> volumeInfos = storageManager.getVolumes();
                boolean found = false;
                for (VolumeInfo volumeInfo : volumeInfos) {
                    if (TextUtils.equals(volumeInfo.getId(), mVolumeId)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    getActivity().finish();
                }
            }
        }
    }

    public static class MissingStorageFragment extends GuidedStepFragment {

        private String mFsUuid;
        private String mDescription;

        private final BroadcastReceiver mDiskReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TextUtils.equals(intent.getAction(), VolumeInfo.ACTION_VOLUME_STATE_CHANGED)) {
                    checkForRemount();
                }
            }
        };

        public static MissingStorageFragment newInstance(String fsUuid) {
            final MissingStorageFragment fragment = new MissingStorageFragment();
            final Bundle b = new Bundle(1);
            b.putString(VolumeRecord.EXTRA_FS_UUID, fsUuid);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            StorageManager storageManager = getActivity().getSystemService(StorageManager.class);
            mFsUuid = getArguments().getString(VolumeRecord.EXTRA_FS_UUID);
            if (TextUtils.isEmpty(mFsUuid)) {
                throw new IllegalStateException(
                        "MissingStorageFragment launched without specifying missing storage");
            }
            final VolumeRecord volumeRecord = storageManager.findRecordByUuid(mFsUuid);
            mDescription = volumeRecord.getNickname();

            super.onCreate(savedInstanceState);
        }

        @Override
        public void onStart() {
            super.onStart();
            getContext().registerReceiver(mDiskReceiver,
                    new IntentFilter(VolumeInfo.ACTION_VOLUME_STATE_CHANGED));
            checkForRemount();
        }

        @Override
        public void onStop() {
            super.onStop();
            getContext().unregisterReceiver(mDiskReceiver);
        }

        @Override
        public @NonNull GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_missing_title, mDescription),
                    getString(R.string.storage_missing_description),
                    null,
                    getActivity().getDrawable(R.drawable.ic_error_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            getActivity().finish();
        }

        private void checkForRemount() {
            if (!isAdded()) {
                return;
            }

            final List<VolumeInfo> volumeInfos =
                    getContext().getSystemService(StorageManager.class).getVolumes();

            for (final VolumeInfo info : volumeInfos) {
                if (!TextUtils.equals(info.getFsUuid(), mFsUuid)) {
                    continue;
                }
                if (info.isMountedReadable()) {
                    getActivity().finish();
                }
            }
        }
    }

}
