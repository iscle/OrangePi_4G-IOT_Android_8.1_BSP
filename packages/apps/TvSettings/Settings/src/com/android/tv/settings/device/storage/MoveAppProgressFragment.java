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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;

import com.android.tv.settings.R;
import com.android.tv.settings.dialog.ProgressDialogFragment;

public class MoveAppProgressFragment extends ProgressDialogFragment {

    private static final String ARG_APP_TITLE = "appTitle";

    public static MoveAppProgressFragment newInstance(CharSequence appTitle) {
        final MoveAppProgressFragment fragment = new MoveAppProgressFragment();
        final Bundle b = new Bundle(1);
        b.putCharSequence(ARG_APP_TITLE, appTitle);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final CharSequence appTitle = getArguments().getCharSequence(ARG_APP_TITLE);
        setTitle(getActivity().getString(R.string.storage_wizard_move_app_progress_title,
                appTitle));
        setSummary(getActivity().getString(R.string.storage_wizard_move_app_progress_description,
                appTitle));
    }

    public static CharSequence moveStatusToMessage(Context context, int returnCode) {
        switch (returnCode) {
            case PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE:
                return context.getString(R.string.insufficient_storage);
            case PackageManager.MOVE_FAILED_DEVICE_ADMIN:
                return context.getString(R.string.move_error_device_admin);
            case PackageManager.MOVE_FAILED_DOESNT_EXIST:
                return context.getString(R.string.does_not_exist);
            case PackageManager.MOVE_FAILED_FORWARD_LOCKED:
                return context.getString(R.string.app_forward_locked);
            case PackageManager.MOVE_FAILED_INVALID_LOCATION:
                return context.getString(R.string.invalid_location);
            case PackageManager.MOVE_FAILED_SYSTEM_PACKAGE:
                return context.getString(R.string.system_package);
            case PackageManager.MOVE_FAILED_INTERNAL_ERROR:
            default:
                return context.getString(R.string.insufficient_storage);
        }
    }

}
