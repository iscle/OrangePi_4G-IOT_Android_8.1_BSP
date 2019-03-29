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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.ShapeDrawable.ShaderFactory;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RectShape;
import android.graphics.drawable.shapes.Shape;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShapeDrawableTest {
    @Test
    public void testConstructors() {
        new ShapeDrawable();

        new ShapeDrawable(null);

        new ShapeDrawable(new RectShape());
    }

    @Test(expected=NullPointerException.class)
    public void testDraw() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();

        shapeDrawable.draw(null);
    }

    @Test
    public void testGetChangingConfigurations() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertEquals(0, shapeDrawable.getChangingConfigurations());

        shapeDrawable.setChangingConfigurations(1);
        assertEquals(1, shapeDrawable.getChangingConfigurations());

        shapeDrawable.setChangingConfigurations(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, shapeDrawable.getChangingConfigurations());

        shapeDrawable.setChangingConfigurations(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, shapeDrawable.getChangingConfigurations());

        shapeDrawable.setChangingConfigurations(1);
        shapeDrawable.getConstantState();
        shapeDrawable.setChangingConfigurations(2);
        assertEquals(3, shapeDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetConstantState() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();

        shapeDrawable.setChangingConfigurations(1);
        ConstantState constantState = shapeDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(1, constantState.getChangingConfigurations());
    }

    @Test
    public void testAccessIntrinsicHeight() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertEquals(0, shapeDrawable.getIntrinsicHeight());

        shapeDrawable.setIntrinsicHeight(10);
        assertEquals(10, shapeDrawable.getIntrinsicHeight());

        shapeDrawable.setIntrinsicHeight(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, shapeDrawable.getIntrinsicHeight());

        shapeDrawable.setIntrinsicHeight(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, shapeDrawable.getIntrinsicHeight());
    }

    @Test
    public void testAccessIntrinsicWidth() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertEquals(0, shapeDrawable.getIntrinsicWidth());

        shapeDrawable.setIntrinsicWidth(10);
        assertEquals(10, shapeDrawable.getIntrinsicWidth());

        shapeDrawable.setIntrinsicWidth(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, shapeDrawable.getIntrinsicWidth());

        shapeDrawable.setIntrinsicWidth(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, shapeDrawable.getIntrinsicWidth());
    }

    @Test
    public void testGetOpacity() {
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RectShape());
        assertEquals(PixelFormat.TRANSLUCENT, shapeDrawable.getOpacity());

        shapeDrawable = new ShapeDrawable();
        assertEquals(255, shapeDrawable.getPaint().getAlpha());
        assertEquals(PixelFormat.OPAQUE, shapeDrawable.getOpacity());

        shapeDrawable.getPaint().setAlpha(0);
        assertEquals(PixelFormat.TRANSPARENT, shapeDrawable.getOpacity());

        shapeDrawable.getPaint().setAlpha(128);
        assertEquals(PixelFormat.TRANSLUCENT, shapeDrawable.getOpacity());
    }

    @Test
    public void testAccessPadding() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        Rect padding = new Rect();
        assertFalse(shapeDrawable.getPadding(padding));
        assertEquals(0, padding.left);
        assertEquals(0, padding.top);
        assertEquals(0, padding.right);
        assertEquals(0, padding.bottom);

        shapeDrawable.setPadding(10, 10, 100, 100);
        assertTrue(shapeDrawable.getPadding(padding));
        assertEquals(10, padding.left);
        assertEquals(10, padding.top);
        assertEquals(100, padding.right);
        assertEquals(100, padding.bottom);

        shapeDrawable.setPadding(0, 0, 0, 0);
        assertFalse(shapeDrawable.getPadding(padding));
        assertEquals(0, padding.left);
        assertEquals(0, padding.top);
        assertEquals(0, padding.right);
        assertEquals(0, padding.bottom);

        shapeDrawable.setPadding(new Rect(5, 5, 80, 80));
        assertTrue(shapeDrawable.getPadding(padding));
        assertEquals(5, padding.left);
        assertEquals(5, padding.top);
        assertEquals(80, padding.right);
        assertEquals(80, padding.bottom);

        shapeDrawable.setPadding(null);
        assertFalse(shapeDrawable.getPadding(padding));
        assertEquals(0, padding.left);
        assertEquals(0, padding.top);
        assertEquals(0, padding.right);
        assertEquals(0, padding.bottom);
    }

    @Test(expected=NullPointerException.class)
    public void testGetPaddingNull() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        shapeDrawable.getPadding(null);
    }

    @Test
    public void testGetPaint() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertNotNull(shapeDrawable.getPaint());
        assertEquals(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG
                | Paint.EMBEDDED_BITMAP_TEXT_FLAG, shapeDrawable.getPaint().getFlags());
    }

    @Test
    public void testAccessShaderFactory() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertNull(shapeDrawable.getShaderFactory());

        MockShaderFactory mockShaderFactory = new MockShaderFactory();
        shapeDrawable.setShaderFactory(mockShaderFactory);
        assertSame(mockShaderFactory, shapeDrawable.getShaderFactory());

        shapeDrawable.setShaderFactory(null);
        assertNull(shapeDrawable.getShaderFactory());
    }

    @Test
    public void testSetXfermode() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();

        PorterDuffXfermode xfermode = new PorterDuffXfermode(Mode.SRC_OVER);
        shapeDrawable.setXfermode(xfermode);
        assertSame(xfermode, shapeDrawable.getPaint().getXfermode());

        shapeDrawable.setXfermode(null);
        assertNull(shapeDrawable.getPaint().getXfermode());
    }

    public static class MockShaderFactory extends ShaderFactory {
        public Shader resize(int width, int height) {
            return null;
        }
    }

    @Test
    public void testAccessShape() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        assertNull(shapeDrawable.getShape());

        RectShape rectShape = new RectShape();
        shapeDrawable.setShape(rectShape);
        assertSame(rectShape, shapeDrawable.getShape());

        shapeDrawable.setShape(null);
        assertNull(shapeDrawable.getShape());
    }

    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        final Resources res = InstrumentationRegistry.getTargetContext().getResources();

        XmlPullParser parser = res.getXml(R.drawable.shapedrawable_test);
        while (parser.next() != XmlPullParser.START_TAG) {
            // ignore event, just seek to first tag
        }
        AttributeSet attrs = Xml.asAttributeSet(parser);
        MockShapeDrawable shapeDrawable = new MockShapeDrawable();
        shapeDrawable.inflate(res, parser, attrs);
        // values from shapedrawable_test.xml
        assertEquals(42, shapeDrawable.getIntrinsicWidth());
        assertEquals(63, shapeDrawable.getIntrinsicHeight());
        Rect padding = new Rect();
        assertTrue(shapeDrawable.getPadding(padding));
        assertEquals(1, padding.left);
        assertEquals(2, padding.top);
        assertEquals(3, padding.right);
        assertEquals(4, padding.bottom);
        assertTrue(shapeDrawable.inflateTagCalled);
        assertTrue(shapeDrawable.extendedAttrsSet);
    }

    // Since Mockito can't mock or spy on protected methods, we have a custom extension
    // of StateListDrawable to track calls to protected inflateTag method.
    public static class MockShapeDrawable extends ShapeDrawable {
        public boolean inflateTagCalled;
        public boolean extendedAttrsSet;

        public MockShapeDrawable() {
            super();
        }

        public MockShapeDrawable(Shape s) {
            super(s);
        }

        protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
            super.onDraw(shape, canvas, paint);
        }

        protected boolean inflateTag(String name, Resources r, XmlPullParser parser,
                AttributeSet attrs) {
            inflateTagCalled = true;
            if (name.equals("testattrs")) {
                extendedAttrsSet = true;
                return true;
            }
            return super.inflateTag(name, r, parser, attrs);
        }
    }

    @Test
    public void testOnDraw() {
        Shape mockShape = spy(new MockShape());
        MockShapeDrawable shapeDrawable = new MockShapeDrawable(mockShape);
        verify(mockShape, never()).draw(any(), any());
        shapeDrawable.onDraw(mockShape, new Canvas(), new Paint());
        verify(mockShape, times(1)).draw(any(), any());
    }

    @Test(expected=NullPointerException.class)
    public void testOnDrawNull() {
        MockShape mockShape = new MockShape();
        MockShapeDrawable shapeDrawable = new MockShapeDrawable(mockShape);

        shapeDrawable.onDraw(null, null, new Paint());
    }

    public static class MockShape extends Shape {
        @Override
        public void draw(Canvas canvas, Paint paint) {
        }
    }

    @Test
    public void testSetAlpha() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();
        shapeDrawable.setAlpha(0);
        shapeDrawable.setAlpha(255);
        shapeDrawable.setAlpha(-1);
        shapeDrawable.setAlpha(256);
    }

    @Test
    public void testSetColorFilter() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();

        ColorFilter cf = new ColorFilter();
        shapeDrawable.setColorFilter(cf);
        assertSame(cf, shapeDrawable.getPaint().getColorFilter());

        shapeDrawable.setColorFilter(null);
        assertNull(shapeDrawable.getPaint().getColorFilter());
    }

    @Test
    public void testSetTint() {
        final ShapeDrawable d = new ShapeDrawable(new RectShape());
        d.setTint(Color.BLACK);
        d.setTintMode(Mode.SRC_OVER);
        assertEquals("Shape is tinted", Color.BLACK, DrawableTestUtils.getPixel(d, 0, 0));
    }

    @Test
    public void testSetDither() {
        ShapeDrawable shapeDrawable = new ShapeDrawable();

        shapeDrawable.setDither(true);
        assertTrue(shapeDrawable.getPaint().isDither());

        shapeDrawable.setDither(false);
        assertFalse(shapeDrawable.getPaint().isDither());
    }

    @Test
    public void testMutateGetShape() {
        ShapeDrawable a = new ShapeDrawable();
        a.setShape(new OvalShape());

        ShapeDrawable b = (ShapeDrawable) a.getConstantState().newDrawable();
        assertSame(a.getShape(), b.getShape());
        a.mutate();

        assertNotNull(a.getShape());
        assertNotNull(b.getShape());
        assertTrue(a.getShape() instanceof OvalShape);
        assertTrue(b.getShape() instanceof OvalShape);
        assertNotSame(a.getShape(), b.getShape());
    }
}
