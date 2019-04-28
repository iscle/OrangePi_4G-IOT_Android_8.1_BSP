/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.tv.quicksettings;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

public class QuickSettings extends Activity {

    private static final String TAG = "QuickSettings";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            final Fragment f = new QuickSettingsFragment();
            getFragmentManager().beginTransaction().add(android.R.id.content, f).commit();
            getFragmentManager().executePendingTransactions();
        }
    }

    public static class QuickSettingsFragment extends LeanbackSettingsFragment {

        @Override
        public void onPreferenceStartInitialScreen() {
            final Fragment f = new QuickSettingsPreferenceFragment();
            startPreferenceFragment(f);
        }

        @Override
        public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
            return false;
        }

        @Override
        public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
            final Fragment f = new SubSettingsFragment();
            final Bundle b = new Bundle(1);
            b.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
            f.setArguments(b);
            startPreferenceFragment(f);
            return true;
        }
    }

    public static class SubSettingsFragment extends LeanbackPreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.quick_settings, rootKey);
        }
    }

}
