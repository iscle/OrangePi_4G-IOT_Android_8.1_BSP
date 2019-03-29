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
package com.android.cts.verifier.sensors.sixdof.Renderer.Renderable;

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.Matrix;

/**
 * Base class for all Renderables
 */
public abstract class Renderable {
    protected static final int BYTES_PER_FLOAT = 4;
    protected static final int POSITION_DATA_SIZE = 3;
    protected static final int COLOUR_DATA_SIZE = 4;
    protected static final int NORMAL_DATA_SIZE = 3;

    protected int mVertexCount;

    protected int mProgramHandle;
    protected int mPositionHandle;
    protected int mMVPMatrixHandle;
    protected int mMVMatrixHandle;

    private float[] mModelMatrix = new float[MathsUtils.MATRIX_4X4];
    private float[] mMvMatrix = new float[MathsUtils.MATRIX_4X4];
    private float[] mMvpMatrix = new float[MathsUtils.MATRIX_4X4];

    /**
     * Applies the view and projection matrices and draws the Renderable.
     *
     * @param drawParameters parameters needed for drawing objects.
     */
    public abstract void draw(DrawParameters drawParameters);

    public synchronized void updateMvpMatrix(float[] viewMatrix,
                                             float[] projectionMatrix) {
        // Compose the model, view, and projection matrices into a single mvp
        // matrix
        Matrix.setIdentityM(mMvMatrix, 0);
        Matrix.setIdentityM(mMvpMatrix, 0);
        Matrix.multiplyMM(mMvMatrix, 0, viewMatrix, 0, getModelMatrix(), 0);
        Matrix.multiplyMM(mMvpMatrix, 0, projectionMatrix, 0, mMvMatrix, 0);
    }

    public float[] getModelMatrix() {
        return mModelMatrix;
    }

    public void setModelMatrix(float[] modelMatrix) {
        mModelMatrix = modelMatrix;
    }

    public float[] getMvMatrix() {
        return mMvMatrix;
    }

    public float[] getMvpMatrix() {
        return mMvpMatrix;
    }

    public synchronized void setRotationAngle(float newAngle) {
        // Rotate around the Z axis. (only used in robustness test).
        float[] translations = new float[]
                {getModelMatrix()[12], getModelMatrix()[13],getModelMatrix()[14]};
        synchronized (this) {
            Matrix.setIdentityM(getModelMatrix(), 0);
            Matrix.rotateM(getModelMatrix(), 0, newAngle, 0.0f, 0.0f, 1.0f);
            Matrix.translateM(getModelMatrix(), 0,
                    translations[X], translations[Y], translations[Z]);
        }
    }

    public abstract void destroy();
}
