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

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;

/**
 * Activity to show details view in DVR.
 */
public class DvrSeriesSettingsActivity extends Activity {
    /**
     * Name of series id added to the Intent.
     * Type: Long
     */
    public static final String SERIES_RECORDING_ID = "series_recording_id";
    /**
     * Name of the boolean flag to decide if the series recording with empty schedule and recording
     * will be removed.
     * Type: boolean
     */
    public static final String REMOVE_EMPTY_SERIES_RECORDING = "remove_empty_series_recording";
    /**
     * Name of the boolean flag to decide if the setting fragment should be translucent.
     * Type: boolean
     */
    public static final String IS_WINDOW_TRANSLUCENT = "windows_translucent";
    /**
     * Name of the program list. The list contains the programs which belong to the series.
     * Type: List<{@link com.android.tv.data.Program}>
     */
    public static final String PROGRAM_LIST = "program_list";

    /**
     * Name of the boolean flag to check if the confirm dialog should show view schedule option.
     * Type: boolean
     */
    public static final String SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG =
            "show_view_schedule_option_in_dialog";

    /**
     * Name of the current program added to series. The current program will be recorded only when
     * the series recording is initialized from media controller. But for other case, the current
     * program won't be recorded.
     */
    public static final String CURRENT_PROGRAM = "current_program";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_series_settings);
        long seriesRecordingId = getIntent().getLongExtra(SERIES_RECORDING_ID, -1);
        SoftPreconditions.checkArgument(seriesRecordingId != -1);

        if (savedInstanceState == null) {
            DvrSeriesSettingsFragment settingFragment = new DvrSeriesSettingsFragment();
            settingFragment.setArguments(getIntent().getExtras());
            GuidedStepFragment.addAsRoot(this, settingFragment, R.id.dvr_settings_view_frame);
        }
    }

    @Override
    public void onAttachedToWindow() {
        if (!getIntent().getExtras().getBoolean(IS_WINDOW_TRANSLUCENT, true)) {
            getWindow().setBackgroundDrawable(
                    new ColorDrawable(getColor(R.color.common_tv_background)));
        }
    }
}