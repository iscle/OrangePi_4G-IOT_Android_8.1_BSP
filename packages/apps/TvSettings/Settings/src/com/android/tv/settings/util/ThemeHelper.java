/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.util;

import android.content.Intent;

import com.android.tv.settings.R;

public class ThemeHelper {
    public static final String EXTRA_FROM_SETUP_WIZARD = "firstRun";

    /**
     * Returns true if the given intent is from the setup wizard.
     */
    public static boolean fromSetupWizard(Intent intent) {
        return intent.getBooleanExtra(EXTRA_FROM_SETUP_WIZARD, false);
    }

    /**
     * Checks for the setup wizard extra and returns the appropriate theme.
     */
    public static int getThemeResource(Intent intent) {
        return getThemeResource(fromSetupWizard(intent));
    }

    /**
     * Returns the appropriate setup theme.
     */
    public static int getThemeResource(boolean transparent) {
        if (transparent) {
            return R.style.Theme_Leanback_FormWizard_Transparent;
        } else {
            return R.style.Theme_Leanback_FormWizard_Solid;
        }
    }

    /**
     * Can't instantiate
     */
    private ThemeHelper() {
    }
}
