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

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ShaderTests extends ActivityTestBase {
    @Test
    public void testSinglePixelBitmapShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            Bitmap shaderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                            shaderBitmap.eraseColor(Color.BLUE);
                            mPaint.setShader(new BitmapShader(shaderBitmap,
                                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                        }
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testSinglePixelComposeShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();

                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            // BLUE as SRC for Compose
                            Bitmap shaderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                            shaderBitmap.eraseColor(Color.BLUE);
                            BitmapShader bitmapShader = new BitmapShader(shaderBitmap,
                                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

                            // Fully opaque gradient mask (via DST_IN).
                            // In color array, only alpha channel will matter.
                            RadialGradient gradientShader = new RadialGradient(
                                    10, 10, 10,
                                    new int[] { Color.RED, Color.GREEN, Color.BLUE }, null,
                                    Shader.TileMode.CLAMP);

                            mPaint.setShader(new ComposeShader(
                                    bitmapShader, gradientShader, PorterDuff.Mode.DST_IN));
                        }
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testComplexShaderUsage() {
        /*
         * This test not only builds a very complex drawing operation, but also tests an
         * implementation detail of HWUI, using the largest number of texture sample sources
         * possible - 4.
         *
         * 1) Bitmap passed to canvas.drawBitmap
         * 2) gradient color lookup
         * 3) gradient dither lookup
         * 4) Bitmap in BitmapShader
          */
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    Bitmap mBitmap;

                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mBitmap == null) {
                            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
                            // Primary content mask
                            Canvas bitmapCanvas = new Canvas(mBitmap);
                            final float radius = width / 2.0f;
                            bitmapCanvas.drawCircle(width / 2, height / 2, radius, mPaint);

                            // Bitmap shader mask, partially overlapping content
                            Bitmap shaderBitmap = Bitmap.createBitmap(
                                    width, height, Bitmap.Config.ALPHA_8);
                            bitmapCanvas = new Canvas(shaderBitmap);
                            bitmapCanvas.drawCircle(width / 2, 0, radius, mPaint);
                            bitmapCanvas.drawCircle(width / 2, height, radius, mPaint);
                            BitmapShader bitmapShader = new BitmapShader(shaderBitmap,
                                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

                            // Gradient fill
                            RadialGradient gradientShader = new RadialGradient(
                                    width / 2, height / 2, radius,
                                    new int[] { Color.RED, Color.BLUE, Color.GREEN },
                                    null, Shader.TileMode.CLAMP);

                            mPaint.setShader(new ComposeShader(gradientShader, bitmapShader,
                                    PorterDuff.Mode.DST_IN));
                        }
                        canvas.drawBitmap(mBitmap, 0, 0, mPaint);
                    }
                })
                // expect extremely similar rendering results between SW and HW, since there's no AA
                .runWithComparer(new MSSIMComparer(0.98f));
    }

    @Test
    public void testRepeatAlphaGradientShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            mPaint.setShader(new LinearGradient(0, 0, width / 2.0f, height,
                                    Color.TRANSPARENT, Color.WHITE, Shader.TileMode.REPEAT));
                        }
                        canvas.drawColor(Color.WHITE);
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    @Test
    public void testClampAlphaGradientShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            mPaint.setShader(new LinearGradient(0, 0, width / 2.0f, height,
                                    Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP));
                        }
                        canvas.drawColor(Color.WHITE);
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }
}
