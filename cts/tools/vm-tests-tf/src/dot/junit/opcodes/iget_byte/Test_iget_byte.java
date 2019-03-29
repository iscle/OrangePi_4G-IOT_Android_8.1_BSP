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

package dot.junit.opcodes.iget_byte;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_1;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_11;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_12;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_13;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_21;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_5;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_6;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_7;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_8;
import dot.junit.opcodes.iget_byte.d.T_iget_byte_9;

public class Test_iget_byte extends DxTestCase {

    /**
     * @title get byte from field
     */
    public void testN1() {
        T_iget_byte_1 t = new T_iget_byte_1();
        assertEquals(77, t.run());
    }

    /**
     * @title access protected field from subclass
     */
    public void testN3() {
        //@uses dot.junit.opcodes.iget_byte.d.T_iget_byte_1
        //@uses dot.junit.opcodes.iget_byte.d.T_iget_byte_11
        T_iget_byte_11 t = new T_iget_byte_11();
        assertEquals(77, t.run());
    }

    /**
     * @title expected NullPointerException
     */
    public void testE2() {
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_9", NullPointerException.class);
    }

    /**
     * @constraint A11
     * @title  constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_4", VerifyError.class);
    }

    /**
     * @constraint A23
     * @title number of registers
     */
    public void testVFE2() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_3", VerifyError.class);
    }

    /**
     * @constraint B13
     * @title read byte from long field - only field with same name but
     * different type exists
     */
    public void testVFE3() {
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_13", NoSuchFieldError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read inaccessible field.
     */
    public void testVFE4() {
        //@uses dot.junit.opcodes.iget_byte.TestStubs
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_6", IllegalAccessError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read field of undefined class.
     */
    public void testVFE5() {
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_7", NoClassDefFoundError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read undefined field.
     */
    public void testVFE6() {
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_8", NoSuchFieldError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read superclass' private field from subclass.
     */
    public void testVFE7() {
        //@uses dot.junit.opcodes.iget_byte.d.T_iget_byte_1
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_12", IllegalAccessError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for reference fields
     */
    public void testVFE8() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_14", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for short fields
     */
    public void testVFE9() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_15", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for int fields
     */
    public void testVFE10() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_16", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for char fields
     */
    public void testVFE11() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_17", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for boolean fields
     */
    public void testVFE12() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_18", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for double fields
     */
    public void testVFE13() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_19", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title iget_byte shall not work for long fields
     */
    public void testVFE14() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_20", VerifyError.class);
    }

    /**
     * @constraint B12
     * @title Attempt to read inaccessible protected field.
     */
    public void testVFE15() {
        //@uses dot.junit.opcodes.iget_byte.TestStubs
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_21", IllegalAccessError.class);
    }

    /**
     * @constraint A11
     * @title Attempt to read static  field.
     */
    public void testVFE16() {
        loadAndRun("dot.junit.opcodes.iget_byte.d.T_iget_byte_5",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @constraint B6
     * @title instance fields may only be accessed on already initialized instances.
     */
    public void testVFE30() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_30", VerifyError.class);
    }

    /**
     * @constraint N/A
     * @title instance fields may only be accessed on reference values.
     */
    public void testVFE31() {
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_31", VerifyError.class);
    }

    /**
     * @constraint N/A
     * @title Attempt to read inaccessible protected field on uninitialized reference.
     */
    public void testVFE35() {
        //@uses dot.junit.opcodes.iget_byte.TestStubs
        load("dot.junit.opcodes.iget_byte.d.T_iget_byte_35", VerifyError.class);
    }
}
