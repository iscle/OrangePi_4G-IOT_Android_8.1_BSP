/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.stream.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

/**
 * Utility class that calculates the number of columns that will fit on the screen. A column's width
 * is determined by the size of the margins and gutters (space between the columns) that fit
 * on-screen. Refer to go/aae-por for a table of margin, gutter and number of columns per screen
 * size.
 */
public class ColumnCalculator {
    private static final String TAG = "Em.ColumnCalculator";

    private static ColumnCalculator sInstance;
    private static int sScreenWidth;

    private int mNumOfColumns;
    private int mNumOfGutters;
    private int mColumnWidth;
    private int mGutterSize;

    public static ColumnCalculator getInstance(Context context) {
        if (sInstance == null) {
            WindowManager windowManager = (WindowManager) context.getSystemService(
                    Context.WINDOW_SERVICE);
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            sScreenWidth = displayMetrics.widthPixels;

            sInstance = new ColumnCalculator(context);
        }

        return sInstance;
    }

    private ColumnCalculator(Context context) {
        Resources res = context.getResources();
        int marginSize = res.getDimensionPixelSize(R.dimen.stream_margin_size);
        mGutterSize = res.getDimensionPixelSize(R.dimen.stream_gutter_size);
        mNumOfColumns = res.getInteger(R.integer.stream_num_of_columns);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "marginSize: " + marginSize + "; numOfColumns: " + mNumOfColumns
                    + "; gutterSize: " + mGutterSize);
        }

        // The gutters appear between each column. As a result, the number of gutters is one less
        // than the number of columns.
        mNumOfGutters = mNumOfColumns - 1;

        // Determine the spacing that is allowed to be filled by the columns by subtracting margins
        // on both size of the screen and the space taken up by the gutters.
        int spaceForColumns = sScreenWidth - (2 * marginSize) - (mNumOfGutters * mGutterSize);

        mColumnWidth = spaceForColumns / mNumOfColumns;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "mColumnWidth: " + mColumnWidth);
        }
    }

    /**
     * Returns the total number of columns that fit on screen.
     */
    public int getNumOfColumns() {
        return mNumOfColumns;
    }

    /**
     * Returns the size in pixels of each column.
     */
    public int getColumnWidth() {
        return mColumnWidth;
    }

    /**
     * Returns the total number of gutters that fit on screen. A gutter is the space between each
     * column. This value is always one less than the number of columns.
     */
    public int getNumOfGutters() {
        return mNumOfGutters;
    }

    /**
     * Returns the size of each gutter.
     */
    public int getGutterSize() {
        return mGutterSize;
    }

    /**
     * Returns the size in pixels for a View that will span the given number of columns.
     */
    public int getSizeForColumnSpan(int columnSpan) {
        int gutterSpan = columnSpan - 1;
        return columnSpan * mColumnWidth + gutterSpan * mGutterSize;
    }
}
