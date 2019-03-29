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

package android.uirendering.cts.bitmapverifiers;

import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.util.Half;
import android.util.Log;

import java.nio.ByteBuffer;

public class SamplePointWideGamutVerifier extends WideGamutBitmapVerifier {
    private static final String TAG = "SamplePointWideGamut";

    private static final ColorSpace SCRGB = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);

    private final Point[] mPoints;
    private final Color[] mColors;
    private final float mEps;

    public SamplePointWideGamutVerifier(Point[] points, Color[] colors, float eps) {
        mPoints = points;
        mColors = colors;
        mEps = eps;
    }

    @Override
    public boolean verify(ByteBuffer bitmap, int offset, int stride, int width, int height) {
        boolean success = true;
        for (int i = 0; i < mPoints.length; i++) {
            Point p = mPoints[i];
            Color c = mColors[i];

            int index = p.y * stride + (p.x << 3);
            float r = Half.toFloat(bitmap.getShort(index));
            float g = Half.toFloat(bitmap.getShort(index + 2));
            float b = Half.toFloat(bitmap.getShort(index + 4));

            boolean localSuccess = true;
            if (!floatCompare(c.red(),   r, mEps)) localSuccess = false;
            if (!floatCompare(c.green(), g, mEps)) localSuccess = false;
            if (!floatCompare(c.blue(),  b, mEps)) localSuccess = false;

            if (!localSuccess) {
                success = false;
                Log.w(TAG, "Expected " + c.toString() + " at " + p.x + "x" + p.y
                        + ", got " + Color.valueOf(r, g, b, 1.0f, SCRGB).toString());
            }
        }
        return success;
    }

    private static boolean floatCompare(float a, float b, float eps) {
        return Float.compare(a, b) == 0 || Math.abs(a - b) <= eps;
    }
}
