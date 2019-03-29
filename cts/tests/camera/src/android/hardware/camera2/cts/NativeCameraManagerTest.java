/*
 * Copyright 2015 The Android Open Source Project
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

import android.test.AndroidTestCase;
import android.util.Log;

/**
 * <p>Basic test for CameraManager class.</p>
 */
public class NativeCameraManagerTest extends AndroidTestCase {
    private static final String TAG = "NativeCameraManagerTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /** Load jni on initialization */
    static {
        Log.i("NativeCameraManagerTest", "before loadlibrary");
        System.loadLibrary("ctscamera2_jni");
        Log.i("NativeCameraManagerTest", "after loadlibrary");
    }

    public void testCameraManagerGetAndClose() {
        assertTrue("testCameraManagerGetAndClose fail, see log for details",
                testCameraManagerGetAndCloseNative());
    }

    public void testCameraManagerGetCameraIds() {
        assertTrue("testCameraManagerGetCameraIds fail, see log for details",
                testCameraManagerGetCameraIdsNative());
    }

    public void testCameraManagerAvailabilityCallback() {
        assertTrue("testCameraManagerAvailabilityCallback fail, see log for details",
                testCameraManagerAvailabilityCallbackNative());
    }

    public void testCameraManagerCameraCharacteristics() {
        assertTrue("testCameraManagerCameraCharacteristics fail, see log for details",
                testCameraManagerCharacteristicsNative());
    }

    private static native boolean testCameraManagerGetAndCloseNative();
    private static native boolean testCameraManagerGetCameraIdsNative();
    private static native boolean testCameraManagerAvailabilityCallbackNative();
    private static native boolean testCameraManagerCharacteristicsNative();
}
