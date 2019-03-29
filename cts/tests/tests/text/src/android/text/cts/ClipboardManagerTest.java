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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.ClipboardManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link ClipboardManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ClipboardManagerTest {
    private ClipboardManager mClipboardManager;

    @UiThreadTest
    @Before
    public void setup() {
        final Context context = InstrumentationRegistry.getTargetContext();
        mClipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @UiThreadTest
    @Test
    public void testAccessText() {
        // set the expected value
        CharSequence expected = "test";
        mClipboardManager.setText(expected);
        assertEquals(expected, mClipboardManager.getText());
    }

    @UiThreadTest
    @Test
    public void testHasText() {
        mClipboardManager.setText("");
        assertFalse(mClipboardManager.hasText());

        mClipboardManager.setText("test");
        assertTrue(mClipboardManager.hasText());

        mClipboardManager.setText(null);
        assertFalse(mClipboardManager.hasText());
    }
}
