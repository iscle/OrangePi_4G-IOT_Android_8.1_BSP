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

package dot.junit.opcodes.invoke_interface;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_1;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_11;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_12;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_13;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_14;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_16;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_18;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_20;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_21;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_23;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_24;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_25;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_3;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_4;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_5;
import dot.junit.opcodes.invoke_interface.d.T_invoke_interface_7;

public class Test_invoke_interface extends DxTestCase {

    /**
     * @title invoke interface method
     */
    public void testN1() {
        T_invoke_interface_1 t = new T_invoke_interface_1();
        assertEquals(0, t.run("aa", "aa"));
        assertEquals(-1, t.run("aa", "bb"));
        assertEquals(1, t.run("bb", "aa"));
    }

    /**
     * @title Check that new frame is created by invoke_interface and
     * arguments are passed to method
     */
    public void testN2() {
        //@uses dot.junit.opcodes.invoke_interface.d.T_invoke_interface_14
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        T_invoke_interface_14 t = new T_invoke_interface_14();
        ITestImpl impl = new ITestImpl();
        assertEquals(1, t.run(impl));
    }

    /**
     * @title invoke default interface method
     */
    public void testN3() {
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_25", null);
    }

    /**
     * @title objref is null
     */
    public void testE3() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_3",
                   NullPointerException.class);
    }

    /**
     * @title object doesn't implement interface
     */
    public void testE4() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_11",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @title dvmInterpFindInterfaceMethod failures were putting NULL Method*s
     * in the interface cache, leading to a null pointer deference the second
     * time you made the same bad call, with no exception thrown.
     * See http://code.google.com/p/android/issues/detail?id=29358 for details.
     */
    public void testE4_2() {
        //@uses dot.junit.opcodes.invoke_interface.d.T_invoke_interface_11
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_11",
                   IncompatibleClassChangeError.class);
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_11",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @title Native method can't be linked
     */
    public void testE5() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_12",
                   UnsatisfiedLinkError.class, new ITestImpl());
    }

    /**
     * @title Attempt to invoke abstract method
     */
    public void testE6() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        //@uses dot.junit.opcodes.invoke_interface.ITestImplAbstract
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_13",
                   AbstractMethodError.class);
    }

    /**
     * @title Attempt to invoke conflict method
     */
    public void testE7() {
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_23",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @title Attempt to invoke abstract method
     */
    public void testE8() {
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_24",
                   AbstractMethodError.class);
    }

    /**
     * @constraint A16
     * @title invalid constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_2", VerifyError.class);
    }

    /**
     * @constraint A16
     * @title The referenced method_id must belong to an interface (not a class).
     */
    public void testVFE2() {
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_4",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @constraint B1
     * @title number of arguments
     */
    public void testVFE5() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_5", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title int is passed instead of objref
     */
    public void testVFE6() {
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_10", VerifyError.class);
    }

    /**
     * @constraint B9
     * @title number of arguments passed to method
     */
    public void testVFE9() {
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_9", VerifyError.class);
    }

    /**
     * @constraint A15
     * @title invoke-interface may not be used to call <init>.
     */
    public void testVFE10() {
        //@uses dot.junit.opcodes.invoke_interface.d.T_invoke_interface_18
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_18", VerifyError.class);
    }

    /**
     * @constraint A15
     * @title invoke-interface may not be used to call <clinit>.
     */
    public void testVFE11() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_20", VerifyError.class);
    }

    /**
     * @constraint B9
     * @title types of arguments passed to method
     */
    public void testVFE12() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_21", VerifyError.class);
    }

    /**
     * @constraint A23
     * @title number of registers
     */
    public void testVFE13() {
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_8", VerifyError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to call undefined method.
     */
    public void testVFE14() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_7",
                   NoSuchMethodError.class, new ITestImpl());
    }

    /**
     * @constraint n/a
     * @title Method has different signature.
     */
    public void testVFE15() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        loadAndRun("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_16",
                   NoSuchMethodError.class, new ITestImpl());
    }


    /**
     * @constraint B6
     * @title instance methods may only be invoked on already initialized instances.
     */
    public void testVFE21() {
        //@uses dot.junit.opcodes.invoke_interface.ITest
        //@uses dot.junit.opcodes.invoke_interface.ITestImpl
        load("dot.junit.opcodes.invoke_interface.d.T_invoke_interface_22", VerifyError.class);
    }
}
