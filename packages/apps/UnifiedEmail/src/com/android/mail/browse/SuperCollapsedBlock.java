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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.utils.ViewUtils;

import java.text.NumberFormat;

/**
 * A header block that expands to a list of collapsed message headers. Will notify a listener on tap
 * so the listener can hide the block and reveal the corresponding collapsed message headers.
 *
 */
public class SuperCollapsedBlock extends FrameLayout implements View.OnClickListener {

    public interface OnClickListener {
        /**
         * Handle a click on a super-collapsed block.
         *
         */
        void onSuperCollapsedClick(SuperCollapsedBlockItem item);
    }

    private SuperCollapsedBlockItem mSuperCollapsedItem;
    private OnClickListener mClick;
    private TextView mSuperCollapsedText;
    private View mSuperCollapsedProgress;

    private int mCount;

    public SuperCollapsedBlock(Context context) {
        this(context, null);
    }

    public SuperCollapsedBlock(Context context, AttributeSet attrs) {
        super(context, attrs);
        setActivated(false);
        setOnClickListener(this);
    }

    public void initialize(OnClickListener onClick) {
        mClick = onClick;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSuperCollapsedText = (TextView) findViewById(R.id.super_collapsed_text);
        mSuperCollapsedProgress = findViewById(R.id.super_collapsed_progress);
    }

    public void bind(SuperCollapsedBlockItem item) {
        mSuperCollapsedItem = item;
        mSuperCollapsedText.setVisibility(VISIBLE);
        mSuperCollapsedProgress.setVisibility(GONE);
        setCount(item.getEnd() - item.getStart() + 1);
    }

    public void setCount(int count) {
        mCount = count;
        final String strCount = NumberFormat.getIntegerInstance().format(mCount);
        mSuperCollapsedText.setText(strCount);
        final Resources res = getResources();
        final int colorId = mSuperCollapsedItem.hasDraft() ?
                R.color.text_color_draft_red : R.color.conversation_view_text_color_light;
        mSuperCollapsedText.setTextColor(res.getColor(colorId));
        setContentDescription(
                res.getQuantityString(R.plurals.show_messages_read, mCount, mCount));
    }

    @Override
    public void onClick(final View v) {
        mSuperCollapsedText.setVisibility(GONE);
        mSuperCollapsedProgress.setVisibility(VISIBLE);
        final String announcement = getResources().getQuantityString(
                R.plurals.super_collapsed_block_accessibility_announcement, mCount, mCount);
        ViewUtils.announceForAccessibility(this, announcement);

        if (mClick != null) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mClick.onSuperCollapsedClick(mSuperCollapsedItem);
                }
            });
        }
    }
}
