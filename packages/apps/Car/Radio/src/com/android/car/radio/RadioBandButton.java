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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * A button that represents a band the user can select in manual tuning. When this button is
 * selected, then it draws a rounded pill background around itself.
 */
public class RadioBandButton extends Button {
    private Drawable mSelectedBackground;
    private Drawable mNormalBackground;

    @ColorInt private int mSelectedColor;
    @ColorInt private int mNormalColor;

    public RadioBandButton(Context context) {
        super(context);
        init(context, null);
    }

    public RadioBandButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public RadioBandButton(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context, attrs);
    }

    public RadioBandButton(Context context, AttributeSet attrs, int defStyleAttrs,
            int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        init(context, attrs);
    }

    /**
     * Initializes whether or not this button is initially selected.
     */
    private void init(Context context, AttributeSet attrs) {
        mSelectedBackground = context.getDrawable(R.drawable.manual_tuner_band_bg);
        mNormalBackground = context.getDrawable(R.drawable.radio_control_background);

        mSelectedColor = context.getColor(R.color.car_grey_50);
        mNormalColor = context.getColor(R.color.manual_tuner_channel_text);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.RadioBandButton);

        try {
            setIsBandSelected(ta.getBoolean(R.styleable.RadioBandButton_isBandSelected, false));
        } finally {
            ta.recycle();
        }
    }

    /**
     * Sets whether or not this button has been selected. This is different from
     * {@link #setSelected(boolean)}.
     */
    public void setIsBandSelected(boolean selected) {
        if (selected) {
            setTextColor(mSelectedColor);
            setBackground(mSelectedBackground);
        } else {
            setTextColor(mNormalColor);
            setBackground(mNormalBackground);
        }
    }
}
