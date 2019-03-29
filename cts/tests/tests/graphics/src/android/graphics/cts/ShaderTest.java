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

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShaderTest {
    @Test
    public void testConstructor() {
        new Shader();
    }

    @Test
    public void testAccessLocalMatrix() {
        int width = 80;
        int height = 120;
        int[] color = new int[width * height];
        Bitmap bitmap = Bitmap.createBitmap(color, width, height, Bitmap.Config.RGB_565);

        Shader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        Matrix m = new Matrix();

        shader.setLocalMatrix(m);
        assertFalse(shader.getLocalMatrix(m));

        shader.setLocalMatrix(null);
        assertFalse(shader.getLocalMatrix(m));
    }

    @Test
    public void testMutateBaseObject() {
        Shader shader = new Shader();
        shader.setLocalMatrix(null);
    }

    @Test
    public void testGetSetLocalMatrix() {
        Matrix skew10x20 = new Matrix();
        skew10x20.setSkew(10, 20);

        Matrix scale2x3 = new Matrix();
        scale2x3.setScale(2, 3);

        // setup shader
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.RGB_565);
        bitmap.eraseColor(Color.BLUE);
        Shader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        // get null
        shader.setLocalMatrix(null);
        Matrix paramMatrix = new Matrix(skew10x20);
        assertFalse("shader should have no matrix set", shader.getLocalMatrix(paramMatrix));
        assertEquals("matrix param not modified when no matrix set", skew10x20, paramMatrix);

        // get nonnull
        shader.setLocalMatrix(scale2x3);
        assertTrue("shader should have matrix set", shader.getLocalMatrix(paramMatrix));
        assertEquals("param matrix should be updated", scale2x3, paramMatrix);
    }

    @Test(expected = NullPointerException.class)
    public void testGetWithNullParam() {
        Shader shader = new Shader();
        Matrix matrix = new Matrix();
        matrix.setScale(10, 10);
        shader.setLocalMatrix(matrix);

        shader.getLocalMatrix(null);
    }
}
