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
package com.android.mail;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.mail.analytics.Analytics;
import com.android.mail.photo.ContactFetcher;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.NotificationUtils;
import com.android.mail.utils.StorageLowState;
import com.android.mail.utils.Utils;

/**
 * A service to handle various intents asynchronously.
 */
public class MailIntentService extends IntentService {
    private static final String LOG_TAG = LogTag.getLogTag();

    public static final String ACTION_RESEND_NOTIFICATIONS =
            "com.android.mail.action.RESEND_NOTIFICATIONS";
    public static final String ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS =
            "com.android.mail.action.CLEAR_NEW_MAIL_NOTIFICATIONS";

    /**
     * After user replies an email from Wear, it marks the conversation as read and resend
     * notifications.
     */
    public static final String ACTION_RESEND_NOTIFICATIONS_WEAR =
            "com.android.mail.action.RESEND_NOTIFICATIONS_WEAR";

    public static final String ACTION_BACKUP_DATA_CHANGED =
            "com.android.mail.action.BACKUP_DATA_CHANGED";
    public static final String ACTION_SEND_SET_NEW_EMAIL_INDICATOR =
            "com.android.mail.action.SEND_SET_NEW_EMAIL_INDICATOR";

    public static final String CONVERSATION_EXTRA = "conversation";

    public MailIntentService() {
        super("MailIntentService");
    }

    protected MailIntentService(final String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        // UnifiedEmail does not handle all Intents

        LogUtils.v(LOG_TAG, "Handling intent %s", intent);

        final String action = intent.getAction();

        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            NotificationUtils.cancelAndResendNotificationsOnLocaleChange(
                    this, getContactFetcher());
        } else if (ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS.equals(action)) {
            final Account account = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
            final Folder folder = intent.getParcelableExtra(Utils.EXTRA_FOLDER);

            NotificationUtils.clearFolderNotification(this, account, folder, true /* markSeen */);
            Analytics.getInstance().sendEvent("notification_dismiss", folder.getTypeDescription(),
                    null, 0);
        } else if (ACTION_RESEND_NOTIFICATIONS.equals(action)) {
            final Uri accountUri = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT_URI);

            final Uri extraFolderUri = intent.getParcelableExtra(Utils.EXTRA_FOLDER_URI);
            final FolderUri folderUri =
                    extraFolderUri == null ? null : new FolderUri(extraFolderUri);

            NotificationUtils.resendNotifications(
                    this, false, accountUri, folderUri, getContactFetcher());
        } else if (ACTION_RESEND_NOTIFICATIONS_WEAR.equals(action)) {
            final Account account = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
            final Folder folder = intent.getParcelableExtra(Utils.EXTRA_FOLDER);
            final Uri conversationUri = intent.getParcelableExtra(Utils.EXTRA_CONVERSATION);

            // Mark the conversation as read and refresh the notifications.  This happens
            // when user replies to a conversation remotely from a Wear device.
            NotificationUtils.markConversationAsReadAndSeen(this, conversationUri);
            NotificationUtils.resendNotifications(this, false, account.uri,
                    folder.folderUri, getContactFetcher());
        } else if (ACTION_SEND_SET_NEW_EMAIL_INDICATOR.equals(action)) {
            final int unreadCount = intent.getIntExtra(NotificationUtils.EXTRA_UNREAD_COUNT, 0);
            final int unseenCount = intent.getIntExtra(NotificationUtils.EXTRA_UNSEEN_COUNT, 0);
            final Account account = intent.getParcelableExtra(Utils.EXTRA_ACCOUNT);
            final Folder folder = intent.getParcelableExtra(Utils.EXTRA_FOLDER);
            final boolean getAttention =
                    intent.getBooleanExtra(NotificationUtils.EXTRA_GET_ATTENTION, false);

            NotificationUtils.setNewEmailIndicator(this, unreadCount, unseenCount,
                    account, folder, getAttention, getContactFetcher());
        } else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action)) {
            // The storage_low state is recorded centrally even though
            // no handler might be present to change application state
            // based on state changes.
            StorageLowState.setIsStorageLow(true);
        } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
            StorageLowState.setIsStorageLow(false);
        }
    }

    public static void broadcastBackupDataChanged(final Context context) {
        final Intent intent = new Intent(ACTION_BACKUP_DATA_CHANGED);
        intent.setPackage(context.getPackageName());
        context.startService(intent);
    }

    /**
     * Derived classes should override this method if they wish to provide their own contact loading
     * behavior separate from the ContactProvider-based default. The default behavior of this method
     * returns null.
     */
    public ContactFetcher getContactFetcher() {
        return null;
    }
}
