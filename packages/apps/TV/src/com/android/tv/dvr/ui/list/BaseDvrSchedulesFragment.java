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

import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.ScheduledRecording;

/**
 * A  base fragment to show the list of schedule recordings.
 */
public abstract class BaseDvrSchedulesFragment extends DetailsFragment
        implements DvrDataManager.ScheduledRecordingListener,
        DvrScheduleManager.OnConflictStateChangeListener {
    /**
     * The key for scheduled recording which has be selected in the list.
     */
    public static final String SCHEDULES_KEY_SCHEDULED_RECORDING =
            "schedules_key_scheduled_recording";

    private ScheduleRowAdapter mRowsAdapter;
    private TextView mEmptyInfoScreenView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(SchedulesHeaderRow.class, onCreateHeaderRowPresenter());
        presenterSelector.addClassPresenter(ScheduleRow.class, onCreateRowPresenter());
        mRowsAdapter = onCreateRowsAdapter(presenterSelector);
        setAdapter(mRowsAdapter);
        mRowsAdapter.start();
        ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
        singletons.getDvrDataManager().addScheduledRecordingListener(this);
        singletons.getDvrScheduleManager().addOnConflictStateChangeListener(this);
        mEmptyInfoScreenView = (TextView) getActivity().findViewById(R.id.empty_info_screen);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        int firstItemPosition = getFirstItemPosition();
        if (firstItemPosition != -1) {
            getRowsFragment().setSelectedPosition(firstItemPosition, false);
        }
        return view;
    }

    /**
     * Returns rows adapter.
     */
    protected ScheduleRowAdapter getRowsAdapter() {
        return mRowsAdapter;
    }

    /**
     * Shows the empty message.
     */
    void showEmptyMessage(int messageId) {
        mEmptyInfoScreenView.setText(messageId);
        if (mEmptyInfoScreenView.getVisibility() != View.VISIBLE) {
            mEmptyInfoScreenView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hides the empty message.
     */
    void hideEmptyMessage() {
        if (mEmptyInfoScreenView.getVisibility() == View.VISIBLE) {
            mEmptyInfoScreenView.setVisibility(View.GONE);
        }
    }

    @Override
    public View onInflateTitleView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        // Workaround of b/31046014
        return null;
    }

    @Override
    public void onDestroy() {
        ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
        singletons.getDvrScheduleManager().removeOnConflictStateChangeListener(this);
        singletons.getDvrDataManager().removeScheduledRecordingListener(this);
        mRowsAdapter.stop();
        super.onDestroy();
    }

    /**
     * Creates header row presenter.
     */
    public abstract SchedulesHeaderRowPresenter onCreateHeaderRowPresenter();

    /**
     * Creates rows presenter.
     */
    public abstract ScheduleRowPresenter onCreateRowPresenter();

    /**
     * Creates rows adapter.
     */
    public abstract ScheduleRowAdapter onCreateRowsAdapter(ClassPresenterSelector presenterSelecor);

    /**
     * Gets the first focus position in schedules list.
     */
    protected int getFirstItemPosition() {
        for (int i = 0; i < mRowsAdapter.size(); i++) {
            if (mRowsAdapter.get(i) instanceof ScheduleRow) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
        if (mRowsAdapter != null) {
            for (ScheduledRecording recording : scheduledRecordings) {
                mRowsAdapter.onScheduledRecordingAdded(recording);
            }
        }
    }

    @Override
    public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
        if (mRowsAdapter != null) {
            for (ScheduledRecording recording : scheduledRecordings) {
                mRowsAdapter.onScheduledRecordingRemoved(recording);
            }
        }
    }

    @Override
    public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
        if (mRowsAdapter != null) {
            for (ScheduledRecording recording : scheduledRecordings) {
                mRowsAdapter.onScheduledRecordingUpdated(recording, false);
            }
        }
    }

    @Override
    public void onConflictStateChange(boolean conflict, ScheduledRecording... schedules) {
        if (mRowsAdapter != null) {
            for (ScheduledRecording recording : schedules) {
                mRowsAdapter.onScheduledRecordingUpdated(recording, true);
            }
        }
    }
}