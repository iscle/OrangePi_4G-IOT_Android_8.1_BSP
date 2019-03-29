/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.graphics.Color;

public class ColorUtils {
    public static void verifyColor(int expected, int observed) {
        verifyColor(expected, observed, 0);
    }

    public static void verifyColor(int expected, int observed, int tolerance) {
        String s = "expected " + Integer.toHexString(expected)
                + ", observed " + Integer.toHexString(observed)
                + ", tolerated channel error " + tolerance;
        assertEquals(s, Color.red(expected), Color.red(observed), tolerance);
        assertEquals(s, Color.green(expected), Color.green(observed), tolerance);
        assertEquals(s, Color.blue(expected), Color.blue(observed), tolerance);
        assertEquals(s, Color.alpha(expected), Color.alpha(observed), tolerance);
    }
}
