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

package dot.junit.opcodes.invoke_super;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_1;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_10;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_14;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_15;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_17;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_18;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_19;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_2;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_20;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_24;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_26;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_27;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_28;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_4;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_5;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_6;
import dot.junit.opcodes.invoke_super.d.T_invoke_super_7;

public class Test_invoke_super extends DxTestCase {

    /**
     * @title invoke method of superclass
     */
    public void testN1() {
        //@uses dot.junit.opcodes.invoke_super.d.T_invoke_super_1
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        T_invoke_super_1 t = new T_invoke_super_1();
        assertEquals(5, t.run());
    }


    /**
     * @title Invoke protected method of superclass
     */
    public void testN3() {
        //@uses dot.junit.opcodes.invoke_super.d.T_invoke_super_7
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        T_invoke_super_7 t = new T_invoke_super_7();
        assertEquals(5, t.run());
    }

    /**
     * @title Check that new frame is created by invoke_super and
     * arguments are passed to method
     */
    public void testN5() {
        //@uses dot.junit.opcodes.invoke_super.d.T_invoke_super_14
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        T_invoke_super_14 t = new T_invoke_super_14();
        assertTrue(t.run());
    }

    /**
     * @title Recursion of method lookup procedure
     */
    public void testN6() {
        //@uses dot.junit.opcodes.invoke_super.d.T_invoke_super_17
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        //@uses dot.junit.opcodes.invoke_super.d.TSuper2
        T_invoke_super_17 t = new T_invoke_super_17();
        assertEquals(5, t.run());
    }

    /**
     * @title invoke-super in conflict class
     */
    public void testN7() {
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_26", null);
    }

    /**
     * @title obj ref is null
     */
    public void testE1() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_2", NullPointerException.class);
        T_invoke_super_2 t = new T_invoke_super_2();
    }

    /**
     * @title Native method can't be linked
     */
    public void testE2() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_4", UnsatisfiedLinkError.class);
    }

    /**
     * @title Attempt to invoke abstract method
     */
    public void testE4() {
        //@uses dot.junit.opcodes.invoke_super.ATest
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_6", AbstractMethodError.class);
    }

    /**
     * @title Attempt to invoke abstract interface method
     */
    public void testE5() {
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_27", AbstractMethodError.class);
    }

    /**
     * @title Attempt to invoke abstract interface method
     */
    public void testE6() {
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_28",
                   IncompatibleClassChangeError.class);
    }

    /**
     * @constraint A13
     * @title invalid constant pool index
     */
    public void testVFE1() {
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_8", ClassNotFoundException.class);
    }

    /**
     * @constraint A15
     * @title &lt;clinit&gt; may not be called using invoke-super
     */
    public void testVFE3() {
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_10", VerifyError.class);
    }

    /**
     * @constraint B1
     * @title number of arguments passed to method
     */
    public void testVFE4() {
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_11", VerifyError.class);
    }

    /**
     * @constraint B9
     * @title types of arguments passed to method.
     */
    public void testVFE5() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_12", VerifyError.class);
    }

    /**
     * @constraint A15
     * @title &lt;init&gt; may not be called using invoke_super
     */
    public void testVFE6() {
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_16", VerifyError.class);
    }

    /**
     * @constraint B10
     * @title assignment incompatible references when accessing
     *                  protected method
     */
    public void testVFE8() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        //@uses dot.junit.opcodes.invoke_super.d.TPlain
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_22", VerifyError.class);
    }

    /**
     * @constraint B10
     * @title assignment incompatible references when accessing
     *                  public method
     */
    public void testVFE9() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        //@uses dot.junit.opcodes.invoke_super.d.TSuper2
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_23", VerifyError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to call static method.
     */
    public void testVFE10() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_5",
                   IncompatibleClassChangeError.class);
    }


    /**
     * @constraint n/a
     * @title Attempt to invoke non-existing method.
     */
    public void testVFE12() {
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_15", NoSuchMethodError.class);
    }

    /**
     * @constraint n/a
     * @title Attempt to invoke private method of other class.
     */
    public void testVFE13() {
        //@uses dot.junit.opcodes.invoke_super.TestStubs
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_18", IllegalAccessError.class,
                   new TestStubs());
    }

    /**
     * @constraint B12
     * @title Attempt to invoke protected method of unrelated class.
     */
    public void testVFE14() {
        //@uses dot.junit.opcodes.invoke_super.TestStubs
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_20", IllegalAccessError.class,
                   new TestStubs());
    }

    /**
     * @constraint n/a
     * @title Method has different signature.
     */
    public void testVFE15() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_19", NoSuchMethodError.class);
    }

    /**
     * @constraint n/a
     * @title invoke-super shall be used to invoke private methods
     */
    public void testVFE16() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_13", VerifyError.class);
    }

    /**
     * @constraint A23
     * @title number of registers
     */
    public void testVFE17() {
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_9", VerifyError.class);
    }

    /**
     * @constraint A13
     * @title attempt to invoke interface method
     */
    public void testVFE18() {
        loadAndRun("dot.junit.opcodes.invoke_super.d.T_invoke_super_24",
                   AbstractMethodError.class);
    }

    /**
     * @constraint B6
     * @title instance methods may only be invoked on already initialized instances.
     */
    public void testVFE19() {
        //@uses dot.junit.opcodes.invoke_super.d.TSuper
        load("dot.junit.opcodes.invoke_super.d.T_invoke_super_25", VerifyError.class);
    }
}
