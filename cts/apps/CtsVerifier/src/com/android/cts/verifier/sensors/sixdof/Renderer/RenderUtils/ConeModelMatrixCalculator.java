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
package com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils;

import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.ConeRenderable;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.Matrix;

/**
 * Manages the model matrix of the direction cone.
 */
public class ConeModelMatrixCalculator extends ModelMatrixCalculator {
    float[] mUpVector;

    public ConeModelMatrixCalculator(int toRotate, float[] upVector) {
        super(toRotate);
        mUpVector = upVector;
    }

    public void updateModelMatrix(float[] translation, float[] quaternion, float[] lookAtPosition) {
        float[] convertedTranslation = MathsUtils.convertToOpenGlCoordinates(translation, mToRotate);
        // Calculate the extrinsics based model matrix with current pose data.
        float[] newModelMatrix = calculateModelMatrix(convertedTranslation, quaternion);

        // Extract the information we need from calculated model matrix. (Just the translation).
        float[] translationMatrix = new float[MathsUtils.MATRIX_4X4];
        Matrix.setIdentityM(translationMatrix, 0);
        Matrix.translateM(translationMatrix, 0, newModelMatrix[MATRIX_4X4_TRANSLATION_X],
                newModelMatrix[MATRIX_4X4_TRANSLATION_Y], newModelMatrix[MATRIX_4X4_TRANSLATION_Z]);

        float[] openGlRingPosition = MathsUtils.convertToOpenGlCoordinates(lookAtPosition, mToRotate);
        float[] rotationTransformation = new float[MathsUtils.MATRIX_4X4];
        // Calculate direction vector.
        float[] relativeVector = new float[MathsUtils.VECTOR_3D];
        for (int i = 0; i < relativeVector.length; i++) {
            relativeVector[i] = openGlRingPosition[i] - convertedTranslation[i];
        }
        Matrix.setIdentityM(rotationTransformation, 0);
        // Calculate look at rotation transformation.
        // Has to be relative to the origin otherwise we get some warping of the cone.
        MathsUtils.setLookAtM(rotationTransformation,
                // Where we are.
                0.0f, 0.0f, 0.0f,
                // What we want to look at.
                relativeVector[X], relativeVector[Y], relativeVector[Z],
                // Up direction.
                mUpVector[X], mUpVector[Y], mUpVector[Z]);

        // Apply translation to the look at matrix.
        Matrix.multiplyMM(mModelMatrix, 0, translationMatrix, 0, rotationTransformation, 0);
    }

    /**
     * Rotations that need to be done before rotating. Used for calculating the CONE_OFFSET.
     *
     * @return The offset that the cone needs to be at.
     */
    @Override
    protected float[] getRequiredTranslations() {
        return ConeRenderable.CONE_OFFSET;
    }
}
