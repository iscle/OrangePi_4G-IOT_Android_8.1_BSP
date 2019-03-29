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

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/**
 * Defines what should be drawn to a given {@link RenderedSurfaceView} for every frame.
 */
public interface SurfaceRenderer {

    /**
     * Draws a single frame onto the canvas of a {@link RenderedSurfaceView}.
     *
     * @param surfaceCanvas the locked surface canvas corresponding to a single
     * {@link SurfaceHolder}.
     */
    void onDrawFrame(Canvas surfaceCanvas);
}
