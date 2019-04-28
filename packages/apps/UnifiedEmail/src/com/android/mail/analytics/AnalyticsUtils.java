/*******************************************************************************
 *      Copyright (C) 2013 Google Inc.
 *      Licensed to The Android Open Source Project.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *******************************************************************************/

package com.android.mail.analytics;

import com.android.mail.R;

public class AnalyticsUtils {
    // individual apps should chain this method call with their own lookup tables if they have
    // app-specific menu items
    public static String getMenuItemString(int id) {
        final String s;
        if (id == R.id.archive) {
            s = "archive";
        } else if (id == R.id.remove_folder) {
            s = "remove_folder";
        } else if (id == R.id.delete) {
            s = "delete";
        } else if (id == R.id.discard_drafts) {
            s = "discard_drafts";
        } else if (id == R.id.discard_outbox) {
            s = "discard_outbox";
        } else if (id == R.id.mark_important) {
            s = "mark important";
        } else if (id == R.id.mark_not_important) {
            s = "mark not important";
        } else if (id == R.id.mute) {
            s = "mute";
        } else if (id == R.id.report_phishing) {
            s = "report_phishing";
        } else if (id == R.id.report_spam) {
            s = "report_spam";
        } else if (id == R.id.mark_not_spam) {
            s = "mark_not_spam";
        } else if (id == R.id.compose) {
            s = "compose";
        } else if (id == R.id.refresh) {
            s = "refresh";
        } else if (id == R.id.toggle_drawer) {
            s = "toggle_drawer";
        } else if (id == R.id.settings) {
            s = "settings";
        } else if (id == R.id.help_info_menu_item) {
            s = "help";
        } else if (id == R.id.feedback_menu_item) {
            s = "feedback";
        } else if (id == R.id.move_to) {
            s = "move_to";
        } else if (id == R.id.change_folders) {
            s = "change_folders";
        } else if (id == R.id.move_to_inbox) {
            s = "move_to_inbox";
        } else if (id == R.id.empty_trash) {
            s = "empty_trash";
        } else if (id == R.id.empty_spam) {
            s = "empty_spam";
        } else if (id == android.R.id.home) {
            s = "home";
        } else if (id == R.id.inside_conversation_unread) {
            s = "inside_conversation_unread";
        } else if (id == R.id.read) {
            s = "mark_read";
        } else if (id == R.id.unread) {
            s = "mark_unread";
        } else if (id == R.id.toggle_read_unread) {
            s = "toggle_read_unread";
        } else if (id == R.id.show_original) {
            s = "show_original";
        } else if (id == R.id.add_file_attachment) {
            s = "add_file_attachment";
        } else if (id == R.id.add_photo_attachment) {
            s = "add_photo_attachment";
        } else if (id == R.id.add_cc_bcc) {
            s = "add_cc_bcc";
        } else if (id == R.id.save) {
            s = "save_draft";
        } else if (id == R.id.send) {
            s = "send_message";
        } else if (id == R.id.discard) {
            s = "compose_discard_draft";
        } else if (id == R.id.search) {
            s = "search";
        } else if (id == R.id.print_all) {
            s = "print_all";
        } else if (id == R.id.print_message) {
            s = "print_message";
        } else if (id == R.id.star) {
            s = "star";
        } else if (id == R.id.remove_star) {
            s = "unstar";
        } else if (id == R.id.reply) {
            s = "reply";
        } else if (id == R.id.reply_all) {
            s = "reply_all";
        } else if (id == R.id.forward) {
            s = "forward";
        } else if (id == R.id.edit_draft) {
            s = "edit_draft";
        } else if (id == R.id.send_date) {
            s = "expand_message_details";
        } else if (id == R.id.details_expanded_content || id == R.id.hide_details) {
            s = "collapse_message_details";
        } else if (id == R.id.upper_header) {
            s = "message_upper_header";
        } else if (id == R.id.download_again || id == R.id.menu_download_again) {
            s = "download_again";
        } else if (id == R.id.menu_save) {
            s = "photo_save";
        } else if (id == R.id.menu_save_all) {
            s = "photo_save_all";
        } else if (id == R.id.menu_share) {
            s = "photo_share";
        } else if (id == R.id.menu_share_all) {
            s = "photo_share_all";
        } else if (id == R.id.show_pictures_text) {
            s = "show_pictures";
        } else {
            s = null;
        }
        return s;
    }
}
