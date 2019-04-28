/*******************************************************************************
 *      Copyright (C) 2014 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.ui.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.android.mail.R;

/**
 * A fancy ListPreference that displays its summary from among the entries in the "entrySummaries"
 * array attribute.
 *
 */
public class FancySummaryListPreference extends ListPreference {

    private CharSequence[] mEntrySummaries;

    public FancySummaryListPreference(Context context) {
        this(context, null);
    }

    public FancySummaryListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.FancySummaryListPreference, 0, 0);
        mEntrySummaries = a.getTextArray(R.styleable.FancySummaryListPreference_entrySummaries);
    }

    public void setEntrySummaries(CharSequence[] summaries) {
        mEntrySummaries = summaries;
        setSummary(getSummaryForValue(getValue()));
    }

    public void setEntrySummaries(int summariesResId) {
        setEntrySummaries(getContext().getResources().getTextArray(summariesResId));
    }

    public CharSequence[] getEntrySummaries() {
        return mEntrySummaries;
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        setSummary(getSummaryForValue(value));
    }

    private CharSequence getSummaryForValue(String value) {
        int i = findIndexOfValue(value);
        return (i >= 0 && i < mEntrySummaries.length) ? mEntrySummaries[i] : null;
    }

}
