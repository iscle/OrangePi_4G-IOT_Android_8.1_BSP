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
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;
import android.text.TextUtils;

import com.android.settingslib.datetime.ZoneGetter;
import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Keep
public class TimeZoneFragment extends LeanbackPreferenceFragment {

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Activity activity = getActivity();
            if (activity != null) {
                updateZones();
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context themedContext = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(
                themedContext);
        screen.setTitle(R.string.system_set_time_zone);
        setPreferenceScreen(screen);

        final List<Map<String, Object>> zoneList = ZoneGetter.getZonesList(getActivity());
        final List<ZonePreference> zonePrefs = new ArrayList<>(zoneList.size());
        for (final Map<String, Object> zone : zoneList) {
            zonePrefs.add(new ZonePreference(themedContext, zone));
        }
        Collections.sort(zonePrefs, new ZonePrefComparator());
        for (final Preference zonePref : zonePrefs) {
            screen.addPreference(zonePref);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Register for zone changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        getActivity().registerReceiver(mIntentReceiver, filter, null, null);
        updateZones();
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(mIntentReceiver);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof ZonePreference) {
            // Update the system timezone value
            final AlarmManager alarm = (AlarmManager)
                    getActivity().getSystemService(Context.ALARM_SERVICE);
            alarm.setTimeZone(preference.getKey());
            if (!getFragmentManager().popBackStackImmediate()) {
                getActivity().finish();
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void updateZones() {
        final String id = TimeZone.getDefault().getID();
        final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference pref = screen.getPreference(i);
            if (!(pref instanceof ZonePreference)) {
                continue;
            }
            final ZonePreference zonePref = (ZonePreference) pref;
            zonePref.setChecked(TextUtils.equals(zonePref.getKey(), id));
        }
    }

    private static class ZonePreference extends CheckBoxPreference {
        Integer offset;

        public ZonePreference(Context context, Map<? extends String, ?> zone) {
            super(context);
            setWidgetLayoutResource(R.layout.radio_preference_widget);
            setKey((String) zone.get(ZoneGetter.KEY_ID));
            setPersistent(false);
            setTitle((String) zone.get(ZoneGetter.KEY_DISPLAYNAME));
            setSummary((String) zone.get(ZoneGetter.KEY_GMT));
            offset = (Integer) zone.get(ZoneGetter.KEY_OFFSET);
        }
    }

    private static class ZonePrefComparator implements Comparator<ZonePreference> {
        public int compare(ZonePreference zone1, ZonePreference zone2) {
            final int firstResult = zone1.offset.compareTo(zone2.offset);
            if (firstResult != 0) {
                return firstResult;
            }
            return zone1.getTitle().toString().compareTo(zone2.getTitle().toString());
        }
    }

}
