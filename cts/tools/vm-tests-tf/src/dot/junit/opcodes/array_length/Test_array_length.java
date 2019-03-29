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

package dot.junit.opcodes.array_length;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.array_length.d.T_array_length_1;
import dot.junit.opcodes.array_length.d.T_array_length_4;


public class Test_array_length extends DxTestCase {
    /**
     * @title get length of array of references
     */
    public void testN1() {
        T_array_length_1 t = new T_array_length_1();
        String[] a = new String[5];
        assertEquals(5, t.run(a));
    }
    
    /**
     * @title get length of array of doubles
     */
    public void testN2() {
        T_array_length_4 t = new T_array_length_4();
        double[] a = new double[10];
        assertEquals(10, t.run(a));
    }

    /**
     * @title expected NullPointerException
     */
    public void testNPE1() {
        loadAndRun("dot.junit.opcodes.array_length.d.T_array_length_1", NullPointerException.class,
                   new Object[] {null});
    }

    /**
     * @constraint B1 
     * @title types of arguments - Object
     */
    public void testVFE1() {
        load("dot.junit.opcodes.array_length.d.T_array_length_2", VerifyError.class);
    }

    /**
     * @constraint B1 
     * @title types of arguments - int
     */
    public void testVFE2() {
        load("dot.junit.opcodes.array_length.d.T_array_length_3", VerifyError.class);
    }
    
    /**
     * @constraint A23 
     * @title number of registers
     */
    public void testVFE3() {
        load("dot.junit.opcodes.array_length.d.T_array_length_5", VerifyError.class);
    }
}
