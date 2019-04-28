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
package com.android.mail.bitmap;

import android.content.res.Resources;
import android.content.res.TypedArray;

import com.android.mail.R;

public interface ColorPicker {
    /**
     * Returns the color to use for the given email address.
     * This method should return the same output for the same input.
     * @param email The normalized email address.
     * @return The color value in the format {@code 0xAARRGGBB}.
     */
    public int pickColor(final String email);

    /**
     * A simple implementation of a {@link ColorPicker}.
     */
    public class PaletteColorPicker implements ColorPicker {
        /**
         * The palette of colors, inflated from {@code R.array.letter_tile_colors}.
         */
        private static TypedArray sColors;

        /**
         * Cached value of {@code sColors.length()}.
         */
        private static int sColorCount;

        /**
         * Default color returned if the one chosen from {@code R.array.letter_tile_colors} is
         * a {@link android.content.res.ColorStateList}.
         */
        private static int sDefaultColor;

        public PaletteColorPicker(Resources res) {
            if (sColors == null) {
                sColors = res.obtainTypedArray(R.array.letter_tile_colors);
                sColorCount = sColors.length();
                sDefaultColor = res.getColor(R.color.letter_tile_default_color);
            }
        }

        @Override
        public int pickColor(final String email) {
            final int color = Math.abs(email.hashCode()) % sColorCount;
            return sColors.getColor(color, sDefaultColor);
        }
    }
}
