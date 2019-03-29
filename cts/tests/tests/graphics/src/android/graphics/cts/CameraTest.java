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

import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CameraTest {
    private Camera mCamera;

    @Before
    public void setup() {
        mCamera = new Camera();
    }

    @Test
    public void testCamera() {
        new Camera();
    }

    @Test
    public void testRestore() {
        // we cannot get the state changed because it was a native method
        mCamera.save();
        mCamera.restore();
    }

    @Test
    public void testMatrixPreCompare() {
        Matrix m = new Matrix();
        mCamera.getMatrix(m);
        float[] f = new float[9];
        m.getValues(f);
        assertArrayEquals(new float[] {
            1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testTranslate() {
        Matrix m1 = new Matrix();

        mCamera.translate(10.0f, 28.0f, 2008.0f);
        Matrix m2 = new Matrix();
        mCamera.getMatrix(m2);
        assertFalse(m1.equals(m2));

        float[] f = new float[9];
        m2.getValues(f);
        assertArrayEquals(new float[] {
                0.22291021f, 0.0f, 2.2291021f, 0.0f, 0.22291021f, -6.241486f, 0.0f, 0.0f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testRotateX() {
        Matrix m1 = new Matrix();

        mCamera.rotateX(90.0f);
        Matrix m2 = new Matrix();
        mCamera.getMatrix(m2);
        assertFalse(m1.equals(m2));

        float[] f = new float[9];
        m2.getValues(f);
        assertArrayEquals(new float[] {
                1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.0017361111f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testRotateY() {
        Matrix m1 = new Matrix();

        mCamera.rotateY(90.0f);
        Matrix m2 = new Matrix();
        mCamera.getMatrix(m2);
        assertFalse(m1.equals(m2));

        float[] f = new float[9];
        m2.getValues(f);
        assertArrayEquals(new float[] {
                0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0017361111f, 0.0f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testRotateZ() {
        Matrix m1 = new Matrix();

        mCamera.rotateZ(90.0f);
        Matrix m2 = new Matrix();
        mCamera.getMatrix(m2);
        assertFalse(m1.equals(m2));

        float[] f = new float[9];
        m2.getValues(f);
        assertArrayEquals(new float[] {
                0.0f, 1.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testRotate() {
        Matrix m1 = new Matrix();

        mCamera.rotate(15.0f, 30.0f, 45.0f);
        Matrix m2 = new Matrix();
        mCamera.getMatrix(m2);
        assertFalse(m1.equals(m2));

        float[] f = new float[9];
        m2.getValues(f);
        assertArrayEquals(new float[] {
                0.6123724f, 0.6123724f, 0.0f, -0.5915063f, 0.774519f, 0.0f, 0.0009106233f,
                0.00027516257f, 1.0f
        }, f, 0.0f);
    }

    @Test
    public void testLocationAccessors() {
        mCamera.setLocation(10.0f, 20.0f, 30.0f);
        assertEquals(10.0f, mCamera.getLocationX(), 0.0f);
        assertEquals(20.0f, mCamera.getLocationY(), 0.0f);
        assertEquals(30.0f, mCamera.getLocationZ(), 0.0f);
    }

    @Test
    public void testApplyToCanvas() {
        Canvas c1 = new Canvas();
        mCamera.applyToCanvas(c1);

        Canvas c2 = new Canvas();
        Matrix m = new Matrix();
        mCamera.getMatrix(m);
        c2.concat(m);

        assertEquals(c1.getMatrix(), c2.getMatrix());
    }

    @Test
    public void testDotWithNormal() {
        assertEquals(0.0792f, mCamera.dotWithNormal(0.1f, 0.28f, 0.2008f), 0.0f);
    }
}
