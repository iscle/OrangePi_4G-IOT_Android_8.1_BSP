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

package com.android.server.wifi.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Random;

/**
 * Unit tests for {@link com.android.server.wifi.util.Matrix}.
 */
public class MatrixTest {
    /**
     * Test that both forms of constructor work
     */
    @Test
    public void testConstructors() throws Exception {
        assertEquals(new Matrix(3, 2), new Matrix(2, new double[]{0, 0, 0, 0, 0, 0}));
    }

    /**
     * Test some degenerate cases
     */
    @Test
    public void testDegenerate() throws Exception {
        Matrix m1 = new Matrix(0, 20);
        Matrix m2 = new Matrix(20, 0);
        assertEquals(m1, m2.transpose());
    }

    /**
     * Test addition
     */
    @Test
    public void testAddition() throws Exception {
        Matrix m1 = new Matrix(2, new double[]{1, 2, 3, 4});
        Matrix m2 = new Matrix(2, new double[]{10, 20, 30, 40});
        Matrix m3 = new Matrix(2, new double[]{11, 22, 33, 44});
        assertEquals(m3, m1.plus(m2));
    }

    /**
     * Test subtraction.
     */
    @Test
    public void testSubtraction() throws Exception {
        Matrix m1 = new Matrix(2, new double[]{1, 2, 3, 4});
        Matrix m2 = new Matrix(2, new double[]{10, 20, 30, 40});
        Matrix m3 = new Matrix(2, new double[]{11, 22, 33, 44});
        assertEquals(m1, m3.minus(m2));
    }

    /**
     * Test multiplication.
     */
    @Test
    public void testMultiplication() throws Exception {
        Matrix m1 = new Matrix(2, new double[]{1, 2, 3, 4});
        Matrix m2 = new Matrix(2, new double[]{-3, 3, 7, 1});
        Matrix m3 = new Matrix(2, new double[]{11, 5, 19, 13});
        assertEquals(m3, m1.dot(m2));
    }

    /**
     * Test that matrix inverse works (non-singular case).
     */
    @Test
    public void testInverse() throws Exception {
        Matrix i3 = new Matrix(3, new double[]{1, 0, 0, 0, 1, 0, 0, 0, 1});
        Matrix f = new Matrix(3, new double[]{100, 100, 100, 100, 100, 100, 100, 100, 100});
        Matrix m1 = new Matrix(3, new double[]{10, 1, -1, 2, 14, -1, 0, 2, 20});
        Matrix m2 = m1.inverse();
        Matrix m12 = m1.dot(m2);
        Matrix m21 = m2.dot(m1);
        // Add f here to wash out the roundoff errors
        assertEquals(i3.plus(f), m12.plus(f));
        assertEquals(i3.plus(f), m21.plus(f));
    }

    /**
     * Test that attempting to invert a singular matrix throws an exception.
     * @throws Exception
     */
    @Test(expected = ArithmeticException.class)
    public void testSingularity() throws Exception {
        Matrix m1 = new Matrix(3, new double[]{10, 1, -1, 0, 0, 0, 0, 0, 0});
        Matrix m2 = m1.inverse();
    }

    /**
     * Test that a copy is equal to the original, and that hash codes match,
     * and that string versions match.
     */
    @Test
    public void testCopy() throws Exception {
        Random random = new Random();
        Matrix m1 = new Matrix(3, 4);
        for (int i = 0; i < m1.mem.length; i++) {
            m1.mem[i] = random.nextDouble();
        }
        Matrix m2 = new Matrix(m1);
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertEquals(m1.toString(), m2.toString());
    }
}
