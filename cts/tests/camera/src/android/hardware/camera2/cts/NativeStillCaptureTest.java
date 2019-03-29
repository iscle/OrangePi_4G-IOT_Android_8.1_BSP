/*
 * Copyright 2016 The Android Open Source Project
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

package android.hardware.camera2.cts;

import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

/**
 * <p>Basic test for CameraManager class.</p>
 */
public class NativeStillCaptureTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "NativeStillCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /** Load jni on initialization */
    static {
        Log.i("NativeStillCaptureTest", "before loadlibrary");
        System.loadLibrary("ctscamera2_jni");
        Log.i("NativeStillCaptureTest", "after loadlibrary");
    }

    public void testStillCapture() {
        // Init preview surface to a guaranteed working size
        updatePreviewSurface(new Size(640, 480));
        assertTrue("testStillCapture fail, see log for details",
                testStillCaptureNative(DEBUG_FILE_NAME_BASE, mPreviewSurface));
    }

    private static native boolean testStillCaptureNative(
            String filePath, Surface previewSurface);
}
