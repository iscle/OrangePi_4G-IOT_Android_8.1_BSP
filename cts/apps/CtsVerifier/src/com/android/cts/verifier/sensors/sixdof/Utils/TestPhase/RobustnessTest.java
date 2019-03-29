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

package com.android.cts.verifier.sensors.sixdof.Utils.TestPhase;


import com.android.cts.verifier.sensors.sixdof.Dialogs.BaseResultsDialog;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.TestReport;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ReferencePath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.RobustnessPath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.RotationData;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Handles all the Robustness test related features.
 */
public class RobustnessTest extends Test {
    private static final float MAXIMUM_PERCENT_ROTATION_FAILURE = 50f;
    private boolean mResultsGiven = false;
    private ArrayList<Long> mTimeDifferences = new ArrayList<>();
    private float mDistanceOfPathToFail;

    /**
     * Created a new robustness path which is to be used in this test.
     *
     * @param referencePath Reference the the reference path.
     * @param testReport    The test report object to record the tests.
     * @param manager       The manager to call when the test is done.
     */
    public RobustnessTest(ReferencePath referencePath, TestReport testReport, Manager manager,
                          int openGlRotation) {
        super(referencePath, testReport, manager, "Robustness Test");
        mTestPath = new RobustnessPath(openGlRotation);
        float mPathTotalDistance = 0;
        for (float distance : mReferencePathDistances) {
            mPathTotalDistance += distance;
        }
        mDistanceOfPathToFail = (MAXIMUM_PERCENT_ROTATION_FAILURE / 100f) * mPathTotalDistance;
    }

    /**
     * Implementation of the abstract method which check whether the test is complete.
     */
    @Override
    protected void runAdditionalMethods() {
        if (mTestPath.getPathMarkersSize() == MAX_MARKER_NUMBER && !mResultsGiven) {
            mResultsGiven = true;
            executeRobustnessTests();
        }
    }

    /**
     * Starts the robustness tests.
     */
    private void executeRobustnessTests() {
        HashMap<BaseResultsDialog.ResultType, Boolean> robustnessTestResults;
        robustnessTestResults = executeTests(true, true);
        robustnessTestResults.put(BaseResultsDialog.ResultType.TIME, timerTest());
        robustnessTestResults.put(BaseResultsDialog.ResultType.ROTATION, rotationTest());
        mManager.onRobustnessTestCompleted(robustnessTestResults);
    }

    /**
     * Test to check whether the waypoint was placed in the appropriate time.
     *
     * @return true if all waypoint times were met, fail if a waypoint was placed after the time
     * expired
     */
    private boolean timerTest() {
        calculateTimeBetweenMarkers();
        boolean state = true;
        for (int i = 0; i < mTimeDifferences.size(); i++) {
            if (mTimeDifferences.get(i) > RobustnessPath.TIME_TO_ADD_MARKER) {
                recordTimerTestResults(i);
                state = false;
            }
        }
        return state;
    }

    /**
     * Calculates the time it took to place a waypoint.
     */
    private void calculateTimeBetweenMarkers() {
        long timeDifference;
        ArrayList<Long> markerTimeStamps = ((RobustnessPath) mTestPath).getMarkerTimeStamp();
        for (int i = 1; i < ((RobustnessPath) mTestPath).getMarkerTimeStampSize(); i++) {
            timeDifference = markerTimeStamps.get(i) - markerTimeStamps.get(i - 1);
            mTimeDifferences.add(timeDifference);
        }
    }

    /**
     * Formats the failed times into a string to add it to the test report.
     *
     * @param markerLocation The marker location which failed the test. Used to get the data needed
     *                       for the test report
     */
    private void recordTimerTestResults(int markerLocation) {
        long failedTime = mTimeDifferences.get(markerLocation);
        String markerToPlace = MathsUtils.coordinatesToString(
                mTestPath.getPathMarkers().get(markerLocation).getCoordinates());
        String testDetails =
                "Timer test: Marker placement was too slow that timer expired. Target time: "
                        + RobustnessPath.TIME_TO_ADD_MARKER / 1000 + " Completed time: " + Math.abs(failedTime) / 1000 +
                        " Marker: " + markerLocation + " Coordinates:" + markerToPlace + "\n";
        Log.e("Timer Result", testDetails);
        mTestReport.setFailDetails(testDetails);
    }

    /**
     * Test to check whether the rotation test has passed based on the percent of failed rotations.
     *
     * @return true if the test passes, false if the test fails
     */
    private boolean rotationTest() {
        float failedRotations = ((RobustnessPath) mTestPath).getFailedRotationsSize();
        float totalRotations = ((RobustnessPath) mTestPath).getRobustnessPathRotationsSize();
        float percentage = (failedRotations / totalRotations) * 100;
        if (totalRotations == 0) {
            Log.e("rotationResult", "Total was 0");
            return false;
        }
        if (percentage > MAXIMUM_PERCENT_ROTATION_FAILURE) {
            Log.d("rotationResult", "failed");
            recordRotationTestResults(percentage, failedRotations, totalRotations);
            return false;
        } else {
            Log.d("getFailedRotationSize", "" + failedRotations);
            Log.d("total", "" + totalRotations);
            Log.d("rotationResult", "passed ");
            Log.d("rotationResult", "" + percentage);
            return true;
        }
    }

    /**
     * Formats the failed rotations into a string to add it to the test report.
     *
     * @param percentFailed   Percentage of failed rotations
     * @param failedRotations number of failed rotations
     * @param totalRotations  number of rotations made
     */
    private void recordRotationTestResults(float percentFailed, float failedRotations, float totalRotations) {
        String testDetails =
                "Rotation test: Rotation fails were too great. Target rotation percent: "
                        + MAXIMUM_PERCENT_ROTATION_FAILURE + " GivenRotation percent: " + percentFailed +
                        " Failed rotation: " + failedRotations + " Total rotations:" + totalRotations + "\n";
        Log.e("Timer Result", testDetails);
        mTestReport.setFailDetails(testDetails);
    }

    /**
     * gets the result of comparing the current rotation
     *
     * @param rotationQuaternion The quaternions of the current rotation
     * @param location           The location of the point with the rotation
     * @return The rotation about the current rotation
     */
    public RotationData getRotationData(float[] rotationQuaternion, float[] location) {
        RotationData rotation = ((RobustnessPath) mTestPath).handleRotation(
                rotationQuaternion, location, mReferencePath.getPathMarkers(), mDistanceOfPathToFail);
        if (rotation == null) {
            if (!mResultsGiven) {
                mResultsGiven = true;
                HashMap<BaseResultsDialog.ResultType, Boolean> testFailed = new HashMap<>();
                testFailed.put(BaseResultsDialog.ResultType.WAYPOINT, false);
                testFailed.put(BaseResultsDialog.ResultType.PATH, false);
                testFailed.put(BaseResultsDialog.ResultType.TIME, false);
                testFailed.put(BaseResultsDialog.ResultType.ROTATION, false);
                String testDetails = "Test terminated as it its impossible to pass the remaining rotations";
                Log.e("Rotation test:", mDistanceOfPathToFail + "");
                Log.e("Rotation test:", testDetails);
                mTestReport.setFailDetails(testDetails);
                mManager.onRobustnessTestCompleted(testFailed);
            }
            return null;
        } else {
            return rotation;
        }

    }

    /**
     * Returns the time remaining for the user to place the marker
     */
    public long getTimeRemaining() {
        return ((RobustnessPath) mTestPath).calculateTimeRemaining();
    }
}
