/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.car.settings.common;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.car.settings.R;

import java.util.Set;

/**
 * Base fragment for setting activity.
 */
public abstract class BaseFragment extends Fragment {
    public static final String EXTRA_TITLE_ID = "extra_title_id";
    public static final String EXTRA_LAYOUT = "extra_layout";
    public static final String EXTRA_ACTION_BAR_LAYOUT = "extra_action_bar_layout";

    /**
     * Controls the transition of fragment.
     */
    public interface FragmentController {
        /**
         * Launches fragment in the main container of current activity.
         */
        void launchFragment(BaseFragment fragment);

        /**
         * Pops the top off the fragment stack.
         */
        void goBack();
    }

    @LayoutRes
    protected int mLayout;

    @LayoutRes
    private int mActionBarLayout;

    @StringRes
    private int mTitleId;

    protected FragmentController mFragmentController;

    public void setFragmentController(FragmentController fragmentController) {
        mFragmentController = fragmentController;
    }

    protected static Bundle getBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt(EXTRA_ACTION_BAR_LAYOUT, R.layout.action_bar);
        return bundle;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Set<String> extraKeys = getArguments().keySet();
        if (extraKeys.contains(EXTRA_ACTION_BAR_LAYOUT)) {
            mActionBarLayout = getArguments().getInt(EXTRA_ACTION_BAR_LAYOUT);
        } else {
            throw new IllegalArgumentException("must specify a actionBar layout");
        }
        if (extraKeys.contains(EXTRA_LAYOUT)) {
            mLayout = getArguments().getInt(EXTRA_LAYOUT);
        } else {
            throw new IllegalArgumentException("must specify a layout");
        }
        if (extraKeys.contains(EXTRA_TITLE_ID)) {
            mTitleId = getArguments().getInt(EXTRA_TITLE_ID);
        } else {
            throw new IllegalArgumentException("must specify a title");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(mLayout, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setCustomView(mActionBarLayout);
        actionBar.setDisplayShowCustomEnabled(true);
        // make the toolbar take the whole width.
        Toolbar toolbar=(Toolbar)actionBar.getCustomView().getParent();
        toolbar.setPadding(0, 0, 0, 0);
        getActivity().findViewById(R.id.action_bar_icon_container).setOnClickListener(
                v -> mFragmentController.goBack());
        ((TextView) getActivity().findViewById(R.id.title)).setText(mTitleId);
    }
}
