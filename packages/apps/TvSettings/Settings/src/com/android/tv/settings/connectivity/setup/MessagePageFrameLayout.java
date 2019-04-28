/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tv.settings.connectivity.setup;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.tv.settings.R;

/**
 * Provide a custom FrameLayout for the message page. The main purpose is that we need to
 * dynamically adjust the position of status textView based on the count of lines.
 */
public class MessagePageFrameLayout extends FrameLayout {
    private float mTitleKeylinePercent;

    public MessagePageFrameLayout(Context context) {
        this(context, null);
    }

    public MessagePageFrameLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessagePageFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTitleKeylinePercent = getKeyLinePercent(context);
    }


    private static float getKeyLinePercent(Context context) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(
                android.support.v17.leanback.R.styleable.LeanbackGuidedStepTheme);
        float percent = ta.getFloat(
                android.support.v17.leanback.R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline,
                40);
        ta.recycle();
        return percent;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        TextView mStatusView = (TextView) getRootView().findViewById(R.id.status_text);
        View mContentView = getRootView().findViewById(R.id.message_content);
        int mTitleKeylinePixels = (int) (getMeasuredHeight() * mTitleKeylinePercent / 100);

        if (mStatusView != null) {
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) mStatusView.getLayoutParams();

            // To make the baseline of the textView match the key line, we need to delete the
            // existing textView top margin and the baseline offset for the text line.
            int guidanceTextContainerTop = mTitleKeylinePixels - lp.topMargin;
            // If there are more than 1 line, always make "second line base line" match
            // the key line. Otherwise, use the "first line base line".
            if (mStatusView.getLineCount() > 1) {
                guidanceTextContainerTop -= mStatusView.getLayout().getLineBaseline(1);
            } else {
                guidanceTextContainerTop -= mStatusView.getLayout().getLineBaseline(0);
            }

            int offset = guidanceTextContainerTop;
            mContentView.offsetTopAndBottom(offset);
        }
    }
}
