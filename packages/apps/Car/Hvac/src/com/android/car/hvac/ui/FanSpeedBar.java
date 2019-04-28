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
package com.android.car.hvac.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.car.hvac.R;

import java.util.ArrayList;

/**
 * Represents the fan speed bar. The bar is composed of a list of fan speed buttons. When a
 * speed is selected, all lower levels will turn on and all higher levels will turn off. The
 * currently selected speed can also be toggled on and off.
 */
public class FanSpeedBar extends RelativeLayout {
    /**
     * A listener that is notified when the buttons in the fan speed bar are clicked.
     */
    public interface FanSpeedButtonClickListener {
        void onMaxButtonClicked();

        void onOffButtonClicked();

        void onFanSpeedSegmentClicked(int position);
    }

    private static final int BAR_SEGMENT_ANIMATION_DELAY_MS = 50;
    private static final int BAR_SEGMENT_ANIMATION_MS = 100;
    private static final int NUM_FAN_SPEED = 34;

    private int mButtonEnabledTextColor;
    private int mButtonDisabledTextColor;

    private int mFanOffEnabledBgColor;
    private int mFanMaxEnabledBgColor;

    private float mCornerRadius;

    private TextView mMaxButton;
    private TextView mOffButton;

    private FanSpeedBarSegment mFanSpeed1;
    private FanSpeedBarSegment mFanSpeed2;
    private FanSpeedBarSegment mFanSpeed3;
    private FanSpeedBarSegment mFanSpeed4;

    private FanSpeedButtonClickListener mListener;

    private final ArrayList<FanSpeedBarSegment> mFanSpeedButtons = new ArrayList<>(NUM_FAN_SPEED);

    public FanSpeedBar(Context context) {
        super(context);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FanSpeedBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.fan_speed, this);

        Resources res = getContext().getResources();
        // The fanspeed bar is set as height 72dp to match min tap target size. However it is
        // inset by to make it appear thinner.
        int barHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_height);
        int insetHeight = res.getDimensionPixelSize(R.dimen.hvac_fan_speed_bar_vertical_inset);
        mCornerRadius = (barHeight - 2 * insetHeight) / 2;

        mFanOffEnabledBgColor = res.getColor(R.color.hvac_fanspeed_off_enabled_bg);

        mButtonEnabledTextColor = res.getColor(R.color.hvac_fanspeed_off_enabled_text_color);
        mButtonDisabledTextColor = res.getColor(R.color.hvac_fanspeed_off_disabled_text_color);
        mFanMaxEnabledBgColor = res.getColor(R.color.hvac_fanspeed_segment_color);
    }

    public void setFanspeedButtonClickListener(FanSpeedButtonClickListener clickListener) {
        mListener = clickListener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mFanSpeed1 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_1);
        mFanSpeed2 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_2);
        mFanSpeed3 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_3);
        mFanSpeed4 = (FanSpeedBarSegment) findViewById(R.id.fan_speed_4);

        mFanSpeed1.setTag(R.id.TAG_FAN_SPEED_LEVEL, 1);
        mFanSpeed2.setTag(R.id.TAG_FAN_SPEED_LEVEL, 2);
        mFanSpeed3.setTag(R.id.TAG_FAN_SPEED_LEVEL, 3);
        mFanSpeed4.setTag(R.id.TAG_FAN_SPEED_LEVEL, 4);

        mFanSpeedButtons.add(mFanSpeed1);
        mFanSpeedButtons.add(mFanSpeed2);
        mFanSpeedButtons.add(mFanSpeed3);
        mFanSpeedButtons.add(mFanSpeed4);

        for (View view : mFanSpeedButtons) {
            view.setOnClickListener(mFanSpeedBarClickListener);
        }

        mMaxButton = (TextView) findViewById(R.id.fan_max);
        mMaxButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setMax();
                if (mListener != null) {
                  mListener.onMaxButtonClicked();
                }
            }
        });

        mOffButton = (TextView) findViewById(R.id.fan_off);
        mOffButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                setOff();
                if (mListener != null) {
                    mListener.onOffButtonClicked();
                }
            }
        });

        // Set the corner radius of the off/max button based on the height of the bar to get a
        // semicircular border.
        GradientDrawable offButtonBg = new GradientDrawable();
        offButtonBg.setCornerRadii(new float[]{mCornerRadius, mCornerRadius, 0, 0,
                0, 0, mCornerRadius, mCornerRadius});
        mOffButton.setBackground(offButtonBg);
        mOffButton.setTextColor(mButtonDisabledTextColor);

        GradientDrawable maxButtonBg = new GradientDrawable();
        maxButtonBg.setCornerRadii(new float[]{0, 0, mCornerRadius, mCornerRadius,
                mCornerRadius, mCornerRadius, 0, 0});
        mMaxButton.setBackground(maxButtonBg);
        mMaxButton.setTextColor(mButtonDisabledTextColor);
    }

    public void setMax() {
        int numFanSpeed = mFanSpeedButtons.size();
        int delay = 0;

        for (int i = 0; i < numFanSpeed; i++) {
            if (!mFanSpeedButtons.get(i).isTurnedOn()) {
                mFanSpeedButtons.get(i).playTurnOnAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
            }
        }
        setOffButtonEnabled(false);
        setMaxButtonEnabled(true);
    }

    private void setMaxButtonEnabled(boolean enabled) {
        GradientDrawable background = (GradientDrawable) mMaxButton.getBackground();
        if (enabled) {
            background.setColor(mFanMaxEnabledBgColor);
            mMaxButton.setTextColor(mButtonEnabledTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mMaxButton.setTextColor(mButtonDisabledTextColor);
        }
    }


    private void setOffButtonEnabled(boolean enabled) {
        GradientDrawable background = (GradientDrawable) mOffButton.getBackground();
        if (enabled) {
            background.setColor(mFanOffEnabledBgColor);
            mOffButton.setTextColor(mButtonEnabledTextColor);
        } else {
            background.setColor(Color.TRANSPARENT);
            mOffButton.setTextColor(mButtonDisabledTextColor);
        }
    }

    public void setOff() {
        setOffButtonEnabled(true);
        setMaxButtonEnabled(false);

        int numFanSpeed = mFanSpeedButtons.size();
        int delay = 0;
        for (int i = numFanSpeed - 1; i >= 0; i--) {
            if (mFanSpeedButtons.get(i).isTurnedOn()) {
                mFanSpeedButtons.get(i).playTurnOffAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
            }
        }
    }

    /**
     * Sets the fan speed segments to on off based on the position. Note the changes do not animate,
     * if animation is required use {@link FanSpeedBar#animateToSpeedSegment(int)}
     */
    public void setSpeedSegment(int position) {
        for (int i = 0; i < mFanSpeedButtons.size(); i++) {
            // For segments lower than the position, they should be turned on.
            mFanSpeedButtons.get(i).setTurnedOn(i < position ? true : false);
        }
    }

    /**
     * Animates the fan speed bar to a specific position. Turning on all positions before it
     * and turning off all positions after it.
     */
    public void animateToSpeedSegment(int position) {
        setOffButtonEnabled(false);
        setMaxButtonEnabled(false);

        int fanSpeedCount = mFanSpeedButtons.size();
        int fanSpeedIndex = position - 1;

        if (fanSpeedIndex < 0) {
            fanSpeedIndex = 0;
        } else if (fanSpeedIndex > fanSpeedCount) {
            fanSpeedIndex = fanSpeedCount - 1;
        }

        int delay = 0;
        if (mFanSpeedButtons.get(fanSpeedIndex).isTurnedOn()) {
            // If selected position is already turned on, then make sure each segment
            // after is turned off.
            for (int i = fanSpeedCount - 1; i > fanSpeedIndex; i--) {
                if (mFanSpeedButtons.get(i).isTurnedOn()) {
                    mFanSpeedButtons.get(i).playTurnOffAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }
        } else {
            // If the selected position is turned off, turn on all positions before it and itself on.
            for (int i = 0; i <= fanSpeedIndex; i++) {
                if (!mFanSpeedButtons.get(i).isTurnedOn()) {
                    mFanSpeedButtons.get(i).playTurnOnAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }
        }
    }

    private final OnClickListener mFanSpeedBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            int level = (int) v.getTag(R.id.TAG_FAN_SPEED_LEVEL);

            setOffButtonEnabled(false);
            setMaxButtonEnabled(false);

            int fanSpeedCount = mFanSpeedButtons.size();
            int fanSpeedIndex = level - 1;

            // If selected speed is the last segment in the bar, turn it off it's currently on.
            if (fanSpeedIndex == fanSpeedCount - 1
                    && mFanSpeedButtons.get(fanSpeedIndex).isTurnedOn()) {
                mFanSpeedButtons.get(fanSpeedIndex)
                        .playTurnOffAnimation(BAR_SEGMENT_ANIMATION_MS, 0);
                return;
            }

            // If the selected speed is on, and the next fan speed is not on. Then turn off
            // the selected speed.
            if (fanSpeedIndex < fanSpeedCount - 1
                    && mFanSpeedButtons.get(fanSpeedIndex).isTurnedOn()
                    && !mFanSpeedButtons.get(fanSpeedIndex + 1).isTurnedOn()) {
                mFanSpeedButtons.get(fanSpeedIndex)
                        .playTurnOffAnimation(BAR_SEGMENT_ANIMATION_MS, 0);
                return;
            }

            int delay = 0;
            for (int i = 0; i < level; i++) {
                if (!mFanSpeedButtons.get(i).isTurnedOn()) {
                    mFanSpeedButtons.get(i).playTurnOnAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }

            delay = 0;
            for (int i = fanSpeedCount - 1; i >= level; i--) {
                if (mFanSpeedButtons.get(i).isTurnedOn()) {
                    mFanSpeedButtons.get(i).playTurnOffAnimation(BAR_SEGMENT_ANIMATION_MS, delay);
                    delay += BAR_SEGMENT_ANIMATION_DELAY_MS;
                }
            }

            if (mListener != null) {
                mListener.onFanSpeedSegmentClicked(level);
            }
        }
    };
}
