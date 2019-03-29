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
 * limitations under the License
 */

package com.android.cts.verifier;

import android.annotation.TargetApi;
import android.os.Build;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/**
 * BoxInsetLayout is a screen shape-aware FrameLayout that can box its children
 * in the center square of a round screen by using the
 * {@code ctsv_layout_box} attribute. The values for this attribute specify the
 * child's edges to be boxed in:
 * {@code left|top|right|bottom} or {@code all}.
 * The {@code ctsv_layout_box} attribute is ignored on a device with a rectangular
 * screen.
 */
@TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
public class BoxInsetLayout extends FrameLayout {

    private static float FACTOR = 0.146467f; //(1 - sqrt(2)/2)/2
    private static final int DEFAULT_CHILD_GRAVITY = Gravity.TOP | Gravity.START;

    private Rect mForegroundPadding;
    private boolean mLastKnownRound;
    private Rect mInsets;

    public BoxInsetLayout(Context context) {
        this(context, null);
    }

    public BoxInsetLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoxInsetLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // make sure we have foreground padding object
        if (mForegroundPadding == null) {
            mForegroundPadding = new Rect();
        }
        if (mInsets == null) {
            mInsets = new Rect();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insets = super.onApplyWindowInsets(insets);
        final boolean round = insets.isRound();
        if (round != mLastKnownRound) {
            mLastKnownRound = round;
            requestLayout();
        }
        mInsets.set(
            insets.getSystemWindowInsetLeft(),
            insets.getSystemWindowInsetTop(),
            insets.getSystemWindowInsetRight(),
            insets.getSystemWindowInsetBottom());
        return insets;
    }

    /**
     * determine screen shape
     * @return true if on a round screen
     */
    public boolean isRound() {
        return mLastKnownRound;
    }

    /**
     * @return the system window insets Rect
     */
    public Rect getInsets() {
        return mInsets;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int count = getChildCount();
        // find max size
        int maxWidth = 0;
        int maxHeight = 0;
        int childState = 0;
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                LayoutParams lp = (BoxInsetLayout.LayoutParams) child.getLayoutParams();
                int marginLeft = 0;
                int marginRight = 0;
                int marginTop = 0;
                int marginBottom = 0;
                if (mLastKnownRound) {
                    // round screen, check boxed, don't use margins on boxed
                    if ((lp.boxedEdges & LayoutParams.BOX_LEFT) == 0) {
                        marginLeft = lp.leftMargin;
                    }
                    if ((lp.boxedEdges & LayoutParams.BOX_RIGHT) == 0) {
                        marginRight = lp.rightMargin;
                    }
                    if ((lp.boxedEdges & LayoutParams.BOX_TOP) == 0) {
                        marginTop = lp.topMargin;
                    }
                    if ((lp.boxedEdges & LayoutParams.BOX_BOTTOM) == 0) {
                        marginBottom = lp.bottomMargin;
                    }
                } else {
                    // rectangular, ignore boxed, use margins
                    marginLeft = lp.leftMargin;
                    marginTop = lp.topMargin;
                    marginRight = lp.rightMargin;
                    marginBottom = lp.bottomMargin;
                }
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                maxWidth = Math.max(maxWidth,
                        child.getMeasuredWidth() + marginLeft + marginRight);
                maxHeight = Math.max(maxHeight,
                        child.getMeasuredHeight() + marginTop + marginBottom);
                childState = combineMeasuredStates(childState, child.getMeasuredState());
            }
        }
        // Account for padding too
        maxWidth += getPaddingLeft() + mForegroundPadding.left
                + getPaddingRight() + mForegroundPadding.right;
        maxHeight += getPaddingTop() + mForegroundPadding.top
                + getPaddingBottom() + mForegroundPadding.bottom;

        // Check against our minimum height and width
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());

        // Check against our foreground's minimum height and width
        final Drawable drawable = getForeground();
        if (drawable != null) {
            maxHeight = Math.max(maxHeight, drawable.getMinimumHeight());
            maxWidth = Math.max(maxWidth, drawable.getMinimumWidth());
        }

        setMeasuredDimension(resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
                resolveSizeAndState(maxHeight, heightMeasureSpec,
                        childState << MEASURED_HEIGHT_STATE_SHIFT));

        // determine boxed inset
        int boxInset = (int) (FACTOR * Math.max(getMeasuredWidth(), getMeasuredHeight()));
        // adjust the match parent children
        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);

            final LayoutParams lp = (BoxInsetLayout.LayoutParams) child.getLayoutParams();
            int childWidthMeasureSpec;
            int childHeightMeasureSpec;
            int plwf = getPaddingLeft() + mForegroundPadding.left;
            int prwf = getPaddingRight() + mForegroundPadding.right;
            int ptwf = getPaddingTop() + mForegroundPadding.top;
            int pbwf = getPaddingBottom() + mForegroundPadding.bottom;

            // adjust width
            int totalPadding = 0;
            int totalMargin = 0;
            // BoxInset is a padding. Ignore margin when we want to do BoxInset.
            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_LEFT) != 0)) {
                totalPadding += boxInset;
            } else {
                totalMargin += plwf + lp.leftMargin;
            }
            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_RIGHT) != 0)) {
                totalPadding += boxInset;
            } else {
                totalMargin += prwf + lp.rightMargin;
            }
            if (lp.width == LayoutParams.MATCH_PARENT) {
                //  Only subtract margin from the actual width, leave the padding in.
                childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                        getMeasuredWidth() - totalMargin, MeasureSpec.EXACTLY);
            } else {
                childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec,
                        totalPadding + totalMargin, lp.width);
            }

            // adjust height
            totalPadding = 0;
            totalMargin = 0;
            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_TOP) != 0)) {
                totalPadding += boxInset;
            } else {
                totalMargin += ptwf + lp.topMargin;
            }
            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_BOTTOM) != 0)) {
                totalPadding += boxInset;
            } else {
                totalMargin += pbwf + lp.bottomMargin;
            }

            if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                        getMeasuredHeight() - totalMargin, MeasureSpec.EXACTLY);
            } else {
                childHeightMeasureSpec = getChildMeasureSpec(heightMeasureSpec,
                        totalPadding + totalMargin, lp.height);
            }

            child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
        }
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        layoutBoxChildren(left, top, right, bottom, false /* no force left gravity */);
    }

    private void layoutBoxChildren(int left, int top, int right, int bottom,
                                  boolean forceLeftGravity) {
        final int count = getChildCount();
        int boxInset = (int)(FACTOR * Math.max(right - left, bottom - top));

        final int parentLeft = getPaddingLeft() + mForegroundPadding.left;
        final int parentRight = right - left - getPaddingRight() - mForegroundPadding.right;

        final int parentTop = getPaddingTop() + mForegroundPadding.top;
        final int parentBottom = bottom - top - getPaddingBottom() - mForegroundPadding.bottom;

        for (int i = 0; i < count; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() != GONE) {
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = DEFAULT_CHILD_GRAVITY;
                }

                final int layoutDirection = getLayoutDirection();
                final int absoluteGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection);
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                // These values are replaced with boxInset below as necessary.
                int paddingLeft = child.getPaddingLeft();
                int paddingRight = child.getPaddingRight();
                int paddingTop = child.getPaddingTop();
                int paddingBottom = child.getPaddingBottom();

                // If the child's width is match_parent, we ignore gravity and set boxInset padding
                // on both sides, with a left position of parentLeft + the child's left margin.
                if (lp.width == LayoutParams.MATCH_PARENT) {
                    if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_LEFT) != 0)) {
                        paddingLeft = boxInset;
                    }
                    if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_RIGHT) != 0)) {
                        paddingRight = boxInset;
                    }
                    childLeft = parentLeft + lp.leftMargin;
                } else {
                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = parentLeft + (parentRight - parentLeft - width) / 2 +
                                    lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            if (!forceLeftGravity) {
                                if (mLastKnownRound
                                        && ((lp.boxedEdges & LayoutParams.BOX_RIGHT) != 0)) {
                                    paddingRight = boxInset;
                                    childLeft = right - left - width;
                                } else {
                                    childLeft = parentRight - width - lp.rightMargin;
                                }
                                break;
                            }
                        case Gravity.LEFT:
                        default:
                            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_LEFT) != 0)) {
                                paddingLeft = boxInset;
                                childLeft = 0;
                            } else {
                                childLeft = parentLeft + lp.leftMargin;
                            }
                    }
                }

                // If the child's height is match_parent, we ignore gravity and set boxInset padding
                // on both top and bottom, with a top position of parentTop + the child's top
                // margin.
                if (lp.height == LayoutParams.MATCH_PARENT) {
                    if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_TOP) != 0)) {
                        paddingTop = boxInset;
                    }
                    if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_BOTTOM) != 0)) {
                        paddingBottom = boxInset;
                    }
                    childTop = parentTop + lp.topMargin;
                } else {
                    switch (verticalGravity) {
                        case Gravity.TOP:
                            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_TOP) != 0)) {
                                paddingTop = boxInset;
                                childTop = 0;
                            } else {
                                childTop = parentTop + lp.topMargin;
                            }
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = parentTop + (parentBottom - parentTop - height) / 2 +
                                    lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            if (mLastKnownRound && ((lp.boxedEdges & LayoutParams.BOX_BOTTOM) != 0)) {
                                paddingBottom = boxInset;
                                childTop = bottom - top - height;
                            } else {
                                childTop = parentBottom - height - lp.bottomMargin;
                            }
                            break;
                        default:
                            childTop = parentTop + lp.topMargin;
                    }
                }

                child.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }
        }
    }

    public void setForeground(Drawable drawable) {
        super.setForeground(drawable);
        if (mForegroundPadding == null) {
            mForegroundPadding = new Rect();
        }
        drawable.getPadding(mForegroundPadding);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new BoxInsetLayout.LayoutParams(getContext(), attrs);
    }

    /**
     * adds {@code ctsv_layout_box} attribute to layout parameters
     */
    public static class LayoutParams extends FrameLayout.LayoutParams {

        public static final int BOX_NONE = 0x0;
        public static final int BOX_LEFT = 0x01;
        public static final int BOX_TOP = 0x02;
        public static final int BOX_RIGHT = 0x04;
        public static final int BOX_BOTTOM = 0x08;
        public static final int BOX_ALL = 0x0F;

        public int boxedEdges = BOX_NONE;

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);
            TypedArray a = context.obtainStyledAttributes(attrs,  R.styleable.BoxInsetLayout_Layout, 0, 0);
            boxedEdges = a.getInt(R.styleable.BoxInsetLayout_Layout_ctsv_layout_box, BOX_NONE);
            a.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height, gravity);
        }

        public LayoutParams(int width, int height, int gravity, int boxed) {
            super(width, height, gravity);
            boxedEdges = boxed;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(FrameLayout.LayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.boxedEdges = source.boxedEdges;
            this.gravity = source.gravity;
        }

    }
}
