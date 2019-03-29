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
package android.uirendering.cts.bitmapverifiers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.CompareUtils;
import android.util.Log;

/**
 * This class looks at every pixel in a given bitmap and verifies that it is correct.
 */
public abstract class PerPixelBitmapVerifier extends BitmapVerifier {
    private static final String TAG = "PerPixelBitmapVerifer";
    public static final int DEFAULT_THRESHOLD = 48;

    // total color difference tolerated without the pixel failing
    private int mColorTolerance;

    // portion of bitmap allowed to fail pixel check
    private float mSpatialTolerance;

    public PerPixelBitmapVerifier() {
        this(DEFAULT_THRESHOLD, 0);
    }

    public PerPixelBitmapVerifier(int colorTolerance) {
        this(colorTolerance, 0);
    }

    public PerPixelBitmapVerifier(int colorTolerance, float spatialTolerance) {
        mColorTolerance = colorTolerance;
        mSpatialTolerance = spatialTolerance;
    }

    @ColorInt
    protected int getExpectedColor(int x, int y) {
        return Color.WHITE;
    }

    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        int failures = 0;
        int[] differenceMap = new int[bitmap.length];
        for (int y = 0 ; y < height ; y++) {
            for (int x = 0 ; x < width ; x++) {
                int index = indexFromXAndY(x, y, stride, offset);
                if (!verifyPixel(x, y, bitmap[index])) {
                    if (failures < 50) {
                        Log.d(TAG, "Expected : " + Integer.toHexString(getExpectedColor(x, y))
                                + " received : " + Integer.toHexString(bitmap[index])
                                + " at position (" + x + "," + y + ")");
                    }
                    failures++;
                    differenceMap[index] = FAIL_COLOR;
                } else {
                    differenceMap[index] = PASS_COLOR;
                }
            }
        }
        int toleratedFailures = (int) (mSpatialTolerance * width * height);
        boolean success = failures <= toleratedFailures;
        Log.d(TAG, failures + " failures observed out of "
                + toleratedFailures + " tolerated failures");
        if (!success) {
            mDifferenceBitmap = Bitmap.createBitmap(ActivityTestBase.TEST_WIDTH,
                    ActivityTestBase.TEST_HEIGHT, Bitmap.Config.ARGB_8888);
            mDifferenceBitmap.setPixels(differenceMap, offset, stride, 0, 0,
                    ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        }
        return success;
    }


    protected boolean verifyPixel(int x, int y, int observedColor) {
        int expectedColor = getExpectedColor(x, y);
        return CompareUtils.verifyPixelWithThreshold(observedColor, expectedColor, mColorTolerance);
    }
}
