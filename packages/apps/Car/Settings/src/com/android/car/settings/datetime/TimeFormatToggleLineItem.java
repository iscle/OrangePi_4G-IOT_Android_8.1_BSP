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
 * limitations under the License
 */

package com.android.car.settings.datetime;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.format.DateFormat;

import com.android.car.settings.R;
import com.android.car.settings.common.ToggleLineItem;

import java.util.Calendar;

/**
 * A LineItem that displays and sets system time format.
 */
class TimeFormatToggleLineItem extends ToggleLineItem {
    private static final String HOURS_12 = "12";
    private static final String HOURS_24 = "24";
    private static final int DEMO_YEAR = 2017;
    private static final int DEMO_MONTH = 11;
    private static final int DEMO_DAY_OF_MONTH = 31;
    private static final int DEMO_HOUR_OF_DAY = 13;
    private static final int DEMO_MINUTE = 0;
    private static final int DEMO_SECOND = 0;

    private final Calendar mTimeFormatDemoDate = Calendar.getInstance();

    private Context mContext;

    public TimeFormatToggleLineItem(Context context) {
        super(context.getString(R.string.date_time_24hour));
        mContext = context;
        mTimeFormatDemoDate.set(
                DEMO_YEAR,
                DEMO_MONTH,
                DEMO_DAY_OF_MONTH,
                DEMO_HOUR_OF_DAY,
                DEMO_MINUTE,
                DEMO_SECOND);
    }

    @Override
    public void onClick(boolean is24Hour) {
        Settings.System.putString(mContext.getContentResolver(),
                Settings.System.TIME_12_24,
                is24Hour ? HOURS_12 : HOURS_24);
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        int timeFormatPreference =
                is24Hour ? Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR
                        : Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR;
        timeChanged.putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, timeFormatPreference);
        mContext.sendBroadcast(timeChanged);
    }

    @Override
    public boolean isChecked() {
        return DateFormat.is24HourFormat(mContext);
    }

    @Override
    public CharSequence getDesc() {
        return DateFormat.getTimeFormat(mContext)
                .format(mTimeFormatDemoDate.getTime());
    }

    @Override
    public boolean isExpandable() {
        return false;
    }
}
