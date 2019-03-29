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

import android.graphics.Canvas;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.cts.R;
import android.text.style.DynamicDrawableSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DynamicDrawableSpanTest {
    @Test
    public void testConstructor() {
        DynamicDrawableSpan d = new MyDynamicDrawableSpan();
        assertEquals(DynamicDrawableSpan.ALIGN_BOTTOM, d.getVerticalAlignment());

        d = new MyDynamicDrawableSpan(DynamicDrawableSpan.ALIGN_BASELINE);
        assertEquals(DynamicDrawableSpan.ALIGN_BASELINE, d.getVerticalAlignment());

        d = new MyDynamicDrawableSpan(DynamicDrawableSpan.ALIGN_BOTTOM);
        assertEquals(DynamicDrawableSpan.ALIGN_BOTTOM, d.getVerticalAlignment());
    }

    @Test
    public void testGetSize() {
        DynamicDrawableSpan dynamicDrawableSpan = new MyDynamicDrawableSpan();
        FontMetricsInt fm = new FontMetricsInt();

        assertEquals(0, fm.ascent);
        assertEquals(0, fm.bottom);
        assertEquals(0, fm.descent);
        assertEquals(0, fm.leading);
        assertEquals(0, fm.top);

        Rect rect = dynamicDrawableSpan.getDrawable().getBounds();
        assertEquals(rect.right, dynamicDrawableSpan.getSize(null, null, 0, 0, fm));

        assertEquals(-rect.bottom, fm.ascent);
        assertEquals(0, fm.bottom);
        assertEquals(0, fm.descent);
        assertEquals(0, fm.leading);
        assertEquals(-rect.bottom, fm.top);

        assertEquals(rect.right, dynamicDrawableSpan.getSize(null, null, 0, 0, null));
    }

    @Test
    public void testDraw() {
        DynamicDrawableSpan dynamicDrawableSpan = new MyDynamicDrawableSpan();
        Canvas canvas = new Canvas();
        dynamicDrawableSpan.draw(canvas, null, 0, 0, 1.0f, 0, 0, 1, null);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawNullCanvas() {
        DynamicDrawableSpan dynamicDrawableSpan = new MyDynamicDrawableSpan();

        dynamicDrawableSpan.draw(null, null, 0, 0, 1.0f, 0, 0, 1, null);
    }

    private class MyDynamicDrawableSpan extends DynamicDrawableSpan {
        public MyDynamicDrawableSpan() {
            super();
        }

        protected MyDynamicDrawableSpan(int verticalAlignment) {
            super(verticalAlignment);
        }

        @Override
        public Drawable getDrawable() {
            return InstrumentationRegistry.getTargetContext().getDrawable(R.drawable.scenery);
        }
    }
}
