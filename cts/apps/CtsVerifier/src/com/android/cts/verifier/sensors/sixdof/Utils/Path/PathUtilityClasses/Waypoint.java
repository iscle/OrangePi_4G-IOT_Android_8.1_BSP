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
package com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses;

import com.android.cts.verifier.sensors.sixdof.Utils.Manager;

/**
 * Waypoint class used to give a waypoint a structure.
 */
public class Waypoint {
    private final float[] mCoordinates;
    private final boolean mUserGenerated;
    private final Manager.Lap mLap;

    /**
     * Constructor for the class used to create the waypoint.
     *
     * @param coordinates   the location of the new waypoint
     * @param userGenerated indicates whether it is a marker or a path point
     * @param lap           the phase of the test the waypoint is in
     */
    public Waypoint(float[] coordinates, boolean userGenerated, Manager.Lap lap) {
        this.mCoordinates = coordinates;
        this.mUserGenerated = userGenerated;
        this.mLap = lap;
    }

    /**
     * Returns the mCoordinates of the waypoint.
     */
    public float[] getCoordinates() {
        return mCoordinates;
    }

    /**
     * Returns who placed the waypoint.
     */
    public boolean isUserGenerated() {
        return mUserGenerated;
    }

    /**
     * Returns the mLap the waypoint was placed on.
     */
    public Manager.Lap getLap() {
        return mLap;
    }
}
