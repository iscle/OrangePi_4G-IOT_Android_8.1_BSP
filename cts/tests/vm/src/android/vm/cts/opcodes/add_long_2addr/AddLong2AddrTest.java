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
package android.vm.cts.opcodes.add_long_2addr;

import junit.framework.TestCase;

public class AddLong2AddrTest extends TestCase {
    /**
     * @title Arguments = 12345678l, 87654321l
     */
    public void testN1() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(99999999l, t.run(12345678l, 87654321l));
    }

    /**
     * @title Arguments = 0l, 87654321l
     */
    public void testN2() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(87654321l, t.run(0l, 87654321l));
    }

    /**
     * @title Arguments = -12345678l, 0l
     */
    public void testN3() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-12345678l, t.run(-12345678l, 0l));
    }

    /**
     * @title Arguments = 0 + Long.MAX_VALUE
     */
    public void testB1() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(9223372036854775807L, t.run(0l, Long.MAX_VALUE));
    }

    /**
     * @title Arguments = 0 + Long.MIN_VALUE
     */
    public void testB2() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-9223372036854775808L, t.run(0l, Long.MIN_VALUE));
    }

    /**
     * @title Arguments = 0 + 0
     */
    public void testB3() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(0l, t.run(0l, 0l));
    }

    /**
     * @title Arguments = Long.MAX_VALUE + Long.MAX_VALUE
     */
    public void testB4() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-2, t.run(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    /**
     * @title Arguments = Long.MAX_VALUE + Long.MIN_VALUE
     */
    public void testB5() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-1l, t.run(Long.MAX_VALUE, Long.MIN_VALUE));
    }

    /**
     * @title Arguments = Long.MIN_VALUE + Long.MIN_VALUE
     */
    public void testB6() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(0l, t.run(Long.MIN_VALUE, Long.MIN_VALUE));
    }

    /**
     * @title Arguments = Long.MIN_VALUE + 1
     */
    public void testB7() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-9223372036854775807l, t.run(Long.MIN_VALUE, 1l));
    }

    /**
     * @title Arguments = Long.MAX_VALUE + 1
     */
    public void testB8() {
        AddLong2Addr_1 t = new AddLong2Addr_1();
        assertEquals(-9223372036854775808l, t.run(Long.MAX_VALUE, 1l));
    }

}
