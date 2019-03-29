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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.DrawableContainer.DrawableContainerState;
import android.graphics.drawable.LevelListDrawable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DrawableContainerStateTest {
    private DrawableContainerState mDrawableContainerState;

    private DrawableContainer mDrawableContainer;

    @Before
    public void setup() {
        // DrawableContainerState has no public constructor. Obtain an instance through
        // LevelListDrawable.getConstants(). This is fine for testing the final methods of
        // DrawableContainerState.
        mDrawableContainer = new LevelListDrawable();
        mDrawableContainerState = (DrawableContainerState) mDrawableContainer.getConstantState();
        assertNotNull(mDrawableContainerState);
    }

    @Test(expected=NullPointerException.class)
    public void testAddChildNull() {
        mDrawableContainerState.addChild(null);
    }

    @Test
    public void testAddChild() {
        assertEquals(0, mDrawableContainerState.getChildCount());

        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        dr0.setVisible(true, false);
        assertTrue(dr0.isVisible());
        assertEquals(0, mDrawableContainerState.addChild(dr0));
        assertEquals(1, mDrawableContainerState.getChildCount());
        Drawable[] children = mDrawableContainerState.getChildren();
        assertNotNull(children);
        assertTrue(children.length >= 1);
        assertSame(dr0, children[0]);
        assertNull(children[1]);
        assertFalse(dr0.isVisible());

        Drawable dr1 = spy(new ColorDrawable(Color.BLUE));
        dr1.setVisible(true, false);
        assertTrue(dr1.isVisible());
        assertEquals(1, mDrawableContainerState.addChild(dr1));
        assertEquals(2, mDrawableContainerState.getChildCount());
        children = mDrawableContainerState.getChildren();
        assertNotNull(children);
        assertTrue(children.length >= 2);
        assertSame(dr0, children[0]);
        assertSame(dr1, children[1]);
        assertNull(children[2]);
        assertFalse(dr1.isVisible());

        // Add the same object twice, is it OK?
        assertEquals(2, mDrawableContainerState.addChild(dr1));
        assertEquals(3, mDrawableContainerState.getChildCount());
        children = mDrawableContainerState.getChildren();
        assertNotNull(children);
        assertTrue(children.length >= 3);
        assertSame(dr1, children[1]);
        assertSame(dr1, children[2]);
    }

    @Test
    public void testIsStateful() {
        assertEquals(0, mDrawableContainerState.getChildCount());
        assertFalse(mDrawableContainerState.isStateful());

        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(false).when(dr0).isStateful();
        mDrawableContainerState.addChild(dr0);
        assertEquals(1, mDrawableContainerState.getChildCount());
        assertFalse(mDrawableContainerState.isStateful());

        Drawable dr1 = spy(new ColorDrawable(Color.GREEN));
        doReturn(false).when(dr1).isStateful();
        mDrawableContainerState.addChild(dr1);
        assertEquals(2, mDrawableContainerState.getChildCount());
        assertFalse(mDrawableContainerState.isStateful());

        Drawable dr2 = spy(new ColorDrawable(Color.BLUE));
        doReturn(true).when(dr2).isStateful();
        mDrawableContainerState.addChild(dr2);
        assertEquals(3, mDrawableContainerState.getChildCount());
        assertTrue(mDrawableContainerState.isStateful());

        Drawable dr3 = spy(new ColorDrawable(Color.YELLOW));
        doReturn(false).when(dr3).isStateful();
        mDrawableContainerState.addChild(dr3);
        assertEquals(4, mDrawableContainerState.getChildCount());
        assertTrue(mDrawableContainerState.isStateful());
    }

    @Test
    public void testAccessEnterFadeDuration() {
        mDrawableContainerState.setEnterFadeDuration(1000);
        assertEquals(1000, mDrawableContainerState.getEnterFadeDuration());

        mDrawableContainerState.setEnterFadeDuration(-1000);
        assertEquals(-1000, mDrawableContainerState.getEnterFadeDuration());
    }

    @Test
    public void testAccessExitFadeDuration() {
        mDrawableContainerState.setExitFadeDuration(1000);
        assertEquals(1000, mDrawableContainerState.getExitFadeDuration());

        mDrawableContainerState.setExitFadeDuration(-1000);
        assertEquals(-1000, mDrawableContainerState.getExitFadeDuration());
    }

    @Test
    public void testAccessConstantSize() {
        mDrawableContainerState.setConstantSize(true);
        assertTrue(mDrawableContainerState.isConstantSize());

        mDrawableContainerState.setConstantSize(false);
        assertFalse(mDrawableContainerState.isConstantSize());
    }

    @Test
    public void testAccessConstantPadding() {
        mDrawableContainerState.setVariablePadding(true);
        assertNull(mDrawableContainerState.getConstantPadding());

        /*
         * TODO: the behavior of getConstantPadding when variable padding is
         * false is undefined
         *
        mDrawableContainerState.setVariablePadding(false);
        Rect padding = mDrawableContainerState.getConstantPadding();
        assertNotNull(padding);
        assertEquals(new Rect(0, 0, 0, 0), padding);

        MockDrawable dr0 = new MockDrawable();
        dr0.setPadding(new Rect(1, 2, 0, 0));
        mDrawableContainerState.addChild(dr0);
        padding = mDrawableContainerState.getConstantPadding();
        assertNotNull(padding);
        assertEquals(new Rect(1, 2, 0, 0), padding);

        MockDrawable dr1 = new MockDrawable();
        dr1.setPadding(new Rect(0, 0, 3, 4));
        mDrawableContainerState.addChild(dr1);
        padding = mDrawableContainerState.getConstantPadding();
        assertNotNull(padding);
        assertEquals(new Rect(1, 2, 3, 4), padding);

        mDrawableContainerState.setVariablePadding(true);
        assertNull(mDrawableContainerState.getConstantPadding());
        */
    }

    @Test
    public void testConstantHeightsAndWidths() {
        assertEquals(0, mDrawableContainerState.getChildCount());
        assertEquals(-1, mDrawableContainerState.getConstantHeight());
        assertEquals(-1, mDrawableContainerState.getConstantWidth());
        assertEquals(0, mDrawableContainerState.getConstantMinimumHeight());
        assertEquals(0, mDrawableContainerState.getConstantMinimumWidth());

        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(1).when(dr0).getMinimumHeight();
        doReturn(2).when(dr0).getMinimumWidth();
        doReturn(0).when(dr0).getIntrinsicHeight();
        doReturn(0).when(dr0).getIntrinsicWidth();
        mDrawableContainerState.addChild(dr0);
        assertEquals(1, mDrawableContainerState.getChildCount());
        assertEquals(0, mDrawableContainerState.getConstantHeight());
        assertEquals(0, mDrawableContainerState.getConstantWidth());
        assertEquals(1, mDrawableContainerState.getConstantMinimumHeight());
        assertEquals(2, mDrawableContainerState.getConstantMinimumWidth());

        Drawable dr1 = spy(new ColorDrawable(Color.BLUE));
        doReturn(0).when(dr1).getMinimumHeight();
        doReturn(0).when(dr1).getMinimumWidth();
        doReturn(3).when(dr1).getIntrinsicHeight();
        doReturn(4).when(dr1).getIntrinsicWidth();
        mDrawableContainerState.addChild(dr1);
        assertEquals(2, mDrawableContainerState.getChildCount());
        assertEquals(3, mDrawableContainerState.getConstantHeight());
        assertEquals(4, mDrawableContainerState.getConstantWidth());
        assertEquals(1, mDrawableContainerState.getConstantMinimumHeight());
        assertEquals(2, mDrawableContainerState.getConstantMinimumWidth());

        Drawable dr2 = spy(new ColorDrawable(Color.GREEN));
        doReturn(5).when(dr2).getMinimumHeight();
        doReturn(5).when(dr2).getMinimumWidth();
        doReturn(5).when(dr2).getIntrinsicHeight();
        doReturn(5).when(dr2).getIntrinsicWidth();
        mDrawableContainerState.addChild(dr2);
        assertEquals(3, mDrawableContainerState.getChildCount());
        assertEquals(5, mDrawableContainerState.getConstantHeight());
        assertEquals(5, mDrawableContainerState.getConstantWidth());
        assertEquals(5, mDrawableContainerState.getConstantMinimumHeight());
        assertEquals(5, mDrawableContainerState.getConstantMinimumWidth());
    }

    @Test
    public void testGetOpacity() {
        assertEquals(0, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.TRANSPARENT, mDrawableContainerState.getOpacity());

        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(PixelFormat.OPAQUE).when(dr0).getOpacity();
        mDrawableContainerState.addChild(dr0);
        assertEquals(1, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.OPAQUE, mDrawableContainerState.getOpacity());

        Drawable dr1 = spy(new ColorDrawable(Color.BLUE));
        doReturn(PixelFormat.TRANSPARENT).when(dr1).getOpacity();
        mDrawableContainerState.addChild(dr1);
        assertEquals(2, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.TRANSPARENT, mDrawableContainerState.getOpacity());

        Drawable dr2 = spy(new ColorDrawable(Color.GREEN));
        doReturn(PixelFormat.TRANSLUCENT).when(dr2).getOpacity();
        mDrawableContainerState.addChild(dr2);
        assertEquals(3, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.TRANSLUCENT, mDrawableContainerState.getOpacity());

        Drawable dr3 = spy(new ColorDrawable(Color.YELLOW));
        doReturn(PixelFormat.UNKNOWN).when(dr3).getOpacity();
        mDrawableContainerState.addChild(dr3);
        assertEquals(4, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.UNKNOWN, mDrawableContainerState.getOpacity());

        Drawable dr4 = spy(new ColorDrawable(Color.MAGENTA));
        doReturn(PixelFormat.TRANSLUCENT).when(dr4).getOpacity();
        mDrawableContainerState.addChild(dr4);
        assertEquals(5, mDrawableContainerState.getChildCount());
        assertEquals(PixelFormat.UNKNOWN, mDrawableContainerState.getOpacity());
    }

    @Test
    public void testCanConstantState() {
        DrawableContainer dr = new LevelListDrawable();
        DrawableContainerState cs = (DrawableContainerState) dr.getConstantState();
        assertTrue(cs.canConstantState());

        Drawable child = spy(new ColorDrawable(Color.RED));
        doReturn(null).when(child).getConstantState();
        cs.addChild(child);
        assertFalse(cs.canConstantState());
    }

    @Test
    public void testGrowArray() {
        DrawableContainer dr = new LevelListDrawable();
        DrawableContainerState cs = (DrawableContainerState) dr.getConstantState();

        // Default capacity is undefined, so pin it to 0.
        cs.growArray(0, 0);
        try {
            cs.getChild(10);
            fail("Expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // Yay!
        }

        cs.growArray(0, 10);
        cs.getChild(9);
    }
}
