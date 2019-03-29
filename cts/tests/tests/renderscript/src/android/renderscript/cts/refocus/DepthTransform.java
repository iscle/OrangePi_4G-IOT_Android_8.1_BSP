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
 * Interface defining a depth transform that translates real depth values
 * into an 8-bit quantized representation.
 */
public interface DepthTransform {
    /**
     * @return The near depth value
     */
    public float getNear();

    /**
     * @return The far depth value
     */
    public float getFar();

    /**
     * @return The format of the transform
     */
    public String getFormat();

    /**
     * @return the quantized value that corresponds to the given depth value
     */
    public int quantize(float depth);

    /**
     * @return the depth value that corresponds to the given quantized value
     */
    public float reconstruct(int value);
}
