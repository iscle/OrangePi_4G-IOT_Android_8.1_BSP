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

package com.android.documentsui.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.text.format.DateUtils;

import com.android.documentsui.R;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService.MessageType;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperations;

import android.support.annotation.StringRes;
import android.util.Log;

public class OperationProgressDialog {

    private final Activity mActivity;
    private final ProgressDialog mDialog;
    private final String mJobId;

    private OperationProgressDialog(Activity activity, String jobId, @StringRes int titleResId,
            @StringRes int prepareResId, final FileOperation operation) {
        mActivity = activity;
        mJobId = jobId;
        mDialog = new ProgressDialog(mActivity);
        mDialog.setTitle(mActivity.getString(titleResId));
        mDialog.setMessage(mActivity.getString(prepareResId));
        mDialog.setProgress(0);
        mDialog.setMax(100);
        mDialog.setIndeterminate(true);
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setCanceledOnTouchOutside(false);

        mDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                activity.getString(android.R.string.cancel),
                (dialog, button) -> {
                    FileOperations.cancel(mActivity, mJobId);
                    mDialog.dismiss();
                });

        mDialog.setButton(ProgressDialog.BUTTON_NEUTRAL,
                activity.getString(R.string.continue_in_background),
                (dialog, button) -> mDialog.dismiss());

        operation.addMessageListener(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case FileOperationService.MESSAGE_PROGRESS:
                        mDialog.setIndeterminate(false);
                        if (message.arg1 != -1) {
                            mDialog.setProgress(message.arg1);
                        }
                        if (message.arg2 > 0) {
                            mDialog.setMessage(mActivity.getString(R.string.copy_remaining,
                                    DateUtils.formatDuration(message.arg2)));
                        }
                        return true;
                    case FileOperationService.MESSAGE_FINISH:
                        operation.removeMessageListener(this);
                        mDialog.dismiss();
                        return true;
                }
                return false;
            }
        });
    }

    public static OperationProgressDialog create(Activity activity, String jobId,
            FileOperation operation) {
        int titleResId;
        int prepareResId;
        switch (operation.getOpType()) {
            case FileOperationService.OPERATION_COPY:
                titleResId = R.string.copy_notification_title;
                prepareResId = R.string.copy_preparing;
                break;
            case FileOperationService.OPERATION_COMPRESS:
                titleResId = R.string.compress_notification_title;
                prepareResId = R.string.compress_preparing;
                break;
            case FileOperationService.OPERATION_EXTRACT:
                titleResId = R.string.extract_notification_title;
                prepareResId = R.string.extract_preparing;
                break;
            case FileOperationService.OPERATION_MOVE:
                titleResId = R.string.move_notification_title;
                prepareResId = R.string.move_preparing;
                break;
            case FileOperationService.OPERATION_DELETE:
                // Not supported yet. Pass through to default.
            default:
                throw new IllegalArgumentException();
        }
        return new OperationProgressDialog(activity, jobId, titleResId, prepareResId, operation);
    }

    public void dismiss() {
        mDialog.dismiss();
    }

    public void show() {
        mDialog.show();
    }
}
