/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.cts.rsblas;

import android.renderscript.RenderScript;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RSRuntimeException;
import android.util.Log;

/**
 * Base RenderScript test class. This class provides a message handler and a
 * convenient way to wait for compute scripts to complete their execution.
 */
public class RSBaseCompute extends RSBase {
    RenderScript mRS;
    protected int INPUTSIZE = 512;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRS = RenderScript.create(mCtx);
        mRS.setMessageHandler(mRsMessage);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}
