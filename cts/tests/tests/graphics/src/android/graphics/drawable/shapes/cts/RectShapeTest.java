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
import android.graphics.drawable.shapes.RectShape;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RectShapeTest {
    private static final int TEST_WIDTH  = 100;
    private static final int TEST_HEIGHT = 200;

    private static final int TEST_COLOR_1 = 0xFF00FF00;
    private static final int TEST_COLOR_2 = 0xFFFF0000;

    @Test
    public void testConstructor() {
        new RectShape();
    }

    private void verifyDrawSuccessfully(Bitmap bitmap, int width, int height, int color) {
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                assertEquals(color, bitmap.getPixel(i, j));
            }
        }
    }

    @Test
    public void testDraw() {
        RectShape rectShape = new RectShape();
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Style.FILL);
        paint.setColor(TEST_COLOR_1);
        rectShape.resize(TEST_WIDTH, TEST_HEIGHT);

        rectShape.draw(canvas, paint);
        verifyDrawSuccessfully(bitmap, TEST_WIDTH, TEST_HEIGHT, TEST_COLOR_1);

        paint.setColor(TEST_COLOR_2);
        rectShape.draw(canvas, paint);
        verifyDrawSuccessfully(bitmap, TEST_WIDTH, TEST_HEIGHT, TEST_COLOR_2);
    }

    @Test
    public void testClone() throws CloneNotSupportedException {
        RectShape rectShape = new RectShape();
        rectShape.resize(100f, 200f);
        RectShape clonedShape = rectShape.clone();
        assertEquals(100f, rectShape.getWidth(), 0.0f);
        assertEquals(200f, rectShape.getHeight(), 0.0f);

        assertNotSame(rectShape, clonedShape);
        assertEquals(rectShape.getWidth(), clonedShape.getWidth(), 0.0f);
        assertEquals(rectShape.getHeight(), clonedShape.getHeight(), 0.0f);
    }

    @Test
    public void testRect() {
        MyRectShape mockRectShape = new MyRectShape();
        RectShape rectShape = mockRectShape;
        RectF rect = mockRectShape.myRect();
        assertEquals(0.0f, rect.left, 0.0f);
        assertEquals(0.0f, rect.top, 0.0f);
        assertEquals(0.0f, rect.right, 0.0f);
        assertEquals(0.0f, rect.bottom, 0.0f);

        rectShape.resize(TEST_WIDTH, TEST_HEIGHT);
        rect = mockRectShape.myRect();
        assertEquals(0.0f, rect.left, 0.0f);
        assertEquals(0.0f, rect.top, 0.0f);
        assertEquals((float) TEST_WIDTH, rect.right, 0.0f);
        assertEquals((float) TEST_HEIGHT, rect.bottom, 0.0f);
    }

    @Test
    public void testGetOutline() {
        Outline outline = new Outline();
        Rect rect = new Rect();
        RectShape shape = new RectShape();

        // Zero-size rect is empty.
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        // Non-zero rect is a rect.
        shape.resize(100, 100);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertEquals(0.0f, outline.getRadius(), 0.0f);
        assertTrue(outline.getRect(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(100, rect.right);
        assertEquals(100, rect.bottom);
    }

    private static class MyRectShape extends RectShape {
        public RectF myRect() {
            return super.rect();
        }
    }
}
