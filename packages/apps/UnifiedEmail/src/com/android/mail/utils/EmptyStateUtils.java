/*
 * Copyright (C) 2014 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.utils;

import android.content.res.Resources;
import android.support.v4.text.BidiFormatter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.Folder;

/**
 * Utility class for handling logic related to empty states throughout the app.
 */
public class EmptyStateUtils {

    /**
     * Given an empty folder, set the corresponding empty state icon for that folder.
     */
    public static void bindEmptyFolderIcon(ImageView view, Folder folder) {
        if (folder == null) {
            view.setImageResource(R.drawable.ic_empty_default);
        } else if (folder.isInbox()) {
            view.setImageResource(R.drawable.ic_empty_inbox);
        } else if (folder.isSearch()) {
            view.setImageResource(R.drawable.ic_empty_search);
        } else if (folder.isSpam()) {
            view.setImageResource(R.drawable.ic_empty_spam);
        } else if (folder.isTrash()) {
            view.setImageResource(R.drawable.ic_empty_trash);
        } else {
            view.setImageResource(R.drawable.ic_empty_default);
        }
    }

    /**
     * Given an empty folder, set the corresponding text for indicating the empty state.
     */
    public static void bindEmptyFolderText(TextView view, Folder folder, Resources res,
            String searchQuery, BidiFormatter bidiFormatter) {
        if (folder == null) {
            view.setText(R.string.empty_folder);
        } else if (folder.isInbox()) {
            view.setText(R.string.empty_inbox);
        } else if (folder.isSearch()) {
            final String text = res.getString(R.string.empty_search,
                    bidiFormatter.unicodeWrap(searchQuery));
            view.setText(text);
        } else if (folder.isSpam()) {
            view.setText(R.string.empty_spam_folder);
        } else if (folder.isTrash()) {
            view.setText(R.string.empty_trash_folder);
        } else {
            view.setText(R.string.empty_folder);
        }
    }
}
