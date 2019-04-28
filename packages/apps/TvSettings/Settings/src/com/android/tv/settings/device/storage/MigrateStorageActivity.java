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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class MigrateStorageActivity extends Activity {
    private static final String TAG = "MigrateStorageActivity";

    private static final String EXTRA_MIGRATE_HERE =
            "com.android.tv.settings.device.storage.MigrateStorageActivity.MIGRATE_HERE";

    private static final String SAVE_STATE_MOVE_ID = "MigrateStorageActivity.MOVE_ID";

    private VolumeInfo mTargetVolumeInfo;
    private VolumeInfo mVolumeInfo;
    private String mTargetVolumeDesc;
    private String mVolumeDesc;
    private int mMoveId = -1;
    private final Handler mHandler = new Handler();
    private PackageManager mPackageManager;
    private final PackageManager.MoveCallback mMoveCallback = new PackageManager.MoveCallback() {
        @Override
        public void onStatusChanged(int moveId, int status, long estMillis) {
            if (moveId != mMoveId || !PackageManager.isMoveStatusFinished(status)) {
                return;
            }
            if (status == PackageManager.MOVE_SUCCEEDED) {
                showMigrationSuccessToast();
            } else {
                showMigrationFailureToast();
            }
            finish();
        }
    };

    public static Intent getLaunchIntent(Context context, String volumeId,
            boolean migrateHere) {
        final Intent i = new Intent(context, MigrateStorageActivity.class);
        i.putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
        i.putExtra(EXTRA_MIGRATE_HERE, migrateHere);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        final String volumeId = intent.getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);
        final StorageManager storageManager = getSystemService(StorageManager.class);

        if (intent.getBooleanExtra(EXTRA_MIGRATE_HERE, true)) {
            mTargetVolumeInfo = storageManager.findVolumeById(volumeId);
            if (mTargetVolumeInfo == null) {
                finish();
                return;
            }
            mTargetVolumeDesc = storageManager.getBestVolumeDescription(mTargetVolumeInfo);
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content,
                            MigrateConfirmationStepFragment.newInstance(mTargetVolumeDesc))
                    .commit();
        } else {
            mVolumeInfo = storageManager.findVolumeById(volumeId);
            if (mVolumeInfo == null) {
                finish();
                return;
            }
            mVolumeDesc = storageManager.getBestVolumeDescription(mVolumeInfo);
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content,
                            ChooseStorageStepFragment.newInstance(mVolumeInfo))
                    .commit();
        }

        mPackageManager = getPackageManager();
        mPackageManager.registerMoveCallback(mMoveCallback, mHandler);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SAVE_STATE_MOVE_ID, mMoveId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getPackageManager().unregisterMoveCallback(mMoveCallback);
    }

    private void onConfirmCancel() {
        finish();
    }

    private void onConfirmProceed() {
        startMigrationInternal();
    }

    private void onChoose(VolumeInfo volumeInfo) {
        mTargetVolumeInfo = volumeInfo;
        final StorageManager storageManager = getSystemService(StorageManager.class);
        mTargetVolumeDesc = storageManager.getBestVolumeDescription(mTargetVolumeInfo);
        startMigrationInternal();
    }

    private void startMigrationInternal() {
        try {
            mMoveId = mPackageManager.movePrimaryStorage(mTargetVolumeInfo);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content,
                            MigrateProgressFragment.newInstance(mTargetVolumeDesc))
                    .commitNow();
        } catch (IllegalArgumentException e) {
            // This will generally happen if there's a move already in progress or completed
            StorageManager sm = (StorageManager) getSystemService(STORAGE_SERVICE);

            if (Objects.equals(mTargetVolumeInfo.getFsUuid(),
                    sm.getPrimaryStorageVolume().getUuid())) {
                // The data is already on the target volume
                showMigrationSuccessToast();
            } else {
                // The data is most likely in the process of being moved
                Log.e(TAG, "Storage migration failure", e);
                showMigrationFailureToast();
            }
            finish();
        } catch (IllegalStateException e) {
            showMigrationFailureToast();
            finish();
        }
    }

    private void showMigrationSuccessToast() {
        Toast.makeText(this,
                getString(R.string.storage_wizard_migrate_toast_success, mTargetVolumeDesc),
                Toast.LENGTH_SHORT).show();
    }

    private void showMigrationFailureToast() {
        Toast.makeText(this,
                getString(R.string.storage_wizard_migrate_toast_failure, mTargetVolumeDesc),
                Toast.LENGTH_SHORT).show();
    }

    public static class MigrateConfirmationStepFragment extends GuidedStepFragment {
        private static final String ARG_VOLUME_DESC = "volumeDesc";

        private static final int ACTION_CONFIRM = 1;
        private static final int ACTION_LATER = 2;

        public static MigrateConfirmationStepFragment newInstance(String volumeDescription) {
            final MigrateConfirmationStepFragment fragment = new MigrateConfirmationStepFragment();
            final Bundle b = new Bundle(1);
            b.putString(ARG_VOLUME_DESC, volumeDescription);
            fragment.setArguments(b);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            final String driveDesc = getArguments().getString(ARG_VOLUME_DESC);
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_wizard_migrate_confirm_title, driveDesc),
                    getString(R.string.storage_wizard_migrate_confirm_description, driveDesc),
                    null,
                    getActivity().getDrawable(R.drawable.ic_storage_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_CONFIRM)
                    .title(R.string.storage_wizard_migrate_confirm_action_move_now)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_LATER)
                    .title(R.string.storage_wizard_migrate_confirm_action_move_later)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final int id = (int) action.getId();
            switch (id) {
                case ACTION_CONFIRM:
                    ((MigrateStorageActivity) getActivity()).onConfirmProceed();
                    break;
                case ACTION_LATER:
                    ((MigrateStorageActivity) getActivity()).onConfirmCancel();
                    break;
            }
        }
    }

    public static class ChooseStorageStepFragment extends GuidedStepFragment {

        private List<VolumeInfo> mCandidateVolumes;

        public static ChooseStorageStepFragment newInstance(VolumeInfo currentVolumeInfo) {
            Bundle args = new Bundle(1);
            args.putString(VolumeInfo.EXTRA_VOLUME_ID, currentVolumeInfo.getId());

            ChooseStorageStepFragment fragment = new ChooseStorageStepFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_wizard_migrate_choose_title),
                    null,
                    null,
                    getActivity().getDrawable(R.drawable.ic_storage_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            final StorageManager storageManager =
                    getContext().getSystemService(StorageManager.class);
            mCandidateVolumes =
                    getContext().getPackageManager().getPrimaryStorageCandidateVolumes();
            final String volumeId = getArguments().getString(VolumeInfo.EXTRA_VOLUME_ID);
            for (final VolumeInfo candidate : mCandidateVolumes) {
                if (TextUtils.equals(candidate.getId(), volumeId)) {
                    continue;
                }
                final File path = candidate.getPath();
                final String avail = Formatter.formatFileSize(getActivity(), path.getFreeSpace());
                actions.add(new GuidedAction.Builder(getContext())
                        .title(storageManager.getBestVolumeDescription(candidate))
                        .description(getString(
                                R.string.storage_wizard_back_up_apps_space_available, avail))
                        .id(mCandidateVolumes.indexOf(candidate))
                        .build());
            }

        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final VolumeInfo volumeInfo = mCandidateVolumes.get((int) action.getId());
            ((MigrateStorageActivity)getActivity()).onChoose(volumeInfo);
        }
    }

    public static class MigrateProgressFragment extends ProgressDialogFragment {
        private static final String ARG_VOLUME_DESC = "volumeDesc";

        public static MigrateProgressFragment newInstance(String volumeDescription) {
            final MigrateProgressFragment fragment = new MigrateProgressFragment();
            final Bundle b = new Bundle(1);
            b.putString(ARG_VOLUME_DESC, volumeDescription);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setTitle(getActivity().getString(R.string.storage_wizard_migrate_progress_title,
                    getArguments().getString(ARG_VOLUME_DESC)));
            setSummary(getActivity()
                    .getString(R.string.storage_wizard_migrate_progress_description));
        }

    }
}
