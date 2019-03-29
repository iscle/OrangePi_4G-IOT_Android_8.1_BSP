/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ColorUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LightingColorFilterTest {
    private static final int TOLERANCE = 2;

    private void verifyColor(int expected, int actual) {
        ColorUtils.verifyColor(expected, actual, TOLERANCE);
    }

    @Test
    public void testLightingColorFilter() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();

        paint.setColor(Color.MAGENTA);
        paint.setColorFilter(new LightingColorFilter(Color.WHITE, Color.BLACK));
        canvas.drawPaint(paint);
        verifyColor(Color.MAGENTA, bitmap.getPixel(0, 0));

        paint.setColor(Color.MAGENTA);
        paint.setColorFilter(new LightingColorFilter(Color.CYAN, Color.BLACK));
        canvas.drawPaint(paint);
        verifyColor(Color.BLUE, bitmap.getPixel(0, 0));

        paint.setColor(Color.MAGENTA);
        paint.setColorFilter(new LightingColorFilter(Color.BLUE, Color.GREEN));
        canvas.drawPaint(paint);
        verifyColor(Color.CYAN, bitmap.getPixel(0, 0));

        // alpha is ignored
        bitmap.eraseColor(Color.TRANSPARENT);
        paint.setColor(Color.MAGENTA);
        paint.setColorFilter(new LightingColorFilter(Color.TRANSPARENT, Color.argb(0, 0, 0xFF, 0)));
        canvas.drawPaint(paint);
        verifyColor(Color.GREEN, bitmap.getPixel(0, 0));

        // channels get clipped (no overflow into green or alpha)
        paint.setColor(Color.MAGENTA);
        paint.setColorFilter(new LightingColorFilter(Color.WHITE, Color.MAGENTA));
        canvas.drawPaint(paint);
        verifyColor(Color.MAGENTA, bitmap.getPixel(0, 0));

        // multiply before add
        paint.setColor(Color.argb(255, 60, 20, 40));
        paint.setColorFilter(
                new LightingColorFilter(Color.rgb(0x80, 0xFF, 0x80), Color.rgb(0, 10, 10)));
        canvas.drawPaint(paint);
        verifyColor(Color.argb(255, 30, 30, 30), bitmap.getPixel(0, 0));

        // source alpha remains unchanged
        bitmap.eraseColor(Color.TRANSPARENT);
        paint.setColor(Color.argb(0x80, 60, 20, 40));
        paint.setColorFilter(
                new LightingColorFilter(Color.rgb(0x80, 0xFF, 0x80), Color.rgb(0, 10, 10)));
        canvas.drawPaint(paint);
        verifyColor(Color.argb(0x80, 30, 30, 30), bitmap.getPixel(0, 0));
    }

    @Test
    public void testGetColorAdd() {
        LightingColorFilter filter = new LightingColorFilter(Color.WHITE, Color.BLACK);
        ColorUtils.verifyColor(Color.BLACK, filter.getColorAdd());

        filter = new LightingColorFilter(0x87654321, 0x12345678);
        ColorUtils.verifyColor(0x12345678, filter.getColorAdd());
    }


    @Test
    public void testGetColorMultiply() {
        LightingColorFilter filter = new LightingColorFilter(Color.WHITE, Color.BLACK);
        ColorUtils.verifyColor(Color.WHITE, filter.getColorMultiply());

        filter = new LightingColorFilter(0x87654321, 0x12345678);
        ColorUtils.verifyColor(0x87654321, filter.getColorMultiply());
    }
}
