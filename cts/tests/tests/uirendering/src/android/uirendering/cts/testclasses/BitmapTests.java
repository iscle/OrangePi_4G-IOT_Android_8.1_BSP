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

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.ColorCountVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.Window;
import android.widget.FrameLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BitmapTests extends ActivityTestBase {
    class BitmapView extends View {
        private Bitmap mBitmap;
        private int mColor;

        public BitmapView(Context context) {
            super(context);
            mBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            setColor(Color.BLUE);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, new Rect(0, 0, 1, 1), canvas.getClipBounds(), null);
        }

        public void setColor(int color) {
            mColor = color;
            mBitmap.setPixel(0, 0, color);
        }

        public int getColor() {
            return mColor;
        }
    }

    /*
     * The following test verifies that bitmap changes during render thread animation won't
     * be visible: we changed a bitmap from blue to red during circular reveal (an RT animation),
     * and changed it back to blue before the end of the animation; we should never see any
     * red pixel.
     */
    @Test
    public void testChangeDuringRtAnimation() {
        class RtOnlyFrameCounter implements Window.OnFrameMetricsAvailableListener {
            private int count = 0;

            @Override
            public void onFrameMetricsAvailable(Window window, FrameMetrics frameMetrics,
                    int dropCountSinceLastInvocation) {
                if (frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION) == 0
                        && frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) == 0
                        && frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) == 0) {
                    count++;
                };
            }

            public boolean isLargeEnough() {
                return count >= 5;
            }
        }

        RtOnlyFrameCounter counter = new RtOnlyFrameCounter();

        ViewInitializer initializer = new ViewInitializer() {
            Animator mAnimator;

            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);

                final BitmapView child = new BitmapView(view.getContext());
                child.setLayoutParams(new FrameLayout.LayoutParams(50, 50));
                root.addView(child);

                mAnimator = ViewAnimationUtils.createCircularReveal(child, 0, 0, 0, 90);
                mAnimator.setDuration(3000);
                mAnimator.start();

                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        child.setColor(Color.RED);
                        try {
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            // do nothing
                        }
                        child.setColor(Color.BLUE);
                    }
                }, 1000);
                getActivity().getWindow().addOnFrameMetricsAvailableListener(counter, handler);
            }

            @Override
            public void teardownView() {
                mAnimator.cancel();
                getActivity().getWindow().removeOnFrameMetricsAvailableListener(counter);
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .runWithAnimationVerifier(new ColorCountVerifier(Color.RED, 0));

        Assert.assertTrue(counter.isLargeEnough());
    }

    /*
     * The following test verifies that bitmap changes during UI thread animation are
     * visible: we keep changing a bitmap's color between red and blue in sync with the
     * background, and we should only see pure blue or red.
    */
    @Test
    public void testChangeDuringUiAnimation() {
        class BlueOrRedVerifier extends BitmapVerifier {
            @Override
            public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
                MSSIMComparer comparer = new MSSIMComparer(0.99);
                int[] red  = new int[offset + height * stride];
                Arrays.fill(red, Color.RED);
                int[] blue  = new int[offset + height * stride];
                Arrays.fill(blue, Color.BLUE);
                boolean isRed = comparer.verifySame(red, bitmap, offset, stride, width, height);
                boolean isBlue = comparer.verifySame(blue, bitmap, offset, stride, width, height);
                return isRed || isBlue;
            }
        }

        ViewInitializer initializer = new ViewInitializer() {
            ValueAnimator mAnimator;

            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                root.setBackgroundColor(Color.BLUE);

                final BitmapView child = new BitmapView(view.getContext());

                // The child size is strictly less than the test canvas size,
                // and we are moving it up and down inside the canvas.
                child.setLayoutParams(new FrameLayout.LayoutParams(ActivityTestBase.TEST_WIDTH / 2,
                        ActivityTestBase.TEST_HEIGHT / 2));
                root.addView(child);
                child.setColor(Color.BLUE);

                mAnimator = ValueAnimator.ofInt(0, ActivityTestBase.TEST_HEIGHT / 2);
                mAnimator.setRepeatMode(mAnimator.REVERSE);
                mAnimator.setRepeatCount(mAnimator.INFINITE);
                mAnimator.setDuration(400);
                mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int v = (Integer) mAnimator.getAnimatedValue();
                        child.setTranslationY(v);
                        if (child.getColor() == Color.BLUE) {
                            root.setBackgroundColor(Color.RED);
                            child.setColor(Color.RED);
                        } else {
                            root.setBackgroundColor(Color.BLUE);
                            child.setColor(Color.BLUE);
                        }
                    }
                });
                mAnimator.start();
            }

            @Override
            public void teardownView() {
                mAnimator.cancel();
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .runWithAnimationVerifier(new BlueOrRedVerifier());
    }
}
