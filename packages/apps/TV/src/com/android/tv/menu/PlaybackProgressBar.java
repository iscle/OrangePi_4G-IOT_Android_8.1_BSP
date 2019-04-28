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

package com.android.tv.menu;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.tv.R;

/**
 * A progress bar control which has two progresses which start in the middle of the control.
 */
public class PlaybackProgressBar extends View {
    private final LayerDrawable mProgressDrawable;
    private final Drawable mPrimaryDrawable;
    private final Drawable mSecondaryDrawable;
    private long mMax = 100;
    private long mProgressStart = 0;
    private long mProgressEnd = 0;
    private long mProgress = 0;

    public PlaybackProgressBar(Context context) {
        this(context, null);
    }

    public PlaybackProgressBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlaybackProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PlaybackProgressBar(Context context, AttributeSet attrs, int defStyleAttr,
                               int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.PlaybackProgressBar, defStyleAttr, defStyleRes);
        mProgressDrawable =
                (LayerDrawable) a.getDrawable(R.styleable.PlaybackProgressBar_progressDrawable);
        mPrimaryDrawable = mProgressDrawable.findDrawableByLayerId(android.R.id.progress);
        mSecondaryDrawable =
                mProgressDrawable.findDrawableByLayerId(android.R.id.secondaryProgress);
        a.recycle();
        refreshProgress();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int saveCount = canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        mProgressDrawable.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        refreshProgress();
    }

    public void setMax(long max) {
        if (max < 0) {
            max = 0;
        }
        if (max != mMax) {
            mMax = max;
            if (mProgressStart > max) {
                mProgressStart = max;
            }
            if (mProgressEnd > max) {
                mProgressEnd = max;
            }
            if (mProgress > max) {
                mProgress = max;
            }
            refreshProgress();
        }
    }

    /**
     * Sets the start and end position of the progress.
     */
    public void setProgressRange(long start, long end) {
        start = constrain(start, 0, mMax);
        end = constrain(end, start, mMax);
        mProgress = constrain(mProgress, start, end);
        if (start != mProgressStart || end != mProgressEnd) {
            mProgressStart = start;
            mProgressEnd = end;
            setProgressLevels();
        }
    }

    /**
     * Sets the progress position.
     */
    public void setProgress(long progress) {
        progress = constrain(progress, mProgressStart, mProgressEnd);
        if (progress != mProgress) {
            mProgress = progress;
            setProgressLevels();
        }
    }

    private long constrain(long value, long min, long max) {
        return Math.min(Math.max(value, min), max);
    }

    private void refreshProgress() {
        int width = getWidth() - getPaddingStart() - getPaddingEnd();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        mProgressDrawable.setBounds(0, 0, width, height);
        setProgressLevels();
    }

    private void setProgressLevels() {
        boolean progressUpdated = setProgressBound(mPrimaryDrawable, mProgressStart, mProgress);
        progressUpdated |= setProgressBound(mSecondaryDrawable, mProgress, mProgressEnd);
        if (progressUpdated) {
            postInvalidate();
        }
    }

    private boolean setProgressBound(Drawable drawable, long start, long end) {
        Rect oldBounds = drawable.getBounds();
        if (mMax == 0) {
            if (!isEqualRect(oldBounds, 0, 0, 0, 0)) {
                drawable.setBounds(0, 0, 0, 0);
                return true;
            }
            return false;
        }
        int width = mProgressDrawable.getBounds().width();
        int height = mProgressDrawable.getBounds().height();
        int left = (int) (width * start / mMax);
        int right = (int) (width * end / mMax);
        if (!isEqualRect(oldBounds, left, 0, right, height)) {
            drawable.setBounds(left, 0, right, height);
            return true;
        }
        return false;
    }

    private boolean isEqualRect(Rect rect, int left, int top, int right, int bottom) {
        return rect.left == left && rect.top == top && rect.right == right && rect.bottom == bottom;
    }
}
