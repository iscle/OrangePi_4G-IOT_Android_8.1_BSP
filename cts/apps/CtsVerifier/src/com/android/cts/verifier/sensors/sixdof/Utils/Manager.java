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

import com.android.cts.verifier.sensors.sixdof.Dialogs.BaseResultsDialog;
import com.android.cts.verifier.sensors.sixdof.Interfaces.AccuracyListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.BaseUiListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.ComplexMovementListener;
import com.android.cts.verifier.sensors.sixdof.Interfaces.RobustnessListener;
import com.android.cts.verifier.sensors.sixdof.Renderer.BaseRenderer;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ReferencePath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.RotationData;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;
import com.android.cts.verifier.sensors.sixdof.Utils.PoseProvider.PoseData;
import com.android.cts.verifier.sensors.sixdof.Utils.ResultObjects.ResultObject;
import com.android.cts.verifier.sensors.sixdof.Utils.TestPhase.AccuracyTest;
import com.android.cts.verifier.sensors.sixdof.Utils.TestPhase.ComplexMovementTest;
import com.android.cts.verifier.sensors.sixdof.Utils.TestPhase.RobustnessTest;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Manages all of the tests.
 */
public class Manager {
    private Lap mLap = Lap.LAP_1;
    public static final int MAX_MARKER_NUMBER = 5;
    private ReferencePath mReferencePath = new ReferencePath();
    private AccuracyTest mAccuracyTest;
    private RobustnessTest mRobustnessTest;
    private ComplexMovementTest mComplexMovementTest;
    private TestReport mTestReport;
    private float mRemainingPath;
    private long mTimeRemaining;

    public enum Lap {
        LAP_1,
        LAP_2,
        LAP_3,
        LAP_4,
    }

    private ComplexMovementListener mComplexMovementListener;
    private RobustnessListener mRobustnessListener;
    private AccuracyListener mAccuracyListener;
    private BaseUiListener mBaseUiListener;

    /**
     * Links the listeners to the activity.
     *
     * @param context reference to the activity.
     */
    public void setupListeners(Context context) {
        mAccuracyListener = (AccuracyListener) context;
        mRobustnessListener = (RobustnessListener) context;
        mComplexMovementListener = (ComplexMovementListener) context;
        mBaseUiListener = (BaseUiListener) context;
    }

    /**
     * Removes the references to the activity so that the activity can be properly terminated.
     */
    public void stopListening() {
        mRobustnessListener = null;
        mAccuracyListener = null;
        mBaseUiListener = null;
        mComplexMovementListener = null;
    }

    public void ringEntered(Ring ring) {
        mComplexMovementListener.onRingEntered(ring);
    }

    /**
     * Indicated that the pose provider is ready.
     */
    public void onPoseProviderReady() {
        mBaseUiListener.onPoseProviderReady();
    }

    /**
     * Constructor for the class.
     *
     * @param testReport a reference to the test report to be used to record failures.
     */
    public Manager(TestReport testReport) {
        mTestReport = testReport;
    }

    /**
     * Adds the waypoint data to the appropriate path.
     *
     * @param coordinates   the coordinates to use for the waypoint.
     * @param userGenerated indicates whether the data was user created or system created.
     * @throws WaypointDistanceException    if the location is too close to another.
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     * @throws WaypointStartPointException  if the location is not close enough to the start.
     */
    public void addPoseDataToPath(
            float[] coordinates, boolean userGenerated)
            throws WaypointAreaCoveredException, WaypointDistanceException,
            WaypointStartPointException, WaypointRingNotEnteredException {
        switch (mLap) {
            case LAP_1:
                try {
                    mReferencePath.createWaypointAndAddToPath(coordinates, userGenerated, mLap);
                } catch (WaypointStartPointException exception) {
                    float[] initialCoords = mReferencePath.getPathMarkers().get(0).getCoordinates();
                    String initialWaypointCoords =
                            MathsUtils.coordinatesToString(initialCoords);
                    String distance = String.valueOf(
                            MathsUtils.distanceCalculationInXYZSpace(
                                    initialCoords, coordinates));
                    String details = "Not close enough to initial waypoint:\n"
                            + "Distance:"
                            + distance
                            + "\nInitial Waypoint Coordinates: "
                            + initialWaypointCoords
                            + "\nAttempted placement coordinates: "
                            + MathsUtils.coordinatesToString(coordinates);
                    mTestReport.setFailDetails(details);

                    // We still need to give the exception to UI to display message.
                    throw exception;
                }

                if (mReferencePath.getPathMarkersSize() == MAX_MARKER_NUMBER) {
                    mAccuracyListener.lap1Complete();
                }
                break;
            case LAP_2:
                mAccuracyTest.addWaypointDataToPath(coordinates, userGenerated, mLap);
                break;
            case LAP_3:
                mRobustnessTest.addWaypointDataToPath(coordinates, userGenerated, mLap);
                break;
            case LAP_4:
                mComplexMovementTest.addWaypointDataToPath(coordinates, userGenerated, mLap);
                break;
            default:
                throw new AssertionError("addPoseDataToPath default: Unrecognised lap", null);
        }
        if (userGenerated) {
            mBaseUiListener.onWaypointPlaced();
        }
    }

    /**
     * Removes the last marker from the current lap.
     */
    public void removeLastAddedMarker() {
        boolean resetTest;
        switch (mLap) {
            case LAP_1:
                resetTest = mReferencePath.removeLastMarker();
                break;
            case LAP_2:
                resetTest = mAccuracyTest.removeLastAddedMarker();
                break;
            case LAP_3:
                resetTest = mRobustnessTest.removeLastAddedMarker();
                break;
            case LAP_4:
                resetTest = mComplexMovementTest.removeLastAddedMarker();
                break;
            default:
                throw new AssertionError("removeLastAddedMarker default: Unrecognised lap", null);
        }
        if (resetTest) {
            mAccuracyListener.onReset();
        }
    }

    /**
     * Initiates the accuracy test.
     */
    public void startAccuracyTest() {
        mAccuracyTest = new AccuracyTest(mReferencePath, mTestReport, this);
        mLap = Lap.LAP_2;
    }

    /**
     * Initiates the robustness test.
     */
    public void startRobustnessTest() {
        mRobustnessTest = new RobustnessTest(mReferencePath, mTestReport, this,
                BaseRenderer.getDeviceRotation((Context) mBaseUiListener));
        mLap = Lap.LAP_3;

    }

    /**
     * Initiates the complex movement test.
     */
    public void startComplexMovementTest() {
        mComplexMovementTest = new ComplexMovementTest(mReferencePath, mTestReport, this);
        mLap = Lap.LAP_4;
    }

    /**
     * Indicates that the accuracy test has been completed.
     *
     * @param passList A list to indicate whether the test passes or not.
     */
    public void onAccuracyTestCompleted(HashMap<BaseResultsDialog.ResultType, Boolean> passList) {
        mBaseUiListener.onResult(new ResultObject(passList));
    }

    /**
     * Indicates that the robustness test has been completed.
     *
     * @param robustnessTestResults List containing information about whether the tests failed or
     *                              passed.
     */
    public void onRobustnessTestCompleted(HashMap<BaseResultsDialog.ResultType, Boolean> robustnessTestResults) {
        ResultObject robustnessResult = new ResultObject(robustnessTestResults);
        mBaseUiListener.onResult(robustnessResult);
    }

    /**
     * Indicates that the complex movement test has been completed.
     *
     * @param complexMovementTestResults List containing information about whether the tests failed
     *                                   or passed.
     */
    public void onComplexMovementTestCompleted(HashMap<BaseResultsDialog.ResultType, Boolean> complexMovementTestResults) {
        ResultObject complexMovementResult = new ResultObject(complexMovementTestResults);

        if (complexMovementResult.hasPassed()) {
            mTestReport.setTestState(TestReport.TestStatus.PASS);
        }

        mBaseUiListener.onResult(complexMovementResult);
    }

    /**
     * Sets the path remaining for the user to travel.
     */
    public void calculateRemainingPath() {
        mRemainingPath = mReferencePath.calculatePathRemaining();
    }

    /**
     * Uses the current rotation and location to calculate the rotation detail's. Also gives the UI
     * information about the rotation.
     *
     * @param rotations   Quaternion containing the current rotation.
     * @param translation The location the rotation occurred.
     */
    public void calculateRotationData(float[] rotations, float[] translation) {
        RotationData rotationData = mRobustnessTest.getRotationData(rotations, translation);
        if (rotationData != null) {
            mRobustnessListener.onNewRotationData(rotationData);
        }
    }

    /**
     * Sets the time remaining to place a waypoint.
     */
    public void calculateTimeRemaining() {
        mTimeRemaining = mRobustnessTest.getTimeRemaining();
    }

    /**
     * Handles new pose data.
     *
     * @param currentPose The current pose data.
     */
    public void onNewPoseData(PoseData currentPose) {
        if (mReferencePath.getCurrentPathSize() != 0) {
            switch (mLap) {
                case LAP_1:
                    calculateRemainingPath();
                    break;
                case LAP_2:
                    break;
                case LAP_3:
                    if (mRobustnessTest.getTestPathMarkersSize() > 0) {
                        calculateTimeRemaining();
                        calculateRotationData(currentPose.getRotationAsFloats(), currentPose.getTranslationAsFloats());
                    }
                    break;
                case LAP_4:
                    mComplexMovementTest.checkIfARingHasBeenPassed(currentPose.getTranslationAsFloats());
                    break;
            }
            try {
                addPoseDataToPath(currentPose.getTranslationAsFloats(),
                        false);
            } catch (WaypointException e) {
                throw new AssertionError(
                        "System added waypoint should not be validated", e);
            }
        }
    }

    /**
     * Returns the distance remaining to travel by the user.
     */
    public float getRemainingPath() {
        return mRemainingPath;
    }

    /**
     * Returns the makers in the reference path.
     */
    public ArrayList<Waypoint> getReferencePathMarkers() {
        return mReferencePath.getPathMarkers();
    }

    /**
     * Returns the makers in the accuracy test path.
     */
    public ArrayList<Waypoint> getTestPathMarkers() {
        return mAccuracyTest.getTestPathMarkers();
    }

    /**
     * Returns the time remaining to place the marker.
     */
    public long getTimeRemaining() {
        return mTimeRemaining;
    }

    /**
     * Returns the markers in the robustness test path.
     */
    public ArrayList<Waypoint> getRobustnessMarker() {
        return mRobustnessTest.getTestPathMarkers();
    }

    /**
     * Returns the current phase of the test.
     */
    public Lap getLap() {
        return mLap;
    }

    /**
     * Returns the rings in the ComplexMovement path.
     */
    public ArrayList<Ring> getRings() {
        return mComplexMovementTest.getRings();
    }

    /**
     * Returns the makers in the ComplexMovement test path.
     */
    public ArrayList<Waypoint> getComplexMovementTestMarkers() {
        return mComplexMovementTest.getTestPathMarkers();
    }
}
