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

package android.renderscript.cts.refocus;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

/**
 * A class that manages the image buffers that interface between Java and Render
 * Script. This class will be specialized for float in f32 package and for byte
 * in u8 package.
 */
public class ImageBuffersForRenderScript {
  /**
   * Input and output images and their corresponding Allocation to interface
   * with Render Script. Both input and output images are unpadded images.
   */
  public Bitmap inputImage;
  public Bitmap outputImage;
  public Allocation inAllocation;
  public Allocation outAllocation;

  /**
   * The following three member variables are used in the subclasses that extend
   * this class. Therefore, they are protected.
   */
  public int imageWidthPadded;
  public int imageHeightPadded;
  public int paddedMargin;

  public ImageBuffersForRenderScript(Bitmap inImage, int margin,
                                     RenderScript renderScript) {
    inputImage = inImage;
    inAllocation = Allocation.createFromBitmap(renderScript, inputImage);

    outputImage = Bitmap.createBitmap(inputImage.getWidth(),
        inputImage.getHeight(), Bitmap.Config.ARGB_8888);
    outAllocation = Allocation.createFromBitmap(renderScript, outputImage);

    paddedMargin = margin;
    imageWidthPadded = inputImage.getWidth() + 2 * margin;
    imageHeightPadded = inputImage.getHeight() + 2 * margin;
  }

  public void destroy() {
    inAllocation.destroy();
    outAllocation.destroy();
  }
}
