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

package com.android.tv.quicksettings;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import java.util.Arrays;
import java.util.List;

public class QuickSettingsPreferenceFragment extends LeanbackPreferenceFragment implements
        SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener {

    private ListPreference mPresetPref;
    private Preference mBacklightPref;
    private Preference mContrastPref;
    private Preference mBrightnessPref;
    private Preference mSharpnessPref;
    private Preference mColorPref;
    private Preference mTintPref;

    private PresetSettingsListener mPresetSettingsListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPresetSettingsListener = new PresetSettingsListener(getActivity());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String key) {
        setPreferencesFromResource(R.xml.quick_settings, key);

        mPresetPref = (ListPreference) findPreference("preset");
        mPresetPref.setOnPreferenceClickListener(this);

        mBacklightPref = findPreference("backlight");
        mBacklightPref.setOnPreferenceClickListener(this);

        mContrastPref = findPreference("contrast");
        mContrastPref.setOnPreferenceClickListener(this);

        mBrightnessPref = findPreference("brightness");
        mBrightnessPref.setOnPreferenceClickListener(this);

        mSharpnessPref = findPreference("sharpness");
        mSharpnessPref.setOnPreferenceClickListener(this);

        mColorPref = findPreference("color");
        mColorPref.setOnPreferenceClickListener(this);

        mTintPref = findPreference("tint");
        mTintPref.setOnPreferenceClickListener(this);

        final Preference resetPreference = findPreference("reset");
        resetPreference.setOnPreferenceClickListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(mPresetSettingsListener);

        updateDescriptions(sharedPreferences);
    }


    @Override
    public void onPause() {
        super.onPause();

        final SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(mPresetSettingsListener);
    }

    private void updateDescriptions(SharedPreferences sharedPreferences) {
        final Resources res = getResources();
        mPresetPref.setSummary(mPresetPref.getEntry());
        mBacklightPref.setSummary(String.format("%d", sharedPreferences.getInt("backlight",
                res.getInteger(R.integer.standard_setting_backlight))));
        mContrastPref.setSummary(String.format("%d", sharedPreferences.getInt("contrast",
                res.getInteger(R.integer.standard_setting_contrast))));
        mBrightnessPref.setSummary(String.format("%d", sharedPreferences.getInt("brightness",
                res.getInteger(R.integer.standard_setting_brightness))));
        mSharpnessPref.setSummary(String.format("%d", sharedPreferences.getInt("sharpness",
                res.getInteger(R.integer.standard_setting_sharpness))));
        mColorPref.setSummary(String.format("%d", sharedPreferences.getInt("color",
                res.getInteger(R.integer.standard_setting_color))));
        mTintPref.setSummary(String.format("%d", sharedPreferences.getInt("tint",
                res.getInteger(R.integer.standard_setting_tint))));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case "preset":
            case "backlight":
            case "contrast":
            case "brightness":
            case "sharpness":
            case "color":
            case "tint":
                updateDescriptions(sharedPreferences);
        }
    }

    private void launchSettingsDialog(int initialPos) {
        final Intent intent = new Intent(getActivity(), SettingsDialog.class);
        intent.putExtra(SettingsDialog.EXTRA_START_POS, initialPos);
        startActivity(intent);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        final String key = preference.getKey();
        final List<String> keys =
                Arrays.asList(getResources().getStringArray(R.array.setting_keys));
        final int pos = keys.indexOf(key);
        switch (key) {
            case "preset":
            case "backlight":
            case "contrast":
            case "brightness":
            case "sharpness":
            case "color":
            case "tint":
                launchSettingsDialog(pos);
                return true;
            case "reset":
                new AlertDialog.Builder(getActivity()).setPositiveButton(
                        android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // User clicked OK button
                                getPreferenceManager().getSharedPreferences().edit()
                                        .putString("preset", "standard")
                                        .commit();
                            }
                        }).setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // User cancelled the dialog - do nothing
                            }
                        }).setTitle(R.string.reset_dialog_message).create().show();
                return true;
        }
        return false;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // Do nothing
    }
}
