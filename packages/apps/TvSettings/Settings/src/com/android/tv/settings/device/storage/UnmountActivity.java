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
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

import java.util.List;

public class UnmountActivity extends Activity {

    private static final String TAG = "UnmountActivity";

    public static final String EXTRA_VOLUME_DESC = "UnmountActivity.volumeDesc";

    private String mUnmountVolumeId;
    private String mUnmountVolumeDesc;

    private final Handler mHandler = new Handler();
    private final BroadcastReceiver mUnmountReceiver = new UnmountReceiver();

    public static Intent getIntent(Context context, String volumeId, String volumeDesc) {
        final Intent i = new Intent(context, UnmountActivity.class);
        i.putExtra(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
        i.putExtra(EXTRA_VOLUME_DESC, volumeDesc);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mUnmountVolumeId = getIntent().getStringExtra(VolumeInfo.EXTRA_VOLUME_ID);
        mUnmountVolumeDesc = getIntent().getStringExtra(EXTRA_VOLUME_DESC);

        LocalBroadcastManager.getInstance(this).registerReceiver(mUnmountReceiver,
                new IntentFilter(SettingsStorageService.ACTION_UNMOUNT));

        if (savedInstanceState == null) {
            final StorageManager storageManager = getSystemService(StorageManager.class);
            final VolumeInfo volumeInfo = storageManager.findVolumeById(mUnmountVolumeId);

            if (volumeInfo == null) {
                // Unmounted already, just bail
                finish();
                return;
            }

            if (volumeInfo.getType() == VolumeInfo.TYPE_PRIVATE) {
                final Fragment fragment = UnmountPrivateStepFragment.newInstance(mUnmountVolumeId);
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, fragment)
                        .commit();
            } else {
                // Jump straight to unmounting
                onRequestUnmount();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mUnmountReceiver);
    }

    public void onRequestUnmount() {
        final Fragment fragment = UnmountProgressFragment.newInstance(mUnmountVolumeDesc);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
        // Post this so that it will presumably run after onResume, if we're calling from onCreate()
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                SettingsStorageService.unmount(UnmountActivity.this, mUnmountVolumeId);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        final VolumeInfo volumeInfo =
                getSystemService(StorageManager.class).findVolumeById(mUnmountVolumeId);

        if (volumeInfo == null) {
            // Unmounted already, just bail
            finish();
        }
    }

    private class UnmountReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(intent.getAction(), SettingsStorageService.ACTION_UNMOUNT)
                    && TextUtils.equals(intent.getStringExtra(VolumeInfo.EXTRA_VOLUME_ID),
                    mUnmountVolumeId)) {
                final Boolean success =
                        intent.getBooleanExtra(SettingsStorageService.EXTRA_SUCCESS, false);
                if (success) {
                    Toast.makeText(context,
                            getString(R.string.storage_unmount_success, mUnmountVolumeDesc),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context,
                            getString(R.string.storage_unmount_failure, mUnmountVolumeDesc),
                            Toast.LENGTH_SHORT).show();
                }
                finish();
            }
        }
    }

    public static class UnmountPrivateStepFragment extends GuidedStepFragment {

        private static final int ACTION_ID_UNMOUNT = 1;

        public static UnmountPrivateStepFragment newInstance(String volumeId) {
            final UnmountPrivateStepFragment fragment = new UnmountPrivateStepFragment();
            final Bundle b = new Bundle(1);
            b.putString(VolumeInfo.EXTRA_VOLUME_ID, volumeId);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public @NonNull
        GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_wizard_eject_private_title),
                    getString(R.string.storage_wizard_eject_private_description), "",
                    getActivity().getDrawable(R.drawable.ic_storage_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_ID_UNMOUNT)
                    .title(getString(R.string.storage_eject))
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            final long id = action.getId();

            if (id == GuidedAction.ACTION_ID_CANCEL) {
                if (!getFragmentManager().popBackStackImmediate()) {
                    getActivity().finish();
                }
            } else if (id == ACTION_ID_UNMOUNT) {
                ((UnmountActivity) getActivity()).onRequestUnmount();
            }
        }
    }

    public static class UnmountProgressFragment extends ProgressDialogFragment {

        private static final String ARG_DESCRIPTION = "description";

        public static UnmountProgressFragment newInstance(CharSequence description) {
            final Bundle b = new Bundle(1);
            b.putCharSequence(ARG_DESCRIPTION, description);
            final UnmountProgressFragment fragment = new UnmountProgressFragment();
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            final CharSequence description = getArguments().getCharSequence(ARG_DESCRIPTION);
            setTitle(getActivity().getString(R.string.storage_wizard_eject_progress_title,
                    description));
        }
    }
}
