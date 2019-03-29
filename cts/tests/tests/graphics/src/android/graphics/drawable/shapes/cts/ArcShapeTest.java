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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.shapes.ArcShape;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ArcShapeTest {
    private static final int TEST_WIDTH = 100;
    private static final int TEST_HEIGHT = 200;

    private static final int TEST_COLOR_1 = 0xFF00FF00;
    private static final int TEST_COLOR_2 = 0xFFFF0000;

    private static final int TOLERANCE = 4; // tolerance in pixels

    @Test
    public void testConstructor() {
        new ArcShape(1f, 5f);

        new ArcShape(0f, 0f);

        new ArcShape(-1f, -1f);
    }

    @Test
    public void testGetSweepAngle() {
        ArcShape shape = new ArcShape(100.0f, 360.0f);
        assertEquals(360.0f, shape.getSweepAngle(), 0.0f);
    }

    @Test
    public void testGetStartAngle() {
        ArcShape shape = new ArcShape(100.0f, 360.0f);
        assertEquals(100.0f, shape.getStartAngle(), 0.0f);
    }

    @Test
    public void testDraw() {
        // draw completely.
        ArcShape arcShape = new ArcShape(0.0f, 360.0f);
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Style.FILL);
        paint.setColor(TEST_COLOR_1);
        arcShape.resize(TEST_WIDTH, TEST_HEIGHT);

        arcShape.draw(canvas, paint);
        // check the color at the center of bitmap
        assertEquals(TEST_COLOR_1, bitmap.getPixel(TEST_WIDTH / 2, TEST_HEIGHT / 2));

        final int SQUARE = Math.min(TEST_WIDTH, TEST_HEIGHT);
        paint.setColor(TEST_COLOR_2);
        arcShape = new ArcShape(0.0f, 180.0f);
        arcShape.resize(SQUARE, SQUARE); // half circle
        arcShape.draw(canvas, paint);
        // count number of pixels with TEST_COLOR_2 along diagonal
        int count = 0;
        for (int i = 0; i < SQUARE; i++) {
            if (bitmap.getPixel(i, i) == TEST_COLOR_2) {
                count += 1;
            }
        }
        assertEquals((double) SQUARE / 2 / Math.sqrt(2), count, TOLERANCE);
    }

    @Test
    public void testGetOutline() {
        Outline outline = new Outline();
        ArcShape shape;

        // This is a no-op. Just make sure it doesn't crash.
        outline.setEmpty();
        shape = new ArcShape(0.0f, 360.0f);
        shape.getOutline(outline);
        assertTrue(outline.isEmpty());
    }

    @Test
    public void testClone() throws Exception {
        ArcShape shape = new ArcShape(0.0f, 360.0f);
        ArcShape clone = shape.clone();
        assertNotNull(clone);
        assertEquals(0.0f, clone.getStartAngle(), 0.0f);
        assertEquals(360.0f, clone.getSweepAngle(), 0.0f);
    }
}
