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

package com.android.documentsui.picker;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;

/**
 * Used to confirm with user that it's OK to overwrite an existing file.
 */
public class OverwriteConfirmFragment extends DialogFragment {

    private static final String TAG = "OverwriteConfirmFragment";

    private ActionHandler<PickActivity> mActions;
    private DocumentInfo mOverwriteTarget;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActions = ((PickActivity) getActivity()).getInjector().actions;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arg = (getArguments() != null) ? getArguments() : savedInstanceState;

        mOverwriteTarget = arg.getParcelable(Shared.EXTRA_DOC);

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final String message = String.format(
                getString(R.string.overwrite_file_confirmation_message),
                mOverwriteTarget.displayName);
        builder.setMessage(message);
        builder.setPositiveButton(
                android.R.string.ok,
                (DialogInterface dialog, int id) ->
                        mActions.finishPicking(mOverwriteTarget.derivedUri));
        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putParcelable(Shared.EXTRA_DOC, mOverwriteTarget);
    }

    public static void show(FragmentManager fm, DocumentInfo overwriteTarget) {
        Bundle arg = new Bundle();
        arg.putParcelable(Shared.EXTRA_DOC, overwriteTarget);

        FragmentTransaction ft = fm.beginTransaction();
        Fragment f = new OverwriteConfirmFragment();
        f.setArguments(arg);
        ft.add(f, TAG);
        ft.commitAllowingStateLoss();
    }
}
