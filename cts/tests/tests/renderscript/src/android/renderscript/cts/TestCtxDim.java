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

package android.renderscript.cts;

import android.renderscript.*;

public class TestCtxDim extends RSBaseCompute {

    public void test() {
        ScriptC_TestCtxDim script = new ScriptC_TestCtxDim(mRS);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        int X = 2;
        script.set_gDimX(X);
        typeBuilder.setX(X);
        int Y = 5;
        script.set_gDimY(Y);
        typeBuilder.setY(Y);
        int Z = 11;
        script.set_gDimZ(Z);
        typeBuilder.setZ(Z);

        Allocation A = Allocation.createTyped(mRS, typeBuilder.create());

        script.forEach_check_kernel(A);
        script.invoke_check_result();
        mRS.finish();
        waitForMessage();

        A.destroy();
        script.destroy();
    }
}
