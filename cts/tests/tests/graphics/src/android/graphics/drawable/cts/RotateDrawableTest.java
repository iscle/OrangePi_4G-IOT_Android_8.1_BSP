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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.RotateDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RotateDrawableTest {
    private Resources mResources;
    private RotateDrawable mRotateDrawable;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
        mRotateDrawable = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable);
    }

    @Test
    public void testConstructor() {
        new RotateDrawable();
    }

    @Test
    public void testDraw() {
        Canvas canvas = new Canvas();
        mRotateDrawable.draw(canvas);
    }

    @Test
    public void testInflate() {
        RotateDrawable d;

        d = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable_rel);
        assertEquals(0.1f, d.getPivotX(), 0.01f);
        assertEquals(0.2f, d.getPivotY(), 0.01f);
        assertEquals(360.0f, d.getFromDegrees(), 0.01f);
        assertEquals(360.0f, d.getToDegrees(), 0.01f);
        assertEquals(true, d.isPivotXRelative());
        assertEquals(true, d.isPivotYRelative());

        d = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable_abs);
        assertEquals(0.3f, d.getPivotX(), 0.01f);
        assertEquals(0.3f, d.getPivotY(), 0.01f);
        assertEquals(180.0f, d.getFromDegrees(), 0.01f);
        assertEquals(-180.0f, d.getToDegrees(), 0.01f);
        assertEquals(false, d.isPivotXRelative());
        assertEquals(false, d.isPivotYRelative());
    }

    @Test
    public void testSetPivot() {
        RotateDrawable d = new RotateDrawable();
        assertEquals(0.5f, d.getPivotX(), 0.01f);
        assertEquals(0.5f, d.getPivotY(), 0.01f);
        assertEquals(true, d.isPivotXRelative());
        assertEquals(true, d.isPivotYRelative());

        d.setPivotX(10.0f);
        assertEquals(10.0f, d.getPivotX(), 0.01f);

        d.setPivotY(10.0f);
        assertEquals(10.0f, d.getPivotY(), 0.01f);

        d.setPivotXRelative(false);
        assertEquals(false, d.isPivotXRelative());

        d.setPivotYRelative(false);
        assertEquals(false, d.isPivotYRelative());
    }

    @Test
    public void testSetDegrees() {
        RotateDrawable d = new RotateDrawable();
        assertEquals(0.0f, d.getFromDegrees(), 0.01f);
        assertEquals(360.0f, d.getToDegrees(), 0.01f);

        d.setFromDegrees(-10.0f);
        assertEquals(-10.0f, d.getFromDegrees(), 0.01f);
        assertEquals(360.0f, d.getToDegrees(), 0.01f);

        d.setToDegrees(10.0f);
        assertEquals(10.0f, d.getToDegrees(), 0.01f);
        assertEquals(-10.0f, d.getFromDegrees(), 0.01f);
    }

    @Test
    public void testGetChangingConfigurations() {
        assertEquals(0, mRotateDrawable.getChangingConfigurations());

        mRotateDrawable.setChangingConfigurations(Configuration.KEYBOARD_NOKEYS);
        assertEquals(Configuration.KEYBOARD_NOKEYS, mRotateDrawable.getChangingConfigurations());

        mRotateDrawable.setChangingConfigurations(Configuration.KEYBOARD_12KEY);
        assertEquals(Configuration.KEYBOARD_12KEY, mRotateDrawable.getChangingConfigurations());
    }

    @Test
    public void testSetAlpha() {
        mRotateDrawable.setAlpha(100);
        assertEquals(100, ((BitmapDrawable) mRotateDrawable.getDrawable()).getPaint().getAlpha());

        mRotateDrawable.setAlpha(255);
        assertEquals(255, ((BitmapDrawable) mRotateDrawable.getDrawable()).getPaint().getAlpha());
    }

    @Test
    public void testSetColorFilter() {
        ColorFilter filter = new ColorFilter();
        mRotateDrawable.setColorFilter(filter);
        assertSame(filter,
                ((BitmapDrawable) mRotateDrawable.getDrawable()).getPaint().getColorFilter());

        mRotateDrawable.setColorFilter(null);
        assertNull(((BitmapDrawable) mRotateDrawable.getDrawable()).getPaint().getColorFilter());
    }

    @Test
    public void testGetOpacity() {
        assertEquals(PixelFormat.OPAQUE, mRotateDrawable.getOpacity());
    }

    @Test
    public void testInvalidateDrawable() {
        Drawable drawable = mResources.getDrawable(R.drawable.pass);
        Drawable.Callback callback = mock(Drawable.Callback.class);

        mRotateDrawable.setCallback(callback);
        mRotateDrawable.invalidateDrawable(null);
        verify(callback, times(1)).invalidateDrawable(any());

        reset(callback);
        mRotateDrawable.invalidateDrawable(drawable);
        verify(callback, times(1)).invalidateDrawable(any());

        reset(callback);
        mRotateDrawable.setCallback(null);
        mRotateDrawable.invalidateDrawable(drawable);
        verify(callback, never()).invalidateDrawable(any());
    }

    @Test
    public void testScheduleDrawable() {
        Drawable.Callback callback = mock(Drawable.Callback.class);

        mRotateDrawable.setCallback(callback);
        mRotateDrawable.scheduleDrawable(null, null, 0);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(callback);
        mRotateDrawable.scheduleDrawable(new ColorDrawable(Color.RED), () -> {}, 1000L);
        verify(callback, times(1)).scheduleDrawable(any(), any(), anyLong());

        reset(callback);
        mRotateDrawable.setCallback(null);
        mRotateDrawable.scheduleDrawable(null, null, 0);
        verify(callback, never()).scheduleDrawable(any(), any(), anyLong());
    }

    @Test
    public void testUnscheduleDrawable() {
        Drawable.Callback callback = mock(Drawable.Callback.class);

        mRotateDrawable.setCallback(callback);
        mRotateDrawable.unscheduleDrawable(null, null);
        verify(callback, times(1)).unscheduleDrawable(any(), any());

        reset(callback);
        mRotateDrawable.unscheduleDrawable(new ColorDrawable(Color.RED), () -> {});
        verify(callback, times(1)).unscheduleDrawable(any(), any());

        reset(callback);
        mRotateDrawable.setCallback(null);
        mRotateDrawable.unscheduleDrawable(null, null);
        verify(callback, never()).unscheduleDrawable(any(), any());
    }

    @Test
    public void testGetPadding() {
        Rect rect = new Rect();
        assertFalse(mRotateDrawable.getPadding(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(0, rect.right);
        assertEquals(0, rect.bottom);
    }

    @Test
    public void testSetVisible() {
        assertTrue(mRotateDrawable.isVisible());

        assertTrue(mRotateDrawable.setVisible(false, false));
        assertFalse(mRotateDrawable.isVisible());

        assertFalse(mRotateDrawable.setVisible(false, true));
        assertFalse(mRotateDrawable.isVisible());

        assertTrue(mRotateDrawable.setVisible(true, false));
        assertTrue(mRotateDrawable.isVisible());
    }

    @Test
    public void testIsStateful() {
        assertFalse(mRotateDrawable.isStateful());
    }

    @Test
    public void testGetIntrinsicWidthAndHeight() throws XmlPullParserException, IOException {
        // testimage is set in res/drawable/rotatedrawable.xml
        Drawable drawable = mResources.getDrawable(R.drawable.testimage);
        assertEquals(drawable.getIntrinsicWidth(), mRotateDrawable.getIntrinsicWidth());
        assertEquals(drawable.getIntrinsicHeight(), mRotateDrawable.getIntrinsicHeight());

        RotateDrawable rotateDrawable = new RotateDrawable();
        XmlPullParser parser = mResources.getXml(R.drawable.rotatedrawable);
        while (parser.next() != XmlPullParser.START_TAG) {
            // ignore event, just seek to first tag
        }
        AttributeSet attrs = Xml.asAttributeSet(parser);
        rotateDrawable.inflate(mResources, parser, attrs);
        assertEquals(drawable.getIntrinsicWidth(), rotateDrawable.getIntrinsicWidth());
        assertEquals(drawable.getIntrinsicHeight(), rotateDrawable.getIntrinsicHeight());
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNull() throws XmlPullParserException, IOException {
        mRotateDrawable.inflate(null, null, null);
    }

    @Test
    public void testGetConstantState() {
        ConstantState state = mRotateDrawable.getConstantState();
        assertNotNull(state);
    }

    @Test
    public void testMutate() {
        RotateDrawable d1 = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable);
        RotateDrawable d2 = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable);
        RotateDrawable d3 = (RotateDrawable) mResources.getDrawable(R.drawable.rotatedrawable);

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
            // restore drawable state
            mResources.getDrawable(R.drawable.rotatedrawable).setAlpha(restoreAlpha);
        }
    }
}
