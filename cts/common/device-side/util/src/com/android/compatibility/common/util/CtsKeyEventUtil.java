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

package com.android.compatibility.common.util;

import android.app.Instrumentation;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import java.lang.reflect.Field;

/**
 * Utility class to send KeyEvents bypassing the IME. The code is similar to functions in
 * {@link Instrumentation} and {@link android.test.InstrumentationTestCase} classes. It uses
 * {@link InputMethodManager#dispatchKeyEventFromInputMethod(View, KeyEvent)} to send the events.
 * After sending the events waits for idle.
 */
public final class CtsKeyEventUtil {

    private CtsKeyEventUtil() {}

    /**
     * Sends the key events corresponding to the text to the app being instrumented.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param text The text to be sent. Null value returns immediately.
     */
    public static void sendString(final Instrumentation instrumentation, final View targetView,
            final String text) {
        if (text == null) {
            return;
        }

        KeyEvent[] events = getKeyEvents(text);

        if (events != null) {
            for (int i = 0; i < events.length; i++) {
                // We have to change the time of an event before injecting it because
                // all KeyEvents returned by KeyCharacterMap.getEvents() have the same
                // time stamp and the system rejects too old events. Hence, it is
                // possible for an event to become stale before it is injected if it
                // takes too long to inject the preceding ones.
                sendKey(instrumentation, targetView, KeyEvent.changeTimeRepeat(
                        events[i], SystemClock.uptimeMillis(), 0 /* newRepeat */));
            }
        }
    }

    /**
     * Sends a series of key events through instrumentation. For instance:
     * sendKeys(view, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER).
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param keys The series of key codes.
     */
    public static void sendKeys(final Instrumentation instrumentation, final View targetView,
            final int...keys) {
        final int count = keys.length;

        for (int i = 0; i < count; i++) {
            try {
                sendKeyDownUp(instrumentation, targetView, keys[i]);
            } catch (SecurityException e) {
                // Ignore security exceptions that are now thrown
                // when trying to send to another app, to retain
                // compatibility with existing tests.
            }
        }
    }

    /**
     * Sends a series of key events through instrumentation. The sequence of keys is a string
     * containing the key names as specified in KeyEvent, without the KEYCODE_ prefix. For
     * instance: sendKeys(view, "DPAD_LEFT A B C DPAD_CENTER"). Each key can be repeated by using
     * the N* prefix. For instance, to send two KEYCODE_DPAD_LEFT, use the following:
     * sendKeys(view, "2*DPAD_LEFT").
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param keysSequence The sequence of keys.
     */
    public static void sendKeys(final Instrumentation instrumentation, final View targetView,
            final String keysSequence) {
        final String[] keys = keysSequence.split(" ");
        final int count = keys.length;

        for (int i = 0; i < count; i++) {
            String key = keys[i];
            int repeater = key.indexOf('*');

            int keyCount;
            try {
                keyCount = repeater == -1 ? 1 : Integer.parseInt(key.substring(0, repeater));
            } catch (NumberFormatException e) {
                Log.w("ActivityTestCase", "Invalid repeat count: " + key);
                continue;
            }

            if (repeater != -1) {
                key = key.substring(repeater + 1);
            }

            for (int j = 0; j < keyCount; j++) {
                try {
                    final Field keyCodeField = KeyEvent.class.getField("KEYCODE_" + key);
                    final int keyCode = keyCodeField.getInt(null);
                    try {
                        sendKeyDownUp(instrumentation, targetView, keyCode);
                    } catch (SecurityException e) {
                        // Ignore security exceptions that are now thrown
                        // when trying to send to another app, to retain
                        // compatibility with existing tests.
                    }
                } catch (NoSuchFieldException e) {
                    Log.w("ActivityTestCase", "Unknown keycode: KEYCODE_" + key);
                    break;
                } catch (IllegalAccessException e) {
                    Log.w("ActivityTestCase", "Unknown keycode: KEYCODE_" + key);
                    break;
                }
            }
        }
    }

    /**
     * Sends an up and down key events.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param key The integer keycode for the event to be sent.
     */
    public static void sendKeyDownUp(final Instrumentation instrumentation, final View targetView,
            final int key) {
        sendKey(instrumentation, targetView, new KeyEvent(KeyEvent.ACTION_DOWN, key));
        sendKey(instrumentation, targetView, new KeyEvent(KeyEvent.ACTION_UP, key));
    }

    /**
     * Sends a key event.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param event KeyEvent to be send.
     */
    public static void sendKey(final Instrumentation instrumentation, final View targetView,
            final KeyEvent event) {
        validateNotAppThread();

        long downTime = event.getDownTime();
        long eventTime = event.getEventTime();
        int action = event.getAction();
        int code = event.getKeyCode();
        int repeatCount = event.getRepeatCount();
        int metaState = event.getMetaState();
        int deviceId = event.getDeviceId();
        int scanCode = event.getScanCode();
        int source = event.getSource();
        int flags = event.getFlags();
        if (source == InputDevice.SOURCE_UNKNOWN) {
            source = InputDevice.SOURCE_KEYBOARD;
        }
        if (eventTime == 0) {
            eventTime = SystemClock.uptimeMillis();
        }
        if (downTime == 0) {
            downTime = eventTime;
        }

        final KeyEvent newEvent = new KeyEvent(downTime, eventTime, action, code, repeatCount,
                metaState, deviceId, scanCode, flags, source);

        InputMethodManager imm = targetView.getContext().getSystemService(InputMethodManager.class);
        imm.dispatchKeyEventFromInputMethod(null, newEvent);
        instrumentation.waitForIdleSync();
    }

    /**
     * Sends a key event while holding another modifier key down, then releases both keys and
     * waits for idle sync. Useful for sending combinations like shift + tab.
     *
     * @param instrumentation the instrumentation used to run the test.
     * @param targetView View to find the ViewRootImpl and dispatch.
     * @param keyCodeToSend The integer keycode for the event to be sent.
     * @param modifierKeyCodeToHold The integer keycode of the modifier to be held.
     */
    public static void sendKeyWhileHoldingModifier(final Instrumentation instrumentation,
            final View targetView, final int keyCodeToSend,
            final int modifierKeyCodeToHold) {
        final int metaState = getMetaStateForModifierKeyCode(modifierKeyCodeToHold);
        final long downTime = SystemClock.uptimeMillis();

        final KeyEvent holdKeyDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                modifierKeyCodeToHold, 0 /* repeat */);
        sendKey(instrumentation ,targetView, holdKeyDown);

        final KeyEvent keyDown = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0 /* repeat */, metaState);
        sendKey(instrumentation, targetView, keyDown);

        final KeyEvent keyUp = new KeyEvent(downTime, downTime, KeyEvent.ACTION_UP,
                keyCodeToSend, 0 /* repeat */, metaState);
        sendKey(instrumentation, targetView, keyUp);

        final KeyEvent holdKeyUp = new KeyEvent(downTime, downTime, KeyEvent.ACTION_UP,
                modifierKeyCodeToHold, 0 /* repeat */);
        sendKey(instrumentation, targetView, holdKeyUp);

        instrumentation.waitForIdleSync();
    }

    private static int getMetaStateForModifierKeyCode(int modifierKeyCode) {
        if (!KeyEvent.isModifierKey(modifierKeyCode)) {
            throw new IllegalArgumentException("Modifier key expected, but got: "
                    + KeyEvent.keyCodeToString(modifierKeyCode));
        }

        int metaState;
        switch (modifierKeyCode) {
            case KeyEvent.KEYCODE_SHIFT_LEFT:
                metaState = KeyEvent.META_SHIFT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                metaState = KeyEvent.META_SHIFT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_LEFT:
                metaState = KeyEvent.META_ALT_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_ALT_RIGHT:
                metaState = KeyEvent.META_ALT_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_LEFT:
                metaState = KeyEvent.META_CTRL_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                metaState = KeyEvent.META_CTRL_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_META_LEFT:
                metaState = KeyEvent.META_META_LEFT_ON;
                break;
            case KeyEvent.KEYCODE_META_RIGHT:
                metaState = KeyEvent.META_META_RIGHT_ON;
                break;
            case KeyEvent.KEYCODE_SYM:
                metaState = KeyEvent.META_SYM_ON;
                break;
            case KeyEvent.KEYCODE_NUM:
                metaState = KeyEvent.META_NUM_LOCK_ON;
                break;
            case KeyEvent.KEYCODE_FUNCTION:
                metaState = KeyEvent.META_FUNCTION_ON;
                break;
            default:
                // Safety net: all modifier keys need to have at least one meta state associated.
                throw new UnsupportedOperationException("No meta state associated with "
                        + "modifier key: " + KeyEvent.keyCodeToString(modifierKeyCode));
        }

        return KeyEvent.normalizeMetaState(metaState);
    }

    private static KeyEvent[] getKeyEvents(final String text) {
        KeyCharacterMap keyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        return keyCharacterMap.getEvents(text.toCharArray());
    }

    private static void validateNotAppThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new RuntimeException(
                    "This method can not be called from the main application thread");
        }
    }
}
