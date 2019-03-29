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
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapRGBAF16Test {
    private Bitmap mOpaqueBitmap;
    private Bitmap mTransparentBitmap;
    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;

        // The bitmaps are in raw-nodpi/ to guarantee aapt and framework leave them untouched
        mOpaqueBitmap = BitmapFactory.decodeResource(mResources, R.raw.p3_opaque, options);
        mTransparentBitmap = BitmapFactory.decodeResource(mResources, R.raw.p3_transparent, options);
    }

    @Test
    public void testDecode() {
        assertNotNull(mOpaqueBitmap);
        assertEquals(Config.RGBA_F16, mOpaqueBitmap.getConfig());
        assertFalse(mOpaqueBitmap.hasAlpha());

        assertNotNull(mTransparentBitmap);
        assertEquals(Config.RGBA_F16, mTransparentBitmap.getConfig());
        assertTrue(mTransparentBitmap.hasAlpha());
    }

    @Test
    public void testScaling() {
        Bitmap scaled = Bitmap.createScaledBitmap(mOpaqueBitmap,
                mOpaqueBitmap.getWidth() / 2, mOpaqueBitmap.getHeight() / 2, true);
        assertNotNull(scaled);
        assertEquals(Config.RGBA_F16, scaled.getConfig());
        assertFalse(scaled.hasAlpha());

        scaled = Bitmap.createScaledBitmap(mTransparentBitmap,
                mTransparentBitmap.getWidth() / 2, mTransparentBitmap.getHeight() / 2, true);
        assertNotNull(scaled);
        assertEquals(Config.RGBA_F16, scaled.getConfig());
        assertTrue(scaled.hasAlpha());
    }

    @Test
    public void testCopy() {
        Bitmap copy = Bitmap.createBitmap(mOpaqueBitmap);
        assertNotNull(copy);
        assertEquals(Config.RGBA_F16, copy.getConfig());
        assertFalse(copy.hasAlpha());

        copy = Bitmap.createBitmap(mTransparentBitmap);
        assertNotNull(copy);
        assertEquals(Config.RGBA_F16, copy.getConfig());
        assertTrue(copy.hasAlpha());
    }

    @Test
    public void testCreate() {
        Bitmap b = Bitmap.createBitmap(64, 64, Config.RGBA_F16, false);
        assertNotNull(b);
        assertEquals(Config.RGBA_F16, b.getConfig());
        assertFalse(b.hasAlpha());

        b = Bitmap.createBitmap(64, 64, Config.RGBA_F16);
        assertNotNull(b);
        assertEquals(Config.RGBA_F16, b.getConfig());
        assertTrue(b.hasAlpha());
    }

    @Test
    public void testGetPixel() {
        // Opaque pixels from opaque bitmap
        assertEquals(0xff0f131f, mOpaqueBitmap.getPixel(0, 0));
        assertEquals(0xff0f1421, mOpaqueBitmap.getPixel(1, 0));
        assertEquals(0xff101523, mOpaqueBitmap.getPixel(2, 0));

        // Opaque pixels from transparent bitmap
        assertEquals(0xffff0000, mTransparentBitmap.getPixel(0, 0));
        assertEquals(0xff00ff00, mTransparentBitmap.getPixel(1, 0));
        assertEquals(0xff0000ff, mTransparentBitmap.getPixel(2, 0));

        // Transparent pixels from transparent bitmap
        assertEquals(0x7fff0000, mTransparentBitmap.getPixel(61, 63));
        assertEquals(0x7f00ff00, mTransparentBitmap.getPixel(62, 63));
        assertEquals(0x7f0000ff, mTransparentBitmap.getPixel(63, 63));
    }

    @Test
    public void testSetPixel() {
        int before = mOpaqueBitmap.getPixel(5, 5);
        mOpaqueBitmap.setPixel(5, 5, 0x7f102030);
        int after = mOpaqueBitmap.getPixel(5, 5);
        assertTrue(before != after);
        assertEquals(0x7f102030, after);

        before = mTransparentBitmap.getPixel(5, 5);
        mTransparentBitmap.setPixel(5, 5, 0x7f102030);
        after = mTransparentBitmap.getPixel(5, 5);
        assertTrue(before != after);
        assertEquals(0x7f102030, after);
    }

    @Test
    public void testCopyFromA8() {
        Bitmap res = BitmapFactory.decodeResource(mResources, R.drawable.alpha_mask);
        Bitmap mask = Bitmap.createBitmap(res.getWidth(), res.getHeight(),
                Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(mask);
        c.drawBitmap(res, 0, 0, null);

        Bitmap b = mask.copy(Config.RGBA_F16, false);
        assertNotNull(b);
    }
}
