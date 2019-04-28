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

package com.android.tv.data;

import android.content.Context;

import com.android.tv.R;

public class DisplayMode {

    // The values should be synced with R.arrays.display_mode_label
    public static final int MODE_NORMAL = 0;
    public static final int MODE_FULL = 1;
    public static final int MODE_ZOOM = 2;
    public static final int SIZE_OF_RATIO_TYPES = MODE_ZOOM + 1;

    /**
     * Constant to indicate that any mode is not set yet.
     */
    public static final int MODE_NOT_DEFINED = -1;

    private DisplayMode() { }

    public static String getLabel(int mode, Context context) {
        return context.getResources().getStringArray(R.array.display_mode_labels)[mode];
    }
}
