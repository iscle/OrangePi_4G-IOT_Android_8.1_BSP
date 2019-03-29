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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.graphics.ColorMatrix;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ColorMatrixTest {
    private static final float TOLERANCE = 0.0000001f;

    private static final float[] SOURCE = new float[] {
            0, 1, 2, 3, 4,
            5, 6, 7, 8, 9,
            10, 11, 12, 13, 14,
            15, 16, 17, 18, 19
    };

    private ColorMatrix mColorMatrix;

    @Before
    public void setup() {
        mColorMatrix = new ColorMatrix(SOURCE);
    }

    @Test
    public void testColorMatrix() {
        new ColorMatrix();

        ColorMatrix cM1 = new ColorMatrix(SOURCE);
        float[] fA1 = cM1.getArray();
        assertArrayEquals(SOURCE, fA1, 0.0f);

        ColorMatrix cM2 = new ColorMatrix(cM1);
        float[] fA2 = cM2.getArray();
        assertArrayEquals(fA1, fA2, 0.0f);
    }

    @Test
    public void testReset() {
        float[] ret = mColorMatrix.getArray();
        preCompare(ret);

        mColorMatrix.reset();
        ret = mColorMatrix.getArray();
        assertEquals(20, ret.length);

        for (int i = 0; i <= 19; i++) {
            if (0 == i % 6) {
                assertEquals(1.0f, ret[i], 0.0f);
                continue;
            }

            assertEquals(0.0f, ret[i], 0.0f);
        }
    }

    @Test
    public void testSet1() {
        float[] ret = mColorMatrix.getArray();
        preCompare(ret);

        float[] fArray = new float[] {
                19, 18, 17, 16, 15,
                14, 13, 12, 11, 10,
                9, 8, 7, 6, 5,
                4, 3, 2, 1, 0
        };

        mColorMatrix.set(fArray);
        ret = mColorMatrix.getArray();
        assertArrayEquals(fArray, ret, 0.0f);
    }

    @Test
    public void testSet2() {
        float[] ret = mColorMatrix.getArray();
        preCompare(ret);

        float[] fArray = new float[] {
                19, 18, 17, 16, 15,
                14, 13, 12, 11, 10,
                9, 8, 7, 6, 5,
                4, 3, 2, 1, 0
        };

        mColorMatrix.set(new ColorMatrix(fArray));
        ret = mColorMatrix.getArray();
        assertArrayEquals(fArray, ret, 0.0f);
    }

    @Test(expected=RuntimeException.class)
    public void testSetRotateIllegalAxis() {
        // abnormal case: IllegalArgument axis
        mColorMatrix.setRotate(4, 90);
    }

    @Test
    public void testSetRotate() {
        mColorMatrix.setRotate(0, 180);
        float[] ret = mColorMatrix.getArray();
        assertEquals(-1.0f, ret[6], TOLERANCE);
        assertEquals(-1.0f, ret[12], TOLERANCE);
        assertEquals(0, ret[7], TOLERANCE);
        assertEquals(0, ret[11], TOLERANCE);

        mColorMatrix.setRotate(1, 180);
        assertEquals(-1.0f, ret[0], TOLERANCE);
        assertEquals(-1.0f, ret[12], TOLERANCE);
        assertEquals(0, ret[2], TOLERANCE);
        assertEquals(0, ret[10], TOLERANCE);

        mColorMatrix.setRotate(2, 180);
        assertEquals(-1.0f, ret[0], TOLERANCE);
        assertEquals(-1.0f, ret[6], TOLERANCE);
        assertEquals(0, ret[1], TOLERANCE);
        assertEquals(0, ret[5], TOLERANCE);
    }

    @Test
    public void testSetSaturation() {
        mColorMatrix.setSaturation(0.5f);
        float[] ret = mColorMatrix.getArray();

        assertArrayEquals(new float[] {
                0.6065f, 0.3575f, 0.036f, 0.0f, 0.0f,
                0.1065f, 0.85749996f, 0.036f, 0.0f, 0.0f,
                0.1065f, 0.3575f, 0.536f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        }, ret, 0.0f);
    }

    @Test
    public void testSetScale() {
        float[] ret = mColorMatrix.getArray();
        preCompare(ret);

        mColorMatrix.setScale(2, 3, 4, 5);
        ret = mColorMatrix.getArray();

        assertEquals(20, ret.length);
        assertEquals(2.0f, ret[0], 0.0f);
        assertEquals(3.0f, ret[6], 0.0f);
        assertEquals(4.0f, ret[12], 0.0f);
        assertEquals(5.0f, ret[18], 0.0f);

        for (int i = 1; i <= 19; i++) {
            if (0 == i % 6) {
                continue;
            }

            assertEquals(0.0f, ret[i], 0.0f);
        }
    }

    @Test
    public void testSetRGB2YUV() {
        mColorMatrix.setRGB2YUV();
        float[] ret = mColorMatrix.getArray();

        assertArrayEquals(new float[] {
                0.299f, 0.587f, 0.114f, 0.0f, 0.0f,
                -0.16874f, -0.33126f, 0.5f, 0.0f, 0.0f,
                0.5f, -0.41869f, -0.08131f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        }, ret, 0.0f);
    }

    @Test
    public void testSetYUV2RGB() {
        mColorMatrix.setYUV2RGB();
        float[] ret = mColorMatrix.getArray();

        assertArrayEquals(new float[] {
                1.0f, 0.0f, 1.402f, 0.0f, 0.0f,
                1.0f, -0.34414f, -0.71414f, 0.0f, 0.0f,
                1.0f, 1.772f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        }, ret, 0.0f);
    }

    @Test
    public void testPostConcat() {
        mColorMatrix.postConcat(new ColorMatrix());

        float[] ret = mColorMatrix.getArray();

        for(int i = 0; i < 20; i++) {
            assertEquals((float) i, ret[i], 0.0f);
        }
    }

    @Test
    public void testPreConcat() {
        mColorMatrix.preConcat(new ColorMatrix());

        float[] ret = mColorMatrix.getArray();

        for(int i = 0; i < 20; i++) {
            assertEquals((float) i, ret[i], 0.0f);
        }
    }

    @Test
    public void testSetConcat() {
        float[] floatA = new float[] {
                0, 1, 2, 3, 4,
                5, 6, 7, 8, 9,
                9, 8, 7, 6, 5,
                4, 3, 2, 1, 0,
        };

        float[] floatB = new float[] {
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
        };

        mColorMatrix.setConcat(new ColorMatrix(floatA), new ColorMatrix(floatB));

        float[] ret = mColorMatrix.getArray();
        assertArrayEquals(new float[] {
                6.0f, 6.0f, 6.0f, 6.0f, 10.f,
                26.0f, 26.0f, 26.0f, 26.0f, 35.0f,
                30.0f, 30.0f, 30.0f, 30.0f, 35.0f,
                10.0f, 10.0f, 10.0f, 10.0f, 10.0f
        }, ret, 0.0f);
    }

    private void preCompare(float[] ret) {
        assertEquals(20, ret.length);

        for(int i = 0; i < 20; i++) {
            assertEquals((float) i, ret[i], 0.0f);
        }
    }

    @Test
    public void testEquals() {
        float[] floatA = new float[] {
                0, 1, 2, 3, 4,
                5, 6, 7, 8, 9,
                9, 8, 7, 6, 5,
                4, 3, 2, 1, 0,
        };

        float[] floatB = new float[] {
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
        };

        assertEquals(new ColorMatrix(floatA), new ColorMatrix(floatA));
        assertEquals(new ColorMatrix(floatB), new ColorMatrix(floatB));

        assertNotEquals(new ColorMatrix(floatA), new ColorMatrix(floatB));
        assertNotEquals(new ColorMatrix(floatB), new ColorMatrix(floatA));


        float[] floatC = new float[] {
                1, Float.NaN, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
                1, 1, 1, 1, 1,
        };

        assertNotEquals(new ColorMatrix(floatA), new ColorMatrix(floatC));
        assertNotEquals(new ColorMatrix(floatB), new ColorMatrix(floatC));
        assertNotEquals(new ColorMatrix(floatC), new ColorMatrix(floatC));

        ColorMatrix nanMatrix = new ColorMatrix(floatC);
        assertNotEquals("same instance, still not equals with NaN present", nanMatrix, nanMatrix);
    }
}
