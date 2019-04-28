/**
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.ui;

import android.content.Context;
import android.support.annotation.Nullable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

/**
 * Generic utility class that deals with capturing drag events in a particular horizontal direction
 * and calls the callback interface for drag events.
 *
 * Usage:
 *
 * <code>
 *
 *  class CustomView extends ... {
 *      private boolean mShouldInterceptDrag;
 *      private int mDragMode;
 *
 *      public boolean onInterceptTouchEvent(MotionEvent ev) {
 *          switch (ev.getAction()) {
 *              case MotionEvent.ACTION_DOWN:
 *                  // Check if the event is in the draggable area
 *                  mShouldInterceptDrag = ...;
 *                  mDragMode = ...;
 *          }
 *          return mShouldInterceptDrag && GmailDragHelper.processTouchEvent(ev, mDragMode);
 *      }
 *
 *      public boolean onTouchEvent(MotionEvent ev) {
 *          if (mShouldInterceptDrag) {
 *              GmailDragHelper.processTouchEvent(ev, mDragMode);
 *              return true;
 *          }
 *          return super.onTouchEvent(ev);
 *      }
 *  }
 *
 * </code>
 */
public class GmailDragHelper {
    public static final int CAPTURE_LEFT_TO_RIGHT = 0;
    public static final int CAPTURE_RIGHT_TO_LEFT = 1;

    private final GmailDragHelperCallback mCallback;
    private final ViewConfiguration mConfiguration;

    private boolean mDragging;
    private VelocityTracker mVelocityTracker;

    private float mInitialInterceptedX;
    private float mInitialInterceptedY;

    private float mStartDragX;

    public interface GmailDragHelperCallback {
        public void onDragStarted();
        public void onDrag(float deltaX);
        public void onDragEnded(float deltaX, float velocityX, boolean isFling);
    }

    /**
     */
    public GmailDragHelper(Context context, GmailDragHelperCallback callback) {
        mCallback = callback;
        mConfiguration = ViewConfiguration.get(context);
    }

    /**
     * Process incoming MotionEvent to compute the new drag state and coordinates.
     *
     * @param ev the captured MotionEvent
     * @param dragMode either {@link GmailDragHelper#CAPTURE_LEFT_TO_RIGHT} or
     *   {@link GmailDragHelper#CAPTURE_RIGHT_TO_LEFT}
     * @return whether if drag is happening
     */
    public boolean processTouchEvent(MotionEvent ev, int dragMode) {
        return processTouchEvent(ev, dragMode, null);
    }

    /**
     * @param xThreshold optional parameter to specify that the drag can only happen if it crosses
     *   the threshold coordinate. This can be used to only start the drag once the user hits the
     *   edge of the view.
     */
    public boolean processTouchEvent(MotionEvent ev, int dragMode, @Nullable Float xThreshold) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mInitialInterceptedX = ev.getX();
                mInitialInterceptedY = ev.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (mDragging) {
                    mCallback.onDrag(ev.getX() - mStartDragX);
                } else {
                    // Try to start dragging
                    final float evX = ev.getX();
                    // Check for directional drag
                    if ((dragMode == CAPTURE_LEFT_TO_RIGHT && evX <= mInitialInterceptedX) ||
                            (dragMode == CAPTURE_RIGHT_TO_LEFT && evX >= mInitialInterceptedX)) {
                        break;
                    }

                    // Check for optional threshold
                    boolean passedThreshold = true;
                    if (xThreshold != null) {
                        if (dragMode == CAPTURE_LEFT_TO_RIGHT) {
                            passedThreshold = evX > xThreshold;
                        } else {
                            passedThreshold = evX < xThreshold;
                        }
                    }

                    // Check for drag threshold
                    final float deltaX = Math.abs(evX - mInitialInterceptedX);
                    final float deltaY = Math.abs(ev.getY() - mInitialInterceptedY);
                    if (deltaX >= mConfiguration.getScaledTouchSlop() && deltaX >= deltaY
                            && passedThreshold) {
                        setDragging(true, evX);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mDragging) {
                    setDragging(false, ev.getX());
                }
                break;
        }

        return mDragging;
    }

    /**
     * Set the internal dragging state and calls the appropriate callbacks.
     */
    private void setDragging(boolean dragging, float evX) {
        mDragging = dragging;

        if (mDragging) {
            mStartDragX = evX;
            mCallback.onDragStarted();
        } else {
            // Here velocity is in pixel/second, let's take that into account for evX.
            mVelocityTracker.computeCurrentVelocity(1000);
            // Check for fling
            final float xVelocity = mVelocityTracker.getXVelocity();
            final boolean isFling =
                    Math.abs(xVelocity) > mConfiguration.getScaledMinimumFlingVelocity();
            mVelocityTracker.clear();

            mCallback.onDragEnded(evX - mStartDragX, xVelocity, isFling);
        }
    }
}
