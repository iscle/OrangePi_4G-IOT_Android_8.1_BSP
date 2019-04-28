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

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvInputManager;
import android.os.Bundle;

import com.android.tv.R;
import com.android.tv.TvApplication;

/**
 * {@link android.app.Activity} for DVR UI.
 */
public class DvrBrowseActivity extends Activity {
    private DvrBrowseFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dvr_main);
        mFragment = (DvrBrowseFragment) getFragmentManager().findFragmentById(R.id.dvr_frame);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (TvInputManager.ACTION_VIEW_RECORDING_SCHEDULES.equals(intent.getAction())) {
            mFragment.showScheduledRow();
        }
    }
}