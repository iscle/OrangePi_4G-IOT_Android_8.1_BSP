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

package dot.junit.opcodes.aput;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.aput.d.T_aput_1;
import dot.junit.opcodes.aput.d.T_aput_8;

public class Test_aput extends DxTestCase {

    /**
     * @title put int into array
     */
    public void testN1() {
        T_aput_1 t = new T_aput_1();
        int[] arr = new int[2];
        t.run(arr, 1, 100000000);
        assertEquals(100000000, arr[1]);
    }

    /**
     * @title put int into array
     */
    public void testN2() {
        T_aput_1 t = new T_aput_1();
        int[] arr = new int[2];
        t.run(arr, 0, 100000000);
        assertEquals(100000000, arr[0]);
    }

    /**
     * @title expected ArrayIndexOutOfBoundsException
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.aput.d.T_aput_1", ArrayIndexOutOfBoundsException.class,
                   new int[2], 2, 100000000);
    }

    /**
     * @title expected NullPointerException
     */
    public void testE2() {
        loadAndRun("dot.junit.opcodes.aput.d.T_aput_1", NullPointerException.class,
                   null, 2, 100000000);
    }

    /**
     * @title expected ArrayIndexOutOfBoundsException (negative index)
     */
    public void testE3() {
        loadAndRun("dot.junit.opcodes.aput.d.T_aput_1", ArrayIndexOutOfBoundsException.class,
                   new int[2], -1, 100000000);
    }

    

    /**
     * @constraint B1 
     * @title types of arguments - array, double, int
     */
    public void testVFE1() {
        load("dot.junit.opcodes.aput.d.T_aput_2", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - array, int, long
     */
    public void testVFE2() {
        load("dot.junit.opcodes.aput.d.T_aput_3", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - object, int, int
     */
    public void testVFE3() {
        load("dot.junit.opcodes.aput.d.T_aput_4", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - double[], int, int
     */
    public void testVFE4() {
        load("dot.junit.opcodes.aput.d.T_aput_5", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - long[], int, int
     */
    public void testVFE5() {
        load("dot.junit.opcodes.aput.d.T_aput_6", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - array, reference, int
     */
    public void testVFE6() {
        load("dot.junit.opcodes.aput.d.T_aput_7", VerifyError.class);
    }
    
    /**
     * @constraint A23 
     * @title number of registers
     */
    public void testVFE7() {
        load("dot.junit.opcodes.aput.d.T_aput_9", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title Type of index argument - float. The verifier checks that ints
     * and floats are not used interchangeably.
     */
    public void testVFE8() {
        load("dot.junit.opcodes.aput.d.T_aput_8", VerifyError.class);
    }
}
