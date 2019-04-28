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

import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.recorder.ConflictChecker;
import com.android.tv.dvr.recorder.ConflictChecker.OnUpcomingConflictChangeListener;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public abstract class DvrConflictFragment extends DvrGuidedStepFragment {
    private static final String TAG = "DvrConflictFragment";
    private static final boolean DEBUG = false;

    private static final int ACTION_DELETE_CONFLICT = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_VIEW_SCHEDULES = 3;

    // The program count which will be listed in the description. This is the number of the
    // program strings in R.plurals.dvr_program_conflict_dialog_description_many.
    private static final int LISTED_PROGRAM_COUNT = 2;

    protected List<ScheduledRecording> mConflicts;

    void setConflicts(List<ScheduledRecording> conflicts) {
        mConflicts = conflicts;
    }

    List<ScheduledRecording> getConflicts() {
        return mConflicts;
    }

    @Override
    public int onProvideTheme() {
        return R.style.Theme_TV_Dvr_Conflict_GuidedStep;
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions,
            Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getContext())
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        actions.add(new GuidedAction.Builder(getContext())
                .id(ACTION_VIEW_SCHEDULES)
                .title(R.string.dvr_action_view_schedules)
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_VIEW_SCHEDULES) {
            DvrUiHelper.startSchedulesActivityForOneTimeRecordingConflict(
                    getContext(), getConflicts());
        }
        dismissDialog();
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = getId();
        if (actionId == ACTION_VIEW_SCHEDULES) {
            return "view-schedules";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }

    String getConflictDescription() {
        List<String> titles = new ArrayList<>();
        HashSet<String> titleSet = new HashSet<>();
        for (ScheduledRecording schedule : getConflicts()) {
            String scheduleTitle = getScheduleTitle(schedule);
            if (scheduleTitle != null && !titleSet.contains(scheduleTitle)) {
                titles.add(scheduleTitle);
                titleSet.add(scheduleTitle);
            }
        }
        switch (titles.size()) {
            case 0:
                Log.i(TAG, "Conflict has been resolved by any reason. Maybe input might have"
                        + " been deleted.");
                return null;
            case 1:
                return getResources().getString(
                        R.string.dvr_program_conflict_dialog_description_1, titles.get(0));
            case 2:
                return getResources().getString(
                        R.string.dvr_program_conflict_dialog_description_2, titles.get(0),
                        titles.get(1));
            case 3:
                return getResources().getString(
                        R.string.dvr_program_conflict_dialog_description_3, titles.get(0),
                        titles.get(1));
            default:
                return getResources().getQuantityString(
                        R.plurals.dvr_program_conflict_dialog_description_many,
                        titles.size() - LISTED_PROGRAM_COUNT, titles.get(0), titles.get(1),
                        titles.size() - LISTED_PROGRAM_COUNT);
        }
    }

    @Nullable
    private String getScheduleTitle(ScheduledRecording schedule) {
        if (schedule.getType() == ScheduledRecording.TYPE_TIMED) {
            Channel channel = TvApplication.getSingletons(getContext()).getChannelDataManager()
                    .getChannel(schedule.getChannelId());
            if (channel != null) {
                return channel.getDisplayName();
            } else {
                return null;
            }
        } else {
            return schedule.getProgramTitle();
        }
    }

    /**
     * A fragment to show the program conflict.
     */
    public static class DvrProgramConflictFragment extends DvrConflictFragment {
        private Program mProgram;
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            Bundle args = getArguments();
            if (args != null) {
                mProgram = args.getParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM);
            }
            SoftPreconditions.checkArgument(mProgram != null);
            TvInputInfo input = Utils.getTvInputInfoForProgram(getContext(), mProgram);
            SoftPreconditions.checkNotNull(input);
            List<ScheduledRecording> conflicts = null;
            if (input != null) {
                conflicts = TvApplication.getSingletons(getContext()).getDvrManager()
                        .getConflictingSchedules(mProgram);
            }
            if (conflicts == null) {
                conflicts = Collections.emptyList();
            }
            if (conflicts.isEmpty()) {
                dismissDialog();
            }
            setConflicts(conflicts);
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getResources().getString(R.string.dvr_program_conflict_dialog_title);
            String descriptionPrefix = getString(
                    R.string.dvr_program_conflict_dialog_description_prefix, mProgram.getTitle());
            String description = getConflictDescription();
            if (description == null) {
                dismissDialog();
            }
            Drawable icon = getResources().getDrawable(R.drawable.ic_error_white_48dp, null);
            return new Guidance(title, descriptionPrefix + " " + description, null, icon);
        }

        @Override
        public String getTrackerPrefix() {
            return "DvrProgramConflictFragment";
        }
    }

    /**
     * A fragment to show the channel recording conflict.
     */
    public static class DvrChannelRecordConflictFragment extends DvrConflictFragment {
        private Channel mChannel;
        private long mStartTimeMs;
        private long mEndTimeMs;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            Bundle args = getArguments();
            long channelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID);
            mChannel = TvApplication.getSingletons(getContext()).getChannelDataManager()
                    .getChannel(channelId);
            SoftPreconditions.checkArgument(mChannel != null);
            TvInputInfo input = Utils.getTvInputInfoForChannelId(getContext(), mChannel.getId());
            SoftPreconditions.checkNotNull(input);
            List<ScheduledRecording> conflicts = null;
            if (input != null) {
                mStartTimeMs = args.getLong(DvrHalfSizedDialogFragment.KEY_START_TIME_MS);
                mEndTimeMs = args.getLong(DvrHalfSizedDialogFragment.KEY_END_TIME_MS);
                conflicts = TvApplication.getSingletons(getContext()).getDvrManager()
                        .getConflictingSchedules(mChannel.getId(), mStartTimeMs, mEndTimeMs);
            }
            if (conflicts == null) {
                conflicts = Collections.emptyList();
            }
            if (conflicts.isEmpty()) {
                dismissDialog();
            }
            setConflicts(conflicts);
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getResources().getString(R.string.dvr_channel_conflict_dialog_title);
            String descriptionPrefix = getString(
                    R.string.dvr_channel_conflict_dialog_description_prefix,
                    mChannel.getDisplayName());
            String description = getConflictDescription();
            if (description == null) {
                dismissDialog();
            }
            Drawable icon = getResources().getDrawable(R.drawable.ic_error_white_48dp, null);
            return new Guidance(title, descriptionPrefix + " " + description, null, icon);
        }

        @Override
        public String getTrackerPrefix() {
            return "DvrChannelRecordConflictFragment";
        }
    }

    /**
     * A fragment to show the channel watching conflict.
     * <p>
     * This fragment is automatically closed when there are no upcoming conflicts.
     */
    public static class DvrChannelWatchConflictFragment extends DvrConflictFragment
            implements OnUpcomingConflictChangeListener {
        private long mChannelId;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            Bundle args = getArguments();
            if (args != null) {
                mChannelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID);
            }
            SoftPreconditions.checkArgument(mChannelId != Channel.INVALID_ID);
            ConflictChecker checker = ((MainActivity) getContext()).getDvrConflictChecker();
            List<ScheduledRecording> conflicts = null;
            if (checker != null) {
                checker.addOnUpcomingConflictChangeListener(this);
                conflicts = checker.getUpcomingConflicts();
                if (DEBUG) Log.d(TAG, "onCreateView: upcoming conflicts: " + conflicts);
                if (conflicts.isEmpty()) {
                    dismissDialog();
                }
            }
            if (conflicts == null) {
                if (DEBUG) Log.d(TAG, "onCreateView: There's no conflict.");
                conflicts = Collections.emptyList();
            }
            if (conflicts.isEmpty()) {
                dismissDialog();
            }
            setConflicts(conflicts);
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getResources().getString(
                    R.string.dvr_epg_channel_watch_conflict_dialog_title);
            String description = getResources().getString(
                    R.string.dvr_epg_channel_watch_conflict_dialog_description);
            return new Guidance(title, description, null, null);
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_DELETE_CONFLICT)
                    .title(R.string.dvr_action_delete_schedule)
                    .build());
            actions.add(new GuidedAction.Builder(getContext())
                    .id(ACTION_CANCEL)
                    .title(R.string.dvr_action_record_program)
                    .build());
        }

        @Override
        public void onTrackedGuidedActionClicked(GuidedAction action) {
            if (action.getId() == ACTION_CANCEL) {
                ConflictChecker checker = ((MainActivity) getContext()).getDvrConflictChecker();
                if (checker != null) {
                    checker.setCheckedConflictsForChannel(mChannelId, getConflicts());
                }
            } else if (action.getId() == ACTION_DELETE_CONFLICT) {
                for (ScheduledRecording schedule : mConflicts) {
                    if (schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                        getDvrManager().stopRecording(schedule);
                    } else {
                        getDvrManager().removeScheduledRecording(schedule);
                    }
                }
            }
            super.onGuidedActionClicked(action);
        }

        @Override
        public String getTrackerPrefix() {
            return "DvrChannelWatchConflictFragment";
        }

        @Override
        public String getTrackerLabelForGuidedAction(GuidedAction action) {
            long actionId = action.getId();
            if (actionId == ACTION_CANCEL) {
                return "cancel";
            } else if (actionId == ACTION_DELETE_CONFLICT) {
                return "delete";
            } else {
                return super.getTrackerLabelForGuidedAction(action);
            }
        }

        @Override
        public void onDetach() {
            ConflictChecker checker = ((MainActivity) getContext()).getDvrConflictChecker();
            if (checker != null) {
                checker.removeOnUpcomingConflictChangeListener(this);
            }
            super.onDetach();
        }

        @Override
        public void onUpcomingConflictChange() {
            ConflictChecker checker = ((MainActivity) getContext()).getDvrConflictChecker();
            if (checker == null || checker.getUpcomingConflicts().isEmpty()) {
                if (DEBUG) Log.d(TAG, "onUpcomingConflictChange: There's no conflict.");
                dismissDialog();
            }
        }
    }
}
