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

package com.android.car.cluster.sample;

import static android.graphics.Color.blue;
import static android.graphics.Color.green;
import static android.graphics.Color.red;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Utility functions to work with bitmaps.
 */
public class BitmapUtils {

    // Non-transparent color to crop images using Xfermode.
    private static final int OPAQUE_COLOR = 0xff424242;

    /**
     * Scales a bitmap while preserving the proportions such that both dimensions are the smallest
     * values possible that are equal to or larger than the given dimensions.
     *
     * This function can be a few times as expensive as Bitmap.createScaledBitmap with
     * filtering when downscaling, but it produces much nicer results.
     *
     * @param bm The bitmap to scale.
     * @param width The desired width.
     * @param height The desired height.
     * @return The scaled bitmap, or the original bitmap if scaling was not necessary.
     */
    public static Bitmap scaleBitmap(Bitmap bm, int width, int height) {
        if (bm == null || (bm.getHeight() == height && bm.getWidth() == width)) {
            return bm;
        }

        float heightScale = (float) height / bm.getHeight();
        float widthScale = (float) width / bm.getWidth();

        float scale = heightScale > widthScale ? heightScale : widthScale;
        int scaleWidth = (int) Math.ceil(bm.getWidth() * scale);
        int scaleHeight = (int) Math.ceil(bm.getHeight() * scale);

        Bitmap scaledBm = bm;
        // If you try to scale an image down too much in one go, you can end up with discontinuous
        // interpolation. Therefore, if necessary, we scale the image to twice the desired size
        // and do a second scaling to the desired size, which smooths jaggedness from the first go.
        if (scale < .5f) {
            scaledBm = Bitmap.createScaledBitmap(scaledBm, scaleWidth * 2, scaleHeight * 2, true);
        }

        if (scale != 1f) {
            Bitmap newScaledBitmap = Bitmap
                    .createScaledBitmap(scaledBm, scaleWidth, scaleHeight, true);
            if (scaledBm != bm) {
                scaledBm.recycle();
            }
            scaledBm = newScaledBitmap;
        }
        return scaledBm;
    }

    public static Bitmap squareCropBitmap(Bitmap bitmap) {
        if (bitmap.getWidth() == bitmap.getHeight()) {
            return bitmap;
        }

        int size = Math.min(bitmap.getWidth(), bitmap.getHeight());

        Bitmap output = Bitmap.createBitmap(size,
                size, Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        int x = size < bitmap.getWidth() ? (bitmap.getWidth() - size ) / 2 : 0;
        int y = size < bitmap.getHeight() ? (bitmap.getHeight() - size ) / 2 : 0;
        Rect srcRect = new Rect(x, y, x + size, y + size);
        Rect dstRect = new Rect(0, 0, output.getWidth(), output.getHeight());

        canvas.drawBitmap(bitmap, srcRect, dstRect, null);
        return output;
    }

    public static Bitmap circleCropBitmap(Bitmap bitmap) {
        bitmap = squareCropBitmap(bitmap);
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(OPAQUE_COLOR);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        return output;
    }

    public static Bitmap generateNavManeuverIcon(int size, int bgColor, Bitmap maneuver) {
        Bitmap bm = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        drawCircle(canvas, bgColor);

        canvas.drawBitmap(maneuver,
                (bm.getWidth() - maneuver.getWidth()) / 2,
                (bm.getHeight() - maneuver.getHeight()) / 2,
                new Paint(Paint.ANTI_ALIAS_FLAG));
        return bm;
    }

    public static Bitmap generateMediaIcon(int size, int bgColor, int fgColor) {
        final float goldenRatio = 1.618f;

        Bitmap bm = Bitmap.createBitmap(size, size, Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        drawCircle(canvas, bgColor);

        // Calculate column parameters relative to the size.
        int bottom = (int) (size / goldenRatio);
        int columnWidth = size / 17;
        int columnSpace = columnWidth / 2;
        int allColumnsWidth = columnWidth * 3 + columnSpace * 2;
        int left = size / 2 - (allColumnsWidth) / 2;

        Paint columnPaint = new Paint();
        columnPaint.setColor(fgColor);
        columnPaint.setAntiAlias(true);
        canvas.drawRect(new RectF(left, bottom - columnWidth * 2, left + columnWidth, bottom),
                columnPaint);

        left += columnWidth + columnSpace;
        canvas.drawRect(new RectF(left, bottom - columnWidth * 4, left + columnWidth, bottom),
                columnPaint);

        left += columnWidth + columnSpace;
        canvas.drawRect(new RectF(left, bottom - columnWidth * 3, left + columnWidth, bottom),
                columnPaint);

        return bm;
    }

    private static Canvas drawCircle(Canvas canvas, int bgColor) {
        int size = canvas.getWidth();
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setColor(bgColor);
        canvas.drawCircle(size / 2, size / 2, (size / 2) - 1, p);
        return canvas;
    }

    public static int[] getBaseColors(int color) {
        return new int[] {red(color), green(color), blue(color)};
    }

    /**
     * Scales colors for given bitmap. This could be used as color filter for example.
     */
    public static Bitmap scaleBitmapColors(Bitmap original, int minColor, int maxColor) {
        int[] pixels = new int[original.getWidth() * original.getHeight()];
        original.getPixels(pixels, 0, original.getWidth(), 0, 0,
                original.getWidth(), original.getHeight());

        int[] min = getBaseColors(minColor);
        int[] max = getBaseColors(maxColor);

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int[] colors = new int[] {red(pixel), green(pixel), blue(pixel)};

            for (int j = 0; j < 3; j++) {
                colors[j] = (int)((colors[j] / 255.0) * (max[j] - min[j]) + min[j]);
            }

            pixels[i] = Color.rgb(colors[0], colors[1], colors[2]);
        }

        Bitmap bmp = Bitmap.createBitmap(original.getWidth(), original.getHeight(), Config.ARGB_8888);
        bmp.setPixels(pixels, 0, original.getWidth(), 0, 0, original.getWidth(),
                original.getHeight());
        return bmp;
    }
}
