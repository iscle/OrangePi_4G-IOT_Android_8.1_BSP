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
 * limitations under the License.
 */
package com.android.emergency.preferences;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceViewHolder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.emergency.R;
import com.android.emergency.ReloadablePreferenceInterface;
import com.android.settingslib.CustomEditTextPreference;

/**
 * Custom {@link EditTextPreference} that allows us to refresh and update the summary.
 */
public class EmergencyEditTextPreference extends CustomEditTextPreference
        implements Preference.OnPreferenceChangeListener, ReloadablePreferenceInterface {

    private static final int MAX_LINES = 50;

    public EmergencyEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.EmergencyEditTextPreference, 0, 0);
        if (a.hasValue(R.styleable.EmergencyEditTextPreference_summary)) {
            setSummary(a.getString(R.styleable.EmergencyEditTextPreference_summary));
        }
        a.recycle();
    }

    @Override
    public void reloadFromPreference() {
        setText(getPersistedString(""));
    }

    @Override
    public boolean isNotSet() {
        return TextUtils.isEmpty(getText());
    }

    @Override
    public CharSequence getSummary() {
        String text = getText();
        return TextUtils.isEmpty(text) ? super.getSummary() : text;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final TextView summaryView = (TextView) holder.findViewById(
                com.android.internal.R.id.summary);
        summaryView.setMaxLines(MAX_LINES);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String text = (String) newValue;
        setSummary(text);
        return true;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        getEditText().setSelection(getEditText().getText().length());
    }
}
