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
package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.KeyboardShortcutInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link android.view.KeyboardShortcutInfo}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyboardShortcutInfoTest {
    private static final CharSequence TEST_LABEL = "Test Label";
    private static final char TEST_BASE_CHARACTER = 't';
    private static final int TEST_KEYCODE = KeyEvent.KEYCODE_T;
    private static final int TEST_MODIFIERS = KeyEvent.META_ALT_ON | KeyEvent.META_CTRL_ON;

    @Test
    public void testCharacterConstructor() {
        KeyboardShortcutInfo info = new KeyboardShortcutInfo(
                TEST_LABEL, TEST_BASE_CHARACTER, TEST_MODIFIERS);
        assertNotNull(info);
        assertEquals(TEST_LABEL, info.getLabel());
        assertEquals(TEST_BASE_CHARACTER, info.getBaseCharacter());
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, info.getKeycode());
        assertEquals(TEST_MODIFIERS, info.getModifiers());
        assertEquals(0, info.describeContents());
    }

    @Test
    public void testKeycodeConstructor() {
        KeyboardShortcutInfo info = new KeyboardShortcutInfo(
                TEST_LABEL, TEST_KEYCODE, TEST_MODIFIERS);
        assertNotNull(info);
        assertEquals(TEST_LABEL, info.getLabel());
        assertEquals(Character.MIN_VALUE, info.getBaseCharacter());
        assertEquals(TEST_KEYCODE, info.getKeycode());
        assertEquals(TEST_MODIFIERS, info.getModifiers());
        assertEquals(0, info.describeContents());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorChecksBaseCharacter() {
        new KeyboardShortcutInfo(TEST_LABEL, Character.MIN_VALUE, TEST_MODIFIERS);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorChecksKeycode() {
        new KeyboardShortcutInfo(TEST_LABEL, KeyEvent.KEYCODE_UNKNOWN - 1, TEST_MODIFIERS);
    }

    @Test
    public void testWriteToParcelAndReadCharacter() {
        Parcel dest = Parcel.obtain();
        KeyboardShortcutInfo info = new KeyboardShortcutInfo(
                TEST_LABEL, TEST_BASE_CHARACTER, TEST_MODIFIERS);
        info.writeToParcel(dest, 0);

        dest.setDataPosition(0);
        KeyboardShortcutInfo result = KeyboardShortcutInfo.CREATOR.createFromParcel(dest);

        assertEquals(TEST_LABEL, result.getLabel());
        assertEquals(TEST_BASE_CHARACTER, result.getBaseCharacter());
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, result.getKeycode());
        assertEquals(TEST_MODIFIERS, result.getModifiers());
    }

    @Test
    public void testWriteToParcelAndReadKeycode() {
        Parcel dest = Parcel.obtain();
        KeyboardShortcutInfo info = new KeyboardShortcutInfo(
                TEST_LABEL, TEST_KEYCODE, TEST_MODIFIERS);
        info.writeToParcel(dest, 0);

        dest.setDataPosition(0);
        KeyboardShortcutInfo result = KeyboardShortcutInfo.CREATOR.createFromParcel(dest);

        assertEquals(TEST_LABEL, result.getLabel());
        assertEquals(Character.MIN_VALUE, result.getBaseCharacter());
        assertEquals(TEST_KEYCODE, result.getKeycode());
        assertEquals(TEST_MODIFIERS, result.getModifiers());
    }
}
