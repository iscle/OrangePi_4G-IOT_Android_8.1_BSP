/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.mail.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.RemoteViews;

import com.android.mail.R;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.ui.FolderDisplayer;
import com.android.mail.utils.FolderUri;

public class WidgetConversationListItemViewBuilder {
    // Static colors
    private static int SUBJECT_TEXT_COLOR_READ;
    private static int SUBJECT_TEXT_COLOR_UNREAD;
    private static int SNIPPET_TEXT_COLOR;
    private static int DATE_TEXT_COLOR_READ;
    private static int DATE_TEXT_COLOR_UNREAD;

    // Static bitmap
    private static Bitmap ATTACHMENT;

    private WidgetFolderDisplayer mFolderDisplayer;

    /**
     * Label Displayer for Widget
     */
    protected static class WidgetFolderDisplayer extends FolderDisplayer {
        public WidgetFolderDisplayer(Context context) {
            super(context);
        }

        // Maximum number of folders we want to display
        private static final int MAX_DISPLAYED_FOLDERS_COUNT = 3;

        /*
         * Load Conversation Labels
         */
        @Override
        public void loadConversationFolders(Conversation conv, final FolderUri ignoreFolderUri,
                final int ignoreFolderType) {
            super.loadConversationFolders(conv, ignoreFolderUri, ignoreFolderType);
        }

        private static int getFolderViewId(int position) {
            switch (position) {
                case 0:
                    return R.id.widget_folder_0;
                case 1:
                    return R.id.widget_folder_1;
                case 2:
                    return R.id.widget_folder_2;
            }
            return 0;
        }

        /**
         * Display folders
         */
        public void displayFolders(RemoteViews remoteViews) {
            int displayedFolder = 0;
            for (Folder folderValues : mFoldersSortedSet) {
                int viewId = getFolderViewId(displayedFolder);
                if (viewId == 0) {
                    continue;
                }
                remoteViews.setViewVisibility(viewId, View.VISIBLE);
                int color[] = new int[]
                        {folderValues.getBackgroundColor(mFolderDrawableResources.defaultBgColor)};
                Bitmap bitmap = Bitmap.createBitmap(color, 1, 1, Bitmap.Config.RGB_565);
                remoteViews.setImageViewBitmap(viewId, bitmap);

                if (++displayedFolder == MAX_DISPLAYED_FOLDERS_COUNT) {
                    break;
                }
            }

            for (int i = displayedFolder; i < MAX_DISPLAYED_FOLDERS_COUNT; i++) {
                remoteViews.setViewVisibility(getFolderViewId(i), View.GONE);
            }
        }
    }

    /*
     * Get font sizes and bitmaps from Resources
     */
    public WidgetConversationListItemViewBuilder(Context context) {
        final Resources res = context.getResources();

        // Initialize colors
        SUBJECT_TEXT_COLOR_READ = res.getColor(R.color.subject_text_color_read);
        SUBJECT_TEXT_COLOR_UNREAD = res.getColor(R.color.subject_text_color_unread);
        SNIPPET_TEXT_COLOR = res.getColor(R.color.snippet_text_color);
        DATE_TEXT_COLOR_READ = res.getColor(R.color.date_text_color_read);
        DATE_TEXT_COLOR_UNREAD = res.getColor(R.color.date_text_color_unread);

        // Initialize Bitmap
        ATTACHMENT = BitmapFactory.decodeResource(res, R.drawable.ic_attach_file_18dp);
    }

    /*
     * Add size, color and style to a given text
     */
    private static SpannableStringBuilder addStyle(CharSequence text, int size, int color) {
        final SpannableStringBuilder builder = new SpannableStringBuilder(text);
        builder.setSpan(
                new AbsoluteSizeSpan(size), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (color != 0) {
            builder.setSpan(new ForegroundColorSpan(color), 0, text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    /*
     * Return the full View
     */
    public RemoteViews getStyledView(final Context context, final CharSequence date,
            final Conversation conversation, final FolderUri folderUri, final int ignoreFolderType,
            final SpannableStringBuilder senders, String subject) {

        final boolean isUnread = !conversation.read;
        final String snippet = conversation.getSnippet();
        final boolean hasAttachments = conversation.hasAttachments;
        final Resources res = context.getResources();
        final int dateFontSize = res.getDimensionPixelSize(R.dimen.widget_date_font_size);
        final int subjectFontSize = res.getDimensionPixelSize(R.dimen.widget_subject_font_size);

        // Add style to date
        final int dateColor = isUnread ? DATE_TEXT_COLOR_UNREAD : DATE_TEXT_COLOR_READ;
        final SpannableStringBuilder dateBuilder = addStyle(date, dateFontSize, dateColor);
        if (isUnread) {
            dateBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, date.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        subject = Conversation.getSubjectForDisplay(context, null /* badgeText */, subject);
        final SpannableStringBuilder subjectBuilder = new SpannableStringBuilder(subject);
        if (isUnread) {
            subjectBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, subject.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final int subjectColor = isUnread ? SUBJECT_TEXT_COLOR_UNREAD : SUBJECT_TEXT_COLOR_READ;
        final CharacterStyle subjectStyle = new ForegroundColorSpan(subjectColor);
        subjectBuilder.setSpan(subjectStyle, 0, subjectBuilder.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        final CharSequence styledSubject = addStyle(subjectBuilder, subjectFontSize, 0);

        final SpannableStringBuilder snippetBuilder = new SpannableStringBuilder(snippet);
        snippetBuilder.setSpan(new ForegroundColorSpan(SNIPPET_TEXT_COLOR), 0,
                snippetBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        final CharSequence styledSnippet = addStyle(snippetBuilder, subjectFontSize, 0);

        // Paper clip for attachment
        Bitmap paperclipBitmap = null;
        if (hasAttachments) {
            paperclipBitmap = ATTACHMENT;
        }

        // Inflate and fill out the remote view
        final RemoteViews remoteViews = new RemoteViews(
                context.getPackageName(), R.layout.widget_conversation_list_item);
        remoteViews.setTextViewText(R.id.widget_senders, senders);
        remoteViews.setTextViewText(R.id.widget_date, dateBuilder);
        remoteViews.setTextViewText(R.id.widget_subject, styledSubject);
        remoteViews.setTextViewText(R.id.widget_snippet, styledSnippet);
        if (paperclipBitmap != null) {
            remoteViews.setViewVisibility(R.id.widget_attachment, View.VISIBLE);
            remoteViews.setImageViewBitmap(R.id.widget_attachment, paperclipBitmap);
        } else {
            remoteViews.setViewVisibility(R.id.widget_attachment, View.GONE);
        }
        if (isUnread) {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.GONE);
        } else {
            remoteViews.setViewVisibility(R.id.widget_unread_background, View.GONE);
            remoteViews.setViewVisibility(R.id.widget_read_background, View.VISIBLE);
        }
        if (context.getResources().getBoolean(R.bool.display_folder_colors_in_widget)) {
            mFolderDisplayer = new WidgetFolderDisplayer(context);
            mFolderDisplayer.loadConversationFolders(conversation, folderUri, ignoreFolderType);
            mFolderDisplayer.displayFolders(remoteViews);
        }

        return remoteViews;
    }
}
