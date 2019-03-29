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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ClickableSpanTest {
    @Test
    public void testUpdateDrawState() {
        ClickableSpan clickableSpan = new MyClickableSpan();

        TextPaint tp = new TextPaint();
        tp.linkColor = Color.RED;
        tp.setUnderlineText(false);
        assertFalse(tp.isUnderlineText());

        clickableSpan.updateDrawState(tp);
        assertEquals(Color.RED, tp.getColor());
        assertTrue(tp.isUnderlineText());

        tp.linkColor = Color.BLUE;
        clickableSpan.updateDrawState(tp);
        assertEquals(Color.BLUE, tp.getColor());
        assertTrue(tp.isUnderlineText());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        ClickableSpan clickableSpan = new MyClickableSpan();

        // Should throw NullPointerException when TextPaint is null
        clickableSpan.updateDrawState(null);
    }

    private class MyClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
        }
    }
}
