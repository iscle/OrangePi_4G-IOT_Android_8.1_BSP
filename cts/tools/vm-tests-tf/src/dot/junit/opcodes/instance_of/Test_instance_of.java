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

package dot.junit.opcodes.instance_of;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.instance_of.d.T_instance_of_1;
import dot.junit.opcodes.instance_of.d.T_instance_of_2;
import dot.junit.opcodes.instance_of.d.T_instance_of_3;
import dot.junit.opcodes.instance_of.d.T_instance_of_7;

public class Test_instance_of extends DxTestCase {


    /**
     * @title (Object)String instanceof String
     */
    public void testN1() {
        T_instance_of_1 t = new T_instance_of_1();
        String s = "";
        assertTrue(t.run(s));
    }

    /**
     * @title null instanceof String
     */
    public void testN2() {
        T_instance_of_1 t = new T_instance_of_1();
        assertFalse(t.run(null));
    }

    /**
     * @title check assignment compatibility rules
     */
    public void testN4() {
        T_instance_of_2 t = new T_instance_of_2();
        assertTrue(t.run());
    }

    /**
     * @title T_instance_of_1 instanceof String
     */
    public void testE1() {
        T_instance_of_1 t = new T_instance_of_1();
        assertFalse(t.run(t));
    }

    /**
     * @title Attempt to access inaccessible class.
     */
    public void testE2() {
        //@uses dot.junit.opcodes.instance_of.TestStubs
        loadAndRun("dot.junit.opcodes.instance_of.d.T_instance_of_3", IllegalAccessError.class);
    }

    /**
     * @title Attempt to access undefined class.
     */
    public void testE3() {
        loadAndRun("dot.junit.opcodes.instance_of.d.T_instance_of_7", NoClassDefFoundError.class);
    }

    /**
     * @constraint A19
     * @title constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.instance_of.d.T_instance_of_4", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title type of argument - int
     */
    public void testVFE2() {
        load("dot.junit.opcodes.instance_of.d.T_instance_of_5", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title type of argument - long
     */
    public void testVFE3() {
        load("dot.junit.opcodes.instance_of.d.T_instance_of_8", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title number of registers
     */
    public void testVFE4() {
        load("dot.junit.opcodes.instance_of.d.T_instance_of_6", VerifyError.class);
    }


}
