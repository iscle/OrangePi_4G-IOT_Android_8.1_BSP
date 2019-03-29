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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources.Theme;
import android.graphics.BitmapFactory;
import android.graphics.Rect;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;
import android.util.LayoutDirection;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BitmapDrawableTest extends ActivityTestBase {

    // The target context.
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    private static final int[] DENSITY_VALUES = new int[] {
            160, 80, 320
    };

    private static final int[] DENSITY_IMAGES = new int[] {
            R.drawable.bitmap_density,
            R.drawable.bitmap_shader_density,
            R.drawable.bitmap_shader_am_density,
    };

    @Test
    public void testPreloadDensity() throws IOException {
        final Resources res = mContext.getResources();
        final int densityDpi = res.getConfiguration().densityDpi;
        try {
            for (int i = 0; i < DENSITY_IMAGES.length; i++) {
                verifyPreloadDensityInner(res, DENSITY_IMAGES[i], DENSITY_VALUES);
            }
        } finally {
            setResourcesDensity(res, densityDpi);
        }
    }

    private void verifyPreloadDensityInner(Resources res, int sourceResId, int[] densities)
            throws IOException {
        final Rect tempPadding = new Rect();

        // Capture initial state at preload density.
        final int preloadDensityDpi = densities[0];
        setResourcesDensity(res, preloadDensityDpi);

        final BitmapDrawable preloadedDrawable = (BitmapDrawable) res.getDrawable(sourceResId);
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        final int origWidth = preloadedDrawable.getIntrinsicWidth();
        final int origHeight = preloadedDrawable.getIntrinsicHeight();
        assertFalse(preloadedDrawable.getPadding(tempPadding));

        runTest(preloadedDrawable);

        for (int i = 1; i < densities.length; i++) {
            final int scaledDensityDpi = densities[i];
            final float scale = scaledDensityDpi / (float) preloadDensityDpi;
            setResourcesDensity(res, scaledDensityDpi);

            final BitmapDrawable scaledDrawable =
                    (BitmapDrawable) preloadedConstantState.newDrawable(res);
            scaledDrawable.setLayoutDirection(LayoutDirection.RTL);

            // Sizes are rounded.
            assertEquals(Math.round(origWidth * scale), scaledDrawable.getIntrinsicWidth());
            assertEquals(Math.round(origHeight * scale), scaledDrawable.getIntrinsicHeight());

            // Bitmaps have no padding.
            assertFalse(scaledDrawable.getPadding(tempPadding));

            runTest(scaledDrawable);

            // Ensure theme density is applied correctly. Unlike most
            // drawables, we don't have any loss of accuracy because density
            // changes are re-computed from the source every time.
            setResourcesDensity(res, preloadDensityDpi);

            final Theme t = res.newTheme();
            scaledDrawable.applyTheme(t);
            assertEquals(origWidth, scaledDrawable.getIntrinsicWidth());
            assertEquals(origHeight, scaledDrawable.getIntrinsicHeight());
            assertFalse(scaledDrawable.getPadding(tempPadding));
        }
    }

    private void runTest(Drawable dr) {
        final Rect drBounds = new Rect(0, 0, dr.getIntrinsicWidth(), dr.getIntrinsicHeight());
        CanvasClient canvasClient = (canvas, width, height) -> {
            assertTrue(width > drBounds.width());
            assertTrue(height > drBounds.height());
            dr.setBounds(drBounds);
            dr.draw(canvas);
        };
        createTest()
                .addCanvasClient(canvasClient)
                .runWithVerifier(new RectVerifier(Color.WHITE, Color.BLUE, drBounds));
    }

    private static void setResourcesDensity(Resources res, int densityDpi) {
        final Configuration config = new Configuration();
        config.setTo(res.getConfiguration());
        config.densityDpi = densityDpi;
        res.updateConfiguration(config, null);
    }
}
