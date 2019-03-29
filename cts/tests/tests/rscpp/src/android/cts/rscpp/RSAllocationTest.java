/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.cts.rscpp;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.renderscript.*;
import android.util.Log;

public class RSAllocationTest extends RSCppTest {
    static {
        System.loadLibrary("rscpptest_jni");
    }

    native boolean typedTest(String path);
    public void testRSAllocationTypes() {
        assertTrue(typedTest(this.getContext().getCacheDir().toString()));
    }

    native boolean test1DCopy(String path);
    public void testRSAllocationCopy1D() {
        assertTrue(test1DCopy(this.getContext().getCacheDir().toString()));
    }

    native boolean test2DCopy(String path);
    public void testRSAllocationCopy2D() {
        assertTrue(test2DCopy(this.getContext().getCacheDir().toString()));
    }

    native boolean test3DCopy(String path);
    public void testRSAllocationCopy3D() {
        assertTrue(test3DCopy(this.getContext().getCacheDir().toString()));
    }

    native boolean test1DCopyPadded(String path);
    public void testRSAllocationCopy1DPadded() {
        assertTrue(test1DCopyPadded(this.getContext().getCacheDir().toString()));
    }

    native boolean test2DCopyPadded(String path);
    public void testRSAllocationCopy2DPadded() {
        assertTrue(test2DCopyPadded(this.getContext().getCacheDir().toString()));
    }

    native boolean test3DCopyPadded(String path);
    public void testRSAllocationCopy3DPadded() {
        assertTrue(test3DCopyPadded(this.getContext().getCacheDir().toString()));
    }

    native boolean testSetElementAt(String path);
    public void testRSAllocationSetElementAt() {
        assertTrue(testSetElementAt(this.getContext().getCacheDir().toString()));
    }


}
