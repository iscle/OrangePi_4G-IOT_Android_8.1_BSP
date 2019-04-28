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
 * limitations under the License
 */

package com.android.tv.settings.device.storage;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

public class FormattingProgressFragment extends ProgressDialogFragment {

    public static FormattingProgressFragment newInstance() {
        return new FormattingProgressFragment();
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(getActivity().getString(R.string.storage_wizard_format_progress_title));
        setSummary(getActivity().getString(R.string.storage_wizard_format_progress_description));
    }
}
