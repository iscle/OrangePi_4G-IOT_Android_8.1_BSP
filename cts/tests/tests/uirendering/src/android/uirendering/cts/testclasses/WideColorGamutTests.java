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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointWideGamutVerifier;
import android.uirendering.cts.testclasses.view.BitmapView;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.view.View;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WideColorGamutTests extends ActivityTestBase {
    private static final ColorSpace DISPLAY_P3 = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
    private static final ColorSpace SCRGB = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);

    private static final Point[] POINTS = {
            new Point(16, 16),
            new Point(48, 16),
            new Point(80, 16),
    };

    // The colors are defined as found in wide-gamut-test.png, which is Display P3
    // Since the UI toolkit renders in scRGB, we want to convert here to compare values
    // directly in the sample point verifier
    private static final Color[] COLORS = {
            Color.valueOf(0.937f, 0.000f, 0.000f, 1.0f, DISPLAY_P3).convert(SCRGB),
            Color.valueOf(1.000f, 0.000f, 0.000f, 1.0f, DISPLAY_P3).convert(SCRGB),
            Color.valueOf(0.918f, 0.200f, 0.137f, 1.0f, DISPLAY_P3).convert(SCRGB)
    };

    private Bitmap mBitmap;

    @Override
    protected boolean isWideColorGamut() {
        return true;
    }

    @Before
    public void loadBitmap() {
        try (InputStream in = getActivity().getAssets().open("wide-gamut-test.png")) {
            mBitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            Assert.fail("Could not load wide-gamut-test.png");
        }
    }

    @SuppressWarnings("SameParameterValue")
    private BitmapVerifier getVerifier(Point[] points, Color[] colors, float eps) {
        if (getActivity().getWindow().isWideColorGamut()) {
            return new SamplePointWideGamutVerifier(points, colors, eps);
        }
        return new SamplePointVerifier(points,
                Arrays.stream(colors).mapToInt(Color::toArgb).toArray(),
                (int) (eps * 255.0f + 0.5f));
    }

    @Test
    public void testDraw() {
        createTest()
                .addLayout(R.layout.wide_gamut_bitmap_layout, view -> {
                    BitmapView bv = (BitmapView) view;
                    bv.setBitmap(mBitmap);
                }, true)
                .runWithVerifier(getVerifier(POINTS, COLORS, 1e-2f));
    }

    @Test
    public void testSaveLayer() {
        createTest()
                .addLayout(R.layout.wide_gamut_bitmap_layout, view -> {
                    BitmapView bv = (BitmapView) view;
                    bv.setBitmap(mBitmap);
                    bv.setSaveLayer(true);
                }, true)
                .runWithVerifier(getVerifier(POINTS, COLORS, 1e-2f));
    }

    @Test
    public void testHardwareLayer() {
        createTest()
                .addLayout(R.layout.wide_gamut_bitmap_layout, view -> {
                    BitmapView bv = (BitmapView) view;
                    bv.setBitmap(mBitmap);
                    bv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }, true)
                .runWithVerifier(getVerifier(POINTS, COLORS, 1e-2f));
    }

    @Test
    public void testSaveLayerInHardwareLayer() {
        createTest()
                .addLayout(R.layout.wide_gamut_bitmap_layout, view -> {
                    BitmapView bv = (BitmapView) view;
                    bv.setBitmap(mBitmap);
                    bv.setSaveLayer(true);
                    bv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }, true)
                .runWithVerifier(getVerifier(POINTS, COLORS, 1e-2f));
    }
}
