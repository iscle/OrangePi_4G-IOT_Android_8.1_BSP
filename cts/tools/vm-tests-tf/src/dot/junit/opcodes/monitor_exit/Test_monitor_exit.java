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

package dot.junit.opcodes.monitor_exit;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.monitor_exit.d.T_monitor_exit_1;
import dot.junit.opcodes.monitor_exit.d.T_monitor_exit_3;

public class Test_monitor_exit extends DxTestCase {

    /**
     * @title thread is not monitor owner
     */
    public void testE1() throws InterruptedException {
        //@uses dot.junit.opcodes.monitor_exit.TestRunnable
        final T_monitor_exit_1 t = new T_monitor_exit_1();
        final Object o = new Object();

        Runnable r = new TestRunnable(t, o);
        synchronized (o) {
            Thread th = new Thread(r);
            th.start();
            th.join();
        }
        if (t.result == false) {
            fail("expected IllegalMonitorStateException");
        }
    }


    /**
     * @title expected NullPointerException
     */
    public void testE3() {
        loadAndRun("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_3", NullPointerException.class);
    }

    /**
     * @constraint A23 
     * @title  number of registers
     */
    public void testVFE1() {
        load("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_4", VerifyError.class);
    }



    /**
     * @constraint B1 
     * @title  type of arguments - int
     */
    public void testVFE2() {
        load("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_5", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title  type of arguments - float
     */
    public void testVFE3() {
        load("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_6", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title  type of arguments - long
     */
    public void testVFE4() {
        load("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_7", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title  type of arguments - double
     */
    public void testVFE5() {
        load("dot.junit.opcodes.monitor_exit.d.T_monitor_exit_8", VerifyError.class);
    }

}


class TestRunnable implements Runnable {
    private T_monitor_exit_1 t;
    private Object o;

    public TestRunnable(T_monitor_exit_1 t, Object o) {
        this.t = t;
        this.o = o;
    }

    public void run() {
        try {
            t.run(o);
        } catch (IllegalMonitorStateException imse) {
            // expected
            t.result = true;
        }
    }
}
