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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;

public class PresetSettingsListener implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final Context mContext;

    PresetSettingsListener(Context context) {
        mContext = context;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (!TextUtils.equals(key, "preset")) {
            return;
        }
        final String preset = sharedPreferences.getString(key, "standard");
        final Resources res = mContext.getResources();
        final int[] newValues;
        switch (preset) {
            case "standard":
                newValues = res.getIntArray(R.array.standard_setting_values);
                break;
            case "cinema":
                newValues = res.getIntArray(R.array.cinema_setting_values);
                break;
            case "vivid":
                newValues = res.getIntArray(R.array.vivid_setting_values);
                break;
            case "game":
                newValues = res.getIntArray(R.array.game_setting_values);
                break;
            default:
            case "custom":
                return;
        }
        final String[] keys = res.getStringArray(R.array.setting_keys);

        final SharedPreferences.Editor prefs = sharedPreferences.edit();
        try {
            for (int i = 0; i < keys.length; i++) {
                prefs.putInt(keys[i], newValues[i]);
            }
        } finally {
            prefs.apply();
        }

    }
}
