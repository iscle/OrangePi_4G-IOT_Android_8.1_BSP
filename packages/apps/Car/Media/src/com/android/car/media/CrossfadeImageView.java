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
package com.android.car.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A view where updating the image will show certain animations. Current animations include fading
 * in and scaling down the new image.
 */
public class CrossfadeImageView extends FrameLayout {
    // ColorFilters can't currently be modified (b/17262092) so creating a saturation fade with
    // color filters would normally require creating a ton of small objects. We get around this by
    // caching color filters and limit the saturation to increments of 0.1.
    // 0-0.09 -> [0]
    // 0.10-0.19 -> [1]
    // ...
    // 1.0 -> [10]
    private final ColorFilter[] mSaturationColorFilters = new ColorFilter[11];
    private final ColorMatrix mColorMatrix = new ColorMatrix();
    private final ImageView mImageView1;
    private final ImageView mImageView2;

    private ImageView mActiveImageView;
    private ImageView mInactiveImageView;

    private Bitmap mCurrentBitmap = null;
    private Integer mCurrentColor = null;
    private Animation mImageInAnimation;
    private Animation mImageOutAnimation;

    public CrossfadeImageView(Context context) {
        this(context, null);
    }

    public CrossfadeImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CrossfadeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CrossfadeImageView(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        LayoutParams lp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ImageView imageViewBackground = new ImageView(context, attrs, defStyleAttr, defStyleRes);
        imageViewBackground.setLayoutParams(lp);
        imageViewBackground.setBackgroundColor(Color.BLACK);
        addView(imageViewBackground);
        mImageView1 = new ImageView(context, attrs, defStyleAttr, defStyleRes);
        mImageView1.setLayoutParams(lp);
        addView(mImageView1);
        mImageView2 = new ImageView(context, attrs, defStyleAttr, defStyleRes);
        mImageView2.setLayoutParams(lp);
        addView(mImageView2);

        mActiveImageView = mImageView1;
        mInactiveImageView = mImageView2;

        mImageInAnimation = AnimationUtils.loadAnimation(context, R.anim.image_in);
        mImageInAnimation.setInterpolator(new DecelerateInterpolator());
        mImageOutAnimation = AnimationUtils.loadAnimation(context, R.anim.image_out);
    }

    public void setImageBitmap(Bitmap bitmap, boolean showAnimation) {
        if (bitmap == null) {
            return;
        }

        if (mCurrentBitmap != null && bitmap.sameAs(mCurrentBitmap)) {
            return;
        }

        mCurrentBitmap = bitmap;
        mCurrentColor = null;
        mInactiveImageView.setImageBitmap(bitmap);
        if (showAnimation) {
            animateViews();
        } else {
            mActiveImageView.setImageBitmap(bitmap);
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        if (mCurrentColor != null && mCurrentColor == color) {
            return;
        }
        mInactiveImageView.setImageBitmap(null);
        mCurrentBitmap = null;
        mCurrentColor = color;
        mInactiveImageView.setBackgroundColor(color);
        animateViews();
    }

    public void setSaturation(float saturation) {
        int i = (int) ((saturation * 100) / 10);
        ColorFilter cf = mSaturationColorFilters[i];
        if (cf == null) {
            mColorMatrix.setSaturation((10 * i) / 100f);
            cf = new ColorMatrixColorFilter(mColorMatrix);
            mSaturationColorFilters[i] = cf;
        }

        mImageView1.setColorFilter(cf);
        mImageView2.setColorFilter(cf);
    }

    private final Animation.AnimationListener mAnimationListener =
            new Animation.AnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            if (mInactiveImageView != null) {
                mInactiveImageView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onAnimationStart(Animation animation) { }

        @Override
        public void onAnimationRepeat(Animation animation) { }
    };

    private void animateViews() {
        mInactiveImageView.setVisibility(View.VISIBLE);
        mInactiveImageView.startAnimation(mImageInAnimation);
        mInactiveImageView.bringToFront();
        mActiveImageView.startAnimation(mImageOutAnimation);
        mImageOutAnimation.setAnimationListener(mAnimationListener);
        if (mActiveImageView == mImageView1) {
            mActiveImageView = mImageView2;
            mInactiveImageView = mImageView1;
        } else {
            mActiveImageView = mImageView1;
            mInactiveImageView = mImageView2;
        }
    }
}
