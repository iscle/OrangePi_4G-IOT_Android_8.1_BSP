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
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;

import com.android.tv.R;
import com.android.tv.TvApplication;

/**
 * Activity to show details view in DVR.
 */
public class DvrSeriesDeletionActivity extends Activity {
    /**
     * Name of series id added to the Intent.
     */
    public static final String SERIES_RECORDING_ID = "series_recording_id";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_series_settings);
        // Check savedInstanceState to prevent that activity is being showed with animation.
        if (savedInstanceState == null) {
            DvrSeriesDeletionFragment deletionFragment = new DvrSeriesDeletionFragment();
            deletionFragment.setArguments(getIntent().getExtras());
            GuidedStepFragment.addAsRoot(this, deletionFragment, R.id.dvr_settings_view_frame);
        }
    }
}
