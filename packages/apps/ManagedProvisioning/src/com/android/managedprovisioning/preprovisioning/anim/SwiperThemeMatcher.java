/*
 * Copyright 2017, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.anim;

import android.content.Context;
import android.graphics.Color;

/** Class finding closest match for a swiper color **/
public class SwiperThemeMatcher {
    private static final String THEME_PREFIX = "Swiper";
    private static final String STYLE_TAG = "style";

    private final Context mContext;
    private final ColorMatcher mColorMatcher;

    public SwiperThemeMatcher(Context context, ColorMatcher colorMatcher) {
        mContext = context;
        mColorMatcher = colorMatcher;
    }

    /**
     * @param targetColor Target color to find the closest match to
     */
    public int findTheme(int targetColor) {
        int closestColor = mColorMatcher.findClosestColor(targetColor);
        int r = Color.red(closestColor);
        int g = Color.green(closestColor);
        int b = Color.blue(closestColor);

        String styleName = String.format("%s%02x%02x%02x", THEME_PREFIX, r, g, b);
        return mContext.getResources().getIdentifier(styleName, STYLE_TAG,
                mContext.getPackageName());
    }
}