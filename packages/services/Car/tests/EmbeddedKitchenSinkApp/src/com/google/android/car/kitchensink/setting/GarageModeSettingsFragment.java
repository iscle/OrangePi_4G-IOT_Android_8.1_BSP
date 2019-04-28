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

import android.app.TimePickerDialog;
import android.car.CarApiUtil;
import android.car.settings.GarageModeSettingsObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.TimePicker;

import com.google.android.car.kitchensink.R;

import java.sql.Time;

import static android.car.settings.CarSettings.Global.KEY_GARAGE_MODE_MAINTENANCE_WINDOW;
import static android.car.settings.CarSettings.Global.KEY_GARAGE_MODE_ENABLED;
import static android.car.settings.CarSettings.Global.KEY_GARAGE_MODE_WAKE_UP_TIME;
import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_ENABLED_URI;
import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_WAKE_UP_TIME_URI;
import static android.car.settings.GarageModeSettingsObserver.GARAGE_MODE_MAINTENANCE_WINDOW_URI;

public class GarageModeSettingsFragment extends PreferenceFragment implements
        TimePickerDialog.OnTimeSetListener, Preference.OnPreferenceChangeListener {

    private static final String TAG = "GarageModeSettings";
    private Preference mTimePreference;
    private Preference mGarageSwitchPreference;
    private Preference mGarageLimitPreference;

    private int mGarageTimeHour;
    private int mGarageTimeMin;

    private GarageModeSettingsObserver mContentObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.garage_mode_prefs);
        mTimePreference = findPreference(KEY_GARAGE_MODE_WAKE_UP_TIME);
        mTimePreference.setOnPreferenceChangeListener(this);
        mGarageSwitchPreference = findPreference(KEY_GARAGE_MODE_ENABLED);
        mGarageSwitchPreference.setOnPreferenceChangeListener(this);
        mGarageLimitPreference = findPreference(KEY_GARAGE_MODE_MAINTENANCE_WINDOW);
        mGarageLimitPreference.setOnPreferenceChangeListener(this);
        refreshUI(KEY_GARAGE_MODE_ENABLED, KEY_GARAGE_MODE_WAKE_UP_TIME, KEY_GARAGE_MODE_MAINTENANCE_WINDOW);
        mContentObserver = new GarageModeSettingsObserver(getContext(), new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                onSettingsChangedInternal(uri);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        mContentObserver.register();
    }

    @Override
    public void onPause() {
        super.onPause();
        mContentObserver.unregister();
    }

    private void refreshUI(String... keys) {
        for (String key : keys) {
            try {
                switch (key) {
                    case KEY_GARAGE_MODE_ENABLED:
                        mGarageSwitchPreference.setDefaultValue(
                                Settings.Global.getInt(getContext().getContentResolver(), key)
                                        == 1);
                        break;
                    case KEY_GARAGE_MODE_WAKE_UP_TIME:
                        int time[] = CarApiUtil.decodeGarageTimeSetting(
                                Settings.Global.getString(getContext().getContentResolver(),
                                        KEY_GARAGE_MODE_WAKE_UP_TIME));
                        mTimePreference.setSummary(
                                DateFormat.getTimeFormat(getContext()).format(
                                        new Time(time[0], time[1], 0)));
                        mGarageTimeHour = time[0];
                        mGarageTimeMin = time[1];
                        break;
                    case KEY_GARAGE_MODE_MAINTENANCE_WINDOW:
                        int limitMinutes = Settings.Global.getInt(getContext().getContentResolver(),
                                key) / 60 / 1000;
                        mGarageLimitPreference.setSummary(
                                getString(R.string.garage_time_limit_summary, limitMinutes));
                        break;
                }
            } catch (Settings.SettingNotFoundException e) {
                Log.e(TAG, "Settings not found " + key);
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mTimePreference) {
            TimePickerDialogFragment dialog =
                    TimePickerDialogFragment.newInstance(mGarageTimeHour, mGarageTimeMin);
            dialog.setTimeSetListener(this);
            dialog.show(getFragmentManager(), "time");
            return true;
        }
        return super.onPreferenceTreeClick(screen, preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mGarageSwitchPreference) {
            updateGarageSwitch((boolean) newValue);
            refreshUI(KEY_GARAGE_MODE_ENABLED);
            return true;
        } else if (preference == mGarageLimitPreference) {
            updateGarageTimeLimit(Integer.valueOf((String) newValue));
            refreshUI(KEY_GARAGE_MODE_MAINTENANCE_WINDOW);
            return true;
        }

        return false;
    }

    private void updateGarageSwitch(boolean newValue) {
        Settings.Global.putInt(getContext().getContentResolver(),
                KEY_GARAGE_MODE_ENABLED, newValue ? 1 : 0);
    }

    private void updateGarageTimeLimit(int newValue) {
        Settings.Global.putInt(getContext().getContentResolver(),
                KEY_GARAGE_MODE_MAINTENANCE_WINDOW, newValue * 60 * 1000);
    }

    private void updateGarageTime(String time) {
        Settings.Global.putString(getContext().getContentResolver(),
                KEY_GARAGE_MODE_WAKE_UP_TIME, time);
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int hour, int minute) {
        updateGarageTime(CarApiUtil.encodeGarageTimeSetting(hour, minute));
        refreshUI(KEY_GARAGE_MODE_WAKE_UP_TIME);
    }

    private void onSettingsChangedInternal(Uri uri) {
        Log.d(TAG, "Content Observer onChange: " + uri);
        if (uri.equals(GARAGE_MODE_ENABLED_URI)) {
            refreshUI(KEY_GARAGE_MODE_ENABLED);
        } else if (uri.equals(GARAGE_MODE_WAKE_UP_TIME_URI)) {
            refreshUI(KEY_GARAGE_MODE_WAKE_UP_TIME);
        } else if (uri.equals(GARAGE_MODE_MAINTENANCE_WINDOW_URI)) {
            refreshUI(KEY_GARAGE_MODE_MAINTENANCE_WINDOW);
        }
    }
}