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

package com.android.documentsui.selection;

import static com.android.documentsui.base.Shared.DEBUG;

import android.graphics.Point;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import com.android.documentsui.DirectoryReloadLock;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.ui.ViewAutoScroller;
import com.android.documentsui.ui.ViewAutoScroller.ScrollActionDelegate;
import com.android.documentsui.ui.ViewAutoScroller.ScrollDistanceDelegate;

import java.util.function.IntSupplier;

import javax.annotation.Nullable;

/*
 * Helper class used to intercept events that could cause a gesture multi-select, and keeps
 * the interception going if necessary.
 */
public final class GestureSelector {
    private final String TAG = "GestureSelector";

    private final SelectionManager mSelectionMgr;
    private final Runnable mDragScroller;
    private final IntSupplier mHeight;
    private final ViewFinder mViewFinder;
    private final DirectoryReloadLock mLock;
    private int mLastStartedItemPos = -1;
    private boolean mStarted = false;
    private Point mLastInterceptedPoint;

    GestureSelector(
            SelectionManager selectionMgr,
            IntSupplier heightSupplier,
            ViewFinder viewFinder,
            ScrollActionDelegate actionDelegate,
            DirectoryReloadLock lock) {
        mSelectionMgr = selectionMgr;
        mHeight = heightSupplier;
        mViewFinder = viewFinder;
        mLock = lock;

        ScrollDistanceDelegate distanceDelegate = new ScrollDistanceDelegate() {
            @Override
            public Point getCurrentPosition() {
                return mLastInterceptedPoint;
            }

            @Override
            public int getViewHeight() {
                return mHeight.getAsInt();
            }

            @Override
            public boolean isActive() {
                return mStarted && mSelectionMgr.hasSelection();
            }
        };

        mDragScroller = new ViewAutoScroller(distanceDelegate, actionDelegate);
    }

    public static GestureSelector create(
            SelectionManager selectionMgr,
            RecyclerView scrollView,
            DirectoryReloadLock lock) {
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
        GestureSelector helper =
                new GestureSelector(
                        selectionMgr,
                        scrollView::getHeight,
                        scrollView::findChildViewUnder,
                        actionDelegate,
                        lock);

        return helper;
    }

    // Explicitly kick off a gesture multi-select.
    public boolean start(InputEvent event) {
        //the anchor must already be set before a multi-select event can be started
        if (mLastStartedItemPos < 0) {
            if (DEBUG) Log.d(TAG, "Tried to start multi-select without setting an anchor.");
            return false;
        }
        if (mStarted) {
            return false;
        }
        mStarted = true;
        return true;
    }

    public boolean onInterceptTouchEvent(InputEvent e) {
        if (e.isMouseEvent()) {
            return false;
        }

        boolean handled = false;

        if (e.isActionDown()) {
            handled = handleInterceptedDownEvent(e);
        }

        if (e.isActionMove()) {
            handled = handleInterceptedMoveEvent(e);
        }

        return handled;
    }

    public void onTouchEvent(RecyclerView rv, InputEvent e) {
        if (!mStarted) {
            return;
        }

        if (e.isActionUp()) {
            handleUpEvent(e);
        }

        if (e.isActionCancel()) {
            handleCancelEvent(e);
        }

        if (e.isActionMove()) {
            handleOnTouchMoveEvent(rv, e);
        }
    }

    // Called when an ACTION_DOWN event is intercepted.
    // If down event happens on a file/doc, we mark that item's position as last started.
    private boolean handleInterceptedDownEvent(InputEvent e) {
        View itemView = mViewFinder.findView(e.getX(), e.getY());
        if (itemView != null) {
            mLastStartedItemPos = e.getItemPosition();
        }
        return false;
    }

    // Called when an ACTION_MOVE event is intercepted.
    private boolean handleInterceptedMoveEvent(InputEvent e) {
        mLastInterceptedPoint = e.getOrigin();
        if (mStarted) {
            mSelectionMgr.startRangeSelection(mLastStartedItemPos);
            // Gesture Selection about to start
            mLock.block();
            return true;
        }
        return false;
    }

    // Called when ACTION_UP event is to be handled.
    // Essentially, since this means all gesture movement is over, reset everything and apply
    // provisional selection.
    private void handleUpEvent(InputEvent e) {
        mSelectionMgr.getSelection().applyProvisionalSelection();
        endSelection();
    }

    // Called when ACTION_CANCEL event is to be handled.
    // This means this gesture selection is aborted, so reset everything and abandon provisional
    // selection.
    private void handleCancelEvent(InputEvent e) {
        mSelectionMgr.cancelProvisionalSelection();
        endSelection();
    }

    private void endSelection() {
        assert(mStarted);
        mLastStartedItemPos = -1;
        mStarted = false;
        mLock.unblock();
    }

    // Call when an intercepted ACTION_MOVE event is passed down.
    // At this point, we are sure user wants to gesture multi-select.
    private void handleOnTouchMoveEvent(RecyclerView rv, InputEvent e) {
        mLastInterceptedPoint = e.getOrigin();

        // If user has moved his pointer to the bottom-right empty pane (ie. to the right of the
        // last item of the recycler view), we would want to set that as the currentItemPos
        View lastItem = rv.getLayoutManager()
                .getChildAt(rv.getLayoutManager().getChildCount() - 1);
        int direction = rv.getContext().getResources().getConfiguration().getLayoutDirection();
        final boolean pastLastItem = isPastLastItem(lastItem.getTop(),
                lastItem.getLeft(),
                lastItem.getRight(),
                e,
                direction);

        // Since views get attached & detached from RecyclerView,
        // {@link LayoutManager#getChildCount} can return a different number from the actual
        // number
        // of items in the adapter. Using the adapter is the for sure way to get the actual last
        // item position.
        final float inboundY = getInboundY(rv.getHeight(), e.getY());
        final int lastGlidedItemPos = (pastLastItem) ? rv.getAdapter().getItemCount() - 1
                : rv.getChildAdapterPosition(rv.findChildViewUnder(e.getX(), inboundY));
        if (lastGlidedItemPos != RecyclerView.NO_POSITION) {
            doGestureMultiSelect(lastGlidedItemPos);
        }
        scrollIfNecessary();
    }

    // It's possible for events to go over the top/bottom of the RecyclerView.
    // We want to get a Y-coordinate within the RecyclerView so we can find the childView underneath
    // correctly.
    private static float getInboundY(float max, float y) {
        if (y < 0f) {
            return 0f;
        } else if (y > max) {
            return max;
        }
        return y;
    }

    /*
     * Check to see an InputEvent if past a particular item, i.e. to the right or to the bottom
     * of the item.
     * For RTL, it would to be to the left or to the bottom of the item.
     */
    @VisibleForTesting
    static boolean isPastLastItem(int top, int left, int right, InputEvent e, int direction) {
        if (direction == View.LAYOUT_DIRECTION_LTR) {
            return e.getX() > right && e.getY() > top;
        } else {
            return e.getX() < left && e.getY() > top;
        }
    }

    /* Given the end position, select everything in-between.
     * @param endPos  The adapter position of the end item.
     */
    private void doGestureMultiSelect(int endPos) {
        mSelectionMgr.snapProvisionalRangeSelection(endPos);
    }

    private void scrollIfNecessary() {
        mDragScroller.run();
    }

    @FunctionalInterface
    interface ViewFinder {
        @Nullable View findView(float x, float y);
    }
}