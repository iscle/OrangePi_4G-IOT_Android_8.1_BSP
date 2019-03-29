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
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.AccuracyPath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.Path;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.ReferencePath;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * TestPhase generic class will be inherited by the other tests.
 */
public abstract class Test {
    public static final int MAX_MARKER_NUMBER = 5;
    private static final float FAILURE_TOLERANCE_PERCENTAGE = 0.025f; // 2.5%
    private String mTestPhaseName;

    protected ArrayList<Float> mMarkerAccuracy = new ArrayList<>();
    protected ArrayList<Float> mPathAccuracy = new ArrayList<>();
    protected ArrayList<Float> mReferencePathDistances = new ArrayList<>();
    private ArrayList<Float> mTestPathDistances = new ArrayList<>();

    protected ReferencePath mReferencePath;
    protected Path mTestPath;
    protected TestReport mTestReport;
    protected Manager mManager;

    /**
     * Constructor for this class.
     *
     * @param referencePath Reference the the reference path.
     * @param testReport    The test report object to record the tests.
     * @param manager       The manager to call when the test is done.
     */
    public Test(ReferencePath referencePath, TestReport testReport, Manager manager, String testPhase) {
        if (referencePath != null) {
            mReferencePath = referencePath;
        } else {
            throw new AssertionError("TestPhase received a null referencePath", null);
        }
        mTestPhaseName = testPhase;
        mTestReport = testReport;
        mManager = manager;
        mTestPath = new AccuracyPath();
        mReferencePathDistances = calculatePathDistance(mReferencePath.getCurrentPath(), mReferencePath.getPathMarkers());
    }

    /**
     * Adds the current waypoint to the test path.
     *
     * @param coordinates   the coordinates to use for the waypoint.
     * @param userGenerated indicates whether the data was user created or system created.
     * @param currentLap    the lap the data was created in.
     * @throws WaypointDistanceException    if the location is too close to another.
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     * @throws WaypointStartPointException  if the location is not close enough to the start.
     */
    public void addWaypointDataToPath(
            float[] coordinates, boolean userGenerated, Manager.Lap currentLap)
            throws WaypointAreaCoveredException, WaypointDistanceException,
            WaypointStartPointException, WaypointRingNotEnteredException {
        mTestPath.createWaypointAndAddToPath(coordinates, userGenerated, currentLap);
        runAdditionalMethods();
    }

    /**
     * Abstract method that is used but subclasses.
     */
    protected abstract void runAdditionalMethods();

    /**
     * Removes the last marker from the chosen lap.
     *
     * @return true of the first marker false if any other marker
     */
    public boolean removeLastAddedMarker() {
        return mTestPath.removeLastMarker();
    }

    /**
     * Performs the tests for this test phase.
     *
     * @return the state of the tests, true if they pass false if they fail.
     */
    protected HashMap<BaseResultsDialog.ResultType, Boolean> executeTests(boolean includeMarkerTest, boolean includePathTest) {
        HashMap<BaseResultsDialog.ResultType, Boolean> testResults = new HashMap<>();
        if (includePathTest) {
            testResults.put(BaseResultsDialog.ResultType.PATH, pathTest());
        }
        if (includeMarkerTest) {
            testResults.put(BaseResultsDialog.ResultType.WAYPOINT, markerTest());
        }
        return testResults;
    }

    /**
     * Calculates the difference between the markers of the laps and executes the marker related
     * test.
     *
     * @return true if the test passes and false if the rest fails.
     */
    private boolean markerTest() {
        float distance;
        for (int i = 0; i < mReferencePath.getPathMarkersSize(); i++) {
            distance = MathsUtils.distanceCalculationInXYZSpace(
                    mReferencePath.getPathMarkers().get(i).getCoordinates(),
                    mTestPath.getPathMarkers().get(i).getCoordinates());
            mMarkerAccuracy.add(distance);
        }
        return markerAccuracyTest();
    }

    /**
     * Runs a check to find any markers that have failed the test and adds them to the test report.
     *
     * @return true if the test passes and false if the rest fails
     */
    private boolean markerAccuracyTest() {
        boolean testState = true;
        for (float markerDifference : mMarkerAccuracy) {
            if (markerDifference > mReferencePath.getFailureTolerance()) {
                recordMarkerTestResults(markerDifference);
                testState = false;
            }
        }
        return testState;
    }

    /**
     * Formats the failed markers into a string to add it to the test report.
     *
     * @param markerDifference the difference which caused the marker to fail
     */
    private void recordMarkerTestResults(float markerDifference) {
        int markerNumber = mMarkerAccuracy.indexOf(markerDifference);
        String referenceMarker = MathsUtils.coordinatesToString(
                mReferencePath.getPathMarkers().get(markerNumber).getCoordinates());
        String testMarker = MathsUtils.coordinatesToString(
                mTestPath.getPathMarkers().get(markerNumber).getCoordinates());
        String testDetails = mTestPhaseName +
                " Marker Accuracy: Distance between the markers too great. Marker: " + markerNumber +
                " Difference: " + markerDifference +
                " Coordinates " + referenceMarker + " " + testMarker + "\n";
        Log.e("Marker Result", testDetails);
        mTestReport.setFailDetails(testDetails);
    }

    /**
     * Executes the the path related tests.
     *
     * @return true if the test passes, false if the test fails
     */
    private boolean pathTest() {
        mTestPathDistances = calculatePathDistance(mTestPath.getCurrentPath(), mTestPath.getPathMarkers());
        calculatePathDifferences();
        return pathAccuracyTest();
    }

    /**
     * Calculates the distance between the markers for the given path.
     *
     * @param pathToCalculate The path that we want to calculate the distances for
     * @param markers         The locations of the user generated markers in that path
     * @return the list of distances for that path
     */
    protected ArrayList<Float> calculatePathDistance(ArrayList<Waypoint> pathToCalculate,
                                                     ArrayList<Waypoint> markers) {
        ArrayList<Float> pathDistances = new ArrayList<>();
        float totalDistance, distance;
        int currentLocation = pathToCalculate.indexOf(markers.get(0));

        while (currentLocation < pathToCalculate.size() - 1) {
            totalDistance = 0;
            do {
                distance = MathsUtils.distanceCalculationOnXYPlane(
                        pathToCalculate.get(currentLocation).getCoordinates(),
                        pathToCalculate.get(currentLocation + 1).getCoordinates());
                totalDistance += distance;
                currentLocation++;
            } while (!pathToCalculate.get(currentLocation).isUserGenerated());
            pathDistances.add(Math.abs(totalDistance));
            if (currentLocation == markers.size() - 1) {
                break;
            }
        }
        return pathDistances;
    }

    /**
     * Calculates the difference between paths on different laps.
     */
    private void calculatePathDifferences() {
        float difference;

        if (!mReferencePathDistances.isEmpty() && !mTestPathDistances.isEmpty()) {
            for (int i = 0; i < mReferencePathDistances.size(); i++) {
                difference = mReferencePathDistances.get(i) - mTestPathDistances.get(i);
                mPathAccuracy.add(Math.abs(difference));
            }
        } else {
            throw new AssertionError("calculatePathDifference has one of the arrays empty", null);
        }
    }

    /**
     * Checks to see if any of the path differences have failed the test and adds them to the test
     * report.
     *
     * @return True if the test passes and false if there is a fail
     */
    private boolean pathAccuracyTest() {
        boolean testState = true;
        for (float path : mPathAccuracy) {
            if (path > mReferencePath.getFailureTolerance()) {
                recordPathTestResults(path);
                testState = false;
            }
        }
        return testState;
    }

    /**
     * Formats the failed paths into a string to add it to the test report.
     *
     * @param difference The distance that failed the test
     */
    private void recordPathTestResults(float difference) {
        int pathNumber = mPathAccuracy.indexOf(difference);
        String referencePath = String.valueOf(mReferencePathDistances.get(pathNumber));
        String testPath = String.valueOf(mTestPathDistances.get(pathNumber));
        String testDetails = mTestPhaseName +
                " Path Length: Path length difference was too great. Path: " + pathNumber +
                " Difference: " + difference +
                " Paths: " + referencePath + " " + testPath + "\n";
        Log.e("Path Result", testDetails);
        mTestReport.setFailDetails(testDetails);
    }

    /**
     * Returns the makers in the test path.
     */
    public ArrayList<Waypoint> getTestPathMarkers() {
        return mTestPath.getPathMarkers();
    }

    /**
     * Returns the size of the current path.
     */
    public int getTestPathMarkersSize() {
        return mTestPath.getPathMarkers().size();
    }
}
