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

package com.android.tv.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;

import com.android.tv.common.SoftPreconditions;

public final class DialogUtils {

    /**
     * Shows a list in a Dialog.
     *
     * @param itemResIds String resource id for each item
     * @param runnables Runnable for each item
     */
    public static void showListDialog(Context context, int[] itemResIds,
            final Runnable[] runnables) {
        int size = itemResIds.length;
        SoftPreconditions.checkState(size == runnables.length);
        DialogInterface.OnClickListener onClickListener
                = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                Runnable runnable = runnables[which];
                if (runnable != null) {
                    runnable.run();
                }
                dialog.dismiss();
            }
        };
        CharSequence[] items = new CharSequence[itemResIds.length];
        Resources res = context.getResources();
        for (int i = 0; i < size; ++i) {
            items[i] = res.getString(itemResIds[i]);
        }
        new AlertDialog.Builder(context)
                .setItems(items, onClickListener)
                .create()
                .show();
    }

    private DialogUtils() { }
}
