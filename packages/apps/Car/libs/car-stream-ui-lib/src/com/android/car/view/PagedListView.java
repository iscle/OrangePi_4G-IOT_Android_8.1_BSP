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
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.android.car.stream.ui.R;

/**
 * Custom {@link android.support.v7.widget.RecyclerView} that displays a list of items that
 * resembles a {@link android.widget.ListView} but also has page up and page down arrows
 * on the right side.
 */
public class PagedListView extends FrameLayout {
    private static final String TAG = "PagedListView";

    /**
     * The amount of time after settling to wait before autoscrolling to the next page when the
     * user holds down a pagination button.
     */
    private static final int PAGINATION_HOLD_DELAY_MS = 400;
    private static final int INVALID_RESOURCE_ID = -1;

    private final CarRecyclerView mRecyclerView;
    private final CarLayoutManager mLayoutManager;
    private final PagedScrollBarView mScrollBarView;
    private final Handler mHandler = new Handler();
    private DividerDecoration mDecor;

    /** Maximum number of pages to show. Values < 0 show all pages. */
    private int mMaxPages = -1;
    /** Number of visible rows per page */
    private int mRowsPerPage = -1;

    /**
     * Used to check if there are more items added to the list.
     */
    private int mLastItemCount = 0;

    private RecyclerView.Adapter<? extends RecyclerView.ViewHolder> mAdapter;

    private boolean mNeedsFocus;
    private OnScrollBarListener mOnScrollBarListener;

    /**
     * Interface for a {@link android.support.v7.widget.RecyclerView.Adapter} to cap the
     * number of items.
     * <p>NOTE: it is still up to the adapter to use maxItems in
     * {@link android.support.v7.widget.RecyclerView.Adapter#getItemCount()}.
     *
     * the recommended way would be with:
     * <pre>
     * @Override
     * public int getItemCount() {
     *     return Math.min(super.getItemCount(), mMaxItems);
     * }
     * </pre>
     */
    public interface ItemCap {
        int UNLIMITED = -1;

        /**
         * Sets the maximum number of items available in the adapter. A value less than '0'
         * means the list should not be capped.
         */
        void setMaxItems(int maxItems);
    }

    public PagedListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /*defStyleAttrs*/, 0 /*defStyleRes*/);
    }

    public PagedListView(Context context, AttributeSet attrs, int defStyleAttrs) {
        this(context, attrs, defStyleAttrs, 0 /*defStyleRes*/);
    }

    public PagedListView(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.PagedListView, defStyleAttrs, defStyleRes);
        LayoutInflater.from(context)
                .inflate(R.layout.car_paged_recycler_view, this /*root*/, true /*attachToRoot*/);
        int scrollContainerWidth = getResources().getDimensionPixelSize(
                R.dimen.car_drawer_button_container_width);
        if (a.hasValue(R.styleable.PagedListView_scrollbarContainerWidth)) {
            scrollContainerWidth = a.getDimensionPixelSize(
                    R.styleable.PagedListView_scrollbarContainerWidth,
                    scrollContainerWidth);
            FrameLayout scrollContainer = (FrameLayout) findViewById(R.id.scroll_container);
            LayoutParams params = (LayoutParams) scrollContainer.getLayoutParams();
            params.width = scrollContainerWidth;
            scrollContainer.setLayoutParams(params);
        }

        boolean offsetScrollBar = a.getBoolean(R.styleable.PagedListView_offsetScrollBar, false);
        if (offsetScrollBar) {
            FrameLayout maxWidthLayout = (FrameLayout) findViewById(R.id.max_width_layout);
            LayoutParams params = (LayoutParams) maxWidthLayout.getLayoutParams();
            params.leftMargin = scrollContainerWidth;
            params.rightMargin = a.getDimensionPixelSize(R.styleable.PagedListView_rightMargin, 0);
            maxWidthLayout.setLayoutParams(params);
        }
        mRecyclerView = (CarRecyclerView) findViewById(R.id.recycler_view);
        boolean fadeLastItem = a.getBoolean(R.styleable.PagedListView_fadeLastItem, false);
        mRecyclerView.setFadeLastItem(fadeLastItem);
        boolean offsetRows = a.getBoolean(R.styleable.PagedListView_offsetRows, false);

        mMaxPages = getDefaultMaxPages();

        mLayoutManager = new CarLayoutManager(context);
        mLayoutManager.setOffsetRows(offsetRows);
        mLayoutManager.setItemsChangedListener(mItemsChangedListener);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setOnScrollListener(mOnScrollListener);
        mRecyclerView.getRecycledViewPool().setMaxRecycledViews(0, 12);
        mRecyclerView.setItemAnimator(new CarItemAnimator(mLayoutManager));

        if (a.getBoolean(R.styleable.PagedListView_showDivider, true)) {
            int dividerStartMargin = a.getDimensionPixelSize(
                    R.styleable.PagedListView_dividerStartMargin, 0);
            int dividerStartId = a.getResourceId(R.styleable.PagedListView_alignDividerStartTo,
                    INVALID_RESOURCE_ID);
            int dividerEndId = a.getResourceId(R.styleable.PagedListView_alignDividerEndTo,
                    INVALID_RESOURCE_ID);

            mRecyclerView.addItemDecoration(new DividerDecoration(context, dividerStartMargin,
                    dividerStartId, dividerEndId));
        }

        mScrollBarView = (PagedScrollBarView) findViewById(R.id.paged_scroll_view);
        mScrollBarView.setPaginationListener(new PagedScrollBarView.PaginationListener() {
            @Override
            public void onPaginate(int direction) {
                if (direction == PagedScrollBarView.PaginationListener.PAGE_UP) {
                    mRecyclerView.pageUp();
                } else if (direction == PagedScrollBarView.PaginationListener.PAGE_DOWN) {
                    mRecyclerView.pageDown();
                } else {
                    Log.e(TAG, "Unknown pagination direction (" + direction + ")");
                }
            }
        });

        setAutoDayNightMode();
        updatePaginationButtons(false /*animate*/);

        a.recycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mUpdatePaginationRunnable);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            // The user has interacted with the list using touch. All movements will now paginate
            // the list.
            mLayoutManager.setRowOffsetMode(CarLayoutManager.ROW_OFFSET_MODE_PAGE);
        }
        return super.onInterceptTouchEvent(e);
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        // The user has interacted with the list using the controller. Movements through the list
        // will now be one row at a time.
        mLayoutManager.setRowOffsetMode(CarLayoutManager.ROW_OFFSET_MODE_INDIVIDUAL);
    }

    public int positionOf(@Nullable View v) {
        if (v == null || v.getParent() != mRecyclerView) {
            return -1;
        }
        return mLayoutManager.getPosition(v);
    }

    @NonNull
    public CarRecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public void scrollToPosition(int position) {
        mLayoutManager.scrollToPosition(position);

        // Sometimes #scrollToPosition doesn't change the scroll state so we need to make sure
        // the pagination arrows actually get updated.
        mHandler.post(mUpdatePaginationRunnable);
    }

    /**
     * Sets the adapter for the list.
     * <p>It <em>must</em> implement {@link ItemCap}, otherwise, will throw
     * an {@link IllegalArgumentException}.
     */
    public void setAdapter(
            @NonNull RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
        if (!(adapter instanceof ItemCap)) {
            throw new IllegalArgumentException("ERROR: adapter "
                    + "[" + adapter.getClass().getCanonicalName() + "] MUST implement ItemCap");
        }

        mAdapter = adapter;
        mRecyclerView.setAdapter(adapter);
        tryUpdateMaxPages();
    }

    @NonNull
    public CarLayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public RecyclerView.Adapter<? extends RecyclerView.ViewHolder> getAdapter() {
        return mRecyclerView.getAdapter();
    }

    public void setMaxPages(int maxPages) {
        mMaxPages = maxPages;
        tryUpdateMaxPages();
    }

    public int getMaxPages() {
        return mMaxPages;
    }

    public void resetMaxPages() {
        mMaxPages = getDefaultMaxPages();
    }

    public void addItemDecoration(@NonNull RecyclerView.ItemDecoration decor) {
        mRecyclerView.addItemDecoration(decor);
    }

    public void removeItemDecoration(@NonNull RecyclerView.ItemDecoration decor) {
        mRecyclerView.removeItemDecoration(decor);
    }

    /**
     * Sets the scrollbars of this PagedListView to change from light to dark colors depending on
     * whether or not device is in night mode.
     */
    public void setAutoDayNightMode() {
        mScrollBarView.setAutoDayNightMode();
    }

    /**
     * Sets the scrollbars of this PagedListView to be light colors.
     */
    public void setLightMode() {
        mScrollBarView.setLightMode();
    }

    /**
     * Sets the scrollbars of this PagedListView to be dark colors.
     */
    public void setDarkMode() {
        mScrollBarView.setDarkMode();
    }

    public void setOnScrollBarListener(OnScrollBarListener listener) {
        mOnScrollBarListener = listener;
    }

    /** Returns the page the given position is on, starting with page 0. */
    public int getPage(int position) {
        if (mRowsPerPage == -1) {
            return -1;
        }
        return position / mRowsPerPage;
    }

    /** Returns the default number of pages the list should have */
    protected int getDefaultMaxPages() {
        // assume list shown in response to a click, so, reduce number of clicks by one
        //return ProjectionUtils.getMaxClicks(getContext().getContentResolver()) - 1;
        return 5;
    }

    private void tryUpdateMaxPages() {
        if (mAdapter == null) {
            return;
        }

        View firstChild = mLayoutManager.getChildAt(0);
        int firstRowHeight = firstChild == null ? 0 : firstChild.getHeight();
        mRowsPerPage = firstRowHeight == 0 ? 1 : getHeight() / firstRowHeight;

        int newMaxItems;
        if (mMaxPages < 0) {
            newMaxItems = -1;
        } else if (mMaxPages == 0) {
            // At the last click of 6 click limit, we show one more warning item at the top of menu.
            newMaxItems = mRowsPerPage + 1;
        } else {
            newMaxItems = mRowsPerPage * mMaxPages;
        }

        int originalCount = mAdapter.getItemCount();
        ((ItemCap) mAdapter).setMaxItems(newMaxItems);
        int newCount = mAdapter.getItemCount();
        if (newCount < originalCount) {
            mAdapter.notifyItemRangeChanged(newCount, originalCount);
        } else if (newCount > originalCount) {
            mAdapter.notifyItemInserted(originalCount);
        }
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // if a late item is added to the top of the layout after the layout is stabilized, causing
        // the former top item to be pushed to the 2nd page, the focus will still be on the former
        // top item. Since our car layout manager tries to scroll the viewport so that the focused
        // item is visible, the view port will be on the 2nd page. That means the newly added item
        // will not be visible, on the first page.

        // what we want to do is: if the formerly focused item is the first one in the list, any
        // item added above it will make the focus to move to the new first item.
        // if the focus is not on the formerly first item, then we don't need to do anything. Let
        // the layout manager do the job and scroll the viewport so the currently focused item
        // is visible.

        // we need to calculate whether we want to request focus here, before the super call,
        // because after the super call, the first born might be changed.
        View focusedChild = mLayoutManager.getFocusedChild();
        View firstBorn = mLayoutManager.getChildAt(0);

        super.onLayout(changed, left, top, right, bottom);

        if (mAdapter != null) {
            int itemCount = mAdapter.getItemCount();
           // if () {
                Log.d(TAG, String.format(
                        "onLayout hasFocus: %s, mLastItemCount: %s, itemCount: %s, focusedChild: " +
                                "%s, firstBorn: %s, isInTouchMode: %s, mNeedsFocus: %s",
                        hasFocus(), mLastItemCount, itemCount, focusedChild, firstBorn,
                        isInTouchMode(), mNeedsFocus));
          //  }
            tryUpdateMaxPages();
            // This is a workaround for missing focus because isInTouchMode() is not always
            // returning the right value.
            // This is okay for the Engine release since focus is always showing.
            // However, in Tala and Fender, we want to show focus only when the user uses
            // hardware controllers, so we need to revisit this logic. b/22990605.
            if (mNeedsFocus && itemCount > 0) {
                if (focusedChild == null) {
                    requestFocusFromTouch();
                }
                mNeedsFocus = false;
            }
            if (itemCount > mLastItemCount && focusedChild == firstBorn &&
                    getContext().getResources().getBoolean(R.bool.has_wheel)) {
                requestFocusFromTouch();
            }
            mLastItemCount = itemCount;
        }
        updatePaginationButtons(true /*animate*/);
    }

    @Override
    public boolean requestFocus(int direction, Rect rect) {
        if (getContext().getResources().getBoolean(R.bool.has_wheel)) {
            mNeedsFocus = true;
        }
        return super.requestFocus(direction, rect);
    }

    public View findViewByPosition(int position) {
        return mLayoutManager.findViewByPosition(position);
    }

    private void updatePaginationButtons(boolean animate) {
        boolean isAtTop = mLayoutManager.isAtTop();
        boolean isAtBottom = mLayoutManager.isAtBottom();
        if (isAtTop && isAtBottom) {
            mScrollBarView.setVisibility(View.INVISIBLE);
        } else {
            mScrollBarView.setVisibility(View.VISIBLE);
        }
        mScrollBarView.setUpEnabled(!isAtTop);
        mScrollBarView.setDownEnabled(!isAtBottom);

        mScrollBarView.setParameters(
                mRecyclerView.computeVerticalScrollRange(),
                mRecyclerView.computeVerticalScrollOffset(),
                mRecyclerView.computeVerticalScrollExtent(),
                animate);
        invalidate();
    }

    private final RecyclerView.OnScrollListener mOnScrollListener =
            new RecyclerView.OnScrollListener() {

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (mOnScrollBarListener != null) {
                if (!mLayoutManager.isAtTop() && mLayoutManager.isAtBottom()) {
                    mOnScrollBarListener.onReachBottom();
                }
                if (mLayoutManager.isAtTop() || !mLayoutManager.isAtBottom()) {
                    mOnScrollBarListener.onLeaveBottom();
                }
            }
            updatePaginationButtons(false);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mHandler.postDelayed(mPaginationRunnable, PAGINATION_HOLD_DELAY_MS);
            }
        }
    };
    private final Runnable mPaginationRunnable = new Runnable() {
        @Override
        public void run() {
            boolean upPressed = mScrollBarView.isUpPressed();
            boolean downPressed = mScrollBarView.isDownPressed();
            if (upPressed && downPressed) {
                // noop
            } else if (upPressed) {
                mRecyclerView.pageUp();
            } else if (downPressed) {
                mRecyclerView.pageDown();
            }
        }
    };

    private final Runnable mUpdatePaginationRunnable = new Runnable() {
        @Override
        public void run() {
            updatePaginationButtons(true /*animate*/);
        }
    };

    private final CarLayoutManager.OnItemsChangedListener mItemsChangedListener =
            new CarLayoutManager.OnItemsChangedListener() {
                @Override
                public void onItemsChanged() {
                    updatePaginationButtons(true /*animate*/);
                }
            };

    abstract static public class OnScrollBarListener {
        public void onReachBottom() {}
        public void onLeaveBottom() {}
    }

    /**
     * A {@link android.support.v7.widget.RecyclerView.ItemDecoration} that will draw a dividing
     * line between each item in the RecyclerView that it is added to.
     */
    public static class DividerDecoration extends RecyclerView.ItemDecoration {
        private final Paint mPaint;
        private final int mDividerHeight;
        private final int mDividerStartMargin;
        @IdRes private final int mDividerStartId;
        @IdRes private final int mDvidierEndId;

        /**
         * @param dividerStartMargin The start offset of the dividing line. This offset will be
         *                           relative to {@code dividerStartId} if that value is given.
         * @param dividerStartId A child view id whose starting edge will be used as the starting
         *                       edge of the dividing line. If this value is
         *                       {@link #INVALID_RESOURCE_ID}, the the top container of each
         *                       child view will be used.
         * @param dividerEndId A child view id whose ending edge will be used as the starting edge
         *                     of the dividing lin.e If this value is {@link #INVALID_RESOURCE_ID},
         *                     then the top container view of each child will be used.
         */
        private DividerDecoration(Context context, int dividerStartMargin,
                @IdRes int dividerStartId, @IdRes int dividerEndId) {
            mDividerStartMargin = dividerStartMargin;
            mDividerStartId = dividerStartId;
            mDvidierEndId = dividerEndId;

            Resources res = context.getResources();
            mPaint = new Paint();
            mPaint.setColor(res.getColor(R.color.car_list_divider));
            mDividerHeight = res.getDimensionPixelSize(R.dimen.car_divider_height);
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            for (int i = 0, childCount = parent.getChildCount(); i < childCount; i++) {
                View container = parent.getChildAt(i);
                View startChild = mDividerStartId != INVALID_RESOURCE_ID
                        ? container.findViewById(mDividerStartId)
                        : container;

                View endChild = mDvidierEndId != INVALID_RESOURCE_ID
                        ? container.findViewById(mDvidierEndId)
                        : container;

                if (startChild == null || endChild == null) {
                    continue;
                }

                int left = mDividerStartMargin + startChild.getLeft();
                int right = endChild.getRight();
                int bottom = container.getBottom();
                int top = bottom - mDividerHeight;

                // Draw a divider line between each item. No need to draw the line for the last
                // item.
                if (i != childCount - 1) {
                    c.drawRect(left, top, right, bottom, mPaint);
                }
            }
        }
    }
}
