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

package com.android.mail.photomanager;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.bitmap.ColorPicker;
import com.android.mail.ui.ImageCanvas.Dimensions;
import com.android.mail.utils.BitmapUtil;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;

/**
 * LetterTileProvider is an implementation of the DefaultImageProvider. When no
 * matching contact photo is found, and there is a supplied displayName or email
 * address whose first letter corresponds to an English alphabet letter (or
 * number), this method creates a bitmap with the letter in the center of a
 * tile. If there is no English alphabet character (or digit), it creates a
 * bitmap with the default contact avatar.
 */
public class LetterTileProvider {
    private static final String TAG = LogTag.getLogTag();
    private final Bitmap mDefaultBitmap;
    private final Bitmap[] mBitmapBackgroundCache;
    private final Bitmap[] mDefaultBitmapCache;
    private final Typeface mSansSerifLight;
    private final Rect mBounds;
    private final int mTileLetterFontSize;
    private final int mTileLetterFontSizeSmall;
    private final int mTileFontColor;
    private final TextPaint mPaint = new TextPaint();
    private final Canvas mCanvas = new Canvas();
    private final char[] mFirstChar = new char[1];

    private static final int POSSIBLE_BITMAP_SIZES = 3;
    private final ColorPicker mTileColorPicker;

    public LetterTileProvider(Resources res) {
        this(res, new ColorPicker.PaletteColorPicker(res));
    }

    public LetterTileProvider(Resources res, ColorPicker colorPicker) {
        mTileLetterFontSize = res.getDimensionPixelSize(R.dimen.tile_letter_font_size_small);
        mTileLetterFontSizeSmall = res.getDimensionPixelSize(R.dimen.tile_letter_font_size_tiny);
        mTileFontColor = res.getColor(R.color.letter_tile_font_color);
        mSansSerifLight = Typeface.create("sans-serif-light", Typeface.NORMAL);
        mBounds = new Rect();
        mPaint.setTypeface(mSansSerifLight);
        mPaint.setColor(mTileFontColor);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setAntiAlias(true);
        mBitmapBackgroundCache = new Bitmap[POSSIBLE_BITMAP_SIZES];

        mDefaultBitmap = BitmapFactory.decodeResource(res, R.drawable.ic_anonymous_avatar_40dp);
        mDefaultBitmapCache = new Bitmap[POSSIBLE_BITMAP_SIZES];

        mTileColorPicker = colorPicker;
    }

    public Bitmap getLetterTile(final Dimensions dimensions, final String displayName,
            final String address) {
        final String display = !TextUtils.isEmpty(displayName) ? displayName : address;
        final char firstChar = display.charAt(0);

        // get an empty bitmap
        final Bitmap bitmap = getBitmap(dimensions, false /* getDefault */);
        if (bitmap == null) {
            LogUtils.w(TAG, "LetterTileProvider width(%d) or height(%d) is 0 for name %s and "
                    + "address %s.", dimensions.width, dimensions.height, displayName, address);
            return null;
        }

        final Canvas c = mCanvas;
        c.setBitmap(bitmap);
        c.drawColor(mTileColorPicker.pickColor(address));

        // If its a valid English alphabet letter,
        // draw the letter on top of the color
        if (isEnglishLetterOrDigit(firstChar)) {
            mFirstChar[0] = Character.toUpperCase(firstChar);
            mPaint.setTextSize(
                    dimensions.fontSize > 0 ? dimensions.fontSize : getFontSize(dimensions.scale));
            mPaint.getTextBounds(mFirstChar, 0, 1, mBounds);
            c.drawText(mFirstChar, 0, 1, 0 + dimensions.width / 2,
                    0 + dimensions.height / 2 + (mBounds.bottom - mBounds.top) / 2, mPaint);
        } else { // draw the generic icon on top
            c.drawBitmap(getBitmap(dimensions, true /* getDefault */), 0, 0, null);
        }

        return bitmap;
    }

    private static boolean isEnglishLetterOrDigit(char c) {
        return ('A' <= c && c <= 'Z')
                || ('a' <= c && c <= 'z')
                || ('0' <= c && c <= '9');
    }

    private Bitmap getBitmap(final Dimensions d, boolean getDefault) {
        if (d.width <= 0 || d.height <= 0) {
            LogUtils.w(TAG,
                    "LetterTileProvider width(%d) or height(%d) is 0.", d.width, d.height);
            return null;
        }
        final int pos;
        float scale = d.scale;
        if (scale == Dimensions.SCALE_ONE) {
            pos = 0;
        } else if (scale == Dimensions.SCALE_HALF) {
            pos = 1;
        } else {
            pos = 2;
        }

        final Bitmap[] cache = (getDefault) ? mDefaultBitmapCache : mBitmapBackgroundCache;

        Bitmap bitmap = cache[pos];
        // ensure bitmap is suitable for the desired w/h
        // (two-pane uses two different sets of dimensions depending on pane width)
        if (bitmap == null || bitmap.getWidth() != d.width || bitmap.getHeight() != d.height) {
            // create and place the bitmap
            if (getDefault) {
                bitmap = BitmapUtil.centerCrop(mDefaultBitmap, d.width, d.height);
            } else {
                bitmap = Bitmap.createBitmap(d.width, d.height, Bitmap.Config.ARGB_8888);
            }
            cache[pos] = bitmap;
        }
        return bitmap;
    }

    private int getFontSize(float scale)  {
        if (scale == Dimensions.SCALE_ONE) {
            return mTileLetterFontSize;
        } else {
            return mTileLetterFontSizeSmall;
        }
    }
}
