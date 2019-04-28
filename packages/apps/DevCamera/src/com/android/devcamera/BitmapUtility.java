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
package com.android.devcamera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;

import java.nio.ByteBuffer;

/**
 * Some Bitmap utility functions.
 */
public class BitmapUtility {

    public static Bitmap bitmapFromJpeg(byte[] data) {
        // 32K buffer.
        byte[] decodeBuffer = new byte[32 * 1024]; // 32K buffer.

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 16; // 3264 / 16 = 204.
        opts.inTempStorage = decodeBuffer;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, opts);

        return rotatedBitmap(b);
    }

    public static Bitmap bitmapFromYuvImage(Image img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer buf0 = img.getPlanes()[0].getBuffer();
        int len = buf0.capacity();
        int[] colors = new int[len];
        int alpha = 255 << 24;
        int green;
        for (int i = 0; i < len; i++) {
            green = ((int) buf0.get(i)) & 255;
            colors[i] = green << 16 | green << 8 | green | alpha;
        }
        Bitmap b = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888);

        return rotatedBitmap(b);
    }

    /**
     * Returns parameter bitmap rotated 90 degrees
     */
    private static Bitmap rotatedBitmap(Bitmap b) {
        Matrix mat = new Matrix();
        mat.postRotate(90);
        Bitmap b2 = Bitmap.createBitmap(b, 0, 0,b.getWidth(),b.getHeight(), mat, true);
        return b2;
    }

}
