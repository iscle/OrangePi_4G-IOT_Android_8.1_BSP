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

package dot.junit.opcodes.sget_short;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.sget_short.d.T_sget_short_1;
import dot.junit.opcodes.sget_short.d.T_sget_short_11;
import dot.junit.opcodes.sget_short.d.T_sget_short_12;
import dot.junit.opcodes.sget_short.d.T_sget_short_13;
import dot.junit.opcodes.sget_short.d.T_sget_short_5;
import dot.junit.opcodes.sget_short.d.T_sget_short_6;
import dot.junit.opcodes.sget_short.d.T_sget_short_7;
import dot.junit.opcodes.sget_short.d.T_sget_short_8;
import dot.junit.opcodes.sget_short.d.T_sget_short_9;

public class Test_sget_short extends DxTestCase {

    /**
     * @title get short from static field
     */
    public void testN1() {
        T_sget_short_1 t = new T_sget_short_1();
        assertEquals(32000, t.run());
    }


    /**
     * @title access protected field from subclass
     */
    public void testN3() {
        //@uses dot.junit.opcodes.sget_short.d.T_sget_short_1
        //@uses dot.junit.opcodes.sget_short.d.T_sget_short_11
        T_sget_short_11 t = new T_sget_short_11();
        assertEquals(32000, t.run());
    }

    /**
     * @constraint A12
     * @title attempt to access non-static field
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_5",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @title initialization of referenced class throws exception
     */
    public void testE6() {
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_9",
                   ExceptionInInitializerError.class);
    }



    /**
     * @constraint A12
     * @title constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_4", VerifyError.class);
    }

    /**
     *
     * @constraint A23
     * @title number of registers
     */
    public void testVFE2() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_3", VerifyError.class);
    }

    /**
     *
     * @constraint B13
     * @title read short from long field - only field with same name but
     * different type exists
     */
    public void testVFE3() {
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_13", NoSuchFieldError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read inaccessible field.
     */
    public void testVFE4() {
        //@uses dot.junit.opcodes.sget_short.TestStubs
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_6", IllegalAccessError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read field of undefined class.
     */
    public void testVFE5() {
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_7", NoClassDefFoundError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read undefined field.
     */
    public void testVFE6() {
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_8", NoSuchFieldError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to read superclass' private field from subclass.
     */
    public void testVFE7() {
        //@uses dot.junit.opcodes.sget_short.d.T_sget_short_1
        loadAndRun("dot.junit.opcodes.sget_short.d.T_sget_short_12", IllegalAccessError.class);
    }

    /**
     * @constraint B1
     * @title sget_short shall not work for reference fields
     */
    public void testVFE8() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_14", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for char fields
     */
    public void testVFE9() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_15", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for int fields
     */
    public void testVFE10() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_16", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for byte fields
     */
    public void testVFE11() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_17", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for boolean fields
     */
    public void testVFE12() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_18", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for double fields
     */
    public void testVFE13() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_19", VerifyError.class);
    }

    /**
     *
     * @constraint B1
     * @title sget_short shall not work for long fields
     */
    public void testVFE14() {
        load("dot.junit.opcodes.sget_short.d.T_sget_short_20", VerifyError.class);
    }
}
