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

/**
 * Contains information about a rotation.
 */
public class RotationData {
    private final float mTargetRotation;
    private final float mCurrentRotation;
    private final boolean mRotationTestState;
    private final float[] mLocation;
    private final boolean mRotationState;

    /**
     * Constructor for this class.
     *
     * @param targetRotation    The rotation to aim for
     * @param currentRotation   The current rotation on the device
     * @param rotationTestState true of the currentRotation the same as the targetRotation, false if
     *                          they are different
     * @param location          The location the rotation was made
     * @param rotationState     true if the rotation is testable, false if it is not
     */
    public RotationData(float targetRotation, float currentRotation,
                        boolean rotationTestState, float[] location, boolean rotationState) {
        mTargetRotation = targetRotation;
        mCurrentRotation = currentRotation;
        mRotationTestState = rotationTestState;
        mLocation = location;
        mRotationState = rotationState;
    }

    /**
     * Returns the rotation to aim for.
     */
    public float getTargetAngle() {
        return mTargetRotation;
    }

    /**
     * Returns the current rotation of the device.
     */
    public float getCurrentAngle() {
        return mCurrentRotation;
    }

    /**
     * Returns whether the rotation passed or failed.
     */
    public boolean getRotationTestState() {
        return mRotationTestState;
    }

    /**
     * Returns whether or not the rotation is testable.
     */
    public boolean getRotationState() {
        return mRotationState;
    }

}
