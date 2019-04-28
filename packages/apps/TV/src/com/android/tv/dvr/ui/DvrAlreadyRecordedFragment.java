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

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.RecordedProgram;

import java.util.List;

/**
 * A fragment which notifies the user that the same episode has already been scheduled.
 *
 * <p>Note that the schedule has not been created yet.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DvrAlreadyRecordedFragment extends DvrGuidedStepFragment {
    private static final int ACTION_RECORD_ANYWAY = 1;
    private static final int ACTION_WATCH = 2;
    private static final int ACTION_CANCEL = 3;

    private Program mProgram;
    private RecordedProgram mDuplicate;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mProgram = getArguments().getParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM);
        DvrManager dvrManager = TvApplication.getSingletons(context).getDvrManager();
        mDuplicate = dvrManager.getRecordedProgram(mProgram.getTitle(),
                mProgram.getSeasonNumber(), mProgram.getEpisodeNumber());
        if (mDuplicate == null) {
            dvrManager.addSchedule(mProgram);
            DvrUiHelper.showAddScheduleToast(context, mProgram.getTitle(),
                    mProgram.getStartTimeUtcMillis(), mProgram.getEndTimeUtcMillis());
            dismissDialog();
        }
    }

    @NonNull
    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.dvr_already_recorded_dialog_title);
        String description = getString(R.string.dvr_already_recorded_dialog_description);
        Drawable image = getResources().getDrawable(R.drawable.ic_warning_white_96dp, null);
        return new Guidance(title, description, null, image);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Context context = getContext();
        actions.add(new GuidedAction.Builder(context)
                .id(ACTION_RECORD_ANYWAY)
                .title(R.string.dvr_action_record_anyway)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .id(ACTION_WATCH)
                .title(R.string.dvr_action_watch_now)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .id(ACTION_CANCEL)
                .title(R.string.dvr_action_record_cancel)
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_RECORD_ANYWAY) {
            getDvrManager().addSchedule(mProgram);
        } else if (action.getId() == ACTION_WATCH) {
            DvrUiHelper.startDetailsActivity(getActivity(), mDuplicate, null, false);
        }
        dismissDialog();
    }

    @Override
    public String getTrackerPrefix() {
        return "onTrackedGuidedActionClicked";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_RECORD_ANYWAY) {
            return "record-anyway";
        } else if (actionId == ACTION_WATCH) {
            return "watch-now";
        } else if (actionId == ACTION_CANCEL) {
            return "cancel-recording";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
