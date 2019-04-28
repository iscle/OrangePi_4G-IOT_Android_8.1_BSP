/*
 * Copyright 2015 The Android Open Source Project
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

package com.android.tv.common;

import android.content.Intent;
import android.media.tv.TvInputInfo;

/**
 * Util class for common use in TV app and inputs.
 */
public final class TvCommonUtils {
    private static Boolean sRunningInTest;

    private TvCommonUtils() { }

    /**
     * Returns an intent to start the setup activity for the TV input using {@link
     * TvCommonConstants#INTENT_ACTION_INPUT_SETUP}.
     */
    public static Intent createSetupIntent(Intent originalSetupIntent, String inputId) {
        if (originalSetupIntent == null) {
            return null;
        }
        Intent setupIntent = new Intent(originalSetupIntent);
        if (!TvCommonConstants.INTENT_ACTION_INPUT_SETUP.equals(originalSetupIntent.getAction())) {
            Intent intentContainer = new Intent(TvCommonConstants.INTENT_ACTION_INPUT_SETUP);
            intentContainer.putExtra(TvCommonConstants.EXTRA_SETUP_INTENT, originalSetupIntent);
            intentContainer.putExtra(TvCommonConstants.EXTRA_INPUT_ID, inputId);
            setupIntent = intentContainer;
        }
        return setupIntent;
    }

    /**
     * Returns an intent to start the setup activity for this TV input using {@link
     * TvCommonConstants#INTENT_ACTION_INPUT_SETUP}.
     */
    public static Intent createSetupIntent(TvInputInfo input) {
        return createSetupIntent(input.createSetupIntent(), input.getId());
    }

    /**
     * Checks if this application is running in tests.
     *
     * <p>{@link android.app.ActivityManager#isRunningInTestHarness} doesn't return {@code true} for
     * the usual devices even the application is running in tests. We need to figure it out by
     * checking whether the class in tv-tests-common module can be loaded or not.
     */
    public static synchronized boolean isRunningInTest() {
        if (sRunningInTest == null) {
            try {
                Class.forName("com.android.tv.testing.Utils");
                sRunningInTest = true;
            } catch (ClassNotFoundException e) {
                sRunningInTest = false;
            }
        }
        return sRunningInTest;
    }
}
