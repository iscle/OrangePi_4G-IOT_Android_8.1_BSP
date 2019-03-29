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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link Gravity}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class GravityTest {
    private Rect mInRect;
    private Rect mOutRect;

    @Before
    public void setup() {
        mInRect = new Rect(1, 2, 3, 4);
        mOutRect = new Rect();
    }

    @Test
    public void testConstructor() {
        new Gravity();
    }

    private void applyGravity(int gravity, int w, int h, boolean bRtl) {
        final int layoutDirection = bRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        Gravity.apply(gravity, w, h, mInRect, mOutRect, layoutDirection);
    }

    @Test
    public void testApply() {
        mInRect = new Rect(10, 20, 30, 40);
        Gravity.apply(Gravity.TOP, 2, 3, mInRect, mOutRect);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(23, mOutRect.bottom);
        Gravity.apply(Gravity.TOP, 2, 3, mInRect, 5, 5, mOutRect);
        assertEquals(24, mOutRect.left);
        assertEquals(26, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(28, mOutRect.bottom);
        applyGravity(Gravity.TOP, 2, 3, false /* LTR direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(23, mOutRect.bottom);
        applyGravity(Gravity.TOP, 2, 3, true /* RTL direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(23, mOutRect.bottom);

        Gravity.apply(Gravity.BOTTOM, 2, 3, mInRect, mOutRect);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(37, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        Gravity.apply(Gravity.BOTTOM, 2, 3, mInRect, 5, 5, mOutRect);
        assertEquals(24, mOutRect.left);
        assertEquals(26, mOutRect.right);
        assertEquals(32, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.BOTTOM, 2, 3, false /* LTR direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(37, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.BOTTOM, 2, 3, true /* RTL direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(37, mOutRect.top);
        assertEquals(40, mOutRect.bottom);

        Gravity.apply(Gravity.LEFT, 2, 10, mInRect, mOutRect);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.LEFT, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(15, mOutRect.left);
        assertEquals(17, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.LEFT, 2, 10, false /* LTR direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.LEFT, 2, 10, true /* RTL direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.START, 2, 10, mInRect, mOutRect);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.START, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(15, mOutRect.left);
        assertEquals(17, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.START, 2, 10, false /* LTR direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.START, 2, 10, true /* RTL direction */);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.RIGHT, 2, 10, mInRect, mOutRect);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.RIGHT, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(23, mOutRect.left);
        assertEquals(25, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.RIGHT, 2, 10, false /* LTR direction */);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.RIGHT, 2, 10, true /* RTL direction */);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.END, 2, 10, mInRect, mOutRect);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.END, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(23, mOutRect.left);
        assertEquals(25, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.END, 2, 10, false /* LTR direction */);
        assertEquals(28, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.END, 2, 10, true /* RTL direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(12, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.CENTER_VERTICAL, 2, 10, mInRect, mOutRect);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.CENTER_VERTICAL, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(24, mOutRect.left);
        assertEquals(26, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.CENTER_VERTICAL, 2, 10, false /* LTR direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.CENTER_VERTICAL, 2, 10, true /* RTL direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.FILL_VERTICAL, 2, 10, mInRect, mOutRect);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        Gravity.apply(Gravity.FILL_VERTICAL, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(24, mOutRect.left);
        assertEquals(26, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(45, mOutRect.bottom);
        applyGravity(Gravity.FILL_VERTICAL, 2, 10, false /* LTR direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.FILL_VERTICAL, 2, 10, true /* RTL direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(20, mOutRect.top);
        assertEquals(40, mOutRect.bottom);

        Gravity.apply(Gravity.CENTER_HORIZONTAL, 2, 10, mInRect, mOutRect);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.CENTER_HORIZONTAL, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(24, mOutRect.left);
        assertEquals(26, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.CENTER_HORIZONTAL, 2, 10, false /* LTR direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.CENTER_HORIZONTAL, 2, 10, true /* RTL direction */);
        assertEquals(19, mOutRect.left);
        assertEquals(21, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);

        Gravity.apply(Gravity.FILL_HORIZONTAL, 2, 10, mInRect, mOutRect);
        assertEquals(10, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        Gravity.apply(Gravity.FILL_HORIZONTAL, 2, 10, mInRect, 5, 5, mOutRect);
        assertEquals(15, mOutRect.left);
        assertEquals(35, mOutRect.right);
        assertEquals(30, mOutRect.top);
        assertEquals(40, mOutRect.bottom);
        applyGravity(Gravity.FILL_HORIZONTAL, 2, 10, false /* LTR direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
        applyGravity(Gravity.FILL_HORIZONTAL, 2, 10, true /* RTL direction */);
        assertEquals(10, mOutRect.left);
        assertEquals(30, mOutRect.right);
        assertEquals(25, mOutRect.top);
        assertEquals(35, mOutRect.bottom);
    }

    @Test
    public void testIsVertical() {
        assertFalse(Gravity.isVertical(-1));
        assertTrue(Gravity.isVertical(Gravity.VERTICAL_GRAVITY_MASK));
        assertFalse(Gravity.isVertical(Gravity.NO_GRAVITY));
    }

    @Test
    public void testIsHorizontal() {
        assertFalse(Gravity.isHorizontal(-1));
        assertTrue(Gravity.isHorizontal(Gravity.HORIZONTAL_GRAVITY_MASK));
        assertTrue(Gravity.isHorizontal(Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK));
        assertFalse(Gravity.isHorizontal(Gravity.NO_GRAVITY));
    }

    @Test
    public void testApplyDisplay() {
        Rect display = new Rect(20, 30, 40, 50);
        Rect inoutRect = new Rect(10, 10, 30, 60);
        Gravity.applyDisplay(Gravity.DISPLAY_CLIP_VERTICAL, display, inoutRect);
        assertEquals(20, inoutRect.left);
        assertEquals(40, inoutRect.right);
        assertEquals(30, inoutRect.top);
        assertEquals(50, inoutRect.bottom);

        display = new Rect(20, 30, 40, 50);
        inoutRect = new Rect(10, 10, 30, 60);
        Gravity.applyDisplay(Gravity.DISPLAY_CLIP_HORIZONTAL, display, inoutRect);
        assertEquals(20, inoutRect.left);
        assertEquals(30, inoutRect.right);
        assertEquals(30, inoutRect.top);
        assertEquals(50, inoutRect.bottom);
    }

    @Test
    public void testGetAbsoluteGravity() {
        verifyOneGravity(Gravity.LEFT, Gravity.LEFT, false);
        verifyOneGravity(Gravity.LEFT, Gravity.LEFT, true);

        verifyOneGravity(Gravity.RIGHT, Gravity.RIGHT, false);
        verifyOneGravity(Gravity.RIGHT, Gravity.RIGHT, true);

        verifyOneGravity(Gravity.TOP, Gravity.TOP, false);
        verifyOneGravity(Gravity.TOP, Gravity.TOP, true);

        verifyOneGravity(Gravity.BOTTOM, Gravity.BOTTOM, false);
        verifyOneGravity(Gravity.BOTTOM, Gravity.BOTTOM, true);

        verifyOneGravity(Gravity.CENTER_VERTICAL, Gravity.CENTER_VERTICAL, false);
        verifyOneGravity(Gravity.CENTER_VERTICAL, Gravity.CENTER_VERTICAL, true);

        verifyOneGravity(Gravity.CENTER_HORIZONTAL, Gravity.CENTER_HORIZONTAL, false);
        verifyOneGravity(Gravity.CENTER_HORIZONTAL, Gravity.CENTER_HORIZONTAL, true);

        verifyOneGravity(Gravity.CENTER, Gravity.CENTER, false);
        verifyOneGravity(Gravity.CENTER, Gravity.CENTER, true);

        verifyOneGravity(Gravity.FILL_VERTICAL, Gravity.FILL_VERTICAL, false);
        verifyOneGravity(Gravity.FILL_VERTICAL, Gravity.FILL_VERTICAL, true);

        verifyOneGravity(Gravity.FILL_HORIZONTAL, Gravity.FILL_HORIZONTAL, false);
        verifyOneGravity(Gravity.FILL_HORIZONTAL, Gravity.FILL_HORIZONTAL, true);

        verifyOneGravity(Gravity.FILL, Gravity.FILL, false);
        verifyOneGravity(Gravity.FILL, Gravity.FILL, true);

        verifyOneGravity(Gravity.CLIP_HORIZONTAL, Gravity.CLIP_HORIZONTAL, false);
        verifyOneGravity(Gravity.CLIP_HORIZONTAL, Gravity.CLIP_HORIZONTAL, true);

        verifyOneGravity(Gravity.CLIP_VERTICAL, Gravity.CLIP_VERTICAL, false);
        verifyOneGravity(Gravity.CLIP_VERTICAL, Gravity.CLIP_VERTICAL, true);

        verifyOneGravity(Gravity.LEFT, Gravity.START, false);
        verifyOneGravity(Gravity.RIGHT, Gravity.START, true);

        verifyOneGravity(Gravity.RIGHT, Gravity.END, false);
        verifyOneGravity(Gravity.LEFT, Gravity.END, true);
    }

    private void verifyOneGravity(int expected, int initial, boolean isRtl) {
        final int layoutDirection = isRtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;

        assertEquals(expected, Gravity.getAbsoluteGravity(initial, layoutDirection));
    }
}
