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
 * limitations under the License.
 */
package com.android.car.apps.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

/**
 * Custom drawable that can be used as the background for fabs.
 *
 * When not focused or pressed, the fab will be a solid circle of the color specified with
 * {@link #setFabColor(int)}. When it is pressed or focused, the fab will grow or shrink
 * and it will gain a stroke that has the color specified with {@link #setStrokeColor(int)}.
 *
 * {@link #FabDrawable(android.content.Context)} provides a quick way to use fab drawable using
 * default values for size and animation values provided for consistency.
 *
 * {@link #FabDrawable(int, int, int)} can also be used for added customization.
 * @hide
 */
public class FabDrawable extends Drawable {
    private final int mFabGrowth;
    private final int mStrokeWidth;
    private final Paint mFabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ValueAnimator mStrokeAnimator;

    private boolean mStrokeAnimatorIsReversing;
    private int mFabRadius;
    private int mStrokeRadius;
    private Outline mOutline;

    /**
     * Default constructor to provide consistent fab values across uses.
     */
    public FabDrawable(Context context) {
        this(context.getResources().getDimensionPixelSize(R.dimen.car_fab_focused_growth),
                context.getResources().getDimensionPixelSize(R.dimen.car_fab_focused_stroke_width),
                context.getResources().getInteger(R.integer.car_fab_animation_duration));
    }

    /**
     * Custom constructor allows extra customization of the fab's behavior.
     *
     * @param fabGrowth The amount that the fab should change by when it is focused in pixels.
     * @param strokeWidth The width of the stroke when the fab is focused in pixels.
     * @param duration The animation duration for the growth of the fab and stroke.
     */
    public FabDrawable(int fabGrowth, int strokeWidth, int duration) {
        if (fabGrowth < 0) {
            throw new IllegalArgumentException("Fab growth must be >= 0.");
        } else if (fabGrowth > strokeWidth) {
            throw new IllegalArgumentException("Fab growth must be <= strokeWidth.");
        } else if (strokeWidth < 0) {
            throw new IllegalArgumentException("Stroke width must be >= 0.");
        }
        mFabGrowth = fabGrowth;
        mStrokeWidth = strokeWidth;
        mStrokeAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
        mStrokeAnimator.setInterpolator(new DecelerateInterpolator());
        mStrokeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                updateRadius();
            }
        });
    }

    /**
     * @param color The primary color of the fab. It will be the entire fab color when not selected
     *              or pressed and will be the color of the interior circle when selected
     *              or pressed.
     */
    public void setFabColor(int color) {
        mFabPaint.setColor(color);
    }

    /**
     * @param color The color of the stroke on the fab that appears when the fab is selected
     *              or pressed.
     */
    public void setStrokeColor(int color) {
        mStrokePaint.setColor(color);
    }

    /**
     * Default implementation of {@link #setFabAndStrokeColor(int, float)} with valueMultiplier
     * set to 0.9.
     */
    public void setFabAndStrokeColor(int color) {
        setFabAndStrokeColor(color, 0.9f);
    }

    /**
     * @param color The primary color of the fab.
     * @param valueMultiplier The hsv value multiplier that will be set as the stroke color.
     */
    public void setFabAndStrokeColor(int color, float valueMultiplier) {
        setFabColor(color);
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= valueMultiplier;
        setStrokeColor(Color.HSVToColor(hsv));
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean superChanged = super.onStateChange(stateSet);

        boolean focused = false;
        boolean pressed = false;

        for (int state : stateSet) {
            if (state == android.R.attr.state_focused) {
                focused = true;
            } else if (state == android.R.attr.state_pressed) {
                pressed = true;
            }
        }

        if ((focused || pressed) && mStrokeAnimatorIsReversing) {
            mStrokeAnimator.start();
            mStrokeAnimatorIsReversing = false;
        } else if (!(focused || pressed) && !mStrokeAnimatorIsReversing) {
            mStrokeAnimator.reverse();
            mStrokeAnimatorIsReversing = true;
        }

        return superChanged || focused;
    }

    @Override
    public void draw(Canvas canvas) {
        int cx = canvas.getWidth() / 2;
        int cy = canvas.getHeight() / 2;

        canvas.drawCircle(cx, cy, mStrokeRadius, mStrokePaint);
        canvas.drawCircle(cx, cy, mFabRadius, mFabPaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        updateRadius();
    }

    @Override
    public void setAlpha(int alpha) {
        mFabPaint.setAlpha(alpha);
        mStrokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mFabPaint.setColorFilter(colorFilter);
        mStrokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    @Override
    public void getOutline(Outline outline) {
        mOutline = outline;
        updateOutline();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    private void updateRadius() {
        int normalRadius = Math.min(getBounds().width(), getBounds().height()) / 2 - mStrokeWidth;
        float fraction = mStrokeAnimator.getAnimatedFraction();
        mStrokeRadius = (int) (normalRadius + (mStrokeWidth * fraction));
        mFabRadius = (int) (normalRadius + (mFabGrowth * fraction));
        updateOutline();
        invalidateSelf();
    }

    private void updateOutline() {
        int cx = getBounds().width() / 2;
        int cy = getBounds().height() / 2;
        if (mOutline != null) {
            mOutline.setRoundRect(
                    cx - mStrokeRadius,
                    cy - mStrokeRadius,
                    cx + mStrokeRadius,
                    cy + mStrokeRadius,
                    mStrokeRadius);
        }
    }
}
