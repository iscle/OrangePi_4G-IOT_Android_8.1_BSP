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

package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertEquals;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region.Op;
import android.support.annotation.ColorInt;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorCountVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LayerTests extends ActivityTestBase {
    @Test
    public void testLayerPaintAlpha() {
        // red channel full strength, other channels 75% strength
        // (since 25% alpha red subtracts from them)
        @ColorInt
        final int expectedColor = Color.rgb(255, 191, 191);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    // reduce alpha by 50%
                    Paint paint = new Paint();
                    paint.setAlpha(128);
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);

                    // reduce alpha by another 50% (ensuring two alphas combine correctly)
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintSimpleAlphaWithHardware() {
        @ColorInt
        final int expectedColor = Color.rgb(255, 128, 128);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    // reduce alpha, so that overdraw will result in a different color
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintSimpleAlphaWithSoftware() {
        @ColorInt
        final int expectedColor = Color.rgb(255, 128, 128);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                    // reduce alpha, so that overdraw will result in a different color
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintColorFilter() {
        // Red, fully desaturated. Note that it's not 255/3 in each channel.
        // See ColorMatrix#setSaturation()
        @ColorInt
        final int expectedColor = Color.rgb(54, 54, 54);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    Paint paint = new Paint();
                    ColorMatrix desatMatrix = new ColorMatrix();
                    desatMatrix.setSaturation(0.0f);
                    paint.setColorFilter(new ColorMatrixColorFilter(desatMatrix));
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintBlend() {
        // Red, drawn underneath opaque white, so output should be white.
        // TODO: consider doing more interesting blending test here
        @ColorInt
        final int expectedColor = Color.WHITE;
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    Paint paint = new Paint();
                    /* Note that when drawing in SW, we're blending within an otherwise empty
                     * SW layer, as opposed to in the frame buffer (which has a white
                     * background).
                     *
                     * For this reason we use just use DST, which just throws out the SRC
                     * content, regardless of the DST alpha channel.
                     */
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST));
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @LargeTest
    @Test
    public void testLayerClear() {
        ViewInitializer initializer = new ViewInitializer() {
            ObjectAnimator mAnimator;
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                root.setAlpha(0.5f);

                View child = new View(view.getContext());
                child.setBackgroundColor(Color.BLUE);
                child.setTranslationX(10);
                child.setTranslationY(10);
                child.setLayoutParams(
                        new FrameLayout.LayoutParams(50, 50));
                child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                root.addView(child);

                mAnimator = ObjectAnimator.ofInt(child, "translationY", 0, 20);
                mAnimator.setRepeatMode(ValueAnimator.REVERSE);
                mAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mAnimator.setDuration(200);
                mAnimator.start();
            }
            @Override
            public void teardownView() {
                mAnimator.cancel();
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .runWithAnimationVerifier(new ColorCountVerifier(Color.WHITE, 90 * 90 - 50 * 50));
    }

    @Test
    public void testAlphaLayerChild() {
        ViewInitializer initializer = new ViewInitializer() {
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                root.setAlpha(0.5f);

                View child = new View(view.getContext());
                child.setBackgroundColor(Color.BLUE);
                child.setTranslationX(10);
                child.setTranslationY(10);
                child.setLayoutParams(
                        new FrameLayout.LayoutParams(50, 50));
                child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                root.addView(child);
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer)
                .runWithVerifier(new RectVerifier(Color.WHITE, 0xff8080ff,
                        new Rect(10, 10, 60, 60)));
    }

    @Test
    public void testLayerInitialSizeZero() {
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                    // disable clipChildren, to ensure children aren't rejected by bounds
                    root.setClipChildren(false);
                    for (int i = 0; i < 2; i++) {
                        View child = new View(view.getContext());
                        child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        // add rendering content, so View isn't skipped at render time
                        child.setBackgroundColor(Color.RED);

                        // add one with width=0, one with height=0
                        root.addView(child, new FrameLayout.LayoutParams(
                                i == 0 ? 0 : 90,
                                i == 0 ? 90 : 0,
                                Gravity.TOP | Gravity.LEFT));
                    }
                }, true)
                .runWithVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    @Test
    public void testLayerResizeZero() {
        final CountDownLatch fence = new CountDownLatch(1);
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                    // disable clipChildren, to ensure child isn't rejected by bounds
                    root.setClipChildren(false);
                    for (int i = 0; i < 2; i++) {
                        View child = new View(view.getContext());
                        child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        // add rendering content, so View isn't skipped at render time
                        child.setBackgroundColor(Color.BLUE);
                        root.addView(child, new FrameLayout.LayoutParams(90, 90,
                                Gravity.TOP | Gravity.LEFT));
                    }

                    // post invalid dimensions a few frames in, so initial layer allocation succeeds
                    // NOTE: this must execute before capture, or verification will fail
                    root.getViewTreeObserver().addOnPreDrawListener(
                            new ViewTreeObserver.OnPreDrawListener() {
                        int mDrawCount = 0;
                        @Override
                        public boolean onPreDraw() {
                            if (mDrawCount++ == 5) {
                                root.getChildAt(0).getLayoutParams().width = 0;
                                root.getChildAt(0).requestLayout();
                                root.getChildAt(1).getLayoutParams().height = 0;
                                root.getChildAt(1).requestLayout();
                                root.getViewTreeObserver().removeOnPreDrawListener(this);
                                root.post(fence::countDown);
                            } else {
                                root.postInvalidate();
                            }
                            return true;
                        }
                    });
                }, true, fence)
                .runWithVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    @Test
    public void testSaveLayerClippedWithColorFilter() {
        // verify that renderer can draw nested clipped layers with chained color filters
        createTest()
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                Paint firstLayerPaint = new Paint();
                float[] blueToGreenMatrix = new float[20];
                blueToGreenMatrix[7] = blueToGreenMatrix[18] = 1.0f;
                ColorMatrixColorFilter blueToGreenFilter = new ColorMatrixColorFilter(blueToGreenMatrix);
                firstLayerPaint.setColorFilter(blueToGreenFilter);
                Paint secondLayerPaint = new Paint();
                float[] redToBlueMatrix = new float[20];
                redToBlueMatrix[10] = redToBlueMatrix[18] = 1.0f;
                ColorMatrixColorFilter redToBlueFilter = new ColorMatrixColorFilter(redToBlueMatrix);
                secondLayerPaint.setColorFilter(redToBlueFilter);
                // The color filters are applied starting first with the inner layer and then the
                // outer layer.
                canvas.saveLayer(40, 5, 80, 70, firstLayerPaint, Canvas.CLIP_TO_LAYER_SAVE_FLAG);
                canvas.saveLayer(5, 40, 70, 80, secondLayerPaint, Canvas.CLIP_TO_LAYER_SAVE_FLAG);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            })
            .runWithVerifier(new RectVerifier(Color.WHITE, Color.GREEN, new Rect(40, 40, 70, 70)));
    }

    // Note: This test will fail for Skia pipeline, but that is OK.
    // TODO: delete this test when Skia pipeline is default and modify next test
    // testSaveLayerUnclippedWithColorFilterSW to run for both HW and SW
    @Test
    public void testSaveLayerUnclippedWithColorFilterHW() {
        // verify that HW can draw nested unclipped layers with chained color filters
        createTest()
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                Paint firstLayerPaint = new Paint();
                float[] blueToGreenMatrix = new float[20];
                blueToGreenMatrix[7] = blueToGreenMatrix[18] = 1.0f;
                ColorMatrixColorFilter blueToGreenFilter =
                      new ColorMatrixColorFilter(blueToGreenMatrix);
                firstLayerPaint.setColorFilter(blueToGreenFilter);
                Paint secondLayerPaint = new Paint();
                float[] redToBlueMatrix = new float[20];
                redToBlueMatrix[10] = redToBlueMatrix[18] = 1.0f;
                ColorMatrixColorFilter redToBlueFilter =
                      new ColorMatrixColorFilter(redToBlueMatrix);
                secondLayerPaint.setColorFilter(redToBlueFilter);
                canvas.saveLayer(40, 5, 80, 70, firstLayerPaint, 0);
                canvas.saveLayer(5, 40, 70, 80, secondLayerPaint, 0);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            }, true)
            // HWUI pipeline does not support a color filter for unclipped save layer and draws
            // as if the filter is not set.
            .runWithVerifier(new RectVerifier(Color.WHITE, Color.RED, new Rect(10, 10, 70, 70)));
    }

    @Test
    public void testSaveLayerUnclippedWithColorFilterSW() {
        // verify that SW can draw nested unclipped layers with chained color filters
        createTest()
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                Paint firstLayerPaint = new Paint();
                float[] blueToGreenMatrix = new float[20];
                blueToGreenMatrix[7] = blueToGreenMatrix[18] = 1.0f;
                ColorMatrixColorFilter blueToGreenFilter =
                    new ColorMatrixColorFilter(blueToGreenMatrix);
                firstLayerPaint.setColorFilter(blueToGreenFilter);
                Paint secondLayerPaint = new Paint();
                float[] redToBlueMatrix = new float[20];
                redToBlueMatrix[10] = redToBlueMatrix[18] = 1.0f;
                ColorMatrixColorFilter redToBlueFilter =
                    new ColorMatrixColorFilter(redToBlueMatrix);
                secondLayerPaint.setColorFilter(redToBlueFilter);
                canvas.saveLayer(40, 5, 80, 70, firstLayerPaint, 0);
                canvas.saveLayer(5, 40, 70, 80, secondLayerPaint, 0);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            }, false)
            .runWithVerifier(new SamplePointVerifier(
                new Point[] {
                    // just outside of rect
                    new Point(9, 9), new Point(70, 10), new Point(10, 70), new Point(70, 70),
                    // red rect
                    new Point(10, 10), new Point(39, 39),
                    // black rect
                    new Point(40, 10), new Point(69, 39),
                    // blue rect
                    new Point(10, 40), new Point(39, 69),
                    // green rect
                    new Point(40, 40), new Point(69, 69),
                },
                new int[] {
                    Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
                    Color.RED, Color.RED,
                    Color.BLACK, Color.BLACK,
                    Color.BLUE, Color.BLUE,
                    Color.GREEN, Color.GREEN,
                }));
    }

    @Test
    public void testSaveLayerClippedWithAlpha() {
        // verify that renderer can draw nested clipped layers with different alpha
        createTest() // picture mode is disable due to bug:34871089
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                canvas.saveLayerAlpha(40, 5, 80, 70, 0x7f, Canvas.CLIP_TO_LAYER_SAVE_FLAG);
                canvas.saveLayerAlpha(5, 40, 70, 80, 0x3f, Canvas.CLIP_TO_LAYER_SAVE_FLAG);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            })
            .runWithVerifier(new RectVerifier(Color.WHITE, 0xffffE0E0, new Rect(40, 40, 70, 70)));
    }

    @Test
    public void testSaveLayerUnclippedWithAlpha() {
        // verify that renderer can draw nested unclipped layers with different alpha
        createTest() // picture mode is disable due to bug:34871089
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                canvas.saveLayerAlpha(40, 5, 80, 70, 0x7f, 0);
                canvas.saveLayerAlpha(5, 40, 70, 80, 0x3f, 0);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            })
            .runWithVerifier(new SamplePointVerifier(
                new Point[]{
                    // just outside of rect
                    new Point(9, 9), new Point(70, 10), new Point(10, 70), new Point(70, 70),
                    // red rect outside both layers
                    new Point(10, 10), new Point(39, 39),
                    // pink rect overlapping one of the layers
                    new Point(40, 10), new Point(69, 39),
                    // pink rect overlapping one of the layers
                    new Point(10, 40), new Point(39, 69),
                    // pink rect overlapping both layers
                    new Point(40, 40), new Point(69, 69),
                },
                new int[]{
                    Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE,
                    Color.RED, Color.RED,
                    0xffff8080, 0xffff8080,
                    0xffffC0C0, 0xffffC0C0,
                    0xffffE0E0, 0xffffE0E0,
                }));
    }

    @Test
    public void testSaveLayerUnclipped_restoreBehavior() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    //set identity matrix
                    Matrix identity = new Matrix();
                    canvas.setMatrix(identity);
                    final Paint p = new Paint();

                    canvas.saveLayer(0, 0, width, height, p, 0);

                    //change matrix and clip to something different
                    canvas.clipRect(0, 0, width >> 1, height >> 1, Op.INTERSECT);
                    Matrix scaledMatrix = new Matrix();
                    scaledMatrix.setScale(4, 5);
                    canvas.setMatrix(scaledMatrix);
                    assertEquals(scaledMatrix, canvas.getMatrix());

                    canvas.drawColor(Color.BLUE);
                    canvas.restore();

                    //check if identity matrix is restored
                    assertEquals(identity, canvas.getMatrix());

                    //should draw to the entire canvas, because clip has been removed
                    canvas.drawColor(Color.RED);
                })
                .runWithVerifier(new ColorVerifier(Color.RED));
    }

    @Test
    public void testSaveLayerClipped_restoreBehavior() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    //set identity matrix
                    Matrix identity = new Matrix();
                    canvas.setMatrix(identity);
                    final Paint p = new Paint();

                    canvas.saveLayer(0, 0, width, height, p, Canvas.CLIP_TO_LAYER_SAVE_FLAG);

                    //change matrix and clip to something different
                    canvas.clipRect(0, 0, width >> 1, height >> 1, Op.INTERSECT);
                    Matrix scaledMatrix = new Matrix();
                    scaledMatrix.setScale(4, 5);
                    canvas.setMatrix(scaledMatrix);
                    assertEquals(scaledMatrix, canvas.getMatrix());

                    canvas.drawColor(Color.BLUE);
                    canvas.restore();

                    //check if identity matrix is restored
                    assertEquals(identity, canvas.getMatrix());

                    //should draw to the entire canvas, because clip has been removed
                    canvas.drawColor(Color.RED);
                })
                .runWithVerifier(new ColorVerifier(Color.RED));
    }
}
