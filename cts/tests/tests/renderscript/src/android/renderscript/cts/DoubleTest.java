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

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.cts.Target;

public class DoubleTest extends RSBaseCompute {

    public void testDoubleGlobal() {
        RenderScript rs = RenderScript.create(getContext());
        ScriptC_doubleglobal dc = new ScriptC_doubleglobal(rs);
        dc.invoke_func_setup();
        int big = 1024 * 1024;
        Allocation out = Allocation.createSized(rs, Element.F32(rs), big);

        dc.forEach_times2pi(out);
        float[] data = new float[big];
        out.copyTo(data);

        Target t = new Target(Target.FunctionType.NORMAL, Target.ReturnType.DOUBLE, true);
        t.setPrecision(1, 1);
        double pi = 3.14159265359;
        Target.Floaty pi2 = t.new32((float) (pi * 2));
        for (int x = 0; x < data.length; x++) {
            float v = data[x];
            Target.Floaty expected = t.multiply(pi2, t.new32(x));
            if (!expected.couldBe(v)) {
                StringBuilder message = new StringBuilder();
                message.append("X: ");
                appendVariableToMessage(message, x);
                message.append("\n");
                message.append("Expected output: ");
                appendVariableToMessage(message, expected);
                message.append("\n");
                message.append("Actual   output: ");
                appendVariableToMessage(message, v);

                message.append("\n");
                assertTrue("Incorrect output for testDoubleGlobal " + message.toString(), false);
            }
        }

        out.destroy();
        dc.destroy();
    }
}


