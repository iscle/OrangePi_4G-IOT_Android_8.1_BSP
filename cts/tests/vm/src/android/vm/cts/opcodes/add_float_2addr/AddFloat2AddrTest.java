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
package android.vm.cts.opcodes.add_float_2addr;

import junit.framework.TestCase;

public class AddFloat2AddrTest extends TestCase {
    /**
     * @title Arguments = 2.7f, 3.14f
     */
    public void testN1() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(5.84f, t.run(2.7f, 3.14f));
    }

    /**
     * @title Arguments = 0, -3.14f
     */
    public void testN2() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(-3.14f, t.run(0, -3.14f));
    }

    /**
     * @title Arguments = -3.14f, -2.7f
     */
    public void testN3() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(-5.84f, t.run(-3.14f, -2.7f));
    }

    /**
     * @title Arguments = Float.MAX_VALUE, Float.NaN
     */
    public void testB1() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(Float.POSITIVE_INFINITY, t.run(3.3028235E38f, 0.11E38f));
    }

    /**
     * @title Arguments = Float.POSITIVE_INFINITY,
     * Float.NEGATIVE_INFINITY
     */
    public void testB2() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertTrue(Float.isNaN(t.run(Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY)));
    }

    /**
     * @title Arguments = Float.POSITIVE_INFINITY,
     * Float.POSITIVE_INFINITY
     */
    public void testB3() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(Float.POSITIVE_INFINITY, t.run(Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY));
    }

    /**
     * @title Arguments = Float.POSITIVE_INFINITY, -2.7f
     */
    public void testB4() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(Float.POSITIVE_INFINITY, t.run(Float.POSITIVE_INFINITY,
                -2.7f));
    }

    /**
     * @title Arguments = +0, -0f
     */
    public void testB5() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(+0f, t.run(+0f, -0f));
    }

    /**
     * @title Arguments = -0f, -0f
     */
    public void testB6() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(-0f, t.run(-0f, -0f));
    }

    /**
     * @title Arguments = -2.7f, 2.7f
     */
    public void testB7() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(+0f, t.run(-2.7f, 2.7f));
    }

    /**
     * @title Arguments = Float.MAX_VALUE, Float.MAX_VALUE
     */
    public void testB8() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(Float.POSITIVE_INFINITY, t.run(Float.MAX_VALUE,
                Float.MAX_VALUE));
    }

    /**
     * @title Arguments = Float.MIN_VALUE, -1.4E-45f
     */
    public void testB9() {
        AddFloat2Addr_1 t = new AddFloat2Addr_1();
        assertEquals(0f, t.run(Float.MIN_VALUE, -1.4E-45f));
    }

    
}
