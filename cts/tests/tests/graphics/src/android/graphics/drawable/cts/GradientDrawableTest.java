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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
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
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GradientDrawableTest {
    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    public void testConstructor() {
        int[] color = new int[] {1, 2, 3};

        new GradientDrawable();
        new GradientDrawable(GradientDrawable.Orientation.BL_TR, color);
        new GradientDrawable(null, null);
    }

    @Test
    public void testGetOpacity() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        assertEquals("Default opacity is TRANSLUCENT",
                PixelFormat.TRANSLUCENT, gradientDrawable.getOpacity());

        gradientDrawable.setColor(Color.TRANSPARENT);
        assertEquals("Color.TRANSPARENT is TRANSLUCENT",
                PixelFormat.TRANSLUCENT, gradientDrawable.getOpacity());

        gradientDrawable.setColor(0x80FFFFFF);
        assertEquals("0x80FFFFFF is TRANSLUCENT",
                PixelFormat.TRANSLUCENT, gradientDrawable.getOpacity());

        gradientDrawable.setColors(new int[] { Color.RED, Color.TRANSPARENT });
        assertEquals("{ RED, TRANSPARENT } is TRANSLUCENT",
                PixelFormat.TRANSLUCENT, gradientDrawable.getOpacity());

        gradientDrawable.setColors(new int[] { Color.RED, Color.BLUE });
        assertEquals("{ RED, BLUE } is OPAQUE",
                PixelFormat.OPAQUE, gradientDrawable.getOpacity());

        gradientDrawable.setColor(Color.RED);
        assertEquals("RED is OPAQUE",
                PixelFormat.OPAQUE, gradientDrawable.getOpacity());

        gradientDrawable.setCornerRadius(10);
        gradientDrawable.setColor(Color.RED);
        assertEquals("RED with corner radius is TRANSLUCENT",
                PixelFormat.TRANSLUCENT, gradientDrawable.getOpacity());

    }

    @Test
    public void testSetOrientation() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        Orientation orientation;

        orientation = Orientation.BL_TR;
        gradientDrawable.setOrientation(orientation);
        assertEquals("Orientation set/get are symmetric",
                orientation, gradientDrawable.getOrientation());
    }

    @Test
    public void testSetCornerRadii() {
        float[] radii = new float[] {1.0f, 2.0f, 3.0f};

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadii(radii);

        float[] radiiActual = gradientDrawable.getCornerRadii();
        assertArrayEquals("Gradient radius set/get are symmetric",
                radii, radiiActual, 0);

        ConstantState constantState = gradientDrawable.getConstantState();
        assertNotNull(constantState);

        // input null as param
        gradientDrawable.setCornerRadii(null);
    }

    @Test
    public void testSetCornerRadius() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setCornerRadius(2.5f);
        gradientDrawable.setCornerRadius(-2.5f);
    }

    @Test
    public void testGetCornerRadius() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadius(5.5f);
        assertEquals(gradientDrawable.getCornerRadius(), 5.5f, 0);
        float[] radii = new float[] {1.0f, 2.0f, 3.0f};
        gradientDrawable.setCornerRadii(radii);
        assertEquals(5.5f, gradientDrawable.getCornerRadius(), 0);
        gradientDrawable.setShape(GradientDrawable.OVAL);
        assertEquals(5.5f, gradientDrawable.getCornerRadius(), 0);
        gradientDrawable.setCornerRadii(null);
        assertEquals(0, gradientDrawable.getCornerRadius(), 0);
    }

    @Test
    public void testSetStroke() {
        helpTestSetStroke(2, Color.RED);
        helpTestSetStroke(-2, Color.TRANSPARENT);
        helpTestSetStroke(0, 0);
    }

    private void helpTestSetStroke(int width, int color) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setStroke(width, color);
        // TODO: Verify stroke properties.
    }

    @Test
    public void testSetStroke_WidthGap() {
        verifySetStroke_WidthGap(2, Color.RED, 3.4f, 5.5f);
        verifySetStroke_WidthGap(-2, Color.TRANSPARENT, -3.4f, -5.5f);
        verifySetStroke_WidthGap(0, 0, 0, (float) 0.0f);
    }

    private void verifySetStroke_WidthGap(int width, int color,
            float dashWidth, float dashGap) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setStroke(width, color, dashWidth, dashGap);
        // TODO: Verify stroke properties.
    }

    @Test
    public void testSetStrokeList() {
        verifySetStrokeList(2, ColorStateList.valueOf(Color.RED));
        verifySetStrokeList(-2, ColorStateList.valueOf(Color.TRANSPARENT));
        verifySetStrokeList(0, null);
    }

    private void verifySetStrokeList(int width,
            ColorStateList colorList) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setStroke(width, colorList);
        // TODO: Verify stroke properties.
    }

    @Test
    public void testSetStrokeList_WidthGap() {
        verifySetStrokeList_WidthGap(2, ColorStateList.valueOf(Color.RED), 3.4f, 5.5f);
        verifySetStrokeList_WidthGap(-2, ColorStateList.valueOf(Color.TRANSPARENT), -3.4f, -5.5f);
        verifySetStrokeList_WidthGap(0, null, 0.0f, 0.0f);
    }

    private void verifySetStrokeList_WidthGap(int width, ColorStateList colorList,
            float dashWidth, float dashGap) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setStroke(width, colorList, dashWidth, dashGap);
        // TODO: Verify stroke properties.
    }

    @Test
    public void testSetSize() {
        verifySetSize(6, 4);
        verifySetSize(-30, -40);
        verifySetSize(0, 0);
        verifySetSize(Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    private void verifySetSize(int width, int height) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(width, height);
        assertEquals(width, gradientDrawable.getIntrinsicWidth());
        assertEquals(height, gradientDrawable.getIntrinsicHeight());
    }

    @Test
    public void testSetShape() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        int shape;

        shape = GradientDrawable.OVAL;
        gradientDrawable.setShape(shape);
        assertEquals("Gradient shape set/get are symmetric",
                shape, gradientDrawable.getShape());

        shape = -1;
        gradientDrawable.setShape(shape);
        assertEquals("Invalid gradient shape set/get are symmetric",
                shape, gradientDrawable.getShape());
    }

    @Test
    public void testSetGradientType() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        int gradientType;

        gradientType = GradientDrawable.LINEAR_GRADIENT;
        gradientDrawable.setGradientType(gradientType);
        assertEquals("Gradient type set/get are symmetric",
                gradientType, gradientDrawable.getGradientType());

        gradientType = -1;
        gradientDrawable.setGradientType(gradientType);
        assertEquals("Invalid gradient type set/get are symmetric",
                gradientType, gradientDrawable.getGradientType());
    }

    @Test
    public void testSetGradientCenter() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        float centerX;
        float centerY;

        centerX = 0.5f;
        centerY = 0.5f;
        assertEquals(centerX, gradientDrawable.getGradientCenterX(), 0.01f);
        assertEquals(centerY, gradientDrawable.getGradientCenterY(), 0.01f);

        centerX = -0.5f;
        centerY = -0.5f;
        gradientDrawable.setGradientCenter(centerX, centerY);
        assertEquals(centerX, gradientDrawable.getGradientCenterX(), 0.01f);
        assertEquals(centerY, gradientDrawable.getGradientCenterY(), 0.01f);

        centerX = 0.0f;
        centerY = 0.0f;
        gradientDrawable.setGradientCenter(centerX, centerY);
        assertEquals(centerX, gradientDrawable.getGradientCenterX(), 0.01f);
        assertEquals(centerY, gradientDrawable.getGradientCenterY(), 0.01f);
    }

    @Test
    public void testSetGradientRadius() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setGradientRadius(3.6f);
        gradientDrawable.setGradientRadius(-3.6f);
    }

    @Test
    public void testSetUseLevel() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        boolean useLevel;

        assertFalse("Default useLevel is false", gradientDrawable.getUseLevel());

        useLevel = true;
        gradientDrawable.setUseLevel(useLevel);
        assertEquals("Gradient set/get useLevel is symmetric",
                useLevel, gradientDrawable.getUseLevel());

        useLevel = false;
        gradientDrawable.setUseLevel(useLevel);
        assertEquals("Gradient set/get useLevel is symmetric",
                useLevel, gradientDrawable.getUseLevel());
    }

    @Test
    public void testDraw() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        Canvas c = new Canvas();
        gradientDrawable.draw(c);

        // input null as param
        gradientDrawable.draw(null);
    }

    @Test
    public void testSetColor() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        int color;

        color = Color.RED;
        gradientDrawable.setColor(color);
        assertEquals("Color was set to " + color, color,
                gradientDrawable.getColor().getDefaultColor());

        color = Color.TRANSPARENT;
        gradientDrawable.setColor(color);
        assertEquals("Color was set to " + color, color,
                gradientDrawable.getColor().getDefaultColor());
    }

    @Test
    public void testSetColors() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        int[] colors;

        colors = new int[] { Color.RED };
        gradientDrawable.setColors(colors);
        assertArrayEquals("Color was set to " + Arrays.toString(colors),
                colors, gradientDrawable.getColors());

        colors = null;
        gradientDrawable.setColors(colors);
        assertArrayEquals("Color was set to " + Arrays.toString(colors),
                colors, gradientDrawable.getColors());
    }

    @Test
    public void testSetColorList() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        ColorStateList color;

        color = ColorStateList.valueOf(Color.RED);
        gradientDrawable.setColor(color);
        assertEquals("Color was set to RED", color, gradientDrawable.getColor());

        color = null;
        gradientDrawable.setColor(color);
        assertEquals("Color was set to null (TRANSPARENT)", color, gradientDrawable.getColor());
    }

    @Test
    public void testGetChangingConfigurations() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        assertEquals(0, gradientDrawable.getChangingConfigurations());

        gradientDrawable.setChangingConfigurations(10);
        assertEquals(10, gradientDrawable.getChangingConfigurations());

        gradientDrawable.setChangingConfigurations(-20);
        assertEquals(-20, gradientDrawable.getChangingConfigurations());
    }

    @Test
    public void testSetAlpha() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setAlpha(1);
        gradientDrawable.setAlpha(-1);
    }

    @Test
    public void testSetDither() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setDither(true);
        gradientDrawable.setDither(false);
    }

    @Test
    public void testSetColorFilter() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        ColorFilter cf = new ColorFilter();
        gradientDrawable.setColorFilter(cf);

        // input null as param
        gradientDrawable.setColorFilter(null);
    }

    @Test
    public void testInflate() throws XmlPullParserException, IOException {
        GradientDrawable gradientDrawable = new GradientDrawable();
        Rect rect = new Rect();
        assertFalse(gradientDrawable.getPadding(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(0, rect.right);
        assertEquals(0, rect.bottom);

        XmlPullParser parser = mResources.getXml(R.drawable.gradientdrawable);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        // find the START_TAG
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }
        assertEquals(XmlPullParser.START_TAG, type);

        // padding is set in gradientdrawable.xml
        gradientDrawable.inflate(mResources, parser, attrs);
        assertTrue(gradientDrawable.getPadding(rect));
        assertEquals(4, rect.left);
        assertEquals(2, rect.top);
        assertEquals(6, rect.right);
        assertEquals(10, rect.bottom);

        try {
            gradientDrawable.getPadding(null);
            fail("did not throw NullPointerException when rect is null.");
        } catch (NullPointerException e) {
            // expected, test success
        }

        try {
            gradientDrawable.inflate(null, null, null);
            fail("did not throw NullPointerException when parameters are null.");
        } catch (NullPointerException e) {
            // expected, test success
        }
    }

    @Test
    public void testInflateGradientRadius() throws XmlPullParserException, IOException {
        Rect parentBounds = new Rect(0, 0, 100, 100);

        GradientDrawable gradientDrawable;
        float radius;

        gradientDrawable = (GradientDrawable) mResources.getDrawable(
                R.drawable.gradientdrawable_radius_base);
        gradientDrawable.setBounds(parentBounds);
        radius = gradientDrawable.getGradientRadius();
        assertEquals(25.0f, radius, 0.0f);

        gradientDrawable = (GradientDrawable) mResources.getDrawable(
                R.drawable.gradientdrawable_radius_parent);
        gradientDrawable.setBounds(parentBounds);
        radius = gradientDrawable.getGradientRadius();
        assertEquals(50.0f, radius, 0.0f);
    }

    @Test
    public void testGetIntrinsicWidth() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(6, 4);
        assertEquals(6, gradientDrawable.getIntrinsicWidth());

        gradientDrawable.setSize(-10, -20);
        assertEquals(-10, gradientDrawable.getIntrinsicWidth());
    }

    @Test
    public void testGetIntrinsicHeight() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(5, 3);
        assertEquals(3, gradientDrawable.getIntrinsicHeight());

        gradientDrawable.setSize(-5, -15);
        assertEquals(-15, gradientDrawable.getIntrinsicHeight());
    }

    @Test
    public void testGetConstantState() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        assertNotNull(gradientDrawable.getConstantState());
    }

    @Test
    public void testMutate() {
        GradientDrawable d1 =
                (GradientDrawable) mResources.getDrawable(R.drawable.gradientdrawable);
        GradientDrawable d2 =
                (GradientDrawable) mResources.getDrawable(R.drawable.gradientdrawable);
        GradientDrawable d3 =
                (GradientDrawable) mResources.getDrawable(R.drawable.gradientdrawable);

        d1.setSize(10, 10);
        assertEquals(10, d1.getIntrinsicHeight());
        assertEquals(10, d1.getIntrinsicWidth());
        assertEquals(10, d2.getIntrinsicHeight());
        assertEquals(10, d2.getIntrinsicWidth());
        assertEquals(10, d3.getIntrinsicHeight());
        assertEquals(10, d3.getIntrinsicWidth());

        d1.mutate();
        d1.setSize(20, 30);
        assertEquals(30, d1.getIntrinsicHeight());
        assertEquals(20, d1.getIntrinsicWidth());
        assertEquals(10, d2.getIntrinsicHeight());
        assertEquals(10, d2.getIntrinsicWidth());
        assertEquals(10, d3.getIntrinsicHeight());
        assertEquals(10, d3.getIntrinsicWidth());

        d2.setSize(40, 50);
        assertEquals(30, d1.getIntrinsicHeight());
        assertEquals(20, d1.getIntrinsicWidth());
        assertEquals(50, d2.getIntrinsicHeight());
        assertEquals(40, d2.getIntrinsicWidth());
        assertEquals(50, d3.getIntrinsicHeight());
        assertEquals(40, d3.getIntrinsicWidth());
    }

    @Test
    public void testPreloadDensity() throws XmlPullParserException, IOException {
        final int densityDpi = mResources.getConfiguration().densityDpi;
        try {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
            verifyPreloadDensityInner(mResources, densityDpi);
        } finally {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
        }
    }

    @Test
    public void testPreloadDensity_tvdpi() throws XmlPullParserException, IOException {
        final int densityDpi = mResources.getConfiguration().densityDpi;
        try {
            DrawableTestUtils.setResourcesDensity(mResources, 213);
            verifyPreloadDensityInner(mResources, 213);
        } finally {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
        }
    }

    private void verifyPreloadDensityInner(Resources res, int densityDpi)
            throws XmlPullParserException, IOException {
        final Rect tempPadding = new Rect();

        // Capture initial state at default density.
        final XmlResourceParser parser = DrawableTestUtils.getResourceParser(
                res, R.drawable.gradient_drawable_density);
        final GradientDrawable preloadedDrawable = new GradientDrawable();
        preloadedDrawable.inflate(res, parser, Xml.asAttributeSet(parser));
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        final int origWidth = preloadedDrawable.getIntrinsicWidth();
        final int origHeight = preloadedDrawable.getIntrinsicHeight();
        final Rect origPadding = new Rect();
        preloadedDrawable.getPadding(origPadding);

        // Set density to approximately half of original. Unlike offsets, which are
        // truncated, dimensions are rounded to the nearest pixel.
        DrawableTestUtils.setResourcesDensity(res, densityDpi / 2);
        final GradientDrawable halfDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable(res);
        // NOTE: densityDpi may not be an even number, so account for *actual* scaling in asserts
        final float approxHalf = (float)(densityDpi / 2) / densityDpi;
        assertEquals(Math.round(origWidth * approxHalf), halfDrawable.getIntrinsicWidth());
        assertEquals(Math.round(origHeight * approxHalf), halfDrawable.getIntrinsicHeight());
        assertTrue(halfDrawable.getPadding(tempPadding));
        assertEquals((int) (origPadding.left * approxHalf), tempPadding.left);

        // Set density to double original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi * 2);
        final GradientDrawable doubleDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(origWidth * 2, doubleDrawable.getIntrinsicWidth());
        assertEquals(origHeight * 2, doubleDrawable.getIntrinsicHeight());
        assertTrue(doubleDrawable.getPadding(tempPadding));
        assertEquals(origPadding.left * 2, tempPadding.left);

        // Restore original density.
        DrawableTestUtils.setResourcesDensity(res, densityDpi);
        final GradientDrawable origDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable();
        assertEquals(origWidth, origDrawable.getIntrinsicWidth());
        assertEquals(origHeight, origDrawable.getIntrinsicHeight());
        assertTrue(origDrawable.getPadding(tempPadding));
        assertEquals(origPadding, tempPadding);

        // Reproduce imprecise truncated scale down, and back up. Note these aren't rounded.
        final float approxDouble = 1 / approxHalf;
        final Rect sloppyOrigPadding = new Rect();
        sloppyOrigPadding.left = (int)(approxDouble * ((int)(origPadding.left * approxHalf)));
        sloppyOrigPadding.top = (int)(approxDouble * ((int)(origPadding.top * approxHalf)));
        sloppyOrigPadding.right = (int)(approxDouble * ((int)(origPadding.right * approxHalf)));
        sloppyOrigPadding.bottom = (int)(approxDouble * ((int)(origPadding.bottom * approxHalf)));

        // Ensure theme density is applied correctly.
        final Theme t = res.newTheme();
        halfDrawable.applyTheme(t);
        assertEquals(Math.round(approxDouble * Math.round(origWidth * approxHalf)),
                halfDrawable.getIntrinsicWidth());
        assertEquals(Math.round(approxDouble * Math.round(origHeight * approxHalf)),
                halfDrawable.getIntrinsicHeight());
        assertTrue(halfDrawable.getPadding(tempPadding));
        assertEquals(sloppyOrigPadding, tempPadding);
        doubleDrawable.applyTheme(t);
        assertEquals(origWidth, doubleDrawable.getIntrinsicWidth());
        assertEquals(origHeight, doubleDrawable.getIntrinsicHeight());
        assertTrue(doubleDrawable.getPadding(tempPadding));
        assertEquals(origPadding, tempPadding);
    }
}
