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

package com.android.tv.settings.device.display.daydream;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;

import com.android.settingslib.dream.DreamBackend;
import com.android.tv.settings.R;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static android.provider.Settings.Secure.SLEEP_TIMEOUT;
import static android.provider.Settings.System.SCREEN_OFF_TIMEOUT;

public class DaydreamFragment extends LeanbackPreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "DaydreamFragment";

    private static final String KEY_ACTIVE_DREAM = "activeDream";
    private static final String KEY_DREAM_TIME = "dreamTime";
    private static final String KEY_SLEEP_TIME = "sleepTime";
    private static final String KEY_DREAM_NOW = "dreamNow";

    private static final String DREAM_COMPONENT_NONE = "NONE";
    private static final String PACKAGE_SCHEME = "package";

    private static final int DEFAULT_DREAM_TIME_MS = (int) (30 * DateUtils.MINUTE_IN_MILLIS);
    private static final int DEFAULT_SLEEP_TIME_MS = (int) (3 * DateUtils.HOUR_IN_MILLIS);

    private final PackageReceiver mPackageReceiver = new PackageReceiver();

    private DreamBackend mBackend;
    private final Map<String, DreamBackend.DreamInfo> mDreamInfos = new ArrayMap<>();

    public static DaydreamFragment newInstance() {
        return new DaydreamFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mBackend = new DreamBackend(getActivity());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshFromBackend();

        // listen for package changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme(PACKAGE_SCHEME);
        getActivity().registerReceiver(mPackageReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(mPackageReceiver);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.daydream, null);

        final ListPreference activeDreamPref = (ListPreference) findPreference(KEY_ACTIVE_DREAM);
        refreshActiveDreamPref(activeDreamPref);
        activeDreamPref.setOnPreferenceChangeListener(this);

        final ListPreference dreamTimePref = (ListPreference) findPreference(KEY_DREAM_TIME);
        dreamTimePref.setValue(Integer.toString(getDreamTime()));
        dreamTimePref.setOnPreferenceChangeListener(this);

        final ListPreference sleepTimePref = (ListPreference) findPreference(KEY_SLEEP_TIME);
        sleepTimePref.setValue(Integer.toString(getSleepTime()));
        sleepTimePref.setOnPreferenceChangeListener(this);

        final Preference dreamNowPref = findPreference(KEY_DREAM_NOW);
        dreamNowPref.setEnabled(mBackend.isEnabled());
    }

    private void refreshActiveDreamPref(ListPreference activeDreamPref) {
        final List<DreamBackend.DreamInfo> infos = mBackend.getDreamInfos();
        final CharSequence[] dreamEntries = new CharSequence[infos.size() + 1];
        final CharSequence[] dreamEntryValues = new CharSequence[infos.size() + 1];
        refreshDreamInfoMap(infos, dreamEntries, dreamEntryValues);
        activeDreamPref.setEntries(dreamEntries);
        activeDreamPref.setEntryValues(dreamEntryValues);
        final ComponentName currentDreamComponent = mBackend.getActiveDream();
        activeDreamPref.setValue(mBackend.isEnabled() && currentDreamComponent != null
                ? currentDreamComponent.toShortString() : DREAM_COMPONENT_NONE);
    }

    private void refreshDreamInfoMap(List<DreamBackend.DreamInfo> infos,
            CharSequence[] listEntries, CharSequence[] listEntryValues) {
        mDreamInfos.clear();
        listEntries[0] = getString(R.string.device_daydreams_none);
        listEntryValues[0] = DREAM_COMPONENT_NONE;
        int index = 1;
        for (final DreamBackend.DreamInfo info : infos) {
            final String componentNameString = info.componentName.toShortString();
            mDreamInfos.put(componentNameString, info);
            listEntries[index] = info.caption;
            listEntryValues[index] = componentNameString;
            index++;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case KEY_ACTIVE_DREAM:
                setActiveDream((String) newValue);
                break;
            case KEY_DREAM_TIME:
                setDreamTime(Integer.parseInt((String) newValue));
                break;
            case KEY_SLEEP_TIME:
                setSleepTime(Integer.parseInt((String) newValue));
                break;
        }
        return true;
    }

    private void setActiveDream(String componentNameString) {
        final DreamBackend.DreamInfo dreamInfo = mDreamInfos.get(componentNameString);
        if (dreamInfo != null) {
            if (dreamInfo.settingsComponentName != null) {
                startActivity(new Intent().setComponent(dreamInfo.settingsComponentName));
            }
            if (!mBackend.isEnabled()) {
                mBackend.setEnabled(true);
            }
            if (!Objects.equals(mBackend.getActiveDream(), dreamInfo.componentName)) {
                mBackend.setActiveDream(dreamInfo.componentName);
            }
        } else {
            if (mBackend.isEnabled()) {
                mBackend.setActiveDream(null);
                mBackend.setEnabled(false);
            }
        }
    }

    private int getDreamTime() {
        return Settings.System.getInt(getActivity().getContentResolver(), SCREEN_OFF_TIMEOUT,
                DEFAULT_DREAM_TIME_MS);
    }

    private void setDreamTime(int ms) {
        Settings.System.putInt(getActivity().getContentResolver(), SCREEN_OFF_TIMEOUT, ms);

    }

    private int getSleepTime() {
        return Settings.Secure.getInt(getActivity().getContentResolver(), SLEEP_TIMEOUT,
                DEFAULT_SLEEP_TIME_MS);
    }

    private void setSleepTime(int ms) {
        Settings.Secure.putInt(getActivity().getContentResolver(), SLEEP_TIMEOUT, ms);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        switch (preference.getKey()) {
            case KEY_DREAM_NOW:
                mBackend.startDreaming();
                return true;
            default:
                return super.onPreferenceTreeClick(preference);
        }
    }

    private void refreshFromBackend() {
        if (getActivity() == null) {
            Log.d(TAG, "No activity, not refreshing");
            return;
        }

        final ListPreference activeDreamPref = (ListPreference) findPreference(KEY_ACTIVE_DREAM);
        if (activeDreamPref != null) {
            refreshActiveDreamPref(activeDreamPref);
        }

        final ListPreference dreamTimePref = (ListPreference) findPreference(KEY_DREAM_TIME);
        if (dreamTimePref != null) {
            dreamTimePref.setValue(Integer.toString(getDreamTime()));
        }

        final ListPreference sleepTimePref = (ListPreference) findPreference(KEY_SLEEP_TIME);
        if (sleepTimePref != null) {
            sleepTimePref.setValue(Integer.toString(getSleepTime()));
        }

        final Preference dreamNowPref = findPreference(KEY_DREAM_NOW);
        if (dreamNowPref != null) {
            dreamNowPref.setEnabled(mBackend.isEnabled());
        }
    }

    private class PackageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshFromBackend();
        }
    }

}
