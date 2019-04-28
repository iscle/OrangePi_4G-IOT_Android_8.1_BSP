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

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.android.car.settings.common.ToggleLineItem;

/**
 * A LineItem that displays and sets system auto date/time.
 */
class DateTimeToggleLineItem extends ToggleLineItem {
    private Context mContext;
    private final String mSettingString;
    private final String mDesc;

    public DateTimeToggleLineItem(Context context, String title, String desc,
            String settingString) {
        super(title);
        mContext = context;
        mSettingString = settingString;
        mDesc = desc;
    }

    @Override
    public void onClick(boolean isChecked) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                mSettingString,
                isChecked ? 0 : 1);
        mContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
    }

    @Override
    public boolean isChecked() {
        return Settings.Global.getInt(mContext.getContentResolver(), mSettingString, 0) > 0;
    }

    @Override
    public CharSequence getDesc() {
        return mDesc;
    }

    @Override
    public boolean isExpandable() {
        return false;
    }
}
