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

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver.OnDrawListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

public class PixelCopyWideGamutViewProducerActivity extends Activity implements OnDrawListener {
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

        mContent = new WideGamutBitmapView(this);
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
        waitForFirstDrawCompleted(3, TimeUnit.SECONDS);
        return mCurrentOrientation != 0;
    }

    void offsetForContent(Rect inOut) {
        inOut.offset(mContentBounds.left, mContentBounds.top);
    }

    private static final class WideGamutBitmapView extends View {
        private final Bitmap mBitmap;

        WideGamutBitmapView(Context context) {
            super(context);
            // We use an asset to ensure aapt will not mess with the data
            AssetManager assets = context.getResources().getAssets();
            try (InputStream in = assets.open("prophoto.png")) {
                mBitmap = BitmapFactory.decodeStream(in);
            } catch (IOException e) {
                throw new RuntimeException("Test failed: ", e);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0.0f, 0.0f, null);
        }
    }
}
