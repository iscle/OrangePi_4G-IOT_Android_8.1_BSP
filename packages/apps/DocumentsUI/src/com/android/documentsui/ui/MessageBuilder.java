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
package com.android.documentsui.ui;

import android.annotation.PluralsRes;
import android.content.Context;
import android.text.BidiFormatter;
import android.net.Uri;
import android.text.Html;

import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.OperationDialogFragment.DialogType;

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_FAILURE;
import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED;

import java.util.List;

public class MessageBuilder {

    private Context mContext;

    public MessageBuilder(Context context) {
        mContext = context;
    }

    public String generateDeleteMessage(List<DocumentInfo> docs) {
        String message;
        int dirsCount = 0;

        for (DocumentInfo doc : docs) {
            if (doc.isDirectory()) {
                ++dirsCount;
            }
        }

        if (docs.size() == 1) {
            // Deleteing 1 file xor 1 folder in cwd

            // Address b/28772371, where including user strings in message can result in
            // broken bidirectional support.
            String displayName = BidiFormatter.getInstance().unicodeWrap(docs.get(0).displayName);
            message = dirsCount == 0
                    ? mContext.getString(R.string.delete_filename_confirmation_message,
                            displayName)
                    : mContext.getString(R.string.delete_foldername_confirmation_message,
                            displayName);
        } else if (dirsCount == 0) {
            // Deleting only files in cwd
            message = Shared.getQuantityString(mContext,
                    R.plurals.delete_files_confirmation_message, docs.size());
        } else if (dirsCount == docs.size()) {
            // Deleting only folders in cwd
            message = Shared.getQuantityString(mContext,
                    R.plurals.delete_folders_confirmation_message, docs.size());
        } else {
            // Deleting mixed items (files and folders) in cwd
            message = Shared.getQuantityString(mContext,
                    R.plurals.delete_items_confirmation_message, docs.size());
        }
        return message;
    }

    public String generateListMessage(
            @DialogType int dialogType, @OpType int operationType, List<DocumentInfo> docs,
            List<Uri> uris) {
        int resourceId;

        switch (dialogType) {
            case DIALOG_TYPE_CONVERTED:
                resourceId = R.plurals.copy_converted_warning_content;
                break;

            case DIALOG_TYPE_FAILURE:
                switch (operationType) {
                    case FileOperationService.OPERATION_COPY:
                        resourceId = R.plurals.copy_failure_alert_content;
                        break;
                    case FileOperationService.OPERATION_COMPRESS:
                        resourceId = R.plurals.compress_failure_alert_content;
                        break;
                    case FileOperationService.OPERATION_EXTRACT:
                        resourceId = R.plurals.extract_failure_alert_content;
                        break;
                    case FileOperationService.OPERATION_DELETE:
                        resourceId = R.plurals.delete_failure_alert_content;
                        break;
                    case FileOperationService.OPERATION_MOVE:
                        resourceId = R.plurals.move_failure_alert_content;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }
                break;

            default:
                throw new UnsupportedOperationException();
        }

        final StringBuilder list = new StringBuilder("<p>");
        for (DocumentInfo documentInfo : docs) {
            list.append("&#8226; " + Html.escapeHtml(BidiFormatter.getInstance().unicodeWrap(
                    documentInfo.displayName)) + "<br>");
        }
        if (uris != null) {
            for (Uri uri : uris) {
                list.append("&#8226; " + BidiFormatter.getInstance().unicodeWrap(uri.toSafeString()) +
                        "<br>");
            }
        }
        list.append("</p>");

        final int totalItems = docs.size() + (uris != null ? uris.size() : 0);
        return mContext.getResources().getQuantityString(resourceId, totalItems, list.toString());
    }

    /**
     * Generates a formatted quantity string.
     */
    public String getQuantityString(@PluralsRes int stringId, int quantity) {
        return Shared.getQuantityString(mContext, stringId, quantity);
    }
}
