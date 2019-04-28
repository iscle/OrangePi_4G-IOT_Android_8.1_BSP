/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.mail.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.text.BidiFormatter;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

import com.android.mail.ui.FolderDisplayer;

/**
 * A replacement span to use when displaying folders in conversation view. Prevents a folder name
 * from wrapping mid-name, and ellipsizes very long folder names that can't fit on a single line.
 */
public class FolderSpan extends ReplacementSpan {
    private final TextPaint mWorkPaint = new TextPaint();
    private final String mName;
    private final int mFgColor;
    private final int mBgColor;
    private final FolderDisplayer.FolderDrawableResources mRes;
    private final BidiFormatter mFormatter;
    private final FolderSpanDimensions mDim;

    public FolderSpan(String name, int fgColor, int bgColor,
            FolderDisplayer.FolderDrawableResources res, BidiFormatter formatter,
            FolderSpanDimensions dim) {
        super();

        mName = name;
        mFgColor = fgColor;
        mBgColor = bgColor;
        mRes = res;
        mFormatter = formatter;
        mDim = dim;
    }

    private int getWidth(Paint p) {
        p.setTextSize(mRes.folderFontSize);
        return Math.min((int) p.measureText(mName) + 2 * mRes.folderHorizontalPadding,
                mDim.getMaxChipWidth());
    }

    private int getHeight(Paint p) {
        p.setTextSize(mRes.folderFontSize);
        final Paint.FontMetricsInt fm = p.getFontMetricsInt();
        return fm.bottom - fm.top;
    }

    @Override
    public int getSize(Paint p, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        mWorkPaint.set(p);
        return getWidth(mWorkPaint);
    }

    @Override
    public void draw(Canvas canvas, CharSequence charSequence, int start, int end, float x, int top,
            int baseline, int bottom, Paint paint) {
        mWorkPaint.set(paint);
        mWorkPaint.setTextSize(mRes.folderFontSize);
        final int width = getWidth(mWorkPaint);
        final int height = getHeight(mWorkPaint);
        String name = mName;
        if (width == mDim.getMaxChipWidth()) {
            name = TextUtils.ellipsize(mName, mWorkPaint, width - 2 * mRes.folderHorizontalPadding,
                    TextUtils.TruncateAt.MIDDLE).toString();
        }
        FolderDisplayer.drawFolder(canvas, x, baseline - height, width, height, name, mFgColor,
                mBgColor, mRes, mFormatter, mWorkPaint);
    }

    public static interface FolderSpanDimensions {
        int getMaxChipWidth();
    }
}
