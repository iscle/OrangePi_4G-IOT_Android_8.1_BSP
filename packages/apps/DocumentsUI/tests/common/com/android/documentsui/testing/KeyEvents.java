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
 * limitations under the License.
 */

package com.android.documentsui.testing;

import android.os.SystemClock;
import android.view.KeyEvent;

public class KeyEvents {

    private KeyEvents() {}

    public static KeyEvent createTestEvent(int action, int keyCode, int meta) {
        long time = SystemClock.uptimeMillis();
        return new KeyEvent(
                time,
                time,
                action,
                keyCode,
                0,
                meta);
    }

    public static KeyEvent createLeftCtrlKey(int action) {
        int meta = (action == KeyEvent.ACTION_UP)
                ? 0
                : KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_LEFT_ON | KeyEvent.META_META_ON;

        return createTestEvent(action, KeyEvent.KEYCODE_CTRL_LEFT, meta);
    }

    public static KeyEvent createRightCtrlKey(int action) {
        int meta = (action == KeyEvent.ACTION_UP)
                ? 0
                : KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_RIGHT_ON | KeyEvent.META_META_ON;

        return createTestEvent(action, KeyEvent.KEYCODE_CTRL_RIGHT, meta);
    }
}
