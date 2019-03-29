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

package android.inputmethodservice.cts.common.test;

import java.util.Arrays;

/**
 * Utility class for preparing "adb shell" command.
 */
public final class ShellCommandUtils {

    // This is utility class, can't instantiate.
    private ShellCommandUtils() {}

    // Copied from android.content.pm.PackageManager#FEATURE_INPUT_METHODS.
    public static final String FEATURE_INPUT_METHODS = "android.software.input_methods";

    /** Command to check whether system has specified {@code featureName} feature. */
    public static String hasFeature(final String featureName) {
        return "cmd package has-feature " + featureName;
    }

    private static final String SETTING_DEFAULT_IME = "secure default_input_method";

    /** Command to get ID of current IME. */
    public static String getCurrentIme() {
        return "settings get " + SETTING_DEFAULT_IME;
    }

    /** Command to set current IME to {@code imeId}. */
    public static String setCurrentIme(final String imeId) {
        return "settings put " + SETTING_DEFAULT_IME + " " + imeId;
    }

    /** Command to enable IME of {@code imeId}. */
    public static String enableIme(final String imeId) {
        return "ime enable " + imeId;
    }

    /** Command to disable IME of {@code imeId}. */
    public static String disableIme(final String imeId) {
        return "ime disable " + imeId;
    }

    /** Command to delete all records of IME event provider. */
    public static String deleteContent(final String contentUri) {
        return "content delete --uri " + contentUri;
    }

    /**
     * Command to send broadcast {@code Intent}.
     *
     * @param action action of intent.
     * @param targetComponent target of intent.
     * @param extras extra of intent, must be specified as triplet of option flag, key, and value.
     * @return shell command to send broadcast intent.
     */
    public static String broadcastIntent(final String action, final String targetComponent,
            final String... extras) {
        if (extras.length % 3 != 0) {
            throw new IllegalArgumentException(
                    "extras must be triplets: " + Arrays.toString(extras));
        }
        final StringBuilder sb = new StringBuilder("am broadcast -a ")
                .append(action);
        for (int index = 0; index < extras.length; index += 3) {
            final String optionFlag = extras[index];
            final String extraKey = extras[index + 1];
            final String extraValue = extras[index + 2];
            sb.append(" ").append(optionFlag)
                    .append(" ").append(extraKey)
                    .append(" ").append(extraValue);
        }
        return sb.append(" ").append(targetComponent).toString();
    }
}
