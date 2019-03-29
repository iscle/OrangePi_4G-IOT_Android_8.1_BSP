/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.cts.helpers.CameraUtils;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.Collections;
import java.util.List;

@LargeTest
public class Camera_SizeTest extends CtsAndroidTestCase {

    private final int HEIGHT1 = 320;
    private final int WIDTH1 = 240;
    private final int HEIGHT2 = 480;
    private final int WIDTH2 = 320;
    private final int HEIGHT3 = 640;
    private final int WIDTH3 = 480;

    private static final float ASPECT_RATIO_TOLERANCE = 0.05f;

    private static final String TAG = "Camera_SizeTest";

    public void testConstructor() {
        if (Camera.getNumberOfCameras() < 1) {
            return;
        }

        Camera camera = Camera.open(0);
        Parameters parameters = camera.getParameters();

        checkSize(parameters, WIDTH1, HEIGHT1);
        checkSize(parameters, WIDTH2, HEIGHT2);
        checkSize(parameters, WIDTH3, HEIGHT3);

        camera.release();
    }

    /**
     * Check that the largest available preview and jpeg outputs have the same aspect ratio.  This
     * aspect ratio must be the same as the physical camera sensor, and the FOV for these outputs
     * must not be cropped.
     *
     * This is only required for backward compatibility of the Camera2 API when running in LEGACY
     * mode.
     *
     * @see {@link android.hardware.camera2.CameraCharacteristics#INFO_SUPPORTED_HARDWARE_LEVEL}
     */
    public void testMaxAspectRatios() throws Exception {
        for (int id = 0; id < Camera.getNumberOfCameras(); ++id) {
            if (CameraUtils.isLegacyHAL(getContext(), id)) {

                Camera camera = Camera.open(id);
                Parameters parameters = camera.getParameters();

                List<Camera.Size> supportedJpegDimens = parameters.getSupportedPictureSizes();
                List<Camera.Size> supportedPreviewDimens = parameters.getSupportedPreviewSizes();

                Collections.sort(supportedJpegDimens, new CameraUtils.LegacySizeComparator());
                Collections.sort(supportedPreviewDimens, new CameraUtils.LegacySizeComparator());

                Camera.Size largestJpegDimen =
                        supportedJpegDimens.get(supportedJpegDimens.size() - 1);
                Camera.Size largestPreviewDimen =
                        supportedPreviewDimens.get(supportedPreviewDimens.size() - 1);

                float jpegAspect = largestJpegDimen.width / (float) largestJpegDimen.height;
                float previewAspect =
                        largestPreviewDimen.width / (float) largestPreviewDimen.height;

                if (Math.abs(jpegAspect - previewAspect) >= ASPECT_RATIO_TOLERANCE) {
                    Log.w(TAG,
                            "Largest preview dimension (w=" + largestPreviewDimen.width + ", h=" +
                            largestPreviewDimen.height + ") should have the same aspect ratio " +
                            "as the largest Jpeg dimension (w=" + largestJpegDimen.width +
                            ", h=" + largestJpegDimen.height + ")");
                }


                camera.release();
            }
        }
    }

    private void checkSize(Parameters parameters, int width, int height) {
        parameters.setPictureSize(width, height);
        assertEquals(width, parameters.getPictureSize().width);
        assertEquals(height, parameters.getPictureSize().height);
    }

    private static void addTestToSuite(TestSuite testSuite, String testName) {
        Camera_SizeTest test = new Camera_SizeTest();
        test.setName(testName);
        testSuite.addTest(test);
    }
}
