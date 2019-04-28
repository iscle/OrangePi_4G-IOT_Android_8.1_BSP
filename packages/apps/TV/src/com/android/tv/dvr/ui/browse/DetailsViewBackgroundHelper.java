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
 * limitations under the License
 */

package com.android.tv.dvr.ui.browse;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v17.leanback.app.BackgroundManager;

/**
 * The Background Helper.
 */
class DetailsViewBackgroundHelper {
    // Background delay serves to avoid kicking off expensive bitmap loading
    // in case multiple backgrounds are set in quick succession.
    private static final int SET_BACKGROUND_DELAY_MS = 100;

    private final BackgroundManager mBackgroundManager;

    class LoadBackgroundRunnable implements Runnable {
        final Drawable mBackGround;

        LoadBackgroundRunnable(Drawable background) {
            mBackGround = background;
        }

        @Override
        public void run() {
            if (!mBackgroundManager.isAttached()) {
                return;
            }
            if (mBackGround instanceof BitmapDrawable) {
                mBackgroundManager.setBitmap(((BitmapDrawable) mBackGround).getBitmap());
            }
            mRunnable = null;
        }
    }

    private LoadBackgroundRunnable mRunnable;

    private final Handler mHandler = new Handler();

    public DetailsViewBackgroundHelper(Activity activity) {
        mBackgroundManager = BackgroundManager.getInstance(activity);
        mBackgroundManager.attach(activity.getWindow());
    }

    /**
     * Sets the given image to background.
     */
    public void setBackground(Drawable background) {
        if (mRunnable != null) {
            mHandler.removeCallbacks(mRunnable);
        }
        mRunnable = new LoadBackgroundRunnable(background);
        mHandler.postDelayed(mRunnable, SET_BACKGROUND_DELAY_MS);
    }

    /**
     * Sets the background color.
     */
    public void setBackgroundColor(int color) {
        if (mBackgroundManager.isAttached()) {
            mBackgroundManager.setColor(color);
        }
    }

    /**
     * Sets the background scrim.
     */
    public void setScrim(int color) {
        if (mBackgroundManager.isAttached()) {
            mBackgroundManager.setDimLayer(new ColorDrawable(color));
        }
    }
}
