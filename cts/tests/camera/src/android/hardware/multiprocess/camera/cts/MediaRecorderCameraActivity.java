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

package android.hardware.multiprocess.camera.cts;

import android.app.Activity;
import android.camera.cts.R;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.File;

/**
 * Activity implementing basic access of camera using MediaRecorder API.
 *
 * <p />
 * This will log all errors to {@link android.hardware.multiprocess.camera.cts.ErrorLoggingService}.
 */
public class MediaRecorderCameraActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "MediaRecorderCameraActivity";

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int LAYOUT_WIDTH = VIDEO_WIDTH;
    private static final int LAYOUT_HEIGHT = VIDEO_HEIGHT;

    private final String OUTPUT_PATH = new File(Environment.getExternalStorageDirectory(),
                "record.out").getAbsolutePath();

    private File mOutFile;
    private SurfaceView mSurfaceView;
    private ErrorLoggingService.ErrorServiceConnection mErrorServiceConnection;
    private MediaRecorder mMediaRecorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.surface_view);

        mErrorServiceConnection = new ErrorLoggingService.ErrorServiceConnection(this);
        mErrorServiceConnection.start();

        mMediaRecorder = new MediaRecorder();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");
        super.onResume();
        try {

            mSurfaceView = (SurfaceView)this.findViewById(R.id.surface_view);
            ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
            lp.width = LAYOUT_WIDTH;
            lp.height = LAYOUT_HEIGHT;
            mSurfaceView.setLayoutParams(lp);

            SurfaceHolder holder = mSurfaceView.getHolder();
            holder.setFixedSize(LAYOUT_WIDTH, LAYOUT_HEIGHT);
            holder.addCallback(this);

        } catch (Throwable e) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                    " camera exception during connection: " + e);
            Log.e(TAG, "Runtime error: " + e);
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause called.");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy called.");
        super.onDestroy();
        if (mErrorServiceConnection != null) {
            mErrorServiceConnection.stop();
            mErrorServiceConnection = null;
        }

        if (mOutFile != null && mOutFile.exists()) {
            mOutFile.delete();
        }

        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.release();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        try {
            mOutFile = new File(OUTPUT_PATH);
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
            mMediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
            mMediaRecorder.setOutputFile(OUTPUT_PATH);
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_CONNECT,
                    TAG + " camera connected");
        } catch (Throwable e) {
            mErrorServiceConnection.logAsync(TestConstants.EVENT_CAMERA_ERROR, TAG +
                    " camera exception during connection: " + e);
            Log.e(TAG, "Runtime error: " + e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
