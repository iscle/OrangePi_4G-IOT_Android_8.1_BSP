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

import java.util.ArrayList;
import java.util.Random;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.VECTOR_2D;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.dotProduct;

import com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils;
import com.android.cts.verifier.sensors.sixdof.Utils.Exceptions.WaypointRingNotEnteredException;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Ring;
import com.android.cts.verifier.sensors.sixdof.Utils.Path.PathUtilityClasses.Waypoint;

/**
 * Handles all the path properties of the ComplexMovement Path.
 */
public class ComplexMovementPath extends com.android.cts.verifier.sensors.sixdof.Utils.Path.Path {
    public static final float DISTANCE_FOR_RING_POSITION = 0.25f;
    public static final int RINGS_PER_PATH = 5;

    private ArrayList<Ring> mRings = new ArrayList<>();
    private Random mRandomGenerator = new Random();
    private int mCurrentLap = 0;
    private float mLocationMapping[][];

    /**
     * Possible locations for a ring.
     */
    private enum RingLocations {
        ORIGINAL,
        TOP,
        DOWN,
        LEFT,
        RIGHT,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
    }

    /**
     * Constructor for this class does the mapping and the creation of rings.
     *
     * @param referencePathDistances The distance between the markers in the reference path
     * @param referencePath          The reference path
     */
    public ComplexMovementPath(
            ArrayList<Float> referencePathDistances, ArrayList<Waypoint> referencePath) {
        mapNineRingLocations();
        generatePathRings(referencePathDistances, referencePath);
    }

    /**
     * Defines the different ring locations that can be used when adding the rings.
     */
    private void mapNineRingLocations() {
        mLocationMapping = new float[RingLocations.values().length][2];
        mLocationMapping[RingLocations.ORIGINAL.ordinal()] = new float[]{0f, 0f};
        mLocationMapping[RingLocations.TOP.ordinal()] =
                new float[]{0f, DISTANCE_FOR_RING_POSITION};
        mLocationMapping[RingLocations.DOWN.ordinal()] =
                new float[]{0f, -DISTANCE_FOR_RING_POSITION};
        mLocationMapping[RingLocations.LEFT.ordinal()] =
                new float[]{-DISTANCE_FOR_RING_POSITION, 0f};
        mLocationMapping[RingLocations.RIGHT.ordinal()] =
                new float[]{DISTANCE_FOR_RING_POSITION, 0f};
        mLocationMapping[RingLocations.TOP_LEFT.ordinal()] =
                new float[]{-DISTANCE_FOR_RING_POSITION, DISTANCE_FOR_RING_POSITION};
        mLocationMapping[RingLocations.TOP_RIGHT.ordinal()] =
                new float[]{DISTANCE_FOR_RING_POSITION, DISTANCE_FOR_RING_POSITION};
        mLocationMapping[RingLocations.BOTTOM_LEFT.ordinal()] =
                new float[]{-DISTANCE_FOR_RING_POSITION, -DISTANCE_FOR_RING_POSITION};
        mLocationMapping[RingLocations.BOTTOM_RIGHT.ordinal()] =
                new float[]{DISTANCE_FOR_RING_POSITION, -DISTANCE_FOR_RING_POSITION};
    }

    /**
     * Performs ComplexMovement path related checks on a marker.
     *
     * @param coordinates the coordinates for the waypoint
     * @throws WaypointRingNotEnteredException if a ring is not entered
     */
    @Override
    public void additionalChecks(float[] coordinates) throws WaypointRingNotEnteredException {
        if (mCurrentLap != 0) {
            for (Ring ring : mRings) {
                if (ring.getPathNumber() == mCurrentLap && !ring.isEntered()) {
                    throw new WaypointRingNotEnteredException();
                }
            }
        }
        mCurrentLap++;
    }

    /**
     * Generates the rings for this path.
     *
     * @param referencePathDistances The distance between the markers in the reference path
     * @param referencePath          The reference path
     */
    private void generatePathRings(
            ArrayList<Float> referencePathDistances, ArrayList<Waypoint> referencePath) {
        ArrayList<Float> distanceBetweenRingSections;
        distanceBetweenRingSections = calculateSectionDistance(referencePathDistances);
        addRingsToPath(referencePath, distanceBetweenRingSections);
    }

    /**
     * Calculates the distance between the rings in a path.
     *
     * @param referencePathDistances The distance between the markers in the reference path.
     * @return The length of a section in the different paths.
     */
    private ArrayList<Float> calculateSectionDistance(ArrayList<Float> referencePathDistances) {
        ArrayList<Float> arrayToReturn = new ArrayList<>();
        for (Float distance : referencePathDistances) {
            arrayToReturn.add(distance / (RINGS_PER_PATH + 1f));
        }
        return arrayToReturn;
    }

    /**
     * Calculates the location for the ring and adds it to the path.
     *
     * @param referencePath               The reference path.
     * @param distanceBetweenRingSections The length of a section in the different paths.
     */
    private void addRingsToPath(
            ArrayList<Waypoint> referencePath, ArrayList<Float> distanceBetweenRingSections) {
        int currentPath = 0;
        Waypoint currentWaypoint = referencePath.get(0);
        for (Float pathIntervalDistance : distanceBetweenRingSections) {
            currentPath++;
            for (int i = 0; i < RINGS_PER_PATH; i++) {
                currentWaypoint = calculateRingLocationOnPath(
                        referencePath, referencePath.indexOf(currentWaypoint), pathIntervalDistance);
                mRings.add(createRing(referencePath, currentWaypoint, currentPath));
            }
            while (!currentWaypoint.isUserGenerated()) {
                currentWaypoint = referencePath.get(referencePath.indexOf(currentWaypoint) + 1);
            }
        }
    }

    /**
     * Creates the ring that will be added onto the path.
     *
     * @param referencePath The reference path.
     * @param waypoint      The waypoint which the ring will be located at.
     * @param currentPath   The part of the lap in which the ring will be placed.
     * @return A reference to the ring created.
     */
    private Ring createRing(ArrayList<Waypoint> referencePath, Waypoint waypoint, int currentPath) {
        float[] ringCenter = waypoint.getCoordinates();
        float[] pointRotation = calculateRingRotation(ringCenter,
                referencePath.get(referencePath.indexOf(waypoint) - 1).getCoordinates());
        int randomNumber = mRandomGenerator.nextInt(RingLocations.values().length);
        RingLocations ringLocationDifference = RingLocations.values()[randomNumber];
        ringCenter[X] += mLocationMapping[ringLocationDifference.ordinal()][0];
        ringCenter[Z] += mLocationMapping[ringLocationDifference.ordinal()][1];
        ArrayList<float[]> rotatedRect = calculateRectangleHitbox(ringCenter, pointRotation);
        return new Ring(ringCenter, currentPath, pointRotation, rotatedRect);
    }

    /**
     * Calculates the orientation of the ring.
     *
     * @param location1 The location of the first point.
     * @param location2 The location of the second point.
     * @return the rotation needed to get the orientation of the ring.
     */
    private float[] calculateRingRotation(float[] location1, float[] location2) {
        float[] rotation = new float[3];
        rotation[X] = location2[X] - location1[X];
        rotation[Y] = location2[Y] - location1[Y];
        rotation[Z] = location2[Z] - location1[Z];
        return rotation;
    }

    /**
     * Calculates the next possible position for the ring to be placed at.
     *
     * @param referencePath        The reference path.
     * @param currentLocation      The location to start calculating from.
     * @param pathIntervalDistance The distance indicating how far apart the rings are going to be.
     * @return The waypoint where the ring will be placed at.
     */
    private Waypoint calculateRingLocationOnPath(
            ArrayList<Waypoint> referencePath, int currentLocation, Float pathIntervalDistance) {
        float pathRemaining = 0;
        while (currentLocation < referencePath.size() - 1) {
            pathRemaining += MathsUtils.distanceCalculationOnXYPlane(
                    referencePath.get(currentLocation).getCoordinates(),
                    referencePath.get(currentLocation + 1).getCoordinates());
            if (pathRemaining >= pathIntervalDistance) {
                return referencePath.get(currentLocation);
            }
            currentLocation++;
        }
        throw new AssertionError(
                "calculateRingLocationOnPath: Ring number and section number don't seem to match up");
    }

    /**
     * Calculates the rectangular hit box for the ring.
     *
     * @param centre   the middle location of the ring.
     * @param rotation the rotation to get the same orientation of the ring.
     * @return The four corners of the rectangle.
     */
    private ArrayList<float[]> calculateRectangleHitbox(float[] centre, float[] rotation) {
        ArrayList<float[]> rectangle = new ArrayList<>();
        float magnitude = (float) Math.sqrt(Math.pow(rotation[X], 2) +
                Math.pow(rotation[Z], 2));
        float lengthScaleFactor = 0.02f / magnitude;
        float widthScaleFactor = 0.17f / magnitude;

        float[] rotationInverse = {0 - rotation[X], 0 - rotation[Y]};
        float[] rotationNinety = {rotation[Y], 0 - rotation[X]};
        float[] rotationNinetyInverse = {0 - rotation[Y], rotation[X]};

        float[] midFront = new float[2];
        midFront[X] = centre[X] + (lengthScaleFactor * rotation[X]);
        midFront[Y] = centre[Y] + (lengthScaleFactor * rotation[Y]);
        float[] midRear = new float[2];
        midRear[X] = centre[X] + (lengthScaleFactor * rotationInverse[X]);
        midRear[Y] = centre[Y] + (lengthScaleFactor * rotationInverse[Y]);

        float[] frontLeft = new float[3];
        frontLeft[Z] = centre[Z];
        frontLeft[X] = midFront[X] + (widthScaleFactor * rotationNinetyInverse[X]);
        frontLeft[Y] = midFront[Y] + (widthScaleFactor * rotationNinetyInverse[Y]);
        float[] frontRight = new float[3];
        frontRight[Z] = centre[Z];
        frontRight[X] = midFront[X] + (widthScaleFactor * rotationNinety[X]);
        frontRight[Y] = midFront[Y] + (widthScaleFactor * rotationNinety[Y]);
        float[] rearLeft = new float[3];
        rearLeft[Z] = centre[Z];
        rearLeft[X] = midRear[X] + (widthScaleFactor * rotationNinetyInverse[X]);
        rearLeft[Y] = midRear[Y] + (widthScaleFactor * rotationNinetyInverse[Y]);
        float[] rearRight = new float[3];
        rearRight[Z] = centre[Z];
        rearRight[X] = midRear[X] + (widthScaleFactor * rotationNinety[X]);
        rearRight[Y] = midRear[Y] + (widthScaleFactor * rotationNinety[Y]);

        rectangle.add(frontLeft);
        rectangle.add(frontRight);
        rectangle.add(rearRight);
        rectangle.add(rearLeft);
        return rectangle;
    }

    /**
     * Check to see if a ring has been entered.
     *
     * @param location the location of the user to be tested.
     */
    public Ring hasRingBeenEntered(float[] location) {
        float xDifference, yDifference, zDifference;
        for (int i = 0; i < mRings.size(); i++) {
            if (mRings.get(i).getPathNumber() == mCurrentLap) {
                xDifference = Math.abs(mRings.get(i).getLocation()[X] - location[X]);
                yDifference = Math.abs(mRings.get(i).getLocation()[Y] - location[Y]);
                zDifference = Math.abs(mRings.get(i).getLocation()[Z] - location[Z]);
                if (xDifference < 0.17 && yDifference < 0.17 && zDifference < 0.17) {
                    if (checkCollision(mRings.get(i), location)) {
                        return mRings.get(i);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Calculates whether the location of the user is in the rectangular hit box or not.
     *
     * @param ring     the ring to be tested.
     * @param location the location of the user.
     * @return true if the ring is entered and false if it is not.
     */
    private boolean checkCollision(Ring ring, float[] location) {
        float[] rectangleVector1 = new float[2];
        rectangleVector1[X] = ring.getRectangleHitBox().get(0)[X] - ring.getRectangleHitBox().get(3)[X];
        rectangleVector1[Y] = ring.getRectangleHitBox().get(0)[Y] - ring.getRectangleHitBox().get(3)[Y];

        float[] rectangleVector2 = new float[2];
        rectangleVector2[X] = ring.getRectangleHitBox().get(2)[X] - ring.getRectangleHitBox().get(3)[X];
        rectangleVector2[Y] = ring.getRectangleHitBox().get(2)[Y] - ring.getRectangleHitBox().get(3)[Y];

        float[] locationVector = new float[2];
        locationVector[X] = location[X] - ring.getRectangleHitBox().get(3)[X];
        locationVector[Y] = location[Y] - ring.getRectangleHitBox().get(3)[Y];

        if (dotProduct(rectangleVector1, locationVector, VECTOR_2D) > 0) {
            if (dotProduct(rectangleVector1, rectangleVector1, VECTOR_2D)
                    > dotProduct(rectangleVector1, locationVector, VECTOR_2D)) {
                if (dotProduct(rectangleVector2, locationVector, VECTOR_2D) > 0) {
                    if (dotProduct(rectangleVector2, rectangleVector2, VECTOR_2D)
                            > dotProduct(rectangleVector2, locationVector, VECTOR_2D)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the list of rings.
     */
    public ArrayList<Ring> getRings() {
        return new ArrayList<>(mRings);
    }
}
