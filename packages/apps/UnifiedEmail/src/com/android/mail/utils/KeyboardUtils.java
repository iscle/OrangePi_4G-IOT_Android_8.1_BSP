/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mail.utils;

import android.view.KeyEvent;

/**
 * Utility class for keyboard navigation.
 */
public class KeyboardUtils {
    /**
     * Determine if the provided keycode is toward the start direction of the normal layout.
     * This is used for keyboard navigation since the layouts are flipped for RTL.
     */
    public static boolean isKeycodeDirectionStart(int keyCode, boolean isRtl) {
        return (!isRtl && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) ||
                (isRtl && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    /**
     * Determine if the provided keycode is toward the end direction of the normal layout.
     * This is used for keyboard navigation since the layouts are flipped for RTL.
     */
    public static boolean isKeycodeDirectionEnd(int keyCode, boolean isRtl) {
        return (isRtl && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) ||
                (!isRtl && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
    }
}
