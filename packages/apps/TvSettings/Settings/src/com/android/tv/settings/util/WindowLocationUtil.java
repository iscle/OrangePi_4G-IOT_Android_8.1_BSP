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

package com.android.tv.settings.util;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

/**
 * Utility class for View/ImageView locations and clip bounds etc.
 */
public class WindowLocationUtil {

    private static final float[] sTmpFloat4 = new float[4];
    private static final float[] sTmpFloat8 = new float[8];

    /**
     * map points inside view into locations of a screen
     * The function is an extension of {@link View#getLocationInWindow } and can map
     * multiple points.
     *
     * @param points x[0], y[0], x[1], y[1], ...
     */
    public static void getLocationsInWindow(View view, float[] points) {

        if (points == null || (points.length & 1) != 0) {
            throw new IllegalArgumentException();
        }
        int length = points.length;
        Matrix matrix = view.getMatrix();
        if (matrix != null && !matrix.isIdentity()) {
            matrix.mapPoints(points);
        }

        int deltax = view.getLeft();
        int deltay = view.getTop();
        for (int i = 0; i < length; ) {
            points[i] += deltax;
            i++;
            points[i] += deltay;
            i++;
        }

        ViewParent viewParent = view.getParent();
        while (viewParent instanceof View) {
            view = (View) viewParent;

            deltax = view.getScrollX();
            deltay = view.getScrollY();
            for (int i = 0; i < length; ) {
                points[i] -= deltax;
                i++;
                points[i] -= deltay;
                i++;
            }

            matrix = view.getMatrix();
            if (matrix != null && !matrix.isIdentity()) {
                matrix.mapPoints(points);
            }

            deltax = view.getLeft();
            deltay = view.getTop();
            for (int i = 0; i < length; ) {
                points[i] += deltax;
                i++;
                points[i] += deltay;
                i++;
            }

            viewParent = view.getParent();
        }
    }

    /**
     * get locations of view bounds in Window
     */
    public static void getLocationsInWindow(View view, RectF rect) {
        sTmpFloat4[0] = rect.left;
        sTmpFloat4[1] = rect.top;
        sTmpFloat4[2] = rect.right;
        sTmpFloat4[3] = rect.bottom;
        getLocationsInWindow(view, sTmpFloat4);
        rect.left = sTmpFloat4[0];
        rect.top = sTmpFloat4[1];
        rect.right = sTmpFloat4[2];
        rect.bottom = sTmpFloat4[3];
    }

    /**
     * get clip and unclipped bounds of ImageView inside a window
     */
    public static void getImageLocationsInWindow(ImageView view,
            RectF clippedBounds, RectF unclippedBitmapRect) {
        // get bounds exclude padding, bitmap will be clipped by this bounds
        clippedBounds.set(view.getPaddingLeft(), view.getPaddingTop(),
                view.getWidth() - view.getPaddingRight(),
                view.getHeight() - view.getPaddingBottom());
        Matrix matrix = view.getImageMatrix();

        Drawable drawable = view.getDrawable();

        if (drawable != null) {
            unclippedBitmapRect.set(drawable.getBounds());
            matrix.mapRect(unclippedBitmapRect);
            unclippedBitmapRect.offset(view.getPaddingLeft(), view.getPaddingTop());
            sTmpFloat8[0] = clippedBounds.left;
            sTmpFloat8[1] = clippedBounds.top;
            sTmpFloat8[2] = clippedBounds.right;
            sTmpFloat8[3] = clippedBounds.bottom;
            sTmpFloat8[4] = unclippedBitmapRect.left;
            sTmpFloat8[5] = unclippedBitmapRect.top;
            sTmpFloat8[6] = unclippedBitmapRect.right;
            sTmpFloat8[7] = unclippedBitmapRect.bottom;
            getLocationsInWindow(view, sTmpFloat8);
            clippedBounds.left = sTmpFloat8[0];
            clippedBounds.top = sTmpFloat8[1];
            clippedBounds.right = sTmpFloat8[2];
            clippedBounds.bottom = sTmpFloat8[3];
            unclippedBitmapRect.left = sTmpFloat8[4];
            unclippedBitmapRect.top = sTmpFloat8[5];
            unclippedBitmapRect.right = sTmpFloat8[6];
            unclippedBitmapRect.bottom = sTmpFloat8[7];
            clippedBounds.intersect(unclippedBitmapRect);
        } else {
            sTmpFloat4[0] = clippedBounds.left;
            sTmpFloat4[1] = clippedBounds.top;
            sTmpFloat4[2] = clippedBounds.right;
            sTmpFloat4[3] = clippedBounds.bottom;
            getLocationsInWindow(view, sTmpFloat4);
            clippedBounds.left = sTmpFloat4[0];
            clippedBounds.top = sTmpFloat4[1];
            clippedBounds.right = sTmpFloat4[2];
            clippedBounds.bottom = sTmpFloat4[3];
            unclippedBitmapRect.set(clippedBounds);
        }
    }
}
