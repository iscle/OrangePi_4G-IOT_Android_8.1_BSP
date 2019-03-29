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

package dot.junit.opcodes.packed_switch;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.packed_switch.d.T_packed_switch_1;
import dot.junit.opcodes.packed_switch.d.T_packed_switch_2;

public class Test_packed_switch extends DxTestCase {

    /**
     * @title try different values
     */
    public void testN1() {
        T_packed_switch_1 t = new T_packed_switch_1();
        assertEquals(2, t.run(-1));

        assertEquals(-1, t.run(4));
        assertEquals(20, t.run(2));
        assertEquals(-1, t.run(5));

        assertEquals(-1, t.run(6));
        assertEquals(20, t.run(3));
        assertEquals(-1, t.run(7));
    }

    /**
     * @title Argument = Integer.MAX_VALUE
     */
    public void testB1() {
        T_packed_switch_1 t = new T_packed_switch_1();
        assertEquals(-1, t.run(Integer.MAX_VALUE));
    }

    /**
     * @title Argument = Integer.MIN_VALUE
     */
    public void testB2() {
        T_packed_switch_1 t = new T_packed_switch_1();
        assertEquals(-1, t.run(Integer.MIN_VALUE));
    }
    
    /**
     * @title Argument = 0
     */
    public void testB3() {
        T_packed_switch_1 t = new T_packed_switch_1();
        assertEquals(-1, t.run(0));
    }

    /**
     * @constraint A23 
     * @title number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_3", VerifyError.class);
    }



    /**
     * @constraint B1 
     * @title type of argument - double
     */
    public void testVFE2() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_4", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title type of argument - long
     */
    public void testVFE3() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_5", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title type of argument - reference
     */
    public void testVFE4() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_6", VerifyError.class);
    }

    /**
     * @constraint A7 
     * @title branch target shall be inside the method
     */
    public void testVFE5() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_7", VerifyError.class);
    }

    /**
     * @constraint A7
     * @title branch target shall not be "inside" instruction
     */
    public void testVFE6() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_8", VerifyError.class);
    }


    /**
     * @constraint A7 
     * @title offset to table shall be inside method
     */
    public void testVFE7() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_9", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title Types of arguments - float, int. The verifier checks that ints
     * and floats are not used interchangeably.
     */
    public void testVFE8() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_2", VerifyError.class);
    }

    /**
     * @constraint A7
     * @title the size and the list of targets must be consistent. 
     */
    public void testVFE9() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_11", VerifyError.class);
    }

    
    /**
     * @constraint B22
     * @title packed-switch-data pseudo-instructions must not be reachable by control flow 
     */
    public void testVFE10() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_12", VerifyError.class);
    }
    
    /**
     * @constraint A7
     * @title table has wrong ident code
     */
    public void testVFE11() {
        load("dot.junit.opcodes.packed_switch.d.T_packed_switch_13", VerifyError.class);
    }
}
