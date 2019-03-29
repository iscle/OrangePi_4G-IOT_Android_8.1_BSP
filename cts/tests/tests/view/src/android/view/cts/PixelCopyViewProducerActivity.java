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

package android.view.cts;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PixelCopyViewProducerActivity extends Activity implements OnDrawListener {
    private static final int[] ORIENTATIONS = {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
    };
    private int mCurrentOrientation = 0;
    private View mContent;
    private Rect mContentBounds = new Rect();
    private CountDownLatch mFence = new CountDownLatch(3);
    private boolean mSupportsRotation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if the device supports both of portrait and landscape orientation screens.
        final PackageManager pm = getPackageManager();
        mSupportsRotation = pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_LANDSCAPE)
                    && pm.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT);
        if (mSupportsRotation) {
            setRequestedOrientation(ORIENTATIONS[mCurrentOrientation]);
        }

        mContent = new ColoredGrid(this);
        setContentView(mContent);
        mContent.getViewTreeObserver().addOnDrawListener(this);
    }

    @Override
    public void onDraw() {
        final int requestedOrientation = ORIENTATIONS[mCurrentOrientation];
        boolean screenPortrait =
                requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                || requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        boolean contentPortrait = mContent.getHeight() > mContent.getWidth();
        if (mSupportsRotation && (screenPortrait != contentPortrait)) {
            return;
        }
        mContent.post(() -> {
            Point offset = new Point();
            // We pass mContentBounds here just as a throwaway rect, we don't care about
            // the visible rect just the global offset.
            mContent.getGlobalVisibleRect(mContentBounds, offset);
            mContentBounds.set(offset.x, offset.y,
                    offset.x + mContent.getWidth(), offset.y + mContent.getHeight());
            mFence.countDown();
            if (mFence.getCount() > 0) {
                mContent.invalidate();
            }
        });
    }

    public void waitForFirstDrawCompleted(int timeout, TimeUnit unit) {
        try {
            if (!mFence.await(timeout, unit)) {
                fail("Timeout");
            }
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }
    }

    public boolean rotate() {
        if (!mSupportsRotation) {
            // Do not rotate the screen if it is not supported.
            return false;
        }
        mFence = new CountDownLatch(3);
        runOnUiThread(() -> {
            mCurrentOrientation = (mCurrentOrientation + 1) % ORIENTATIONS.length;
            setRequestedOrientation(ORIENTATIONS[mCurrentOrientation]);
        });
        waitForFirstDrawCompleted(10, TimeUnit.SECONDS);
        return mCurrentOrientation != 0;
    }

    // Convert a rect in normalized 0-100 dimensions to the bounds of the actual View.
    public void normalizedToSurface(Rect inOut) {
        float sx = mContentBounds.width() / 100.0f;
        float sy = mContentBounds.height() / 100.0f;
        inOut.left = (int) (inOut.left * sx);
        inOut.top = (int) (inOut.top * sy);
        inOut.right = (int) (inOut.right * sx + 0.5f);
        inOut.bottom = (int) (inOut.bottom * sy + 0.5f);
        inOut.offset(mContentBounds.left, mContentBounds.top);
    }

    private static final class ColoredGrid extends View {
        private Paint mPaint = new Paint();
        private Rect mRect = new Rect();

        ColoredGrid(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            canvas.drawColor(Color.YELLOW);

            mRect.set(1, 1, cx, cy);
            mPaint.setColor(Color.RED);
            canvas.drawRect(mRect, mPaint);

            mRect.set(cx, 1, getWidth() - 1, cy);
            mPaint.setColor(Color.GREEN);
            canvas.drawRect(mRect, mPaint);

            mRect.set(1, cy, cx, getHeight() - 1);
            mPaint.setColor(Color.BLUE);
            canvas.drawRect(mRect, mPaint);

            mRect.set(cx, cy, getWidth() - 1, getHeight() - 1);
            mPaint.setColor(Color.BLACK);
            canvas.drawRect(mRect, mPaint);
        }
    }
}
