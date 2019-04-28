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
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.text.format.DateUtils;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.DvrConflictFragment.DvrProgramConflictFragment;
import com.android.tv.util.Utils;

import java.util.Collections;
import java.util.List;

/**
 * A fragment which asks the user the type of the recording.
 * <p>
 * The program should be episodic and the series recording should not had been created yet.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DvrScheduleFragment extends DvrGuidedStepFragment {
    /**
     * Key for the whether to add the current program to series.
     * Type: boolean
     */
    public static final String KEY_ADD_CURRENT_PROGRAM_TO_SERIES = "add_current_program_to_series";

    private static final String TAG = "DvrScheduleFragment";

    private static final int ACTION_RECORD_EPISODE = 1;
    private static final int ACTION_RECORD_SERIES = 2;

    private Program mProgram;
    private boolean mAddCurrentProgramToSeries;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mProgram = args.getParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM);
            mAddCurrentProgramToSeries = args.getBoolean(KEY_ADD_CURRENT_PROGRAM_TO_SERIES, false);
        }
        DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
        SoftPreconditions.checkArgument(mProgram != null && mProgram.isEpisodic(), TAG,
                "The program should be episodic: " + mProgram);
        SeriesRecording seriesRecording = dvrManager.getSeriesRecording(mProgram);
        SoftPreconditions.checkArgument(seriesRecording == null
                || seriesRecording.isStopped(), TAG,
                "The series recording should be stopped or null: " + seriesRecording);
        super.onCreate(savedInstanceState);
    }

    @Override
    public int onProvideTheme() {
        return R.style.Theme_TV_Dvr_GuidedStep_Twoline_Action;
    }

    @NonNull
    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.dvr_schedule_dialog_title);
        Drawable icon = getResources().getDrawable(R.drawable.ic_dvr, null);
        return new Guidance(title, null, null, icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Context context = getContext();
        String description;
        if (mProgram.getStartTimeUtcMillis() <= System.currentTimeMillis()) {
            description = getString(R.string.dvr_action_record_episode_from_now_description,
                    DateUtils.formatDateTime(context, mProgram.getEndTimeUtcMillis(),
                            DateUtils.FORMAT_SHOW_TIME));
        } else {
            description = Utils.getDurationString(context, mProgram.getStartTimeUtcMillis(),
                    mProgram.getEndTimeUtcMillis(), true);
        }
        actions.add(new GuidedAction.Builder(context)
                .id(ACTION_RECORD_EPISODE)
                .title(R.string.dvr_action_record_episode)
                .description(description)
                .build());
        actions.add(new GuidedAction.Builder(context)
                .id(ACTION_RECORD_SERIES)
                .title(R.string.dvr_action_record_series)
                .description(mProgram.getTitle())
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_RECORD_EPISODE) {
            getDvrManager().addSchedule(mProgram);
            List<ScheduledRecording> conflicts = getDvrManager().getConflictingSchedules(mProgram);
            if (conflicts.isEmpty()) {
                DvrUiHelper.showAddScheduleToast(getContext(), mProgram.getTitle(),
                        mProgram.getStartTimeUtcMillis(), mProgram.getEndTimeUtcMillis());
                dismissDialog();
            } else {
                GuidedStepFragment fragment = new DvrProgramConflictFragment();
                Bundle args = new Bundle();
                args.putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, mProgram);
                fragment.setArguments(args);
                GuidedStepFragment.add(getFragmentManager(), fragment,
                        R.id.halfsized_dialog_host);
            }
        } else if (action.getId() == ACTION_RECORD_SERIES) {
            SeriesRecording seriesRecording = TvApplication.getSingletons(getContext())
                    .getDvrDataManager().getSeriesRecording(mProgram.getSeriesId());
            if (seriesRecording == null) {
                seriesRecording = getDvrManager().addSeriesRecording(mProgram,
                        Collections.emptyList(), SeriesRecording.STATE_SERIES_STOPPED);
            } else {
                // Reset priority to the highest.
                seriesRecording = SeriesRecording.buildFrom(seriesRecording)
                        .setPriority(TvApplication.getSingletons(getContext())
                                .getDvrScheduleManager().suggestNewSeriesPriority())
                        .build();
                getDvrManager().updateSeriesRecording(seriesRecording);
            }

            DvrUiHelper.startSeriesSettingsActivity(getContext(),
                    seriesRecording.getId(), null, true, true, true,
                    mAddCurrentProgramToSeries ? mProgram : null);
            dismissDialog();
        }
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrSmallSizedStorageErrorFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_RECORD_EPISODE) {
            return "record-episode";
        } else if (actionId == ACTION_RECORD_SERIES) {
            return "record-series";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
