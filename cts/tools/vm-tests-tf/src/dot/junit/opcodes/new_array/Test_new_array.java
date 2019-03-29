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

package dot.junit.opcodes.new_array;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.new_array.d.T_new_array_1;
import dot.junit.opcodes.new_array.d.T_new_array_10;
import dot.junit.opcodes.new_array.d.T_new_array_11;
import dot.junit.opcodes.new_array.d.T_new_array_2;
import dot.junit.opcodes.new_array.d.T_new_array_3;

public class Test_new_array extends DxTestCase {

    /**
     * @title Array of ints
     */
    public void testN1() {
        T_new_array_1 t = new T_new_array_1();
        int[] r = t.run(10);
        int l = r.length;
        assertEquals(10, l);

        // check default initialization
        for (int i = 0; i < l; i++) {
            assertEquals(0, r[i]);
        }

    }

    /**
     * @title Array of booleans
     */
    public void testN2() {
        T_new_array_2 t = new T_new_array_2();
        boolean[] r = t.run(10);
        int l = r.length;
        assertEquals(10, l);

        // check default initialization
        for (int i = 0; i < l; i++) {
            assertFalse(r[i]);
        }
    }

    /**
     * @title Array of Objects
     */
    public void testN3() {
        T_new_array_3 t = new T_new_array_3();
        Object[] r = t.run(10);
        int l = r.length;
        assertEquals(10, l);

        // check default initialization
        for (int i = 0; i < l; i++) {
            assertNull(r[i]);
        }
    }

    /**
     * @title Array size = 0
     */
    public void testB1() {
        T_new_array_1 t = new T_new_array_1();
        int[] r = t.run(0);
        assertNotNull(r);
        assertEquals(0, r.length);
    }

    /**
     * @title expected NegativeArraySizeException
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.new_array.d.T_new_array_2", NegativeArraySizeException.class,
                   -1);
    }


    /**
     * @constraint B1
     * @title  number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.new_array.d.T_new_array_4", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title  size argument - long
     */
    public void testVFE2() {
        load("dot.junit.opcodes.new_array.d.T_new_array_5", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title  size argument - reference
     */
    public void testVFE3() {
        load("dot.junit.opcodes.new_array.d.T_new_array_9", VerifyError.class);
    }

    /**
     *
     * @constraint A19
     * @title  constant pool index
     */
    public void testVFE4() {
        load("dot.junit.opcodes.new_array.d.T_new_array_6", VerifyError.class);
    }

    /**
     *
     * @constraint A22
     * @title  attempt to create object
     */
    public void testVFE5() {
        load("dot.junit.opcodes.new_array.d.T_new_array_7", VerifyError.class);
    }

    /**
     *
     * @constraint A20
     * @title  array of more than 255 dimensions
     */
    public void testVFE6() {
        load("dot.junit.opcodes.new_array.d.T_new_array_8", ClassNotFoundException.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to instantiate array of non-existent class.
     */
    public void testVFE7() {
        loadAndRun("dot.junit.opcodes.new_array.d.T_new_array_11", NoClassDefFoundError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to instantiate array of inaccessible class.
     */
    public void testVFE8() {
        //@uses dot.junit.opcodes.new_array.TestStubs
        loadAndRun("dot.junit.opcodes.new_array.d.T_new_array_10", IllegalAccessError.class);
    }

}
