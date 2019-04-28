/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import android.support.annotation.Nullable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

/**
 * Displays a UI for showing that WPS is active
 */
public class WpsScanningFragment extends ProgressDialogFragment {

    public static WpsScanningFragment newInstance() {
        return new WpsScanningFragment();
    }

    // The progress dialog fragment is not full screen, but fragment transition assumes all
    // fragments are fullscreen.  This class adds a FrameLayout to hold the actual content.
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        FrameLayout holder = new FrameLayout(getActivity());
        holder.setLayoutParams(
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        holder.addView(view);
        return holder;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.wifi_wps_title);
        setSummary(R.string.wifi_wps_instructions);
        setIcon(R.drawable.setup_wps);
    }
}
