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
 * limitations under the License.
 */

package com.android.documentsui.ui;

import android.annotation.StringRes;
import android.app.Activity;
import android.support.design.widget.Snackbar;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;

public final class Snackbars {
    private Snackbars() {}

    public static final void showDocumentsClipped(Activity activity, int docCount) {
        String msg = Shared.getQuantityString(
                activity, R.plurals.clipboard_files_clipped, docCount);
        Snackbars.makeSnackbar(activity, msg, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showMove(Activity activity, int docCount) {
        CharSequence message = Shared.getQuantityString(activity, R.plurals.move_begin, docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showCopy(Activity activity, int docCount) {
        CharSequence message = Shared.getQuantityString(activity, R.plurals.copy_begin, docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showCompress(Activity activity, int docCount) {
        CharSequence message = Shared.getQuantityString(activity, R.plurals.compress_begin, docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showExtract(Activity activity, int docCount) {
        CharSequence message = Shared.getQuantityString(activity, R.plurals.extract_begin, docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showDelete(Activity activity, int docCount) {
        CharSequence message = Shared.getQuantityString(activity, R.plurals.deleting, docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showOperationRejected(Activity activity) {
        makeSnackbar(activity, R.string.file_operation_rejected, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showOperationFailed(Activity activity) {
        makeSnackbar(activity, R.string.file_operation_error, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showRenameFailed(Activity activity) {
        makeSnackbar(activity, R.string.rename_error, Snackbar.LENGTH_SHORT).show();
    }

    public static final void showInspectorError(Activity activity) {

        //Document Inspector uses a different view from other files app activities.
        final View view = activity.findViewById(R.id.fragment_container);
        Snackbar.make(view, R.string.file_inspector_load_error, Snackbar.LENGTH_INDEFINITE).show();
    }

    public static final void showCustomTextWithImage(Activity activity, String text, int imageRes) {
        Snackbar snackbar = makeSnackbar(activity, text, Snackbar.LENGTH_SHORT);
        View snackbarLayout = snackbar.getView();
        TextView textView = (TextView)snackbarLayout.findViewById(
                android.support.design.R.id.snackbar_text);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setCompoundDrawablesWithIntrinsicBounds(imageRes, 0, 0, 0);
        snackbar.show();
    }

    public static final Snackbar makeSnackbar(Activity activity, @StringRes int messageId,
            int duration) {
        return Snackbars.makeSnackbar(
                activity, activity.getResources().getText(messageId), duration);
    }

    public static final Snackbar makeSnackbar(
            Activity activity, CharSequence message, int duration) {
        final View view = activity.findViewById(R.id.coordinator_layout);
        return Snackbar.make(view, message, duration);
    }
}
