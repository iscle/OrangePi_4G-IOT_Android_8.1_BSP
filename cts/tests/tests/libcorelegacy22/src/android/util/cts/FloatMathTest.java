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

import junit.framework.TestCase;
import android.util.FloatMath;

public class FloatMathTest extends TestCase {

    public void testSqrt() {
        assertEquals(5.0f, FloatMath.sqrt(25));
        assertEquals(7, FloatMath.sqrt(49), 0);
        assertEquals(10, FloatMath.sqrt(100), 0);
        assertEquals(0, FloatMath.sqrt(0), 0);
        assertEquals(1, FloatMath.sqrt(1), 0);
    }

    public void testFloor() {
        assertEquals(78, FloatMath.floor(78.89f), 0);
        assertEquals(-79, FloatMath.floor(-78.89f), 0);
        assertEquals(7.0f, FloatMath.floor(7.2f));
        assertEquals(-7.0f, FloatMath.floor(-6.3f));
    }

    public void testCeil() {
        assertEquals(79, FloatMath.ceil(78.89f), 0);
        assertEquals(-78, FloatMath.ceil(-78.89f), 0);
        assertEquals(8.0f, FloatMath.ceil(7.2f));
        assertEquals(-6.0f, FloatMath.ceil(-6.3f));
    }

    public void testCos() {
        assertEquals(1.0f, FloatMath.cos(0), 0);
        assertEquals(0.5403023058681398f, FloatMath.cos(1), 0);
        assertEquals(0.964966f, FloatMath.cos(50));
        assertEquals(0.69925081f, FloatMath.cos(150));
        assertEquals(0.964966f, FloatMath.cos(-50));
    }

    public void testSin() {
        assertEquals(0.0, FloatMath.sin(0), 0);
        assertEquals(0.8414709848078965f, FloatMath.sin(1), 0);
        assertEquals(-0.26237485f, FloatMath.sin(50));
        assertEquals(-0.71487643f, FloatMath.sin(150));
        assertEquals(0.26237485f, FloatMath.sin(-50));

    }
}

