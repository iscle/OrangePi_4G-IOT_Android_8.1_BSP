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

package dot.junit.opcodes.opc_return;

import dot.junit.DxTestCase;
import dot.junit.DxUtil;
import dot.junit.opcodes.opc_return.d.T_opc_return_1;
import dot.junit.opcodes.opc_return.d.T_opc_return_3;

public class Test_opc_return extends DxTestCase {
    /**
     * @title check that frames are discarded and reinstananted correctly
     */
    public void testN1() {
        T_opc_return_1 t = new T_opc_return_1();
        assertEquals(123456, t.run());
    }

    /**
     * @title Method is synchronized but thread is not monitor owner
     */
    public void testE1() {
        loadAndRun("dot.junit.opcodes.opc_return.d.T_opc_return_3",
                   IllegalMonitorStateException.class);
    }

    /**
     * @constraint B11 
     * @title method's return type - long
     */
    public void testVFE1() {
        load("dot.junit.opcodes.opc_return.d.T_opc_return_5", VerifyError.class);
    }

    /**
     * @constraint B11 
     * @title method's return type - reference
     */
    public void testVFE2() {
        load("dot.junit.opcodes.opc_return.d.T_opc_return_6", VerifyError.class);
    }

    /**
     * @constraint A23 
     * @title number of registers
     */
    public void testVFE3() {
        load("dot.junit.opcodes.opc_return.d.T_opc_return_7", VerifyError.class);
    }
    
    /**
     * @constraint B1 
     * @title return on wide register pair
     */
    public void testVFE4() {
        load("dot.junit.opcodes.opc_return.d.T_opc_return_8", VerifyError.class);
    }
}
