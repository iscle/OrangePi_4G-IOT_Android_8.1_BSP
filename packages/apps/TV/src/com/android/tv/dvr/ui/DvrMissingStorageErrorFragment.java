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
 * limitations under the License.
 */

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.dvr.ui.browse.DvrDetailsActivity;

import java.util.List;

public class DvrMissingStorageErrorFragment extends DvrGuidedStepFragment {
    private static final String TAG = "DvrMissingStorageError";

    private static final int ACTION_OK = 1;
    private static final int ACTION_OPEN_STORAGE_SETTINGS = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getResources().getString(R.string.dvr_error_missing_storage_title);
        String description = getResources().getString(
                R.string.dvr_error_missing_storage_description);
        return new Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_OK)
                .title(android.R.string.ok)
                .build());
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_OPEN_STORAGE_SETTINGS)
                .title(getResources().getString(R.string.dvr_action_error_storage_settings))
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        Activity activity = getActivity();
        if (activity instanceof DvrDetailsActivity) {
            activity.finish();
        } else {
            dismissDialog();
        }
        if (action.getId() != ACTION_OPEN_STORAGE_SETTINGS) {
            return;
        }
        final Intent intent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Can't start internal storage settings activity", e);
        }
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrMissingStorageErrorFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_OPEN_STORAGE_SETTINGS) {
            return "open-storage-settings";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
