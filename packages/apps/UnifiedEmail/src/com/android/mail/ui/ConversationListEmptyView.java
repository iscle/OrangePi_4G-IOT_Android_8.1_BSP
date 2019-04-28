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
import android.support.v4.text.BidiFormatter;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.utils.EmptyStateUtils;

/**
 * Empty view for {@link ConversationListFragment}.
 */
public class ConversationListEmptyView extends LinearLayout {

    private ImageView mIcon;
    private TextView mText;

    public ConversationListEmptyView(Context context) {
        this(context, null);
    }

    public ConversationListEmptyView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mIcon = (ImageView) findViewById(R.id.empty_icon);
        mText = (TextView) findViewById(R.id.empty_text);
    }

    /**
     * Initializes the empty view to use the proper icon and text
     * based on the type of folder that will be visible.
     */
    public void setupEmptyText(final Folder folder, final String searchQuery,
            final BidiFormatter bidiFormatter, boolean shouldShowIcon) {
        if (shouldShowIcon) {
            EmptyStateUtils.bindEmptyFolderIcon(mIcon, folder);
            mIcon.setVisibility(VISIBLE);
        } else {
            mIcon.setVisibility(GONE);
        }

        EmptyStateUtils.bindEmptyFolderText(mText, folder, getResources(), searchQuery,
                bidiFormatter);
    }
}
