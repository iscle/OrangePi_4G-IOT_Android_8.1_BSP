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
 * limitations under the License
 */

package com.android.tv.dvr.ui;

import android.app.DialogFragment;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.R;

/**
 * A dialog fragment which contains {@link DvrStopSeriesRecordingFragment}.
 */
public class DvrStopSeriesRecordingDialogFragment extends DialogFragment {
    public static final String DIALOG_TAG = "dialog_tag";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.halfsized_dialog, container, false);
        GuidedStepFragment fragment = new DvrStopSeriesRecordingFragment();
        fragment.setArguments(getArguments());
        GuidedStepFragment.add(getChildFragmentManager(), fragment, R.id.halfsized_dialog_host);
        return view;
    }

    @Override
    public int getTheme() {
        return R.style.Theme_TV_dialog_HalfSizedDialog;
    }
}
