/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.radio;

import android.content.Context;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.android.car.apps.common.FabDrawable;

/**
 * A default FAB button for the radio that will color itself based on the accent color defined
 * in the application.
 */
public class RadioFabButton extends ImageView {
    private final FabDrawable mFabDrawable;
    @ColorInt private final int mEnabledColor;
    @ColorInt private final int mDisabledColor;

    public RadioFabButton(Context context) {
        super(context);
    }

    public RadioFabButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RadioFabButton(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
    }

    public RadioFabButton(Context context, AttributeSet attrs, int defStyleAttrs,
            int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
    }

    {
        Context context = getContext();

        mFabDrawable = new FabDrawable(context);
        setBackground(mFabDrawable);

        mEnabledColor = context.getColor(R.color.car_radio_accent_color);
        mDisabledColor = context.getColor(R.color.car_radio_control_fab_button_disabled);

        mFabDrawable.setFabAndStrokeColor(mEnabledColor);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mFabDrawable.setFabAndStrokeColor(enabled ? mEnabledColor : mDisabledColor);
    }
}
