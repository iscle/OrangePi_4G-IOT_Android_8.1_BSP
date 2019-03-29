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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.shapes.OvalShape;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OvalShapeTest {
    private static final int TEST_WIDTH  = 100;
    private static final int TEST_HEIGHT = 200;

    private static final int TEST_COLOR_1 = 0xFF00FF00;
    private static final int TEST_COLOR_2 = 0xFFFF0000;

    private static final int TOLERANCE = 4; // tolerance in pixels

    @Test
    public void testConstructor() {
        new OvalShape();
    }

    @Test
    public void testDraw() {
        OvalShape ovalShape = new OvalShape();
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Style.FILL);
        paint.setColor(TEST_COLOR_1);
        ovalShape.resize(TEST_WIDTH, TEST_HEIGHT);

        ovalShape.draw(canvas, paint);
        // check the color at the center of bitmap
        assertEquals(TEST_COLOR_1, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 2));

        final int SQUARE = Math.min(TEST_WIDTH, TEST_HEIGHT);
        paint.setColor(TEST_COLOR_2);
        ovalShape.resize(SQUARE, SQUARE); // circle
        ovalShape.draw(canvas, paint);
        // count number of pixels with TEST_COLOR_2 along diagonal
        int count = 0;
        for (int i = 0; i < SQUARE; i++) {
            if (bitmap.getPixel(i, i) == TEST_COLOR_2) {
                count += 1;
            }
        }
        assertEquals((double)SQUARE / Math.sqrt(2), count, TOLERANCE);
    }

    @Test
    public void testGetOutline() {
        Outline outline = new Outline();
        Rect rect = new Rect();
        OvalShape shape;

        // Zero-sized oval yields an empty outline.
        shape = new OvalShape();
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));

        // Non-zero oval yields a rounded rect.
        shape.resize(100, 100);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertEquals(50.0f, outline.getRadius(), 0.01f);
        assertTrue(outline.getRect(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(100, rect.right);
        assertEquals(100, rect.bottom);

        // Non-circular oval yields a path.
        shape.resize(100, 200);
        shape.getOutline(outline);
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(rect));
    }

    @Test
    public void testClone() throws Exception {
        OvalShape shape = new OvalShape();
        shape.resize(100, 100);

        OvalShape clone = shape.clone();
        assertNotNull(clone);
        assertEquals(shape.getWidth(), clone.getWidth(), 0.0f);
        assertEquals(shape.getHeight(), clone.getHeight(), 0.0f);
    }
}
