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

import android.util.Log;
import android.renderscript.RenderScript;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Type;
import android.renderscript.Script;

public class IntrinsicBase extends RSBaseCompute {
    protected final String TAG = "Img";

    protected Allocation mAllocSrc;
    protected Allocation mAllocRef;
    protected Allocation mAllocDst;
    protected ScriptC_verify mVerify;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mVerify = new ScriptC_verify(mRS);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mVerify != null) {
            mVerify.destroy();
            mVerify = null;
        }
        super.tearDown();
    }

    protected void checkError() {
        mRS.finish();
        mVerify.invoke_checkError();
        waitForMessage();
        checkForErrors();
    }
}
