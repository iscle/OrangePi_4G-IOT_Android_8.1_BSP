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

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.cts.CameraTestUtils.ImageVerifierListener;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.cts.testcases.Camera2MultiViewTestCase;
import android.hardware.camera2.cts.testcases.Camera2MultiViewTestCase.CameraPreviewListener;
import android.hardware.camera2.params.OutputConfiguration;
import android.media.ImageReader;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CameraDevice test by using combination of SurfaceView, TextureView and ImageReader
 */
public class MultiViewTest extends Camera2MultiViewTestCase {
    private static final String TAG = "MultiViewTest";
    private final static long WAIT_FOR_COMMAND_TO_COMPLETE = 2000;
    private final static long PREVIEW_TIME_MS = 2000;

    public void testTextureViewPreview() throws Exception {
        for (String cameraId : mCameraIds) {
            Exception prior = null;

            try {
                openCamera(cameraId);
                if (!getStaticInfo(cameraId).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping");
                    continue;
                }
                List<TextureView> views = Arrays.asList(mTextureView[0]);
                textureViewPreview(cameraId, views, /*ImageReader*/null);
            } catch (Exception e) {
                prior = e;
            } finally {
                try {
                    closeCamera(cameraId);
                } catch (Exception e) {
                    if (prior != null) {
                        Log.e(TAG, "Prior exception received: " + prior);
                    }
                    prior = e;
                }
                if (prior != null) throw prior; // Rethrow last exception.
            }
        }
    }

    public void testTextureViewPreviewWithImageReader() throws Exception {
        for (String cameraId : mCameraIds) {
            Exception prior = null;

            ImageVerifierListener yuvListener;
            ImageReader yuvReader = null;

            try {
                openCamera(cameraId);
                if (!getStaticInfo(cameraId).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping");
                    continue;
                }
                Size previewSize = getOrderedPreviewSizes(cameraId).get(0);
                yuvListener =
                        new ImageVerifierListener(previewSize, ImageFormat.YUV_420_888);
                yuvReader = makeImageReader(previewSize,
                        ImageFormat.YUV_420_888, MAX_READER_IMAGES, yuvListener, mHandler);
                int maxNumStreamsProc =
                        getStaticInfo(cameraId).getMaxNumOutputStreamsProcessedChecked();
                if (maxNumStreamsProc < 2) {
                    continue;
                }
                List<TextureView> views = Arrays.asList(mTextureView[0]);
                textureViewPreview(cameraId, views, yuvReader);
            } catch (Exception e) {
                prior = e;
            } finally {
                try {
                    // Close camera device first. This will give some more time for
                    // ImageVerifierListener to finish the validation before yuvReader is closed
                    // (all image will be closed after that)
                    closeCamera(cameraId);
                    if (yuvReader != null) {
                        yuvReader.close();
                    }
                } catch (Exception e) {
                    if (prior != null) {
                        Log.e(TAG, "Prior exception received: " + prior);
                    }
                    prior = e;
                }
                if (prior != null) throw prior; // Rethrow last exception.
            }
        }
    }

    public void testDualTextureViewPreview() throws Exception {
        for (String cameraId : mCameraIds) {
            Exception prior = null;
            try {
                openCamera(cameraId);
                if (!getStaticInfo(cameraId).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping");
                    continue;
                }
                int maxNumStreamsProc =
                        getStaticInfo(cameraId).getMaxNumOutputStreamsProcessedChecked();
                if (maxNumStreamsProc < 2) {
                    continue;
                }
                List<TextureView> views = Arrays.asList(mTextureView[0], mTextureView[1]);
                textureViewPreview(cameraId, views, /*ImageReader*/null);
            } catch (Exception e) {
                prior = e;
            } finally {
                try {
                    closeCamera(cameraId);
                } catch (Exception e) {
                    if (prior != null) {
                        Log.e(TAG, "Prior exception received: " + prior);
                    }
                    prior = e;
                }
                if (prior != null) throw prior; // Rethrow last exception.
            }
        }
    }

    public void testDualTextureViewAndImageReaderPreview() throws Exception {
        for (String cameraId : mCameraIds) {
            Exception prior = null;

            ImageVerifierListener yuvListener;
            ImageReader yuvReader = null;

            try {
                openCamera(cameraId);
                if (!getStaticInfo(cameraId).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + cameraId + " does not support color outputs, skipping");
                    continue;
                }
                Size previewSize = getOrderedPreviewSizes(cameraId).get(0);
                yuvListener =
                        new ImageVerifierListener(previewSize, ImageFormat.YUV_420_888);
                yuvReader = makeImageReader(previewSize,
                        ImageFormat.YUV_420_888, MAX_READER_IMAGES, yuvListener, mHandler);
                int maxNumStreamsProc =
                        getStaticInfo(cameraId).getMaxNumOutputStreamsProcessedChecked();
                if (maxNumStreamsProc < 3) {
                    continue;
                }
                List<TextureView> views = Arrays.asList(mTextureView[0], mTextureView[1]);
                textureViewPreview(cameraId, views, yuvReader);
            } catch (Exception e) {
                prior = e;
            } finally {
                try {
                    if (yuvReader != null) {
                        yuvReader.close();
                    }
                    closeCamera(cameraId);
                } catch (Exception e) {
                    if (prior != null) {
                        Log.e(TAG, "Prior exception received: " + prior);
                    }
                    prior = e;
                }
                if (prior != null) throw prior; // Rethrow last exception.
            }
        }
    }

    public void testDualCameraPreview() throws Exception {
        final int NUM_CAMERAS_TESTED = 2;
        if (mCameraIds.length < NUM_CAMERAS_TESTED) {
            return;
        }

        try {
            for (int i = 0; i < NUM_CAMERAS_TESTED; i++) {
                openCamera(mCameraIds[i]);
                if (!getStaticInfo(mCameraIds[i]).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIds[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                List<TextureView> views = Arrays.asList(mTextureView[i]);

                startTextureViewPreview(mCameraIds[i], views, /*ImageReader*/null);
            }
            // TODO: check the framerate is correct
            SystemClock.sleep(PREVIEW_TIME_MS);
            for (int i = 0; i < NUM_CAMERAS_TESTED; i++) {
                stopPreview(mCameraIds[i]);
            }
        } catch (BlockingOpenException e) {
            // The only error accepted is ERROR_MAX_CAMERAS_IN_USE, which means HAL doesn't support
            // concurrent camera streaming
            assertEquals("Camera device open failed",
                    CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE, e.getCode());
            Log.i(TAG, "Camera HAL does not support dual camera preview. Skip the test");
        } finally {
            for (int i = 0; i < NUM_CAMERAS_TESTED; i++) {
                closeCamera(mCameraIds[i]);
            }
        }
    }

    /*
     * Verify behavior of sharing surfaces within one OutputConfiguration
     */
    public void testSharedSurfaces() throws Exception {
        for (String cameraId : mCameraIds) {
            try {
                openCamera(cameraId);
                if (getStaticInfo(cameraId).isHardwareLevelLegacy()) {
                    Log.i(TAG, "Camera " + cameraId + " is legacy, skipping");
                    continue;
                }
                if (!getStaticInfo(cameraId).isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + cameraId +
                            " does not support color outputs, skipping");
                    continue;
                }

                testSharedSurfacesConfigByCamera(cameraId);

                testSharedSurfacesCaptureSessionByCamera(cameraId);

                testSharedDeferredSurfacesByCamera(cameraId);
            }
            finally {
                closeCamera(cameraId);
            }
        }
    }


    /**
     * Start camera preview using input texture views and/or one image reader
     */
    private void startTextureViewPreview(
            String cameraId, List<TextureView> views, ImageReader imageReader)
            throws Exception {
        int numPreview = views.size();
        Size previewSize = getOrderedPreviewSizes(cameraId).get(0);
        CameraPreviewListener[] previewListener =
                new CameraPreviewListener[numPreview];
        SurfaceTexture[] previewTexture = new SurfaceTexture[numPreview];
        List<Surface> surfaces = new ArrayList<Surface>();

        // Prepare preview surface.
        int i = 0;
        for (TextureView view : views) {
            previewListener[i] = new CameraPreviewListener();
            view.setSurfaceTextureListener(previewListener[i]);
            previewTexture[i] = getAvailableSurfaceTexture(WAIT_FOR_COMMAND_TO_COMPLETE, view);
            assertNotNull("Unable to get preview surface texture", previewTexture[i]);
            previewTexture[i].setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            // Correct the preview display rotation.
            updatePreviewDisplayRotation(previewSize, view);
            surfaces.add(new Surface(previewTexture[i]));
            i++;
        }
        if (imageReader != null) {
            surfaces.add(imageReader.getSurface());
        }

        startPreview(cameraId, surfaces, null);

        i = 0;
        for (TextureView view : views) {
            boolean previewDone =
                    previewListener[i].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
            assertTrue("Unable to start preview " + i, previewDone);
            view.setSurfaceTextureListener(null);
            i++;
        }
    }

    /**
     * Test camera preview using input texture views and/or one image reader
     */
    private void textureViewPreview(
            String cameraId, List<TextureView> views, ImageReader testImagerReader)
            throws Exception {
        startTextureViewPreview(cameraId, views, testImagerReader);

        // TODO: check the framerate is correct
        SystemClock.sleep(PREVIEW_TIME_MS);

        stopPreview(cameraId);
    }

    /*
     * Verify behavior of OutputConfiguration when sharing surfaces
     */
    private void testSharedSurfacesConfigByCamera(String cameraId) throws Exception {
        Size previewSize = getOrderedPreviewSizes(cameraId).get(0);

        SurfaceTexture[] previewTexture = new SurfaceTexture[2];
        Surface[] surfaces = new Surface[2];

        // Create surface textures with the same size
        for (int i = 0; i < 2; i++) {
            previewTexture[i] = getAvailableSurfaceTexture(
                    WAIT_FOR_COMMAND_TO_COMPLETE, mTextureView[i]);
            assertNotNull("Unable to get preview surface texture", previewTexture[i]);
            previewTexture[i].setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            // Correct the preview display rotation.
            updatePreviewDisplayRotation(previewSize, mTextureView[i]);
            surfaces[i] = new Surface(previewTexture[i]);
        }

        // Verify that outputConfiguration can be created with 2 surfaces with the same setting.
        OutputConfiguration previewConfiguration = new OutputConfiguration(
                OutputConfiguration.SURFACE_GROUP_ID_NONE, surfaces[0]);
        previewConfiguration.enableSurfaceSharing();
        previewConfiguration.addSurface(surfaces[1]);
        List<Surface> previewSurfaces = previewConfiguration.getSurfaces();
        List<Surface> inputSurfaces = Arrays.asList(surfaces);
        assertTrue(
                String.format("Surfaces returned from getSurfaces() don't match those passed in"),
                previewSurfaces.equals(inputSurfaces));

        // Verify that createCaptureSession fails if 2 surfaces are different size
        SurfaceTexture outputTexture2 = new SurfaceTexture(/* random texture ID*/ 5);
        outputTexture2.setDefaultBufferSize(previewSize.getWidth()/2,
                previewSize.getHeight()/2);
        Surface outputSurface2 = new Surface(outputTexture2);
        OutputConfiguration configuration = new OutputConfiguration(
                OutputConfiguration.SURFACE_GROUP_ID_NONE, surfaces[0]);
        configuration.enableSurfaceSharing();
        configuration.addSurface(outputSurface2);
        List<OutputConfiguration> outputConfigurations = new ArrayList<>();
        outputConfigurations.add(configuration);
        verifyCreateSessionWithConfigsFailure(cameraId, outputConfigurations);

        // Verify that outputConfiguration throws exception if 2 surfaces are different format
        ImageReader imageReader = makeImageReader(previewSize, ImageFormat.YUV_420_888,
                MAX_READER_IMAGES, new ImageDropperListener(), mHandler);
        try {
            configuration = new OutputConfiguration(OutputConfiguration.SURFACE_GROUP_ID_NONE,
                    surfaces[0]);
            configuration.enableSurfaceSharing();
            configuration.addSurface(imageReader.getSurface());
            fail("No error for invalid output config created from different format surfaces");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Verify that outputConfiguration can be created with deferred surface with the same
        // setting.
        OutputConfiguration deferredPreviewConfigure = new OutputConfiguration(
                previewSize, SurfaceTexture.class);
        deferredPreviewConfigure.addSurface(surfaces[0]);
        assertTrue(String.format("Number of surfaces %d doesn't match expected value 1",
                deferredPreviewConfigure.getSurfaces().size()),
                deferredPreviewConfigure.getSurfaces().size() == 1);
        assertEquals("Surface 0 in OutputConfiguration doesn't match input",
                deferredPreviewConfigure.getSurfaces().get(0), surfaces[0]);

        // Verify that outputConfiguration throws exception if deferred surface and non-deferred
        // surface properties don't match
        try {
            configuration = new OutputConfiguration(previewSize, SurfaceTexture.class);
            configuration.addSurface(imageReader.getSurface());
            fail("No error for invalid output config created deferred class with different type");
        } catch (IllegalArgumentException e) {
            // expected;
        }

        // Verify that non implementation-defined formats sharing are not supported.
        int[] unsupportedFormats =
                {ImageFormat.YUV_420_888, ImageFormat.DEPTH16, ImageFormat.DEPTH_POINT_CLOUD,
                 ImageFormat.JPEG, ImageFormat.RAW_SENSOR, ImageFormat.RAW_PRIVATE};
        for (int format : unsupportedFormats) {
            Size[] availableSizes = getStaticInfo(cameraId).getAvailableSizesForFormatChecked(
                    format, StaticMetadata.StreamDirection.Output);
            if (availableSizes.length == 0) {
                continue;
            }
            Size size = availableSizes[0];

            imageReader = makeImageReader(size, format, MAX_READER_IMAGES,
                    new ImageDropperListener(), mHandler);
            configuration = new OutputConfiguration(OutputConfiguration.SURFACE_GROUP_ID_NONE,
                    imageReader.getSurface());
            configuration.enableSurfaceSharing();

            List<OutputConfiguration> outputConfigs = new ArrayList<>();
            outputConfigs.add(configuration);

            verifyCreateSessionWithConfigsFailure(cameraId, outputConfigs);

        }
    }

    private void testSharedSurfacesCaptureSessionByCamera(String cameraId) throws Exception {
        Size previewSize = getOrderedPreviewSizes(cameraId).get(0);
        CameraPreviewListener[] previewListener = new CameraPreviewListener[2];
        SurfaceTexture[] previewTexture = new SurfaceTexture[2];
        Surface[] surfaces = new Surface[2];

        // Create surface textures with the same size
        for (int i = 0; i < 2; i++) {
            previewListener[i] = new CameraPreviewListener();
            mTextureView[i].setSurfaceTextureListener(previewListener[i]);
            previewTexture[i] = getAvailableSurfaceTexture(
                    WAIT_FOR_COMMAND_TO_COMPLETE, mTextureView[i]);
            assertNotNull("Unable to get preview surface texture", previewTexture[i]);
            previewTexture[i].setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            // Correct the preview display rotation.
            updatePreviewDisplayRotation(previewSize, mTextureView[i]);
            surfaces[i] = new Surface(previewTexture[i]);
        }

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        // Create shared outputs for the two surface textures
        OutputConfiguration surfaceSharedOutput = new OutputConfiguration(
                OutputConfiguration.SURFACE_GROUP_ID_NONE, surfaces[0]);
        surfaceSharedOutput.enableSurfaceSharing();
        surfaceSharedOutput.addSurface(surfaces[1]);

        List<OutputConfiguration> outputConfigurations = new ArrayList<>();
        outputConfigurations.add(surfaceSharedOutput);

        startPreviewWithConfigs(cameraId, outputConfigurations, null);

        for (int i = 0; i < 2; i++) {
            boolean previewDone =
                    previewListener[i].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
            assertTrue("Unable to start preview " + i, previewDone);
            mTextureView[i].setSurfaceTextureListener(null);
        }

        SystemClock.sleep(PREVIEW_TIME_MS);

        stopPreview(cameraId);
    }

    private void testSharedDeferredSurfacesByCamera(String cameraId) throws Exception {
        Size previewSize = getOrderedPreviewSizes(cameraId).get(0);
        CameraPreviewListener[] previewListener = new CameraPreviewListener[2];
        SurfaceTexture[] previewTexture = new SurfaceTexture[2];
        Surface[] surfaces = new Surface[2];

        // Create surface textures with the same size
        for (int i = 0; i < 2; i++) {
            previewListener[i] = new CameraPreviewListener();
            mTextureView[i].setSurfaceTextureListener(previewListener[i]);
            previewTexture[i] = getAvailableSurfaceTexture(
                    WAIT_FOR_COMMAND_TO_COMPLETE, mTextureView[i]);
            assertNotNull("Unable to get preview surface texture", previewTexture[i]);
            previewTexture[i].setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            // Correct the preview display rotation.
            updatePreviewDisplayRotation(previewSize, mTextureView[i]);
            surfaces[i] = new Surface(previewTexture[i]);
        }

        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();

        //
        // Create deferred outputConfiguration, addSurface, createCaptureSession, addSurface, and
        // finalizeOutputConfigurations.
        //

        OutputConfiguration surfaceSharedOutput = new OutputConfiguration(
                previewSize, SurfaceTexture.class);
        surfaceSharedOutput.enableSurfaceSharing();
        surfaceSharedOutput.addSurface(surfaces[0]);

        List<OutputConfiguration> outputConfigurations = new ArrayList<>();
        outputConfigurations.add(surfaceSharedOutput);

        // Run preview with one surface, and verify at least one frame is received.
        startPreviewWithConfigs(cameraId, outputConfigurations, null);
        boolean previewDone =
                previewListener[0].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
        assertTrue("Unable to start preview 0", previewDone);

        SystemClock.sleep(PREVIEW_TIME_MS);

        // Add deferred surface to the output configuration
        surfaceSharedOutput.addSurface(surfaces[1]);
        List<OutputConfiguration> deferredConfigs = new ArrayList<OutputConfiguration>();
        deferredConfigs.add(surfaceSharedOutput);

        // Run preview with both surfaces, and verify at least one frame is received for each
        // surface.
        updateOutputConfigs(cameraId, deferredConfigs, null);
        previewDone =
                previewListener[1].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
        assertTrue("Unable to start preview 1", previewDone);

        stopPreview(cameraId);

        previewListener[0].reset();
        previewListener[1].reset();

        //
        // Create outputConfiguration with a surface, createCaptureSession, addSurface, and
        // finalizeOutputConfigurations.
        //

        surfaceSharedOutput = new OutputConfiguration(
                OutputConfiguration.SURFACE_GROUP_ID_NONE, surfaces[0]);
        surfaceSharedOutput.enableSurfaceSharing();
        outputConfigurations.clear();
        outputConfigurations.add(surfaceSharedOutput);

        startPreviewWithConfigs(cameraId, outputConfigurations, null);
        previewDone =
                previewListener[0].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
        assertTrue("Unable to start preview 0", previewDone);

        // Add deferred surface to the output configuration, and continue running preview
        surfaceSharedOutput.addSurface(surfaces[1]);
        deferredConfigs.clear();
        deferredConfigs.add(surfaceSharedOutput);
        updateOutputConfigs(cameraId, deferredConfigs, null);
        previewDone =
                previewListener[1].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
        assertTrue("Unable to start preview 1", previewDone);

        SystemClock.sleep(PREVIEW_TIME_MS);
        stopPreview(cameraId);

        previewListener[0].reset();
        previewListener[1].reset();

        //
        // Create deferred output configuration, createCaptureSession, addSurface, addSurface, and
        // finalizeOutputConfigurations.

        surfaceSharedOutput = new OutputConfiguration(
                previewSize, SurfaceTexture.class);
        surfaceSharedOutput.enableSurfaceSharing();
        outputConfigurations.clear();
        outputConfigurations.add(surfaceSharedOutput);
        createSessionWithConfigs(cameraId, outputConfigurations);

        // Add 2 surfaces to the output configuration, and run preview
        surfaceSharedOutput.addSurface(surfaces[0]);
        surfaceSharedOutput.addSurface(surfaces[1]);
        deferredConfigs.clear();
        deferredConfigs.add(surfaceSharedOutput);
        updateOutputConfigs(cameraId, deferredConfigs, null);
        for (int i = 0; i < 2; i++) {
            previewDone =
                    previewListener[i].waitForPreviewDone(WAIT_FOR_COMMAND_TO_COMPLETE);
            assertTrue("Unable to start preview " + i, previewDone);
        }

        SystemClock.sleep(PREVIEW_TIME_MS);
        stopPreview(cameraId);
    }
}
