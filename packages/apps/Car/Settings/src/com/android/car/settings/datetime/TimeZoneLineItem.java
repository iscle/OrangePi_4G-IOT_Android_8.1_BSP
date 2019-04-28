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

package com.android.car.settings.datetime;

import android.app.AlarmManager;
import android.content.Context;
import android.provider.Settings;

import android.annotation.NonNull;
import com.android.car.settings.R;
import com.android.car.settings.common.TextLineItem;
import com.android.settingslib.datetime.ZoneGetter;

import java.util.Map;

/**
 * A LineItem that displays available time zone.
 */
class TimeZoneLineItem extends TextLineItem {
    private final Context mContext;
    private final TimeZoneChangeListener mListener;
    private final Map<String, Object> mTimeZone;

    public interface TimeZoneChangeListener {
        void onTimeZoneChanged();
    }

    public TimeZoneLineItem(
            Context context,
            @NonNull TimeZoneChangeListener listener,
            Map<String, Object> timeZone) {
        super((CharSequence) timeZone.get(ZoneGetter.KEY_DISPLAYNAME));
        mContext = context;
        mListener = listener;
        mTimeZone = timeZone;
    }

    @Override
    public CharSequence getDesc() {
        return (CharSequence) mTimeZone.get(ZoneGetter.KEY_GMT);
    }

    @Override
    public boolean isEnabled() {
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.AUTO_TIME_ZONE, 0) <= 0;
    }

    @Override
    public boolean isExpandable() {
        return false;
    }

    @Override
    public boolean isClickable() {
        return true;
    }

    @Override
    public void onClick() {
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        am.setTimeZone((String) mTimeZone.get(ZoneGetter.KEY_ID));
        mListener.onTimeZoneChanged();
    }
}
