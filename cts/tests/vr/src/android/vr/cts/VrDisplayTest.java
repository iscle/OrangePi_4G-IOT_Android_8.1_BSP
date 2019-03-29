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
package android.vr.cts;

import android.content.Context;
import android.content.Intent;
import android.opengl.EGL14;
import android.opengl.GLES32;
import android.test.ActivityInstrumentationTestCase2;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.nio.IntBuffer;

public class VrDisplayTest extends ActivityInstrumentationTestCase2<OpenGLESActivity> {

    private OpenGLESActivity mActivity;

    public VrDisplayTest() {
        super(OpenGLESActivity.class);
    }

    private OpenGLESActivity getGlEsActivity(int latchCount, int viewIndex) {
        Intent intent = new Intent();
        intent.putExtra(OpenGLESActivity.EXTRA_LATCH_COUNT, latchCount);
        intent.putExtra(OpenGLESActivity.EXTRA_VIEW_INDEX, viewIndex);
        intent.putExtra(OpenGLESActivity.EXTRA_PROTECTED, 0);
        intent.putExtra(OpenGLESActivity.EXTRA_PRIORITY, 0);
        setActivityIntent(intent);
        OpenGLESActivity activity = getActivity();
        if (latchCount == 1) {
          assertTrue(activity.waitForFrameDrawn());
        }
        return activity;
    }

    /**
     * Tests that the refresh rate is at least 60Hz.
     */
    public void testRefreshRateIsAtLeast60Hz() throws Throwable {
        final int NUM_FRAMES = 200;
        // Add an extra frame to allow the activity to start up.
        mActivity = getGlEsActivity(NUM_FRAMES + 1, OpenGLESActivity.RENDERER_REFRESHRATE);
        if (!mActivity.supportsVrHighPerformance())
            return;

        // Skip the first frame to allow for startup time.
        mActivity.waitForFrameDrawn();

        // Render a few hundred frames.
        long startNanos = System.nanoTime();
        while (!mActivity.waitForFrameDrawn());
        long endNanos = System.nanoTime();
        int error = mActivity.glGetError();
        assertEquals(GLES32.GL_NO_ERROR, error);

        double fps = NUM_FRAMES / (double)(endNanos - startNanos) * 1e9;
        assertTrue(fps >= 59.);
    }

    /**
     * Tests that the display resolution is at least 1080p.
     */
    public void testDisplayResolution() {
        mActivity = getGlEsActivity(1, OpenGLESActivity.RENDERER_BASIC);
        if (!mActivity.supportsVrHighPerformance())
            return;

        WindowManager windowManager = (WindowManager)mActivity.getSystemService(
            Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();

        // Find the real screen size.
        int displayWidth;
        int displayHeight;
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        if (metrics.widthPixels > metrics.heightPixels) {
          displayWidth = metrics.widthPixels;
          displayHeight = metrics.heightPixels;
        } else {
          displayWidth = metrics.heightPixels;
          displayHeight = metrics.widthPixels;
        }
        assertTrue(displayWidth >= 1920);
        assertTrue(displayHeight >= 1080);
    }

    /**
     * Tests that the display dimensions are between 4.7" and 6".
     */
    public void testDisplayDimensions() {
        mActivity = getGlEsActivity(1, OpenGLESActivity.RENDERER_BASIC);
        if (!mActivity.supportsVrHighPerformance())
            return;

        WindowManager windowManager = (WindowManager)mActivity.getSystemService(
            Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);

        double width = metrics.widthPixels / metrics.xdpi;
        double height = metrics.heightPixels / metrics.ydpi;
        double diagonalLength = Math.sqrt(width * width + height * height);

        assertTrue(diagonalLength >= 4.7);
        assertTrue(diagonalLength <= 6.3);
    }
}
