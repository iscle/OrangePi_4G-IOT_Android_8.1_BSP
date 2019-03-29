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

package android.graphics.drawable.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.VectorDrawable;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VectorDrawableTest {
    private static final String LOGTAG = "VectorDrawableTest";

    // Separate the test assets into different groups such that we could isolate the issue faster.
    // Some new APIs or bug fixes only exist in particular os version, such that we name the tests
    // and associated assets with OS code name L, M, N etc...
    private static final int[] BASIC_ICON_RES_IDS = new int[]{
            R.drawable.vector_icon_create,
            R.drawable.vector_icon_delete,
            R.drawable.vector_icon_heart,
            R.drawable.vector_icon_schedule,
            R.drawable.vector_icon_settings,
            R.drawable.vector_icon_random_path_1,
            R.drawable.vector_icon_random_path_2,
            R.drawable.vector_icon_repeated_cq,
            R.drawable.vector_icon_repeated_st,
            R.drawable.vector_icon_repeated_a_1,
            R.drawable.vector_icon_repeated_a_2,
            R.drawable.vector_icon_clip_path_1,
    };

    private static final int[] BASIC_GOLDEN_IMAGES = new int[] {
            R.drawable.vector_icon_create_golden,
            R.drawable.vector_icon_delete_golden,
            R.drawable.vector_icon_heart_golden,
            R.drawable.vector_icon_schedule_golden,
            R.drawable.vector_icon_settings_golden,
            R.drawable.vector_icon_random_path_1_golden,
            R.drawable.vector_icon_random_path_2_golden,
            R.drawable.vector_icon_repeated_cq_golden,
            R.drawable.vector_icon_repeated_st_golden,
            R.drawable.vector_icon_repeated_a_1_golden,
            R.drawable.vector_icon_repeated_a_2_golden,
            R.drawable.vector_icon_clip_path_1_golden,
    };

    private static final int[] L_M_ICON_RES_IDS = new int[] {
            R.drawable.vector_icon_transformation_1,
            R.drawable.vector_icon_transformation_2,
            R.drawable.vector_icon_transformation_3,
            R.drawable.vector_icon_transformation_4,
            R.drawable.vector_icon_transformation_5,
            R.drawable.vector_icon_transformation_6,
            R.drawable.vector_icon_render_order_1,
            R.drawable.vector_icon_render_order_2,
            R.drawable.vector_icon_stroke_1,
            R.drawable.vector_icon_stroke_2,
            R.drawable.vector_icon_stroke_3,
            R.drawable.vector_icon_scale_1,
            R.drawable.vector_icon_scale_2,
            R.drawable.vector_icon_scale_3,
            R.drawable.vector_icon_group_clip,
    };

    private static final int[] L_M_GOLDEN_IMAGES = new int[] {
            R.drawable.vector_icon_transformation_1_golden,
            R.drawable.vector_icon_transformation_2_golden,
            R.drawable.vector_icon_transformation_3_golden,
            R.drawable.vector_icon_transformation_4_golden,
            R.drawable.vector_icon_transformation_5_golden,
            R.drawable.vector_icon_transformation_6_golden,
            R.drawable.vector_icon_render_order_1_golden,
            R.drawable.vector_icon_render_order_2_golden,
            R.drawable.vector_icon_stroke_1_golden,
            R.drawable.vector_icon_stroke_2_golden,
            R.drawable.vector_icon_stroke_3_golden,
            R.drawable.vector_icon_scale_1_golden,
            R.drawable.vector_icon_scale_2_golden,
            R.drawable.vector_icon_scale_3_golden,
            R.drawable.vector_icon_group_clip_golden,
    };

    private static final int[] N_ICON_RES_IDS = new int[] {
            R.drawable.vector_icon_implicit_lineto,
            R.drawable.vector_icon_arcto,
            R.drawable.vector_icon_filltype_nonzero,
            R.drawable.vector_icon_filltype_evenodd,
    };

    private static final int[] N_GOLDEN_IMAGES = new int[] {
            R.drawable.vector_icon_implicit_lineto_golden,
            R.drawable.vector_icon_arcto_golden,
            R.drawable.vector_icon_filltype_nonzero_golden,
            R.drawable.vector_icon_filltype_evenodd_golden,
    };

    private static final int[] GRADIENT_ICON_RES_IDS = new int[] {
            R.drawable.vector_icon_gradient_1,
            R.drawable.vector_icon_gradient_2,
            R.drawable.vector_icon_gradient_3,
            R.drawable.vector_icon_gradient_1_clamp,
            R.drawable.vector_icon_gradient_2_repeat,
            R.drawable.vector_icon_gradient_3_mirror,
    };

    private static final int[] GRADIENT_GOLDEN_IMAGES = new int[] {
            R.drawable.vector_icon_gradient_1_golden,
            R.drawable.vector_icon_gradient_2_golden,
            R.drawable.vector_icon_gradient_3_golden,
            R.drawable.vector_icon_gradient_1_clamp_golden,
            R.drawable.vector_icon_gradient_2_repeat_golden,
            R.drawable.vector_icon_gradient_3_mirror_golden,
    };

    private static final int[] STATEFUL_RES_IDS = new int[] {
            // All these icons are using the same color state list, make sure it works for either
            // the same drawable ID or different ID but same content.
            R.drawable.vector_icon_state_list,
            R.drawable.vector_icon_state_list,
            R.drawable.vector_icon_state_list_2,
    };

    private static final int[][] STATEFUL_GOLDEN_IMAGES = new int[][] {
            {
                    R.drawable.vector_icon_state_list_golden,
                    R.drawable.vector_icon_state_list_golden,
                    R.drawable.vector_icon_state_list_2_golden
            },
            {
                    R.drawable.vector_icon_state_list_pressed_golden,
                    R.drawable.vector_icon_state_list_pressed_golden,
                    R.drawable.vector_icon_state_list_2_pressed_golden
            }
    };

    private static final int[][] STATEFUL_STATE_SETS = new int[][] {
            {},
            { android.R.attr.state_pressed }
    };

    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;

    private static final boolean DBG_DUMP_PNG = false;

    private Resources mResources;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Context mContext;

    @Before
    public void setup() {
        final int width = IMAGE_WIDTH;
        final int height = IMAGE_HEIGHT;

        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        mContext = InstrumentationRegistry.getTargetContext();
        mResources = mContext.getResources();
    }

    @Test
    public void testBasicVectorDrawables() throws XmlPullParserException, IOException {
        verifyVectorDrawables(BASIC_ICON_RES_IDS, BASIC_GOLDEN_IMAGES, null);
    }

    @Test
    public void testLMVectorDrawables() throws XmlPullParserException, IOException {
        verifyVectorDrawables(L_M_ICON_RES_IDS, L_M_GOLDEN_IMAGES, null);
    }

    @Test
    public void testNVectorDrawables() throws XmlPullParserException, IOException {
        verifyVectorDrawables(N_ICON_RES_IDS, N_GOLDEN_IMAGES, null);
    }

    @Test
    public void testVectorDrawableGradient() throws XmlPullParserException, IOException {
        verifyVectorDrawables(GRADIENT_ICON_RES_IDS, GRADIENT_GOLDEN_IMAGES, null);
    }

    @Test
    public void testColorStateList() throws XmlPullParserException, IOException {
        for (int i = 0; i < STATEFUL_STATE_SETS.length; i++) {
            verifyVectorDrawables(
                    STATEFUL_RES_IDS, STATEFUL_GOLDEN_IMAGES[i], STATEFUL_STATE_SETS[i]);
        }
    }

    private void verifyVectorDrawables(int[] resIds, int[] goldenImages, int[] stateSet)
            throws XmlPullParserException, IOException {
        for (int i = 0; i < resIds.length; i++) {
            VectorDrawable vectorDrawable = new VectorDrawable();
            vectorDrawable.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

            // Setup VectorDrawable from xml file and draw into the bitmap.
            XmlPullParser parser = mResources.getXml(resIds[i]);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            int type;
            while ((type=parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }

            if (type != XmlPullParser.START_TAG) {
                throw new XmlPullParserException("No start tag found");
            }

            Theme theme = mResources.newTheme();
            theme.applyStyle(R.style.Theme_ThemedDrawableTest, true);
            vectorDrawable.inflate(mResources, parser, attrs, theme);

            if (stateSet != null) {
                vectorDrawable.setState(stateSet);
            }

            mBitmap.eraseColor(0);
            vectorDrawable.draw(mCanvas);

            if (DBG_DUMP_PNG) {
                String stateSetTitle = getTitleForStateSet(stateSet);
                DrawableTestUtils.saveAutoNamedVectorDrawableIntoPNG(mContext, mBitmap, resIds[i],
                        stateSetTitle);
            } else {
                // Start to compare
                Bitmap golden = BitmapFactory.decodeResource(mResources, goldenImages[i]);
                DrawableTestUtils.compareImages(mResources.getString(resIds[i]), mBitmap, golden,
                        DrawableTestUtils.PIXEL_ERROR_THRESHOLD,
                        DrawableTestUtils.PIXEL_ERROR_COUNT_THRESHOLD,
                        DrawableTestUtils.PIXEL_ERROR_TOLERANCE);

            }
        }
    }

    /**
     * Generates an underline-delimited list of states in a given state set.
     * <p>
     * For example, the array {@code {R.attr.state_pressed}} would return
     * {@code "pressed"}.
     *
     * @param stateSet a state set
     * @return a string representing the state set, or {@code null} if the state set is empty or
     * {@code null}
     */
    private @Nullable String getTitleForStateSet(int[] stateSet) {
        if (stateSet == null || stateSet.length == 0) {
            return null;
        }

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stateSet.length; i++) {
            final String state = mResources.getResourceName(stateSet[i]);
            final int stateIndex = state.indexOf("state_");
            if (stateIndex >= 0) {
                builder.append(state.substring(stateIndex + 6));
            } else {
                builder.append(stateSet[i]);
            }
        }

        return builder.toString();
    }

    @Test
    public void testGetChangingConfigurations() {
        VectorDrawable vectorDrawable = new VectorDrawable();
        ConstantState constantState = vectorDrawable.getConstantState();

        // default
        assertEquals(0, constantState.getChangingConfigurations());
        assertEquals(0, vectorDrawable.getChangingConfigurations());

        // change the drawable's configuration does not affect the state's configuration
        vectorDrawable.setChangingConfigurations(0xff);
        assertEquals(0xff, vectorDrawable.getChangingConfigurations());
        assertEquals(0, constantState.getChangingConfigurations());

        // the state's configuration get refreshed
        constantState = vectorDrawable.getConstantState();
        assertEquals(0xff,  constantState.getChangingConfigurations());

        // set a new configuration to drawable
        vectorDrawable.setChangingConfigurations(0xff00);
        assertEquals(0xff,  constantState.getChangingConfigurations());
        assertEquals(0xffff,  vectorDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetConstantState() {
        VectorDrawable vectorDrawable = new VectorDrawable();
        ConstantState constantState = vectorDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(0, constantState.getChangingConfigurations());

        vectorDrawable.setChangingConfigurations(1);
        constantState = vectorDrawable.getConstantState();
        assertNotNull(constantState);
        assertEquals(1, constantState.getChangingConfigurations());
    }

    @Test
    public void testMutate() {
        // d1 and d2 will be mutated, while d3 will not.
        VectorDrawable d1 = (VectorDrawable) mResources.getDrawable(R.drawable.vector_icon_create);
        VectorDrawable d2 = (VectorDrawable) mResources.getDrawable(R.drawable.vector_icon_create);
        VectorDrawable d3 = (VectorDrawable) mResources.getDrawable(R.drawable.vector_icon_create);
        int restoreAlpha = d1.getAlpha();

        try {
            // verify bad behavior - modify before mutate pollutes other drawables
            d1.setAlpha(0x80);
            assertEquals(0x80, d1.getAlpha());
            assertEquals(0x80, d2.getAlpha());
            assertEquals(0x80, d3.getAlpha());

            d1.mutate();
            d1.setAlpha(0x40);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(0x80, d2.getAlpha());
            assertEquals(0x80, d3.getAlpha());

            d2.setAlpha(0x00);
            d2.mutate();
            // Test that after mutating, the alpha value is copied over.
            assertEquals(0x00, d2.getAlpha());

            d2.setAlpha(0x20);
            assertEquals(0x40, d1.getAlpha());
            assertEquals(0x20, d2.getAlpha());
            assertEquals(0x00, d3.getAlpha());
        } finally {
            mResources.getDrawable(R.drawable.vector_icon_create).setAlpha(restoreAlpha);
        }
    }

    @Test
    public void testColorFilter() {
        PorterDuffColorFilter filter = new PorterDuffColorFilter(Color.RED, Mode.SRC_IN);
        VectorDrawable vectorDrawable = new VectorDrawable();
        vectorDrawable.setColorFilter(filter);

        assertEquals(filter, vectorDrawable.getColorFilter());
    }

    @Test
    public void testGetOpacity () throws XmlPullParserException, IOException {
        VectorDrawable vectorDrawable = new VectorDrawable();

        assertEquals("Default alpha should be 255", 255, vectorDrawable.getAlpha());
        assertEquals("Default opacity should be TRANSLUCENT", PixelFormat.TRANSLUCENT,
                vectorDrawable.getOpacity());

        vectorDrawable.setAlpha(0);
        assertEquals("Alpha should be 0 now", 0, vectorDrawable.getAlpha());
        assertEquals("Opacity should be TRANSPARENT now", PixelFormat.TRANSPARENT,
                vectorDrawable.getOpacity());
    }

    @Test
    public void testPreloadDensity() throws XmlPullParserException, IOException {
        final int densityDpi = mResources.getConfiguration().densityDpi;
        try {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
            verifyPreloadDensityInner(mResources, densityDpi);
        } finally {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
        }
    }

    @Test
    public void testPreloadDensity_tvdpi() throws XmlPullParserException, IOException {
        final int densityDpi = mResources.getConfiguration().densityDpi;
        try {
            DrawableTestUtils.setResourcesDensity(mResources, 213);
            verifyPreloadDensityInner(mResources, 213);
        } finally {
            DrawableTestUtils.setResourcesDensity(mResources, densityDpi);
        }
    }

    private void verifyPreloadDensityInner(Resources res, int densityDpi)
            throws XmlPullParserException, IOException {
        // Capture initial state at default density.
        final XmlResourceParser parser = DrawableTestUtils.getResourceParser(
                res, R.drawable.vector_density);
        final VectorDrawable preloadedDrawable = new VectorDrawable();
        preloadedDrawable.inflate(mResources, parser, Xml.asAttributeSet(parser));
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        final int origWidth = preloadedDrawable.getIntrinsicWidth();

        // Set density to half of original. Unlike offsets, which are
        // truncated, dimensions are rounded to the nearest pixel.
        DrawableTestUtils.setResourcesDensity(res, densityDpi / 2);
        final VectorDrawable halfDrawable =
                (VectorDrawable) preloadedConstantState.newDrawable(res);
        // NOTE: densityDpi may not be an even number, so account for *actual* scaling in asserts
        final float approxHalf = (float)(densityDpi / 2) / densityDpi;
        assertEquals(Math.round(origWidth * approxHalf), halfDrawable.getIntrinsicWidth());

        // Set density to double original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi * 2);
        final VectorDrawable doubleDrawable =
                (VectorDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(origWidth * 2, doubleDrawable.getIntrinsicWidth());

        // Restore original density.
        DrawableTestUtils.setResourcesDensity(res, densityDpi);
        final VectorDrawable origDrawable =
                (VectorDrawable) preloadedConstantState.newDrawable();
        assertEquals(origWidth, origDrawable.getIntrinsicWidth());

        // Ensure theme density is applied correctly.
        final Theme t = res.newTheme();
        halfDrawable.applyTheme(t);
        assertEquals(origWidth, halfDrawable.getIntrinsicWidth());
        doubleDrawable.applyTheme(t);
        assertEquals(origWidth, doubleDrawable.getIntrinsicWidth());
    }
}
