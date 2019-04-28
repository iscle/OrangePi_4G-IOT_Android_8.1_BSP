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
* limitations under the License
*/

package com.android.tv.dvr.ui.list;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.util.Utils;

/**
 * A RowPresenter for series schedule row.
 */
class SeriesScheduleRowPresenter extends ScheduleRowPresenter {
    private static final String TAG = "SeriesRowPresenter";

    private boolean mLtr;

    public SeriesScheduleRowPresenter(Context context) {
        super(context);
        mLtr = context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_LTR;
    }

    public static class SeriesScheduleRowViewHolder extends ScheduleRowViewHolder {
        public SeriesScheduleRowViewHolder(View view, ScheduleRowPresenter presenter) {
            super(view, presenter);
            ViewGroup.LayoutParams lp = getTimeView().getLayoutParams();
            lp.width = view.getResources().getDimensionPixelSize(
                    R.dimen.dvr_series_schedules_item_time_width);
            getTimeView().setLayoutParams(lp);
        }
    }

    @Override
    protected ScheduleRowViewHolder onGetScheduleRowViewHolder(View view) {
        return new SeriesScheduleRowViewHolder(view, this);
    }

    @Override
    protected String onGetRecordingTimeText(ScheduleRow row) {
        return Utils.getDurationString(getContext(), row.getStartTimeMs(), row.getEndTimeMs(),
                false, true, true, 0);
    }

    @Override
    protected String onGetProgramInfoText(ScheduleRow row) {
        return row.getEpisodeDisplayTitle(getContext());
    }

    @Override
    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        super.onBindRowViewHolder(vh, item);
        SeriesScheduleRowViewHolder viewHolder = (SeriesScheduleRowViewHolder) vh;
        EpisodicProgramRow row = (EpisodicProgramRow) item;
        if (getDvrManager().isConflicting(row.getSchedule())) {
            viewHolder.getProgramTitleView().setCompoundDrawablePadding(getContext()
                    .getResources().getDimensionPixelOffset(
                            R.dimen.dvr_schedules_warning_icon_padding));
            viewHolder.getProgramTitleView().setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_warning_gray600_36dp, 0, 0, 0);
        } else {
            viewHolder.getProgramTitleView().setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }
    }

    @Override
    protected void onInfoClicked(ScheduleRow row) {
        DvrUiHelper.startSchedulesActivity(getContext(), row.getSchedule());
    }

    @Override
    protected void onStartRecording(ScheduleRow row) {
        SoftPreconditions.checkState(row.getSchedule() == null, TAG,
                "Start request with the existing schedule: " + row);
        row.setStartRecordingRequested(true);
        getDvrManager().addScheduleWithHighestPriority(((EpisodicProgramRow) row).getProgram());
    }

    @Override
    protected void onStopRecording(ScheduleRow row) {
        SoftPreconditions.checkState(row.getSchedule() != null, TAG,
                "Stop request with the null schedule: " + row);
        row.setStopRecordingRequested(true);
        getDvrManager().stopRecording(row.getSchedule());
    }

    @Override
    protected void onCreateSchedule(ScheduleRow row) {
        if (row.getSchedule() == null) {
            getDvrManager().addScheduleWithHighestPriority(((EpisodicProgramRow) row).getProgram());
        } else {
            super.onCreateSchedule(row);
        }
    }

    @Override
    @ScheduleRowAction
    protected int[] getAvailableActions(ScheduleRow row) {
        if (row.getSchedule() == null) {
            if (row.isOnAir()) {
                return new int[] {ACTION_START_RECORDING};
            } else {
                return new int[] {ACTION_CREATE_SCHEDULE};
            }
        }
        return super.getAvailableActions(row);
    }

    @Override
    protected boolean canResolveConflict() {
        return false;
    }

    @Override
    protected boolean shouldKeepScheduleAfterRemoving() {
        return true;
    }
}
