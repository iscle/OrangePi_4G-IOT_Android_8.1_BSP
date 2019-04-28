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

import android.graphics.Bitmap;
import android.util.Log;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

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

        float heightScale = 1f;
        if (bm.getHeight() > height) {
            heightScale = (float) height / bm.getHeight();
        }
        float widthScale = 1f;
        if (bm.getWidth() > width) {
            widthScale = (float) width / bm.getWidth();
        }
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

    /**
     * Crops the given bitmap to a centered rectangle of the given dimensions.
     *
     * @param bm the bitmap to crop.
     * @param width the width to crop to.
     * @param height the height to crop to.
     * @return The cropped bitmap, or the original if no cropping was necessary.
     */
    public static Bitmap cropBitmap(Bitmap bm, int width, int height) {
        if (bm == null) {
            return bm;
        }
        if (bm.getHeight() < height || bm.getWidth() < width) {
            if (Log.isLoggable(TAG, Log.INFO)) {
                Log.i(TAG, String.format(
                        "Can't crop bitmap to larger dimensions (%d, %d) -> (%d, %d).",
                        bm.getWidth(), bm.getHeight(), width, height));
            }
            return bm;
        }
        int x = (bm.getWidth() - width) / 2;
        int y =(bm.getHeight() - height) / 2;
        return Bitmap.createBitmap(bm, x, y, width, height);
    }
}
