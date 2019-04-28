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

package com.android.tv.onboarding;

import android.app.Fragment;
import android.os.Bundle;
import android.transition.Slide;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.ui.setup.SetupActionHelper;
import com.android.tv.util.SetupUtils;

/**
 * A fragment for new channel source info/setup.
 */
public class NewSourcesFragment extends Fragment {
    /**
     * The action category.
     */
    public static final String ACTION_CATEOGRY =
            "com.android.tv.onboarding.NewSourcesFragment";
    /**
     * An action to show the setup screen.
     */
    public static final int ACTION_SETUP = 1;
    /**
     * An action to close this fragment.
     */
    public static final int ACTION_SKIP = 2;

    public NewSourcesFragment() {
        setAllowEnterTransitionOverlap(false);
        setAllowReturnTransitionOverlap(false);
        setEnterTransition(new Slide(Gravity.BOTTOM));
        setExitTransition(new Slide(Gravity.BOTTOM));
        setReenterTransition(new Slide(Gravity.BOTTOM));
        setReturnTransition(new Slide(Gravity.BOTTOM));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_new_sources, container, false);
        initializeButton(view.findViewById(R.id.setup), ACTION_SETUP);
        initializeButton(view.findViewById(R.id.skip), ACTION_SKIP);
        SetupUtils.getInstance(getActivity()).markAllInputsRecognized(TvApplication
                .getSingletons(getActivity()).getTvInputManagerHelper());
        view.requestFocus();
        return view;
    }

    private void initializeButton(View view, int actionId) {
        view.setOnClickListener(SetupActionHelper.createOnClickListenerForAction(this,
                ACTION_CATEOGRY, actionId, null));
    }
}
