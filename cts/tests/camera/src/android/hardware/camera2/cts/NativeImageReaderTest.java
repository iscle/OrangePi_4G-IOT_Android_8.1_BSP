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

import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.util.Log;

/**
 * <p>Basic test for CameraManager class.</p>
 */
public class NativeImageReaderTest extends Camera2AndroidTestCase {
    private static final String TAG = "NativeImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /** Load jni on initialization */
    static {
        Log.i("NativeImageReaderTest", "before loadlibrary");
        System.loadLibrary("ctscamera2_jni");
        Log.i("NativeImageReaderTest", "after loadlibrary");
    }

    public void testJpeg() {
        assertTrue("testJpeg fail, see log for details",
                testJpegNative(DEBUG_FILE_NAME_BASE));
    }

    public void testImageReaderCloseAcquiredImages() {
        assertTrue("testImageReaderClose fail, see log for details",
                testImageReaderCloseAcquiredImagesNative());
    }

    private static native boolean testJpegNative(String filePath);
    private static native boolean testImageReaderCloseAcquiredImagesNative();
}
