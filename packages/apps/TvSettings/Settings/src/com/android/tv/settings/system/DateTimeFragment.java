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

package com.android.tv.settings.system;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v14.preference.SwitchPreference;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.text.format.DateFormat;

import com.android.settingslib.datetime.ZoneGetter;
import com.android.tv.settings.R;

import java.util.Calendar;
import java.util.Date;

public class DateTimeFragment extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String KEY_AUTO_DATE_TIME = "auto_date_time";
    private static final String KEY_SET_DATE = "set_date";
    private static final String KEY_SET_TIME = "set_time";
    private static final String KEY_SET_TIME_ZONE = "set_time_zone";
    private static final String KEY_USE_24_HOUR = "use_24_hour";

    private static final String AUTO_DATE_TIME_NTP = "network";
    private static final String AUTO_DATE_TIME_TS = "transport_stream";
    private static final String AUTO_DATE_TIME_OFF = "off";

    private static final String HOURS_12 = "12";
    private static final String HOURS_24 = "24";

    //    private TvInputManager mTvInputManager;
    private final Calendar mDummyDate = Calendar.getInstance();

    private Preference mDatePref;
    private Preference mTimePref;
    private Preference mTimeZone;
    private Preference mTime24Pref;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Activity activity = getActivity();
            if (activity != null) {
                updateTimeAndDateDisplay(activity);
            }
        }
    };

    public static DateTimeFragment newInstance() {
        return new DateTimeFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
//        mTvInputManager =
//                (TvInputManager) getActivity().getSystemService(Context.TV_INPUT_SERVICE);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.date_time, null);

        final boolean isRestricted = SecurityFragment.isRestrictedProfileInEffect(getContext());

        mDatePref = findPreference(KEY_SET_DATE);
        mDatePref.setVisible(!isRestricted);
        mTimePref = findPreference(KEY_SET_TIME);
        mTimePref.setVisible(!isRestricted);

        final boolean tsTimeCapable = SystemProperties.getBoolean("ro.config.ts.date.time", false);
        final ListPreference autoDateTimePref =
                (ListPreference) findPreference(KEY_AUTO_DATE_TIME);
        autoDateTimePref.setValue(getAutoDateTimeState());
        autoDateTimePref.setOnPreferenceChangeListener(this);
        if (tsTimeCapable) {
            autoDateTimePref.setEntries(R.array.auto_date_time_ts_entries);
            autoDateTimePref.setEntryValues(R.array.auto_date_time_ts_entry_values);
        }
        autoDateTimePref.setVisible(!isRestricted);
        mTimeZone = findPreference(KEY_SET_TIME_ZONE);
        mTimeZone.setVisible(!isRestricted);
        mTime24Pref = findPreference(KEY_USE_24_HOUR);
        mTime24Pref.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        ((SwitchPreference)mTime24Pref).setChecked(is24Hour());

        // Register for time ticks and other reasons for time change
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getActivity().registerReceiver(mIntentReceiver, filter, null, null);

        updateTimeAndDateDisplay(getActivity());
        updateTimeDateEnable();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mIntentReceiver);
    }

    private void updateTimeAndDateDisplay(Context context) {
        final Calendar now = Calendar.getInstance();
        mDummyDate.setTimeZone(now.getTimeZone());
        // We use December 31st because it's unambiguous when demonstrating the date format.
        // We use 13:00 so we can demonstrate the 12/24 hour options.
        mDummyDate.set(now.get(Calendar.YEAR), 11, 31, 13, 0, 0);
        Date dummyDate = mDummyDate.getTime();
        mDatePref.setSummary(DateFormat.getLongDateFormat(context).format(now.getTime()));
        mTimePref.setSummary(DateFormat.getTimeFormat(getActivity()).format(now.getTime()));
        mTimeZone.setSummary(ZoneGetter.getTimeZoneOffsetAndName(getActivity(),
                now.getTimeZone(), now.getTime()));
        mTime24Pref.setSummary(DateFormat.getTimeFormat(getActivity()).format(dummyDate));
    }

    private void updateTimeDateEnable() {
        final boolean enable = TextUtils.equals(getAutoDateTimeState(), AUTO_DATE_TIME_OFF);
        mDatePref.setEnabled(enable);
        mTimePref.setEnabled(enable);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (TextUtils.equals(preference.getKey(), KEY_AUTO_DATE_TIME)) {
            String value = (String) newValue;
            if (TextUtils.equals(value, AUTO_DATE_TIME_NTP)) {
                setAutoDateTime(true);
            } else if (TextUtils.equals(value, AUTO_DATE_TIME_TS)) {
                throw new IllegalStateException("TS date is not yet implemented");
//                mTvInputManager.syncTimefromBroadcast(true);
//                setAutoDateTime(false);
            } else if (TextUtils.equals(value, AUTO_DATE_TIME_OFF)) {
                setAutoDateTime(false);
            } else {
                throw new IllegalArgumentException("Unknown auto time value " + value);
            }
            updateTimeDateEnable();
        } else if (TextUtils.equals(preference.getKey(), KEY_USE_24_HOUR)) {
            final boolean use24Hour = (Boolean) newValue;
            set24Hour(use24Hour);
            timeUpdated(use24Hour);
        }
        return true;
    }

    /*  Get & Set values from the system settings  */

    private boolean is24Hour() {
        return DateFormat.is24HourFormat(getActivity());
    }

    private void timeUpdated(boolean use24Hour) {
        Intent timeChanged = new Intent(Intent.ACTION_TIME_CHANGED);
        int timeFormatPreference =
                use24Hour ? Intent.EXTRA_TIME_PREF_VALUE_USE_24_HOUR
                        : Intent.EXTRA_TIME_PREF_VALUE_USE_12_HOUR;
        timeChanged.putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, timeFormatPreference);
        getContext().sendBroadcast(timeChanged);
    }

    private void set24Hour(boolean use24Hour) {
        Settings.System.putString(getContext().getContentResolver(),
                Settings.System.TIME_12_24,
                use24Hour ? HOURS_24 : HOURS_12);
    }

    private void setAutoDateTime(boolean on) {
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.AUTO_TIME, on ? 1 : 0);
    }

    private String getAutoDateTimeState() {
//        if(mTvInputManager.isUseBroadcastDateTime()) {
//            return AUTO_DATE_TIME_TS;
//        }

        int value = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.AUTO_TIME, 0);
        if(value > 0) {
            return AUTO_DATE_TIME_NTP;
        }

        return AUTO_DATE_TIME_OFF;
    }

}
