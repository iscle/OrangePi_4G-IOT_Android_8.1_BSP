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

package com.android.tv.tuner.util;

import android.content.Context;
import android.provider.Settings;

/**
 * Utility class that get information of global settings.
 */
public class GlobalSettingsUtils {
    // Since global surround setting is hided, add the related variable here for checking surround
    // sound setting when the audio is unavailable. Remove this workaround after b/31254857 fixed.
    private static final String ENCODED_SURROUND_OUTPUT = "encoded_surround_output";
    public static final int ENCODED_SURROUND_OUTPUT_NEVER = 1;

    private GlobalSettingsUtils () { }

    public static int getEncodedSurroundOutputSettings(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), ENCODED_SURROUND_OUTPUT, 0);
    }
}
