/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.hardware.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;

/**
 * Check sensor native library funcationality.
 *
 * This is the place to implement sensor NDK CTS tests.
 */
public class SensorNativeTest extends SensorTestCase {
    private SensorManager mSensorManager;
    private boolean mAreHifiSensorsSupported;
    private boolean mVrHighPerformanceModeSupported;

    protected native long nativeSetUp();
    protected native void nativeTearDown(long instance);
    private native void nativeTest(long instance);
    private long mNativeInstance;

    static {
        System.loadLibrary("cts-sensors-ndk-jni");
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mNativeInstance = nativeSetUp();
        assertTrue("create native instance failed", mNativeInstance != 0);
    }

    @Override
    public void tearDown() throws Exception {
        nativeTearDown(mNativeInstance);
    }

    public void testNative() throws AssertionError {
        nativeTest(mNativeInstance);
    }
}
