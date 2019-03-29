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

import android.util.Size;
import android.hardware.camera2.cts.ScriptC_means_yuvx_444_1d_to_single;
import android.hardware.camera2.cts.rs.AllocationInfo.ElementInfo;
import android.renderscript.Element;

/**
 * Average a {@code Hx1} {@link ElementInfo#U8_3 U8_3} {@link Allocation allocation} into a 1x1
 * {@code U8x3} {@link Allocation allocation}.
 *
 * <p>Users of this script should chain {@link ScriptYuvMeans1d} immediately before this
 * to average the input down to a 1x1 element.</p>
 */
public class ScriptYuvMeans1d extends Script<ScriptC_means_yuvx_444_1d_to_single>{
    private static final String TAG = "ScriptYuvMeans1d";

    private static final Size UNIT_SQUARE = new Size(/*width*/1, /*height*/1);

    private static AllocationInfo createOutputInfo(AllocationInfo inputInfo) {
        checkNotNull("inputInfo", inputInfo);
        // (input) Hx1 -> 1x1
        return AllocationInfo.newInstance(Element.U8_3(getRS()), UNIT_SQUARE);
    }

    public ScriptYuvMeans1d(AllocationInfo inputInfo) {
        super(inputInfo,
              createOutputInfo(inputInfo),
              new ScriptC_means_yuvx_444_1d_to_single(getRS()));

        // U8x3 is the only supported element here
        if (!inputInfo.isElementEqualTo(ElementInfo.U8_3)) {
            throw new UnsupportedOperationException("Unsupported element "
                    + inputInfo.getElement());
        }
    }

    @Override
    protected void executeUnchecked() {
        mScript.forEach_means_yuvx_444(mOutputAllocation);
    }

    @Override
    protected void updateScriptInput() {
        mScript.set_mInput(mInputAllocation);

        int width = mInputAllocation.getType().getX();
        mScript.set_width(width);
        mScript.set_inv_width(1.0f / width);
    }
}
