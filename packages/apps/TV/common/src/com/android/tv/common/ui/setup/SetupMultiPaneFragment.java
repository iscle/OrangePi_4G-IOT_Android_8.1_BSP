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
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;

import com.android.tv.common.R;

/**
 * A fragment for channel source info/setup.
 */
public abstract class SetupMultiPaneFragment extends SetupFragment {
    private static final String TAG = "SetupMultiPaneFragment";
    private static final boolean DEBUG = false;

    public static final int ACTION_DONE = Integer.MAX_VALUE;
    public static final int ACTION_SKIP = ACTION_DONE - 1;

    private static final String CONTENT_FRAGMENT_TAG = "content_fragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView(" + inflater + ", " + container + ", " + savedInstanceState
                    + ")");
        }
        View view = super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null) {
            SetupGuidedStepFragment contentFragment = onCreateContentFragment();
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.guided_step_fragment_container, contentFragment,
                            CONTENT_FRAGMENT_TAG).commit();
        }
        if (needsDoneButton()) {
            setOnClickAction(view.findViewById(R.id.button_done), getActionCategory(), ACTION_DONE);
        }
        if (needsSkipButton()) {
            view.findViewById(R.id.button_skip).setVisibility(View.VISIBLE);
            setOnClickAction(view.findViewById(R.id.button_skip), getActionCategory(), ACTION_SKIP);
        }
        if (!needsDoneButton() && !needsSkipButton()) {
            View doneButtonContainer = view.findViewById(R.id.done_button_container);
            // Use content view to check layout direction while view is being created.
            if (getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_LTR) {
                ((MarginLayoutParams) doneButtonContainer.getLayoutParams()).rightMargin =
                        -getResources().getDimensionPixelOffset(
                                R.dimen.setup_done_button_container_width);
            } else {
                ((MarginLayoutParams) doneButtonContainer.getLayoutParams()).leftMargin =
                        -getResources().getDimensionPixelOffset(
                                R.dimen.setup_done_button_container_width);
            }
            view.findViewById(R.id.button_done).setFocusable(false);
        }
        return view;
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.fragment_setup_multi_pane;
    }

    abstract protected SetupGuidedStepFragment onCreateContentFragment();

    @Nullable
    protected SetupGuidedStepFragment getContentFragment() {
        return (SetupGuidedStepFragment) getChildFragmentManager()
                .findFragmentByTag(CONTENT_FRAGMENT_TAG);
    }

    abstract protected String getActionCategory();

    protected boolean needsDoneButton() {
        return true;
    }

    protected boolean needsSkipButton() {
        return false;
    }

    @Override
    protected int[] getParentIdsForDelay() {
        return new int[] {R.id.content_fragment, R.id.guidedactions_list};
    }

    @Override
    public int[] getSharedElementIds() {
        return new int[] {R.id.action_fragment_background, R.id.done_button_container};
    }
}