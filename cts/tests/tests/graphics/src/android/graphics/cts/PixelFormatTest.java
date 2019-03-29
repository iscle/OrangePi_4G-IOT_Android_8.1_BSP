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
import static org.junit.Assert.assertTrue;

import android.graphics.PixelFormat;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PixelFormatTest {
    @Test
    public void testConstructor() {
        new PixelFormat();
    }

    @Test
    public void testGetPixelFormatInfo() {
        PixelFormat pixelFormat = new PixelFormat();

        PixelFormat.getPixelFormatInfo(PixelFormat.RGBA_8888, pixelFormat);
        assertEquals(4, pixelFormat.bytesPerPixel);
        assertEquals(32, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGBX_8888, pixelFormat);
        assertEquals(4, pixelFormat.bytesPerPixel);
        assertEquals(32, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGB_888, pixelFormat);
        assertEquals(3, pixelFormat.bytesPerPixel);
        assertEquals(24, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGB_565, pixelFormat);
        assertEquals(2, pixelFormat.bytesPerPixel);
        assertEquals(16, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGBA_5551, pixelFormat);
        assertEquals(2, pixelFormat.bytesPerPixel);
        assertEquals(16, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGBA_4444, pixelFormat);
        assertEquals(2, pixelFormat.bytesPerPixel);
        assertEquals(16, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.A_8, pixelFormat);
        assertEquals(1, pixelFormat.bytesPerPixel);
        assertEquals(8, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.L_8, pixelFormat);
        assertEquals(1, pixelFormat.bytesPerPixel);
        assertEquals(8, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.LA_88, pixelFormat);
        assertEquals(2, pixelFormat.bytesPerPixel);
        assertEquals(16, pixelFormat.bitsPerPixel);

        PixelFormat.getPixelFormatInfo(PixelFormat.RGB_332, pixelFormat);
        assertEquals(1, pixelFormat.bytesPerPixel);
        assertEquals(8, pixelFormat.bitsPerPixel);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelFormatInfoUnknown() {
        PixelFormat.getPixelFormatInfo(PixelFormat.UNKNOWN, new PixelFormat());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelFormatInfoJpeg() {
        PixelFormat.getPixelFormatInfo(PixelFormat.JPEG, new PixelFormat());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelFormatInfoTranslucent() {
        PixelFormat.getPixelFormatInfo(PixelFormat.TRANSLUCENT, new PixelFormat());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelFormatInfoTransparent() {
        PixelFormat.getPixelFormatInfo(PixelFormat.TRANSPARENT, new PixelFormat());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelFormatInfoOpaque() {
        PixelFormat.getPixelFormatInfo(PixelFormat.OPAQUE, new PixelFormat());
    }

    @Test
    public void testFormatHasAlpha() {
        assertTrue(PixelFormat.formatHasAlpha(PixelFormat.RGBA_8888));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.RGBX_8888));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.RGB_888));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.RGB_565));
        assertTrue(PixelFormat.formatHasAlpha(PixelFormat.RGBA_5551));
        assertTrue(PixelFormat.formatHasAlpha(PixelFormat.RGBA_4444));
        assertTrue(PixelFormat.formatHasAlpha(PixelFormat.A_8));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.L_8));
        assertTrue(PixelFormat.formatHasAlpha(PixelFormat.LA_88));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.RGB_332));
        assertFalse(PixelFormat.formatHasAlpha(PixelFormat.UNKNOWN));
    }
}
