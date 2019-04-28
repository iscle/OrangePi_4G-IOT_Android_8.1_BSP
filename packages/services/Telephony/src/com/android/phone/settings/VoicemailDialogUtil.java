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
 * limitations under the License
 */

package com.android.phone.settings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.view.WindowManager;

import com.android.phone.R;

public class VoicemailDialogUtil {

    // Voicemail dialog identifiers.
    public static final int VM_NOCHANGE_ERROR_DIALOG = 400;
    public static final int VM_RESPONSE_ERROR_DIALOG = 500;
    public static final int FWD_SET_RESPONSE_ERROR_DIALOG = 501;
    public static final int FWD_GET_RESPONSE_ERROR_DIALOG = 502;
    public static final int VM_CONFIRM_DIALOG = 600;
    public static final int VM_FWD_SAVING_DIALOG = 601;
    public static final int VM_FWD_READING_DIALOG = 602;
    public static final int VM_REVERTING_DIALOG = 603;
    public static final int TTY_SET_RESPONSE_ERROR = 800;

    public static Dialog getDialog(VoicemailSettingsActivity parent, int id) {
        if ((id == VM_RESPONSE_ERROR_DIALOG) || (id == VM_NOCHANGE_ERROR_DIALOG) ||
            (id == FWD_SET_RESPONSE_ERROR_DIALOG) || (id == FWD_GET_RESPONSE_ERROR_DIALOG) ||
                (id == VM_CONFIRM_DIALOG) || (id == TTY_SET_RESPONSE_ERROR)) {

            AlertDialog.Builder b = new AlertDialog.Builder(parent);

            int msgId;
            int titleId = R.string.error_updating_title;
            switch (id) {
                case VM_CONFIRM_DIALOG:
                    msgId = R.string.vm_changed;
                    titleId = R.string.voicemail;
                    // Set Button 2
                    b.setNegativeButton(R.string.close_dialog, parent);
                    break;
                case VM_NOCHANGE_ERROR_DIALOG:
                    // even though this is technically an error,
                    // keep the title friendly.
                    msgId = R.string.no_change;
                    titleId = R.string.voicemail;
                    // Set Button 2
                    b.setNegativeButton(R.string.close_dialog, parent);
                    break;
                case VM_RESPONSE_ERROR_DIALOG:
                    msgId = R.string.vm_change_failed;
                    // Set Button 1
                    b.setPositiveButton(R.string.close_dialog, parent);
                    break;
                case FWD_SET_RESPONSE_ERROR_DIALOG:
                    msgId = R.string.fw_change_failed;
                    // Set Button 1
                    b.setPositiveButton(R.string.close_dialog, parent);
                    break;
                case FWD_GET_RESPONSE_ERROR_DIALOG:
                    msgId = R.string.fw_get_in_vm_failed;
                    b.setPositiveButton(R.string.alert_dialog_yes, parent);
                    b.setNegativeButton(R.string.alert_dialog_no, parent);
                    break;
                case TTY_SET_RESPONSE_ERROR:
                    titleId = R.string.tty_mode_option_title;
                    msgId = R.string.tty_mode_not_allowed_video_call;
                    b.setIconAttribute(android.R.attr.alertDialogIcon);
                    b.setPositiveButton(R.string.ok, parent);
                    break;
                default:
                    msgId = R.string.exception_error;
                    // Set Button 3, tells the activity that the error is
                    // not recoverable on dialog exit.
                    b.setNeutralButton(R.string.close_dialog, parent);
                    break;
            }

            b.setTitle(parent.getText(titleId));
            String message = parent.getText(msgId).toString();
            b.setMessage(message);
            b.setCancelable(false);
            AlertDialog dialog = b.create();

            // make the dialog more obvious by bluring the background.
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

            return dialog;
        } else if (id == VM_FWD_SAVING_DIALOG || id == VM_FWD_READING_DIALOG ||
                id == VM_REVERTING_DIALOG) {
            ProgressDialog dialog = new ProgressDialog(parent);
            dialog.setTitle(parent.getText(R.string.call_settings));
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.setMessage(parent.getText(
                    id == VM_FWD_SAVING_DIALOG ? R.string.updating_settings :
                    (id == VM_REVERTING_DIALOG ? R.string.reverting_settings :
                    R.string.reading_settings)));
            return dialog;
        }

        return null;
    }
}
