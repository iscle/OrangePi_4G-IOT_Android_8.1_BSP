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

package com.android.tv.common.ui.setup;

import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.leanback.widget.VerticalGridView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.LinearLayout;

import com.android.tv.common.R;

/**
 * A fragment for channel source info/setup.
 */
public abstract class SetupGuidedStepFragment extends GuidedStepFragment {
    /**
     * Key of the argument which indicate whether the parent of this fragment has three panes.
     *
     * <p>Value type: boolean
     */
    public static final String KEY_THREE_PANE = "key_three_pane";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        Bundle arguments = getArguments();
        view.findViewById(R.id.action_fragment_root).setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams guidanceLayoutParams = (LinearLayout.LayoutParams)
                view.findViewById(R.id.content_fragment).getLayoutParams();
        guidanceLayoutParams.weight = 0;
        if (arguments != null && arguments.getBoolean(KEY_THREE_PANE, false)) {
            // Content fragment.
            guidanceLayoutParams.width = getResources().getDimensionPixelOffset(
                    R.dimen.setup_guidedstep_guidance_section_width_3pane);
            int doneButtonWidth = getResources().getDimensionPixelOffset(
                    R.dimen.setup_done_button_container_width);
            // Guided actions list
            View list = view.findViewById(R.id.guidedactions_list);
            MarginLayoutParams marginLayoutParams = (MarginLayoutParams) list.getLayoutParams();
            // Use content view to check layout direction while view is being created.
            if (getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_LTR) {
                marginLayoutParams.rightMargin = doneButtonWidth;
            } else {
                marginLayoutParams.leftMargin = doneButtonWidth;
            }
        } else {
            // Content fragment.
            guidanceLayoutParams.width = getResources().getDimensionPixelOffset(
                    R.dimen.setup_guidedstep_guidance_section_width_2pane);
        }
        // gridView Alignment
        VerticalGridView gridView = getGuidedActionsStylist().getActionsGridView();
        int offset = getResources().getDimensionPixelOffset(
                R.dimen.setup_guidedactions_selector_margin_top);
        gridView.setWindowAlignmentOffset(offset);
        gridView.setWindowAlignmentOffsetPercent(0);
        gridView.setItemAlignmentOffsetPercent(0);
        ((ViewGroup) view.findViewById(R.id.guidedactions_list)).setTransitionGroup(false);
        // Needed for the shared element transition.
        // content_frame is defined in leanback.
        ViewGroup group = (ViewGroup) view.findViewById(R.id.content_frame);
        group.setClipChildren(false);
        group.setClipToPadding(false);
        return view;
    }

    @Override
    public GuidanceStylist onCreateGuidanceStylist() {
        return new GuidanceStylist() {
            @Override
            public View onCreateView(LayoutInflater inflater, ViewGroup container,
                    Guidance guidance) {
                View view = super.onCreateView(inflater, container, guidance);
                if (guidance.getIconDrawable() == null) {
                    // Icon view should not take up space when we don't use image.
                    getIconView().setVisibility(View.GONE);
                }
                return view;
            }
        };
    }

    abstract protected String getActionCategory();

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        SetupActionHelper.onActionClick(this, getActionCategory(), (int) action.getId());
    }

    @Override
    protected void onProvideFragmentTransitions() {
        // Don't use the fragment transition defined in GuidedStepFragment.
    }

    @Override
    public boolean isFocusOutEndAllowed() {
        return true;
    }
}
