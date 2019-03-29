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
package com.android.cts.verifier.sensors.sixdof.Utils;

import android.opengl.Matrix;
import android.util.Log;

import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseData;

import java.text.DecimalFormat;

/**
 * Contains functions that are used throughout the app.
 */
public class MathsUtils {
    public static final int X = PoseData.INDEX_TRANSLATION_X;
    public static final int Y = PoseData.INDEX_TRANSLATION_Y;
    public static final int Z = PoseData.INDEX_TRANSLATION_Z;

    public static final int MATRIX_4X4 = 16;
    public static final int VECTOR_2D = 2;
    public static final int VECTOR_3D = 3;

    public static final int ORIENTATION_0 = 0;
    public static final int ORIENTATION_90_ANTI_CLOCKWISE = 90;
    public static final int ORIENTATION_180_ANTI_CLOCKWISE = 180;
    public static final int ORIENTATION_270_ANTI_CLOCKWISE = 270;
    public static final int ORIENTATION_360_ANTI_CLOCKWISE = 360;

    /**
     * Converts from float array in 6DoF coordinate system to a Vector3 in OpenGl coordinate
     * system.
     *
     * @param location float array to convert.
     * @return the Vector3 in OpenGL coord system.
     */
    public static float[] convertToOpenGlCoordinates(float[] location, int toRotate) {
        // Have to swap Y and Z as they are different in OpenGl and 6DoF. Also invert Z.
        float[] inDefaultOrientation = new float[]{location[X], location[Z], -location[Y]};

        return rotateCoordinates(inDefaultOrientation, toRotate);
    }

    public static float[] rotateCoordinates(float[] coordinates, int toRotate) {
        final float[] inCurrentOrientation;

        switch (toRotate) {
            case ORIENTATION_0:
            case ORIENTATION_360_ANTI_CLOCKWISE:
                inCurrentOrientation = coordinates;
                break;
            case ORIENTATION_90_ANTI_CLOCKWISE:
                inCurrentOrientation = new float[]{coordinates[Y], -coordinates[X],
                        coordinates[Z]};
                break;
            case ORIENTATION_180_ANTI_CLOCKWISE:
                inCurrentOrientation = new float[]{coordinates[X], coordinates[Y],
                        coordinates[Z]};
                break;
            case ORIENTATION_270_ANTI_CLOCKWISE:
                inCurrentOrientation = new float[]{-coordinates[Y], coordinates[X],
                        coordinates[Z]};
                break;
            default:
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }

        return inCurrentOrientation;
    }

    /**
     * Produce a rotation transformation that looks at a point 'center' from a point 'eye'.
     */
    public static void setLookAtM(float[] rm, float eyeX, float eyeY, float eyeZ,
                                  float centerX, float centerY, float centerZ,
                                  float upX, float upY, float upZ) {
        // Algorithm taken from DirectX documentation.
        // https://msdn.microsoft.com/en-us/library/bb205343.aspx

        float zAxisX = eyeX - centerX;
        float zAxisY = eyeY - centerY;
        float zAxisZ = eyeZ - centerZ;

        // Normalize zAxis
        float rlf = 1.0f / Matrix.length(zAxisX, zAxisY, zAxisZ);
        zAxisX *= rlf;
        zAxisY *= rlf;
        zAxisZ *= rlf;

        // compute xAxis = up x zAxis (x means "cross product")
        float xAxisX = upY * zAxisZ - upZ * zAxisY;
        float xAxisY = upZ * zAxisX - upX * zAxisZ;
        float xAxisZ = upX * zAxisY - upY * zAxisX;

        // and normalize xAxis
        float rls = 1.0f / Matrix.length(xAxisX, xAxisY, xAxisZ);
        xAxisX *= rls;
        xAxisY *= rls;
        xAxisZ *= rls;

        // compute yAxis = zAxis x xAxis
        float yAxisX = zAxisY * xAxisZ - zAxisZ * xAxisY;
        float yAxisY = zAxisZ * xAxisX - zAxisX * xAxisZ;
        float yAxisZ = zAxisX * xAxisY - zAxisY * xAxisX;

        rm[0] = xAxisX;
        rm[1] = xAxisY;
        rm[2] = xAxisZ;
        rm[3] = (xAxisX * eyeX) + (xAxisY * eyeY) + (xAxisZ * eyeZ);

        rm[4] = yAxisX;
        rm[5] = yAxisY;
        rm[6] = yAxisZ;
        rm[7] = (yAxisX * eyeX) + (yAxisY * eyeY) + (yAxisZ * eyeZ);

        rm[8] = zAxisX;
        rm[9] = zAxisY;
        rm[10] = zAxisZ;
        rm[11] = (zAxisX * eyeX) + (zAxisY * eyeY) + (zAxisZ * eyeZ);

        rm[12] = 0.0f;
        rm[13] = 0.0f;
        rm[14] = 0.0f;
        rm[15] = 1.0f;
    }

    /**
     * A function to convert a quaternion to quaternion Matrix. Please note that Opengl.Matrix is
     * Column Major and so we construct the matrix in Column Major Format. - - - - | 0 4 8 12 | | 1
     * 5 9 13 | | 2 6 10 14 | | 3 7 11 15 | - - - -
     *
     * @param quaternion Input quaternion with float[4]
     * @return Quaternion Matrix of float[16]
     */
    public static float[] quaternionMatrixOpenGL(float[] quaternion) {
        float[] matrix = new float[16];
        normalizeVector(quaternion);

        float x = quaternion[0];
        float y = quaternion[1];
        float z = quaternion[2];
        float w = quaternion[3];

        float x2 = x * x;
        float y2 = y * y;
        float z2 = z * z;
        float xy = x * y;
        float xz = x * z;
        float yz = y * z;
        float wx = w * x;
        float wy = w * y;
        float wz = w * z;

        matrix[0] = 1f - 2f * (y2 + z2);
        matrix[4] = 2f * (xy - wz);
        matrix[8] = 2f * (xz + wy);
        matrix[12] = 0f;

        matrix[1] = 2f * (xy + wz);
        matrix[5] = 1f - 2f * (x2 + z2);
        matrix[9] = 2f * (yz - wx);
        matrix[13] = 0f;

        matrix[2] = 2f * (xz - wy);
        matrix[6] = 2f * (yz + wx);
        matrix[10] = 1f - 2f * (x2 + y2);
        matrix[14] = 0f;

        matrix[3] = 0f;
        matrix[7] = 0f;
        matrix[11] = 0f;
        matrix[15] = 1f;

        return matrix;
    }

    /**
     * Calculates the dot product between two vectors.
     *
     * @param vector1 the first vector.
     * @param vector2 the second vector.
     * @return the dot product.
     */
    public static double dotProduct(float[] vector1, float[] vector2, int dimensions) {
        double total = 0.0;
        for (int i = 0; i < dimensions; i++) {
            total += vector1[i] * vector2[i];
        }
        return total;
    }

    /**
     * Creates a unit vector in the direction of an arbitrary vector. The original vector is
     * modified in place.
     *
     * @param v the vector to normalize.
     */
    public static void normalizeVector(float[] v) {
        float mag2 = v[0] * v[0] + v[1] * v[1] + v[2] * v[2] + v[3] * v[3];
        if (Math.abs(mag2) > 0.00001f && Math.abs(mag2 - 1.0f) > 0.00001f) {
            float mag = (float) Math.sqrt(mag2);
            v[0] = v[0] / mag;
            v[1] = v[1] / mag;
            v[2] = v[2] / mag;
            v[3] = v[3] / mag;
        }
    }

    /**
     * Calculates the distance between 2 points in 2D space.
     *
     * @param point1 the mCoordinates of the first point.
     * @param point2 the mCoordinates of the second point.
     * @return the distance between the 2 points.
     */
    public static float distanceCalculationOnXYPlane(float[] point1, float[] point2) {
        float yDifference = point2[Y] - point1[Y];
        float xDifference = point2[X] - point1[X];
        return (float) Math.sqrt((yDifference * yDifference) + (xDifference * xDifference));
    }

    /**
     * Calculates the distance between 2 points in 3D space.
     *
     * @param point1 the mCoordinates of the first point.
     * @param point2 the mCoordinates of the second point.
     * @return the distance between the 2 points.
     */
    public static float distanceCalculationInXYZSpace(float[] point1, float[] point2) {
        float zDifference = point2[Z] - point1[Z];
        float yDifference = point2[Y] - point1[Y];
        float xDifference = point2[X] - point1[X];
        return (float) Math.sqrt((zDifference * zDifference) + (yDifference * yDifference) +
                (xDifference * xDifference));
    }

    /**
     * Puts the given coordinates in a printable format.
     *
     * @param coordinates the mCoordinates to print.
     * @return the mCoordinates formatted.
     */
    public static String coordinatesToString(float[] coordinates) {
        DecimalFormat threeDec = new DecimalFormat("0.0");
        String formattedCoordinates = "[";
        for (int i = 0; i < coordinates.length; i++) {
            formattedCoordinates += threeDec.format(coordinates[i]);
            if (i < (coordinates.length - 1)) {
                formattedCoordinates += ", ";
            } else {
                formattedCoordinates += "]";
            }
        }
        return formattedCoordinates;
    }

    public static float[] getDeviceOrientationMatrix(int toRotate) {
        float[] deviceMatrix;

        switch (toRotate) {
            case ORIENTATION_0:
            case ORIENTATION_360_ANTI_CLOCKWISE:
                deviceMatrix = new float[]{1.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f};
                break;
            case ORIENTATION_90_ANTI_CLOCKWISE:
                deviceMatrix = new float[]{
                        0.0f, -1.0f, 0.0f, 0.0f,
                        1.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f};
                break;
            case ORIENTATION_180_ANTI_CLOCKWISE:
                deviceMatrix = new float[]{
                        -1.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, -1.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f};
                break;
            case ORIENTATION_270_ANTI_CLOCKWISE:
                deviceMatrix = new float[]{
                        0.0f, 1.0f, 0.0f, 0.0f,
                        -1.0f, 0.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 1.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 1.0f};
                break;
            default:
                throw new RuntimeException("Unexpected orientation that cannot be dealt with!");
        }

        return deviceMatrix;
    }
}

