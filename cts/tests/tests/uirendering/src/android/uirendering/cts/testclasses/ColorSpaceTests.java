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

import static android.graphics.Bitmap.Config.ARGB_8888;
import static android.graphics.Bitmap.Config.HARDWARE;
import static android.graphics.Bitmap.Config.RGB_565;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Shader;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ColorSpaceTests extends ActivityTestBase {
    private Bitmap mMask;

    @Before
    public void loadMask() {
        Bitmap res = BitmapFactory.decodeResource(getActivity().getResources(),
                android.uirendering.cts.R.drawable.alpha_mask);
        mMask = Bitmap.createBitmap(res.getWidth(), res.getHeight(), Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(mMask);
        c.drawBitmap(res, 0, 0, null);
    }

    @Test
    public void testDrawDisplayP3() {
        // Uses hardware transfer function
        Bitmap bitmap8888 = loadAsset("green-p3.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("green-p3.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_DisplayP3_8888",
                        (c, w, h) -> drawAsset(c, bitmap8888), true)
                .addCanvasClientWithoutUsingPicture(
                        (c, w, h) -> drawAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xff00ff00, 0xff00ff00, 0xff00ff00, 0xffffffff, 0xff7f7f00 }
                ));
    }

    @Test
    public void testDrawDisplayP3Config565() {
        // Uses hardware transfer function
        Bitmap bitmap = loadAsset("green-p3.png", RGB_565);
        createTest()
                .addCanvasClient("Draw_DisplayP3_565", (c, w, h) -> drawAsset(c, bitmap), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xff00ff00, 0xff00ff00, 0xff00ff00, 0xffffffff, 0xff7f7f00 }
                ));
    }

    @Test
    public void testDrawProPhotoRGB() {
        // Uses hardware limited shader transfer function
        Bitmap bitmap8888 = loadAsset("orange-prophotorgb.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("orange-prophotorgb.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_ProPhotoRGB_8888",
                        (c, w, h) -> drawAsset(c, bitmap8888), true)
                .addCanvasClientWithoutUsingPicture(
                        (c, w, h) -> drawAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xffff7f00, 0xffff7f00, 0xffff7f00, 0xffffffff, 0xffff3f00 }
                ));
    }

    @Test
    public void testDrawProPhotoRGBConfig565() {
        // Uses hardware limited shader transfer function
        Bitmap bitmap = loadAsset("orange-prophotorgb.png", RGB_565);
        createTest()
                .addCanvasClient("Draw_ProPhotoRGB_565",
                        (c, w, h) -> drawAsset(c, bitmap), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                point(0, 0), point(48, 0), point(32, 40), point(0, 40), point(0, 56)
                        },
                        new int[] { 0xffff7f00, 0xffff7f00, 0xffff7f00, 0xffffffff, 0xffff3f00 }
                ));
    }

    @Test
    public void testDrawTranslucentAdobeRGB() {
        // Uses hardware simplified gamma transfer function
        Bitmap bitmap8888 = loadAsset("red-adobergb.png", ARGB_8888);
        Bitmap bitmapHardware = loadAsset("red-adobergb.png", HARDWARE);
        createTest()
                .addCanvasClient("Draw_AdobeRGB_Translucent_8888",
                        (c, w, h) -> drawTranslucentAsset(c, bitmap8888), true)
                .addCanvasClientWithoutUsingPicture(
                        (c, w, h) -> drawTranslucentAsset(c, bitmapHardware), true)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] { point(0, 0) },
                        new int[] { 0xffed8080 }
                ));
    }

    private void drawAsset(@NonNull Canvas canvas, Bitmap bitmap) {
        // Render bitmap directly
        canvas.save();
        canvas.clipRect(0, 0, 32, 32);
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();

        // Render bitmap via shader
        Paint p = new Paint();
        p.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        canvas.drawRect(32.0f, 0.0f, 64.0f, 32.0f, p);

        // Render bitmap via shader using another bitmap as a mask
        canvas.save();
        canvas.clipRect(0, 32, 64, 48);
        canvas.drawBitmap(mMask, 0, 0, p);
        canvas.restore();

        // Render bitmap with alpha to test modulation
        p.setShader(null);
        p.setAlpha(127);
        canvas.save();
        canvas.clipRect(0, 48, 64, 64);
        canvas.drawColor(0xffff0000);
        canvas.drawBitmap(bitmap, 0, 0, p);
        canvas.restore();
    }

    @Nullable
    private Bitmap loadAsset(@NonNull String assetName, @NonNull Bitmap.Config config) {
        Bitmap bitmap;
        AssetManager assets = getActivity().getResources().getAssets();
        try (InputStream in = assets.open(assetName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = config;

            bitmap = BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            throw new RuntimeException("Test failed: ", e);
        }
        return bitmap;
    }

    private void drawTranslucentAsset(@NonNull Canvas canvas, Bitmap bitmap) {
        canvas.drawBitmap(bitmap, 0, 0, null);
    }

    @NonNull
    private static Point point(int x, int y) {
        return new Point(x, y);
    }
}
