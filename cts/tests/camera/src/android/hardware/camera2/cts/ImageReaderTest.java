/*
 * Copyright 2013 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.CameraTestUtils.ImageDropperListener;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.rs.BitmapUtils;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.os.ConditionVariable;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.android.ex.camera2.blocking.BlockingSessionCallback;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.hardware.camera2.cts.CameraTestUtils.CAPTURE_RESULT_TIMEOUT_MS;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import static android.hardware.camera2.cts.CameraTestUtils.SimpleImageReaderListener;
import static android.hardware.camera2.cts.CameraTestUtils.dumpFile;
import static android.hardware.camera2.cts.CameraTestUtils.getValueNotNull;

/**
 * <p>Basic test for ImageReader APIs. It uses CameraDevice as producer, camera
 * sends the data to the surface provided by imageReader. Below image formats
 * are tested:</p>
 *
 * <p>YUV_420_888: flexible YUV420, it is mandatory format for camera. </p>
 * <p>JPEG: used for JPEG still capture, also mandatory format. </p>
 * <p>Some invalid access test. </p>
 * <p>TODO: Add more format tests? </p>
 */
public class ImageReaderTest extends Camera2AndroidTestCase {
    private static final String TAG = "ImageReaderTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    // Number of frame (for streaming requests) to be verified.
    private static final int NUM_FRAME_VERIFIED = 2;
    // Number of frame (for streaming requests) to be verified with log processing time.
    private static final int NUM_LONG_PROCESS_TIME_FRAME_VERIFIED = 10;
    // The time to hold each image for to simulate long processing time.
    private static final int LONG_PROCESS_TIME_MS = 300;
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 5;
    // Max difference allowed between YUV and JPEG patches. This tolerance is intentionally very
    // generous to avoid false positives due to punch/saturation operations vendors apply to the
    // JPEG outputs.
    private static final double IMAGE_DIFFERENCE_TOLERANCE = 40;
    // Legacy level devices needs even larger tolerance because jpeg and yuv are not captured
    // from the same frame in legacy mode.
    private static final double IMAGE_DIFFERENCE_TOLERANCE_LEGACY = 60;

    private SimpleImageListener mListener;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testFlexibleYuv() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                bufferFormatTestByCamera(ImageFormat.YUV_420_888, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testDepth16() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                bufferFormatTestByCamera(ImageFormat.DEPTH16, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testDepthPointCloud() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                bufferFormatTestByCamera(ImageFormat.DEPTH_POINT_CLOUD, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testJpeg() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing jpeg capture for Camera " + id);
                openDevice(id);
                bufferFormatTestByCamera(ImageFormat.JPEG, /*repeating*/false);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testRaw() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing raw capture for camera " + id);
                openDevice(id);

                bufferFormatTestByCamera(ImageFormat.RAW_SENSOR, /*repeating*/false);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testRawPrivate() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing raw capture for camera " + id);
                openDevice(id);

                bufferFormatTestByCamera(ImageFormat.RAW_PRIVATE, /*repeating*/false);
            } finally {
                closeDevice(id);
            }
        }
    }


    public void testRepeatingJpeg() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing repeating jpeg capture for Camera " + id);
                openDevice(id);
                bufferFormatTestByCamera(ImageFormat.JPEG, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testRepeatingRaw() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing repeating raw capture for camera " + id);
                openDevice(id);

                bufferFormatTestByCamera(ImageFormat.RAW_SENSOR, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testRepeatingRawPrivate() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing repeating raw capture for camera " + id);
                openDevice(id);

                bufferFormatTestByCamera(ImageFormat.RAW_PRIVATE, /*repeating*/true);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testLongProcessingRepeatingRaw() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing long processing on repeating raw for camera " + id);
                openDevice(id);

                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }

                bufferFormatLongProcessingTimeTestByCamera(ImageFormat.RAW_SENSOR);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testLongProcessingRepeatingFlexibleYuv() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing long processing on repeating YUV for camera " + id);
                openDevice(id);

                if (!mStaticInfo.isCapabilitySupported(
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)) {
                    continue;
                }

                bufferFormatLongProcessingTimeTestByCamera(ImageFormat.YUV_420_888);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test invalid access of image after an image is closed, further access
     * of the image will get an IllegalStateException. The basic assumption of
     * this test is that the ImageReader always gives direct byte buffer, which is always true
     * for camera case. For if the produced image byte buffer is not direct byte buffer, there
     * is no guarantee to get an ISE for this invalid access case.
     */
    public void testInvalidAccessTest() throws Exception {
        // Test byte buffer access after an image is released, it should throw ISE.
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing invalid image access for Camera " + id);
                openDevice(id);
                invalidAccessTestAfterClose();
            } finally {
                closeDevice(id);
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Test two image stream (YUV420_888 and JPEG) capture by using ImageReader.
     *
     * <p>Both stream formats are mandatory for Camera2 API</p>
     */
    public void testYuvAndJpeg() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "YUV and JPEG testing for camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                bufferFormatWithYuvTestByCamera(ImageFormat.JPEG);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Test two image stream (YUV420_888 and RAW_SENSOR) capture by using ImageReader.
     *
     */
    public void testImageReaderYuvAndRaw() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "YUV and RAW testing for camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                bufferFormatWithYuvTestByCamera(ImageFormat.RAW_SENSOR);
            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Check that the center patches for YUV and JPEG outputs for the same frame match for each YUV
     * resolution and format supported.
     */
    public void testAllOutputYUVResolutions() throws Exception {
        Integer[] sessionStates = {BlockingSessionCallback.SESSION_READY,
                BlockingSessionCallback.SESSION_CONFIGURE_FAILED};
        for (String id : mCameraIds) {
            try {
                Log.v(TAG, "Testing all YUV image resolutions for camera " + id);
                openDevice(id);

                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }

                // Skip warmup on FULL mode devices.
                int warmupCaptureNumber = (mStaticInfo.isHardwareLevelLegacy()) ?
                        MAX_NUM_IMAGES - 1 : 0;

                // NV21 isn't supported by ImageReader.
                final int[] YUVFormats = new int[] {ImageFormat.YUV_420_888, ImageFormat.YV12};

                CameraCharacteristics.Key<StreamConfigurationMap> key =
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
                StreamConfigurationMap config = mStaticInfo.getValueFromKeyNonNull(key);
                int[] supportedFormats = config.getOutputFormats();
                List<Integer> supportedYUVFormats = new ArrayList<>();
                for (int format : YUVFormats) {
                    if (CameraTestUtils.contains(supportedFormats, format)) {
                        supportedYUVFormats.add(format);
                    }
                }

                Size[] jpegSizes = mStaticInfo.getAvailableSizesForFormatChecked(ImageFormat.JPEG,
                        StaticMetadata.StreamDirection.Output);
                assertFalse("JPEG output not supported for camera " + id +
                        ", at least one JPEG output is required.", jpegSizes.length == 0);

                Size maxJpegSize = CameraTestUtils.getMaxSize(jpegSizes);
                Size maxPreviewSize = mOrderedPreviewSizes.get(0);

                for (int format : supportedYUVFormats) {
                    Size[] targetCaptureSizes =
                            mStaticInfo.getAvailableSizesForFormatChecked(format,
                            StaticMetadata.StreamDirection.Output);

                    for (Size captureSz : targetCaptureSizes) {
                        if (VERBOSE) {
                            Log.v(TAG, "Testing yuv size " + captureSz + " and jpeg size "
                                    + maxJpegSize + " for camera " + mCamera.getId());
                        }

                        ImageReader jpegReader = null;
                        ImageReader yuvReader = null;
                        try {
                            // Create YUV image reader
                            SimpleImageReaderListener yuvListener = new SimpleImageReaderListener();
                            yuvReader = createImageReader(captureSz, format, MAX_NUM_IMAGES,
                                    yuvListener);
                            Surface yuvSurface = yuvReader.getSurface();

                            // Create JPEG image reader
                            SimpleImageReaderListener jpegListener =
                                    new SimpleImageReaderListener();
                            jpegReader = createImageReader(maxJpegSize,
                                    ImageFormat.JPEG, MAX_NUM_IMAGES, jpegListener);
                            Surface jpegSurface = jpegReader.getSurface();

                            // Setup session
                            List<Surface> outputSurfaces = new ArrayList<Surface>();
                            outputSurfaces.add(yuvSurface);
                            outputSurfaces.add(jpegSurface);
                            createSession(outputSurfaces);

                            int state = mCameraSessionListener.getStateWaiter().waitForAnyOfStates(
                                        Arrays.asList(sessionStates),
                                        CameraTestUtils.SESSION_CONFIGURE_TIMEOUT_MS);

                            if (state == BlockingSessionCallback.SESSION_CONFIGURE_FAILED) {
                                if (captureSz.getWidth() > maxPreviewSize.getWidth() ||
                                        captureSz.getHeight() > maxPreviewSize.getHeight()) {
                                    Log.v(TAG, "Skip testing {yuv:" + captureSz
                                            + " ,jpeg:" + maxJpegSize + "} for camera "
                                            + mCamera.getId() +
                                            " because full size jpeg + yuv larger than "
                                            + "max preview size (" + maxPreviewSize
                                            + ") is not supported");
                                    continue;
                                } else {
                                    fail("Camera " + mCamera.getId() +
                                            ":session configuration failed for {jpeg: " +
                                            maxJpegSize + ", yuv: " + captureSz + "}");
                                }
                            }

                            // Warm up camera preview (mainly to give legacy devices time to do 3A).
                            CaptureRequest.Builder warmupRequest =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            warmupRequest.addTarget(yuvSurface);
                            assertNotNull("Fail to get CaptureRequest.Builder", warmupRequest);
                            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

                            for (int i = 0; i < warmupCaptureNumber; i++) {
                                startCapture(warmupRequest.build(), /*repeating*/false,
                                        resultListener, mHandler);
                            }
                            for (int i = 0; i < warmupCaptureNumber; i++) {
                                resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);
                                Image image = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                                image.close();
                            }

                            // Capture image.
                            CaptureRequest.Builder mainRequest =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            for (Surface s : outputSurfaces) {
                                mainRequest.addTarget(s);
                            }

                            startCapture(mainRequest.build(), /*repeating*/false, resultListener,
                                    mHandler);

                            // Verify capture result and images
                            resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);

                            Image yuvImage = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                            Image jpegImage = jpegListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);

                            //Validate captured images.
                            CameraTestUtils.validateImage(yuvImage, captureSz.getWidth(),
                                    captureSz.getHeight(), format, /*filePath*/null);
                            CameraTestUtils.validateImage(jpegImage, maxJpegSize.getWidth(),
                                    maxJpegSize.getHeight(), ImageFormat.JPEG, /*filePath*/null);

                            // Compare the image centers.
                            RectF jpegDimens = new RectF(0, 0, jpegImage.getWidth(),
                                    jpegImage.getHeight());
                            RectF yuvDimens = new RectF(0, 0, yuvImage.getWidth(),
                                    yuvImage.getHeight());

                            // Find scale difference between YUV and JPEG output
                            Matrix m = new Matrix();
                            m.setRectToRect(yuvDimens, jpegDimens, Matrix.ScaleToFit.START);
                            RectF scaledYuv = new RectF();
                            m.mapRect(scaledYuv, yuvDimens);
                            float scale = scaledYuv.width() / yuvDimens.width();

                            final int PATCH_DIMEN = 40; // pixels in YUV

                            // Find matching square patch of pixels in YUV and JPEG output
                            RectF tempPatch = new RectF(0, 0, PATCH_DIMEN, PATCH_DIMEN);
                            tempPatch.offset(yuvDimens.centerX() - tempPatch.centerX(),
                                    yuvDimens.centerY() - tempPatch.centerY());
                            Rect yuvPatch = new Rect();
                            tempPatch.roundOut(yuvPatch);

                            tempPatch.set(0, 0, PATCH_DIMEN * scale, PATCH_DIMEN * scale);
                            tempPatch.offset(jpegDimens.centerX() - tempPatch.centerX(),
                                    jpegDimens.centerY() - tempPatch.centerY());
                            Rect jpegPatch = new Rect();
                            tempPatch.roundOut(jpegPatch);

                            // Decode center patches
                            int[] yuvColors = convertPixelYuvToRgba(yuvPatch.width(),
                                    yuvPatch.height(), yuvPatch.left, yuvPatch.top, yuvImage);
                            Bitmap yuvBmap = Bitmap.createBitmap(yuvColors, yuvPatch.width(),
                                    yuvPatch.height(), Bitmap.Config.ARGB_8888);

                            byte[] compressedJpegData = CameraTestUtils.getDataFromImage(jpegImage);
                            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(
                                    compressedJpegData, /*offset*/0, compressedJpegData.length,
                                    /*isShareable*/true);
                            BitmapFactory.Options opt = new BitmapFactory.Options();
                            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            Bitmap fullSizeJpegBmap = decoder.decodeRegion(jpegPatch, opt);
                            Bitmap jpegBmap = Bitmap.createScaledBitmap(fullSizeJpegBmap,
                                    yuvPatch.width(), yuvPatch.height(), /*filter*/true);

                            // Compare two patches using average of per-pixel differences
                            double difference = BitmapUtils.calcDifferenceMetric(yuvBmap, jpegBmap);
                            double tolerance = IMAGE_DIFFERENCE_TOLERANCE;
                            if (mStaticInfo.isHardwareLevelLegacy()) {
                                tolerance = IMAGE_DIFFERENCE_TOLERANCE_LEGACY;
                            }
                            Log.i(TAG, "Difference for resolution " + captureSz + " is: " +
                                    difference);
                            if (difference > tolerance) {
                                // Dump files if running in verbose mode
                                if (DEBUG) {
                                    String jpegFileName = DEBUG_FILE_NAME_BASE + "/" + captureSz +
                                            "_jpeg.jpg";
                                    dumpFile(jpegFileName, jpegBmap);
                                    String fullSizeJpegFileName = DEBUG_FILE_NAME_BASE + "/" +
                                            captureSz + "_full_jpeg.jpg";
                                    dumpFile(fullSizeJpegFileName, compressedJpegData);
                                    String yuvFileName = DEBUG_FILE_NAME_BASE + "/" + captureSz +
                                            "_yuv.jpg";
                                    dumpFile(yuvFileName, yuvBmap);
                                    String fullSizeYuvFileName = DEBUG_FILE_NAME_BASE + "/" +
                                            captureSz + "_full_yuv.jpg";
                                    int[] fullYUVColors = convertPixelYuvToRgba(yuvImage.getWidth(),
                                            yuvImage.getHeight(), 0, 0, yuvImage);
                                    Bitmap fullYUVBmap = Bitmap.createBitmap(fullYUVColors,
                                            yuvImage.getWidth(), yuvImage.getHeight(),
                                            Bitmap.Config.ARGB_8888);
                                    dumpFile(fullSizeYuvFileName, fullYUVBmap);
                                }
                                fail("Camera " + mCamera.getId() + ": YUV and JPEG image at " +
                                        "capture size " + captureSz + " for the same frame are " +
                                        "not similar, center patches have difference metric of " +
                                        difference + ", tolerance is " + tolerance);
                            }

                            // Stop capture, delete the streams.
                            stopCapture(/*fast*/false);
                            yuvImage.close();
                            jpegImage.close();
                            yuvListener.drain();
                            jpegListener.drain();
                        } finally {
                            closeImageReader(jpegReader);
                            jpegReader = null;
                            closeImageReader(yuvReader);
                            yuvReader = null;
                        }
                    }
                }

            } finally {
                closeDevice(id);
            }
        }
    }

    /**
     * Convert a rectangular patch in a YUV image to an ARGB color array.
     *
     * @param w width of the patch.
     * @param h height of the patch.
     * @param wOffset offset of the left side of the patch.
     * @param hOffset offset of the top of the patch.
     * @param yuvImage a YUV image to select a patch from.
     * @return the image patch converted to RGB as an ARGB color array.
     */
    private static int[] convertPixelYuvToRgba(int w, int h, int wOffset, int hOffset,
                                               Image yuvImage) {
        final int CHANNELS = 3; // yuv
        final float COLOR_RANGE = 255f;

        assertTrue("Invalid argument to convertPixelYuvToRgba",
                w > 0 && h > 0 && wOffset >= 0 && hOffset >= 0);
        assertNotNull(yuvImage);

        int imageFormat = yuvImage.getFormat();
        assertTrue("YUV image must have YUV-type format",
                imageFormat == ImageFormat.YUV_420_888 || imageFormat == ImageFormat.YV12 ||
                        imageFormat == ImageFormat.NV21);

        int height = yuvImage.getHeight();
        int width = yuvImage.getWidth();

        Rect imageBounds = new Rect(/*left*/0, /*top*/0, /*right*/width, /*bottom*/height);
        Rect crop = new Rect(/*left*/wOffset, /*top*/hOffset, /*right*/wOffset + w,
                /*bottom*/hOffset + h);
        assertTrue("Output rectangle" + crop + " must lie within image bounds " + imageBounds,
                imageBounds.contains(crop));
        Image.Plane[] planes = yuvImage.getPlanes();

        Image.Plane yPlane = planes[0];
        Image.Plane cbPlane = planes[1];
        Image.Plane crPlane = planes[2];

        ByteBuffer yBuf = yPlane.getBuffer();
        int yPixStride = yPlane.getPixelStride();
        int yRowStride = yPlane.getRowStride();
        ByteBuffer cbBuf = cbPlane.getBuffer();
        int cbPixStride = cbPlane.getPixelStride();
        int cbRowStride = cbPlane.getRowStride();
        ByteBuffer crBuf = crPlane.getBuffer();
        int crPixStride = crPlane.getPixelStride();
        int crRowStride = crPlane.getRowStride();

        int[] output = new int[w * h];

        // TODO: Optimize this with renderscript intrinsics
        byte[] yRow = new byte[yPixStride * w];
        byte[] cbRow = new byte[cbPixStride * w / 2];
        byte[] crRow = new byte[crPixStride * w / 2];
        yBuf.mark();
        cbBuf.mark();
        crBuf.mark();
        int initialYPos = yBuf.position();
        int initialCbPos = cbBuf.position();
        int initialCrPos = crBuf.position();
        int outputPos = 0;
        for (int i = hOffset; i < hOffset + h; i++) {
            yBuf.position(initialYPos + i * yRowStride + wOffset * yPixStride);
            yBuf.get(yRow);
            if ((i & 1) == (hOffset & 1)) {
                cbBuf.position(initialCbPos + (i / 2) * cbRowStride + wOffset * cbPixStride / 2);
                cbBuf.get(cbRow);
                crBuf.position(initialCrPos + (i / 2) * crRowStride + wOffset * crPixStride / 2);
                crBuf.get(crRow);
            }
            for (int j = 0, yPix = 0, crPix = 0, cbPix = 0; j < w; j++, yPix += yPixStride) {
                float y = yRow[yPix] & 0xFF;
                float cb = cbRow[cbPix] & 0xFF;
                float cr = crRow[crPix] & 0xFF;

                // convert YUV -> RGB (from JFIF's "Conversion to and from RGB" section)
                int r = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.402f * (cr - 128)));
                int g = (int) Math.max(0.0f,
                        Math.min(COLOR_RANGE, y - 0.34414f * (cb - 128) - 0.71414f * (cr - 128)));
                int b = (int) Math.max(0.0f, Math.min(COLOR_RANGE, y + 1.772f * (cb - 128)));

                // Convert to ARGB pixel color (use opaque alpha)
                output[outputPos++] = Color.rgb(r, g, b);

                if ((j & 1) == 1) {
                    crPix += crPixStride;
                    cbPix += cbPixStride;
                }
            }
        }
        yBuf.rewind();
        cbBuf.rewind();
        crBuf.rewind();

        return output;
    }

    /**
     * Test capture a given format stream with yuv stream simultaneously.
     *
     * <p>Use fixed yuv size, varies targeted format capture size. Single capture is tested.</p>
     *
     * @param format The capture format to be tested along with yuv format.
     */
    private void bufferFormatWithYuvTestByCamera(int format) throws Exception {
        if (format != ImageFormat.JPEG && format != ImageFormat.RAW_SENSOR
                && format != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Unsupported format: " + format);
        }

        final int NUM_SINGLE_CAPTURE_TESTED = MAX_NUM_IMAGES - 1;
        Size maxYuvSz = mOrderedPreviewSizes.get(0);
        Size[] targetCaptureSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        for (Size captureSz : targetCaptureSizes) {
            if (VERBOSE) {
                Log.v(TAG, "Testing yuv size " + maxYuvSz.toString() + " and capture size "
                        + captureSz.toString() + " for camera " + mCamera.getId());
            }

            ImageReader captureReader = null;
            ImageReader yuvReader = null;
            try {
                // Create YUV image reader
                SimpleImageReaderListener yuvListener  = new SimpleImageReaderListener();
                yuvReader = createImageReader(maxYuvSz, ImageFormat.YUV_420_888, MAX_NUM_IMAGES,
                        yuvListener);
                Surface yuvSurface = yuvReader.getSurface();

                // Create capture image reader
                SimpleImageReaderListener captureListener = new SimpleImageReaderListener();
                captureReader = createImageReader(captureSz, format, MAX_NUM_IMAGES,
                        captureListener);
                Surface captureSurface = captureReader.getSurface();

                // Capture images.
                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(yuvSurface);
                outputSurfaces.add(captureSurface);
                CaptureRequest.Builder request = prepareCaptureRequestForSurfaces(outputSurfaces,
                        CameraDevice.TEMPLATE_PREVIEW);
                SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

                for (int i = 0; i < NUM_SINGLE_CAPTURE_TESTED; i++) {
                    startCapture(request.build(), /*repeating*/false, resultListener, mHandler);
                }

                // Verify capture result and images
                for (int i = 0; i < NUM_SINGLE_CAPTURE_TESTED; i++) {
                    resultListener.getCaptureResult(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the capture result back for " + i + "th capture");
                    }

                    Image yuvImage = yuvListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the yuv image back for " + i + "th capture");
                    }

                    Image captureImage = captureListener.getImage(CAPTURE_WAIT_TIMEOUT_MS);
                    if (VERBOSE) {
                        Log.v(TAG, " Got the capture image back for " + i + "th capture");
                    }

                    //Validate captured images.
                    CameraTestUtils.validateImage(yuvImage, maxYuvSz.getWidth(),
                            maxYuvSz.getHeight(), ImageFormat.YUV_420_888, /*filePath*/null);
                    CameraTestUtils.validateImage(captureImage, captureSz.getWidth(),
                            captureSz.getHeight(), format, /*filePath*/null);
                    yuvImage.close();
                    captureImage.close();
                }

                // Stop capture, delete the streams.
                stopCapture(/*fast*/false);
            } finally {
                closeImageReader(captureReader);
                captureReader = null;
                closeImageReader(yuvReader);
                yuvReader = null;
            }
        }
    }

    private void invalidAccessTestAfterClose() throws Exception {
        final int FORMAT = mStaticInfo.isColorOutputSupported() ?
            ImageFormat.YUV_420_888 : ImageFormat.DEPTH16;

        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(FORMAT,
                StaticMetadata.StreamDirection.Output);
        Image img = null;
        // Create ImageReader.
        mListener = new SimpleImageListener();
        createDefaultImageReader(availableSizes[0], FORMAT, MAX_NUM_IMAGES, mListener);

        // Start capture.
        CaptureRequest request = prepareCaptureRequest();
        SimpleCaptureCallback listener = new SimpleCaptureCallback();
        startCapture(request, /* repeating */false, listener, mHandler);

        mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
        img = mReader.acquireNextImage();
        Plane firstPlane = img.getPlanes()[0];
        ByteBuffer buffer = firstPlane.getBuffer();
        img.close();

        imageInvalidAccessTestAfterClose(img, firstPlane, buffer);
    }

    private void bufferFormatTestByCamera(int format, boolean repeating) throws Exception {

        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        // for each resolution, test imageReader:
        for (Size sz : availableSizes) {
            try {
                if (VERBOSE) {
                    Log.v(TAG, "Testing size " + sz.toString() + " format " + format
                            + " for camera " + mCamera.getId());
                }

                // Create ImageReader.
                mListener  = new SimpleImageListener();
                createDefaultImageReader(sz, format, MAX_NUM_IMAGES, mListener);

                // Start capture.
                CaptureRequest request = prepareCaptureRequest();
                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                startCapture(request, repeating, listener, mHandler);

                int numFrameVerified = repeating ? NUM_FRAME_VERIFIED : 1;

                // Validate images.
                validateImage(sz, format, numFrameVerified, repeating);

                // Validate capture result.
                validateCaptureResult(format, sz, listener, numFrameVerified);

                // stop capture.
                stopCapture(/*fast*/false);
            } finally {
                closeDefaultImageReader();
            }

        }
    }

    private void bufferFormatLongProcessingTimeTestByCamera(int format)
            throws Exception {

        final int TEST_SENSITIVITY_VALUE = mStaticInfo.getSensitivityClampToRange(204);
        final long TEST_EXPOSURE_TIME_NS = mStaticInfo.getExposureClampToRange(28000000);
        final long EXPOSURE_TIME_ERROR_MARGIN_NS = 100000;

        Size[] availableSizes = mStaticInfo.getAvailableSizesForFormatChecked(format,
                StaticMetadata.StreamDirection.Output);

        // for each resolution, test imageReader:
        for (Size sz : availableSizes) {
            Log.v(TAG, "testing size " + sz.toString());
            try {
                if (VERBOSE) {
                    Log.v(TAG, "Testing long processing time: size " + sz.toString() + " format " +
                            format + " for camera " + mCamera.getId());
                }

                // Create ImageReader.
                mListener  = new SimpleImageListener();
                createDefaultImageReader(sz, format, MAX_NUM_IMAGES, mListener);

                // Setting manual controls
                List<Surface> outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(mReader.getSurface());
                CaptureRequest.Builder requestBuilder = prepareCaptureRequestForSurfaces(
                        outputSurfaces, CameraDevice.TEMPLATE_STILL_CAPTURE);

                requestBuilder.set(
                        CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                requestBuilder.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF);
                requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                        CaptureRequest.CONTROL_AWB_MODE_OFF);
                requestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, TEST_SENSITIVITY_VALUE);
                requestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, TEST_EXPOSURE_TIME_NS);

                SimpleCaptureCallback listener = new SimpleCaptureCallback();
                startCapture(requestBuilder.build(), /*repeating*/true, listener, mHandler);

                for (int i = 0; i < NUM_LONG_PROCESS_TIME_FRAME_VERIFIED; i++) {
                    mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);

                    // Verify image.
                    Image img = mReader.acquireNextImage();
                    assertNotNull("Unable to acquire next image", img);
                    CameraTestUtils.validateImage(img, sz.getWidth(), sz.getHeight(), format,
                            DEBUG_FILE_NAME_BASE);

                    // Verify the exposure time and iso match the requested values.
                    CaptureResult result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);

                    long exposureTimeDiff = TEST_EXPOSURE_TIME_NS -
                            getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                    int sensitivityDiff = TEST_SENSITIVITY_VALUE -
                            getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);

                    mCollector.expectTrue(
                            String.format("Long processing frame %d format %d size %s " +
                                    "exposure time was %d expecting %d.", i, format, sz.toString(),
                                    getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME),
                                    TEST_EXPOSURE_TIME_NS),
                            exposureTimeDiff < EXPOSURE_TIME_ERROR_MARGIN_NS &&
                            exposureTimeDiff >= 0);

                    mCollector.expectTrue(
                            String.format("Long processing frame %d format %d size %s " +
                                    "sensitivity was %d expecting %d.", i, format, sz.toString(),
                                    getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY),
                                    TEST_SENSITIVITY_VALUE),
                            sensitivityDiff >= 0);


                    // Sleep to Simulate long porcessing before closing the image.
                    Thread.sleep(LONG_PROCESS_TIME_MS);
                    img.close();
                }
                // Stop capture.
                // Drain the reader queue in case the full queue blocks
                // HAL from delivering new results
                ImageDropperListener imageDropperListener = new ImageDropperListener();
                mReader.setOnImageAvailableListener(imageDropperListener, mHandler);
                Image img = mReader.acquireLatestImage();
                if (img != null) {
                    img.close();
                }
                stopCapture(/*fast*/false);
            } finally {
                closeDefaultImageReader();
            }
        }
    }

    /**
     * Validate capture results.
     *
     * @param format The format of this capture.
     * @param size The capture size.
     * @param listener The capture listener to get capture result callbacks.
     */
    private void validateCaptureResult(int format, Size size, SimpleCaptureCallback listener,
            int numFrameVerified) {
        for (int i = 0; i < numFrameVerified; i++) {
            CaptureResult result = listener.getCaptureResult(CAPTURE_RESULT_TIMEOUT_MS);

            // TODO: Update this to use availableResultKeys once shim supports this.
            if (mStaticInfo.isCapabilitySupported(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS)) {
                Long exposureTime = getValueNotNull(result, CaptureResult.SENSOR_EXPOSURE_TIME);
                Integer sensitivity = getValueNotNull(result, CaptureResult.SENSOR_SENSITIVITY);
                mCollector.expectInRange(
                        String.format(
                                "Capture for format %d, size %s exposure time is invalid.",
                                format, size.toString()),
                        exposureTime,
                        mStaticInfo.getExposureMinimumOrDefault(),
                        mStaticInfo.getExposureMaximumOrDefault()
                );
                mCollector.expectInRange(
                        String.format("Capture for format %d, size %s sensitivity is invalid.",
                                format, size.toString()),
                        sensitivity,
                        mStaticInfo.getSensitivityMinimumOrDefault(),
                        mStaticInfo.getSensitivityMaximumOrDefault()
                );
            }
            // TODO: add more key validations.
        }
    }

    private final class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        private final ConditionVariable imageAvailable = new ConditionVariable();
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (mReader != reader) {
                return;
            }

            if (VERBOSE) Log.v(TAG, "new image available");
            imageAvailable.open();
        }

        public void waitForAnyImageAvailable(long timeout) {
            if (imageAvailable.block(timeout)) {
                imageAvailable.close();
            } else {
                fail("wait for image available timed out after " + timeout + "ms");
            }
        }

        public void closePendingImages() {
            Image image = mReader.acquireLatestImage();
            if (image != null) {
                image.close();
            }
        }
    }

    private void validateImage(Size sz, int format, int captureCount,  boolean repeating)
            throws Exception {
        // TODO: Add more format here, and wrap each one as a function.
        Image img;
        final int MAX_RETRY_COUNT = 20;
        int numImageVerified = 0;
        int reTryCount = 0;
        while (numImageVerified < captureCount) {
            assertNotNull("Image listener is null", mListener);
            if (VERBOSE) Log.v(TAG, "Waiting for an Image");
            mListener.waitForAnyImageAvailable(CAPTURE_WAIT_TIMEOUT_MS);
            if (repeating) {
                /**
                 * Acquire the latest image in case the validation is slower than
                 * the image producing rate.
                 */
                img = mReader.acquireLatestImage();
                /**
                 * Sometimes if multiple onImageAvailable callbacks being queued,
                 * acquireLatestImage will clear all buffer before corresponding callback is
                 * executed. Wait for a new frame in that case.
                 */
                if (img == null && reTryCount < MAX_RETRY_COUNT) {
                    reTryCount++;
                    continue;
                }
            } else {
                img = mReader.acquireNextImage();
            }
            assertNotNull("Unable to acquire the latest image", img);
            if (VERBOSE) Log.v(TAG, "Got the latest image");
            CameraTestUtils.validateImage(img, sz.getWidth(), sz.getHeight(), format,
                    DEBUG_FILE_NAME_BASE);
            if (VERBOSE) Log.v(TAG, "finish validation of image " + numImageVerified);
            img.close();
            numImageVerified++;
            reTryCount = 0;
        }

        // Return all pending images to the ImageReader as the validateImage may
        // take a while to return and there could be many images pending.
        mListener.closePendingImages();
    }
}
