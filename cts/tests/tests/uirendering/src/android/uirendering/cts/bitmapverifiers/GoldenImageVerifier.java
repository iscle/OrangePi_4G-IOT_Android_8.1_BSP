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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.differencevisualizers.PassFailVisualizer;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

public class GoldenImageVerifier extends BitmapVerifier {
    private BitmapComparer mBitmapComparer;
    private int[] mGoldenBitmapArray;

    public GoldenImageVerifier(Bitmap goldenBitmap, BitmapComparer bitmapComparer) {
        mGoldenBitmapArray = new int[ActivityTestBase.TEST_WIDTH * ActivityTestBase.TEST_HEIGHT];
        goldenBitmap.getPixels(mGoldenBitmapArray, 0, ActivityTestBase.TEST_WIDTH, 0, 0,
                ActivityTestBase.TEST_WIDTH, ActivityTestBase.TEST_HEIGHT);
        mBitmapComparer = bitmapComparer;
    }

    public GoldenImageVerifier(Context context, int goldenResId, BitmapComparer bitmapComparer) {
        this(BitmapFactory.decodeResource(context.getResources(), goldenResId), bitmapComparer);
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        boolean success = mBitmapComparer.verifySame(mGoldenBitmapArray, bitmap, offset, stride,
                width, height);
        if (!success) {
            mDifferenceBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            int[] differences = new PassFailVisualizer().getDifferences(mGoldenBitmapArray, bitmap);
            mDifferenceBitmap.setPixels(differences, 0, width, 0, 0, width, height);
        }
        return success;
    }
}
