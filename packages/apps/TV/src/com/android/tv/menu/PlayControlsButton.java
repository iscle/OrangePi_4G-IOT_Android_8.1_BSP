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

import android.R.integer;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.tv.R;

public class PlayControlsButton extends FrameLayout {
    private static final float ALPHA_ENABLED = 1.0f;
    private static final float ALPHA_DISABLED = 0.3f;

    private final ImageView mButton;
    private final ImageView mIcon;
    private final TextView mLabel;
    private final long mFocusAnimationTimeMs;
    private final int mIconColor;
    private int mIconFocusedColor;

    private int mImageResourceId;
    private int mTintColor;

    public PlayControlsButton(Context context) {
        this(context, null);
    }

    public PlayControlsButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayControlsButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PlayControlsButton(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        inflate(context, R.layout.play_controls_button, this);
        mButton = (ImageView) findViewById(R.id.button);
        mIcon = (ImageView) findViewById(R.id.icon);
        mLabel = (TextView) findViewById(R.id.label);
        mFocusAnimationTimeMs = context.getResources().getInteger(integer.config_shortAnimTime);
        mIconColor = context.getResources().getColor(R.color.play_controls_icon_color);
        mIconFocusedColor = mIconColor;
    }

    /**
     * Sets the resource ID of the image to be displayed in the center of this control.
     */
    public void setImageResId(int imageResId) {
        int newTintColor = hasFocus() ? mIconFocusedColor : mIconColor;
        if (mImageResourceId != imageResId) {
            mImageResourceId = imageResId;
            mIcon.setImageResource(imageResId);
            updateTint(newTintColor);
        } else if (newTintColor != mTintColor) {
            updateTint(newTintColor);
        }
    }

    private void updateTint(int tintColor) {
        mTintColor = tintColor;
        // Since on focus changing, icons' color should be switched with animation,
        // as a result, selectors cannot be used to switch colors in this case.
        mIcon.getDrawable().setTint(tintColor);
    }

    /**
     * Sets an action which is to be run when the button is clicked.
     */
    public void setAction(final Runnable clickAction) {
        mButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                clickAction.run();
            }
        });
    }

    /**
     * Sets the icon's color should change to when the button is on focus.
     */
    public void setFocusedIconColor(int color) {
        final ValueAnimator valueAnimator = ValueAnimator.ofArgb(mIconColor, color);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animator) {
                mIcon.getDrawable().setTint((int) animator.getAnimatedValue());
            }
        });
        valueAnimator.setDuration(mFocusAnimationTimeMs);
        mButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    valueAnimator.start();
                } else {
                    valueAnimator.reverse();
                }
            }
        });
        mIconFocusedColor = color;
    }

    public void setLabel(String label) {
        if (TextUtils.isEmpty(label)) {
            mIcon.setVisibility(View.VISIBLE);
            mLabel.setVisibility(View.GONE);
        } else {
            mIcon.setVisibility(View.GONE);
            mLabel.setVisibility(View.VISIBLE);
            if (!TextUtils.equals(mLabel.getText(), label)) {
                mLabel.setText(label);
            }
        }
    }

    public void hideRippleAnimation() {
        mButton.getDrawable().jumpToCurrentState();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mButton.setEnabled(enabled);
        mButton.setFocusable(enabled);
        mIcon.setEnabled(enabled);
        mIcon.setAlpha(enabled ? ALPHA_ENABLED : ALPHA_DISABLED);
        mLabel.setEnabled(enabled);
    }
}
