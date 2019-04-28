/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.radio;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Observable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.util.ArrayList;

/**
 * A View that displays a vertical list of child views provided by a {@link CarouselView.Adapter}.
 * The Views can be shifted up and down and will loop backwards on itself if the end is reached.
 * The View that is considered first to be displayed can be offset by a given amount, and the rest
 * of the Views will sandwich that first View.
 */
public class CarouselView extends ViewGroup {
    private static final String TAG = "CarouselView";

    /**
     * The alpha is that is used for the view considered first in the carousel.
     */
    private static final float FIRST_VIEW_ALPHA = 1.f;

    /**
     * The alpha for all the other views in the carousel.
     */
    private static final float DEFAULT_VIEW_ALPHA = 0.24f;

    private CarouselView.Adapter mAdapter;
    private int mTopOffset;
    private int mItemMargin;

    /**
     * The position into the the data set in {@link #mAdapter} that will be displayed as the first
     * item in the carousel.
     */
    private int mStartPosition;

    /**
     * The number of views in {@link #mScrapViews} that have been bound with data and should be
     * displayed in the carousel. This number can be different from the size of {@code mScrapViews}.
     */
    private int mBoundViews;

    /**
     * A {@link ArrayList} of scrap Views that can be used to populate the carousel. The views
     * contained in this scrap will be the ones that are returned {@link #mAdapter}.
     */
    private ArrayList<View> mScrapViews = new ArrayList<>();

    public CarouselView(Context context) {
        super(context);
        init(context, null);
    }

    public CarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CarouselView(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context, attrs);
    }

    public CarouselView(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        init(context, attrs);
    }

    /**
     * Initializes the starting top offset and margins between each of the items in the carousel.
     */
    private void init(Context context, AttributeSet attrs) {
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.CarouselView);

        try {
            setTopOffset(ta.getDimensionPixelSize(R.styleable.CarouselView_topOffset, 0));
            setItemMargins(ta.getDimensionPixelSize(R.styleable.CarouselView_itemMargins, 0));
        } finally {
            ta.recycle();
        }
    }

    /**
     * Sets the adapter that will provide the Views to be displayed in the carousel.
     */
    public void setAdapter(CarouselView.Adapter adapter) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setAdapter(): " + adapter);
        }

        if (mAdapter != null) {
            mAdapter.unregisterAll();
        }

        mAdapter = adapter;

        // Clear the scrap views because the Views returned from the adapter can be different from
        // an adapter that was previously set.
        mScrapViews.clear();

        if (mAdapter != null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "adapter item count: " + adapter.getItemCount());
            }

            mScrapViews.ensureCapacity(adapter.getItemCount());
            mAdapter.registerObserver(this);
        }
    }

    /**
     * Sets the amount by which the first view in the carousel will be offset from the top of the
     * carousel. The last item and second item will sandwich this first view and expand upwards
     * and downwards respectively as space permits.
     *
     * <p>This value can be set in XML with the value {@code app:topOffset}.
     */
    public void setTopOffset(int topOffset) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setTopOffset(): " + topOffset);
        }

        mTopOffset = topOffset;
    }

    /**
     * Sets the amount of space between each item in the carousel.
     *
     * <p>This value can be set in XML with the value {@code app:itemMargins}.
     */
    public void setItemMargins(int itemMargin) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setItemMargins(): " + itemMargin);
        }

        mItemMargin = itemMargin;
    }

    /**
     * Shifts the carousel to the specified position.
     */
    public void shiftToPosition(int position) {
        if (mAdapter == null || position >= mAdapter.getItemCount() || position < 0) {
            return;
        }

        mStartPosition = position;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onMeasure()");
        }

        removeAllViewsInLayout();

        // If there is no adapter, then have the carousel take up no space.
        if (mAdapter == null) {
            Log.w(TAG, "No adapter set on this CarouselView. "
                    + "Setting measured dimensions as (0, 0)");
            setMeasuredDimension(0, 0);
            return;
        }

        int widthMode = MeasureSpec.getMode(widthSpec);
        int heightMode = MeasureSpec.getMode(heightSpec);

        int requestedHeight;
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            requestedHeight = getDefaultHeight();
        } else {
            requestedHeight = MeasureSpec.getSize(heightSpec);
        }

        int requestedWidth;
        if (widthMode == MeasureSpec.UNSPECIFIED) {
            requestedWidth = getDefaultWidth();
        } else {
            requestedWidth = MeasureSpec.getSize(widthSpec);
        }

        // The children of this carousel can take up as much space as this carousel has been
        // set to.
        int childWidthSpec = MeasureSpec.makeMeasureSpec(requestedWidth, MeasureSpec.AT_MOST);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(requestedHeight, MeasureSpec.AT_MOST);

        int availableHeight = requestedHeight;
        int largestWidth = 0;
        int itemCount = mAdapter.getItemCount();
        int currentAdapterPosition = mStartPosition;

        mBoundViews = 0;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("onMeasure(); requestedWidth: %d, requestedHeight: %d, "
                    + "availableHeight: %d", requestedWidth, requestedHeight, availableHeight));
        }

        int availableHeightDownwards = availableHeight - mTopOffset;

        // Starting from the top offset, measure the views that can fit downwards.
        while (availableHeightDownwards >= 0) {
            View childView = getChildView(mBoundViews);

            mAdapter.bindView(childView, currentAdapterPosition,
                    currentAdapterPosition == mStartPosition);
            mBoundViews++;

            // Ensure that only the first view has full alpha.
            if (currentAdapterPosition == mStartPosition) {
                childView.setAlpha(FIRST_VIEW_ALPHA);
            } else {
                childView.setAlpha(DEFAULT_VIEW_ALPHA);
            }

            childView.measure(childWidthSpec, childHeightSpec);

            largestWidth = Math.max(largestWidth, childView.getMeasuredWidth());
            availableHeightDownwards -= childView.getMeasuredHeight();

            // Wrap the current adapter position if necessary.
            if (++currentAdapterPosition == itemCount) {
                currentAdapterPosition = 0;
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Measuring views downwards; current position: "
                        + currentAdapterPosition);
            }

            // Break if there are no more views to bind.
            if (mBoundViews == itemCount) {
                break;
            }
        }

        int availableHeightUpwards = mTopOffset;
        currentAdapterPosition = mStartPosition;

        // Starting from the top offset, measure the views that can fit upwards.
        while (availableHeightUpwards >= 0) {
            // Wrap the current adapter position if necessary.
            if (--currentAdapterPosition < 0) {
                currentAdapterPosition = itemCount - 1;
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Measuring views upwards; current position: "
                        + currentAdapterPosition);
            }

            View childView = getChildView(mBoundViews);

            mAdapter.bindView(childView, currentAdapterPosition,
                    currentAdapterPosition == mStartPosition);
            mBoundViews++;

            // We know that the first view will be measured in the "downwards" pass, so all these
            // views can have DEFAULT_VIEW_ALPHA.
            childView.setAlpha(DEFAULT_VIEW_ALPHA);
            childView.measure(childWidthSpec, childHeightSpec);

            largestWidth = Math.max(largestWidth, childView.getMeasuredWidth());
            availableHeightUpwards -= childView.getMeasuredHeight();

            // Break if there are no more views to bind.
            if (mBoundViews == itemCount) {
                break;
            }
        }

        int width = widthMode == MeasureSpec.EXACTLY
                ? requestedWidth
                : Math.min(largestWidth, requestedWidth);

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("Measure finished. Largest width is %s; "
                    + "setting final width as %s.", largestWidth, width));
        }

        setMeasuredDimension(width, requestedHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int height = b - t;
        int width = r - l;

        int top = mTopOffset;
        int viewsLaidOut = 0;
        int currentPosition = 0;
        LayoutParams layoutParams = getLayoutParams();

        // Double check that the item count has not changed since the views have been bound.
        if (mBoundViews > mAdapter.getItemCount()) {
            return;
        }

        // Start laying out the views from the first position downwards.
        for (; viewsLaidOut < mBoundViews; viewsLaidOut++) {
            View childView = mScrapViews.get(currentPosition);
            addViewInLayout(childView, -1, layoutParams);
            int measuredHeight = childView.getMeasuredHeight();

            childView.layout(width - childView.getMeasuredWidth(), top, width,
                    top + measuredHeight);

            top += mItemMargin + measuredHeight;

            // Wrap the current position if necessary.
            if (++currentPosition >= mBoundViews) {
                currentPosition = 0;
            }

            // Check if there is still space to fit another view. If not, then stop layout.
            if (top >= height) {
                // Increase the number of views laid out by 1 since this usually will happen at the
                // end of the loop, but we are breaking out of it.
                viewsLaidOut++;
                break;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("onLayout(). First pass laid out %s views", viewsLaidOut));
        }

        // Reset the top position to the first position's top and the starting position.
        top = mTopOffset;
        currentPosition = 0;

        // Now, if there are any views remaining, back-fill the space above the first position.
        for (; viewsLaidOut < mBoundViews; viewsLaidOut++) {
            // Wrap the current position if necessary. Since this is a back-fill, we will subtract
            // from the current position.
            if (--currentPosition < 0) {
                currentPosition = mBoundViews - 1;
            }

            View childView = mScrapViews.get(currentPosition);
            addViewInLayout(childView, -1, layoutParams);
            int measuredHeight = childView.getMeasuredHeight();

            top -= measuredHeight + mItemMargin;

            childView.layout(width - childView.getMeasuredWidth(), top, width,
                    top + measuredHeight);

            // Check if there is still space to fit another view.
            if (top <= 0) {
                // Although this value is not technically needed, increasing its value so that the
                // debug statement will print out the correct value.
                viewsLaidOut++;
                break;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, String.format("onLayout(). Second pass total laid out %s views",
                    viewsLaidOut));
        }
    }

    /**
     * Returns the {@link View} that should be drawn at the given position.
     */
    private View getChildView(int position) {
        View childView;

        // Check if there is already a View in the scrap pile of Views that can be used. Otherwise,
        // create a new View and add it to the scrap.
        if (mScrapViews.size() > position) {
            childView = mScrapViews.get(position);
        } else {
            childView = mAdapter.createView(this /* parent */);
            mScrapViews.add(childView);
        }

        return childView;
    }

    /**
     * Returns the default height that the {@link CarouselView} will take up. This will be the
     * height of the current screen.
     */
    private int getDefaultHeight() {
        return getDisplayMetrics(getContext()).heightPixels;
    }

    /**
     * Returns the default width that the {@link CarouselView} will take up. This will be the width
     * of the current screen.
     */
    private int getDefaultWidth() {
        return getDisplayMetrics(getContext()).widthPixels;
    }

    /**
     * Returns a {@link DisplayMetrics} object that can be used to query the height and width of the
     * current device's screen.
     */
    private static DisplayMetrics getDisplayMetrics(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics;
    }

    /**
     * A data set adapter for the {@link CarouselView} that is responsible for providing the views
     * to be displayed as well as binding data on those views.
     */
    public static abstract class Adapter extends Observable<CarouselView> {
        /**
         * Returns a View to be displayed. The views returned should all be the same.
         *
         * @param parent The {@link CarouselView} that the views will be attached to.
         * @return A non-{@code null} View.
         */
        public abstract View createView(ViewGroup parent);

        /**
         * Binds the given View with data. The View passed to this method will be the same View
         * returned by {@link #createView(ViewGroup)}.
         *
         * @param view The View to bind with data.
         * @param position The position of the View in the carousel.
         * @param isFirstView {@code true} if the view being bound is the first view in the
         *                    carousel.
         */
        public abstract void bindView(View view, int position, boolean isFirstView);

        /**
         * Returns the total number of unique items that will be displayed in the
         * {@link CarouselView}.
         */
        public abstract int getItemCount();

        /**
         * Notify the {@link CarouselView} that the data set has changed. This will cause the
         * {@link CarouselView} to re-layout itself.
         */
        public final void notifyDataSetChanged() {
            if (mObservers.size() > 0) {
                for (CarouselView carouselView : mObservers) {
                    carouselView.requestLayout();
                }
            }
        }
    }
}
