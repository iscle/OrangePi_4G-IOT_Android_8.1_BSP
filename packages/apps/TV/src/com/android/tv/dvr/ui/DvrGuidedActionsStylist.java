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
 * limitations under the License.
 */

package com.android.tv.dvr.ui;

import android.content.Context;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedActionsStylist;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.tv.R;

/**
 * Stylist class used for DVR settings {@link GuidedStepFragment}.
 */
public class DvrGuidedActionsStylist extends GuidedActionsStylist {
    private static boolean sInitialized;
    private static float sWidthWeight;
    private static int sItemHeight;

    private final boolean mIsButtonActions;

    public DvrGuidedActionsStylist(boolean isButtonActions) {
        super();
        mIsButtonActions = isButtonActions;
        if (mIsButtonActions) {
            setAsButtonActions();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container) {
        initializeIfNeeded(container.getContext());
        View v = super.onCreateView(inflater, container);
        if (mIsButtonActions) {
            ((LinearLayout.LayoutParams) v.getLayoutParams()).weight = sWidthWeight;
        }
        return v;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        initializeIfNeeded(parent.getContext());
        ViewHolder viewHolder = super.onCreateViewHolder(parent);
        viewHolder.itemView.getLayoutParams().height = sItemHeight;
        return viewHolder;
    }

    private void initializeIfNeeded(Context context) {
        if (sInitialized) {
            return;
        }
        sInitialized = true;
        sItemHeight = context.getResources().getDimensionPixelSize(
                R.dimen.dvr_settings_one_line_action_container_height);
        TypedValue outValue = new TypedValue();
        context.getResources().getValue(R.dimen.dvr_settings_button_actions_list_width_weight,
                outValue, true);
        sWidthWeight = outValue.getFloat();
    }
}
