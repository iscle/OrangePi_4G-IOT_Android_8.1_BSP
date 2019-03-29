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
package com.android.cts.verifier.sensors.sixdof.Renderer;

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.CameraStreamManager;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.CameraPreviewRenderable;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseProvider;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.MATRIX_4X4;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.ORIENTATION_360_ANTI_CLOCKWISE;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Abstract class that connects to Android Camera to use as an OpenGL texture.
 */
public abstract class BaseRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "BaseRenderer";
    private static final int ORIENTATION_COUNT = 4;

    protected float[] mViewMatrix = new float[MATRIX_4X4];
    protected float[] mOrthogonalViewMatrix = new float[MATRIX_4X4];
    protected float[] mProjectionMatrix = new float[MATRIX_4X4];

    protected float[] mOrthogonalProjectionMatrix = new float[MATRIX_4X4];
    protected float[] mFrustrumProjectionMatrix = new float[MATRIX_4X4];

    protected DrawParameters mDrawParameters;

    protected float[] mCameraCoordinates;

    protected PoseProvider mPoseProvider;
    protected boolean mIsValid = false;

    protected CameraPreviewRenderable mCameraPreview;
    protected double mLastRGBFrameTimestamp = -1;

    private int mCameraPreviewRotation = 0;

    protected int mOpenGlRotation = 0;
    protected float[] mOpenGlUpVector;

    private Context mContext;

    public BaseRenderer(Context context) {
        mContext = context;
        mOpenGlRotation = getDeviceRotation(context);
        mOpenGlUpVector = getUpVector(mOpenGlRotation);
        mCameraPreviewRotation = CameraStreamManager.getRotation(context, mOpenGlRotation);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        // Set the background clear color to black.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        mCameraPreview = new CameraPreviewRenderable();

        resetViewMatrix();
    }

    protected void resetViewMatrix() {
        // Position the eye in front of the origin.
        final float eyeX = 0.0f;
        final float eyeY = 0.0f;
        final float eyeZ = 0.0f;

        // We are looking toward the distance
        final float lookX = 0.0f;
        final float lookY = 0.0f;
        final float lookZ = -5.0f;

        // Set our up vector. This is where our head would be pointing were we holding the camera.
        float[] upVector = getUpVector(mCameraPreviewRotation);

        // Set the view matrix.
        Matrix.setLookAtM(mViewMatrix, 0,
                eyeX, eyeY, eyeZ,
                lookX, lookY, lookZ,
                upVector[X], upVector[Y], upVector[Z]);
        Matrix.setLookAtM(mOrthogonalViewMatrix, 0,
                eyeX, eyeY, eyeZ,
                lookX, lookY, lookZ,
                upVector[X], upVector[Y], upVector[Z]);
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        // This project matrix does not take into account the camera intrinsics and should not be
        // used for AR purposes.
        final float ratio = (float) width / height;
        float left = -ratio;
        float right = ratio;
        float bottom = -1.0f;
        float top = 1.0f;
        final float near = 1.0f;
        final float far = 10.0f;

        boolean invertAxis = false;

        switch (mCameraPreviewRotation) {
            case MathsUtils.ORIENTATION_0:
            case MathsUtils.ORIENTATION_180_ANTI_CLOCKWISE:
            case MathsUtils.ORIENTATION_360_ANTI_CLOCKWISE:
                break;
            case MathsUtils.ORIENTATION_90_ANTI_CLOCKWISE:
            case MathsUtils.ORIENTATION_270_ANTI_CLOCKWISE:
                // Invert aspect ratio.
                invertAxis = true;
                bottom = -ratio;
                top = ratio;
                left = -1.0f;
                right = 1.0f;
                break;
            default:
                // Unexpected orientation, error out.
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }

        mCameraCoordinates = getCameraCoordinates(left, right, bottom, top);

        // Give camera preview reference to the context so that it can connect to the camera.
        mCameraPreview.initialiseCameraPreview(mCameraCoordinates, invertAxis, mContext);

        Matrix.orthoM(mOrthogonalProjectionMatrix, 0, left, right, bottom, top, near, far);
        Matrix.frustumM(mFrustrumProjectionMatrix, 0, left, right, bottom, top, near, far);

        mProjectionMatrix = mOrthogonalProjectionMatrix;

        mDrawParameters = new DrawParameters();

        mIsValid = true;
    }

    @Override
    public void onDrawFrame(GL10 glUnused) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        doPreRenderingSetup();
        doCoreRendering();
        doTestSpecificRendering();
    }

    private void doCoreRendering() {
        mDrawParameters.update(mViewMatrix, mProjectionMatrix);
        mCameraPreview.draw(mDrawParameters);
    }

    protected synchronized void updateCameraTexture() {
        mLastRGBFrameTimestamp = mCameraPreview.updateTexture();
    }

    /**
     * Setup up view and projecttion matrices to be the ones you want for this draw call.
     */
    protected abstract void doPreRenderingSetup();

    /**
     * Do rendering that is unique to each test.
     */
    protected abstract void doTestSpecificRendering();

    /**
     * Where to position the camera preview on the screen. Can be overridden by sub classes.
     */
    protected float[] getCameraCoordinates(float left, float right, float bottom, float top) {
        switch (mCameraPreviewRotation) {
            case MathsUtils.ORIENTATION_0:
            case MathsUtils.ORIENTATION_180_ANTI_CLOCKWISE:
            case MathsUtils.ORIENTATION_360_ANTI_CLOCKWISE:
                // Normal aspect ratio.
                return new float[]{
                        left, top, 0.0f,
                        left, bottom, 0.0f,
                        right, top, 0.0f,
                        left, bottom, 0.0f,
                        right, bottom, 0.0f,
                        right, top, 0.0f,
                };
            case MathsUtils.ORIENTATION_90_ANTI_CLOCKWISE:
            case MathsUtils.ORIENTATION_270_ANTI_CLOCKWISE:
                // Inverted aspect ratio.
                return new float[]{
                        bottom, right, 0.0f,
                        bottom, left, 0.0f,
                        top, right, 0.0f,
                        bottom, left, 0.0f,
                        top, left, 0.0f,
                        top, right, 0.0f,
                };
            default:
                // Unexpected orientation, error out.
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }
    }


    /**
     * Saves PoseProvider object for later so we can connect to camera at appropriate time.
     */
    public void connectCamera(PoseProvider poseProvider, Context context) {
        // Save these for later so we can connect to camera after mCameraPreview has been
        // initialised. Also used to setup extrinsics in ComplexMovementRenderer.
        mPoseProvider = poseProvider;
        mContext = context;
    }

    public void disconnectCamera() {
        mCameraPreview.disconnectCamera();
    }

    public void onDestroy() {
        mPoseProvider = null;
        mContext = null;

        if (mCameraPreview != null) {
            mCameraPreview.destroy();
            mCameraPreview = null;
        }
    }

    private static float[] getUpVector(int rotation) {
        float [] upVector = new float[MathsUtils.VECTOR_3D];

        switch (rotation) {
            case MathsUtils.ORIENTATION_0:
            case ORIENTATION_360_ANTI_CLOCKWISE:
                upVector[X] = 0.0f;
                upVector[Y] = 1.0f;
                upVector[Z] = 0.0f;
                break;
            case MathsUtils.ORIENTATION_90_ANTI_CLOCKWISE:
                upVector[X] = -1.0f;
                upVector[Y] = 0.0f;
                upVector[Z] = 0.0f;
                break;
            case MathsUtils.ORIENTATION_180_ANTI_CLOCKWISE:
                upVector[X] = 0.0f;
                upVector[Y] = -1.0f;
                upVector[Z] = 0.0f;
                break;
            case MathsUtils.ORIENTATION_270_ANTI_CLOCKWISE:
                upVector[X] = 1.0f;
                upVector[Y] = 0.0f;
                upVector[Z] = 0.0f;
                break;
            default:
                // Unexpected orientation, error out.
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }

        return upVector;
    }

    public static int getDeviceRotation(Context context) {
        WindowManager windowManager = (WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = windowManager.getDefaultDisplay();
        int naturalOrientation = Configuration.ORIENTATION_LANDSCAPE;
        int configOrientation = context.getResources().getConfiguration().orientation;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                // We are currently in the same basic orientation as the natural orientation.
                naturalOrientation = configOrientation;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                // We are currently in the other basic orientation to the natural orientation.
                naturalOrientation = (configOrientation == Configuration.ORIENTATION_LANDSCAPE) ?
                        Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
                break;
            default:
                // Unexpected orientation, error out.
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }

        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }

        // Get rotation as a clockwise rotation.
        int currentRotation = ORIENTATION_COUNT - display.getRotation();

        // Check for reverse rotation direction and currentRotation if required.
        try {
            if (context.getResources().getBoolean(context.getResources().getSystem().getIdentifier(
                    "config_reverseDefaultRotation", "bool", "android"))) {
                currentRotation = display.getRotation();
            }
        } catch (Resources.NotFoundException e) {
            // If resource is not found, assume default rotation and continue.
            Log.d(TAG, "Cannot determine device rotation direction, assuming default");
        }

        int currentOrientation = (currentRotation + indexOffset);
        int defaultOrientation = indexOffset;

        int difference = (currentOrientation - defaultOrientation) % ORIENTATION_COUNT;
        difference = difference * 90;

        return difference;
    }
}
