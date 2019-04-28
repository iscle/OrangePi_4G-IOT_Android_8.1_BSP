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

import android.app.Activity;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;

import com.android.tv.R;

public class DvrSeriesScheduledDialogActivity extends Activity {
    /**
     * Name of series recording id added to the Intent.
     */
    public static final String SERIES_RECORDING_ID = "series_recording_id";

    /**
     * Name of flag to check if the dialog should show view schedule option.
     */
    public static final String SHOW_VIEW_SCHEDULE_OPTION = "show_view_schedule_option";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.halfsized_dialog);
        if (savedInstanceState == null) {
            DvrSeriesScheduledFragment dvrSeriesScheduledFragment =
                    new DvrSeriesScheduledFragment();
            dvrSeriesScheduledFragment.setArguments(getIntent().getExtras());
            GuidedStepFragment.addAsRoot(this, dvrSeriesScheduledFragment,
                    R.id.halfsized_dialog_host);
        }
    }
}
