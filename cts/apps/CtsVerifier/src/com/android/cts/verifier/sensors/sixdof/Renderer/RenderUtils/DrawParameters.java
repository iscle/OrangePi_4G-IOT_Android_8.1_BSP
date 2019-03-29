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
package com.android.cts.verifier.sensors.sixdof.Renderer.RenderUtils;

import com.android.cts.verifier.sensors.sixdof.Renderer.Renderable.Light;

/**
 * Parameters to be passed on a draw call
 */
public class DrawParameters {
    private float[] mViewMatrix;
    private float[] mProjectionMatrix;
    private Light mLight;

    public void update(float[] viewMatrix, float[] projectionMatrix) {
        mViewMatrix = viewMatrix;
        mProjectionMatrix = projectionMatrix;
    }

    public void update(float[] viewMatrix, float[] projectionMatrix, Light light) {
        update(viewMatrix, projectionMatrix);
        mLight = light;
    }

    public float[] getViewMatrix() {
        return mViewMatrix;
    }

    public float[] getProjectionMatrix() {
        return mProjectionMatrix;
    }

    public Light getLight() {
        return mLight;
    }
}
