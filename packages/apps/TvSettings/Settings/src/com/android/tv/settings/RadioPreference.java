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

package com.android.tv.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.AttributeSet;

public class RadioPreference extends CheckBoxPreference {
    private String mRadioGroup;

    public RadioPreference(Context context) {
        this(context, null);
    }

    public RadioPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        final TypedArray a =
                context.obtainStyledAttributes(attrs, R.styleable.RadioPreference, 0, 0);

        mRadioGroup = a.getString(R.styleable.RadioPreference_radioGroup);

        a.recycle();

        setWidgetLayoutResource(R.layout.radio_preference_widget);
    }

    public String getRadioGroup() {
        return mRadioGroup;
    }

    public void setRadioGroup(String radioGroup) {
        mRadioGroup = radioGroup;
    }

    public void clearOtherRadioPreferences(PreferenceGroup preferenceGroup) {
        final int count = preferenceGroup.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference p = preferenceGroup.getPreference(i);
            if (!(p instanceof RadioPreference)) {
                continue;
            }
            final RadioPreference radioPreference = (RadioPreference) p;
            if (!TextUtils.equals(getRadioGroup(), radioPreference.getRadioGroup())) {
                continue;
            }
            if (TextUtils.equals(getKey(), radioPreference.getKey())) {
                continue;
            }
            radioPreference.setChecked(false);
        }
    }
}
