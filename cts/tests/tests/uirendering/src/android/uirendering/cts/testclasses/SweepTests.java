/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE2.0
*
* Unless required by applicable law or agreed to in riting, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package android.uirendering.cts.testclasses;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;
import android.uirendering.cts.testinfrastructure.DisplayModifier;
import android.uirendering.cts.testinfrastructure.ResourceModifier;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases of all combination of resource modifications.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class SweepTests extends ActivityTestBase {
    private final static DisplayModifier COLOR_FILTER_GRADIENT_MODIFIER = new DisplayModifier() {
        private final Rect mBounds = new Rect(30, 30, 150, 150);
        private final int[] mColors = new int[] {
                Color.RED, Color.GREEN, Color.BLUE
        };

        private final Bitmap mBitmap = createGradient();

        @Override
        public void modifyDrawing(Paint paint, Canvas canvas) {
            canvas.drawBitmap(mBitmap, 0, 0, paint);
        }

        private Bitmap createGradient() {
            LinearGradient gradient = new LinearGradient(15, 45, 75, 45, mColors, null,
                    Shader.TileMode.REPEAT);
            Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
            Paint p = new Paint();
            p.setShader(gradient);
            Canvas c = new Canvas(bitmap);
            c.drawRect(mBounds, p);
            return bitmap;
        }
    };

    public static final DisplayModifier mCircleDrawModifier = new DisplayModifier() {
        @Override
        public void modifyDrawing(Paint paint, Canvas canvas) {
            canvas.drawCircle(TEST_WIDTH / 2, TEST_HEIGHT / 2, TEST_HEIGHT / 2, paint);
        }
    };

    /**
     * 0.5 defines minimum similarity as 50%
     */
    private static final float HIGH_THRESHOLD = 0.5f;

    private static final BitmapComparer[] DEFAULT_MSSIM_COMPARER = new BitmapComparer[] {
            new MSSIMComparer(HIGH_THRESHOLD)
    };

    @Test
    public void testBasicDraws() {
        sweepModifiersForMask(DisplayModifier.Accessor.SHAPES_MASK, null, DEFAULT_MSSIM_COMPARER);
    }

    @Test
    public void testBasicShaders() {
        sweepModifiersForMask(DisplayModifier.Accessor.SHADER_MASK, mCircleDrawModifier,
                DEFAULT_MSSIM_COMPARER);
    }

    @Test
    public void testColorFilterUsingGradient() {
        sweepModifiersForMask(DisplayModifier.Accessor.COLOR_FILTER_MASK,
                COLOR_FILTER_GRADIENT_MODIFIER, DEFAULT_MSSIM_COMPARER);
    }

    protected void sweepModifiersForMask(int mask, final DisplayModifier drawOp,
                BitmapComparer[] bitmapComparers) {
        if ((mask & DisplayModifier.Accessor.ALL_OPTIONS_MASK) == 0) {
            throw new IllegalArgumentException("Attempt to test with a mask that is invalid");
        }
        // Get the accessor of all the different modifications possible
        final DisplayModifier.Accessor modifierAccessor = new DisplayModifier.Accessor(mask);
        // Initialize the resources that we will need to access
        ResourceModifier.init(getActivity().getResources());
        // For each modification combination, we will get the CanvasClient associated with it and
        // from there execute a normal canvas test with that.
        CanvasClient canvasClient = (canvas, width, height) -> {
            Paint paint = new Paint();
            modifierAccessor.modifyDrawing(canvas, paint);
            if (drawOp != null) {
                drawOp.modifyDrawing(paint, canvas);
            }
        };

        int index = 0;
        // Create the test cases with each combination
        do {
            int arrIndex = Math.min(index, bitmapComparers.length - 1);
            TestCaseBuilder builder = createTest();
            String debugString = modifierAccessor.getDebugString();
            if (debugString.contains("bitmapMesh")) {
                // picture mode is disabled due to bug:34871089
                builder.addCanvasClientWithoutUsingPicture(debugString, canvasClient);
            } else {
                builder.addCanvasClient(debugString, canvasClient);
            }
            builder.runWithComparer(bitmapComparers[arrIndex]);
            index++;
        } while (modifierAccessor.step());
    }
}
