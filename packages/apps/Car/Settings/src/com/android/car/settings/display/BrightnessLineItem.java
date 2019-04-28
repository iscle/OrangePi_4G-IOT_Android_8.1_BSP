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

package com.android.car.settings.display;

import static android.provider.Settings.System.SCREEN_BRIGHTNESS;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.android.car.settings.R;
import com.android.car.settings.common.SeekbarLineItem;

/**
 * A LineItem that displays and sets display brightness.
 */
class BrightnessLineItem extends SeekbarLineItem {
    private static final String TAG = "BrightnessLineItem";
    private static final int MAX_BRIGHTNESS = 255;

    private final Context mContext;

    public BrightnessLineItem(Context context) {
        super(context.getText(R.string.brightness));
        mContext = context;
    }

    @Override
    public int getSeekbarValue() {
        int currentBrightness = 0;
        try {
            currentBrightness = Settings.System.getInt(mContext.getContentResolver(),
                    SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            Log.w(TAG, "Can't find setting for SCREEN_BRIGHTNESS.");
        }
        return currentBrightness;
    }

    @Override
    public int getMaxSeekbarValue() {
        return MAX_BRIGHTNESS;
    }

    @Override
    public void onSeekbarChanged(int progress) {
        Settings.System.putInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS, progress);
    }
}
