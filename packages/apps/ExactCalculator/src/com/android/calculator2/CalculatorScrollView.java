/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class CalculatorScrollView extends HorizontalScrollView {

    public CalculatorScrollView(Context context) {
        this(context, null /* attrs */);
    }

    public CalculatorScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public CalculatorScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private static int getChildMeasureSpecCompat(int spec, int padding, int childDimension) {
        if (MeasureSpec.getMode(spec) == UNSPECIFIED
                && (childDimension == MATCH_PARENT || childDimension == WRAP_CONTENT)) {
            final int size = Math.max(0, MeasureSpec.getSize(spec) - padding);
            return MeasureSpec.makeMeasureSpec(size, UNSPECIFIED);
        }
        return ViewGroup.getChildMeasureSpec(spec, padding, childDimension);
    }

    @Override
    protected void measureChild(View child, int parentWidthMeasureSpec,
            int parentHeightMeasureSpec) {
        // Allow child to be as wide as they want.
        parentWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(parentWidthMeasureSpec), UNSPECIFIED);

        final ViewGroup.LayoutParams lp = child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpecCompat(parentWidthMeasureSpec,
                0 /* padding */, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpecCompat(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom(), lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }

    @Override
    protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed,
            int parentHeightMeasureSpec, int heightUsed) {
        // Allow child to be as wide as they want.
        parentWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(parentWidthMeasureSpec), UNSPECIFIED);

        final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        final int childWidthMeasureSpec = getChildMeasureSpecCompat(parentWidthMeasureSpec,
                lp.leftMargin + lp.rightMargin, lp.width);
        final int childHeightMeasureSpec = getChildMeasureSpecCompat(parentHeightMeasureSpec,
                getPaddingTop() + getPaddingBottom() + lp.topMargin + lp.bottomMargin, lp.height);

        child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
    }
}
