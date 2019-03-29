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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources.Theme;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.cts.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.LayoutDirection;
import android.util.Xml;
import android.view.Gravity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapDrawableTest {
    // A small value is actually making sure that the values are matching
    // exactly with the golden image.
    // We can increase the threshold if the Skia is drawing with some variance
    // on different devices. So far, the tests show they are matching correctly.
    private static final float PIXEL_ERROR_THRESHOLD = 0.03f;
    private static final float PIXEL_ERROR_COUNT_THRESHOLD = 0.005f;

    // Set true to generate golden images, false for normal tests.
    private static final boolean DBG_DUMP_PNG = false;

    // The target context.
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testConstructor() {
        // TODO: should default paint flags be left as an untested implementation detail?
        final int defaultPaintFlags = Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG |
            Paint.DEV_KERN_TEXT_FLAG | Paint.EMBEDDED_BITMAP_TEXT_FLAG;
        BitmapDrawable bitmapDrawable = new BitmapDrawable();
        assertNotNull(bitmapDrawable.getPaint());
        assertEquals(defaultPaintFlags,
                bitmapDrawable.getPaint().getFlags());
        assertNull(bitmapDrawable.getBitmap());

        Bitmap bitmap = Bitmap.createBitmap(200, 300, Config.ARGB_8888);
        bitmapDrawable = new BitmapDrawable(bitmap);
        assertNotNull(bitmapDrawable.getPaint());
        assertEquals(defaultPaintFlags,
                bitmapDrawable.getPaint().getFlags());
        assertEquals(bitmap, bitmapDrawable.getBitmap());

        new BitmapDrawable(mContext.getResources());

        new BitmapDrawable(mContext.getResources(), bitmap);

        new BitmapDrawable(mContext.getFilesDir().getPath());

        new BitmapDrawable(new ByteArrayInputStream("test constructor".getBytes()));

        // exceptional test
        new BitmapDrawable((Bitmap) null);

        new BitmapDrawable(mContext.getResources(), (String) null);

        new BitmapDrawable((String) null);

        new BitmapDrawable(mContext.getResources(), (InputStream) null);

        new BitmapDrawable((InputStream) null);
    }

    @Test
    public void testAccessGravity() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertEquals(Gravity.FILL, bitmapDrawable.getGravity());

        bitmapDrawable.setGravity(Gravity.CENTER);
        assertEquals(Gravity.CENTER, bitmapDrawable.getGravity());

        bitmapDrawable.setGravity(-1);
        assertEquals(-1, bitmapDrawable.getGravity());

        bitmapDrawable.setGravity(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, bitmapDrawable.getGravity());
    }

    @Test
    public void testAccessMipMap() {
        Bitmap source = BitmapFactory.decodeResource(mContext.getResources(), R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        bitmapDrawable.setMipMap(true);
        assertTrue(source.hasMipMap());

        bitmapDrawable.setMipMap(false);
        assertFalse(source.hasMipMap());
    }

    @Test
    public void testSetAntiAlias() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertFalse(bitmapDrawable.getPaint().isAntiAlias());

        bitmapDrawable.setAntiAlias(true);
        assertTrue(bitmapDrawable.getPaint().isAntiAlias());

        bitmapDrawable.setAntiAlias(false);
        assertFalse(bitmapDrawable.getPaint().isAntiAlias());
    }

    @Test
    public void testSetFilterBitmap() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertTrue(bitmapDrawable.getPaint().isFilterBitmap());

        bitmapDrawable.setFilterBitmap(false);
        assertFalse(bitmapDrawable.getPaint().isFilterBitmap());

        bitmapDrawable.setFilterBitmap(true);
        assertTrue(bitmapDrawable.getPaint().isFilterBitmap());
    }

    @Test
    public void testIsFilterBitmap() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertTrue(bitmapDrawable.isFilterBitmap());

        bitmapDrawable.setFilterBitmap(false);
        assertFalse(bitmapDrawable.isFilterBitmap());
        assertEquals(bitmapDrawable.isFilterBitmap(), bitmapDrawable.getPaint().isFilterBitmap());


        bitmapDrawable.setFilterBitmap(true);
        assertTrue(bitmapDrawable.isFilterBitmap());
        assertEquals(bitmapDrawable.isFilterBitmap(), bitmapDrawable.getPaint().isFilterBitmap());
    }

    @Test
    public void testSetDither() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertTrue(bitmapDrawable.getPaint().isDither());

        bitmapDrawable.setDither(false);
        assertFalse(bitmapDrawable.getPaint().isDither());

        bitmapDrawable.setDither(true);
        assertTrue(bitmapDrawable.getPaint().isDither());

    }

    @Test
    public void testAccessTileMode() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertNull(bitmapDrawable.getTileModeX());
        assertNull(bitmapDrawable.getTileModeY());
        assertNull(bitmapDrawable.getPaint().getShader());

        bitmapDrawable.setTileModeX(TileMode.CLAMP);
        assertEquals(TileMode.CLAMP, bitmapDrawable.getTileModeX());
        assertNull(bitmapDrawable.getTileModeY());

        bitmapDrawable.draw(new Canvas());
        assertNotNull(bitmapDrawable.getPaint().getShader());
        Shader oldShader = bitmapDrawable.getPaint().getShader();

        bitmapDrawable.setTileModeY(TileMode.REPEAT);
        assertEquals(TileMode.CLAMP, bitmapDrawable.getTileModeX());
        assertEquals(TileMode.REPEAT, bitmapDrawable.getTileModeY());

        bitmapDrawable.draw(new Canvas());
        assertNotSame(oldShader, bitmapDrawable.getPaint().getShader());
        oldShader = bitmapDrawable.getPaint().getShader();

        bitmapDrawable.setTileModeXY(TileMode.REPEAT, TileMode.MIRROR);
        assertEquals(TileMode.REPEAT, bitmapDrawable.getTileModeX());
        assertEquals(TileMode.MIRROR, bitmapDrawable.getTileModeY());

        bitmapDrawable.draw(new Canvas());
        assertNotSame(oldShader, bitmapDrawable.getPaint().getShader());
        oldShader = bitmapDrawable.getPaint().getShader();

        bitmapDrawable.setTileModeX(TileMode.MIRROR);
        assertEquals(TileMode.MIRROR, bitmapDrawable.getTileModeX());
        assertEquals(TileMode.MIRROR, bitmapDrawable.getTileModeY());

        bitmapDrawable.draw(new Canvas());
        assertNotSame(oldShader, bitmapDrawable.getPaint().getShader());
    }

    @Test
    public void testGetChangingConfigurations() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertEquals(0, bitmapDrawable.getChangingConfigurations());

        bitmapDrawable.setChangingConfigurations(1);
        assertEquals(1, bitmapDrawable.getChangingConfigurations());

        bitmapDrawable.setChangingConfigurations(2);
        assertEquals(2, bitmapDrawable.getChangingConfigurations());
    }

    @Test
    public void testSetAlpha() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertEquals(255, bitmapDrawable.getPaint().getAlpha());

        bitmapDrawable.setAlpha(0);
        assertEquals(0, bitmapDrawable.getPaint().getAlpha());

        bitmapDrawable.setAlpha(100);
        assertEquals(100, bitmapDrawable.getPaint().getAlpha());

        // exceptional test
        bitmapDrawable.setAlpha(-1);
        assertEquals(255, bitmapDrawable.getPaint().getAlpha());

        bitmapDrawable.setAlpha(256);
        assertEquals(0, bitmapDrawable.getPaint().getAlpha());
    }

    @Test
    public void testSetColorFilter() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        assertNull(bitmapDrawable.getPaint().getColorFilter());

        ColorFilter colorFilter = new ColorFilter();
        bitmapDrawable.setColorFilter(colorFilter);
        assertSame(colorFilter, bitmapDrawable.getPaint().getColorFilter());

        bitmapDrawable.setColorFilter(null);
        assertNull(bitmapDrawable.getPaint().getColorFilter());
    }

    @Test
    public void testSetTint() {
        final InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        final BitmapDrawable d = new BitmapDrawable(source);

        d.setTint(Color.BLACK);
        d.setTintMode(Mode.SRC_OVER);
        assertEquals("Nine-patch is tinted", Color.BLACK, DrawableTestUtils.getPixel(d, 0, 0));

        d.setTintList(null);
        d.setTintMode(null);
    }

    @Test
    public void testGetOpacity() {
        BitmapDrawable bitmapDrawable = new BitmapDrawable();
        assertEquals(Gravity.FILL, bitmapDrawable.getGravity());
        assertEquals(PixelFormat.TRANSLUCENT, bitmapDrawable.getOpacity());

        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        bitmapDrawable = new BitmapDrawable(source);
        assertEquals(Gravity.FILL, bitmapDrawable.getGravity());
        assertEquals(PixelFormat.OPAQUE, bitmapDrawable.getOpacity());
        bitmapDrawable.setGravity(Gravity.BOTTOM);
        assertEquals(PixelFormat.TRANSLUCENT, bitmapDrawable.getOpacity());

        source = mContext.getResources().openRawResource(R.raw.testimage);
        bitmapDrawable = new BitmapDrawable(source);
        assertEquals(Gravity.FILL, bitmapDrawable.getGravity());
        assertEquals(PixelFormat.OPAQUE, bitmapDrawable.getOpacity());
        bitmapDrawable.setAlpha(120);
        assertEquals(PixelFormat.TRANSLUCENT, bitmapDrawable.getOpacity());
    }

    @Test
    public void testGetConstantState() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);
        ConstantState constantState = bitmapDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(0, constantState.getChangingConfigurations());

        bitmapDrawable.setChangingConfigurations(1);
        constantState = bitmapDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(1, constantState.getChangingConfigurations());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetIntrinsicSize() {
        BitmapDrawable bitmapDrawable = new BitmapDrawable();
        assertEquals(0, bitmapDrawable.getIntrinsicWidth());
        assertEquals(0, bitmapDrawable.getIntrinsicHeight());

        Bitmap bitmap = Bitmap.createBitmap(200, 300, Config.RGB_565);
        bitmapDrawable = new BitmapDrawable(bitmap);
        bitmapDrawable.setTargetDensity(bitmap.getDensity());
        assertEquals(200, bitmapDrawable.getIntrinsicWidth());
        assertEquals(300, bitmapDrawable.getIntrinsicHeight());

        InputStream source = mContext.getResources().openRawResource(R.drawable.size_48x48);
        bitmapDrawable = new BitmapDrawable(source);
        bitmapDrawable.setTargetDensity(bitmapDrawable.getBitmap().getDensity());
        assertEquals(48, bitmapDrawable.getIntrinsicWidth());
        assertEquals(48, bitmapDrawable.getIntrinsicHeight());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testSetTargetDensity() {
        int sourceWidth, targetWidth;
        int sourceHeight, targetHeight;
        int sourceDensity, targetDensity;
        BitmapDrawable bitmapDrawable;
        Bitmap bitmap;

        sourceWidth = 200;
        sourceHeight = 300;
        bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        bitmapDrawable = new BitmapDrawable(bitmap);
        sourceDensity = bitmap.getDensity();
        targetDensity = canvas.getDensity();
        bitmapDrawable.setTargetDensity(canvas);
        targetWidth = DrawableTestUtils.scaleBitmapFromDensity(
                sourceWidth, sourceDensity, targetDensity);
        targetHeight = DrawableTestUtils.scaleBitmapFromDensity(
                sourceHeight, sourceDensity, targetDensity);
        assertEquals(targetWidth, bitmapDrawable.getIntrinsicWidth());
        assertEquals(targetHeight, bitmapDrawable.getIntrinsicHeight());

        sourceWidth = 200;
        sourceHeight = 300;
        bitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Config.RGB_565);
        bitmapDrawable = new BitmapDrawable(bitmap);
        sourceDensity = bitmap.getDensity();
        targetDensity = mContext.getResources().getDisplayMetrics().densityDpi;
        bitmapDrawable.setTargetDensity(mContext.getResources().getDisplayMetrics());
        targetWidth = DrawableTestUtils.scaleBitmapFromDensity(
                sourceWidth, sourceDensity, targetDensity);
        targetHeight = DrawableTestUtils.scaleBitmapFromDensity(
                sourceHeight, sourceDensity, targetDensity);
        assertEquals(targetWidth, bitmapDrawable.getIntrinsicWidth());
        assertEquals(targetHeight, bitmapDrawable.getIntrinsicHeight());

        sourceWidth = 48;
        sourceHeight = 48;
        InputStream source = mContext.getResources().openRawResource(R.drawable.size_48x48);
        bitmapDrawable = new BitmapDrawable(source);
        bitmap = bitmapDrawable.getBitmap();
        sourceDensity = bitmap.getDensity();
        targetDensity = sourceDensity * 2;
        bitmapDrawable.setTargetDensity(targetDensity);
        targetWidth = DrawableTestUtils.scaleBitmapFromDensity(
                sourceWidth, sourceDensity, targetDensity);
        targetHeight = DrawableTestUtils.scaleBitmapFromDensity(
                sourceHeight, sourceDensity, targetDensity);
        assertEquals(targetWidth, bitmapDrawable.getIntrinsicWidth());
        assertEquals(targetHeight, bitmapDrawable.getIntrinsicHeight());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testInflate() throws IOException, XmlPullParserException {
        BitmapDrawable bitmapDrawable = new BitmapDrawable();

        XmlResourceParser parser = mContext.getResources().getXml(R.xml.bitmapdrawable);
        AttributeSet attrs = DrawableTestUtils.getAttributeSet(
                mContext.getResources().getXml(R.xml.bitmapdrawable), "bitmap_allattrs");
        bitmapDrawable.inflate(mContext.getResources(), parser, attrs);
        assertEquals(Gravity.TOP | Gravity.RIGHT, bitmapDrawable.getGravity());
        assertTrue(bitmapDrawable.getPaint().isDither());
        assertTrue(bitmapDrawable.getPaint().isAntiAlias());
        assertFalse(bitmapDrawable.getPaint().isFilterBitmap());
        assertEquals(TileMode.REPEAT, bitmapDrawable.getTileModeX());
        assertEquals(TileMode.REPEAT, bitmapDrawable.getTileModeY());

        bitmapDrawable = new BitmapDrawable();
        attrs = DrawableTestUtils.getAttributeSet(
                mContext.getResources().getXml(R.xml.bitmapdrawable), "bitmap_partattrs");
        // when parser is null
        bitmapDrawable.inflate(mContext.getResources(), null, attrs);
        assertEquals(Gravity.CENTER, bitmapDrawable.getGravity());
        assertEquals(TileMode.MIRROR, bitmapDrawable.getTileModeX());
        assertEquals(TileMode.MIRROR, bitmapDrawable.getTileModeY());
        // default value
        assertTrue(bitmapDrawable.getPaint().isDither());
        assertFalse(bitmapDrawable.getPaint().isAntiAlias());
        assertTrue(bitmapDrawable.getPaint().isFilterBitmap());

        attrs = DrawableTestUtils.getAttributeSet(
                mContext.getResources().getXml(R.xml.bitmapdrawable), "bitmap_wrongsrc");
        try {
            bitmapDrawable = new BitmapDrawable();
            bitmapDrawable.inflate(mContext.getResources(), parser, attrs);
            fail("Should throw XmlPullParserException if the bitmap source can't be decoded.");
        } catch (XmlPullParserException e) {
        }

        attrs = DrawableTestUtils.getAttributeSet(
                mContext.getResources().getXml(R.xml.bitmapdrawable), "bitmap_nosrc");
        try {
            bitmapDrawable = new BitmapDrawable();
            bitmapDrawable.inflate(mContext.getResources(), parser, attrs);
            fail("Should throw XmlPullParserException if the bitmap src is not defined.");
        } catch (XmlPullParserException e) {
        }

        attrs = DrawableTestUtils.getAttributeSet(
                mContext.getResources().getXml(R.xml.bitmapdrawable), "bitmap_allattrs");
        try {
            bitmapDrawable = new BitmapDrawable();
            bitmapDrawable.inflate(null, parser, attrs);
            fail("Should throw NullPointerException if resource is null");
        } catch (NullPointerException e) {
        }

        try {
            bitmapDrawable = new BitmapDrawable();
            bitmapDrawable.inflate(mContext.getResources(), parser, null);
            fail("Should throw NullPointerException if attribute set is null");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testDraw() {
        InputStream source = mContext.getResources().openRawResource(R.raw.testimage);
        BitmapDrawable bitmapDrawable = new BitmapDrawable(source);

        // if the function draw() does not throw any exception, we think it is right.
        bitmapDrawable.draw(new Canvas());

        // input null as param
        try {
            bitmapDrawable.draw(null);
            fail("Should throw NullPointerException.");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testMutate() {
        Resources resources = mContext.getResources();
        BitmapDrawable d1 = (BitmapDrawable) resources.getDrawable(R.drawable.testimage);
        BitmapDrawable d2 = (BitmapDrawable) resources.getDrawable(R.drawable.testimage);
        BitmapDrawable d3 = (BitmapDrawable) resources.getDrawable(R.drawable.testimage);
        int restoreAlpha = d1.getAlpha();

        try {
            // verify bad behavior - modify before mutate pollutes other drawables
            d1.setAlpha(100);
            assertEquals(100, d1.getPaint().getAlpha());
            assertEquals(100, d2.getPaint().getAlpha());
            assertEquals(100, d3.getPaint().getAlpha());

            d1.mutate();
            d1.setAlpha(200);
            assertEquals(200, d1.getPaint().getAlpha());
            assertEquals(100, d2.getPaint().getAlpha());
            assertEquals(100, d3.getPaint().getAlpha());
            d2.setAlpha(50);
            assertEquals(200, d1.getPaint().getAlpha());
            assertEquals(50, d2.getPaint().getAlpha());
            assertEquals(50, d3.getPaint().getAlpha());
        } finally {
            // restore externally visible state, since other tests may use the drawable
            resources.getDrawable(R.drawable.testimage).setAlpha(restoreAlpha);
        }
    }
}
