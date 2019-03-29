/*
 * Copyright 2017 The Android Open Source Project
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

package android.media.cts;

import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

/**
 * Verification test for AImageReader.
 */
public class NativeImageReaderTest extends AndroidTestCase {
    private static final String TAG = "NativeImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /** Load jni on initialization */
    static {
        Log.i("NativeImageReaderTest", "before loadlibrary");
        System.loadLibrary("ctsimagereader_jni");
        Log.i("NativeImageReaderTest", "after loadlibrary");
    }

    public void testSucceedsWithSupportedUsageFormat() {
        assertTrue(
                "Native test failed, see log for details",
                testSucceedsWithSupportedUsageFormatNative());
    }

    public void testTakePictures() {
        assertTrue("Native test failed, see log for details", testTakePicturesNative());
    }

    public void testCreateSurface() {
        Surface surface = testCreateSurfaceNative();
        assertNotNull("Surface created is null.", surface);
        assertTrue("Surface created is invalid.", surface.isValid());
    }

    private static native boolean testSucceedsWithSupportedUsageFormatNative();
    private static native boolean testTakePicturesNative();
    private static native Surface testCreateSurfaceNative();
}
