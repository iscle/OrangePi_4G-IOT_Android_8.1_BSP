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
 * limitations under the License
 */

package com.android.tv.settings.system;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;

import com.android.internal.app.LocalePicker;
import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;

import java.util.List;

public class CaptionFragment extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String KEY_CAPTIONS_DISPLAY = "captions_display";
    private static final String KEY_CAPTIONS_LANGUAGE = "captions_language";
    private static final String KEY_CAPTIONS_TEXT_SIZE = "captions_text_size";
    private static final String KEY_CAPTIONS_STYLE_GROUP = "captions_style";
    private static final String KEY_CAPTIONS_STYLE_0 = "captions_style_0";
    private static final String KEY_CAPTIONS_STYLE_1 = "captions_style_1";
    private static final String KEY_CAPTIONS_STYLE_2 = "captions_style_2";
    private static final String KEY_CAPTIONS_STYLE_3 = "captions_style_3";
    private static final String KEY_CAPTIONS_STYLE_CUSTOM = "captions_style_custom";

    private TwoStatePreference mCaptionsDisplayPref;
    private ListPreference mCaptionsLanguagePref;
    private ListPreference mCaptionsTextSizePref;
    private PreferenceGroup mCaptionsStyleGroup;
    private RadioPreference mCaptionsStyle0Pref;
    private RadioPreference mCaptionsStyle1Pref;
    private RadioPreference mCaptionsStyle2Pref;
    private RadioPreference mCaptionsStyle3Pref;
    private RadioPreference mCaptionsStyleCustomPref;

    public static CaptionFragment newInstance() {
        return new CaptionFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.caption, null);

        mCaptionsDisplayPref = (TwoStatePreference) findPreference(KEY_CAPTIONS_DISPLAY);

        final List<LocalePicker.LocaleInfo> localeInfoList =
                LocalePicker.getAllAssetLocales(getContext(), false);
        final String[] langNames = new String[localeInfoList.size() + 1];
        final String[] langLocales = new String[localeInfoList.size() + 1];
        langNames[0] = getString(R.string.captions_language_default);
        langLocales[0] = "";
        int i = 1;
        for (final LocalePicker.LocaleInfo info : localeInfoList) {
            langNames[i] = info.toString();
            langLocales[i] = info.getLocale().toString();
            i++;
        }
        mCaptionsLanguagePref = (ListPreference) findPreference(KEY_CAPTIONS_LANGUAGE);
        mCaptionsLanguagePref.setEntries(langNames);
        mCaptionsLanguagePref.setEntryValues(langLocales);
        mCaptionsLanguagePref.setOnPreferenceChangeListener(this);

        mCaptionsTextSizePref = (ListPreference) findPreference(KEY_CAPTIONS_TEXT_SIZE);
        mCaptionsTextSizePref.setOnPreferenceChangeListener(this);

        mCaptionsStyleGroup = (PreferenceGroup) findPreference(KEY_CAPTIONS_STYLE_GROUP);

        mCaptionsStyle0Pref = (RadioPreference) findPreference(KEY_CAPTIONS_STYLE_0);
        mCaptionsStyle1Pref = (RadioPreference) findPreference(KEY_CAPTIONS_STYLE_1);
        mCaptionsStyle2Pref = (RadioPreference) findPreference(KEY_CAPTIONS_STYLE_2);
        mCaptionsStyle3Pref = (RadioPreference) findPreference(KEY_CAPTIONS_STYLE_3);
        mCaptionsStyleCustomPref = (RadioPreference) findPreference(KEY_CAPTIONS_STYLE_CUSTOM);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (key) {
            case KEY_CAPTIONS_DISPLAY:
                setCaptionsEnabled(((TwoStatePreference) preference).isChecked());
                return true;
            case KEY_CAPTIONS_STYLE_0:
                setCaptionsStyle(0);
                mCaptionsStyle0Pref.setChecked(true);
                mCaptionsStyle0Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                return true;
            case KEY_CAPTIONS_STYLE_1:
                setCaptionsStyle(1);
                mCaptionsStyle1Pref.setChecked(true);
                mCaptionsStyle1Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                return true;
            case KEY_CAPTIONS_STYLE_2:
                setCaptionsStyle(2);
                mCaptionsStyle2Pref.setChecked(true);
                mCaptionsStyle2Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                return true;
            case KEY_CAPTIONS_STYLE_3:
                setCaptionsStyle(3);
                mCaptionsStyle3Pref.setChecked(true);
                mCaptionsStyle3Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                return true;
            case KEY_CAPTIONS_STYLE_CUSTOM:
                setCaptionsStyle(-1);
                mCaptionsStyleCustomPref.setChecked(true);
                mCaptionsStyleCustomPref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                // Call to super to show custom configuration screen
                return super.onPreferenceTreeClick(preference);
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            throw new IllegalStateException("Unknown preference change");
        }
        switch (key) {
            case KEY_CAPTIONS_LANGUAGE:
                setCaptionsLocale((String) newValue);
                break;
            case KEY_CAPTIONS_TEXT_SIZE:
                setCaptionsTextSize((String) newValue);
                break;
            default:
                throw new IllegalStateException("Preference change with unknown key " + key);
        }
        return true;
    }

    private void refresh() {
        mCaptionsDisplayPref.setChecked(getCaptionsEnabled());
        mCaptionsLanguagePref.setValue(getCaptionsLocale());
        mCaptionsTextSizePref.setValue(getCaptionsTextSize());
        switch (getCaptionsStyle()) {
            default:
            case 0:
                mCaptionsStyle0Pref.setChecked(true);
                mCaptionsStyle0Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                break;
            case 1:
                mCaptionsStyle1Pref.setChecked(true);
                mCaptionsStyle1Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                break;
            case 2:
                mCaptionsStyle2Pref.setChecked(true);
                mCaptionsStyle2Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                break;
            case 3:
                mCaptionsStyle3Pref.setChecked(true);
                mCaptionsStyle3Pref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                break;
            case -1:
                mCaptionsStyleCustomPref.setChecked(true);
                mCaptionsStyleCustomPref.clearOtherRadioPreferences(mCaptionsStyleGroup);
                break;
        }
    }

    private boolean getCaptionsEnabled() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
            Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, 0) != 0;
    }

    private void setCaptionsEnabled(boolean enabled) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_ENABLED, enabled ? 1 : 0);
    }

    private int getCaptionsStyle() {
        return Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, 0);
    }

    private void setCaptionsStyle(int style) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, style);
        LocalBroadcastManager.getInstance(getContext())
                .sendBroadcast(new Intent(CaptionSettingsFragment.ACTION_REFRESH_CAPTIONS_PREVIEW));
    }

    private String getCaptionsLocale() {
        final String captionLocale = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_LOCALE);
        return captionLocale == null ? "" : captionLocale;
    }

    private void setCaptionsLocale(String locale) {
        Settings.Secure.putString(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_LOCALE, locale);
    }

    private String getCaptionsTextSize() {
        final float textSize = Settings.Secure.getFloat(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, 1);

        if (0 <= textSize && textSize < .375) {
            return "0.25";
        } else if (textSize < .75) {
            return "0.5";
        } else if (textSize < 1.25) {
            return "1.0";
        } else if (textSize < 1.75) {
            return "1.5";
        } else if (textSize < 2.5) {
            return "2.0";
        }

        return "1.0";
    }

    private void setCaptionsTextSize(String textSize) {
        Settings.Secure.putFloat(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, Float.parseFloat(textSize));
    }
}
