/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.mail.bitmap;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.android.mail.R;

/**
 * Custom FlipDrawable which has a {@link ContactDrawable} on the front,
 * and a {@link CheckmarkDrawable} on the back.
 */
public class CheckableContactFlipDrawable extends FlipDrawable implements AnimatorUpdateListener {

    private final ContactDrawable mContactDrawable;
    private final CheckmarkDrawable mCheckmarkDrawable;

    private final ValueAnimator mCheckmarkScaleAnimator;
    private final ValueAnimator mCheckmarkAlphaAnimator;

    private static final int POST_FLIP_DURATION_MS = 150;

    private static final float CHECKMARK_SCALE_BEGIN_VALUE = 0.2f;
    private static final float CHECKMARK_ALPHA_BEGIN_VALUE = 0f;

    /** Must be <= 1f since the animation value is used as a percentage. */
    private static final float END_VALUE = 1f;

    public CheckableContactFlipDrawable(final Resources res, final int flipDurationMs) {
        super(new ContactDrawable(res), new CheckmarkDrawable(res), flipDurationMs,
                0 /* preFlipDurationMs */, POST_FLIP_DURATION_MS);

        mContactDrawable = (ContactDrawable) mFront;
        mCheckmarkDrawable = (CheckmarkDrawable) mBack;

        // We will create checkmark animations that are synchronized with the flipping animation.
        // The entire delay + duration of the checkmark animation needs to equal the entire
        // duration of the flip animation (where delay is 0).

        // The checkmark animation is in effect only when the back drawable is being shown.
        // For the flip animation duration    <pre>[_][]|[][_]<post>
        // The checkmark animation will be    |--delay--|-duration-|

        // Need delay to skip the first half of the flip duration.
        final long animationDelay = mPreFlipDurationMs + mFlipDurationMs / 2;
        // Actual duration is the second half of the flip duration.
        final long animationDuration = mFlipDurationMs / 2 + mPostFlipDurationMs;

        mCheckmarkScaleAnimator = ValueAnimator.ofFloat(CHECKMARK_SCALE_BEGIN_VALUE, END_VALUE)
                .setDuration(animationDuration);
        mCheckmarkScaleAnimator.setStartDelay(animationDelay);
        mCheckmarkScaleAnimator.addUpdateListener(this);

        mCheckmarkAlphaAnimator = ValueAnimator.ofFloat(CHECKMARK_ALPHA_BEGIN_VALUE, END_VALUE)
                .setDuration(animationDuration);
        mCheckmarkAlphaAnimator.setStartDelay(animationDelay);
        mCheckmarkAlphaAnimator.addUpdateListener(this);
    }

    @Override
    public void reset(final boolean side) {
        super.reset(side);
        if (mCheckmarkScaleAnimator == null) {
            // Call from super's constructor. Not yet initialized.
            return;
        }
        mCheckmarkScaleAnimator.cancel();
        mCheckmarkAlphaAnimator.cancel();
        mCheckmarkDrawable.setScaleAnimatorValue(side ? CHECKMARK_SCALE_BEGIN_VALUE : END_VALUE);
        mCheckmarkDrawable.setAlphaAnimatorValue(side ? CHECKMARK_ALPHA_BEGIN_VALUE : END_VALUE);
    }

    @Override
    public void flip() {
        super.flip();
        // Keep the checkmark animators in sync with the flip animator.
        if (mCheckmarkScaleAnimator.isStarted()) {
            mCheckmarkScaleAnimator.reverse();
            mCheckmarkAlphaAnimator.reverse();
        } else {
            if (!getSideFlippingTowards() /* front to back */) {
                mCheckmarkScaleAnimator.start();
                mCheckmarkAlphaAnimator.start();
            } else /* back to front */ {
                mCheckmarkScaleAnimator.reverse();
                mCheckmarkAlphaAnimator.reverse();
            }
        }
    }

    public ContactDrawable getContactDrawable() {
        return mContactDrawable;
    }

    @Override
    public void onAnimationUpdate(final ValueAnimator animation) {
        //noinspection ConstantConditions
        final float value = (Float) animation.getAnimatedValue();

        if (animation == mCheckmarkScaleAnimator) {
            mCheckmarkDrawable.setScaleAnimatorValue(value);
        } else if (animation == mCheckmarkAlphaAnimator) {
            mCheckmarkDrawable.setAlphaAnimatorValue(value);
        }
    }

    /**
     * Meant to be used as the with a FlipDrawable. The animator driving this Drawable should be
     * more or less in sync with the containing FlipDrawable's flip animator.
     */
    private static class CheckmarkDrawable extends Drawable {

        private static Bitmap CHECKMARK;
        private static int sBackgroundColor;

        private final Paint mPaint;

        private float mScaleFraction;
        private float mAlphaFraction;

        private static final Matrix sMatrix = new Matrix();

        public CheckmarkDrawable(final Resources res) {
            if (CHECKMARK == null) {
                CHECKMARK = BitmapFactory.decodeResource(res, R.drawable.ic_check_wht_24dp);
                sBackgroundColor = res.getColor(R.color.checkmark_tile_background_color);
            }
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setFilterBitmap(true);
            mPaint.setColor(sBackgroundColor);
        }

        @Override
        public void draw(final Canvas canvas) {
            final Rect bounds = getBounds();
            if (!isVisible() || bounds.isEmpty()) {
                return;
            }

            canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() / 2, mPaint);

            // Scale the checkmark.
            sMatrix.reset();
            sMatrix.setScale(mScaleFraction, mScaleFraction, CHECKMARK.getWidth() / 2,
                    CHECKMARK.getHeight() / 2);
            sMatrix.postTranslate(bounds.centerX() - CHECKMARK.getWidth() / 2,
                    bounds.centerY() - CHECKMARK.getHeight() / 2);

            // Fade the checkmark.
            final int oldAlpha = mPaint.getAlpha();
            // Interpolate the alpha.
            mPaint.setAlpha((int) (oldAlpha * mAlphaFraction));
            canvas.drawBitmap(CHECKMARK, sMatrix, mPaint);
            // Restore the alpha.
            mPaint.setAlpha(oldAlpha);
        }

        @Override
        public void setAlpha(final int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(final ColorFilter cf) {
            mPaint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            // Always a gray background.
            return PixelFormat.OPAQUE;
        }

        /**
         * Set value as a fraction from 0f to 1f.
         */
        public void setScaleAnimatorValue(final float value) {
            final float old = mScaleFraction;
            mScaleFraction = value;
            if (old != mScaleFraction) {
                invalidateSelf();
            }
        }

        /**
         * Set value as a fraction from 0f to 1f.
         */
        public void setAlphaAnimatorValue(final float value) {
            final float old = mAlphaFraction;
            mAlphaFraction = value;
            if (old != mAlphaFraction) {
                invalidateSelf();
            }
        }
    }
}
