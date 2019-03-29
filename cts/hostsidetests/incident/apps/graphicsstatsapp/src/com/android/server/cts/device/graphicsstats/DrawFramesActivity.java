/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.graphicsstats;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DrawFramesActivity extends Activity implements Window.OnFrameMetricsAvailableListener {

    public static final int FRAME_JANK_RECORD_DRAW = 1 << 0;
    public static final int FRAME_JANK_ANIMATION = 1 << 1;
    public static final int FRAME_JANK_LAYOUT = 1 << 2;
    public static final int FRAME_JANK_DAVEY_JR = 1 << 3;
    public static final int FRAME_JANK_DAVEY = 1 << 4;
    public static final int FRAME_JANK_MISS_VSYNC = 1 << 5;

    private static final String TAG = "GraphicsStatsDeviceTest";

    private static final int[] COLORS = new int[] {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
    };

    private View mColorView;
    private int mColorIndex;
    private final CountDownLatch mReady = new CountDownLatch(1);
    private Choreographer mChoreographer;
    private CountDownLatch mFramesFinishedFence = mReady;
    private int mFrameIndex;
    private int[] mFramesToDraw;
    private int mDroppedReportsCount = 0;
    private int mRenderedFrames = 0;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().addOnFrameMetricsAvailableListener(this, new Handler());

        mChoreographer = Choreographer.getInstance();
        mColorView = new View(this) {
            {
                setWillNotDraw(false);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                jankIf(FRAME_JANK_RECORD_DRAW);
            }

            @Override
            public void layout(int l, int t, int r, int b) {
                super.layout(l, t, r, b);
                jankIf(FRAME_JANK_LAYOUT);
            }
        };
        updateColor();
        setContentView(mColorView);
    }

    private void setupFrame() {
        updateColor();
        if (isFrameFlagSet(FRAME_JANK_LAYOUT)) {
            mColorView.requestLayout();
        }
        if (isFrameFlagSet(FRAME_JANK_DAVEY_JR)) {
            spinSleep(150);
        }
        if (isFrameFlagSet(FRAME_JANK_DAVEY)) {
            spinSleep(700);
        }
    }

    private void updateColor() {
        mColorView.setBackgroundColor(COLORS[mColorIndex]);
        // allow COLORs to be length == 1 or have duplicates without breaking the test
        mColorView.invalidate();
        mColorIndex = (mColorIndex + 1) % COLORS.length;
    }

    private void jankIf(int flagIsSet) {
        if (isFrameFlagSet(flagIsSet)) {
            jank();
        }
    }

    private boolean isFrameFlagSet(int flag) {
        return mFramesToDraw != null && (mFramesToDraw[mFrameIndex] & flag) != 0;
    }

    private void jank() {
        spinSleep(20);
    }

    private void spinSleep(int durationMs) {
        long until = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < until) {}
    }

    private void scheduleDraw() {
        mChoreographer.postFrameCallback((long timestamp) -> {
            setupFrame();
            jankIf(FRAME_JANK_ANIMATION);
        });
        if (isFrameFlagSet(FRAME_JANK_MISS_VSYNC)) {
            spinSleep(32);
        }
    }

    private void onDrawFinished() {
        if (mFramesToDraw != null && mFrameIndex < mFramesToDraw.length - 1) {
            mFrameIndex++;
            scheduleDraw();
        } else if (mFramesFinishedFence != null) {
            mFramesFinishedFence.countDown();
            mFramesFinishedFence = null;
            mFramesToDraw = null;
        }
    }

    public void drawFrames(final int frameCount) throws InterruptedException, TimeoutException {
        drawFrames(new int[frameCount]);
    }

    public void waitForReady() throws InterruptedException, TimeoutException {
        if (!mReady.await(4, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
    }

    public void drawFrames(final int[] framesToDraw) throws InterruptedException, TimeoutException {
        if (!mReady.await(4, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        final CountDownLatch fence = new CountDownLatch(1);
        long timeoutDurationMs = 0;
        for (int frame : framesToDraw) {
            // 50ms base time + 20ms for every extra jank event
            timeoutDurationMs += 50 + (20 * Integer.bitCount(frame));
            if ((frame & FRAME_JANK_DAVEY_JR) != 0) {
                timeoutDurationMs += 150;
            }
            if ((frame & FRAME_JANK_DAVEY) != 0) {
                timeoutDurationMs += 700;
            }
        }
        runOnUiThread(() -> {
            mFramesToDraw = framesToDraw;
            mFrameIndex = 0;
            mFramesFinishedFence = fence;
            scheduleDraw();
        });
        if (!fence.await(timeoutDurationMs, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException("Drawing " + framesToDraw.length + " frames timed out after "
                    + timeoutDurationMs + "ms");
        }
    }

    public int getRenderedFramesCount() {
        return mRenderedFrames;
    }

    public int getDroppedReportsCount() {
        return mDroppedReportsCount;
    }

    @Override
    public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics,
            int dropCountSinceLastInvocation) {
        mDroppedReportsCount += dropCountSinceLastInvocation;
        mRenderedFrames++;
        onDrawFinished();
    }
}
