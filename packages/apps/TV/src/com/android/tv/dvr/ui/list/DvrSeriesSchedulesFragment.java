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

import android.annotation.TargetApi;
import android.content.Context;
import android.database.ContentObserver;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.SeriesRecordingListener;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.provider.EpisodicProgramLoadTask;
import com.android.tv.dvr.ui.BigArguments;

import java.util.Collections;
import java.util.List;

/**
 * A fragment to show the list of series schedule recordings.
 */
@TargetApi(Build.VERSION_CODES.N)
public class DvrSeriesSchedulesFragment extends BaseDvrSchedulesFragment {
    /**
     * The key for series recording whose scheduled recording list will be displayed.
     * Type: {@link SeriesRecording}
     */
    public static final String SERIES_SCHEDULES_KEY_SERIES_RECORDING =
            "series_schedules_key_series_recording";
    /**
     * The key for programs which belong to the series recording whose scheduled recording list
     * will be displayed.
     * Type: List<{@link Program}>
     */
    public static final String SERIES_SCHEDULES_KEY_SERIES_PROGRAMS =
            "series_schedules_key_series_programs";

    private ChannelDataManager mChannelDataManager;
    private DvrDataManager mDvrDataManager;
    private SeriesRecording mSeriesRecording;
    private List<Program> mPrograms;
    private EpisodicProgramLoadTask mProgramLoadTask;

    private final SeriesRecordingListener mSeriesRecordingListener =
            new SeriesRecordingListener() {
                @Override
                public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) { }

                @Override
                public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
                    for (SeriesRecording r : seriesRecordings) {
                        if (r.getId() == mSeriesRecording.getId()) {
                            getActivity().finish();
                            return;
                        }
                    }
                }

                @Override
                public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
                    for (SeriesRecording r : seriesRecordings) {
                        if (r.getId() == mSeriesRecording.getId()
                                && getRowsAdapter() instanceof SeriesScheduleRowAdapter) {
                            ((SeriesScheduleRowAdapter) getRowsAdapter())
                                    .onSeriesRecordingUpdated(r);
                            mSeriesRecording = r;
                            updateEmptyMessage();
                            return;
                        }
                    }
                }
            };

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver mContentObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            executeProgramLoadingTask();
        }
    };

    private final ChannelDataManager.Listener mChannelListener = new ChannelDataManager.Listener() {
        @Override
        public void onLoadFinished() { }

        @Override
        public void onChannelListUpdated() {
            executeProgramLoadingTask();
        }

        @Override
        public void onChannelBrowsableChanged() { }
    };

    public DvrSeriesSchedulesFragment() {
        setEnterTransition(new Fade(Fade.IN));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) {
            mSeriesRecording = args.getParcelable(SERIES_SCHEDULES_KEY_SERIES_RECORDING);
            mPrograms = (List<Program>) BigArguments.getArgument(
                    SERIES_SCHEDULES_KEY_SERIES_PROGRAMS);
            BigArguments.reset();
        }
        if (args == null || mPrograms == null) {
            getActivity().finish();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
        mChannelDataManager = singletons.getChannelDataManager();
        mChannelDataManager.addListener(mChannelListener);
        mDvrDataManager = singletons.getDvrDataManager();
        mDvrDataManager.addSeriesRecordingListener(mSeriesRecordingListener);
        getContext().getContentResolver().registerContentObserver(Programs.CONTENT_URI, true,
                mContentObserver);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        onProgramsUpdated();
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    private void onProgramsUpdated() {
        ((SeriesScheduleRowAdapter) getRowsAdapter()).setPrograms(mPrograms);
        updateEmptyMessage();
    }

    private void updateEmptyMessage() {
        if (mPrograms == null || mPrograms.isEmpty()) {
            if (mSeriesRecording.getState() == SeriesRecording.STATE_SERIES_STOPPED) {
                showEmptyMessage(R.string.dvr_series_schedules_stopped_empty_state);
            } else {
                showEmptyMessage(R.string.dvr_series_schedules_empty_state);
            }
        } else {
            hideEmptyMessage();
        }
    }

    @Override
    public void onDestroy() {
        if (mProgramLoadTask != null) {
            mProgramLoadTask.cancel(true);
            mProgramLoadTask = null;
        }
        getContext().getContentResolver().unregisterContentObserver(mContentObserver);
        mHandler.removeCallbacksAndMessages(null);
        mChannelDataManager.removeListener(mChannelListener);
        mDvrDataManager.removeSeriesRecordingListener(mSeriesRecordingListener);
        super.onDestroy();
    }

    @Override
    public SchedulesHeaderRowPresenter onCreateHeaderRowPresenter() {
        return new SchedulesHeaderRowPresenter.SeriesRecordingHeaderRowPresenter(getContext());
    }

    @Override
    public ScheduleRowPresenter onCreateRowPresenter() {
        return new SeriesScheduleRowPresenter(getContext());
    }

    @Override
    public ScheduleRowAdapter onCreateRowsAdapter(ClassPresenterSelector presenterSelector) {
        return new SeriesScheduleRowAdapter(getContext(), presenterSelector, mSeriesRecording);
    }

    @Override
    protected int getFirstItemPosition() {
        if (mSeriesRecording != null
                && mSeriesRecording.getState() == SeriesRecording.STATE_SERIES_STOPPED) {
            return 0;
        }
        return super.getFirstItemPosition();
    }

    private void executeProgramLoadingTask() {
        if (mProgramLoadTask != null) {
            mProgramLoadTask.cancel(true);
        }
        mProgramLoadTask = new EpisodicProgramLoadTask(getContext(), mSeriesRecording) {
            @Override
            protected void onPostExecute(List<Program> programs) {
                mPrograms = programs == null ? Collections.EMPTY_LIST : programs;
                onProgramsUpdated();
            }
        };
        mProgramLoadTask.setLoadCurrentProgram(true)
                .setLoadDisallowedProgram(true)
                .setLoadScheduledEpisode(true)
                .setIgnoreChannelOption(true)
                .execute();
    }
}
