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
package android.vm.cts.opcodes.add_int_lit8;

import junit.framework.TestCase;

public class AddIntLit8 extends TestCase {
    /**
     * @title Arguments = 8 + 4
     */
    public void testN1() {
        AddIntLit8_1 t = new AddIntLit8_1();
        assertEquals(12, t.run());
    }

    /**
     * @title Arguments = Byte.MIN_VALUE + Byte.MAX_VALUE
     */
    public void testN2() {
        AddIntLit8_2 t = new AddIntLit8_2();
        assertEquals(-1, t.run());
    }

    /**
     * @title Arguments = 0 + (-128)
     */
    public void testN3() {
        AddIntLit8_3 t = new AddIntLit8_3();
        assertEquals(-128, t.run());
    }

    /**
     * @title Arguments = (-2147483647) + 0
     */
    public void testN4() {
        AddIntLit8_4 t = new AddIntLit8_4();
        assertEquals(-2147483647, t.run());
    }

    /**
     * @title Arguments = 0x7ffffffe + 2
     */
    public void testN5() {
        AddIntLit8_5 t = new AddIntLit8_5();
        assertEquals(-2147483648, t.run());
    }

    /**
     * @title Arguments = -1 + 1
     */
    public void testN6() {
        AddIntLit8_6 t = new AddIntLit8_6();
        assertEquals(0, t.run());
    }

    /**
     * @title Arguments = 0 + Byte.MAX_VALUE
     */
    public void testB1() {
        AddIntLit8_7 t = new AddIntLit8_7();
        assertEquals(Byte.MAX_VALUE, t.run());
    }

    /**
     * @title Arguments = Integer.MAX_VALUE + Byte.MAX_VALUE
     */
    public void testB2() {
        AddIntLit8_8 t = new AddIntLit8_8();
        assertEquals(-2147483522, t.run());
    }

    /**
     * @title Arguments = Integer.MAX_VALUE + 1
     */
    public void testB3() {
        AddIntLit8_9 t = new AddIntLit8_9();
        assertEquals(Integer.MIN_VALUE, t.run());
    }

    /**
     * @title Arguments = Integer.MIN_VALUE + 1
     */
    public void testB4() {
        AddIntLit8_10 t = new AddIntLit8_10();
        assertEquals(-2147483647, t.run());
    }

    /**
     * @title Arguments = 0 + 0
     */
    public void testB5() {
        AddIntLit8_11 t = new AddIntLit8_11();
        assertEquals(0, t.run());
    }

    /**
     * @title Arguments = Short.MIN_VALUE + Byte.MIN_VALUE
     */
    public void testB6() {
        AddIntLit8_12 t = new AddIntLit8_12();
        assertEquals(-32896, t.run());
    }

}
