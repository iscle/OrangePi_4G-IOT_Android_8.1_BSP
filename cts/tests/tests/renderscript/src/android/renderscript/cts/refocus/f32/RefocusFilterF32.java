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

package android.renderscript.cts.refocus.f32;


import android.graphics.Bitmap;

import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.util.Log;

import android.renderscript.cts.refocus.BlurStack;
import android.renderscript.cts.refocus.KernelDataForRenderScript;
import android.renderscript.cts.refocus.LayerInfo;
import android.renderscript.cts.refocus.MediaStoreSaver;
import android.renderscript.cts.refocus.RefocusFilter;
import android.renderscript.cts.refocus.ScriptC_layered_filter_fast_f32;

/**
 * An accelerated implementation of RefocusFilter using float32 as pixel
 * representation. The corresponding RenderScript class is
 * ScriptC_layered_filter_f32. Integral image is used for the speedup.
 *
 * Example Usage:
 *
 * {@code RenderScript renderScript = RenderScript.create(context);}
 * {@code RefocusFilterF32 rfFilter = new RefocusFilterF32(renderScript);}
 * {@code ProgressCallback progress;}
 * {@code Bitmap result = rfFilter.compute(rgbdImage, blurStack, progress);}
 *
 */
public class RefocusFilterF32 extends
    RefocusFilter<ScriptC_layered_filter_fast_f32> {
  private static final String myTAG = "RefocusFilterF32";
  private static final boolean ENABLE_FAST_FILTER = true;
  private static final float MIN_DISC_RADIUS_FOR_FAST_FILTER = 3;
  boolean useFastFilterForCurrentLayer = false;
  ImageBuffersForRenderScriptF32 buffers;

  public RefocusFilterF32(RenderScript rs) {
    super(rs);
  }

  public void destroy() {
    buffers.destroy();
    scriptC.destroy();
  }

  @Override
  protected void initializeScriptAndBuffers(Bitmap inputImage,
      LayerInfo focalLayer) {
    scriptC = new ScriptC_layered_filter_fast_f32(renderScript);

    // Allocates, binds, and initializes buffers that interface between Java
    // and Render Script.
    // + 1 is for the boundary case of using integral image.
    KernelDataForRenderScript.setUseNewRS(false);
    int margin = KernelDataForRenderScript.getMaxKernelRadius() + 1;
    buffers = new ImageBuffersForRenderScriptF32(inputImage, margin,
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
    MediaStoreSaver.savePNG(mBitmap, "sharpF32", name, renderScript.getApplicationContext());
    mAllocation.destroy();
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
    MediaStoreSaver.savePNG(mBitmap, "fuzzyF32", name, renderScript.getApplicationContext());
    mAllocation.destroy();
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
    KernelDataForRenderScriptF32 kernelData =
        new KernelDataForRenderScriptF32(targetLayer, blurStack, renderScript);

    if (ENABLE_FAST_FILTER
        && kernelData.minDiskRadius > MIN_DISC_RADIUS_FOR_FAST_FILTER) {
      useFastFilterForCurrentLayer = true;
    } else {
      useFastFilterForCurrentLayer = false;
    }

    scriptC.bind_g_kernel_info(kernelData.getKernelInfo());
    scriptC.bind_g_kernel_stack(kernelData.stackAllocation);
  }

  @Override
  protected void computeLayerMatteBehindFocalDepth() {
    // Marks active pixels (pixels that are on this target layer);
    // Marks adjacent pixels that are close enough to active pixels;
    long startnow;
    long endnow;
    startnow = System.nanoTime();
    scriptC.forEach_MarkLayerMask(buffers.inAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "MarkLayerMask: "+(endnow - startnow)+ " ns" );

    startnow = System.nanoTime();
    scriptC.forEach_ComputeLayerMatteBehindFocalDepth(buffers.inAllocation);
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
    scriptC.forEach_FilterLayerBehindFocalDepth(buffers.inAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "FilterLayerBehindFocalDepth: "+(endnow - startnow)+ " ns" );

    //extractFuzzyImage("fuzzy_behind");
    //extractSharpImage("sharp_behind");
  }

  @Override
  protected void updateSharpImageUsingFuzzyImage() {
		// Log the kernel execution time
    long startnow;
    long endnow;
    startnow = System.nanoTime();
    scriptC.forEach_UpdateSharpImageUsingFuzzyImage(buffers.inAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "UpdateSharpImageUsingFuzzyImage: "+(endnow - startnow)+ " ns" );

    //extractSharpImage("sharp_update");
  }

  @Override
  protected void computeLayerMatteInFrontOfFocalDepth() {
    // Marks active pixels (pixels that are on this target layer);
    // Marks adjacent pixels that are close enough to active pixels;
    long startnow;
    long endnow;
    startnow = System.nanoTime();
    scriptC.forEach_MarkLayerMask(buffers.inAllocation);
    endnow = System.nanoTime();
    Log.d(myTAG, "MarkLayerMask: "+(endnow - startnow)+ " ns" );

    startnow = System.nanoTime();
    scriptC.forEach_ComputeLayerMatteInFrontOfFocalDepth(buffers.inAllocation);
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
    scriptC.forEach_FilterLayerInFrontOfFocalDepth(buffers.inAllocation);
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
    endnow = System.nanoTime();
    Log.d(myTAG, "FinalizeFuzzyImageUsingSharpImage: "+(endnow - startnow)+ " ns" );
  }
}
