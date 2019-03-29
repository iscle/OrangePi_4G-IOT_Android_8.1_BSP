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
package android.uirendering.cts.testclasses;

import com.android.compatibility.common.util.SynchronousPixelCopy;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SurfaceViewTests extends ActivityTestBase {

    static final CanvasCallback sGreenCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.GREEN));
    static final CanvasCallback sWhiteCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.WHITE));
    static final CanvasCallback sRedCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.RED));

    private static class CanvasCallback implements SurfaceHolder.Callback {
        final CanvasClient mCanvasClient;

        public CanvasCallback(CanvasClient canvasClient) {
            mCanvasClient = canvasClient;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            mCanvasClient.draw(canvas, width, height);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }
    }

    static ObjectAnimator createInfiniteAnimator(Object target, String prop,
            float start, float end) {
        ObjectAnimator a = ObjectAnimator.ofFloat(target, prop, start, end);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setDuration(200);
        a.setInterpolator(new LinearInterpolator());
        a.start();
        return a;
    }

    @Test
    public void testMovingWhiteSurfaceView() {
        // A moving SurfaceViews with white content against a white background should be invisible
        ViewInitializer initializer = new ViewInitializer() {
            ObjectAnimator mAnimator;
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                mAnimator = createInfiniteAnimator(root, "translationY", 0, 50);

                SurfaceView surfaceViewA = new SurfaceView(view.getContext());
                surfaceViewA.getHolder().addCallback(sWhiteCanvasCallback);
                root.addView(surfaceViewA, new FrameLayout.LayoutParams(
                        90, 40, Gravity.START | Gravity.TOP));
            }
            @Override
            public void teardownView() {
                mAnimator.cancel();
            }
        };
        Screenshotter screenshotter = testOffset -> {
            Bitmap source = getInstrumentation().getUiAutomation().takeScreenshot();
            return Bitmap.createBitmap(source, testOffset.x, testOffset.y, TEST_WIDTH, TEST_HEIGHT);
        };
        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .withScreenshotter(screenshotter)
                .runWithAnimationVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    private static class SurfaceViewHelper implements ViewInitializer, Screenshotter, SurfaceHolder.Callback {
        private final CanvasClient mCanvasClient;
        private final CountDownLatch mFence = new CountDownLatch(1);
        private SurfaceView mSurfaceView;

        public SurfaceViewHelper(CanvasClient canvasClient) {
            mCanvasClient = canvasClient;
        }

        @Override
        public Bitmap takeScreenshot(Point point /* ignored */) {
            SynchronousPixelCopy copy = new SynchronousPixelCopy();
            Bitmap dest = Bitmap.createBitmap(
                    TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
            Rect srcRect = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
            int copyResult = copy.request(mSurfaceView, srcRect, dest);
            Assert.assertEquals(PixelCopy.SUCCESS, copyResult);
            return dest;
        }

        @Override
        public void initializeView(View view) {
            FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
            mSurfaceView = new SurfaceView(view.getContext());
            mSurfaceView.getHolder().addCallback(this);
            root.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // TODO: Remove the post() which is a temporary workaround for b/32484713
            mSurfaceView.post(() -> {
                Canvas canvas = holder.lockHardwareCanvas();
                mCanvasClient.draw(canvas, width, height);
                holder.unlockCanvasAndPost(canvas);
                mFence.countDown();
            });
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public CountDownLatch getFence() {
            return mFence;
        }
    }

    @Test
    public void testSurfaceHolderHardwareCanvas() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            Assert.assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.GREEN);
        });
        createTest()
                .addLayout(R.layout.frame_layout, helper, true, helper.getFence())
                .withScreenshotter(helper)
                .runWithVerifier(new ColorVerifier(Color.GREEN, 0 /* zero tolerance */));
    }
}
