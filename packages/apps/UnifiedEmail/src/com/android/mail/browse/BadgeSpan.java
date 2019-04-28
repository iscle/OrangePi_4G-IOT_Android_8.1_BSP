/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.browse;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.RectF;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;

/**
 * A replacement span to use when displaying a badge in a conversation list item.
 * A badge will be some piece of text with a colored background and rounded
 * corners.
 */
public class BadgeSpan extends ReplacementSpan {

    public interface BadgeSpanDimensions {
        /**
         * Returns the padding value that corresponds to one side of the
         * horizontal padding surrounding the text inside the background color.
         */
        int getHorizontalPadding();

        /**
         * Returns the radius to use for the rounded corners on the background rect.
         */
        float getRoundedCornerRadius();
    }

    private TextPaint mWorkPaint = new TextPaint();
    /**
     * A reference to the enclosing Spanned object to collect other CharacterStyle spans and take
     * them into account when drawing.
     */
    private final Spanned mSpanned;
    private final BadgeSpanDimensions mDims;

    public BadgeSpan(Spanned spanned, BadgeSpanDimensions dims) {
        mSpanned = spanned;
        mDims = dims;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, FontMetricsInt fm) {
        if (fm != null) {
            paint.getFontMetricsInt(fm);
            // The magic set of measurements to vertically center text within a colored region!
            fm.top = fm.ascent;
        }
        return measureWidth(paint, text, start, end);
    }

    private int measureWidth(Paint paint, CharSequence text, int start, int end) {
        return (int) paint.measureText(text, start, end) + 2 * mDims.getHorizontalPadding();
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
            int y, int bottom, Paint paint) {

        mWorkPaint.set(paint);

        // take into account the foreground/background color spans when painting
        final CharacterStyle[] otherSpans = mSpanned.getSpans(start, end, CharacterStyle.class);
        for (CharacterStyle otherSpan : otherSpans) {
            otherSpan.updateDrawState(mWorkPaint);
        }

        // paint a background if present
        if (mWorkPaint.bgColor != 0) {
            final int prevColor = mWorkPaint.getColor();
            final Paint.Style prevStyle = mWorkPaint.getStyle();

            mWorkPaint.setColor(mWorkPaint.bgColor);
            mWorkPaint.setStyle(Paint.Style.FILL);

            final int bgWidth = measureWidth(mWorkPaint, text, start, end);
            final RectF rect = new RectF(x, top, x + bgWidth, bottom);
            final float radius = mDims.getRoundedCornerRadius();
            canvas.drawRoundRect(rect, radius, radius, mWorkPaint);

            mWorkPaint.setColor(prevColor);
            mWorkPaint.setStyle(prevStyle);
        }

        canvas.drawText(text, start, end, x + mDims.getHorizontalPadding(), y, mWorkPaint);
    }
}
