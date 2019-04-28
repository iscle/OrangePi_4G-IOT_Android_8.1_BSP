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

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.IntDef;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Program;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.provider.EpisodicProgramLoadTask;
import com.android.tv.dvr.recorder.SeriesRecordingScheduler;
import com.android.tv.dvr.ui.BigArguments;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * Activity to show the list of recording schedules.
 */
public class DvrSchedulesActivity extends Activity {
    /**
     * The key for the type of the schedules which will be listed in the list. The type of the value
     * should be {@link ScheduleListType}.
     */
    public static final String KEY_SCHEDULES_TYPE = "schedules_type";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({TYPE_FULL_SCHEDULE, TYPE_SERIES_SCHEDULE})
    public @interface ScheduleListType {}
    /**
     * A type which means the activity will display the full scheduled recordings.
     */
    public static final int TYPE_FULL_SCHEDULE = 0;
    /**
     * A type which means the activity will display a scheduled recording list of a series
     * recording.
     */
    public static final int TYPE_SERIES_SCHEDULE = 1;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        // Pass null to prevent automatically re-creating fragments
        super.onCreate(null);
        setContentView(R.layout.activity_dvr_schedules);
        int scheduleType = getIntent().getIntExtra(KEY_SCHEDULES_TYPE, TYPE_FULL_SCHEDULE);
        if (scheduleType == TYPE_FULL_SCHEDULE) {
            DvrSchedulesFragment schedulesFragment = new DvrSchedulesFragment();
            schedulesFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().add(
                    R.id.fragment_container, schedulesFragment).commit();
        } else if (scheduleType == TYPE_SERIES_SCHEDULE) {
            if (BigArguments.getArgument(DvrSeriesSchedulesFragment
                    .SERIES_SCHEDULES_KEY_SERIES_PROGRAMS) != null) {
                // The programs will be passed to the DvrSeriesSchedulesFragment, so don't need
                // to reset the BigArguments.
                showDvrSeriesSchedulesFragment(getIntent().getExtras());
            } else {
                final ProgressDialog dialog = ProgressDialog.show(this, null, getString(
                        R.string.dvr_series_progress_message_reading_programs));
                SeriesRecording seriesRecording = getIntent().getExtras()
                        .getParcelable(DvrSeriesSchedulesFragment
                                .SERIES_SCHEDULES_KEY_SERIES_RECORDING);
                // To get programs faster, hold the update of the series schedules.
                SeriesRecordingScheduler.getInstance(this).pauseUpdate();
                new EpisodicProgramLoadTask(this, Collections.singletonList(seriesRecording)) {
                    @Override
                    protected void onPostExecute(List<Program> programs) {
                        SeriesRecordingScheduler.getInstance(DvrSchedulesActivity.this)
                                .resumeUpdate();
                        dialog.dismiss();
                        Bundle args = getIntent().getExtras();
                        BigArguments.reset();
                        BigArguments.setArgument(
                                DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_PROGRAMS,
                                programs == null ? Collections.EMPTY_LIST : programs);
                        showDvrSeriesSchedulesFragment(args);
                    }
                }.setLoadCurrentProgram(true)
                        .setLoadDisallowedProgram(true)
                        .setLoadScheduledEpisode(true)
                        .setIgnoreChannelOption(true)
                        .execute();
            }
        } else {
            finish();
        }
    }

    private void showDvrSeriesSchedulesFragment(Bundle args) {
        DvrSeriesSchedulesFragment schedulesFragment = new DvrSeriesSchedulesFragment();
        schedulesFragment.setArguments(args);
        getFragmentManager().beginTransaction().add(
                R.id.fragment_container, schedulesFragment).commit();
    }
}
