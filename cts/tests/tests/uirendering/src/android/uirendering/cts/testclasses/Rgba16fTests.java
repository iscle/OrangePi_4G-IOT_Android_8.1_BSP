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

package android.uirendering.cts.testclasses;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class Rgba16fTests extends ActivityTestBase {
    @Test
    public void testTransferFunctions() {
        createTest()
                .addCanvasClient("RGBA16F_TransferFunctions", (canvas, width, height) -> {
                    AssetManager assets = getActivity().getResources().getAssets();
                    try (InputStream in = assets.open("linear-rgba16f.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        canvas.scale(
                                width / (float) bitmap.getWidth(),
                                height / (float) bitmap.getHeight());
                        canvas.drawBitmap(bitmap, 0, 0, null);
                    } catch (IOException e) {
                        throw new RuntimeException("Test failed: ", e);
                    }
                }, true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { new Point(0, 0) },
                        new int[] { 0xffbbbbbb }
                ));
    }

    @Test
    public void testAlpha() {
        createTest()
                .addCanvasClient("RGBA16F_TransferFunctions", (canvas, width, height) -> {
                    AssetManager assets = getActivity().getResources().getAssets();
                    try (InputStream in = assets.open("linear-rgba16f.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        canvas.scale(
                                width / (float) bitmap.getWidth(),
                                height / (float) bitmap.getHeight());
                        Paint p = new Paint();
                        p.setAlpha(127);
                        canvas.drawColor(0xffff0000);
                        canvas.drawBitmap(bitmap, 0, 0, p);
                    } catch (IOException e) {
                        throw new RuntimeException("Test failed: ", e);
                    }
                }, true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { new Point(0, 0) },
                        new int[] { 0xffdd5d5d }
                ));
    }

    @Test
    public void testMasked() {
        createTest()
                .addCanvasClient("RGBA16F_Masked", (canvas, width, height) -> {
                    AssetManager assets = getActivity().getResources().getAssets();
                    try (InputStream in = assets.open("linear-rgba16f.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);

                        Bitmap res = BitmapFactory.decodeResource(getActivity().getResources(),
                                R.drawable.alpha_mask);
                        Bitmap mask = Bitmap.createBitmap(res.getWidth(), res.getHeight(),
                                Bitmap.Config.ALPHA_8);
                        Canvas c = new Canvas(mask);
                        c.drawBitmap(res, 0, 0, null);

                        Paint p = new Paint();
                        p.setShader(new BitmapShader(bitmap,
                                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

                        canvas.scale(
                                width / (float) bitmap.getWidth(),
                                height / (float) bitmap.getHeight());

                        canvas.drawBitmap(mask, 0, 0, p);
                    } catch (IOException e) {
                        throw new RuntimeException("Test failed: ", e);
                    }
                }, true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { new Point(0, 0), new Point(31, 31) },
                        new int[] { 0xffffffff, 0xffbbbbbb }
                ));
    }

    @Test
    public void testTransferFunctionsShader() {
        createTest()
                .addCanvasClient("RGBA16F_TransferFunctions_Shader", (canvas, width, height) -> {
                    AssetManager assets = getActivity().getResources().getAssets();
                    try (InputStream in = assets.open("linear-rgba16f.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        Paint p = new Paint();
                        p.setShader(new BitmapShader(bitmap,
                                Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                        canvas.drawRect(0.0f, 0.0f, width, height, p);
                    } catch (IOException e) {
                        throw new RuntimeException("Test failed: ", e);
                    }
                }, true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { new Point(0, 0) },
                        new int[] { 0xffbbbbbb }
                ));
    }

    @Test
    public void testMirroredTransferFunctions() {
        createTest()
                .addCanvasClient("RGBA16F_TransferFunctions_Mirror", (canvas, width, height) -> {
                    AssetManager assets = getActivity().getResources().getAssets();
                    // Pure blue in ProPhoto RGB will yield negative R and G values in scRGB,
                    // as well as a value > 1.0 for B
                    try (InputStream in = assets.open("prophoto-rgba16f.png")) {
                        Bitmap bitmap = BitmapFactory.decodeStream(in);
                        canvas.scale(
                                width / (float) bitmap.getWidth(),
                                height / (float) bitmap.getHeight());
                        canvas.drawBitmap(bitmap, 0, 0, null);
                    } catch (IOException e) {
                        throw new RuntimeException("Test failed: ", e);
                    }
                }, true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { new Point(0, 0) },
                        new int[] { 0xff0000ff }
                ));
    }
}
