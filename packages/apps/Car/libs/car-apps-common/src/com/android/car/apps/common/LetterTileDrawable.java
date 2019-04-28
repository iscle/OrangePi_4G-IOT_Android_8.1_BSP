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
package com.android.car.apps.common;

import android.annotation.Nullable;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

/**
 * A drawable that encapsulates all the functionality needed to display a letter tile to
 * represent a contact image.
 */
public class LetterTileDrawable extends Drawable {
    /** Letter tile */
    private static int[] sColors;
    private static int sDefaultColor;
    private static int sTileFontColor;
    private static float sLetterToTileRatio;
    private static Drawable sDefaultPersonAvatar;
    private static Drawable sDefaultBusinessAvatar;
    private static Drawable sDefaultVoicemailAvatar;

    /** Reusable components to avoid new allocations */
    private static final Paint sPaint = new Paint();
    private static final Rect sRect = new Rect();
    private static final char[] sFirstChar = new char[1];

    /** Contact type constants */
    public static final int TYPE_PERSON = 1;
    public static final int TYPE_BUSINESS = 2;
    public static final int TYPE_VOICEMAIL = 3;
    public static final int TYPE_DEFAULT = TYPE_PERSON;

    private final Paint mPaint;

    @Nullable private String mDisplayName;
    private int mColor;
    private int mContactType = TYPE_DEFAULT;
    private float mScale = 1.0f;
    private float mOffset = 0.0f;
    private boolean mIsCircle = false;

    // TODO(rogerxue): the use pattern for this class is always:
    // create LTD, setContactDetails(), setIsCircular(true). merge them into ctor.
    public LetterTileDrawable(final Resources res) {
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);
        setScale(0.7f);

        if (sColors == null) {
            sDefaultColor = res.getColor(R.color.letter_tile_default_color);
            TypedArray ta = res.obtainTypedArray(R.array.letter_tile_colors);
            if (ta.length() == 0) {
                // TODO(dnotario). Looks like robolectric shadow doesn't currently support
                // obtainTypedArray and always returns length 0 array, which will make some code
                // below that does a division by length of sColors choke. Workaround by creating
                // an array of length 1. A more proper fix tracked by b/26518438.
                sColors = new int[] { sDefaultColor };

            } else {
                sColors = new int[ta.length()];
                for (int i = ta.length() - 1; i >= 0; i--) {
                    sColors[i] = ta.getColor(i, sDefaultColor);
                }
                ta.recycle();
            }

            sTileFontColor = res.getColor(R.color.letter_tile_font_color);
            sLetterToTileRatio = res.getFraction(R.fraction.letter_to_tile_ratio, 1, 1);
            // TODO: get images for business and voicemail
            sDefaultPersonAvatar = res.getDrawable(R.drawable.ic_person, null /* theme */);
            sDefaultBusinessAvatar = res.getDrawable(R.drawable.ic_person, null /* theme */);
            sDefaultVoicemailAvatar = res.getDrawable(R.drawable.ic_person, null /* theme */);
            sPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            sPaint.setTextAlign(Align.CENTER);
            sPaint.setAntiAlias(true);
        }
    }

    @Override
    public void draw(final Canvas canvas) {
        final Rect bounds = getBounds();
        if (!isVisible() || bounds.isEmpty()) {
            return;
        }
        // Draw letter tile.
        drawLetterTile(canvas);
    }

    /**
     * Draw the drawable onto the canvas at the current bounds taking into account the current
     * scale.
     */
    private void drawDrawableOnCanvas(final Drawable drawable, final Canvas canvas) {
        // The drawable should be drawn in the middle of the canvas without changing its width to
        // height ratio.
        final Rect destRect = copyBounds();

        // Crop the destination bounds into a square, scaled and offset as appropriate
        final int halfLength = (int) (mScale * Math.min(destRect.width(), destRect.height()) / 2);

        destRect.set(destRect.centerX() - halfLength,
                (int) (destRect.centerY() - halfLength + mOffset * destRect.height()),
                destRect.centerX() + halfLength,
                (int) (destRect.centerY() + halfLength + mOffset * destRect.height()));

        drawable.setAlpha(mPaint.getAlpha());
        drawable.setColorFilter(sTileFontColor, PorterDuff.Mode.SRC_IN);
        drawable.setBounds(destRect);
        drawable.draw(canvas);
    }

    private void drawLetterTile(final Canvas canvas) {
        // Draw background color.
        sPaint.setColor(mColor);

        sPaint.setAlpha(mPaint.getAlpha());
        final Rect bounds = getBounds();
        final int minDimension = Math.min(bounds.width(), bounds.height());

        if (mIsCircle) {
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), minDimension / 2, sPaint);
        } else {
            canvas.drawRect(bounds, sPaint);
        }

        // Draw letter/digit only if the first character is an english letter
        if (!TextUtils.isEmpty(mDisplayName) && isEnglishLetter(mDisplayName.charAt(0))) {
            // Draw letter or digit.
            sFirstChar[0] = Character.toUpperCase(mDisplayName.charAt(0));

            // Scale text by canvas bounds and user selected scaling factor
            sPaint.setTextSize(mScale * sLetterToTileRatio * minDimension);
            //sPaint.setTextSize(sTileLetterFontSize);
            sPaint.getTextBounds(sFirstChar, 0, 1, sRect);
            sPaint.setColor(sTileFontColor);

            // Draw the letter in the canvas, vertically shifted up or down by the user-defined
            // offset
            canvas.drawText(sFirstChar, 0, 1, bounds.centerX(),
                    bounds.centerY() + mOffset * bounds.height() + sRect.height() / 2,
                    sPaint);
        } else {
            // Draw the default image if there is no letter/digit to be drawn
            final Drawable drawable = getDrawablepForContactType(mContactType);
            drawDrawableOnCanvas(drawable, canvas);
        }
    }

    public int getColor() {
        return mColor;
    }

    /**
     * Returns a deterministic color based on the provided contact identifier string.
     */
    private int pickColor(final String identifier) {
        if (TextUtils.isEmpty(identifier) || mContactType == TYPE_VOICEMAIL) {
            return sDefaultColor;
        }
        // String.hashCode() implementation is not supposed to change across java versions, so
        // this should guarantee the same email address always maps to the same color.
        // The email should already have been normalized by the ContactRequest.
        final int color = Math.abs(identifier.hashCode()) % sColors.length;
        return sColors[color];
    }

    private static Drawable getDrawablepForContactType(int contactType) {
        switch (contactType) {
            case TYPE_PERSON:
                return sDefaultPersonAvatar;
            case TYPE_BUSINESS:
                return sDefaultBusinessAvatar;
            case TYPE_VOICEMAIL:
                return sDefaultVoicemailAvatar;
            default:
                return sDefaultPersonAvatar;
        }
    }

    private static boolean isEnglishLetter(final char c) {
        return ('A' <= c && c <= 'Z') || ('a' <= c && c <= 'z');
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
        return android.graphics.PixelFormat.OPAQUE;
    }

    /**
     * Scale the drawn letter tile to a ratio of its default size
     *
     * @param scale The ratio the letter tile should be scaled to as a percentage of its default
     * size, from a scale of 0 to 2.0f. The default is 1.0f.
     */
    public void setScale(float scale) {
        mScale = scale;
    }

    /**
     * Assigns the vertical offset of the position of the letter tile to the ContactDrawable
     *
     * @param offset The provided offset must be within the range of -0.5f to 0.5f.
     * If set to -0.5f, the letter will be shifted upwards by 0.5 times the height of the canvas
     * it is being drawn on, which means it will be drawn with the center of the letter starting
     * at the top edge of the canvas.
     * If set to 0.5f, the letter will be shifted downwards by 0.5 times the height of the canvas
     * it is being drawn on, which means it will be drawn with the center of the letter starting
     * at the bottom edge of the canvas.
     * The default is 0.0f.
     */
    public void setOffset(float offset) {
        mOffset = offset;
    }

    public void setContactDetails(@Nullable String displayName, String identifier) {
        mDisplayName = displayName;
        mColor = pickColor(identifier);
    }

    public void setContactType(int contactType) {
        mContactType = contactType;
    }

    public void setIsCircular(boolean isCircle) {
        mIsCircle = isCircle;
    }

    /**
     * Convert the drawable to a bitmap.
     * @param size The target size of the bitmap.
     * @return A bitmap representation of the drawable.
     */
    public Bitmap toBitmap(int size) {
        Bitmap largeIcon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(largeIcon);
        Rect bounds = getBounds();
        setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        draw(canvas);
        setBounds(bounds);
        return largeIcon;
    }
}