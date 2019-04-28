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
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * Empty frame to steal events for two-pane view when the drawer is open.
 */
public class ConversationViewFrame extends FrameLayout {

    private final ViewConfiguration mConfiguration;
    private long mInterceptedTime;
    private float mInterceptedXDown;
    private float mInterceptedYDown;

    public interface DownEventListener {
        boolean shouldBlockTouchEvents();
        void onConversationViewFrameTapped();
        void onConversationViewTouchDown();
    }

    private DownEventListener mDownEventListener;

    public ConversationViewFrame(Context c) {
        this(c, null);
    }

    public ConversationViewFrame(Context c, AttributeSet attrs) {
        super(c, attrs);
        mConfiguration = ViewConfiguration.get(c);
    }

    public void setDownEventListener(DownEventListener l) {
        mDownEventListener = l;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final boolean steal = (mDownEventListener != null
                && mDownEventListener.shouldBlockTouchEvents());
        if (!steal && ev.getActionMasked() == MotionEvent.ACTION_DOWN
                && mDownEventListener != null) {
            // notify 2-pane that this CV is being interacted (to turn a peek->normal)
            mDownEventListener.onConversationViewTouchDown();
        }
        return steal;
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent ev) {
        if (mDownEventListener != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mInterceptedTime = SystemClock.elapsedRealtime();
                    mInterceptedXDown = ev.getX();
                    mInterceptedYDown = ev.getY();
                    break;
                case MotionEvent.ACTION_UP:
                    // Check for a tap
                    final long timeDelta = SystemClock.elapsedRealtime() - mInterceptedTime;
                    final float xDelta = ev.getX() - mInterceptedXDown;
                    final float yDelta = ev.getY() - mInterceptedYDown;
                    if (timeDelta < ViewConfiguration.getTapTimeout()
                            && xDelta < mConfiguration.getScaledTouchSlop()
                            && yDelta < mConfiguration.getScaledTouchSlop()) {
                        mDownEventListener.onConversationViewFrameTapped();
                    }
            }
            return true;
        }
        return false;
    }

}
