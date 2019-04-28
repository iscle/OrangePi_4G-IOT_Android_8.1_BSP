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
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.text.TextUtils;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.ui.GuidedActionsStylistWithDivider;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Fragment for DVR series recording settings.
 */
public class DvrSeriesDeletionFragment extends GuidedStepFragment {
    private static final long WATCHED_TIME_UNIT_THRESHOLD = TimeUnit.MINUTES.toMillis(2);

    // Since recordings' IDs are used as its check actions' IDs, which are random positive numbers,
    // negative values are used by other actions to prevent duplicated IDs.
    private static final long ACTION_ID_SELECT_WATCHED = -110;
    private static final long ACTION_ID_SELECT_ALL = -111;
    private static final long ACTION_ID_DELETE = -112;

    private DvrDataManager mDvrDataManager;
    private DvrWatchedPositionManager mDvrWatchedPositionManager;
    private List<RecordedProgram> mRecordings;
    private final Set<Long> mWatchedRecordings = new HashSet<>();
    private boolean mAllSelected;
    private long mSeriesRecordingId;
    private int mOneLineActionHeight;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSeriesRecordingId = getArguments()
                .getLong(DvrSeriesDeletionActivity.SERIES_RECORDING_ID, -1);
        SoftPreconditions.checkArgument(mSeriesRecordingId != -1);
        mDvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        mDvrWatchedPositionManager =
                TvApplication.getSingletons(context).getDvrWatchedPositionManager();
        mRecordings = mDvrDataManager.getRecordedPrograms(mSeriesRecordingId);
        mOneLineActionHeight = getResources().getDimensionPixelSize(
                R.dimen.dvr_settings_one_line_action_container_height);
        if (mRecordings.isEmpty()) {
            Toast.makeText(getActivity(), getString(R.string.dvr_series_deletion_no_recordings),
                    Toast.LENGTH_LONG).show();
            finishGuidedStepFragments();
            return;
        }
        Collections.sort(mRecordings, RecordedProgram.EPISODE_COMPARATOR);
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String breadcrumb = null;
        SeriesRecording series = mDvrDataManager.getSeriesRecording(mSeriesRecordingId);
        if (series != null) {
            breadcrumb = series.getTitle();
        }
        return new Guidance(getString(R.string.dvr_series_deletion_title),
                getString(R.string.dvr_series_deletion_description), breadcrumb, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_SELECT_WATCHED)
                .title(getString(R.string.dvr_series_select_watched))
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_SELECT_ALL)
                .title(getString(R.string.dvr_series_select_all))
                .build());
        actions.add(GuidedActionsStylistWithDivider.createDividerAction(getContext()));
        for (RecordedProgram recording : mRecordings) {
            long watchedPositionMs =
                    mDvrWatchedPositionManager.getWatchedPosition(recording.getId());
            String title = recording.getEpisodeDisplayTitle(getContext());
            if (TextUtils.isEmpty(title)) {
                title = TextUtils.isEmpty(recording.getTitle()) ?
                        getString(R.string.channel_banner_no_title) : recording.getTitle();
            }
            String description;
            if (watchedPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                description = getWatchedString(watchedPositionMs, recording.getDurationMillis());
                mWatchedRecordings.add(recording.getId());
            } else {
                description = getString(R.string.dvr_series_never_watched);
            }
            actions.add(new GuidedAction.Builder(getActivity())
                    .id(recording.getId())
                    .title(title)
                    .description(description)
                    .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                    .build());
        }
    }

    @Override
    public void onCreateButtonActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_DELETE)
                .title(getString(R.string.dvr_detail_delete))
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_ID_DELETE) {
            List<Long> idsToDelete = new ArrayList<>();
            for (GuidedAction guidedAction : getActions()) {
                if (guidedAction.getCheckSetId() == GuidedAction.CHECKBOX_CHECK_SET_ID
                        && guidedAction.isChecked()) {
                    idsToDelete.add(guidedAction.getId());
                }
            }
            if (!idsToDelete.isEmpty()) {
                DvrManager dvrManager = TvApplication.getSingletons(getActivity()).getDvrManager();
                dvrManager.removeRecordedPrograms(idsToDelete);
            }
            Toast.makeText(getContext(), getResources().getQuantityString(
                    R.plurals.dvr_msg_episodes_deleted, idsToDelete.size(), idsToDelete.size(),
                    mRecordings.size()), Toast.LENGTH_LONG).show();
            finishGuidedStepFragments();
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepFragments();
        } else if (actionId == ACTION_ID_SELECT_WATCHED) {
            for (GuidedAction guidedAction : getActions()) {
                if (guidedAction.getCheckSetId() == GuidedAction.CHECKBOX_CHECK_SET_ID) {
                    long recordingId = guidedAction.getId();
                    if (mWatchedRecordings.contains(recordingId)) {
                        guidedAction.setChecked(true);
                    } else {
                        guidedAction.setChecked(false);
                    }
                    notifyActionChanged(findActionPositionById(recordingId));
                }
            }
            mAllSelected = updateSelectAllState();
        } else if (actionId == ACTION_ID_SELECT_ALL) {
            mAllSelected = !mAllSelected;
            for (GuidedAction guidedAction : getActions()) {
                if (guidedAction.getCheckSetId() == GuidedAction.CHECKBOX_CHECK_SET_ID) {
                    guidedAction.setChecked(mAllSelected);
                    notifyActionChanged(findActionPositionById(guidedAction.getId()));
                }
            }
            updateSelectAllState(action, mAllSelected);
        } else {
            mAllSelected = updateSelectAllState();
        }
    }

    @Override
    public GuidedActionsStylist onCreateButtonActionsStylist() {
        return new DvrGuidedActionsStylist(true);
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return new GuidedActionsStylistWithDivider() {
            @Override
            public void onBindViewHolder(ViewHolder vh, GuidedAction action) {
                super.onBindViewHolder(vh, action);
                if (action.getId() == ACTION_DIVIDER) {
                    return;
                }
                LayoutParams lp = vh.itemView.getLayoutParams();
                if (action.getCheckSetId() != GuidedAction.CHECKBOX_CHECK_SET_ID) {
                    lp.height = mOneLineActionHeight;
                } else {
                    vh.itemView.setLayoutParams(
                            new LayoutParams(lp.width, LayoutParams.WRAP_CONTENT));
                }
            }
        };
    }

    private String getWatchedString(long watchedPositionMs, long durationMs) {
        if (durationMs > WATCHED_TIME_UNIT_THRESHOLD) {
            return getResources().getString(R.string.dvr_series_watched_info_minutes,
                    Math.max(1, Utils.getRoundOffMinsFromMs(watchedPositionMs)),
                    Utils.getRoundOffMinsFromMs(durationMs));
        } else {
            return getResources().getString(R.string.dvr_series_watched_info_seconds,
                    Math.max(1, TimeUnit.MILLISECONDS.toSeconds(watchedPositionMs)),
                    TimeUnit.MILLISECONDS.toSeconds(durationMs));
        }
    }

    private boolean updateSelectAllState() {
        for (GuidedAction guidedAction : getActions()) {
            if (guidedAction.getCheckSetId() == GuidedAction.CHECKBOX_CHECK_SET_ID) {
                if (!guidedAction.isChecked()) {
                    if (mAllSelected) {
                        updateSelectAllState(findActionById(ACTION_ID_SELECT_ALL), false);
                    }
                    return false;
                }
            }
        }
        if (!mAllSelected) {
            updateSelectAllState(findActionById(ACTION_ID_SELECT_ALL), true);
        }
        return true;
    }

    private void updateSelectAllState(GuidedAction selectAll, boolean select) {
        selectAll.setTitle(select ? getString(R.string.dvr_series_deselect_all)
                : getString(R.string.dvr_series_select_all));
        notifyActionChanged(findActionPositionById(ACTION_ID_SELECT_ALL));
    }
}
