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

package com.android.tv.guide;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Range;
import android.view.View;
import android.view.ViewTreeObserver;

import com.android.tv.R;
import com.android.tv.ui.OnRepeatedKeyInterceptListener;

import java.util.concurrent.TimeUnit;

/**
 * A {@link VerticalGridView} for the program table view.
 */
public class ProgramGrid extends VerticalGridView {
    private static final String TAG = "ProgramGrid";

    private static final int INVALID_INDEX = -1;
    private static final long FOCUS_AREA_RIGHT_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private final ViewTreeObserver.OnGlobalFocusChangeListener mGlobalFocusChangeListener =
            new ViewTreeObserver.OnGlobalFocusChangeListener() {
                @Override
                public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                    if (newFocus != mNextFocusByUpDown) {
                        // If focus is changed by other buttons than UP/DOWN buttons,
                        // we clear the focus state.
                        clearUpDownFocusState(newFocus);
                    }
                    mNextFocusByUpDown = null;
                    if (GuideUtils.isDescendant(ProgramGrid.this, newFocus)) {
                        mLastFocusedView = newFocus;
                    }
                }
            };

    private final ProgramManager.Listener mProgramManagerListener =
            new ProgramManager.ListenerAdapter() {
                @Override
                public void onTimeRangeUpdated() {
                    // When time range is changed, we clear the focus state.
                    clearUpDownFocusState(null);
                }
            };

    private final ViewTreeObserver.OnPreDrawListener mPreDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    updateInputLogo();
                    return true;
                }
            };

    private ProgramManager mProgramManager;
    private View mNextFocusByUpDown;

    // New focus will be overlapped with [mFocusRangeLeft, mFocusRangeRight].
    private int mFocusRangeLeft;
    private int mFocusRangeRight;

    private final int mRowHeight;
    private final int mDetailHeight;
    private final int mSelectionRow;  // Row that is focused

    private View mLastFocusedView;
    private final Rect mTempRect = new Rect();
    private int mLastUpDownDirection;

    private boolean mKeepCurrentProgramFocused;

    private ChildFocusListener mChildFocusListener;
    private final OnRepeatedKeyInterceptListener mOnRepeatedKeyInterceptListener;

    interface ChildFocusListener {
        /**
         * Is called before focus is moved. Only children to {@code ProgramGrid} will be passed.
         * See {@code ProgramGrid#setChildFocusListener(ChildFocusListener)}.
         */
        void onRequestChildFocus(View oldFocus, View newFocus);
    }

    public ProgramGrid(Context context) {
        this(context, null);
    }

    public ProgramGrid(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ProgramGrid(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        clearUpDownFocusState(null);

        // Don't cache anything that is off screen. Normally it is good to prefetch and prepopulate
        // off screen views in order to reduce jank, however the program guide is capable to scroll
        // in all four directions so not only would we prefetch views in the scrolling direction
        // but also keep views in the perpendicular direction up to date.
        // E.g. when scrolling horizontally we would have to update rows above and below the current
        // view port even though they are not visible.
        setItemViewCacheSize(0);

        Resources res = context.getResources();
        mRowHeight = res.getDimensionPixelSize(R.dimen.program_guide_table_item_row_height);
        mDetailHeight = res.getDimensionPixelSize(R.dimen.program_guide_table_detail_height);
        mSelectionRow = res.getInteger(R.integer.program_guide_selection_row);
        mOnRepeatedKeyInterceptListener = new OnRepeatedKeyInterceptListener(this);
        setOnKeyInterceptListener(mOnRepeatedKeyInterceptListener);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (mChildFocusListener != null) {
            mChildFocusListener.onRequestChildFocus(getFocusedChild(), child);
        }
        super.requestChildFocus(child, focused);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(mGlobalFocusChangeListener);
        mProgramManager.addListener(mProgramManagerListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(mGlobalFocusChangeListener);
        mProgramManager.removeListener(mProgramManagerListener);
        clearUpDownFocusState(null);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        mNextFocusByUpDown = null;
        if (focused == null || (focused != this && !GuideUtils.isDescendant(this, focused))) {
            return super.focusSearch(focused, direction);
        }
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            updateUpDownFocusState(focused, direction);
            View nextFocus = focusFind(focused, direction);
            if (nextFocus != null) {
                return nextFocus;
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (mLastFocusedView != null && mLastFocusedView.isShown()) {
            if (mLastFocusedView.requestFocus()) {
                return true;
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        // It is required to properly handle OnRepeatedKeyInterceptListener. If the focused
        // item's are at the almost end of screen, focus change to the next item doesn't work.
        // It restricts that a focus item's position cannot be too far from the desired position.
        View focusedView = findFocus();
        if (focusedView != null && mOnRepeatedKeyInterceptListener.isFocusAccelerated()) {
            int[] location = new int[2];
            getLocationOnScreen(location);
            int[] focusedLocation = new int[2];
            focusedView.getLocationOnScreen(focusedLocation);
            int y = focusedLocation[1] - location[1];
            int minY = (mSelectionRow - 1) * mRowHeight;
            if (y < minY) scrollBy(0, y - minY);
            int maxY = (mSelectionRow + 1) * mRowHeight + mDetailHeight;
            if (y > maxY) scrollBy(0, y - maxY);
        }
        updateInputLogo();
    }

    @Override
    public void onViewRemoved(View view) {
        // It is required to ensure input logo showing when the scroll is moved to most bottom.
        updateInputLogo();
    }

    /**
     * Initializes ProgramGrid. It should be called before the view is actually attached to
     * Window.
     */
    void initialize(ProgramManager programManager) {
        mProgramManager = programManager;
    }

    /**
     * Registers a listener focus events occurring on children to the {@code ProgramGrid}.
     */
    void setChildFocusListener(ChildFocusListener childFocusListener) {
        mChildFocusListener = childFocusListener;
    }

    void onItemSelectionReset() {
        getViewTreeObserver().addOnPreDrawListener(mPreDrawListener);
    }

    /**
     * Resets focus states. If the logic to keep the last focus needs to be cleared, it should
     * be called.
     */
    void resetFocusState() {
        mLastFocusedView = null;
        clearUpDownFocusState(null);
    }

    /** Returns the currently focused item's horizontal range. */
    Range<Integer> getFocusRange() {
        return new Range<>(mFocusRangeLeft, mFocusRangeRight);
    }

    /** Returns if the next focused item should be the current program if possible. */
    boolean isKeepCurrentProgramFocused() {
        return mKeepCurrentProgramFocused;
    }

    /** Returns the last up/down move direction of browsing */
    int getLastUpDownDirection() {
        return mLastUpDownDirection;
    }

    private View focusFind(View focused, int direction) {
        int focusedChildIndex = getFocusedChildIndex();
        if (focusedChildIndex == INVALID_INDEX) {
            Log.w(TAG, "No child view has focus");
            return null;
        }
        int nextChildIndex = direction == View.FOCUS_UP ? focusedChildIndex - 1
                : focusedChildIndex + 1;
        if (nextChildIndex < 0 || nextChildIndex >= getChildCount()) {
            // Wraparound if reached head or end
            if (getSelectedPosition() == 0) {
                scrollToPosition(getAdapter().getItemCount() - 1);
                return null;
            } else if (getSelectedPosition() == getAdapter().getItemCount() - 1) {
                scrollToPosition(0);
                return null;
            }
            return focused;
        }
        View nextFocusedProgram = GuideUtils.findNextFocusedProgram(getChildAt(nextChildIndex),
                mFocusRangeLeft, mFocusRangeRight, mKeepCurrentProgramFocused);
        if (nextFocusedProgram != null) {
            nextFocusedProgram.getGlobalVisibleRect(mTempRect);
            mNextFocusByUpDown = nextFocusedProgram;

        } else {
            Log.w(TAG, "focusFind doesn't find proper focusable");
        }
        return nextFocusedProgram;
    }

    // Returned value is not the position of VerticalGridView. But it's the index of ViewGroup
    // among visible children.
    private int getFocusedChildIndex() {
        for (int i = 0; i < getChildCount(); ++i) {
            if (getChildAt(i).hasFocus()) {
                return i;
            }
        }
        return INVALID_INDEX;
    }

    private void updateUpDownFocusState(View focused, int direction) {
        mLastUpDownDirection = direction;
        int rightMostFocusablePosition = getRightMostFocusablePosition();
        Rect focusedRect = mTempRect;

        // In order to avoid from focusing small width item, we clip the position with
        // mostRightFocusablePosition.
        focused.getGlobalVisibleRect(focusedRect);
        mFocusRangeLeft = Math.min(mFocusRangeLeft, rightMostFocusablePosition);
        mFocusRangeRight = Math.min(mFocusRangeRight, rightMostFocusablePosition);
        focusedRect.left = Math.min(focusedRect.left, rightMostFocusablePosition);
        focusedRect.right = Math.min(focusedRect.right, rightMostFocusablePosition);

        if (focusedRect.left > mFocusRangeRight || focusedRect.right < mFocusRangeLeft) {
            Log.w(TAG, "The current focus is out of [mFocusRangeLeft, mFocusRangeRight]");
            mFocusRangeLeft = focusedRect.left;
            mFocusRangeRight = focusedRect.right;
            return;
        }
        mFocusRangeLeft = Math.max(mFocusRangeLeft, focusedRect.left);
        mFocusRangeRight = Math.min(mFocusRangeRight, focusedRect.right);
    }

    private void clearUpDownFocusState(View focus) {
        mLastUpDownDirection = 0;
        mFocusRangeLeft = 0;
        mFocusRangeRight = getRightMostFocusablePosition();
        mNextFocusByUpDown = null;
        // If focus is not a program item, drop focus to the current program when back to the grid
        mKeepCurrentProgramFocused = !(focus instanceof ProgramItemView)
                || GuideUtils.isCurrentProgram((ProgramItemView) focus);
    }

    private int getRightMostFocusablePosition() {
        if (!getGlobalVisibleRect(mTempRect)) {
            return Integer.MAX_VALUE;
        }
        return mTempRect.right - GuideUtils.convertMillisToPixel(FOCUS_AREA_RIGHT_MARGIN_MILLIS);
    }

    private int getFirstVisibleChildIndex() {
        final LayoutManager mLayoutManager = getLayoutManager();
        int top = mLayoutManager.getPaddingTop();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            int childTop = mLayoutManager.getDecoratedTop(childView);
            int childBottom = mLayoutManager.getDecoratedBottom(childView);
            if ((childTop + childBottom) / 2 > top) {
                return i;
            }
        }
        return -1;
    }

    private void updateInputLogo() {
        int childCount = getChildCount();
        if (childCount == 0) {
            return;
        }
        int firstVisibleChildIndex = getFirstVisibleChildIndex();
        if (firstVisibleChildIndex == -1) {
            return;
        }
        View childView = getChildAt(firstVisibleChildIndex);
        int childAdapterPosition = getChildAdapterPosition(childView);
        ((ProgramTableAdapter.ProgramRowViewHolder) getChildViewHolder(childView))
                .updateInputLogo(childAdapterPosition, true);
        for (int i = firstVisibleChildIndex + 1; i < childCount; i++) {
            childView = getChildAt(i);
            ((ProgramTableAdapter.ProgramRowViewHolder) getChildViewHolder(childView))
                    .updateInputLogo(childAdapterPosition, false);
            childAdapterPosition = getChildAdapterPosition(childView);
        }
    }
}
