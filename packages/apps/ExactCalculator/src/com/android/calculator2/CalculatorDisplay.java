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

import android.content.Context;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.Toolbar;

public class CalculatorDisplay extends LinearLayout
        implements AccessibilityManager.AccessibilityStateChangeListener {

    /**
     * The duration in milliseconds after which to hide the toolbar.
     */
    private static final long AUTO_HIDE_DELAY_MILLIS = 3000L;

    /**
     * The duration in milliseconds to fade in/out the toolbar.
     */
    private static final long FADE_DURATION = 200L;

    private final Runnable mHideToolbarRunnable = new Runnable() {
        @Override
        public void run() {
            // Remove any duplicate callbacks to hide the toolbar.
            removeCallbacks(this);

            // Only animate if we have been laid out at least once.
            if (isLaidOut()) {
                TransitionManager.beginDelayedTransition(CalculatorDisplay.this, mTransition);
            }
            mToolbar.setVisibility(View.INVISIBLE);
        }
    };

    private final AccessibilityManager mAccessibilityManager;
    private final GestureDetector mTapDetector;

    private Toolbar mToolbar;
    private Transition mTransition;

    private boolean mForceToolbarVisible;

    public CalculatorDisplay(Context context) {
        this(context, null /* attrs */);
    }

    public CalculatorDisplay(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public CalculatorDisplay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mAccessibilityManager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

        mTapDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                // Remove callbacks to hide the toolbar.
                removeCallbacks(mHideToolbarRunnable);

                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mToolbar.getVisibility() != View.VISIBLE) {
                    showToolbar(true);
                } else {
                    hideToolbar();
                }

                return true;
            }
        });

        // Draw the children in reverse order so that the toolbar is on top.
        setChildrenDrawingOrderEnabled(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mTransition = new Fade()
                .setDuration(FADE_DURATION)
                .addTarget(mToolbar);
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        // Reverse the normal drawing order.
        return (childCount - 1) - i;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAccessibilityManager.addAccessibilityStateChangeListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mAccessibilityManager.removeAccessibilityStateChangeListener(this);
    }

    @Override
    public void onAccessibilityStateChanged(boolean enabled) {
        // Always show the toolbar whenever accessibility is enabled.
        showToolbar(true);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        mTapDetector.onTouchEvent(event);
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mTapDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    /**
     * Returns {@code true} if the toolbar should remain visible.
     */
    public boolean getForceToolbarVisible() {
        return mForceToolbarVisible || mAccessibilityManager.isEnabled();
    }

    /**
     * Forces the toolbar to remain visible.
     *
     * @param forceToolbarVisible {@code true} to keep the toolbar visible
     */
    public void setForceToolbarVisible(boolean forceToolbarVisible) {
        if (mForceToolbarVisible != forceToolbarVisible) {
            mForceToolbarVisible = forceToolbarVisible;
            showToolbar(!forceToolbarVisible);
        }
    }

    /**
     * Shows the toolbar.
     * @param autoHide Automatically ide toolbar again after delay
     */
    public void showToolbar(boolean autoHide) {
        // Only animate if we have been laid out at least once.
        if (isLaidOut()) {
            TransitionManager.beginDelayedTransition(this, mTransition);
        }
        mToolbar.setVisibility(View.VISIBLE);

        // Remove callbacks to hide the toolbar.
        removeCallbacks(mHideToolbarRunnable);

        // Auto hide the toolbar after 3 seconds.
        if (autoHide && !getForceToolbarVisible()) {
            postDelayed(mHideToolbarRunnable, AUTO_HIDE_DELAY_MILLIS);
        }
    }

    /**
     * Hides the toolbar.
     */
    public void hideToolbar() {
        if (!getForceToolbarVisible()) {
            removeCallbacks(mHideToolbarRunnable);
            mHideToolbarRunnable.run();
        }
    }

    public boolean isToolbarVisible() {
        return mToolbar.getVisibility() == View.VISIBLE;
    }
}
