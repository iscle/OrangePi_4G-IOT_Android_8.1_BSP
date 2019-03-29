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

package dot.junit.opcodes.move_exception;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.move_exception.d.T_move_exception_1;
import dot.junit.opcodes.move_exception.d.T_move_exception_2;

public class Test_move_exception extends DxTestCase {
    
    /**
     * @title tests move-exception functionality
     */
    public void testN1() {
        loadAndRun("dot.junit.opcodes.move_exception.d.T_move_exception_1",
                   ArithmeticException.class);
    }
    
    /**
     * @title tests move-exception functionality
     */
    public void testN2() {
        T_move_exception_2 t = new T_move_exception_2();
        assertTrue(t.run());
    }
    
    /**
     * @constraint A23 
     * @title number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.move_exception.d.T_move_exception_3", VerifyError.class);
    }
    
    /**
     * @constraint B21 
     * @title  move-exception is not first instruction in an exception handler
     */
    public void testVFE3() {
        load("dot.junit.opcodes.move_exception.d.T_move_exception_5", VerifyError.class);
    }    
    
}
