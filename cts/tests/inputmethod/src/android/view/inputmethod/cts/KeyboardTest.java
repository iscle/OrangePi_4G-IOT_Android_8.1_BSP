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

package android.view.inputmethod.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.inputmethod.cts.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyboardTest {
    @Test
    public void testKeyOnPressedAndReleased() {
        Key nonStickyKey = null;
        Key stickyKey = null;
        // Indirectly instantiate Keyboard.Key with XML resources.
        final Keyboard keyboard =
                new Keyboard(InstrumentationRegistry.getTargetContext(), R.xml.keyboard);
        for (final Key key : keyboard.getKeys()) {
            if (!key.sticky) {
                nonStickyKey = key;
                break;
            }
        }
        for (final Key key : keyboard.getModifierKeys()) {
            if (key.sticky) {
                stickyKey = key;
                break;
            }
        }

        // Asserting existences of following keys is not the goal of this test, but this should work
        // anyway.
        assertNotNull(nonStickyKey);
        assertNotNull(stickyKey);

        // At first, both "pressed" and "on" must be false.
        assertFalse(nonStickyKey.pressed);
        assertFalse(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertFalse(stickyKey.on);

        // Pressing the key must flip the "pressed" state only.
        nonStickyKey.onPressed();
        stickyKey.onPressed();
        assertTrue(nonStickyKey.pressed);
        assertTrue(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertFalse(stickyKey.on);

        // Releasing the key inside the key area must flip the "pressed" state and toggle the "on"
        // state if the key is marked as sticky.
        nonStickyKey.onReleased(true /* inside */);
        stickyKey.onReleased(true /* inside */);
        assertFalse(nonStickyKey.pressed);
        assertFalse(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertTrue(stickyKey.on);   // The key state is toggled.

        // Pressing the key again must flip the "pressed" state only.
        nonStickyKey.onPressed();
        stickyKey.onPressed();
        assertTrue(nonStickyKey.pressed);
        assertTrue(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertTrue(stickyKey.on);

        // Releasing the key inside the key area must flip the "pressed" state and toggle the "on"
        // state if the key is marked as sticky hence we will be back to the initial state.
        nonStickyKey.onReleased(true /* inside */);
        stickyKey.onReleased(true /* inside */);
        assertFalse(nonStickyKey.pressed);
        assertFalse(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertFalse(stickyKey.on);

        // Pressing then releasing the key outside the key area must not affect the "on" state.
        nonStickyKey.onPressed();
        stickyKey.onPressed();
        nonStickyKey.onReleased(false /* inside */);
        stickyKey.onReleased(false /* inside */);
        assertFalse(nonStickyKey.pressed);
        assertFalse(stickyKey.pressed);
        assertFalse(nonStickyKey.on);
        assertFalse(stickyKey.on);
    }
}
