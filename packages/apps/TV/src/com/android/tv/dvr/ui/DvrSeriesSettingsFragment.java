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

import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.util.LongSparseArray;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeasonEpisodeNumber;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.data.SeriesRecording.ChannelOption;
import com.android.tv.dvr.recorder.SeriesRecordingScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for DVR series recording settings.
 */
public class DvrSeriesSettingsFragment extends GuidedStepFragment
        implements DvrDataManager.SeriesRecordingListener {
    private static final String TAG = "SeriesSettingsFragment";
    private static final boolean DEBUG = false;

    private static final long ACTION_ID_PRIORITY = 10;
    private static final long ACTION_ID_CHANNEL = 11;

    private static final long SUB_ACTION_ID_CHANNEL_ALL = 102;
    // Each channel's action id = SUB_ACTION_ID_CHANNEL_ONE_BASE + channel id
    private static final long SUB_ACTION_ID_CHANNEL_ONE_BASE = 500;

    private DvrDataManager mDvrDataManager;
    private SeriesRecording mSeriesRecording;
    private long mSeriesRecordingId;
    @ChannelOption int mChannelOption;
    private long mSelectedChannelId;
    private int mBackStackCount;
    private boolean mShowViewScheduleOptionInDialog;
    private Program mCurrentProgram;

    private String mFragmentTitle;
    private String mProrityActionTitle;
    private String mProrityActionHighestText;
    private String mProrityActionLowestText;
    private String mChannelsActionTitle;
    private String mChannelsActionAllText;
    private LongSparseArray<Channel> mId2Channel = new LongSparseArray<>();
    private List<Channel> mChannels = new ArrayList<>();
    private List<Program> mPrograms;

    private GuidedAction mPriorityGuidedAction;
    private GuidedAction mChannelsGuidedAction;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mBackStackCount = getFragmentManager().getBackStackEntryCount();
        mDvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        mSeriesRecordingId = getArguments().getLong(DvrSeriesSettingsActivity.SERIES_RECORDING_ID);
        mSeriesRecording = mDvrDataManager.getSeriesRecording(mSeriesRecordingId);
        if (mSeriesRecording == null) {
            getActivity().finish();
            return;
        }
        mShowViewScheduleOptionInDialog = getArguments().getBoolean(
                DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG);
        mCurrentProgram = getArguments().getParcelable(DvrSeriesSettingsActivity.CURRENT_PROGRAM);
        mDvrDataManager.addSeriesRecordingListener(this);
        mPrograms = (List<Program>) BigArguments.getArgument(
                DvrSeriesSettingsActivity.PROGRAM_LIST);
        BigArguments.reset();
        if (mPrograms == null) {
            getActivity().finish();
            return;
        }
        Set<Long> channelIds = new HashSet<>();
        ChannelDataManager channelDataManager =
                TvApplication.getSingletons(context).getChannelDataManager();
        for (Program program : mPrograms) {
            long channelId = program.getChannelId();
            if (channelIds.add(channelId)) {
                Channel channel = channelDataManager.getChannel(channelId);
                if (channel != null) {
                    mId2Channel.put(channel.getId(), channel);
                    mChannels.add(channel);
                }
            }
        }
        mChannelOption = mSeriesRecording.getChannelOption();
        mSelectedChannelId = Channel.INVALID_ID;
        if (mChannelOption == SeriesRecording.OPTION_CHANNEL_ONE) {
            Channel channel = channelDataManager.getChannel(mSeriesRecording.getChannelId());
            if (channel != null) {
                mSelectedChannelId = channel.getId();
            } else {
                mChannelOption = SeriesRecording.OPTION_CHANNEL_ALL;
            }
        }
        mChannels.sort(Channel.CHANNEL_NUMBER_COMPARATOR);
        mFragmentTitle = getString(R.string.dvr_series_settings_title);
        mProrityActionTitle = getString(R.string.dvr_series_settings_priority);
        mProrityActionHighestText = getString(R.string.dvr_series_settings_priority_highest);
        mProrityActionLowestText = getString(R.string.dvr_series_settings_priority_lowest);
        mChannelsActionTitle = getString(R.string.dvr_series_settings_channels);
        mChannelsActionAllText = getString(R.string.dvr_series_settings_channels_all);
    }

    @Override
    public void onResume() {
        super.onResume();
        // To avoid the order of series's priority has changed, but series doesn't get update.
        updatePriorityGuidedAction();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mDvrDataManager.removeSeriesRecordingListener(this);
    }

    @Override
    public void onDestroy() {
        if (getFragmentManager().getBackStackEntryCount() == mBackStackCount && getArguments()
                .getBoolean(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING)) {
            mDvrDataManager.checkAndRemoveEmptySeriesRecording(mSeriesRecordingId);
        }
        super.onDestroy();
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String breadcrumb = mSeriesRecording.getTitle();
        String title = mFragmentTitle;
        return new Guidance(title, null, breadcrumb, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        mPriorityGuidedAction = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_PRIORITY)
                .title(mProrityActionTitle)
                .build();
        actions.add(mPriorityGuidedAction);

        mChannelsGuidedAction = new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_CHANNEL)
                .title(mChannelsActionTitle)
                .subActions(buildChannelSubAction())
                .build();
        actions.add(mChannelsGuidedAction);
        updateChannelsGuidedAction(false);
    }

    @Override
    public void onCreateButtonActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_OK)
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == GuidedAction.ACTION_ID_OK) {
            if (mChannelOption != mSeriesRecording.getChannelOption()
                    || mSeriesRecording.isStopped()
                    || (mChannelOption == SeriesRecording.OPTION_CHANNEL_ONE
                            && mSeriesRecording.getChannelId() != mSelectedChannelId)) {
                SeriesRecording.Builder builder = SeriesRecording.buildFrom(mSeriesRecording)
                        .setChannelOption(mChannelOption)
                        .setState(SeriesRecording.STATE_SERIES_NORMAL);
                if (mSelectedChannelId != Channel.INVALID_ID) {
                    builder.setChannelId(mSelectedChannelId);
                }
                DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
                        dvrManager.updateSeriesRecording(builder.build());
                if (mCurrentProgram != null && (mChannelOption == SeriesRecording.OPTION_CHANNEL_ALL
                        || mSelectedChannelId == mCurrentProgram.getChannelId())) {
                    dvrManager.addSchedule(mCurrentProgram);
                }
                updateSchedulesToSeries();
                showConfirmDialog();
            } else {
                showConfirmDialog();
            }
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepFragments();
        } else if (actionId == ACTION_ID_PRIORITY) {
            FragmentManager fragmentManager = getFragmentManager();
            DvrPrioritySettingsFragment fragment = new DvrPrioritySettingsFragment();
            Bundle args = new Bundle();
            args.putLong(DvrPrioritySettingsFragment.COME_FROM_SERIES_RECORDING_ID,
                    mSeriesRecording.getId());
            fragment.setArguments(args);
            GuidedStepFragment.add(fragmentManager, fragment, R.id.dvr_settings_view_frame);
        }
    }

    @Override
    public boolean onSubGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == SUB_ACTION_ID_CHANNEL_ALL) {
            mChannelOption = SeriesRecording.OPTION_CHANNEL_ALL;
            mSelectedChannelId = Channel.INVALID_ID;
            updateChannelsGuidedAction(true);
            return true;
        } else if (actionId > SUB_ACTION_ID_CHANNEL_ONE_BASE) {
            mChannelOption = SeriesRecording.OPTION_CHANNEL_ONE;
            mSelectedChannelId = actionId - SUB_ACTION_ID_CHANNEL_ONE_BASE;
            updateChannelsGuidedAction(true);
            return true;
        }
        return false;
    }

    @Override
    public GuidedActionsStylist onCreateButtonActionsStylist() {
        return new DvrGuidedActionsStylist(true);
    }

    private void updateChannelsGuidedAction(boolean notifyActionChanged) {
        if (mChannelOption == SeriesRecording.OPTION_CHANNEL_ALL) {
            mChannelsGuidedAction.setDescription(mChannelsActionAllText);
        } else if (mId2Channel.get(mSelectedChannelId) != null){
            mChannelsGuidedAction.setDescription(mId2Channel.get(mSelectedChannelId)
                    .getDisplayText());
        }
        if (notifyActionChanged) {
            notifyActionChanged(findActionPositionById(ACTION_ID_CHANNEL));
        }
    }

    private void updatePriorityGuidedAction() {
        int totalSeriesCount = 0;
        int priorityOrder = 0;
        for (SeriesRecording seriesRecording : mDvrDataManager.getSeriesRecordings()) {
            if (seriesRecording.getState() == SeriesRecording.STATE_SERIES_NORMAL
                    || seriesRecording.getId() == mSeriesRecording.getId()) {
                ++totalSeriesCount;
            }
            if (seriesRecording.getState() == SeriesRecording.STATE_SERIES_NORMAL
                    && seriesRecording.getId() != mSeriesRecording.getId()
                    && seriesRecording.getPriority() > mSeriesRecording.getPriority()) {
                ++priorityOrder;
            }
        }
        if (priorityOrder == 0) {
            mPriorityGuidedAction.setDescription(mProrityActionHighestText);
        } else if (priorityOrder >= totalSeriesCount - 1) {
            mPriorityGuidedAction.setDescription(mProrityActionLowestText);
        } else {
            mPriorityGuidedAction.setDescription(getString(
                    R.string.dvr_series_settings_priority_rank, priorityOrder + 1));
        }
        notifyActionChanged(findActionPositionById(ACTION_ID_PRIORITY));
    }

    private void updateSchedulesToSeries() {
        List<Program> recordingCandidates = new ArrayList<>();
        Set<SeasonEpisodeNumber> scheduledEpisodes = new HashSet<>();
        for (ScheduledRecording r : mDvrDataManager.getScheduledRecordings(mSeriesRecordingId)) {
            if (r.getState() != ScheduledRecording.STATE_RECORDING_FAILED
                    && r.getState() != ScheduledRecording.STATE_RECORDING_CLIPPED) {
                scheduledEpisodes.add(new SeasonEpisodeNumber(
                        r.getSeriesRecordingId(), r.getSeasonNumber(), r.getEpisodeNumber()));
            }
        }
        for (Program program : mPrograms) {
            // Removes current programs and scheduled episodes out, matches the channel option.
            if (program.getStartTimeUtcMillis() >= System.currentTimeMillis()
                    && mSeriesRecording.matchProgram(program)
                    && !scheduledEpisodes.contains(new SeasonEpisodeNumber(
                    mSeriesRecordingId, program.getSeasonNumber(), program.getEpisodeNumber()))) {
                recordingCandidates.add(program);
            }
        }
        if (recordingCandidates.isEmpty()) {
            return;
        }
        List<Program> programsToSchedule = SeriesRecordingScheduler.pickOneProgramPerEpisode(
                mDvrDataManager, Collections.singletonList(mSeriesRecording), recordingCandidates)
                .get(mSeriesRecordingId);
        if (!programsToSchedule.isEmpty()) {
            TvApplication.getSingletons(getContext()).getDvrManager()
                    .addScheduleToSeriesRecording(mSeriesRecording, programsToSchedule);
        }
    }

    private List<GuidedAction> buildChannelSubAction() {
        List<GuidedAction> channelSubActions = new ArrayList<>();
        channelSubActions.add(new GuidedAction.Builder(getActivity())
                .id(SUB_ACTION_ID_CHANNEL_ALL)
                .title(mChannelsActionAllText)
                .build());
        for (Channel channel : mChannels) {
            channelSubActions.add(new GuidedAction.Builder(getActivity())
                    .id(SUB_ACTION_ID_CHANNEL_ONE_BASE + channel.getId())
                    .title(channel.getDisplayText())
                    .build());
        }
        return channelSubActions;
    }

    private void showConfirmDialog() {
        DvrUiHelper.StartSeriesScheduledDialogActivity(getContext(), mSeriesRecording,
                mShowViewScheduleOptionInDialog, mPrograms);
        finishGuidedStepFragments();
    }

    @Override
    public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) { }

    @Override
    public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
        for (SeriesRecording series : seriesRecordings) {
            if (series.getId() == mSeriesRecording.getId()) {
                finishGuidedStepFragments();
                return;
            }
        }
    }

    @Override
    public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
        for (SeriesRecording seriesRecording : seriesRecordings) {
            if (seriesRecording.getId() == mSeriesRecordingId) {
                mSeriesRecording = seriesRecording;
                updatePriorityGuidedAction();
                return;
            }
        }
    }
}