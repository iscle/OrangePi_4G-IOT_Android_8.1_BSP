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
 * limitations under the License.
 */

package com.android.tv.ui;

import android.content.Context;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.widget.TextClock;

public class LocaleAwareTextClock extends TextClock {
    private static final String TAG = "LocaleAwareTextClock";

    public LocaleAwareTextClock(Context context) {
        this(context, null);
    }

    public LocaleAwareTextClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LocaleAwareTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Note: This assumes that locale cannot be changed while TV is showing.
        String pattern12 = DateFormat.getBestDateTimePattern(getTextLocale(), "hm MMMd");
        String pattern24 = DateFormat.getBestDateTimePattern(getTextLocale(), "Hm MMMd");
        setFormat12Hour(pattern12);
        setFormat24Hour(pattern24);
    }
}
