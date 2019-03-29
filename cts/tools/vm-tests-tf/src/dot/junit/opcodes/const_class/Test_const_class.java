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

package dot.junit.opcodes.const_class;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.const_class.d.T_const_class_1;
import dot.junit.opcodes.const_class.d.T_const_class_2;
import dot.junit.opcodes.const_class.d.T_const_class_6;
import dot.junit.opcodes.const_class.d.T_const_class_7;

public class Test_const_class extends DxTestCase {
    /**
     * @title const-class v254, java/lang/String
     */
    public void testN1() {
        T_const_class_1 t = new T_const_class_1();
        Class c = t.run();
        assertEquals(0, c.getCanonicalName().compareTo("java.lang.String"));
    }
    
    /**
     * @title const-class v254, I
     */
    public void testN2() {
        T_const_class_2 t = new T_const_class_2();
        Class c = t.run();
        assertEquals(c.getCanonicalName(), "int");
    }

    /**
     * @title Class definition not found
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.const_class.d.T_const_class_6", NoClassDefFoundError.class);
    }
    
    /**
     * @title Class is not accessible
     */
    public void testE2() {
        //@uses dot.junit.opcodes.const_class.TestStubs
        loadAndRun("dot.junit.opcodes.const_class.d.T_const_class_7", IllegalAccessError.class);
    }
    
    /**
     * @constraint A23 
     * @title  number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.const_class.d.T_const_class_3", VerifyError.class);
    }
    
    /**
     * @constraint B11 
     * @title  When writing to a register that is one half of a register 
     * pair, but not touching the other half, the old register pair gets broken up, and the 
     * other register involved in it becomes undefined
     */
    public void testVFE2() {
        load("dot.junit.opcodes.const_class.d.T_const_class_4", VerifyError.class);
    }
    
    /**
     * @constraint A18 
     * @title  constant pool index
     */
    public void testVFE3() {
        load("dot.junit.opcodes.const_class.d.T_const_class_5", VerifyError.class);
    }
    
}
