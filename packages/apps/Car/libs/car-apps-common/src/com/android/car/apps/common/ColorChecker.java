/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

/**
 * @hide
 */
public class ColorChecker {
    private static final String TAG = "ColorChecker";
    private static final double MIN_CONTRAST_RATIO = 4.5;
    /**
     * Non-critical information doesn't have to meet as stringent contrast requirements.
     */
    private static final double MIN_NON_CRITICAL_CONTRAST_RATIO = 1.5;

    /**
     * Calls {@link #getTintColor(int, int...)} with:
     *     {@link R.color#car_tint_light} and
     *     {@link R.color#car_tint_dark}
     */
    public static int getTintColor(Context context, int backgroundColor) {
        int lightTintColor = context.getResources().getColor(R.color.car_tint_light);
        int darkTintColor = context.getResources().getColor(R.color.car_tint_dark);

        return getTintColor(backgroundColor, lightTintColor, darkTintColor);
    }

    /**
     * Calls {@link #getNonCriticalTintColor(int, int...)} with:
     *     {@link R.color#car_tint_light} and
     *     {@link R.color#car_tint_dark}
     */
    public static int getNonCriticalTintColor(Context context, int backgroundColor) {
        int lightTintColor = context.getResources().getColor(R.color.car_tint_light);
        int darkTintColor = context.getResources().getColor(R.color.car_tint_dark);

        return getNonCriticalTintColor(backgroundColor, lightTintColor, darkTintColor);
    }

    /**
     * Calls {@link #getTintColor(int, int...)} with {@link #MIN_CONTRAST_RATIO}.
     */
    public static int getTintColor(int backgroundColor, int... tintColors) {
        return getTintColor(MIN_CONTRAST_RATIO, backgroundColor, tintColors);
    }

    /**
     * Calls {@link #getTintColor(int, int...)} with {@link #MIN_NON_CRITICAL_CONTRAST_RATIO}.
     */
    public static int getNonCriticalTintColor(int backgroundColor, int... tintColors) {
        return getTintColor(MIN_NON_CRITICAL_CONTRAST_RATIO, backgroundColor, tintColors);
    }

    /**
     *
     * Determines what color to tint icons given the background color that they sit on.
     *
     * @param minAllowedContrastRatio The minimum contrast ratio
     * @param bgColor The background color that the icons sit on.
     * @param tintColors A list of potential colors to tint the icons with.
     * @return The color that the icons should be tinted. Will be the first tinted color that
     *         meets the requirements. If none of the tint colors meet the minimum requirements,
     *         either black or white will be returned, whichever has a higher contrast.
     */
    public static int getTintColor(double minAllowedContrastRatio, int bgColor, int... tintColors) {
        for (int tc : tintColors) {
            double contrastRatio = getContrastRatio(bgColor, tc);
            if (contrastRatio >= minAllowedContrastRatio) {
                return tc;
            }
        }
        double blackContrastRatio = getContrastRatio(bgColor, Color.BLACK);
        double whiteContrastRatio = getContrastRatio(bgColor, Color.WHITE);
        if (whiteContrastRatio >= blackContrastRatio) {
            Log.w(TAG, "Tint color does not meet contrast requirements. Using white.");
            return Color.WHITE;
        } else {
            Log.w(TAG, "Tint color does not meet contrast requirements. Using black.");
            return Color.BLACK;
        }
    }

    public static double getContrastRatio(int color1, int color2) {
        return getContrastRatio(getLuminance(color1), getLuminance(color2));
    }

    public static double getContrastRatio(double luminance1, double luminance2) {
        return (Math.max(luminance1, luminance2) + 0.05) /
                (Math.min(luminance1, luminance2) + 0.05);
    }

    /**
     * Calculates the luminance of a color as specified by:
     *     http://www.w3.org/TR/WCAG20-TECHS/G17.html
     *
     * @param color The color to calculate the luminance of.
     * @return The luminance.
     */
    public static double getLuminance(int color) {
        // Values are in sRGB
        double r = convert8BitToLuminanceComponent(Color.red(color));
        double g = convert8BitToLuminanceComponent(Color.green(color));
        double b = convert8BitToLuminanceComponent(Color.blue(color));
        return r * 0.2126 + g * 0.7152 + b * 0.0722;
    }

    /**
     * Converts am 8 bit color component (0-255) to the luminance component as specified by:
     *     http://www.w3.org/TR/WCAG20-TECHS/G17.html
     */
    private static double convert8BitToLuminanceComponent(double component) {
        component /= 255.0;
        if (component <= 0.03928) {
            return component / 12.92;
        } else {
            return Math.pow(((component + 0.055) / 1.055), 2.4);
        }
    }
}
