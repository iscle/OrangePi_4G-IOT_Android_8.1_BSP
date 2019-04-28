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

package com.android.tv.ui;

import android.content.Context;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.GuidedActionsStylist;

import com.android.tv.R;

/**
 * Extended stylist class used for {@link GuidedStepFragment} with divider support.
 */
public class GuidedActionsStylistWithDivider extends GuidedActionsStylist {
    /**
     * ID used mark a divider.
     */
    public static final int ACTION_DIVIDER = -100;
    private static final int VIEW_TYPE_DIVIDER = 1;

    @Override
    public int getItemViewType(GuidedAction action) {
        if (action.getId() == ACTION_DIVIDER) {
            return VIEW_TYPE_DIVIDER;
        }
        return super.getItemViewType(action);
    }

    @Override
    public int onProvideItemLayoutId(int viewType) {
        if (viewType == VIEW_TYPE_DIVIDER) {
            return R.layout.guided_action_divider;
        }
        return super.onProvideItemLayoutId(viewType);
    }

    /**
     * Creates a divider for {@link GuidedStepFragment}, targeted fragments must use
     * {@link GuidedActionsStylistWithDivider} as its actions' stylist for divider to work.
     */
    public static GuidedAction createDividerAction(Context context) {
        return new GuidedAction.Builder(context)
                .id(ACTION_DIVIDER)
                .title(null)
                .description(null)
                .focusable(false)
                .infoOnly(true)
                .build();
    }
}
