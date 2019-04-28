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

import android.app.FragmentManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.SeriesRecording;

import java.util.ArrayList;
import java.util.List;

/** Fragment for DVR series recording settings. */
public class DvrPrioritySettingsFragment extends TrackedGuidedStepFragment {
    /**
     * Name of series recording id starting the fragment.
     * Type: Long
     */
    public static final String COME_FROM_SERIES_RECORDING_ID = "series_recording_id";

    private static final int ONE_TIME_RECORDING_ID = 0;
    // button action's IDs are negative.
    private static final long ACTION_ID_SAVE = -100L;

    private final List<SeriesRecording> mSeriesRecordings = new ArrayList<>();

    private SeriesRecording mSelectedRecording;
    private SeriesRecording mComeFromSeriesRecording;
    private float mSelectedActionElevation;
    private int mActionColor;
    private int mSelectedActionColor;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSeriesRecordings.clear();
        mSeriesRecordings.add(new SeriesRecording.Builder()
                .setTitle(getString(R.string.dvr_priority_action_one_time_recording))
                .setPriority(Long.MAX_VALUE)
                .setId(ONE_TIME_RECORDING_ID)
                .build());
        DvrDataManager dvrDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        long comeFromSeriesRecordingId =
                getArguments().getLong(COME_FROM_SERIES_RECORDING_ID, -1);
        for (SeriesRecording series : dvrDataManager.getSeriesRecordings()) {
            if (series.getState() == SeriesRecording.STATE_SERIES_NORMAL
                    || series.getId() == comeFromSeriesRecordingId) {
                mSeriesRecordings.add(series);
            }
        }
        mSeriesRecordings.sort(SeriesRecording.PRIORITY_COMPARATOR);
        mComeFromSeriesRecording = dvrDataManager.getSeriesRecording(comeFromSeriesRecordingId);
        mSelectedActionElevation = getResources().getDimension(R.dimen.card_elevation_normal);
        mActionColor = getResources().getColor(R.color.dvr_guided_step_action_text_color, null);
        mSelectedActionColor =
                getResources().getColor(R.color.dvr_guided_step_action_text_color_selected, null);
    }

    @Override
    public void onResume() {
        super.onResume();
        setSelectedActionPosition(mComeFromSeriesRecording == null ? 1
                : mSeriesRecordings.indexOf(mComeFromSeriesRecording));
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String breadcrumb = mComeFromSeriesRecording == null ? null
                : mComeFromSeriesRecording.getTitle();
        return new Guidance(getString(R.string.dvr_priority_title),
                getString(R.string.dvr_priority_description), breadcrumb, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        int position = 0;
        for (SeriesRecording seriesRecording : mSeriesRecordings) {
            actions.add(new GuidedAction.Builder(getActivity())
                    .id(position++)
                    .title(seriesRecording.getTitle())
                    .build());
        }
    }

    @Override
    public void onCreateButtonActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(new GuidedAction.Builder(getActivity())
                .id(ACTION_ID_SAVE)
                .title(getString(R.string.dvr_priority_button_action_save))
                .build());
        actions.add(new GuidedAction.Builder(getActivity())
                .clickAction(GuidedAction.ACTION_ID_CANCEL)
                .build());
    }

    @Override
    public void onTrackedGuidedActionClicked(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_ID_SAVE) {
            DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
            int size = mSeriesRecordings.size();
            for (int i = 1; i < size; ++i) {
                long priority = DvrScheduleManager.suggestSeriesPriority(size - i);
                SeriesRecording seriesRecording = mSeriesRecordings.get(i);
                if (seriesRecording.getPriority() != priority) {
                    dvrManager.updateSeriesRecording(SeriesRecording.buildFrom(seriesRecording)
                            .setPriority(priority).build());
                }
            }
            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.popBackStack();
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.popBackStack();
        } else if (mSelectedRecording == null) {
            mSelectedRecording = mSeriesRecordings.get((int) actionId);
            for (int i = 0; i < mSeriesRecordings.size(); ++i) {
                updateItem(i);
            }
        } else {
            mSelectedRecording = null;
            for (int i = 0; i < mSeriesRecordings.size(); ++i) {
                updateItem(i);
            }
        }
    }

    @Override
    public String getTrackerPrefix() {
        return "DvrPrioritySettingsFragment";
    }

    @Override
    public String getTrackerLabelForGuidedAction(GuidedAction action) {
        long actionId = action.getId();
        if (actionId == ACTION_ID_SAVE) {
            return "save";
        } else {
            return super.getTrackerLabelForGuidedAction(action);
        }
    }

    @Override
    public void onGuidedActionFocused(GuidedAction action) {
        super.onGuidedActionFocused(action);
        if (mSelectedRecording == null) {
            return;
        }
        if (action.getId() < 0) {
            mSelectedRecording = null;
            for (int i = 0; i < mSeriesRecordings.size(); ++i) {
                updateItem(i);
            }
            return;
        }
        int position = (int) action.getId();
        int previousPosition = mSeriesRecordings.indexOf(mSelectedRecording);
        mSeriesRecordings.remove(mSelectedRecording);
        mSeriesRecordings.add(position, mSelectedRecording);
        updateItem(previousPosition);
        updateItem(position);
        notifyActionChanged(previousPosition);
        notifyActionChanged(position);
    }

    @Override
    public GuidedActionsStylist onCreateButtonActionsStylist() {
        return new DvrGuidedActionsStylist(true);
    }

    @Override
    public GuidedActionsStylist onCreateActionsStylist() {
        return new DvrGuidedActionsStylist(false) {
            @Override
            public void onBindViewHolder(ViewHolder vh, GuidedAction action) {
                super.onBindViewHolder(vh, action);
                updateItem(vh.itemView, (int) action.getId());
            }

            @Override
            public int onProvideItemLayoutId() {
                return R.layout.priority_settings_action_item;
            }
        };
    }

    private void updateItem(int position) {
        View itemView = getActionItemView(position);
        if (itemView == null) {
            return;
        }
        updateItem(itemView, position);
    }

    private void updateItem(View itemView, int position) {
        GuidedAction action = getActions().get(position);
        action.setTitle(mSeriesRecordings.get(position).getTitle());
        boolean selected = mSelectedRecording != null
                && mSeriesRecordings.indexOf(mSelectedRecording) == position;
        TextView titleView = (TextView) itemView.findViewById(R.id.guidedactions_item_title);
        ImageView imageView = (ImageView) itemView.findViewById(R.id.guidedactions_item_tail_image);
        if (position == 0) {
            // one-time recording
            itemView.setBackgroundResource(R.drawable.setup_selector_background);
            imageView.setVisibility(View.GONE);
            itemView.setFocusable(false);
            itemView.setElevation(0);
            // strings.xml <i> tag doesn't work.
            titleView.setTypeface(titleView.getTypeface(), Typeface.ITALIC);
        } else if (mSelectedRecording == null) {
            titleView.setTextColor(mActionColor);
            itemView.setBackgroundResource(R.drawable.setup_selector_background);
            imageView.setImageResource(R.drawable.ic_draggable_white);
            imageView.setVisibility(View.VISIBLE);
            itemView.setFocusable(true);
            itemView.setElevation(0);
            titleView.setTypeface(titleView.getTypeface(), Typeface.NORMAL);
        } else if (selected) {
            titleView.setTextColor(mSelectedActionColor);
            itemView.setBackgroundResource(R.drawable.priority_settings_action_item_selected);
            imageView.setImageResource(R.drawable.ic_dragging_grey);
            imageView.setVisibility(View.VISIBLE);
            itemView.setFocusable(true);
            itemView.setElevation(mSelectedActionElevation);
            titleView.setTypeface(titleView.getTypeface(), Typeface.NORMAL);
        } else {
            titleView.setTextColor(mActionColor);
            itemView.setBackgroundResource(R.drawable.setup_selector_background);
            imageView.setVisibility(View.INVISIBLE);
            itemView.setFocusable(true);
            itemView.setElevation(0);
            titleView.setTypeface(titleView.getTypeface(), Typeface.NORMAL);
        }
    }
}