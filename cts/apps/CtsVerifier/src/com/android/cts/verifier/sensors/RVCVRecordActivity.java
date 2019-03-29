/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.cts.verifier.sensors;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.cts.verifier.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ----------------------------------------------------------------------

/**
 *  An activity that does recording of the camera video and rotation vector data at the same time.
 */
public class RVCVRecordActivity extends Activity {
    private static final String TAG = "RVCVRecordActivity";
    private static final boolean LOCAL_LOGV = false;

    private MotionIndicatorView mIndicatorView;

    private SoundPool mSoundPool;
    private Map<String, Integer> mSoundMap;

    private File mRecordDir;
    private RecordProcedureController mController;
    private VideoRecorder           mVideoRecorder;
    private RVSensorLogger          mRVSensorLogger;
    private CoverageManager         mCoverManager;
    private CameraContext mCameraContext;

    public static final int AXIS_NONE = 0;
    public static final int AXIS_ALL = SensorManager.AXIS_X +
                                       SensorManager.AXIS_Y +
                                       SensorManager.AXIS_Z;

    // For Rotation Vector algorithm research use
    private final static boolean     LOG_RAW_SENSORS = false;
    private RawSensorLogger          mRawSensorLogger;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // inflate xml
        setContentView(R.layout.cam_preview_overlay);

        // locate views
        mIndicatorView = (MotionIndicatorView) findViewById(R.id.cam_indicator);

        initStoragePath();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mController.quit();

        mCameraContext.end();
        endSoundPool();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // delay the initialization as much as possible
        init();
    }

    /** display toast message
     *
     * @param msg Message content
     */
    private void message(String msg) {

        Context context = getApplicationContext();
        int duration = Toast.LENGTH_SHORT;

        Toast toast = Toast.makeText(context, msg, duration);
        toast.show();
    }

    /**
     *  Initialize components
     *
     */
    private void init() {
        mCameraContext = new CameraContext();
        mCameraContext.init();

        mCoverManager = new CoverageManager();
        mIndicatorView.setDataProvider(
                mCoverManager.getAxis(SensorManager.AXIS_X),
                mCoverManager.getAxis(SensorManager.AXIS_Y),
                mCoverManager.getAxis(SensorManager.AXIS_Z)  );

        initSoundPool();
        mRVSensorLogger = new RVSensorLogger(this);

        mVideoRecorder = new VideoRecorder(mCameraContext.getCamera(), mCameraContext.getProfile());

        if (LOG_RAW_SENSORS) {
            mRawSensorLogger = new RawSensorLogger(mRecordDir);
        }

        mController = new RecordProcedureController(this);
    }

    /**
     * Notify recording is completed. This is the successful exit.
     */
    public void notifyComplete() {
        message("Capture completed!");

        Uri resultUri = Uri.fromFile(mRecordDir);
        Intent result = new Intent();
        result.setData(resultUri);
        setResult(Activity.RESULT_OK, result);

        finish();
    }

    /**
     * Notify the user what to do next in text
     *
     * @param axis SensorManager.AXIS_X or SensorManager.AXIS_Y or SensorManager.AXIS_Z
     */
    private void notifyPrompt(int axis) {
        // It is not XYZ because of earlier design have different definition of
        // X and Y
        final String axisName = "YXZ";

        message("Manipulate the device in " + axisName.charAt(axis - 1) +
                " axis (as illustrated) about the pattern.");
    }

    /**
     *  Ask indicator view to redraw
     */
    private void redrawIndicator() {
        mIndicatorView.invalidate();
    }

    /**
     * Switch to a different axis for display and logging
     * @param axis
     */
    private void switchAxis(int axis) {
        ImageView imageView = (ImageView) findViewById(R.id.cam_overlay);

        final int [] prompts = {R.drawable.prompt_x, R.drawable.prompt_y, R.drawable.prompt_z};

        if (axis >=SensorManager.AXIS_X && axis <=SensorManager.AXIS_Z) {
            imageView.setImageResource(prompts[axis-1]);
            mIndicatorView.enableAxis(axis);
            mRVSensorLogger.updateRegister(mCoverManager.getAxis(axis), axis);
            notifyPrompt(axis);
        } else {
            imageView.setImageDrawable(null);
            mIndicatorView.enableAxis(AXIS_NONE);
        }
        redrawIndicator();
    }

    /**
     * Asynchronized way to call switchAxis. Use this if caller is not on UI thread.
     * @param axis @param axis SensorManager.AXIS_X or SensorManager.AXIS_Y or SensorManager.AXIS_Z
     */
    public void switchAxisAsync(int axis) {
        // intended to be called from a non-UI thread
        final int fAxis = axis;
        runOnUiThread(new Runnable() {
            public void run() {
                // UI code goes here
                switchAxis(fAxis);
            }
        });
    }

    /**
     * Initialize sound pool for user notification
     */
    private void initSoundPool() {
        mSoundPool = new SoundPool(1 /*maxStreams*/, AudioManager.STREAM_MUSIC, 0);
        mSoundMap = new HashMap<>();

        // TODO: add different sound into this
        mSoundMap.put("start", mSoundPool.load(this, R.raw.start_axis, 1));
        mSoundMap.put("end", mSoundPool.load(this, R.raw.next_axis, 1));
        mSoundMap.put("half-way", mSoundPool.load(this, R.raw.half_way, 1));
    }
    private void endSoundPool() {
        mSoundPool.release();
    }

    /**
     * Play notify sound to user
     * @param name name of the sound to be played
     */
    public void playNotifySound(String name) {
        Integer id = mSoundMap.get(name);
        if (id != null) {
            mSoundPool.play(id.intValue(), 0.75f/*left vol*/, 0.75f/*right vol*/, 0 /*priority*/,
                    0/*loop play*/, 1/*rate*/);
        }
    }

    /**
     * Start the sensor recording
     */
    public void startRecordSensor() {
        runOnUiThread(new Runnable() {
            public void run() {
                mRVSensorLogger.init();
                if (LOG_RAW_SENSORS) {
                    mRawSensorLogger.init();
                }
            }
        });
    }

    /**
     * Stop the sensor recording
     */
    public void stopRecordSensor() {
        runOnUiThread(new Runnable() {
            public void run() {
                mRVSensorLogger.end();
                if (LOG_RAW_SENSORS) {
                    mRawSensorLogger.end();
                }
            }
        });
    }

    /**
     * Start video recording
     */
    public void startRecordVideo() {
        mVideoRecorder.init();
    }

    /**
     * Stop video recording
     */
    public void stopRecordVideo() {
        mVideoRecorder.end();
    }

    /**
     * Wait until a sensor recording for a certain axis is fully covered
     * @param axis
     */
    public void waitUntilCovered(int axis) {
        mCoverManager.waitUntilCovered(axis);
    }

    /**
     * Wait until a sensor recording for a certain axis is halfway covered
     * @param axis
     */
    public void waitUntilHalfCovered(int axis) {
        mCoverManager.waitUntilHalfCovered(axis);
    }

    /**
     *
     */
    private void initStoragePath() {
        File rxcvRecDataDir = new File(Environment.getExternalStorageDirectory(),"RVCVRecData");

        // Create the storage directory if it does not exist
        if (! rxcvRecDataDir.exists()) {
            if (! rxcvRecDataDir.mkdirs()) {
                Log.e(TAG, "failed to create main data directory");
            }
        }

        mRecordDir = new File(rxcvRecDataDir, new SimpleDateFormat("yyMMdd-hhmmss").format(new Date()));

        if (! mRecordDir.mkdirs()) {
            Log.e(TAG, "failed to create rec data directory");
        }
    }

    /**
     * Get the sensor log file path
     * @return Path of the sensor log file
     */
    public String getSensorLogFilePath() {
        return new File(mRecordDir, "sensor.log").getPath();
    }

    /**
     * Get the video recording file path
     * @return Path of the video recording file
     */
    public String getVideoRecFilePath() {
        return new File(mRecordDir, "video.mp4").getPath();
    }

    /**
     * Write out important camera/video information to a JSON file
     * @param width         width of frame
     * @param height        height of frame
     * @param frameRate     frame rate in fps
     * @param fovW          field of view in width direction
     * @param fovH          field of view in height direction
     */
    public void writeVideoMetaInfo(int width, int height, float frameRate, float fovW, float fovH) {
        try {
            JsonWriter writer =
                    new JsonWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(
                                        new File(mRecordDir, "videometa.json").getPath()
                                )
                        )
                    );
            writer.beginObject();
            writer.name("fovW").value(fovW);
            writer.name("fovH").value(fovH);
            writer.name("width").value(width);
            writer.name("height").value(height);
            writer.name("frameRate").value(frameRate);
            writer.endObject();

            writer.close();
        }catch (FileNotFoundException e) {
            // Not very likely to happen
            e.printStackTrace();
        }catch (IOException e) {
            // do nothing
            e.printStackTrace();
            Log.e(TAG, "Writing video meta data failed.");
        }
    }

    /**
     * Camera preview control class
     */
    class CameraContext {
        private Camera mCamera;
        private CamcorderProfile mProfile;
        private Camera.CameraInfo mCameraInfo;

        private int [] mPreferredProfiles = {
                CamcorderProfile.QUALITY_480P,  // smaller -> faster
                CamcorderProfile.QUALITY_720P,
                CamcorderProfile.QUALITY_1080P,
                CamcorderProfile.QUALITY_HIGH // existence guaranteed
        };

        private String [] mPreferredFocusMode = {
                Camera.Parameters.FOCUS_MODE_FIXED,
                Camera.Parameters.FOCUS_MODE_INFINITY,
                // the following two modes are more likely to mess up recording
                // but they are still better than FOCUS_MODE_AUTO, which requires
                // calling autoFocus explicitly to focus.
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
        };

        CameraContext() {
            try {
                mCamera = Camera.open(); // attempt to get a default Camera instance (0)
                mProfile = null;
                if (mCamera != null) {
                    mCameraInfo = new Camera.CameraInfo();
                    Camera.getCameraInfo(0, mCameraInfo);
                    setupCamera();
                }
            }
            catch (Exception e){
                // Camera is not available (in use or does not exist)
                Log.e(TAG, "Cannot obtain Camera!");
            }
        }

        /**
         * Find a preferred camera profile and set preview and picture size property accordingly.
         */
        void setupCamera() {
            CamcorderProfile profile = null;
            boolean isSetNeeded = false;
            Camera.Parameters param = mCamera.getParameters();
            List<Camera.Size> pre_sz = param.getSupportedPreviewSizes();
            List<Camera.Size> pic_sz = param.getSupportedPictureSizes();

            for (int i : mPreferredProfiles) {
                if (CamcorderProfile.hasProfile(i)) {
                    profile = CamcorderProfile.get(i);

                    int valid = 0;
                    for (Camera.Size j : pre_sz) {
                        if (j.width == profile.videoFrameWidth &&
                                j.height == profile.videoFrameHeight) {
                            ++valid;
                            break;
                        }
                    }
                    for (Camera.Size j : pic_sz) {
                        if (j.width == profile.videoFrameWidth &&
                                j.height == profile.videoFrameHeight) {
                            ++valid;
                            break;
                        }
                    }
                    if (valid == 2) {
                        param.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
                        param.setPictureSize(profile.videoFrameWidth, profile.videoFrameHeight);
                        isSetNeeded = true;
                        break;
                    } else {
                        profile = null;
                    }
                }
            }

            for (String i : mPreferredFocusMode) {
                if (param.getSupportedFocusModes().contains(i)){
                    param.setFocusMode(i);
                    isSetNeeded = true;
                    break;
                }
            }

            if (isSetNeeded) {
                mCamera.setParameters(param);
            }

            if (profile != null) {
                param = mCamera.getParameters(); //acquire proper fov after change the picture size
                float fovW = param.getHorizontalViewAngle();
                float fovH = param.getVerticalViewAngle();
                writeVideoMetaInfo(profile.videoFrameWidth, profile.videoFrameHeight,
                        profile.videoFrameRate, fovW, fovH);
            } else {
                Log.e(TAG, "Cannot find a proper video profile");
            }
            mProfile = profile;

        }


        /**
         * Get sensor information of the camera being used
         */
        public Camera.CameraInfo getCameraInfo() {
            return mCameraInfo;
        }

        /**
         * Get the camera to be previewed
         * @return Reference to Camera used
         */
        public Camera getCamera() {
            return mCamera;
        }

        /**
         * Get the camera profile to be used
         * @return Reference to Camera profile
         */
        public CamcorderProfile getProfile() {
            return mProfile;
        }

        /**
         * Setup the camera
         */
        public void init() {
            if (mCamera != null) {
                double alpha = mCamera.getParameters().getHorizontalViewAngle()*Math.PI/180.0;
                int width = mProfile.videoFrameWidth;
                double fx = width/2/Math.tan(alpha/2.0);

                if (LOCAL_LOGV) Log.v(TAG, "View angle="
                        + mCamera.getParameters().getHorizontalViewAngle() +"  Estimated fx = "+fx);

                RVCVCameraPreview cameraPreview =
                        (RVCVCameraPreview) findViewById(R.id.cam_preview);
                cameraPreview.init(mCamera,
                        (float)mProfile.videoFrameWidth/mProfile.videoFrameHeight,
                        mCameraInfo.orientation);
            } else {
                message("Cannot open camera!");
                finish();
            }
        }

        /**
         * End the camera preview
         */
        public void end() {
            if (mCamera != null) {
                mCamera.release();        // release the camera for other applications
                mCamera = null;
            }
        }
    }

    /**
     * Manage a set of RangeCoveredRegister objects
     */
    class CoverageManager {
        // settings
        private final int MAX_TILT_ANGLE = 50; // +/- 50
        //private final int REQUIRED_TILT_ANGLE = 50; // +/- 50
        private final int TILT_ANGLE_STEP = 5; // 5 degree(s) per step
        private final int YAW_ANGLE_STEP = 10; // 10 degree(s) per step

        RangeCoveredRegister[] mAxisCovered;

        CoverageManager() {
            mAxisCovered = new RangeCoveredRegister[3];
            // X AXIS
            mAxisCovered[0] = new RangeCoveredRegister(
                    -MAX_TILT_ANGLE, +MAX_TILT_ANGLE, TILT_ANGLE_STEP);
            // Y AXIS
            mAxisCovered[1] = new RangeCoveredRegister(
                    -MAX_TILT_ANGLE, +MAX_TILT_ANGLE, TILT_ANGLE_STEP);
            // Z AXIS
            mAxisCovered[2] = new RangeCoveredRegister(YAW_ANGLE_STEP);
        }

        public RangeCoveredRegister getAxis(int axis) {
            // SensorManager.AXIS_X = 1, need offset -1 for mAxisCovered array
            return mAxisCovered[axis-1];
        }

        public void waitUntilHalfCovered(int axis) {
            if (axis == SensorManager.AXIS_Z) {
                waitUntilCovered(axis);
            }

            // SensorManager.AXIS_X = 1, need offset -1 for mAxisCovered array
            while(!(mAxisCovered[axis-1].isRangeCovered(-MAX_TILT_ANGLE, -MAX_TILT_ANGLE/2) ||
                        mAxisCovered[axis-1].isRangeCovered(MAX_TILT_ANGLE/2, MAX_TILT_ANGLE) ) ) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (LOCAL_LOGV) {
                        Log.v(TAG, "waitUntilHalfCovered axis = "+ axis + " is interrupted");
                    }
                    Thread.currentThread().interrupt();
                }
            }
        }

        public void waitUntilCovered(int axis) {
            // SensorManager.AXIS_X = 1, need offset -1 for mAxisCovered array
            while(!mAxisCovered[axis-1].isFullyCovered()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    if (LOCAL_LOGV) {
                        Log.v(TAG, "waitUntilCovered axis = "+ axis + " is interrupted");
                    }
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * A class controls the video recording
     */
    class VideoRecorder
    {
        private MediaRecorder mRecorder;
        private CamcorderProfile mProfile;
        private Camera mCamera;
        private boolean mRunning = false;

        VideoRecorder(Camera camera, CamcorderProfile profile){
            mCamera = camera;
            mProfile = profile;
        }

        /**
         * Initialize and start recording
         */
        public void init() {
            if (mCamera == null  || mProfile ==null){
                return;
            }

            mRecorder = new MediaRecorder();
            mCamera.unlock();
            mRecorder.setCamera(mCamera);

            mRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);

            mRecorder.setProfile(mProfile);

            try {
                mRecorder.setOutputFile(getVideoRecFilePath());
                mRecorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "Preparation for recording failed.");
                return;
            }

            try {
                mRecorder.start();
            } catch (RuntimeException e) {
                Log.e(TAG, "Starting recording failed.");
                mRecorder.reset();
                mRecorder.release();
                mCamera.lock();
                return;
            }
            mRunning = true;
        }

        /**
         * Stop recording
         */
        public void end() {
            if (mRunning) {
                try {
                    mRecorder.stop();
                    mRecorder.reset();
                    mRecorder.release();
                    mCamera.lock();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    Log.e(TAG, "Runtime error in stopping recording.");
                }
            }
            mRecorder = null;
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     *  Log all raw sensor readings, for Rotation Vector sensor algorithms research
     */
    class RawSensorLogger implements SensorEventListener {
        private final String TAG = "RawSensorLogger";

        private final static int SENSOR_RATE = SensorManager.SENSOR_DELAY_FASTEST;
        private File mRecPath;

        SensorManager mSensorManager;
        Sensor mAccSensor, mGyroSensor, mMagSensor;
        OutputStreamWriter mAccLogWriter, mGyroLogWriter, mMagLogWriter;

        private float[] mRTemp = new float[16];

        RawSensorLogger(File recPath) {
            mRecPath = recPath;
        }

        /**
         * Initialize and start recording
         */
        public void init() {
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

            mAccSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
            mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);

            mSensorManager.registerListener(this, mAccSensor, SENSOR_RATE);
            mSensorManager.registerListener(this, mGyroSensor, SENSOR_RATE);
            mSensorManager.registerListener(this, mMagSensor, SENSOR_RATE);

            try {
                mAccLogWriter= new OutputStreamWriter(
                        new FileOutputStream(new File(mRecPath, "raw_acc.log")));
                mGyroLogWriter= new OutputStreamWriter(
                        new FileOutputStream(new File(mRecPath, "raw_uncal_gyro.log")));
                mMagLogWriter= new OutputStreamWriter(
                        new FileOutputStream(new File(mRecPath, "raw_uncal_mag.log")));

            } catch (FileNotFoundException e) {
                Log.e(TAG, "Sensor log file open failed: " + e.toString());
            }
        }

        /**
         * Stop recording and clean up
         */
        public void end() {
            mSensorManager.flush(this);
            mSensorManager.unregisterListener(this);

            try {
                if (mAccLogWriter != null) {
                    OutputStreamWriter writer = mAccLogWriter;
                    mAccLogWriter = null;
                    writer.close();
                }
                if (mGyroLogWriter != null) {
                    OutputStreamWriter writer = mGyroLogWriter;
                    mGyroLogWriter = null;
                    writer.close();
                }
                if (mMagLogWriter != null) {
                    OutputStreamWriter writer = mMagLogWriter;
                    mMagLogWriter = null;
                    writer.close();
                }

            } catch (IOException e) {
                Log.e(TAG, "Sensor log file close failed: " + e.toString());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            // do not care
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            OutputStreamWriter writer=null;
            switch(event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    writer = mAccLogWriter;
                    break;
                case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                    writer = mGyroLogWriter;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                    writer = mMagLogWriter;
                    break;

            }
            if (writer!=null)  {
                float[] data = event.values;
                try {
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        writer.write(String.format("%d %f %f %f\r\n",
                                event.timestamp, data[0], data[1], data[2]));
                    }else // TYPE_GYROSCOPE_UNCALIBRATED and TYPE_MAGNETIC_FIELD_UNCALIBRATED
                    {
                        writer.write(String.format("%d %f %f %f %f %f %f\r\n", event.timestamp,
                                data[0], data[1], data[2], data[3], data[4], data[5]));
                    }
                }catch (IOException e)
                {
                    Log.e(TAG, "Write to raw sensor log file failed.");
                }

            }
        }
    }

    /**
     *  Rotation sensor logger class
     */
    class RVSensorLogger implements SensorEventListener {
        private final String TAG = "RVSensorLogger";

        private final static int SENSOR_RATE = SensorManager.SENSOR_DELAY_FASTEST;
        RangeCoveredRegister mRegister;
        int mAxis;
        RVCVRecordActivity mActivity;

        SensorManager mSensorManager;
        Sensor mRVSensor;
        OutputStreamWriter mLogWriter;

        private float[] mRTemp = new float[16];

        RVSensorLogger(RVCVRecordActivity activity) {
            mActivity = activity;
        }

        /**
         * Initialize and start recording
         */
        public void init() {
            mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            if (mSensorManager == null) {
                Log.e(TAG,"SensorManager is null!");
            }
            mRVSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (mRVSensor != null) {
                if (LOCAL_LOGV) Log.v(TAG, "Got RV Sensor");
            }else {
                Log.e(TAG, "Did not get RV sensor");
            }
            if(mSensorManager.registerListener(this, mRVSensor, SENSOR_RATE)) {
                if (LOCAL_LOGV) Log.v(TAG,"Register listener successfull");
            } else {
                Log.e(TAG,"Register listener failed");
            }

            try {
                mLogWriter= new OutputStreamWriter(
                        new FileOutputStream(mActivity.getSensorLogFilePath()));
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Sensor log file open failed: " + e.toString());
            }
        }

        /**
         * Stop recording and clean up
         */
        public void end() {
            mSensorManager.flush(this);
            mSensorManager.unregisterListener(this);

            try {
                if (mLogWriter != null) {
                    OutputStreamWriter writer = mLogWriter;
                    mLogWriter = null;
                    writer.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Sensor log file close failed: " + e.toString());
            }

            updateRegister(null, AXIS_NONE);
        }

        private void onNewData(float[] data, long timestamp) {
            // LOG
            try {
                if (mLogWriter != null) {
                    mLogWriter.write(String.format("%d %f %f %f %f\r\n", timestamp,
                            data[3], data[0], data[1], data[2]));
                }
            } catch (IOException e) {
                Log.e(TAG, "Sensor log file write failed: " + e.toString());
            }

            // Update UI
            if (mRegister != null) {
                int d = 0;
                int dx, dy, dz;
                boolean valid = false;
                SensorManager.getRotationMatrixFromVector(mRTemp, data);

                dx = (int)(Math.asin(mRTemp[8])*(180.0/Math.PI));
                dy = (int)(Math.asin(mRTemp[9])*(180.0/Math.PI));
                dz = (int)((Math.atan2(mRTemp[4], mRTemp[0])+Math.PI)*(180.0/Math.PI));

                switch(mAxis) {
                    case SensorManager.AXIS_X:
                        d = dx;
                        valid = (Math.abs(dy) < 30);
                        break;
                    case SensorManager.AXIS_Y:
                        d = dy;
                        valid = (Math.abs(dx) < 30);
                        break;
                    case SensorManager.AXIS_Z:
                        d = dz;
                        valid = (Math.abs(dx) < 20 && Math.abs(dy) < 20);
                        break;
                }

                if (valid) {
                    mRegister.update(d);
                    mActivity.redrawIndicator();
                }
            }

        }

        public void updateRegister(RangeCoveredRegister reg, int axis) {
            mRegister = reg;
            mAxis = axis;
        }


        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
            // do not care
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                onNewData(event.values, event.timestamp);
            }
        }
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Controls the over all logic of record procedure: first x-direction, then y-direction and
     * then z-direction.
     */
    class RecordProcedureController implements Runnable {
        private static final boolean LOCAL_LOGV = false;

        private final RVCVRecordActivity mActivity;
        private Thread mThread = null;

        RecordProcedureController(RVCVRecordActivity activity) {
            mActivity = activity;
            mThread = new Thread(this);
            mThread.start();
        }

        /**
         * Run the record procedure
         */
        public void run() {
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread Started.");
            //start recording & logging
            delay(2000);

            init();
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread init() finished.");

            // test 3 axis
            // It is in YXZ order because UI element design use opposite definition
            // of XY axis. To ensure the user see X Y Z, it is flipped here.
            recordAxis(SensorManager.AXIS_Y);
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread axis 0 finished.");

            recordAxis(SensorManager.AXIS_X);
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread axis 1 finished.");

            recordAxis(SensorManager.AXIS_Z);
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread axis 2 finished.");

            delay(1000);
            end();
            if (LOCAL_LOGV) Log.v(TAG, "Controller Thread End.");
        }

        private void delay(int milli) {
            try{
                Thread.sleep(milli);
            } catch(InterruptedException e) {
                if (LOCAL_LOGV) Log.v(TAG, "Controller Thread Interrupted.");
            }
        }
        private void init() {
            // start video recording
            mActivity.startRecordVideo();

            // start sensor logging & listening
            mActivity.startRecordSensor();
        }

        private void end() {
            // stop video recording
            mActivity.stopRecordVideo();

            // stop sensor logging
            mActivity.stopRecordSensor();

            // notify ui complete
            runOnUiThread(new Runnable(){
                public void run() {
                    mActivity.notifyComplete();
                }
            });
        }

        private void recordAxis(int axis) {
            // delay 2 seconds?
            delay(1000);

            // change ui
            mActivity.switchAxisAsync(axis);

            // play start sound
            mActivity.playNotifySound("start");

            if (axis != SensorManager.AXIS_Z) {
                // wait until axis half covered
                mActivity.waitUntilHalfCovered(axis);

                // play half way sound
                mActivity.playNotifySound("half-way");
            }

            // wait until axis covered
            mActivity.waitUntilCovered(axis);

            // play stop sound
            mActivity.playNotifySound("end");
        }

        /**
         * Force quit
         */
        public void quit() {
            mThread.interrupt();
            try {
                if (LOCAL_LOGV) Log.v(TAG, "Wait for controller to end");

                // stop video recording
                mActivity.stopRecordVideo();

                // stop sensor logging
                mActivity.stopRecordSensor();

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

}
