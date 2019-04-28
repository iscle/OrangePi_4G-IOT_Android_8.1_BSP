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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.car.hvac.R;

import java.util.ArrayList;
import java.util.List;

/**
 * An expandable temperature control bar. Note this UI is meant to only support Fahrenheit.
 */
public class TemperatureBarOverlay extends FrameLayout {
    /**
     * A listener that observes clicks on the temperature bar.
     */
    public interface TemperatureAdjustClickListener {
        void onTemperatureChanged(int temperature);
    }

    private static final int EXPAND_ANIMATION_TIME_MS = 500;
    private static final int COLLAPSE_ANIMATION_TIME_MS = 200;

    private static final int TEXT_ALPHA_ANIMATION_TIME_DELAY_MS = 400;
    private static final int TEXT_ALPHA_FADE_OUT_ANIMATION_TIME_MS = 100;
    private static final int TEXT_ALPHA_FADE_IN_ANIMATION_TIME_MS = 300;

    private static final int COLOR_CHANGE_ANIMATION_TIME_MS = 200;

    private static final float BUTTON_ALPHA_COLLAPSED = 0f;
    private static final float BUTTON_ALPHA_EXPANDED = 1.0f;

    private static final int DEFAULT_TEMPERATURE = 32;
    private static final int MAX_TEMPERATURE = 85;
    private static final int MIN_TEMPERATURE = 60;

    private String mInvalidTemperature;

    private int mTempColor1;
    private int mTempColor2;
    private int mTempColor3;
    private int mTempColor4;
    private int mTempColor5;

    private int mOffColor;

    private ImageView mIncreaseButton;
    private ImageView mDecreaseButton;
    private TextView mText;
    private TextView mFloatingText;
    private TextView mOffText;
    private View mTemperatureBar;
    private View mCloseButton;

    private int mTemperature = DEFAULT_TEMPERATURE;

    private int mCollapsedWidth;
    private int mExpandedWidth;
    private int mCollapsedHeight;
    private int mExpandedHeight;
    private int mCollapsedYShift;
    private int mExpandedYShift;

    private boolean mIsOpen;
    private boolean mIsOn = true;

    private TemperatureAdjustClickListener mListener;

    public TemperatureBarOverlay(Context context) {
        super(context);
    }

    public TemperatureBarOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TemperatureBarOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        Resources res = getResources();

        mCollapsedHeight = res.getDimensionPixelSize(R.dimen.temperature_bar_collapsed_height);
        mExpandedHeight = res.getDimensionPixelSize(R.dimen.temperature_bar_expanded_height);
        // Push the collapsed circle all the way down to the bottom of the screen and leave
        // half of it visible.
        mCollapsedYShift
                = res.getDimensionPixelSize(R.dimen.car_hvac_panel_full_expanded_height)
                - (mCollapsedHeight / 2);
        mExpandedYShift = res.getDimensionPixelSize(R.dimen.hvac_panel_row_margin);

        mCollapsedWidth = res.getDimensionPixelSize(R.dimen.temperature_bar_width_collapsed);
        mExpandedWidth = res.getDimensionPixelSize(R.dimen.temperature_bar_width_expanded);

        mInvalidTemperature = getContext().getString(R.string.hvac_invalid_temperature);

        mTempColor1 = res.getColor(R.color.temperature_1);
        mTempColor2 = res.getColor(R.color.temperature_2);
        mTempColor3 = res.getColor(R.color.temperature_3);
        mTempColor4 = res.getColor(R.color.temperature_4);
        mTempColor5 = res.getColor(R.color.temperature_5);

        mOffColor = res.getColor(R.color.hvac_temperature_off_text_bg_color);

        mIncreaseButton = (ImageView) findViewById(R.id.increase_button);
        mDecreaseButton = (ImageView) findViewById(R.id.decrease_button);

        mFloatingText = (TextView) findViewById(R.id.floating_temperature_text);
        mText = (TextView) findViewById(R.id.temperature_text);
        mOffText = (TextView) findViewById(R.id.temperature_off_text);

        mTemperatureBar = findViewById(R.id.temperature_bar);
        mTemperatureBar.setTranslationY(mCollapsedYShift);

        mCloseButton = findViewById(R.id.close_button);

        mText.setText(getContext().getString(R.string.hvac_temperature_template,
                mInvalidTemperature));
        mFloatingText.setText(getContext()
                .getString(R.string.hvac_temperature_template,
                        mInvalidTemperature));

        mIncreaseButton.setOnTouchListener(new PressAndHoldTouchListener(temperatureClickListener));
        mDecreaseButton.setOnTouchListener(new PressAndHoldTouchListener(temperatureClickListener));

        if (!mIsOpen) {
            mIncreaseButton.setAlpha(BUTTON_ALPHA_COLLAPSED);
            mDecreaseButton.setAlpha(BUTTON_ALPHA_COLLAPSED);
            mText.setAlpha(BUTTON_ALPHA_COLLAPSED);

            mDecreaseButton.setVisibility(GONE);
            mIncreaseButton.setVisibility(GONE);
            mText.setVisibility(GONE);
        }
    }

    public void setTemperatureChangeListener(TemperatureAdjustClickListener listener) {
        mListener =  listener;
    }

    public void setBarOnClickListener(OnClickListener l) {
        mFloatingText.setOnClickListener(l);
        mTemperatureBar.setOnClickListener(l);
    }

    public void setCloseButtonOnClickListener(OnClickListener l) {
        mCloseButton.setOnClickListener(l);
    }

    public AnimatorSet getExpandAnimatons() {
        List<Animator> list = new ArrayList();
        AnimatorSet animation = new AnimatorSet();
        if (mIsOpen) {
            return animation;
        }

        list.add(getAlphaAnimator(mIncreaseButton, false /* fade */, EXPAND_ANIMATION_TIME_MS));
        list.add(getAlphaAnimator(mDecreaseButton, false /* fade */, EXPAND_ANIMATION_TIME_MS));
        list.add(getAlphaAnimator(mText, false /* fade */, EXPAND_ANIMATION_TIME_MS));
        list.add(getAlphaAnimator(mFloatingText, true /* fade */,
                TEXT_ALPHA_FADE_OUT_ANIMATION_TIME_MS));

        ValueAnimator widthAnimator = new ValueAnimator().ofInt(mCollapsedWidth, mExpandedWidth)
                .setDuration(EXPAND_ANIMATION_TIME_MS);
        widthAnimator.addUpdateListener(mWidthUpdateListener);
        list.add(widthAnimator);

        ValueAnimator heightAnimator = new ValueAnimator().ofInt(mCollapsedHeight,
                mExpandedHeight)
                .setDuration(EXPAND_ANIMATION_TIME_MS);
        heightAnimator.addUpdateListener(mHeightUpdateListener);
        list.add(heightAnimator);


        ValueAnimator translationYAnimator
                = new ValueAnimator().ofFloat(mCollapsedYShift, mExpandedYShift);
        translationYAnimator.addUpdateListener(mTranslationYListener);
        list.add(translationYAnimator);

        animation.playTogether(list);
        animation.addListener(mStateListener);

        return animation;
    }

    public AnimatorSet getCollapseAnimations() {

        List<Animator> list = new ArrayList();
        AnimatorSet animation = new AnimatorSet();

        if (!mIsOpen) {
            return animation;
        }
        list.add(getAlphaAnimator(mIncreaseButton, true /* fade */, COLLAPSE_ANIMATION_TIME_MS));
        list.add(getAlphaAnimator(mDecreaseButton, true /* fade */, COLLAPSE_ANIMATION_TIME_MS));
        list.add(getAlphaAnimator(mText, true /* fade */, COLLAPSE_ANIMATION_TIME_MS));

        ObjectAnimator floatingTextAnimator = getAlphaAnimator(mFloatingText,
                false /* fade */, TEXT_ALPHA_FADE_IN_ANIMATION_TIME_MS);
        floatingTextAnimator.setStartDelay(TEXT_ALPHA_ANIMATION_TIME_DELAY_MS);

        list.add(floatingTextAnimator);

        ValueAnimator widthAnimator = new ValueAnimator().ofInt(mExpandedWidth, mCollapsedWidth)
                .setDuration(COLLAPSE_ANIMATION_TIME_MS);
        widthAnimator.addUpdateListener(mWidthUpdateListener);
        list.add(widthAnimator);

        ValueAnimator heightAnimator = new ValueAnimator().ofInt(mExpandedHeight, mCollapsedHeight)
                .setDuration(COLLAPSE_ANIMATION_TIME_MS);
        heightAnimator.addUpdateListener(mHeightUpdateListener);
        list.add(heightAnimator);

        ValueAnimator translationYAnimator
                = new ValueAnimator().ofFloat(mExpandedYShift, mCollapsedYShift);
        translationYAnimator.addUpdateListener(mTranslationYListener);
        list.add(translationYAnimator);

        animation.playTogether(list);
        animation.addListener(mStateListener);

        return animation;
    }

    private ValueAnimator.AnimatorListener mStateListener = new ValueAnimator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            if (!mIsOpen) {
                mDecreaseButton.setVisibility(VISIBLE);
                mIncreaseButton.setVisibility(VISIBLE);
                mText.setVisibility(VISIBLE);
                mCloseButton.setVisibility(VISIBLE);
            } else {
                mCloseButton.setVisibility(GONE);
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mIsOpen) {
                //Finished closing, make sure the buttons are now gone,
                //so they are no longer touchable
                mDecreaseButton.setVisibility(GONE);
                mIncreaseButton.setVisibility(GONE);
                mText.setVisibility(GONE);
                mIsOpen = false;
            } else {
                //Finished opening
                mIsOpen = true;
            }
        }

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationRepeat(Animator animation) {
        }
    };

    private void changeTemperatureColor(int startColor, int endColor) {
        if (endColor != startColor) {
            ValueAnimator animator = ValueAnimator.ofArgb(startColor, endColor);
            animator.addUpdateListener(mTemperatureColorListener);
            animator.setDuration(COLOR_CHANGE_ANIMATION_TIME_MS);
            animator.start();
        } else {
            ((GradientDrawable) mTemperatureBar.getBackground()).setColor(endColor);
        }
    }

    private final View.OnClickListener temperatureClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int startColor = getTemperatureColor(mTemperature);

            if (v == mIncreaseButton && mTemperature < MAX_TEMPERATURE) {
                mTemperature++;
            } else if (v == mDecreaseButton && mTemperature > MIN_TEMPERATURE) {
                mTemperature--;
            }
            int endColor = getTemperatureColor(mTemperature);
            changeTemperatureColor(startColor, endColor);

            mText.setText(getContext().getString(R.string.hvac_temperature_template, mTemperature));
            mFloatingText.setText(getContext()
                    .getString(R.string.hvac_temperature_template, mTemperature));
            mListener.onTemperatureChanged(mTemperature);
        }
    };

    public void setTemperature(int temperature) {
        int startColor = getTemperatureColor(mTemperature);
        int endColor = getTemperatureColor(temperature);
        mTemperature = temperature;
        String temperatureString;

        if (mTemperature < MIN_TEMPERATURE || mTemperature > MAX_TEMPERATURE) {
            temperatureString = mInvalidTemperature;
        } else {
            temperatureString = String.valueOf(mTemperature);
        }

        mText.setText(getContext().getString(R.string.hvac_temperature_template,
                temperatureString));
        mFloatingText.setText(getContext()
                .getString(R.string.hvac_temperature_template, temperatureString));

        // Only animate the color if the button is currently enabled.
        if (mIsOn) {
            changeTemperatureColor(startColor, endColor);
        }
    }

    /**
     * Sets whether or not the temperature bar is on. If it is off, it should show "off" instead
     * of the temperature.
     */
    public void setIsOn(boolean isOn) {
        mIsOn = isOn;
        GradientDrawable temperatureBall
                = (GradientDrawable) mTemperatureBar.getBackground();
        if (mIsOn) {
            mFloatingText.setVisibility(VISIBLE);
            mOffText.setVisibility(GONE);
            temperatureBall.setColor(getTemperatureColor(mTemperature));
            setAlpha(1.0f);
        } else {
            mOffText.setVisibility(VISIBLE);
            mFloatingText.setVisibility(GONE);
            temperatureBall.setColor(mOffColor);
            setAlpha(.2f);
        }
    }

    private int getTemperatureColor(int temperature) {
        if (temperature >= 78) {
            return mTempColor1;
        } else if (temperature >= 74 && temperature < 78) {
            return mTempColor2;
        } else if (temperature >= 70 && temperature < 74) {
            return mTempColor3;
        } else if (temperature >= 66 && temperature < 70) {
            return mTempColor4;
        } else {
            return mTempColor5;
        }
    }

    private final ValueAnimator.AnimatorUpdateListener mTranslationYListener
            = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float translation = (float) animation.getAnimatedValue();
            mTemperatureBar.setTranslationY(translation);
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mWidthUpdateListener
            = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int width = (Integer) animation.getAnimatedValue();
            mTemperatureBar.getLayoutParams().width = width;
            mTemperatureBar.requestLayout();
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mHeightUpdateListener
            = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int height = (Integer) animation.getAnimatedValue();
            int currentHeight = mTemperatureBar.getLayoutParams().height;
            mTemperatureBar.getLayoutParams().height = height;
            mTemperatureBar.setTop(mTemperatureBar.getTop() + height - currentHeight);
            mTemperatureBar.requestLayout();
        }
    };

    private final ValueAnimator.AnimatorUpdateListener mTemperatureColorListener
            = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            int color = (Integer) animation.getAnimatedValue();
            ((GradientDrawable) mTemperatureBar.getBackground()).setColor(color);
        }
    };

    private ObjectAnimator getAlphaAnimator(View view, boolean fade) {
        return getAlphaAnimator(view, fade, EXPAND_ANIMATION_TIME_MS);
    }

    private ObjectAnimator getAlphaAnimator(View view, boolean fade, int duration) {

        float startingAlpha = BUTTON_ALPHA_COLLAPSED;
        float endingAlpha = BUTTON_ALPHA_EXPANDED;

        if (fade) {
            startingAlpha = BUTTON_ALPHA_EXPANDED;
            endingAlpha = BUTTON_ALPHA_COLLAPSED;
        }

        return ObjectAnimator.ofFloat(view, View.ALPHA,
                startingAlpha, endingAlpha).setDuration(duration);
    }
}
