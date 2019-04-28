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

import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.ui.DvrUiHelper;

/**
 * {@link RecordingDetailsFragment} for scheduled recording in DVR.
 */
public class ScheduledRecordingDetailsFragment extends RecordingDetailsFragment {
    private static final int ACTION_VIEW_SCHEDULE = 1;
    private static final int ACTION_CANCEL = 2;

    private DvrManager mDvrManager;
    private Action mScheduleAction;
    private boolean mHideViewSchedule;

    @Override
    public void onCreate(Bundle savedInstance) {
        mDvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
        mHideViewSchedule = getArguments().getBoolean(DvrDetailsActivity.HIDE_VIEW_SCHEDULE);
        super.onCreate(savedInstance);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mScheduleAction != null) {
            mScheduleAction.setIcon(getResources().getDrawable(getScheduleIconId()));
        }
    }

    @Override
    protected SparseArrayObjectAdapter onCreateActionsAdapter() {
        SparseArrayObjectAdapter adapter =
                new SparseArrayObjectAdapter(new ActionPresenterSelector());
        Resources res = getResources();
        if (!mHideViewSchedule) {
            mScheduleAction = new Action(ACTION_VIEW_SCHEDULE,
                    res.getString(R.string.dvr_detail_view_schedule), null,
                    res.getDrawable(getScheduleIconId()));
            adapter.set(ACTION_VIEW_SCHEDULE, mScheduleAction);
        }
        adapter.set(ACTION_CANCEL, new Action(ACTION_CANCEL,
                res.getString(R.string.dvr_detail_cancel_recording), null,
                res.getDrawable(R.drawable.ic_dvr_cancel_32dp)));
        return adapter;
    }

    @Override
    protected OnActionClickedListener onCreateOnActionClickedListener() {
        return new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                long actionId = action.getId();
                if (actionId == ACTION_VIEW_SCHEDULE) {
                    DvrUiHelper.startSchedulesActivity(getContext(), getRecording());
                } else if (actionId == ACTION_CANCEL) {
                    mDvrManager.removeScheduledRecording(getRecording());
                    getActivity().finish();
                }
            }
        };
    }

    private int getScheduleIconId() {
        if (mDvrManager.isConflicting(getRecording())) {
            return R.drawable.ic_warning_white_32dp;
        } else {
            return R.drawable.ic_schedule_32dp;
        }
    }
}
