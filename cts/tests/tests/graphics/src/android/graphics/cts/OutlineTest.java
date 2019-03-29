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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OutlineTest {
    @Test
    public void testDefaults() {
        Outline outline = new Outline();

        assertEquals(0.0f, outline.getAlpha(), 0.0f);
        assertTrue(outline.isEmpty());
        Rect outRect = new Rect();
        assertFalse(outline.getRect(outRect));
        assertTrue(outline.getRadius() < 0);
    }

    @Test
    public void testCopyConstructor() {
        Outline orig = new Outline();
        orig.setAlpha(0.5f);
        orig.setRect(10, 10, 20, 20);

        Outline copy = new Outline(orig);
        assertEquals(0.5f, copy.getAlpha(), 0.0f);

        Rect copyRect = new Rect();
        copy.getRect(copyRect);
        assertEquals(new Rect(10, 10, 20, 20), copyRect);
    }

    @Test
    public void testGetSetAlpha() {
        Outline outline = new Outline();

        outline.setAlpha(1.0f);
        assertEquals(1.0f, outline.getAlpha(), 0.0f);

        outline.setAlpha(0.0f);
        assertEquals(0.0f, outline.getAlpha(), 0.0f);

        outline.setAlpha(0.45f);
        assertEquals(0.45f, outline.getAlpha(), 0.0f);

        // define out of range getter/setter behavior: (note will be clamped in native when consumed)
        outline.setAlpha(4f);
        assertEquals(4f, outline.getAlpha(), 0.0f);
        outline.setAlpha(-30f);
        assertEquals(-30f, outline.getAlpha(), 0.0f);
    }

    @Test
    public void testSet() {
        Outline orig = new Outline();
        orig.setAlpha(0.5f);
        orig.setRect(10, 10, 20, 20);

        Outline copy = new Outline();
        copy.set(orig);
        assertEquals(0.5f, copy.getAlpha(), 0.0f);

        Rect copyRect = new Rect();
        copy.getRect(copyRect);
        assertEquals(new Rect(10, 10, 20, 20), copyRect);
    }

    @Test
    public void testSetRect() {
        Outline outline = new Outline();
        Rect outRect = new Rect();

        outline.setRect(0, 0, 0, 0);
        assertTrue(outline.isEmpty());

        outline.setRect(10, 5, 4, 5);
        assertTrue(outline.isEmpty());

        outline.setRect(new Rect());
        assertTrue(outline.isEmpty());

        outline.setRect(10, 10, 20, 20);
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(10, 10, 20, 20), outRect);
        assertTrue(outline.canClip());

        outline.setRect(new Rect(10, 10, 20, 20));
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(10, 10, 20, 20), outRect);
        assertTrue(outline.canClip());
    }

    @Test
    public void testSetRoundRect() {
        Outline outline = new Outline();
        Rect outRect = new Rect();

        outline.setRoundRect(0, 0, 0, 0, 1f);
        assertTrue(outline.isEmpty());

        outline.setRoundRect(10, 5, 4, 5, 1f);
        assertTrue(outline.isEmpty());

        outline.setRoundRect(new Rect(), 1f);
        assertTrue(outline.isEmpty());

        outline.setRoundRect(10, 10, 20, 20, 5f);
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(10, 10, 20, 20), outRect);
        assertEquals(5f, outline.getRadius(), 0.0f);
        assertTrue(outline.canClip());

        outline.setRoundRect(new Rect(10, 10, 20, 20), 4f);
        assertFalse(outline.isEmpty());
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(10, 10, 20, 20), outRect);
        assertEquals(4f, outline.getRadius(), 0.0f);
        assertTrue(outline.canClip());
    }

    @Test
    public void testSetOval() {
        Outline outline = new Outline();

        outline.setOval(0, 0, 0, 0);
        assertTrue(outline.isEmpty());

        outline.setOval(10, 5, 4, 5);
        assertTrue(outline.isEmpty());

        Rect outRect = new Rect();
        outline.setOval(0, 0, 50, 51); // different x & y radii, so not round rect
        assertFalse(outline.getRect(outRect)); // not round rect, doesn't work
        assertFalse(outline.canClip()); // not round rect, doesn't work
        assertFalse(outline.isEmpty());

        outline.setOval(0, 0, 50, 50); // same x & y radii, so round rect
        assertTrue(outline.getRect(outRect)); // is round rect, so works
        assertTrue(outline.canClip()); // is round rect, so works
        assertFalse(outline.isEmpty());

        outline.setOval(new Rect(0, 0, 50, 50)); // same x & y radii, so round rect
        assertTrue(outline.getRect(outRect)); // is round rect, so works
        assertTrue(outline.canClip()); // is round rect, so works
        assertFalse(outline.isEmpty());
    }

    @Test
    public void testSetConvexPath() {
        Outline outline = new Outline();
        Path path = new Path();

        assertTrue(path.isEmpty());
        outline.setConvexPath(path);
        assertTrue(outline.isEmpty());

        path.addCircle(50, 50, 50, Path.Direction.CW);
        outline.setConvexPath(path);
        assertFalse(outline.isEmpty());
    }

    @Test
    public void testGetRectRadius() {
        Outline outline = new Outline();

        Rect outRect = new Rect();
        outline.setRoundRect(15, 10, 45, 40, 30.0f);
        assertEquals(30.0f, outline.getRadius(), 0.0f);
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(15, 10, 45, 40), outRect);

        outline.setRect(5, 10, 15, 20);
        assertEquals(0.0f, outline.getRadius(), 0.0f);
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(5, 10, 15, 20), outRect);

        outline.setOval(0, 0, 50, 60);
        assertTrue(outline.getRadius() < 0);
        assertFalse(outline.getRect(outRect));
    }

    @Test
    public void testOffset() {
        Outline outline = new Outline();

        Rect outRect = new Rect();
        outline.setRoundRect(15, 10, 45, 40, 30.0f);
        outline.offset(-15, -10);
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(0, 0, 30, 30), outRect);

        outline.setRect(5, 10, 15, 20);
        outline.offset(-5, -10);
        assertTrue(outline.getRect(outRect));
        assertEquals(new Rect(0, 0, 10, 10), outRect);
    }
}
