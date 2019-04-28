/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.ui;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ListView;

/**
 * A list view that auto resizes depending on the height of the viewing frame. This means this
 * list view will auto resize whenever the soft keyboard appears/disappears.
 */
public class AutoResizeListView extends ListView {
    private final Rect mRect = new Rect();
    private final int[] mCoords = new int[2];

    public AutoResizeListView(Context context) {
        this(context, null);
    }

    public AutoResizeListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        getWindowVisibleDisplayFrame(mRect);
        getLocationInWindow(mCoords);

        // The desired height is the available height we have for VIEWING.
        final int desiredHeight = mRect.bottom - mCoords[1];
        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        // Measure height and obey the measure mode.
        final int height;
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else {
            // For AT_MOST and UNSPECIFIED we always want to get the minimum.
            height = Math.min(desiredHeight, heightSize);
        }

        // Compile back to measure spec and pass it along
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(height, heightMode);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
