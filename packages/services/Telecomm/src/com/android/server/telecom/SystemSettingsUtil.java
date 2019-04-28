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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.provider.Settings;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Accesses the Global System settings for more control during testing.
 */
@VisibleForTesting
public class SystemSettingsUtil {

    public boolean isTheaterModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.THEATER_MODE_ON,
                0) == 1;
    }

    public boolean canVibrateWhenRinging(Context context) {
        return Settings.System.getInt(context.getContentResolver(),
                Settings.System.VIBRATE_WHEN_RINGING, 0) != 0;
    }
}
