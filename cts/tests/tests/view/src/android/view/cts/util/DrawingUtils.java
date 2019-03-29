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

package android.view.cts.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Pair;
import android.view.View;

import junit.framework.Assert;

import java.util.List;

public class DrawingUtils {
    /**
     * Checks the pixels in the specified {@link View}. This methods draws the view into an
     * offscreen bitmap and checks each pixel to match the expected color. The expected color
     * of each pixel is either the color associated with the {@link Rect} that contains it,
     * or the default color in case none of the rectangles cover that pixel.
     *
     * In case of a mismatch this method will call {@link Assert#fail(String)}
     * with detailed description of the mismatch.
     */
    public static void assertAllPixelsOfColor(String failMessagePrefix, View view,
            int defaultColor, List<Pair<Rect, Integer>> colorRectangles) {
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        // Create a bitmap that matches the size of our view
        final Bitmap bitmap = Bitmap.createBitmap(viewWidth, viewHeight, Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        final Canvas canvas = new Canvas(bitmap);
        // And ask the view to draw itself to the canvas / bitmap
        view.draw(canvas);

        // Now create a golden bitmap based on the colors passed to us
        final Bitmap goldenBitmap = Bitmap.createBitmap(viewWidth, viewHeight,
                Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        final Canvas goldenCanvas = new Canvas(goldenBitmap);
        // Fill it with default color
        final Paint goldenPaint = new Paint();
        goldenPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        goldenPaint.setColor(defaultColor);
        goldenCanvas.drawRect(0, 0, viewWidth, viewHeight, goldenPaint);
        if (colorRectangles != null) {
            for (Pair<Rect, Integer> colorRectangle : colorRectangles) {
                goldenPaint.setColor(colorRectangle.second);
                goldenCanvas.drawRect(colorRectangle.first, goldenPaint);
            }
        }

        // Compare bitmap pixels (row-wise)
        try {
            int[] rowPixels = new int[viewWidth];
            int[] rowGoldenPixels = new int[viewWidth];
            for (int row = 0; row < viewHeight; row++) {
                bitmap.getPixels(rowPixels, 0, viewWidth, 0, row, viewWidth, 1);
                goldenBitmap.getPixels(rowGoldenPixels, 0, viewWidth, 0, row, viewWidth, 1);
                for (int column = 0; column < viewWidth; column++) {
                    int expectedPixelColor = rowGoldenPixels[column];
                    int actualPixelColor = rowPixels[column];
                    if (rowPixels[column] != expectedPixelColor) {
                        String mismatchDescription = failMessagePrefix
                                + ": expected all drawable colors to be ["
                                + Color.alpha(expectedPixelColor) + ","
                                + Color.red(expectedPixelColor) + ","
                                + Color.green(expectedPixelColor) + ","
                                + Color.blue(expectedPixelColor)
                                + "] but at position (" + row + "," + column + ") found ["
                                + Color.alpha(actualPixelColor) + ","
                                + Color.red(actualPixelColor) + ","
                                + Color.green(actualPixelColor) + ","
                                + Color.blue(actualPixelColor) + "]";
                        Assert.fail(mismatchDescription);
                    }
                }
            }
        } finally {
            bitmap.recycle();
            goldenBitmap.recycle();
        }
    }
}
