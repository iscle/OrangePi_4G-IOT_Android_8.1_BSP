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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;

import com.android.mail.R;

/**
 * A contact drawable with the default avatar as a letter tile.
 */
public class ContactDrawable extends AbstractAvatarDrawable {
    /** Letter tile */
    private ColorPicker mTileColorPicker;

    /** Reusable components to avoid new allocations */
    private static int sTileLetterFontSize;
    private static int sTileFontColor;
    private static Bitmap DEFAULT_AVATAR;
    private static final Paint sPaint = new Paint();
    private static final Rect sRect = new Rect();
    private static final char[] sFirstChar = new char[1];

    public ContactDrawable(final Resources res) {
        super(res);

        if (sTileLetterFontSize == 0) {
            sTileLetterFontSize = res.getDimensionPixelSize(R.dimen.tile_letter_font_size_small);
            sTileFontColor = res.getColor(R.color.letter_tile_font_color);
            DEFAULT_AVATAR = BitmapFactory.decodeResource(res, R.drawable.ic_anonymous_avatar_40dp);

            sPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            sPaint.setTextAlign(Align.CENTER);
            sPaint.setAntiAlias(true);
        }
    }

    /**
     * Sets the {@link ColorPicker} for the background tile used in letter avatars.
     * @param colorPicker
     */
    public void setTileColorPicker(ColorPicker colorPicker) {
        mTileColorPicker = colorPicker;
    }

    /**
     * Returns the color picker for the background tile used in the letter avatars.
     * If none was set, initializes a simple {@link ColorPicker.PaletteColorPicker} first.
     * @return non-null color picker.
     */
    public ColorPicker getTileColorPicker() {
        if (mTileColorPicker == null) {
            mTileColorPicker = new ColorPicker.PaletteColorPicker(mResources);
        }
        return mTileColorPicker;
    }

    @Override
    protected void drawDefaultAvatar(Canvas canvas) {
        // Draw letter tile as default
        drawLetterTile(canvas);
    }

    private void drawLetterTile(final Canvas canvas) {
        if (mContactRequest == null) {
            return;
        }

        final Rect bounds = getBounds();

        // Draw background color.
        final String email = mContactRequest.getEmail();
        // The email should already have been normalized by the ContactRequest.
        sPaint.setColor(getTileColorPicker().pickColor(email));
        sPaint.setAlpha(mBitmapPaint.getAlpha());
        drawCircle(canvas, bounds, sPaint);

        // Draw letter/digit or generic avatar.
        final String displayName = mContactRequest.getDisplayName();
        final char firstChar = displayName.charAt(0);
        if (isEnglishLetterOrDigit(firstChar)) {
            // Draw letter or digit.
            sFirstChar[0] = Character.toUpperCase(firstChar);
            sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
            sPaint.setColor(sTileFontColor);
            canvas.drawText(sFirstChar, 0, 1, bounds.centerX(),
                    bounds.centerY() + sRect.height() / 2, sPaint);
        } else {
            drawBitmap(DEFAULT_AVATAR, DEFAULT_AVATAR.getWidth(), DEFAULT_AVATAR.getHeight(),
                    canvas);
        }
    }

    private static boolean isEnglishLetterOrDigit(final char c) {
        return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z') || ('0' <= c && c <= '9');
    }
}
