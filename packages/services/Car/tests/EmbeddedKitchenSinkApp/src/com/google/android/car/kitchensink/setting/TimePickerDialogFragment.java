/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.google.android.car.kitchensink.setting;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.TimePicker;

public class TimePickerDialogFragment extends DialogFragment implements
        TimePickerDialog.OnTimeSetListener {
    private static final String HOUR_KEY = "hour";
    private static final String MINUTE_KEY = "minute";

    private TimePickerDialog.OnTimeSetListener mListener;

    public static TimePickerDialogFragment newInstance(int hourOfDay, int minute) {
        TimePickerDialogFragment frag = new TimePickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt(HOUR_KEY, hourOfDay);
        args.putInt(MINUTE_KEY, minute);
        frag.setArguments(args);

        return frag;
    }

    public void setTimeSetListener(TimePickerDialog.OnTimeSetListener listener) {
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        int hour = args.getInt(HOUR_KEY);
        int minute = args.getInt(MINUTE_KEY);
        return new TimePickerDialog(getContext(), this, hour, minute, true);
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        if (mListener != null) {
            mListener.onTimeSet(view, hourOfDay, minute);
        }
    }
}