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

package com.android.tv.ui.sidepanel;

import android.view.View;
import android.widget.TextView;

import com.android.tv.R;

public class DividerItem extends Item {
    private TextView mTitleView;
    private String mTitle;

    public DividerItem() { }

    public DividerItem(String title) {
        mTitle = title;
    }

    @Override
    protected int getResourceId() {
        return R.layout.option_item_divider;
    }

    @Override
    protected void onBind(View view) {
        super.onBind(view);
        mTitleView = (TextView) view.findViewById(R.id.title);
        if (mTitle == null) {
            mTitleView.setVisibility(View.GONE);
            view.setMinimumHeight(0);
        } else {
            mTitleView.setVisibility(View.VISIBLE);
            mTitleView.setText(mTitle);
            view.setMinimumHeight(view.getContext().getResources().getDimensionPixelOffset(
                    R.dimen.option_item_height));
        }
    }

    @Override
    protected void onUnbind() {
        mTitleView = null;
    }

    @Override
    protected void onSelected() { }
}
