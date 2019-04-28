/*
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
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.View;

/**
 * Overrides {@code SwipeRefreshLayout} to enable swiping the empty state.
 */
public class MailSwipeRefreshLayout extends SwipeRefreshLayout {

    private View mScrollableChild;
    public MailSwipeRefreshLayout(Context context) {
        this(context, null);
    }

    public MailSwipeRefreshLayout(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public void setScrollableChild(View scrollableChild) {
        mScrollableChild = scrollableChild;
    }

    @Override
    public boolean canChildScrollUp() {
        return mScrollableChild.canScrollVertically(-1 /* up direction */);
    }
}
