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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Camera;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.RectF;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MatrixTest {
    private Matrix mMatrix;
    private float[] mValues;

    @Before
    public void setup() {
        mMatrix = new Matrix();
        mValues = new float[9];
    }

    @Test
    public void testConstructor() {
        assertTrue(new Matrix().isIdentity());
        assertTrue(new Matrix(mMatrix).isIdentity());
    }

    @Test
    public void testIsIdentity() {
        assertTrue(mMatrix.isIdentity());
        mMatrix.setScale(0f, 0f);
        assertFalse(mMatrix.isIdentity());
    }

    @Test
    public void testRectStaysRect() {
        assertTrue(mMatrix.rectStaysRect());
        mMatrix.postRotate(80);
        assertFalse(mMatrix.rectStaysRect());
    }

    @Test
    public void testIsAffine() {
        assertTrue(mMatrix.isAffine());

        // translate/scale/rotateZ don't affect whether matrix is affine
        mMatrix.postTranslate(50, 50);
        mMatrix.postScale(20, 4);
        mMatrix.postRotate(80);
        assertTrue(mMatrix.isAffine());

        Camera camera = new Camera();
        camera.setLocation(0, 0, 100);
        camera.rotateX(20);
        camera.getMatrix(mMatrix);
        assertFalse(mMatrix.isAffine());
    }

    @Test
    public void testSet() {
        mValues[0] = 1000;
        mMatrix.getValues(mValues);
        Matrix matrix = new Matrix();
        matrix.set(mMatrix);
        mValues = new float[9];
        mValues[0] = 2000;
        matrix.getValues(mValues);
        assertEquals(1f, mValues[0], 0f);
    }

    @Test
    public void testEquals() {
        mMatrix.setScale(1f, 2f);
        Matrix matrix = new Matrix();
        matrix.set(mMatrix);
        assertFalse(mMatrix.equals(null));
        assertFalse(mMatrix.equals(new String()));
        assertTrue(mMatrix.equals(matrix));
    }

    @Test
    public void testReset() {
        mMatrix.setScale(1f, 2f, 3f, 4f);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, -4.0f, 0.0f, 0.0f, 1.0f });
        mMatrix.reset();
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetScale() {
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix.setScale(1f, 2f);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetScale2() {
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });

        mMatrix.setScale(1f, 2f, 3f, 4f);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, -4.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetRotate() {
        mMatrix.setRotate(1f);
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0f, 0.017452406f, 0.9998477f, 0.0f, 0.0f, 0.0f, 1.0f
        });
    }

    @Test
    public void testSetRotate2() {
        mMatrix.setRotate(1f, 2f, 3f);
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0526618f, 0.017452406f, 0.9998477f, -0.034447942f, 0.0f,
                0.0f, 1.0f
        });
    }

    @Test
    public void testSetSinCos() {
        mMatrix.setSinCos(1f, 2f);
        verifyMatrix(new float[] { 2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetSinCos2() {
        mMatrix.setSinCos(1f, 2f, 3f, 4f);
        verifyMatrix(new float[] { 2.0f, -1.0f, 1.0f, 1.0f, 2.0f, -7.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetSkew() {
        mMatrix.setSkew(1f, 2f);
        verifyMatrix(new float[] { 1.0f, 1.0f, 0.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetSkew2() {
        mMatrix.setSkew(1f, 2f, 3f, 4f);
        verifyMatrix(new float[] { 1.0f, 1.0f, -4.0f, 2.0f, 1.0f, -6.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testSetConcat() {
        Matrix a = new Matrix();
        Matrix b = new Matrix();
        mMatrix.setConcat(a, b);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix = new Matrix();
        mMatrix.setConcat(mMatrix, b);
        mMatrix.setConcat(a, b);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix = new Matrix();
        mValues = new float[9];
        mMatrix.setConcat(a, mMatrix);
        mMatrix.getValues(mValues);
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreTranslate() {
        assertTrue(mMatrix.preTranslate(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 2.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreScale() {
        assertTrue(mMatrix.preScale(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreScale2() {
        assertTrue(mMatrix.preScale(1f, 2f, 3f, 4f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, -4.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreRotate() {
        assertTrue(mMatrix.preRotate(1f));
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0f, 0.017452406f, 0.9998477f, 0.0f, 0.0f, 0.0f, 1.0f
        });
    }

    @Test
    public void testPreRotate2() {
        assertTrue(mMatrix.preRotate(1f, 2f, 3f));
        float[] values = new float[9];
        mMatrix.getValues(values);
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0526618f, 0.017452406f, 0.9998477f, -0.034447942f, 0.0f,
                0.0f, 1.0f
        });
    }

    @Test
    public void testPreSkew() {
        assertTrue(mMatrix.preSkew(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 1.0f, 0.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreSkew2() {
        assertTrue(mMatrix.preSkew(1f, 2f, 3f, 4f));
        verifyMatrix(new float[] { 1.0f, 1.0f, -4.0f, 2.0f, 1.0f, -6.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPreConcat() {
        float[] values = new float[9];
        values[0] = 1000;
        Matrix matrix = new Matrix();
        matrix.setValues(values);
        assertTrue(mMatrix.preConcat(matrix));
        verifyMatrix(new float[] { 1000.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f });
    }

    @Test
    public void testPostTranslate() {
        assertTrue(mMatrix.postTranslate(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 1.0f, 0.0f, 1.0f, 2.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPostScale() {
        assertTrue(mMatrix.postScale(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPostScale2() {
        assertTrue(mMatrix.postScale(1f, 2f, 3f, 4f));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 2.0f, -4.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPostRotate() {
        assertTrue(mMatrix.postRotate(1f));
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0f, 0.017452406f, 0.9998477f, 0.0f, 0.0f, 0.0f, 1.0f
        });
    }

    @Test
    public void testPostRotate2() {
        assertTrue(mMatrix.postRotate(1f, 2f, 3f));
        verifyMatrix(new float[] {
            0.9998477f, -0.017452406f, 0.0526618f, 0.017452406f, 0.9998477f, -0.034447942f, 0.0f,
                0.0f, 1.0f
        });
    }

    @Test
    public void testPostSkew() {
        assertTrue(mMatrix.postSkew(1f, 2f));
        verifyMatrix(new float[] { 1.0f, 1.0f, 0.0f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPostSkew2() {
        assertTrue(mMatrix.postSkew(1f, 2f, 3f, 4f));
        verifyMatrix(new float[] { 1.0f, 1.0f, -4.0f, 2.0f, 1.0f, -6.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test
    public void testPostConcat() {
        Matrix matrix = new Matrix();
        float[] values = new float[9];
        values[0] = 1000;
        matrix.setValues(values);
        assertTrue(mMatrix.postConcat(matrix));

        verifyMatrix(new float[] { 1000.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f });
    }

    @Test
    public void testSetRectToRect() {
        RectF r1 = new RectF();
        r1.set(1f, 2f, 3f, 3f);
        RectF r2 = new RectF();
        r1.set(10f, 20f, 30f, 30f);
        assertTrue(mMatrix.setRectToRect(r1, r2, ScaleToFit.CENTER));
        verifyMatrix(new float[] { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix.setRectToRect(r1, r2, ScaleToFit.END);

        verifyMatrix(new float[] { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix.setRectToRect(r1, r2, ScaleToFit.FILL);
        verifyMatrix(new float[] { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        mMatrix.setRectToRect(r1, r2, ScaleToFit.START);
        verifyMatrix(new float[] { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f });

        assertFalse(mMatrix.setRectToRect(r2, r1, ScaleToFit.CENTER));

        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        assertFalse(mMatrix.setRectToRect(r2, r1, ScaleToFit.FILL));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        assertFalse(mMatrix.setRectToRect(r2, r1, ScaleToFit.START));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
        assertFalse(mMatrix.setRectToRect(r2, r1, ScaleToFit.END));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test(expected=Exception.class)
    public void testSetRectToRectNull() {
        mMatrix.setRectToRect(null, null, ScaleToFit.CENTER);
    }

    @Test
    public void testInvert() {
        Matrix matrix = new Matrix();
        float[] values = new float[9];
        values[0] = 1000f;
        matrix.setValues(values);
        assertTrue(mMatrix.invert(matrix));
        verifyMatrix(new float[] { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f });
    }

    @Test(expected=Exception.class)
    public void testInvertNull() {
        mMatrix.invert(null);
    }

    @Test
    public void testSetPolyToPoly() {
        float[] src = new float[9];
        src[0] = 100f;
        float[] dst = new float[9];
        dst[0] = 200f;
        dst[1] = 300f;
        assertTrue(mMatrix.setPolyToPoly(src, 0, dst, 0, 1));
        verifyMatrix(new float[] { 1.0f, 0.0f, 100.0f, 0.0f, 1.0f, 300.0f, 0.0f, 0.0f, 1.0f });
        try {
            mMatrix.setPolyToPoly(src, 0, dst, 0, 5);
            fail("should throw exception");
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testMapPoints() {
        float[] value = new float[9];
        value[0] = 100f;
        mMatrix.mapPoints(value);
        assertEquals(value[0], 100f, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapPointsNull() {
        mMatrix.mapPoints(null);
    }

    @Test
    public void testMapPoints2() {
        float[] dst = new float[9];
        dst[0] = 100f;
        float[] src = new float[9];
        src[0] = 200f;
        mMatrix.mapPoints(dst, src);
        assertEquals(dst[0], 200f, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapPointsArraysMismatch() {
        mMatrix.mapPoints(new float[8], new float[9]);
    }

    @Test
    public void testMapPointsWithIndices() {
        float[] dst = new float[9];
        dst[0] = 100f;
        float[] src = new float[9];
        src[0] = 200f;
        mMatrix.mapPoints(dst, 0, src, 0, 9 >> 1);
        assertEquals(dst[0], 200f, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapPointsWithIndicesNull() {
        mMatrix.mapPoints(null, 0, new float[9], 0, 1);
    }

    @Test
    public void testMapVectors() {
        float[] values = new float[9];
        values[0] = 100f;
        mMatrix.mapVectors(values);
        assertEquals(values[0], 100f, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapVectorsNull() {
        mMatrix.mapVectors(null);
    }

    @Test
    public void testMapVectorsDstSrc() {
        float[] src = new float[9];
        src[0] = 100f;
        float[] dst = new float[9];
        dst[0] = 200f;
        mMatrix.mapVectors(dst, src);
        assertEquals(dst[0], 100f, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapVectorsDstSrcMismatch() {
        mMatrix.mapVectors(new float[9], new float[8]);
    }

    @Test
    public void testMapVectorsDstSrcWithIndices() {
        float[] src = new float[9];
        src[0] = 100f;
        float[] dst = new float[9];
        dst[0] = 200f;
        mMatrix.mapVectors(dst, 0, src, 0, 1);
        assertEquals(dst[0], 100f, 0f);
        try {
            mMatrix.mapVectors(dst, 0, src, 0, 10);
            fail("should throw exception");
        } catch (Exception ignored) {
        }
    }

    @Test
    public void testMapRadius() {
        assertEquals(mMatrix.mapRadius(100f), 100f, 0f);
        assertEquals(mMatrix.mapRadius(Float.MAX_VALUE),
                Float.POSITIVE_INFINITY, 0f);
        assertEquals(mMatrix.mapRadius(Float.MIN_VALUE), 0f, 0f);
    }

    @Test
    public void testMapRect() {
        RectF r = new RectF();
        r.set(1f, 2f, 3f, 4f);
        assertTrue(mMatrix.mapRect(r));
        assertEquals(1f, r.left, 0f);
        assertEquals(2f, r.top, 0f);
        assertEquals(3f, r.right, 0f);
        assertEquals(4f, r.bottom, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapRectNull() {
        mMatrix.mapRect(null);
    }

    @Test
    public void testMapRectDstSrc() {
        RectF dst = new RectF();
        dst.set(100f, 100f, 200f, 200f);
        RectF src = new RectF();
        dst.set(10f, 10f, 20f, 20f);
        assertTrue(mMatrix.mapRect(dst, src));
        assertEquals(0f, dst.left, 0f);
        assertEquals(0f, dst.top, 0f);
        assertEquals(0f, dst.right, 0f);
        assertEquals(0f, dst.bottom, 0f);

        assertEquals(0f, src.left, 0f);
        assertEquals(0f, src.top, 0f);
        assertEquals(0f, src.right, 0f);
        assertEquals(0f, src.bottom, 0f);
    }

    @Test(expected=Exception.class)
    public void testMapRectDstSrcNull() {
        mMatrix.mapRect(null, null);
    }

    @Test
    public void testAccessValues() {
        Matrix matrix = new Matrix();
        mMatrix.invert(matrix);
        float[] values = new float[9];
        values[0] = 9f;
        values[1] = 100f;
        mMatrix.setValues(values);
        values = new float[9];
        mMatrix.getValues(values);
        assertArrayEquals(new float[] {
                9.0f, 100.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f
        }, values, 0.0f);
    }

    @Test
    public void testToString() {
        assertNotNull(mMatrix.toString());
    }

    @Test
    public void testToShortString() {
        String expect = "[1.0, 0.0, 0.0][0.0, 1.0, 0.0][0.0, 0.0, 1.0]";
        assertEquals(expect, mMatrix.toShortString());
    }

    @Test
    public void testSetTranslate() {
        mMatrix.setTranslate(2f, 3f);
        verifyMatrix(new float[] { 1.0f, 0.0f, 2.0f, 0.0f, 1.0f, 3.0f, 0.0f, 0.0f, 1.0f });
    }

    private void verifyMatrix(float[] expected) {
        if ((expected == null) || (expected.length != 9)) {
            fail("Expected does not have 9 elements");
        }
        final float[] actualValues = new float[9];
        mMatrix.getValues(actualValues);
        assertArrayEquals(expected, actualValues, 0.0f);
    }
}
