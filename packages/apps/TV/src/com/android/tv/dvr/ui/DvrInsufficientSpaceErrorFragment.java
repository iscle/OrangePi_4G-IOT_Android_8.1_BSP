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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.ui.browse.DvrBrowseActivity;

import java.util.ArrayList;
import java.util.List;

public class DvrInsufficientSpaceErrorFragment extends DvrGuidedStepFragment {
    /**
     * Key for the failed scheduled recordings information.
     */
    public static final String FAILED_SCHEDULED_RECORDING_INFOS =
            "failed_scheduled_recording_infos";

    private static final String TAG = "DvrInsufficientSpaceErrorFragment";

    private static final int ACTION_VIEW_RECENT_RECORDINGS = 1;

    private ArrayList<String> mFailedScheduledRecordingInfos;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) {
            mFailedScheduledRecordingInfos =
                    args.getStringArrayList(FAILED_SCHEDULED_RECORDING_INFOS);
        }
        SoftPreconditions.checkState(
                mFailedScheduledRecordingInfos != null && !mFailedScheduledRecordingInfos.isEmpty(),
                TAG, "failed scheduled recording is null");
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title;
        String description;
        int failedScheduledRecordingSize = mFailedScheduledRecordingInfos.size();
        if (failedScheduledRecordingSize == 1) {
            title =  getString(
                    R.string.dvr_error_insufficient_space_title_one_recording,
                    mFailedScheduledRecordingInfos.get(0));
            description = getString(
                    R.string.dvr_error_insufficient_space_description_one_recording,
                    mFailedScheduledRecordingInfos.get(0));
        } else if (failedScheduledRecordingSize == 2) {
            title =  getString(
                    R.string.dvr_error_insufficient_space_title_two_recordings,
                    mFailedScheduledRecordingInfos.get(0), mFailedScheduledRecordingInfos.get(1));
            description = getString(
                    R.string.dvr_error_insufficient_space_description_two_recordings,
                    mFailedScheduledRecordingInfos.get(0), mFailedScheduledRecordingInfos.get(1));
        } else {
            title =  getString(
                    R.string.dvr_error_insufficient_space_title_three_or_more_recordings,
                    mFailedScheduledRecordingInfos.get(0), mFailedScheduledRecordingInfos.get(1),
                    mFailedScheduledRecordingInfos.get(2));
            description = getString(
                    R.string.dvr_error_insufficient_space_description_three_or_more_recordings,
                    mFailedScheduledRecordingInfos.get(0), mFailedScheduledRecordingInfos.get(1),
                    mFailedScheduledRecordingInfos.get(2));
        }
        return new Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(new GuidedAction.Builder(activity)
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        if (TvApplication.getSingletons(getContext()).getDvrManager().hasValidItems()) {
            actions.add(new GuidedAction.Builder(activity)
                    .id(ACTION_VIEW_RECENT_RECORDINGS)
                    .title(getResources().getString(
                            R.string.dvr_error_insufficient_space_action_view_recent_recordings))
                    .build());
        }
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_VIEW_RECENT_RECORDINGS) {
            Intent intent = new Intent(getActivity(), DvrBrowseActivity.class);
            getActivity().startActivity(intent);
        }
        dismissDialog();
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrInsufficientSpaceErrorFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_VIEW_RECENT_RECORDINGS) {
            return "view-recent";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
