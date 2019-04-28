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

package com.android.tv.settings.accessories;

import android.annotation.Nullable;
import android.os.Bundle;
import android.view.View;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

/**
 * Custom Content Fragment for the Bluetooth settings activity.
 */
public class AddAccessoryContentFragment extends ProgressDialogFragment {

    public static AddAccessoryContentFragment newInstance() {
        return new AddAccessoryContentFragment();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.accessories_add_title);
        setIcon(R.drawable.ic_bluetooth_searching_128dp);
        setSummary(R.string.accessories_add_bluetooth_inst);
    }
}
