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
package com.android.cts.verifier.sensors.sixdof.Renderer.Renderable;

import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.X;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Y;
import static com.android.cts.verifier.sensors.sixdof.Utils.MathsUtils.Z;

import android.opengl.Matrix;

import com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils.DrawParameters;

/**
 * Light object for applying shadows. Not actually rendered, but we make use of matrices in
 * Renderable class.
 */
public class Light extends Renderable {
    private static final float DEFAULT_LIGHT_STRENGTH = 1.0f;

    private float mStrength = DEFAULT_LIGHT_STRENGTH;

    /**
     * Used to hold the transformed position of the light in eye space (after transformation via
     * modelview matrix)
     */
    private final float[] mLightPosInEyeSpace = new float[4];

    /**
     * Creates a light at the given position.
     *
     * @param position coordinates in open gl coordinate system.
     */
    public Light(float[] position) {
        new Light(position, DEFAULT_LIGHT_STRENGTH);
    }

    public float getStrength() {
        return mStrength;
    }

    /**
     * Creates a light at the given position with a given strength.
     *
     * @param position coordinates in open gl coordinate system.
     * @param strength strength of light.
     */
    public Light(float[] position, float strength) {
        mStrength = strength;

        Matrix.setIdentityM(getModelMatrix(), 0);
        Matrix.translateM(getModelMatrix(), 0, position[X], position[Y], position[Z]);
    }

    @Override
    public void draw(DrawParameters drawParameters) {
        // Don't actually need to draw anything here.
    }

    public synchronized float[] getPositionInEyeSpace(float[] viewMatrix) {
        Matrix.multiplyMV(mLightPosInEyeSpace, 0, viewMatrix, 0, getModelMatrix(), 0);
        return mLightPosInEyeSpace;
    }

    public synchronized void updateLightPosition(float[] translation) {
        Matrix.setIdentityM(getModelMatrix(), 0);
        Matrix.translateM(getModelMatrix(), 0, translation[X], translation[Y], translation[Z]);
    }

    @Override
    public void destroy() {
        // Nothing to destroy.
    }
}
