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
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.shapes.RoundRectShape;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RoundRectShapeTest {
    private static final int TEST_WIDTH  = 100;
    private static final int TEST_HEIGHT = 200;

    private static final int TEST_COLOR_1 = 0xFF00FF00;
    private static final int TEST_COLOR_2 = 0xFFFF0000;

    @Test
    public void testConstructor() {
        new RoundRectShape(new float[8], new RectF(), new float[8]);

        new RoundRectShape(new float[9], new RectF(), new float[9]);

        new RoundRectShape(new float[8], null, new float[8]);

        new RoundRectShape(new float[8], new RectF(), null);

        new RoundRectShape(null, new RectF(), new float[8]);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testConstructorTooFewOuterRadii() {
        new RoundRectShape(new float[7], new RectF(), new float[8]);
    }


    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testConstructorTooFewInnerRadii() {
        new RoundRectShape(new float[8], new RectF(), new float[7]);
    }

    @Test
    public void testDraw() {
        float[] outerR = new float[] { 12, 12, 0, 0, 0, 0, 0, 0 };
        RectF   inset = new RectF(6, 6, 6, 6);
        float[] innerR = new float[] { 12, 12, 0, 0, 0, 0, 0, 0 };
        RoundRectShape roundRectShape = new RoundRectShape(outerR, inset, innerR);
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Style.FILL);
        paint.setColor(TEST_COLOR_1);
        roundRectShape.resize(TEST_WIDTH, TEST_HEIGHT);

        roundRectShape.draw(canvas, paint);
        assertEquals(0, bitmap.getPixel(0, 0));
        assertEquals(TEST_COLOR_1, bitmap.getPixel(TEST_WIDTH / 2, 0));

        paint.setColor(TEST_COLOR_2);
        roundRectShape.draw(canvas, paint);
        assertEquals(0, bitmap.getPixel(0, 0));
        assertEquals(TEST_COLOR_2, bitmap.getPixel(TEST_WIDTH / 2, 0));
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        RoundRectShape roundRectShape = new RoundRectShape(new float[8], new RectF(), new float[8]);
        roundRectShape.resize(100f, 200f);
        RoundRectShape clonedShape = roundRectShape.clone();
        assertEquals(100f, roundRectShape.getWidth(), 0.0f);
        assertEquals(200f, roundRectShape.getHeight(), 0.0f);

        assertNotSame(roundRectShape, clonedShape);
        assertEquals(roundRectShape.getWidth(), clonedShape.getWidth(), 0.0f);
        assertEquals(roundRectShape.getHeight(), clonedShape.getHeight(), 0.0f);
    }

    @Test
    public void testGetOutline() {
        Outline outline = new Outline();
        Rect rect = new Rect();
        RoundRectShape shape;

        // All null.
        shape = new RoundRectShape(null, null, null);
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        shape.resize(100, 100);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertEquals(0.0f, outline.getRadius(), 0.0f);
        assertTrue(outline.getRect(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(100, rect.right);
        assertEquals(100, rect.bottom);

        // Consistent outer radius gives a rounded rect outline.
        shape = new RoundRectShape(new float[] { 10, 10, 10, 10, 10, 10, 10, 10 }, null, null);
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        shape.resize(100, 100);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertEquals(10.0f, outline.getRadius(), 0.01f);
        assertTrue(outline.getRect(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(100, rect.right);
        assertEquals(100, rect.bottom);

        // Inconsistent outer radius gives a path outline.
        shape = new RoundRectShape(new float[] { 10, 10, 0, 0, 0, 0, 0, 0 }, null, null);
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        shape.resize(100, 100);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        // Inset can't produce a valid outline, so the resulting behavior is
        // undefined. Just verify that it doesn't crash.
        shape = new RoundRectShape(new float[] { 10, 10, 0, 0, 0, 0, 0, 0 },
                new RectF(100, 100, 100, 100), new float[] { 10, 10, 0, 0, 0, 0, 0, 0 });
        shape.getOutline(outline);
    }
}
