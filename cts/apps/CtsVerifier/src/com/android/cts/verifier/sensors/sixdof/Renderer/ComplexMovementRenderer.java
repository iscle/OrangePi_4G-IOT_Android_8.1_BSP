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

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ModelMatrixCalculator;
import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.ObjImporter;
import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.ConeRenderable;
import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.Light;
import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.RingRenderable;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.Intrinsics;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseData;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.MATRIX_4X4;

import android.content.Context;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;

import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer for the robustness test
 */
public class ComplexMovementRenderer extends BaseRenderer {
    private static final String TAG = "ComplexMovementRenderer";
    private static final float[] DEFAULT_LIGHT_POSITION = new float[]{
            0.0f, 3.0f, 0.0f};
    private static final Object RING_LOCK = new Object();
    private ModelMatrixCalculator mCameraModelMatrixCalculator;
    private ConeRenderable mCone;
    private Light mLight;
    private float[] mPoseViewMatrix = new float[MATRIX_4X4];
    private float[] mAugmentedRealityProjectMatrix = new float[MATRIX_4X4];

    protected boolean mIsCameraConfigured = false;

    protected double mCameraPoseTimestamp = 0;
    private PoseData mLastFramePose;

    private Context mContext;

    private int mWaypointCount = 0;
    private MediaPlayer mMediaPlayer;
    private ArrayList<Ring> mRings;

    public ComplexMovementRenderer(Context context, ArrayList<Ring> rings) {
        super(context);
        mCameraModelMatrixCalculator = new ModelMatrixCalculator(mOpenGlRotation);
        mContext = context;
        mMediaPlayer = MediaPlayer.create(context, R.raw.ring_sound);
        mRings = rings;
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        super.onSurfaceCreated(glUnused, config);
        mCone = new ConeRenderable(mOpenGlRotation, mOpenGlUpVector);
        mLight = new Light(DEFAULT_LIGHT_POSITION, 2.0f);
        setUpExtrinsics();

        ObjImporter.ObjectData ringData = ObjImporter.parse(mContext.getResources(), R.raw.ring_obj);

        for (Ring ring : mRings) {
            final float[] position =
                    MathsUtils.convertToOpenGlCoordinates(ring.getLocation(), mOpenGlRotation);
            final float[] rotation =
                    MathsUtils.convertToOpenGlCoordinates(ring.getRingRotation(), mOpenGlRotation);
            RingRenderable ringRenderable = new RingRenderable(position, rotation, mOpenGlUpVector);
            ringRenderable.initialise(ringData);
            ring.setRingRenderable(ringRenderable);
        }

        ObjImporter.ObjectData coneData = ObjImporter.parse(mContext.getResources(), R.raw.cone_obj);
        mCone.initialise(coneData);
    }

    @Override
    protected void doPreRenderingSetup() {
        // Set up drawing of background camera preview (orthogonal).
        mViewMatrix = mOrthogonalViewMatrix;
        mProjectionMatrix = mOrthogonalProjectionMatrix;
    }

    @Override
    protected void doTestSpecificRendering() {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);
        if (mPoseProvider != null) {
            // Update the texture with the latest camera frame.
            updateCameraTexture();

            // We delay the camera set-up until now because if we do it earlier (i.e., when the
            // camera is connected to the renderer) the PoseProvider service may still not have the
            // necessary intrinsic and extrinsic transformation information available.
            if (!mIsCameraConfigured) {
                configureCamera();
            }

            // Calculate the device pose at the camera frame update time.
            mLastFramePose = mPoseProvider.getLatestPoseData();
            // Update the camera pose from the renderer
            updateRenderCameraPose(mLastFramePose);
            // Update the MV matrix with new pose data.
            updatePoseViewMatrix();
            // Update light with new translation.
            mLight.updateLightPosition(MathsUtils.convertToOpenGlCoordinates(
                    mLastFramePose.getTranslationAsFloats(), mOpenGlRotation));
            mCameraPoseTimestamp = mLastFramePose.timestamp;
        }

        // Render objects with latest pose information available.
        renderAugmentedRealityObjects();
    }

    private void renderAugmentedRealityObjects() {
        // Set up projection matrix to match camera intrinsics.
        mProjectionMatrix = mAugmentedRealityProjectMatrix;
        // Set up view matrix to match current device positioning.
        mViewMatrix = mPoseViewMatrix;

        mDrawParameters.update(mViewMatrix, mProjectionMatrix, mLight);
        for (Ring ring : mRings) {
            // If we have placed the initial waypoint, we want rings for the first path, path 0.
            if (ring.getPathNumber() == mWaypointCount && !ring.isEntered()) {
                // Only draw the rings that are on our current path and have not been entered.
                ring.getRingRenderable().draw(mDrawParameters);
            }
        }
        // Clear depth buffer so cone does not clip with rings.
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT);

        // Set cone to look at nearest ring.
        boolean lookingAt = false;
        for (Ring ring : mRings) {
            if (!ring.isEntered() && !lookingAt && ring.getPathNumber() == mWaypointCount) {
                // If the ring has not been entered, the cone has not been set to look at anything
                // yet, and we are on the correct lap for this ring.

                mCone.updateModelMatrix(mLastFramePose.getTranslationAsFloats(),
                        mLastFramePose.getRotationAsFloats(), ring.getLocation());
                lookingAt = true;
            }
        }

        if (lookingAt) {
            // Only draw the cone if it has something to look at.
            mCone.draw(mDrawParameters);
        }
    }

    protected void configureCamera() {
        // This should never happen, but it never hurts to double-check.
        if (mPoseProvider == null) {
            return;
        }

        Intrinsics intrinsics = mPoseProvider.getIntrinsics();

        mAugmentedRealityProjectMatrix = calculateProjectionMatrix(
                intrinsics.getWidth(), intrinsics.getHeight(),
                intrinsics.getFocalLengthInPixelsX(), intrinsics.getFocalLengthInPixelsY());
        mIsCameraConfigured = true;
    }

    /**
     * Called when a waypoint is placed in the last test. Used to show and hide rings.
     *
     * @param waypointCount Number of waypoints placed.
     */
    public void onWaypointPlaced(int waypointCount) {
        mWaypointCount = waypointCount;
    }

    /**
     * Called when a ring has been entered. Plays a sound and then hides the ring.
     *
     * @param ring Ring that has just been entered.
     */
    public void onRingEntered(Ring ring) {
        synchronized (RING_LOCK) {
            ring.setSoundPlayed(true);
        }
        mMediaPlayer.start();
    }

    /**
     * Setup the extrinsics of the device.
     */
    private void setUpExtrinsics() {
    }

    /**
     * Update the scene camera based on the provided pose. The
     * device pose should match the pose of the device at the time the last rendered RGB frame.
     */
    public void updateRenderCameraPose(PoseData devicePose) {
        mCameraModelMatrixCalculator.updateModelMatrix(devicePose.getTranslationAsFloats(),
                devicePose.getRotationAsFloats());
    }

    /**
     * Update the view matrix of the Renderer to follow the position of the device in the current
     * perspective.
     */
    public void updatePoseViewMatrix() {
        float[] invertModelMat = new float[MATRIX_4X4];
        Matrix.setIdentityM(invertModelMat, 0);

        float[] temporaryMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(temporaryMatrix, 0);

        Matrix.setIdentityM(mPoseViewMatrix, 0);
        Matrix.invertM(invertModelMat, 0,
                mCameraModelMatrixCalculator.getModelMatrix(), 0);
        Matrix.multiplyMM(temporaryMatrix, 0, mPoseViewMatrix, 0,
                invertModelMat, 0);
        System.arraycopy(temporaryMatrix, 0, mPoseViewMatrix, 0, MATRIX_4X4);
    }

    /**
     * Use camera intrinsics to calculate the projection Matrix.
     */
    private float[] calculateProjectionMatrix(int width, int height,
                                              double focalLengthX, double focalLengthY) {
        // Uses frustumM to create a projection matrix taking into account calibrated camera
        // intrinsic parameter.
        // Reference: http://ksimek.github.io/2013/06/03/calibrated_cameras_in_opengl/
        float near = 0.1f;
        float far = 100f;

        float xScale = (float) (near / focalLengthX);
        float yScale = (float) (near / focalLengthY);

        float[] projectionMatrix = new float[16];
        Matrix.frustumM(projectionMatrix, 0,
                xScale * -width / 2.0f,
                xScale * width / 2.0f,
                yScale * -height / 2.0f,
                yScale * height / 2.0f,
                near, far);
        return projectionMatrix;
    }
}
