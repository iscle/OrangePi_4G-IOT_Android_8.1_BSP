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

package com.android.tv.dvr.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrConflictFragment.DvrChannelRecordConflictFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DvrChannelRecordDurationOptionFragment extends DvrGuidedStepFragment {
    private final List<Long> mDurations = new ArrayList<>();
    private Channel mChannel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            long channelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID);
            mChannel = TvApplication.getSingletons(getContext()).getChannelDataManager()
                    .getChannel(channelId);
        }
        SoftPreconditions.checkArgument(mChannel != null);
        super.onCreate(savedInstanceState);
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getResources().getString(R.string.dvr_channel_record_duration_dialog_title);
        Drawable icon = getResources().getDrawable(R.drawable.ic_dvr, null);
        return new Guidance(title, null, null, icon);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        int actionId = -1;
        mDurations.clear();
        mDurations.add(TimeUnit.MINUTES.toMillis(10));
        mDurations.add(TimeUnit.MINUTES.toMillis(30));
        mDurations.add(TimeUnit.HOURS.toMillis(1));
        mDurations.add(TimeUnit.HOURS.toMillis(3));

        actions.add(new GuidedAction.Builder(getContext())
                .id(++actionId)
                .title(R.string.recording_start_dialog_10_min_duration)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .id(++actionId)
                .title(R.string.recording_start_dialog_30_min_duration)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .id(++actionId)
                .title(R.string.recording_start_dialog_1_hour_duration)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .id(++actionId)
                .title(R.string.recording_start_dialog_3_hours_duration)
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
        long duration = mDurations.get((int) action.getId());
        long startTimeMs = System.currentTimeMillis();
        long endTimeMs = System.currentTimeMillis() + duration;
        List<ScheduledRecording> conflicts = dvrManager.getConflictingSchedules(
                mChannel.getId(), startTimeMs, endTimeMs);
        dvrManager.addSchedule(mChannel, startTimeMs, endTimeMs);
        if (conflicts.isEmpty()) {
            dismissDialog();
        } else {
            GuidedStepFragment fragment = new DvrChannelRecordConflictFragment();
            Bundle args = new Bundle();
            args.putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, mChannel.getId());
            args.putLong(DvrHalfSizedDialogFragment.KEY_START_TIME_MS, startTimeMs);
            args.putLong(DvrHalfSizedDialogFragment.KEY_END_TIME_MS, endTimeMs);
            fragment.setArguments(args);
            GuidedStepFragment.add(getFragmentManager(), fragment,
                    R.id.halfsized_dialog_host);
        }
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrChannelRecordDurationOptionFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == 0) {
            return "record-10-minutes";
        } else if (actionId == 1) {
            return "record-30-minutes";
        } else if (actionId == 2) {
            return "record-1-hour";
        } else if (actionId == 3) {
            return "record-3-hour";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
