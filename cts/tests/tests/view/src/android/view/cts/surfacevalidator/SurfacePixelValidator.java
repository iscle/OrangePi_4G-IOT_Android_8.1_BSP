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
package android.view.cts.surfacevalidator;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;
import android.util.SparseArray;
import android.view.Surface;

public class SurfacePixelValidator {
    private static final String TAG = "SurfacePixelValidator";

    /**
     * Observed that first few frames have errors with SurfaceView placement, so we skip for now.
     * b/29603849 tracking that issue.
     */
    private static final int NUM_FIRST_FRAMES_SKIPPED = 8;

    // If no channel is greater than this value, pixel will be considered 'blackish'.
    private static final short PIXEL_CHANNEL_THRESHOLD = 4;

    private static final int MAX_CAPTURED_FAILURES = 5;

    private final int mWidth;
    private final int mHeight;

    private final HandlerThread mWorkerThread;
    private final Handler mWorkerHandler;

    private final PixelChecker mPixelChecker;

    private final RenderScript mRS;

    private final Allocation mInPixelsAllocation;
    private final ScriptC_PixelCounter mScript;


    private final Object mResultLock = new Object();
    private int mResultSuccessFrames;
    private int mResultFailureFrames;
    private SparseArray<Bitmap> mFirstFailures = new SparseArray<>(MAX_CAPTURED_FAILURES);

    private Runnable mConsumeRunnable = new Runnable() {
        int numSkipped = 0;
        @Override
        public void run() {
            Trace.beginSection("consume buffer");
            mInPixelsAllocation.ioReceive();
            Trace.endSection();

            Trace.beginSection("compare and sum");
            int blackishPixelCount = mScript.reduce_countBlackishPixels(mInPixelsAllocation).get();
            Trace.endSection();

            boolean success = mPixelChecker.checkPixels(blackishPixelCount, mWidth, mHeight);
            synchronized (mResultLock) {
                if (numSkipped < NUM_FIRST_FRAMES_SKIPPED) {
                    numSkipped++;
                    Log.d(TAG, "skipped frame nr " + numSkipped + ", success = " + success);
                } else {
                    if (success) {
                        mResultSuccessFrames++;
                    } else {
                        mResultFailureFrames++;
                        int totalFramesSeen = mResultSuccessFrames + mResultFailureFrames;
                        Log.d(TAG, "Failure (pixel count = " + blackishPixelCount
                                + ") occurred on frame " + totalFramesSeen);

                        if (mFirstFailures.size() < MAX_CAPTURED_FAILURES) {
                            Log.d(TAG, "Capturing bitmap #" + mFirstFailures.size());
                            // error, worth looking at...
                            Bitmap capture = Bitmap.createBitmap(mWidth, mHeight,
                                    Bitmap.Config.ARGB_8888);
                            mInPixelsAllocation.copyTo(capture);
                            mFirstFailures.put(totalFramesSeen, capture);
                        }
                    }
                }
            }
        }
    };

    public SurfacePixelValidator(Context context, Point size, PixelChecker pixelChecker) {
        mWidth = size.x;
        mHeight = size.y;

        mWorkerThread = new HandlerThread("SurfacePixelValidator");
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());

        mPixelChecker = pixelChecker;

        mRS = RenderScript.create(context);
        mScript = new ScriptC_PixelCounter(mRS);

        mInPixelsAllocation = createBufferQueueAllocation();
        mScript.set_THRESHOLD(PIXEL_CHANNEL_THRESHOLD);

        mInPixelsAllocation.setOnBufferAvailableListener(
                allocation -> mWorkerHandler.post(mConsumeRunnable));
    }

    public Surface getSurface() {
        return mInPixelsAllocation.getSurface();
    }

    private Allocation createBufferQueueAllocation() {
        return Allocation.createAllocations(mRS, Type.createXY(mRS,
                Element.RGBA_8888(mRS)
                /*Element.U32(mRS)*/, mWidth, mHeight),
                Allocation.USAGE_SCRIPT | Allocation.USAGE_IO_INPUT,
                1)[0];
    }

    /**
     * Shuts down processing pipeline, and returns current pass/fail counts.
     *
     * Wait for pipeline to flush before calling this method. If not, frames that are still in
     * flight may be lost.
     */
    public void finish(CapturedActivity.TestResult testResult) {
        synchronized (mResultLock) {
            // could in theory miss results still processing, but only if latency is extremely high.
            // Caller should only call this
            testResult.failFrames = mResultFailureFrames;
            testResult.passFrames = mResultSuccessFrames;

            for (int i = 0; i < mFirstFailures.size(); i++) {
                testResult.failures.put(mFirstFailures.keyAt(i), mFirstFailures.valueAt(i));
            }
        }
        mWorkerThread.quitSafely();
    }
}
