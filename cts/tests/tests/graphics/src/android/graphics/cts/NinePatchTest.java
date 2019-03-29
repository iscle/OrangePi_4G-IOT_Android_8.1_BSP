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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NinePatchTest {
    private static final int ALPHA_OPAQUE = 0xFF;
    private static final String NAME = "TESTNAME";
    private static final int WIDTH = 80;
    private static final int HEIGTH = 120;
    private static final int[] COLOR = new int[WIDTH * HEIGTH];

    private NinePatch mNinePatch;
    private Bitmap mBitmap;
    private BitmapFactory.Options mOptNoScale;
    private Resources mRes;
    private byte[] mChunk;

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
        mOptNoScale = new BitmapFactory.Options();
        mOptNoScale.inDensity = mOptNoScale.inTargetDensity = mRes.getDisplayMetrics().densityDpi;
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.opaque, mOptNoScale);
        mChunk = mBitmap.getNinePatchChunk();
        assertNotNull(mChunk);
        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);
    }

    @Test(expected=Exception.class)
    public void testConstructorTooShort() {
        mNinePatch = new NinePatch(mBitmap, new byte[2]);
    }

    @Test(expected=Exception.class)
    public void testConstructorNamedTooShort() {
        mNinePatch = new NinePatch(mBitmap, new byte[2], NAME);
    }

    @Test
    public void testConstructor() {
        mNinePatch = new NinePatch(mBitmap, mChunk);
        assertEquals(mBitmap, mNinePatch.getBitmap());
        assertEquals(null, mNinePatch.getName());

        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);
        assertEquals(mBitmap, mNinePatch.getBitmap());
        assertEquals(NAME, mNinePatch.getName());
    }

    @Test
    public void testPaintAccessors() {
        Paint p = new Paint();
        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);
        assertNull(mNinePatch.getPaint());

        mNinePatch.setPaint(p);
        assertEquals(p, mNinePatch.getPaint());

        mNinePatch.setPaint(null);
        assertNull(mNinePatch.getPaint());
    }

    @Test
    public void testIsNinePatchChunk() {
        assertTrue(NinePatch.isNinePatchChunk(mChunk));
        Bitmap bitmap = Bitmap.createBitmap(COLOR, 10, 10, Bitmap.Config.ARGB_4444);
        assertFalse(NinePatch.isNinePatchChunk(bitmap.getNinePatchChunk()));
        assertFalse(NinePatch.isNinePatchChunk(null));
    }

    @Test
    public void testDraw() {
        Bitmap expected = BitmapFactory.decodeResource(mRes, R.drawable.scaled1, mOptNoScale);

        Bitmap bitmap = Bitmap.createBitmap(expected.getWidth(), expected.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        RectF rectf = new RectF(0, 0, c.getWidth(), c.getHeight());
        mNinePatch.draw(c, rectf);
        verifyBitmapWithAlpha(expected, bitmap, ALPHA_OPAQUE);

        expected = BitmapFactory.decodeResource(mRes, R.drawable.scaled2, mOptNoScale);
        bitmap = Bitmap.createBitmap(expected.getWidth(), expected.getHeight(),
                Bitmap.Config.ARGB_8888);
        c = new Canvas(bitmap);
        Rect rect = new Rect(0, 0, c.getWidth(), c.getHeight());
        mNinePatch.draw(c, rect);
        verifyBitmapWithAlpha(expected, bitmap, ALPHA_OPAQUE);

        bitmap = Bitmap.createBitmap(expected.getWidth(), expected.getHeight(),
                Bitmap.Config.ARGB_8888);
        c = new Canvas(bitmap);
        rect = new Rect(0, 0, c.getWidth(), c.getHeight());
        final int alpha = 128;
        Paint p = new Paint();
        p.setAlpha(alpha);
        mNinePatch.draw(c, rect, p);
        verifyBitmapWithAlpha(expected, bitmap, alpha);

        bitmap = Bitmap.createBitmap(expected.getWidth(), expected.getHeight(),
                Bitmap.Config.ARGB_8888);
        c = new Canvas(bitmap);
        rectf = new RectF(0, 0, c.getWidth(), c.getHeight());
        mNinePatch.setPaint(p);
        mNinePatch.draw(c, rectf);
        verifyBitmapWithAlpha(expected, bitmap, alpha);
    }

    private void verifyBitmapWithAlpha(Bitmap expected, Bitmap bitmap, int alpha) {
        assertEquals(expected.getWidth(), bitmap.getWidth());
        assertEquals(expected.getHeight(), bitmap.getHeight());
        int width = expected.getWidth();
        int height = expected.getHeight();

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int expectedPixel = expected.getPixel(i, j);
                int actualPixel = bitmap.getPixel(i, j);
                int expectedAlpha = Color.alpha(expectedPixel);
                int actualAlpha = Color.alpha(actualPixel);
                expectedPixel &= 0xFFFFFF;
                actualPixel &= 0xFFFFFF;
                assertEquals(expectedPixel, actualPixel);
                assertEquals(expectedAlpha * alpha / ALPHA_OPAQUE, actualAlpha, 1);
            }
        }
    }

    @Test
    public void testHasAlpha() {
        assertFalse(mNinePatch.hasAlpha());
        assertEquals(mNinePatch.hasAlpha(), mBitmap.hasAlpha());

        Bitmap bitmap =
            BitmapFactory.decodeResource(mRes, R.drawable.transparent_border, mOptNoScale);
        byte[] chunk = bitmap.getNinePatchChunk();
        NinePatch ninePatch = new NinePatch(bitmap, chunk, NAME);
        assertTrue(ninePatch.hasAlpha());
        assertEquals(ninePatch.hasAlpha(), bitmap.hasAlpha());
    }

    @Test
    public void testGetHeight() {
        assertEquals(5, mNinePatch.getHeight());
        assertEquals(mNinePatch.getHeight(), mBitmap.getHeight());
    }

    @Test
    public void testGetWidth() {
        assertEquals(5, mNinePatch.getHeight());
        assertEquals(mNinePatch.getWidth(), mBitmap.getWidth());
    }

    @Test
    public void testGetDensity() {
        mBitmap.setDensity(11);
        assertEquals(11, mNinePatch.getDensity());
        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);
        assertEquals(mNinePatch.getDensity(), mBitmap.getDensity());
    }

    @Test
    public void testGetTransparentRegion() {
        // no transparency in opaque bitmap
        Rect location = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        Region region = mNinePatch.getTransparentRegion(location);
        assertNull(region);

        // transparent border of 1px
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.transparent_border, mOptNoScale);
        mChunk = mBitmap.getNinePatchChunk();
        assertNotNull(mChunk);
        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);

        location = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        region = mNinePatch.getTransparentRegion(location);
        assertNotNull(region);
        Rect regionBounds = region.getBounds();
        verifyBounds(regionBounds, 0, 0, 5, 5);

        location = new Rect(0, 0, mBitmap.getWidth() * 2, mBitmap.getHeight() * 2);
        region = mNinePatch.getTransparentRegion(location);
        assertNotNull(region);
        regionBounds = region.getBounds();
        verifyBounds(regionBounds, 0, 0, 10, 10);

        // transparent padding of 1px on the right side
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.transparent_right, mOptNoScale);
        mChunk = mBitmap.getNinePatchChunk();
        assertNotNull(mChunk);
        mNinePatch = new NinePatch(mBitmap, mChunk, NAME);

        location = new Rect(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
        region = mNinePatch.getTransparentRegion(location);
        assertNotNull(region);
        regionBounds = region.getBounds();
        verifyBounds(regionBounds, 4, 0, 5, 5);

        location = new Rect(0, 0, mBitmap.getWidth() * 2, mBitmap.getHeight() * 2);
        region = mNinePatch.getTransparentRegion(location);
        regionBounds = region.getBounds();
        verifyBounds(regionBounds, 9, 0, 10, 10);
    }

    private void verifyBounds(Rect regionBounds, int left, int top, int right, int bottom) {
        assertEquals(left, regionBounds.left);
        assertEquals(top, regionBounds.top);
        assertEquals(right, regionBounds.right);
        assertEquals(bottom, regionBounds.bottom);
    }
}
