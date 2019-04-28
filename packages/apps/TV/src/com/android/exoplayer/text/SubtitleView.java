/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.text;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import com.google.android.exoplayer.util.Util;

import java.util.ArrayList;

/**
 * Since this class does not exist in recent version of ExoPlayer and used by
 * {@link com.android.tv.tuner.cc.CaptionWindowLayout}, this class is copied from
 * older version of ExoPlayer.
 * A view for rendering a single caption.
 */
@Deprecated
public class SubtitleView extends View {
    /**
     * Ratio of inner padding to font size.
     */
    private static final float INNER_PADDING_RATIO = 0.125f;

    /**
     * Temporary rectangle used for computing line bounds.
     */
    private final RectF mLineBounds = new RectF();

    // Styled dimensions.
    private final float mCornerRadius;
    private final float mOutlineWidth;
    private final float mShadowRadius;
    private final float mShadowOffset;

    private final TextPaint mTextPaint;
    private final Paint mPaint;

    private CharSequence mText;

    private int mForegroundColor;
    private int mBackgroundColor;
    private int mEdgeColor;
    private int mEdgeType;

    private boolean mHasMeasurements;
    private int mLastMeasuredWidth;
    private StaticLayout mLayout;

    private Alignment mAlignment;
    private final float mSpacingMult;
    private final float mSpacingAdd;
    private int mInnerPaddingX;
    private float mWhiteSpaceWidth;
    private ArrayList<Integer> mPrefixSpaces = new ArrayList<>();

    public SubtitleView(Context context) {
        this(context, null);
    }

    public SubtitleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SubtitleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        int[] viewAttr = {android.R.attr.text, android.R.attr.textSize,
                android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier};
        TypedArray a = context.obtainStyledAttributes(attrs, viewAttr, defStyleAttr, 0);
        CharSequence text = a.getText(0);
        int textSize = a.getDimensionPixelSize(1, 15);
        mSpacingAdd = a.getDimensionPixelSize(2, 0);
        mSpacingMult = a.getFloat(3, 1);
        a.recycle();

        Resources resources = getContext().getResources();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        int twoDpInPx =
                Math.round((2f * displayMetrics.densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
        mCornerRadius = twoDpInPx;
        mOutlineWidth = twoDpInPx;
        mShadowRadius = twoDpInPx;
        mShadowOffset = twoDpInPx;

        mTextPaint = new TextPaint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setSubpixelText(true);

        mAlignment = Alignment.ALIGN_CENTER;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mInnerPaddingX = 0;
        setText(text);
        setTextSize(textSize);
        setStyle(CaptionStyleCompat.DEFAULT);
    }

    @Override
    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        forceUpdate(false);
    }

    /**
     * Sets the text to be displayed by the view.
     *
     * @param text The text to display.
     */
    public void setText(CharSequence text) {
        this.mText = text;
        forceUpdate(true);
    }

    /**
     * Sets the text size in pixels.
     *
     * @param size The text size in pixels.
     */
    public void setTextSize(float size) {
        if (mTextPaint.getTextSize() != size) {
            mTextPaint.setTextSize(size);
            mInnerPaddingX = (int) (size * INNER_PADDING_RATIO + 0.5f);
            mWhiteSpaceWidth -= mInnerPaddingX * 2;
            forceUpdate(true);
        }
    }

    /**
     * Sets the text alignment.
     *
     * @param textAlignment The text alignment.
     */
    public void setTextAlignment(Alignment textAlignment) {
        mAlignment = textAlignment;
    }

    /**
     * Configures the view according to the given style.
     *
     * @param style A style for the view.
     */
    public void setStyle(CaptionStyleCompat style) {
        mForegroundColor = style.foregroundColor;
        mBackgroundColor = style.backgroundColor;
        mEdgeType = style.edgeType;
        mEdgeColor = style.edgeColor;
        setTypeface(style.typeface);
        super.setBackgroundColor(style.windowColor);
        forceUpdate(true);
    }

    public void setPrefixSpaces(ArrayList<Integer> prefixSpaces) {
        mPrefixSpaces = prefixSpaces;
    }

    public void setWhiteSpaceWidth(float whiteSpaceWidth) {
        mWhiteSpaceWidth = whiteSpaceWidth;
    }

    private void setTypeface(Typeface typeface) {
        if (mTextPaint.getTypeface() != typeface) {
            mTextPaint.setTypeface(typeface);
            forceUpdate(true);
        }
    }

    private void forceUpdate(boolean needsLayout) {
        if (needsLayout) {
            mHasMeasurements = false;
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthSpec = MeasureSpec.getSize(widthMeasureSpec);

        if (computeMeasurements(widthSpec)) {
            final StaticLayout layout = this.mLayout;
            final int paddingX = getPaddingLeft() + getPaddingRight() + mInnerPaddingX * 2;
            final int height = layout.getHeight() + getPaddingTop() + getPaddingBottom();
            int width = 0;
            int lineCount = layout.getLineCount();
            for (int i = 0; i < lineCount; i++) {
                width = Math.max((int) Math.ceil(layout.getLineWidth(i)), width);
            }
            width += paddingX;
            setMeasuredDimension(width, height);
        } else if (Util.SDK_INT >= 11) {
            setTooSmallMeasureDimensionV11();
        } else {
            setMeasuredDimension(0, 0);
        }
    }

    @TargetApi(11)
    private void setTooSmallMeasureDimensionV11() {
        setMeasuredDimension(MEASURED_STATE_TOO_SMALL, MEASURED_STATE_TOO_SMALL);
    }

    @Override
    public void onLayout(boolean changed, int l, int t, int r, int b) {
        final int width = r - l;
        computeMeasurements(width);
    }

    private boolean computeMeasurements(int maxWidth) {
        if (mHasMeasurements && maxWidth == mLastMeasuredWidth) {
            return true;
        }

        // Account for padding.
        final int paddingX = getPaddingLeft() + getPaddingRight() + mInnerPaddingX * 2;
        maxWidth -= paddingX;
        if (maxWidth <= 0) {
            return false;
        }

        mHasMeasurements = true;
        mLastMeasuredWidth = maxWidth;
        mLayout = new StaticLayout(mText, mTextPaint, maxWidth, mAlignment,
                mSpacingMult, mSpacingAdd, true);
        return true;
    }

    @Override
    protected void onDraw(Canvas c) {
        final StaticLayout layout = this.mLayout;
        if (layout == null) {
            return;
        }

        final int saveCount = c.save();
        final int innerPaddingX = this.mInnerPaddingX;
        c.translate(getPaddingLeft() + innerPaddingX, getPaddingTop());

        final int lineCount = layout.getLineCount();
        final Paint textPaint = this.mTextPaint;
        final Paint paint = this.mPaint;
        final RectF bounds = mLineBounds;

        if (Color.alpha(mBackgroundColor) > 0) {
            final float cornerRadius = this.mCornerRadius;
            float previousBottom = layout.getLineTop(0);

            paint.setColor(mBackgroundColor);
            paint.setStyle(Style.FILL);

            for (int i = 0; i < lineCount; i++) {
                float spacesPadding = 0.0f;
                if (i < mPrefixSpaces.size()) {
                    spacesPadding += mPrefixSpaces.get(i) * mWhiteSpaceWidth;
                }
                bounds.left = layout.getLineLeft(i) - innerPaddingX + spacesPadding;
                bounds.right = layout.getLineRight(i) + innerPaddingX;
                bounds.top = previousBottom;
                bounds.bottom = layout.getLineBottom(i);
                previousBottom = bounds.bottom;

                c.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
            }
        }

        if (mEdgeType == CaptionStyleCompat.EDGE_TYPE_OUTLINE) {
            textPaint.setStrokeJoin(Join.ROUND);
            textPaint.setStrokeWidth(mOutlineWidth);
            textPaint.setColor(mEdgeColor);
            textPaint.setStyle(Style.FILL_AND_STROKE);
            layout.draw(c);
        } else if (mEdgeType == CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW) {
            textPaint.setShadowLayer(mShadowRadius, mShadowOffset, mShadowOffset, mEdgeColor);
        } else if (mEdgeType == CaptionStyleCompat.EDGE_TYPE_RAISED
                || mEdgeType == CaptionStyleCompat.EDGE_TYPE_DEPRESSED) {
            boolean raised = mEdgeType == CaptionStyleCompat.EDGE_TYPE_RAISED;
            int colorUp = raised ? Color.WHITE : mEdgeColor;
            int colorDown = raised ? mEdgeColor : Color.WHITE;
            float offset = mShadowRadius / 2f;
            textPaint.setColor(mForegroundColor);
            textPaint.setStyle(Style.FILL);
            textPaint.setShadowLayer(mShadowRadius, -offset, -offset, colorUp);
            layout.draw(c);
            textPaint.setShadowLayer(mShadowRadius, offset, offset, colorDown);
        }

        textPaint.setColor(mForegroundColor);
        textPaint.setStyle(Style.FILL);
        layout.draw(c);
        textPaint.setShadowLayer(0, 0, 0, 0);
        c.restoreToCount(saveCount);
    }

}
