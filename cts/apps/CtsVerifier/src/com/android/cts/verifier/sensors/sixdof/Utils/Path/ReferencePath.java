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

import com.android.cts.verifier.sensors.sixdof.BuildConfig;
import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;

/**
 * Class that deals with reference waypoints.
 */
public class ReferencePath extends Path {
    private static final float FAILURE_TOLERANCE_PERCENTAGE = 0.025f; // 2.5%

    // If in Debug mode, have values that are easier to pass tests with.
    private static final float MINIMUM_DISTANCE_FROM_WAYPOINT = (BuildConfig.DEBUG ? 0f : 3f);
    private static final float MINIMUM_AREA_OF_TRIANGLE = (BuildConfig.DEBUG ? 0f : 2f);
    private static final float MINIMUM_PATH_DISTANCE = (BuildConfig.DEBUG ? 0f : 10f);

    private float mFailureTolerance = 0.0f;

    /**
     * @param coordinates the coordinates to use for the waypoint.
     * @throws WaypointDistanceException    if the location is too close to another
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     * @throws WaypointStartPointException  if the location is not close enough to the start.
     */
    @Override
    public void additionalChecks(float[] coordinates)
            throws WaypointStartPointException, WaypointDistanceException,
            WaypointAreaCoveredException {
        testValidationSelection(coordinates);
    }

    /**
     * Checks if the marker to remove is the first marker and removes all current path details.
     *
     * @return true of the first marker false if any other marker
     */
    @Override
    public boolean removeLastMarker() {
        if (mPathMarkers.size() == 1) {
            mCurrentPath.clear();
            mPathMarkers.clear();
            return true;
        } else {
            return super.removeLastMarker();
        }
    }

    /**
     * Calculates the path that the user still has to travel.
     *
     * @return The distance the user still has to travel.
     */
    public float calculatePathRemaining() {
        float distance, pathRemaining = 0;

        int currentLocation = mCurrentPath.indexOf(mPathMarkers.get(mPathMarkers.size() - 1));

        while (currentLocation < mCurrentPath.size() - 1) {
            distance = MathsUtils.distanceCalculationOnXYPlane(
                    mCurrentPath.get(currentLocation).getCoordinates(),
                    mCurrentPath.get(currentLocation + 1).getCoordinates());
            pathRemaining += distance;
            currentLocation++;
        }
        pathRemaining = MINIMUM_PATH_DISTANCE - pathRemaining;
        return pathRemaining;
    }

    /**
     * Executes the validation tests for the given waypoint.
     *
     * @param coordinates the location of the point to perform validations on.
     * @throws WaypointDistanceException    if the location is too close to another.
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     * @throws WaypointStartPointException  if the location is not close enough to the start.
     */
    public void testValidationSelection(float[] coordinates) throws WaypointDistanceException,
            WaypointAreaCoveredException, WaypointStartPointException {
        if (mPathMarkers.size() < Manager.MAX_MARKER_NUMBER - 1) {
            validateWaypointDistance(coordinates);
            if (mPathMarkers.size() == 2) {
                validateAreaCovered(coordinates);
            }
        } else if (mPathMarkers.size() == Manager.MAX_MARKER_NUMBER - 1) {
            validateBackToStart(coordinates);
        }
    }

    /**
     * Checks to make sure the waypoints added are away from other waypoints.
     *
     * @param coordinates the location of the point to validate the distance.
     * @throws WaypointDistanceException WaypointDistanceException if the location is too close to
     *                                   another.
     */
    private void validateWaypointDistance(float[] coordinates) throws WaypointDistanceException {
        for (Waypoint waypoint : mPathMarkers) {
            if (MathsUtils.distanceCalculationInXYZSpace(waypoint.getCoordinates(),
                    coordinates) < MINIMUM_DISTANCE_FROM_WAYPOINT) {
                throw new WaypointDistanceException();
            }
        }
    }

    /**
     * Checks to make sure enough distance is covered before adding the third waypoint.
     *
     * @param point3 the location used to validate the area.
     * @throws WaypointAreaCoveredException if the area covered by the user is too little.
     */
    private void validateAreaCovered(float[] point3) throws WaypointAreaCoveredException {
        float[] A = mPathMarkers.get(0).getCoordinates();
        float[] B = mPathMarkers.get(1).getCoordinates();

        /* The equation used to calculate the area is:
         * area = 1/2|(Ax - Cx)*(By - Ay) - (Ax - Bx)*(Cy - Ay) */
        try {
            float part1 = (A[0] - point3[0]) * (B[1] - A[1]);
            float part2 = (A[0] - B[0]) * (point3[1] - A[1]);
            float area = 0.5f * Math.abs((part1 - part2));
            if (area <= MINIMUM_AREA_OF_TRIANGLE) {
                throw new WaypointAreaCoveredException();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new AssertionError(
                    "validateAreaCovered was given an array with a length less than 3", e);
        }

    }

    /**
     * Check the last waypoint of the first phase goes back to the start.
     *
     * @param coordinates the location of the point to validate the distance from the start marker.
     * @throws WaypointStartPointException if the location is not close enough to the start.
     */
    private void validateBackToStart(float[] coordinates) throws WaypointStartPointException {
        float[] firstMarkerCoordinates = mPathMarkers.get(0).getCoordinates();
        float distance = MathsUtils.distanceCalculationInXYZSpace(
                firstMarkerCoordinates, coordinates);

        mFailureTolerance = FAILURE_TOLERANCE_PERCENTAGE * getLengthOfCurrentPath();

        float maximumDistanceFromFirstWaypoint = (BuildConfig.DEBUG ? 1000f : mFailureTolerance);

        if (distance > maximumDistanceFromFirstWaypoint) {
            throw new WaypointStartPointException();
        }
    }

    public float getFailureTolerance() {
        return mFailureTolerance;
    }
}
