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

package com.android.documentsui.dirlist;

import android.graphics.Point;
import android.support.annotation.VisibleForTesting;
import android.view.DragEvent;
import android.view.View;
import android.view.View.OnDragListener;

import com.android.documentsui.ItemDragListener;
import com.android.documentsui.ItemDragListener.DragHost;
import com.android.documentsui.ui.ViewAutoScroller;
import com.android.documentsui.ui.ViewAutoScroller.ScrollActionDelegate;
import com.android.documentsui.ui.ViewAutoScroller.ScrollDistanceDelegate;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * This class acts as a middle-man handler for potential auto-scrolling before passing the dragEvent
 * onto {@link DirectoryDragListener}.
 */
class DragHoverListener implements OnDragListener {

    private final ItemDragListener<? extends DragHost> mDragHandler;
    private final IntSupplier mHeight;
    private final BooleanSupplier mCanScrollUp;
    private final BooleanSupplier mCanScrollDown;
    private final Runnable mDragScroller;

    /**
     * Predicate to tests whether it's the scroll view ({@link DirectoryFragment#mRecView}) itself.
     *
     * {@link DragHoverListener} is used for both {@link DirectoryFragment#mRecView} and its
     * children. When we decide whether it's in the scroll zone we need to obtain the coordinate
     * relative to {@link DirectoryFragment#mRecView} so we need to transform the coordinate if the
     * view that gets drag and drop events is a child of {@link DirectoryFragment#mRecView}.
     */
    private final Predicate<View> mIsScrollView;

    private boolean mDragHappening;
    private @Nullable Point mCurrentPosition;

    @VisibleForTesting
    DragHoverListener(
            ItemDragListener<? extends DragHost> dragHandler,
            IntSupplier heightSupplier,
            Predicate<View> isScrollView,
            BooleanSupplier scrollUpSupplier,
            BooleanSupplier scrollDownSupplier,
            ViewAutoScroller.ScrollActionDelegate actionDelegate) {
        mDragHandler = dragHandler;
        mHeight = heightSupplier;
        mIsScrollView = isScrollView;
        mCanScrollUp = scrollUpSupplier;
        mCanScrollDown = scrollDownSupplier;

        ScrollDistanceDelegate distanceDelegate = new ScrollDistanceDelegate() {
            @Override
            public Point getCurrentPosition() {
                return mCurrentPosition;
            }

            @Override
            public int getViewHeight() {
                return mHeight.getAsInt();
            }

            @Override
            public boolean isActive() {
                return mDragHappening;
            }
        };

        mDragScroller = new ViewAutoScroller(distanceDelegate, actionDelegate);
    }

    static DragHoverListener create(
            ItemDragListener<? extends DragHost> dragHandler,
            View scrollView) {
        ScrollActionDelegate actionDelegate = new ScrollActionDelegate() {
            @Override
            public void scrollBy(int dy) {
                scrollView.scrollBy(0, dy);
            }

            @Override
            public void runAtNextFrame(Runnable r) {
                scrollView.postOnAnimation(r);

            }

            @Override
            public void removeCallback(Runnable r) {
                scrollView.removeCallbacks(r);
            }
        };
        DragHoverListener listener = new DragHoverListener(
                dragHandler,
                scrollView::getHeight,
                (view) -> (scrollView == view),
                () -> scrollView.canScrollVertically(-1),
                () -> scrollView.canScrollVertically(1),
                actionDelegate);
        return listener;
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                mDragHappening = true;
                break;
            case DragEvent.ACTION_DRAG_ENDED:
                mDragHappening = false;
                break;
            case DragEvent.ACTION_DRAG_LOCATION:
                handleLocationEvent(v, event.getX(), event.getY());
                break;
            default:
                break;
        }

        // Always forward events to the drag handler for item highlight, spring load, etc.
        return mDragHandler.onDrag(v, event);
    }

    private boolean handleLocationEvent(View v, float x, float y) {
        mCurrentPosition = transformToScrollViewCoordinate(v, x, y);
        if (insideDragZone()) {
            mDragScroller.run();
            return true;
        }
        return false;
    }

    private Point transformToScrollViewCoordinate(View v, float x, float y) {
        // Check if v is the RecyclerView itself. If not we need to transform the coordinate to
        // relative to the RecyclerView because we need to test the scroll zone in the coordinate
        // relative to the RecyclerView; if yes we don't need to transform coordinates.
        final boolean isScrollView = mIsScrollView.test(v);
        final float offsetX = isScrollView ? 0 : v.getX();
        final float offsetY = isScrollView ? 0 : v.getY();
        return new Point(Math.round(offsetX + x), Math.round(offsetY + y));
    }

    private boolean insideDragZone() {
        if (mCurrentPosition == null) {
            return false;
        }

        float topBottomRegionHeight = mHeight.getAsInt()
                * ViewAutoScroller.TOP_BOTTOM_THRESHOLD_RATIO;
        boolean shouldScrollUp = mCurrentPosition.y < topBottomRegionHeight
                && mCanScrollUp.getAsBoolean();
        boolean shouldScrollDown = mCurrentPosition.y > mHeight.getAsInt() - topBottomRegionHeight
                && mCanScrollDown.getAsBoolean();
        return shouldScrollUp || shouldScrollDown;
    }
}