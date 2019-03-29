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
package android.vm.cts.opcodes.add_int_lit16;

import junit.framework.TestCase;

public class AddIntLit16Test extends TestCase {
    /**
     * @title Arguments = 8 + 4
     */
    public void testN1() {
        AddIntLit16_1 t = new AddIntLit16_1();
        assertEquals(12, t.run());
    }

    /**
     * @title Arguments = 0 + 255
     */
    public void testN2() {
        AddIntLit16_2 t = new AddIntLit16_2();
        assertEquals(255, t.run());
    }

    /**
     * @title Arguments = 0 + (-32768)
     */
    public void testN3() {
        AddIntLit16_3 t = new AddIntLit16_3();
        assertEquals(-32768, t.run());
    }

    /**
     * @title Arguments = (-2147483647) + 0
     */
    public void testN4() {
        AddIntLit16_4 t = new AddIntLit16_4();
        assertEquals(-2147483647, t.run());
    }

    /**
     * @title Arguments = 0x7ffffffe + 2
     */
    public void testN5() {
        AddIntLit16_5 t = new AddIntLit16_5();
        assertEquals(-2147483648, t.run());
    }

    /**
     * @title Arguments = -1 + 1
     */
    public void testN6() {
        AddIntLit16_6 t = new AddIntLit16_6();
        assertEquals(0, t.run());
    }

    /**
     * @title Arguments = 0 + Short.MAX_VALUE
     */
    public void testB1() {
        AddIntLit16_7 t = new AddIntLit16_7();
        assertEquals(Short.MAX_VALUE, t.run());
    }

    /**
     * @title Arguments = Integer.MAX_VALUE + Short.MAX_VALUE
     */
    public void testB2() {
        AddIntLit16_8 t = new AddIntLit16_8();
        assertEquals(-2147450882, t.run());
    }

    /**
     * @title Arguments = Integer.MAX_VALUE + 1
     */
    public void testB3() {
        AddIntLit16_9 t = new AddIntLit16_9();
        assertEquals(Integer.MIN_VALUE, t.run());
    }

    /**
     * @title Arguments = Integer.MIN_VALUE + 1
     */
    public void testB4() {
        AddIntLit16_10 t = new AddIntLit16_10();
        assertEquals(-2147483647, t.run());
    }

    /**
     * @title Arguments = 0 + 0
     */
    public void testB5() {
        AddIntLit16_11 t = new AddIntLit16_11();
        assertEquals(0, t.run());
    }

    /**
     * @title Arguments = Short.MIN_VALUE + Short.MIN_VALUE
     */
    public void testB6() {
        AddIntLit16_12 t = new AddIntLit16_12();
        assertEquals(-65536, t.run());
    }
    
}
