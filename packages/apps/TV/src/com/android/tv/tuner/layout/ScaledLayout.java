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

package com.android.tv.tuner.layout;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.tuner.R;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A layout that scales its children using the given percentage value.
 */
public class ScaledLayout extends ViewGroup {
    private static final String TAG = "ScaledLayout";
    private static final boolean DEBUG = false;
    private static final Comparator<Rect> mRectTopLeftSorter = new Comparator<Rect>() {
        @Override
        public int compare(Rect lhs, Rect rhs) {
            if (lhs.top != rhs.top) {
                return lhs.top - rhs.top;
            } else {
                return lhs.left - rhs.left;
            }
        }
    };

    private Rect[] mRectArray;
    private final int mMaxWidth;
    private final int mMaxHeight;

    public ScaledLayout(Context context) {
        this(context, null);
    }

    public ScaledLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScaledLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Point size = new Point();
        DisplayManager displayManager = (DisplayManager) getContext()
                .getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealSize(size);
        mMaxWidth = size.x;
        mMaxHeight = size.y;
    }

    /**
     * ScaledLayoutParams stores the four scale factors.
     * <br>
     * Vertical coordinate system:   ({@code scaleStartRow} * 100) % ~ ({@code scaleEndRow} * 100) %
     * Horizontal coordinate system: ({@code scaleStartCol} * 100) % ~ ({@code scaleEndCol} * 100) %
     * <br>
     * In XML, for example,
     * <pre>
     * {@code
     * <View
     *     app:layout_scaleStartRow="0.1"
     *     app:layout_scaleEndRow="0.5"
     *     app:layout_scaleStartCol="0.4"
     *     app:layout_scaleEndCol="1" />
     * }
     * </pre>
     */
    public static class ScaledLayoutParams extends ViewGroup.LayoutParams {
        public static final float SCALE_UNSPECIFIED = -1;
        public final float scaleStartRow;
        public final float scaleEndRow;
        public final float scaleStartCol;
        public final float scaleEndCol;

        public ScaledLayoutParams(float scaleStartRow, float scaleEndRow,
                float scaleStartCol, float scaleEndCol) {
            super(MATCH_PARENT, MATCH_PARENT);
            this.scaleStartRow = scaleStartRow;
            this.scaleEndRow = scaleEndRow;
            this.scaleStartCol = scaleStartCol;
            this.scaleEndCol = scaleEndCol;
        }

        public ScaledLayoutParams(Context context, AttributeSet attrs) {
            super(MATCH_PARENT, MATCH_PARENT);
            TypedArray array =
                context.obtainStyledAttributes(attrs, R.styleable.utScaledLayout);
            scaleStartRow =
                array.getFloat(R.styleable.utScaledLayout_layout_scaleStartRow, SCALE_UNSPECIFIED);
            scaleEndRow =
                array.getFloat(R.styleable.utScaledLayout_layout_scaleEndRow, SCALE_UNSPECIFIED);
            scaleStartCol =
                array.getFloat(R.styleable.utScaledLayout_layout_scaleStartCol, SCALE_UNSPECIFIED);
            scaleEndCol =
                array.getFloat(R.styleable.utScaledLayout_layout_scaleEndCol, SCALE_UNSPECIFIED);
            array.recycle();
        }
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new ScaledLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return (p instanceof ScaledLayoutParams);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
        int width = widthSpecSize - getPaddingLeft() - getPaddingRight();
        int height = heightSpecSize - getPaddingTop() - getPaddingBottom();
        if (DEBUG) {
            Log.d(TAG, String.format("onMeasure width: %d, height: %d", width, height));
        }
        int count = getChildCount();
        mRectArray = new Rect[count];
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            float scaleStartRow, scaleEndRow, scaleStartCol, scaleEndCol;
            if (!(params instanceof ScaledLayoutParams)) {
                throw new RuntimeException(
                        "A child of ScaledLayout cannot have the UNSPECIFIED scale factors");
            }
            scaleStartRow = ((ScaledLayoutParams) params).scaleStartRow;
            scaleEndRow = ((ScaledLayoutParams) params).scaleEndRow;
            scaleStartCol = ((ScaledLayoutParams) params).scaleStartCol;
            scaleEndCol = ((ScaledLayoutParams) params).scaleEndCol;
            if (scaleStartRow < 0 || scaleStartRow > 1) {
                throw new RuntimeException("A child of ScaledLayout should have a range of "
                        + "scaleStartRow between 0 and 1");
            }
            if (scaleEndRow < scaleStartRow || scaleStartRow > 1) {
                throw new RuntimeException("A child of ScaledLayout should have a range of "
                        + "scaleEndRow between scaleStartRow and 1");
            }
            if (scaleEndCol < 0 || scaleEndCol > 1) {
                throw new RuntimeException("A child of ScaledLayout should have a range of "
                        + "scaleStartCol between 0 and 1");
            }
            if (scaleEndCol < scaleStartCol || scaleEndCol > 1) {
                throw new RuntimeException("A child of ScaledLayout should have a range of "
                        + "scaleEndCol between scaleStartCol and 1");
            }
            if (DEBUG) {
                Log.d(TAG, String.format("onMeasure child scaleStartRow: %f scaleEndRow: %f "
                        + "scaleStartCol: %f scaleEndCol: %f",
                        scaleStartRow, scaleEndRow, scaleStartCol, scaleEndCol));
            }
            mRectArray[i] = new Rect((int) (scaleStartCol * width), (int) (scaleStartRow * height),
                    (int) (scaleEndCol * width), (int) (scaleEndRow * height));
            int scaleWidth = (int) (width * (scaleEndCol - scaleStartCol));
            int childWidthSpec = MeasureSpec.makeMeasureSpec(
                    scaleWidth > mMaxWidth ? mMaxWidth : scaleWidth, MeasureSpec.EXACTLY);
            int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            child.measure(childWidthSpec, childHeightSpec);

            // If the height of the measured child view is bigger than the height of the calculated
            // region by the given ScaleLayoutParams, the height of the region should be increased
            // to fit the size of the child view.
            if (child.getMeasuredHeight() > mRectArray[i].height()) {
                int overflowedHeight = child.getMeasuredHeight() - mRectArray[i].height();
                overflowedHeight = (overflowedHeight + 1) / 2;
                mRectArray[i].bottom += overflowedHeight;
                mRectArray[i].top -= overflowedHeight;
                if (mRectArray[i].top < 0) {
                    mRectArray[i].bottom -= mRectArray[i].top;
                    mRectArray[i].top = 0;
                }
                if (mRectArray[i].bottom > height) {
                    mRectArray[i].top -= mRectArray[i].bottom - height;
                    mRectArray[i].bottom = height;
                }
            }
            int scaleHeight = (int) (height * (scaleEndRow - scaleStartRow));
            childHeightSpec = MeasureSpec.makeMeasureSpec(
                    scaleHeight > mMaxHeight ? mMaxHeight : scaleHeight, MeasureSpec.EXACTLY);
            child.measure(childWidthSpec, childHeightSpec);
        }

        // Avoid overlapping rectangles.
        // Step 1. Sort rectangles by position (top-left).
        int visibleRectCount = 0;
        int[] visibleRectGroup = new int[count];
        Rect[] visibleRectArray = new Rect[count];
        for (int i = 0; i < count; ++i) {
            if (getChildAt(i).getVisibility() == View.VISIBLE) {
                visibleRectGroup[visibleRectCount] = visibleRectCount;
                visibleRectArray[visibleRectCount] = mRectArray[i];
                ++visibleRectCount;
            }
        }
        Arrays.sort(visibleRectArray, 0, visibleRectCount, mRectTopLeftSorter);

        // Step 2. Move down if there are overlapping rectangles.
        for (int i = 0; i < visibleRectCount - 1; ++i) {
            for (int j = i + 1; j < visibleRectCount; ++j) {
                if (Rect.intersects(visibleRectArray[i], visibleRectArray[j])) {
                    visibleRectGroup[j] = visibleRectGroup[i];
                    visibleRectArray[j].set(visibleRectArray[j].left,
                            visibleRectArray[i].bottom,
                            visibleRectArray[j].right,
                            visibleRectArray[i].bottom + visibleRectArray[j].height());
                }
            }
        }

        // Step 3. Move up if there is any overflowed rectangle.
        for (int i = visibleRectCount - 1; i >= 0; --i) {
            if (visibleRectArray[i].bottom > height) {
                int overflowedHeight = visibleRectArray[i].bottom - height;
                for (int j = 0; j <= i; ++j) {
                    if (visibleRectGroup[i] == visibleRectGroup[j]) {
                        visibleRectArray[j].set(visibleRectArray[j].left,
                                visibleRectArray[j].top - overflowedHeight,
                                visibleRectArray[j].right,
                                visibleRectArray[j].bottom - overflowedHeight);
                    }
                }
            }
        }
        setMeasuredDimension(widthSpecSize, heightSpecSize);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int count = getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                int childLeft = paddingLeft + mRectArray[i].left;
                int childTop = paddingTop + mRectArray[i].top;
                int childBottom = paddingLeft + mRectArray[i].bottom;
                int childRight = paddingTop + mRectArray[i].right;
                if (DEBUG) {
                    Log.d(TAG, String.format("layoutChild bottom: %d left: %d right: %d top: %d",
                            childBottom, childLeft,
                            childRight, childTop));
                }
                child.layout(childLeft, childTop, childRight, childBottom);
            }
        }
    }
}
