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
package android.graphics.cts;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** This test case must run with hardware. It can't be tested in emulator. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class CameraGpuTest {

    private static final String TAG = "CameraGpuTest";
    private Context mContext;

    @Rule
    public ActivityTestRule<CameraGpuCtsActivity> mActivityRule =
            new ActivityTestRule<>(CameraGpuCtsActivity.class, false, false);

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    private boolean cameraAvailable() throws Exception {
        CameraManager cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            if(cameraIds.length > 0) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraIds[0]);
                for(int capability : characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)) {
                    if(capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
                        return true;
                }
            }
        } catch (CameraAccessException e) {
            Assert.fail("Failed to access camera, " + Log.getStackTraceString(e));
        }
        return false;
    }

    @Test
    public void testCameraImageCaptureAndRendering() throws Exception {
        if(cameraAvailable()) {
            CameraGpuCtsActivity activity = mActivityRule.launchActivity(null);
            activity.waitToFinishRendering();
            activity.finish();
        }
    }
}
