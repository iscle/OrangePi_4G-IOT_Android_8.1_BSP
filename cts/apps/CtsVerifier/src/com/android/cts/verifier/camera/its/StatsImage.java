/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.util.Log;

public class StatsImage {

    static {
        try {
            System.loadLibrary("ctsverifier_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e("StatsImage", "Error loading cts verifier JNI library");
            e.printStackTrace();
        }
    }

    public native static float[] computeStatsImage(
            byte[] img, int width, int height,
            int aaX, int aaY, int aaW, int aaH, int gridW, int gridH);

}
