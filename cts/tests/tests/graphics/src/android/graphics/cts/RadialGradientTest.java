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

package android.graphics.cts;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader.TileMode;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RadialGradientTest {

    @Test
    public void testZeroScaleMatrix() {
        RadialGradient gradient = new RadialGradient(0.5f, 0.5f, 1,
                Color.RED, Color.BLUE, TileMode.CLAMP);

        Matrix m = new Matrix();
        m.setScale(0, 0);
        gradient.setLocalMatrix(m);

        Bitmap bitmap = Bitmap.createBitmap(3, 1, Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setShader(gradient);
        canvas.drawPaint(paint);

        ColorUtils.verifyColor(Color.BLACK, bitmap.getPixel(0, 0), 1);
        ColorUtils.verifyColor(Color.BLACK, bitmap.getPixel(1, 0), 1);
        ColorUtils.verifyColor(Color.BLACK, bitmap.getPixel(2, 0), 1);
    }
}
