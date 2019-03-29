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

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.platform.test.annotations.Presubmit;
import android.util.Log;
import android.util.Size;

import java.nio.ByteBuffer;

import android.hardware.camera2.cts.Camera2SurfaceViewCtsActivity;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;

/**
 * Quick-running test for very basic camera operation for all cameras
 * and both camera APIs.
 *
 * May not take more than a few seconds to run, to be suitable for quick
 * testing.
 */
@Presubmit
public class FastBasicsTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "FastBasicsTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int WAIT_FOR_FRAMES_TIMEOUT_MS = 3000;
    private static final int WAIT_FOR_PICTURE_TIMEOUT_MS = 5000;
    private static final int FRAMES_TO_WAIT_FOR_CAPTURE = 100;

    public void testCamera2() throws Exception {
        for (int i = 0; i < mCameraIds.length; i++) {
            try {
                Log.i(TAG, "Testing camera2 API for camera device " + mCameraIds[i]);
                openDevice(mCameraIds[i]);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }

                camera2TestByCamera();
            } finally {
                closeDevice();
            }
        }
    }

    public void camera2TestByCamera() throws Exception {
        CaptureRequest.Builder previewRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        CaptureRequest.Builder stillCaptureRequest =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        Size previewSize = mOrderedPreviewSizes.get(0);
        Size stillSize = mOrderedStillSizes.get(0);
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        SimpleImageReaderListener imageListener = new SimpleImageReaderListener();

        prepareStillCaptureAndStartPreview(previewRequest, stillCaptureRequest,
                previewSize, stillSize, resultListener, imageListener);

        CaptureResult result = resultListener.getCaptureResult(WAIT_FOR_FRAMES_TIMEOUT_MS);

        Long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        assertNotNull("Can't read a capture result timestamp", timestamp);

        CaptureResult result2 = resultListener.getCaptureResult(WAIT_FOR_FRAMES_TIMEOUT_MS);

        Long timestamp2 = result2.get(CaptureResult.SENSOR_TIMESTAMP);
        assertNotNull("Can't read a capture result 2 timestamp", timestamp2);

        assertTrue("Bad timestamps", timestamp2 > timestamp);

        CaptureRequest capture = stillCaptureRequest.build();
        mSession.capture(capture, resultListener, mHandler);

        CaptureResult stillResult =
                resultListener.getTotalCaptureResultForRequest(capture, FRAMES_TO_WAIT_FOR_CAPTURE);

        Long timestamp3 = stillResult.get(CaptureResult.SENSOR_TIMESTAMP);
        assertNotNull("Can't read a still capture result timestamp", timestamp3);

        assertTrue("Bad timestamps", timestamp3 > timestamp2);

        Image img = imageListener.getImage(WAIT_FOR_PICTURE_TIMEOUT_MS);

        ByteBuffer jpegBuffer = img.getPlanes()[0].getBuffer();
        byte[] jpegData = new byte[jpegBuffer.remaining()];
        jpegBuffer.get(jpegData);

        Bitmap b = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

        assertNotNull("Unable to decode still capture JPEG", b);

        closeImageReader();
    }

    private class Camera1Listener
            implements SurfaceTexture.OnFrameAvailableListener, Camera.PictureCallback {

        private Object mFrameSignal = new Object();
        private boolean mGotFrame = false;

        public boolean waitForFrame() {
            synchronized(mFrameSignal) {
                boolean waited = false;
                while (!waited) {
                    try {
                        mFrameSignal.wait(WAIT_FOR_FRAMES_TIMEOUT_MS);
                        waited = true;
                    } catch (InterruptedException e) {
                    }
                }
                return mGotFrame;
            }
        }

        public void onFrameAvailable(SurfaceTexture s) {
            synchronized(mFrameSignal) {
                mGotFrame = true;
                mFrameSignal.notifyAll();
            }
        }

        private Object mPictureSignal = new Object();
        private byte[] mPictureData = null;

        public byte[] waitForPicture() {
            synchronized(mPictureSignal) {
                boolean waited = false;
                while (!waited) {
                    try {
                        mPictureSignal.wait(WAIT_FOR_PICTURE_TIMEOUT_MS);
                        waited = true;
                    } catch (InterruptedException e) {
                    }
                }
                return mPictureData;
            }
        }

        public void onPictureTaken(byte[] data, Camera camera) {
            synchronized(mPictureSignal) {
                mPictureData = data;
                mPictureSignal.notifyAll();
            }
        }
    }

    public void testCamera1() throws Exception {
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera camera = null;
            try {
                Log.i(TAG, "Testing android.hardware.Camera API for camera device " + i);

                camera = Camera.open(i);

                Camera1Listener listener = new Camera1Listener();

                SurfaceTexture st = new SurfaceTexture(/*random int*/ 5);
                st.setOnFrameAvailableListener(listener);

                camera.setPreviewTexture(st);
                camera.startPreview();

                assertTrue("No preview received from camera", listener.waitForFrame());

                camera.takePicture(null, null, listener);

                byte[] picture = listener.waitForPicture();

                assertNotNull("No still picture received from camera", picture);

                Bitmap b = BitmapFactory.decodeByteArray(picture, 0, picture.length);

                assertNotNull("Still picture could not be decoded into Bitmap", b);

            } finally {
                if (camera != null) {
                    camera.release();
                }
            }
        }
    }
}
