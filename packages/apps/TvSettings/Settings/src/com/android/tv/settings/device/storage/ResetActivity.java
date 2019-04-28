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

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;

import com.android.tv.settings.R;

import java.util.List;

public class ResetActivity extends Activity {

    private static final String TAG = "ResetActivity";

    /**
     * Support for shutdown-after-reset. If our launch intent has a true value for
     * the boolean extra under the following key, then include it in the intent we
     * use to trigger a factory reset. This will cause us to shut down instead of
     * restart after the reset.
     */
    private static final String SHUTDOWN_INTENT_EXTRA = "shutdown";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            GuidedStepFragment.addAsRoot(this, ResetFragment.newInstance(), android.R.id.content);
        }
    }

    public static class ResetFragment extends GuidedStepFragment {

        public static ResetFragment newInstance() {

            Bundle args = new Bundle();

            ResetFragment fragment = new ResetFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.device_reset),
                    getString(R.string.factory_reset_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .title(getString(R.string.device_reset))
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                add(getFragmentManager(), ResetConfirmFragment.newInstance());
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                getActivity().finish();
            } else {
                Log.wtf(TAG, "Unknown action clicked");
            }
        }
    }

    public static class ResetConfirmFragment extends GuidedStepFragment {

        public static ResetConfirmFragment newInstance() {

            Bundle args = new Bundle();

            ResetConfirmFragment fragment = new ResetConfirmFragment();
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.device_reset),
                    getString(R.string.confirm_factory_reset_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .title(getString(R.string.confirm_factory_reset_device))
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                if (ActivityManager.isUserAMonkey()) {
                    Log.v(TAG, "Monkey tried to erase the device. Bad monkey, bad!");
                    getActivity().finish();
                } else {
                    Intent resetIntent = new Intent(Intent.ACTION_FACTORY_RESET);
                    resetIntent.setPackage("android");
                    resetIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    resetIntent.putExtra(Intent.EXTRA_REASON, "ResetConfirmFragment");
                    if (getActivity().getIntent().getBooleanExtra(SHUTDOWN_INTENT_EXTRA, false)) {
                        resetIntent.putExtra(SHUTDOWN_INTENT_EXTRA, true);
                    }
                    getContext().sendBroadcast(resetIntent);
                }
            } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
                getActivity().finish();
            } else {
                Log.wtf(TAG, "Unknown action clicked");
            }
        }
    }
}
