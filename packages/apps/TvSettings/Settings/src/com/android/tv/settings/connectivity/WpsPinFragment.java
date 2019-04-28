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
import android.view.View;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

/**
 * Displays a UI for showing that the user must enter a PIN for WPS to continue
 */
public class WpsPinFragment extends ProgressDialogFragment {

    private static final String KEY_PIN = "pin";

    public static WpsPinFragment newInstance(String pin) {
        WpsPinFragment fragment = new WpsPinFragment();
        Bundle args = new Bundle();
        args.putString(KEY_PIN, pin);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(getString(R.string.wifi_wps_onstart_pin,
                getArguments().getString(KEY_PIN)));
        setSummary(R.string.wifi_wps_onstart_pin_description);
        setIcon(R.drawable.ic_wps);
    }
}
