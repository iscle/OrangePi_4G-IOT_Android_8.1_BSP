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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
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
import org.mockito.invocation.InvocationOnMock;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DrawableContainerTest {
    private DrawableContainerState mDrawableContainerState;

    private MockDrawableContainer mMockDrawableContainer;
    private DrawableContainer mDrawableContainer;

    @Before
    public void setup() {
        // DrawableContainerState has no public constructor. Obtain an instance through
        // LevelListDrawable.getConstants(). This is fine for testing the final methods of
        // DrawableContainerState.
        mDrawableContainerState =
            (DrawableContainerState) new LevelListDrawable().getConstantState();
        assertNotNull(mDrawableContainerState);

        mMockDrawableContainer = new MockDrawableContainer();
        // While the two fields point to the same object, the second one is there to
        // workaround the bug in CTS coverage tool that is not recognizing calls on
        // subclasses.
        mDrawableContainer = mMockDrawableContainer;

        assertNull(mDrawableContainer.getCurrent());
    }

    @Test(expected=NullPointerException.class)
    public void testConstantStateNotSet() {
        // This should throw NPE since our mock container has not been configured with
        // constant state yet
        mDrawableContainer.getConstantState();
    }

    @Test
    public void testDraw() {
        mDrawableContainer.draw(null);
        mDrawableContainer.draw(new Canvas());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr = spy(new ColorDrawable(Color.WHITE));
        addAndSelectDrawable(dr);

        reset(dr);
        doNothing().when(dr).draw(any());
        mDrawableContainer.draw(null);
        verify(dr, times(1)).draw(any());

        reset(dr);
        doNothing().when(dr).draw(any());
        mDrawableContainer.draw(new Canvas());
        verify(dr, times(1)).draw(any());
    }

    @Test
    public void testSetEnterFadeDuration() {
        verifySetEnterFadeDuration(1000);
        verifySetEnterFadeDuration(0);
    }

    private void verifySetEnterFadeDuration(int enterFadeDuration) {
        DrawableContainer container = new LevelListDrawable();
        DrawableContainerState cs = ((DrawableContainerState) container.getConstantState());
        container.setEnterFadeDuration(enterFadeDuration);
        assertEquals(enterFadeDuration, cs.getEnterFadeDuration());
    }

    @Test
    public void testSetExitFadeDuration() {
        verifySetExitFadeDuration(1000);
        verifySetExitFadeDuration(0);
    }

    private void verifySetExitFadeDuration(int exitFadeDuration) {
        DrawableContainer container = new LevelListDrawable();
        DrawableContainerState cs = ((DrawableContainerState) container.getConstantState());
        container.setExitFadeDuration(exitFadeDuration);
        assertEquals(exitFadeDuration, cs.getExitFadeDuration());
    }

    @Test(expected=NullPointerException.class)
    public void testGetChangingConfigurationsNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getChangingConfigurations();
    }

    @Test
    public void testGetChangingConfigurations() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr0 = new MockDrawable();
        dr0.setChangingConfigurations(0x001);
        mDrawableContainerState.addChild(dr0);
        MockDrawable dr1 = new MockDrawable();
        dr1.setChangingConfigurations(0x010);
        mDrawableContainerState.addChild(dr1);
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());

        // can not set mDrawableContainerState's ChangingConfigurations
        mDrawableContainer.setChangingConfigurations(0x100);
        assertEquals(0x111 | mDrawableContainerState.getChangingConfigurations(),
                mDrawableContainer.getChangingConfigurations());
    }

    @Test(expected=NullPointerException.class)
    public void testGetPaddingNoConstantState() {
        Rect result = new Rect(1, 1, 1, 1);
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getPadding(result);
    }

    @Test
    public void testGetPadding() {
        Rect result = new Rect(1, 1, 1, 1);

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.BLUE));
        doAnswer((InvocationOnMock invocation) -> {
            Rect target = (Rect) invocation.getArguments() [0];
            target.set(1, 2, 0, 0);
            return true;
        }).when(dr0).getPadding(any());
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.RED));
        doAnswer((InvocationOnMock invocation) -> {
            Rect target = (Rect) invocation.getArguments() [0];
            target.set(0, 0, 3, 4);
            return true;
        }).when(dr1).getPadding(any());
        mDrawableContainerState.addChild(dr1);
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());

        // use the current drawable's padding
        mDrawableContainerState.setVariablePadding(true);
        assertNull(mDrawableContainerState.getConstantPadding());
        assertTrue(mDrawableContainer.getPadding(result));
        assertEquals(new Rect(1, 2, 0, 0), result);

        // use constant state's padding
        mDrawableContainerState.setVariablePadding(false);
        assertNotNull(mDrawableContainerState.getConstantPadding());
        assertTrue(mDrawableContainer.getPadding(result));
        assertEquals(mDrawableContainerState.getConstantPadding(), result);

        // use default padding
        mDrawableContainer.selectDrawable(-1);
        assertNull(mDrawableContainer.getCurrent());
        mDrawableContainerState.setVariablePadding(true);
        assertNull(mDrawableContainerState.getConstantPadding());
        assertFalse(mDrawableContainer.getPadding(result));
        assertEquals(new Rect(0, 0, 0, 0), result);

        try {
            mDrawableContainer.getPadding(null);
            fail("Should throw NullPointerException if the padding is null.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testSetAlpha() {
        mDrawableContainer.setAlpha(0);

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable mockDrawable = spy(new ColorDrawable(Color.BLACK));
        addAndSelectDrawable(mockDrawable);

        // call current drawable's setAlpha if alpha is changed.
        reset(mockDrawable);
        mDrawableContainer.setAlpha(1);
        verify(mockDrawable, times(1)).setAlpha(1);

        // does not call it if alpha is not changed.
        reset(mockDrawable);
        mDrawableContainer.setAlpha(1);
        verify(mockDrawable, never()).setAlpha(anyInt());
    }

    @Test
    public void testSetDither() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        mDrawableContainer.setDither(false);
        mDrawableContainer.setDither(true);

        Drawable dr = spy(new ColorDrawable(Color.BLUE));
        addAndSelectDrawable(dr);

        // call current drawable's setDither if dither is changed.
        reset(dr);
        mDrawableContainer.setDither(false);
        verify(dr, times(1)).setDither(false);

        // does not call it if dither is not changed.
        reset(dr);
        mDrawableContainer.setDither(true);
        verify(dr, times(1)).setDither(true);
    }

    @Test
    public void testSetHotspotBounds() {
        Rect bounds = new Rect(10, 15, 100, 150);
        assertNull(mDrawableContainer.getCurrent());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);

        MockDrawable dr = new MockDrawable();
        addAndSelectDrawable(dr);

        dr.reset();
        mDrawableContainer.setHotspotBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
        Rect outRect = new Rect();
        mDrawableContainer.getHotspotBounds(outRect);
        assertEquals(bounds, outRect);

        dr.reset();
    }

    @Test
    public void testGetHotspotBounds() {
        Rect bounds = new Rect(10, 15, 100, 150);
        assertNull(mDrawableContainer.getCurrent());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);

        MockDrawable dr = new MockDrawable();
        addAndSelectDrawable(dr);

        dr.reset();
        mDrawableContainer.setHotspotBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
        Rect outRect = new Rect();
        mDrawableContainer.getHotspotBounds(outRect);
        assertEquals(bounds, outRect);

        dr.reset();
    }

    @Test
    public void testSetColorFilter() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        mDrawableContainer.setColorFilter(null);
        mDrawableContainer.setColorFilter(new ColorFilter());

        Drawable mockDrawable = spy(new ColorDrawable(Color.MAGENTA));
        addAndSelectDrawable(mockDrawable);

        // call current drawable's setColorFilter if filter is changed.
        reset(mockDrawable);
        mDrawableContainer.setColorFilter(null);
        verify(mockDrawable, times(1)).setColorFilter(null);

        // does not call it if filter is not changed.
        reset(mockDrawable);
        mDrawableContainer.setColorFilter(new ColorFilter());
        verify(mockDrawable, times(1)).setColorFilter(any());
    }

    @Test
    public void testSetTint() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        mDrawableContainer.setTint(Color.BLACK);
        mDrawableContainer.setTintMode(Mode.SRC_OVER);

        Drawable dr = spy(new ColorDrawable(Color.GREEN));
        addAndSelectDrawable(dr);

        verify(dr, times(1)).setTintMode(Mode.SRC_OVER);

        mDrawableContainer.setTintList(null);
        mDrawableContainer.setTintMode(null);
        verify(dr, times(1)).setTintMode(null);
    }

    @Test
    public void testOnBoundsChange() {
        mMockDrawableContainer.onBoundsChange(new Rect());
        mMockDrawableContainer.onBoundsChange(null);

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr = new MockDrawable();
        dr.setBounds(new Rect());
        addAndSelectDrawable(dr);

        // set current drawable's bounds.
        dr.reset();
        assertEquals(new Rect(), dr.getBounds());
        mMockDrawableContainer.onBoundsChange(new Rect(1, 1, 1, 1));
        assertTrue(dr.hasOnBoundsChangedCalled());
        assertEquals(new Rect(1, 1, 1, 1), dr.getBounds());

        dr.reset();
        mMockDrawableContainer.onBoundsChange(new Rect(1, 1, 1, 1));
        assertFalse(dr.hasOnBoundsChangedCalled());
        assertEquals(new Rect(1, 1, 1, 1), dr.getBounds());

        try {
            mMockDrawableContainer.onBoundsChange(null);
            fail("Should throw NullPointerException if the bounds is null.");
        } catch (NullPointerException e) {
        }
    }

    @Test(expected=NullPointerException.class)
    public void testIsStatefulNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.isStateful();
    }

    @Test
    public void testIsStateful() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.YELLOW));
        doReturn(true).when(dr0).isStateful();
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.GREEN));
        doReturn(false).when(dr1).isStateful();
        mDrawableContainerState.addChild(dr1);

        // return result of constant state's isStateful
        assertEquals(mDrawableContainerState.isStateful(), mDrawableContainer.isStateful());
        assertEquals(true, mDrawableContainer.isStateful());

        mDrawableContainer.selectDrawable(1);
        assertEquals(mDrawableContainerState.isStateful(), mDrawableContainer.isStateful());
        assertEquals(true, mDrawableContainer.isStateful());
    }

    @Test
    public void testOnStateChange() {
        assertFalse(mMockDrawableContainer.onStateChange(new int[] { 0 }));
        assertFalse(mMockDrawableContainer.onStateChange(null));

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr = new MockDrawable();
        dr.setState(new int[] { 0 });
        addAndSelectDrawable(dr);

        // set current drawable's state.
        dr.reset();
        assertNotNull(dr.getState());
        mMockDrawableContainer.onStateChange(null);
        assertTrue(dr.hasOnStateChangedCalled());
        assertNull(dr.getState());

        dr.reset();
        mMockDrawableContainer.onStateChange(new int[] { 0 });
        assertTrue(dr.hasOnStateChangedCalled());
        assertTrue(Arrays.equals(new int[] { 0 }, dr.getState()));

        dr.reset();
        assertFalse(mMockDrawableContainer.onStateChange(new int[] { 0 }));
        assertFalse(dr.hasOnStateChangedCalled());
        assertTrue(Arrays.equals(new int[] { 0 }, dr.getState()));
    }

    @Test
    public void testOnLevelChange() {
        assertFalse(mMockDrawableContainer.onLevelChange(Integer.MAX_VALUE));
        assertFalse(mMockDrawableContainer.onLevelChange(Integer.MIN_VALUE));

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr = new MockDrawable();
        dr.setLevel(0);
        addAndSelectDrawable(dr);

        // set current drawable's level.
        dr.reset();
        assertEquals(0, dr.getLevel());
        mMockDrawableContainer.onLevelChange(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, dr.getLevel());
        assertTrue(dr.hasOnLevelChangedCalled());

        dr.reset();
        assertEquals(Integer.MAX_VALUE, dr.getLevel());
        mMockDrawableContainer.onLevelChange(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, dr.getLevel());
        assertTrue(dr.hasOnLevelChangedCalled());

        dr.reset();
        assertEquals(Integer.MIN_VALUE, dr.getLevel());
        assertFalse(mMockDrawableContainer.onLevelChange(Integer.MIN_VALUE));
        assertEquals(Integer.MIN_VALUE, dr.getLevel());
        assertFalse(dr.hasOnLevelChangedCalled());
    }

    @Test(expected=NullPointerException.class)
    public void testGetIntrinsicWidthNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getIntrinsicWidth();
    }

    @Test
    public void testGetIntrinsicWidth() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(1).when(dr0).getIntrinsicWidth();
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.GREEN));
        doReturn(2).when(dr1).getIntrinsicWidth();
        mDrawableContainerState.addChild(dr1);

        // return result of constant state's getConstantWidth
        mDrawableContainerState.setConstantSize(true);
        assertEquals(mDrawableContainerState.getConstantWidth(),
                mDrawableContainer.getIntrinsicWidth());
        assertEquals(2, mDrawableContainer.getIntrinsicWidth());

        // return default value
        mDrawableContainerState.setConstantSize(false);
        assertNull(mDrawableContainer.getCurrent());
        assertEquals(-1, mDrawableContainer.getIntrinsicWidth());

        // return current drawable's getIntrinsicWidth
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());
        assertEquals(1, mDrawableContainer.getIntrinsicWidth());
    }

    @Test(expected=NullPointerException.class)
    public void testGetIntrinsicHeightNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getIntrinsicHeight();
    }

    @Test
    public void testGetIntrinsicHeight() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(1).when(dr0).getIntrinsicHeight();
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.GREEN));
        doReturn(2).when(dr1).getIntrinsicHeight();
        mDrawableContainerState.addChild(dr1);

        // return result of constant state's getConstantHeight
        mDrawableContainerState.setConstantSize(true);
        assertEquals(mDrawableContainerState.getConstantHeight(),
                mDrawableContainer.getIntrinsicHeight());
        assertEquals(2, mDrawableContainer.getIntrinsicHeight());

        // return default value
        mDrawableContainerState.setConstantSize(false);
        assertNull(mDrawableContainer.getCurrent());
        assertEquals(-1, mDrawableContainer.getIntrinsicHeight());

        // return current drawable's getIntrinsicHeight
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());
        assertEquals(1, mDrawableContainer.getIntrinsicHeight());
    }

    @Test(expected=NullPointerException.class)
    public void testGetMinimumWidthNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getMinimumWidth();
    }

    @Test
    public void testGetMinimumWidth() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(1).when(dr0).getMinimumWidth();
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.RED));
        doReturn(2).when(dr1).getMinimumWidth();
        mDrawableContainerState.addChild(dr1);

        // return result of constant state's getConstantMinimumWidth
        mDrawableContainerState.setConstantSize(true);
        assertEquals(mDrawableContainerState.getConstantMinimumWidth(),
                mDrawableContainer.getMinimumWidth());
        assertEquals(2, mDrawableContainer.getMinimumWidth());

        // return default value
        mDrawableContainerState.setConstantSize(false);
        assertNull(mDrawableContainer.getCurrent());
        assertEquals(0, mDrawableContainer.getMinimumWidth());

        // return current drawable's getMinimumWidth
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());
        assertEquals(1, mDrawableContainer.getMinimumWidth());
    }

    @Test(expected=NullPointerException.class)
    public void testGetMinimumHeightNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.getMinimumHeight();
    }

    @Test
    public void testGetMinimumHeight() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.RED));
        doReturn(1).when(dr0).getMinimumHeight();
        mDrawableContainerState.addChild(dr0);
        Drawable dr1 = spy(new ColorDrawable(Color.GREEN));
        doReturn(2).when(dr1).getMinimumHeight();
        mDrawableContainerState.addChild(dr1);

        // return result of constant state's getConstantMinimumHeight
        mDrawableContainerState.setConstantSize(true);
        assertEquals(mDrawableContainerState.getConstantMinimumHeight(),
                mDrawableContainer.getMinimumHeight());
        assertEquals(2, mDrawableContainer.getMinimumHeight());

        // return default value
        mDrawableContainerState.setConstantSize(false);
        assertNull(mDrawableContainer.getCurrent());
        assertEquals(0, mDrawableContainer.getMinimumHeight());

        // return current drawable's getMinimumHeight
        mDrawableContainer.selectDrawable(0);
        assertSame(dr0, mDrawableContainer.getCurrent());
        assertEquals(1, mDrawableContainer.getMinimumHeight());
    }

    @Test
    public void testInvalidateDrawable() {
        mDrawableContainer.setCallback(null);
        mDrawableContainer.invalidateDrawable(mDrawableContainer);
        mDrawableContainer.invalidateDrawable(null);

        Drawable.Callback callback = mock(Drawable.Callback.class);
        mDrawableContainer.setCallback(callback);

        mDrawableContainer.invalidateDrawable(mDrawableContainer);
        verify(callback, never()).invalidateDrawable(any());

        // the callback method can be called if the drawable passed in and the
        // current drawable are both null
        mDrawableContainer.invalidateDrawable(null);
        verify(callback, times(1)).invalidateDrawable(any());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable mockDrawable = new MockDrawable();
        addAndSelectDrawable(mockDrawable);

        reset(callback);
        mDrawableContainer.invalidateDrawable(mDrawableContainer);
        verify(callback, never()).invalidateDrawable(any());

        mDrawableContainer.invalidateDrawable(null);
        verify(callback, never()).invalidateDrawable(any());

        // Call the callback method if the drawable is selected.
        mDrawableContainer.invalidateDrawable(mockDrawable);
        verify(callback, times(1)).invalidateDrawable(any());
    }

    @Test
    public void testScheduleDrawable() {
        mDrawableContainer.setCallback(null);
        mDrawableContainer.scheduleDrawable(mDrawableContainer, null, 0);
        mDrawableContainer.scheduleDrawable(null, () -> {}, 0);

        Drawable.Callback callback = mock(Drawable.Callback.class);
        mDrawableContainer.setCallback(callback);

        mDrawableContainer.scheduleDrawable(mDrawableContainer, null, 0);
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());

        // the callback method can be called if the drawable passed in and the
        // current drawble are both null
        mDrawableContainer.scheduleDrawable(null, () -> {}, 0);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable mockDrawable = new MockDrawable();
        addAndSelectDrawable(mockDrawable);

        reset(callback);
        mDrawableContainer.scheduleDrawable(mDrawableContainer, null, 0);
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());

        mDrawableContainer.scheduleDrawable(null, () -> {}, 0);
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());

        // Call the callback method if the drawable is selected.
        mDrawableContainer.scheduleDrawable(mockDrawable, null, 0);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());
    }

    @Test
    public void testUnscheduleDrawable() {
        mDrawableContainer.setCallback(null);
        mDrawableContainer.unscheduleDrawable(mDrawableContainer, null);
        mDrawableContainer.unscheduleDrawable(null, () -> {});

        Drawable.Callback callback = mock(Drawable.Callback.class);
        mDrawableContainer.setCallback(callback);

        mDrawableContainer.unscheduleDrawable(mDrawableContainer, null);
        verify(callback, never()).unscheduleDrawable(any(), any());

        // the callback method can be called if the drawable passed in and the
        // current drawble are both null
        mDrawableContainer.unscheduleDrawable(null, () -> {});
        verify(callback, times(1)).unscheduleDrawable(any(), any());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable mockDrawable = new MockDrawable();
        addAndSelectDrawable(mockDrawable);

        reset(callback);
        mDrawableContainer.unscheduleDrawable(mDrawableContainer, null);
        verify(callback, never()).unscheduleDrawable(any(), any());

        mDrawableContainer.unscheduleDrawable(null, () -> {});
        verify(callback, never()).unscheduleDrawable(any(), any());

        // Call the callback method if the drawable is selected.
        mDrawableContainer.unscheduleDrawable(mockDrawable, null);
        verify(callback, times(1)).unscheduleDrawable(any(), any());
    }

    @Test
    public void testSetVisible() {
        assertTrue(mDrawableContainer.isVisible());
        assertFalse(mDrawableContainer.setVisible(true, false));
        assertTrue(mDrawableContainer.setVisible(false, false));
        assertFalse(mDrawableContainer.setVisible(false, false));
        assertTrue(mDrawableContainer.setVisible(true, false));

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr = new MockDrawable();
        addAndSelectDrawable(dr);

        // set current drawable's visibility
        assertTrue(mDrawableContainer.isVisible());
        assertTrue(dr.isVisible());
        assertTrue(mDrawableContainer.setVisible(false, false));
        assertFalse(mDrawableContainer.isVisible());
        assertFalse(dr.isVisible());
    }

    @Test
    public void testGetOpacity() {
        // there is no child, so the container is transparent
        assertEquals(PixelFormat.TRANSPARENT, mDrawableContainer.getOpacity());

        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.GREEN));
        doReturn(PixelFormat.OPAQUE).when(dr0).getOpacity();
        mDrawableContainerState.addChild(dr0);
        // no child selected yet
        assertEquals(PixelFormat.TRANSPARENT, mDrawableContainer.getOpacity());

        mDrawableContainer.selectDrawable(0);
        assertEquals(mDrawableContainerState.getOpacity(), mDrawableContainer.getOpacity());
        assertEquals(PixelFormat.OPAQUE, mDrawableContainer.getOpacity());

        Drawable dr1 = spy(new ColorDrawable(Color.RED));
        doReturn(PixelFormat.TRANSLUCENT).when(dr1).getOpacity();
        mDrawableContainerState.addChild(dr1);

        mDrawableContainer.selectDrawable(1);
        assertEquals(mDrawableContainerState.getOpacity(), mDrawableContainer.getOpacity());
        assertEquals(PixelFormat.TRANSLUCENT, mDrawableContainer.getOpacity());
    }

    @Test(expected=NullPointerException.class)
    public void testSelectDrawableNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.selectDrawable(0);
    }

    @Test
    public void testSelectDrawable() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        MockDrawable dr0 = new MockDrawable();
        dr0.setVisible(false, false);
        assertFalse(dr0.isVisible());
        mDrawableContainerState.addChild(dr0);
        MockDrawable dr1 = new MockDrawable();
        dr1.setVisible(false, false);
        assertFalse(dr1.isVisible());
        mDrawableContainerState.addChild(dr1);

        assertTrue(mDrawableContainer.selectDrawable(0));
        assertSame(dr0, mDrawableContainer.getCurrent());
        assertTrue(dr0.isVisible());

        assertFalse(mDrawableContainer.selectDrawable(0));

        assertTrue(mDrawableContainer.selectDrawable(1));
        assertSame(dr1, mDrawableContainer.getCurrent());
        assertTrue(dr1.isVisible());
        assertFalse(dr0.isVisible());

        assertFalse(mDrawableContainer.selectDrawable(1));

        assertTrue(mDrawableContainer.selectDrawable(-1));
        assertNull(mDrawableContainer.getCurrent());
        assertFalse(dr0.isVisible());
        assertFalse(dr1.isVisible());

        assertTrue(mDrawableContainer.selectDrawable(2));
        assertNull(mDrawableContainer.getCurrent());
        assertFalse(dr0.isVisible());
        assertFalse(dr1.isVisible());
    }

    @Test
    public void testAccessConstantState() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        assertSame(mDrawableContainerState, mDrawableContainer.getConstantState());

        mMockDrawableContainer.setConstantState(null);
        // Note that we're not using 'expected' on the @Test annotation since we want to
        // make sure that only this next call is going to throw NPE.
        try {
            mDrawableContainer.getConstantState();
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test(expected=NullPointerException.class)
    public void testMutateNoConstantState() {
        // Should throw NullPointerException if the constant state is not set
        mDrawableContainer.mutate();
    }

    @Test
    public void testMutate() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        Drawable dr0 = spy(new ColorDrawable(Color.MAGENTA));
        mDrawableContainerState.addChild(dr0);
        mDrawableContainer.mutate();
        verify(dr0, atLeastOnce()).mutate();
    }

    @Test
    public void testOpacityChange() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        ColorDrawable c1 = new ColorDrawable(Color.RED);
        ColorDrawable c2 = new ColorDrawable(Color.BLUE);
        addAndSelectDrawable(c1);
        addAndSelectDrawable(c2);
        assertEquals(PixelFormat.OPAQUE, mDrawableContainer.getOpacity());

        // Changes to the not-current drawable should still refresh.
        c1.setTint(0x80FF0000);
        c1.setTintMode(PorterDuff.Mode.SRC);
        assertEquals(PixelFormat.TRANSLUCENT, mDrawableContainer.getOpacity());
    }

    @Test
    public void testStatefulnessChange() {
        mMockDrawableContainer.setConstantState(mDrawableContainerState);
        ColorDrawable c1 = new ColorDrawable(Color.RED);
        ColorDrawable c2 = new ColorDrawable(Color.BLUE);
        addAndSelectDrawable(c1);
        addAndSelectDrawable(c2);
        assertEquals(false, mDrawableContainer.isStateful());

        // Changes to the not-current drawable should still refresh.
        ColorStateList csl = new ColorStateList(
                new int[][] { { android.R.attr.state_enabled }, { } },
                new int[] { Color.RED, Color.BLUE });
        c1.setTintList(csl);
        assertEquals(true, mDrawableContainer.isStateful());
    }

    private void addAndSelectDrawable(Drawable drawable) {
        int pos = mDrawableContainerState.addChild(drawable);
        mDrawableContainer.selectDrawable(pos);
        assertSame(drawable, mDrawableContainer.getCurrent());
    }

    private class MockDrawableContainer extends DrawableContainer {
        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
        }

        @Override
        protected boolean onLevelChange(int level) {
            return super.onLevelChange(level);
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return super.onStateChange(state);
        }

        @Override
        protected void setConstantState(DrawableContainerState state) {
            super.setConstantState(state);
        }
    }

    // Since Mockito can't mock or spy on protected methods, we have a custom extension
    // of Drawable to track calls to protected methods. This class also has empty implementations
    // of the base abstract methods.
    private class MockDrawable extends Drawable {
        private boolean mHasCalledOnBoundsChanged;
        private boolean mHasCalledOnStateChanged;
        private boolean mHasCalledOnLevelChanged;

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        public boolean hasOnBoundsChangedCalled() {
            return mHasCalledOnBoundsChanged;
        }

        public boolean hasOnStateChangedCalled() {
            return mHasCalledOnStateChanged;
        }

        public boolean hasOnLevelChangedCalled() {
            return mHasCalledOnLevelChanged;
        }

        public void reset() {
            mHasCalledOnLevelChanged = false;
            mHasCalledOnStateChanged = false;
            mHasCalledOnBoundsChanged = false;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            mHasCalledOnBoundsChanged = true;
        }

        @Override
        protected boolean onLevelChange(int level) {
            boolean result = super.onLevelChange(level);
            mHasCalledOnLevelChanged = true;
            return result;
        }

        @Override
        protected boolean onStateChange(int[] state) {
            boolean result = super.onStateChange(state);
            mHasCalledOnStateChanged = true;
            return result;
        }
    }
}
