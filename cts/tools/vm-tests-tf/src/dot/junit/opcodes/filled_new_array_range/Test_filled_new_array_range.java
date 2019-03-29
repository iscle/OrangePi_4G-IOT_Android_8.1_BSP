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

package dot.junit.opcodes.filled_new_array_range;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_1;
import dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_10;
import dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_11;
import dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_2;

public class Test_filled_new_array_range extends DxTestCase {
    /**
     * @title array of ints
     */
    public void testN1() {
        T_filled_new_array_range_1 t = new T_filled_new_array_range_1();
        int[] arr = t.run(1, 2, 3, 4, 5);
        assertNotNull(arr);
        assertEquals(5, arr.length);
        for(int i = 0; i < 5; i++)
            assertEquals(i + 1, arr[i]);
     }

    /**
     * @title array of objects
     */
    public void testN2() {
        T_filled_new_array_range_2 t = new T_filled_new_array_range_2();
        String s = "android";
        Object[] arr = t.run(t, s);
        assertNotNull(arr);
        assertEquals(2, arr.length);
        assertEquals(t, arr[0]);
        assertEquals(s, arr[1]);
    }

    /**
     * @constraint A18
     * @title invalid constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_3",
             VerifyError.class);
    }

    /**
     * @constraint A23 
     * @title  number of registers
     */
    public void testVFE2() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_4",
             VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title try to pass obj ref instead of int
     */
    public void testVFE3() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_5",
             VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title try to pass long instead of int
     */
    public void testVFE4() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_6",
             VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title try to create non-array type
     */
    public void testVFE5() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_7",
             VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title invalid arg count
     */
    public void testVFE6() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_8",
             VerifyError.class);
    }
    
    /**
     * @constraint n/a 
     * @title attempt to instantiate String[] and fill it with reference to assignment-incompatible class
     */
    public void testVFE7() {
        load("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_9",
             VerifyError.class);
    }
    
    /**
     * @constraint n/a
     * @title attempt to instantiate array of non-existent class
     */
    public void testVFE8() {
        loadAndRun("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_10",
                   NoClassDefFoundError.class);
    }

    /**
     * @constraint n/a
     * @title attempt to instantiate array of inaccessible class
     */
    public void testVFE9() {
        //@uses dot.junit.opcodes.filled_new_array_range.TestStubs
        loadAndRun("dot.junit.opcodes.filled_new_array_range.d.T_filled_new_array_range_11",
                   IllegalAccessError.class);
    }
}
