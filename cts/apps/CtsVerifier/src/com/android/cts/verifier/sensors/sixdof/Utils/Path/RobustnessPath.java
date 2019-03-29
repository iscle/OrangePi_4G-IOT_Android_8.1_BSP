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
package com.android.cts.verifier.sensors.sixdof.Utils.Path;

import com.android.cts.verifier.sensors.sixdof.Activities.TestActivity;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.RotationData;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;

import android.opengl.Matrix;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Handles all the path properties of the robustness path.
 */
public class RobustnessPath extends Path {
    public static final long TIME_TO_ADD_MARKER = 20000;
    private static final int MAXIMUM_ROTATION_ANGLE = 40;
    private static final int MINIMUM_ROTATION_ANGLE = MAXIMUM_ROTATION_ANGLE * -1;
    private static final long TARGET_ROTATION_TIMER_INTERVALS = 100;
    private static final float CHANGE_IN_ANGLE = 0.5f;
    private static final int MAXIMUM_ROTATION_INACCURACY = 10;
    private static final double DISTANCE_FROM_MARKER = 0.5F;


    private static final float[] X_AXIS = new float[]{1, 0, 0, 0};

    private float mTargetRotation = 0;
    private boolean mRotationPhase = true;
    private ArrayList<RotationData> mPathRotations = new ArrayList<>();
    private ArrayList<Long> mMarkerTimeStamp = new ArrayList<>();
    private float mDistanceOfPathFailedRotation = 0;

    private int mOpenGlRotation = 0;

    /**
     * Constructor which starts the timer which changes the targetRotation.
     */
    public RobustnessPath(int openGlRotation) {
        mOpenGlRotation = openGlRotation;
        startChangingTargetRotation();
    }

    /**
     * Performs robustness path related checks on a marker.
     *
     * @param coordinates the coordinates to use for the waypoint
     * @throws WaypointDistanceException    if the location is too close to another.
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     * @throws WaypointStartPointException  if the location is not close enough to the start.
     */
    @Override
    public void additionalChecks(float[] coordinates)
            throws WaypointStartPointException, WaypointDistanceException,
            WaypointAreaCoveredException {
        mMarkerTimeStamp.add(System.currentTimeMillis());
        if (mPathMarkers.size() == 0) {
            mTargetRotation = 0;
        }
    }

    /**
     * Starts a timer which changes the target rotation at specified intervals.
     */
    private void startChangingTargetRotation() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                synchronized (TestActivity.POSE_LOCK) {
                    setRotationToMake();
                }
            }
        }, 0, TARGET_ROTATION_TIMER_INTERVALS);
    }

    /**
     * Performs the change to the target rotation.
     */
    private void setRotationToMake() {
        if (mRotationPhase) {
            mTargetRotation = mTargetRotation - CHANGE_IN_ANGLE;
            if (mTargetRotation <= MINIMUM_ROTATION_ANGLE) {
                mRotationPhase = false;
            }
        } else {
            mTargetRotation = mTargetRotation + CHANGE_IN_ANGLE;
            if (mTargetRotation >= MAXIMUM_ROTATION_ANGLE) {
                mRotationPhase = true;
            }
        }
    }

    /**
     * Calculates the time left for the user to place the waypoint.
     *
     * @return the time left based on the current timestamp and the timestamp of the last marker.
     */
    public long calculateTimeRemaining() {
        long timeRemaining;
        if (!mMarkerTimeStamp.isEmpty()) {
            int lastTimestamp = mMarkerTimeStamp.size() - 1;
            timeRemaining = System.currentTimeMillis() - mMarkerTimeStamp.get(lastTimestamp);
            return TIME_TO_ADD_MARKER - timeRemaining;
        }
        return TIME_TO_ADD_MARKER;
    }

    /**
     * Converts the rotation from quaternion to euler.
     *
     * @param rotationQuaternion The quaternions of the current rotation.
     * @return The euler rotation.
     */
    private float calculateRotation(float[] rotationQuaternion) {
        float qx = rotationQuaternion[0];
        float qy = rotationQuaternion[1];
        float qz = rotationQuaternion[2];
        float qw = rotationQuaternion[3];

        // Set initial Vector to be -(X Axis).
        double x = -X_AXIS[0];
        double y = X_AXIS[1];
        double z = X_AXIS[2];

        // Create quaternion based rotation matrix and extract the values that we need.
        final double X = x * (qy * qy + qx * qx - qz * qz - qw * qw)
                + y * (2 * qy * qz - 2 * qx * qw)
                + z * (2 * qy * qw + 2 * qx * qz);
        final double Y = x * (2 * qx * qw + 2 * qy * qz)
                + y * (qx * qx - qy * qy + qz * qz - qw * qw)
                + z * (-2 * qx * qy + 2 * qz * qw);
        final double Z = x * (-2 * qx * qz + 2 * qy * qw)
                + y * (2 * qx * qy + 2 * qz * qw)
                + z * (qx * qx - qy * qy - qz * qz + qw * qw);

        // Invert X and Z axis.
        float[] values = {(float) Z, (float) Y, (float) X, 0.0f};
        MathsUtils.normalizeVector(values);

        // Rotate the X axis based on the orientation of the device.
        float[] adjustedXAxis = new float[4];
        Matrix.multiplyMV(adjustedXAxis, 0, MathsUtils.getDeviceOrientationMatrix(mOpenGlRotation),
                0, X_AXIS, 0);

        // Calculate angle between current pose and adjusted X axis.
        double angle = Math.acos(MathsUtils.dotProduct(values, adjustedXAxis, MathsUtils.VECTOR_3D));

        // Set our angle to be 0 based when upright.
        angle = Math.toDegrees(angle) - MathsUtils.ORIENTATION_90_ANTI_CLOCKWISE;
        angle *= -1;

        return (float) angle;
    }

    /**
     * Test the rotation and create a rotation object.
     *
     * @param rotationQuaternion    The quaternions of the current rotation.
     * @param rotationLocation      The location of the point with the rotation.
     * @param referencePathMarkers  The list of markers in the reference path.
     * @param maximumDistanceToFail The distance that auto fails the test.
     * @return The rotation data if the rotation doesn't cause the test to be invalid, null if the
     * rotation causes the rest to be invalid.
     */
    public RotationData handleRotation(float[] rotationQuaternion, float[] rotationLocation,
                                       ArrayList<Waypoint> referencePathMarkers,
                                       float maximumDistanceToFail) {
        float eulerRotation = calculateRotation(rotationQuaternion);
        boolean rotationTest = testRotation(eulerRotation, rotationLocation);
        boolean rotationTestable = checkIfRotationTestable(rotationLocation, referencePathMarkers);
        if (mDistanceOfPathFailedRotation > maximumDistanceToFail) {
            return null;
        } else {
            return createRotation(eulerRotation, rotationTest, rotationLocation, rotationTestable);
        }
    }

    /**
     * Tests the current rotation against the target rotation.
     *
     * @param eulerRotation    The rotation as a euler angle.
     * @param rotationLocation The location of the current rotation.
     * @return True if the rotation passes, and false if the rotation fails.
     */
    private boolean testRotation(double eulerRotation, float[] rotationLocation) {
        boolean rotationTestState = true;
        double rotationDifference = Math.abs(eulerRotation - mTargetRotation);
        if (rotationDifference > MAXIMUM_ROTATION_INACCURACY) {
            mDistanceOfPathFailedRotation += MathsUtils.distanceCalculationOnXYPlane(
                    rotationLocation, mCurrentPath.get(mCurrentPath.size() - 1).getCoordinates());
            rotationTestState = false;
        }
        return rotationTestState;
    }

    /**
     * Checks to make sure the rotation not close to other markers.
     *
     * @param rotationLocation     The location of the point to validate the distance.
     * @param referencePathMarkers The list of markers in the reference path.
     * @return true if the location is not close to a marker, false if the location is close to a
     * marker.
     */
    private boolean checkIfRotationTestable(
            float[] rotationLocation, ArrayList<Waypoint> referencePathMarkers) {
        for (Waypoint marker : referencePathMarkers) {
            if (MathsUtils.distanceCalculationInXYZSpace(marker.getCoordinates(),
                    rotationLocation) < DISTANCE_FROM_MARKER) {
                return false;
            }
        }
        return true;
    }

    /**
     * Creates a rotation data object.
     *
     * @param currentRotation       The rotation of the current point.
     * @param rotationTestState     Indicates whether the rotation fails or passes the test.
     * @param rotationLocation      The location of the current point.
     * @param testableRotationState Indicates whether the rotation is valid for testing.
     * @return Reference to the rotation data object which contains the rotation.
     */
    private RotationData createRotation(
            float currentRotation, boolean rotationTestState, float[] rotationLocation,
            boolean testableRotationState) {
        RotationData rotationData = new RotationData(
                mTargetRotation, currentRotation, rotationTestState, rotationLocation,
                testableRotationState);
        mPathRotations.add(rotationData);
        return rotationData;
    }

    /**
     * Returns the timestamps for the markers in the path.
     */
    public ArrayList<Long> getMarkerTimeStamp() {
        return new ArrayList<>(mMarkerTimeStamp);
    }

    /**
     * Returns the number of timestamps collected.
     */
    public int getMarkerTimeStampSize() {
        return mMarkerTimeStamp.size();
    }

    /**
     * Returns the rotations recorded for this path.
     */
    public int getRobustnessPathRotationsSize() {
        return mPathRotations.size();
    }

    /**
     * Returns the number of failed rotations.
     */
    public int getFailedRotationsSize() {
        ArrayList<RotationData> failedRotations = new ArrayList<>();
        for (RotationData rotationObject : mPathRotations) {
            if (!rotationObject.getRotationTestState() && rotationObject.getRotationState()) {
                failedRotations.add(rotationObject);
            }
        }
        return failedRotations.size();
    }

    /**
     * Returns the number of passed rotations.
     */
    public int getPassedRotationsSize() {
        ArrayList<RotationData> passedRotations = new ArrayList<>();
        for (RotationData rotationObject : mPathRotations) {
            if (rotationObject.getRotationTestState() && rotationObject.getRotationState()) {
                passedRotations.add(rotationObject);
            }
        }
        return passedRotations.size();
    }
}
