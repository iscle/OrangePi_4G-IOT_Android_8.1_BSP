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

package com.android.tv.dvr.ui.browse;

import android.content.res.Resources;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.data.RecordedProgram;

/**
 * {@link android.support.v17.leanback.app.DetailsFragment} for recorded program in DVR.
 */
public class RecordedProgramDetailsFragment extends DvrDetailsFragment
        implements DvrDataManager.RecordedProgramListener {
    private static final int ACTION_RESUME_PLAYING = 1;
    private static final int ACTION_PLAY_FROM_BEGINNING = 2;
    private static final int ACTION_DELETE_RECORDING = 3;

    private DvrWatchedPositionManager mDvrWatchedPositionManager;

    private RecordedProgram mRecordedProgram;
    private boolean mPaused;
    private DvrDataManager mDvrDataManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mDvrDataManager = TvApplication.getSingletons(getContext()).getDvrDataManager();
        mDvrDataManager.addRecordedProgramListener(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreateInternal() {
        mDvrWatchedPositionManager = TvApplication.getSingletons(getActivity())
                .getDvrWatchedPositionManager();
        setDetailsOverviewRow(DetailsContent
                .createFromRecordedProgram(getContext(), mRecordedProgram));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPaused) {
            updateActions();
            mPaused = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    public void onDestroy() {
        mDvrDataManager.removeRecordedProgramListener(this);
        super.onDestroy();
    }

    @Override
    protected boolean onLoadRecordingDetails(Bundle args) {
        long recordedProgramId = args.getLong(DvrDetailsActivity.RECORDING_ID);
        mRecordedProgram = mDvrDataManager.getRecordedProgram(recordedProgramId);
        return mRecordedProgram != null;
    }

    @Override
    protected SparseArrayObjectAdapter onCreateActionsAdapter() {
        SparseArrayObjectAdapter adapter =
                new SparseArrayObjectAdapter(new ActionPresenterSelector());
        Resources res = getResources();
        if (mDvrWatchedPositionManager.getWatchedStatus(mRecordedProgram)
                == DvrWatchedPositionManager.DVR_WATCHED_STATUS_WATCHING) {
            adapter.set(ACTION_RESUME_PLAYING, new Action(ACTION_RESUME_PLAYING,
                    res.getString(R.string.dvr_detail_resume_play), null,
                    res.getDrawable(R.drawable.lb_ic_play)));
            adapter.set(ACTION_PLAY_FROM_BEGINNING, new Action(ACTION_PLAY_FROM_BEGINNING,
                    res.getString(R.string.dvr_detail_play_from_beginning), null,
                    res.getDrawable(R.drawable.lb_ic_replay)));
        } else {
            adapter.set(ACTION_PLAY_FROM_BEGINNING, new Action(ACTION_PLAY_FROM_BEGINNING,
                    res.getString(R.string.dvr_detail_watch), null,
                    res.getDrawable(R.drawable.lb_ic_play)));
        }
        adapter.set(ACTION_DELETE_RECORDING, new Action(ACTION_DELETE_RECORDING,
                res.getString(R.string.dvr_detail_delete), null,
                res.getDrawable(R.drawable.ic_delete_32dp)));
        return adapter;
    }

    @Override
    protected OnActionClickedListener onCreateOnActionClickedListener() {
        return new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_PLAY_FROM_BEGINNING) {
                    startPlayback(mRecordedProgram, TvInputManager.TIME_SHIFT_INVALID_TIME);
                } else if (action.getId() == ACTION_RESUME_PLAYING) {
                    startPlayback(mRecordedProgram, mDvrWatchedPositionManager
                            .getWatchedPosition(mRecordedProgram.getId()));
                } else if (action.getId() == ACTION_DELETE_RECORDING) {
                    DvrManager dvrManager = TvApplication
                            .getSingletons(getActivity()).getDvrManager();
                    dvrManager.removeRecordedProgram(mRecordedProgram);
                    getActivity().finish();
                }
            }
        };
    }

    @Override
    public void onRecordedProgramsAdded(RecordedProgram... recordedPrograms) { }

    @Override
    public void onRecordedProgramsChanged(RecordedProgram... recordedPrograms) { }

    @Override
    public void onRecordedProgramsRemoved(RecordedProgram... recordedPrograms) {
        for (RecordedProgram recordedProgram : recordedPrograms) {
            if (recordedProgram.getId() == mRecordedProgram.getId()) {
                getActivity().finish();
            }
        }
    }
}
