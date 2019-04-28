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

import android.app.Fragment;
import android.os.Bundle;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;

import com.android.tv.settings.R;

import java.util.List;

public class FormatAsPublicStepFragment extends GuidedStepFragment {
    private static final int ACTION_ID_BACKUP = 1;
    private static final int ACTION_ID_FORMAT = 2;

    private String mVolumeId;
    private String mDiskId;

    public interface Callback {
        void onRequestFormatAsPublic(String diskId, String volumeId);
        void onCancelFormatDialog();
    }

    public static FormatAsPublicStepFragment newInstance(String diskId) {
        final FormatAsPublicStepFragment fragment = new FormatAsPublicStepFragment();
        final Bundle b = new Bundle(1);
        b.putString(DiskInfo.EXTRA_DISK_ID, diskId);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mDiskId = getArguments().getString(DiskInfo.EXTRA_DISK_ID);
        final StorageManager storageManager = getActivity().getSystemService(StorageManager.class);
        final List<VolumeInfo> volumes = storageManager.getVolumes();
        for (final VolumeInfo volume : volumes) {
            if ((volume.getType() == VolumeInfo.TYPE_PRIVATE ||
                    volume.getType() == VolumeInfo.TYPE_PUBLIC) &&
                    TextUtils.equals(volume.getDiskId(), mDiskId)) {
                mVolumeId = volume.getId();
                break;
            }
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    public @NonNull GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        return new GuidanceStylist.Guidance(
                getString(R.string.storage_wizard_format_as_public_title),
                getString(R.string.storage_wizard_format_as_public_description), "",
                getActivity().getDrawable(R.drawable.ic_warning_132dp));
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getContext())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
        if (!TextUtils.isEmpty(mVolumeId)) {
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_ID_BACKUP)
                    .title(getString(R.string.storage_wizard_backup_apps_action))
                    .build());
        }
        actions.add(new GuidedAction.Builder(getContext())
                .id(ACTION_ID_FORMAT)
                .title(getString(R.string.storage_wizard_format_action))
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        final long id = action.getId();

        if (id == GuidedAction.ACTION_ID_CANCEL) {
            ((Callback) getActivity()).onCancelFormatDialog();
        } else if (id == ACTION_ID_BACKUP) {
            final Fragment f = BackupAppsStepFragment.newInstance(mVolumeId);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, f)
                    .addToBackStack(null)
                    .commit();
        } else if (id == ACTION_ID_FORMAT) {
            ((Callback) getActivity()).onRequestFormatAsPublic(mDiskId, mVolumeId);
        }
    }
}
