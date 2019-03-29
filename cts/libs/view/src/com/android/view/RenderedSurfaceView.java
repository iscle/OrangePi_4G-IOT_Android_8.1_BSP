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

import android.content.Context;
import android.graphics.Canvas;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * A {@link SurfaceView} that manages its own rendering thread and uses a {@link SurfaceRenderer} to
 * dictate what should be drawn for each frame.
 */
public class RenderedSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    private static final int JOIN_TIME_OUT_MS = 1000;

    private SurfaceRenderer mRenderer;
    private volatile boolean mRunning;
    private Thread mRenderThread;

    public RenderedSurfaceView(Context context) {
        super(context);

        mRenderer = null;
        mRunning = false;
        getHolder().addCallback(this);
    }

    /**
     * Sets the renderer to be used.
     *
     * <i>Must</i> be called after instantiation.
     */
    public void setRenderer(SurfaceRenderer renderer) {
        mRenderer = renderer;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mRenderThread = new RenderThread();
        mRunning = true;
        mRenderThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        // Configuration changes are disabled so surface changes can be ignored.
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mRunning = false;
        // Wait for rendering thread to halt after it has observed that it should no longer render
        while (true) {
            try {
                mRenderThread.join(JOIN_TIME_OUT_MS);
                break;
            } catch (InterruptedException e) {
                // Ignore spurious wakeup
            }
        }
        mRenderThread = null;
    }

    /**
     * Thread to run the rendering loop for this SurfaceView.
     */
    private final class RenderThread extends Thread {
        private static final int SLEEP_TIME_MS = 16;

        @Override
        public void run() {
            while (mRunning) {
                SurfaceHolder holder = getHolder();
                Canvas surfaceCanvas = holder.lockCanvas();
                // Draw onto canvas if valid
                if (surfaceCanvas != null && mRenderer != null) {
                    mRenderer.onDrawFrame(surfaceCanvas);
                    holder.unlockCanvasAndPost(surfaceCanvas);
                }
                try {
                    sleep(SLEEP_TIME_MS);
                } catch (InterruptedException e) {
                    // Stop rendering if interrupted
                    break;
                }
            }
        }
    }
}
