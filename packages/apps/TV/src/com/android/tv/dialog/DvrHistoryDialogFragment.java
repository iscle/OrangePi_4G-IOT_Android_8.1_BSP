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

package com.android.tv.dialog;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.ScheduledRecording.RecordingState;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the DVR history.
 */
@TargetApi(VERSION_CODES.N)
public class DvrHistoryDialogFragment extends SafeDismissDialogFragment {
    public static final String DIALOG_TAG = DvrHistoryDialogFragment.class.getSimpleName();

    private static final String TRACKER_LABEL = "DVR history";
    private final List<ScheduledRecording> mSchedules = new ArrayList<>();

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ApplicationSingletons singletons = TvApplication.getSingletons(getContext());
        DvrDataManager dataManager = singletons.getDvrDataManager();
        ChannelDataManager channelDataManager = singletons.getChannelDataManager();
        for (ScheduledRecording schedule : dataManager.getAllScheduledRecordings()) {
            if (!schedule.isInProgress() && !schedule.isNotStarted()) {
                mSchedules.add(schedule);
            }
        }
        mSchedules.sort(ScheduledRecording.START_TIME_COMPARATOR.reversed());
        LayoutInflater inflater = LayoutInflater.from(getContext());
        ArrayAdapter adapter = new ArrayAdapter<ScheduledRecording>(getContext(),
                R.layout.list_item_dvr_history, ScheduledRecording.toArray(mSchedules)) {
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = inflater.inflate(R.layout.list_item_dvr_history, parent, false);
                ScheduledRecording schedule = mSchedules.get(position);
                setText(view, R.id.state, getStateString(schedule.getState()));
                setText(view, R.id.schedule_time, getRecordingTimeText(schedule));
                setText(view, R.id.program_title, DvrUiHelper.getStyledTitleWithEpisodeNumber(
                        getContext(), schedule, 0));
                setText(view, R.id.channel_name, getChannelNameText(schedule));
                return view;
            }

            private void setText(View view, int id, CharSequence text) {
                ((TextView) view.findViewById(id)).setText(text);
            }

            private void setText(View view, int id, int text) {
                ((TextView) view.findViewById(id)).setText(text);
            }

            @SuppressLint("SwitchIntDef")
            private int getStateString(@RecordingState int state) {
                switch (state) {
                    case ScheduledRecording.STATE_RECORDING_CLIPPED:
                        return R.string.dvr_history_dialog_state_clip;
                    case ScheduledRecording.STATE_RECORDING_FAILED:
                        return R.string.dvr_history_dialog_state_fail;
                    case ScheduledRecording.STATE_RECORDING_FINISHED:
                        return R.string.dvr_history_dialog_state_success;
                    default:
                        break;
                }
                return 0;
            }

            private String getChannelNameText(ScheduledRecording schedule) {
                Channel channel = channelDataManager.getChannel(schedule.getChannelId());
                return channel == null ? null :
                        TextUtils.isEmpty(channel.getDisplayName()) ? channel.getDisplayNumber() :
                                channel.getDisplayName().trim() + " " + channel.getDisplayNumber();
            }

            private String getRecordingTimeText(ScheduledRecording schedule) {
                return Utils.getDurationString(getContext(), schedule.getStartTimeMs(),
                        schedule.getEndTimeMs(), true, true, true, 0);
            }
        };
        ListView listView = new ListView(getActivity());
        listView.setAdapter(adapter);
        return new AlertDialog.Builder(getActivity()).setTitle(R.string.dvr_history_dialog_title)
                .setView(listView).create();
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }
}
