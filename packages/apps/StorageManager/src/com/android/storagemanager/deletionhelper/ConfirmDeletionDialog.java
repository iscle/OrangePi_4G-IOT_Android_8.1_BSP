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
 * limitations under the License.
 */

package com.android.storagemanager.deletionhelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.format.Formatter;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.storagemanager.R;
import com.android.storagemanager.utils.Constants;

/**
 * Fragment used to confirm that the user wishes to delete a certain amount of data.
 */
public class ConfirmDeletionDialog extends DialogFragment implements
        DialogInterface.OnClickListener {
    public static final String TAG = "ConfirmDeletionDialog";
    private static final String ARG_TOTAL_SPACE = "total_freeable";
    // If the confirm deletion dialog has been shown before. Used to choose which warning message
    // we show to the user.
    private static final String SHOWN_BEFORE = "shown_before";

    private long mFreeableBytes;

    public static ConfirmDeletionDialog newInstance(long freeableBytes) {
        Bundle args = new Bundle(1);
        args.putLong(ARG_TOTAL_SPACE, freeableBytes);

        ConfirmDeletionDialog dialog = new ConfirmDeletionDialog();
        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Bundle args = getArguments();
        mFreeableBytes = args.getLong(ARG_TOTAL_SPACE);

        final Context context = getContext();
        return new AlertDialog.Builder(context)
                .setMessage(context.getString(getClearWarningText(),
                        Formatter.formatFileSize(context, mFreeableBytes)))
                .setPositiveButton(R.string.deletion_helper_clear_dialog_remove, this)
                .setNegativeButton(android.R.string.cancel, this)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        // Set the first time flag to avoid showing the first time warning twice.
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putBoolean(SHOWN_BEFORE, true);
        editor.apply();

        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                ((DeletionHelperSettings) getTargetFragment()).clearData();
                MetricsLogger.action(getContext(),
                        MetricsEvent.ACTION_DELETION_HELPER_REMOVE_CONFIRM);
                if (StorageManagerUpsellDialog.shouldShow(
                        getContext(), System.currentTimeMillis())) {
                    StorageManagerUpsellDialog upsellDialog =
                            StorageManagerUpsellDialog.newInstance(mFreeableBytes);
                    upsellDialog.show(getFragmentManager(), StorageManagerUpsellDialog.TAG);
                } else {
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.finish();
                    }
                }
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                MetricsLogger.action(getContext(),
                        MetricsEvent.ACTION_DELETION_HELPER_REMOVE_CANCEL);
                break;
            default:
                break;
        }
    }

    private int getClearWarningText() {
        // If the storage manager is on by default, we can use the normal message.
        boolean warningUnneeded = SystemProperties.getBoolean(
                Constants.STORAGE_MANAGER_VISIBLE_PROPERTY, false);
        if (warningUnneeded) {
            return R.string.deletion_helper_clear_dialog_message;
        }

        SharedPreferences sp = getSharedPreferences();
        boolean shownBefore = sp.getBoolean(SHOWN_BEFORE, false);
        return shownBefore ? R.string.deletion_helper_clear_dialog_message :
                R.string.deletion_helper_clear_dialog_message_first_time;
    }

    private SharedPreferences getSharedPreferences() {
        return getContext().getSharedPreferences(Constants.SHARED_PREFERENCE_NAME,
                Context.MODE_PRIVATE);
    }
}
