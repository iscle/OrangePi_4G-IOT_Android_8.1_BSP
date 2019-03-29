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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link android.view.KeyboardShortcutGroup}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyboardShortcutGroupTest {
    private static final CharSequence TEST_LABEL = "Test Group Label";
    private static final List<KeyboardShortcutInfo> TEST_ITEMS = new ArrayList<>();

    static {
        TEST_ITEMS.add(new KeyboardShortcutInfo(
                "Item 1", KeyEvent.KEYCODE_U, KeyEvent.META_CTRL_ON));
        TEST_ITEMS.add(new KeyboardShortcutInfo(
                "Item 2", KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON));
    }

    @Test
    public void testConstructor() {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL, TEST_ITEMS);

        assertEquals(TEST_LABEL, group.getLabel());
        assertEquals(TEST_ITEMS, group.getItems());
        assertFalse(group.isSystemGroup());
        assertEquals(0, group.describeContents());
    }

    @Test
    public void testShortConstructor() {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL);

        assertEquals(TEST_LABEL, group.getLabel());
        assertNotNull(group.getItems());
        assertFalse(group.isSystemGroup());
        assertEquals(0, group.describeContents());
    }

    @Test
    public void testSystemConstructor() {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL, TEST_ITEMS, true);

        assertEquals(TEST_LABEL, group.getLabel());
        assertEquals(TEST_ITEMS, group.getItems());
        assertTrue(group.isSystemGroup());
        assertEquals(0, group.describeContents());
    }

    @Test
    public void testSystemShortConstructor() {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL, true);

        assertEquals(TEST_LABEL, group.getLabel());
        assertNotNull(group.getItems());
        assertTrue(group.isSystemGroup());
        assertEquals(0, group.describeContents());
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorChecksList() {
        new KeyboardShortcutGroup(TEST_LABEL, null);
    }

    @Test
    public void testAddItem() {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL, TEST_ITEMS);

        group.addItem(new KeyboardShortcutInfo(
                "Additional item", KeyEvent.KEYCODE_P, KeyEvent.META_CTRL_ON));

        final int newSize = group.getItems().size();
        assertEquals(TEST_ITEMS.size() + 1, newSize);
        assertEquals("Additional item", group.getItems().get(newSize - 1).getLabel());
    }

    @Test
    public void testWriteToParcelAndRead() {
        Parcel dest = Parcel.obtain();
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(TEST_LABEL, TEST_ITEMS, true);
        group.writeToParcel(dest, 0);

        dest.setDataPosition(0);
        KeyboardShortcutGroup result = KeyboardShortcutGroup.CREATOR.createFromParcel(dest);

        assertEquals(TEST_LABEL, result.getLabel());
        assertEquals(TEST_ITEMS.size(), result.getItems().size());
        assertEquals(TEST_ITEMS.get(0).getLabel(), result.getItems().get(0).getLabel());
        assertEquals(TEST_ITEMS.get(1).getLabel(), result.getItems().get(1).getLabel());
        assertEquals(TEST_ITEMS.get(0).getKeycode(), result.getItems().get(0).getKeycode());
        assertEquals(TEST_ITEMS.get(1).getKeycode(), result.getItems().get(1).getKeycode());
        assertEquals(TEST_ITEMS.get(0).getModifiers(), result.getItems().get(0).getModifiers());
        assertEquals(TEST_ITEMS.get(1).getModifiers(), result.getItems().get(1).getModifiers());
        assertTrue(result.isSystemGroup());
    }
}
