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

import android.app.Activity;
import android.os.Bundle;
import android.support.v17.leanback.app.DetailsFragment;

import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.view.View;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dialog.PinDialogFragment;

/**
 * Activity to show details view in DVR.
 */
public class DvrDetailsActivity extends Activity implements PinDialogFragment.OnPinCheckedListener {
    /**
     * Name of record id added to the Intent.
     */
    public static final String RECORDING_ID = "record_id";

    /**
     * Name of flag added to the Intent to determine if details view should hide "View schedule"
     * button.
     */
    public static final String HIDE_VIEW_SCHEDULE = "hide_view_schedule";

    /**
     * Name of details view's type added to the intent.
     */
    public static final String DETAILS_VIEW_TYPE = "details_view_type";

    /**
     * Name of shared element between activities.
     */
    public static final String SHARED_ELEMENT_NAME = "shared_element";

    /**
     * CURRENT_RECORDING_VIEW refers to Current Recordings in DVR.
     */
    public static final int CURRENT_RECORDING_VIEW = 1;

    /**
     * SCHEDULED_RECORDING_VIEW refers to Scheduled Recordings in DVR.
     */
    public static final int SCHEDULED_RECORDING_VIEW = 2;

    /**
     * RECORDED_PROGRAM_VIEW refers to Recorded programs in DVR.
     */
    public static final int RECORDED_PROGRAM_VIEW = 3;

    /**
     * SERIES_RECORDING_VIEW refers to series recording in DVR.
     */
    public static final int SERIES_RECORDING_VIEW = 4;

    private PinDialogFragment.OnPinCheckedListener mOnPinCheckedListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        TvApplication.setCurrentRunningProcess(this, true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_details);
        long recordId = getIntent().getLongExtra(RECORDING_ID, -1);
        int detailsViewType = getIntent().getIntExtra(DETAILS_VIEW_TYPE, -1);
        boolean hideViewSchedule = getIntent().getBooleanExtra(HIDE_VIEW_SCHEDULE, false);
        if (recordId != -1 && detailsViewType != -1 && savedInstanceState == null) {
            Bundle args = new Bundle();
            args.putLong(RECORDING_ID, recordId);
            DetailsFragment detailsFragment = null;
            if (detailsViewType == CURRENT_RECORDING_VIEW) {
                detailsFragment = new CurrentRecordingDetailsFragment();
            } else if (detailsViewType == SCHEDULED_RECORDING_VIEW) {
                args.putBoolean(HIDE_VIEW_SCHEDULE, hideViewSchedule);
                detailsFragment = new ScheduledRecordingDetailsFragment();
            } else if (detailsViewType == RECORDED_PROGRAM_VIEW) {
                detailsFragment = new RecordedProgramDetailsFragment();
            } else if (detailsViewType == SERIES_RECORDING_VIEW) {
                detailsFragment = new SeriesRecordingDetailsFragment();
            }
            detailsFragment.setArguments(args);
            getFragmentManager().beginTransaction()
                    .replace(R.id.dvr_details_view_frame, detailsFragment).commit();
        }

        // This is a workaround for the focus on O device
        addTransitionListener();
    }

    @Override
    public void onPinChecked(boolean checked, int type, String rating) {
        if (mOnPinCheckedListener != null) {
            mOnPinCheckedListener.onPinChecked(checked, type, rating);
        }
    }

    void setOnPinCheckListener(PinDialogFragment.OnPinCheckedListener listener) {
        mOnPinCheckedListener = listener;
    }

    private void addTransitionListener() {
        getWindow()
                .getSharedElementEnterTransition()
                .addListener(
                        new TransitionListener() {
                            @Override
                            public void onTransitionStart(Transition transition) {
                                // Do nothing
                            }

                            @Override
                            public void onTransitionEnd(Transition transition) {
                                View actions = findViewById(R.id.details_overview_actions);
                                if (actions != null) {
                                    actions.requestFocus();
                                }
                            }

                            @Override
                            public void onTransitionCancel(Transition transition) {
                                // Do nothing

                            }

                            @Override
                            public void onTransitionPause(Transition transition) {
                                // Do nothing
                            }

                            @Override
                            public void onTransitionResume(Transition transition) {
                                // Do nothing
                            }
                        });
    }
}
