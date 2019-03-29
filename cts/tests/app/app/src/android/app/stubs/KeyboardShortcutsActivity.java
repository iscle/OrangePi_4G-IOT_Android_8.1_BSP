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

package android.app.stubs;

import android.app.Activity;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class KeyboardShortcutsActivity extends Activity {

    public static final String ITEM_1_NAME = "item 1";
    public static final char ITEM_1_SHORTCUT = 'i';
    private boolean mOnProvideKeyboardShortcutsCalled;
    private final Lock mLock = new ReentrantLock();
    private final Condition mSignalOnProvideKeyboardShortcuts  = mLock.newCondition();
    private final Condition mSignalOnActivityDeFocused = mLock.newCondition();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(ITEM_1_NAME).setAlphabeticShortcut(ITEM_1_SHORTCUT);
        return true;
    }

    public void waitForMenuToBeOpen() throws InterruptedException {
        boolean stillWaiting = true;
        while (hasWindowFocus() && stillWaiting) {
            mLock.lock();
            try {
                stillWaiting = mSignalOnActivityDeFocused.await(10, TimeUnit.SECONDS);
            } finally {
                mLock.unlock();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            mLock.lock();
            try {
                mSignalOnActivityDeFocused.signal();
            } finally {
                mLock.unlock();
            }
        }
        super.onWindowFocusChanged(hasFocus);
    }

    public void waitForKeyboardShortcutsToBeRequested() throws InterruptedException {
        boolean stillWaiting = true;
        while (!onProvideKeyboardShortcutsCalled() && stillWaiting) {
            mLock.lock();
            try {
                stillWaiting = mSignalOnProvideKeyboardShortcuts.await(10, TimeUnit.SECONDS);
            } finally {
                mLock.unlock();
            }
        }
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        mOnProvideKeyboardShortcutsCalled = true;
        mLock.lock();
        try {
            mSignalOnProvideKeyboardShortcuts.signal();
        } finally {
            mLock.unlock();
        }
        super.onProvideKeyboardShortcuts(data, menu, deviceId);
    }

    public boolean onProvideKeyboardShortcutsCalled() {
        return mOnProvideKeyboardShortcutsCalled;
    }
}
