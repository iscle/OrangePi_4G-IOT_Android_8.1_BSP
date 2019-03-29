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

import com.android.cts.verifier.sensors.sixdof.Utils.Manager;
import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointAreaCoveredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointDistanceException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointStartPointException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;

import java.util.ArrayList;

/**
 * Contains all the information of the current path.
 */
public abstract class Path {
    protected ArrayList<Waypoint> mCurrentPath = new ArrayList<>();
    protected ArrayList<Waypoint> mPathMarkers = new ArrayList<>();

    /**
     * Creates a waypoint and adds it to the path.
     *
     * @param coordinates   the coordinates to use for the waypoint.
     * @param userGenerated indicates whether the data was user created or system created.
     * @param currentLap    the lap the data was created in.
     * @throws WaypointDistanceException       if the location is too close to another.
     * @throws WaypointAreaCoveredException    if the area covered by the user is too little.
     * @throws WaypointStartPointException     if the location is not close enough to the start.
     * @throws WaypointRingNotEnteredException if a ring is not entered.
     */
    public void createWaypointAndAddToPath(
            float[] coordinates, boolean userGenerated, Manager.Lap currentLap)
            throws WaypointStartPointException, WaypointDistanceException,
            WaypointAreaCoveredException, WaypointRingNotEnteredException {
        if (userGenerated) {
            additionalChecks(coordinates);
        }
        Waypoint waypoint = new Waypoint(coordinates, userGenerated, currentLap);
        mCurrentPath.add(waypoint);
        if (waypoint.isUserGenerated()) {
            mPathMarkers.add(waypoint);
        }
    }

    protected float getLengthOfCurrentPath() {
        float length = 0.0f;

        // Start at index 1.
        for (int i = 1; i < mCurrentPath.size(); i++) {
            float distance = MathsUtils.distanceCalculationOnXYPlane(
                    mCurrentPath.get(i).getCoordinates(),
                    mCurrentPath.get(i - 1).getCoordinates());
            length += Math.abs(distance);
        }

        return length;
    }

    /**
     * Abstract method used by classes that extend this one to run additional functionality.
     *
     * @param coordinates the coordinates for the waypoint.
     * @throws WaypointDistanceException       if the location is too close to another.
     * @throws WaypointAreaCoveredException    if the area covered by the user is too little.
     * @throws WaypointStartPointException     if the location is not close enough to the start.
     * @throws WaypointRingNotEnteredException if a ring is not entered.
     */
    public abstract void additionalChecks(float[] coordinates)
            throws WaypointStartPointException, WaypointDistanceException,
            WaypointAreaCoveredException, WaypointRingNotEnteredException;

    /**
     * Removes the last maker in the current path.
     *
     * @return true of the first marker false if any other marker.
     */
    public boolean removeLastMarker() {
        Waypoint markerToRemove = mPathMarkers.get(mPathMarkers.size() - 1);
        mCurrentPath.remove(markerToRemove);
        mPathMarkers.remove(markerToRemove);
        return false;
    }

    /**
     * Returns the current path.
     */
    public ArrayList<Waypoint> getCurrentPath() {
        return new ArrayList<>(mCurrentPath);
    }

    /**
     * Returns the markers for the current path.
     */
    public ArrayList<Waypoint> getPathMarkers() {
        return new ArrayList<>(mPathMarkers);
    }

    /**
     * Returns the size of the path.
     */
    public int getCurrentPathSize() {
        return mCurrentPath.size();
    }

    /**
     * Returns the number if markers.
     */
    public int getPathMarkersSize() {
        return mPathMarkers.size();
    }
}
