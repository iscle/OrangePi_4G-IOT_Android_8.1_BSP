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
package com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider;

public class PoseData {
    /** Index of the X-value in the translation array. */
    public static final int INDEX_TRANSLATION_X = 0;
    /** Index of the Y-value in the translation array. */
    public static final int INDEX_TRANSLATION_Y = 1;
    /** Index of the Z-value in the translation array. */
    public static final int INDEX_TRANSLATION_Z = 2;

    /** Index of the quaternion X-value in the rotation array. */
    public static final int INDEX_ROTATION_X = 0;
    /** Index of the quaternion Y-value in the rotation array. */
    public static final int INDEX_ROTATION_Y = 1;
    /** Index of the quaternion Z-value in the rotation array. */
    public static final int INDEX_ROTATION_Z = 2;
    /** Index of the quaternion W-value in the rotation array. */
    public static final int INDEX_ROTATION_W = 3;

    public double timestamp = 0;

    /**
     * Orientation, as a quaternion, of the pose of the target frame with reference to to the base
     * frame.
     * <p>
     * Specified as (x,y,z,w) where RotationAngle is in radians:
     * <pre>
     * x = RotationAxis.x * sin(RotationAngle / 2)
     * y = RotationAxis.y * sin(RotationAngle / 2)
     * z = RotationAxis.z * sin(RotationAngle / 2)
     * w = cos(RotationAngle / 2)
     * </pre>
     */
    public float mRotation[] = {
            0.0f, 0.0f, 0.0f, 1.0f };

    /**
     * Translation, ordered x, y, z, of the pose of the target frame relative to the reference
     * frame.
     */
    public float mTranslation[] = {
            0.0f, 0.0f, 0.0f };

    public PoseData(float[] sixDoFSensorValues, long timestamp){
        this.timestamp = timestamp;
        mRotation[0] = sixDoFSensorValues[0];
        mRotation[1] = sixDoFSensorValues[1];
        mRotation[2] = sixDoFSensorValues[2];
        mRotation[3] = sixDoFSensorValues[3];
        mTranslation[0] = sixDoFSensorValues[4];
        mTranslation[1] = sixDoFSensorValues[5];
        mTranslation[2] = sixDoFSensorValues[6];
    }

    public PoseData(float[] translation, float[] rotation, long timestamp){
        this.timestamp = timestamp;
        mRotation[0] = rotation[0];
        mRotation[1] = rotation[1];
        mRotation[2] = rotation[2];
        mRotation[3] = rotation[3];
        mTranslation[0] = translation[0];
        mTranslation[1] = translation[1];
        mTranslation[2] = translation[2];
    }

    /**
     * Convenience function to get the rotation casted as an array of floats.
     *
     * @return The pose rotation.
     */
    public float[] getRotationAsFloats() {
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            out[i] = mRotation[i];
        }
        return out;
    }

    /**
     * Convenience function to get the translation casted as an array of floats.
     *
     * @return The pose translation.
     */
    public float[] getTranslationAsFloats() {
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            out[i] = mTranslation[i];
        }
        return out;
    }
}
