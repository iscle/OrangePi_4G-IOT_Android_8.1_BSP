/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ColorSpace.Named;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.graphics.Color.alpha;
import static android.graphics.Color.blue;
import static android.graphics.Color.colorSpace;
import static android.graphics.Color.convert;
import static android.graphics.Color.green;
import static android.graphics.Color.luminance;
import static android.graphics.Color.pack;
import static android.graphics.Color.red;
import static android.graphics.Color.toArgb;
import static android.graphics.Color.valueOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Color_ColorLongTest {
    @Test
    public void testRed() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertEquals(0.5f, red(pack(0.5f, 0.0f, 1.0f)), 0.01f);
        assertEquals(0.5f, red(pack(0.5f, 0.0f, 1.0f, 1.0f, p3)), 0.01f);
    }

    @Test
    public void testGreen() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertEquals(0.7f, green(pack(0.5f, 0.7f, 1.0f)), 0.01f);
        assertEquals(0.7f, green(pack(0.5f, 0.7f, 1.0f, 1.0f, p3)), 0.01f);
    }

    @Test
    public void testBlue() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertEquals(1.0f, blue(pack(0.5f, 0.7f, 1.0f)), 0.01f);
        assertEquals(1.0f, blue(pack(0.5f, 0.7f, 1.0f, 1.0f, p3)), 0.01f);
    }

    @Test
    public void testAlpha() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertEquals(0.25f, alpha(pack(0.5f, 0.7f, 1.0f, 0.25f)), 0.01f);
        assertEquals(0.25f, alpha(pack(0.5f, 0.7f, 1.0f, 0.25f, p3)), 0.01f);
    }

    @Test
    public void testColorSpace() {
        ColorSpace srgb = ColorSpace.get(Named.SRGB);
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertEquals(srgb, colorSpace(pack(0.5f, 0.7f, 1.0f)));
        assertEquals(p3, colorSpace(pack(0.5f, 0.7f, 1.0f, 1.0f, p3)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidColorSpace() {
        colorSpace(0xffffffffffffffffL);
    }

    @Test
    public void testIsSrgb() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertTrue(Color.isSrgb(pack(0.5f, 0.7f, 1.0f)));
        assertFalse(Color.isSrgb(pack(0.5f, 0.7f, 1.0f, 1.0f, p3)));

        assertTrue(Color.valueOf(0.5f, 0.7f, 1.0f).isSrgb());
        assertFalse(Color.valueOf(0.5f, 0.7f, 1.0f, 1.0f, p3).isSrgb());
    }

    @Test
    public void testIsWideGamut() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertFalse(Color.isWideGamut(pack(0.5f, 0.7f, 1.0f)));
        assertTrue(Color.isWideGamut(pack(0.5f, 0.7f, 1.0f, 1.0f, p3)));

        assertFalse(Color.valueOf(0.5f, 0.7f, 1.0f).isWideGamut());
        assertTrue(Color.valueOf(0.5f, 0.7f, 1.0f, 1.0f, p3).isWideGamut());
    }

    @Test
    public void testIsInColorSpace() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        assertFalse(Color.isInColorSpace(pack(0.5f, 0.7f, 1.0f), p3));
        assertTrue(Color.isInColorSpace(pack(0.5f, 0.7f, 1.0f, 1.0f, p3), p3));
    }

    @Test
    public void testValueOf() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);

        Color color1 = valueOf(0x7fff00ff);
        assertEquals(1.0f, color1.red(), 0.01f);
        assertEquals(0.0f, color1.green(), 0.01f);
        assertEquals(1.0f, color1.blue(), 0.01f);
        assertEquals(0.5f, color1.alpha(), 0.01f);
        assertTrue(color1.getColorSpace().isSrgb());

        Color color2 = valueOf(0.5f, 0.7f, 1.0f);
        assertEquals(0.5f, color2.red(), 0.01f);
        assertEquals(0.7f, color2.green(), 0.01f);
        assertEquals(1.0f, color2.blue(), 0.01f);
        assertEquals(1.0f, color2.alpha(), 0.01f);
        assertTrue(color2.getColorSpace().isSrgb());

        Color color3 = valueOf(0.5f, 0.5f, 1.0f, 0.25f);
        assertEquals(0.5f, color3.red(), 0.01f);
        assertEquals(0.5f, color3.green(), 0.01f);
        assertEquals(1.0f, color3.blue(), 0.01f);
        assertEquals(0.25f, color3.alpha(), 0.01f);
        assertTrue(color3.getColorSpace().isSrgb());

        Color color4 = valueOf(0.5f, 0.5f, 1.0f, 0.25f, p3);
        assertEquals(0.5f, color4.red(), 0.01f);
        assertEquals(0.5f, color4.green(), 0.01f);
        assertEquals(1.0f, color4.blue(), 0.01f);
        assertEquals(0.25f, color4.alpha(), 0.01f);
        assertFalse(color4.getColorSpace().isSrgb());
        assertEquals(p3, color4.getColorSpace());

        Color color5 = valueOf(pack(0.5f, 0.5f, 1.0f, 0.25f, p3));
        assertEquals(0.5f, color5.red(), 0.01f);
        assertEquals(0.5f, color5.green(), 0.01f);
        assertEquals(1.0f, color5.blue(), 0.01f);
        assertEquals(0.25f, color5.alpha(), 0.01f);
        assertFalse(color5.getColorSpace().isSrgb());
        assertEquals(p3, color5.getColorSpace());

        Color color6 = valueOf(pack(0.5f, 0.5f, 1.0f, 0.25f));
        assertEquals(0.5f, color6.red(), 0.01f);
        assertEquals(0.5f, color6.green(), 0.01f);
        assertEquals(1.0f, color6.blue(), 0.01f);
        assertEquals(0.25f, color6.alpha(), 0.01f);
        assertTrue(color6.getColorSpace().isSrgb());

        Color color7 = valueOf(new float[] { 0.5f, 0.5f, 1.0f, 0.25f }, ColorSpace.get(Named.SRGB));
        assertEquals(0.5f, color7.red(), 0.01f);
        assertEquals(0.5f, color7.green(), 0.01f);
        assertEquals(1.0f, color7.blue(), 0.01f);
        assertEquals(0.25f, color7.alpha(), 0.01f);
        assertTrue(color7.getColorSpace().isSrgb());

        float[] components = { 0.5f, 0.5f, 1.0f, 0.25f, 0.5f, 0.8f };
        Color color8 = valueOf(components, ColorSpace.get(Named.SRGB));
        assertEquals(0.5f, color8.red(), 0.01f);
        assertEquals(0.5f, color8.green(), 0.01f);
        assertEquals(1.0f, color8.blue(), 0.01f);
        assertEquals(0.25f, color8.alpha(), 0.01f);
        assertTrue(color8.getColorSpace().isSrgb());
        // Make sure we received a copy
        components[0] = 127.0f;
        assertNotEquals(color8.red(), components[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValueOfFailure() {
        valueOf(new float[] { 0.5f, 0.5f, 1.0f }, ColorSpace.get(Named.SRGB));
    }

    @Test
    public void testModel() {
        Color c = valueOf(new float[] { 0.5f, 0.5f, 1.0f, 1.0f }, ColorSpace.get(Named.CIE_XYZ));
        assertEquals(ColorSpace.Model.XYZ, c.getModel());
    }

    @Test
    public void testComponents() {
        Color c = valueOf(0.1f, 0.2f, 0.3f, 0.4f);

        assertEquals(4, c.getComponentCount());
        assertEquals(c.red(), c.getComponent(0), 0.0f);
        assertEquals(c.green(), c.getComponent(1), 0.0f);
        assertEquals(c.blue(), c.getComponent(2), 0.0f);
        assertEquals(c.alpha(), c.getComponent(3), 0.0f);

        float[] components = c.getComponents();
        assertEquals(c.getComponentCount(), components.length);
        assertEquals(c.red(), components[0], 0.0f);
        assertEquals(c.green(), components[1], 0.0f);
        assertEquals(c.blue(), components[2], 0.0f);
        assertEquals(c.alpha(), components[3], 0.0f);

        // Make sure we received a copy
        components[0] = 127.0f;
        assertNotEquals(c.red(), components[0]);

        float[] componentsRet = c.getComponents(components);
        assertSame(components, componentsRet);
        assertEquals(c.getComponentCount(), componentsRet.length);
        assertEquals(c.red(), componentsRet[0], 0.0f);
        assertEquals(c.green(), componentsRet[1], 0.0f);
        assertEquals(c.blue(), componentsRet[2], 0.0f);
        assertEquals(c.alpha(), componentsRet[3], 0.0f);

        componentsRet = c.getComponents(null);
        assertNotNull(componentsRet);
        assertEquals(c.getComponentCount(), componentsRet.length);
        assertEquals(c.red(), componentsRet[0], 0.0f);
        assertEquals(c.green(), componentsRet[1], 0.0f);
        assertEquals(c.blue(), componentsRet[2], 0.0f);
        assertEquals(c.alpha(), componentsRet[3], 0.0f);
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testComponentOutOfBounds() {
        valueOf(0.1f, 0.2f, 0.3f, 0.4f).getComponent(4);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetComponentOutOfBounds() {
        valueOf(0.1f, 0.2f, 0.3f, 0.4f).getComponents(new float[3]);
    }

    @Test
    public void testToArgb() {
        assertEquals(0xff8000ff, toArgb(pack(0.5f, 0.0f, 1.0f)));

        long color = pack(0.8912f, 0.4962f, 0.1164f, 1.0f, ColorSpace.get(Named.ADOBE_RGB));
        // Red if 0x7f instead of 0x80 because the rounding error caused by the
        // intermediate fp16 representation
        assertEquals(0xffff7f00, toArgb(color));

        assertEquals(0xff8000ff, valueOf(0.5f, 0.0f, 1.0f).toArgb());
        assertEquals(0xffff7f00,
                valueOf(0.8912f, 0.4962f, 0.1164f, 1.0f, ColorSpace.get(Named.ADOBE_RGB)).toArgb());
    }

    @Test
    public void testPackSrgb() {
        ColorSpace srgb = ColorSpace.get(Named.SRGB);

        long color1 = pack(0.5f, 0.0f, 1.0f);
        long color2 = pack(0.5f, 0.0f, 1.0f, 1.0f);
        long color3 = pack(0.5f, 0.0f, 1.0f, 1.0f, srgb);

        assertEquals(color1, color2);
        assertEquals(color1, color3);

        assertEquals(0xff8000ff, (int) (color1 >>> 32));

        long color4 = pack(0.5f, 0.0f, 1.0f);

        assertEquals(0.5f, red(color4), 0.01f);
        assertEquals(0.0f, green(color4), 0.01f);
        assertEquals(1.0f, blue(color4), 0.01f);
        assertEquals(1.0f, alpha(color4), 0.01f);

        long color5 = pack(0xff8000ff);

        assertEquals(color1, color5);
        assertEquals(0xff8000ff, (int) (color5 >>> 32));

        assertEquals(color1, valueOf(0.5f, 0.0f, 1.0f).pack());
    }

    @Test
    public void testPack() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);
        long color = pack(0.5f, 0.0f, 1.0f, 0.25f, p3);

        assertEquals(0.5f, red(color), 0.01f);
        assertEquals(0.0f, green(color), 0.01f);
        assertEquals(1.0f, blue(color), 0.01f);
        assertEquals(0.25f, alpha(color), 0.01f);

        assertEquals(color, valueOf(0.5f, 0.0f, 1.0f, 0.25f, p3).pack());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPackFailure() {
        ColorSpace colorSpace = new ColorSpace.Rgb("Fake",
                new float[] { 0.7347f, 0.2653f, 0.1596f, 0.8404f, 0.0366f, 0.0001f },
                new float[] { 0.34567f, 0.35850f }, x -> x * 2.0f, x -> x / 2.0f, 0.0f, 1.0f);
        pack(0.5f, 0.0f, 1.0f, 0.25f, colorSpace);
    }

    @Test
    public void testLuminanceSrgb() {
        assertEquals(0.0722f, luminance(pack(0.0f, 0.0f, 1.0f)), 0.000001f);
        assertEquals(0.2126f, luminance(pack(1.0f, 0.0f, 0.0f)), 0.000001f);
        assertEquals(0.7152f, luminance(pack(0.0f, 1.0f, 0.0f)), 0.000001f);
        assertEquals(1.0f,    luminance(pack(1.0f, 1.0f, 1.0f)), 0.0f);
        assertEquals(0.0f,    luminance(pack(0.0f, 0.0f, 0.0f)), 0.0f);

        assertEquals(0.0722f, valueOf(0.0f, 0.0f, 1.0f).luminance(), 0.000001f);
        assertEquals(0.2126f, valueOf(1.0f, 0.0f, 0.0f).luminance(), 0.000001f);
        assertEquals(0.7152f, valueOf(0.0f, 1.0f, 0.0f).luminance(), 0.000001f);
        assertEquals(1.0f,    valueOf(1.0f, 1.0f, 1.0f).luminance(), 0.0f);
        assertEquals(0.0f,    valueOf(0.0f, 0.0f, 0.0f).luminance(), 0.0f);
    }

    @Test
    public void testLuminance() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);
        assertEquals(0.0722f, luminance(pack(0.0f, 0.0f, 1.0f, 1.0f, p3)), 0.000001f);
        assertEquals(0.2126f, luminance(pack(1.0f, 0.0f, 0.0f, 1.0f, p3)), 0.000001f);
        assertEquals(0.7152f, luminance(pack(0.0f, 1.0f, 0.0f, 1.0f, p3)), 0.000001f);
        assertEquals(1.0f,    luminance(pack(1.0f, 1.0f, 1.0f, 1.0f, p3)), 0.0f);
        assertEquals(0.0f,    luminance(pack(0.0f, 0.0f, 0.0f, 1.0f, p3)), 0.0f);

        assertEquals(0.0722f, valueOf(0.0f, 0.0f, 1.0f, 1.0f, p3).luminance(), 0.000001f);
        assertEquals(0.2126f, valueOf(1.0f, 0.0f, 0.0f, 1.0f, p3).luminance(), 0.000001f);
        assertEquals(0.7152f, valueOf(0.0f, 1.0f, 0.0f, 1.0f, p3).luminance(), 0.000001f);
        assertEquals(1.0f,    valueOf(1.0f, 1.0f, 1.0f, 1.0f, p3).luminance(), 0.0f);
        assertEquals(0.0f,    valueOf(0.0f, 0.0f, 0.0f, 1.0f, p3).luminance(), 0.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLuminanceFunctionFailure() {
        luminance(pack(1.0f, 1.0f, 1.0f, 1.0f, ColorSpace.get(Named.CIE_LAB)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLuminanceMethodFailure() {
        valueOf(1.0f, 1.0f, 1.0f, 1.0f, ColorSpace.get(Named.CIE_LAB)).luminance();
    }

    @Test
    public void testConvertMethod() {
        Color sRgb = Color.valueOf(1.0f, 0.5f, 0.0f);
        Color adobeRgb = sRgb.convert(ColorSpace.get(Named.ADOBE_RGB));

        assertEquals(ColorSpace.get(Named.ADOBE_RGB), adobeRgb.getColorSpace());
        assertEquals(0.8912f, adobeRgb.red(), 0.001f);
        assertEquals(0.4962f, adobeRgb.green(), 0.001f);
        assertEquals(0.1164f, adobeRgb.blue(), 0.001f);
    }

    @Test
    public void testConvertColorInt() {
        long color = convert(0xffff8000, ColorSpace.get(Named.ADOBE_RGB));

        assertEquals(ColorSpace.get(Named.ADOBE_RGB), colorSpace(color));
        assertEquals(0.8912f, red(color), 0.01f);
        assertEquals(0.4962f, green(color), 0.01f);
        assertEquals(0.1164f, blue(color), 0.01f);
    }

    @Test
    public void testConvertColorLong() {
        long color = convert(pack(1.0f, 0.5f, 0.0f, 1.0f, ColorSpace.get(Named.DISPLAY_P3)),
                ColorSpace.get(Named.ADOBE_RGB));

        assertEquals(0.9499f, red(color), 0.01f);
        assertEquals(0.4597f, green(color), 0.01f);
        assertEquals(0.0000f, blue(color), 0.01f);
    }

    @Test
    public void testConvertConnector() {
        ColorSpace p3 = ColorSpace.get(Named.DISPLAY_P3);
        ColorSpace.Connector connector = ColorSpace.connect(p3, ColorSpace.get(Named.ADOBE_RGB));
        long color = convert(pack(1.0f, 0.5f, 0.0f, 1.0f, p3), connector);

        assertEquals(0.9499f, red(color), 0.01f);
        assertEquals(0.4597f, green(color), 0.01f);
        assertEquals(0.0000f, blue(color), 0.01f);

        color = convert(1.0f, 0.5f, 0.0f, 1.0f, connector);

        assertEquals(0.9499f, red(color), 0.01f);
        assertEquals(0.4597f, green(color), 0.01f);
        assertEquals(0.0000f, blue(color), 0.01f);
    }

    @Test
    public void testConvert() {
        long color = convert(1.0f, 0.5f, 0.0f, 1.0f,
                ColorSpace.get(Named.DISPLAY_P3), ColorSpace.get(Named.ADOBE_RGB));

        assertEquals(0.9499f, red(color), 0.01f);
        assertEquals(0.4597f, green(color), 0.01f);
        assertEquals(0.0000f, blue(color), 0.01f);
    }
}
