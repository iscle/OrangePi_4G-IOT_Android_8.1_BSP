/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class DragLayout extends ViewGroup {

    private static final double AUTO_OPEN_SPEED_LIMIT = 600.0;
    private static final String KEY_IS_OPEN = "IS_OPEN";
    private static final String KEY_SUPER_STATE = "SUPER_STATE";

    private FrameLayout mHistoryFrame;
    private ViewDragHelper mDragHelper;

    // No concurrency; allow modifications while iterating.
    private final List<DragCallback> mDragCallbacks = new CopyOnWriteArrayList<>();
    private CloseCallback mCloseCallback;

    private final Map<Integer, PointF> mLastMotionPoints = new HashMap<>();
    private final Rect mHitRect = new Rect();

    private int mVerticalRange;
    private boolean mIsOpen;

    public DragLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mDragHelper = ViewDragHelper.create(this, 1.0f, new DragHelperCallback());
        mHistoryFrame = (FrameLayout) findViewById(R.id.history_frame);
        super.onFinishInflate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        measureChildren(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int displayHeight = 0;
        for (DragCallback c : mDragCallbacks) {
            displayHeight = Math.max(displayHeight, c.getDisplayHeight());
        }
        mVerticalRange = getHeight() - displayHeight;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            final View child = getChildAt(i);

            int top = 0;
            if (child == mHistoryFrame) {
                if (mDragHelper.getCapturedView() == mHistoryFrame
                        && mDragHelper.getViewDragState() != ViewDragHelper.STATE_IDLE) {
                    top = child.getTop();
                } else {
                    top = mIsOpen ? 0 : -mVerticalRange;
                }
            }
            child.layout(0, top, child.getMeasuredWidth(), top + child.getMeasuredHeight());
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SUPER_STATE, super.onSaveInstanceState());
        bundle.putBoolean(KEY_IS_OPEN, mIsOpen);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = (Bundle) state;
            mIsOpen = bundle.getBoolean(KEY_IS_OPEN);
            mHistoryFrame.setVisibility(mIsOpen ? View.VISIBLE : View.INVISIBLE);
            for (DragCallback c : mDragCallbacks) {
                c.onInstanceStateRestored(mIsOpen);
            }

            state = bundle.getParcelable(KEY_SUPER_STATE);
        }
        super.onRestoreInstanceState(state);
    }

    private void saveLastMotion(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                final int actionIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(actionIndex);
                final PointF point = new PointF(event.getX(actionIndex), event.getY(actionIndex));
                mLastMotionPoints.put(pointerId, point);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = event.getPointerCount() - 1; i >= 0; --i) {
                    final int pointerId = event.getPointerId(i);
                    final PointF point = mLastMotionPoints.get(pointerId);
                    if (point != null) {
                        point.set(event.getX(i), event.getY(i));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                final int actionIndex = event.getActionIndex();
                final int pointerId = event.getPointerId(actionIndex);
                mLastMotionPoints.remove(pointerId);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mLastMotionPoints.clear();
                break;
            }
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        saveLastMotion(event);
        return mDragHelper.shouldInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Workaround: do not process the error case where multi-touch would cause a crash.
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                && mDragHelper.getViewDragState() == ViewDragHelper.STATE_DRAGGING
                && mDragHelper.getActivePointerId() != ViewDragHelper.INVALID_POINTER
                && event.findPointerIndex(mDragHelper.getActivePointerId()) == -1) {
            mDragHelper.cancel();
            return false;
        }

        saveLastMotion(event);

        mDragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public void computeScroll() {
        if (mDragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void onStartDragging() {
        for (DragCallback c : mDragCallbacks) {
            c.onStartDraggingOpen();
        }
        mHistoryFrame.setVisibility(VISIBLE);
    }

    public boolean isViewUnder(View view, int x, int y) {
        view.getHitRect(mHitRect);
        offsetDescendantRectToMyCoords((View) view.getParent(), mHitRect);
        return mHitRect.contains(x, y);
    }

    public boolean isMoving() {
        final int draggingState = mDragHelper.getViewDragState();
        return draggingState == ViewDragHelper.STATE_DRAGGING
                || draggingState == ViewDragHelper.STATE_SETTLING;
    }

    public boolean isOpen() {
        return mIsOpen;
    }

    private void setClosed() {
        if (mIsOpen) {
            mIsOpen = false;
            mHistoryFrame.setVisibility(View.INVISIBLE);

            if (mCloseCallback != null) {
                mCloseCallback.onClose();
            }
        }
    }

    public Animator createAnimator(boolean toOpen) {
        if (mIsOpen == toOpen) {
            return ValueAnimator.ofFloat(0f, 1f).setDuration(0L);
        }

        mIsOpen = toOpen;
        mHistoryFrame.setVisibility(VISIBLE);

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDragHelper.cancel();
                mDragHelper.smoothSlideViewTo(mHistoryFrame, 0, mIsOpen ? 0 : -mVerticalRange);
            }
        });

        return animator;
    }

    public void setCloseCallback(CloseCallback callback) {
        mCloseCallback = callback;
    }

    public void addDragCallback(DragCallback callback) {
        mDragCallbacks.add(callback);
    }

    public void removeDragCallback(DragCallback callback) {
        mDragCallbacks.remove(callback);
    }

    /**
     * Callback when the layout is closed.
     * We use this to pop the HistoryFragment off the backstack.
     * We can't use a method in DragCallback because we get ConcurrentModificationExceptions on
     * mDragCallbacks when executePendingTransactions() is called for popping the fragment off the
     * backstack.
     */
    public interface CloseCallback {
        void onClose();
    }

    /**
     * Callbacks for coordinating with the RecyclerView or HistoryFragment.
     */
    public interface DragCallback {
        // Callback when a drag to open begins.
        void onStartDraggingOpen();

        // Callback in onRestoreInstanceState.
        void onInstanceStateRestored(boolean isOpen);

        // Animate the RecyclerView text.
        void whileDragging(float yFraction);

        // Whether we should allow the view to be dragged.
        boolean shouldCaptureView(View view, int x, int y);

        int getDisplayHeight();
    }

    public class DragHelperCallback extends ViewDragHelper.Callback {
        @Override
        public void onViewDragStateChanged(int state) {
            // The view stopped moving.
            if (state == ViewDragHelper.STATE_IDLE
                    && mDragHelper.getCapturedView().getTop() < -(mVerticalRange / 2)) {
                setClosed();
            }
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            for (DragCallback c : mDragCallbacks) {
                // Top is between [-mVerticalRange, 0].
                c.whileDragging(1f + (float) top / mVerticalRange);
            }
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return mVerticalRange;
        }

        @Override
        public boolean tryCaptureView(View view, int pointerId) {
            final PointF point = mLastMotionPoints.get(pointerId);
            if (point == null) {
                return false;
            }

            final int x = (int) point.x;
            final int y = (int) point.y;

            for (DragCallback c : mDragCallbacks) {
                if (!c.shouldCaptureView(view, x, y)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return Math.max(Math.min(top, 0), -mVerticalRange);
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            super.onViewCaptured(capturedChild, activePointerId);

            if (!mIsOpen) {
                mIsOpen = true;
                onStartDragging();
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final boolean settleToOpen;
            if (yvel > AUTO_OPEN_SPEED_LIMIT) {
                // Speed has priority over position.
                settleToOpen = true;
            } else if (yvel < -AUTO_OPEN_SPEED_LIMIT) {
                settleToOpen = false;
            } else {
                settleToOpen = releasedChild.getTop() > -(mVerticalRange / 2);
            }

            if (mDragHelper.settleCapturedViewAt(0, settleToOpen ? 0 : -mVerticalRange)) {
                ViewCompat.postInvalidateOnAnimation(DragLayout.this);
            }
        }
    }
}
