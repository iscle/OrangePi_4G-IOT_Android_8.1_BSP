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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.tv.settings.R;

/**
 * Relative layout implementation that lays out child views based on provided keyline percent(
 * distance of TitleView baseline from the top). We do the similar thing in
 * {@link com.google.android.tungsten.setupwraith.ui.GuidanceRelativeLayout}
 */
public class GuidanceRelativeLayout extends RelativeLayout {
    private float mTitleKeylinePercent;

    public GuidanceRelativeLayout(Context context) {
        this(context, null);
    }

    public GuidanceRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GuidanceRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTitleKeylinePercent = getKeyLinePercent(context);
    }

    private static float getKeyLinePercent(Context context) {
        TypedArray ta = context.getTheme().obtainStyledAttributes(
                R.styleable.LeanbackGuidedStepTheme);
        float percent = ta.getFloat(R.styleable.LeanbackGuidedStepTheme_guidedStepKeyline, 40);
        ta.recycle();
        return percent;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        TextView titleView = (TextView) getRootView().findViewById(R.id.guidance_title);
        TextView descriptionView = (TextView) getRootView().findViewById(R.id.guidance_description);

        int mTitleKeylinePixels = (int) (getMeasuredHeight() * mTitleKeylinePercent / 100);
        if (titleView != null && titleView.getParent() == this) {
            LayoutParams lp = (LayoutParams) titleView.getLayoutParams();

            // To make the mid position of a text line match the key line, we need to delete the
            // existing textview top margin, the icon height and the baseline offset for the text
            // line.
            int guidanceTextContainerTop = mTitleKeylinePixels - lp.topMargin;

            // If there are more than 1 line, always make "second line baseline" match
            // the key line. Otherwise, use the "first line baseline".
            if (titleView.getLineCount() > 1) {
                guidanceTextContainerTop -= titleView.getLayout().getLineBaseline(1);
            } else {
                guidanceTextContainerTop -= titleView.getLayout().getLineBaseline(0);
            }

            int offset = guidanceTextContainerTop;
            titleView.offsetTopAndBottom(offset);

            if (descriptionView != null && descriptionView.getParent() == this) {
                descriptionView.offsetTopAndBottom(offset);
            }
        }
    }
}
