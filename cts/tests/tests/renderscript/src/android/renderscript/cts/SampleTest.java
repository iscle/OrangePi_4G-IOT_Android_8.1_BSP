/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RSIllegalArgumentException;
import android.renderscript.Sampler;
import android.renderscript.Type;
import android.renderscript.Type.CubemapFace;

public class SampleTest extends RSBaseCompute {

    ScriptC_sample mScript;

    Allocation mAlloc_RGBA_1D;
    Allocation mAlloc_RGBA_2D;

    Allocation createAlloc(Type t) {
        Allocation a = Allocation.createTyped(mRS, t, Allocation.MipmapControl.MIPMAP_FULL,
                                              Allocation.USAGE_SCRIPT);

        int[] tmp = new int[t.getCount()];
        int idx = 0;
        int w = t.getY();
        if (w < 1) {
            w = 1;
        }

        for (int ct = 0; ct < (8 * w); ct++) {
            tmp[idx++] = 0x0000ffff;
        }
        w = (w + 1) >> 1;
        for (int ct = 0; ct < (4 * w); ct++) {
            tmp[idx++] = 0x00ff00ff;
        }
        w = (w + 1) >> 1;
        for (int ct = 0; ct < (2 * w); ct++) {
            tmp[idx++] = 0x00ffff00;
        }
        w = (w + 1) >> 1;
        for (int ct = 0; ct < (1 * 1); ct++) {
            tmp[idx++] = 0xffffff00;
        }
        a.copyFromUnchecked(tmp);
        return a;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Element format = Element.RGBA_8888(mRS);
        Type.Builder b = new Type.Builder(mRS, format);
        b.setMipmaps(true);
        mAlloc_RGBA_1D = createAlloc(b.setX(8).create());
        mAlloc_RGBA_2D = createAlloc(b.setX(8).setY(8).create());

        mScript = new ScriptC_sample(mRS);

        mScript.set_gNearest(Sampler.CLAMP_NEAREST(mRS));
        mScript.set_gLinear(Sampler.CLAMP_LINEAR(mRS));

        Sampler.Builder sb = new Sampler.Builder(mRS);
        sb.setMinification(Sampler.Value.LINEAR_MIP_NEAREST);
        mScript.set_gMipNearest(sb.create());

        mScript.set_gMipLinear(Sampler.CLAMP_LINEAR_MIP_LINEAR(mRS));
    }

    @Override
    protected void tearDown() throws Exception {
        mAlloc_RGBA_1D.destroy();
        mAlloc_RGBA_2D.destroy();
        mScript.destroy();
        super.tearDown();
    }

    public void testNearest() {
        mScript.invoke_test_RGBA(mAlloc_RGBA_1D, mAlloc_RGBA_2D);
        mRS.finish();
        waitForMessage();
        checkForErrors();
    }
}


