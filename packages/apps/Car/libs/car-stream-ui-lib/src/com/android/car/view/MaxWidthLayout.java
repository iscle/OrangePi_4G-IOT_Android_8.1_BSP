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
package com.android.car.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.car.stream.ui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Acts as a container to make the width of all its children not larger than the setup max width.
 *
 * To use MaxWidthLayout, put it as the outermost layout of all children you want to limit its max
 * width and set the maxWidth appropriately.
 */
public class MaxWidthLayout extends FrameLayout {

    // If mMaxChildrenWidth == 0, it means that it doesn't set the max width, just use the current
    // width directly.
    private int mMaxChildrenWidth;

    public MaxWidthLayout(Context context) {
        super(context);
        initialize(context.obtainStyledAttributes(R.styleable.MaxWidthLayout));
    }

    public MaxWidthLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLayout));
    }

    public MaxWidthLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context
                .obtainStyledAttributes(attrs, R.styleable.MaxWidthLayout, defStyleAttr, 0));
    }

    /**
     * Initialize MaxWidthLayout specific attributes and recycle the TypeArray.
     */
    private void initialize(TypedArray ta) {
        mMaxChildrenWidth = (int) (ta.getDimension(R.styleable.MaxWidthLayout_carMaxWidth, 0));
        ta.recycle();
    }

    /**
     * Re-measure the child width if it is greater than max width.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mMaxChildrenWidth == 0) {
            return;
        }

        final List<View> matchParentChildren = new ArrayList<View>();
        for (int i = getChildCount() - 1; i >= 0; --i) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (lp.width == LayoutParams.MATCH_PARENT) {
                    matchParentChildren.add(child);
                }
            }
        }
        for (int i = matchParentChildren.size() - 1; i >= 0; --i) {
            final View child = matchParentChildren.get(i);
            final MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            if (child.getMeasuredWidth() > mMaxChildrenWidth) {
                int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        mMaxChildrenWidth - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
                int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        child.getMeasuredHeight(), MeasureSpec.EXACTLY);
                child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
            }
        }
    }
}
