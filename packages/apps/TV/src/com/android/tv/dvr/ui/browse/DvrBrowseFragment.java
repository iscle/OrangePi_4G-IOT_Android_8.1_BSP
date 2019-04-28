/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.TitleViewAdapter;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.GenreItems;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.OnDvrScheduleLoadFinishedListener;
import com.android.tv.dvr.DvrDataManager.OnRecordedProgramLoadFinishedListener;
import com.android.tv.dvr.DvrDataManager.RecordedProgramListener;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrDataManager.SeriesRecordingListener;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.SortedArrayAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * {@link BrowseFragment} for DVR functions.
 */
public class DvrBrowseFragment extends BrowseFragment implements
        RecordedProgramListener, ScheduledRecordingListener, SeriesRecordingListener,
        OnDvrScheduleLoadFinishedListener, OnRecordedProgramLoadFinishedListener {
    private static final String TAG = "DvrBrowseFragment";
    private static final boolean DEBUG = false;

    private static final int MAX_RECENT_ITEM_COUNT = 10;
    private static final int MAX_SCHEDULED_ITEM_COUNT = 4;

    private boolean mShouldShowScheduleRow;
    private boolean mEntranceTransitionEnded;

    private RecordedProgramAdapter mRecentAdapter;
    private ScheduleAdapter mScheduleAdapter;
    private SeriesAdapter mSeriesAdapter;
    private RecordedProgramAdapter[] mGenreAdapters =
            new RecordedProgramAdapter[GenreItems.getGenreCount() + 1];
    private ListRow mRecentRow;
    private ListRow mScheduledRow;
    private ListRow mSeriesRow;
    private ListRow[] mGenreRows = new ListRow[GenreItems.getGenreCount() + 1];
    private List<String> mGenreLabels;
    private DvrDataManager mDvrDataManager;
    private DvrScheduleManager mDvrScheudleManager;
    private ArrayObjectAdapter mRowsAdapter;
    private ClassPresenterSelector mPresenterSelector;
    private final HashMap<String, RecordedProgram> mSeriesId2LatestProgram = new HashMap<>();
    private final Handler mHandler = new Handler();
    private final OnGlobalFocusChangeListener mOnGlobalFocusChangeListener =
            new OnGlobalFocusChangeListener() {
                @Override
                public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                    if (oldFocus instanceof RecordingCardView) {
                        ((RecordingCardView) oldFocus).expandTitle(false, true);
                    }
                    if (newFocus instanceof RecordingCardView) {
                        // If the header transition is ongoing, expand cards immediately without
                        // animation to make a smooth transition.
                        ((RecordingCardView) newFocus).expandTitle(true, !isInHeadersTransition());
                    }
                }
            };

    private final Comparator<Object> RECORDED_PROGRAM_COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object lhs, Object rhs) {
            if (lhs instanceof SeriesRecording) {
                lhs = mSeriesId2LatestProgram.get(((SeriesRecording) lhs).getSeriesId());
            }
            if (rhs instanceof SeriesRecording) {
                rhs = mSeriesId2LatestProgram.get(((SeriesRecording) rhs).getSeriesId());
            }
            if (lhs instanceof RecordedProgram) {
                if (rhs instanceof RecordedProgram) {
                    return RecordedProgram.START_TIME_THEN_ID_COMPARATOR.reversed()
                            .compare((RecordedProgram) lhs, (RecordedProgram) rhs);
                } else {
                    return -1;
                }
            } else if (rhs instanceof RecordedProgram) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    private static final Comparator<Object> SCHEDULE_COMPARATOR = new Comparator<Object>() {
        @Override
        public int compare(Object lhs, Object rhs) {
            if (lhs instanceof ScheduledRecording) {
                if (rhs instanceof ScheduledRecording) {
                    return ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR
                            .compare((ScheduledRecording) lhs, (ScheduledRecording) rhs);
                } else {
                    return -1;
                }
            } else if (rhs instanceof ScheduledRecording) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    private final DvrScheduleManager.OnConflictStateChangeListener mOnConflictStateChangeListener =
            new DvrScheduleManager.OnConflictStateChangeListener() {
        @Override
        public void onConflictStateChange(boolean conflict, ScheduledRecording... schedules) {
            if (mScheduleAdapter != null) {
                for (ScheduledRecording schedule : schedules) {
                    onScheduledRecordingConflictStatusChanged(schedule);
                }
            }
        }
    };

    private final Runnable mUpdateRowsRunnable = new Runnable() {
        @Override
        public void run() {
            updateRows();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        Context context = getContext();
        ApplicationSingletons singletons = TvApplication.getSingletons(context);
        mDvrDataManager = singletons.getDvrDataManager();
        mDvrScheudleManager = singletons.getDvrScheduleManager();
        mPresenterSelector = new ClassPresenterSelector()
                .addClassPresenter(ScheduledRecording.class,
                        new ScheduledRecordingPresenter(context))
                .addClassPresenter(RecordedProgram.class, new RecordedProgramPresenter(context))
                .addClassPresenter(SeriesRecording.class, new SeriesRecordingPresenter(context))
                .addClassPresenter(FullScheduleCardHolder.class,
                        new FullSchedulesCardPresenter(context));
        mGenreLabels = new ArrayList<>(Arrays.asList(GenreItems.getLabels(context)));
        mGenreLabels.add(getString(R.string.dvr_main_others));
        prepareUiElements();
        if (!startBrowseIfDvrInitialized()) {
            if (!mDvrDataManager.isDvrScheduleLoadFinished()) {
                mDvrDataManager.addDvrScheduleLoadFinishedListener(this);
            }
            if (!mDvrDataManager.isRecordedProgramLoadFinished()) {
                mDvrDataManager.addRecordedProgramLoadFinishedListener(this);
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.getViewTreeObserver().addOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);
    }

    @Override
    public void onDestroyView() {
        getView().getViewTreeObserver()
                .removeOnGlobalFocusChangeListener(mOnGlobalFocusChangeListener);
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        mHandler.removeCallbacks(mUpdateRowsRunnable);
        mDvrScheudleManager.removeOnConflictStateChangeListener(mOnConflictStateChangeListener);
        mDvrDataManager.removeRecordedProgramListener(this);
        mDvrDataManager.removeScheduledRecordingListener(this);
        mDvrDataManager.removeSeriesRecordingListener(this);
        mDvrDataManager.removeDvrScheduleLoadFinishedListener(this);
        mDvrDataManager.removeRecordedProgramLoadFinishedListener(this);
        mRowsAdapter.clear();
        mSeriesId2LatestProgram.clear();
        for (Presenter presenter : mPresenterSelector.getPresenters()) {
            if (presenter instanceof DvrItemPresenter) {
                ((DvrItemPresenter) presenter).unbindAllViewHolders();
            }
        }
        super.onDestroy();
    }

    @Override
    public void onDvrScheduleLoadFinished() {
        startBrowseIfDvrInitialized();
        mDvrDataManager.removeDvrScheduleLoadFinishedListener(this);
    }

    @Override
    public void onRecordedProgramLoadFinished() {
        startBrowseIfDvrInitialized();
        mDvrDataManager.removeRecordedProgramLoadFinishedListener(this);
    }

    @Override
    public void onRecordedProgramsAdded(RecordedProgram... recordedPrograms) {
        for (RecordedProgram recordedProgram : recordedPrograms) {
            handleRecordedProgramAdded(recordedProgram, true);
        }
        postUpdateRows();
    }

    @Override
    public void onRecordedProgramsChanged(RecordedProgram... recordedPrograms) {
        for (RecordedProgram recordedProgram : recordedPrograms) {
            handleRecordedProgramChanged(recordedProgram);
        }
        postUpdateRows();
    }

    @Override
    public void onRecordedProgramsRemoved(RecordedProgram... recordedPrograms) {
        for (RecordedProgram recordedProgram : recordedPrograms) {
            handleRecordedProgramRemoved(recordedProgram);
        }
        postUpdateRows();
    }

    // No need to call updateRows() during ScheduledRecordings' change because
    // the row for ScheduledRecordings is always displayed.
    @Override
    public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording scheduleRecording : scheduledRecordings) {
            if (needToShowScheduledRecording(scheduleRecording)) {
                mScheduleAdapter.add(scheduleRecording);
            }
        }
    }

    @Override
    public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording scheduleRecording : scheduledRecordings) {
            mScheduleAdapter.remove(scheduleRecording);
        }
    }

    @Override
    public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording scheduleRecording : scheduledRecordings) {
            if (needToShowScheduledRecording(scheduleRecording)) {
                mScheduleAdapter.change(scheduleRecording);
            } else {
                mScheduleAdapter.removeWithId(scheduleRecording);
            }
        }
    }

    private void onScheduledRecordingConflictStatusChanged(ScheduledRecording... schedules) {
        for (ScheduledRecording schedule : schedules) {
            if (needToShowScheduledRecording(schedule)) {
                if (mScheduleAdapter.contains(schedule)) {
                    mScheduleAdapter.change(schedule);
                }
            } else {
                mScheduleAdapter.removeWithId(schedule);
            }
        }
    }

    @Override
    public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) {
        handleSeriesRecordingsAdded(Arrays.asList(seriesRecordings));
        postUpdateRows();
    }

    @Override
    public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
        handleSeriesRecordingsRemoved(Arrays.asList(seriesRecordings));
        postUpdateRows();
    }

    @Override
    public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
        handleSeriesRecordingsChanged(Arrays.asList(seriesRecordings));
        postUpdateRows();
    }

    // Workaround of b/29108300
    @Override
    public void showTitle(int flags) {
        flags &= ~TitleViewAdapter.SEARCH_VIEW_VISIBLE;
        super.showTitle(flags);
    }

    @Override
    protected void onEntranceTransitionEnd() {
        super.onEntranceTransitionEnd();
        if (mShouldShowScheduleRow) {
            showScheduledRowInternal();
        }
        mEntranceTransitionEnded = true;
    }

    void showScheduledRow() {
        if (!mEntranceTransitionEnded) {
            setHeadersState(HEADERS_HIDDEN);
            mShouldShowScheduleRow = true;
        } else {
            showScheduledRowInternal();
        }
    }

    private void showScheduledRowInternal() {
        setSelectedPosition(mRowsAdapter.indexOf(mScheduledRow), true, null);
        if (getHeadersState() == HEADERS_ENABLED) {
            startHeadersTransition(false);
        }
        mShouldShowScheduleRow = false;
    }

    private void prepareUiElements() {
        setBadgeDrawable(getActivity().getDrawable(R.drawable.ic_dvr_badge));
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(false);
        setBrandColor(getResources().getColor(R.color.program_guide_side_panel_background, null));
        mRowsAdapter = new ArrayObjectAdapter(new DvrListRowPresenter(getContext()));
        setAdapter(mRowsAdapter);
        prepareEntranceTransition();
    }

    private boolean startBrowseIfDvrInitialized() {
        if (mDvrDataManager.isInitialized()) {
            // Setup rows
            mRecentAdapter = new RecordedProgramAdapter(MAX_RECENT_ITEM_COUNT);
            mScheduleAdapter = new ScheduleAdapter(MAX_SCHEDULED_ITEM_COUNT);
            mSeriesAdapter = new SeriesAdapter();
            for (int i = 0; i < mGenreAdapters.length; i++) {
                mGenreAdapters[i] = new RecordedProgramAdapter();
            }
            // Schedule Recordings.
            List<ScheduledRecording> schedules = mDvrDataManager.getAllScheduledRecordings();
            onScheduledRecordingAdded(ScheduledRecording.toArray(schedules));
            mScheduleAdapter.addExtraItem(FullScheduleCardHolder.FULL_SCHEDULE_CARD_HOLDER);
            // Recorded Programs.
            for (RecordedProgram recordedProgram : mDvrDataManager.getRecordedPrograms()) {
                handleRecordedProgramAdded(recordedProgram, false);
            }
            // Series Recordings. Series recordings should be added after recorded programs, because
            // we build series recordings' latest program information while adding recorded programs.
            List<SeriesRecording> recordings = mDvrDataManager.getSeriesRecordings();
            handleSeriesRecordingsAdded(recordings);
            mRecentRow = new ListRow(new HeaderItem(
                    getString(R.string.dvr_main_recent)), mRecentAdapter);
            mScheduledRow = new ListRow(new HeaderItem(
                    getString(R.string.dvr_main_scheduled)), mScheduleAdapter);
            mSeriesRow = new ListRow(new HeaderItem(
                    getString(R.string.dvr_main_series)), mSeriesAdapter);
            mRowsAdapter.add(mScheduledRow);
            updateRows();
            // Initialize listeners
            mDvrDataManager.addRecordedProgramListener(this);
            mDvrDataManager.addScheduledRecordingListener(this);
            mDvrDataManager.addSeriesRecordingListener(this);
            mDvrScheudleManager.addOnConflictStateChangeListener(mOnConflictStateChangeListener);
            startEntranceTransition();
            return true;
        }
        return false;
    }

    private void handleRecordedProgramAdded(RecordedProgram recordedProgram,
            boolean updateSeriesRecording) {
        mRecentAdapter.add(recordedProgram);
        String seriesId = recordedProgram.getSeriesId();
        SeriesRecording seriesRecording = null;
        if (seriesId != null) {
            seriesRecording = mDvrDataManager.getSeriesRecording(seriesId);
            RecordedProgram latestProgram = mSeriesId2LatestProgram.get(seriesId);
            if (latestProgram == null || RecordedProgram.START_TIME_THEN_ID_COMPARATOR
                    .compare(latestProgram, recordedProgram) < 0) {
                mSeriesId2LatestProgram.put(seriesId, recordedProgram);
                if (updateSeriesRecording && seriesRecording != null) {
                    onSeriesRecordingChanged(seriesRecording);
                }
            }
        }
        if (seriesRecording == null) {
            for (RecordedProgramAdapter adapter
                    : getGenreAdapters(recordedProgram.getCanonicalGenres())) {
                adapter.add(recordedProgram);
            }
        }
    }

    private void handleRecordedProgramRemoved(RecordedProgram recordedProgram) {
        mRecentAdapter.remove(recordedProgram);
        String seriesId = recordedProgram.getSeriesId();
        if (seriesId != null) {
            SeriesRecording seriesRecording = mDvrDataManager.getSeriesRecording(seriesId);
            RecordedProgram latestProgram =
                    mSeriesId2LatestProgram.get(recordedProgram.getSeriesId());
            if (latestProgram != null && latestProgram.getId() == recordedProgram.getId()) {
                if (seriesRecording != null) {
                    updateLatestRecordedProgram(seriesRecording);
                    onSeriesRecordingChanged(seriesRecording);
                }
            }
        }
        for (RecordedProgramAdapter adapter
                : getGenreAdapters(recordedProgram.getCanonicalGenres())) {
            adapter.remove(recordedProgram);
        }
    }

    private void handleRecordedProgramChanged(RecordedProgram recordedProgram) {
        mRecentAdapter.change(recordedProgram);
        String seriesId = recordedProgram.getSeriesId();
        SeriesRecording seriesRecording = null;
        if (seriesId != null) {
            seriesRecording = mDvrDataManager.getSeriesRecording(seriesId);
            RecordedProgram latestProgram = mSeriesId2LatestProgram.get(seriesId);
            if (latestProgram == null || RecordedProgram.START_TIME_THEN_ID_COMPARATOR
                    .compare(latestProgram, recordedProgram) <= 0) {
                mSeriesId2LatestProgram.put(seriesId, recordedProgram);
                if (seriesRecording != null) {
                    onSeriesRecordingChanged(seriesRecording);
                }
            } else if (latestProgram.getId() == recordedProgram.getId()) {
                if (seriesRecording != null) {
                    updateLatestRecordedProgram(seriesRecording);
                    onSeriesRecordingChanged(seriesRecording);
                }
            }
        }
        if (seriesRecording == null) {
            updateGenreAdapters(getGenreAdapters(
                    recordedProgram.getCanonicalGenres()), recordedProgram);
        } else {
            updateGenreAdapters(new ArrayList<>(), recordedProgram);
        }
    }

    private void handleSeriesRecordingsAdded(List<SeriesRecording> seriesRecordings) {
        for (SeriesRecording seriesRecording : seriesRecordings) {
            mSeriesAdapter.add(seriesRecording);
            if (mSeriesId2LatestProgram.get(seriesRecording.getSeriesId()) != null) {
                for (RecordedProgramAdapter adapter
                        : getGenreAdapters(seriesRecording.getCanonicalGenreIds())) {
                    adapter.add(seriesRecording);
                }
            }
        }
    }

    private void handleSeriesRecordingsRemoved(List<SeriesRecording> seriesRecordings) {
        for (SeriesRecording seriesRecording : seriesRecordings) {
            mSeriesAdapter.remove(seriesRecording);
            for (RecordedProgramAdapter adapter
                    : getGenreAdapters(seriesRecording.getCanonicalGenreIds())) {
                adapter.remove(seriesRecording);
            }
        }
    }

    private void handleSeriesRecordingsChanged(List<SeriesRecording> seriesRecordings) {
        for (SeriesRecording seriesRecording : seriesRecordings) {
            mSeriesAdapter.change(seriesRecording);
            if (mSeriesId2LatestProgram.get(seriesRecording.getSeriesId()) != null) {
                updateGenreAdapters(getGenreAdapters(
                        seriesRecording.getCanonicalGenreIds()), seriesRecording);
            } else {
                // Remove series recording from all genre rows if it has no recorded program
                updateGenreAdapters(new ArrayList<>(), seriesRecording);
            }
        }
    }

    private List<RecordedProgramAdapter> getGenreAdapters(String[] genres) {
        List<RecordedProgramAdapter> result = new ArrayList<>();
        if (genres == null || genres.length == 0) {
            result.add(mGenreAdapters[mGenreAdapters.length - 1]);
        } else {
            for (String genre : genres) {
                int genreId = GenreItems.getId(genre);
                if(genreId >= mGenreAdapters.length) {
                    Log.d(TAG, "Wrong Genre ID: " + genreId);
                } else {
                    result.add(mGenreAdapters[genreId]);
                }
            }
        }
        return result;
    }

    private List<RecordedProgramAdapter> getGenreAdapters(int[] genreIds) {
        List<RecordedProgramAdapter> result = new ArrayList<>();
        if (genreIds == null || genreIds.length == 0) {
            result.add(mGenreAdapters[mGenreAdapters.length - 1]);
        } else {
            for (int genreId : genreIds) {
                if(genreId >= mGenreAdapters.length) {
                    Log.d(TAG, "Wrong Genre ID: " + genreId);
                } else {
                    result.add(mGenreAdapters[genreId]);
                }
            }
        }
        return result;
    }

    private void updateGenreAdapters(List<RecordedProgramAdapter> adapters, Object r) {
        for (RecordedProgramAdapter adapter : mGenreAdapters) {
            if (adapters.contains(adapter)) {
                adapter.change(r);
            } else {
                adapter.remove(r);
            }
        }
    }

    private void postUpdateRows() {
        mHandler.removeCallbacks(mUpdateRowsRunnable);
        mHandler.post(mUpdateRowsRunnable);
    }

    private void updateRows() {
        int visibleRowsCount = 1;  // Schedule's Row will never be empty
        if (mRecentAdapter.isEmpty()) {
            mRowsAdapter.remove(mRecentRow);
        } else {
            if (mRowsAdapter.indexOf(mRecentRow) < 0) {
                mRowsAdapter.add(0, mRecentRow);
            }
            visibleRowsCount++;
        }
        if (mSeriesAdapter.isEmpty()) {
            mRowsAdapter.remove(mSeriesRow);
        } else {
            if (mRowsAdapter.indexOf(mSeriesRow) < 0) {
                mRowsAdapter.add(visibleRowsCount, mSeriesRow);
            }
            visibleRowsCount++;
        }
        for (int i = 0; i < mGenreAdapters.length; i++) {
            RecordedProgramAdapter adapter = mGenreAdapters[i];
            if (adapter != null) {
                if (adapter.isEmpty()) {
                    mRowsAdapter.remove(mGenreRows[i]);
                } else {
                    if (mGenreRows[i] == null || mRowsAdapter.indexOf(mGenreRows[i]) < 0) {
                        mGenreRows[i] = new ListRow(new HeaderItem(mGenreLabels.get(i)), adapter);
                        mRowsAdapter.add(visibleRowsCount, mGenreRows[i]);
                    }
                    visibleRowsCount++;
                }
            }
        }
    }

    private boolean needToShowScheduledRecording(ScheduledRecording recording) {
        int state = recording.getState();
        return state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS
                || state == ScheduledRecording.STATE_RECORDING_NOT_STARTED;
    }

    private void updateLatestRecordedProgram(SeriesRecording seriesRecording) {
        RecordedProgram latestProgram = null;
        for (RecordedProgram program :
                mDvrDataManager.getRecordedPrograms(seriesRecording.getId())) {
            if (latestProgram == null || RecordedProgram
                    .START_TIME_THEN_ID_COMPARATOR.compare(latestProgram, program) < 0) {
                latestProgram = program;
            }
        }
        mSeriesId2LatestProgram.put(seriesRecording.getSeriesId(), latestProgram);
    }

    private class ScheduleAdapter extends SortedArrayAdapter<Object> {
        ScheduleAdapter(int maxItemCount) {
            super(mPresenterSelector, SCHEDULE_COMPARATOR, maxItemCount);
        }

        @Override
        public long getId(Object item) {
            if (item instanceof ScheduledRecording) {
                return ((ScheduledRecording) item).getId();
            } else {
                return -1;
            }
        }
    }

    private class SeriesAdapter extends SortedArrayAdapter<SeriesRecording> {
        SeriesAdapter() {
            super(mPresenterSelector, new Comparator<SeriesRecording>() {
                @Override
                public int compare(SeriesRecording lhs, SeriesRecording rhs) {
                    if (lhs.isStopped() && !rhs.isStopped()) {
                        return 1;
                    } else if (!lhs.isStopped() && rhs.isStopped()) {
                        return -1;
                    }
                    return SeriesRecording.PRIORITY_COMPARATOR.compare(lhs, rhs);
                }
            });
        }

        @Override
        public long getId(SeriesRecording item) {
            return item.getId();
        }
    }

    private class RecordedProgramAdapter extends SortedArrayAdapter<Object> {
        RecordedProgramAdapter() {
            this(Integer.MAX_VALUE);
        }

        RecordedProgramAdapter(int maxItemCount) {
            super(mPresenterSelector, RECORDED_PROGRAM_COMPARATOR, maxItemCount);
        }

        @Override
        public long getId(Object item) {
            // We takes the inverse number for the ID of recorded programs to make the ID stable.
            if (item instanceof SeriesRecording) {
                return ((SeriesRecording) item).getId();
            } else if (item instanceof RecordedProgram) {
                return -((RecordedProgram) item).getId() - 1;
            } else {
                return -1;
            }
        }
    }
}