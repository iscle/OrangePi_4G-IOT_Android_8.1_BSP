/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.view;

/**
 * Represents coordinates where (x, y) = (0, 0) represents the top-left most point.
 */
public class Position {
    private final float mX;
    private final float mY;

    public Position(float x, float y) {
        mX = x;
        mY = y;
    }

    public float getX() {
        return mX;
    }

    public float getY() {
        return mY;
    }

    /**
     * @return The vector dot product between {@code this} and another {@link Position}.
     */
    public double dotProduct(Position other) {
        return (mX * other.mX) + (mY * other.mY);
    }

    /**
     * @return The euclidean distance between {@code this} and the other {@link Position}.
     */
    public double distanceTo(Position other) {
        return Math.sqrt(Math.pow((mX - other.mX), 2) + Math.pow((mY - other.mY), 2));
    }

    /**
     * Returns the closest double approximation to the smallest angle swept out by an arc from
     * {@code this} to the other {@link Position}, given the origin of the arc.
     *
     * @param origin The {@link Position} to use as the origin of the arc.
     * @return The angle swept out, in radians within the range {@code [-pi..pi]}. A negative double
     * indicates that the smallest angle swept out is in the clockwise direction, and a positive
     * double indicates otherwise.
     */
    public double arcAngleTo(Position other, Position origin) {
        // Compute the angle of the polar representation of this and other w.r.t. the arc origin.
        double originToThisAngle = Math.atan2(origin.mY - mY, mX - origin.mX);
        double originToOtherAngle = Math.atan2(origin.mY - other.mY, other.mX - origin.mX);
        double difference = originToOtherAngle - originToThisAngle;

        // If the difference exceeds PI or is less then -PI, then we should compensate to
        // bring the value back into the [-pi..pi] range by removing/adding a full revolution.
        if (difference < -Math.PI) {
            difference += 2 * Math.PI;
        } else if (difference > Math.PI){
            difference -= 2 * Math.PI;
        }
        return difference;
    }

    /**
     * Returns the closest double approximation to the angle to the other {@link Position}.
     *
     * @return The angle swept out, in radians within the range {@code [-pi..pi]}.
     */
    public double angleTo(Position other) {
        return Math.atan2(other.mY - mY, other.mX - mX);
    }

    /**
     * Defines equality between pairs of {@link Position}s.
     * <p>
     * Two Position instances are defined to be equal if their x and y coordinates are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Position)) {
            return false;
        }
        Position other = (Position) o;
        return (Float.compare(other.mX, mX) == 0) && (Float.compare(other.mY, mY) == 0);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Float.floatToIntBits(mX);
        result = 31 * result + Float.floatToIntBits(mY);
        return result;
    }
}
