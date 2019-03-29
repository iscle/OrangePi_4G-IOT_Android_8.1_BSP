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

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.MATRIX_4X4;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.Matrix;

import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;


/**
 * Utility class to manage the calculation of a Model Matrix from the translation and quaternion
 * arrays obtained from an PoseData object.
 */
public class ModelMatrixCalculator {

    protected static final int MATRIX_4X4_TRANSLATION_X = 12;
    protected static final int MATRIX_4X4_TRANSLATION_Y = 13;
    protected static final int MATRIX_4X4_TRANSLATION_Z = 14;

    public static final float[] TANGO_TO_OPENGL = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, -1.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f};

    protected float[] mModelMatrix = new float[MATRIX_4X4];

    // Set these to identity matrix.
    protected float[] mDevice2IMUMatrix = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            1.0f};
    protected float[] mColorCamera2IMUMatrix = new float[]{1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f};
    protected float[] mOpengl2ColorCameraMatrix = new float[]{1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f};

    protected int mToRotate = 0;

    public ModelMatrixCalculator(int toRotate) {
        Matrix.setIdentityM(mModelMatrix, 0);
        mToRotate = toRotate;
    }

    /**
     * Calculates a new model matrix, taking into account extrinsics and the latest pose
     * data.
     *
     * @param translationInOpenGlCoordinates latest translation from pose data in OpenGl coordinate
     *                                       system.
     * @param quaternion                     latest rotation from pose data.
     * @return the new model matrix.
     */
    protected float[] calculateModelMatrix(float[] translationInOpenGlCoordinates,
                                           float[] quaternion) {
        float[] newModelMatrix = new float[MATRIX_4X4];

        // Calculate an initial matrix with extrinsics taken into account.
        float[] imu2OpenGlMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(imu2OpenGlMatrix, 0);
        Matrix.multiplyMM(imu2OpenGlMatrix, 0, mColorCamera2IMUMatrix, 0,
                mOpengl2ColorCameraMatrix, 0);
        float[] invertedDevice2ImuMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(invertedDevice2ImuMatrix, 0);
        Matrix.invertM(invertedDevice2ImuMatrix, 0, mDevice2IMUMatrix, 0);
        float[] extrinsicsBasedMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(extrinsicsBasedMatrix, 0);
        Matrix.multiplyMM(extrinsicsBasedMatrix, 0, invertedDevice2ImuMatrix, 0,
                imu2OpenGlMatrix, 0);

        // Do any translations that need to be done before rotating. Only used for the Cone offset.
        float[] requiredTranslations = getRequiredTranslations();
        Matrix.translateM(extrinsicsBasedMatrix, 0, requiredTranslations[X], requiredTranslations[Y],
                requiredTranslations[Z]);

        // Rotate based on rotation pose data.
        float[] quaternionMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(quaternionMatrix, 0);
        quaternionMatrix = MathsUtils.quaternionMatrixOpenGL(quaternion);
        float[] rotatedMatrix = new float[MATRIX_4X4];
        float[] deviceOrientationMatrix = new float[MATRIX_4X4];
        Matrix.setIdentityM(rotatedMatrix, 0);
        Matrix.setIdentityM(newModelMatrix, 0);
        Matrix.setIdentityM(deviceOrientationMatrix, 0);
        Matrix.multiplyMM(rotatedMatrix, 0, quaternionMatrix, 0,
                extrinsicsBasedMatrix, 0);
        Matrix.multiplyMM(deviceOrientationMatrix, 0, TANGO_TO_OPENGL, 0,
                rotatedMatrix, 0);

        Matrix.multiplyMM(newModelMatrix, 0, deviceOrientationMatrix, 0,
                MathsUtils.getDeviceOrientationMatrix(mToRotate), 0);

        // Finally, add the translations from the pose data.
        newModelMatrix[MATRIX_4X4_TRANSLATION_X] += translationInOpenGlCoordinates[X];
        newModelMatrix[MATRIX_4X4_TRANSLATION_Y] += translationInOpenGlCoordinates[Y];
        newModelMatrix[MATRIX_4X4_TRANSLATION_Z] += translationInOpenGlCoordinates[Z];

        return newModelMatrix;
    }

    /**
     * Updates the model matrix (rotation and translation).
     *
     * @param translation a three-element array of translation data.
     * @param quaternion  a four-element array of rotation data.
     */
    public void updateModelMatrix(float[] translation, float[] quaternion) {
        float[] convertedTranslation = MathsUtils.convertToOpenGlCoordinates(translation, mToRotate);
        mModelMatrix = calculateModelMatrix(convertedTranslation, quaternion);
    }

    public void setDevice2IMUMatrix(float[] translation, float[] quaternion) {
        mDevice2IMUMatrix = MathsUtils.quaternionMatrixOpenGL(quaternion);
        mDevice2IMUMatrix[MATRIX_4X4_TRANSLATION_X] = translation[X];
        mDevice2IMUMatrix[MATRIX_4X4_TRANSLATION_Y] = translation[Y];
        mDevice2IMUMatrix[MATRIX_4X4_TRANSLATION_Z] = translation[Z];
    }

    public void setColorCamera2IMUMatrix(float[] translation, float[] quaternion) {
        mOpengl2ColorCameraMatrix = new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                -1.0f, 0.0f, 0.0f, 0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                1.0f};
        mColorCamera2IMUMatrix = MathsUtils.quaternionMatrixOpenGL(quaternion);
        mColorCamera2IMUMatrix[MATRIX_4X4_TRANSLATION_X] = translation[X];
        mColorCamera2IMUMatrix[MATRIX_4X4_TRANSLATION_Y] = translation[Y];
        mColorCamera2IMUMatrix[MATRIX_4X4_TRANSLATION_Z] = translation[Z];
    }

    public float[] getModelMatrix() {
        return mModelMatrix;
    }

    /**
     * Translations that need to be done before rotating. Used for calculating the CONE_OFFSET.
     *
     * @return no translation.
     */
    protected float[] getRequiredTranslations() {
        return new float[]{0.0f, 0.0f, 0.0f};
    }
}
