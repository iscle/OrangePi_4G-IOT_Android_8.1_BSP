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
import android.hardware.camera2.cts.ScriptC_crop_yuvf_420_to_yuvx_444;
import android.hardware.camera2.cts.rs.AllocationInfo.ElementInfo;
import android.renderscript.Element;
import android.util.Log;

/**
 * Crop {@link ImageFormat#YUV_420_888 flexible-YUV} {@link Allocation allocations} into
 * a {@link ElementInfo#U8_3 U8_3} {@link Allocation allocation}.
 *
 * <p>Users of this script must configure it with the
 * {@link ScriptYuvCrop#CROP_WINDOW crop window} parameter.</p>
 *
 */
public class ScriptYuvCrop extends Script<ScriptC_crop_yuvf_420_to_yuvx_444> {
    private static final String TAG = "ScriptYuvCrop";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * A rectangle holding the top,left,right,bottom normalized coordinates each within [0,1].
     *
     * <p>The output will be a cropped copy of the input to only this crop window.</p>
     */
    // TODO: Change this to use Patch
    public static final Script.ScriptParameter<ScriptYuvCrop, RectF> CROP_WINDOW =
            new Script.ScriptParameter<ScriptYuvCrop, RectF>(ScriptYuvCrop.class,
                    RectF.class);

    private final RectF mCropWindow;

    private static AllocationInfo createOutputInfo(AllocationInfo inputInfo,
            ParameterMap<ScriptYuvCrop> parameters) {
        checkNotNull("inputInfo", inputInfo);
        checkNotNull("parameters", parameters);

        if (!parameters.contains(CROP_WINDOW)) {
            throw new IllegalArgumentException("Script's CROP_WINDOW was not set");
        }

        Size inputSize = inputInfo.getSize();
        RectF crop = parameters.get(CROP_WINDOW);
        Size outputSize = new Size(
                (int)(crop.width() * inputSize.getWidth()),
                (int)(crop.height() * inputSize.getHeight()));

        if (VERBOSE) Log.v(TAG, String.format("createOutputInfo - outputSize is %s", outputSize));

        /**
         * Input  YUV  W     x H
         * Output U8_3 CropW x CropH
         */
        return AllocationInfo.newInstance(Element.U8_3(getRS()), outputSize);
    }

    public ScriptYuvCrop(AllocationInfo inputInfo,
            ParameterMap<ScriptYuvCrop> parameterMap) {
        super(inputInfo,
              createOutputInfo(inputInfo, parameterMap),
              new ScriptC_crop_yuvf_420_to_yuvx_444(getRS()));

        // YUV_420_888 is the only supported format here
        if (!inputInfo.isElementEqualTo(ElementInfo.YUV)) {
            throw new UnsupportedOperationException("Unsupported element "
                    + inputInfo.getElement());
        }

        mCropWindow = parameterMap.get(CROP_WINDOW);
    }

    @Override
    protected void executeUnchecked() {
        mScript.forEach_crop(mOutputAllocation);

        if (VERBOSE) { Log.v(TAG, "executeUnchecked - forEach_crop done"); }
    }

    @Override
    protected void updateScriptInput() {
        int x = (int)(mCropWindow.left * mInputInfo.getSize().getWidth());
        int y = (int)(mCropWindow.top * mInputInfo.getSize().getHeight());

        mScript.set_src_x(x);
        mScript.set_src_y(y);

        mScript.set_mInput(mInputAllocation);
    }
}
