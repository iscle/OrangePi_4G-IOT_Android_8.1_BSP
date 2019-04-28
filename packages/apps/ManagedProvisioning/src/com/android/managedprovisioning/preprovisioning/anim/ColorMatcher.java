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

import android.graphics.Color;

public class ColorMatcher {
    private static final int MAX_VALUE = 0xff;
    private static final int BUCKET_SIZE = 32;

    public int findClosestColor(int targetColor) {
        int r = bucketize(Color.red(targetColor));
        int g = bucketize(Color.green(targetColor));
        int b = bucketize(Color.blue(targetColor));

        return Color.argb(MAX_VALUE, r, g, b);
    }

    private int bucketize(int value) {
        int result = (int) Math.round(((double) value / BUCKET_SIZE)) * BUCKET_SIZE;
        return Math.min(MAX_VALUE, result);
    }
}