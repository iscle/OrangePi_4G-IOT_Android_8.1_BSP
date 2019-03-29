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

package android.util.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TypedValueTest {
    @Test
    public void testConstructor() {
        new TypedValue();
    }

    @Test
    public void testGetFloat() {
        final float expected = Float.intBitsToFloat(99);
        TypedValue tv = new TypedValue();
        tv.data = 99;
        assertEquals(expected, tv.getFloat(), 0.0f);
    }

    @Test
    public void testComplexToDimension() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.density = 1.1f;
        dm.heightPixels = 100;
        dm.scaledDensity = 2.1f;
        dm.xdpi = 200f;
        dm.ydpi = 300f;
        final float expected = TypedValue.applyDimension((10 >> TypedValue.COMPLEX_UNIT_SHIFT)
                & TypedValue.COMPLEX_UNIT_MASK, TypedValue.complexToFloat(10), dm);

        assertEquals(expected, TypedValue.complexToDimension(10, dm), 0.0f);
        assertEquals((int)expected, TypedValue.complexToDimensionPixelOffset(10, dm));
    }

    @Test
    public void testSetTo() {
        TypedValue tv1 = new TypedValue();
        TypedValue tv2 = new TypedValue();
        tv1.assetCookie = 1;
        tv1.changingConfigurations = 2;
        tv1.data = 3;
        tv1.resourceId = 4;
        tv1.string = "test";
        tv1.type = 5;

        tv2.setTo(tv1);

        assertEquals(1, tv2.assetCookie);
        assertEquals(-1, tv2.changingConfigurations);
        assertEquals(3, tv2.data);
        assertEquals(4, tv2.resourceId);
        assertEquals("test", tv2.string);
        assertEquals(5, tv2.type);
    }

    @Test
    public void testGetFraction() {
        // set the expected value
        final float expected = TypedValue.complexToFraction(10, 1.1f, 2.1f) ;
        TypedValue tv = new TypedValue();
        tv.data = 10;
        assertEquals(expected, tv.getFraction(1.1f, 2.1f), 0.0f);
    }

    @Test
    public void testComplexToDimensionPixelSize() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.density = 1.1f;
        dm.heightPixels = 100;
        dm.scaledDensity = 2.1f;
        dm.xdpi = 200f;
        dm.ydpi = 300f;

        // static function just calculate and return it.
        assertEquals(1, TypedValue.complexToDimensionPixelSize(1000, dm));
        assertEquals(1, TypedValue.complexToDimensionPixelSize(9999, dm));
        assertEquals(1, TypedValue.complexToDimensionPixelSize(5000, dm));
        assertEquals(102, TypedValue.complexToDimensionPixelSize(3333, dm));
        assertEquals(1, TypedValue.complexToDimensionPixelSize(2222, dm));
        assertEquals(1, TypedValue.complexToDimensionPixelSize(1500, dm));
        assertEquals(0, TypedValue.complexToDimensionPixelSize(10, dm));

    }

    @Test
    public void testComplexToFraction() {
        final int data1 = 1;
        final float base1 = 1.1f;
        final float pbase1 = 2.2f;
        final float expected1 = 0.0f;

        final int data2 = 100000;
        final float base2 = 1.1f;
        final float pbase2 = 2.2f;
        final float expected2 = 0.013092041f;
        assertEquals(expected1, TypedValue.complexToFraction(data1, base1, pbase1), 0.0f);
        assertEquals(expected2, TypedValue.complexToFraction(data2, base2, pbase2), 0.0f);
    }

    @Test
    public void testToString() {
        TypedValue tv = new TypedValue();
        tv.assetCookie = 1;
        tv.changingConfigurations = 2;
        tv.data = 3;
        tv.resourceId = 4;
        tv.string = "test";
        tv.type = 5;
        assertNotNull(tv.toString());

        tv.type = 3;
        assertNotNull(tv.toString());
    }

    @Test
    public void testApplyDimension() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.density = 1.1f;
        dm.heightPixels = 100;
        dm.scaledDensity = 2.1f;
        dm.xdpi = 200f;
        dm.ydpi = 300f;

        assertEquals(10.0f, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, 10, dm), 0.0f);
        assertEquals(10 * dm.density, TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, dm), 0.0f);
        assertEquals(10 * dm.scaledDensity, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                10, dm), 0.0f);
        assertEquals(10 * dm.xdpi * (1.0f/72),
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PT, 10, dm), 0.0f);
        assertEquals(10 * dm.xdpi, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_IN, 10, dm),
                0.0f);
        assertEquals(10 * dm.xdpi * (1.0f / 25.4f), TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_MM, 10, dm), 0.0f);

        assertEquals(0.0f, TypedValue.applyDimension(-1, 10, dm), 0.0f);
    }

    @Test
    public void testCoerceToString1() {
        TypedValue tv = new TypedValue();
        tv.assetCookie = 1;
        tv.changingConfigurations = 2;
        tv.data = 3;
        tv.resourceId = 4;
        tv.string = "test";
        tv.type = 5;

        assertNotNull(tv.coerceToString());

        tv.type = 3;
        assertNotNull(tv.coerceToString());
    }

    @Test
    public void testCoerceToString2() {
        assertNull(TypedValue.coerceToString(TypedValue.TYPE_NULL, 10));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_REFERENCE, 10));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_ATTRIBUTE, 10));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_FLOAT, 10));

        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_DIMENSION, 2));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_FRACTION, 1));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_INT_HEX, 10));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_INT_BOOLEAN, 1));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_INT_BOOLEAN, 0));
        assertNotNull(TypedValue.coerceToString(TypedValue.TYPE_FIRST_COLOR_INT, 10));
        assertNotNull(TypedValue.coerceToString(0x11, 10));
        assertNull(TypedValue.coerceToString(-1, 10));
    }

    @Test
    public void testComplexToFloat() {
        final int complex1 = 1;
        final float expected1 = 0.0f;
        final int complex2 = 17;
        final float expected2 = 0.0f;
        final int complex3 = 9999;
        final float expected3 = 39.0f;
        assertEquals(expected1, TypedValue.complexToFloat(complex1), 0.0f);
        assertEquals(expected2, TypedValue.complexToFloat(complex2), 0.0f);
        assertEquals(expected3, TypedValue.complexToFloat(complex3), 0.0f);
    }

    @Test
    public void testGetDimension() {
        DisplayMetrics dm = new DisplayMetrics();
        dm.density = 1.1f;
        dm.heightPixels = 100;
        dm.scaledDensity = 2.1f;
        dm.xdpi = 200f;
        dm.ydpi = 300f;

        TypedValue tv = new TypedValue();
        tv.data = 10;
        tv.getDimension(dm);

        assertEquals(TypedValue.complexToDimension(10, dm), tv.getDimension(dm), 0.0f);
    }

    @Test
    public void testGetComplexUnit() {
        TypedValue tv = new TypedValue();
        tv.data = 256;
        assertEquals(TypedValue.COMPLEX_UNIT_PX, tv.getComplexUnit());
        tv.data = 257;
        assertEquals(TypedValue.COMPLEX_UNIT_DIP, tv.getComplexUnit());
        tv.data = 258;
        assertEquals(TypedValue.COMPLEX_UNIT_SP, tv.getComplexUnit());
        tv.data = 259;
        assertEquals(TypedValue.COMPLEX_UNIT_PT, tv.getComplexUnit());
        tv.data = 260;
        assertEquals(TypedValue.COMPLEX_UNIT_IN, tv.getComplexUnit());
        tv.data = 261;
        assertEquals(TypedValue.COMPLEX_UNIT_MM, tv.getComplexUnit());
        tv.data = 21474864;
        assertEquals(TypedValue.COMPLEX_UNIT_FRACTION, tv.getComplexUnit());
        tv.data = 21474865;
        assertEquals(TypedValue.COMPLEX_UNIT_FRACTION_PARENT, tv.getComplexUnit());
    }
}
