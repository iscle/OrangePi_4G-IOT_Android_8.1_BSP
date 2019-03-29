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
package com.android.cts.verifier.sensors.sixdof.Renderer;

import android.content.Context;

/**
 * Renderer for the Accuracy test.
 */
public class AccuracyRenderer extends BaseRenderer {
    public AccuracyRenderer(Context context) {
        super(context);
    }

    @Override
    protected void doPreRenderingSetup() {
        // Set view and projection matrix to orthogonal so that camera preview fills the screen.
        mViewMatrix = mOrthogonalViewMatrix;
        mProjectionMatrix = mOrthogonalProjectionMatrix;
    }

    @Override
    protected void doTestSpecificRendering() {
        // Update the texture with the latest camera frame if there is an update pending.
        updateCameraTexture();
    }
}
