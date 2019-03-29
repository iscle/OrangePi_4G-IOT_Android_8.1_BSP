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
import android.graphics.Point;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.util.CompareUtils;
import android.util.Log;

import java.util.Arrays;

/**
 * This class will test specific points, and ensure that they match up perfectly with the input colors
 */
public class SamplePointVerifier extends BitmapVerifier {
    private static final String TAG = "SamplePoint";
    private static final int DEFAULT_TOLERANCE = 20;
    private final Point[] mTestPoints;
    private final int[] mExpectedColors;
    private final int mTolerance;

    public SamplePointVerifier(Point[] testPoints, int[] expectedColors) {
        this(testPoints, expectedColors, DEFAULT_TOLERANCE);
    }

    public SamplePointVerifier(Point[] testPoints, int[] expectedColors, int tolerance) {
        mTestPoints = testPoints;
        mExpectedColors = expectedColors;
        mTolerance = tolerance;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        boolean success = true;
        int[] differenceMap = new int[bitmap.length];
        Arrays.fill(differenceMap, PASS_COLOR);
        for (int i = 0 ; i < mTestPoints.length ; i++) {
            int x = mTestPoints[i].x;
            int y = mTestPoints[i].y;
            int index = indexFromXAndY(x, y, stride, offset);
            if (!verifyPixel(bitmap[index], mExpectedColors[i])) {
                Log.d(TAG, "Expected : " + Integer.toHexString(mExpectedColors[i]) +
                        " at position x = " + x + " y = " + y + " , tested color : " +
                        Integer.toHexString(bitmap[index]));
                differenceMap[index] = FAIL_COLOR;
                success = false;
            } else {
                differenceMap[index] = PASS_COLOR;
            }
        }
        if (!success) {
            mDifferenceBitmap = Bitmap.createBitmap(ActivityTestBase.TEST_WIDTH,
                    ActivityTestBase.TEST_HEIGHT, Bitmap.Config.ARGB_8888);
            mDifferenceBitmap.setPixels(differenceMap, offset, stride, 0, 0,
                    ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        }
        return success;
    }

    protected boolean verifyPixel(int color, int expectedColor) {
        return CompareUtils.verifyPixelWithThreshold(color, expectedColor, mTolerance);
    }
}
