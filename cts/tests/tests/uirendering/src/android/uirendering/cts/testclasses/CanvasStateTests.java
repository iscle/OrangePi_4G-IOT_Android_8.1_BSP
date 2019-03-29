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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.util.DisplayMetrics;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests of state query-able from canvas at draw time.
 *
 * Although these tests don't verify drawing content, they still make use of ActivityTestBase's
 * capability to test the hardware accelerated Canvas in the way that it is used by Views.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class CanvasStateTests extends ActivityTestBase {
    @Test
    public void testClipRectReturnValues() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.save();
                    boolean isNonEmpty = canvas.clipRect(0, 0, 20, 20);
                    assertTrue("clip state should be non empty", isNonEmpty);

                    isNonEmpty = canvas.clipRect(0, 40, 20, 60);
                    assertFalse("clip state should be empty", isNonEmpty);
                    canvas.restore();
                })
                .runWithoutVerification();
    }

    @Test
    public void testClipPathReturnValues() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.save();
                    Path rectPath = new Path();
                    rectPath.addRect(0, 0, 20, 20, Path.Direction.CW);

                    boolean isNonEmpty = canvas.clipPath(rectPath);
                    assertTrue("clip state should be non empty", isNonEmpty);

                    rectPath.offset(0, 40);
                    isNonEmpty = canvas.clipPath(rectPath);
                    assertFalse("clip state should be empty", isNonEmpty);
                    canvas.restore();
                })
                .runWithoutVerification();
    }
    @Test
    public void testQuickReject() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.save();
                    canvas.clipRect(0, 0, 20, 20);

                    // not rejected!
                    assertFalse(canvas.quickReject(0, 0, 20, 20, Canvas.EdgeType.BW));

                    // rejected!
                    assertTrue(canvas.quickReject(0, 40, 20, 60, Canvas.EdgeType.BW));
                    canvas.restore();
                })
                .runWithoutVerification();
    }

    private void testFailureOnBitmapDraw(Bitmap bitmap) {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    boolean sawException = false;
                    try {
                        canvas.drawBitmap(bitmap, 0, 0, null);
                    } catch (RuntimeException e) {
                        sawException = true;
                    }
                    assertTrue(sawException);
                })
                .runWithoutVerification();
    }

    @Test
    public void testFailureOnDrawRecycledBitmap() {
        Bitmap recycledBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        recycledBitmap.recycle();
        testFailureOnBitmapDraw(recycledBitmap);
    }

    @Test
    public void testFailureOnNonPremultipliedBitmap() {
        Bitmap nonPremultipliedBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        nonPremultipliedBitmap.setPremultiplied(false);
        nonPremultipliedBitmap.setHasAlpha(true);
        testFailureOnBitmapDraw(nonPremultipliedBitmap);
    }

    @Test
    public void testDrawScreenWideBitmap() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    DisplayMetrics displayMetrics =
                            getActivity().getResources().getDisplayMetrics();
                    assertTrue(displayMetrics.widthPixels <= canvas.getMaximumBitmapWidth());
                    assertTrue(displayMetrics.heightPixels <= canvas.getMaximumBitmapHeight());
                    Bitmap bitmap = Bitmap.createBitmap(displayMetrics.widthPixels,
                            displayMetrics.heightPixels, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.RED);
                    canvas.drawBitmap(bitmap, 0, 0, null);
                })
                .runWithVerifier(new ColorVerifier(Color.RED, 0));
    }

    @Test
    public void testDrawLargeBitmap() {
        // verify that HW and SW pipelines can both draw screen-and-a-half sized bitmap
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    DisplayMetrics displayMetrics =
                            getActivity().getResources().getDisplayMetrics();

                    int bWidth = displayMetrics.widthPixels * 3 / 2;
                    int bHeight = displayMetrics.heightPixels * 3 / 2;
                    bWidth = Math.min(bWidth, canvas.getMaximumBitmapWidth());
                    bHeight = Math.min(bHeight, canvas.getMaximumBitmapHeight());
                    Bitmap bitmap = Bitmap.createBitmap(bWidth, bHeight, Bitmap.Config.ARGB_8888);
                    bitmap.eraseColor(Color.RED);
                    canvas.drawBitmap(bitmap, 0, 0, null);
                })
                .runWithVerifier(new ColorVerifier(Color.RED, 0));
    }
}
