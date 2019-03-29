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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.res.Resources;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.test.filters.MediumTest;
import android.uirendering.cts.bitmapcomparers.ExactComparer;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.util.DisplayMetrics;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HardwareBitmapTests extends ActivityTestBase {

    private Resources mRes;

    private static final BitmapFactory.Options HARDWARE_OPTIONS = createHardwareOptions();

    private static BitmapFactory.Options createHardwareOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.HARDWARE;
        return options;
    }

    @Before
    public void setup() {
        mRes = getActivity().getResources();
    }

    @Test
    public void testDecodeResource() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                    HARDWARE_OPTIONS);
            canvas.drawBitmap(hardwareBitmap, 0, 0, new Paint());
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_robot, new MSSIMComparer(0.95)));
    }

    @Test
    public void testBitmapRegionDecode() throws IOException {
        InputStream inputStream = mRes.openRawResource(R.drawable.robot);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(inputStream, false);
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = decoder.decodeRegion(new Rect(10, 15, 34, 39),
                    HARDWARE_OPTIONS);
            canvas.drawBitmap(hardwareBitmap, 0, 0, new Paint());
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_headless_robot, new MSSIMComparer(0.95)));
    }

    @Test
    public void testBitmapConfigFromRGB565() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.RGB_565, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromARGB8888() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.ARGB_8888, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromA8() {
        Bitmap b = Bitmap.createBitmap(32, 32, Bitmap.Config.ALPHA_8);
        // we do not support conversion from A8
        assertNull(b.copy(Bitmap.Config.HARDWARE, false));
    }

    @Test
    public void testBitmapConfigFromHardwareToHardware() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.HARDWARE, Bitmap.Config.HARDWARE);
    }

    @Test
    public void testBitmapConfigFromHardwareToARGB8888() {
        testBitmapCopy(R.drawable.robot, Bitmap.Config.HARDWARE, Bitmap.Config.ARGB_8888);
    }

    @Test
    public void testSetDensity() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot);
            bitmap.setDensity(DisplayMetrics.DENSITY_LOW);
            canvas.drawBitmap(bitmap, 0, 0, null);
        }, true).addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                    HARDWARE_OPTIONS);
            hardwareBitmap.setDensity(DisplayMetrics.DENSITY_LOW);
            canvas.drawBitmap(hardwareBitmap, 0, 0, null);
        }, true).runWithComparer(new ExactComparer());
    }

    @Test
    public void testNinePatch() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            InputStream is = mRes.openRawResource(R.drawable.blue_padded_square);
            NinePatchDrawable ninePatch = (NinePatchDrawable) Drawable.createFromResourceStream(
                    mRes, null, is, null, HARDWARE_OPTIONS);
            ninePatch.setBounds(0, 0, width, height);
            ninePatch.draw(canvas);
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_hardwaretest_ninepatch, new MSSIMComparer(0.95)));
    }

    @Test
    public void testCreateIdentityBitmap() {
        Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                HARDWARE_OPTIONS);
        Bitmap newBitmap = Bitmap.createBitmap(hardwareBitmap);
        assertEquals(hardwareBitmap, newBitmap);
    }

    @Test
    public void testCreateScaledBitmap() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                    HARDWARE_OPTIONS);
            Bitmap scaled = Bitmap.createScaledBitmap(hardwareBitmap, 24, 24, false);
            assertEquals(Bitmap.Config.HARDWARE, scaled.getConfig());
            canvas.drawBitmap(scaled, 0, 0, null);
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_hardwaretest_create_scaled, new MSSIMComparer(0.9)));
    }

    @Test
    public void testCreateSubsetBitmap() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                    HARDWARE_OPTIONS);
            Matrix matrix = new Matrix();
            matrix.setRotate(90);
            Bitmap cropped = Bitmap.createBitmap(hardwareBitmap, 7, 7, 30, 30);
            assertEquals(Bitmap.Config.HARDWARE, cropped.getConfig());
            canvas.drawBitmap(cropped, 0, 0, null);
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_hardwaretest_create_subset, new MSSIMComparer(0.9)));
    }

    @Test
    public void testCreateTransformedBitmap() {
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                    HARDWARE_OPTIONS);
            Matrix matrix = new Matrix();
            matrix.setRotate(90);
            Bitmap transformed = Bitmap.createBitmap(hardwareBitmap, 7, 7, 30, 30, matrix, false);
            assertEquals(Bitmap.Config.HARDWARE, transformed.getConfig());
            canvas.drawBitmap(transformed, 0, 0, null);
        }, true).runWithVerifier(new GoldenImageVerifier(getActivity(),
                R.drawable.golden_hardwaretest_create_transformed, new MSSIMComparer(0.9)));
    }


    @Test
    public void testCompressHardware() {
        Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                HARDWARE_OPTIONS);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        assertTrue(hardwareBitmap.compress(Bitmap.CompressFormat.PNG, 50, stream));
        Bitmap decoded = BitmapFactory.decodeStream(
                new ByteArrayInputStream(stream.toByteArray()));
        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            canvas.drawBitmap(hardwareBitmap, 0, 0, null);
        }, true).addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            canvas.drawBitmap(decoded, 0, 0, null);
        }, true).runWithComparer(new MSSIMComparer(0.99));

    }

    @Test
    public void testHardwareExtractAlpha() {
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.argb(127, 250, 0, 0));
        bitmap.setPixel(25, 25, Color.BLUE);

        Bitmap hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false);
        Bitmap alphaBitmap = hwBitmap.extractAlpha();
        assertEquals(Bitmap.Config.ALPHA_8, alphaBitmap.getConfig());
        assertTrue(Color.alpha(alphaBitmap.getPixel(25, 25)) >= 254);
        assertEquals(127, Color.alpha(alphaBitmap.getPixel(40, 40)));
    }

    private void testBitmapCopy(int id, Bitmap.Config from, Bitmap.Config to) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = from;
        Bitmap bitmap = BitmapFactory.decodeResource(getActivity().getResources(), id, options);
        assertEquals(from, bitmap.getConfig());

        createTest().addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            canvas.drawBitmap(bitmap, 0, 0, null);
        }, true).addCanvasClientWithoutUsingPicture((canvas, width, height) -> {
            canvas.drawColor(Color.CYAN);
            Bitmap copy = bitmap.copy(to, false);
            assertNotNull(copy);
            assertEquals(to, copy.getConfig());
            canvas.drawBitmap(copy, 0, 0, null);
        }, true).runWithComparer(new MSSIMComparer(0.99));
    }
}
