/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.musicfx;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;


/*
 *  This ViewGroup contains a single view, which will be rotated by 90 degrees counterclockwise.
 */

public class SeekBarRotator extends ViewGroup {


    public SeekBarRotator(Context context) {
        super(context);
    }

    public SeekBarRotator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SeekBarRotator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SeekBarRotator(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final View child = getChildAt(0);

        if (child.getVisibility() != GONE) {
            // swap width and height for child
            measureChild(child, heightMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(
                    child.getMeasuredHeightAndState(),
                    child.getMeasuredWidthAndState());
        } else {
            setMeasuredDimension(
                    resolveSizeAndState(0, widthMeasureSpec, 0),
                    resolveSizeAndState(0, heightMeasureSpec, 0));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final View child = getChildAt(0);

        if (child.getVisibility() != GONE) {
            // rotate the child 90 degrees counterclockwise around its upper-left
            child.setPivotX(0);
            child.setPivotY(0);
            child.setRotation(-90);

            // place the child below this view, so it rotates into view
            int mywidth = r - l;
            int myheight = b - t;
            int childwidth = myheight;
            int childheight = mywidth;

            child.layout(0, myheight, childwidth, myheight + childheight);
        }
    }

}
