/*
 * Copyright (C) 2008 The Android Open Source Project.
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.ScaleDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.StateSet;
import android.view.Gravity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScaleDrawableTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testConstructor() {
        Drawable d = new BitmapDrawable();
        ScaleDrawable scaleDrawable = new ScaleDrawable(d, Gravity.CENTER, 100, 200);
        assertSame(d, scaleDrawable.getDrawable());

        new ScaleDrawable(null, -1, Float.MAX_VALUE, Float.MIN_VALUE);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testInvalidateDrawable() {
        ScaleDrawable scaleDrawable = new ScaleDrawable(new BitmapDrawable(),
                Gravity.CENTER, 100, 200);

        Drawable.Callback callback = mock(Drawable.Callback.class);
        scaleDrawable.setCallback(callback);
        scaleDrawable.invalidateDrawable(null);
        verify(callback, times(1)).invalidateDrawable(any());

        reset(callback);
        scaleDrawable.invalidateDrawable(new BitmapDrawable());
        verify(callback, times(1)).invalidateDrawable(any());

        reset(callback);
        scaleDrawable.setCallback(null);
        scaleDrawable.invalidateDrawable(null);
        verify(callback, never()).invalidateDrawable(any());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testScheduleDrawable() {
        ScaleDrawable scaleDrawable = new ScaleDrawable(new BitmapDrawable(),
                Gravity.CENTER, 100, 200);

        Drawable.Callback callback = mock(Drawable.Callback.class);
        scaleDrawable.setCallback(callback);
        scaleDrawable.scheduleDrawable(null, null, 0);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(callback);
        scaleDrawable.scheduleDrawable(new BitmapDrawable(), () -> {}, 1000L);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(callback);
        scaleDrawable.setCallback(null);
        scaleDrawable.scheduleDrawable(null, null, 0);
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testUnscheduleDrawable() {
        ScaleDrawable scaleDrawable = new ScaleDrawable(new BitmapDrawable(),
                Gravity.CENTER, 100, 200);

        Drawable.Callback callback = mock(Drawable.Callback.class);
        scaleDrawable.setCallback(callback);
        scaleDrawable.unscheduleDrawable(null, null);
        verify(callback, times(1)).unscheduleDrawable(any(), any());

        reset(callback);
        scaleDrawable.unscheduleDrawable(new BitmapDrawable(), () -> {});
        verify(callback, times(1)).unscheduleDrawable(any(), any());

        reset(callback);
        scaleDrawable.setCallback(null);
        scaleDrawable.unscheduleDrawable(null, null);
        verify(callback, never()).unscheduleDrawable(any(), any());
    }

    @Test
    public void testDraw() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        scaleDrawable.draw(new Canvas());
        verify(mockDrawable, never()).draw(any());

        // this method will call the contained drawable's draw method
        // if the contained drawable's level doesn't equal 0.
        mockDrawable.setLevel(1);
        scaleDrawable.draw(new Canvas());
        verify(mockDrawable, times(1)).draw(any());

        reset(mockDrawable);
        doNothing().when(mockDrawable).draw(any());
        scaleDrawable.draw(null);
        verify(mockDrawable, times(1)).draw(any());
    }

    @Test
    public void testGetChangingConfigurations() {
        final int SUPER_CONFIG = 1;
        final int CONTAINED_DRAWABLE_CONFIG = 2;

        Drawable mockDrawable = new ColorDrawable(Color.YELLOW);
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        assertEquals(0, scaleDrawable.getChangingConfigurations());

        mockDrawable.setChangingConfigurations(CONTAINED_DRAWABLE_CONFIG);
        assertEquals(CONTAINED_DRAWABLE_CONFIG, scaleDrawable.getChangingConfigurations());

        scaleDrawable.setChangingConfigurations(SUPER_CONFIG);
        assertEquals(SUPER_CONFIG | CONTAINED_DRAWABLE_CONFIG,
                scaleDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetPadding() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's getPadding method.
        scaleDrawable.getPadding(new Rect());
        verify(mockDrawable, times(1)).getPadding(any());
    }

    @Test(expected=NullPointerException.class)
    public void testGetPaddingNull() {
        Drawable mockDrawable = new ColorDrawable(Color.YELLOW);
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        scaleDrawable.getPadding(null);
    }

    @Test
    public void testSetVisible() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);
        assertTrue(scaleDrawable.isVisible());

        assertTrue(scaleDrawable.setVisible(false, false));
        assertFalse(scaleDrawable.isVisible());
        verify(mockDrawable, atLeastOnce()).setVisible(anyBoolean(), anyBoolean());

        reset(mockDrawable);
        assertFalse(scaleDrawable.setVisible(false, false));
        assertFalse(scaleDrawable.isVisible());
        verify(mockDrawable, times(1)).setVisible(anyBoolean(), anyBoolean());

        reset(mockDrawable);
        assertTrue(scaleDrawable.setVisible(true, false));
        assertTrue(scaleDrawable.isVisible());
        verify(mockDrawable, times(1)).setVisible(anyBoolean(), anyBoolean());
    }

    @Test
    public void testSetAlpha() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's setAlpha method.
        scaleDrawable.setAlpha(100);
        verify(mockDrawable, times(1)).setAlpha(anyInt());

        reset(mockDrawable);
        scaleDrawable.setAlpha(Integer.MAX_VALUE);
        verify(mockDrawable, times(1)).setAlpha(anyInt());

        reset(mockDrawable);
        scaleDrawable.setAlpha(-1);
        verify(mockDrawable, times(1)).setAlpha(anyInt());
    }

    @Test
    public void testSetColorFilter() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's setColorFilter method.
        scaleDrawable.setColorFilter(new ColorFilter());
        verify(mockDrawable, times(1)).setColorFilter(any());

        reset(mockDrawable);
        scaleDrawable.setColorFilter(null);
        verify(mockDrawable, times(1)).setColorFilter(any());
    }

    @Test
    public void testGetOpacity() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // This method will call contained drawable's getOpacity method.
        scaleDrawable.setLevel(1);
        scaleDrawable.getOpacity();
        verify(mockDrawable, times(1)).getOpacity();
    }

    @Test
    public void testIsStateful() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's isStateful method.
        scaleDrawable.isStateful();
        verify(mockDrawable, times(1)).isStateful();
    }

    @Test
    public void testOnStateChange() {
        Drawable d = new MockDrawable();
        MockScaleDrawable scaleDrawable = new MockScaleDrawable(d, Gravity.CENTER, 100, 200);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);

        int[] state = new int[] {1, 2, 3};
        assertFalse("child did not change", scaleDrawable.onStateChange(state));
        assertEquals("child state did not change", d.getState(), StateSet.WILD_CARD);

        d = mContext.getDrawable(R.drawable.statelistdrawable);
        scaleDrawable = new MockScaleDrawable(d, Gravity.CENTER, 100, 200);
        assertEquals("initial child state is empty", d.getState(), StateSet.WILD_CARD);
        scaleDrawable.onStateChange(state);
        assertTrue("child state changed", Arrays.equals(state, d.getState()));

        // input null as param
        scaleDrawable.onStateChange(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testInitialLevel() throws XmlPullParserException, IOException {
        ScaleDrawable dr = new ScaleDrawable(null, Gravity.CENTER, 1, 1);
        Resources res = mContext.getResources();
        XmlResourceParser parser = res.getXml(R.xml.scaledrawable_level);
        AttributeSet attrs = DrawableTestUtils.getAttributeSet(parser, "scale_allattrs");

        // Ensure that initial level is loaded from XML.
        dr.inflate(res, parser, attrs);
        assertEquals(5000, dr.getLevel());

        dr.setLevel(0);
        assertEquals(0, dr.getLevel());

        // Ensure that initial level is propagated to constant state clones.
        ScaleDrawable clone = (ScaleDrawable) dr.getConstantState().newDrawable(res);
        assertEquals(5000, clone.getLevel());

        // Ensure that current level is not tied to constant state.
        dr.setLevel(1000);
        assertEquals(1000, dr.getLevel());
        assertEquals(5000, clone.getLevel());
    }

    @Test
    public void testOnLevelChange() {
        MockDrawable mockDrawable = new MockDrawable();
        MockScaleDrawable mockScaleDrawable = new MockScaleDrawable(
                mockDrawable, Gravity.CENTER, 100, 200);

        assertTrue(mockScaleDrawable.onLevelChange(0));
        assertFalse(mockDrawable.hasCalledOnLevelChange());
        assertTrue(mockScaleDrawable.hasCalledOnBoundsChange());

        mockDrawable.reset();
        mockScaleDrawable.reset();
        assertTrue(mockScaleDrawable.onLevelChange(Integer.MIN_VALUE));
        assertTrue(mockDrawable.hasCalledOnLevelChange());
        assertTrue(mockScaleDrawable.hasCalledOnBoundsChange());
    }

    @Test
    public void testOnBoundsChange() {
        Drawable mockDrawable = new ColorDrawable(Color.YELLOW);
        float scaleWidth = 0.3f;
        float scaleHeight = 0.3f;
        MockScaleDrawable mockScaleDrawable = new MockScaleDrawable(
                mockDrawable, Gravity.LEFT, scaleWidth, scaleHeight);
        Rect bounds = new Rect(2, 2, 26, 32);
        mockDrawable.setBounds(bounds);
        mockScaleDrawable.onBoundsChange(bounds);
        Rect expected = new Rect();
        Gravity.apply(Gravity.LEFT, bounds.width() - (int) (bounds.width() * scaleWidth),
                bounds.height() - (int) (bounds.height() * scaleHeight), bounds, expected);
        assertEquals(expected.left, mockDrawable.getBounds().left);
        assertEquals(expected.top, mockDrawable.getBounds().top);
        assertEquals(expected.right, mockDrawable.getBounds().right);
        assertEquals(expected.bottom, mockDrawable.getBounds().bottom);

        scaleWidth = 0.6f;
        scaleHeight = 0.7f;
        int level = 4000;
        mockScaleDrawable = new MockScaleDrawable(
                mockDrawable, Gravity.BOTTOM | Gravity.RIGHT, scaleWidth, scaleHeight);
        mockDrawable.setBounds(bounds);
        mockScaleDrawable.setLevel(level);
        mockScaleDrawable.onBoundsChange(bounds);
        Gravity.apply(Gravity.BOTTOM | Gravity.RIGHT,
                bounds.width() - (int) (bounds.width() * scaleWidth * (10000 - level) / 10000),
                bounds.height() - (int) (bounds.height() * scaleHeight * (10000 - level) / 10000),
                bounds, expected);
        assertEquals(expected.left, mockDrawable.getBounds().left);
        assertEquals(expected.top, mockDrawable.getBounds().top);
        assertEquals(expected.right, mockDrawable.getBounds().right);
        assertEquals(expected.bottom, mockDrawable.getBounds().bottom);

        scaleWidth = 0f;
        scaleHeight = -0.3f;
        mockScaleDrawable = new MockScaleDrawable(
                mockDrawable, Gravity.BOTTOM | Gravity.RIGHT, scaleWidth, scaleHeight);
        mockDrawable.setBounds(bounds);
        mockScaleDrawable.onBoundsChange(bounds);
        assertEquals(bounds.left, mockDrawable.getBounds().left);
        assertEquals(bounds.top, mockDrawable.getBounds().top);
        assertEquals(bounds.right, mockDrawable.getBounds().right);
        assertEquals(bounds.bottom, mockDrawable.getBounds().bottom);

        scaleWidth = 1f;
        scaleHeight = 1.7f;
        mockScaleDrawable = new MockScaleDrawable(
                mockDrawable, Gravity.BOTTOM | Gravity.RIGHT, scaleWidth, scaleHeight);
        mockDrawable.setBounds(bounds);
        mockScaleDrawable.onBoundsChange(bounds);
        assertEquals(bounds.left, mockDrawable.getBounds().left);
        assertEquals(bounds.top, mockDrawable.getBounds().top);
        assertEquals(bounds.right, mockDrawable.getBounds().right);
        assertEquals(bounds.bottom, mockDrawable.getBounds().bottom);
    }

    @Test
    public void testGetIntrinsicWidth() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's getIntrinsicWidth method.
        scaleDrawable.getIntrinsicWidth();
        verify(mockDrawable, times(1)).getIntrinsicWidth();
    }

    @Test
    public void testGetIntrinsicHeight() {
        Drawable mockDrawable = spy(new ColorDrawable(Color.RED));
        ScaleDrawable scaleDrawable = new ScaleDrawable(mockDrawable, Gravity.CENTER, 100, 200);

        // this method will call contained drawable's getIntrinsicHeight method.
        scaleDrawable.getIntrinsicHeight();
        verify(mockDrawable, times(1)).getIntrinsicHeight();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetConstantState() {
        ScaleDrawable scaleDrawable = new ScaleDrawable(new BitmapDrawable(),
                Gravity.CENTER, 100, 200);

        ConstantState constantState = scaleDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(0, constantState.getChangingConfigurations());

        scaleDrawable.setChangingConfigurations(1);
        constantState = scaleDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(1, constantState.getChangingConfigurations());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        ScaleDrawable scaleDrawable = new ScaleDrawable(new BitmapDrawable(),
                Gravity.RIGHT, 100, 200);

        Resources res = mContext.getResources();
        XmlResourceParser parser = res.getXml(R.xml.scaledrawable);
        AttributeSet attrs = DrawableTestUtils.getAttributeSet(parser, "scale_allattrs");
        scaleDrawable.inflate(res, parser, attrs);
        final int bitmapSize = Math.round(48f * res.getDisplayMetrics().density);
        assertEquals(bitmapSize, scaleDrawable.getIntrinsicWidth());
        assertEquals(bitmapSize, scaleDrawable.getIntrinsicHeight());

        parser = res.getXml(R.xml.scaledrawable);
        attrs = DrawableTestUtils.getAttributeSet(parser, "scale_nodrawable");
        try {
            Drawable.createFromXmlInner(res, parser, attrs);
            fail("Should throw XmlPullParserException if missing drawable");
        } catch (XmlPullParserException e) {
        }

        try {
            Drawable.createFromXmlInner(null, parser, attrs);
            fail("Should throw NullPointerException if resource is null");
        } catch (NullPointerException e) {
        }

        try {
            Drawable.createFromXmlInner(res, null, attrs);
            fail("Should throw NullPointerException if parser is null");
        } catch (NullPointerException e) {
        }

        try {
            Drawable.createFromXmlInner(res, parser, null);
            fail("Should throw NullPointerException if attribute set is null");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testMutate() {
        ScaleDrawable d1 = (ScaleDrawable) mContext.getDrawable(R.drawable.scaledrawable);
        ScaleDrawable d2 = (ScaleDrawable) mContext.getDrawable(R.drawable.scaledrawable);
        ScaleDrawable d3 = (ScaleDrawable) mContext.getDrawable(R.drawable.scaledrawable);
        int restoreAlpha = d1.getAlpha();

        try {
            // verify bad behavior - modify before mutate pollutes other drawables
            d1.setAlpha(100);
            assertEquals(100, ((BitmapDrawable) d1.getDrawable()).getPaint().getAlpha());
            assertEquals(100, ((BitmapDrawable) d2.getDrawable()).getPaint().getAlpha());
            assertEquals(100, ((BitmapDrawable) d3.getDrawable()).getPaint().getAlpha());

            d1.mutate();
            d1.setAlpha(200);
            assertEquals(200, ((BitmapDrawable) d1.getDrawable()).getPaint().getAlpha());
            assertEquals(100, ((BitmapDrawable) d2.getDrawable()).getPaint().getAlpha());
            assertEquals(100, ((BitmapDrawable) d3.getDrawable()).getPaint().getAlpha());

            d2.setAlpha(50);
            assertEquals(200, ((BitmapDrawable) d1.getDrawable()).getPaint().getAlpha());
            assertEquals(50, ((BitmapDrawable) d2.getDrawable()).getPaint().getAlpha());
            assertEquals(50, ((BitmapDrawable) d3.getDrawable()).getPaint().getAlpha());
        } finally {
            // restore externally visible state, since other tests may use the drawable
            mContext.getDrawable(R.drawable.scaledrawable).setAlpha(restoreAlpha);
        }
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
            return 0;
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

    private static class MockScaleDrawable extends ScaleDrawable {
        private boolean mCalledOnBoundsChange = false;

        MockScaleDrawable() {
            super(null, Gravity.CENTER, 100, 200);
        }

        public MockScaleDrawable(Drawable drawable, int gravity,
                float scaleWidth, float scaleHeight) {
            super(drawable, gravity, scaleWidth, scaleHeight);
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
            mCalledOnBoundsChange = true;
            super.onBoundsChange(bounds);
        }

        public boolean hasCalledOnBoundsChange() {
            return mCalledOnBoundsChange;
        }

        public void reset() {
            mCalledOnBoundsChange = false;
        }
    }
}
