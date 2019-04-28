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

package com.android.tv.tuner.cc;

import android.content.Context;
import android.util.AttributeSet;

import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.layout.ScaledLayout;

/**
 * Layout containing the safe title area that helps the closed captions look more prominent.
 * This is required by CEA-708B.
 */
public class CaptionLayout extends ScaledLayout {
    // The safe title area has 10% margins of the screen.
    private static final float SAFE_TITLE_AREA_SCALE_START_X = 0.1f;
    private static final float SAFE_TITLE_AREA_SCALE_END_X = 0.9f;
    private static final float SAFE_TITLE_AREA_SCALE_START_Y = 0.1f;
    private static final float SAFE_TITLE_AREA_SCALE_END_Y = 0.9f;

    private final ScaledLayout mSafeTitleAreaLayout;
    private AtscCaptionTrack mCaptionTrack;

    public CaptionLayout(Context context) {
        this(context, null);
    }

    public CaptionLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptionLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mSafeTitleAreaLayout = new ScaledLayout(context);
        addView(mSafeTitleAreaLayout, new ScaledLayoutParams(
                SAFE_TITLE_AREA_SCALE_START_X, SAFE_TITLE_AREA_SCALE_END_X,
                SAFE_TITLE_AREA_SCALE_START_Y, SAFE_TITLE_AREA_SCALE_END_Y));
    }

    public void addOrUpdateViewToSafeTitleArea(CaptionWindowLayout captionWindowLayout,
            ScaledLayoutParams scaledLayoutParams) {
        int index = mSafeTitleAreaLayout.indexOfChild(captionWindowLayout);
        if (index < 0) {
            mSafeTitleAreaLayout.addView(captionWindowLayout, scaledLayoutParams);
            return;
        }
        mSafeTitleAreaLayout.updateViewLayout(captionWindowLayout, scaledLayoutParams);
    }

    public void removeViewFromSafeTitleArea(CaptionWindowLayout captionWindowLayout) {
        mSafeTitleAreaLayout.removeView(captionWindowLayout);
    }

    public void setCaptionTrack(AtscCaptionTrack captionTrack) {
        mCaptionTrack = captionTrack;
    }

    public AtscCaptionTrack getCaptionTrack() {
        return mCaptionTrack;
    }
}
