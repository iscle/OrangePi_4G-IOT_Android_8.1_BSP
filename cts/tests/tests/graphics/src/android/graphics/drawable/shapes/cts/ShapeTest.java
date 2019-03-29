/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.graphics.drawable.shapes.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.shapes.Shape;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShapeTest {
    private static final int TEST_WIDTH  = 100;
    private static final int TEST_HEIGHT = 200;

    private static final int TEST_COLOR_1 = 0xFF00FF00;
    private static final int TEST_COLOR_2 = 0xFFFF0000;

    @Test
    public void testSize() {
        MockShape mockShape = new MockShape();
        assertFalse(mockShape.hasCalledOnResize());

        mockShape.resize(200f, 300f);
        assertEquals(200f, mockShape.getWidth(), 0.0f);
        assertEquals(300f, mockShape.getHeight(), 0.0f);
        assertTrue(mockShape.hasCalledOnResize());

        mockShape.resize(0f, 0f);
        assertEquals(0f, mockShape.getWidth(), 0.0f);
        assertEquals(0f, mockShape.getHeight(), 0.0f);

        mockShape.resize(Float.MAX_VALUE, Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, mockShape.getWidth(), 0.0f);
        assertEquals(Float.MAX_VALUE, mockShape.getHeight(), 0.0f);

        mockShape.resize(-1, -1);
        assertEquals(0f, mockShape.getWidth(), 0.0f);
        assertEquals(0f, mockShape.getHeight(), 0.0f);
    }

    @Test
    public void testOnResize() {
        MockShape mockShape = new MockShape();
        assertFalse(mockShape.hasCalledOnResize());

        mockShape.resize(200f, 300f);
        assertTrue(mockShape.hasCalledOnResize());

        // size does not change
        mockShape.reset();
        mockShape.resize(200f, 300f);
        assertFalse(mockShape.hasCalledOnResize());

        // size changes
        mockShape.reset();
        mockShape.resize(100f, 200f);
        assertTrue(mockShape.hasCalledOnResize());
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        Shape shape = new MockShape();
        shape.resize(100f, 200f);
        Shape clonedShape = shape.clone();
        assertEquals(100f, shape.getWidth(), 0.0f);
        assertEquals(200f, shape.getHeight(), 0.0f);

        assertNotSame(shape, clonedShape);
        assertEquals(shape.getWidth(), clonedShape.getWidth(), 0.0f);
        assertEquals(shape.getHeight(), clonedShape.getHeight(), 0.0f);
    }

    @Test
    public void testHasAlpha() {
        Shape shape = new MockShape();
        assertTrue(shape.hasAlpha());
    }

    @Test
    public void testDraw() {
        Shape shape = new MockShape();
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Style.FILL);
        paint.setColor(TEST_COLOR_1);
        shape.resize(TEST_WIDTH, TEST_HEIGHT);

        shape.draw(canvas, paint);
        assertEquals(0, bitmap.getPixel(0, 0));

        paint.setColor(TEST_COLOR_2);
        shape.draw(canvas, paint);
        assertEquals(0, bitmap.getPixel(0, 0));
    }

    @Test
    public void testGetOutline() {
        Shape shape = new MockShape();
        Outline outline = new Outline();

        // This is a no-op. Just make sure it doesn't crash.
        shape.getOutline(outline);
    }

    private static class MockShape extends Shape {
        private boolean mCalledOnResize = false;

        @Override
        public void draw(Canvas canvas, Paint paint) {
        }

        @Override
        protected void onResize(float width, float height) {
            super.onResize(width, height);
            mCalledOnResize = true;
        }

        public boolean hasCalledOnResize() {
            return mCalledOnResize;
        }

        public void reset() {
            mCalledOnResize = false;
        }
    }
}
