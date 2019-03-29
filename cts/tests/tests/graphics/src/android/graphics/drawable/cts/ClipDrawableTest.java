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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.Xml;
import android.view.Gravity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ClipDrawableTest {
    @SuppressWarnings("deprecation")
    @Test
    public void testClipDrawable() {
        new ClipDrawable((Drawable) null, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);

        BitmapDrawable bmpDrawable = new BitmapDrawable();
        new ClipDrawable(bmpDrawable, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
    }

    @Test
    public void testDraw() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.GREEN));
        mockDrawable.setLevel(5000);
        ClipDrawable clipDrawable = new ClipDrawable(mockDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        clipDrawable.setBounds(new Rect(0, 0, 100, 100));
        clipDrawable.setLevel(5000);
        verify(mockDrawable, never()).draw(any());
        clipDrawable.draw(new Canvas());
        verify(mockDrawable, times(1)).draw(any());

        try {
            clipDrawable.draw(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testGetChangingConfigurations() {
        final int SUPER_CONFIG = 1;
        final int CONTAINED_DRAWABLE_CONFIG = 2;

        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);

        assertEquals(0, clipDrawable.getChangingConfigurations());

        colorDrawable.setChangingConfigurations(CONTAINED_DRAWABLE_CONFIG);
        assertEquals(CONTAINED_DRAWABLE_CONFIG, clipDrawable.getChangingConfigurations());

        clipDrawable.setChangingConfigurations(SUPER_CONFIG);
        assertEquals(SUPER_CONFIG | CONTAINED_DRAWABLE_CONFIG,
                clipDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetConstantState() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.GREEN));
        doReturn(null).when(mockDrawable).getConstantState();
        ClipDrawable clipDrawable = new ClipDrawable(mockDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertNull(clipDrawable.getConstantState());

        doReturn(new MockConstantState()).when(mockDrawable).getConstantState();
        clipDrawable = new ClipDrawable(mockDrawable, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        clipDrawable.setChangingConfigurations(1);
        assertNotNull(clipDrawable.getConstantState());
        assertEquals(1, clipDrawable.getConstantState().getChangingConfigurations());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetIntrinsicHeight() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals(-1, clipDrawable.getIntrinsicHeight());

        Bitmap bitmap = Bitmap.createBitmap(100, 50, Config.RGB_565);
        BitmapDrawable bmpDrawable = new BitmapDrawable(bitmap);
        bmpDrawable.setTargetDensity(bitmap.getDensity()); // avoid scaling
        clipDrawable = new ClipDrawable(bmpDrawable, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals(50, clipDrawable.getIntrinsicHeight());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetIntrinsicWidth() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals(-1, clipDrawable.getIntrinsicWidth());

        Bitmap bitmap = Bitmap.createBitmap(100, 50, Config.RGB_565);
        BitmapDrawable bmpDrawable = new BitmapDrawable(bitmap);
        bmpDrawable.setTargetDensity(bitmap.getDensity()); // avoid scaling
        clipDrawable = new ClipDrawable(bmpDrawable, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals(100, clipDrawable.getIntrinsicWidth());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetOpacity() {
        Drawable dr = spy(new ColorDrawable(Color.GREEN));
        doReturn(PixelFormat.OPAQUE).when(dr).getOpacity();

        ClipDrawable clipDrawable = new ClipDrawable(dr, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        clipDrawable.setLevel(0);
        assertEquals("Fully-clipped opaque drawable is transparent",
                PixelFormat.TRANSPARENT, clipDrawable.getOpacity());
        clipDrawable.setLevel(5000);
        assertEquals("Partially-clipped opaque drawable is translucent",
                PixelFormat.TRANSLUCENT, clipDrawable.getOpacity());
        clipDrawable.setLevel(10000);
        assertEquals("Unclipped opaque drawable is opaque",
                PixelFormat.OPAQUE, clipDrawable.getOpacity());

        doReturn(PixelFormat.TRANSLUCENT).when(dr).getOpacity();
        clipDrawable = new ClipDrawable(dr, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        clipDrawable.setLevel(10000);
        assertEquals("Unclipped translucent drawable is translucent",
                PixelFormat.TRANSLUCENT, clipDrawable.getOpacity());
    }

    @Test
    public void testGetPadding() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        Rect padding = new Rect(10, 10, 100, 100);
        assertFalse(clipDrawable.getPadding(padding));
        assertEquals(0, padding.left);
        assertEquals(0, padding.top);
        assertEquals(0, padding.bottom);
        assertEquals(0, padding.right);

        try {
            clipDrawable.getPadding(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        BitmapDrawable bmpDrawable = new BitmapDrawable();
        ClipDrawable clipDrawable = new ClipDrawable(bmpDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);

        Resources resources = InstrumentationRegistry.getTargetContext().getResources();
        XmlPullParser parser = resources.getXml(R.drawable.gradientdrawable);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        clipDrawable.inflate(resources, parser, attrs);
    }

    @Test
    public void testInvalidateDrawable() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        Drawable.Callback callback = mock(Drawable.Callback.class);
        clipDrawable.setCallback(callback);
        clipDrawable.invalidateDrawable(colorDrawable);
        verify(callback, times(1)).invalidateDrawable(clipDrawable);

        clipDrawable.invalidateDrawable(null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testIsStateful() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertFalse(clipDrawable.isStateful());

        BitmapDrawable bmpDrawable =
                new BitmapDrawable(Bitmap.createBitmap(100, 50, Config.RGB_565));
        clipDrawable = new ClipDrawable(bmpDrawable, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertFalse(clipDrawable.isStateful());
    }

    @Test
    public void testOnBoundsChange() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        MockClipDrawable mockClipDrawable = new MockClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals(0, colorDrawable.getBounds().left);
        assertEquals(0, colorDrawable.getBounds().top);
        assertEquals(0, colorDrawable.getBounds().bottom);
        assertEquals(0, colorDrawable.getBounds().right);
        mockClipDrawable.onBoundsChange(new Rect(10, 10, 100, 100));
        assertEquals(10, colorDrawable.getBounds().left);
        assertEquals(10, colorDrawable.getBounds().top);
        assertEquals(100, colorDrawable.getBounds().bottom);
        assertEquals(100, colorDrawable.getBounds().right);

        try {
            mockClipDrawable.onBoundsChange(null);
            fail("should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testOnLevelChange() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        MockClipDrawable mockClipDrawable = new MockClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        Drawable.Callback callback = mock(Drawable.Callback.class);
        mockClipDrawable.setCallback(callback);

        assertEquals("Default level is 0", 0, colorDrawable.getLevel());
        mockClipDrawable.onLevelChange(1000);
        assertEquals(1000, colorDrawable.getLevel());
        verify(callback, times(1)).invalidateDrawable(mockClipDrawable);

        mockClipDrawable.onLevelChange(0);
        assertEquals(0, colorDrawable.getLevel());

        mockClipDrawable.onLevelChange(10000);
        assertEquals(10000, colorDrawable.getLevel());
    }

    @Test
    public void testOnStateChange() {
        Context context = InstrumentationRegistry.getTargetContext();
        Drawable d = context.getDrawable(R.drawable.pass);
        MockClipDrawable clipDrawable = new MockClipDrawable(d,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);

        int[] state = new int[] {1, 2, 3};
        assertFalse("child did not change", clipDrawable.onStateChange(state));
        assertEquals("child state did not change", d.getState(), StateSet.WILD_CARD);

        d = context.getDrawable(R.drawable.statelistdrawable);
        clipDrawable = new MockClipDrawable(d, Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);
        clipDrawable.onStateChange(state);
        assertTrue("child state changed", Arrays.equals(state, d.getState()));

        // input null as param
        clipDrawable.onStateChange(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testScheduleDrawable() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        Drawable.Callback callback = mock(Drawable.Callback.class);
        clipDrawable.setCallback(callback);
        clipDrawable.scheduleDrawable(colorDrawable, null, 1000L);
        verify(callback, times(1)).scheduleDrawable(clipDrawable, null, 1000L);
    }

    @Test
    public void testSetAlpha() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);

        clipDrawable.setAlpha(0);
        assertEquals(0, colorDrawable.getAlpha());

        clipDrawable.setAlpha(128);
        assertEquals(128, colorDrawable.getAlpha());

        clipDrawable.setAlpha(255);
        assertEquals(255, colorDrawable.getAlpha());
    }

    @Test
    public void testSetColorFilter() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.GREEN));
        ClipDrawable clipDrawable = new ClipDrawable(mockDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);

        ColorFilter cf = new ColorFilter();
        clipDrawable.setColorFilter(cf);
        verify(mockDrawable, times(1)).setColorFilter(cf);

        reset(mockDrawable);
        clipDrawable.setColorFilter(null);
        verify(mockDrawable, times(1)).setColorFilter(null);
    }

    @Test
    public void testSetVisible() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        assertTrue(clipDrawable.isVisible());

        assertTrue(clipDrawable.setVisible(false, false));
        assertFalse(clipDrawable.isVisible());

        assertFalse(clipDrawable.setVisible(false, false));
        assertFalse(clipDrawable.isVisible());

        assertTrue(clipDrawable.setVisible(true, false));
        assertTrue(clipDrawable.isVisible());
    }

    @Test
    public void testUnscheduleDrawable() {
        Drawable colorDrawable = new ColorDrawable(Color.GREEN);
        ClipDrawable clipDrawable = new ClipDrawable(colorDrawable,
                Gravity.BOTTOM, ClipDrawable.HORIZONTAL);
        Drawable.Callback callback = mock(Drawable.Callback.class);
        clipDrawable.setCallback(callback);
        clipDrawable.unscheduleDrawable(colorDrawable, null);
        verify(callback, times(1)).unscheduleDrawable(clipDrawable, null);
    }

    private class MockClipDrawable extends ClipDrawable {
        public MockClipDrawable(Drawable drawable, int gravity, int orientation) {
            super(drawable, gravity, orientation);
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

    private class MockConstantState extends ConstantState {
        public Drawable newDrawable() {
            return null;
        }

        public int getChangingConfigurations() {
            return 0;
        }
    }
}
