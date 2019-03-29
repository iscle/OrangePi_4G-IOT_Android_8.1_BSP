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

import android.content.Context;

import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.RectangleRenderable;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer for the robustness test
 */
public class RobustnessRenderer extends BaseRenderer {

    private float[] mRectanglePositionData;
    private RectangleRenderable mRectangle;

    public RobustnessRenderer(Context context) {
        super(context);
    }

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        super.onSurfaceCreated(glUnused, config);
        mRectangle = new RectangleRenderable();
    }

    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        super.onSurfaceChanged(glUnused, width, height);
        mRectangle.initialiseRectangle(mRectanglePositionData);
        mProjectionMatrix = mFrustrumProjectionMatrix;
    }

    public void setLineColor(float[] newColor) {
        if (mIsValid) {
            mRectangle.setLineColor(newColor);
        }
    }

    public void updateCurrentAngle(float newAngle) {
        if (mIsValid) {
            mRectangle.setRotationAngle(newAngle);
        }
    }

    public void updateTargetAngle(float newAngle) {
        if (mIsValid) {
            mCameraPreview.setRotationAngle(newAngle);
        }
    }

    @Override
    protected void doPreRenderingSetup() {
        // Set view matrix to one that doesn't move.
        mViewMatrix = mOrthogonalViewMatrix;
        // Set projection matrix to show camera preview slightly set back.
        mProjectionMatrix = mFrustrumProjectionMatrix;
    }

    @Override
    protected void doTestSpecificRendering() {
        // Update the texture with the latest camera frame if there is an update pending.
        updateCameraTexture();

        mDrawParameters.update(mViewMatrix, mProjectionMatrix);
        mRectangle.draw(mDrawParameters);
    }

    @Override
    protected float[] getCameraCoordinates(float left, float right, float bottom, float top) {
        // Set rectangle coordinates to be the exact same as the camera preview.
        mRectanglePositionData = new float[]{
                2 * left, 2 * top, 0.0f,
                2 * left, 2 * bottom, 0.0f,

                2 * left, 2 * bottom, 0.0f,
                2 * right, 2 * bottom, 0.0f,

                2 * right, 2 * bottom, 0.0f,
                2 * right, 2 * top, 0.0f,

                2 * right, 2 * top, 0.0f,
                2 * left, 2 * top, 0.0f,
        };

        return new float[]{
                2 * left, 2 * top, 0.0f,
                2 * left, 2 * bottom, 0.0f,
                2 * right, 2 * top, 0.0f,
                2 * left, 2 * bottom, 0.0f,
                2 * right, 2 * bottom, 0.0f,
                2 * right, 2 * top, 0.0f,
        };
    }
}
