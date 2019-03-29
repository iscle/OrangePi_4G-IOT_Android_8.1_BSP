/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.hardware.camera2.cts.helpers.AssertHelpers.assertArrayContains;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.ImageReader;
import android.util.Pair;
import android.util.Size;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import android.hardware.camera2.cts.helpers.Camera2Focuser;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.Image;
import android.os.ConditionVariable;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StillCaptureTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "StillCaptureTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    // 60 second to accommodate the possible long exposure time.
    private static final int RELAXED_CAPTURE_IMAGE_TIMEOUT_MS = CAPTURE_IMAGE_TIMEOUT_MS + 1000;
    private static final int MAX_REGIONS_AE_INDEX = 0;
    private static final int MAX_REGIONS_AWB_INDEX = 1;
    private static final int MAX_REGIONS_AF_INDEX = 2;
    private static final int WAIT_FOR_FOCUS_DONE_TIMEOUT_MS = 6000;
    private static final double AE_COMPENSATION_ERROR_TOLERANCE = 0.2;
    private static final int NUM_FRAMES_WAITED = 30;
    // 5 percent error margin for resulting metering regions
    private static final float METERING_REGION_ERROR_PERCENT_DELTA = 0.05f;
    // Android CDD (5.0 and newer) required number of simultenous bitmap allocations for camera
    private static final int MAX_ALLOCATED_BITMAPS = 3;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test JPEG capture exif fields for each camera.
     */
    public void testJpegExif() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing JPEG exif for Camera " + mCameraIds[i]);
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                jpegExifTestByCamera();
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test normal still capture sequence.
     * <p>
     * Preview and jpeg output streams are configured. Max still capture
     * size is used for jpeg capture. The sequence of still capture being test
     * is: start preview, auto focus, precapture metering (if AE is not
     * converged), then capture jpeg. The AWB and AE are in auto modes. AF mode
     * is CONTINUOUS_PICTURE.
     * </p>
     */
    public void testTakePicture() throws Exception{
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing basic take picture for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                takePictureTestByCamera(/*aeRegions*/null, /*awbRegions*/null, /*afRegions*/null);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test ZSL still capture sequence.
     * <p>
     * Preview and jpeg output streams are configured. Max still capture
     * size is used for jpeg capture. The sequence of still capture being test
     * is: start preview, auto focus, precapture metering (if AE is not
     * converged), then capture jpeg. The AWB and AE are in auto modes. AF mode
     * is CONTINUOUS_PICTURE. Same as testTakePicture, but with enableZSL set.
     * </p>
     */
    public void testTakePictureZsl() throws Exception{
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing basic ZSL take picture for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                CaptureRequest.Builder stillRequest =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillRequest.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
                takePictureTestByCamera(/*aeRegions*/null, /*awbRegions*/null, /*afRegions*/null,
                        /*addAeTriggerCancel*/false, /*allocateBitmap*/false,
                        /*previewRequest*/null, stillRequest);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test basic Raw capture. Raw buffer avaiablility is checked, but raw buffer data is not.
     */
    public void testBasicRawCapture()  throws Exception {
       for (int i = 0; i < mCameraIds.length; i++) {
           try {
               Log.i(TAG, "Testing raw capture for Camera " + mCameraIds[i]);
               openDevice(mCameraIds[i]);

               if (!mStaticInfo.isCapabilitySupported(
                       CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                   Log.i(TAG, "RAW capability is not supported in camera " + mCameraIds[i] +
                           ". Skip the test.");
                   continue;
               }

               rawCaptureTestByCamera(/*stillRequest*/null);
           } finally {
               closeDevice();
               closeImageReader();
           }
       }
    }

    /**
     * Test basic Raw ZSL capture. Raw buffer avaiablility is checked, but raw buffer data is not.
     */
    public void testBasicRawZslCapture()  throws Exception {
       for (int i = 0; i < mCameraIds.length; i++) {
           try {
               Log.i(TAG, "Testing raw ZSL capture for Camera " + mCameraIds[i]);
               openDevice(mCameraIds[i]);

               if (!mStaticInfo.isCapabilitySupported(
                       CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                   Log.i(TAG, "RAW capability is not supported in camera " + mCameraIds[i] +
                           ". Skip the test.");
                   continue;
               }
               CaptureRequest.Builder stillRequest =
                       mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
               stillRequest.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
               rawCaptureTestByCamera(stillRequest);
           } finally {
               closeDevice();
               closeImageReader();
           }
       }
    }


    /**
     * Test the full raw capture use case.
     *
     * This includes:
     * - Configuring the camera with a preview, jpeg, and raw output stream.
     * - Running preview until AE/AF can settle.
     * - Capturing with a request targeting all three output streams.
     */
    public void testFullRawCapture() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing raw+JPEG capture for Camera " + mCameraIds[i]);
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    Log.i(TAG, "RAW capability is not supported in camera " + mCameraIds[i] +
                            ". Skip the test.");
                    continue;
                }

                fullRawCaptureTestByCamera(/*stillRequest*/null);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test the full raw capture ZSL use case.
     *
     * This includes:
     * - Configuring the camera with a preview, jpeg, and raw output stream.
     * - Running preview until AE/AF can settle.
     * - Capturing with a request targeting all three output streams.
     */
    public void testFullRawZSLCapture() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing raw+JPEG ZSL capture for Camera " + mCameraIds[i]);
                openDevice(mCameraIds[i]);
                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)) {
                    Log.i(TAG, "RAW capability is not supported in camera " + mCameraIds[i] +
                            ". Skip the test.");
                    continue;
                }
                CaptureRequest.Builder stillRequest =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                stillRequest.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
                fullRawCaptureTestByCamera(stillRequest);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test touch for focus.
     * <p>
     * AF is in CAF mode when preview is started, test uses several pre-selected
     * regions to simulate touches. Active scan is triggered to make sure the AF
     * converges in reasonable time.
     * </p>
     */
    public void testTouchForFocus() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing touch for focus for Camera " + id);
                openDevice(id);
                int maxAfRegions = mStaticInfo.getAfMaxRegionsChecked();
                if (!(mStaticInfo.hasFocuser() && maxAfRegions > 0)) {
                    continue;
                }
                // TODO: Relax test to use non-SurfaceView output for depth cases
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                touchForFocusTestByCamera();
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test all combination of available preview sizes and still sizes.
     * <p>
     * For each still capture, Only the jpeg buffer is validated, capture
     * result validation is covered by {@link #jpegExifTestByCamera} test.
     * </p>
     */
    public void testStillPreviewCombination() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Still preview capture combination for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                previewStillCombinationTestByCamera();
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test AE compensation.
     * <p>
     * For each integer EV compensation setting: retrieve the exposure value (exposure time *
     * sensitivity) with or without compensation, verify if the exposure value is legal (conformed
     * to what static info has) and the ratio between two exposure values matches EV compensation
     * setting. Also test for the behavior that exposure settings should be changed when AE
     * compensation settings is changed, even when AE lock is ON.
     * </p>
     */
    public void testAeCompensation() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing AE compensation for Camera " + id);
                openDevice(id);

                if (mStaticInfo.isHardwareLevelLegacy()) {
                    Log.i(TAG, "Skipping test on legacy devices");
                    continue;
                }
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                aeCompensationTestByCamera();
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test Ae region for still capture.
     */
    public void testAeRegions() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing AE regions for Camera " + id);
                openDevice(id);

                boolean aeRegionsSupported = isRegionsSupportedFor3A(MAX_REGIONS_AE_INDEX);
                if (!aeRegionsSupported) {
                    continue;
                }

                ArrayList<MeteringRectangle[]> aeRegionTestCases = get3ARegionTestCasesForCamera();
                for (MeteringRectangle[] aeRegions : aeRegionTestCases) {
                    takePictureTestByCamera(aeRegions, /*awbRegions*/null, /*afRegions*/null);
                }
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test AWB region for still capture.
     */
    public void testAwbRegions() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing AE regions for Camera " + id);
                openDevice(id);

                boolean awbRegionsSupported = isRegionsSupportedFor3A(MAX_REGIONS_AWB_INDEX);
                if (!awbRegionsSupported) {
                    continue;
                }

                ArrayList<MeteringRectangle[]> awbRegionTestCases = get3ARegionTestCasesForCamera();
                for (MeteringRectangle[] awbRegions : awbRegionTestCases) {
                    takePictureTestByCamera(/*aeRegions*/null, awbRegions, /*afRegions*/null);
                }
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test Af region for still capture.
     */
    public void testAfRegions() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing AF regions for Camera " + id);
                openDevice(id);

                boolean afRegionsSupported = isRegionsSupportedFor3A(MAX_REGIONS_AF_INDEX);
                if (!afRegionsSupported) {
                    continue;
                }

                ArrayList<MeteringRectangle[]> afRegionTestCases = get3ARegionTestCasesForCamera();
                for (MeteringRectangle[] afRegions : afRegionTestCases) {
                    takePictureTestByCamera(/*aeRegions*/null, /*awbRegions*/null, afRegions);
                }
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test preview is still running after a still request
     */
    public void testPreviewPersistence() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing preview persistence for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                previewPersistenceTestByCamera();
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    public void testAePrecaptureTriggerCancelJpegCapture() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing AE precapture cancel for jpeg capture for Camera " + id);
                openDevice(id);

                // Legacy device doesn't support AE precapture trigger
                if (mStaticInfo.isHardwareLevelLegacy()) {
                    Log.i(TAG, "Skipping AE precapture trigger cancel test on legacy devices");
                    continue;
                }
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                takePictureTestByCamera(/*aeRegions*/null, /*awbRegions*/null, /*afRegions*/null,
                        /*addAeTriggerCancel*/true, /*allocateBitmap*/false,
                        /*previewRequest*/null, /*stillRequest*/null);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }
    }

    /**
     * Test allocate some bitmaps while taking picture.
     * <p>
     * Per android CDD (5.0 and newer), android devices should support allocation of at least 3
     * bitmaps equal to the size of the images produced by the largest resolution camera sensor on
     * the devices.
     * </p>
     */
    public void testAllocateBitmap() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing bitmap allocations for Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                takePictureTestByCamera(/*aeRegions*/null, /*awbRegions*/null, /*afRegions*/null,
                        /*addAeTriggerCancel*/false, /*allocateBitmap*/true,
                        /*previewRequest*/null, /*stillRequest*/null);
            } finally {
                closeDevice();
                closeImageReader();
            }
        }

    }

    /**
     * Start preview,take a picture and test preview is still running after snapshot
     */
    private void previewPersistenceTestByCamera() throws Exception {
        Size maxStillSz = mOrderedStillSizes.get(0);
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleCaptureCallback stillResultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder stillRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        prepareStillCaptureAndStartPreview(previewRequest, stillRequest, maxPreviewSz,
                maxStillSz, resultListener, imageListener);

        // make sure preview is actually running
        waitForNumResults(resultListener, NUM_FRAMES_WAITED);

        // take a picture
        CaptureRequest request = stillRequest.build();
        mSession.capture(request, stillResultListener, mHandler);
        stillResultListener.getCaptureResultForRequest(request,
                WAIT_FOR_RESULT_TIMEOUT_MS);

        // validate image
        Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
        validateJpegCapture(image, maxStillSz);

        // make sure preview is still running after still capture
        waitForNumResults(resultListener, NUM_FRAMES_WAITED);

        stopPreview();

        // Free image resources
        image.close();
        closeImageReader();
        return;
    }

    /**
     * Take a picture for a given set of 3A regions for a particular camera.
     * <p>
     * Before take a still capture, it triggers an auto focus and lock it first,
     * then wait for AWB to converge and lock it, then trigger a precapture
     * metering sequence and wait for AE converged. After capture is received, the
     * capture result and image are validated.
     * </p>
     *
     * @param aeRegions AE regions for this capture
     * @param awbRegions AWB regions for this capture
     * @param afRegions AF regions for this capture
     */
    private void takePictureTestByCamera(
            MeteringRectangle[] aeRegions, MeteringRectangle[] awbRegions,
            MeteringRectangle[] afRegions) throws Exception {
        takePictureTestByCamera(aeRegions, awbRegions, afRegions,
                /*addAeTriggerCancel*/false, /*allocateBitmap*/false,
                /*previewRequest*/null, /*stillRequest*/null);
    }

    /**
     * Take a picture for a given set of 3A regions for a particular camera.
     * <p>
     * Before take a still capture, it triggers an auto focus and lock it first,
     * then wait for AWB to converge and lock it, then trigger a precapture
     * metering sequence and wait for AE converged. After capture is received, the
     * capture result and image are validated. If {@code addAeTriggerCancel} is true,
     * a precapture trigger cancel will be inserted between two adjacent triggers, which
     * should effective cancel the first trigger.
     * </p>
     *
     * @param aeRegions AE regions for this capture
     * @param awbRegions AWB regions for this capture
     * @param afRegions AF regions for this capture
     * @param addAeTriggerCancel If a AE precapture trigger cancel is sent after the trigger.
     * @param allocateBitmap If a set of bitmaps are allocated during the test for memory test.
     * @param previewRequest The preview request builder to use, or null to use the default
     * @param stillRequest The still capture request to use, or null to use the default
     */
    private void takePictureTestByCamera(
            MeteringRectangle[] aeRegions, MeteringRectangle[] awbRegions,
            MeteringRectangle[] afRegions, boolean addAeTriggerCancel, boolean allocateBitmap,
            CaptureRequest.Builder previewRequest, CaptureRequest.Builder stillRequest)
                    throws Exception {

        boolean hasFocuser = mStaticInfo.hasFocuser();

        Size maxStillSz = mOrderedStillSizes.get(0);
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        CaptureResult result;
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
        if (previewRequest == null) {
            previewRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }
        if (stillRequest == null) {
            stillRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        }
        prepareStillCaptureAndStartPreview(previewRequest, stillRequest, maxPreviewSz,
                maxStillSz, resultListener, imageListener);

        // Set AE mode to ON_AUTO_FLASH if flash is available.
        if (mStaticInfo.hasFlash()) {
            previewRequest.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            stillRequest.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }

        Camera2Focuser focuser = null;
        /**
         * Step 1: trigger an auto focus run, and wait for AF locked.
         */
        boolean canSetAfRegion = hasFocuser && (afRegions != null) &&
                isRegionsSupportedFor3A(MAX_REGIONS_AF_INDEX);
        if (hasFocuser) {
            SimpleAutoFocusListener afListener = new SimpleAutoFocusListener();
            focuser = new Camera2Focuser(mCamera, mSession, mPreviewSurface, afListener,
                    mStaticInfo.getCharacteristics(), mHandler);
            if (canSetAfRegion) {
                previewRequest.set(CaptureRequest.CONTROL_AF_REGIONS, afRegions);
                stillRequest.set(CaptureRequest.CONTROL_AF_REGIONS, afRegions);
            }
            focuser.startAutoFocus(afRegions);
            afListener.waitForAutoFocusDone(WAIT_FOR_FOCUS_DONE_TIMEOUT_MS);
        }

        /**
         * Have to get the current AF mode to be used for other 3A repeating
         * request, otherwise, the new AF mode in AE/AWB request could be
         * different with existing repeating requests being sent by focuser,
         * then it could make AF unlocked too early. Beside that, for still
         * capture, AF mode must not be different with the one in current
         * repeating request, otherwise, the still capture itself would trigger
         * an AF mode change, and the AF lock would be lost for this capture.
         */
        int currentAfMode = CaptureRequest.CONTROL_AF_MODE_OFF;
        if (hasFocuser) {
            currentAfMode = focuser.getCurrentAfMode();
        }
        previewRequest.set(CaptureRequest.CONTROL_AF_MODE, currentAfMode);
        stillRequest.set(CaptureRequest.CONTROL_AF_MODE, currentAfMode);

        /**
         * Step 2: AF is already locked, wait for AWB converged, then lock it.
         */
        resultListener = new SimpleCaptureCallback();
        boolean canSetAwbRegion =
                (awbRegions != null) && isRegionsSupportedFor3A(MAX_REGIONS_AWB_INDEX);
        if (canSetAwbRegion) {
            previewRequest.set(CaptureRequest.CONTROL_AWB_REGIONS, awbRegions);
            stillRequest.set(CaptureRequest.CONTROL_AWB_REGIONS, awbRegions);
        }
        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
        if (mStaticInfo.isHardwareLevelAtLeastLimited()) {
            waitForResultValue(resultListener, CaptureResult.CONTROL_AWB_STATE,
                    CaptureResult.CONTROL_AWB_STATE_CONVERGED, NUM_RESULTS_WAIT_TIMEOUT);
        } else {
            // LEGACY Devices don't have the AWB_STATE reported in results, so just wait
            waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
        }
        boolean canSetAwbLock = mStaticInfo.isAwbLockSupported();
        if (canSetAwbLock) {
            previewRequest.set(CaptureRequest.CONTROL_AWB_LOCK, true);
        }
        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
        // Validate the next result immediately for region and mode.
        result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        mCollector.expectEquals("AWB mode in result and request should be same",
                previewRequest.get(CaptureRequest.CONTROL_AWB_MODE),
                result.get(CaptureResult.CONTROL_AWB_MODE));
        if (canSetAwbRegion) {
            MeteringRectangle[] resultAwbRegions =
                    getValueNotNull(result, CaptureResult.CONTROL_AWB_REGIONS);
            mCollector.expectEquals("AWB regions in result and request should be same",
                    awbRegions, resultAwbRegions);
        }

        /**
         * Step 3: trigger an AE precapture metering sequence and wait for AE converged.
         */
        resultListener = new SimpleCaptureCallback();
        boolean canSetAeRegion =
                (aeRegions != null) && isRegionsSupportedFor3A(MAX_REGIONS_AE_INDEX);
        if (canSetAeRegion) {
            previewRequest.set(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
            stillRequest.set(CaptureRequest.CONTROL_AE_REGIONS, aeRegions);
        }
        mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
        previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
        mSession.capture(previewRequest.build(), resultListener, mHandler);
        if (addAeTriggerCancel) {
            // Cancel the current precapture trigger, then send another trigger.
            // The camera device should behave as if the first trigger is not sent.
            // Wait one request to make the trigger start doing something before cancel.
            waitForNumResults(resultListener, /*numResultsWait*/ 1);
            previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL);
            mSession.capture(previewRequest.build(), resultListener, mHandler);
            waitForResultValue(resultListener, CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL,
                    NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            // Issue another trigger
            previewRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mSession.capture(previewRequest.build(), resultListener, mHandler);
        }
        waitForAeStable(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);

        // Validate the next result immediately for region and mode.
        result = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        mCollector.expectEquals("AE mode in result and request should be same",
                previewRequest.get(CaptureRequest.CONTROL_AE_MODE),
                result.get(CaptureResult.CONTROL_AE_MODE));
        if (canSetAeRegion) {
            MeteringRectangle[] resultAeRegions =
                    getValueNotNull(result, CaptureResult.CONTROL_AE_REGIONS);

            mCollector.expectMeteringRegionsAreSimilar(
                    "AE regions in result and request should be similar",
                    aeRegions,
                    resultAeRegions,
                    METERING_REGION_ERROR_PERCENT_DELTA);
        }

        /**
         * Step 4: take a picture when all 3A are in good state.
         */
        resultListener = new SimpleCaptureCallback();
        CaptureRequest request = stillRequest.build();
        mSession.capture(request, resultListener, mHandler);
        // Validate the next result immediately for region and mode.
        result = resultListener.getCaptureResultForRequest(request, WAIT_FOR_RESULT_TIMEOUT_MS);
        mCollector.expectEquals("AF mode in result and request should be same",
                stillRequest.get(CaptureRequest.CONTROL_AF_MODE),
                result.get(CaptureResult.CONTROL_AF_MODE));
        if (canSetAfRegion) {
            MeteringRectangle[] resultAfRegions =
                    getValueNotNull(result, CaptureResult.CONTROL_AF_REGIONS);
            mCollector.expectMeteringRegionsAreSimilar(
                    "AF regions in result and request should be similar",
                    afRegions,
                    resultAfRegions,
                    METERING_REGION_ERROR_PERCENT_DELTA);
        }

        if (hasFocuser) {
            // Unlock auto focus.
            focuser.cancelAutoFocus();
        }

        // validate image
        Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
        validateJpegCapture(image, maxStillSz);
        // Test if the system can allocate 3 bitmap successfully, per android CDD camera memory
        // requirements added by CDD 5.0
        if (allocateBitmap) {
            Bitmap bm[] = new Bitmap[MAX_ALLOCATED_BITMAPS];
            for (int i = 0; i < MAX_ALLOCATED_BITMAPS; i++) {
                bm[i] = Bitmap.createBitmap(
                        maxStillSz.getWidth(), maxStillSz.getHeight(), Config.ARGB_8888);
                assertNotNull("Created bitmap #" + i + " shouldn't be null", bm[i]);
            }
        }

        // Free image resources
        image.close();

        stopPreview();
    }

    /**
     * Test touch region for focus by camera.
     */
    private void touchForFocusTestByCamera() throws Exception {
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        CaptureRequest.Builder requestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        startPreview(requestBuilder, maxPreviewSz, listener);

        SimpleAutoFocusListener afListener = new SimpleAutoFocusListener();
        Camera2Focuser focuser = new Camera2Focuser(mCamera, mSession, mPreviewSurface, afListener,
                mStaticInfo.getCharacteristics(), mHandler);
        ArrayList<MeteringRectangle[]> testAfRegions = get3ARegionTestCasesForCamera();

        for (MeteringRectangle[] afRegions : testAfRegions) {
            focuser.touchForAutoFocus(afRegions);
            afListener.waitForAutoFocusDone(WAIT_FOR_FOCUS_DONE_TIMEOUT_MS);
            focuser.cancelAutoFocus();
        }
    }

    private void previewStillCombinationTestByCamera() throws Exception {
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();

        for (Size stillSz : mOrderedStillSizes)
            for (Size previewSz : mOrderedPreviewSizes) {
                if (VERBOSE) {
                    Log.v(TAG, "Testing JPEG capture size " + stillSz.toString()
                            + " with preview size " + previewSz.toString() + " for camera "
                            + mCamera.getId());
                }
                CaptureRequest.Builder previewRequest =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                CaptureRequest.Builder stillRequest =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                prepareStillCaptureAndStartPreview(previewRequest, stillRequest, previewSz,
                        stillSz, resultListener, imageListener);
                mSession.capture(stillRequest.build(), resultListener, mHandler);
                Image image = imageListener.getImage((mStaticInfo.isHardwareLevelLegacy()) ?
                        RELAXED_CAPTURE_IMAGE_TIMEOUT_MS : CAPTURE_IMAGE_TIMEOUT_MS);
                validateJpegCapture(image, stillSz);

                // Free image resources
                image.close();

                // stopPreview must be called here to make sure next time a preview stream
                // is created with new size.
                stopPreview();
            }
    }

    /**
     * Basic raw capture test for each camera.
     */
    private void rawCaptureTestByCamera(CaptureRequest.Builder stillRequest) throws Exception {
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        Size size = mStaticInfo.getRawDimensChecked();

        // Prepare raw capture and start preview.
        CaptureRequest.Builder previewBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder rawBuilder = (stillRequest != null) ? stillRequest :
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
        prepareRawCaptureAndStartPreview(previewBuilder, rawBuilder, maxPreviewSz, size,
                resultListener, imageListener);

        if (VERBOSE) {
            Log.v(TAG, "Testing Raw capture with size " + size.toString()
                    + ", preview size " + maxPreviewSz);
        }

        CaptureRequest rawRequest = rawBuilder.build();
        mSession.capture(rawRequest, resultListener, mHandler);

        Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
        validateRaw16Image(image, size);
        if (DEBUG) {
            byte[] rawBuffer = getDataFromImage(image);
            String rawFileName = DEBUG_FILE_NAME_BASE + "/test" + "_" + size.toString() + "_cam" +
                    mCamera.getId() + ".raw16";
            Log.d(TAG, "Dump raw file into " + rawFileName);
            dumpFile(rawFileName, rawBuffer);
        }

        // Free image resources
        image.close();

        stopPreview();
    }

    private void fullRawCaptureTestByCamera(CaptureRequest.Builder stillRequest) throws Exception {
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        Size maxStillSz = mOrderedStillSizes.get(0);

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener jpegListener = new SimpleImageReaderListener();
        SimpleImageReaderListener rawListener = new SimpleImageReaderListener();

        Size size = mStaticInfo.getRawDimensChecked();

        if (VERBOSE) {
            Log.v(TAG, "Testing multi capture with size " + size.toString()
                    + ", preview size " + maxPreviewSz);
        }

        // Prepare raw capture and start preview.
        CaptureRequest.Builder previewBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder multiBuilder = (stillRequest != null) ? stillRequest :
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

        ImageReader rawReader = null;
        ImageReader jpegReader = null;

        try {
            // Create ImageReaders.
            rawReader = makeImageReader(size,
                    ImageFormat.RAW_SENSOR, MAX_READER_IMAGES, rawListener, mHandler);
            jpegReader = makeImageReader(maxStillSz,
                    ImageFormat.JPEG, MAX_READER_IMAGES, jpegListener, mHandler);
            updatePreviewSurface(maxPreviewSz);

            // Configure output streams with preview and jpeg streams.
            List<Surface> outputSurfaces = new ArrayList<Surface>();
            outputSurfaces.add(rawReader.getSurface());
            outputSurfaces.add(jpegReader.getSurface());
            outputSurfaces.add(mPreviewSurface);
            mSessionListener = new BlockingSessionCallback();
            mSession = configureCameraSession(mCamera, outputSurfaces,
                    mSessionListener, mHandler);

            // Configure the requests.
            previewBuilder.addTarget(mPreviewSurface);
            multiBuilder.addTarget(mPreviewSurface);
            multiBuilder.addTarget(rawReader.getSurface());
            multiBuilder.addTarget(jpegReader.getSurface());

            // Start preview.
            mSession.setRepeatingRequest(previewBuilder.build(), null, mHandler);

            // Poor man's 3A, wait 2 seconds for AE/AF (if any) to settle.
            // TODO: Do proper 3A trigger and lock (see testTakePictureTest).
            Thread.sleep(3000);

            multiBuilder.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,
                    CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON);
            CaptureRequest multiRequest = multiBuilder.build();

            mSession.capture(multiRequest, resultListener, mHandler);

            CaptureResult result = resultListener.getCaptureResultForRequest(multiRequest,
                    NUM_RESULTS_WAIT_TIMEOUT);
            Image jpegImage = jpegListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            basicValidateJpegImage(jpegImage, maxStillSz);
            Image rawImage = rawListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            validateRaw16Image(rawImage, size);
            verifyRawCaptureResult(multiRequest, result);


            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (DngCreator dngCreator = new DngCreator(mStaticInfo.getCharacteristics(), result)) {
                dngCreator.writeImage(outputStream, rawImage);
            }

            if (DEBUG) {
                byte[] rawBuffer = outputStream.toByteArray();
                String rawFileName = DEBUG_FILE_NAME_BASE + "/raw16_" + TAG + size.toString() +
                        "_cam_" + mCamera.getId() + ".dng";
                Log.d(TAG, "Dump raw file into " + rawFileName);
                dumpFile(rawFileName, rawBuffer);

                byte[] jpegBuffer = getDataFromImage(jpegImage);
                String jpegFileName = DEBUG_FILE_NAME_BASE + "/jpeg_" + TAG + size.toString() +
                        "_cam_" + mCamera.getId() + ".jpg";
                Log.d(TAG, "Dump jpeg file into " + rawFileName);
                dumpFile(jpegFileName, jpegBuffer);
            }

            stopPreview();
        } finally {
            CameraTestUtils.closeImageReader(rawReader);
            CameraTestUtils.closeImageReader(jpegReader);
            rawReader = null;
            jpegReader = null;
        }
    }

    /**
     * Validate that raw {@link CaptureResult}.
     *
     * @param rawRequest a {@link CaptureRequest} use to capture a RAW16 image.
     * @param rawResult the {@link CaptureResult} corresponding to the given request.
     */
    private void verifyRawCaptureResult(CaptureRequest rawRequest, CaptureResult rawResult) {
        assertNotNull(rawRequest);
        assertNotNull(rawResult);

        Rational[] empty = new Rational[] { Rational.ZERO, Rational.ZERO, Rational.ZERO};
        Rational[] neutralColorPoint = mCollector.expectKeyValueNotNull("NeutralColorPoint",
                rawResult, CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        if (neutralColorPoint != null) {
            mCollector.expectEquals("NeutralColorPoint length", empty.length,
                    neutralColorPoint.length);
            mCollector.expectNotEquals("NeutralColorPoint cannot be all zeroes, ", empty,
                    neutralColorPoint);
            mCollector.expectValuesGreaterOrEqual("NeutralColorPoint", neutralColorPoint,
                    Rational.ZERO);
        }

        mCollector.expectKeyValueGreaterOrEqual(rawResult, CaptureResult.SENSOR_GREEN_SPLIT, 0.0f);

        Pair<Double, Double>[] noiseProfile = mCollector.expectKeyValueNotNull("NoiseProfile",
                rawResult, CaptureResult.SENSOR_NOISE_PROFILE);
        if (noiseProfile != null) {
            mCollector.expectEquals("NoiseProfile length", noiseProfile.length,
                /*Num CFA channels*/4);
            for (Pair<Double, Double> p : noiseProfile) {
                mCollector.expectTrue("NoiseProfile coefficients " + p +
                        " must have: S > 0, O >= 0", p.first > 0 && p.second >= 0);
            }
        }

        Integer hotPixelMode = mCollector.expectKeyValueNotNull("HotPixelMode", rawResult,
                CaptureResult.HOT_PIXEL_MODE);
        Boolean hotPixelMapMode = mCollector.expectKeyValueNotNull("HotPixelMapMode", rawResult,
                CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE);
        Point[] hotPixelMap = rawResult.get(CaptureResult.STATISTICS_HOT_PIXEL_MAP);

        Size pixelArraySize = mStaticInfo.getPixelArraySizeChecked();
        boolean[] availableHotPixelMapModes = mStaticInfo.getValueFromKeyNonNull(
                        CameraCharacteristics.STATISTICS_INFO_AVAILABLE_HOT_PIXEL_MAP_MODES);

        if (hotPixelMode != null) {
            Integer requestMode = mCollector.expectKeyValueNotNull(rawRequest,
                    CaptureRequest.HOT_PIXEL_MODE);
            if (requestMode != null) {
                mCollector.expectKeyValueEquals(rawResult, CaptureResult.HOT_PIXEL_MODE,
                        requestMode);
            }
        }

        if (hotPixelMapMode != null) {
            Boolean requestMapMode = mCollector.expectKeyValueNotNull(rawRequest,
                    CaptureRequest.STATISTICS_HOT_PIXEL_MAP_MODE);
            if (requestMapMode != null) {
                mCollector.expectKeyValueEquals(rawResult,
                        CaptureResult.STATISTICS_HOT_PIXEL_MAP_MODE, requestMapMode);
            }

            if (!hotPixelMapMode) {
                mCollector.expectTrue("HotPixelMap must be empty", hotPixelMap == null ||
                        hotPixelMap.length == 0);
            } else {
                mCollector.expectTrue("HotPixelMap must not be empty", hotPixelMap != null);
                mCollector.expectNotNull("AvailableHotPixelMapModes must not be null",
                        availableHotPixelMapModes);
                if (availableHotPixelMapModes != null) {
                    mCollector.expectContains("HotPixelMapMode", availableHotPixelMapModes, true);
                }

                int height = pixelArraySize.getHeight();
                int width = pixelArraySize.getWidth();
                for (Point p : hotPixelMap) {
                    mCollector.expectTrue("Hotpixel " + p + " must be in pixelArray " +
                            pixelArraySize, p.x >= 0 && p.x < width && p.y >= 0 && p.y < height);
                }
            }
        }
        // TODO: profileHueSatMap, and profileToneCurve aren't supported yet.

    }

    /**
     * Issue a Jpeg capture and validate the exif information.
     * <p>
     * TODO: Differentiate full and limited device, some of the checks rely on
     * per frame control and synchronization, most of them don't.
     * </p>
     */
    private void jpegExifTestByCamera() throws Exception {
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        Size maxStillSz = mOrderedStillSizes.get(0);
        if (VERBOSE) {
            Log.v(TAG, "Testing JPEG exif with jpeg size " + maxStillSz.toString()
                    + ", preview size " + maxPreviewSz);
        }

        // prepare capture and start preview.
        CaptureRequest.Builder previewBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder stillBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
        prepareStillCaptureAndStartPreview(previewBuilder, stillBuilder, maxPreviewSz, maxStillSz,
                resultListener, imageListener);

        // Set the jpeg keys, then issue a capture
        Size[] thumbnailSizes = mStaticInfo.getAvailableThumbnailSizesChecked();
        Size maxThumbnailSize = thumbnailSizes[thumbnailSizes.length - 1];
        Size[] testThumbnailSizes = new Size[EXIF_TEST_DATA.length];
        Arrays.fill(testThumbnailSizes, maxThumbnailSize);
        // Make sure thumbnail size (0, 0) is covered.
        testThumbnailSizes[0] = new Size(0, 0);

        for (int i = 0; i < EXIF_TEST_DATA.length; i++) {
            setJpegKeys(stillBuilder, EXIF_TEST_DATA[i], testThumbnailSizes[i], mCollector);

            // Capture a jpeg image.
            CaptureRequest request = stillBuilder.build();
            mSession.capture(request, resultListener, mHandler);
            CaptureResult stillResult =
                    resultListener.getCaptureResultForRequest(request, NUM_RESULTS_WAIT_TIMEOUT);
            Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);

            verifyJpegKeys(image, stillResult, maxStillSz, testThumbnailSizes[i], EXIF_TEST_DATA[i],
                    mStaticInfo, mCollector);

            // Free image resources
            image.close();
        }
    }

    private void aeCompensationTestByCamera() throws Exception {
        Range<Integer> compensationRange = mStaticInfo.getAeCompensationRangeChecked();
        // Skip the test if exposure compensation is not supported.
        if (compensationRange.equals(Range.create(0, 0))) {
            return;
        }

        Rational step = mStaticInfo.getAeCompensationStepChecked();
        float stepF = (float) step.getNumerator() / step.getDenominator();
        int stepsPerEv = (int) Math.round(1.0 / stepF);
        int numSteps = (compensationRange.getUpper() - compensationRange.getLower()) / stepsPerEv;

        Size maxStillSz = mOrderedStillSizes.get(0);
        Size maxPreviewSz = mOrderedPreviewSizes.get(0);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder stillRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        boolean canSetAeLock = mStaticInfo.isAeLockSupported();
        boolean canReadSensorSettings = mStaticInfo.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);

        if (canSetAeLock) {
            stillRequest.set(CaptureRequest.CONTROL_AE_LOCK, true);
        }

        CaptureResult normalResult;
        CaptureResult compensatedResult;

        boolean canReadExposureValueRange = mStaticInfo.areKeysAvailable(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE,
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        boolean canVerifyExposureValue = canReadSensorSettings && canReadExposureValueRange;
        long minExposureValue = -1;
        long maxExposureValuePreview = -1;
        long maxExposureValueStill = -1;
        if (canReadExposureValueRange) {
            // Minimum exposure settings is mostly static while maximum exposure setting depends on
            // frame rate range which in term depends on capture request.
            minExposureValue = mStaticInfo.getSensitivityMinimumOrDefault() *
                    mStaticInfo.getExposureMinimumOrDefault() / 1000;
            long maxSensitivity = mStaticInfo.getSensitivityMaximumOrDefault();
            long maxExposureTimeUs = mStaticInfo.getExposureMaximumOrDefault() / 1000;
            maxExposureValuePreview = getMaxExposureValue(previewRequest, maxExposureTimeUs,
                    maxSensitivity);
            maxExposureValueStill = getMaxExposureValue(stillRequest, maxExposureTimeUs,
                    maxSensitivity);
        }

        // Set the max number of images to be same as the burst count, as the verification
        // could be much slower than producing rate, and we don't want to starve producer.
        prepareStillCaptureAndStartPreview(previewRequest, stillRequest, maxPreviewSz,
                maxStillSz, resultListener, numSteps, imageListener);

        for (int i = 0; i <= numSteps; i++) {
            int exposureCompensation = i * stepsPerEv + compensationRange.getLower();
            double expectedRatio = Math.pow(2.0, exposureCompensation / stepsPerEv);

            // Wait for AE to be stabilized before capture: CONVERGED or FLASH_REQUIRED.
            waitForAeStable(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            normalResult = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);

            long normalExposureValue = -1;
            if (canVerifyExposureValue) {
                // get and check if current exposure value is valid
                normalExposureValue = getExposureValue(normalResult);
                mCollector.expectInRange("Exposure setting out of bound", normalExposureValue,
                        minExposureValue, maxExposureValuePreview);

                // Only run the test if expectedExposureValue is within valid range
                long expectedExposureValue = (long) (normalExposureValue * expectedRatio);
                if (expectedExposureValue < minExposureValue ||
                    expectedExposureValue > maxExposureValueStill) {
                    continue;
                }
                Log.v(TAG, "Expect ratio: " + expectedRatio +
                        " normalExposureValue: " + normalExposureValue +
                        " expectedExposureValue: " + expectedExposureValue +
                        " minExposureValue: " + minExposureValue +
                        " maxExposureValuePreview: " + maxExposureValuePreview +
                        " maxExposureValueStill: " + maxExposureValueStill);
            }

            // Now issue exposure compensation and wait for AE locked. AE could take a few
            // frames to go back to locked state
            previewRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                    exposureCompensation);
            if (canSetAeLock) {
                previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, true);
            }
            mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
            if (canSetAeLock) {
                waitForAeLocked(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            } else {
                waitForSettingsApplied(resultListener, NUM_FRAMES_WAITED_FOR_UNKNOWN_LATENCY);
            }

            // Issue still capture
            if (VERBOSE) {
                Log.v(TAG, "Verifying capture result for ae compensation value "
                        + exposureCompensation);
            }

            stillRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation);
            CaptureRequest request = stillRequest.build();
            mSession.capture(request, resultListener, mHandler);

            compensatedResult = resultListener.getCaptureResultForRequest(
                    request, WAIT_FOR_RESULT_TIMEOUT_MS);

            if (canVerifyExposureValue) {
                // Verify the exposure value compensates as requested
                long compensatedExposureValue = getExposureValue(compensatedResult);
                mCollector.expectInRange("Exposure setting out of bound", compensatedExposureValue,
                        minExposureValue, maxExposureValueStill);
                double observedRatio = (double) compensatedExposureValue / normalExposureValue;
                double error = observedRatio / expectedRatio;
                String errorString = String.format(
                        "Exposure compensation ratio exceeds error tolerence:" +
                        " expected(%f) observed(%f)." +
                        " Normal exposure time %d us, sensitivity %d." +
                        " Compensated exposure time %d us, sensitivity %d",
                        expectedRatio, observedRatio,
                        (int) (getValueNotNull(
                                normalResult, CaptureResult.SENSOR_EXPOSURE_TIME) / 1000),
                        getValueNotNull(normalResult, CaptureResult.SENSOR_SENSITIVITY),
                        (int) (getValueNotNull(
                                compensatedResult, CaptureResult.SENSOR_EXPOSURE_TIME) / 1000),
                        getValueNotNull(compensatedResult, CaptureResult.SENSOR_SENSITIVITY));
                mCollector.expectInRange(errorString, error,
                        1.0 - AE_COMPENSATION_ERROR_TOLERANCE,
                        1.0 + AE_COMPENSATION_ERROR_TOLERANCE);
            }

            mCollector.expectEquals("Exposure compensation result should match requested value.",
                    exposureCompensation,
                    compensatedResult.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION));
            if (canSetAeLock) {
                mCollector.expectTrue("Exposure lock should be set",
                        compensatedResult.get(CaptureResult.CONTROL_AE_LOCK));
            }

            Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            validateJpegCapture(image, maxStillSz);
            image.close();

            // Recover AE compensation and lock
            previewRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            if (canSetAeLock) {
                previewRequest.set(CaptureRequest.CONTROL_AE_LOCK, false);
            }
            mSession.setRepeatingRequest(previewRequest.build(), resultListener, mHandler);
        }
    }

    private long getExposureValue(CaptureResult result) throws Exception {
        int expTimeUs = (int) (getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME) / 1000);
        int sensitivity = getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);
        Integer postRawSensitivity = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST);
        if (postRawSensitivity != null) {
            return (long) sensitivity * postRawSensitivity / 100 * expTimeUs;
        }
        return (long) sensitivity * expTimeUs;
    }

    private long getMaxExposureValue(CaptureRequest.Builder request, long maxExposureTimeUs,
                long maxSensitivity)  throws Exception {
        Range<Integer> fpsRange = request.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE);
        long maxFrameDurationUs = Math.round(1000000.0 / fpsRange.getLower());
        long currentMaxExposureTimeUs = Math.min(maxFrameDurationUs, maxExposureTimeUs);
        return currentMaxExposureTimeUs * maxSensitivity;
    }


    //----------------------------------------------------------------
    //---------Below are common functions for all tests.--------------
    //----------------------------------------------------------------
    /**
     * Validate standard raw (RAW16) capture image.
     *
     * @param image The raw16 format image captured
     * @param rawSize The expected raw size
     */
    private static void validateRaw16Image(Image image, Size rawSize) {
        CameraTestUtils.validateImage(image, rawSize.getWidth(), rawSize.getHeight(),
                ImageFormat.RAW_SENSOR, /*filePath*/null);
    }

    /**
     * Validate JPEG capture image object sanity and test.
     * <p>
     * In addition to image object sanity, this function also does the decoding
     * test, which is slower.
     * </p>
     *
     * @param image The JPEG image to be verified.
     * @param jpegSize The JPEG capture size to be verified against.
     */
    private static void validateJpegCapture(Image image, Size jpegSize) {
        CameraTestUtils.validateImage(image, jpegSize.getWidth(), jpegSize.getHeight(),
                ImageFormat.JPEG, /*filePath*/null);
    }

    private static class SimpleAutoFocusListener implements Camera2Focuser.AutoFocusListener {
        final ConditionVariable focusDone = new ConditionVariable();
        @Override
        public void onAutoFocusLocked(boolean success) {
            focusDone.open();
        }

        public void waitForAutoFocusDone(long timeoutMs) {
            if (focusDone.block(timeoutMs)) {
                focusDone.close();
            } else {
                throw new TimeoutRuntimeException("Wait for auto focus done timed out after "
                        + timeoutMs + "ms");
            }
        }
    }

    /**
     * Get 5 3A region test cases, each with one square region in it.
     * The first one is at center, the other four are at corners of
     * active array rectangle.
     *
     * @return array of test 3A regions
     */
    private ArrayList<MeteringRectangle[]> get3ARegionTestCasesForCamera() {
        final int TEST_3A_REGION_NUM = 5;
        final int DEFAULT_REGION_WEIGHT = 30;
        final int DEFAULT_REGION_SCALE_RATIO = 8;
        ArrayList<MeteringRectangle[]> testCases =
                new ArrayList<MeteringRectangle[]>(TEST_3A_REGION_NUM);
        final Rect activeArraySize = mStaticInfo.getActiveArraySizeChecked();
        int regionWidth = activeArraySize.width() / DEFAULT_REGION_SCALE_RATIO - 1;
        int regionHeight = activeArraySize.height() / DEFAULT_REGION_SCALE_RATIO - 1;
        int centerX = activeArraySize.width() / 2;
        int centerY = activeArraySize.height() / 2;
        int bottomRightX = activeArraySize.width() - 1;
        int bottomRightY = activeArraySize.height() - 1;

        // Center region
        testCases.add(
                new MeteringRectangle[] {
                    new MeteringRectangle(
                            centerX - regionWidth / 2,  // x
                            centerY - regionHeight / 2, // y
                            regionWidth,                // width
                            regionHeight,               // height
                            DEFAULT_REGION_WEIGHT)});

        // Upper left corner
        testCases.add(
                new MeteringRectangle[] {
                    new MeteringRectangle(
                            0,                // x
                            0,                // y
                            regionWidth,      // width
                            regionHeight,     // height
                            DEFAULT_REGION_WEIGHT)});

        // Upper right corner
        testCases.add(
                new MeteringRectangle[] {
                    new MeteringRectangle(
                            bottomRightX - regionWidth, // x
                            0,                          // y
                            regionWidth,                // width
                            regionHeight,               // height
                            DEFAULT_REGION_WEIGHT)});

        // Bottom left corner
        testCases.add(
                new MeteringRectangle[] {
                    new MeteringRectangle(
                            0,                           // x
                            bottomRightY - regionHeight, // y
                            regionWidth,                 // width
                            regionHeight,                // height
                            DEFAULT_REGION_WEIGHT)});

        // Bottom right corner
        testCases.add(
                new MeteringRectangle[] {
                    new MeteringRectangle(
                            bottomRightX - regionWidth,  // x
                            bottomRightY - regionHeight, // y
                            regionWidth,                 // width
                            regionHeight,                // height
                            DEFAULT_REGION_WEIGHT)});

        if (VERBOSE) {
            StringBuilder sb = new StringBuilder();
            for (MeteringRectangle[] mr : testCases) {
                sb.append("{");
                sb.append(Arrays.toString(mr));
                sb.append("}, ");
            }
            if (sb.length() > 1)
                sb.setLength(sb.length() - 2); // Remove the redundant comma and space at the end
            Log.v(TAG, "Generated test regions are: " + sb.toString());
        }

        return testCases;
    }

    private boolean isRegionsSupportedFor3A(int index) {
        int maxRegions = 0;
        switch (index) {
            case MAX_REGIONS_AE_INDEX:
                maxRegions = mStaticInfo.getAeMaxRegionsChecked();
                break;
            case MAX_REGIONS_AWB_INDEX:
                maxRegions = mStaticInfo.getAwbMaxRegionsChecked();
                break;
            case  MAX_REGIONS_AF_INDEX:
                maxRegions = mStaticInfo.getAfMaxRegionsChecked();
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm index");
        }
        boolean isRegionsSupported = maxRegions > 0;
        if (index == MAX_REGIONS_AF_INDEX && isRegionsSupported) {
            mCollector.expectTrue(
                    "Device reports non-zero max AF region count for a camera without focuser!",
                    mStaticInfo.hasFocuser());
            isRegionsSupported = isRegionsSupported && mStaticInfo.hasFocuser();
        }

        return isRegionsSupported;
    }
}
