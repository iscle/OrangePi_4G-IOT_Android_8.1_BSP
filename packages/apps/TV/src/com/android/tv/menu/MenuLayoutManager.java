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

package com.android.tv.menu;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.UiThread;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * A view that represents TV main menu.
 */
@UiThread
public class MenuLayoutManager {
    static final String TAG = "MenuLayoutManager";
    static final boolean DEBUG = false;

    // The visible duration of the title before it is hidden.
    private static final long TITLE_SHOW_DURATION_BEFORE_HIDDEN_MS = TimeUnit.SECONDS.toMillis(2);
    private static final int INVALID_POSITION = -1;

    private final MenuView mMenuView;
    private final List<MenuRow> mMenuRows = new ArrayList<>();
    private final List<MenuRowView> mMenuRowViews = new ArrayList<>();
    private final List<Integer> mRemovingRowViews = new ArrayList<>();
    private int mSelectedPosition = INVALID_POSITION;
    private int mPendingSelectedPosition = INVALID_POSITION;

    private final int mRowAlignFromBottom;
    private final int mRowContentsPaddingTop;
    private final int mRowContentsPaddingBottomMax;
    private final int mRowTitleTextDescenderHeight;
    private final int mMenuMarginBottomMin;
    private final int mRowTitleHeight;
    private final int mRowScrollUpAnimationOffset;

    private final long mRowAnimationDuration;
    private final long mOldContentsFadeOutDuration;
    private final long mCurrentContentsFadeInDuration;
    private final TimeInterpolator mFastOutSlowIn = new FastOutSlowInInterpolator();
    private final TimeInterpolator mFastOutLinearIn = new FastOutLinearInInterpolator();
    private final TimeInterpolator mLinearOutSlowIn = new LinearOutSlowInInterpolator();
    private AnimatorSet mAnimatorSet;
    private ObjectAnimator mTitleFadeOutAnimator;
    private final List<ViewPropertyValueHolder> mPropertyValuesAfterAnimation = new ArrayList<>();

    private TextView mTempTitleViewForOld;
    private TextView mTempTitleViewForCurrent;

    public MenuLayoutManager(Context context, MenuView menuView) {
        mMenuView = menuView;
        // Load dimensions
        Resources res = context.getResources();
        mRowAlignFromBottom = res.getDimensionPixelOffset(R.dimen.menu_row_align_from_bottom);
        mRowContentsPaddingTop = res.getDimensionPixelOffset(R.dimen.menu_row_contents_padding_top);
        mRowContentsPaddingBottomMax = res.getDimensionPixelOffset(
                R.dimen.menu_row_contents_padding_bottom_max);
        mRowTitleTextDescenderHeight = res.getDimensionPixelOffset(
                R.dimen.menu_row_title_text_descender_height);
        mMenuMarginBottomMin = res.getDimensionPixelOffset(R.dimen.menu_margin_bottom_min);
        mRowTitleHeight = res.getDimensionPixelSize(R.dimen.menu_row_title_height);
        mRowScrollUpAnimationOffset =
                res.getDimensionPixelOffset(R.dimen.menu_row_scroll_up_anim_offset);
        mRowAnimationDuration = res.getInteger(R.integer.menu_row_selection_anim_duration);
        mOldContentsFadeOutDuration = res.getInteger(
                R.integer.menu_previous_contents_fade_out_duration);
        mCurrentContentsFadeInDuration = res.getInteger(
                R.integer.menu_current_contents_fade_in_duration);
    }

    /**
     * Sets the menu rows and views.
     */
    public void setMenuRowsAndViews(List<MenuRow> menuRows, List<MenuRowView> menuRowViews) {
        mMenuRows.clear();
        mMenuRows.addAll(menuRows);
        mMenuRowViews.clear();
        mMenuRowViews.addAll(menuRowViews);
    }

    /**
     * Layouts main menu view.
     *
     * <p>Do not call this method directly. It's supposed to be called only by View.onLayout().
     */
    public void layout(int left, int top, int right, int bottom) {
        if (mAnimatorSet != null) {
            // Layout will be done after the animation ends.
            return;
        }

        int count = mMenuRowViews.size();
        MenuRowView currentView = mMenuRowViews.get(mSelectedPosition);
        if (currentView.getVisibility() == View.GONE) {
            // If the selected row is not visible, select the first visible row.
            int firstVisiblePosition = findNextVisiblePosition(INVALID_POSITION);
            if (firstVisiblePosition != INVALID_POSITION) {
                mSelectedPosition = firstVisiblePosition;
            } else {
                // No rows are visible.
                return;
            }
        }
        List<Rect> layouts = getViewLayouts(left, top, right, bottom);
        for (int i = 0; i < count; ++i) {
            Rect rect = layouts.get(i);
            if (rect != null) {
                currentView = mMenuRowViews.get(i);
                currentView.layout(rect.left, rect.top, rect.right, rect.bottom);
                if (DEBUG) dumpChildren("layout()");
            }
        }

        // If the contents view is INVISIBLE initially, it should be changed to GONE after layout.
        // See MenuRowView.onFinishInflate() for more information
        // TODO: Find a better way to resolve this issue..
        for (MenuRowView view : mMenuRowViews) {
            if (view.getVisibility() == View.VISIBLE
                    && view.getContentsView().getVisibility() == View.INVISIBLE) {
                view.onDeselected();
            }
        }

        if (mPendingSelectedPosition != INVALID_POSITION) {
            setSelectedPositionSmooth(mPendingSelectedPosition);
        }
    }

    private int findNextVisiblePosition(int start) {
        int count = mMenuRowViews.size();
        for (int i = start + 1; i < count; ++i) {
            if (mMenuRowViews.get(i).getVisibility() != View.GONE) {
                return i;
            }
        }
        return INVALID_POSITION;
    }

    private void dumpChildren(String prefix) {
        int position = 0;
        for (MenuRowView view : mMenuRowViews) {
            View title = view.getChildAt(0);
            View contents = view.getChildAt(1);
            Log.d(TAG, prefix + " position=" + position++
                    + " rowView={visiblility=" + view.getVisibility()
                    + ", alpha=" + view.getAlpha()
                    + ", translationY=" + view.getTranslationY()
                    + ", left=" + view.getLeft() + ", top=" + view.getTop()
                    + ", right=" + view.getRight() + ", bottom=" + view.getBottom()
                    + "}, title={visiblility=" + title.getVisibility()
                    + ", alpha=" + title.getAlpha()
                    + ", translationY=" + title.getTranslationY()
                    + ", left=" + title.getLeft() + ", top=" + title.getTop()
                    + ", right=" + title.getRight() + ", bottom=" + title.getBottom()
                    + "}, contents={visiblility=" + contents.getVisibility()
                    + ", alpha=" + contents.getAlpha()
                    + ", translationY=" + contents.getTranslationY()
                    + ", left=" + contents.getLeft() + ", top=" + contents.getTop()
                    + ", right=" + contents.getRight() + ", bottom=" + contents.getBottom()+ "}");
        }
    }

    /**
     * Checks if the view will take up space for the layout not.
     *
     * @param position The index of the menu row view in the list. This is not the index of the view
     * in the screen.
     * @param view The menu row view.
     * @param rowsToAdd The menu row views to be added in the next layout process.
     * @param rowsToRemove The menu row views to be removed in the next layout process.
     * @return {@code true} if the view will take up space for the layout, otherwise {@code false}.
     */
    private boolean isVisibleInLayout(int position, MenuRowView view, List<Integer> rowsToAdd,
            List<Integer> rowsToRemove) {
        // Checks if the view will be visible or not.
        return (view.getVisibility() != View.GONE && !rowsToRemove.contains(position))
                || rowsToAdd.contains(position);
    }

    /**
     * Calculates and returns a list of the layout bounds of the menu row views for the layout.
     *
     * @param left The left coordinate of the menu view.
     * @param top The top coordinate of the menu view.
     * @param right The right coordinate of the menu view.
     * @param bottom The bottom coordinate of the menu view.
     */
    private List<Rect> getViewLayouts(int left, int top, int right, int bottom) {
        return getViewLayouts(left, top, right, bottom, Collections.emptyList(),
                Collections.emptyList());
    }

    /**
     * Calculates and returns a list of the layout bounds of the menu row views for the layout. The
     * order of the bounds is the same as that of the menu row views. e.g. the second rectangle in
     * the list is for the second menu row view in the view list (not the second view in the
     * screen).
     *
     * <p>It predicts the layout bounds for the next layout process. Some views will be added or
     * removed in the layout, so they need to be considered here.
     *
     * @param left The left coordinate of the menu view.
     * @param top The top coordinate of the menu view.
     * @param right The right coordinate of the menu view.
     * @param bottom The bottom coordinate of the menu view.
     * @param rowsToAdd The menu row views to be added in the next layout process.
     * @param rowsToRemove The menu row views to be removed in the next layout process.
     * @return the layout bounds of the menu row views.
     */
    private List<Rect> getViewLayouts(int left, int top, int right, int bottom,
            List<Integer> rowsToAdd, List<Integer> rowsToRemove) {
        // The coordinates should be relative to the parent.
        int relativeLeft = 0;
        int relateiveRight = right - left;
        int relativeBottom = bottom - top;

        List<Rect> layouts = new ArrayList<>();
        int count = mMenuRowViews.size();
        MenuRowView selectedView = mMenuRowViews.get(mSelectedPosition);
        int rowTitleHeight = selectedView.getTitleView().getMeasuredHeight();
        int rowContentsHeight = selectedView.getPreferredContentsHeight();
        // Calculate for the selected row first.
        // The distance between the bottom of the screen and the vertical center of the contents
        // should be kept fixed. For more information, please see the redlines.
        int childTop = relativeBottom - mRowAlignFromBottom - rowContentsHeight / 2
                - mRowContentsPaddingTop - rowTitleHeight;
        int childBottom = relativeBottom;
        int position = mSelectedPosition + 1;
        for (; position < count; ++position) {
            // Find and layout the next row to calculate the bottom line of the selected row.
            MenuRowView nextView = mMenuRowViews.get(position);
            if (isVisibleInLayout(position, nextView, rowsToAdd, rowsToRemove)) {
                int nextTitleTopMax = relativeBottom - mMenuMarginBottomMin - rowTitleHeight
                        + mRowTitleTextDescenderHeight;
                int childBottomMax = relativeBottom - mRowAlignFromBottom + rowContentsHeight / 2
                        + mRowContentsPaddingBottomMax - rowTitleHeight;
                childBottom = Math.min(nextTitleTopMax, childBottomMax);
                layouts.add(new Rect(relativeLeft, childBottom, relateiveRight, relativeBottom));
                break;
            } else {
                // null means that the row is GONE.
                layouts.add(null);
            }
        }
        layouts.add(0, new Rect(relativeLeft, childTop, relateiveRight, childBottom));
        // Layout the previous rows.
        for (int i = mSelectedPosition - 1; i >= 0; --i) {
            MenuRowView view = mMenuRowViews.get(i);
            if (isVisibleInLayout(i, view, rowsToAdd, rowsToRemove)) {
                childTop -= mRowTitleHeight;
                childBottom = childTop + rowTitleHeight;
                layouts.add(0, new Rect(relativeLeft, childTop, relateiveRight, childBottom));
            } else {
                layouts.add(0, null);
            }
        }
        // Move all the next rows to the below of the screen.
        childTop = relativeBottom;
        for (++position; position < count; ++position) {
            MenuRowView view = mMenuRowViews.get(position);
            if (isVisibleInLayout(position, view, rowsToAdd, rowsToRemove)) {
                childBottom = childTop + rowTitleHeight;
                layouts.add(new Rect(relativeLeft, childTop, relateiveRight, childBottom));
                childTop += mRowTitleHeight;
            } else {
                layouts.add(null);
            }
        }
        return layouts;
    }

    /**
     * Move the current selection to the given {@code position}.
     */
    public void setSelectedPosition(int position) {
        if (DEBUG) {
            Log.d(TAG, "setSelectedPosition(position=" + position + ") {previousPosition="
                    + mSelectedPosition + "}");
        }
        if (mSelectedPosition == position) {
            return;
        }
        boolean indexValid = Utils.isIndexValid(mMenuRowViews, position);
        SoftPreconditions.checkArgument(indexValid, TAG, "position " + position);
        if (!indexValid) {
            return;
        }
        MenuRow row = mMenuRows.get(position);
        if (!row.isVisible()) {
            Log.e(TAG, "Selecting invisible row: " + position);
            return;
        }
        if (Utils.isIndexValid(mMenuRowViews, mSelectedPosition)) {
            mMenuRowViews.get(mSelectedPosition).onDeselected();
        }
        mSelectedPosition = position;
        mPendingSelectedPosition = INVALID_POSITION;
        if (Utils.isIndexValid(mMenuRowViews, mSelectedPosition)) {
            mMenuRowViews.get(mSelectedPosition).onSelected(false);
        }
        if (mMenuView.getVisibility() == View.VISIBLE) {
            // Request focus after the new contents view shows up.
            mMenuView.requestFocus();
            // Adjust the position of the selected row.
            mMenuView.requestLayout();
        }
    }

    /**
     * Move the current selection to the given {@code position} with animation.
     * The animation specification is included in http://b/21069476
     */
    public void setSelectedPositionSmooth(final int position) {
        if (DEBUG) {
            Log.d(TAG, "setSelectedPositionSmooth(position=" + position + ") {previousPosition="
                    + mSelectedPosition + "}");
        }
        if (mMenuView.getVisibility() != View.VISIBLE) {
            setSelectedPosition(position);
            return;
        }
        if (mSelectedPosition == position) {
            return;
        }
        boolean oldIndexValid = Utils.isIndexValid(mMenuRowViews, mSelectedPosition);
        SoftPreconditions
                .checkState(oldIndexValid, TAG, "No previous selection: " + mSelectedPosition);
        if (!oldIndexValid) {
            return;
        }
        boolean newIndexValid = Utils.isIndexValid(mMenuRowViews, position);
        SoftPreconditions.checkArgument(newIndexValid, TAG, "position " + position);
        if (!newIndexValid) {
            return;
        }
        MenuRow row = mMenuRows.get(position);
        if (!row.isVisible()) {
            Log.e(TAG, "Moving to the invisible row: " + position);
            return;
        }
        if (mAnimatorSet != null) {
            // Do not cancel the animation here. The property values should be set to the end values
            // when the animation finishes.
            mAnimatorSet.end();
        }
        if (mTitleFadeOutAnimator != null) {
            // Cancel the animation instead of ending it in order that the title animation starts
            // again from the intermediate state.
            mTitleFadeOutAnimator.cancel();
        }
        if (DEBUG) dumpChildren("startRowAnimation()");

        // Show the children of the next row.
        final MenuRowView currentView = mMenuRowViews.get(position);
        TextView currentTitleView = currentView.getTitleView();
        View currentContentsView = currentView.getContentsView();
        currentTitleView.setVisibility(View.VISIBLE);
        currentContentsView.setVisibility(View.VISIBLE);
        if (currentView instanceof PlayControlsRowView) {
            ((PlayControlsRowView) currentView).onPreselected();
        }
        // When contents view's visibility is gone, layouting might be delayed until it's shown and
        // thus cause onBindViewHolder() and menu action updating occurs in front of users' sight.
        // Therefore we call requestLayout() here if there are pending adapter updates.
        if (currentContentsView instanceof RecyclerView
                && ((RecyclerView) currentContentsView).hasPendingAdapterUpdates()) {
            currentContentsView.requestLayout();
            mPendingSelectedPosition = position;
            return;
        }
        final int oldPosition = mSelectedPosition;
        mSelectedPosition = position;
        mPendingSelectedPosition = INVALID_POSITION;
        // Request focus after the new contents view shows up.
        mMenuView.requestFocus();
        if (mTempTitleViewForOld == null) {
            // Initialize here because we don't know when the views are inflated.
            mTempTitleViewForOld =
                    (TextView) mMenuView.findViewById(R.id.temp_title_for_old);
            mTempTitleViewForCurrent =
                    (TextView) mMenuView.findViewById(R.id.temp_title_for_current);
        }

        // Animations.
        mPropertyValuesAfterAnimation.clear();
        List<Animator> animators = new ArrayList<>();
        boolean scrollDown = position > oldPosition;
        List<Rect> layouts = getViewLayouts(mMenuView.getLeft(), mMenuView.getTop(),
                mMenuView.getRight(), mMenuView.getBottom());

        // Old row.
        MenuRow oldRow = mMenuRows.get(oldPosition);
        final MenuRowView oldView = mMenuRowViews.get(oldPosition);
        View oldContentsView = oldView.getContentsView();
        // Old contents view.
        animators.add(createAlphaAnimator(oldContentsView, 1.0f, 0.0f, 1.0f, mLinearOutSlowIn)
                .setDuration(mOldContentsFadeOutDuration));
        final TextView oldTitleView = oldView.getTitleView();
        setTempTitleView(mTempTitleViewForOld, oldTitleView);
        Rect oldLayoutRect = layouts.get(oldPosition);
        if (scrollDown) {
            // Old title view.
            if (oldRow.hideTitleWhenSelected() && oldTitleView.getVisibility() != View.VISIBLE) {
                // This case is not included in the animation specification.
                mTempTitleViewForOld.setScaleX(1.0f);
                mTempTitleViewForOld.setScaleY(1.0f);
                animators.add(createAlphaAnimator(mTempTitleViewForOld, 0.0f,
                        oldView.getTitleViewAlphaDeselected(), mFastOutLinearIn));
                int offset = oldLayoutRect.top - mTempTitleViewForOld.getTop();
                animators.add(createTranslationYAnimator(mTempTitleViewForOld,
                        offset + mRowScrollUpAnimationOffset, offset));
            } else {
                animators.add(createScaleXAnimator(mTempTitleViewForOld,
                        oldView.getTitleViewScaleSelected(), 1.0f));
                animators.add(createScaleYAnimator(mTempTitleViewForOld,
                        oldView.getTitleViewScaleSelected(), 1.0f));
                animators.add(createAlphaAnimator(mTempTitleViewForOld, oldTitleView.getAlpha(),
                        oldView.getTitleViewAlphaDeselected(), mLinearOutSlowIn));
                animators.add(createTranslationYAnimator(mTempTitleViewForOld, 0,
                        oldLayoutRect.top - mTempTitleViewForOld.getTop()));
            }
            oldTitleView.setAlpha(oldView.getTitleViewAlphaDeselected());
            oldTitleView.setVisibility(View.INVISIBLE);
        } else {
            Rect currentLayoutRect = new Rect(layouts.get(position));
            // Old title view.
            // The move distance in the specification is 32dp(mRowScrollUpAnimationOffset).
            // But if the height of the upper row is small, the upper row will move down a lot. In
            // this case, this row needs to move more than the specification to avoid the overlap of
            // the two titles.
            // The maximum is to the top of the start position of mTempTitleViewForOld.
            int distanceCurrentTitle = currentLayoutRect.top - currentView.getTop();
            int distance = Math.max(mRowScrollUpAnimationOffset, distanceCurrentTitle);
            int distanceToTopOfSecondTitle = oldLayoutRect.top - mRowScrollUpAnimationOffset
                    - oldView.getTop();
            animators.add(createTranslationYAnimator(oldTitleView, 0.0f,
                    Math.min(distance, distanceToTopOfSecondTitle)));
            animators.add(createAlphaAnimator(oldTitleView, 1.0f, 0.0f, 1.0f, mLinearOutSlowIn)
                    .setDuration(mOldContentsFadeOutDuration));
            animators.add(createScaleXAnimator(oldTitleView,
                    oldView.getTitleViewScaleSelected(), 1.0f));
            animators.add(createScaleYAnimator(oldTitleView,
                    oldView.getTitleViewScaleSelected(), 1.0f));
            mTempTitleViewForOld.setScaleX(1.0f);
            mTempTitleViewForOld.setScaleY(1.0f);
            animators.add(createAlphaAnimator(mTempTitleViewForOld, 0.0f,
                    oldView.getTitleViewAlphaDeselected(), mFastOutLinearIn));
            int offset = oldLayoutRect.top - mTempTitleViewForOld.getTop();
            animators.add(createTranslationYAnimator(mTempTitleViewForOld,
                    offset - mRowScrollUpAnimationOffset, offset));
        }
        // Current row.
        Rect currentLayoutRect = new Rect(layouts.get(position));
        currentContentsView.setAlpha(0.0f);
        if (scrollDown) {
            // Current title view.
            setTempTitleView(mTempTitleViewForCurrent, currentTitleView);
            // The move distance in the specification is 32dp(mRowScrollUpAnimationOffset).
            // But if the height of the upper row is small, the upper row will move up a lot. In
            // this case, this row needs to start the move from more than the specification to avoid
            // the overlap of the two titles.
            // The maximum is to the top of the end position of mTempTitleViewForCurrent.
            int distanceOldTitle = oldView.getTop() - oldLayoutRect.top;
            int distance = Math.max(mRowScrollUpAnimationOffset, distanceOldTitle);
            int distanceTopOfSecondTitle = currentView.getTop() - mRowScrollUpAnimationOffset
                    - currentLayoutRect.top;
            animators.add(createTranslationYAnimator(currentTitleView,
                    Math.min(distance, distanceTopOfSecondTitle), 0.0f));
            currentView.setTop(currentLayoutRect.top);
            ObjectAnimator animator = createAlphaAnimator(currentTitleView, 0.0f, 1.0f,
                    mFastOutLinearIn).setDuration(mCurrentContentsFadeInDuration);
            animator.setStartDelay(mOldContentsFadeOutDuration);
            currentTitleView.setAlpha(0.0f);
            animators.add(animator);
            animators.add(createScaleXAnimator(currentTitleView, 1.0f,
                    currentView.getTitleViewScaleSelected()));
            animators.add(createScaleYAnimator(currentTitleView, 1.0f,
                    currentView.getTitleViewScaleSelected()));
            animators.add(createTranslationYAnimator(mTempTitleViewForCurrent, 0.0f,
                    -mRowScrollUpAnimationOffset));
            animators.add(createAlphaAnimator(mTempTitleViewForCurrent,
                    currentView.getTitleViewAlphaDeselected(), 0, mLinearOutSlowIn));
            // Current contents view.
            animators.add(createTranslationYAnimator(currentContentsView,
                    mRowScrollUpAnimationOffset, 0.0f));
            animator = createAlphaAnimator(currentContentsView, 0.0f, 1.0f, mFastOutLinearIn)
                    .setDuration(mCurrentContentsFadeInDuration);
            animator.setStartDelay(mOldContentsFadeOutDuration);
            animators.add(animator);
        } else {
            currentView.setBottom(currentLayoutRect.bottom);
            // Current title view.
            int currentViewOffset = currentLayoutRect.top - currentView.getTop();
            animators.add(createTranslationYAnimator(currentTitleView, 0, currentViewOffset));
            animators.add(createAlphaAnimator(currentTitleView,
                    currentView.getTitleViewAlphaDeselected(), 1.0f, mFastOutSlowIn));
            animators.add(createScaleXAnimator(currentTitleView, 1.0f,
                    currentView.getTitleViewScaleSelected()));
            animators.add(createScaleYAnimator(currentTitleView, 1.0f,
                    currentView.getTitleViewScaleSelected()));
            // Current contents view.
            animators.add(createTranslationYAnimator(currentContentsView,
                    currentViewOffset - mRowScrollUpAnimationOffset, currentViewOffset));
            ObjectAnimator animator = createAlphaAnimator(currentContentsView, 0.0f, 1.0f,
                    mFastOutLinearIn).setDuration(mCurrentContentsFadeInDuration);
            animator.setStartDelay(mOldContentsFadeOutDuration);
            animators.add(animator);
        }
        // Next row.
        int nextPosition;
        if (scrollDown) {
            nextPosition = findNextVisiblePosition(position);
            if (nextPosition != INVALID_POSITION) {
                MenuRowView nextView = mMenuRowViews.get(nextPosition);
                Rect nextLayoutRect = layouts.get(nextPosition);
                animators.add(createTranslationYAnimator(nextView,
                        nextLayoutRect.top + mRowScrollUpAnimationOffset - nextView.getTop(),
                        nextLayoutRect.top - nextView.getTop()));
                animators.add(createAlphaAnimator(nextView, 0.0f, 1.0f, mFastOutLinearIn));
            }
        } else {
            nextPosition = findNextVisiblePosition(oldPosition);
            if (nextPosition != INVALID_POSITION) {
                MenuRowView nextView = mMenuRowViews.get(nextPosition);
                animators.add(createTranslationYAnimator(nextView, 0, mRowScrollUpAnimationOffset));
                animators.add(createAlphaAnimator(nextView,
                        nextView.getTitleViewAlphaDeselected(), 0.0f, 1.0f, mLinearOutSlowIn));
            }
        }
        // Other rows.
        int count = mMenuRowViews.size();
        for (int i = 0; i < count; ++i) {
            MenuRowView view = mMenuRowViews.get(i);
            if (view.getVisibility() == View.VISIBLE && i != oldPosition && i != position
                    && i != nextPosition) {
                Rect rect = layouts.get(i);
                animators.add(createTranslationYAnimator(view, 0, rect.top - view.getTop()));
            }
        }
        // Run animation.
        final List<ViewPropertyValueHolder> propertyValuesAfterAnimation = new ArrayList<>();
        propertyValuesAfterAnimation.addAll(mPropertyValuesAfterAnimation);
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animators);
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (DEBUG) dumpChildren("onRowAnimationEndBefore");
                mAnimatorSet = null;
                // The property values which are different from the end values and need to be
                // changed after the animation are set here.
                // e.g. setting translationY to 0, alpha of the contents view to 1.
                for (ViewPropertyValueHolder holder : propertyValuesAfterAnimation) {
                    holder.property.set(holder.view, holder.value);
                }
                oldView.onDeselected();
                currentView.onSelected(true);
                mTempTitleViewForOld.setVisibility(View.GONE);
                mTempTitleViewForCurrent.setVisibility(View.GONE);
                layout(mMenuView.getLeft(), mMenuView.getTop(), mMenuView.getRight(),
                        mMenuView.getBottom());
                if (DEBUG) dumpChildren("onRowAnimationEndAfter");

                MenuRow currentRow = mMenuRows.get(position);
                if (currentRow.hideTitleWhenSelected()) {
                    View titleView = mMenuRowViews.get(position).getTitleView();
                    mTitleFadeOutAnimator = createAlphaAnimator(titleView, titleView.getAlpha(),
                            0.0f, mLinearOutSlowIn);
                    mTitleFadeOutAnimator.setStartDelay(TITLE_SHOW_DURATION_BEFORE_HIDDEN_MS);
                    mTitleFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
                        private boolean mCanceled;

                        @Override
                        public void onAnimationCancel(Animator animator) {
                            mCanceled = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            mTitleFadeOutAnimator = null;
                            if (!mCanceled) {
                                mMenuRowViews.get(position).onSelected(false);
                            }
                        }
                    });
                    mTitleFadeOutAnimator.start();
                }
            }
        });
        mAnimatorSet.start();
        if (DEBUG) dumpChildren("startedRowAnimation()");
    }

    private void setTempTitleView(TextView dest, TextView src) {
        dest.setVisibility(View.VISIBLE);
        dest.setText(src.getText());
        dest.setTranslationY(0.0f);
        if (src.getVisibility() == View.VISIBLE) {
            dest.setAlpha(src.getAlpha());
            dest.setScaleX(src.getScaleX());
            dest.setScaleY(src.getScaleY());
        } else {
            dest.setAlpha(0.0f);
            dest.setScaleX(1.0f);
            dest.setScaleY(1.0f);
        }
        View parent = (View) src.getParent();
        dest.setLeft(src.getLeft() + parent.getLeft());
        dest.setRight(src.getRight() + parent.getLeft());
        dest.setTop(src.getTop() + parent.getTop());
        dest.setBottom(src.getBottom() + parent.getTop());
    }

    /**
     * Called when the menu row information is updated. The add/remove animation of the row views
     * will be started.
     *
     * <p>Note that the current row should not be removed.
     */
    public void onMenuRowUpdated() {
        if (mMenuView.getVisibility() != View.VISIBLE) {
            int count = mMenuRowViews.size();
            for (int i = 0; i < count; ++i) {
                mMenuRowViews.get(i)
                        .setVisibility(mMenuRows.get(i).isVisible() ? View.VISIBLE : View.GONE);
            }
            return;
        }

        List<Integer> addedRowViews = new ArrayList<>();
        List<Integer> removedRowViews = new ArrayList<>();
        Map<Integer, Integer> offsetsToMove = new HashMap<>();
        int added = 0;
        for (int i = mSelectedPosition - 1; i >= 0; --i) {
            MenuRow row = mMenuRows.get(i);
            MenuRowView view = mMenuRowViews.get(i);
            if (row.isVisible() && (view.getVisibility() == View.GONE
                    || mRemovingRowViews.contains(i))) {
                // Removing rows are still VISIBLE.
                addedRowViews.add(i);
                ++added;
            } else if (!row.isVisible() && view.getVisibility() == View.VISIBLE) {
                removedRowViews.add(i);
                --added;
            } else if (added != 0) {
                offsetsToMove.put(i, -added);
            }
        }
        added = 0;
        int count = mMenuRowViews.size();
        for (int i = mSelectedPosition + 1; i < count; ++i) {
            MenuRow row = mMenuRows.get(i);
            MenuRowView view = mMenuRowViews.get(i);
            if (row.isVisible() && (view.getVisibility() == View.GONE
                    || mRemovingRowViews.contains(i))) {
                // Removing rows are still VISIBLE.
                addedRowViews.add(i);
                ++added;
            } else if (!row.isVisible() && view.getVisibility() == View.VISIBLE) {
                removedRowViews.add(i);
                --added;
            } else if (added != 0) {
                offsetsToMove.put(i, added);
            }
        }
        if (addedRowViews.size() == 0 && removedRowViews.size() == 0) {
            return;
        }

        if (mAnimatorSet != null) {
            // Do not cancel the animation here. The property values should be set to the end values
            // when the animation finishes.
            mAnimatorSet.end();
        }
        if (mTitleFadeOutAnimator != null) {
            mTitleFadeOutAnimator.end();
        }
        mPropertyValuesAfterAnimation.clear();
        List<Animator> animators = new ArrayList<>();
        List<Rect> layouts = getViewLayouts(mMenuView.getLeft(), mMenuView.getTop(),
                mMenuView.getRight(), mMenuView.getBottom(), addedRowViews, removedRowViews);
        for (int position : addedRowViews) {
            MenuRowView view = mMenuRowViews.get(position);
            view.setVisibility(View.VISIBLE);
            Rect rect = layouts.get(position);
            // TODO: The animation is not visible when it is shown for the first time. Need to find
            // a better way to resolve this issue.
            view.layout(rect.left, rect.top, rect.right, rect.bottom);
            View titleView = view.getTitleView();
            MarginLayoutParams params = (MarginLayoutParams) titleView.getLayoutParams();
            titleView.layout(view.getPaddingLeft() + params.leftMargin,
                    view.getPaddingTop() + params.topMargin,
                    rect.right - rect.left - view.getPaddingRight() - params.rightMargin,
                    rect.bottom - rect.top - view.getPaddingBottom() - params.bottomMargin);
            animators.add(createAlphaAnimator(view, 0.0f, 1.0f, mFastOutLinearIn));
        }
        for (int position : removedRowViews) {
            MenuRowView view = mMenuRowViews.get(position);
            animators.add(createAlphaAnimator(view, 1.0f, 0.0f, 1.0f, mLinearOutSlowIn));
        }
        for (Entry<Integer, Integer> entry : offsetsToMove.entrySet()) {
            MenuRowView view = mMenuRowViews.get(entry.getKey());
            animators.add(createTranslationYAnimator(view, 0, entry.getValue() * mRowTitleHeight));
        }
        // Run animation.
        final List<ViewPropertyValueHolder> propertyValuesAfterAnimation = new ArrayList<>();
        propertyValuesAfterAnimation.addAll(mPropertyValuesAfterAnimation);
        mRemovingRowViews.clear();
        mRemovingRowViews.addAll(removedRowViews);
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(animators);
        mAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimatorSet = null;
                // The property values which are different from the end values and need to be
                // changed after the animation are set here.
                // e.g. setting translationY to 0, alpha of the contents view to 1.
                for (ViewPropertyValueHolder holder : propertyValuesAfterAnimation) {
                    holder.property.set(holder.view, holder.value);
                }
                for (int position : mRemovingRowViews) {
                    mMenuRowViews.get(position).setVisibility(View.GONE);
                }
                layout(mMenuView.getLeft(), mMenuView.getTop(), mMenuView.getRight(),
                        mMenuView.getBottom());
            }
        });
        mAnimatorSet.start();
        if (DEBUG) dumpChildren("onMenuRowUpdated()");
    }

    private ObjectAnimator createTranslationYAnimator(View view, float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, from, to);
        animator.setDuration(mRowAnimationDuration);
        animator.setInterpolator(mFastOutSlowIn);
        mPropertyValuesAfterAnimation.add(new ViewPropertyValueHolder(View.TRANSLATION_Y, view, 0));
        return animator;
    }

    private ObjectAnimator createAlphaAnimator(View view, float from, float to,
            TimeInterpolator interpolator) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, from, to);
        animator.setDuration(mRowAnimationDuration);
        animator.setInterpolator(interpolator);
        return animator;
    }

    private ObjectAnimator createAlphaAnimator(View view, float from, float to, float end,
            TimeInterpolator interpolator) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.ALPHA, from, to);
        animator.setDuration(mRowAnimationDuration);
        animator.setInterpolator(interpolator);
        mPropertyValuesAfterAnimation.add(new ViewPropertyValueHolder(View.ALPHA, view, end));
        return animator;
    }

    private ObjectAnimator createScaleXAnimator(View view, float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.SCALE_X, from, to);
        animator.setDuration(mRowAnimationDuration);
        animator.setInterpolator(mFastOutSlowIn);
        return animator;
    }

    private ObjectAnimator createScaleYAnimator(View view, float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, View.SCALE_Y, from, to);
        animator.setDuration(mRowAnimationDuration);
        animator.setInterpolator(mFastOutSlowIn);
        return animator;
    }

    /**
     * Returns the current position.
     */
    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    private static final class ViewPropertyValueHolder {
        public final Property<View, Float> property;
        public final View view;
        public final float value;

        public ViewPropertyValueHolder(Property<View, Float> property, View view, float value) {
            this.property = property;
            this.view = view;
            this.value = value;
        }
    }

    /**
     * Called when the menu becomes visible.
     */
    public void onMenuShow() {
    }

    /**
     * Called when the menu becomes hidden.
     */
    public void onMenuHide() {
        if (mAnimatorSet != null) {
            mAnimatorSet.end();
            mAnimatorSet = null;
        }
        // Should be finished after the animator set.
        if (mTitleFadeOutAnimator != null) {
            mTitleFadeOutAnimator.end();
            mTitleFadeOutAnimator = null;
        }
    }
}
