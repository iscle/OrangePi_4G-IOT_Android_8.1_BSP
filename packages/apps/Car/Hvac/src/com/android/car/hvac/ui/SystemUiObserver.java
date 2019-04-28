/*
 * Copyright (c) 2017, The Android Open Source Project
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

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Mostly a hack. The system ui change methods on View or even the listeners don't get
 * called reliably, it seems like the framework is caching drawn views for performance
 * reasons. Instead we make this class which is a framelayout that must be match_parent
 * along both dimensions. This way we have this view that gets asked to be laid out
 * every time the screen size changes, which we can then use to compute whether the
 * system ui is visible or not.
 */
public class SystemUiObserver extends FrameLayout {
    private Listener mListener;
    private final DisplayMetrics mDisplayMetrics = new DisplayMetrics();
    // Assuming that android always starts up with systemui visible.
    private boolean mVisible = true;

    {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(mDisplayMetrics);
    }

    public SystemUiObserver(Context context) {
        super(context);
    }

    public SystemUiObserver(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SystemUiObserver(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        boolean visible = !(bottom == mDisplayMetrics.heightPixels);
        if (visible != mVisible && mListener != null) {
            mListener.onSystemUiVisibilityChange(visible);
        }
        mVisible = visible;
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public interface Listener {
        void onSystemUiVisibilityChange(boolean visible);
    }
}
