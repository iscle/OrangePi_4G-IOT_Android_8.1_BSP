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

import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
import static android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL;

import android.content.Context;
import android.provider.Settings;
import android.support.v7.widget.RecyclerView;

import com.android.car.settings.R;

import com.android.car.settings.common.ToggleLineItem;

/**
 * A LineItem that displays and sets display auto brightness setting.
 */
class AutoBrightnessLineItem extends ToggleLineItem {
    private final Context mContext;

    public AutoBrightnessLineItem(Context context) {
        super(context.getText(R.string.auto_brightness_title));
        mContext = context;
    }

    @Override
    public void onClick(boolean isChecked) {
        Settings.System.putInt(mContext.getContentResolver(), SCREEN_BRIGHTNESS_MODE,
                isChecked ? SCREEN_BRIGHTNESS_MODE_MANUAL : SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    @Override
    public boolean isChecked() {
        if (!isEnabled()) {
            return false;
        }
        int brightnessMode = Settings.System.getInt(mContext.getContentResolver(),
                SCREEN_BRIGHTNESS_MODE, SCREEN_BRIGHTNESS_MODE_MANUAL);
        return brightnessMode != SCREEN_BRIGHTNESS_MODE_MANUAL;
    }

    @Override
    public CharSequence getDesc() {
        return mContext.getText(R.string.auto_brightness_summary);
    }

    @Override
    public void bindViewHolder(ToggleLineItemViewHolder holder) {
        super.bindViewHolder(holder);
        boolean enabled = isEnabled();
        holder.titleView.setEnabled(enabled);
        holder.descView.setEnabled(enabled);
        holder.toggle.setEnabled(enabled);
        holder.itemView.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
    }

    @Override
    public boolean isExpandable() {
        return false;
    }
}
