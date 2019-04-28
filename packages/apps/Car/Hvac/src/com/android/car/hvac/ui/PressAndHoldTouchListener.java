/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.hvac.ui;

import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;

/**
 * A wrapper for a click listener that repeats the click when a press and hold action takes place.
 */
public class PressAndHoldTouchListener implements OnTouchListener {
    private static final int REPEAT_ACTION_DELAY_MS = 100;
    private static final int FIRST_ACTION_DELAY_MS = 300;

    private Handler mHandler = new Handler();

    private final OnClickListener clickListener;

    private View mView;

    public PressAndHoldTouchListener(OnClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mHandler.removeCallbacks(mRepeatedAction);
                mHandler.postDelayed(mRepeatedAction, FIRST_ACTION_DELAY_MS);
                mView = view;
                mView.setPressed(true);
                clickListener.onClick(view);
                return true;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mHandler.removeCallbacks(mRepeatedAction);
                mView.setPressed(false);
                mView = null;
                return true;
            default:
                return false;
        }
    }

    private Runnable mRepeatedAction = new Runnable() {
        @Override
        public void run() {
            mHandler.postDelayed(this, REPEAT_ACTION_DELAY_MS);
            clickListener.onClick(mView);  //
        }
    };
}