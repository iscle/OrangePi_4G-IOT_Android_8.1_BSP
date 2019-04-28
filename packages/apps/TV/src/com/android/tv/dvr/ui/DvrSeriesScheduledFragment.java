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

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.list.DvrSchedulesActivity;
import com.android.tv.dvr.ui.list.DvrSeriesSchedulesFragment;

import java.util.List;

public class DvrSeriesScheduledFragment extends DvrGuidedStepFragment {
    /**
     * The key for program list which will be passed to {@link DvrSeriesSchedulesFragment}.
     * Type: List<{@link Program}>
     */
    public static final String SERIES_SCHEDULED_KEY_PROGRAMS = "series_scheduled_key_programs";

    private final static long SERIES_RECORDING_ID_NOT_SET = -1;

    private final static int ACTION_VIEW_SCHEDULES = 1;

    private SeriesRecording mSeriesRecording;
    private boolean mShowViewScheduleOption;
    private List<Program> mPrograms;

    private int mSchedulesAddedCount = 0;
    private boolean mHasConflict = false;
    private int mInThisSeriesConflictCount = 0;
    private int mOutThisSeriesConflictCount = 0;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        long seriesRecordingId = getArguments().getLong(
                DvrSeriesScheduledDialogActivity.SERIES_RECORDING_ID, SERIES_RECORDING_ID_NOT_SET);
        if (seriesRecordingId == SERIES_RECORDING_ID_NOT_SET) {
            getActivity().finish();
            return;
        }
        mShowViewScheduleOption = getArguments().getBoolean(
                DvrSeriesScheduledDialogActivity.SHOW_VIEW_SCHEDULE_OPTION);
        mSeriesRecording = TvApplication.getSingletons(context).getDvrDataManager()
                .getSeriesRecording(seriesRecordingId);
        if (mSeriesRecording == null) {
            getActivity().finish();
            return;
        }
        mPrograms = (List<Program>) BigArguments.getArgument(SERIES_SCHEDULED_KEY_PROGRAMS);
        BigArguments.reset();
        mSchedulesAddedCount = TvApplication.getSingletons(getContext()).getDvrManager()
                .getAvailableScheduledRecording(mSeriesRecording.getId()).size();
        DvrScheduleManager dvrScheduleManager =
                TvApplication.getSingletons(context).getDvrScheduleManager();
        List<ScheduledRecording> conflictingRecordings =
                dvrScheduleManager.getConflictingSchedules(mSeriesRecording);
        mHasConflict = !conflictingRecordings.isEmpty();
        for (ScheduledRecording recording : conflictingRecordings) {
            if (recording.getSeriesRecordingId() == mSeriesRecording.getId()) {
                ++mInThisSeriesConflictCount;
            } else if (recording.getPriority() < mSeriesRecording.getPriority()) {
                ++mOutThisSeriesConflictCount;
            }
        }
     }

    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.dvr_series_recording_dialog_title);
        Drawable icon;
        if (!mHasConflict) {
            icon = getResources().getDrawable(R.drawable.ic_check_circle_white_48dp, null);
        } else {
            icon = getResources().getDrawable(R.drawable.ic_error_white_48dp, null);
        }
        return new GuidanceStylist.Guidance(title, getDescription(), null, icon);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        Context context = getContext();
        actions.add(new GuidedAction.Builder(context)
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        if (mShowViewScheduleOption) {
            actions.add(new GuidedAction.Builder(context)
                    .id(ACTION_VIEW_SCHEDULES)
                    .title(R.string.dvr_action_view_schedules)
                    .build());
        }
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_VIEW_SCHEDULES) {
            Intent intent = new Intent(getActivity(), DvrSchedulesActivity.class);
            intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE, DvrSchedulesActivity
                    .TYPE_SERIES_SCHEDULE);
            intent.putExtra(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING,
                    mSeriesRecording);
            BigArguments.reset();
            BigArguments.setArgument(DvrSeriesSchedulesFragment
                    .SERIES_SCHEDULES_KEY_SERIES_PROGRAMS, mPrograms);
            startActivity(intent);
        }
        getActivity().finish();
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrMissingStorageErrorFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_VIEW_SCHEDULES) {
            return "view-schedules";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }

    private String getDescription() {
        if (!mHasConflict) {
            return getResources().getQuantityString(
                    R.plurals.dvr_series_scheduled_no_conflict, mSchedulesAddedCount,
                    mSchedulesAddedCount, mSeriesRecording.getTitle());
        } else {
            // mInThisSeriesConflictCount equals 0 and mOutThisSeriesConflictCount equals 0 means
            // mHasConflict is false. So we don't need to check that case.
            if (mInThisSeriesConflictCount != 0 && mOutThisSeriesConflictCount != 0) {
                return getResources().getQuantityString(
                        R.plurals.dvr_series_scheduled_this_and_other_series_conflict,
                        mSchedulesAddedCount, mSchedulesAddedCount, mSeriesRecording.getTitle(),
                        mInThisSeriesConflictCount + mOutThisSeriesConflictCount);
            } else if (mInThisSeriesConflictCount != 0) {
                return getResources().getQuantityString(
                        R.plurals.dvr_series_recording_scheduled_only_this_series_conflict,
                        mSchedulesAddedCount, mSchedulesAddedCount, mSeriesRecording.getTitle(),
                        mInThisSeriesConflictCount);
            } else {
                if (mOutThisSeriesConflictCount == 1) {
                    return getResources().getQuantityString(
                            R.plurals.dvr_series_scheduled_only_other_series_one_conflict,
                            mSchedulesAddedCount, mSchedulesAddedCount,
                            mSeriesRecording.getTitle());
                } else {
                    return getResources().getQuantityString(
                            R.plurals.dvr_series_scheduled_only_other_series_many_conflicts,
                            mSchedulesAddedCount, mSchedulesAddedCount, mSeriesRecording.getTitle(),
                            mOutThisSeriesConflictCount);
                }
            }
        }
    }
}
