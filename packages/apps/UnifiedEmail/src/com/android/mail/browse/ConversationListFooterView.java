/*
 * Copyright (C) 2012 Google Inc.
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
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.android.mail.R;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;

public final class ConversationListFooterView extends LinearLayout implements View.OnClickListener {

    public interface FooterViewClickListener {
        void onFooterViewLoadMoreClick(Folder folder);
    }

    private View mLoading;
    private View mLoadMore;
    private Uri mLoadMoreUri;
    private FooterViewClickListener mClickListener;

    public ConversationListFooterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLoading = findViewById(R.id.loading);
        mLoadMore = findViewById(R.id.load_more);
        mLoadMore.setOnClickListener(this);
    }

    public void setClickListener(FooterViewClickListener listener) {
        mClickListener = listener;
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        final Folder f = (Folder) v.getTag();
        if (id == R.id.load_more) {
            mClickListener.onFooterViewLoadMoreClick(f);
        }
    }

    public void setFolder(Folder folder) {
        mLoadMore.setTag(folder);
        mLoadMoreUri = folder.loadMoreUri;
    }

    /**
     * Update the view to reflect the new folder status.
     */
    public boolean updateStatus(final ConversationCursor cursor) {
        if (cursor == null) {
            mLoading.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.GONE);
            return false;
        }
        boolean showFooter = true;
        final Bundle extras = cursor.getExtras();
        final int cursorStatus = extras.getInt(UIProvider.CursorExtraKeys.EXTRA_STATUS);
        final int totalCount = extras.getInt(UIProvider.CursorExtraKeys.EXTRA_TOTAL_COUNT);

        if (UIProvider.CursorStatus.isWaitingForResults(cursorStatus)) {
            if (cursor.getCount() != 0) {
                // When loading more, show the spinner in the footer.
                mLoading.setVisibility(View.VISIBLE);
                mLoadMore.setVisibility(View.GONE);
            } else {
                // We're currently loading, but we have no messages at all. We don't need to show
                // the footer, because we should be displaying the loading state on the
                // conversation list itself.
                showFooter = false;
            }

        } else if (mLoadMoreUri != null && cursor.getCount() < totalCount) {
            // We know that there are more messages on the server than we have locally, so we
            // need to show the footer with the "load more" button.
            mLoading.setVisibility(View.GONE);
            mLoadMore.setVisibility(View.VISIBLE);
        } else {
            showFooter = false;
        }
        return showFooter;
    }
}
