/*
 * Copyright (C) 2013 Google Inc.
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

package com.android.mail.browse;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

import com.android.mail.utils.LogUtils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A container that tries to play nice with an internally scrollable {@link Touchable} child view.
 * The assumption is that the child view can scroll horizontally, but not vertically, so any
 * touch events on that child view should ALSO be sent here so it can simultaneously vertically
 * scroll (not the standard either/or behavior).
 * <p>
 * Touch events on any other child of this ScrollView are intercepted in the standard fashion.
 */
public class MessageScrollView extends ScrollView implements ScrollNotifier,
        ScaleGestureDetector.OnScaleGestureListener, GestureDetector.OnDoubleTapListener {

    /**
     * A View that reports whether onTouchEvent() was recently called.
     */
    public interface Touchable {
        boolean wasTouched();
        void clearTouched();
        boolean zoomIn();
        boolean zoomOut();
    }

    /**
     * True when performing "special" interception.
     */
    private boolean mWantToIntercept;
    /**
     * Whether to perform the standard touch interception procedure. This is set to true when we
     * want to intercept a touch stream from any child OTHER than {@link #mTouchableChild}.
     */
    private boolean mInterceptNormally;
    /**
     * The special child that we want to NOT intercept from in the normal way. Instead, this child
     * will continue to receive the touch event stream (so it can handle the horizontal component)
     * while this parent will additionally handle the events to perform vertical scrolling.
     */
    private Touchable mTouchableChild;

    /**
     * We want to detect the scale gesture so that we don't try to scroll instead, but we don't
     * care about actually interpreting it because the webview does that by itself when it handles
     * the touch events.
     *
     * This might lead to really weird interactions if the two gesture detectors' implementations
     * drift...
     */
    private ScaleGestureDetector mScaleDetector;
    private boolean mInScaleGesture;

    /**
     * We also want to detect double-tap gestures, but in a way that doesn't conflict with
     * tap-tap-drag gestures
     */
    private GestureDetector mGestureDetector;
    private boolean mDoubleTapOccurred;
    private boolean mZoomedIn;

    /**
     * Touch slop used to determine if this double tap is valid for starting a scale or should be
     * ignored.
     */
    private int mTouchSlopSquared;

    /**
     * X and Y coordinates for the current down event. Since mDoubleTapOccurred only contains the
     * information that there was a double tap event, use these to get the secondary tap
     * information to determine if a user has moved beyond touch slop.
     */
    private float mDownFocusX;
    private float mDownFocusY;

    private final Set<ScrollListener> mScrollListeners =
            new CopyOnWriteArraySet<ScrollListener>();

    public static final String LOG_TAG = "MsgScroller";

    public MessageScrollView(Context c) {
        this(c, null);
    }

    public MessageScrollView(Context c, AttributeSet attrs) {
        super(c, attrs);
        final int touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        mTouchSlopSquared = touchSlop * touchSlop;
        mScaleDetector = new ScaleGestureDetector(c, this);
        mGestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener());
        mGestureDetector.setOnDoubleTapListener(this);
    }

    public void setInnerScrollableView(Touchable child) {
        mTouchableChild = child;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mInterceptNormally) {
            LogUtils.d(LOG_TAG, "IN ScrollView.onIntercept, NOW stealing. ev=%s", ev);
            return true;
        } else if (mWantToIntercept) {
            LogUtils.d(LOG_TAG, "IN ScrollView.onIntercept, already stealing. ev=%s", ev);
            return false;
        }

        mWantToIntercept = super.onInterceptTouchEvent(ev);
        LogUtils.d(LOG_TAG, "OUT ScrollView.onIntercept, steal=%s ev=%s", mWantToIntercept, ev);
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                LogUtils.d(LOG_TAG, "IN ScrollView.dispatchTouch, clearing flags");
                mWantToIntercept = false;
                mInterceptNormally = false;
                break;
        }
        if (mTouchableChild != null) {
            mTouchableChild.clearTouched();
        }

        mScaleDetector.onTouchEvent(ev);
        mGestureDetector.onTouchEvent(ev);

        final boolean handled = super.dispatchTouchEvent(ev);
        LogUtils.d(LOG_TAG, "OUT ScrollView.dispatchTouch, handled=%s ev=%s", handled, ev);

        if (mWantToIntercept && !mInScaleGesture) {
            final boolean touchedChild = (mTouchableChild != null && mTouchableChild.wasTouched());
            if (touchedChild) {
                // also give the event to this scroll view if the WebView got the event
                // and didn't stop any parent interception
                LogUtils.d(LOG_TAG, "IN extra ScrollView.onTouch, ev=%s", ev);
                onTouchEvent(ev);
            } else {
                mInterceptNormally = true;
                mWantToIntercept = false;
            }
        }

        return handled;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        LogUtils.d(LOG_TAG, "Begin scale gesture");
        mInScaleGesture = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        LogUtils.d(LOG_TAG, "End scale gesture");
        mInScaleGesture = false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        mDoubleTapOccurred = true;
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        final int action = e.getAction();
        boolean handled = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownFocusX = e.getX();
                mDownFocusY = e.getY();
                break;
            case MotionEvent.ACTION_UP:
                handled = triggerZoom();
                break;
            case MotionEvent.ACTION_MOVE:
                final int deltaX = (int) (e.getX() - mDownFocusX);
                final int deltaY = (int) (e.getY() - mDownFocusY);
                int distance = (deltaX * deltaX) + (deltaY * deltaY);
                if (distance > mTouchSlopSquared) {
                    mDoubleTapOccurred = false;
                }
                break;

        }
        return handled;
    }

    private boolean triggerZoom() {
        boolean handled = false;
        if (mDoubleTapOccurred) {
            if (mZoomedIn) {
                mTouchableChild.zoomOut();
            } else {
                mTouchableChild.zoomIn();
            }
            mZoomedIn = !mZoomedIn;
            LogUtils.d(LogUtils.TAG, "Trigger Zoom!");
            handled = true;
        }
        mDoubleTapOccurred = false;
        return handled;
    }

    @Override
    public void addScrollListener(ScrollListener l) {
        mScrollListeners.add(l);
    }

    @Override
    public void removeScrollListener(ScrollListener l) {
        mScrollListeners.remove(l);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        for (ScrollListener listener : mScrollListeners) {
            listener.onNotifierScroll(t);
        }
    }

    @Override
    public int computeVerticalScrollRange() {
        return super.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        return super.computeVerticalScrollExtent();
    }

    @Override
    public int computeHorizontalScrollRange() {
        return super.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return super.computeHorizontalScrollOffset();
    }

    @Override
    public int computeHorizontalScrollExtent() {
        return super.computeHorizontalScrollExtent();
    }
}
