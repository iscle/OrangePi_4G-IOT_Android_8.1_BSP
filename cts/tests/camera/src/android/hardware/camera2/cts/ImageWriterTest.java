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

import static android.hardware.camera2.cts.CameraTestUtils.*;

import android.graphics.ImageFormat;
import android.hardware.camera2.cts.CameraTestUtils.SimpleCaptureCallback;
import android.hardware.camera2.cts.testcases.Camera2AndroidTestCase;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <p>
 * Basic test for ImageWriter APIs. ImageWriter takes the images produced by
 * camera (via ImageReader), then the data is consumed by either camera input
 * interface or ImageReader.
 * </p>
 */
public class ImageWriterTest extends Camera2AndroidTestCase {
    private static final String TAG = "ImageWriterTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    // Max number of images can be accessed simultaneously from ImageReader.
    private static final int MAX_NUM_IMAGES = 3;
    private static final int CAMERA_PRIVATE_FORMAT = ImageFormat.PRIVATE;
    private ImageReader mReaderForWriter;
    private ImageWriter mWriter;

    @Override
    protected void tearDown() throws Exception {
        try {
            closeImageReader(mReaderForWriter);
        } finally {
            mReaderForWriter = null;
            if (mWriter != null) {
                mWriter.close();
                mWriter = null;
            }
        }

        super.tearDown();
    }

    /**
     * `
     * <p>
     * Basic YUV420_888 format ImageWriter ImageReader test that checks the
     * images produced by camera can be passed correctly by ImageWriter.
     * </p>
     * <p>
     * {@link ImageReader} reads the images produced by {@link CameraDevice}.
     * The images are then passed to ImageWriter, which produces new images that
     * are consumed by the second image reader. The images from first
     * ImageReader should be identical with the images from the second
     * ImageReader. This validates the basic image input interface of the
     * ImageWriter. Below is the data path tested:
     * <li>Explicit data copy: Dequeue an image from ImageWriter, copy the image
     * data from first ImageReader into this image, then queue this image back
     * to ImageWriter. This validates the ImageWriter explicit buffer copy
     * interface.</li>
     * </p>
     */
    public void testYuvImageWriterReaderOperation() throws Exception {
        for (String id : mCameraIds) {
            try {
                Log.i(TAG, "Testing Camera " + id);
                openDevice(id);
                if (!mStaticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id + " does not support color outputs, skipping");
                    continue;
                }
                readerWriterFormatTestByCamera(ImageFormat.YUV_420_888);
            } finally {
                closeDevice(id);
            }
        }
    }

    public void testAbandonedSurfaceExceptions() throws Exception {
        final int READER_WIDTH = 1920;
        final int READER_HEIGHT = 1080;
        final int READER_FORMAT = ImageFormat.YUV_420_888;

        // Verify that if the image writer's input surface is abandoned, dequeueing an image
        // throws IllegalStateException
        ImageReader reader = ImageReader.newInstance(READER_WIDTH, READER_HEIGHT, READER_FORMAT,
                MAX_NUM_IMAGES);
        ImageWriter writer = ImageWriter.newInstance(reader.getSurface(), MAX_NUM_IMAGES);

        // Close image reader to abandon the input surface.
        reader.close();

        Image image;
        try {
            image = writer.dequeueInputImage();
            fail("Should get an IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        } finally {
            writer.close();
        }

        // Verify that if the image writer's input surface is abandoned, queueing an image
        // throws IllegalStateException
        reader = ImageReader.newInstance(READER_WIDTH, READER_HEIGHT, READER_FORMAT,
                MAX_NUM_IMAGES);
        writer = ImageWriter.newInstance(reader.getSurface(), MAX_NUM_IMAGES);
        image = writer.dequeueInputImage();

        // Close image reader to abandon the input surface.
        reader.close();

        try {
            writer.queueInputImage(image);
            fail("Should get an IllegalStateException");
        } catch (IllegalStateException e) {
            // Expected
        } finally {
            writer.close();
        }
    }

    private void readerWriterFormatTestByCamera(int format)  throws Exception {
        List<Size> sizes = getSortedSizesForFormat(mCamera.getId(), mCameraManager, format, null);
        Size maxSize = sizes.get(0);
        if (VERBOSE) {
            Log.v(TAG, "Testing size " + maxSize);
        }

        // Create ImageReader for camera output.
        SimpleImageReaderListener listenerForCamera  = new SimpleImageReaderListener();
        createDefaultImageReader(maxSize, format, MAX_NUM_IMAGES, listenerForCamera);
        if (VERBOSE) {
            Log.v(TAG, "Created camera output ImageReader");
        }

        // Create ImageReader for ImageWriter output
        SimpleImageReaderListener listenerForWriter  = new SimpleImageReaderListener();
        mReaderForWriter = createImageReader(maxSize, format, MAX_NUM_IMAGES, listenerForWriter);
        if (VERBOSE) {
            Log.v(TAG, "Created ImageWriter output ImageReader");
        }

        // Create ImageWriter
        Surface surface = mReaderForWriter.getSurface();
        assertNotNull("Surface from ImageReader shouldn't be null", surface);
        mWriter = ImageWriter.newInstance(surface, MAX_NUM_IMAGES);
        SimpleImageWriterListener writerImageListener = new SimpleImageWriterListener(mWriter);
        mWriter.setOnImageReleasedListener(writerImageListener, mHandler);

        // Start capture: capture 2 images.
        List<Surface> outputSurfaces = new ArrayList<Surface>();
        outputSurfaces.add(mReader.getSurface());
        CaptureRequest.Builder requestBuilder = prepareCaptureRequestForSurfaces(outputSurfaces,
                CameraDevice.TEMPLATE_PREVIEW);
        SimpleCaptureCallback captureListener = new SimpleCaptureCallback();
        // Capture 1st image.
        startCapture(requestBuilder.build(), /*repeating*/false, captureListener, mHandler);
        // Capture 2nd image.
        startCapture(requestBuilder.build(), /*repeating*/false, captureListener, mHandler);
        if (VERBOSE) {
            Log.v(TAG, "Submitted 2 captures");
        }

        // Image from the first ImageReader.
        Image cameraImage = null;
        // ImageWriter input image.
        Image inputImage = null;
        // Image from the second ImageReader.
        Image outputImage = null;
        assertTrue("ImageWriter max images should be " + MAX_NUM_IMAGES,
                mWriter.getMaxImages() == MAX_NUM_IMAGES);
        if (format == CAMERA_PRIVATE_FORMAT) {
            assertTrue("First ImageReader format should be PRIVATE",
                    mReader.getImageFormat() == CAMERA_PRIVATE_FORMAT);
            assertTrue("Second ImageReader should be PRIVATE",
                    mReaderForWriter.getImageFormat() == CAMERA_PRIVATE_FORMAT);
            assertTrue("Format of first ImageReader should be PRIVATE",
                    mReader.getImageFormat() == CAMERA_PRIVATE_FORMAT);
            assertTrue(" Format of second ImageReader should be PRIVATE",
                    mReaderForWriter.getImageFormat() == CAMERA_PRIVATE_FORMAT);
            assertTrue(" Format of ImageWriter should be PRIVATE",
                    mWriter.getFormat() == CAMERA_PRIVATE_FORMAT);

            // Validate 2 images
            validateOpaqueImages(maxSize, listenerForCamera, listenerForWriter, captureListener,
                    /*numImages*/2, writerImageListener);
        } else {
            // Test case 1: Explicit data copy, only applicable for explicit formats.

            // Get 1st image from first ImageReader, and copy the data to ImageWrtier input image
            cameraImage = listenerForCamera.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            inputImage = mWriter.dequeueInputImage();
            inputImage.setTimestamp(cameraImage.getTimestamp());
            if (VERBOSE) {
                Log.v(TAG, "Image is being copied");
            }
            imageCopy(cameraImage, inputImage);
            if (VERBOSE) {
                Log.v(TAG, "Image copy is done");
            }
            mCollector.expectTrue(
                    "ImageWriter 1st input image should match camera 1st output image",
                    isImageStronglyEqual(inputImage, cameraImage));

            // Image should be closed after queueInputImage call
            Plane closedPlane = inputImage.getPlanes()[0];
            ByteBuffer closedBuffer = closedPlane.getBuffer();
            mWriter.queueInputImage(inputImage);
            imageInvalidAccessTestAfterClose(inputImage, closedPlane, closedBuffer);

            outputImage = listenerForWriter.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            mCollector.expectTrue("ImageWriter 1st output image should match 1st input image",
                    isImageStronglyEqual(cameraImage, outputImage));
            if (DEBUG) {
                String img1FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize + "_image1_copy.yuv";
                String outputImg1FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize
                        + "_outputImage2_copy.yuv";
                dumpFile(img1FileName, getDataFromImage(cameraImage));
                dumpFile(outputImg1FileName, getDataFromImage(outputImage));
            }
            // No need to close inputImage, as it is sent to the surface after queueInputImage;
            cameraImage.close();
            outputImage.close();

            // Make sure ImageWriter listener callback is fired.
            writerImageListener.waitForImageReleased(CAPTURE_IMAGE_TIMEOUT_MS);

            // Test case 2: Directly inject the image into ImageWriter: works for all formats.

            // Get 2nd image and queue it directly to ImageWrier
            cameraImage = listenerForCamera.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            // make a copy of image1 data, as it will be closed after queueInputImage;
            byte[] img1Data = getDataFromImage(cameraImage);
            if (DEBUG) {
                String img2FileName = DEBUG_FILE_NAME_BASE + "/" + maxSize + "_image2.yuv";
                dumpFile(img2FileName, img1Data);
            }

            // Image should be closed after queueInputImage call
            closedPlane = cameraImage.getPlanes()[0];
            closedBuffer = closedPlane.getBuffer();
            mWriter.queueInputImage(cameraImage);
            imageInvalidAccessTestAfterClose(cameraImage, closedPlane, closedBuffer);

            outputImage = listenerForWriter.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            byte[] outputImageData = getDataFromImage(outputImage);

            mCollector.expectTrue("ImageWriter 2nd output image should match camera "
                    + "2nd output image", Arrays.equals(img1Data, outputImageData));

            if (DEBUG) {
                String outputImgFileName = DEBUG_FILE_NAME_BASE + "/" + maxSize +
                        "_outputImage2.yuv";
                dumpFile(outputImgFileName, outputImageData);
            }
            // No need to close inputImage, as it is sent to the surface after queueInputImage;
            outputImage.close();

            // Make sure ImageWriter listener callback is fired.
            writerImageListener.waitForImageReleased(CAPTURE_IMAGE_TIMEOUT_MS);
        }

        stopCapture(/*fast*/false);
        mReader.close();
        mReader = null;
        mReaderForWriter.close();
        mReaderForWriter = null;
        mWriter.close();
        mWriter = null;
    }

    private void validateOpaqueImages(Size maxSize, SimpleImageReaderListener listenerForCamera,
            SimpleImageReaderListener listenerForWriter, SimpleCaptureCallback captureListener,
            int numImages, SimpleImageWriterListener writerListener) throws Exception {
        Image cameraImage;
        Image outputImage;
        for (int i = 0; i < numImages; i++) {
            cameraImage = listenerForCamera.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            CaptureResult result = captureListener.getCaptureResult(CAPTURE_IMAGE_TIMEOUT_MS);
            validateOpaqueImage(cameraImage, "Opaque image " + i + "from camera: ", maxSize,
                    result);
            mWriter.queueInputImage(cameraImage);
            // Image should be closed after queueInputImage
            imageInvalidAccessTestAfterClose(cameraImage,
                    /*closedPlane*/null, /*closedBuffer*/null);
            outputImage = listenerForWriter.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
            validateOpaqueImage(outputImage, "First Opaque image output by ImageWriter: ",
                    maxSize, result);
            outputImage.close();
            writerListener.waitForImageReleased(CAPTURE_IMAGE_TIMEOUT_MS);
        }
    }

    private void validateOpaqueImage(Image image, String msg, Size imageSize,
            CaptureResult result) {
        assertNotNull("Opaque image Capture result should not be null", result != null);
        mCollector.expectImageProperties(msg + "Opaque ", image, CAMERA_PRIVATE_FORMAT,
                imageSize, result.get(CaptureResult.SENSOR_TIMESTAMP));
        mCollector.expectTrue(msg + "Opaque image number planes should be zero",
                image.getPlanes().length == 0);
    }
}
