/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.VectorDrawable;
import android.support.annotation.Nullable;

/**
 * Utility class for manipulating Bitmaps.
 */
public class BitmapUtils {
    private BitmapUtils() {}

    /**
     * Returns a {@link Bitmap} from a {@link VectorDrawable}.
     * {@link android.graphics.BitmapFactory#decodeResource(Resources, int)} cannot be used to
     * retrieve a bitmap from a VectorDrawable, so this method works around that.
     */
    @Nullable
    public static Bitmap getBitmap(VectorDrawable vectorDrawable) {
        if (vectorDrawable == null) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        vectorDrawable.draw(canvas);
        return bitmap;
    }
}
