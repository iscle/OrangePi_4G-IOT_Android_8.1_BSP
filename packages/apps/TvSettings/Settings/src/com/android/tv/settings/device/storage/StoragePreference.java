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

package com.android.tv.settings.device.storage;

import android.content.Context;
import android.support.annotation.Keep;
import android.support.v7.preference.Preference;
import android.text.format.Formatter;
import android.util.AttributeSet;

import com.android.tv.settings.R;
import com.android.tv.settings.device.StorageResetFragment;

public class StoragePreference extends Preference {

    private static final long SIZE_CALCULATING = -1;

    public StoragePreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setSize(SIZE_CALCULATING);
    }

    public StoragePreference(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setSize(SIZE_CALCULATING);
    }

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSize(SIZE_CALCULATING);
    }

    public StoragePreference(Context context) {
        super(context);
        setSize(SIZE_CALCULATING);
    }

    public void setSize(long size) {
        setSummary(formatSize(getContext(), size));
    }

    public static String formatSize(Context context, long size) {
        return (size == SIZE_CALCULATING)
                ? context.getString(R.string.storage_calculating_size)
                : Formatter.formatShortFileSize(context, size);
    }
}
