/*
 * Copyright 2014 The Android Open Source Project
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
package android.hardware.camera2.cts.rs;

import static android.hardware.camera2.cts.helpers.Preconditions.*;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.camera2.cts.rs.AllocationInfo.ElementInfo;
import android.renderscript.Element;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Log;

/**
 * Convert {@link ImageFormat#YUV_420_888 flexible-YUV} {@link Allocation allocations} into
 * a {@link ElementInfo#RGBA_8888 RGBA_8888} {@link Allocation allocation}.
 */
public class ScriptYuvToRgb extends Script<ScriptIntrinsicYuvToRGB> {
    private static final String TAG = "ScriptYuvToRgb";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static AllocationInfo createOutputInfo(AllocationInfo outputInfo) {
        checkNotNull("outputInfo", outputInfo);
        return outputInfo.changeFormatWithDefaultUsage(PixelFormat.RGBA_8888);
    }

    public ScriptYuvToRgb(AllocationInfo inputInfo) {
        super(inputInfo,
              createOutputInfo(inputInfo),
              ScriptIntrinsicYuvToRGB.create(getRS(), Element.YUV(getRS())));

        // YUV_420_888 is the only supported format here
        //      XX: Supports any YUV 4:2:0 such as NV21/YV12 or just YUV_420_888 ?
        if (!inputInfo.isElementEqualTo(ElementInfo.YUV)) {
            throw new UnsupportedOperationException("Unsupported element "
                    + inputInfo.getElement());
        }
    }

    @Override
    protected void executeUnchecked() {
        mScript.forEach(mOutputAllocation);

        if (VERBOSE) { Log.v(TAG, "executeUnchecked - forEach done"); }
    }

    @Override
    protected void updateScriptInput() {
        mScript.setInput(mInputAllocation);
    }
}
