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

package com.android.tv.menu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.R;

/**
 * A base class to render a card.
 */
public abstract class BaseCardView<T> extends LinearLayout implements ItemListRowView.CardView<T> {
    private static final float SCALE_FACTOR_0F = 0f;
    private static final float SCALE_FACTOR_1F = 1f;

    private ValueAnimator mFocusAnimator;
    private final int mFocusAnimDuration;
    private final float mFocusTranslationZ;
    private final float mVerticalCardMargin;
    private final float mCardCornerRadius;
    private float mFocusAnimatedValue;
    private boolean mExtendViewOnFocus;
    private final float mExtendedCardHeight;
    private final float mTextViewHeight;
    private final float mExtendedTextViewHeight;
    @Nullable
    private TextView mTextView;
    @Nullable
    private TextView mTextViewFocused;
    private final int mCardImageWidth;
    private final float mCardHeight;
    private boolean mSelected;

    private int mTextResId;
    private String mTextString;
    private boolean mTextChanged;

    public BaseCardView(Context context) {
        this(context, null);
    }

    public BaseCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BaseCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setClipToOutline(true);
        mFocusAnimDuration = getResources().getInteger(R.integer.menu_focus_anim_duration);
        mFocusTranslationZ = getResources().getDimension(R.dimen.channel_card_elevation_focused)
                - getResources().getDimension(R.dimen.card_elevation_normal);
        mVerticalCardMargin = 2 * (
                getResources().getDimensionPixelOffset(R.dimen.menu_list_padding_top)
                + getResources().getDimensionPixelOffset(R.dimen.menu_list_margin_top));
        // Ensure the same elevation and focus animation for all subclasses.
        setElevation(getResources().getDimension(R.dimen.card_elevation_normal));
        mCardCornerRadius = getResources().getDimensionPixelSize(R.dimen.channel_card_round_radius);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mCardCornerRadius);
            }
        });
        mCardImageWidth = getResources().getDimensionPixelSize(R.dimen.card_image_layout_width);
        mCardHeight = getResources().getDimensionPixelSize(R.dimen.card_layout_height);
        mExtendedCardHeight = getResources().getDimensionPixelSize(
                R.dimen.card_layout_height_extended);
        mTextViewHeight = getResources().getDimensionPixelSize(R.dimen.card_meta_layout_height);
        mExtendedTextViewHeight = getResources().getDimensionPixelOffset(
                R.dimen.card_meta_layout_height_extended);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTextView = (TextView) findViewById(R.id.card_text);
        mTextViewFocused = (TextView) findViewById(R.id.card_text_focused);
    }

    /**
     * Called when the view is displayed.
     */
    @Override
    public void onBind(T item, boolean selected) {
        setFocusAnimatedValue(selected ? SCALE_FACTOR_1F : SCALE_FACTOR_0F);
    }

    @Override
    public void onRecycled() { }

    @Override
    public void onSelected() {
        mSelected = true;
        if (isAttachedToWindow() && getVisibility() == View.VISIBLE) {
            startFocusAnimation(SCALE_FACTOR_1F);
        } else {
            cancelFocusAnimationIfAny();
            setFocusAnimatedValue(SCALE_FACTOR_1F);
        }
    }

    @Override
    public void onDeselected() {
        mSelected = false;
        if (isAttachedToWindow() && getVisibility() == View.VISIBLE) {
            startFocusAnimation(SCALE_FACTOR_0F);
        } else {
            cancelFocusAnimationIfAny();
            setFocusAnimatedValue(SCALE_FACTOR_0F);
        }
    }

    /**
     * Sets text of this card view.
     */
    public void setText(int resId) {
        if (mTextResId != resId) {
            mTextResId = resId;
            mTextString = null;
            mTextChanged = true;
            if (mTextViewFocused != null) {
                mTextViewFocused.setText(resId);
            }
            if (mTextView != null) {
                mTextView.setText(resId);
            }
            onTextViewUpdated();
        }
    }

    /**
     * Sets text of this card view.
     */
    public void setText(String text) {
        if (!TextUtils.equals(text, mTextString)) {
            mTextString = text;
            mTextResId = 0;
            mTextChanged = true;
            if (mTextViewFocused != null) {
                mTextViewFocused.setText(text);
            }
            if (mTextView != null) {
                mTextView.setText(text);
            }
            onTextViewUpdated();
        }
    }

    private void onTextViewUpdated() {
        if (mTextView != null && mTextViewFocused != null) {
            mTextViewFocused.measure(
                MeasureSpec.makeMeasureSpec(mCardImageWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            mExtendViewOnFocus = mTextViewFocused.getLineCount() > 1;
            if (mExtendViewOnFocus) {
                setTextViewFocusedAlpha(mSelected ? 1f : 0f);
            } else {
                setTextViewFocusedAlpha(1f);
            }
        }
        setFocusAnimatedValue(mSelected ? SCALE_FACTOR_1F : SCALE_FACTOR_0F);
    }

    /**
     * Enables or disables text view of this card view.
     */
    public void setTextViewEnabled(boolean enabled) {
        if (mTextViewFocused != null) {
            mTextViewFocused.setEnabled(enabled);
        }
        if (mTextView != null) {
            mTextView.setEnabled(enabled);
        }
    }

    /**
     * Called when the focus animation started.
     */
    protected void onFocusAnimationStart(boolean selected) {
        if (mExtendViewOnFocus) {
            setTextViewFocusedAlpha(selected ? 1f : 0f);
        }
    }

    /**
     * Called when the focus animation ended.
     */
    protected void onFocusAnimationEnd(boolean selected) {
        // do nothing.
    }

    /**
     * Called when the view is bound, or while focus animation is running with a value
     * between {@code SCALE_FACTOR_0F} and {@code SCALE_FACTOR_1F}.
     */
    protected void onSetFocusAnimatedValue(float animatedValue) {
        float cardViewHeight = (mExtendViewOnFocus && isFocused())
                ? mExtendedCardHeight : mCardHeight;
        float scale = 1f + (mVerticalCardMargin / cardViewHeight) * animatedValue;
        setScaleX(scale);
        setScaleY(scale);
        setTranslationZ(mFocusTranslationZ * animatedValue);
        if (mTextView != null && mTextViewFocused != null) {
            ViewGroup.LayoutParams params = mTextView.getLayoutParams();
            int height = mExtendViewOnFocus ? Math.round(mTextViewHeight
                + (mExtendedTextViewHeight - mTextViewHeight) * animatedValue)
                : (int) mTextViewHeight;
            if (height != params.height) {
                params.height = height;
                setTextViewLayoutParams(params);
            }
            if (mExtendViewOnFocus) {
                setTextViewFocusedAlpha(animatedValue);
            }
        }
    }

    private void setFocusAnimatedValue(float animatedValue) {
        mFocusAnimatedValue = animatedValue;
        onSetFocusAnimatedValue(animatedValue);
    }

    private void startFocusAnimation(final float targetAnimatedValue) {
        cancelFocusAnimationIfAny();
        final boolean selected = targetAnimatedValue == SCALE_FACTOR_1F;
        mFocusAnimator = ValueAnimator.ofFloat(mFocusAnimatedValue, targetAnimatedValue);
        mFocusAnimator.setDuration(mFocusAnimDuration);
        mFocusAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setHasTransientState(true);
                onFocusAnimationStart(selected);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                setHasTransientState(false);
                onFocusAnimationEnd(selected);
            }
        });
        mFocusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setFocusAnimatedValue((Float) animation.getAnimatedValue());
            }
        });
        mFocusAnimator.start();
    }

    private void cancelFocusAnimationIfAny() {
        if (mFocusAnimator != null) {
            mFocusAnimator.cancel();
            mFocusAnimator = null;
        }
    }

    private void setTextViewLayoutParams(ViewGroup.LayoutParams params) {
        mTextViewFocused.setLayoutParams(params);
        mTextView.setLayoutParams(params);
    }

    private void setTextViewFocusedAlpha(float focusedAlpha) {
        mTextViewFocused.setAlpha(focusedAlpha);
        mTextView.setAlpha(1f - focusedAlpha);
    }
}
