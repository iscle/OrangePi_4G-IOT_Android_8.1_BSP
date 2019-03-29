/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.PopupMenu;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MenuInflater}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MenuTest {
    private MenuTestActivity mActivity;
    private MenuInflater mMenuInflater;
    private Menu mMenu;

    @Rule
    public ActivityTestRule<MenuTestActivity> mActivityRule =
            new ActivityTestRule<>(MenuTestActivity.class);

    @UiThreadTest
    @Before
    public void setup() {
        mActivity = (MenuTestActivity) mActivityRule.getActivity();
        mMenuInflater = mActivity.getMenuInflater();
        mMenu = new PopupMenu(mActivity, null).getMenu();
    }

    @UiThreadTest
    @Test
    public void testPerformShortcut() {
        mMenuInflater.inflate(R.menu.shortcut_modifiers, mMenu);
        mMenu.setQwertyMode(true);
        int keyCodeToSend, metaState;
        KeyEvent keyEventToSend;

        // Test shortcut trigger in case of no modifier
        keyCodeToSend = KeyEvent.KEYCODE_A;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = generateKeyEvent(keyCodeToSend, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.no_modifiers).getItemId());

        // Test shortcut trigger in case of default modifier
        keyCodeToSend = KeyEvent.KEYCODE_B;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = generateKeyEvent(keyCodeToSend, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.default_modifiers).getItemId());

        // Test shortcut trigger in case of non-default single modifier
        keyCodeToSend = KeyEvent.KEYCODE_C;
        metaState = KeyEvent.META_SHIFT_ON;
        keyEventToSend = generateKeyEvent(keyCodeToSend, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.single_modifier).getItemId());

        // Test shortcut trigger in case of multiple modifiers
        keyCodeToSend = KeyEvent.KEYCODE_D;
        metaState = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;
        keyEventToSend = generateKeyEvent(keyCodeToSend, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.multiple_modifiers).getItemId());

        // Test no shortcut trigger in case of incorrect modifier
        keyCodeToSend = KeyEvent.KEYCODE_E;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = generateKeyEvent(keyCodeToSend, metaState);
        assertFalse(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
    }

    @UiThreadTest
    @Test
    public void testSetShortcutWithAlpha() {
        mMenu.setQwertyMode(true);
        // Test default modifier (CTRL) when unspecified in setShortcut
        mMenu.add(0, 0, 0, "test").setShortcut('2', 'a');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_A,
                generateKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_A,
                generateKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)));
        // Test setShortcut with modifier
        mMenu.add(0, 0, 0, "test").setShortcut('3', 'b',
                KeyEvent.META_ALT_ON, KeyEvent.META_ALT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_C,
                generateKeyEvent(KeyEvent.KEYCODE_C, KeyEvent.META_ALT_ON)));
    }

    @UiThreadTest
    @Test
    public void testSetShortcutWithNumeric() {
        mMenu.setQwertyMode(false);
        // Test default modifier (CTRL) when unspecified in setShortcut
        mMenu.add(0, 0, 0, "test").setShortcut('2', 'a');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_2,
                generateKeyEvent(KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_2,
                generateKeyEvent(KeyEvent.KEYCODE_2, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON)));
        // Test setShortcut with modifier
        mMenu.add(0, 0, 0, "test").setShortcut('3', 'b',
                KeyEvent.META_ALT_ON, KeyEvent.META_ALT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_4,
                generateKeyEvent(KeyEvent.KEYCODE_4, KeyEvent.META_ALT_ON)));
    }

    @UiThreadTest
    @Test
    public void testSetAlphabeticShortcut() {
        mMenu.setQwertyMode(true);
        // Test default modifier when unspecified in setAlphabeticShortcut
        mMenu.add(0, 0, 0, "test").setAlphabeticShortcut('a');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_A,
                generateKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_A,
                generateKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)));
        // Test setAlphabeticShortcut with single modifier
        mMenu.add(0, 0, 0, "test").setAlphabeticShortcut('b', KeyEvent.META_ALT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_B,
                generateKeyEvent(KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_C,
                generateKeyEvent(KeyEvent.KEYCODE_C, KeyEvent.META_ALT_ON)));
        // Test setAlphabeticShortcut with multiple modifiers
        mMenu.add(0, 0, 0, "test").setAlphabeticShortcut('c',
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_C, generateKeyEvent(KeyEvent.KEYCODE_C,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_C, generateKeyEvent(KeyEvent.KEYCODE_C,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_D, generateKeyEvent(KeyEvent.KEYCODE_D,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)));
    }

    @UiThreadTest
    @Test
    public void testSetNumericShortcut() {
        mMenu.setQwertyMode(false);
        // Test default modifier when unspecified in setNumericShortcut
        mMenu.add(0, 0, 0, "test").setNumericShortcut('2');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_2,
                generateKeyEvent(KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_2,
                generateKeyEvent(KeyEvent.KEYCODE_2, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON)));
        // Test setNumericShortcut with single modifier
        mMenu.add(0, 0, 0, "test").setNumericShortcut('3', KeyEvent.META_ALT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_ALT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_3,
                generateKeyEvent(KeyEvent.KEYCODE_3, KeyEvent.META_CTRL_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_4,
                generateKeyEvent(KeyEvent.KEYCODE_4, KeyEvent.META_ALT_ON)));
        // Test setNumericShortcut with multiple modifiers
        mMenu.add(0, 0, 0, "test").setNumericShortcut('4',
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON);
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_4, generateKeyEvent(KeyEvent.KEYCODE_4,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_4, generateKeyEvent(KeyEvent.KEYCODE_4,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)));
        assertFalse(mMenu.isShortcutKey(KeyEvent.KEYCODE_5, generateKeyEvent(KeyEvent.KEYCODE_5,
                KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON)));
    }

    @UiThreadTest
    @Test
    public void testGetShortcutModifiers() {
        MenuItem item = mMenu.add(0, 0, 0, "test");
        // Test shortcut getters and setters
        item.setShortcut('2', 'a', KeyEvent.META_ALT_ON,
                KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON);
        assertEquals('2', item.getNumericShortcut());
        assertEquals(KeyEvent.META_ALT_ON, item.getNumericModifiers());
        assertEquals('a', item.getAlphabeticShortcut());
        assertEquals(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON, item.getAlphabeticModifiers());
    }

    @UiThreadTest
    @Test
    public void testIsShortcutWithBackspace() {
        mMenu.setQwertyMode(true);
        mMenu.add(0, 0, 0, "test").setShortcut('2', '\b');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_DEL,
                generateKeyEvent(KeyEvent.KEYCODE_DEL, KeyEvent.META_CTRL_ON)));
    }

    @UiThreadTest
    @Test
    public void testIsShortcutWithNewline() {
        mMenu.setQwertyMode(true);
        mMenu.add(0, 0, 0, "test").setShortcut('2', '\n');
        assertTrue(mMenu.isShortcutKey(KeyEvent.KEYCODE_ENTER,
                generateKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON)));
    }

    private static KeyEvent generateKeyEvent(int keyCodeToSend, int metaState) {
        long downTime = SystemClock.uptimeMillis();
        return new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCodeToSend, 0, metaState);
    }
}
