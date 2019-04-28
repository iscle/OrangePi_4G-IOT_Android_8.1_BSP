/*
 * Copyright (C) 2011 Google Inc.
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
import android.view.View;

import com.android.mail.browse.ScrollNotifier.ScrollListener;

/**
 * An overlay to sit on top of WebView, message headers, and snap header to display scrollbars.
 * It has to sit on top of all other views that compose the conversation so that the scrollbars are
 * not obscured.
 *
 */
public class ScrollIndicatorsView extends View implements ScrollListener {

    private ScrollNotifier mSource;

    public ScrollIndicatorsView(Context context) {
        super(context);
    }

    public ScrollIndicatorsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSourceView(ScrollNotifier notifier) {
        mSource = notifier;
        mSource.addScrollListener(this);
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mSource.computeVerticalScrollRange();
    }

    @Override
    protected int computeVerticalScrollOffset() {
        return mSource.computeVerticalScrollOffset();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return mSource.computeVerticalScrollExtent();
    }

    @Override
    protected int computeHorizontalScrollRange() {
        return mSource.computeHorizontalScrollRange();
    }

    @Override
    protected int computeHorizontalScrollOffset() {
        return mSource.computeHorizontalScrollOffset();
    }

    @Override
    protected int computeHorizontalScrollExtent() {
        return mSource.computeHorizontalScrollExtent();
    }

    @Override
    public void onNotifierScroll(int top) {
        awakenScrollBars();
    }
}
