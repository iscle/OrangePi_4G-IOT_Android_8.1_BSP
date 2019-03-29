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
import android.graphics.RectF;
import android.util.Size;
import android.hardware.camera2.cts.ScriptC_means_yuvx_444_2d_to_1d;
import android.hardware.camera2.cts.rs.AllocationInfo.ElementInfo;
import android.util.Log;

/**
 * Average pixels from a {@link ImageFormat#YUV_420_888 flexible-YUV} or
 * {@link ElementInfo#U8_3 U8_3} {@link Allocation allocations} into a 1D Hx1
 * {@link ElementInfo#U8_3 U8_3} {@link Allocation allocation}.
 *
 * <p>Users of this script should chain {@link ScriptYuvMeans1d} immediately afterwards
 * to average the output down to a 1x1 element.</p>
 */
public class ScriptYuvMeans2dTo1d extends Script<ScriptC_means_yuvx_444_2d_to_1d> {

    private static final String TAG = "ScriptYuvMeans2dTo1d";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static AllocationInfo createOutputInfo(AllocationInfo inputInfo) {
        checkNotNull("inputInfo", inputInfo);
        // (input) WxH -> (output) Hx1
        return AllocationInfo.newInstance(inputInfo.getElement(),
                new Size(inputInfo.getSize().getHeight(), /*height*/1));
    }

    public ScriptYuvMeans2dTo1d(AllocationInfo inputInfo) {
        super(inputInfo,
              createOutputInfo(inputInfo),
              new ScriptC_means_yuvx_444_2d_to_1d(getRS()));

        // YUV_420_888 and U8_3 is the only supported format here
        if (!inputInfo.isElementEqualTo(ElementInfo.YUV) &&
                !inputInfo.isElementEqualTo(ElementInfo.U8_3)) {
            throw new UnsupportedOperationException("Unsupported element "
                    + inputInfo.getElement());
        }
    }

    @Override
    protected void executeUnchecked() {
        // TODO: replace with switch statement
        if (mInputInfo.isElementEqualTo(ElementInfo.YUV)) {
            mScript.forEach_means_yuvf_420(mOutputAllocation);

            if (VERBOSE) Log.v(TAG, "executeUnchecked - forEach_means_yuvf_420");
        } else if (mInputInfo.isElementEqualTo(ElementInfo.U8_3)) {
            mScript.forEach_means_yuvx_444(mOutputAllocation);

            if (VERBOSE) Log.v(TAG, "executeUnchecked - forEach_means_yuvx_444");
        } else {
            throw new UnsupportedOperationException("Unsupported element "
                    + mInputInfo.getElement());
        }
    }

    @Override
    protected void updateScriptInput() {
        mScript.set_mInput(mInputAllocation);

        int width = mInputAllocation.getType().getX();
        mScript.set_width(width);
        mScript.set_inv_width(1.0f / width);

        // Do not crop. Those who want to crop should use ScriptYuvCrop.class
        mScript.set_src_x(0);
        mScript.set_src_y(0);
    }
}
