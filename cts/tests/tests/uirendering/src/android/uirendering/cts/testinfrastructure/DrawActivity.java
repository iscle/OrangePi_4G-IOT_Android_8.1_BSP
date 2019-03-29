/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.uirendering.cts.testinfrastructure;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.Nullable;
import android.uirendering.cts.R;
import android.util.Log;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.view.Window;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A generic activity that uses a view specified by the user.
 */
public class DrawActivity extends Activity {
    static final String EXTRA_WIDE_COLOR_GAMUT = "DrawActivity.WIDE_COLOR_GAMUT";

    private final static long TIME_OUT_MS = 10000;
    private final Point mLock = new Point();

    private Handler mHandler;
    private View mView;
    private View mViewWrapper;
    private boolean mOnTv;

    public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        if (getIntent().getBooleanExtra(EXTRA_WIDE_COLOR_GAMUT, false)) {
            getWindow().setColorMode(ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT);
        }
        mHandler = new RenderSpecHandler();
        int uiMode = getResources().getConfiguration().uiMode;
        mOnTv = (uiMode & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION;

        // log frame metrics
        HandlerThread handlerThread = new HandlerThread("FrameMetrics");
        handlerThread.start();
        getWindow().addOnFrameMetricsAvailableListener(
                new Window.OnFrameMetricsAvailableListener() {
                    int mRtFrameCount = 0;
                    @Override
                    public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics,
                            int dropCountSinceLastInvocation) {
                        Log.d("UiRendering", "Window frame count " + mRtFrameCount
                                + ", frame drops " + dropCountSinceLastInvocation);
                        mRtFrameCount++;
                    }
                }, new Handler(handlerThread.getLooper()));

        // log draw metrics
        View view = new View(this);
        setContentView(view);
        view.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            int mFrameCount;
            @Override
            public void onDraw() {
                Log.d("UiRendering", "View tree frame count " + mFrameCount);
                mFrameCount++;
            }
        });
    }

    public boolean getOnTv() {
        return mOnTv;
    }

    public Point enqueueRenderSpecAndWait(int layoutId, CanvasClient canvasClient,
            @Nullable ViewInitializer viewInitializer, boolean useHardware, boolean usePicture) {
        ((RenderSpecHandler) mHandler).setViewInitializer(viewInitializer);
        int arg2 = (useHardware ? View.LAYER_TYPE_NONE : View.LAYER_TYPE_SOFTWARE);
        Point point = new Point();
        synchronized (mLock) {
            if (canvasClient != null) {
                mHandler.obtainMessage(RenderSpecHandler.CANVAS_MSG, usePicture ? 1 : 0,
                        arg2, canvasClient).sendToTarget();
            } else {
                mHandler.obtainMessage(RenderSpecHandler.LAYOUT_MSG, layoutId, arg2).sendToTarget();
            }

            try {
                mLock.wait(TIME_OUT_MS);
                point.set(mLock.x, mLock.y);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return point;
    }

    public void reset() {
        CountDownLatch fence = new CountDownLatch(1);
        mHandler.obtainMessage(RenderSpecHandler.RESET_MSG, fence).sendToTarget();
        try {
            if (!fence.await(10, TimeUnit.SECONDS)) {
                fail("Timeout exception");
            }
        } catch (InterruptedException ex) {
            fail(ex.getMessage());
        }
    }

    private ViewInitializer mViewInitializer;

    private void notifyOnDrawCompleted() {
        DrawCounterListener onDrawListener = new DrawCounterListener();
        mView.getViewTreeObserver().addOnDrawListener(onDrawListener);
        mView.invalidate();
    }

    private class RenderSpecHandler extends Handler {
        public static final int RESET_MSG = 0;
        public static final int LAYOUT_MSG = 1;
        public static final int CANVAS_MSG = 2;


        public void setViewInitializer(ViewInitializer viewInitializer) {
            mViewInitializer = viewInitializer;
        }

        public void handleMessage(Message message) {
            Log.d("UiRendering", "message of type " + message.what);
            if (message.what == RESET_MSG) {
                ((ViewGroup)findViewById(android.R.id.content)).removeAllViews();
                ((CountDownLatch)message.obj).countDown();
                return;
            }
            setContentView(R.layout.test_container);
            ViewStub stub = (ViewStub) findViewById(R.id.test_content_stub);
            mViewWrapper = findViewById(R.id.test_content_wrapper);
            switch (message.what) {
                case LAYOUT_MSG: {
                    stub.setLayoutResource(message.arg1);
                    mView = stub.inflate();
                } break;

                case CANVAS_MSG: {
                    stub.setLayoutResource(R.layout.test_content_canvasclientview);
                    mView = stub.inflate();
                    ((CanvasClientView) mView).setCanvasClient((CanvasClient) (message.obj));
                    if (message.arg1 != 0) {
                        ((CanvasClientView) mView).setUsePicture(true);
                    }
                } break;
            }

            if (mView == null) {
                throw new IllegalStateException("failed to inflate test content");
            }

            if (mViewInitializer != null) {
                mViewInitializer.initializeView(mView);
            }

            // set layer on wrapper parent of view, so view initializer
            // can control layer type of View under test.
            mViewWrapper.setLayerType(message.arg2, null);

            notifyOnDrawCompleted();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mViewInitializer != null) {
            mViewInitializer.teardownView();
        }
    }

    @Override
    public void finish() {
        // Ignore
    }

    /** Call this when all the tests that use this activity have completed.
     * This will then clean up any internal state and finish the activity. */
    public void allTestsFinished() {
        super.finish();
    }

    private class DrawCounterListener implements ViewTreeObserver.OnDrawListener {
        private final int[] mLocationInWindow = new int[2];
        private static final int DEBUG_REQUIRE_EXTRA_FRAMES = 1;
        private int mDrawCount = 0;

        @Override
        public void onDraw() {
            if (++mDrawCount <= DEBUG_REQUIRE_EXTRA_FRAMES) {
                mView.postInvalidate();
                return;
            }
            mView.post(() -> {
                Log.d("UiRendering", "notifying capture");
                mView.getViewTreeObserver().removeOnDrawListener(this);
                synchronized (mLock) {
                    mViewWrapper.getLocationInWindow(mLocationInWindow);
                    mLock.set(mLocationInWindow[0], mLocationInWindow[1]);
                    mLock.notify();
                }
            });
        }
    }
}
