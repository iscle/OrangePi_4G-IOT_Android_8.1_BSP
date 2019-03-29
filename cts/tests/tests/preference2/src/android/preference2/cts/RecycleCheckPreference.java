/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.preference2.cts;

import android.content.Context;
import android.preference.CheckBoxPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Preference wrapper designed to help to verify whether the preference is recycled by logging calls
 * of {@link #getView(View, ViewGroup)}
 */
public class RecycleCheckPreference extends CheckBoxPreference {

    /**
     * How many times was the {@link #getView(View, ViewGroup)} method called during the lifetime.
     */
    int getViewCalledCnt;

    /**
     * Whether the convert view param was null during the last call of
     * {@link #getView(View, ViewGroup)}
     */
    boolean wasConvertViewNullInLastCall;


    public RecycleCheckPreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RecycleCheckPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public RecycleCheckPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecycleCheckPreference(Context context) {
        super(context);
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        getViewCalledCnt++;
        wasConvertViewNullInLastCall = convertView == null;

        return super.getView(convertView, parent);
    }
}
