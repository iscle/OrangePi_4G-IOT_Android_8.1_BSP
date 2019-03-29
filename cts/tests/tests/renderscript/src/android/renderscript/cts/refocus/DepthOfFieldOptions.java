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

/**
 * A struct containing all the data needed to apply a depth-of-field effect.
 */
public class DepthOfFieldOptions {
  public final RGBZ rgbz;
  public float focalDepth;
  public float blurInfinity;
  // The depth of field specifies the depth range in focus (i.e., zero blur) as
  // a ratio of the focal depth. Its range is [0, 1). The depth of field range
  // in depth units is computed as
  // [(1 - depthOfField) * focalDepth,(1 + depthOfField) * focalDepth].
  public float depthOfField;

  /**
   * Creates a {@code DepthOfFieldOptions} from an {@code RGBZ}.
   *
   * @param rgbz the {@code RGBZ} to render
   */
  public DepthOfFieldOptions(RGBZ rgbz) {
    this.focalDepth = (float)rgbz.getFocusDepth();
    this.depthOfField = (float)rgbz.getDepthOfField();
    this.blurInfinity = (float)rgbz.getBlurInfinity();
    this.rgbz = rgbz;
  }

  public void setFocusPoint(float x, float y) {
    this.focalDepth = rgbz.getDepth((int)(x * rgbz.getWidth()), (int)(y * rgbz.getHeight()));
  }

  public void setBokeh(float bokeh) {
    this.blurInfinity = bokeh * 200;
  }
}
