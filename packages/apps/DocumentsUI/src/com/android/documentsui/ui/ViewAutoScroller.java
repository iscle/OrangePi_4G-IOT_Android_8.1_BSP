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


package com.android.documentsui.ui;

import android.graphics.Point;

/**
 * Provides auto-scrolling upon request when user's interaction with the application
 * introduces a natural intent to scroll. Used by BandController, GestureSelector,
 * and DragHoverListener to allow auto scrolling when user either does band selection,
 * attempting to drag and drop files to somewhere off the current screen, or trying to motion select
 * past top/bottom of the screen.
 */
public final class ViewAutoScroller implements Runnable {
    public static final int NOT_SET = -1;
    // ratio used to calculate the top/bottom hotspot region; used with view height
    public static final float TOP_BOTTOM_THRESHOLD_RATIO = 0.125f;
    public static final int MAX_SCROLL_STEP = 70;

    // Top and bottom inner buffer such that user's cursor does not have to be exactly off screen
    // for auto scrolling to begin
    private final ScrollDistanceDelegate mCalcDelegate;
    private final ScrollActionDelegate mUiDelegate;

    public ViewAutoScroller(ScrollDistanceDelegate calcDelegate, ScrollActionDelegate uiDelegate) {
        mCalcDelegate = calcDelegate;
        mUiDelegate = uiDelegate;
    }

    /**
     * Attempts to smooth-scroll the view at the given UI frame. Application should be
     * responsible to do any clean up (such as unsubscribing scrollListeners) after the run has
     * finished, and re-run this method on the next UI frame if applicable.
     */
    @Override
    public void run() {
        // Compute the number of pixels the pointer's y-coordinate is past the view.
        // Negative values mean the pointer is at or before the top of the view, and
        // positive values mean that the pointer is at or after the bottom of the view. Note
        // that top/bottom threshold is added here so that the view still scrolls when the
        // pointer are in these buffer pixels.
        int pixelsPastView = 0;

        final int topBottomThreshold = (int) (mCalcDelegate.getViewHeight()
                * TOP_BOTTOM_THRESHOLD_RATIO);

        if (mCalcDelegate.getCurrentPosition().y <= topBottomThreshold) {
            pixelsPastView = mCalcDelegate.getCurrentPosition().y - topBottomThreshold;
        } else if (mCalcDelegate.getCurrentPosition().y >= mCalcDelegate.getViewHeight()
                - topBottomThreshold) {
            pixelsPastView = mCalcDelegate.getCurrentPosition().y - mCalcDelegate.getViewHeight()
                    + topBottomThreshold;
        }

        if (!mCalcDelegate.isActive() || pixelsPastView == 0) {
            // If the operation that started the scrolling is no longer inactive, or if it is active
            // but not at the edge of the view, no scrolling is necessary.
            return;
        }

        if (pixelsPastView > topBottomThreshold) {
            pixelsPastView = topBottomThreshold;
        }

        // Compute the number of pixels to scroll, and scroll that many pixels.
        final int numPixels = computeScrollDistance(pixelsPastView);
        mUiDelegate.scrollBy(numPixels);

        // Remove callback to this, and then properly run at next frame again
        mUiDelegate.removeCallback(this);
        mUiDelegate.runAtNextFrame(this);
    }

    /**
     * Computes the number of pixels to scroll based on how far the pointer is past the end
     * of the region. Roughly based on ItemTouchHelper's algorithm for computing the number of
     * pixels to scroll when an item is dragged to the end of a view.
     * @return
     */
    public int computeScrollDistance(int pixelsPastView) {
        final int topBottomThreshold = (int) (mCalcDelegate.getViewHeight()
                * TOP_BOTTOM_THRESHOLD_RATIO);

        final int direction = (int) Math.signum(pixelsPastView);
        final int absPastView = Math.abs(pixelsPastView);

        // Calculate the ratio of how far out of the view the pointer currently resides to
        // the top/bottom scrolling hotspot of the view.
        final float outOfBoundsRatio = Math.min(
                1.0f, (float) absPastView / topBottomThreshold);
        // Interpolate this ratio and use it to compute the maximum scroll that should be
        // possible for this step.
        final int cappedScrollStep =
                (int) (direction * MAX_SCROLL_STEP * smoothOutOfBoundsRatio(outOfBoundsRatio));

        // If the final number of pixels to scroll ends up being 0, the view should still
        // scroll at least one pixel.
        return cappedScrollStep != 0 ? cappedScrollStep : direction;
    }

    /**
     * Interpolates the given out of bounds ratio on a curve which starts at (0,0) and ends
     * at (1,1) and quickly approaches 1 near the start of that interval. This ensures that
     * drags that are at the edge or barely past the edge of the threshold does little to no
     * scrolling, while drags that are near the edge of the view does a lot of
     * scrolling. The equation y=x^10 is used, but this could also be tweaked if
     * needed.
     * @param ratio A ratio which is in the range [0, 1].
     * @return A "smoothed" value, also in the range [0, 1].
     */
    private float smoothOutOfBoundsRatio(float ratio) {
        return (float) Math.pow(ratio, 10);
    }

    /**
     * Used by {@link run} to properly calculate the proper amount of pixels to scroll given time
     * passed since scroll started, and to properly scroll / proper listener clean up if necessary.
     */
    public interface ScrollDistanceDelegate {
        public Point getCurrentPosition();
        public int getViewHeight();
        public boolean isActive();
    }

    /**
     * Used by {@link run} to do UI tasks, such as scrolling and rerunning at next UI cycle.
     */
    public interface ScrollActionDelegate {
        public void scrollBy(int dy);
        public void runAtNextFrame(Runnable r);
        public void removeCallback(Runnable r);
    }
}