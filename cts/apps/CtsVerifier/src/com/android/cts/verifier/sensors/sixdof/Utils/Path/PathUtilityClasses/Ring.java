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

import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.RingRenderable;

import java.util.ArrayList;

/**
 * Ring object, contains all the information about a ring.
 */
public class Ring {
    private final float[] mLocation;
    private final ArrayList<float[]> mRectangleHitBox;
    private final float[] mRotation;
    private final int mPathNumber;
    private boolean mEntered;
    private RingRenderable mRingRenderable;
    private boolean mSoundPlayed = false;

    /**
     * Constructor to the ring. The ring is always initialised to not entered.
     *
     * @param location        the location of the center of the ring
     * @param pathNumber      the path that the ring is located along
     * @param rotation        the orientation of the ring
     * @param rectangleHitBox the four corners of the rectangular hit box covered by the ring in a
     *                        top down view
     */
    public Ring(float[] location, int pathNumber,
                float[] rotation, ArrayList<float[]> rectangleHitBox) {
        mLocation = location;
        mEntered = false;
        mPathNumber = pathNumber;
        mRotation = rotation;
        mRectangleHitBox = rectangleHitBox;
        mSoundPlayed = false;
    }

    /**
     * Sets whether the ring has been entered or not.
     *
     * @param entered true if the ring is entered, false if the ring has not been entered
     */
    public void setEntered(boolean entered) {
        mEntered = entered;
    }

    /**
     * Sets whether the sound has been played or not.
     *
     * @param soundPlayed the state of whether the sound has been played or not
     */
    public void setSoundPlayed(boolean soundPlayed) {
        mSoundPlayed = soundPlayed;
    }

    /**
     * Returns the location if the center of the ring.
     */
    public float[] getLocation() {
        return mLocation;
    }

    /**
     * Returns the path the ring is located along.
     */
    public int getPathNumber() {
        return mPathNumber;
    }

    /**
     * Returns the coordinates of the four corners of the rectangular hit box.
     */
    public ArrayList<float[]> getRectangleHitBox() {
        return new ArrayList<>(mRectangleHitBox);
    }

    /**
     * Returns the orientation the ring is at.
     */
    public float[] getRingRotation() {
        return mRotation;
    }

    /**
     * Returns true if the ring had been entered, false if the ring has not been entered.
     */
    public boolean isEntered() {
        return mEntered;
    }

    public RingRenderable getRingRenderable() {
        return mRingRenderable;
    }

    /**
     * Returns true if the sound has been played, false if the sound has not been played.
     */
    public boolean isSoundPlayed() {
        return mSoundPlayed;
    }

    public void setRingRenderable(RingRenderable mRingRenderable) {
        this.mRingRenderable = mRingRenderable;
    }
}
