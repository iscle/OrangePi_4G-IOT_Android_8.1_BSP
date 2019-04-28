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

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;


/**
 * A minimum camera app.
 * To keep it simple: portrait mode only.
 */
public class DevCameraActivity extends Activity implements CameraInterface.MyCameraCallback, SurfaceHolder.Callback {
    private static final String TAG = "DevCamera_UI";

    private static final boolean LOG_FRAME_DATA = false;
    private static final int AF_TRIGGER_HOLD_MILLIS = 4000;
    private static final boolean STARTUP_FULL_YUV_ON = true;
    private static final boolean START_WITH_FRONT_CAMERA = false;

    private static final int PERMISSIONS_REQUEST_CAMERA = 1;
    private boolean mPermissionCheckActive = false;

    private SurfaceView mPreviewView;
    private SurfaceHolder mPreviewHolder;
    private PreviewOverlay mPreviewOverlay;
    private FrameLayout mPreviewFrame;

    private TextView mLabel1;
    private TextView mLabel2;
    private ToggleButton mToggleFrontCam; // Use front camera
    private ToggleButton mToggleYuvFull; // full YUV
    private ToggleButton mToggleYuvVga; // VGA YUV
    private ToggleButton mToggleRaw; // raw10
    private Button mButtonNoiseMode; // Noise reduction mode
    private Button mButtonEdgeModeReprocess; // Edge mode
    private Button mButtonNoiseModeReprocess; // Noise reduction mode for reprocessing
    private Button mButtonEdgeMode; // Edge mode for reprocessing
    private ToggleButton mToggleFace; // Face detection
    private ToggleButton mToggleShow3A; // 3A info
    private ToggleButton mToggleGyro; // Gyro
    private ToggleButton mToggleBurstJpeg;
    private ToggleButton mToggleSaveSdCard;
    private LinearLayout mReprocessingGroup;
    private Handler mMainHandler;
    private CameraInterface mCamera;

    // Used for saving JPEGs.
    private HandlerThread mUtilityThread;
    private Handler mUtilityHandler;

    // send null for initialization
    View.OnClickListener mTransferUiStateToCameraState = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // set capture flow.
            if (view == mToggleYuvFull || view == mToggleYuvVga || view == mToggleRaw ||
                    view == mButtonNoiseMode || view == mButtonEdgeMode || view == mToggleFace || view == null)
                mCamera.setCaptureFlow(
                    mToggleYuvFull.isChecked(),
                    mToggleYuvVga.isChecked(),
                    mToggleRaw.isChecked(),
                    view == mButtonNoiseMode, /* cycle noise reduction mode */
                    view == mButtonEdgeMode, /* cycle edge mode */
                    mToggleFace.isChecked()
            );
            // set reprocessing flow.
            if (view == mButtonNoiseModeReprocess || view == mButtonEdgeModeReprocess || view == null) {
                mCamera.setReprocessingFlow(view == mButtonNoiseModeReprocess, view == mButtonEdgeModeReprocess);
            }
            // set visibility of cluster of reprocessing controls.
            int reprocessingViz = mToggleYuvFull.isChecked() && mCamera.isReprocessingAvailable() ? View.VISIBLE : View.GONE;
            mReprocessingGroup.setVisibility(reprocessingViz);

            // if just turned off YUV1 stream, end burst.
            if (view == mToggleYuvFull && !mToggleYuvFull.isChecked()) {
                mToggleBurstJpeg.setChecked(false);
                mCamera.setBurst(false);
            }

            if (view == mToggleBurstJpeg) {
                mCamera.setBurst(mToggleBurstJpeg.isChecked());
            }

            if (view == mToggleShow3A || view == null) {
                mPreviewOverlay.show3AInfo(mToggleShow3A.isChecked());
            }
            if (view == mToggleGyro || view == null) {
                if (mToggleGyro.isChecked()) {
                    startGyroDisplay();
                } else {
                    stopGyroDisplay();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        CameraTimer.t0 = SystemClock.elapsedRealtime();

        if (checkPermissions()) {
            // Go speed racer.
            openCamera(START_WITH_FRONT_CAMERA);
        }

        // Initialize UI.
        setContentView(R.layout.activity_main);
        mLabel1 = (TextView) findViewById(R.id.label1);
        mLabel1.setText("Snappy initializing.");
        mLabel2 = (TextView) findViewById(R.id.label2);
        mLabel2.setText(" ...");
        Button mAfTriggerButton = (Button) findViewById(R.id.af_trigger);
        mToggleFrontCam = (ToggleButton) findViewById(R.id.toggle_front_cam);
        mToggleFrontCam.setChecked(START_WITH_FRONT_CAMERA);
        mToggleYuvFull = (ToggleButton) findViewById(R.id.toggle_yuv_full);
        mToggleYuvVga = (ToggleButton) findViewById(R.id.toggle_yuv_vga);
        mToggleRaw = (ToggleButton) findViewById(R.id.toggle_raw);
        mButtonNoiseMode = (Button) findViewById(R.id.button_noise);
        mButtonEdgeMode = (Button) findViewById(R.id.button_edge);
        mButtonNoiseModeReprocess = (Button) findViewById(R.id.button_noise_reprocess);
        mButtonEdgeModeReprocess = (Button) findViewById(R.id.button_edge_reprocess);

        mToggleFace = (ToggleButton) findViewById(R.id.toggle_face);
        mToggleShow3A = (ToggleButton) findViewById(R.id.toggle_show_3A);
        mToggleGyro = (ToggleButton) findViewById(R.id.toggle_show_gyro);
        Button mGetJpegButton = (Button) findViewById(R.id.jpeg_capture);
        Button mGalleryButton = (Button) findViewById(R.id.gallery);

        mToggleBurstJpeg = (ToggleButton) findViewById(R.id.toggle_burst_jpeg);
        mToggleSaveSdCard = (ToggleButton) findViewById(R.id.toggle_save_sdcard);
        mReprocessingGroup = (LinearLayout) findViewById(R.id.reprocessing_controls);
        mPreviewView = (SurfaceView) findViewById(R.id.preview_view);
        mPreviewHolder = mPreviewView.getHolder();
        mPreviewHolder.addCallback(this);
        mPreviewOverlay = (PreviewOverlay) findViewById(R.id.preview_overlay_view);
        mPreviewFrame = (FrameLayout) findViewById(R.id.preview_frame);

        // Set UI listeners.
        mAfTriggerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doAFScan();
            }
        });
        mGetJpegButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hitCaptureButton();
            }
        });
        mGalleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchPhotosViewer();
            }
        });
        mToggleFrontCam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG, "switchCamera()");
                CameraTimer.t0 = SystemClock.elapsedRealtime();
                // ToggleButton isChecked state will determine which camera is started.
                openCamera(mToggleFrontCam.isChecked());
                startCamera();
            }
        });
        mToggleYuvFull.setOnClickListener(mTransferUiStateToCameraState);
        mToggleYuvVga.setOnClickListener(mTransferUiStateToCameraState);
        mToggleRaw.setOnClickListener(mTransferUiStateToCameraState);
        mButtonNoiseMode.setOnClickListener(mTransferUiStateToCameraState);
        mButtonEdgeMode.setOnClickListener(mTransferUiStateToCameraState);
        mButtonNoiseModeReprocess.setOnClickListener(mTransferUiStateToCameraState);
        mButtonEdgeModeReprocess.setOnClickListener(mTransferUiStateToCameraState);
        mToggleFace.setOnClickListener(mTransferUiStateToCameraState);
        mToggleShow3A.setOnClickListener(mTransferUiStateToCameraState);
        mToggleGyro.setOnClickListener(mTransferUiStateToCameraState);
        mToggleBurstJpeg.setOnClickListener(mTransferUiStateToCameraState);
        mToggleSaveSdCard.setOnClickListener(mTransferUiStateToCameraState);
        mToggleSaveSdCard.setChecked(true);

        mMainHandler = new Handler(this.getApplicationContext().getMainLooper());

        // General utility thread for e.g. saving JPEGs.
        mUtilityThread = new HandlerThread("UtilityThread");
        mUtilityThread.start();
        mUtilityHandler = new Handler(mUtilityThread.getLooper());

        // --- PRINT REPORT ---
        //CameraDeviceReport.printReport(this, false);
        super.onCreate(savedInstanceState);
    }

    // Open camera. No UI required.
    private void openCamera(boolean frontCamera) {
        // Close previous camera if required.
        if (mCamera != null) {
            mCamera.closeCamera();
        }
        // --- SET UP CAMERA ---
        mCamera = new Api2Camera(this, frontCamera);
        mCamera.setCallback(this);
        mCamera.openCamera();
    }

    // Initialize camera related UI and start camera; call openCamera first.
    private void startCamera() {
        // --- SET UP USER INTERFACE ---
        mToggleYuvFull.setChecked(STARTUP_FULL_YUV_ON);
        mToggleFace.setChecked(true);
        mToggleRaw.setVisibility(mCamera.isRawAvailable() ? View.VISIBLE : View.GONE);
        mToggleShow3A.setChecked(true);
        mTransferUiStateToCameraState.onClick(null);

        // --- SET UP PREVIEW AND OPEN CAMERA ---

        if (mPreviewSurfaceValid) {
            mCamera.startPreview(mPreviewHolder.getSurface());
        } else {
            // Note that preview is rotated 90 degrees from camera. We just hard code this now.
            Size previewSize = mCamera.getPreviewSize();
            // Render in top 12 x 9 of 16 x 9 display.
            int renderHeight = 3 * displayHeight() / 4;
            int renderWidth = renderHeight * previewSize.getHeight() / previewSize.getWidth();
            int renderPad = (displayWidth() - renderWidth) / 2;

            mPreviewFrame.setPadding(renderPad, 0, 0, 0);
            mPreviewFrame.setLayoutParams(new LinearLayout.LayoutParams(renderWidth + renderPad, renderHeight));
            // setFixedSize() will trigger surfaceChanged() callback below, which will start preview.
            mPreviewHolder.setFixedSize(previewSize.getHeight(), previewSize.getWidth());
        }
    }

    boolean mPreviewSurfaceValid = false;

    @Override
    public synchronized void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.v(TAG, String.format("surfaceChanged: format=%x w=%d h=%d", format, width, height));
        if (checkPermissions()) {
            mPreviewSurfaceValid = true;
            mCamera.startPreview(mPreviewHolder.getSurface());
        }
    }

    Runnable mReturnToCafRunnable = new Runnable() {
        @Override
        public void run() {
            mCamera.setCAF();
        }
    };

    private void doAFScan() {
        mCamera.triggerAFScan();
        mMainHandler.removeCallbacks(mReturnToCafRunnable);
        mMainHandler.postDelayed(mReturnToCafRunnable, AF_TRIGGER_HOLD_MILLIS);
    }

    private int displayWidth() {
        DisplayMetrics metrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels;
    }

    private int displayHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        this.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    @Override
    public void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        // Leave screen on.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!checkPermissions()) return;

        // Can start camera now that we have the above initialized.
        if (mCamera == null) {
            openCamera(mToggleFrontCam.isChecked());
        }
        startCamera();
    }

    private boolean checkPermissions() {
        if (mPermissionCheckActive) return false;

        // Check for all runtime permissions
        if ((checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED )
            || (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            || (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)) {
            Log.i(TAG, "Requested camera/video permissions");
            requestPermissions(new String[] {
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_CAMERA);
            mPermissionCheckActive = true;
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        mPermissionCheckActive = false;
        if (requestCode == PERMISSIONS_REQUEST_CAMERA) {
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    Log.i(TAG, "At least one permission denied, can't continue: " + permissions[i]);
                    finish();
                    return;
                }
            }

            Log.i(TAG, "All permissions granted");
            openCamera(mToggleFrontCam.isChecked());
            startCamera();
        }
    }

    @Override
    public void onStop() {
        Log.v(TAG, "onStop");
        if (mCamera != null) {
            mCamera.closeCamera();
            mCamera = null;
        }

        // Cancel any pending AF operations.
        mMainHandler.removeCallbacks(mReturnToCafRunnable);
        stopGyroDisplay(); // No-op if not running.
        super.onStop();
    }

    public void noCamera2Full() {
        Toast toast = Toast.makeText(this, "WARNING: this camera does not support camera2 HARDWARE_LEVEL_FULL.", Toast.LENGTH_LONG);
        toast.setGravity(Gravity.TOP, 0, 0);
        toast.show();
    }

    @Override
    public void setNoiseEdgeText(final String nrMode, final String edgeMode) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mButtonNoiseMode.setText(nrMode);
                mButtonEdgeMode.setText(edgeMode);
            }
        });
    }

    @Override
    public void setNoiseEdgeTextForReprocessing(final String nrMode, final String edgeMode) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mButtonNoiseModeReprocess.setText(nrMode);
                mButtonEdgeModeReprocess.setText(edgeMode);
            }
        });
    }

    int mJpegCounter = 0;
    long mJpegMillis = 0;

    @Override
    public void jpegAvailable(final byte[] jpegData, final int x, final int y) {
        Log.v(TAG, "JPEG returned, size = " + jpegData.length);
        long now = SystemClock.elapsedRealtime();
        final long dt = mJpegMillis > 0 ? now - mJpegMillis : 0;
        mJpegMillis = now;

        if (mToggleSaveSdCard.isChecked()) {
            mUtilityHandler.post(new Runnable() {
                @Override
                public void run() {
                    final String result = MediaSaver.saveJpeg(getApplicationContext(), jpegData, getContentResolver());
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            fileNameToast(String.format("Saved %dx%d and %d bytes JPEG to %s in %d ms.", x, y, jpegData.length, result, dt));
                        }
                    });

                }
            });
        } else {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    fileNameToast(String.format("Processing JPEG #%d %dx%d and %d bytes in %d ms.", ++mJpegCounter, x, y, jpegData.length, dt));
                }
            });
        }
    }

    @Override
    public void receivedFirstFrame() {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mPreviewView.setBackgroundColor(Color.TRANSPARENT);
            }
        });
    }

    Toast mToast;

    public void fileNameToast(String s) {
        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(this, s, Toast.LENGTH_SHORT);
        mToast.setGravity(Gravity.TOP, 0, 0);
        mToast.show();
    }

    @Override
    public void frameDataAvailable(final NormalizedFace[] faces, final float normExposure, final float normLens, float fps, int iso, final int afState, int aeState, int awbState) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mPreviewOverlay.setFrameData(faces, normExposure, normLens, afState);
            }
        });
        // Build info string.
        String ae = aeStateToString(aeState);
        String af = afStateToString(afState);
        String awb = awbStateToString(awbState);
        final String info = String.format(" %2.0f FPS%5d ISO  AF:%s AE:%s AWB:%s", fps, iso, af, ae, awb);
        mLastInfo = info;

        if (LOG_FRAME_DATA && faces != null) {
            Log.v(TAG, "normExposure: " + normExposure);
            Log.v(TAG, "normLens: " + normLens);
            for (int i = 0; i < faces.length; ++i) {
                Log.v(TAG, "Face getBounds: " + faces[i].bounds);
                Log.v(TAG, "Face left eye: " + faces[i].leftEye);
                Log.v(TAG, "Face right eye: " + faces[i].rightEye);
                Log.v(TAG, "Face mouth: " + faces[i].mouth);
            }
        }

        // Status line
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mLabel1.setText(info);
            }
        });
    }

    Integer mTimeToFirstFrame = 0;
    Integer mHalWaitTime = 0;
    Float mDroppedFrameCount = 0f;
    String mLastInfo;

    @Override
    public void performanceDataAvailable(Integer timeToFirstFrame, Integer halWaitTime, Float droppedFrameCount) {
        if (timeToFirstFrame != null) {
            mTimeToFirstFrame = timeToFirstFrame;
        }
        if (halWaitTime != null) {
            mHalWaitTime = halWaitTime;
        }
        if (droppedFrameCount != null) {
            mDroppedFrameCount += droppedFrameCount;
        }
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                mLabel2.setText(String.format("TTP %dms  HAL %dms  Framedrops:%.2f", mTimeToFirstFrame, mHalWaitTime, mDroppedFrameCount));
            }
        });
    }

    // Hit capture button.
    private void hitCaptureButton() {
        Log.v(TAG, "hitCaptureButton");
        mCamera.takePicture();
    }

    // Hit Photos button.
    private void launchPhotosViewer() {
        Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
        intent.setType("image/*");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /*********************************
     * Gyro graphics overlay update. *
     *********************************/
    GyroOperations mGyroOperations;

    private void startGyroDisplay() {

        float[] fovs = mCamera.getFieldOfView();
        mPreviewOverlay.setFieldOfView(fovs[0], fovs[1]);
        mPreviewOverlay.setFacingAndOrientation(mToggleFrontCam.isChecked() ?
                CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK,
                mCamera.getOrientation());
        if (mGyroOperations == null) {
            SensorManager sensorManager = (SensorManager) getSystemService(this.SENSOR_SERVICE);
            mGyroOperations = new GyroOperations(sensorManager);
        }
        mGyroOperations.startListening(
                new GyroListener() {
                    @Override
                    public void updateGyroAngles(float[] gyroAngles) {
                        mPreviewOverlay.setGyroAngles(gyroAngles);
                    }
                }
        );

        mPreviewOverlay.showGyroGrid(true);
    }

    private void stopGyroDisplay() {
        if (mGyroOperations != null) {
            mGyroOperations.stopListening();
        }
        mPreviewOverlay.showGyroGrid(false);
    }


    /*******************************************
     * SurfaceView callbacks just for logging. *
     *******************************************/

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.v(TAG, "surfaceCreated");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.v(TAG, "surfaceDestroyed");
    }

    /*********************
     * UTILITY FUNCTIONS *
     *********************/

    private static String awbStateToString(int mode) {
        switch (mode) {
            case CaptureResult.CONTROL_AWB_STATE_INACTIVE:
                return "inactive";
            case CaptureResult.CONTROL_AWB_STATE_SEARCHING:
                return "searching";
            case CaptureResult.CONTROL_AWB_STATE_CONVERGED:
                return "converged";
            case CaptureResult.CONTROL_AWB_STATE_LOCKED:
                return "lock";
            default:
                return "unknown " + Integer.toString(mode);
        }
    }

    private static String aeStateToString(int mode) {
        switch (mode) {
            case CaptureResult.CONTROL_AE_STATE_INACTIVE:
                return "inactive";
            case CaptureResult.CONTROL_AE_STATE_SEARCHING:
                return "searching";
            case CaptureResult.CONTROL_AE_STATE_PRECAPTURE:
                return "precapture";
            case CaptureResult.CONTROL_AE_STATE_CONVERGED:
                return "converged";
            case CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED:
                return "flashReq";
            case CaptureResult.CONTROL_AE_STATE_LOCKED:
                return "lock";
            default:
                return "unknown " + Integer.toString(mode);
        }
    }

    private static String afStateToString(int mode) {
        switch (mode) {
            case CaptureResult.CONTROL_AF_STATE_INACTIVE:
                return "inactive";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN:
                return "passiveScan";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED:
                return "passiveFocused";
            case CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED:
                return "passiveUnfocused";
            case CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN:
                return "activeScan";
            case CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED:
                return "focusedLock";
            case CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED:
                return "notFocusedLock";
            default:
                return "unknown" + Integer.toString(mode);
        }
    }

}
