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
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;

import java.util.ArrayList;
import java.util.List;

/**
 * A fragment which asks the user to stop series recording.
 */
public class DvrStopSeriesRecordingFragment extends DvrGuidedStepFragment {
    /**
     * Key for the series recording to be stopped.
     */
    public static final String KEY_SERIES_RECORDING = "key_series_recoridng";

    private static final int ACTION_STOP_SERIES_RECORDING = 1;

    private SeriesRecording mSeriesRecording;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mSeriesRecording = getArguments().getParcelable(KEY_SERIES_RECORDING);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @NonNull
    @Override
    public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.dvr_series_schedules_stop_dialog_title);
        String description = getString(R.string.dvr_series_schedules_stop_dialog_description);
        Drawable icon = getContext().getDrawable(R.drawable.ic_dvr_delete);
        return new GuidanceStylist.Guidance(title, description, null, icon);
    }

    @Override
    public void onCreateActions(@NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
        Activity activity = getActivity();
        actions.add(new GuidedAction.Builder(activity)
                .id(ACTION_STOP_SERIES_RECORDING)
                .title(R.string.dvr_series_schedules_stop_dialog_action_stop)
                .build());
        actions.add(new GuidedAction.Builder(activity)
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        if (action.getId() == ACTION_STOP_SERIES_RECORDING) {
            ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
            DvrManager dvrManager = singletons.getDvrManager();
            DvrDataManager dataManager = singletons.getDvrDataManager();
            List<ScheduledRecording> toDelete = new ArrayList<>();
            for (ScheduledRecording r : dataManager.getAvailableScheduledRecordings()) {
                if (r.getSeriesRecordingId() == mSeriesRecording.getId()) {
                    if (r.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                        toDelete.add(r);
                    } else {
                        dvrManager.stopRecording(r);
                    }
                }
            }
            if (!toDelete.isEmpty()) {
                dvrManager.forceRemoveScheduledRecording(ScheduledRecording.toArray(toDelete));
            }
            dvrManager.updateSeriesRecording(SeriesRecording.buildFrom(mSeriesRecording)
                    .setState(SeriesRecording.STATE_SERIES_STOPPED).build());
        }
        dismissDialog();
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrStopSeriesRecordingFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        if (action.getId() == ACTION_STOP_SERIES_RECORDING) {
            return "stop";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }
}
