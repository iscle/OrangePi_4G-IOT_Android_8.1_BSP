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
package android.uirendering.cts.bitmapverifiers;

import android.util.Log;

public class ColorCountVerifier extends BitmapVerifier {
    private int mColor;
    private int mCount;

    public ColorCountVerifier(int color, int count) {
        mColor = color;
        mCount = count;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (bitmap[indexFromXAndY(x, y, stride, offset)] == mColor) {
                    count++;
                }
            }
        }
        if (count != mCount) {
            Log.d("ColorCountVerifier", ("Color count mismatch " + count) + " != " + mCount);
        }
        return count == mCount;
    }

}
