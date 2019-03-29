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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.cts.R;
import android.graphics.drawable.ColorDrawable;
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
public class ColorDrawableTest {
    @Test
    public void testConstructors() {
        new ColorDrawable();
        new ColorDrawable(0);
        new ColorDrawable(1);
    }

    @Test
    public void testAccessAlpha() {
        ColorDrawable colorDrawable = new ColorDrawable();
        assertEquals(0, colorDrawable.getAlpha());

        colorDrawable.setAlpha(128);
        assertEquals(0, colorDrawable.getAlpha());

        colorDrawable = new ColorDrawable(1 << 24);
        assertEquals(1, colorDrawable.getAlpha());

        colorDrawable.setAlpha(128);
        assertEquals(0, colorDrawable.getAlpha());

        colorDrawable.setAlpha(255);
        assertEquals(1, colorDrawable.getAlpha());
    }

    @Test
    public void testGetChangingConfigurations() {
        final ColorDrawable colorDrawable = new ColorDrawable();
        assertEquals(0, colorDrawable.getChangingConfigurations());

        colorDrawable.setChangingConfigurations(1);
        assertEquals(1, colorDrawable.getChangingConfigurations());

        colorDrawable.setChangingConfigurations(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE, colorDrawable.getChangingConfigurations());

        colorDrawable.setChangingConfigurations(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, colorDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetConstantState() {
        final ColorDrawable colorDrawable = new ColorDrawable();
        assertNotNull(colorDrawable.getConstantState());
        assertEquals(colorDrawable.getChangingConfigurations(),
                colorDrawable.getConstantState().getChangingConfigurations());
    }

    @Test
    public void testGetOpacity() {
        ColorDrawable colorDrawable = new ColorDrawable();
        assertEquals(PixelFormat.TRANSPARENT, colorDrawable.getOpacity());

        colorDrawable = new ColorDrawable(255 << 24);
        assertEquals(PixelFormat.OPAQUE, colorDrawable.getOpacity());

        colorDrawable = new ColorDrawable(1 << 24);
        assertEquals(PixelFormat.TRANSLUCENT, colorDrawable.getOpacity());
    }

    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        int eventType = -1;
        final ColorDrawable colorDrawable = new ColorDrawable();

        Resources resources = InstrumentationRegistry.getTargetContext().getResources();
        final XmlPullParser parser = resources.getXml(R.drawable.colordrawable_test);
        // start to parse XML document
        while (eventType != XmlResourceParser.START_TAG
                && eventType != XmlResourceParser.END_DOCUMENT) {
            try {
                eventType = parser.next();
            } catch (XmlPullParserException e) {
                fail(e.getMessage());
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }
        if (eventType == XmlResourceParser.START_TAG) {
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            colorDrawable.inflate(resources, parser, attrs);
            // set the alpha to 2 in colordrawable_test.xml
            assertEquals(2, colorDrawable.getAlpha());
        } else {
            fail("XML parser didn't find the start element of the specified xml file.");
        }
    }

    @Test
    public void testSetColorFilter() {
        final ColorDrawable d = new ColorDrawable(Color.WHITE);
        assertEquals(Color.WHITE, DrawableTestUtils.getPixel(d, 0, 0));

        d.setColorFilter(Color.BLACK, Mode.SRC_OVER);
        assertEquals(Color.BLACK, DrawableTestUtils.getPixel(d, 0, 0));

        d.setColorFilter(new PorterDuffColorFilter(Color.BLACK, Mode.SRC_OVER));
        assertEquals(Color.BLACK, DrawableTestUtils.getPixel(d, 0, 0));
    }

    @Test
    public void testSetTint() {
        final ColorDrawable d = new ColorDrawable(Color.WHITE);
        assertEquals(Color.WHITE, DrawableTestUtils.getPixel(d, 0, 0));

        d.setTint(Color.BLACK);
        d.setTintMode(Mode.SRC_OVER);
        assertEquals(Color.BLACK, DrawableTestUtils.getPixel(d, 0, 0));
    }

    @Test
    public void testDraw() {
        final ColorDrawable d = new ColorDrawable(Color.WHITE);
        final Bitmap b = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        final Canvas c = new Canvas(b);
        d.setBounds(0, 0, 1, 1);
        d.draw(c);
        final int pixel = b.getPixel(0, 0);
        b.recycle();
        assertEquals(Color.WHITE, pixel);
    }
}
