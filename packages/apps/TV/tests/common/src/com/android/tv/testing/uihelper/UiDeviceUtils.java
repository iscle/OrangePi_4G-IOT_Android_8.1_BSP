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
package com.android.tv.testing.uihelper;

import static junit.framework.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Build;
import android.os.SystemClock;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * Static utility methods for {@link UiDevice}.
 */
public final class UiDeviceUtils {

    public static void pressDpad(UiDevice uiDevice, Direction direction) {
        switch (direction) {
            case UP:
                uiDevice.pressDPadUp();
                break;
            case DOWN:
                uiDevice.pressDPadDown();
                break;
            case LEFT:
                uiDevice.pressDPadLeft();
                break;
            case RIGHT:
                uiDevice.pressDPadRight();
                break;
            default:
                throw new IllegalArgumentException(direction.toString());
        }
    }


    public static void pressKeys(UiDevice uiDevice, int... keyCodes) {
        for (int k : keyCodes) {
            uiDevice.pressKeyCode(k);
        }
    }

    /**
     * Parses the string and sends the corresponding individual key presses.
     * <p>
     * <b>Note:</b> only handles 0-9, '.', and '-'.
     */
    public static void pressKeys(UiDevice uiDevice, String keys) {
        for (char c : keys.toCharArray()) {
            if (c >= '0' && c <= '9') {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_0 + c - '0');
            } else if (c == '-') {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_MINUS);
            } else if (c == '.') {
                uiDevice.pressKeyCode(KeyEvent.KEYCODE_PERIOD);
            } else {
                throw new IllegalArgumentException(c + " is not supported");
            }
        }
    }

    /**
     * Sends the DPAD Center key presses with the {@code repeat} count.
     * TODO: Remove instrumentation argument once migrated to JUnit4.
     */
    public static void pressDPadCenter(Instrumentation instrumentation, int repeat) {
        pressKey(instrumentation, KeyEvent.KEYCODE_DPAD_CENTER, repeat);
    }

    private static void pressKey(Instrumentation instrumentation, int keyCode, int repeat) {
        UiDevice.getInstance(instrumentation).waitForIdle();
        for (int i = 0; i < repeat; ++i) {
            assertPressKeyDown(instrumentation, keyCode, false);
            if (i < repeat - 1) {
                assertPressKeyUp(instrumentation, keyCode, false);
            }
        }
        // Send last key event synchronously.
        assertPressKeyUp(instrumentation, keyCode, true);
    }

    private static void assertPressKeyDown(Instrumentation instrumentation, int keyCode,
            boolean sync) {
        assertPressKey(instrumentation, KeyEvent.ACTION_DOWN, keyCode, sync);
    }

    private static void assertPressKeyUp(Instrumentation instrumentation, int keyCode,
            boolean sync) {
        assertPressKey(instrumentation, KeyEvent.ACTION_UP, keyCode, sync);
    }

    private static void assertPressKey(Instrumentation instrumentation, int action, int keyCode,
            boolean sync) {
        long eventTime = SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(eventTime, eventTime, action, keyCode, 0, 0, -1, 0, 0,
                InputDevice.SOURCE_KEYBOARD);
        assertTrue("Failed to inject key up event:" + event,
                injectEvent(instrumentation, event, sync));
    }

    private static boolean injectEvent(Instrumentation instrumentation, KeyEvent event,
            boolean sync) {
        return getUiAutomation(instrumentation).injectInputEvent(event, sync);
    }

    private static UiAutomation getUiAutomation(Instrumentation instrumentation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            int flags = Configurator.getInstance().getUiAutomationFlags();
            return instrumentation.getUiAutomation(flags);
        } else {
            return instrumentation.getUiAutomation();
        }
    }

    private UiDeviceUtils() {
    }
}
