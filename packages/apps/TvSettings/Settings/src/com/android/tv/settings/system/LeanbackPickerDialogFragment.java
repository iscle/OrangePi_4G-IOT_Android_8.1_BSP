/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.system;

import android.app.AlarmManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v17.leanback.widget.picker.DatePicker;
import android.support.v17.leanback.widget.picker.TimePicker;
import android.support.v17.preference.LeanbackPreferenceDialogFragment;
import android.support.v7.preference.DialogPreference;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.settings.R;

import java.util.Calendar;

/**
 * A DialogFragment started for either setting date or setting time purposes. The type of
 * fragment launched is controlled by the type of {@link LeanbackPickerDialogPreference}
 * that's clicked. Launching of these two fragments is done inside
 * {@link com.android.tv.settings.BaseSettingsFragment#onPreferenceDisplayDialog}.
 */
public class LeanbackPickerDialogFragment extends LeanbackPreferenceDialogFragment {

    private static final String EXTRA_PICKER_TYPE = "LeanbackPickerDialogFragment.PickerType";
    private static final String TYPE_DATE = "date";
    private static final String TYPE_TIME = "time";
    private static final String SAVE_STATE_TITLE = "LeanbackPickerDialogFragment.title";

    private CharSequence mDialogTitle;
    private Calendar mCalendar;

    /**
     * Generated a new DialogFragment displaying a Leanback DatePicker widget.
     * @param key The preference key starting this DialogFragment.
     * @return The fragment to be started displaying a DatePicker widget for setting date.
     */
    public static LeanbackPickerDialogFragment newDatePickerInstance(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        args.putString(EXTRA_PICKER_TYPE, TYPE_DATE);

        final LeanbackPickerDialogFragment fragment = new LeanbackPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Generated a new DialogFragment displaying a Leanback TimePicker widget.
     * @param key The preference key starting this DialogFragment.
     * @return The fragment to be started displaying a TimePicker widget for setting time.
     */
    public static LeanbackPickerDialogFragment newTimePickerInstance(String key) {
        final Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        args.putString(EXTRA_PICKER_TYPE, TYPE_TIME);

        final LeanbackPickerDialogFragment fragment = new LeanbackPickerDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final DialogPreference preference = getPreference();
            mDialogTitle = preference.getDialogTitle();
        } else {
            mDialogTitle = savedInstanceState.getCharSequence(SAVE_STATE_TITLE);
        }
        mCalendar = Calendar.getInstance();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putCharSequence(SAVE_STATE_TITLE, mDialogTitle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final String pickerType = getArguments().getString(EXTRA_PICKER_TYPE);

        final View view = inflater.inflate(R.layout.picker_dialog_fragment, container, false);
        ViewGroup pickerContainer = (ViewGroup) view.findViewById(R.id.picker_container);
        if (pickerType.equals(TYPE_DATE)) {
            inflater.inflate(R.layout.date_picker_widget, pickerContainer, true);
            DatePicker datePicker = (DatePicker) pickerContainer.findViewById(R.id.date_picker);
            datePicker.setActivated(true);
            datePicker.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    // Setting the new system date
                    ((AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE)).setTime(
                            datePicker.getDate()
                    );
                    // Finish the fragment/activity when clicked.
                    if (!getFragmentManager().popBackStackImmediate()) {
                        getActivity().finish();
                    }
                }
            });

        } else {
            inflater.inflate(R.layout.time_picker_widget, pickerContainer, true);
            TimePicker timePicker = (TimePicker) pickerContainer.findViewById(R.id.time_picker);
            timePicker.setActivated(true);
            timePicker.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    // Setting the new system time
                    mCalendar.set(Calendar.HOUR_OF_DAY, timePicker.getHour());
                    mCalendar.set(Calendar.MINUTE, timePicker.getMinute());
                    mCalendar.set(Calendar.SECOND, 0);
                    mCalendar.set(Calendar.MILLISECOND, 0);
                    ((AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE)).setTime(
                            mCalendar.getTimeInMillis()
                    );
                    // Finish the fragment/activity when clicked.
                    if (!getFragmentManager().popBackStackImmediate()) {
                        getActivity().finish();
                    }
                }
            });
        }

        final CharSequence title = mDialogTitle;
        if (!TextUtils.isEmpty(title)) {
            final TextView titleView = (TextView) view.findViewById(R.id.decor_title);
            titleView.setText(title);
        }
        return view;
    }
}
