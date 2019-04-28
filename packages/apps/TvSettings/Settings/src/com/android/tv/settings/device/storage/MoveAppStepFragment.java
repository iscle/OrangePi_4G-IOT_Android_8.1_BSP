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

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import com.android.tv.settings.R;

import java.io.File;
import java.util.List;

public class MoveAppStepFragment extends GuidedStepFragment {

    private static final String TAG = "MoveAppStepFragment";

    private static final String ARG_PACKAGE_NAME = "packageName";
    private static final String ARG_PACKAGE_DESC = "packageDesc";

    private PackageManager mPackageManager;
    private StorageManager mStorageManager;

    private String mPackageName;
    private String mPackageDesc;
    private List<VolumeInfo> mCandidateVolumes;
    private VolumeInfo mCurrentVolume;

    public interface Callback {
        void onRequestMovePackageToVolume(String packageName, VolumeInfo destination);
    }

    public static MoveAppStepFragment newInstance(String packageName, String packageDesc) {
        final MoveAppStepFragment fragment = new MoveAppStepFragment();
        final Bundle b = new Bundle(2);
        b.putString(ARG_PACKAGE_NAME, packageName);
        b.putString(ARG_PACKAGE_DESC, packageDesc);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Need mPackageManager before onCreateActions, which is called from super.onCreate
        mPackageManager = getActivity().getPackageManager();
        mStorageManager = getActivity().getSystemService(StorageManager.class);

        mPackageDesc = getArguments().getString(ARG_PACKAGE_DESC, "");
        mPackageName = getArguments().getString(ARG_PACKAGE_NAME);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public @NonNull GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        Drawable icon;
        try {
            icon = mPackageManager.getApplicationIcon(mPackageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Missing package while resolving icon", e);
            icon = null;
        }
        return new GuidanceStylist.Guidance(getString(R.string.storage_wizard_move_app_title),
                null,
                mPackageDesc,
                icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        final String packageName = getArguments().getString(ARG_PACKAGE_NAME);
        final ApplicationInfo info;
        try {
             info = mPackageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Package missing while resolving storage", e);
            return;
        }
        mCurrentVolume = mPackageManager.getPackageCurrentVolume(info);
        mCandidateVolumes = mPackageManager.getPackageCandidateVolumes(info);

        for (final VolumeInfo candidate : mCandidateVolumes) {
            if (!candidate.isMountedWritable()) {
                continue;
            }
            final File path = candidate.getPath();
            final String avail = Formatter.formatFileSize(getActivity(), path.getFreeSpace());
            actions.add(new GuidedAction.Builder(getContext())
                    .title(mStorageManager.getBestVolumeDescription(candidate))
                    .description(
                            getString(R.string.storage_wizard_back_up_apps_space_available, avail))
                    .checked(TextUtils.equals(mCurrentVolume.getId(), candidate.getId()))
                    .checkSetId(GuidedAction.DEFAULT_CHECK_SET_ID)
                    .id(mCandidateVolumes.indexOf(candidate))
                    .build());
        }
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        final Callback callback = (Callback) getActivity();
        final VolumeInfo destination = mCandidateVolumes.get((int) action.getId());
        if (destination.equals(mCurrentVolume)) {
            if (!getFragmentManager().popBackStackImmediate()) {
                getActivity().finish();
            }
        } else {
            callback.onRequestMovePackageToVolume(mPackageName, destination);
        }
    }

}
