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
 * limitations under the License.
 */
package com.android.car.settings.display;

import android.os.Bundle;

import com.android.car.settings.R;
import com.android.car.settings.common.ListSettingsFragment;
import com.android.car.settings.common.TypedPagedListAdapter;

import java.util.ArrayList;

/**
 * Activity to host Display related preferences.
 */
public class DisplaySettingsFragment extends ListSettingsFragment {

    public static DisplaySettingsFragment getInstance() {
        DisplaySettingsFragment displaySettingsFragment = new DisplaySettingsFragment();
        Bundle bundle = ListSettingsFragment.getBundle();
        bundle.putInt(EXTRA_TITLE_ID, R.string.display_settings);
        displaySettingsFragment.setArguments(bundle);
        return displaySettingsFragment;
    }

    @Override
    public ArrayList<TypedPagedListAdapter.LineItem> getLineItems() {
        ArrayList<TypedPagedListAdapter.LineItem> lineItems = new ArrayList<>();
        lineItems.add(new AutoBrightnessLineItem(getContext()));
        lineItems.add(new BrightnessLineItem(getContext()));
        return lineItems;
    }
}
