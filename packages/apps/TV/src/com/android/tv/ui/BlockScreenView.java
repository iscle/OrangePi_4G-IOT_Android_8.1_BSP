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

package com.android.tv.ui;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.ui.TunableTvView.BlockScreenType;

public class BlockScreenView extends FrameLayout {
    private View mContainerView;
    private View mImageContainer;
    private ImageView mNormalLockIconView;
    private ImageView mShrunkenLockIconView;
    private View mSpace;
    private TextView mBlockingInfoTextView;
    private ImageView mBackgroundImageView;

    private final int mSpacingNormal;
    private final int mSpacingShrunken;

    // Animator used to fade out the whole block screen.
    private Animator mFadeOut;

    // Animators used to fade in/out the block screen icon and info text.
    private Animator mInfoFadeIn;
    private Animator mInfoFadeOut;

    public BlockScreenView(Context context) {
        this(context, null, 0);
    }

    public BlockScreenView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BlockScreenView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mSpacingNormal = getResources().getDimensionPixelOffset(
                R.dimen.tvview_block_vertical_spacing);
        mSpacingShrunken = getResources().getDimensionPixelOffset(
                R.dimen.shrunken_tvview_block_vertical_spacing);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContainerView = findViewById(R.id.block_screen_container);
        mImageContainer = findViewById(R.id.image_container);
        mNormalLockIconView = (ImageView) findViewById(R.id.block_screen_icon);
        mShrunkenLockIconView = (ImageView) findViewById(R.id.block_screen_shrunken_icon);
        mSpace = findViewById(R.id.space);
        mBlockingInfoTextView = (TextView) findViewById(R.id.block_screen_text);
        mBackgroundImageView = (ImageView) findViewById(R.id.background_image);
        mFadeOut = AnimatorInflater.loadAnimator(getContext(),
                R.animator.tvview_block_screen_fade_out);
        mFadeOut.setTarget(this);
        mFadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(GONE);
                setBackgroundImage(null);
                setAlpha(1.0f);
            }
        });
        mInfoFadeIn = AnimatorInflater.loadAnimator(getContext(),
                R.animator.tvview_block_screen_fade_in);
        mInfoFadeIn.setTarget(mContainerView);
        mInfoFadeOut = AnimatorInflater.loadAnimator(getContext(),
                R.animator.tvview_block_screen_fade_out);
        mInfoFadeOut.setTarget(mContainerView);
        mInfoFadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mContainerView.setVisibility(GONE);
            }
        });
    }

    /**
     * Sets the normal image.
     */
    public void setIconImage(int resId) {
        mNormalLockIconView.setImageResource(resId);
        updateSpaceVisibility();
    }

    /**
     * Sets the scale type of the normal image.
     */
    public void setIconScaleType(ScaleType scaleType) {
        mNormalLockIconView.setScaleType(scaleType);
        updateSpaceVisibility();
    }

    /**
     * Show or hide the image of this view.
     */
    public void setIconVisibility(boolean visible) {
        mImageContainer.setVisibility(visible ? VISIBLE : GONE);
        updateSpaceVisibility();
    }

    /**
     * Sets the text message.
     */
    public void setInfoText(int resId) {
        mBlockingInfoTextView.setText(resId);
        updateSpaceVisibility();
    }

    /**
     * Sets the text message.
     */
    public void setInfoText(String text) {
        mBlockingInfoTextView.setText(text);
        updateSpaceVisibility();
    }

    /**
     * Sets the background image should be displayed in the block screen view. Passes {@code null}
     * to remove the currently displayed background image.
     */
    public void setBackgroundImage(Drawable backgroundImage) {
        mBackgroundImageView.setVisibility(backgroundImage == null ? GONE : VISIBLE);
        mBackgroundImageView.setImageDrawable(backgroundImage);
    }

    private void updateSpaceVisibility() {
        if (isImageViewVisible() && isTextViewVisible(mBlockingInfoTextView)) {
            mSpace.setVisibility(VISIBLE);
        } else {
            mSpace.setVisibility(GONE);
        }
    }

    private boolean isImageViewVisible() {
        return mImageContainer.getVisibility() == VISIBLE
                && (isImageViewVisible(mNormalLockIconView)
                        || isImageViewVisible(mShrunkenLockIconView));
    }

    private static boolean isImageViewVisible(ImageView imageView) {
        return imageView.getVisibility() != GONE && imageView.getDrawable() != null;
    }

    private static boolean isTextViewVisible(TextView textView) {
        return textView.getVisibility() != GONE && !TextUtils.isEmpty(textView.getText());
    }

    /**
     * Changes the spacing between the image view and the text view according to the
     * {@code blockScreenType}.
     */
    public void setSpacing(@BlockScreenType int blockScreenType) {
        mSpace.getLayoutParams().height =
                blockScreenType == TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW
                ? mSpacingShrunken : mSpacingNormal;
        requestLayout();
    }

    /**
     * Changes the view layout according to the {@code blockScreenType}.
     */
    public void onBlockStatusChanged(@BlockScreenType int blockScreenType, boolean withAnimation) {
        if (!withAnimation) {
            switch (blockScreenType) {
                case TunableTvView.BLOCK_SCREEN_TYPE_NO_UI:
                    mContainerView.setVisibility(GONE);
                    break;
                case TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW:
                    mNormalLockIconView.setVisibility(GONE);
                    mShrunkenLockIconView.setVisibility(VISIBLE);
                    mContainerView.setVisibility(VISIBLE);
                    mContainerView.setAlpha(1.0f);
                    break;
                case TunableTvView.BLOCK_SCREEN_TYPE_NORMAL:
                    mNormalLockIconView.setVisibility(VISIBLE);
                    mShrunkenLockIconView.setVisibility(GONE);
                    mContainerView.setVisibility(VISIBLE);
                    mContainerView.setAlpha(1.0f);
                    break;
            }
        } else {
            switch (blockScreenType) {
                case TunableTvView.BLOCK_SCREEN_TYPE_NO_UI:
                    if (mContainerView.getVisibility() == VISIBLE) {
                        mInfoFadeOut.start();
                    }
                    break;
                case TunableTvView.BLOCK_SCREEN_TYPE_SHRUNKEN_TV_VIEW:
                    mNormalLockIconView.setVisibility(GONE);
                    mShrunkenLockIconView.setVisibility(VISIBLE);
                    if (mContainerView.getVisibility() == GONE) {
                        mContainerView.setVisibility(VISIBLE);
                        mInfoFadeIn.start();
                    }
                    break;
                case TunableTvView.BLOCK_SCREEN_TYPE_NORMAL:
                    mNormalLockIconView.setVisibility(VISIBLE);
                    mShrunkenLockIconView.setVisibility(GONE);
                    if (mContainerView.getVisibility() == GONE) {
                        mContainerView.setVisibility(VISIBLE);
                        mInfoFadeIn.start();
                    }
                    break;
            }
        }
        updateSpaceVisibility();
    }

    /**
     * Adds a listener to the fade-in animation of info text and icons of the block screen.
     */
    public void addInfoFadeInAnimationListener(AnimatorListener listener) {
        mInfoFadeIn.addListener(listener);
    }

    /**
     * Fades out the block screen.
     */
    public void fadeOut() {
        if (getVisibility() == VISIBLE && !mFadeOut.isStarted()) {
            mFadeOut.start();
        }
    }

    /**
     * Ends the currently running animations.
     */
    public void endAnimations() {
        if (mFadeOut != null && mFadeOut.isRunning()) {
            mFadeOut.end();
        }
        if (mInfoFadeIn != null && mInfoFadeIn.isRunning()) {
            mInfoFadeIn.end();
        }
        if (mInfoFadeOut != null && mInfoFadeOut.isRunning()) {
            mInfoFadeOut.end();
        }
    }
}
