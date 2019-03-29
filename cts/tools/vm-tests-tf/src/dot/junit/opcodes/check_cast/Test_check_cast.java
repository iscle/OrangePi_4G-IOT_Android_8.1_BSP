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

package dot.junit.opcodes.check_cast;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.check_cast.d.T_check_cast_1;
import dot.junit.opcodes.check_cast.d.T_check_cast_2;
import dot.junit.opcodes.check_cast.d.T_check_cast_3;
import dot.junit.opcodes.check_cast.d.T_check_cast_7;


public class Test_check_cast extends DxTestCase {
   
    
    /**
     * @title (String)(Object)String
     */
    public void testN1() {
        T_check_cast_1 t = new T_check_cast_1();
        String s = "";
        assertEquals(s, t.run(s));
    }

    /**
     * @title (String)(Object)null
     */
    public void testN2() {
        T_check_cast_1 t = new T_check_cast_1();
        assertNull(t.run(null));
    }

    /**
     * @title check assignment compatibility rules
     */
    public void testN4() {
        T_check_cast_2 t = new T_check_cast_2();
        assertEquals(5, t.run());
    }

    /**
     * @title expected ClassCastException
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.check_cast.d.T_check_cast_1", ClassCastException.class,
                   new Integer(1));
    }

    /**
     * @constraint A18 
     * @title  constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.check_cast.d.T_check_cast_4", VerifyError.class);
    }

    /**
     * 
     * @constraint B1 
     * @title  type of argument - int
     */
    public void testVFE2() {
        load("dot.junit.opcodes.check_cast.d.T_check_cast_5", VerifyError.class);
    }

    /**
     * 
     * @constraint B1 
     * @title  type of argument - long
     */
    public void testVFE3() {
        load("dot.junit.opcodes.check_cast.d.T_check_cast_8", VerifyError.class);
    }
    
    /**
     * 
     * @constraint B1 
     * @title  number of registers
     */
    public void testVFE4() {
        load("dot.junit.opcodes.check_cast.d.T_check_cast_6", VerifyError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to access inaccessible class, expect throws IllegalAccessError
     */
    public void testVFE5() {
        //@uses dot.junit.opcodes.check_cast.TestStubs
        loadAndRun("dot.junit.opcodes.check_cast.d.T_check_cast_3", IllegalAccessError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to access undefined class, expect throws NoClassDefFoundError on
     * first access
     */
    public void testVFE6() {
        loadAndRun("dot.junit.opcodes.check_cast.d.T_check_cast_7", NoClassDefFoundError.class);
    }
    
    /**
     * @constraint A18 
     * @title  constant pool type
     */    
    public void testVFE7() {
        load("dot.junit.opcodes.check_cast.d.T_check_cast_9", VerifyError.class);
    }

}
