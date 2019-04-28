/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.telecom.components;

import com.android.server.telecom.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.Log;

// TODO: Needed for move to system service: import com.android.internal.R;

/**
 * Used to display an error dialog from within the Telecom service when an outgoing call fails
 */
public class ErrorDialogActivity extends Activity {
    private static final String TAG = ErrorDialogActivity.class.getSimpleName();

    public static final String SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA = "show_missing_voicemail";
    public static final String ERROR_MESSAGE_ID_EXTRA = "error_message_id";
    public static final String ERROR_MESSAGE_STRING_EXTRA = "error_message_string";

    /**
     * Intent action to bring up Voicemail Provider settings.
     */
    public static final String ACTION_ADD_VOICEMAIL =
            "com.android.phone.CallFeaturesSetting.ADD_VOICEMAIL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final boolean showVoicemailDialog = getIntent().getBooleanExtra(
                SHOW_MISSING_VOICEMAIL_NO_DIALOG_EXTRA, false);

        if (showVoicemailDialog) {
            showMissingVoicemailErrorDialog();
        }  else if (getIntent().getCharSequenceExtra(ERROR_MESSAGE_STRING_EXTRA) != null) {
            final CharSequence error = getIntent().getCharSequenceExtra(
                    ERROR_MESSAGE_STRING_EXTRA);
            showGenericErrorDialog(error);
        } else {
            final int error = getIntent().getIntExtra(ERROR_MESSAGE_ID_EXTRA, -1);
            if (error == -1) {
                Log.w(TAG, "ErrorDialogActivity called with no error type extra.");
                finish();
            } else {
                showGenericErrorDialog(error);
            }
        }
    }

    private void showGenericErrorDialog(CharSequence msg) {
        final DialogInterface.OnClickListener clickListener;
        final DialogInterface.OnCancelListener cancelListener;

        clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        };

        cancelListener = new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                finish();
            }
        };

        final AlertDialog errorDialog = new AlertDialog.Builder(this)
                .setMessage(msg).setPositiveButton(android.R.string.ok, clickListener)
                        .setOnCancelListener(cancelListener).create();

        errorDialog.show();
    }

    private void showGenericErrorDialog(int resid) {
        final CharSequence msg = getResources().getText(resid);
        showGenericErrorDialog(msg);
    }

    private void showMissingVoicemailErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.no_vm_number)
                .setMessage(R.string.no_vm_number_msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }})
                .setNegativeButton(R.string.add_vm_number_str,
                        new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    addVoiceMailNumberPanel(dialog);
                                }})
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }}).show();
    }


    private void addVoiceMailNumberPanel(DialogInterface dialog) {
        if (dialog != null) {
            dialog.dismiss();
        }

        // Navigate to the Voicemail setting in the Call Settings activity.
        Intent intent = new Intent(ACTION_ADD_VOICEMAIL);
        startActivity(intent);
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        // Don't show the return to previous task animation to avoid showing a black screen.
        // Just dismiss the dialog and undim the previous activity immediately.
        overridePendingTransition(0, 0);
    }
}
