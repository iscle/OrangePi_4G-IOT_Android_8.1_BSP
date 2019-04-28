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

import android.content.SharedPreferences;

public class Setting {

    static final int TYPE_UNKNOWN = 0;
    static final int TYPE_INT = 1;
    static final int TYPE_STRING = 2;

    private String mTitle;
    private String mKey;
    private int mSettingType;

    private int mMaxValue;

    private final SharedPreferences mSharedPreferences;

    public Setting(String title) {
        mTitle = title;
        mSettingType = TYPE_UNKNOWN;
        mSharedPreferences = null;
    }

    public Setting(SharedPreferences sharedPreferences, String key, String title, int max) {
        mSharedPreferences = sharedPreferences;
        mTitle = title;
        mKey = key;
        mMaxValue = max;
        mSettingType = TYPE_INT;
    }

    public Setting(SharedPreferences sharedPreferences, String key, String title) {
        mSharedPreferences = sharedPreferences;
        mTitle = title;
        mKey = key;
        mSettingType = TYPE_STRING;
    }

    public int getType() {
        return mSettingType;
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getKey() {
        return mKey;
    }

    public int getMaxValue() {
        return mMaxValue;
    }

    public int getIntValue() {
        return mSharedPreferences.getInt(mKey, -1);
    }

    public String getStringValue() {
        return mSharedPreferences.getString(mKey, "");
    }

    public void setValue(int value) {
        mSharedPreferences.edit().putInt(mKey, value).apply();
        mSettingType = TYPE_INT;
    }

    public void setValue(String value) {
        mSharedPreferences.edit().putString(mKey, value).apply();
        mSettingType = TYPE_STRING;
    }
}
