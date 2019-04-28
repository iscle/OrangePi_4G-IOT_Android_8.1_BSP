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
 * limitations under the License
 */

package android.platform.test.utils;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.EventCondition;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;
import android.view.KeyEvent;

import java.io.IOException;


public class DPadUtil {

    private static final String TAG = DPadUtil.class.getSimpleName();
    private static final long DPAD_DEFAULT_WAIT_TIME_MS = 1000; // 1 sec
    private UiDevice mDevice;


    public DPadUtil(Instrumentation instrumentation) {
        mDevice = UiDevice.getInstance(instrumentation);
    }

    public DPadUtil(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    public void setUiDevice(UiDevice uiDevice) {
        mDevice = uiDevice;
    }

    public boolean pressDPad(Direction direction) {
        return pressDPad(direction, 1, DPAD_DEFAULT_WAIT_TIME_MS);
    }

    public void pressDPad(Direction direction, long repeat) {
        pressDPad(direction, repeat, DPAD_DEFAULT_WAIT_TIME_MS);
    }

    /**
     * Presses DPad button of the same direction for the count times.
     * It sleeps between each press for DPAD_DEFAULT_WAIT_TIME_MS.
     *
     * @param direction the direction of the button to press.
     * @param repeat the number of times to press the button.
     * @param timeout the timeout for the wait.
     * @return true if the last key simulation is successful, else return false
     */
    public boolean pressDPad(Direction direction, long repeat, long timeout) {
        int iteration = 0;
        boolean result = false;
        while (iteration++ < repeat) {
            switch (direction) {
                case LEFT:
                    result = mDevice.pressDPadLeft();
                    break;
                case RIGHT:
                    result = mDevice.pressDPadRight();
                    break;
                case UP:
                    result = mDevice.pressDPadUp();
                    break;
                case DOWN:
                    result = mDevice.pressDPadDown();
                    break;
            }
            SystemClock.sleep(timeout);
        }
        return result;
    }

    public boolean pressDPadLeft() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_DPAD_LEFT);
    }

    public boolean pressDPadRight() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_DPAD_RIGHT);
    }

    public boolean pressDPadUp() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_DPAD_UP);
    }

    public boolean pressDPadDown() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_DPAD_DOWN);
    }

    public boolean pressDPadCenter() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_DPAD_CENTER);
    }

    public boolean pressEnter() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_ENTER);
    }

    public boolean pressPipKey() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_WINDOW);
    }

    public boolean pressSearch() {
        return pressKeyCodeAndWait(KeyEvent.KEYCODE_SEARCH);
    }

    public boolean pressKeyCode(int keyCode) {
        return pressKeyCodeAndWait(keyCode);
    }
    public boolean pressKeyCodeAndWait(int keyCode) {
        boolean retVal = mDevice.pressKeyCode(keyCode);
        // Dpad key presses will cause some UI change to occur.
        // Wait for the accessibility event stream to become idle.
        mDevice.waitForIdle();
        return retVal;
    }

    public boolean pressHome() {
        return mDevice.pressHome();
    }

    public boolean pressBack() {
        return mDevice.pressBack();
    }

    public boolean longPressKeyCode(int keyCode) {
        try {
            mDevice.executeShellCommand(String.format("input keyevent --longpress %d", keyCode));
            mDevice.waitForIdle();
            return true;
        } catch (IOException e) {
            // Ignore
            Log.w(TAG, String.format("Failed to long press the key code: %d", keyCode));
            return false;
        }
    }

    /**
     * Press the key code, and waits for the given condition to become true.
     * @param keyCode
     * @param condition
     * @param longpress
     * @param timeout
     * @param <R>
     * @return
     */
    public <R> R pressKeyCodeAndWait(int keyCode, EventCondition<R> condition, boolean longpress,
            long timeout) {
        return mDevice.performActionAndWait(new KeyEventRunnable(keyCode, longpress), condition,
                timeout);
    }

    public <R> R pressKeyCodeAndWait(int keyCode, EventCondition<R> condition, long timeout) {
        return pressKeyCodeAndWait(keyCode, condition, false, timeout);
    }

    public <R> R pressDPadCenterAndWait(EventCondition<R> condition, long timeout) {
        return mDevice.performActionAndWait(new KeyEventRunnable(KeyEvent.KEYCODE_DPAD_CENTER),
                condition, timeout);
    }

    public <R> R pressEnterAndWait(EventCondition<R> condition, long timeout) {
        return mDevice.performActionAndWait(new KeyEventRunnable(KeyEvent.KEYCODE_ENTER),
                condition, timeout);
    }

    private class KeyEventRunnable implements Runnable {
        private int mKeyCode;
        private boolean mLongPress = false;
        public KeyEventRunnable(int keyCode) {
            mKeyCode = keyCode;
        }
        public KeyEventRunnable(int keyCode, boolean longpress) {
            mKeyCode = keyCode;
            mLongPress = longpress;
        }
        @Override
        public void run() {
            if (mLongPress) {
                longPressKeyCode(mKeyCode);
            } else {
                pressKeyCode(mKeyCode);
            }
        }
    }
}
