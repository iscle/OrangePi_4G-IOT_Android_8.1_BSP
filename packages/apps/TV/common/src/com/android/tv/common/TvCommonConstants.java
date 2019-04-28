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

import android.media.tv.TvInputInfo;
import android.os.Build;

/**
 * Constants for common use in TV app and tests.
 */
public final class TvCommonConstants {
    /**
     * A constant for the key of the extra data for the app linking intent.
     */
    public static final String EXTRA_APP_LINK_CHANNEL_URI = "app_link_channel_uri";

    /**
     * An intent action to launch setup activity of a TV input. The intent should include
     * TV input ID in the value of {@link EXTRA_INPUT_ID}. Optionally, given the value of
     * {@link EXTRA_ACTIVITY_AFTER_COMPLETION}, the activity will be launched after the setup
     * activity successfully finishes.
     */
    public static final String INTENT_ACTION_INPUT_SETUP =
            "com.android.tv.action.LAUNCH_INPUT_SETUP";

    /**
     * A constant of the key to indicate a TV input ID for the intent action
     * {@link INTENT_ACTION_INPUT_SETUP}.
     *
     * <p>Value type: String
     */
    public static final String EXTRA_INPUT_ID = TvInputInfo.EXTRA_INPUT_ID;

    /**
     * A constant of the key for intent to launch actual TV input setup activity used with
     * {@link INTENT_ACTION_INPUT_SETUP}.
     *
     * <p>Value type: Intent (Parcelable)
     */
    public static final String EXTRA_SETUP_INTENT =
            "com.android.tv.extra.SETUP_INTENT";

    /**
     * A constant of the key to indicate an Activity launch intent for the intent action
     * {@link INTENT_ACTION_INPUT_SETUP}.
     *
     * <p>Value type: Intent (Parcelable)
     */
    public static final String EXTRA_ACTIVITY_AFTER_COMPLETION =
            "com.android.tv.intent.extra.ACTIVITY_AFTER_COMPLETION";

    private TvCommonConstants() {
    }
}
