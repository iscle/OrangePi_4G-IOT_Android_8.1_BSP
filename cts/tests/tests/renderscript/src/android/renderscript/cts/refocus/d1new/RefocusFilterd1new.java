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

package android.renderscript.cts.refocus.d1new;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;

import android.renderscript.Script;
import android.renderscript.cts.refocus.BlurStack;
import android.renderscript.cts.refocus.KernelDataForRenderScript;
import android.renderscript.cts.refocus.LayerInfo;
import android.renderscript.cts.refocus.MediaStoreSaver;
import android.renderscript.cts.refocus.RefocusFilter;
import android.renderscript.cts.refocus.ScriptC_layered_filter_fast_d1new;
import android.util.Log;

/**
 * An accelerated implementation of RefocusFilter using float32 as pixel
 * representation. The corresponding RenderScript class is
 * ScriptC_layered_filter_Fast. Integral image is used for the speedup.
 *
 * Example Usage:
 *
 * {@code RenderScript renderScript = RenderScript.create(context);}
 * {@code RefocusFilterd1new rfFilter = new RefocusFilterd1new(renderScript);}
 * {@code ProgressCallback progress;}
 * {@code Bitmap result = rfFilter.compute(rgbdImage, blurStack, progress);}
 */
public class RefocusFilterd1new extends
        RefocusFilter<ScriptC_layered_filter_fast_d1new> {
  private static final String myTAG = "RefocusFilterd1new";
  private static final boolean ENABLE_FAST_FILTER = true;
  private static final float MIN_DISC_RADIUS_FOR_FAST_FILTER = 3;
  boolean useFastFilterForCurrentLayer = false;
  ImageBuffersForRenderScriptd1new buffers;
  Allocation kernelInfo, kernelStack;

  public RefocusFilterd1new(RenderScript rs) {
    super(rs);
  }

  public void destroy() {
    buffers.destroy();
    kernelInfo.destroy();
    kernelStack.destroy();
    scriptC.destroy();
  }

  @Override
  protected void initializeScriptAndBuffers(Bitmap inputImage,
      LayerInfo focalLayer) {
    scriptC = new ScriptC_layered_filter_fast_d1new(renderScript);

    // Allocates, binds, and initializes buffers that interface between Java
    // and Render Script.
    // + 1 is for the boundary case of using integral image.
    KernelDataForRenderScript.setUseNewRS(true);
    int margin = KernelDataForRenderScript.getMaxKernelRadius() + 1;
    buffers = new ImageBuffersForRenderScriptd1new(inputImage, margin,
        renderScript, scriptC);
    buffers.initializeRenderScript(focalLayer, scriptC);
  }

  @Override
  protected Bitmap extractResultImage() {
    // Extracts the result from .rs file to {@code buffers.outputImage} in Java.
    long startnow;
    long endnow;
    startnow = System.nanoTime();
    scriptC.forEach_PackOutputImage(buffers.outAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "PackOutputImage: "+(endnow - startnow)+ " ns" );

    buffers.outAllocation.copyTo(buffers.outputImage);
    return buffers.outputImage;
  }

  /*
   * Utility Method to extract intermediatory result
   */
  private  void extractSharpImage(String name) {

    Bitmap mBitmap = Bitmap.createBitmap(buffers.inputImage.getWidth(),
            buffers.inputImage.getHeight(), Bitmap.Config.ARGB_8888);
    Allocation mAllocation = Allocation.createFromBitmap(renderScript, mBitmap);
    scriptC.forEach_PackSharpImage(mAllocation);

    mAllocation.copyTo(mBitmap);
    mAllocation.destroy();
    MediaStoreSaver.savePNG(mBitmap, "sharpd1new", name, renderScript.getApplicationContext());
  }
  /*
   * Utility Method to extract intermediatory result
   */
  private  void extractFuzzyImage(String name) {

    Bitmap mBitmap = Bitmap.createBitmap(buffers.inputImage.getWidth(),
            buffers.inputImage.getHeight(), Bitmap.Config.ARGB_8888);
    Allocation mAllocation = Allocation.createFromBitmap(renderScript, mBitmap);
    scriptC.forEach_PackFuzzyImage(mAllocation);

    mAllocation.copyTo(mBitmap);
    mAllocation.destroy();
    MediaStoreSaver.savePNG(mBitmap, "fuzzyd1new", name, renderScript.getApplicationContext());
  }

  @Override
  protected void setTargetLayer(LayerInfo layerInfo) {
    scriptC.invoke_SetTargetLayer(layerInfo.frontDepth, layerInfo.backDepth);
  }

  @Override
  protected void setBlendInfo(int dilationRadius) {
    scriptC.invoke_SetBlendInfo(dilationRadius);
  }

  @Override
  protected void setKernelData(int targetLayer, BlurStack blurStack) {
    KernelDataForRenderScriptd1new kernelData =
        new KernelDataForRenderScriptd1new(targetLayer, blurStack, renderScript);

    if (ENABLE_FAST_FILTER
        && kernelData.minDiskRadius > MIN_DISC_RADIUS_FOR_FAST_FILTER) {
      useFastFilterForCurrentLayer = true;
    } else {
      useFastFilterForCurrentLayer = false;
    }
    scriptC.set_g_kernel_stack(kernelData.stackAllocation);
    scriptC.set_galloc_kernel_info(kernelData.infoAllocation);
    if (kernelInfo != null) {
      kernelInfo.destroy();
    }
    kernelInfo = kernelData.infoAllocation;
    if (kernelStack != null) {
      kernelStack.destroy();
    }
    kernelStack = kernelData.stackAllocation;
  }

  @Override
  protected void computeLayerMatteBehindFocalDepth() {
    // Marks active pixels (pixels that are on this target layer);
    // Marks adjacent pixels that are close enough to active pixels;
    long startnow;
    long endnow;

    startnow = System.nanoTime();
    //scriptC.forEach_MarkLayerMask(buffers.inAllocation);
    // Pass the sharp actual depth allocation directly into the kernel, and modify the dilated depth
    // allocation which is set as a global.
    scriptC.forEach_MarkLayerMaskPassInput(buffers.sharpActualDepthAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "MarkLayerMask: "+(endnow - startnow)+ " ns" );

    startnow = System.nanoTime();
    //scriptC.forEach_ComputeLayerMatteBehindFocalDepth(buffers.inAllocation);
    // Pass g_sharp_meta into kernel and get updated g_sharp_meta
    scriptC.forEach_ComputeLayerMatteBehindFocalDepthPassInput(buffers.sharpDilatedDepthAllocation, buffers.sharpDilatedDepthAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "ComputeLayerMatteBehindFocalDepth: "+(endnow - startnow)+ " ns" );
  }

  @Override
  protected void filterLayerBehindFocalDepth() {
    // Filters the target layer and saves the result to {@code g_accum_map} in
    // .rs file.
    long startnow;
    long endnow;

    if (useFastFilterForCurrentLayer) {
      scriptC.invoke_SetUseIntegralImage(1);
      Script.LaunchOptions launchOptions = new Script.LaunchOptions();
      launchOptions.setX(0, 1);
      launchOptions.setY(0, buffers.inputImage.getHeight());

      startnow = System.nanoTime();
      scriptC.forEach_ComputeIntegralImageForLayerBehindFocalDepth(
          buffers.inAllocation, launchOptions);
      endnow = System.nanoTime();
      Log.d(myTAG, "ComputeIntegralImageForLayerBehindFocalDepth: "+(endnow - startnow)+ " ns" );
    } else {
      scriptC.invoke_SetUseIntegralImage(0);
    }

    startnow = System.nanoTime();
    //scriptC.forEach_FilterLayerBehindFocalDepth(buffers.inAllocation);
    // Pass g_fuzzy_RGBA into kernel and get g_fuzzy_RGBA as output
    scriptC.forEach_FilterLayerBehindFocalDepthPassInput(buffers.fuzzyRGBAAllocation, buffers.fuzzyRGBAAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "FilterLayerBehindFocalDepth: "+(endnow - startnow)+ " ns" );
    //extractFuzzyImage("fuzzy_behind");
    //extractSharpImage("sharp_behind");
  }

  @Override
  protected void updateSharpImageUsingFuzzyImage() {
    long startnow;
    long endnow;

    startnow = System.nanoTime();

    //scriptC.forEach_UpdateSharpImageUsingFuzzyImage(buffers.inAllocation);
    // Pass input and output version of UpdateSharpImageUsingFuzzyImage
		scriptC.forEach_UpdateSharpUsingFuzzyPassInput(buffers.sharpDilatedDepthAllocation, buffers.sharpDilatedDepthAllocation);

		endnow = System.nanoTime();
		Log.d(myTAG, "updateSharpImageUsingFuzzyImage: "+(endnow - startnow)+ " ns" );
    //extractSharpImage("sharp_update");
  }

  @Override
  protected void computeLayerMatteInFrontOfFocalDepth() {
    // Marks active pixels (pixels that are on this target layer);
    // Marks adjacent pixels that are close enough to active pixels;
    long startnow;
    long endnow;

    startnow = System.nanoTime();
    //scriptC.forEach_MarkLayerMask(buffers.inAllocation);
    // Pass the sharp actual depth allocation directly into the kernel, and modify the dilated depth
    // allocation which is set as a global.
    scriptC.forEach_MarkLayerMaskPassInput(buffers.sharpActualDepthAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "MarkLayerMask: "+(endnow - startnow)+ " ns" );

    startnow = System.nanoTime();
    //scriptC.forEach_ComputeLayerMatteInFrontOfFocalDepth(buffers.inAllocation);
    // Pass g_sharp_meta and g_fuzzy_RGBA directly into the kernel
    scriptC.forEach_ComputeLayerMatteInFrontOfFocalDepthPassInput(buffers.sharpDilatedDepthAllocation, buffers.sharpDilatedDepthAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "ComputeLayerMatteInFrontOfFocalDepth: "+(endnow - startnow)+ " ns" );
  }

  @Override
  protected void filterLayerInFrontOfFocalDepth() {
    // Filters the target layer and accumulates the result to {@code
    // g_accum_map} in .rs file.
    long startnow;
    long endnow;
    if (useFastFilterForCurrentLayer) {
      scriptC.invoke_SetUseIntegralImage(1);
      Script.LaunchOptions launchOptions = new Script.LaunchOptions();
      launchOptions.setX(0, 1);
      launchOptions.setY(0, buffers.inputImage.getHeight());

      startnow = System.nanoTime();
      scriptC.forEach_ComputeIntegralImageForLayerInFrontOfFocalDepth(
          buffers.inAllocation, launchOptions);
      endnow = System.nanoTime();
      Log.d(myTAG, "ComputeIntegralImageForLayerInFrontOfFocalDepth: "+(endnow - startnow)+ " ns" );
    } else {
      scriptC.invoke_SetUseIntegralImage(0);
    }
    startnow = System.nanoTime();
    //scriptC.forEach_FilterLayerInFrontOfFocalDepth(buffers.inAllocation);
    // Pass g_sharp_dilated_depth into kernel and get g_fuzzy_RGBA as output
    scriptC.forEach_FilterLayerInFrontOfFocalDepthPassInput(buffers.sharpDilatedDepthAllocation, buffers.sharpDilatedDepthAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "FilterLayerInFrontOfFocalDepth: "+(endnow - startnow)+ " ns" );

    //extractFuzzyImage("fuzzy_front");
    //extractSharpImage("sharp_front");
  }

  @Override
  protected void finalizeFuzzyImageUsingSharpImage() {
    // Blends {@code g_accum_map} and {@code g_focus_map} in .rs file.
    // Saves the result in {@code g_accum_map}.
    long startnow;
    long endnow;
    startnow = System.nanoTime();
    scriptC.forEach_FinalizeFuzzyImageUsingSharpImage(buffers.inAllocation);
    //scriptC.forEach_FinalizeFuzzyImageUsingSharpImagePassInput(buffers.sharpActualDepthAllocation, buffers.fuzzyRGBAAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "finalizeFuzzyImageUsingSharpImage: "+(endnow - startnow)+ " ns" );
  }
}
