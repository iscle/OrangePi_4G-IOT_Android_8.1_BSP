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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
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
public class PaintDrawableTest {
    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    public void testConstructor() {
        new PaintDrawable();
        new PaintDrawable(0x0);
        new PaintDrawable(0xffffffff);
    }

    @Test
    public void testSetCornerRadius() {
        PaintDrawable paintDrawable;

        // Test input value with a positive float
        // First, create a new PaintDrawable, which has no Shape
        paintDrawable = getPaintDrawable(false);
        assertNull(paintDrawable.getShape());
        paintDrawable.setCornerRadius(1.5f);
        assertNotNull(paintDrawable.getShape());
        assertTrue(paintDrawable.getShape() instanceof RoundRectShape);

        // Test input value as 0, this function will set its Shape as null
        paintDrawable = getPaintDrawable(true);
        assertNotNull(paintDrawable.getShape());
        paintDrawable.setCornerRadius(0);
        assertNull(paintDrawable.getShape());

        // Test input value as negative, this function will set its Shape as null
        paintDrawable = getPaintDrawable(true);
        assertNotNull(paintDrawable.getShape());
        paintDrawable.setCornerRadius(-2.5f);
        assertNull(paintDrawable.getShape());
    }

    @Test
    public void testSetCornerRadii() {
        PaintDrawable paintDrawable;

        // Test with null
        paintDrawable = getPaintDrawable(false);
        assertNull(paintDrawable.getShape());
        paintDrawable.setCornerRadii(null);
        assertNull(paintDrawable.getShape());

        // Test with a Shape
        paintDrawable = getPaintDrawable(true);
        assertNotNull(paintDrawable.getShape());
        paintDrawable.setCornerRadii(null);
        assertNull(paintDrawable.getShape());

        float[] radii = {
                4.5f, 6.0f, 4.5f, 6.0f, 4.5f, 6.0f, 4.5f, 6.0f
        };
        float[] fakeRadii = new float[7];

        // Test the array which is shorter than 8
        try {
            paintDrawable.setCornerRadii(fakeRadii);
            fail("setCornerRadii should throw a ArrayIndexOutOfBoundsException if array is"
                    + " shorter than 8.");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected
        }

        // Test the function with correct input float array
        assertNull(paintDrawable.getShape());
        paintDrawable.setCornerRadii(radii);
        assertNotNull(paintDrawable.getShape());
        assertTrue(paintDrawable.getShape() instanceof RoundRectShape);
    }

    @Test
    public void testInflateTag() throws XmlPullParserException, IOException {
        // Test name is not 'corners', and default executing path will load super's method.
        XmlResourceParser parser = getParser();
        AttributeSet attr = getAttributeSet(parser);
        assertNotNull(attr);
        gotoTag(parser, "padding");
        Rect padding = new Rect(0, 0, 10, 10);
        MyPaintDrawable paintDrawable = new MyPaintDrawable();
        // set mPadding not null
        paintDrawable.setPadding(padding);
        assertTrue(paintDrawable.getPadding(padding));
        //If the Tagname is not 'corners',inflateTag will invoke its super's version. and the super
        // version is a operation on mPadding, in this case, it will set mPadding to null, and
        // return false by getPadding.
        assertTrue(paintDrawable.inflateTag("padding", mResources, parser, attr));
        assertFalse(paintDrawable.getPadding(padding));

        // Test tag-name with ''
        parser = getParser();
        attr = getAttributeSet(parser);
        assertNotNull(attr);
        assertFalse(new MyPaintDrawable().inflateTag("", mResources, parser, attr));

        // Exceptional input Tests
        try {
            // null tag name
            new MyPaintDrawable().inflateTag(null, mResources, parser, attr);
            fail("Normally the function would throw a NullPointerException here.");
        } catch (NullPointerException e) {
            // expected
        }

        try {
            // null Resources
            gotoTag(parser, "padding");
            new MyPaintDrawable().inflateTag("padding", null, parser, attr);
            fail("Normally the function would throw a NullPointerException here.");
        } catch (NullPointerException e) {
            // expected
        }

        // null XmlPullParser
        parser = getParser();
        attr = getAttributeSet(parser);
        assertNotNull(attr);
        gotoTag(parser, "padding");
        paintDrawable = new MyPaintDrawable();
        assertTrue(paintDrawable.inflateTag("padding", mResources, null, attr));

        try {
            // null AttributeSet
            new MyPaintDrawable().inflateTag("padding", mResources, parser, null);
            fail("Normally the function would throw a NullPointerException here.");
        } catch (NullPointerException e) {
            // expected
        }

        assertNull(paintDrawable.getShape());

        parser = getParser();
        attr = getAttributeSet(parser);
        assertNotNull(attr);
        gotoTag(parser, "corners");
        assertTrue(paintDrawable.inflateTag("corners", mResources, parser, attr));
        assertNotNull(paintDrawable.getShape());
    }

    private XmlResourceParser getParser() {
        return mResources.getXml(R.drawable.paintdrawable_attr);
    }

    private AttributeSet getAttributeSet(XmlResourceParser parser) throws XmlPullParserException,
            IOException {
        int type;
        // FIXME: this come from
        // com.android.common.XmlUtils.beginDocument. It's better put
        // it up as a utility method of CTS test cases.
        type = parser.next();
        while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = parser.next();
        }
        if (type != XmlPullParser.START_TAG) {
            return null;
        }

        return Xml.asAttributeSet(parser);
    }

    private void gotoTag(XmlResourceParser parser, String tagName) throws XmlPullParserException,
            IOException {
        if (parser == null) {
            return;
        }

        while (parser.nextTag() != XmlPullParser.END_TAG) {
            String name = parser.getName();
            if (name.equals(tagName)) {
                break;
            }
            parser.nextText();
        }
    }

    private static class MyPaintDrawable extends PaintDrawable {
        @Override
        protected boolean inflateTag(String name, Resources r, XmlPullParser parser,
                AttributeSet attrs) {
            return super.inflateTag(name, r, parser, attrs);
        }
    }

    private static PaintDrawable getPaintDrawable(boolean hasShape) {
        PaintDrawable paintDrawable = new PaintDrawable();
        if (hasShape) {
            paintDrawable.setCornerRadius(1.5f);
        }
        return paintDrawable;
    }
}
