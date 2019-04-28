/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.devcamera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.InputConfiguration;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaActionSound;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.media.Image.Plane;

import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.lang.IndexOutOfBoundsException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;


/**
 * Api2Camera : a camera2 implementation
 *
 * The goal here is to make the simplest possible API2 camera,
 * where individual streams and capture options (e.g. edge enhancement,
 * noise reduction, face detection) can be toggled on and off.
 *
 */

public class Api2Camera implements CameraInterface, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "DevCamera_API2";

    // Nth frame to log; put 10^6 if you don't want logging.
    private static int LOG_NTH_FRAME = 30;
    // Log dropped frames. There are a log on Angler MDA32.
    private static boolean LOG_DROPPED_FRAMES = true;

    // IMPORTANT: Only one of these can be true:
    private static boolean SECOND_YUV_IMAGEREADER_STREAM = true;
    private static boolean SECOND_SURFACE_TEXTURE_STREAM = false;

    // Enable raw stream if available.
    private static boolean RAW_STREAM_ENABLE = true;
    // Use JPEG ImageReader and YUV ImageWriter if reprocessing is available
    private static final boolean USE_REPROCESSING_IF_AVAIL = true;

    // Whether we are continuously taking pictures, or not.
    boolean mIsBursting = false;
    // Last total capture result
    TotalCaptureResult mLastTotalCaptureResult;

    // ImageReader/Writer buffer sizes.
    private static final int YUV1_IMAGEREADER_SIZE = 8;
    private static final int YUV2_IMAGEREADER_SIZE = 8;
    private static final int RAW_IMAGEREADER_SIZE = 8;
    private static final int IMAGEWRITER_SIZE = 2;

    private CameraInfoCache mCameraInfoCache;
    private CameraManager mCameraManager;
    private CameraCaptureSession mCurrentCaptureSession;
    private MediaActionSound mMediaActionSound = new MediaActionSound();

    MyCameraCallback mMyCameraCallback;

    // Generally everything running on this thread & this module is *not thread safe*.
    private HandlerThread mOpsThread;
    private Handler mOpsHandler;
    private HandlerThread mInitThread;
    private Handler mInitHandler;
    private HandlerThread mJpegListenerThread;
    private Handler mJpegListenerHandler;

    Context mContext;
    boolean mCameraIsFront;
    SurfaceTexture mSurfaceTexture;
    Surface mSurfaceTextureSurface;

    private boolean mFirstFrameArrived;
    private ImageReader mYuv1ImageReader;
    private int mYuv1ImageCounter;
    // Handle to last received Image: allows ZSL to be implemented.
    private Image mYuv1LastReceivedImage = null;
    // Time at which reprocessing request went in (right now we are doing one at a time).
    private long mReprocessingRequestNanoTime;

    private ImageReader mJpegImageReader;
    private ImageReader mYuv2ImageReader;
    private int mYuv2ImageCounter;
    private ImageReader mRawImageReader;
    private int mRawImageCounter;
    private boolean mIsDepthCloudSupported = false;
    private ImageReader mDepthCloudImageReader;
    private int mDepthCloudImageCounter = 0;
    private static int STORE_NTH_DEPTH_CLOUD = 30;
    private static boolean DEPTH_CLOUD_STORE_ENABLED = false;

    // Starting the preview requires each of these 3 to be true/non-null:
    volatile private Surface mPreviewSurface;
    volatile private CameraDevice mCameraDevice;
    volatile boolean mAllThingsInitialized = false;

    /**
     * Constructor.
     */
    public Api2Camera(Context context, boolean useFrontCamera) {
        mContext = context;
        mCameraIsFront = useFrontCamera;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mCameraInfoCache = new CameraInfoCache(mCameraManager, useFrontCamera);

        // Create thread and handler for camera operations.
        mOpsThread = new HandlerThread("CameraOpsThread");
        mOpsThread.start();
        mOpsHandler = new Handler(mOpsThread.getLooper());

        // Create thread and handler for slow initialization operations.
        // Don't want to use camera operations thread because we want to time camera open carefully.
        mInitThread = new HandlerThread("CameraInitThread");
        mInitThread.start();
        mInitHandler = new Handler(mInitThread.getLooper());
        mInitHandler.post(new Runnable() {
            @Override
            public void run() {
                InitializeAllTheThings();
                mAllThingsInitialized = true;
                Log.v(TAG, "STARTUP_REQUIREMENT ImageReader initialization done.");
                tryToStartCaptureSession();
            }
        });

        // Set initial Noise and Edge modes.
        if (mCameraInfoCache.isHardwareLevelAtLeast(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3)) {
            // YUV streams.
            if (mCameraInfoCache.supportedModesContains(mCameraInfoCache.noiseModes,
                    CameraCharacteristics.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)) {
                mCaptureNoiseMode = CameraCharacteristics.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG;
            } else {
                mCaptureNoiseMode = CameraCharacteristics.NOISE_REDUCTION_MODE_FAST;
            }
            if (mCameraInfoCache.supportedModesContains(mCameraInfoCache.edgeModes,
                    CameraCharacteristics.EDGE_MODE_ZERO_SHUTTER_LAG)) {
                mCaptureEdgeMode = CameraCharacteristics.EDGE_MODE_ZERO_SHUTTER_LAG;
            } else {
                mCaptureEdgeMode = CameraCharacteristics.EDGE_MODE_FAST;
            }

            // Reprocessing.
            mReprocessingNoiseMode = CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY;
            mReprocessingEdgeMode = CameraCharacteristics.EDGE_MODE_HIGH_QUALITY;
        }

        if (null != mCameraInfoCache.getDepthCloudSize()) {
            mIsDepthCloudSupported = true;
        }
    }

    // Ugh, why is this stuff so slow?
    private void InitializeAllTheThings() {

        // Thread to handle returned JPEGs.
        mJpegListenerThread = new HandlerThread("CameraJpegThread");
        mJpegListenerThread.start();
        mJpegListenerHandler = new Handler(mJpegListenerThread.getLooper());

        // Create ImageReader to receive JPEG image buffers via reprocessing.
        mJpegImageReader = ImageReader.newInstance(
                mCameraInfoCache.getYuvStream1Size().getWidth(),
                mCameraInfoCache.getYuvStream1Size().getHeight(),
                ImageFormat.JPEG,
                2);
        mJpegImageReader.setOnImageAvailableListener(mJpegImageListener, mJpegListenerHandler);

        // Create ImageReader to receive YUV image buffers.
        mYuv1ImageReader = ImageReader.newInstance(
                mCameraInfoCache.getYuvStream1Size().getWidth(),
                mCameraInfoCache.getYuvStream1Size().getHeight(),
                ImageFormat.YUV_420_888,
                YUV1_IMAGEREADER_SIZE);
        mYuv1ImageReader.setOnImageAvailableListener(mYuv1ImageListener, mOpsHandler);

        if (mIsDepthCloudSupported) {
            mDepthCloudImageReader = ImageReader.newInstance(
                    mCameraInfoCache.getDepthCloudSize().getWidth(),
                    mCameraInfoCache.getDepthCloudSize().getHeight(),
                    ImageFormat.DEPTH_POINT_CLOUD, 2);
            mDepthCloudImageReader.setOnImageAvailableListener(mDepthCloudImageListener, mOpsHandler);
        }

        if (SECOND_YUV_IMAGEREADER_STREAM) {
            // Create ImageReader to receive YUV image buffers.
            mYuv2ImageReader = ImageReader.newInstance(
                    mCameraInfoCache.getYuvStream2Size().getWidth(),
                    mCameraInfoCache.getYuvStream2Size().getHeight(),
                    ImageFormat.YUV_420_888,
                    YUV2_IMAGEREADER_SIZE);
            mYuv2ImageReader.setOnImageAvailableListener(mYuv2ImageListener, mOpsHandler);
        }

        if (SECOND_SURFACE_TEXTURE_STREAM) {
            int[] textures = new int[1];
            // generate one texture pointer and bind it as an external texture.
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
            // No mip-mapping with camera source.
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_MIN_FILTER,
                    GL10.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            // Clamp to edge is only option.
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

            int texture_id = textures[0];
            mSurfaceTexture = new SurfaceTexture(texture_id);
            mSurfaceTexture.setDefaultBufferSize(320, 240);
            mSurfaceTexture.setOnFrameAvailableListener(this);
            mSurfaceTextureSurface = new Surface(mSurfaceTexture);
        }

        if (RAW_STREAM_ENABLE && mCameraInfoCache.rawAvailable()) {
            // Create ImageReader to receive thumbnail sized YUV image buffers.
            mRawImageReader = ImageReader.newInstance(
                    mCameraInfoCache.getRawStreamSize().getWidth(),
                    mCameraInfoCache.getRawStreamSize().getHeight(),
                    mCameraInfoCache.getRawFormat(),
                    RAW_IMAGEREADER_SIZE);
            mRawImageReader.setOnImageAvailableListener(mRawImageListener, mOpsHandler);
        }

        // Load click sound.
        mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);

    }

    public void setCallback(MyCameraCallback callback) {
        mMyCameraCallback = callback;
    }

    public void triggerAFScan() {
        Log.v(TAG, "AF trigger");
        issuePreviewCaptureRequest(true);
    }

    public void setCAF() {
        Log.v(TAG, "run CAF");
        issuePreviewCaptureRequest(false);
    }

    public void takePicture() {
        mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                runReprocessing();
            }
        });
    }

    public void onFrameAvailable (SurfaceTexture surfaceTexture) {
        Log.v(TAG, " onFrameAvailable(SurfaceTexture)");
    }

    public void setBurst(boolean go) {
        // if false to true transition.
        if (go && !mIsBursting) {
            takePicture();
        }
        mIsBursting = go;
    }

    public boolean isRawAvailable() {
        return mCameraInfoCache.rawAvailable();
    }

    public boolean isReprocessingAvailable() {
        return mCameraInfoCache.isYuvReprocessingAvailable();
    }

    @Override
    public Size getPreviewSize() {
        return mCameraInfoCache.getPreviewSize();
    }

    @Override
    public float[] getFieldOfView() {
        return mCameraInfoCache.getFieldOfView();
    }

    @Override
    public int getOrientation() {
        return mCameraInfoCache.sensorOrientation();
    }

    @Override
    public void openCamera() {
        // If API2 FULL mode is not available, display toast
        if (!mCameraInfoCache.isCamera2FullModeAvailable()) {
            mMyCameraCallback.noCamera2Full();
        }

        Log.v(TAG, "Opening camera " + mCameraInfoCache.getCameraId());
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                CameraTimer.t_open_start = SystemClock.elapsedRealtime();
                try {
                    mCameraManager.openCamera(mCameraInfoCache.getCameraId(), mCameraStateCallback, null);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Unable to openCamera().");
                }
            }
        });
    }

    @Override
    public void closeCamera() {
        // TODO: We are stalling main thread now which is bad.
        Log.v(TAG, "Closing camera " + mCameraInfoCache.getCameraId());
        if (mCameraDevice != null) {
            try {
                mCurrentCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                Log.e(TAG, "Could not abortCaptures().");
            }
            mCameraDevice.close();
        }
        mCurrentCaptureSession = null;
        Log.v(TAG, "Done closing camera " + mCameraInfoCache.getCameraId());
    }

    public void startPreview(final Surface surface) {
        Log.v(TAG, "STARTUP_REQUIREMENT preview Surface ready.");
        mPreviewSurface = surface;
        tryToStartCaptureSession();
    }

    private CameraDevice.StateCallback mCameraStateCallback = new LoggingCallbacks.DeviceStateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            CameraTimer.t_open_end = SystemClock.elapsedRealtime();
            mCameraDevice = camera;
            Log.v(TAG, "STARTUP_REQUIREMENT Done opening camera " + mCameraInfoCache.getCameraId() +
                    ". HAL open took: (" + (CameraTimer.t_open_end - CameraTimer.t_open_start) + " ms)");

            super.onOpened(camera);
            tryToStartCaptureSession();
        }
    };

    private void tryToStartCaptureSession() {
        if (mCameraDevice != null && mAllThingsInitialized && mPreviewSurface != null) {
            mOpsHandler.post(new Runnable() {
                @Override
                public void run() {
                    // It used to be: this needed to be posted on a Handler.
                    startCaptureSession();
                }
            });
        }
    }

    // Create CameraCaptureSession. Callback will start repeating request with current parameters.
    private void startCaptureSession() {
        CameraTimer.t_session_go = SystemClock.elapsedRealtime();

        Log.v(TAG, "Configuring session..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(4);

        outputSurfaces.add(mPreviewSurface);
        Log.v(TAG, "  .. added SurfaceView " + mCameraInfoCache.getPreviewSize().getWidth() +
                " x " + mCameraInfoCache.getPreviewSize().getHeight());

        outputSurfaces.add(mYuv1ImageReader.getSurface());
        Log.v(TAG, "  .. added YUV ImageReader " + mCameraInfoCache.getYuvStream1Size().getWidth() +
                " x " + mCameraInfoCache.getYuvStream1Size().getHeight());

        if (mIsDepthCloudSupported) {
            outputSurfaces.add(mDepthCloudImageReader.getSurface());
            Log.v(TAG, "  .. added Depth cloud ImageReader");
        }

        if (SECOND_YUV_IMAGEREADER_STREAM) {
            outputSurfaces.add(mYuv2ImageReader.getSurface());
            Log.v(TAG, "  .. added YUV ImageReader " + mCameraInfoCache.getYuvStream2Size().getWidth() +
                    " x " + mCameraInfoCache.getYuvStream2Size().getHeight());
        }

        if (SECOND_SURFACE_TEXTURE_STREAM) {
            outputSurfaces.add(mSurfaceTextureSurface);
            Log.v(TAG, "  .. added SurfaceTexture");
        }

        if (RAW_STREAM_ENABLE && mCameraInfoCache.rawAvailable()) {
            outputSurfaces.add(mRawImageReader.getSurface());
            Log.v(TAG, "  .. added Raw ImageReader " + mCameraInfoCache.getRawStreamSize().getWidth() +
                    " x " + mCameraInfoCache.getRawStreamSize().getHeight());
        }

        if (USE_REPROCESSING_IF_AVAIL && mCameraInfoCache.isYuvReprocessingAvailable()) {
            outputSurfaces.add(mJpegImageReader.getSurface());
            Log.v(TAG, "  .. added JPEG ImageReader " + mCameraInfoCache.getJpegStreamSize().getWidth() +
                    " x " + mCameraInfoCache.getJpegStreamSize().getHeight());
        }

        try {
            if (USE_REPROCESSING_IF_AVAIL && mCameraInfoCache.isYuvReprocessingAvailable()) {
                InputConfiguration inputConfig = new InputConfiguration(mCameraInfoCache.getYuvStream1Size().getWidth(),
                        mCameraInfoCache.getYuvStream1Size().getHeight(), ImageFormat.YUV_420_888);
                mCameraDevice.createReprocessableCaptureSession(inputConfig, outputSurfaces,
                        mSessionStateCallback, null);
                Log.v(TAG, "  Call to createReprocessableCaptureSession complete.");
            } else {
                mCameraDevice.createCaptureSession(outputSurfaces, mSessionStateCallback, null);
                Log.v(TAG, "  Call to createCaptureSession complete.");
            }

        } catch (CameraAccessException e) {
            Log.e(TAG, "Error configuring ISP.");
        }
    }

    ImageWriter mImageWriter;

    private CameraCaptureSession.StateCallback mSessionStateCallback = new LoggingCallbacks.SessionStateCallback() {
        @Override
        public void onReady(CameraCaptureSession session) {
            Log.v(TAG, "capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTimer.t_session_go) + " ms)");
            mCurrentCaptureSession = session;
            issuePreviewCaptureRequest(false);

            if (session.isReprocessable()) {
                mImageWriter = ImageWriter.newInstance(session.getInputSurface(), IMAGEWRITER_SIZE);
                mImageWriter.setOnImageReleasedListener(
                        new ImageWriter.OnImageReleasedListener() {
                            @Override
                            public void onImageReleased(ImageWriter writer) {
                                Log.v(TAG, "ImageWriter.OnImageReleasedListener onImageReleased()");
                            }
                        }, null);
                Log.v(TAG, "Created ImageWriter.");
            }
            super.onReady(session);
        }
    };

    // Variables to hold capture flow state.
    private boolean mCaptureYuv1 = false;
    private boolean mCaptureYuv2 = false;
    private boolean mCaptureRaw = false;
    private int mCaptureNoiseMode = CaptureRequest.NOISE_REDUCTION_MODE_FAST;
    private int mCaptureEdgeMode = CaptureRequest.EDGE_MODE_FAST;
    private boolean mCaptureFace = false;
    // Variables to hold reprocessing state.
    private int mReprocessingNoiseMode = CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY;
    private int mReprocessingEdgeMode = CaptureRequest.EDGE_MODE_HIGH_QUALITY;

    public void setCaptureFlow(Boolean yuv1, Boolean yuv2, Boolean raw10, Boolean nr, Boolean edge, Boolean face) {
        if (yuv1 != null) mCaptureYuv1 = yuv1;
        if (yuv2 != null) mCaptureYuv2 = yuv2;
        if (raw10 != null) mCaptureRaw = raw10 && RAW_STREAM_ENABLE;
        if (nr) {
            mCaptureNoiseMode = getNextMode(mCaptureNoiseMode, mCameraInfoCache.noiseModes);
        }
        if (edge) {
            mCaptureEdgeMode = getNextMode(mCaptureEdgeMode, mCameraInfoCache.edgeModes);
        }
        if (face != null) mCaptureFace = face;
        mMyCameraCallback.setNoiseEdgeText(
                "NR " + noiseModeToString(mCaptureNoiseMode),
                "Edge " + edgeModeToString(mCaptureEdgeMode)
        );

        if (mCurrentCaptureSession != null) {
            issuePreviewCaptureRequest(false);
        }
    }

    public void setReprocessingFlow(Boolean nr, Boolean edge) {
        if (nr) {
            mReprocessingNoiseMode = getNextMode(mReprocessingNoiseMode, mCameraInfoCache.noiseModes);
        }
        if (edge) {
            mReprocessingEdgeMode = getNextMode(mReprocessingEdgeMode, mCameraInfoCache.edgeModes);
        }
        mMyCameraCallback.setNoiseEdgeTextForReprocessing(
                "NR " + noiseModeToString(mReprocessingNoiseMode),
                "Edge " + edgeModeToString(mReprocessingEdgeMode)
        );
    }

    public void issuePreviewCaptureRequest(boolean AFtrigger) {
        CameraTimer.t_burst = SystemClock.elapsedRealtime();
        Log.v(TAG, "issuePreviewCaptureRequest...");
        try {
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b1.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE);
            b1.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY);
            if (AFtrigger) {
                b1.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            } else {
                b1.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }

            b1.set(CaptureRequest.NOISE_REDUCTION_MODE, mCaptureNoiseMode);
            b1.set(CaptureRequest.EDGE_MODE, mCaptureEdgeMode);
            b1.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mCaptureFace ? mCameraInfoCache.bestFaceDetectionMode() : CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);

            Log.v(TAG, "  .. NR=" + mCaptureNoiseMode + "  Edge=" + mCaptureEdgeMode + "  Face=" + mCaptureFace);

            if (mCaptureYuv1) {
                b1.addTarget(mYuv1ImageReader.getSurface());
                Log.v(TAG, "  .. YUV1 on");
            }

            if (mCaptureRaw) {
                b1.addTarget(mRawImageReader.getSurface());
            }

            b1.addTarget(mPreviewSurface);

            if (mIsDepthCloudSupported && !mCaptureYuv1 && !mCaptureYuv2 && !mCaptureRaw) {
                b1.addTarget(mDepthCloudImageReader.getSurface());
            }

            if (mCaptureYuv2) {
                if (SECOND_SURFACE_TEXTURE_STREAM) {
                    b1.addTarget(mSurfaceTextureSurface);
                }
                if (SECOND_YUV_IMAGEREADER_STREAM) {
                    b1.addTarget(mYuv2ImageReader.getSurface());
                }
                Log.v(TAG, "  .. YUV2 on");
            }

            if (AFtrigger) {
                b1.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                mCurrentCaptureSession.capture(b1.build(), mCaptureCallback, mOpsHandler);
                b1.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            }
            mCurrentCaptureSession.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    void runReprocessing() {
        if (mYuv1LastReceivedImage == null) {
            Log.e(TAG, "No YUV Image available.");
            return;
        }
        mImageWriter.queueInputImage(mYuv1LastReceivedImage);
        Log.v(TAG, "  Sent YUV1 image to ImageWriter.queueInputImage()");
        try {
            CaptureRequest.Builder b1 = mCameraDevice.createReprocessCaptureRequest(mLastTotalCaptureResult);
            // Todo: Read current orientation instead of just assuming device is in native orientation
            b1.set(CaptureRequest.JPEG_ORIENTATION, mCameraInfoCache.sensorOrientation());
            b1.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            b1.set(CaptureRequest.NOISE_REDUCTION_MODE, mReprocessingNoiseMode);
            b1.set(CaptureRequest.EDGE_MODE, mReprocessingEdgeMode);
            b1.addTarget(mJpegImageReader.getSurface());
            mCurrentCaptureSession.capture(b1.build(), mReprocessingCaptureCallback, mOpsHandler);
            mReprocessingRequestNanoTime = System.nanoTime();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
        mYuv1LastReceivedImage = null;
        Log.v(TAG, "  Reprocessing request submitted.");
    }


    /*********************************
     * onImageAvailable() processing *
     *********************************/

    ImageReader.OnImageAvailableListener mYuv1ImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV1");
                        return;
                    }
                    if (mYuv1LastReceivedImage != null) {
                        mYuv1LastReceivedImage.close();
                    }
                    mYuv1LastReceivedImage = img;
                    if (++mYuv1ImageCounter % LOG_NTH_FRAME == 0) {
                        Log.v(TAG, "YUV1 buffer available, Frame #=" + mYuv1ImageCounter + " w=" + img.getWidth() + " h=" + img.getHeight() + " time=" + img.getTimestamp());
                    }

                }
            };

    ImageReader.OnImageAvailableListener mDepthCloudImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader)
                        throws BufferUnderflowException, IndexOutOfBoundsException {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned Depth");
                        return;
                    }
                    Plane[] planes = img.getPlanes();
                    if (0 < planes.length) {
                        if (DEPTH_CLOUD_STORE_ENABLED) {
                            if ((mDepthCloudImageCounter % STORE_NTH_DEPTH_CLOUD) == 0) {
                                ByteBuffer b = planes[0].getBuffer();
                                MediaSaver.saveDepth(mContext, b);
                            }
                        }
                    } else {
                        Log.e(TAG, "Depth buffer with empty planes!");
                    }
                    img.close();
                    mDepthCloudImageCounter++;
                }
            };

    ImageReader.OnImageAvailableListener mJpegImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned JPEG");
                        return;
                    }
                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();
                    long dt = System.nanoTime() - mReprocessingRequestNanoTime;
                    Log.v(TAG, String.format("JPEG buffer available, w=%d h=%d time=%d size=%d dt=%.1f ms  ISO=%d",
                            img.getWidth(), img.getHeight(), img.getTimestamp(), buffer.capacity(), 0.000001 * dt, mLastIso));
                    // Save JPEG on the utility thread,
                    final byte[] jpegBuf;
                    if (buffer.hasArray()) {
                        jpegBuf = buffer.array();
                    } else {
                        jpegBuf = new byte[buffer.capacity()];
                        buffer.get(jpegBuf);
                    }
                    mMyCameraCallback.jpegAvailable(jpegBuf, img.getWidth(), img.getHeight());
                    img.close();

                    // take (reprocess) another picture right away if bursting.
                    if (mIsBursting) {
                        takePicture();
                    }
                }
            };


    ImageReader.OnImageAvailableListener mYuv2ImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV2");
                    } else {
                        if (++mYuv2ImageCounter % LOG_NTH_FRAME == 0) {
                            Log.v(TAG, "YUV2 buffer available, Frame #=" + mYuv2ImageCounter + " w=" + img.getWidth() + " h=" + img.getHeight() + " time=" + img.getTimestamp());
                        }
                        img.close();
                    }
                }
            };


    ImageReader.OnImageAvailableListener mRawImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    final Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned RAW");
                    } else {
                        if (++mRawImageCounter % LOG_NTH_FRAME == 0) {
                            Image.Plane plane0 = img.getPlanes()[0];
                            final ByteBuffer buffer = plane0.getBuffer();
                            Log.v(TAG, "Raw buffer available, Frame #=" + mRawImageCounter + "w=" + img.getWidth()
                                    + " h=" + img.getHeight()
                                    + " format=" + CameraDeviceReport.getFormatName(img.getFormat())
                                    + " time=" + img.getTimestamp()
                                    + " size=" + buffer.capacity()
                                    + " getRowStride()=" + plane0.getRowStride());
                        }
                        img.close();
                    }
                }
            };

    /*************************************
     * CaptureResult metadata processing *
     *************************************/

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new LoggingCallbacks.SessionCaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (!mFirstFrameArrived) {
                mFirstFrameArrived = true;
                long now = SystemClock.elapsedRealtime();
                long dt = now - CameraTimer.t0;
                long camera_dt = now - CameraTimer.t_session_go + CameraTimer.t_open_end - CameraTimer.t_open_start;
                long repeating_req_dt = now - CameraTimer.t_burst;
                Log.v(TAG, "App control to first frame: (" + dt + " ms)");
                Log.v(TAG, "HAL request to first frame: (" + repeating_req_dt + " ms) " + " Total HAL wait: (" + camera_dt + " ms)");
                mMyCameraCallback.receivedFirstFrame();
                mMyCameraCallback.performanceDataAvailable((int) dt, (int) camera_dt, null);
            }
            publishFrameData(result);
            // Used for reprocessing.
            mLastTotalCaptureResult = result;
            super.onCaptureCompleted(session, request, result);
        }
    };

    // Reprocessing capture completed.
    private CameraCaptureSession.CaptureCallback mReprocessingCaptureCallback = new LoggingCallbacks.SessionCaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Log.v(TAG, "Reprocessing onCaptureCompleted()");
        }
    };

    private static double SHORT_LOG_EXPOSURE = Math.log10(1000000000 / 10000); // 1/10000 second
    private static double LONG_LOG_EXPOSURE = Math.log10(1000000000 / 10); // 1/10 second
    public int FPS_CALC_LOOKBACK = 15;
    private LinkedList<Long> mFrameTimes = new LinkedList<Long>();

    private void publishFrameData(TotalCaptureResult result) {
        // Faces.
        final Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        NormalizedFace[] newFaces = new NormalizedFace[faces.length];
        if (faces.length > 0) {
            int offX = mCameraInfoCache.faceOffsetX();
            int offY = mCameraInfoCache.faceOffsetY();
            int dX = mCameraInfoCache.activeAreaWidth() - 2 * offX;
            int dY = mCameraInfoCache.activeAreaHeight() - 2 * offY;
            if (mCameraInfoCache.IS_NEXUS_6 && mCameraIsFront) {
                // Front camera on Nexus 6 is currently 16 x 9 cropped to 4 x 3.
                // TODO: Generalize this.
                int cropOffset = dX / 8;
                dX -= 2 * cropOffset;
                offX += cropOffset;
            }
            int orientation = mCameraInfoCache.sensorOrientation();
            for (int i = 0; i < faces.length; ++i) {
                newFaces[i] = new NormalizedFace(faces[i], dX, dY, offX, offY);
                if (mCameraIsFront && orientation == 90) {
                    newFaces[i].mirrorInY();
                }
                if (mCameraIsFront && orientation == 270) {
                    newFaces[i].mirrorInX();
                }
                if (!mCameraIsFront && orientation == 270) {
                    newFaces[i].mirrorInX();
                    newFaces[i].mirrorInY();
                }
            }
        }

        // Normalized lens and exposure coordinates.
        double rm = Math.log10(result.get(CaptureResult.SENSOR_EXPOSURE_TIME));
        float normExposure = (float) ((rm - SHORT_LOG_EXPOSURE) / (LONG_LOG_EXPOSURE - SHORT_LOG_EXPOSURE));
        float normLensPos = (mCameraInfoCache.getDiopterHi() - result.get(CaptureResult.LENS_FOCUS_DISTANCE)) / (mCameraInfoCache.getDiopterHi() - mCameraInfoCache.getDiopterLow());
        mLastIso = result.get(CaptureResult.SENSOR_SENSITIVITY);

        // Update frame arrival history.
        mFrameTimes.add(result.get(CaptureResult.SENSOR_TIMESTAMP));
        if (mFrameTimes.size() > FPS_CALC_LOOKBACK) {
            mFrameTimes.removeFirst();
        }

        // Frame drop detector
        {
            float frameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
            if (mFrameTimes.size() > 1) {
                long dt = result.get(CaptureResult.SENSOR_TIMESTAMP) - mFrameTimes.get(mFrameTimes.size()-2);
                if (dt > 3 * frameDuration / 2 && LOG_DROPPED_FRAMES) {
                    float drops = (dt * 1f / frameDuration) - 1f;
                    Log.e(TAG, String.format("dropped %.2f frames", drops));
                    mMyCameraCallback.performanceDataAvailable(null, null, drops);
                }
            }
        }

        // FPS calc.
        float fps = 0;
        if (mFrameTimes.size() > 1) {
            long dt = mFrameTimes.getLast() - mFrameTimes.getFirst();
            fps = (mFrameTimes.size() - 1) * 1000000000f / dt;
            fps = (float) Math.floor(fps + 0.1); // round to nearest whole number, ish.
        }

        // Do callback.
        if (mMyCameraCallback != null) {
            mMyCameraCallback.frameDataAvailable(newFaces, normExposure, normLensPos, fps,
                    (int) mLastIso, result.get(CaptureResult.CONTROL_AF_STATE), result.get(CaptureResult.CONTROL_AE_STATE), result.get(CaptureResult.CONTROL_AWB_STATE));
        } else {
            Log.v(TAG, "mMyCameraCallbacks is null!!.");
        }
    }

    long mLastIso = 0;

    /*********************
     * UTILITY FUNCTIONS *
     *********************/

    /**
     * Return the next mode after currentMode in supportedModes, wrapping to
     * start of mode list if currentMode is last.  Returns currentMode if it is not found in
     * supportedModes.
     *
     * @param currentMode
     * @param supportedModes
     * @return next mode after currentMode in supportedModes
     */
    private int getNextMode(int currentMode, int[] supportedModes) {
        boolean getNext = false;
        for (int m : supportedModes) {
            if (getNext) {
                return m;
            }
            if (m == currentMode) {
                getNext = true;
            }
        }
        if (getNext) {
            return supportedModes[0];
        }
        // Can't find mode in list
        return currentMode;
    }

    private static String edgeModeToString(int mode) {
        switch (mode) {
            case CaptureRequest.EDGE_MODE_OFF:
                return "OFF";
            case CaptureRequest.EDGE_MODE_FAST:
                return "FAST";
            case CaptureRequest.EDGE_MODE_HIGH_QUALITY:
                return "HiQ";
            case CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG:
                return "ZSL";
        }
        return Integer.toString(mode);
    }

    private static String noiseModeToString(int mode) {
        switch (mode) {
            case CaptureRequest.NOISE_REDUCTION_MODE_OFF:
                return "OFF";
            case CaptureRequest.NOISE_REDUCTION_MODE_FAST:
                return "FAST";
            case CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY:
                return "HiQ";
            case CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL:
                return "MIN";
            case CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG:
                return "ZSL";
        }
        return Integer.toString(mode);
    }
}
