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

package com.android.tv.settings.system.development;

import android.content.res.TypedArray;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Keep;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.TwoStatePreference;
import android.text.TextUtils;

import com.android.tv.settings.R;

@Keep
public class CaptionCustomFragment extends LeanbackPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String KEY_FONT_FAMILY = "font_family";
    private static final String KEY_TEXT_COLOR = "text_color";
    private static final String KEY_TEXT_OPACITY = "text_opacity";
    private static final String KEY_EDGE_TYPE = "edge_type";
    private static final String KEY_EDGE_COLOR = "edge_color";
    private static final String KEY_BACKGROUND_SHOW = "background_show";
    private static final String KEY_BACKGROUND_COLOR = "background_color";
    private static final String KEY_BACKGROUND_OPACITY = "background_opacity";
    private static final String KEY_WINDOW_SHOW = "window_show";
    private static final String KEY_WINDOW_COLOR = "window_color";
    private static final String KEY_WINDOW_OPACITY = "window_opacity";

    private ListPreference mFontFamilyPref;
    private ListPreference mTextColorPref;
    private ListPreference mTextOpacityPref;
    private ListPreference mEdgeTypePref;
    private ListPreference mEdgeColorPref;
    private TwoStatePreference mBackgroundShowPref;
    private ListPreference mBackgroundColorPref;
    private ListPreference mBackgroundOpacityPref;
    private TwoStatePreference mWindowShowPref;
    private ListPreference mWindowColorPref;
    private ListPreference mWindowOpacityPref;

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.caption_custom, null);

        final TypedArray ta =
                getResources().obtainTypedArray(R.array.captioning_color_selector_ids);
        final int colorLen = ta.length();
        final String[] namedColors =
                getResources().getStringArray(R.array.captioning_color_selector_titles);
        final String[] colorNames = new String[colorLen];
        final String[] colorValues = new String[colorLen];
        for (int i = 0; i < colorLen; i++) {
            final int color = ta.getColor(i, 0);
            colorValues[i] = Integer.toHexString(color & 0x00ffffff);
            if (i < namedColors.length) {
                colorNames[i] = namedColors[i];
            } else {
                colorNames[i] = String.format("#%06X", color & 0x00ffffff);
            }
        }
        ta.recycle();

        mFontFamilyPref = (ListPreference) findPreference(KEY_FONT_FAMILY);
        mFontFamilyPref.setOnPreferenceChangeListener(this);

        mTextColorPref = (ListPreference) findPreference(KEY_TEXT_COLOR);
        mTextColorPref.setEntries(colorNames);
        mTextColorPref.setEntryValues(colorValues);
        mTextColorPref.setOnPreferenceChangeListener(this);

        mTextOpacityPref = (ListPreference) findPreference(KEY_TEXT_OPACITY);
        mTextOpacityPref.setOnPreferenceChangeListener(this);
        mEdgeTypePref = (ListPreference) findPreference(KEY_EDGE_TYPE);
        mEdgeTypePref.setOnPreferenceChangeListener(this);

        mEdgeColorPref = (ListPreference) findPreference(KEY_EDGE_COLOR);
        mEdgeColorPref.setEntries(colorNames);
        mEdgeColorPref.setEntryValues(colorValues);
        mEdgeColorPref.setOnPreferenceChangeListener(this);

        mBackgroundShowPref = (TwoStatePreference) findPreference(KEY_BACKGROUND_SHOW);

        mBackgroundColorPref = (ListPreference) findPreference(KEY_BACKGROUND_COLOR);
        mBackgroundColorPref.setEntries(colorNames);
        mBackgroundColorPref.setEntryValues(colorValues);
        mBackgroundColorPref.setOnPreferenceChangeListener(this);

        mBackgroundOpacityPref = (ListPreference) findPreference(KEY_BACKGROUND_OPACITY);
        mBackgroundOpacityPref.setOnPreferenceChangeListener(this);

        mWindowShowPref = (TwoStatePreference) findPreference(KEY_WINDOW_SHOW);

        mWindowColorPref = (ListPreference) findPreference(KEY_WINDOW_COLOR);
        mWindowColorPref.setEntries(colorNames);
        mWindowColorPref.setEntryValues(colorValues);
        mWindowColorPref.setOnPreferenceChangeListener(this);

        mWindowOpacityPref = (ListPreference) findPreference(KEY_WINDOW_OPACITY);
        mWindowOpacityPref.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (key) {
            case KEY_BACKGROUND_SHOW:
                setCaptionsBackgroundVisible(((TwoStatePreference) preference).isChecked());
                return true;
            case KEY_WINDOW_SHOW:
                setCaptionsWindowVisible(((TwoStatePreference) preference).isChecked());
                return true;
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
            case KEY_FONT_FAMILY:
                setCaptionsFontFamily((String) newValue);
                break;
            case KEY_TEXT_COLOR:
                setCaptionsTextColor((String) newValue);
                break;
            case KEY_TEXT_OPACITY:
                setCaptionsTextOpacity((String) newValue);
                break;
            case KEY_EDGE_TYPE:
                setCaptionsEdgeType((String) newValue);
                break;
            case KEY_EDGE_COLOR:
                setCaptionsEdgeColor((String) newValue);
                break;
            case KEY_BACKGROUND_COLOR:
                setCaptionsBackgroundColor((String) newValue);
                break;
            case KEY_BACKGROUND_OPACITY:
                setCaptionsBackgroundOpacity((String) newValue);
                break;
            case KEY_WINDOW_COLOR:
                setCaptionsWindowColor((String) newValue);
                break;
            case KEY_WINDOW_OPACITY:
                setCaptionsWindowOpacity((String) newValue);
                break;
            default:
                throw new IllegalStateException("Preference change with unknown key " + key);
        }
        return true;
    }

    private void refresh() {
        mFontFamilyPref.setValue(getCaptionsFontFamily());
        mTextColorPref.setValue(getCaptionsTextColor());
        mTextOpacityPref.setValue(getCaptionsTextOpacity());
        mEdgeTypePref.setValue(getCaptionsEdgeType());
        mEdgeColorPref.setValue(getCaptionsEdgeColor());
        mBackgroundShowPref.setChecked(isCaptionsBackgroundVisible());
        mBackgroundColorPref.setValue(getCaptionsBackgroundColor());
        mBackgroundOpacityPref.setValue(getCaptionsBackgroundOpacity());
        mWindowShowPref.setChecked(isCaptionsWindowVisible());
        mWindowColorPref.setValue(getCaptionsWindowColor());
        mWindowOpacityPref.setValue(getCaptionsWindowOpacity());

    }

    private String getCaptionsFontFamily() {
        final String typeface = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_TYPEFACE);
        return TextUtils.isEmpty(typeface) ? "default" : typeface;
    }

    private void setCaptionsFontFamily(String fontFamily) {
        if (TextUtils.equals(fontFamily, "default")) {
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_CAPTIONING_TYPEFACE, null);
        } else {
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_CAPTIONING_TYPEFACE, fontFamily);
        }
    }

    private String getCaptionsTextColor() {
        return Integer.toHexString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, 0) & 0x00ffffff);
    }

    private void setCaptionsTextColor(String textColor) {
        final int color = (int) Long.parseLong(textColor, 16) & 0x00ffffff;
        final int alpha = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, 0xff000000) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, color | alpha);
    }

    private String getCaptionsTextOpacity() {
        return opacityToString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, 0) & 0xff000000);
    }

    private void setCaptionsTextOpacity(String textOpacity) {
        final int color = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, 0) & 0x00ffffff;
        final int alpha = (int) Long.parseLong(textOpacity, 16) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FOREGROUND_COLOR, color | alpha);
    }

    private String getCaptionsEdgeType() {
        return Integer.toString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_TYPE, 0));
    }

    private void setCaptionsEdgeType(String edgeType) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_TYPE, Integer.parseInt(edgeType));
    }

    private String getCaptionsEdgeColor() {
        return Integer.toHexString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_COLOR, 0) & 0x00ffffff);
    }

    private void setCaptionsEdgeColor(String edgeColor) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_EDGE_COLOR,
                0xff000000 | (int) Long.parseLong(edgeColor, 16));
    }

    private boolean isCaptionsBackgroundVisible() {
        return (Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, 0) & 0xff000000) != 0;
    }

    private void setCaptionsBackgroundVisible(boolean visible) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR,
                visible ? 0xff000000 : 0);
        if (!visible) {
            mBackgroundColorPref.setValue(Integer.toHexString(0));
            mBackgroundOpacityPref.setValue(opacityToString(0));
        }
    }

    private String getCaptionsBackgroundColor() {
        return Integer.toHexString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, 0) & 0x00ffffff);
    }

    private void setCaptionsBackgroundColor(String backgroundColor) {
        final int color = (int) Long.parseLong(backgroundColor, 16) & 0x00ffffff;
        final int alpha = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, 0xff000000) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, color | alpha);
    }

    private String getCaptionsBackgroundOpacity() {
        return opacityToString (Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, 0) & 0xff000000);
    }

    private void setCaptionsBackgroundOpacity(String backgroundOpacity) {
        final int color = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, 0) & 0x00ffffff;
        final int alpha = (int) Long.parseLong(backgroundOpacity, 16) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_BACKGROUND_COLOR, color | alpha);
    }

    private boolean isCaptionsWindowVisible() {
        return (Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, 0) & 0xff000000) != 0;
    }

    private void setCaptionsWindowVisible(boolean visible) {
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, visible ? 0xff000000 : 0);
        if (!visible) {
            mWindowColorPref.setValue(Integer.toHexString(0));
            mWindowOpacityPref.setValue(opacityToString(0));
        }
    }

    private String getCaptionsWindowColor() {
        return Integer.toHexString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, 0) & 0x00ffffff);
    }

    private void setCaptionsWindowColor(String windowColor) {
        final int color = (int) Long.parseLong(windowColor, 16) & 0x00ffffff;
        final int alpha = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, 0xff000000) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, color | alpha);
    }

    private String getCaptionsWindowOpacity() {
        return opacityToString(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, 0) & 0xff000000);
    }

    private void setCaptionsWindowOpacity(String windowOpacity) {
        final int color = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, 0) & 0x00ffffff;
        final int alpha = (int) Long.parseLong(windowOpacity, 16) & 0xff000000;
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_WINDOW_COLOR, color | alpha);
    }

    private String opacityToString(int opacity) {
        switch (opacity & 0xff000000) {
            case 0x40000000:
                return "40FFFFFF";
            case 0x80000000:
                return "80FFFFFF";
            case 0xc0000000:
                return "C0FFFFFF";
            case 0xff000000:
            default:
                return "FFFFFFFF";
        }
    }
}
