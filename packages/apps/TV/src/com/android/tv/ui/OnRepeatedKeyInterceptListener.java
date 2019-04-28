/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tv.ui;

import android.os.Message;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.android.tv.common.WeakHandler;

/**
 * Listener to make focus change faster over time.
 */
public class OnRepeatedKeyInterceptListener implements VerticalGridView.OnKeyInterceptListener {
    private static final String TAG = "OnRepeatedKeyListener";
    private static final boolean DEBUG = false;

    private static final int[] THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS = { 2000, 5000 };
    private static final int[] MAX_SKIPPED_VIEW_COUNT = { 1, 4 };
    private static final int MSG_MOVE_FOCUS = 1000;

    private final VerticalGridView mView;
    private final MyHandler mHandler = new MyHandler(this);
    private int mDirection;
    private boolean mFocusAccelerated;
    private long mRepeatedKeyInterval;

    public OnRepeatedKeyInterceptListener(VerticalGridView view) {
        mView = view;
    }

    public boolean isFocusAccelerated() {
        return mFocusAccelerated;
    }

    @Override
    public boolean onInterceptKeyEvent(KeyEvent event) {
        mHandler.removeMessages(MSG_MOVE_FOCUS);
        if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_UP &&
                event.getKeyCode() != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false;
        }

        long duration = event.getEventTime() - event.getDownTime();
        if (duration < THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[0]
                || event.isCanceled()) {
            mFocusAccelerated = false;
            return false;
        }
        mDirection = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP
                : View.FOCUS_DOWN;
        int skippedViewCount = MAX_SKIPPED_VIEW_COUNT[0];
        for (int i = 1 ;i < THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS.length; ++i) {
            if (THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[i] < duration) {
                skippedViewCount = MAX_SKIPPED_VIEW_COUNT[i];
            } else {
                break;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mRepeatedKeyInterval = duration / event.getRepeatCount();
            mFocusAccelerated = true;
        } else {
            // HACK: we move focus skippedViewCount times more even after ACTION_UP. Without this
            // hack, a focused view's position doesn't reach to the desired position
            // in ProgramGrid.
            mFocusAccelerated = false;
        }
        for (int i = 0; i < skippedViewCount; ++i) {
            mHandler.sendEmptyMessageDelayed(MSG_MOVE_FOCUS,
                    mRepeatedKeyInterval * i / (skippedViewCount + 1));
        }
        if (DEBUG) Log.d(TAG, "onInterceptKeyEvent: focused view " + mView.findFocus());
        return false;
    }

    private static class MyHandler extends WeakHandler<OnRepeatedKeyInterceptListener> {
        private MyHandler(OnRepeatedKeyInterceptListener listener) {
            super(listener);
        }

        @Override
        public void handleMessage(Message msg, @NonNull OnRepeatedKeyInterceptListener listener) {
            if (msg.what == MSG_MOVE_FOCUS) {
                View focused = listener.mView.findFocus();
                if (DEBUG) Log.d(TAG, "MSG_MOVE_FOCUS: focused view " + focused);
                if (focused != null) {
                    View v = focused.focusSearch(listener.mDirection);
                    if (v != null && v != focused) {
                        v.requestFocus(listener.mDirection);
                    }
                }
            }
        }
    }
}
