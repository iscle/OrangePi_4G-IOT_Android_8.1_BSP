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

package com.android.tv.settings.widget;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Adapter;
import android.widget.AdapterView;

import com.android.tv.settings.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable AdapterView, similar to {@link android.widget.Gallery}. Features include:
 * <p>
 * Supports "expandable" views by supplying a Adapter that implements
 * {@link ScrollAdapter#getExpandAdapter()}. Generally you could see two expanded views at most: one
 * fade in, one fade out.
 * <p>
 * Supports {@link #HORIZONTAL} and {@link #VERTICAL} set by {@link #setOrientation(int)}.
 * So you could have a vertical ScrollAdapterView with a nested expanding Horizontal ScrollAdapterView.
 * <p>
 * Supports Grid view style, see {@link #setGridSetting(int)}.
 * <p>
 * Supports Different strategies of scrolling viewport, see
 * {@link ScrollController#SCROLL_CENTER_IN_MIDDLE},
 * {@link ScrollController#SCROLL_CENTER_FIXED}, and
 * {@link ScrollController#SCROLL_CENTER_FIXED_PERCENT}.
 * Also take a look of {@link #adjustSystemScrollPos()} for better understanding how Center
 * is translated to android View scroll position.
 * <p>
 * Expandable items animation is based on distance to the center. Motivation behind not using two
 * time based animations for focusing/onfocusing is that in a fast scroll, there is no better way to
 * synchronize these two animations with scroller animation; so you will end up with situation that
 * scale animated item cannot be kept in the center because scroll animation is too fast/too slow.
 * By using distance to the scroll center, the animation of focus/unfocus will be accurately synced
 * with scroller animation. {@link #setLowItemTransform(Animator)} transforms items that are left or
 * up to scroll center position; {@link #setHighItemTransform(Animator)} transforms items that are
 * right or down to the scroll center position. It's recommended to use xml resource ref
 * "highItemTransform" and "lowItemTransform" attributes to load the animation from xml. The
 * animation duration which android by default is a duration of milliseconds is interpreted as dip
 * to the center. Here is an example that scales the center item to "1.2" of original size, any item
 * far from 60dip to scroll center has normal scale (scale = 1):
 * <pre>{@code
 * <set xmlns:android="http://schemas.android.com/apk/res/android" >
 *   <objectAnimator
 *       android:startOffset="0"
 *       android:duration="60"
 *       android:valueFrom="1.2"
 *       android:valueTo="1"
 *       android:valueType="floatType"
 *       android:propertyName="scaleX" />
 *   <objectAnimator
 *       android:startOffset="0"
 *       android:duration="60"
 *       android:valueFrom="1.2"
 *       android:valueTo="1"
 *       android:valueType="floatType"
 *       android:propertyName="scaleY"/>
 * </set>
 * } </pre>
 * When using an animation that expands the selected item room has to be made in the view for
 * the scale animation. To accomplish this set right/left and/or top/bottom padding values
 * for the ScrollAdapterView and also set its clipToPadding value to false. Another option is
 * to include padding in the item view itself.
 * <p>
 * Expanded items animation uses "normal" animation: duration is duration. Use xml attribute
 * expandedItemInAnim and expandedItemOutAnim for animation. A best practice is specify startOffset
 * for expandedItemInAnim to avoid showing half loaded expanded items during a fast scroll of
 * expandable items.
 */
public final class ScrollAdapterView extends AdapterView<Adapter> {

    /** Callback interface for changing state of selected item */
    public static interface OnItemChangeListener {
        /**
         * In contrast to standard onFocusChange, the event is fired only when scrolling stops
         * @param view the view focusing to
         * @param position index in ScrollAdapter
         * @param targetCenter final center position of view to the left edge of ScrollAdapterView
         */
        public void onItemSelected(View view, int position, int targetCenter);
    }

    /**
     * Callback interface when there is scrolling happened, this function is called before
     * applying transformations ({@link ScrollAdapterTransform}).  This listener can be a
     * replacement of {@link ScrollAdapterTransform}.  The difference is that this listener
     * is called once when scroll position changes, {@link ScrollAdapterTransform} is called
     * on each child view.
     */
    public static interface OnScrollListener {
        /**
         * @param view the view focusing to
         * @param position index in ScrollAdapter
         * @param mainPosition position in the main axis 0(inclusive) ~ 1(exclusive)
         * @param secondPosition position in the second axis 0(inclusive) ~ 1(exclusive)
         */
        public void onScrolled(View view, int position, float mainPosition, float secondPosition);
    }

    private static final String TAG = "ScrollAdapterView";

    private static final boolean DBG = false;
    private static final boolean DEBUG_FOCUS = false;

    private static final int MAX_RECYCLED_VIEWS = 10;
    private static final int MAX_RECYCLED_EXPANDED_VIEWS = 3;

    // search range for stable id, see {@link #heuristicGetPersistentIndex()}
    private static final int SEARCH_ID_RANGE = 30;

    /**
     * {@link ScrollAdapterView} fills horizontally
     */
    public static final int HORIZONTAL = 0;

    /**
     * {@link ScrollAdapterView} fills vertically
     */
    public static final int VERTICAL = 1;

    /** calculate number of items on second axis by "parentSize / childSize" */
    public static final int GRID_SETTING_AUTO = 0;
    /** single item on second axis (i.e. not a grid view) */
    public static final int GRID_SETTING_SINGLE = 1;

    private int mOrientation = HORIZONTAL;

    /** saved measuredSpec to pass to child views */
    private int mMeasuredSpec = -1;

    /** the Adapter used to create views */
    private ScrollAdapter mAdapter;
    private ScrollAdapterCustomSize mAdapterCustomSize;
    private ScrollAdapterCustomAlign mAdapterCustomAlign;
    private int mSelectedSize;

    // flag that we have made initial selection during refreshing ScrollAdapterView
    private boolean mMadeInitialSelection = false;

    /** allow animate expanded size change when Scroller is stopped */
    private boolean mAnimateLayoutChange = true;

    private static class RecycledViews {
        List<View>[] mViews;
        final int mMaxRecycledViews;
        ScrollAdapterBase mAdapter;

        RecycledViews(int max) {
            mMaxRecycledViews = max;
        }

        void updateAdapter(ScrollAdapterBase adapter) {
            if (adapter != null) {
                int typeCount = adapter.getViewTypeCount();
                if (mViews == null || typeCount != mViews.length) {
                    mViews = new List[typeCount];
                    for (int i = 0; i < typeCount; i++) {
                        mViews[i] = new ArrayList<>();
                    }
                }
            }
            mAdapter = adapter;
        }

        void recycleView(View child, int type) {
            if (mAdapter != null) {
                mAdapter.viewRemoved(child);
            }
            if (mViews != null && type >=0 && type < mViews.length
                    && mViews[type].size() < mMaxRecycledViews) {
                mViews[type].add(child);
            }
        }

        View getView(int type) {
            if (mViews != null && type >= 0 && type < mViews.length) {
                List<View> array = mViews[type];
                return array.size() > 0 ? array.remove(array.size() - 1) : null;
            }
            return null;
        }
    }

    private final RecycledViews mRecycleViews = new RecycledViews(MAX_RECYCLED_VIEWS);

    private final RecycledViews mRecycleExpandedViews =
            new RecycledViews(MAX_RECYCLED_EXPANDED_VIEWS);

    /** exclusive index of view on the left */
    private int mLeftIndex;
    /** exclusive index of view on the right */
    private int mRightIndex;

    /** space between two items */
    private int mSpace;
    private int mSpaceLow;
    private int mSpaceHigh;

    private int mGridSetting = GRID_SETTING_SINGLE;
    /** effective number of items on 2nd axis, calculated in {@link #onMeasure} */
    private int mItemsOnOffAxis;

    /** maintains the scroller information */
    private final ScrollController mScroll;

    private final ArrayList<OnItemChangeListener> mOnItemChangeListeners = new ArrayList<>();
    private final ArrayList<OnScrollListener> mOnScrollListeners = new ArrayList<>();

    private final static boolean DEFAULT_NAVIGATE_OUT_ALLOWED = true;
    private final static boolean DEFAULT_NAVIGATE_OUT_OF_OFF_AXIS_ALLOWED = true;

    private final static boolean DEFAULT_NAVIGATE_IN_ANIMATION_ALLOWED = true;

    final class ExpandableChildStates extends ViewsStateBundle {
        ExpandableChildStates() {
            super(SAVE_NO_CHILD, 0);
        }
        @Override
        protected void saveVisibleViewsUnchecked() {
            for (int i = firstExpandableIndex(), last = lastExpandableIndex(); i < last; i++) {
                saveViewUnchecked(getChildAt(i), getAdapterIndex(i));
            }
        }
    }
    final class ExpandedChildStates extends ViewsStateBundle {
        ExpandedChildStates() {
            super(SAVE_LIMITED_CHILD, SAVE_LIMITED_CHILD_DEFAULT_VALUE);
        }
        @Override
        protected void saveVisibleViewsUnchecked() {
            for (int i = 0, size = mExpandedViews.size(); i < size; i++) {
                ExpandedView v = mExpandedViews.get(i);
                saveViewUnchecked(v.expandedView, v.index);
            }
        }
    }

    private static class ChildViewHolder {
        final int mItemViewType;
        int mMaxSize; // max size in mainAxis of the same offaxis
        int mExtraSpaceLow; // extra space added before the view
        float mLocationInParent; // temp variable used in animating expanded view size change
        float mLocation; // temp variable used in animating expanded view size change
        int mScrollCenter; // cached scroll center

        ChildViewHolder(int t) {
            mItemViewType = t;
        }
    }

    /**
     * set in {@link #onRestoreInstanceState(Parcelable)} which triggers a re-layout
     * and ScrollAdapterView restores states in {@link #onLayout}
     */
    private AdapterViewState mLoadingState;

    /** saves all expandable child states */
    final private ExpandableChildStates mExpandableChildStates = new ExpandableChildStates();

    /** saves all expanded child states */
    final private ExpandedChildStates mExpandedChildStates = new ExpandedChildStates();

    private ScrollAdapterTransform mItemTransform;

    /** flag for data changed, {@link #onLayout} will cleaning the whole view */
    private boolean mDataSetChangedFlag;

    // current selected view adapter index, this is the final position to scroll to
    private int mSelectedIndex;

    private static class ScrollInfo {
        int index;
        long id;
        float mainPos;
        float secondPos;
        int viewLocation;
        ScrollInfo() {
            clear();
        }
        boolean isValid() {
            return index >= 0;
        }
        void clear() {
            index = -1;
            id = INVALID_ROW_ID;
        }
        void copyFrom(ScrollInfo other) {
            index = other.index;
            id = other.id;
            mainPos = other.mainPos;
            secondPos = other.secondPos;
            viewLocation = other.viewLocation;
        }
    }

    // positions that current scrolled to
    private final ScrollInfo mCurScroll = new ScrollInfo();
    private int mItemSelected = -1;

    private int mPendingSelection = -1;
    private float mPendingScrollPosition = 0f;

    private final ScrollInfo mScrollBeforeReset = new ScrollInfo();

    private boolean mScrollTaskRunning;

    private ScrollAdapterBase mExpandAdapter;

    /** used for measuring the size of {@link ScrollAdapterView} */
    private int mScrapWidth;
    private int mScrapHeight;

    /** Animator for showing expanded item */
    private Animator mExpandedItemInAnim = null;

    /** Animator for hiding expanded item */
    private Animator mExpandedItemOutAnim = null;

    private boolean mNavigateOutOfOffAxisAllowed = DEFAULT_NAVIGATE_OUT_OF_OFF_AXIS_ALLOWED;
    private boolean mNavigateOutAllowed = DEFAULT_NAVIGATE_OUT_ALLOWED;

    private boolean mNavigateInAnimationAllowed = DEFAULT_NAVIGATE_IN_ANIMATION_ALLOWED;

    /**
     * internal structure maintaining status of expanded views
     */
    final class ExpandedView {
        private static final int ANIM_DURATION = 450;
        ExpandedView(View v, int i, int t) {
            expandedView = v;
            index = i;
            viewType = t;
        }

        final int index; // "Adapter index" of the expandable view
        final int viewType;
        final View expandedView; // expanded view
        float progress = 0f; // 0 ~ 1, indication if it's expanding or shrinking
        Animator grow_anim;
        Animator shrink_anim;

        Animator createFadeInAnimator() {
            if (mExpandedItemInAnim == null) {
                expandedView.setAlpha(0);
                ObjectAnimator anim1 = ObjectAnimator.ofFloat(null, "alpha", 1);
                anim1.setStartDelay(ANIM_DURATION / 2);
                anim1.setDuration(ANIM_DURATION * 2);
                return anim1;
            } else {
                return mExpandedItemInAnim.clone();
            }
        }

        Animator createFadeOutAnimator() {
            if (mExpandedItemOutAnim == null) {
                ObjectAnimator anim1 = ObjectAnimator.ofFloat(null, "alpha", 0);
                anim1.setDuration(ANIM_DURATION);
                return anim1;
            } else {
                return mExpandedItemOutAnim.clone();
            }
        }

        void setProgress(float p) {
            boolean growing = p > progress;
            boolean shrinking = p < progress;
            progress = p;
            if (growing) {
                if (shrink_anim != null) {
                    shrink_anim.cancel();
                    shrink_anim = null;
                }
                if (grow_anim == null) {
                    grow_anim = createFadeInAnimator();
                    grow_anim.setTarget(expandedView);
                    grow_anim.start();
                }
                if (!mAnimateLayoutChange) {
                    grow_anim.end();
                }
            } else if (shrinking) {
                if (grow_anim != null) {
                    grow_anim.cancel();
                    grow_anim = null;
                }
                if (shrink_anim == null) {
                    shrink_anim = createFadeOutAnimator();
                    shrink_anim.setTarget(expandedView);
                    shrink_anim.start();
                }
                if (!mAnimateLayoutChange) {
                    shrink_anim.end();
                }
            }
        }

        void close() {
            if (shrink_anim != null) {
                shrink_anim.cancel();
                shrink_anim = null;
            }
            if (grow_anim != null) {
                grow_anim.cancel();
                grow_anim = null;
            }
        }
    }

    /** list of ExpandedView structure */
    private final ArrayList<ExpandedView> mExpandedViews = new ArrayList<>(4);

    /** no scrolling */
    private static final int NO_SCROLL = 0;
    /** scrolling and centering a known focused view */
    private static final int SCROLL_AND_CENTER_FOCUS = 3;

    /**
     * internal state machine for scrolling, typical scenario: <br>
     * DPAD up/down is pressed: -> {@link #SCROLL_AND_CENTER_FOCUS} -> {@link #NO_SCROLL} <br>
     */
    private int mScrollerState;

    final Rect mTempRect = new Rect(); // temp variable used in UI thread

    // Controls whether or not sounds should be played when scrolling/clicking
    private boolean mPlaySoundEffects = true;

    public ScrollAdapterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScroll = new ScrollController(getContext());
        setChildrenDrawingOrderEnabled(true);
        setSoundEffectsEnabled(true);
        setWillNotDraw(true);
        initFromAttributes(context, attrs);
        reset();
    }

    private void initFromAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ScrollAdapterView);

        setOrientation(a.getInt(R.styleable.ScrollAdapterView_orientation, HORIZONTAL));

        mScroll.setScrollItemAlign(a.getInt(R.styleable.ScrollAdapterView_scrollItemAlign,
                ScrollController.SCROLL_ITEM_ALIGN_CENTER));

        setGridSetting(a.getInt(R.styleable.ScrollAdapterView_gridSetting, 1));

        if (a.hasValue(R.styleable.ScrollAdapterView_lowItemTransform)) {
            setLowItemTransform(AnimatorInflater.loadAnimator(getContext(),
                    a.getResourceId(R.styleable.ScrollAdapterView_lowItemTransform, -1)));
        }

        if (a.hasValue(R.styleable.ScrollAdapterView_highItemTransform)) {
            setHighItemTransform(AnimatorInflater.loadAnimator(getContext(),
                    a.getResourceId(R.styleable.ScrollAdapterView_highItemTransform, -1)));
        }

        if (a.hasValue(R.styleable.ScrollAdapterView_expandedItemInAnim)) {
            mExpandedItemInAnim = AnimatorInflater.loadAnimator(getContext(),
                    a.getResourceId(R.styleable.ScrollAdapterView_expandedItemInAnim, -1));
        }

        if (a.hasValue(R.styleable.ScrollAdapterView_expandedItemOutAnim)) {
            mExpandedItemOutAnim = AnimatorInflater.loadAnimator(getContext(),
                    a.getResourceId(R.styleable.ScrollAdapterView_expandedItemOutAnim, -1));
        }

        setSpace(a.getDimensionPixelSize(R.styleable.ScrollAdapterView_space, 0));

        setSelectedTakesMoreSpace(a.getBoolean(
                R.styleable.ScrollAdapterView_selectedTakesMoreSpace, false));

        setSelectedSize(a.getDimensionPixelSize(
                R.styleable.ScrollAdapterView_selectedSize, 0));

        setScrollCenterStrategy(a.getInt(R.styleable.ScrollAdapterView_scrollCenterStrategy, 0));

        setScrollCenterOffset(a.getDimensionPixelSize(
                R.styleable.ScrollAdapterView_scrollCenterOffset, 0));

        setScrollCenterOffsetPercent(a.getInt(
                R.styleable.ScrollAdapterView_scrollCenterOffsetPercent, 0));

        setNavigateOutAllowed(a.getBoolean(
                R.styleable.ScrollAdapterView_navigateOutAllowed, DEFAULT_NAVIGATE_OUT_ALLOWED));

        setNavigateOutOfOffAxisAllowed(a.getBoolean(
                R.styleable.ScrollAdapterView_navigateOutOfOffAxisAllowed,
                DEFAULT_NAVIGATE_OUT_OF_OFF_AXIS_ALLOWED));

        setNavigateInAnimationAllowed(a.getBoolean(
                R.styleable.ScrollAdapterView_navigateInAnimationAllowed,
                DEFAULT_NAVIGATE_IN_ANIMATION_ALLOWED));

        mScroll.lerper().setDivisor(a.getFloat(
                R.styleable.ScrollAdapterView_lerperDivisor, Lerper.DEFAULT_DIVISOR));

        a.recycle();
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
        mScroll.setOrientation(orientation);
    }

    public int getOrientation() {
        return mOrientation;
    }

    @SuppressWarnings("unchecked")
    private void reset() {
        mScrollBeforeReset.copyFrom(mCurScroll);
        mLeftIndex = -1;
        mRightIndex = 0;
        mDataSetChangedFlag = false;
        for (int i = 0, c = mExpandedViews.size(); i < c; i++) {
            ExpandedView v = mExpandedViews.get(i);
            v.close();
            removeViewInLayout(v.expandedView);
            mRecycleExpandedViews.recycleView(v.expandedView, v.viewType);
        }
        mExpandedViews.clear();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            removeViewInLayout(child);
            recycleExpandableView(child);
        }
        mRecycleViews.updateAdapter(mAdapter);
        mRecycleExpandedViews.updateAdapter(mExpandAdapter);
        mSelectedIndex = -1;
        mCurScroll.clear();
        mMadeInitialSelection = false;
    }

    /** find the view that containing scrollCenter or the next view */
    private int findViewIndexContainingScrollCenter(int scrollCenter, int scrollCenterOffAxis,
            boolean findNext) {
        final int lastExpandable = lastExpandableIndex();
        for (int i = firstExpandableIndex(); i < lastExpandable; i ++) {
            View view = getChildAt(i);
            int centerOffAxis = getCenterInOffAxis(view);
            int viewSizeOffAxis;
            if (mOrientation == HORIZONTAL) {
                viewSizeOffAxis = view.getHeight();
            } else {
                viewSizeOffAxis = view.getWidth();
            }
            int centerMain = getScrollCenter(view);
            if (hasScrollPosition(centerMain, getSize(view), scrollCenter)
                    && (mItemsOnOffAxis == 1 ||  hasScrollPositionSecondAxis(
                            scrollCenterOffAxis, viewSizeOffAxis, centerOffAxis))) {
                if (findNext) {
                    if (mScroll.isMainAxisMovingForward() && centerMain < scrollCenter) {
                        if (i + mItemsOnOffAxis < lastExpandableIndex()) {
                            i = i + mItemsOnOffAxis;
                        }
                    } else if (!mScroll.isMainAxisMovingForward() && centerMain > scrollCenter) {
                        if (i - mItemsOnOffAxis >= firstExpandableIndex()) {
                            i = i - mItemsOnOffAxis;
                        }
                    }
                    if (mItemsOnOffAxis == 1) {
                        // don't look in second axis if it's not grid
                    } else if (mScroll.isSecondAxisMovingForward() &&
                            centerOffAxis < scrollCenterOffAxis) {
                        if (i + 1 < lastExpandableIndex()) {
                            i += 1;
                        }
                    } else if (!mScroll.isSecondAxisMovingForward() &&
                            centerOffAxis < scrollCenterOffAxis) {
                        if (i - 1 >= firstExpandableIndex()) {
                            i -= 1;
                        }
                    }
                }
                return i;
            }
        }
        return -1;
    }

    private int findViewIndexContainingScrollCenter() {
        return findViewIndexContainingScrollCenter(mScroll.mainAxis().getScrollCenter(),
                mScroll.secondAxis().getScrollCenter(), false);
    }

    @Override
    public int getFirstVisiblePosition() {
        int first = firstExpandableIndex();
        return lastExpandableIndex() == first ? -1 : getAdapterIndex(first);
    }

    @Override
    public int getLastVisiblePosition() {
        int last = lastExpandableIndex();
        return firstExpandableIndex() == last ? -1 : getAdapterIndex(last - 1);
    }

    @Override
    public void setSelection(int position) {
        setSelectionInternal(position, 0f, true);
    }

    public void setSelection(int position, float offset) {
        setSelectionInternal(position, offset, true);
    }

    public int getCurrentAnimationDuration() {
        return mScroll.getCurrentAnimationDuration();
    }

    public void setSelectionSmooth(int index) {
        setSelectionSmooth(index, 0);
    }

    /** set selection using animation with a given duration, use 0 duration for auto  */
    public void setSelectionSmooth(int index, int duration) {
        int currentExpandableIndex = indexOfChild(getSelectedView());
        if (currentExpandableIndex < 0) {
            return;
        }
        int adapterIndex = getAdapterIndex(currentExpandableIndex);
        if (index == adapterIndex) {
            return;
        }
        boolean isGrowing = index > adapterIndex;
        View nextTop = null;
        if (isGrowing) {
            do {
                if (index < getAdapterIndex(lastExpandableIndex())) {
                    nextTop = getChildAt(expandableIndexFromAdapterIndex(index));
                    break;
                }
            } while (fillOneRightChildView(false));
        } else {
            do {
                if (index >= getAdapterIndex(firstExpandableIndex())) {
                    nextTop = getChildAt(expandableIndexFromAdapterIndex(index));
                    break;
                }
            } while (fillOneLeftChildView(false));
        }
        if (nextTop == null) {
            return;
        }
        int direction = isGrowing ?
                (mOrientation == HORIZONTAL ? View.FOCUS_RIGHT : View.FOCUS_DOWN) :
                (mOrientation == HORIZONTAL ? View.FOCUS_LEFT : View.FOCUS_UP);
        scrollAndFocusTo(nextTop, direction, false, duration, false);
    }

    private void fireDataSetChanged() {
        // set flag and trigger a scroll task
        mDataSetChangedFlag = true;
        scheduleScrollTask();
    }

    private final DataSetObserver mDataObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            fireDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            fireDataSetChanged();
        }

    };

    @Override
    public Adapter getAdapter() {
        return mAdapter;
    }

    /**
     * Adapter must be an implementation of {@link ScrollAdapter}.
     */
    @Override
    public void setAdapter(Adapter adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mDataObserver);
        }
        mAdapter = (ScrollAdapter) adapter;
        mExpandAdapter = mAdapter.getExpandAdapter();
        mAdapter.registerDataSetObserver(mDataObserver);
        mAdapterCustomSize = adapter instanceof ScrollAdapterCustomSize ?
                (ScrollAdapterCustomSize) adapter : null;
        mAdapterCustomAlign = adapter instanceof ScrollAdapterCustomAlign ?
                (ScrollAdapterCustomAlign) adapter : null;
        mMeasuredSpec = -1;
        mLoadingState = null;
        mPendingSelection = -1;
        mExpandableChildStates.clear();
        mExpandedChildStates.clear();
        mCurScroll.clear();
        mScrollBeforeReset.clear();
        fireDataSetChanged();
    }

    @Override
    public View getSelectedView() {
        return mSelectedIndex >= 0 ?
                getChildAt(expandableIndexFromAdapterIndex(mSelectedIndex)) : null;
    }

    public View getSelectedExpandedView() {
        ExpandedView ev = findExpandedView(mExpandedViews, getSelectedItemPosition());
        return ev == null ? null : ev.expandedView;
    }

    public View getViewContainingScrollCenter() {
        return getChildAt(findViewIndexContainingScrollCenter());
    }

    public int getIndexContainingScrollCenter() {
        return getAdapterIndex(findViewIndexContainingScrollCenter());
    }

    @Override
    public int getSelectedItemPosition() {
        return mSelectedIndex;
    }

    @Override
    public Object getSelectedItem() {
        int index = getSelectedItemPosition();
        if (index < 0) return null;
        return getAdapter().getItem(index);
    }

    @Override
    public long getSelectedItemId() {
        if (mAdapter != null) {
            int index = getSelectedItemPosition();
            if (index < 0) return INVALID_ROW_ID;
            return mAdapter.getItemId(index);
        }
        return INVALID_ROW_ID;
    }

    public View getItemView(int position) {
        int index = expandableIndexFromAdapterIndex(position);
        if (index >= firstExpandableIndex() && index < lastExpandableIndex()) {
            return getChildAt(index);
        }
        return null;
    }

    /**
     * set system scroll position from our scroll position,
     */
    private void adjustSystemScrollPos() {
        scrollTo(mScroll.horizontal.getSystemScrollPos(), mScroll.vertical.getSystemScrollPos());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mScroll.horizontal.setSize(w);
        mScroll.vertical.setSize(h);
        scheduleScrollTask();
    }

    /**
     * called from onLayout() to adjust all children's transformation based on how far they are from
     * {@link ScrollController.Axis#getScrollCenter()}
     */
    private void applyTransformations() {
        if (mItemTransform == null) {
            return;
        }
        int lastExpandable = lastExpandableIndex();
        for (int i = firstExpandableIndex(); i < lastExpandable; i++) {
            View child = getChildAt(i);
            mItemTransform.transform(child, getScrollCenter(child)
                    - mScroll.mainAxis().getScrollCenter(), mItemsOnOffAxis == 1 ? 0
                    : getCenterInOffAxis(child) - mScroll.secondAxis().getScrollCenter());
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateViewsLocations(true);
    }

    private void scheduleScrollTask() {
        if (!mScrollTaskRunning) {
            mScrollTaskRunning = true;
            postOnAnimation(mScrollTask);
        }
    }

    final Runnable mScrollTask = new Runnable() {
        @Override
        public void run() {
            try {
                scrollTaskRunInternal();
            } catch (RuntimeException ex) {
                reset();
                ex.printStackTrace();
            }
        }
    };

    private void scrollTaskRunInternal() {
        mScrollTaskRunning = false;
        // 1. adjust mScrollController and system Scroll position
        if (mDataSetChangedFlag) {
            reset();
        }
        if (mAdapter == null || mAdapter.getCount() == 0) {
            invalidate();
            if (mAdapter != null) {
                fireItemChange();
            }
            return;
        }
        if (mMeasuredSpec == -1) {
            // not layout yet
            requestLayout();
            scheduleScrollTask();
            return;
        }
        restoreLoadingState();
        mScroll.computeAndSetScrollPosition();

        boolean noChildBeforeFill = getChildCount() == 0;

        if (!noChildBeforeFill) {
            updateViewsLocations(false);
            adjustSystemScrollPos();
        }

        // 2. prune views that scroll out of visible area
        pruneInvisibleViewsInLayout();

        // 3. fill views in blank area
        fillVisibleViewsInLayout();

        if (noChildBeforeFill && getChildCount() > 0) {
            // if this is the first time add child(ren), we will get the initial value of
            // mScrollCenter after fillVisibleViewsInLayout(), and we need initialize the system
            // scroll position
            updateViewsLocations(false);
            adjustSystemScrollPos();
        }

        // 4. perform scroll position based animation
        fireScrollChange();
        applyTransformations();

        // 5. trigger another layout until the scroll stops
        if (!mScroll.isFinished()) {
            scheduleScrollTask();
        } else {
            // force ScrollAdapterView to reorder child order and call getChildDrawingOrder()
            invalidate();
            fireItemChange();
        }
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        boolean receiveFocus = getFocusedChild() == null && child != null;
        super.requestChildFocus(child, focused);
        if (receiveFocus && mScroll.isFinished()) {
            // schedule {@link #updateViewsLocations()} for focus transition into expanded view
            scheduleScrollTask();
        }
    }

    private void recycleExpandableView(View child) {
        ChildViewHolder holder = ((ChildViewHolder)child.getTag(R.id.ScrollAdapterViewChild));
        if (holder != null) {
            mRecycleViews.recycleView(child, holder.mItemViewType);
        }
    }

    private void pruneInvisibleViewsInLayout() {
        View selectedView = getSelectedView();
        if (mScroll.isFinished() || mScroll.isMainAxisMovingForward()) {
            while (true) {
                int firstIndex = firstExpandableIndex();
                View child = getChildAt(firstIndex);
                if (child == selectedView) {
                    break;
                }
                View nextChild = getChildAt(firstIndex + mItemsOnOffAxis);
                if (nextChild == null) {
                    break;
                }
                if (mOrientation == HORIZONTAL) {
                    if (child.getRight() - getScrollX() > 0) {
                        // don't prune the first view if it's visible
                        break;
                    }
                } else {
                    // VERTICAL is symmetric to HORIZONTAL, see comments above
                    if (child.getBottom() - getScrollY() > 0) {
                        break;
                    }
                }
                boolean foundFocus = false;
                for (int i = 0; i < mItemsOnOffAxis; i++){
                    int childIndex = firstIndex + i;
                    if (childHasFocus(childIndex)) {
                        foundFocus = true;
                        break;
                    }
                }
                if (foundFocus) {
                    break;
                }
                for (int i = 0; i < mItemsOnOffAxis; i++){
                    child = getChildAt(firstExpandableIndex());
                    mExpandableChildStates.saveInvisibleView(child, mLeftIndex + 1);
                    removeViewInLayout(child);
                    recycleExpandableView(child);
                    mLeftIndex++;
                }
            }
        }
        if (mScroll.isFinished() || !mScroll.isMainAxisMovingForward()) {
            while (true) {
                int count = mRightIndex % mItemsOnOffAxis;
                if (count == 0) {
                    count = mItemsOnOffAxis;
                }
                if (count > mRightIndex - mLeftIndex - 1) {
                    break;
                }
                int lastIndex = lastExpandableIndex();
                View child = getChildAt(lastIndex - 1);
                if (child == selectedView) {
                    break;
                }
                if (mOrientation == HORIZONTAL) {
                    if (child.getLeft() - getScrollX() < getWidth()) {
                        // don't prune the last view if it's visible
                        break;
                    }
                } else {
                    // VERTICAL is symmetric to HORIZONTAL, see comments above
                    if (child.getTop() - getScrollY() < getHeight()) {
                        break;
                    }
                }
                boolean foundFocus = false;
                for (int i = 0; i < count; i++){
                    int childIndex = lastIndex - 1 - i;
                    if (childHasFocus(childIndex)) {
                        foundFocus = true;
                        break;
                    }
                }
                if (foundFocus) {
                    break;
                }
                for (int i = 0; i < count; i++){
                    child = getChildAt(lastExpandableIndex() - 1);
                    mExpandableChildStates.saveInvisibleView(child, mRightIndex - 1);
                    removeViewInLayout(child);
                    recycleExpandableView(child);
                    mRightIndex--;
                }
            }
        }
    }

    /** check if expandable view or related expanded view has focus */
    private boolean childHasFocus(int expandableViewIndex) {
        View child = getChildAt(expandableViewIndex);
        if (child.hasFocus()) {
            return true;
        }
        ExpandedView v = findExpandedView(mExpandedViews, getAdapterIndex(expandableViewIndex));
        if (v != null && v.expandedView.hasFocus()) {
            return true;
        }
        return false;
    }

    /**
     * @param gridSetting <br>
     * {@link #GRID_SETTING_SINGLE}: single item on second axis, i.e. not a grid view <br>
     * {@link #GRID_SETTING_AUTO}: auto calculate number of items on second axis <br>
     * >1: shown as a grid view, with given fixed number of items on second axis <br>
     */
    public void setGridSetting(int gridSetting) {
        mGridSetting = gridSetting;
        requestLayout();
    }

    public int getGridSetting() {
        return mGridSetting;
    }

    private void fillVisibleViewsInLayout() {
        while (fillOneRightChildView(true)) {
        }
        while (fillOneLeftChildView(true)) {
        }
        if (mRightIndex >= 0 && mLeftIndex == -1) {
            // first child available
            View child = getChildAt(firstExpandableIndex());
            int scrollCenter = getScrollCenter(child);
            mScroll.mainAxis().updateScrollMin(scrollCenter, getScrollLow(scrollCenter, child));
        } else {
            mScroll.mainAxis().invalidateScrollMin();
        }
        if (mRightIndex == mAdapter.getCount()) {
            // last child available
            View child = getChildAt(lastExpandableIndex() - 1);
            int scrollCenter = getScrollCenter(child);
            mScroll.mainAxis().updateScrollMax(scrollCenter, getScrollHigh(scrollCenter, child));
        } else {
            mScroll.mainAxis().invalidateScrollMax();
        }
    }

    /**
     * try to add one left/top child view, returning false tells caller can stop loop
     */
    private boolean fillOneLeftChildView(boolean stopOnInvisible) {
        // 1. check if we still need add view
        if (mLeftIndex < 0) {
            return false;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        if (lastExpandableIndex() - firstExpandableIndex() > 0) {
            int childIndex = firstExpandableIndex();
            int last = Math.min(lastExpandableIndex(), childIndex + mItemsOnOffAxis);
            for (int i = childIndex; i < last; i++) {
                View v = getChildAt(i);
                if (mOrientation == HORIZONTAL) {
                    if (v.getLeft() < left) {
                        left = v.getLeft();
                    }
                } else {
                    if (v.getTop() < top) {
                        top = v.getTop();
                    }
                }
            }
            boolean itemInvisible;
            if (mOrientation == HORIZONTAL) {
                left -= mSpace;
                itemInvisible = left - getScrollX() <= 0;
                top = getPaddingTop();
            } else {
                top -= mSpace;
                itemInvisible = top - getScrollY() <= 0;
                left = getPaddingLeft();
            }
            if (itemInvisible && stopOnInvisible) {
                return false;
            }
        } else {
            return false;
        }
        // 2. create view and layout
        return fillOneAxis(left, top, false, true);
    }

    private View addAndMeasureExpandableView(int adapterIndex, int insertIndex) {
        int type = mAdapter.getItemViewType(adapterIndex);
        View recycleView = mRecycleViews.getView(type);
        View child = mAdapter.getView(adapterIndex, recycleView, this);
        if (child == null) {
            return null;
        }
        child.setTag(R.id.ScrollAdapterViewChild, new ChildViewHolder(type));
        addViewInLayout(child, insertIndex, child.getLayoutParams(), true);
        measureChild(child);
        return child;
    }

    private void measureScrapChild(View child, int widthMeasureSpec, int heightMeasureSpec) {
        LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = generateDefaultLayoutParams();
            child.setLayoutParams(p);
        }

        int childWidthSpec, childHeightSpec;
        if (mOrientation == VERTICAL) {
            childWidthSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec, 0, p.width);
            int lpHeight = p.height;
            if (lpHeight > 0) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
        } else {
            childHeightSpec = ViewGroup.getChildMeasureSpec(heightMeasureSpec, 0, p.height);
            int lpWidth = p.width;
            if (lpWidth > 0) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lpWidth, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
        }
        child.measure(childWidthSpec, childHeightSpec);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mAdapter == null) {
            Log.e(TAG, "onMeasure: Adapter not available ");
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        mScroll.horizontal.setPadding(getPaddingLeft(), getPaddingRight());
        mScroll.vertical.setPadding(getPaddingTop(), getPaddingBottom());

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int clientWidthSize = widthSize - getPaddingLeft() - getPaddingRight();
        int clientHeightSize = heightSize - getPaddingTop() - getPaddingBottom();

        if (mMeasuredSpec == -1) {
            View scrapView = mAdapter.getScrapView(this);
            measureScrapChild(scrapView, MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            mScrapWidth = scrapView.getMeasuredWidth();
            mScrapHeight = scrapView.getMeasuredHeight();
        }

        mItemsOnOffAxis = mGridSetting > 0 ? mGridSetting
            : mOrientation == HORIZONTAL ?
                (heightMode == MeasureSpec.UNSPECIFIED ? 1 : clientHeightSize / mScrapHeight)
                : (widthMode == MeasureSpec.UNSPECIFIED ? 1 : clientWidthSize / mScrapWidth);
        if (mItemsOnOffAxis == 0) {
            mItemsOnOffAxis = 1;
        }

        if (mLoadingState != null && mItemsOnOffAxis != mLoadingState.itemsOnOffAxis) {
            mLoadingState = null;
        }

        // see table below "height handling"
        if (widthMode == MeasureSpec.UNSPECIFIED ||
                (widthMode == MeasureSpec.AT_MOST && mOrientation == VERTICAL)) {
            int size = mOrientation == VERTICAL ? mScrapWidth * mItemsOnOffAxis
                    + mSpace * (mItemsOnOffAxis - 1) : mScrapWidth;
            size += getPaddingLeft() + getPaddingRight();
            widthSize = widthMode == MeasureSpec.AT_MOST ? Math.min(size, widthSize) : size;
        }
        // table of height handling
        // heightMode:   UNSPECIFIED              AT_MOST                              EXACTLY
        // HORIZONTAL    items*childHeight        min(items * childHeight, height)     height
        // VERTICAL      childHeight              height                               height
        if (heightMode == MeasureSpec.UNSPECIFIED ||
                (heightMode == MeasureSpec.AT_MOST && mOrientation == HORIZONTAL)) {
            int size = mOrientation == HORIZONTAL ?
                    mScrapHeight * mItemsOnOffAxis + mSpace * (mItemsOnOffAxis - 1) : mScrapHeight;
            size += getPaddingTop() + getPaddingBottom();
            heightSize = heightMode == MeasureSpec.AT_MOST ? Math.min(size, heightSize) : size;
        }
        mMeasuredSpec = mOrientation == HORIZONTAL ? heightMeasureSpec : widthMeasureSpec;

        setMeasuredDimension(widthSize, heightSize);

        // we allow scroll from padding low to padding high in the second axis
        int scrollMin = mScroll.secondAxis().getPaddingLow();
        int scrollMax = (mOrientation == HORIZONTAL ? heightSize : widthSize) -
                mScroll.secondAxis().getPaddingHigh();
        mScroll.secondAxis().updateScrollMin(scrollMin, scrollMin);
        mScroll.secondAxis().updateScrollMax(scrollMax, scrollMax);

        for (int j = 0, size = mExpandedViews.size(); j < size; j++) {
            ExpandedView v = mExpandedViews.get(j);
            measureChild(v.expandedView);
        }

        for (int i = firstExpandableIndex(); i < lastExpandableIndex(); i++) {
            View v = getChildAt(i);
            if (v.isLayoutRequested()) {
                measureChild(v);
            }
        }
    }

    /**
     * override to draw from two sides, center item is draw at last
     */
    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        int focusIndex = mSelectedIndex < 0 ? -1 :
                expandableIndexFromAdapterIndex(mSelectedIndex);
        if (focusIndex < 0) {
            return i;
        }
        // supposedly 0 1 2 3 4 5 6 7 8 9, 4 is the center item
        // drawing order is 0 1 2 3 9 8 7 6 5 4
        if (i < focusIndex) {
            return i;
        } else if (i < childCount - 1) {
            return focusIndex + childCount - 1 - i;
        } else {
            return focusIndex;
        }
    }

    /**
     * fill one off-axis views, the left/top of main axis will be interpreted as right/bottom if
     * leftToRight is false
     */
    private boolean fillOneAxis(int left, int top, boolean leftToRight, boolean setInitialPos) {
        // 2. create view and layout
        int viewIndex = lastExpandableIndex();
        int itemsToAdd = leftToRight ? Math.min(mItemsOnOffAxis, mAdapter.getCount() - mRightIndex)
                : mItemsOnOffAxis;
        int maxSize = 0;
        int maxSelectedSize = 0;
        for (int i = 0; i < itemsToAdd; i++) {
            View child = leftToRight ? addAndMeasureExpandableView(mRightIndex + i, -1) :
                addAndMeasureExpandableView(mLeftIndex - i, firstExpandableIndex());
            if (child == null) {
                return false;
            }
            maxSize = Math.max(maxSize, mOrientation == HORIZONTAL ? child.getMeasuredWidth() :
                    child.getMeasuredHeight());
            maxSelectedSize = Math.max(
                    maxSelectedSize, getSelectedItemSize(mLeftIndex - i, child));
        }
        if (!leftToRight) {
            viewIndex = firstExpandableIndex();
            if (mOrientation == HORIZONTAL) {
                left = left - maxSize;
            } else {
                top = top - maxSize;
            }
        }
        for (int i = 0; i < itemsToAdd; i++) {
            View child = getChildAt(viewIndex + i);
            ChildViewHolder h = (ChildViewHolder) child.getTag(R.id.ScrollAdapterViewChild);
            h.mMaxSize = maxSize;
            if (mOrientation == HORIZONTAL) {
                switch (mScroll.getScrollItemAlign()) {
                case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
                    child.layout(left + maxSize / 2 - child.getMeasuredWidth() / 2, top,
                            left + maxSize / 2 + child.getMeasuredWidth() / 2,
                            top + child.getMeasuredHeight());
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_LOW:
                    child.layout(left, top, left + child.getMeasuredWidth(),
                            top + child.getMeasuredHeight());
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
                    child.layout(left + maxSize - child.getMeasuredWidth(), top, left + maxSize,
                            top + child.getMeasuredHeight());
                    break;
                }
                top += child.getMeasuredHeight();
                top += mSpace;
            } else {
                switch (mScroll.getScrollItemAlign()) {
                case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
                    child.layout(left, top + maxSize / 2 - child.getMeasuredHeight() / 2,
                            left + child.getMeasuredWidth(),
                            top + maxSize / 2 + child.getMeasuredHeight() / 2);
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_LOW:
                    child.layout(left, top, left + child.getMeasuredWidth(),
                            top + child.getMeasuredHeight());
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
                    child.layout(left, top + maxSize - child.getMeasuredHeight(),
                            left + getMeasuredWidth(), top + maxSize);
                    break;
                }
                left += child.getMeasuredWidth();
                left += mSpace;
            }
            if (leftToRight) {
                mExpandableChildStates.loadView(child, mRightIndex);
                mRightIndex++;
            } else {
                mExpandableChildStates.loadView(child, mLeftIndex);
                mLeftIndex--;
            }
            h.mScrollCenter = computeScrollCenter(viewIndex + i);
            if (setInitialPos && leftToRight &&
                    mAdapter.isEnabled(mRightIndex - 1) && !mMadeInitialSelection) {
                // this is the first child being added
                int centerMain = getScrollCenter(child);
                int centerSecond = getCenterInOffAxis(child);
                if (mOrientation == HORIZONTAL) {
                    mScroll.setScrollCenter(centerMain, centerSecond);
                } else {
                    mScroll.setScrollCenter(centerSecond, centerMain);
                }
                mMadeInitialSelection = true;
                transferFocusTo(child, 0);
            }
        }
        return true;
    }
    /**
     * try to add one right/bottom child views, returning false tells caller can stop loop
     */
    private boolean fillOneRightChildView(boolean stopOnInvisible) {
        // 1. check if we still need add view
        if (mRightIndex >= mAdapter.getCount()) {
            return false;
        }
        int left = getPaddingLeft();
        int top = getPaddingTop();
        boolean checkedChild = false;
        if (lastExpandableIndex() - firstExpandableIndex() > 0) {
            // position of new view should starts from the last child or expanded view of last
            // child if it exists
            int childIndex = lastExpandableIndex() - 1;
            int gridPos = getAdapterIndex(childIndex) % mItemsOnOffAxis;
            for (int i = childIndex - gridPos; i < lastExpandableIndex(); i++) {
                View v = getChildAt(i);
                int adapterIndex = getAdapterIndex(i);
                ExpandedView expandedView = findExpandedView(mExpandedViews, adapterIndex);
                if (expandedView != null) {
                    if (mOrientation == HORIZONTAL) {
                        left = expandedView.expandedView.getRight();
                    } else {
                        top = expandedView.expandedView.getBottom();
                    }
                    checkedChild = true;
                    break;
                }
                if (mOrientation == HORIZONTAL) {
                    if (!checkedChild) {
                        checkedChild = true;
                        left = v.getRight();
                    } else if (v.getRight() > left) {
                        left = v.getRight();
                    }
                } else {
                    if (!checkedChild) {
                        checkedChild = true;
                        top = v.getBottom();
                    } else if (v.getBottom() > top) {
                        top = v.getBottom();
                    }
                }
            }
            boolean itemInvisible;
            if (mOrientation == HORIZONTAL) {
                left += mSpace;
                itemInvisible = left - getScrollX() >= getWidth();
                top = getPaddingTop();
            } else {
                top += mSpace;
                itemInvisible = top - getScrollY() >= getHeight();
                left = getPaddingLeft();
            }
            if (itemInvisible && stopOnInvisible) {
                return false;
            }
        }
        // 2. create view and layout
        return fillOneAxis(left, top, true, true);
    }

    private int heuristicGetPersistentIndex() {
        int c = mAdapter.getCount();
        if (mScrollBeforeReset.id != INVALID_ROW_ID) {
            if (mScrollBeforeReset.index < c
                    && mAdapter.getItemId(mScrollBeforeReset.index) == mScrollBeforeReset.id) {
                return mScrollBeforeReset.index;
            }
            for (int i = 1; i <= SEARCH_ID_RANGE; i++) {
                int index = mScrollBeforeReset.index + i;
                if (index < c && mAdapter.getItemId(index) == mScrollBeforeReset.id) {
                    return index;
                }
                index = mScrollBeforeReset.index - i;
                if (index >=0 && index < c && mAdapter.getItemId(index) == mScrollBeforeReset.id) {
                    return index;
                }
            }
        }
        return mScrollBeforeReset.index >= c ? c - 1 : mScrollBeforeReset.index;
    }

    private void restoreLoadingState() {
        int selection;
        int viewLoc = Integer.MIN_VALUE;
        float scrollPosition = 0f;
        if (mPendingSelection >= 0) {
            // got setSelection calls
            selection = mPendingSelection;
            scrollPosition = mPendingScrollPosition;
        } else if (mScrollBeforeReset.isValid()) {
            // data was refreshed, try to recover where we were
            selection = heuristicGetPersistentIndex();
            viewLoc = mScrollBeforeReset.viewLocation;
        } else if (mLoadingState != null) {
            // scrollAdapterView is restoring from loading state
            selection = mLoadingState.index;
        } else {
            return;
        }
        mPendingSelection = -1;
        mScrollBeforeReset.clear();
        mLoadingState = null;
        if (selection < 0 || selection >= mAdapter.getCount()) {
            Log.w(TAG, "invalid selection "+selection);
            return;
        }

        // startIndex is the first child in the same offAxis of selection
        // We add this view first because we don't know "selection" position in offAxis
        int startIndex = selection - selection % mItemsOnOffAxis;
        int left, top;
        if (mOrientation == HORIZONTAL) {
            // estimation of left
            left = viewLoc != Integer.MIN_VALUE ? viewLoc: mScroll.horizontal.getPaddingLow()
                    + mScrapWidth * (selection / mItemsOnOffAxis);
            top = mScroll.vertical.getPaddingLow();
        } else {
            left = mScroll.horizontal.getPaddingLow();
            // estimation of top
            top = viewLoc != Integer.MIN_VALUE ? viewLoc: mScroll.vertical.getPaddingLow()
                    + mScrapHeight * (selection / mItemsOnOffAxis);
        }
        mRightIndex = startIndex;
        mLeftIndex = mRightIndex - 1;
        fillOneAxis(left, top, true, false);
        mMadeInitialSelection = true;
        // fill all views, should include the "selection" view
        fillVisibleViewsInLayout();
        View child = getExpandableView(selection);
        if (child == null) {
            Log.w(TAG, "unable to restore selection view");
            return;
        }
        mExpandableChildStates.loadView(child, selection);
        if (viewLoc != Integer.MIN_VALUE && mScrollerState == SCROLL_AND_CENTER_FOCUS) {
            // continue scroll animation but since the views and sizes might change, we need
            // update the scrolling final target
            int finalLocation = (mOrientation == HORIZONTAL) ? mScroll.getFinalX() :
                    mScroll.getFinalY();
            mSelectedIndex = getAdapterIndex(indexOfChild(child));
            int scrollCenter = getScrollCenter(child);
            if (mScroll.mainAxis().getScrollCenter() <= finalLocation) {
                while (scrollCenter < finalLocation) {
                    int nextAdapterIndex = mSelectedIndex + mItemsOnOffAxis;
                    View nextView = getExpandableView(nextAdapterIndex);
                    if (nextView == null) {
                        if (!fillOneRightChildView(false)) {
                            break;
                        }
                        nextView = getExpandableView(nextAdapterIndex);
                    }
                    int nextScrollCenter = getScrollCenter(nextView);
                    if (nextScrollCenter > finalLocation) {
                        break;
                    }
                    mSelectedIndex = nextAdapterIndex;
                    scrollCenter = nextScrollCenter;
                }
            } else {
                while (scrollCenter > finalLocation) {
                    int nextAdapterIndex = mSelectedIndex - mItemsOnOffAxis;
                    View nextView = getExpandableView(nextAdapterIndex);
                    if (nextView == null) {
                        if (!fillOneLeftChildView(false)) {
                            break;
                        }
                        nextView = getExpandableView(nextAdapterIndex);
                    }
                    int nextScrollCenter = getScrollCenter(nextView);
                    if (nextScrollCenter < finalLocation) {
                        break;
                    }
                    mSelectedIndex = nextAdapterIndex;
                    scrollCenter = nextScrollCenter;
                }
            }
            if (mOrientation == HORIZONTAL) {
                mScroll.setFinalX(scrollCenter);
            } else {
                mScroll.setFinalY(scrollCenter);
            }
        } else {
            // otherwise center focus to the view and stop animation
            setSelectionInternal(selection, scrollPosition, false);
        }
    }

    private void measureChild(View child) {
        LayoutParams p = child.getLayoutParams();
        if (p == null) {
            p = generateDefaultLayoutParams();
            child.setLayoutParams(p);
        }
        if (mOrientation == VERTICAL) {
            int childWidthSpec = ViewGroup.getChildMeasureSpec(mMeasuredSpec, 0, p.width);
            int lpHeight = p.height;
            int childHeightSpec;
            if (lpHeight > 0) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
            child.measure(childWidthSpec, childHeightSpec);
        } else {
            int childHeightSpec = ViewGroup.getChildMeasureSpec(mMeasuredSpec, 0, p.height);
            int lpWidth = p.width;
            int childWidthSpec;
            if (lpWidth > 0) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lpWidth, MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            }
            child.measure(childWidthSpec, childHeightSpec);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // passing key event to focused child, which has chance to stop event processing by
        // returning true.
        // If child does not handle the event, we handle DPAD etc.
        return super.dispatchKeyEvent(event) || event.dispatch(this, null, null);
    }

    protected boolean internalKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (handleArrowKey(View.FOCUS_LEFT, 0, false, false)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (handleArrowKey(View.FOCUS_RIGHT, 0, false, false)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (handleArrowKey(View.FOCUS_UP, 0, false, false)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (handleArrowKey(View.FOCUS_DOWN, 0, false, false)) {
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return internalKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (getOnItemClickListener() != null) {
                    int index = findViewIndexContainingScrollCenter();
                    View child = getChildAt(index);
                    if (child != null) {
                        int adapterIndex = getAdapterIndex(index);
                        getOnItemClickListener().onItemClick(this, child,
                                adapterIndex, mAdapter.getItemId(adapterIndex));
                        return true;
                    }
                }
                // otherwise fall back to default handling, typically handled by
                // the focused child view
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Scroll to next/last expandable view.
     * @param direction The direction corresponding to the arrow key that was pressed
     * @param repeats repeated count (0 means no repeat)
     * @return True if we consumed the event, false otherwise
     */
    public boolean arrowScroll(int direction, int repeats) {
        if (DBG) Log.d(TAG, "arrowScroll " + direction);
        return handleArrowKey(direction, repeats, true, false);
    }

    /** equivalent to arrowScroll(direction, 0) */
    public boolean arrowScroll(int direction) {
        return arrowScroll(direction, 0);
    }

    public boolean isInScrolling() {
        return !mScroll.isFinished();
    }

    public boolean isInScrollingOrDragging() {
        return mScrollerState != NO_SCROLL;
    }

    public void setPlaySoundEffects(boolean playSoundEffects) {
        mPlaySoundEffects = playSoundEffects;
    }

    private static boolean isDirectionGrowing(int direction) {
        return direction == View.FOCUS_RIGHT || direction == View.FOCUS_DOWN;
    }

    private static boolean isDescendant(View parent, View v) {
        while (v != null) {
            ViewParent p = v.getParent();
            if (p == parent) {
                return true;
            }
            if (!(p instanceof View)) {
                return false;
            }
            v = (View) p;
        }
        return false;
    }

    private boolean requestNextFocus(int direction, View focused, View newFocus) {
        focused.getFocusedRect(mTempRect);
        offsetDescendantRectToMyCoords(focused, mTempRect);
        offsetRectIntoDescendantCoords(newFocus, mTempRect);
        return newFocus.requestFocus(direction, mTempRect);
    }

    protected boolean handleArrowKey(int direction, int repeats, boolean forceFindNextExpandable,
            boolean page) {
        View currentTop = getFocusedChild();
        View currentExpandable = getExpandableChild(currentTop);
        View focused = findFocus();
        if (currentTop == currentExpandable && focused != null && !forceFindNextExpandable) {
            // find next focused inside expandable item
            View v = focused.focusSearch(direction);
            if (v != null && v != focused && isDescendant(currentTop, v)) {
                requestNextFocus(direction, focused, v);
                return true;
            }
        }
        boolean isGrowing = isDirectionGrowing(direction);
        boolean isOnOffAxis = false;
        if (direction == View.FOCUS_RIGHT || direction == View.FOCUS_LEFT) {
            isOnOffAxis = mOrientation == VERTICAL;
        } else if (direction == View.FOCUS_DOWN || direction == View.FOCUS_UP) {
            isOnOffAxis = mOrientation == HORIZONTAL;
        }

        if (currentTop != currentExpandable && !forceFindNextExpandable) {
            // find next focused inside expanded item
            View nextFocused = currentTop instanceof ViewGroup ? FocusFinder.getInstance()
                    .findNextFocus((ViewGroup) currentTop, findFocus(), direction)
                    : null;
            View nextTop = getTopItem(nextFocused);
            if (nextTop == currentTop) {
                // within same expanded item
                // ignore at this level, the key handler of expanded item will take care
                return false;
            }
        }

        // focus to next expandable item
        int currentExpandableIndex = expandableIndexFromAdapterIndex(mSelectedIndex);
        if (currentExpandableIndex < 0) {
            return false;
        }
        View nextTop = null;
        if (isOnOffAxis) {
            if (isGrowing && currentExpandableIndex + 1 < lastExpandableIndex() &&
                            getAdapterIndex(currentExpandableIndex) % mItemsOnOffAxis
                            != mItemsOnOffAxis - 1) {
                nextTop = getChildAt(currentExpandableIndex + 1);
            } else if (!isGrowing && currentExpandableIndex - 1 >= firstExpandableIndex()
                    && getAdapterIndex(currentExpandableIndex) % mItemsOnOffAxis != 0) {
                nextTop = getChildAt(currentExpandableIndex - 1);
            } else {
                return !mNavigateOutOfOffAxisAllowed;
            }
        } else {
            int adapterIndex = getAdapterIndex(currentExpandableIndex);
            int focusAdapterIndex = adapterIndex;
            for (int totalCount = repeats + 1; totalCount > 0;) {
                int nextFocusAdapterIndex = isGrowing ? focusAdapterIndex + mItemsOnOffAxis:
                    focusAdapterIndex - mItemsOnOffAxis;
                if ((isGrowing && nextFocusAdapterIndex >= mAdapter.getCount())
                        || (!isGrowing && nextFocusAdapterIndex < 0)) {
                    if (focusAdapterIndex == adapterIndex
                            || !mAdapter.isEnabled(focusAdapterIndex)) {
                        if (hasFocus() && mNavigateOutAllowed) {
                            View view = getChildAt(
                                    expandableIndexFromAdapterIndex(focusAdapterIndex));
                            if (view != null && !view.hasFocus()) {
                                view.requestFocus();
                            }
                        }
                        return !mNavigateOutAllowed;
                    } else {
                        break;
                    }
                }
                focusAdapterIndex = nextFocusAdapterIndex;
                if (mAdapter.isEnabled(focusAdapterIndex)) {
                    totalCount--;
                }
            }
            if (isGrowing) {
                do {
                    if (focusAdapterIndex <= getAdapterIndex(lastExpandableIndex() - 1)) {
                        nextTop = getChildAt(expandableIndexFromAdapterIndex(focusAdapterIndex));
                        break;
                    }
                } while (fillOneRightChildView(false));
                if (nextTop == null) {
                    nextTop = getChildAt(lastExpandableIndex() - 1);
                }
            } else {
                do {
                    if (focusAdapterIndex >= getAdapterIndex(firstExpandableIndex())) {
                        nextTop = getChildAt(expandableIndexFromAdapterIndex(focusAdapterIndex));
                        break;
                    }
                } while (fillOneLeftChildView(false));
                if (nextTop == null) {
                    nextTop = getChildAt(firstExpandableIndex());
                }
            }
            if (nextTop == null) {
                return true;
            }
        }
        scrollAndFocusTo(nextTop, direction, false, 0, page);
        if (mPlaySoundEffects) {
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
        }
        return true;
    }

    private void fireItemChange() {
        int childIndex = findViewIndexContainingScrollCenter();
        View topItem = getChildAt(childIndex);
        if (isFocused() && getDescendantFocusability() == FOCUS_AFTER_DESCENDANTS
                && topItem != null) {
            // transfer focus to child for reset/restore
            topItem.requestFocus();
        }
        if (mOnItemChangeListeners != null && !mOnItemChangeListeners.isEmpty()) {
            if (topItem == null) {
                if (mItemSelected != -1) {
                    for (OnItemChangeListener listener : mOnItemChangeListeners) {
                        listener.onItemSelected(null, -1, 0);
                    }
                    mItemSelected = -1;
                }
            } else {
                int adapterIndex = getAdapterIndex(childIndex);
                int scrollCenter = getScrollCenter(topItem);
                for (OnItemChangeListener listener : mOnItemChangeListeners) {
                    listener.onItemSelected(topItem, adapterIndex, scrollCenter -
                            mScroll.mainAxis().getSystemScrollPos(scrollCenter));
                }
                mItemSelected = adapterIndex;
            }
        }

        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    private void updateScrollInfo(ScrollInfo info) {
        int scrollCenter = mScroll.mainAxis().getScrollCenter();
        int scrollCenterOff = mScroll.secondAxis().getScrollCenter();
        int index = findViewIndexContainingScrollCenter(
                scrollCenter, scrollCenterOff, false);
        if (index < 0) {
            info.index = -1;
            return;
        }
        View view = getChildAt(index);
        int center = getScrollCenter(view);
        if (scrollCenter > center) {
            if (index + mItemsOnOffAxis < lastExpandableIndex()) {
                int nextCenter = getScrollCenter(getChildAt(index + mItemsOnOffAxis));
                info.mainPos = (float)(scrollCenter - center) / (nextCenter - center);
            } else {
                // overscroll to right
                info.mainPos = (float)(scrollCenter - center) / getSize(view);
            }
        } else if (scrollCenter == center){
            info.mainPos = 0;
        } else {
            if (index - mItemsOnOffAxis >= firstExpandableIndex()) {
                index = index - mItemsOnOffAxis;
                view = getChildAt(index);
                int previousCenter = getScrollCenter(view);
                info.mainPos = (float) (scrollCenter - previousCenter) /
                        (center - previousCenter);
            } else {
                // overscroll to left, negative value
                info.mainPos = (float) (scrollCenter - center) / getSize(view);
            }
        }
        int centerOffAxis = getCenterInOffAxis(view);
        if (scrollCenterOff > centerOffAxis) {
            if (index + 1 < lastExpandableIndex()) {
                int nextCenter = getCenterInOffAxis(getChildAt(index + 1));
                info.secondPos = (float) (scrollCenterOff - centerOffAxis)
                        / (nextCenter - centerOffAxis);
            } else {
                // overscroll to right
                info.secondPos = (float) (scrollCenterOff - centerOffAxis) /
                        getSizeInOffAxis(view);
            }
        } else if (scrollCenterOff == centerOffAxis) {
            info.secondPos = 0;
        } else {
            if (index - 1 >= firstExpandableIndex()) {
                index = index - 1;
                view = getChildAt(index);
                int previousCenter = getCenterInOffAxis(view);
                info.secondPos = (float) (scrollCenterOff - previousCenter)
                        / (centerOffAxis - previousCenter);
            } else {
                // overscroll to left, negative value
                info.secondPos = (float) (scrollCenterOff - centerOffAxis) /
                        getSizeInOffAxis(view);
            }
        }
        info.index = getAdapterIndex(index);
        info.viewLocation = mOrientation == HORIZONTAL ? view.getLeft() : view.getTop();
        if (mAdapter.hasStableIds()) {
            info.id = mAdapter.getItemId(info.index);
        }
    }

    private void fireScrollChange() {
        int savedIndex = mCurScroll.index;
        float savedMainPos = mCurScroll.mainPos;
        float savedSecondPos = mCurScroll.secondPos;
        updateScrollInfo(mCurScroll);
        if (mOnScrollListeners != null && !mOnScrollListeners.isEmpty()
                &&(savedIndex != mCurScroll.index
                || savedMainPos != mCurScroll.mainPos || savedSecondPos != mCurScroll.secondPos)) {
            if (mCurScroll.index >= 0) {
                for (OnScrollListener l : mOnScrollListeners) {
                    l.onScrolled(getChildAt(expandableIndexFromAdapterIndex(
                            mCurScroll.index)), mCurScroll.index,
                            mCurScroll.mainPos, mCurScroll.secondPos);
                }
            }
        }
    }

    private void fireItemSelected() {
        OnItemSelectedListener listener = getOnItemSelectedListener();
        if (listener != null) {
            listener.onItemSelected(this, getSelectedView(), getSelectedItemPosition(),
                    getSelectedItemId());
        }
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    /** manually set scroll position */
    private void setSelectionInternal(int adapterIndex, float scrollPosition, boolean fireEvent) {
        if (adapterIndex < 0 || mAdapter == null || adapterIndex >= mAdapter.getCount()
                || !mAdapter.isEnabled(adapterIndex)) {
            Log.w(TAG, "invalid selection index = " + adapterIndex);
            return;
        }
        int viewIndex = expandableIndexFromAdapterIndex(adapterIndex);
        if (mDataSetChangedFlag || viewIndex < firstExpandableIndex() ||
                viewIndex >= lastExpandableIndex()) {
            mPendingSelection = adapterIndex;
            mPendingScrollPosition = scrollPosition;
            fireDataSetChanged();
            return;
        }
        View view = getChildAt(viewIndex);
        int scrollCenter = getScrollCenter(view);
        int scrollCenterOffAxis = getCenterInOffAxis(view);
        int deltaMain;
        if (scrollPosition > 0 && viewIndex + mItemsOnOffAxis < lastExpandableIndex()) {
            int nextCenter = getScrollCenter(getChildAt(viewIndex + mItemsOnOffAxis));
            deltaMain = (int) ((nextCenter - scrollCenter) * scrollPosition);
        } else {
            deltaMain = (int) (getSize(view) * scrollPosition);
        }
        if (mOrientation == HORIZONTAL) {
            mScroll.setScrollCenter(scrollCenter + deltaMain, scrollCenterOffAxis);
        } else {
            mScroll.setScrollCenter(scrollCenterOffAxis, scrollCenter + deltaMain);
        }
        transferFocusTo(view, 0);
        adjustSystemScrollPos();
        applyTransformations();
        if (fireEvent) {
            updateViewsLocations(false);
            fireScrollChange();
            if (scrollPosition == 0) {
                fireItemChange();
            }
        }
    }

    private void transferFocusTo(View topItem, int direction) {
        View oldSelection = getSelectedView();
        if (topItem == oldSelection) {
            return;
        }
        mSelectedIndex = getAdapterIndex(indexOfChild(topItem));
        View focused = findFocus();
        if (focused != null) {
            if (direction != 0) {
                requestNextFocus(direction, focused, topItem);
            } else {
                topItem.requestFocus();
            }
        }
        fireItemSelected();
    }

    /** scroll And Focus To expandable item in the main direction */
    public void scrollAndFocusTo(View topItem, int direction, boolean easeFling, int duration,
            boolean page) {
        if (topItem == null) {
            mScrollerState = NO_SCROLL;
            return;
        }
        int delta = getScrollCenter(topItem) - mScroll.mainAxis().getScrollCenter();
        int deltaOffAxis = mItemsOnOffAxis == 1 ? 0 : // don't scroll 2nd axis for non-grid
                getCenterInOffAxis(topItem) - mScroll.secondAxis().getScrollCenter();
        if (delta != 0 || deltaOffAxis != 0) {
            mScrollerState = SCROLL_AND_CENTER_FOCUS;
            mScroll.startScrollByMain(delta, deltaOffAxis, easeFling, duration, page);
            // Instead of waiting scrolling animation finishes, we immediately change focus.
            // This will cause focused item to be off center and benefit is to dealing multiple
            // DPAD events without waiting animation finish.
        } else {
            mScrollerState = NO_SCROLL;
        }

        transferFocusTo(topItem, direction);

        scheduleScrollTask();
    }

    public int getScrollCenterStrategy() {
        return mScroll.mainAxis().getScrollCenterStrategy();
    }

    public void setScrollCenterStrategy(int scrollCenterStrategy) {
        mScroll.mainAxis().setScrollCenterStrategy(scrollCenterStrategy);
    }

    public int getScrollCenterOffset() {
        return mScroll.mainAxis().getScrollCenterOffset();
    }

    public void setScrollCenterOffset(int scrollCenterOffset) {
        mScroll.mainAxis().setScrollCenterOffset(scrollCenterOffset);
    }

    public void setScrollCenterOffsetPercent(int scrollCenterOffsetPercent) {
        mScroll.mainAxis().setScrollCenterOffsetPercent(scrollCenterOffsetPercent);
    }

    public void setItemTransform(ScrollAdapterTransform transform) {
        mItemTransform = transform;
    }

    public ScrollAdapterTransform getItemTransform() {
        return mItemTransform;
    }

    private void ensureSimpleItemTransform() {
        if (! (mItemTransform instanceof SimpleScrollAdapterTransform)) {
            mItemTransform = new SimpleScrollAdapterTransform(getContext());
        }
    }

    public void setLowItemTransform(Animator anim) {
        ensureSimpleItemTransform();
        ((SimpleScrollAdapterTransform)mItemTransform).setLowItemTransform(anim);
    }

    public void setHighItemTransform(Animator anim) {
        ensureSimpleItemTransform();
        ((SimpleScrollAdapterTransform)mItemTransform).setHighItemTransform(anim);
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (mOrientation != HORIZONTAL || mAdapter == null || getChildCount() == 0) {
            return 0;
        }
        if (mRightIndex == mAdapter.getCount()) {
            View lastChild = getChildAt(lastExpandableIndex() - 1);
            int maxEdge = lastChild.getRight();
            if (getScrollX() + getWidth() >= maxEdge) {
                return 0;
            }
        }
        return 1;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        if (mOrientation != HORIZONTAL || mAdapter == null || getChildCount() == 0) {
            return 0;
        }
        if (mRightIndex == mAdapter.getCount()) {
            View lastChild = getChildAt(lastExpandableIndex() - 1);
            int maxEdge = lastChild.getBottom();
            if (getScrollY() + getHeight() >= maxEdge) {
                return 0;
            }
        }
        return 1;
    }

    /**
     * get the view which is ancestor of "v" and immediate child of root view return "v" if
     * rootView is not ViewGroup or "v" is not in the subtree
     */
    private View getTopItem(View v) {
        ViewGroup root = this;
        View ret = v;
        while (ret != null && ret.getParent() != root) {
            if (!(ret.getParent() instanceof View)) {
                break;
            }
            ret = (View) ret.getParent();
        }
        if (ret == null) {
            return v;
        } else {
            return ret;
        }
    }

    private int getCenter(View v) {
        return mOrientation == HORIZONTAL ? (v.getLeft() + v.getRight()) / 2 : (v.getTop()
                + v.getBottom()) / 2;
    }

    private int getCenterInOffAxis(View v) {
        return mOrientation == VERTICAL ? (v.getLeft() + v.getRight()) / 2 : (v.getTop()
                + v.getBottom()) / 2;
    }

    private int getSize(View v) {
        return ((ChildViewHolder) v.getTag(R.id.ScrollAdapterViewChild)).mMaxSize;
    }

    private int getSizeInOffAxis(View v) {
        return mOrientation == HORIZONTAL ? v.getHeight() : v.getWidth();
    }

    public View getExpandableView(int adapterIndex) {
        return getChildAt(expandableIndexFromAdapterIndex(adapterIndex));
    }

    public int firstExpandableIndex() {
        return mExpandedViews.size();
    }

    public int lastExpandableIndex() {
        return getChildCount();
    }

    private int getAdapterIndex(int expandableViewIndex) {
        return expandableViewIndex - firstExpandableIndex() + mLeftIndex + 1;
    }

    private int expandableIndexFromAdapterIndex(int index) {
        return firstExpandableIndex() + index - mLeftIndex - 1;
    }

    View getExpandableChild(View view) {
        if (view != null) {
            for (int i = 0, size = mExpandedViews.size(); i < size; i++) {
                ExpandedView v = mExpandedViews.get(i);
                if (v.expandedView == view) {
                    return getChildAt(expandableIndexFromAdapterIndex(v.index));
                }
            }
        }
        return view;
    }

    private static ExpandedView findExpandedView(ArrayList<ExpandedView> expandedView, int index) {
        int expandedCount = expandedView.size();
        for (int i = 0; i < expandedCount; i++) {
            ExpandedView v = expandedView.get(i);
            if (v.index == index) {
                return v;
            }
        }
        return null;
    }

    /**
     * This function is only called from {@link #updateViewsLocations()} Returns existing
     * ExpandedView or create a new one.
     */
    private ExpandedView getOrCreateExpandedView(int index) {
        if (mExpandAdapter == null || index < 0) {
            return null;
        }
        ExpandedView ret = findExpandedView(mExpandedViews, index);
        if (ret != null) {
            return ret;
        }
        int type = mExpandAdapter.getItemViewType(index);
        View recycleView = mRecycleExpandedViews.getView(type);
        View v = mExpandAdapter.getView(index, recycleView, ScrollAdapterView.this);
        if (v == null) {
            return null;
        }
        addViewInLayout(v, 0, v.getLayoutParams(), true);
        mExpandedChildStates.loadView(v, index);
        measureChild(v);
        if (DBG) Log.d(TAG, "created new expanded View for " + index + " " + v);
        ExpandedView view = new ExpandedView(v, index, type);
        for (int i = 0, size = mExpandedViews.size(); i < size; i++) {
            if (view.index < mExpandedViews.get(i).index) {
                mExpandedViews.add(i, view);
                return view;
            }
        }
        mExpandedViews.add(view);
        return view;
    }

    public void setAnimateLayoutChange(boolean animateLayoutChange) {
        mAnimateLayoutChange = animateLayoutChange;
    }

    public boolean getAnimateLayoutChange() {
        return mAnimateLayoutChange;
    }

    /**
     * Key function to update expandable views location and create/destroy expanded views
     */
    private void updateViewsLocations(boolean onLayout) {
        int lastExpandable = lastExpandableIndex();
        if (((mExpandAdapter == null && !selectedItemCanScale() && mAdapterCustomAlign == null)
                || lastExpandable == 0) &&
                (!onLayout || getChildCount() == 0)) {
            return;
        }

        int scrollCenter = mScroll.mainAxis().getScrollCenter();
        int scrollCenterOffAxis = mScroll.secondAxis().getScrollCenter();
        // 1 search center and nextCenter that contains mScrollCenter.
        int expandedCount = mExpandedViews.size();
        int center = -1;
        int nextCenter = -1;
        int expandIdx = -1;
        int firstExpandable = firstExpandableIndex();
        int alignExtraOffset = 0;
        for (int idx = firstExpandable; idx < lastExpandable; idx++) {
            View view = getChildAt(idx);
            int centerMain = getScrollCenter(view);
            int centerOffAxis = getCenterInOffAxis(view);
            int viewSizeOffAxis = mOrientation == HORIZONTAL ? view.getHeight() : view.getWidth();
            if (centerMain <= scrollCenter && (mItemsOnOffAxis == 1 || hasScrollPositionSecondAxis(
                    scrollCenterOffAxis, viewSizeOffAxis, centerOffAxis))) {
                // find last one match the criteria,  we can optimize it..
                expandIdx = idx;
                center = centerMain;
                if (mAdapterCustomAlign != null) {
                    alignExtraOffset = mAdapterCustomAlign.getItemAlignmentExtraOffset(
                            getAdapterIndex(idx), view);
                }
            }
        }
        if (expandIdx == -1) {
            // mScrollCenter scrolls too fast, a fling action might cause this
            return;
        }
        int nextExpandIdx = expandIdx + mItemsOnOffAxis;
        int nextAlignExtraOffset = 0;
        if (nextExpandIdx < lastExpandable) {
            View nextView = getChildAt(nextExpandIdx);
            nextCenter = getScrollCenter(nextView);
            if (mAdapterCustomAlign != null) {
                nextAlignExtraOffset = mAdapterCustomAlign.getItemAlignmentExtraOffset(
                        getAdapterIndex(nextExpandIdx), nextView);
            }
        } else {
            nextExpandIdx = -1;
        }
        int previousExpandIdx = expandIdx - mItemsOnOffAxis;
        if (previousExpandIdx < firstExpandable) {
            previousExpandIdx = -1;
        }

        // 2. prepare the expanded view, they could be new created or from existing.
        int xindex = getAdapterIndex(expandIdx);
        ExpandedView thisExpanded = getOrCreateExpandedView(xindex);
        ExpandedView nextExpanded = null;
        if (nextExpandIdx != -1) {
            nextExpanded = getOrCreateExpandedView(xindex + mItemsOnOffAxis);
        }
        // cache one more expanded view before the visible one, it's always invisible
        ExpandedView previousExpanded = null;
        if (previousExpandIdx != -1) {
            previousExpanded = getOrCreateExpandedView(xindex - mItemsOnOffAxis);
        }

        // these count and index needs to be updated after we inserted new views
        int newExpandedAdded = mExpandedViews.size() - expandedCount;
        expandIdx += newExpandedAdded;
        if (nextExpandIdx != -1) {
            nextExpandIdx += newExpandedAdded;
        }
        expandedCount = mExpandedViews.size();
        lastExpandable = lastExpandableIndex();

        // 3. calculate the expanded View size, and optional next expanded view size.
        int expandedSize = 0;
        int nextExpandedSize = 0;
        float progress = 1;
        if (expandIdx < lastExpandable - 1) {
            progress = (float) (nextCenter - mScroll.mainAxis().getScrollCenter()) /
                       (float) (nextCenter - center);
            if (thisExpanded != null) {
                expandedSize =
                        (mOrientation == HORIZONTAL ? thisExpanded.expandedView.getMeasuredWidth()
                                : thisExpanded.expandedView.getMeasuredHeight());
                expandedSize = (int) (progress * expandedSize);
                thisExpanded.setProgress(progress);
            }
            if (nextExpanded != null) {
                nextExpandedSize =
                        (mOrientation == HORIZONTAL ? nextExpanded.expandedView.getMeasuredWidth()
                                : nextExpanded.expandedView.getMeasuredHeight());
                nextExpandedSize = (int) ((1f - progress) * nextExpandedSize);
                nextExpanded.setProgress(1f - progress);
            }
        } else {
            if (thisExpanded != null) {
                expandedSize =
                        (mOrientation == HORIZONTAL ? thisExpanded.expandedView.getMeasuredWidth()
                                : thisExpanded.expandedView.getMeasuredHeight());
                thisExpanded.setProgress(1f);
            }
        }

        int totalExpandedSize = expandedSize + nextExpandedSize;
        int extraSpaceLow = 0, extraSpaceHigh = 0;
        // 4. update expandable views positions
        int low = Integer.MAX_VALUE;
        int expandedStart = 0;
        int nextExpandedStart = 0;
        int numOffAxis = (lastExpandable - firstExpandableIndex() + mItemsOnOffAxis - 1)
                / mItemsOnOffAxis;
        boolean canAnimateExpandedSize = mAnimateLayoutChange &&
                mScroll.isFinished() && mExpandAdapter != null;
        for (int j = 0; j < numOffAxis; j++) {
            int viewIndex = firstExpandableIndex() + j * mItemsOnOffAxis;
            int endViewIndex = viewIndex + mItemsOnOffAxis - 1;
            if (endViewIndex >= lastExpandable) {
                endViewIndex = lastExpandable - 1;
            }
            // get maxSize of the off-axis, get start position for first off-axis
            int maxSize = 0;
            for (int k = viewIndex; k <= endViewIndex; k++) {
                View view = getChildAt(k);
                ChildViewHolder h = (ChildViewHolder) view.getTag(R.id.ScrollAdapterViewChild);
                if (canAnimateExpandedSize) {
                    // remember last position in temporary variable
                    if (mOrientation == HORIZONTAL) {
                        h.mLocation = view.getLeft();
                        h.mLocationInParent = h.mLocation + view.getTranslationX();
                    } else {
                        h.mLocation = view.getTop();
                        h.mLocationInParent = h.mLocation + view.getTranslationY();
                    }
                }
                maxSize = Math.max(maxSize, mOrientation == HORIZONTAL ? view.getMeasuredWidth() :
                    view.getMeasuredHeight());
                if (j == 0) {
                    int viewLow = mOrientation == HORIZONTAL ? view.getLeft() : view.getTop();
                    // because we start over again,  we should remove the extra space
                    if (mScroll.mainAxis().getSelectedTakesMoreSpace()) {
                        viewLow -= h.mExtraSpaceLow;
                    }
                    if (viewLow < low) {
                        low = viewLow;
                    }
                }
            }
            // layout views within the off axis and get the max right/bottom
            int maxSelectedSize = Integer.MIN_VALUE;
            int maxHigh = low + maxSize;
            for (int k = viewIndex; k <= endViewIndex; k++) {
                View view = getChildAt(k);
                int viewStart = low;
                int viewMeasuredSize = mOrientation == HORIZONTAL ? view.getMeasuredWidth()
                        : view.getMeasuredHeight();
                switch (mScroll.getScrollItemAlign()) {
                case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
                    viewStart += maxSize / 2 - viewMeasuredSize / 2;
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
                    viewStart += maxSize - viewMeasuredSize;
                    break;
                case ScrollController.SCROLL_ITEM_ALIGN_LOW:
                    break;
                }
                if (mOrientation == HORIZONTAL) {
                    if (view.isLayoutRequested()) {
                        measureChild(view);
                        view.layout(viewStart, view.getTop(), viewStart + view.getMeasuredWidth(),
                                view.getTop() + view.getMeasuredHeight());
                    } else {
                        view.offsetLeftAndRight(viewStart - view.getLeft());
                    }
                } else {
                    if (view.isLayoutRequested()) {
                        measureChild(view);
                        view.layout(view.getLeft(), viewStart, view.getLeft() +
                                view.getMeasuredWidth(), viewStart + view.getMeasuredHeight());
                    } else {
                        view.offsetTopAndBottom(viewStart - view.getTop());
                    }
                }
                if (selectedItemCanScale()) {
                    maxSelectedSize = Math.max(maxSelectedSize,
                            getSelectedItemSize(getAdapterIndex(k), view));
                }
            }
            // we might need update mMaxSize/mMaxSelectedSize in case a relayout happens
            for (int k = viewIndex; k <= endViewIndex; k++) {
                View view = getChildAt(k);
                ChildViewHolder h = (ChildViewHolder) view.getTag(R.id.ScrollAdapterViewChild);
                h.mMaxSize = maxSize;
                h.mExtraSpaceLow = 0;
                h.mScrollCenter = computeScrollCenter(k);
            }
            boolean isTransitionFrom = viewIndex <= expandIdx && expandIdx <= endViewIndex;
            boolean isTransitionTo = viewIndex <= nextExpandIdx && nextExpandIdx <= endViewIndex;
            // adding extra space
            if (maxSelectedSize != Integer.MIN_VALUE) {
                int extraSpace = 0;
                if (isTransitionFrom) {
                    extraSpace = (int) ((maxSelectedSize - maxSize) * progress);
                } else if (isTransitionTo) {
                    extraSpace = (int) ((maxSelectedSize - maxSize) * (1 - progress));
                }
                if (extraSpace > 0) {
                    int lowExtraSpace;
                    if (mScroll.mainAxis().getSelectedTakesMoreSpace()) {
                        maxHigh = maxHigh + extraSpace;
                        totalExpandedSize = totalExpandedSize + extraSpace;
                        switch (mScroll.getScrollItemAlign()) {
                            case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
                                lowExtraSpace = extraSpace / 2; // extraSpace added low and high
                                break;
                            case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
                                lowExtraSpace = extraSpace; // extraSpace added on the low
                                break;
                            case ScrollController.SCROLL_ITEM_ALIGN_LOW:
                            default:
                                lowExtraSpace = 0; // extraSpace is added on the high
                                break;
                        }
                    } else {
                        // if we don't add extra space surrounding it,  the view should
                        // grow evenly on low and high
                        lowExtraSpace = extraSpace / 2;
                    }
                    extraSpaceLow += lowExtraSpace;
                    extraSpaceHigh += (extraSpace - lowExtraSpace);
                    for (int k = viewIndex; k <= endViewIndex; k++) {
                        View view = getChildAt(k);
                        if (mScroll.mainAxis().getSelectedTakesMoreSpace()) {
                            if (mOrientation == HORIZONTAL) {
                                view.offsetLeftAndRight(lowExtraSpace);
                            } else {
                                view.offsetTopAndBottom(lowExtraSpace);
                            }
                            ChildViewHolder h = (ChildViewHolder)
                                    view.getTag(R.id.ScrollAdapterViewChild);
                            h.mExtraSpaceLow = lowExtraSpace;
                        }
                    }
                }
            }
            // animate between different expanded view size
            if (canAnimateExpandedSize) {
                for (int k = viewIndex; k <= endViewIndex; k++) {
                    View view = getChildAt(k);
                    ChildViewHolder h = (ChildViewHolder) view.getTag(R.id.ScrollAdapterViewChild);
                    float target = (mOrientation == HORIZONTAL) ? view.getLeft() : view.getTop();
                    if (h.mLocation != target) {
                        if (mOrientation == HORIZONTAL) {
                            view.setTranslationX(h.mLocationInParent - target);
                            view.animate().translationX(0).start();
                        } else {
                            view.setTranslationY(h.mLocationInParent - target);
                            view.animate().translationY(0).start();
                        }
                    }
                }
            }
            // adding expanded size
            if (isTransitionFrom) {
                expandedStart = maxHigh;
                // "low" (next expandable start) is next to current one until fully expanded
                maxHigh += progress == 1f ? expandedSize : 0;
            } else if (isTransitionTo) {
                nextExpandedStart = maxHigh;
                maxHigh += progress == 1f ? nextExpandedSize : expandedSize + nextExpandedSize;
            }
            // assign beginning position for next "off axis"
            low = maxHigh + mSpace;
        }
        mScroll.mainAxis().setAlignExtraOffset(
                (int) (alignExtraOffset * progress + nextAlignExtraOffset * (1 - progress)));
        mScroll.mainAxis().setExpandedSize(totalExpandedSize);
        mScroll.mainAxis().setExtraSpaceLow(extraSpaceLow);
        mScroll.mainAxis().setExtraSpaceHigh(extraSpaceHigh);

        // 5. update expanded views
        for (int j = 0; j < expandedCount;) {
            // remove views in mExpandedViews and are not newly created
            ExpandedView v = mExpandedViews.get(j);
            if (v!= thisExpanded && v!= nextExpanded && v != previousExpanded) {
                if (v.expandedView.hasFocus()) {
                    View expandableView = getChildAt(expandableIndexFromAdapterIndex(v.index));
                     expandableView.requestFocus();
                }
                v.close();
                mExpandedChildStates.saveInvisibleView(v.expandedView, v.index);
                removeViewInLayout(v.expandedView);
                mRecycleExpandedViews.recycleView(v.expandedView, v.viewType);
                mExpandedViews.remove(j);
                expandedCount--;
            } else {
                j++;
            }
        }
        for (int j = 0, size = mExpandedViews.size(); j < size; j++) {
            ExpandedView v = mExpandedViews.get(j);
            int start = v == thisExpanded ? expandedStart : nextExpandedStart;
            if (!(v == previousExpanded || v == nextExpanded && progress == 1f)) {
                v.expandedView.setVisibility(VISIBLE);
            }
            if (mOrientation == HORIZONTAL) {
                if (v.expandedView.isLayoutRequested()) {
                    measureChild(v.expandedView);
                }
                v.expandedView.layout(start, 0, start + v.expandedView.getMeasuredWidth(),
                        v.expandedView.getMeasuredHeight());
            } else {
                if (v.expandedView.isLayoutRequested()) {
                    measureChild(v.expandedView);
                }
                v.expandedView.layout(0, start, v.expandedView.getMeasuredWidth(),
                        start + v.expandedView.getMeasuredHeight());
            }
        }
        for (int j = 0, size = mExpandedViews.size(); j < size; j++) {
            ExpandedView v = mExpandedViews.get(j);
            if (v == previousExpanded || v == nextExpanded && progress == 1f) {
                v.expandedView.setVisibility(View.INVISIBLE);
            }
        }

        // 6. move focus from expandable view to expanded view, disable expandable view after it's
        // expanded
        if (mExpandAdapter != null && hasFocus()) {
            View focusedChild = getFocusedChild();
            int focusedIndex = indexOfChild(focusedChild);
            if (focusedIndex >= firstExpandableIndex()) {
                for (int j = 0, size = mExpandedViews.size(); j < size; j++) {
                    ExpandedView v = mExpandedViews.get(j);
                    if (expandableIndexFromAdapterIndex(v.index) == focusedIndex
                            && v.expandedView.getVisibility() == View.VISIBLE) {
                        v.expandedView.requestFocus();
                    }
                }
            }
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        View view = getSelectedExpandedView();
        if (view != null) {
            return view.requestFocus(direction, previouslyFocusedRect);
        }
        view = getSelectedView();
        if (view != null) {
            return view.requestFocus(direction, previouslyFocusedRect);
        }
        return false;
    }

    private int getScrollCenter(View view) {
        return ((ChildViewHolder) view.getTag(R.id.ScrollAdapterViewChild)).mScrollCenter;
    }

    public int getScrollItemAlign() {
        return mScroll.getScrollItemAlign();
    }

    private boolean hasScrollPosition(int scrollCenter, int maxSize, int scrollPosInMain) {
        switch (mScroll.getScrollItemAlign()) {
        case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
            return scrollCenter - maxSize / 2 - mSpaceLow < scrollPosInMain &&
                    scrollPosInMain < scrollCenter + maxSize / 2 + mSpaceHigh;
        case ScrollController.SCROLL_ITEM_ALIGN_LOW:
            return scrollCenter - mSpaceLow <= scrollPosInMain &&
                    scrollPosInMain < scrollCenter + maxSize + mSpaceHigh;
        case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
            return scrollCenter - maxSize - mSpaceLow < scrollPosInMain &&
                    scrollPosInMain <= scrollCenter + mSpaceHigh;
        }
        return false;
    }

    private boolean hasScrollPositionSecondAxis(int scrollCenterOffAxis, int viewSizeOffAxis,
            int centerOffAxis) {
        return centerOffAxis - viewSizeOffAxis / 2 - mSpaceLow <= scrollCenterOffAxis
                && scrollCenterOffAxis <= centerOffAxis + viewSizeOffAxis / 2 + mSpaceHigh;
    }

    /**
     * Get the center of expandable view in the state that all expandable views are collapsed, i.e.
     * expanded views are excluded from calculating.  The space is included in calculation
     */
    private int computeScrollCenter(int expandViewIndex) {
        int lastIndex = lastExpandableIndex();
        int firstIndex = firstExpandableIndex();
        View firstView = getChildAt(firstIndex);
        int center = 0;
        switch (mScroll.getScrollItemAlign()) {
        case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
            center = getCenter(firstView);
            break;
        case ScrollController.SCROLL_ITEM_ALIGN_LOW:
            center = mOrientation == HORIZONTAL ? firstView.getLeft() : firstView.getTop();
            break;
        case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
            center = mOrientation == HORIZONTAL ? firstView.getRight() : firstView.getBottom();
            break;
        }
        if (mScroll.mainAxis().getSelectedTakesMoreSpace()) {
            center -= ((ChildViewHolder) firstView.getTag(
                    R.id.ScrollAdapterViewChild)).mExtraSpaceLow;
        }
        int nextCenter = -1;
        for (int idx = firstIndex; idx < lastIndex; idx += mItemsOnOffAxis) {
            View view = getChildAt(idx);
            if (idx <= expandViewIndex && expandViewIndex < idx + mItemsOnOffAxis) {
                return center;
            }
            if (idx < lastIndex - mItemsOnOffAxis) {
                // nextView is never null if scrollCenter is larger than center of current view
                View nextView = getChildAt(idx + mItemsOnOffAxis);
                switch (mScroll.getScrollItemAlign()) { // fixme
                    case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
                        nextCenter = center + (getSize(view) + getSize(nextView)) / 2;
                        break;
                    case ScrollController.SCROLL_ITEM_ALIGN_LOW:
                        nextCenter = center + getSize(view);
                        break;
                    case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
                        nextCenter = center + getSize(nextView);
                        break;
                }
                nextCenter += mSpace;
            } else {
                nextCenter = Integer.MAX_VALUE;
            }
            center = nextCenter;
        }
        assertFailure("Scroll out of range?");
        return 0;
    }

    private int getScrollLow(int scrollCenter, View view) {
        ChildViewHolder holder = (ChildViewHolder)view.getTag(R.id.ScrollAdapterViewChild);
        switch (mScroll.getScrollItemAlign()) {
        case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
            return scrollCenter - holder.mMaxSize / 2;
        case ScrollController.SCROLL_ITEM_ALIGN_LOW:
            return scrollCenter;
        case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
            return scrollCenter - holder.mMaxSize;
        }
        return 0;
    }

    private int getScrollHigh(int scrollCenter, View view) {
        ChildViewHolder holder = (ChildViewHolder)view.getTag(R.id.ScrollAdapterViewChild);
        switch (mScroll.getScrollItemAlign()) {
        case ScrollController.SCROLL_ITEM_ALIGN_CENTER:
            return scrollCenter + holder.mMaxSize / 2;
        case ScrollController.SCROLL_ITEM_ALIGN_LOW:
            return scrollCenter + holder.mMaxSize;
        case ScrollController.SCROLL_ITEM_ALIGN_HIGH:
            return scrollCenter;
        }
        return 0;
    }

    /**
     * saves the current item index and scroll information for fully restore from
     */
    final static class AdapterViewState {
        int itemsOnOffAxis;
        int index; // index inside adapter of the current view
        Bundle expandedChildStates = Bundle.EMPTY;
        Bundle expandableChildStates = Bundle.EMPTY;
    }

    final static class SavedState extends BaseSavedState {

        final AdapterViewState theState = new AdapterViewState();

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(theState.itemsOnOffAxis);
            out.writeInt(theState.index);
            out.writeBundle(theState.expandedChildStates);
            out.writeBundle(theState.expandableChildStates);
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };

        SavedState(Parcel in) {
            super(in);
            theState.itemsOnOffAxis = in.readInt();
            theState.index = in.readInt();
            ClassLoader loader = ScrollAdapterView.class.getClassLoader();
            theState.expandedChildStates = in.readBundle(loader);
            theState.expandableChildStates = in.readBundle(loader);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        int index = findViewIndexContainingScrollCenter();
        if (index < 0) {
            return superState;
        }
        mExpandedChildStates.saveVisibleViews();
        mExpandableChildStates.saveVisibleViews();
        ss.theState.itemsOnOffAxis = mItemsOnOffAxis;
        ss.theState.index = getAdapterIndex(index);
        ss.theState.expandedChildStates = mExpandedChildStates.getChildStates();
        ss.theState.expandableChildStates = mExpandableChildStates.getChildStates();
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());
        mLoadingState = ss.theState;
        fireDataSetChanged();
    }

    /**
     * Returns expandable children states policy, returns one of
     * {@link ViewsStateBundle#SAVE_NO_CHILD} {@link ViewsStateBundle#SAVE_VISIBLE_CHILD}
     * {@link ViewsStateBundle#SAVE_LIMITED_CHILD} {@link ViewsStateBundle#SAVE_ALL_CHILD}
     */
    public int getSaveExpandableViewsPolicy() {
        return mExpandableChildStates.getSavePolicy();
    }

    /** See explanation in {@link #getSaveExpandableViewsPolicy()} */
    public void setSaveExpandableViewsPolicy(int saveExpandablePolicy) {
        mExpandableChildStates.setSavePolicy(saveExpandablePolicy);
    }

    /**
     * Returns the limited number of expandable children that will be saved when
     * {@link #getSaveExpandableViewsPolicy()} is {@link ViewsStateBundle#SAVE_LIMITED_CHILD}
     */
    public int getSaveExpandableViewsLimit() {
        return mExpandableChildStates.getLimitNumber();
    }

    /** See explanation in {@link #getSaveExpandableViewsLimit()} */
    public void setSaveExpandableViewsLimit(int saveExpandableChildNumber) {
        mExpandableChildStates.setLimitNumber(saveExpandableChildNumber);
    }

    /**
     * Returns expanded children states policy, returns one of
     * {@link ViewsStateBundle#SAVE_NO_CHILD} {@link ViewsStateBundle#SAVE_VISIBLE_CHILD}
     * {@link ViewsStateBundle#SAVE_LIMITED_CHILD} {@link ViewsStateBundle#SAVE_ALL_CHILD}
     */
    public int getSaveExpandedViewsPolicy() {
        return mExpandedChildStates.getSavePolicy();
    }

    /** See explanation in {@link #getSaveExpandedViewsPolicy} */
    public void setSaveExpandedViewsPolicy(int saveExpandedChildPolicy) {
        mExpandedChildStates.setSavePolicy(saveExpandedChildPolicy);
    }

    /**
     * Returns the limited number of expanded children that will be saved when
     * {@link #getSaveExpandedViewsPolicy()} is {@link ViewsStateBundle#SAVE_LIMITED_CHILD}
     */
    public int getSaveExpandedViewsLimit() {
        return mExpandedChildStates.getLimitNumber();
    }

    /** See explanation in {@link #getSaveExpandedViewsLimit()} */
    public void setSaveExpandedViewsLimit(int mSaveExpandedNumber) {
        mExpandedChildStates.setLimitNumber(mSaveExpandedNumber);
    }

    public ArrayList<OnItemChangeListener> getOnItemChangeListeners() {
        return mOnItemChangeListeners;
    }

    public void setOnItemChangeListener(OnItemChangeListener onItemChangeListener) {
        mOnItemChangeListeners.clear();
        addOnItemChangeListener(onItemChangeListener);
    }

    public void addOnItemChangeListener(OnItemChangeListener onItemChangeListener) {
        if (!mOnItemChangeListeners.contains(onItemChangeListener)) {
            mOnItemChangeListeners.add(onItemChangeListener);
        }
    }

    public ArrayList<OnScrollListener> getOnScrollListeners() {
        return mOnScrollListeners;
    }

    public void setOnScrollListener(OnScrollListener onScrollListener) {
        mOnScrollListeners.clear();
        addOnScrollListener(onScrollListener);
    }

    public void addOnScrollListener(OnScrollListener onScrollListener) {
        if (!mOnScrollListeners.contains(onScrollListener)) {
            mOnScrollListeners.add(onScrollListener);
        }
    }

    public void setExpandedItemInAnim(Animator animator) {
        mExpandedItemInAnim = animator;
    }

    public Animator getExpandedItemInAnim() {
        return mExpandedItemInAnim;
    }

    public void setExpandedItemOutAnim(Animator animator) {
        mExpandedItemOutAnim = animator;
    }

    public Animator getExpandedItemOutAnim() {
        return mExpandedItemOutAnim;
    }

    public boolean isNavigateOutOfOffAxisAllowed() {
        return mNavigateOutOfOffAxisAllowed;
    }

    public boolean isNavigateOutAllowed() {
        return mNavigateOutAllowed;
    }

    /**
     * if allow DPAD key in secondary axis to navigate out of ScrollAdapterView
     */
    public void setNavigateOutOfOffAxisAllowed(boolean navigateOut) {
        mNavigateOutOfOffAxisAllowed = navigateOut;
    }

    /**
     * if allow DPAD key in main axis to navigate out of ScrollAdapterView
     */
    public void setNavigateOutAllowed(boolean navigateOut) {
        mNavigateOutAllowed = navigateOut;
    }

    public boolean isNavigateInAnimationAllowed() {
        return mNavigateInAnimationAllowed;
    }

    /**
     * if {@code true} allow DPAD event from trackpadNavigation when ScrollAdapterView is in
     * animation, this does not affect physical keyboard or manually calling arrowScroll()
     */
    public void setNavigateInAnimationAllowed(boolean navigateInAnimation) {
        mNavigateInAnimationAllowed = navigateInAnimation;
    }

    /** set space in pixels between two items */
    public void setSpace(int space) {
        mSpace = space;
        // mSpace may not be evenly divided by 2
        mSpaceLow = mSpace / 2;
        mSpaceHigh = mSpace - mSpaceLow;
    }

    /** get space in pixels between two items */
    public int getSpace() {
        return mSpace;
    }

    /** set pixels of selected item, use {@link ScrollAdapterCustomSize} for more complicated case */
    public void setSelectedSize(int selectedScale) {
        mSelectedSize = selectedScale;
    }

    /** get pixels of selected item */
    public int getSelectedSize() {
        return mSelectedSize;
    }

    public void setSelectedTakesMoreSpace(boolean selectedTakesMoreSpace) {
        mScroll.mainAxis().setSelectedTakesMoreSpace(selectedTakesMoreSpace);
    }

    public boolean getSelectedTakesMoreSpace() {
        return mScroll.mainAxis().getSelectedTakesMoreSpace();
    }

    private boolean selectedItemCanScale() {
        return mSelectedSize != 0 || mAdapterCustomSize != null;
    }

    private int getSelectedItemSize(int adapterIndex, View view) {
        if (mSelectedSize != 0) {
            return mSelectedSize;
        } else if (mAdapterCustomSize != null) {
            return mAdapterCustomSize.getSelectItemSize(adapterIndex, view);
        }
        return 0;
    }

    private static void assertFailure(String msg) {
        throw new RuntimeException(msg);
    }

}
