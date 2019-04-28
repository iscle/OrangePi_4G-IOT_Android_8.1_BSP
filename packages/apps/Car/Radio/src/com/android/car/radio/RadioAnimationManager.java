/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.radio;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v7.widget.CardView;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import com.android.car.stream.ui.ColumnCalculator;

/**
 * A animation manager that is responsible for the start and exiting animation for the
 * {@link RadioPresetsFragment}.
 */
public class RadioAnimationManager {
    private static final int START_ANIM_DURATION_MS = 500;
    private static final int START_TRANSLATE_ANIM_DELAY_MS = 117;
    private static final int START_TRANSLATE_ANIM_DURATION_MS = 383;
    private static final int START_FADE_ANIM_DELAY_MS = 150;
    private static final int START_FADE_ANIM_DURATION_MS = 100;

    private static final int STOP_ANIM_DELAY_MS = 215;
    private static final int STOP_ANIM_DURATION_MS = 333;
    private static final int STOP_TRANSLATE_ANIM_DURATION_MS = 417;
    private static final int STOP_FADE_ANIM_DELAY_MS = 150;
    private static final int STOP_FADE_ANIM_DURATION_MS = 100;

    private static final FastOutSlowInInterpolator sInterpolator = new FastOutSlowInInterpolator();

    private final Context mContext;
    private final int mScreenWidth;
    private int mAppScreenHeight;

    private final int mCardColumnSpan;
    private final int mCornerRadius;
    private final int mActionPanelHeight;
    private final int mPresetFinalHeight;

    private final int mFabSize;
    private final int mPresetFabSize;
    private final int mPresetContainerHeight;

    private final View mContainer;
    private final CardView mRadioCard;
    private final View mRadioCardContainer;
    private final View mFab;
    private final View mPresetFab;
    private final View mRadioControls;
    private final View mRadioCardControls;
    private final View mPresetsList;

    public interface OnExitCompleteListener {
        void onExitAnimationComplete();
    }

    public RadioAnimationManager(Context context, View container) {
        mContext = context;
        mContainer = container;

        WindowManager windowManager =
                (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mScreenWidth = size.x;

        Resources res = mContext.getResources();
        mCardColumnSpan = res.getInteger(R.integer.stream_card_default_column_span);
        mCornerRadius = res.getDimensionPixelSize(R.dimen.car_preset_item_radius);
        mActionPanelHeight = res.getDimensionPixelSize(R.dimen.action_panel_height);
        mPresetFinalHeight = res.getDimensionPixelSize(R.dimen.car_preset_item_height);
        mFabSize = res.getDimensionPixelSize(R.dimen.stream_fab_size);
        mPresetFabSize = res.getDimensionPixelSize(R.dimen.car_presets_play_button_size);
        mPresetContainerHeight = res.getDimensionPixelSize(R.dimen.car_preset_container_height);

        mRadioCard = container.findViewById(R.id.current_radio_station_card);
        mRadioCardContainer = container.findViewById(R.id.preset_current_card_container);
        mFab = container.findViewById(R.id.radio_play_button);
        mPresetFab = container.findViewById(R.id.preset_radio_play_button);
        mRadioControls = container.findViewById(R.id.radio_buttons_container);
        mRadioCardControls = container.findViewById(R.id.current_radio_station_card_controls);
        mPresetsList = container.findViewById(R.id.presets_list);
    }

    /**
     * Start the exit animation for the preset activity. This animation will move the radio controls
     * down to where it would be in the {@link CarRadioActivity}. Upon completion of the
     * animation, the given {@link OnExitCompleteListener} will be notified.
     */
    public void playExitAnimation(@NonNull OnExitCompleteListener listener) {
        // Animator that will animate the radius of mRadioCard from rounded to non-rounded.
        ValueAnimator cornerRadiusAnimator = ValueAnimator.ofInt(mCornerRadius, 0);
        cornerRadiusAnimator.setStartDelay(STOP_ANIM_DELAY_MS);
        cornerRadiusAnimator.setDuration(STOP_ANIM_DURATION_MS);
        cornerRadiusAnimator.addUpdateListener(
                animator -> mRadioCard.setRadius((int) animator.getAnimatedValue()));
        cornerRadiusAnimator.setInterpolator(sInterpolator);

        // Animator that will animate the radius of mRadioCard from its current width to the width
        // of the screen.
        ValueAnimator widthAnimator = ValueAnimator.ofInt(mRadioCard.getWidth(), mScreenWidth);
        widthAnimator.setInterpolator(sInterpolator);
        widthAnimator.setStartDelay(STOP_ANIM_DELAY_MS);
        widthAnimator.setDuration(STOP_ANIM_DURATION_MS);
        widthAnimator.addUpdateListener(valueAnimator -> {
            int width = (int) valueAnimator.getAnimatedValue();
            mRadioCard.getLayoutParams().width  = width;
            mRadioCard.requestLayout();
        });

        // Animate the height of the radio controls from its current height to the full height of
        // the action panel.
        ValueAnimator heightAnimator = ValueAnimator.ofInt(mRadioCard.getHeight(),
                mActionPanelHeight);
        heightAnimator.setInterpolator(sInterpolator);
        heightAnimator.setStartDelay(STOP_ANIM_DELAY_MS);
        heightAnimator.setDuration(STOP_ANIM_DURATION_MS);
        heightAnimator.addUpdateListener(valueAnimator -> {
            int height = (int) valueAnimator.getAnimatedValue();
            mRadioCard.getLayoutParams().height = height;
            mRadioCard.requestLayout();
        });

        // Animate the fab back to the size it will be in the main radio display.
        ValueAnimator fabAnimator = ValueAnimator.ofInt(mPresetFabSize, mFabSize);
        fabAnimator.setInterpolator(sInterpolator);
        fabAnimator.setStartDelay(STOP_ANIM_DELAY_MS);
        fabAnimator.setDuration(STOP_ANIM_DURATION_MS);
        fabAnimator.addUpdateListener(valueAnimator -> {
            int fabSize = (int) valueAnimator.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = mFab.getLayoutParams();
            layoutParams.width = fabSize;
            layoutParams.height = fabSize;
            mFab.requestLayout();

            layoutParams = mPresetFab.getLayoutParams();
            layoutParams.width = fabSize;
            layoutParams.height = fabSize;
            mPresetFab.requestLayout();
        });

        // The animator for the move downwards of the radio card.
        ObjectAnimator translationYAnimator = ObjectAnimator.ofFloat(mRadioCard,
                View.TRANSLATION_Y, mRadioCard.getTranslationY(), 0);
        translationYAnimator.setDuration(STOP_TRANSLATE_ANIM_DURATION_MS);

        // The animator for the move downwards of the preset list.
        ObjectAnimator presetAnimator = ObjectAnimator.ofFloat(mPresetsList,
                View.TRANSLATION_Y, 0, mAppScreenHeight);
        presetAnimator.setDuration(STOP_TRANSLATE_ANIM_DURATION_MS);
        presetAnimator.start();

        // The animator for will fade in the radio controls.
        ValueAnimator radioControlsAlphaAnimator = ValueAnimator.ofFloat(0.f, 1.f);
        radioControlsAlphaAnimator.setInterpolator(sInterpolator);
        radioControlsAlphaAnimator.setStartDelay(STOP_FADE_ANIM_DELAY_MS);
        radioControlsAlphaAnimator.setDuration(STOP_FADE_ANIM_DURATION_MS);
        radioControlsAlphaAnimator.addUpdateListener(valueAnimator ->
                mRadioControls.setAlpha((float) valueAnimator.getAnimatedValue()));
        radioControlsAlphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mRadioControls.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {}

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        // The animator for will fade out of the preset radio controls.
        ObjectAnimator radioCardControlsAlphaAnimator = ObjectAnimator.ofFloat(mRadioCardControls,
                View.ALPHA, 1.f, 0.f);
        radioCardControlsAlphaAnimator.setInterpolator(sInterpolator);
        radioCardControlsAlphaAnimator.setStartDelay(STOP_FADE_ANIM_DELAY_MS);
        radioCardControlsAlphaAnimator.setDuration(STOP_FADE_ANIM_DURATION_MS);
        radioCardControlsAlphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}

            @Override
            public void onAnimationEnd(Animator animator) {
                mRadioCardControls.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(cornerRadiusAnimator, heightAnimator, widthAnimator, fabAnimator,
                translationYAnimator, radioControlsAlphaAnimator, radioCardControlsAlphaAnimator,
                presetAnimator);
        animatorSet.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                // Remove any elevation from the radio container since the radio card will move
                // out from the container.
                mRadioCardContainer.setElevation(0);
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                listener.onExitAnimationComplete();
            }

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });
        animatorSet.start();
    }

    /**
     * Start the enter animation for the preset fragment. This animation will move the radio
     * controls up from where they are in the {@link CarRadioActivity} to its final position.
     */
    public void playEnterAnimation() {
        // The animation requires that we know the size of the activity window. This value is
        // different from the size of the screen, which we could obtain using DisplayMetrics. As a
        // result, need to use a ViewTreeObserver to get the size of the containing view.
        mContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mAppScreenHeight = mContainer.getHeight();
                        startEnterAnimation();
                        mContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    /**
     * Actually starts the animations for the enter of the preset fragment.
     */
    private void startEnterAnimation() {
        FastOutSlowInInterpolator sInterpolator = new FastOutSlowInInterpolator();

        // Animator that will animate the radius of mRadioCard to its final rounded state.
        ValueAnimator cornerRadiusAnimator = ValueAnimator.ofInt(0, mCornerRadius);
        cornerRadiusAnimator.setDuration(START_ANIM_DURATION_MS);
        cornerRadiusAnimator.addUpdateListener(
                animator -> mRadioCard.setRadius((int) animator.getAnimatedValue()));
        cornerRadiusAnimator.setInterpolator(sInterpolator);

        // Animate the radio card from the size of the screen to its final size in the preset
        // list.
        ValueAnimator widthAnimator = ValueAnimator.ofInt(mScreenWidth,
                ColumnCalculator.getInstance(mContext).getSizeForColumnSpan(mCardColumnSpan));
        widthAnimator.setInterpolator(sInterpolator);
        widthAnimator.setDuration(START_ANIM_DURATION_MS);
        widthAnimator.addUpdateListener(valueAnimator -> {
            int width = (int) valueAnimator.getAnimatedValue();
            mRadioCard.getLayoutParams().width  = width;
            mRadioCard.requestLayout();
        });

        // Shrink the radio card down to its final height.
        ValueAnimator heightAnimator = ValueAnimator.ofInt(mActionPanelHeight, mPresetFinalHeight);
        heightAnimator.setInterpolator(sInterpolator);
        heightAnimator.setDuration(START_ANIM_DURATION_MS);
        heightAnimator.addUpdateListener(valueAnimator -> {
            int height = (int) valueAnimator.getAnimatedValue();
            mRadioCard.getLayoutParams().height = height;
            mRadioCard.requestLayout();
        });

        // Animate the fab from its large size in the radio controls to the smaller size in the
        // preset list.
        ValueAnimator fabAnimator = ValueAnimator.ofInt(mFabSize, mPresetFabSize);
        fabAnimator.setInterpolator(sInterpolator);
        fabAnimator.setDuration(START_ANIM_DURATION_MS);
        fabAnimator.addUpdateListener(valueAnimator -> {
            int fabSize = (int) valueAnimator.getAnimatedValue();
            ViewGroup.LayoutParams layoutParams = mFab.getLayoutParams();
            layoutParams.width = fabSize;
            layoutParams.height = fabSize;
            mFab.requestLayout();

            layoutParams = mPresetFab.getLayoutParams();
            layoutParams.width = fabSize;
            layoutParams.height = fabSize;
            mPresetFab.requestLayout();
        });

        // The top of the screen relative to where mRadioCard is positioned.
        int topOfScreen = mAppScreenHeight - mActionPanelHeight;

        // Because the height of the radio controls changes, we need to add the difference in height
        // to the final translation.
        topOfScreen = topOfScreen + (mActionPanelHeight - mPresetFinalHeight);

        // The radio card will need to be centered within the area given by mPresetContainerHeight.
        // This finalTranslation value is negative so that mRadioCard moves upwards.
        int finalTranslation = -(topOfScreen - ((mPresetContainerHeight - mPresetFinalHeight) / 2));

        // Animator to move the radio card from the bottom of the screen to its final y value.
        ObjectAnimator translationYAnimator = ObjectAnimator.ofFloat(mRadioCard,
                View.TRANSLATION_Y, 0, finalTranslation);
        translationYAnimator.setStartDelay(START_TRANSLATE_ANIM_DELAY_MS);
        translationYAnimator.setDuration(START_TRANSLATE_ANIM_DURATION_MS);

        // Animator to slide the preset list from the bottom of the screen to just below the radio
        // card.
        ObjectAnimator presetAnimator = ObjectAnimator.ofFloat(mPresetsList,
                View.TRANSLATION_Y, mAppScreenHeight, 0);
        presetAnimator.setStartDelay(START_TRANSLATE_ANIM_DELAY_MS);
        presetAnimator.setDuration(START_TRANSLATE_ANIM_DURATION_MS);
        presetAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mPresetsList.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {}

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        // Animator to fade out the radio controls.
        ValueAnimator radioControlsAlphaAnimator = ValueAnimator.ofFloat(1.f, 0.f);
        radioControlsAlphaAnimator.setInterpolator(sInterpolator);
        radioControlsAlphaAnimator.setStartDelay(START_FADE_ANIM_DELAY_MS);
        radioControlsAlphaAnimator.setDuration(START_FADE_ANIM_DURATION_MS);
        radioControlsAlphaAnimator.addUpdateListener(valueAnimator ->
                mRadioControls.setAlpha((float) valueAnimator.getAnimatedValue()));
        radioControlsAlphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}

            @Override
            public void onAnimationEnd(Animator animator) {
                mRadioControls.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        // Animator to fade in the radio controls for the preset card.
        ObjectAnimator radioCardControlsAlphaAnimator = ObjectAnimator.ofFloat(mRadioCardControls,
                View.ALPHA, 0.f, 1.f);
        radioCardControlsAlphaAnimator.setInterpolator(sInterpolator);
        radioCardControlsAlphaAnimator.setStartDelay(START_FADE_ANIM_DELAY_MS);
        radioCardControlsAlphaAnimator.setDuration(START_FADE_ANIM_DURATION_MS);
        radioCardControlsAlphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {
                mRadioCardControls.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animator) {}

            @Override
            public void onAnimationCancel(Animator animator) {}

            @Override
            public void onAnimationRepeat(Animator animator) {}
        });

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(cornerRadiusAnimator, heightAnimator, widthAnimator, fabAnimator,
                translationYAnimator, radioControlsAlphaAnimator, radioCardControlsAlphaAnimator,
                presetAnimator);
        animatorSet.start();
    }
}
