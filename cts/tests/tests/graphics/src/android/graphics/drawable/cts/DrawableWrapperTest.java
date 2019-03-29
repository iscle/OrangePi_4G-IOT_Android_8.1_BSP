/*
 * Copyright (C) 2015 The Android Open Source Project.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.StateSet;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DrawableWrapperTest {
    static class MyWrapper extends DrawableWrapper {
        public MyWrapper(Drawable dr) {
            super(dr);
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testConstructor() {
        Drawable d = new BitmapDrawable();
        DrawableWrapper wrapper = new MyWrapper(d);
        assertSame(d, wrapper.getDrawable());

        new MyWrapper(null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetDrawable() {
        Drawable d = new BitmapDrawable();
        DrawableWrapper wrapper = new MyWrapper(d);
        assertSame(d, wrapper.getDrawable());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetDrawable() {
        Drawable d = new BitmapDrawable();
        DrawableWrapper wrapper = new MyWrapper(null);
        assertSame(null, wrapper.getDrawable());

        wrapper.setDrawable(d);
        assertSame(d, wrapper.getDrawable());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testInvalidateDrawable() {
        DrawableWrapper wrapper = new MyWrapper(new BitmapDrawable());

        Drawable.Callback cb = mock(Drawable.Callback.class);
        wrapper.setCallback(cb);
        wrapper.invalidateDrawable(null);
        verify(cb, times(1)).invalidateDrawable(any());

        reset(cb);
        wrapper.invalidateDrawable(new BitmapDrawable());
        verify(cb, times(1)).invalidateDrawable(any());

        reset(cb);
        wrapper.setCallback(null);
        wrapper.invalidateDrawable(null);
        verify(cb, never()).invalidateDrawable(any());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testScheduleDrawable() {
        DrawableWrapper wrapper = new MyWrapper(new BitmapDrawable());

        Drawable.Callback cb = mock(Drawable.Callback.class);
        wrapper.setCallback(cb);
        wrapper.scheduleDrawable(null, null, 0);
        verify(cb, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(cb);
        wrapper.scheduleDrawable(new BitmapDrawable(), () -> {}, 1000L);
        verify(cb, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(cb);
        wrapper.setCallback(null);
        wrapper.scheduleDrawable(null, null, 0);
        verify(cb, never()).scheduleDrawable(any(), any(), anyLong());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testUnscheduleDrawable() {
        DrawableWrapper wrapper = new MyWrapper(new BitmapDrawable());

        Drawable.Callback cb = mock(Drawable.Callback.class);
        wrapper.setCallback(cb);
        wrapper.unscheduleDrawable(null, null);
        verify(cb, times(1)).unscheduleDrawable(any(), any());

        reset(cb);
        wrapper.unscheduleDrawable(new BitmapDrawable(), () -> {});
        verify(cb, times(1)).unscheduleDrawable(any(), any());

        reset(cb);
        wrapper.setCallback(null);
        wrapper.unscheduleDrawable(null, null);
        verify(cb, never()).unscheduleDrawable(any(), any());
    }

    @Test
    public void testDraw() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.BLUE));
        doNothing().when(mockDrawable).draw(any());
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        wrapper.draw(new Canvas());
        verify(mockDrawable, times(1)).draw(any());

        reset(mockDrawable);
        doNothing().when(mockDrawable).draw(any());
        wrapper.draw(null);
        verify(mockDrawable, times(1)).draw(any());
    }

    @Test
    public void testGetChangingConfigurations() {
        final int SUPER_CONFIG = 1;
        final int CONTAINED_DRAWABLE_CONFIG = 2;

        MockDrawable mockDrawable = new MockDrawable();
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        assertEquals(0, wrapper.getChangingConfigurations());

        mockDrawable.setChangingConfigurations(CONTAINED_DRAWABLE_CONFIG);
        assertEquals(CONTAINED_DRAWABLE_CONFIG, wrapper.getChangingConfigurations());

        wrapper.setChangingConfigurations(SUPER_CONFIG);
        assertEquals(SUPER_CONFIG | CONTAINED_DRAWABLE_CONFIG,
                wrapper.getChangingConfigurations());
    }

    @Test
    public void testGetPadding() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's getPadding method.
        wrapper.getPadding(new Rect());
        verify(mockDrawable, times(1)).getPadding(any());
    }

    @Test(expected=NullPointerException.class)
    public void testGetPaddingNull() {
        DrawableWrapper wrapper = new MyWrapper(new ColorDrawable(Color.RED));

        wrapper.getPadding(null);
    }

    @Test
    public void testSetVisible() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.YELLOW));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);
        assertTrue(wrapper.isVisible());

        assertTrue(wrapper.setVisible(false, false));
        assertFalse(wrapper.isVisible());
        verify(mockDrawable, times(1)).setVisible(anyBoolean(), anyBoolean());

        reset(mockDrawable);
        assertFalse(wrapper.setVisible(false, false));
        assertFalse(wrapper.isVisible());
        verify(mockDrawable, times(1)).setVisible(anyBoolean(), anyBoolean());

        reset(mockDrawable);
        assertTrue(wrapper.setVisible(true, false));
        assertTrue(wrapper.isVisible());
        verify(mockDrawable, times(1)).setVisible(anyBoolean(), anyBoolean());
    }

    @Test
    public void testSetAlpha() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.MAGENTA));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's setAlpha method.
        wrapper.setAlpha(100);
        verify(mockDrawable, times(1)).setAlpha(anyInt());

        reset(mockDrawable);
        wrapper.setAlpha(Integer.MAX_VALUE);
        verify(mockDrawable, times(1)).setAlpha(anyInt());

        reset(mockDrawable);
        wrapper.setAlpha(-1);
        verify(mockDrawable, times(1)).setAlpha(anyInt());
    }

    @Test
    public void testSetColorFilter() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.GRAY));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's setColorFilter method.
        wrapper.setColorFilter(new ColorFilter());
        verify(mockDrawable, times(1)).setColorFilter(any());

        reset(mockDrawable);
        wrapper.setColorFilter(null);
        verify(mockDrawable, times(1)).setColorFilter(any());
    }

    @Test
    public void testGetOpacity() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // This method will call contained drawable's getOpacity method.
        wrapper.setLevel(1);
        wrapper.getOpacity();
        verify(mockDrawable, times(1)).getOpacity();
    }

    @Test
    public void testIsStateful() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.BLACK));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's isStateful method.
        wrapper.isStateful();
        verify(mockDrawable, times(1)).isStateful();
    }

    @Test
    public void testOnStateChange() {
        Drawable d = new MockDrawable();
        MockDrawableWrapper wrapper = new MockDrawableWrapper(d);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);

        int[] state = new int[] {1, 2, 3};
        assertFalse("child did not change", wrapper.onStateChange(state));
        assertEquals("child state did not change", d.getState(), StateSet.WILD_CARD);

        d = InstrumentationRegistry.getTargetContext().getDrawable(R.drawable.statelistdrawable);
        wrapper = new MockDrawableWrapper(d);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);
        wrapper.onStateChange(state);
        assertTrue("child state changed", Arrays.equals(state, d.getState()));

        // input null as param
        wrapper.onStateChange(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testOnLevelChange() {
        MockDrawable mockDrawable = new MockDrawable();
        MockDrawableWrapper mockDrawableWrapper = new MockDrawableWrapper(mockDrawable);

        assertEquals(0, mockDrawable.getLevel());
        assertFalse(mockDrawableWrapper.onLevelChange(0));
        assertFalse(mockDrawable.hasCalledOnLevelChange());

        assertFalse(mockDrawableWrapper.onLevelChange(1000));
        assertTrue(mockDrawable.hasCalledOnLevelChange());
        assertEquals(1000, mockDrawable.getLevel());

        mockDrawable.reset();
        assertFalse(mockDrawableWrapper.onLevelChange(Integer.MIN_VALUE));
        assertTrue(mockDrawable.hasCalledOnLevelChange());
    }

    @Test
    public void testOnBoundsChange() {
        MockDrawable mockDrawable = new MockDrawable();
        MockDrawableWrapper mockDrawableWrapper = new MockDrawableWrapper(mockDrawable);
        Rect bounds = new Rect(2, 2, 26, 32);
        mockDrawable.setBounds(bounds);
        mockDrawableWrapper.onBoundsChange(bounds);

        mockDrawableWrapper = new MockDrawableWrapper(mockDrawable);
        mockDrawable.setBounds(bounds);
        mockDrawableWrapper.onBoundsChange(bounds);
        assertEquals(bounds.left, mockDrawable.getBounds().left);
        assertEquals(bounds.top, mockDrawable.getBounds().top);
        assertEquals(bounds.right, mockDrawable.getBounds().right);
        assertEquals(bounds.bottom, mockDrawable.getBounds().bottom);

        bounds = mockDrawable.getBounds();
        assertEquals(2, bounds.left);
        assertEquals(2, bounds.top);
        assertEquals(26, bounds.right);
        assertEquals(32, bounds.bottom);
    }

    @Test(expected=NullPointerException.class)
    public void testOnBoundsChangeNull() {
        MockDrawable mockDrawable = new MockDrawable();
        MockDrawableWrapper mockDrawableWrapper = new MockDrawableWrapper(mockDrawable);

        mockDrawableWrapper.onBoundsChange(null);
    }

    @Test
    public void testGetIntrinsicWidth() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.WHITE));
        MyWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's getIntrinsicWidth method.
        wrapper.getIntrinsicWidth();
        verify(mockDrawable, times(1)).getIntrinsicWidth();
    }

    @Test
    public void testGetIntrinsicHeight() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        DrawableWrapper wrapper = new MyWrapper(mockDrawable);

        // this method will call contained drawable's getIntrinsicHeight method.
        wrapper.getIntrinsicHeight();
        verify(mockDrawable, times(1)).getIntrinsicHeight();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetConstantState() {
        DrawableWrapper wrapper = new MyWrapper(new BitmapDrawable());
        wrapper.getConstantState();
    }

    // Since Mockito can't mock or spy on protected methods, we have a custom extension
    // of Drawable to track calls to protected methods. This class also has empty implementations
    // of the base abstract methods.
    private static class MockDrawable extends Drawable {
        private boolean mCalledOnLevelChange = false;

        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        protected boolean onLevelChange(int level) {
            mCalledOnLevelChange = true;
            return super.onLevelChange(level);
        }

        public boolean hasCalledOnLevelChange() {
            return mCalledOnLevelChange;
        }

        public void reset() {
            mCalledOnLevelChange = false;
        }
    }

    private static class MockDrawableWrapper extends DrawableWrapper {
        MockDrawableWrapper() {
            super(null);
        }

        public MockDrawableWrapper(Drawable drawable) {
            super(drawable);
        }

        @Override
        protected boolean onStateChange(int[] state) {
            return super.onStateChange(state);
        }

        @Override
        protected boolean onLevelChange(int level) {
            return super.onLevelChange(level);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
        }
    }
}
