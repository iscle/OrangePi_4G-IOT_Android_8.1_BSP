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
package com.android.mail.utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.MailTo;
import android.net.Uri;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.text.BidiFormatter;
import android.support.v4.util.ArrayMap;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.TextAppearanceSpan;
import android.util.Pair;
import android.util.SparseArray;

import com.android.emailcommon.mail.Address;
import com.android.mail.EmailAddress;
import com.android.mail.MailIntentService;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.SendersView;
import com.android.mail.photo.ContactFetcher;
import com.android.mail.photomanager.LetterTileProvider;
import com.android.mail.preferences.AccountPreferences;
import com.android.mail.preferences.FolderPreferences;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.Message;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ImageCanvas.Dimensions;
import com.android.mail.utils.NotificationActionUtils.NotificationAction;
import com.google.android.mail.common.html.parser.HTML;
import com.google.android.mail.common.html.parser.HTML4;
import com.google.android.mail.common.html.parser.HtmlDocument;
import com.google.android.mail.common.html.parser.HtmlTree;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Closeables;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationUtils {
    public static final String LOG_TAG = "NotifUtils";

    public static final String EXTRA_UNREAD_COUNT = "unread-count";
    public static final String EXTRA_UNSEEN_COUNT = "unseen-count";
    public static final String EXTRA_GET_ATTENTION = "get-attention";

    /** Contains a list of <(account, label), unread conversations> */
    private static NotificationMap sActiveNotificationMap = null;

    private static final SparseArray<Bitmap> sNotificationIcons = new SparseArray<Bitmap>();
    private static WeakReference<Bitmap> sDefaultWearableBg = new WeakReference<Bitmap>(null);

    private static TextAppearanceSpan sNotificationUnreadStyleSpan;
    private static CharacterStyle sNotificationReadStyleSpan;

    /** A factory that produces a plain text converter that removes elided text. */
    private static final HtmlTree.ConverterFactory MESSAGE_CONVERTER_FACTORY =
            new HtmlTree.ConverterFactory() {
                @Override
                public HtmlTree.Converter<String> createInstance() {
                    return new MailMessagePlainTextConverter();
                }
            };

    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    // Maps summary notification to conversation notification ids.
    private static Map<NotificationKey, Set<Integer>> sConversationNotificationMap =
            new HashMap<NotificationKey, Set<Integer>>();

    /**
     * Clears all notifications in response to the user tapping "Clear" in the status bar.
     */
    public static void clearAllNotfications(Context context) {
        LogUtils.v(LOG_TAG, "Clearing all notifications.");
        final NotificationMap notificationMap = getNotificationMap(context);
        notificationMap.clear();
        notificationMap.saveNotificationMap(context);
    }

    /**
     * Returns the notification map, creating it if necessary.
     */
    private static synchronized NotificationMap getNotificationMap(Context context) {
        if (sActiveNotificationMap == null) {
            sActiveNotificationMap = new NotificationMap();

            // populate the map from the cached data
            sActiveNotificationMap.loadNotificationMap(context);
        }
        return sActiveNotificationMap;
    }

    /**
     * Class representing the existing notifications, and the number of unread and
     * unseen conversations that triggered each.
     */
    private static final class NotificationMap {

        private static final String NOTIFICATION_PART_SEPARATOR = " ";
        private static final int NUM_NOTIFICATION_PARTS= 4;
        private final ConcurrentHashMap<NotificationKey, Pair<Integer, Integer>> mMap =
            new ConcurrentHashMap<NotificationKey, Pair<Integer, Integer>>();

        /**
         * Returns the number of key values pairs in the inner map.
         */
        public int size() {
            return mMap.size();
        }

        /**
         * Returns a set of key values.
         */
        public Set<NotificationKey> keySet() {
            return mMap.keySet();
        }

        /**
         * Remove the key from the inner map and return its value.
         *
         * @param key The key {@link NotificationKey} to be removed.
         * @return The value associated with this key.
         */
        public Pair<Integer, Integer> remove(NotificationKey key) {
            return mMap.remove(key);
        }

        /**
         * Clear all key-value pairs in the map.
         */
        public void clear() {
            mMap.clear();
        }

        /**
         * Discover if a key-value pair with this key exists.
         *
         * @param key The key {@link NotificationKey} to be checked.
         * @return If a key-value pair with this key exists in the map.
         */
        public boolean containsKey(NotificationKey key) {
            return mMap.containsKey(key);
        }

        /**
         * Returns the unread count for the given NotificationKey.
         */
        public Integer getUnread(NotificationKey key) {
            final Pair<Integer, Integer> value = mMap.get(key);
            return value != null ? value.first : null;
        }

        /**
         * Returns the unread unseen count for the given NotificationKey.
         */
        public Integer getUnseen(NotificationKey key) {
            final Pair<Integer, Integer> value = mMap.get(key);
            return value != null ? value.second : null;
        }

        /**
         * Store the unread and unseen value for the given NotificationKey
         */
        public void put(NotificationKey key, int unread, int unseen) {
            final Pair<Integer, Integer> value =
                    new Pair<Integer, Integer>(Integer.valueOf(unread), Integer.valueOf(unseen));
            mMap.put(key, value);
        }

        /**
         * Populates the notification map with previously cached data.
         */
        public synchronized void loadNotificationMap(final Context context) {
            final MailPrefs mailPrefs = MailPrefs.get(context);
            final Set<String> notificationSet = mailPrefs.getActiveNotificationSet();
            if (notificationSet != null) {
                for (String notificationEntry : notificationSet) {
                    // Get the parts of the string that make the notification entry
                    final String[] notificationParts =
                            TextUtils.split(notificationEntry, NOTIFICATION_PART_SEPARATOR);
                    if (notificationParts.length == NUM_NOTIFICATION_PARTS) {
                        final Uri accountUri = Uri.parse(notificationParts[0]);
                        final Cursor accountCursor = context.getContentResolver().query(
                                accountUri, UIProvider.ACCOUNTS_PROJECTION, null, null, null);

                        if (accountCursor == null) {
                            throw new IllegalStateException("Unable to locate account for uri: " +
                                    LogUtils.contentUriToString(accountUri));
                        }

                        final Account account;
                        try {
                            if (accountCursor.moveToFirst()) {
                                account = Account.builder().buildFrom(accountCursor);
                            } else {
                                continue;
                            }
                        } finally {
                            accountCursor.close();
                        }

                        final Uri folderUri = Uri.parse(notificationParts[1]);
                        final Cursor folderCursor = context.getContentResolver().query(
                                folderUri, UIProvider.FOLDERS_PROJECTION, null, null, null);

                        if (folderCursor == null) {
                            throw new IllegalStateException("Unable to locate folder for uri: " +
                                    LogUtils.contentUriToString(folderUri));
                        }

                        final Folder folder;
                        try {
                            if (folderCursor.moveToFirst()) {
                                folder = new Folder(folderCursor);
                            } else {
                                continue;
                            }
                        } finally {
                            folderCursor.close();
                        }

                        final NotificationKey key = new NotificationKey(account, folder);
                        final Integer unreadValue = Integer.valueOf(notificationParts[2]);
                        final Integer unseenValue = Integer.valueOf(notificationParts[3]);
                        put(key, unreadValue, unseenValue);
                    }
                }
            }
        }

        /**
         * Cache the notification map.
         */
        public synchronized void saveNotificationMap(Context context) {
            final Set<String> notificationSet = Sets.newHashSet();
            final Set<NotificationKey> keys = keySet();
            for (NotificationKey key : keys) {
                final Integer unreadCount = getUnread(key);
                final Integer unseenCount = getUnseen(key);
                if (unreadCount != null && unseenCount != null) {
                    final String[] partValues = new String[] {
                            key.account.uri.toString(), key.folder.folderUri.fullUri.toString(),
                            unreadCount.toString(), unseenCount.toString()};
                    notificationSet.add(TextUtils.join(NOTIFICATION_PART_SEPARATOR, partValues));
                }
            }
            final MailPrefs mailPrefs = MailPrefs.get(context);
            mailPrefs.cacheActiveNotificationSet(notificationSet);
        }
    }

    /**
     * @return the title of this notification with each account and the number of unread and unseen
     * conversations for it. Also remove any account in the map that has 0 unread.
     */
    private static String createNotificationString(NotificationMap notifications) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        Set<NotificationKey> keysToRemove = Sets.newHashSet();
        for (NotificationKey key : notifications.keySet()) {
            Integer unread = notifications.getUnread(key);
            Integer unseen = notifications.getUnseen(key);
            if (unread == null || unread.intValue() == 0) {
                keysToRemove.add(key);
            } else {
                if (i > 0) result.append(", ");
                result.append(key.toString() + " (" + unread + ", " + unseen + ")");
                i++;
            }
        }

        for (NotificationKey key : keysToRemove) {
            notifications.remove(key);
        }

        return result.toString();
    }

    /**
     * Get all notifications for all accounts and cancel them.
     **/
    public static void cancelAllNotifications(Context context) {
        LogUtils.d(LOG_TAG, "cancelAllNotifications - cancelling all");
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancelAll();
        clearAllNotfications(context);
    }

    /**
     * Get all notifications for all accounts, cancel them, and repost.
     * This happens when locale changes.
     **/
    public static void cancelAndResendNotificationsOnLocaleChange(
            Context context, final ContactFetcher contactFetcher) {
        LogUtils.d(LOG_TAG, "cancelAndResendNotificationsOnLocaleChange");
        sBidiFormatter = BidiFormatter.getInstance();
        resendNotifications(context, true, null, null, contactFetcher);
    }

    /**
     * Get all notifications for all accounts, optionally cancel them, and repost.
     * This happens when locale changes. If you only want to resend messages from one
     * account-folder pair, pass in the account and folder that should be resent.
     * All other account-folder pairs will not have their notifications resent.
     * All notifications will be resent if account or folder is null.
     *
     * @param context Current context.
     * @param cancelExisting True, if all notifications should be canceled before resending.
     *                       False, otherwise.
     * @param accountUri The {@link Uri} of the {@link Account} of the notification
     *                   upon which an action occurred, or {@code null}.
     * @param folderUri The {@link Uri} of the {@link Folder} of the notification
     *                  upon which an action occurred, or {@code null}.
     */
    public static void resendNotifications(Context context, final boolean cancelExisting,
            final Uri accountUri, final FolderUri folderUri,
            final ContactFetcher contactFetcher) {
        LogUtils.i(LOG_TAG, "resendNotifications cancelExisting: %b, account: %s, folder: %s",
                cancelExisting,
                accountUri == null ? null : LogUtils.sanitizeName(LOG_TAG, accountUri.toString()),
                folderUri == null ? null : LogUtils.sanitizeName(LOG_TAG, folderUri.toString()));

        if (cancelExisting) {
            LogUtils.d(LOG_TAG, "resendNotifications - cancelling all");
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancelAll();
        }
        // Re-validate the notifications.
        final NotificationMap notificationMap = getNotificationMap(context);
        final Set<NotificationKey> keys = notificationMap.keySet();
        for (NotificationKey notification : keys) {
            final Folder folder = notification.folder;
            final int notificationId =
                    getNotificationId(notification.account.getAccountManagerAccount(), folder);

            // Only resend notifications if the notifications are from the same folder
            // and same account as the undo notification that was previously displayed.
            if (accountUri != null && !Objects.equal(accountUri, notification.account.uri) &&
                    folderUri != null && !Objects.equal(folderUri, folder.folderUri)) {
                LogUtils.d(LOG_TAG, "resendNotifications - not resending %s / %s"
                        + " because it doesn't match %s / %s",
                        notification.account.uri, folder.folderUri, accountUri, folderUri);
                continue;
            }

            LogUtils.d(LOG_TAG, "resendNotifications - resending %s / %s",
                    notification.account.uri, folder.folderUri);

            final NotificationAction undoableAction =
                    NotificationActionUtils.sUndoNotifications.get(notificationId);
            if (undoableAction == null) {
                validateNotifications(context, folder, notification.account, true,
                        false, notification, contactFetcher);
            } else {
                // Create an undo notification
                NotificationActionUtils.createUndoNotification(context, undoableAction);
            }
        }
    }

    /**
     * Validate the notifications for the specified account.
     */
    public static void validateAccountNotifications(Context context, Account account) {
        final String email = account.getEmailAddress();
        LogUtils.d(LOG_TAG, "validateAccountNotifications - %s", email);

        List<NotificationKey> notificationsToCancel = Lists.newArrayList();
        // Iterate through the notification map to see if there are any entries that correspond to
        // labels that are not in the sync set.
        final NotificationMap notificationMap = getNotificationMap(context);
        Set<NotificationKey> keys = notificationMap.keySet();
        final AccountPreferences accountPreferences = new AccountPreferences(context,
                account.getAccountId());
        final boolean enabled = accountPreferences.areNotificationsEnabled();
        if (!enabled) {
            // Cancel all notifications for this account
            for (NotificationKey notification : keys) {
                if (notification.account.getAccountManagerAccount().name.equals(email)) {
                    notificationsToCancel.add(notification);
                }
            }
        } else {
            // Iterate through the notification map to see if there are any entries that
            // correspond to labels that are not in the notification set.
            for (NotificationKey notification : keys) {
                if (notification.account.getAccountManagerAccount().name.equals(email)) {
                    // If notification is not enabled for this label, remember this NotificationKey
                    // to later cancel the notification, and remove the entry from the map
                    final Folder folder = notification.folder;
                    final boolean isInbox = folder.folderUri.equals(
                            notification.account.settings.defaultInbox);
                    final FolderPreferences folderPreferences = new FolderPreferences(
                            context, notification.account.getAccountId(), folder, isInbox);

                    if (!folderPreferences.areNotificationsEnabled()) {
                        notificationsToCancel.add(notification);
                    }
                }
            }
        }

        // Cancel & remove the invalid notifications.
        if (notificationsToCancel.size() > 0) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            for (NotificationKey notification : notificationsToCancel) {
                final Folder folder = notification.folder;
                final int notificationId =
                        getNotificationId(notification.account.getAccountManagerAccount(), folder);
                LogUtils.d(LOG_TAG, "validateAccountNotifications - cancelling %s / %s",
                        notification.account.getEmailAddress(), folder.persistentId);
                nm.cancel(notificationId);
                notificationMap.remove(notification);
                NotificationActionUtils.sUndoNotifications.remove(notificationId);
                NotificationActionUtils.sNotificationTimestamps.delete(notificationId);

                cancelConversationNotifications(notification, nm);
            }
            notificationMap.saveNotificationMap(context);
        }
    }

    public static void sendSetNewEmailIndicatorIntent(Context context, final int unreadCount,
            final int unseenCount, final Account account, final Folder folder,
            final boolean getAttention) {
        LogUtils.i(LOG_TAG, "sendSetNewEmailIndicator account: %s, folder: %s",
                LogUtils.sanitizeName(LOG_TAG, account.getEmailAddress()),
                LogUtils.sanitizeName(LOG_TAG, folder.name));

        final Intent intent = new Intent(MailIntentService.ACTION_SEND_SET_NEW_EMAIL_INDICATOR);
        intent.setPackage(context.getPackageName()); // Make sure we only deliver this to ourselves
        intent.putExtra(EXTRA_UNREAD_COUNT, unreadCount);
        intent.putExtra(EXTRA_UNSEEN_COUNT, unseenCount);
        intent.putExtra(Utils.EXTRA_ACCOUNT, account);
        intent.putExtra(Utils.EXTRA_FOLDER, folder);
        intent.putExtra(EXTRA_GET_ATTENTION, getAttention);
        context.startService(intent);
    }

    /**
     * Display only one notification. Should only be called from
     * {@link com.android.mail.MailIntentService}. Use {@link #sendSetNewEmailIndicatorIntent}
     * if you need to perform this action anywhere else.
     */
    public static void setNewEmailIndicator(Context context, final int unreadCount,
            final int unseenCount, final Account account, final Folder folder,
            final boolean getAttention, final ContactFetcher contactFetcher) {
        LogUtils.d(LOG_TAG, "setNewEmailIndicator unreadCount = %d, unseenCount = %d, account = %s,"
                + " folder = %s, getAttention = %b", unreadCount, unseenCount,
                account.getEmailAddress(), folder.folderUri, getAttention);

        boolean ignoreUnobtrusiveSetting = false;

        final int notificationId = getNotificationId(account.getAccountManagerAccount(), folder);

        // Update the notification map
        final NotificationMap notificationMap = getNotificationMap(context);
        final NotificationKey key = new NotificationKey(account, folder);
        if (unreadCount == 0) {
            LogUtils.d(LOG_TAG, "setNewEmailIndicator - cancelling %s / %s",
                    account.getEmailAddress(), folder.persistentId);
            notificationMap.remove(key);

            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancel(notificationId);
            cancelConversationNotifications(key, nm);
        } else {
            LogUtils.d(LOG_TAG, "setNewEmailIndicator - update count for: %s / %s " +
                    "to: unread: %d unseen %d", account.getEmailAddress(), folder.persistentId,
                    unreadCount, unseenCount);
            if (!notificationMap.containsKey(key)) {
                // This account previously didn't have any unread mail; ignore the "unobtrusive
                // notifications" setting and play sound and/or vibrate the device even if a
                // notification already exists (bug 2412348).
                LogUtils.d(LOG_TAG, "setNewEmailIndicator - ignoringUnobtrusiveSetting");
                ignoreUnobtrusiveSetting = true;
            }
            notificationMap.put(key, unreadCount, unseenCount);
        }
        notificationMap.saveNotificationMap(context);

        if (LogUtils.isLoggable(LOG_TAG, LogUtils.VERBOSE)) {
            LogUtils.v(LOG_TAG, "New email: %s mapSize: %d getAttention: %b",
                    createNotificationString(notificationMap), notificationMap.size(),
                    getAttention);
        }

        if (NotificationActionUtils.sUndoNotifications.get(notificationId) == null) {
            validateNotifications(context, folder, account, getAttention, ignoreUnobtrusiveSetting,
                    key, contactFetcher);
        }
    }

    /**
     * Validate the notifications notification.
     */
    private static void validateNotifications(Context context, final Folder folder,
            final Account account, boolean getAttention, boolean ignoreUnobtrusiveSetting,
            NotificationKey key, final ContactFetcher contactFetcher) {

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

        final NotificationMap notificationMap = getNotificationMap(context);
        if (LogUtils.isLoggable(LOG_TAG, LogUtils.VERBOSE)) {
            LogUtils.i(LOG_TAG, "Validating Notification: %s mapSize: %d "
                    + "folder: %s getAttention: %b ignoreUnobtrusive: %b",
                    createNotificationString(notificationMap),
                    notificationMap.size(), folder.name, getAttention, ignoreUnobtrusiveSetting);
        } else {
            LogUtils.i(LOG_TAG, "Validating Notification, mapSize: %d "
                    + "getAttention: %b ignoreUnobtrusive: %b", notificationMap.size(),
                    getAttention, ignoreUnobtrusiveSetting);
        }
        // The number of unread messages for this account and label.
        final Integer unread = notificationMap.getUnread(key);
        final int unreadCount = unread != null ? unread.intValue() : 0;
        final Integer unseen = notificationMap.getUnseen(key);
        int unseenCount = unseen != null ? unseen.intValue() : 0;

        Cursor cursor = null;

        try {
            final Uri.Builder uriBuilder = folder.conversationListUri.buildUpon();
            uriBuilder.appendQueryParameter(
                    UIProvider.SEEN_QUERY_PARAMETER, Boolean.FALSE.toString());
            // Do not allow this quick check to disrupt any active network-enabled conversation
            // cursor.
            uriBuilder.appendQueryParameter(
                    UIProvider.ConversationListQueryParameters.USE_NETWORK,
                    Boolean.FALSE.toString());
            cursor = context.getContentResolver().query(uriBuilder.build(),
                    UIProvider.CONVERSATION_PROJECTION, null, null, null);
            if (cursor == null) {
                // This folder doesn't exist.
                LogUtils.i(LOG_TAG,
                        "The cursor is null, so the specified folder probably does not exist");
                clearFolderNotification(context, account, folder, false);
                return;
            }
            final int cursorUnseenCount = cursor.getCount();

            // Make sure the unseen count matches the number of items in the cursor.  But, we don't
            // want to overwrite a 0 unseen count that was specified in the intent
            if (unseenCount != 0 && unseenCount != cursorUnseenCount) {
                LogUtils.i(LOG_TAG,
                        "Unseen count doesn't match cursor count.  unseen: %d cursor count: %d",
                        unseenCount, cursorUnseenCount);
                unseenCount = cursorUnseenCount;
            }

            // For the purpose of the notifications, the unseen count should be capped at the num of
            // unread conversations.
            if (unseenCount > unreadCount) {
                unseenCount = unreadCount;
            }

            final int notificationId =
                    getNotificationId(account.getAccountManagerAccount(), folder);

            NotificationKey notificationKey = new NotificationKey(account, folder);

            if (unseenCount == 0) {
                LogUtils.i(LOG_TAG, "validateNotifications - cancelling account %s / folder %s",
                        LogUtils.sanitizeName(LOG_TAG, account.getEmailAddress()),
                        LogUtils.sanitizeName(LOG_TAG, folder.persistentId));
                nm.cancel(notificationId);
                cancelConversationNotifications(notificationKey, nm);

                return;
            }

            // We now have all we need to create the notification and the pending intent
            PendingIntent clickIntent = null;

            NotificationCompat.Builder notification = new NotificationCompat.Builder(context);
            NotificationCompat.WearableExtender wearableExtender =
                    new NotificationCompat.WearableExtender();
            Map<Integer, NotificationBuilders> msgNotifications =
                    new ArrayMap<Integer, NotificationBuilders>();

            if (com.android.mail.utils.Utils.isRunningLOrLater()) {
                notification.setColor(
                        context.getResources().getColor(R.color.notification_icon_color));
            }

            if(unseenCount > 1) {
                notification.setSmallIcon(R.drawable.ic_notification_multiple_mail_24dp);
            } else {
                notification.setSmallIcon(R.drawable.ic_notification_mail_24dp);
            }
            notification.setTicker(account.getDisplayName());
            notification.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            notification.setCategory(NotificationCompat.CATEGORY_EMAIL);

            final long when;

            final long oldWhen =
                    NotificationActionUtils.sNotificationTimestamps.get(notificationId);
            if (oldWhen != 0) {
                when = oldWhen;
            } else {
                when = System.currentTimeMillis();
            }

            notification.setWhen(when);

            // The timestamp is now stored in the notification, so we can remove it from here
            NotificationActionUtils.sNotificationTimestamps.delete(notificationId);

            // Dispatch a CLEAR_NEW_MAIL_NOTIFICATIONS intent if the user taps the "X" next to a
            // notification.  Also this intent gets fired when the user taps on a notification as
            // the AutoCancel flag has been set
            final Intent cancelNotificationIntent =
                    new Intent(MailIntentService.ACTION_CLEAR_NEW_MAIL_NOTIFICATIONS);
            cancelNotificationIntent.setPackage(context.getPackageName());
            cancelNotificationIntent.setData(Utils.appendVersionQueryParameter(context,
                    folder.folderUri.fullUri));
            cancelNotificationIntent.putExtra(Utils.EXTRA_ACCOUNT, account);
            cancelNotificationIntent.putExtra(Utils.EXTRA_FOLDER, folder);

            notification.setDeleteIntent(PendingIntent.getService(
                    context, notificationId, cancelNotificationIntent, 0));

            // Ensure that the notification is cleared when the user selects it
            notification.setAutoCancel(true);

            boolean eventInfoConfigured = false;

            final boolean isInbox = folder.folderUri.equals(account.settings.defaultInbox);
            final FolderPreferences folderPreferences =
                    new FolderPreferences(context, account.getAccountId(), folder, isInbox);

            if (isInbox) {
                final AccountPreferences accountPreferences =
                        new AccountPreferences(context, account.getAccountId());
                moveNotificationSetting(accountPreferences, folderPreferences);
            }

            if (!folderPreferences.areNotificationsEnabled()) {
                LogUtils.i(LOG_TAG, "Notifications are disabled for this folder; not notifying");
                // Don't notify
                return;
            }

            if (unreadCount > 0) {
                // How can I order this properly?
                if (cursor.moveToNext()) {
                    final Intent notificationIntent;

                    // Launch directly to the conversation, if there is only 1 unseen conversation
                    final boolean launchConversationMode = (unseenCount == 1);
                    if (launchConversationMode) {
                        notificationIntent = createViewConversationIntent(context, account, folder,
                                cursor);
                    } else {
                        notificationIntent = createViewConversationIntent(context, account, folder,
                                null);
                    }

                    Analytics.getInstance().sendEvent("notification_create",
                            launchConversationMode ? "conversation" : "conversation_list",
                            folder.getTypeDescription(), unseenCount);

                    if (notificationIntent == null) {
                        LogUtils.e(LOG_TAG, "Null intent when building notification");
                        return;
                    }

                    clickIntent = createClickPendingIntent(context, notificationIntent);

                    configureLatestEventInfoFromConversation(context, account, folderPreferences,
                            notification, wearableExtender, msgNotifications, notificationId,
                            cursor, clickIntent, notificationIntent, unreadCount, unseenCount,
                            folder, when, contactFetcher);
                    eventInfoConfigured = true;
                }
            }

            final boolean vibrate = folderPreferences.isNotificationVibrateEnabled();
            final String ringtoneUri = folderPreferences.getNotificationRingtoneUri();
            final boolean notifyOnce = !folderPreferences.isEveryMessageNotificationEnabled();

            if (!ignoreUnobtrusiveSetting && notifyOnce) {
                // If the user has "unobtrusive notifications" enabled, only alert the first time
                // new mail is received in this account.  This is the default behavior.  See
                // bugs 2412348 and 2413490.
                LogUtils.d(LOG_TAG, "Setting Alert Once");
                notification.setOnlyAlertOnce(true);
            }

            LogUtils.i(LOG_TAG, "Account: %s vibrate: %s",
                    LogUtils.sanitizeName(LOG_TAG, account.getEmailAddress()),
                    Boolean.toString(folderPreferences.isNotificationVibrateEnabled()));

            int defaults = 0;

            // Check if any current conversation notifications exist previously.  Only notify if
            // one of them is new.
            boolean hasNewConversationNotification;
            Set<Integer> prevConversationNotifications =
                    sConversationNotificationMap.get(notificationKey);
            if (prevConversationNotifications != null) {
                hasNewConversationNotification = false;
                for (Integer currentNotificationId : msgNotifications.keySet()) {
                    if (!prevConversationNotifications.contains(currentNotificationId)) {
                        hasNewConversationNotification = true;
                        break;
                    }
                }
            } else {
                hasNewConversationNotification = true;
            }

            LogUtils.d(LOG_TAG, "getAttention=%s,oldWhen=%s,hasNewConversationNotification=%s",
                    getAttention, oldWhen, hasNewConversationNotification);

            /*
             * We do not want to notify if this is coming back from an Undo notification, hence the
             * oldWhen check.
             */
            if (getAttention && oldWhen == 0 && hasNewConversationNotification) {
                final AccountPreferences accountPreferences =
                        new AccountPreferences(context, account.getAccountId());
                if (accountPreferences.areNotificationsEnabled()) {
                    if (vibrate) {
                        defaults |= Notification.DEFAULT_VIBRATE;
                    }

                    notification.setSound(TextUtils.isEmpty(ringtoneUri) ? null
                            : Uri.parse(ringtoneUri));
                    LogUtils.i(LOG_TAG, "New email in %s vibrateWhen: %s, playing notification: %s",
                            LogUtils.sanitizeName(LOG_TAG, account.getEmailAddress()), vibrate,
                            ringtoneUri);
                }
            }

            // TODO(skennedy) Why do we do any of the above if we're just going to bail here?
            if (eventInfoConfigured) {
                defaults |= Notification.DEFAULT_LIGHTS;
                notification.setDefaults(defaults);

                if (oldWhen != 0) {
                    // We do not want to display the ticker again if we are re-displaying this
                    // notification (like from an Undo notification)
                    notification.setTicker(null);
                }

                notification.extend(wearableExtender);

                // create the *public* form of the *private* notification we have been assembling
                final Notification publicNotification = createPublicNotification(context, account,
                        folder, when, unseenCount, unreadCount, clickIntent);

                notification.setPublicVersion(publicNotification);

                nm.notify(notificationId, notification.build());

                if (prevConversationNotifications != null) {
                    Set<Integer> currentNotificationIds = msgNotifications.keySet();
                    for (Integer prevConversationNotificationId : prevConversationNotifications) {
                        if (!currentNotificationIds.contains(prevConversationNotificationId)) {
                            nm.cancel(prevConversationNotificationId);
                            LogUtils.d(LOG_TAG, "canceling conversation notification %s",
                                    prevConversationNotificationId);
                        }
                    }
                }

                for (Map.Entry<Integer, NotificationBuilders> entry : msgNotifications.entrySet()) {
                    NotificationBuilders builders = entry.getValue();
                    builders.notifBuilder.extend(builders.wearableNotifBuilder);
                    nm.notify(entry.getKey(), builders.notifBuilder.build());
                    LogUtils.d(LOG_TAG, "notifying conversation notification %s", entry.getKey());
                }

                Set<Integer> conversationNotificationIds = new HashSet<Integer>();
                conversationNotificationIds.addAll(msgNotifications.keySet());
                sConversationNotificationMap.put(notificationKey, conversationNotificationIds);
            } else {
                LogUtils.i(LOG_TAG, "event info not configured - not notifying");
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Build and return a redacted form of a notification using the given information. This redacted
     * form is shown above the lock screen and is devoid of sensitive information.
     *
     * @param context a context used to construct the notification
     * @param account the account for which the notification is being generated
     * @param folder the folder for which the notification is being generated
     * @param when the timestamp of the notification
     * @param unseenCount the number of unseen messages
     * @param unreadCount the number of unread messages
     * @param clickIntent the behavior to invoke if the notification is tapped (note that the user
     *                    will be prompted to unlock the device before the behavior is executed)
     * @return the redacted form of the notification to display above the lock screen
     */
    private static Notification createPublicNotification(Context context, Account account,
            Folder folder, long when, int unseenCount, int unreadCount, PendingIntent clickIntent) {
        final boolean multipleUnseen = unseenCount > 1;

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setContentTitle(createTitle(context, unseenCount))
                .setContentText(account.getDisplayName())
                .setContentIntent(clickIntent)
                .setNumber(unreadCount)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setWhen(when);

        if (com.android.mail.utils.Utils.isRunningLOrLater()) {
            builder.setColor(context.getResources().getColor(R.color.notification_icon_color));
        }

        // if this public notification summarizes multiple single notifications, mark it as the
        // summary notification and generate the same group key as the single notifications
        if (multipleUnseen) {
            builder.setGroup(createGroupKey(account, folder));
            builder.setGroupSummary(true);
            builder.setSmallIcon(R.drawable.ic_notification_multiple_mail_24dp);
        } else {
            builder.setSmallIcon(R.drawable.ic_notification_mail_24dp);
        }

        return builder.build();
    }

    /**
     * @param account the account in which the unread email resides
     * @param folder the folder in which the unread email resides
     * @return a key that groups notifications with common accounts and folders
     */
    private static String createGroupKey(Account account, Folder folder) {
        return account.uri.toString() + "/" + folder.folderUri.fullUri;
    }

    /**
     * @param context a context used to construct the title
     * @param unseenCount the number of unseen messages
     * @return e.g. "1 new message" or "2 new messages"
     */
    private static String createTitle(Context context, int unseenCount) {
        final Resources resources = context.getResources();
        return resources.getQuantityString(R.plurals.new_messages, unseenCount, unseenCount);
    }

    private static PendingIntent createClickPendingIntent(Context context,
            Intent notificationIntent) {
        // Amend the click intent with a hint that its source was a notification,
        // but remove the hint before it's used to generate notification action
        // intents. This prevents the following sequence:
        // 1. generate single notification
        // 2. user clicks reply, then completes Compose activity
        // 3. main activity launches, gets FROM_NOTIFICATION hint in intent
        notificationIntent.putExtra(Utils.EXTRA_FROM_NOTIFICATION, true);
        PendingIntent clickIntent = PendingIntent.getActivity(context, -1, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notificationIntent.removeExtra(Utils.EXTRA_FROM_NOTIFICATION);
        return clickIntent;
    }

    /**
     * @return an {@link Intent} which, if launched, will display the corresponding conversation
     */
    private static Intent createViewConversationIntent(final Context context, final Account account,
            final Folder folder, final Cursor cursor) {
        if (folder == null || account == null) {
            LogUtils.e(LOG_TAG, "createViewConversationIntent(): "
                    + "Null account or folder.  account: %s folder: %s", account, folder);
            return null;
        }

        final Intent intent;

        if (cursor == null) {
            intent = Utils.createViewFolderIntent(context, folder.folderUri.fullUri, account);
        } else {
            // A conversation cursor has been specified, so this intent is intended to be go
            // directly to the one new conversation

            // Get the Conversation object
            final Conversation conversation = new Conversation(cursor);
            intent = Utils.createViewConversationIntent(context, conversation,
                    folder.folderUri.fullUri, account);
        }

        return intent;
    }

    private static Bitmap getIcon(final Context context, final int resId) {
        final Bitmap cachedIcon = sNotificationIcons.get(resId);
        if (cachedIcon != null) {
            return cachedIcon;
        }

        final Bitmap icon = BitmapFactory.decodeResource(context.getResources(), resId);
        sNotificationIcons.put(resId, icon);

        return icon;
    }

    private static Bitmap getDefaultWearableBg(Context context) {
        Bitmap bg = sDefaultWearableBg.get();
        if (bg == null) {
            bg = BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_email);
            sDefaultWearableBg = new WeakReference<>(bg);
        }
        return bg;
    }

    private static void configureLatestEventInfoFromConversation(final Context context,
            final Account account, final FolderPreferences folderPreferences,
            final NotificationCompat.Builder notificationBuilder,
            final NotificationCompat.WearableExtender wearableExtender,
            final Map<Integer, NotificationBuilders> msgNotifications,
            final int summaryNotificationId, final Cursor conversationCursor,
            final PendingIntent clickIntent, final Intent notificationIntent,
            final int unreadCount, final int unseenCount,
            final Folder folder, final long when, final ContactFetcher contactFetcher) {
        final Resources res = context.getResources();
        final boolean multipleUnseen = unseenCount > 1;

        LogUtils.i(LOG_TAG, "Showing notification with unreadCount of %d and unseenCount of %d",
                unreadCount, unseenCount);

        String notificationTicker = null;

        // Boolean indicating that this notification is for a non-inbox label.
        final boolean isInbox = folder.folderUri.fullUri.equals(account.settings.defaultInbox);

        // Notification label name for user label notifications.
        final String notificationLabelName = isInbox ? null : folder.name;

        if (multipleUnseen) {
            // Build the string that describes the number of new messages
            final String newMessagesString = createTitle(context, unseenCount);

            // The ticker initially start as the new messages string.
            notificationTicker = newMessagesString;

            // The title of the notification is the new messages string
            notificationBuilder.setContentTitle(newMessagesString);

            // TODO(skennedy) Can we remove this check?
            if (com.android.mail.utils.Utils.isRunningJellybeanOrLater()) {
                // For a new-style notification
                final int maxNumDigestItems = context.getResources().getInteger(
                        R.integer.max_num_notification_digest_items);

                // The body of the notification is the account name, or the label name.
                notificationBuilder.setSubText(
                        isInbox ? account.getDisplayName() : notificationLabelName);

                final NotificationCompat.InboxStyle digest =
                        new NotificationCompat.InboxStyle(notificationBuilder);

                // Group by account and folder
                final String notificationGroupKey = createGroupKey(account, folder);
                // Track all senders to later tag them along with the digest notification
                final HashSet<String> senderAddressesSet = new HashSet<String>();
                notificationBuilder.setGroup(notificationGroupKey).setGroupSummary(true);

                ConfigResult firstResult = null;
                int numDigestItems = 0;
                do {
                    final Conversation conversation = new Conversation(conversationCursor);

                    if (!conversation.read) {
                        boolean multipleUnreadThread = false;
                        // TODO(cwren) extract this pattern into a helper

                        Cursor cursor = null;
                        MessageCursor messageCursor = null;
                        try {
                            final Uri.Builder uriBuilder = conversation.messageListUri.buildUpon();
                            uriBuilder.appendQueryParameter(
                                    UIProvider.LABEL_QUERY_PARAMETER, notificationLabelName);
                            cursor = context.getContentResolver().query(uriBuilder.build(),
                                    UIProvider.MESSAGE_PROJECTION, null, null, null);
                            messageCursor = new MessageCursor(cursor);

                            String from = "";
                            String fromAddress = "";
                            if (messageCursor.moveToPosition(messageCursor.getCount() - 1)) {
                                final Message message = messageCursor.getMessage();
                                fromAddress = message.getFrom();
                                if (fromAddress == null) {
                                    fromAddress = "";
                                }
                                from = getDisplayableSender(fromAddress);
                                addEmailAddressToSet(fromAddress, senderAddressesSet);
                            }
                            while (messageCursor.moveToPosition(messageCursor.getPosition() - 1)) {
                                final Message message = messageCursor.getMessage();
                                if (!message.read &&
                                        !fromAddress.contentEquals(message.getFrom())) {
                                    multipleUnreadThread = true;
                                    addEmailAddressToSet(message.getFrom(), senderAddressesSet);
                                }
                            }
                            final SpannableStringBuilder sendersBuilder;
                            if (multipleUnreadThread) {
                                final int sendersLength =
                                        res.getInteger(R.integer.swipe_senders_length);

                                sendersBuilder = getStyledSenders(context, conversationCursor,
                                        sendersLength, account);
                            } else {
                                sendersBuilder =
                                        new SpannableStringBuilder(getWrappedFromString(from));
                            }
                            final CharSequence digestLine = getSingleMessageInboxLine(context,
                                    sendersBuilder.toString(),
                                    ConversationItemView.filterTag(context, conversation.subject),
                                    conversation.getSnippet());
                            digest.addLine(digestLine);
                            numDigestItems++;

                            // Adding conversation notification for Wear.
                            NotificationCompat.Builder conversationNotif =
                                    new NotificationCompat.Builder(context);
                            conversationNotif.setCategory(NotificationCompat.CATEGORY_EMAIL);

                            conversationNotif.setSmallIcon(
                                    R.drawable.ic_notification_multiple_mail_24dp);

                            if (com.android.mail.utils.Utils.isRunningLOrLater()) {
                                conversationNotif.setColor(
                                        context.getResources()
                                                .getColor(R.color.notification_icon_color));
                            }
                            conversationNotif.setContentText(digestLine);
                            Intent conversationNotificationIntent = createViewConversationIntent(
                                    context, account, folder, conversationCursor);
                            PendingIntent conversationClickIntent = createClickPendingIntent(
                                    context, conversationNotificationIntent);
                            conversationNotif.setContentIntent(conversationClickIntent);
                            conversationNotif.setAutoCancel(true);

                            // Conversations are sorted in descending order, but notification sort
                            // key is in ascending order.  Invert the order key to get the right
                            // order.  Left pad 19 zeros because it's a long.
                            String groupSortKey = String.format("%019d",
                                    (Long.MAX_VALUE - conversation.orderKey));
                            conversationNotif.setGroup(notificationGroupKey);
                            conversationNotif.setSortKey(groupSortKey);
                            conversationNotif.setWhen(conversation.dateMs);

                            int conversationNotificationId = getNotificationId(
                                    summaryNotificationId, conversation.hashCode());

                            final NotificationCompat.WearableExtender conversationWearExtender =
                                    new NotificationCompat.WearableExtender();
                            final ConfigResult result =
                                    configureNotifForOneConversation(context, account,
                                    folderPreferences, conversationNotif, conversationWearExtender,
                                    conversationCursor, notificationIntent, folder, when, res,
                                    isInbox, notificationLabelName, conversationNotificationId,
                                    contactFetcher);
                            msgNotifications.put(conversationNotificationId,
                                    NotificationBuilders.of(conversationNotif,
                                            conversationWearExtender));

                            if (firstResult == null) {
                                firstResult = result;
                            }
                        } finally {
                            if (messageCursor != null) {
                                messageCursor.close();
                            }
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    }
                } while (numDigestItems <= maxNumDigestItems && conversationCursor.moveToNext());

                // Tag main digest notification with the senders
                tagNotificationsWithPeople(notificationBuilder, senderAddressesSet);

                if (firstResult != null && firstResult.contactIconInfo != null) {
                    wearableExtender.setBackground(firstResult.contactIconInfo.wearableBg);
                } else {
                    LogUtils.w(LOG_TAG, "First contact icon is null!");
                    wearableExtender.setBackground(getDefaultWearableBg(context));
                }
            } else {
                // The body of the notification is the account name, or the label name.
                notificationBuilder.setContentText(
                        isInbox ? account.getDisplayName() : notificationLabelName);
            }
        } else {
            // For notifications for a single new conversation, we want to get the information
            // from the conversation

            // Move the cursor to the most recent unread conversation
            seekToLatestUnreadConversation(conversationCursor);

            final ConfigResult result = configureNotifForOneConversation(context, account,
                    folderPreferences, notificationBuilder, wearableExtender, conversationCursor,
                    notificationIntent, folder, when, res, isInbox, notificationLabelName,
                    summaryNotificationId, contactFetcher);
            notificationTicker = result.notificationTicker;

            if (result.contactIconInfo != null) {
                wearableExtender.setBackground(result.contactIconInfo.wearableBg);
            } else {
                wearableExtender.setBackground(getDefaultWearableBg(context));
            }
        }

        // Build the notification ticker
        if (notificationLabelName != null && notificationTicker != null) {
            // This is a per label notification, format the ticker with that information
            notificationTicker = res.getString(R.string.label_notification_ticker,
                    notificationLabelName, notificationTicker);
        }

        if (notificationTicker != null) {
            // If we didn't generate a notification ticker, it will default to account name
            notificationBuilder.setTicker(notificationTicker);
        }

        // Set the number in the notification
        if (unreadCount > 1) {
            notificationBuilder.setNumber(unreadCount);
        }

        notificationBuilder.setContentIntent(clickIntent);
    }

    /**
     * Configure the notification for one conversation.  When there are multiple conversations,
     * this method is used to configure bundled notification for Android Wear.
     */
    private static ConfigResult configureNotifForOneConversation(Context context,
            Account account, FolderPreferences folderPreferences,
            NotificationCompat.Builder notificationBuilder,
            NotificationCompat.WearableExtender wearExtender, Cursor conversationCursor,
            Intent notificationIntent, Folder folder, long when, Resources res,
            boolean isInbox, String notificationLabelName, int notificationId,
            final ContactFetcher contactFetcher) {

        final ConfigResult result = new ConfigResult();

        final Conversation conversation = new Conversation(conversationCursor);

        // Set of all unique senders for unseen messages
        final HashSet<String> senderAddressesSet = new HashSet<String>();
        Cursor cursor = null;
        MessageCursor messageCursor = null;
        boolean multipleUnseenThread = false;
        String from = null;
        try {
            final Uri uri = conversation.messageListUri.buildUpon().appendQueryParameter(
                    UIProvider.LABEL_QUERY_PARAMETER, folder.persistentId).build();
            cursor = context.getContentResolver().query(uri, UIProvider.MESSAGE_PROJECTION,
                    null, null, null);
            messageCursor = new MessageCursor(cursor);
            // Use the information from the last sender in the conversation that triggered
            // this notification.

            String fromAddress = "";
            if (messageCursor.moveToPosition(messageCursor.getCount() - 1)) {
                final Message message = messageCursor.getMessage();
                fromAddress = message.getFrom();
                if (fromAddress == null) {
                    // No sender. Go back to default value.
                    LogUtils.e(LOG_TAG, "No sender found for message: %d", message.getId());
                    fromAddress = "";
                }
                from = getDisplayableSender(fromAddress);
                result.contactIconInfo = getContactIcon(
                        context, account.getAccountManagerAccount().name, from,
                        getSenderAddress(fromAddress), folder, contactFetcher);
                addEmailAddressToSet(fromAddress, senderAddressesSet);
                notificationBuilder.setLargeIcon(result.contactIconInfo.icon);
            }

            // Assume that the last message in this conversation is unread
            int firstUnseenMessagePos = messageCursor.getPosition();
            while (messageCursor.moveToPosition(messageCursor.getPosition() - 1)) {
                final Message message = messageCursor.getMessage();
                final boolean unseen = !message.seen;
                if (unseen) {
                    firstUnseenMessagePos = messageCursor.getPosition();
                    addEmailAddressToSet(message.getFrom(), senderAddressesSet);
                    if (!multipleUnseenThread
                            && !fromAddress.contentEquals(message.getFrom())) {
                        multipleUnseenThread = true;
                    }
                }
            }

            final String subject = ConversationItemView.filterTag(context, conversation.subject);

            // TODO(skennedy) Can we remove this check?
            if (Utils.isRunningJellybeanOrLater()) {
                // For a new-style notification

                if (multipleUnseenThread) {
                    // The title of a single conversation is the list of senders.
                    int sendersLength = res.getInteger(R.integer.swipe_senders_length);

                    final SpannableStringBuilder sendersBuilder = getStyledSenders(
                            context, conversationCursor, sendersLength, account);

                    notificationBuilder.setContentTitle(sendersBuilder);
                    // For a single new conversation, the ticker is based on the sender's name.
                    result.notificationTicker = sendersBuilder.toString();
                } else {
                    from = getWrappedFromString(from);
                    // The title of a single message the sender.
                    notificationBuilder.setContentTitle(from);
                    // For a single new conversation, the ticker is based on the sender's name.
                    result.notificationTicker = from;
                }

                // The notification content will be the subject of the conversation.
                notificationBuilder.setContentText(getSingleMessageLittleText(context, subject));

                // The notification subtext will be the subject of the conversation for inbox
                // notifications, or will based on the the label name for user label
                // notifications.
                notificationBuilder.setSubText(isInbox ?
                        account.getDisplayName() : notificationLabelName);

                final NotificationCompat.BigTextStyle bigText =
                        new NotificationCompat.BigTextStyle(notificationBuilder);

                // Seek the message cursor to the first unread message
                final Message message;
                if (messageCursor.moveToPosition(firstUnseenMessagePos)) {
                    message = messageCursor.getMessage();
                    bigText.bigText(getSingleMessageBigText(context, subject, message));
                } else {
                    LogUtils.e(LOG_TAG, "Failed to load message");
                    message = null;
                }

                if (message != null) {
                    final Set<String> notificationActions =
                            folderPreferences.getNotificationActions(account);

                    NotificationActionUtils.addNotificationActions(context, notificationIntent,
                            notificationBuilder, wearExtender, account, conversation, message,
                            folder, notificationId, when, notificationActions);
                }
            } else {
                // For an old-style notification

                // The title of a single conversation notification is built from both the sender
                // and subject of the new message.
                notificationBuilder.setContentTitle(
                        getSingleMessageNotificationTitle(context, from, subject));

                // The notification content will be the subject of the conversation for inbox
                // notifications, or will based on the the label name for user label
                // notifications.
                notificationBuilder.setContentText(
                        isInbox ? account.getDisplayName() : notificationLabelName);

                // For a single new conversation, the ticker is based on the sender's name.
                result.notificationTicker = from;
            }

            tagNotificationsWithPeople(notificationBuilder, senderAddressesSet);
        } finally {
            if (messageCursor != null) {
                messageCursor.close();
            }
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    /**
     * Iterates through all senders and adds their respective Uris to the notifications. Each Uri
     * string consists of the prefix "mailto:" followed by the sender address.
     * @param notificationBuilder
     * @param senderAddressesSet List of unique senders to be tagged with the conversation
     */
    private static void tagNotificationsWithPeople(NotificationCompat.Builder notificationBuilder,
            HashSet<String> senderAddressesSet) {
        for (final String sender : senderAddressesSet) {
            if (TextUtils.isEmpty(sender)) {
                continue;
            }
            // Tag a notification with a person using "mailto:<sender address>"
            notificationBuilder.addPerson(MailTo.MAILTO_SCHEME.concat(sender));
        }
    }

    private static String getWrappedFromString(String from) {
        if (from == null) {
            LogUtils.e(LOG_TAG, "null from string in getWrappedFromString");
            from = "";
        }
        from = sBidiFormatter.unicodeWrap(from);
        return from;
    }

    private static SpannableStringBuilder getStyledSenders(final Context context,
            final Cursor conversationCursor, final int maxLength, final Account account) {
        final Conversation conversation = new Conversation(conversationCursor);
        final com.android.mail.providers.ConversationInfo conversationInfo =
                conversation.conversationInfo;
        final ArrayList<SpannableString> senders = new ArrayList<>();
        if (sNotificationUnreadStyleSpan == null) {
            sNotificationUnreadStyleSpan = new TextAppearanceSpan(
                    context, R.style.NotificationSendersUnreadTextAppearance);
            sNotificationReadStyleSpan =
                    new TextAppearanceSpan(context, R.style.NotificationSendersReadTextAppearance);
        }
        SendersView.format(context, conversationInfo, "", maxLength, senders, null, null, account,
                sNotificationUnreadStyleSpan, sNotificationReadStyleSpan,
                false /* showToHeader */, false /* resourceCachingRequired */);

        return ellipsizeStyledSenders(context, senders);
    }

    private static String sSendersSplitToken = null;
    private static String sElidedPaddingToken = null;

    private static SpannableStringBuilder ellipsizeStyledSenders(final Context context,
            ArrayList<SpannableString> styledSenders) {
        if (sSendersSplitToken == null) {
            sSendersSplitToken = context.getString(R.string.senders_split_token);
            sElidedPaddingToken = context.getString(R.string.elided_padding_token);
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        SpannableString prevSender = null;
        for (SpannableString sender : styledSenders) {
            if (sender == null) {
                LogUtils.e(LOG_TAG, "null sender iterating over styledSenders");
                continue;
            }
            CharacterStyle[] spans = sender.getSpans(0, sender.length(), CharacterStyle.class);
            if (SendersView.sElidedString.equals(sender.toString())) {
                prevSender = sender;
                sender = copyStyles(spans, sElidedPaddingToken + sender + sElidedPaddingToken);
            } else if (builder.length() > 0
                    && (prevSender == null || !SendersView.sElidedString.equals(prevSender
                            .toString()))) {
                prevSender = sender;
                sender = copyStyles(spans, sSendersSplitToken + sender);
            } else {
                prevSender = sender;
            }
            builder.append(sender);
        }
        return builder;
    }

    private static SpannableString copyStyles(CharacterStyle[] spans, CharSequence newText) {
        SpannableString s = new SpannableString(newText);
        if (spans != null && spans.length > 0) {
            s.setSpan(spans[0], 0, s.length(), 0);
        }
        return s;
    }

    /**
     * Seeks the cursor to the position of the most recent unread conversation. If no unread
     * conversation is found, the position of the cursor will be restored, and false will be
     * returned.
     */
    private static boolean seekToLatestUnreadConversation(final Cursor cursor) {
        final int initialPosition = cursor.getPosition();
        do {
            final Conversation conversation = new Conversation(cursor);
            if (!conversation.read) {
                return true;
            }
        } while (cursor.moveToNext());

        // Didn't find an unread conversation, reset the position.
        cursor.moveToPosition(initialPosition);
        return false;
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     *
     * @param context
     * @param senders Sender of the new message that triggered the notification.
     * @param subject Subject of the new message that triggered the notification
     * @param snippet Snippet of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use in
     *         {@link android.support.v4.app.NotificationCompat.BigTextStyle}
     */
    private static CharSequence getSingleMessageInboxLine(Context context,
            String senders, String subject, String snippet) {
        // TODO(cwren) finish this step toward commmon code with getSingleMessageBigText

        final String subjectSnippet = !TextUtils.isEmpty(subject) ? subject : snippet;

        final TextAppearanceSpan notificationPrimarySpan =
                new TextAppearanceSpan(context, R.style.NotificationPrimaryText);

        if (TextUtils.isEmpty(senders)) {
            // If the senders are empty, just use the subject/snippet.
            return subjectSnippet;
        } else if (TextUtils.isEmpty(subjectSnippet)) {
            // If the subject/snippet is empty, just use the senders.
            final SpannableString spannableString = new SpannableString(senders);
            spannableString.setSpan(notificationPrimarySpan, 0, senders.length(), 0);

            return spannableString;
        } else {
            final String formatString = context.getResources().getString(
                    R.string.multiple_new_message_notification_item);
            final TextAppearanceSpan notificationSecondarySpan =
                    new TextAppearanceSpan(context, R.style.NotificationSecondaryText);

            // senders is already individually unicode wrapped so it does not need to be done here
            final String instantiatedString = String.format(formatString,
                    senders,
                    sBidiFormatter.unicodeWrap(subjectSnippet));

            final SpannableString spannableString = new SpannableString(instantiatedString);

            final boolean isOrderReversed = formatString.indexOf("%2$s") <
                    formatString.indexOf("%1$s");
            final int primaryOffset =
                    (isOrderReversed ? instantiatedString.lastIndexOf(senders) :
                     instantiatedString.indexOf(senders));
            final int secondaryOffset =
                    (isOrderReversed ? instantiatedString.lastIndexOf(subjectSnippet) :
                     instantiatedString.indexOf(subjectSnippet));
            spannableString.setSpan(notificationPrimarySpan,
                    primaryOffset, primaryOffset + senders.length(), 0);
            spannableString.setSpan(notificationSecondarySpan,
                    secondaryOffset, secondaryOffset + subjectSnippet.length(), 0);
            return spannableString;
        }
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     * @param context
     * @param subject Subject of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use in
     * {@link NotificationCompat.Builder#setContentText}
     */
    private static CharSequence getSingleMessageLittleText(Context context, String subject) {
        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        final SpannableString spannableString = new SpannableString(subject);
        spannableString.setSpan(notificationSubjectSpan, 0, subject.length(), 0);

        return spannableString;
    }

    /**
     * Sets the bigtext for a notification for a single new conversation
     *
     * @param context
     * @param subject Subject of the new message that triggered the notification
     * @param message the {@link Message} to be displayed.
     * @return a {@link CharSequence} suitable for use in
     *         {@link android.support.v4.app.NotificationCompat.BigTextStyle}
     */
    private static CharSequence getSingleMessageBigText(Context context, String subject,
            final Message message) {

        final TextAppearanceSpan notificationSubjectSpan = new TextAppearanceSpan(
                context, R.style.NotificationPrimaryText);

        final String snippet = getMessageBodyWithoutElidedText(message);

        // Change multiple newlines (with potential white space between), into a single new line
        final String collapsedSnippet =
                !TextUtils.isEmpty(snippet) ? snippet.replaceAll("\\n\\s+", "\n") : "";

        if (TextUtils.isEmpty(subject)) {
            // If the subject is empty, just use the snippet.
            return snippet;
        } else if (TextUtils.isEmpty(collapsedSnippet)) {
            // If the snippet is empty, just use the subject.
            final SpannableString spannableString = new SpannableString(subject);
            spannableString.setSpan(notificationSubjectSpan, 0, subject.length(), 0);

            return spannableString;
        } else {
            final String notificationBigTextFormat = context.getResources().getString(
                    R.string.single_new_message_notification_big_text);

            // Localizers may change the order of the parameters, look at how the format
            // string is structured.
            final boolean isSubjectFirst = notificationBigTextFormat.indexOf("%2$s") >
                    notificationBigTextFormat.indexOf("%1$s");
            final String bigText =
                    String.format(notificationBigTextFormat, subject, collapsedSnippet);
            final SpannableString spannableString = new SpannableString(bigText);

            final int subjectOffset =
                    (isSubjectFirst ? bigText.indexOf(subject) : bigText.lastIndexOf(subject));
            spannableString.setSpan(notificationSubjectSpan,
                    subjectOffset, subjectOffset + subject.length(), 0);

            return spannableString;
        }
    }

    /**
     * Gets the title for a notification for a single new conversation
     * @param context
     * @param sender Sender of the new message that triggered the notification.
     * @param subject Subject of the new message that triggered the notification
     * @return a {@link CharSequence} suitable for use as a {@link Notification} title.
     */
    private static CharSequence getSingleMessageNotificationTitle(Context context,
            String sender, String subject) {

        if (TextUtils.isEmpty(subject)) {
            // If the subject is empty, just set the title to the sender's information.
            return sender;
        } else {
            final String notificationTitleFormat = context.getResources().getString(
                    R.string.single_new_message_notification_title);

            // Localizers may change the order of the parameters, look at how the format
            // string is structured.
            final boolean isSubjectLast = notificationTitleFormat.indexOf("%2$s") >
                    notificationTitleFormat.indexOf("%1$s");
            final String titleString = String.format(notificationTitleFormat, sender, subject);

            // Format the string so the subject is using the secondaryText style
            final SpannableString titleSpannable = new SpannableString(titleString);

            // Find the offset of the subject.
            final int subjectOffset =
                    isSubjectLast ? titleString.lastIndexOf(subject) : titleString.indexOf(subject);
            final TextAppearanceSpan notificationSubjectSpan =
                    new TextAppearanceSpan(context, R.style.NotificationSecondaryText);
            titleSpannable.setSpan(notificationSubjectSpan,
                    subjectOffset, subjectOffset + subject.length(), 0);
            return titleSpannable;
        }
    }

    /**
     * Clears the notifications for the specified account/folder.
     */
    public static void clearFolderNotification(Context context, Account account, Folder folder,
            final boolean markSeen) {
        LogUtils.v(LOG_TAG, "Clearing all notifications for %s/%s", account.getEmailAddress(),
                folder.name);
        final NotificationMap notificationMap = getNotificationMap(context);
        final NotificationKey key = new NotificationKey(account, folder);
        notificationMap.remove(key);
        notificationMap.saveNotificationMap(context);

        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);
        notificationManager.cancel(getNotificationId(account.getAccountManagerAccount(), folder));

        cancelConversationNotifications(key, notificationManager);

        if (markSeen) {
            markSeen(context, folder);
        }
    }

    /**
     * Use content resolver to update a conversation.  Should not be called from a main thread.
     */
    public static void markConversationAsReadAndSeen(Context context, Uri conversationUri) {
        LogUtils.v(LOG_TAG, "markConversationAsReadAndSeen=%s", conversationUri);

        final ContentValues values = new ContentValues(2);
        values.put(UIProvider.ConversationColumns.SEEN, Boolean.TRUE);
        values.put(UIProvider.ConversationColumns.READ, Boolean.TRUE);
        context.getContentResolver().update(conversationUri, values, null, null);
    }

    /**
     * Clears all notifications for the specified account.
     */
    public static void clearAccountNotifications(final Context context,
            final android.accounts.Account account) {
        LogUtils.v(LOG_TAG, "Clearing all notifications for %s", account);
        final NotificationMap notificationMap = getNotificationMap(context);

        // Find all NotificationKeys for this account
        final ImmutableList.Builder<NotificationKey> keyBuilder = ImmutableList.builder();

        for (final NotificationKey key : notificationMap.keySet()) {
            if (account.equals(key.account.getAccountManagerAccount())) {
                keyBuilder.add(key);
            }
        }

        final List<NotificationKey> notificationKeys = keyBuilder.build();

        final NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(context);

        for (final NotificationKey notificationKey : notificationKeys) {
            final Folder folder = notificationKey.folder;
            notificationManager.cancel(getNotificationId(account, folder));
            notificationMap.remove(notificationKey);

            cancelConversationNotifications(notificationKey, notificationManager);
        }

        notificationMap.saveNotificationMap(context);
    }

    private static void cancelConversationNotifications(NotificationKey key,
            NotificationManagerCompat nm) {
        final Set<Integer> conversationNotifications = sConversationNotificationMap.get(key);
        if (conversationNotifications != null) {
            for (Integer conversationNotification : conversationNotifications) {
                nm.cancel(conversationNotification);
            }
            sConversationNotificationMap.remove(key);
        }
    }

    private static ContactIconInfo getContactIcon(final Context context, String accountName,
            final String displayName, final String senderAddress, final Folder folder,
            final ContactFetcher contactFetcher) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "getContactIcon should not be called on the main thread.");
        }

        final ContactIconInfo contactIconInfo;
        if (TextUtils.isEmpty(senderAddress)) {
            contactIconInfo = new ContactIconInfo();
        } else {
            // Get the ideal size for this icon.
            final Resources res = context.getResources();
            final int idealIconHeight =
                    res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            final int idealIconWidth =
                    res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            final int idealWearableBgWidth =
                    res.getDimensionPixelSize(R.dimen.wearable_background_width);
            final int idealWearableBgHeight =
                    res.getDimensionPixelSize(R.dimen.wearable_background_height);

            if (contactFetcher != null) {
                contactIconInfo = contactFetcher.getContactPhoto(context, accountName,
                        senderAddress, idealIconWidth, idealIconHeight, idealWearableBgWidth,
                        idealWearableBgHeight);
            } else {
                contactIconInfo = getContactInfo(context, senderAddress, idealIconWidth,
                        idealIconHeight, idealWearableBgWidth, idealWearableBgHeight);
            }

            if (contactIconInfo.icon == null) {
                // Make a colorful tile!
                final Dimensions dimensions = new Dimensions(idealIconWidth, idealIconHeight,
                        Dimensions.SCALE_ONE);

                contactIconInfo.icon = new LetterTileProvider(context.getResources())
                        .getLetterTile(dimensions, displayName, senderAddress);
            }

            // Only turn the square photo/letter tile into a circle for L and later
            if (Utils.isRunningLOrLater()) {
                contactIconInfo.icon = BitmapUtil.frameBitmapInCircle(contactIconInfo.icon);
            }
        }

        if (contactIconInfo.icon == null) {
            // Use anonymous icon due to lack of sender
            contactIconInfo.icon = getIcon(context,
                    R.drawable.ic_notification_anonymous_avatar_32dp);
        }

        if (contactIconInfo.wearableBg == null) {
            contactIconInfo.wearableBg = getDefaultWearableBg(context);
        }

        return contactIconInfo;
    }

    private static ArrayList<Long> findContacts(Context context, Collection<String> addresses) {
        ArrayList<String> whereArgs = new ArrayList<String>();
        StringBuilder whereBuilder = new StringBuilder();
        String[] questionMarks = new String[addresses.size()];

        whereArgs.addAll(addresses);
        Arrays.fill(questionMarks, "?");
        whereBuilder.append(Email.DATA1 + " IN (").
                append(TextUtils.join(",", questionMarks)).
                append(")");

        ContentResolver resolver = context.getContentResolver();
        Cursor c = resolver.query(Email.CONTENT_URI,
                new String[] {Email.CONTACT_ID}, whereBuilder.toString(),
                whereArgs.toArray(new String[0]), null);

        ArrayList<Long> contactIds = new ArrayList<Long>();
        if (c == null) {
            return contactIds;
        }
        try {
            while (c.moveToNext()) {
                contactIds.add(c.getLong(0));
            }
        } finally {
            c.close();
        }
        return contactIds;
    }

    public static ContactIconInfo getContactInfo(
            final Context context, final String senderAddress,
            final int idealIconWidth, final int idealIconHeight,
            final int idealWearableBgWidth, final int idealWearableBgHeight) {
        final ContactIconInfo contactIconInfo = new ContactIconInfo();
        final List<Long> contactIds = findContacts(context, Arrays.asList(
                new String[]{senderAddress}));

        if (contactIds != null) {
            for (final long id : contactIds) {
                final Uri contactUri = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI, id);
                final InputStream inputStream =
                        ContactsContract.Contacts.openContactPhotoInputStream(
                                context.getContentResolver(), contactUri, true /*preferHighres*/);

                if (inputStream != null) {
                    try {
                        final Bitmap source = BitmapFactory.decodeStream(inputStream);
                        if (source != null) {
                            // We should scale this image to fit the intended size
                            contactIconInfo.icon = Bitmap.createScaledBitmap(source, idealIconWidth,
                                    idealIconHeight, true);

                            contactIconInfo.wearableBg = Bitmap.createScaledBitmap(source,
                                    idealWearableBgWidth, idealWearableBgHeight, true);
                        }

                        if (contactIconInfo.icon != null) {
                            break;
                        }
                    } finally {
                        Closeables.closeQuietly(inputStream);
                    }
                }
            }
        }

        return contactIconInfo;
    }

    private static String getMessageBodyWithoutElidedText(final Message message) {
        return getMessageBodyWithoutElidedText(message.getBodyAsHtml());
    }

    public static String getMessageBodyWithoutElidedText(String html) {
        if (TextUtils.isEmpty(html)) {
            return "";
        }
        // Get the html "tree" for this message body
        final HtmlTree htmlTree = com.android.mail.utils.Utils.getHtmlTree(html);
        htmlTree.setConverterFactory(MESSAGE_CONVERTER_FACTORY);

        return htmlTree.getPlainText();
    }

    public static void markSeen(final Context context, final Folder folder) {
        final Uri uri = folder.folderUri.fullUri;

        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.ConversationColumns.SEEN, 1);

        context.getContentResolver().update(uri, values, null, null);
    }

    /**
     * Returns a displayable string representing
     * the message sender. It has a preference toward showing the name,
     * but will fall back to the address if that is all that is available.
     */
    private static String getDisplayableSender(String sender) {
        final EmailAddress address = EmailAddress.getEmailAddress(sender);

        String displayableSender = address.getName();

        if (!TextUtils.isEmpty(displayableSender)) {
            return Address.decodeAddressPersonal(displayableSender);
        }

        // If that fails, default to the sender address.
        displayableSender = address.getAddress();

        // If we were unable to tokenize a name or address,
        // just use whatever was in the sender.
        if (TextUtils.isEmpty(displayableSender)) {
            displayableSender = sender;
        }
        return displayableSender;
    }

    /**
     * Returns only the address portion of a message sender.
     */
    private static String getSenderAddress(String sender) {
        final EmailAddress address = EmailAddress.getEmailAddress(sender);

        String tokenizedAddress = address.getAddress();

        // If we were unable to tokenize a name or address,
        // just use whatever was in the sender.
        if (TextUtils.isEmpty(tokenizedAddress)) {
            tokenizedAddress = sender;
        }
        return tokenizedAddress;
    }

    /**
     * Given a sender, retrieve the email address. If an email address is extracted, add it to the
     * input set, otherwise ignore it.
     * @param sender
     * @param senderAddressesSet
     */
    private static void addEmailAddressToSet(String sender, HashSet<String> senderAddressesSet) {
        // Only continue if we have a non-empty, non-null sender
        if (!TextUtils.isEmpty(sender)) {
            final EmailAddress address = EmailAddress.getEmailAddress(sender);
            final String senderEmailAddress = address.getAddress();

            // Add to set only if we have a non-empty email address
            if (!TextUtils.isEmpty(senderEmailAddress)) {
                senderAddressesSet.add(senderEmailAddress);
            } else {
                LogUtils.i(LOG_TAG, "Unable to grab email from \"%s\" for notification tagging",
                        LogUtils.sanitizeName(LOG_TAG, sender));
            }
        }
    }

    public static int getNotificationId(final android.accounts.Account account,
            final Folder folder) {
        return 1 ^ account.hashCode() ^ folder.hashCode();
    }

    private static int getNotificationId(int summaryNotificationId, int conversationHashCode) {
        return summaryNotificationId ^ conversationHashCode;
    }

    private static class NotificationKey {
        public final Account account;
        public final Folder folder;

        public NotificationKey(Account account, Folder folder) {
            this.account = account;
            this.folder = folder;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof NotificationKey)) {
                return false;
            }
            NotificationKey key = (NotificationKey) other;
            return account.getAccountManagerAccount().equals(key.account.getAccountManagerAccount())
                    && folder.equals(key.folder);
        }

        @Override
        public String toString() {
            return account.getDisplayName() + " " + folder.name;
        }

        @Override
        public int hashCode() {
            final int accountHashCode = account.getAccountManagerAccount().hashCode();
            final int folderHashCode = folder.hashCode();
            return accountHashCode ^ folderHashCode;
        }
    }

    /**
     * Contains the logic for converting the contents of one HtmlTree into
     * plaintext.
     */
    public static class MailMessagePlainTextConverter extends HtmlTree.DefaultPlainTextConverter {
        // Strings for parsing html message bodies
        private static final String ELIDED_TEXT_ELEMENT_NAME = "div";
        private static final String ELIDED_TEXT_ELEMENT_ATTRIBUTE_NAME = "class";
        private static final String ELIDED_TEXT_ELEMENT_ATTRIBUTE_CLASS_VALUE = "elided-text";

        private static final HTML.Attribute ELIDED_TEXT_ATTRIBUTE =
                new HTML.Attribute(ELIDED_TEXT_ELEMENT_ATTRIBUTE_NAME, HTML.Attribute.NO_TYPE);

        private static final HtmlDocument.Node ELIDED_TEXT_REPLACEMENT_NODE =
                HtmlDocument.createSelfTerminatingTag(HTML4.BR_ELEMENT, null, null, null);

        private int mEndNodeElidedTextBlock = -1;

        @Override
        public void addNode(HtmlDocument.Node n, int nodeNum, int endNum) {
            // If we are in the middle of an elided text block, don't add this node
            if (nodeNum < mEndNodeElidedTextBlock) {
                return;
            } else if (nodeNum == mEndNodeElidedTextBlock) {
                super.addNode(ELIDED_TEXT_REPLACEMENT_NODE, nodeNum, endNum);
                return;
            }

            // If this tag starts another elided text block, we want to remember the end
            if (n instanceof HtmlDocument.Tag) {
                boolean foundElidedTextTag = false;
                final HtmlDocument.Tag htmlTag = (HtmlDocument.Tag)n;
                final HTML.Element htmlElement = htmlTag.getElement();
                if (ELIDED_TEXT_ELEMENT_NAME.equals(htmlElement.getName())) {
                    // Make sure that the class is what is expected
                    final List<HtmlDocument.TagAttribute> attributes =
                            htmlTag.getAttributes(ELIDED_TEXT_ATTRIBUTE);
                    for (HtmlDocument.TagAttribute attribute : attributes) {
                        if (ELIDED_TEXT_ELEMENT_ATTRIBUTE_CLASS_VALUE.equals(
                                attribute.getValue())) {
                            // Found an "elided-text" div.  Remember information about this tag
                            mEndNodeElidedTextBlock = endNum;
                            foundElidedTextTag = true;
                            break;
                        }
                    }
                }

                if (foundElidedTextTag) {
                    return;
                }
            }

            super.addNode(n, nodeNum, endNum);
        }
    }

    /**
     * During account setup in Email, we may not have an inbox yet, so the notification setting had
     * to be stored in {@link AccountPreferences}. If it is still there, we need to move it to the
     * {@link FolderPreferences} now.
     */
    public static void moveNotificationSetting(final AccountPreferences accountPreferences,
            final FolderPreferences folderPreferences) {
        if (accountPreferences.isDefaultInboxNotificationsEnabledSet()) {
            // If this setting has been changed some other way, don't overwrite it
            if (!folderPreferences.isNotificationsEnabledSet()) {
                final boolean notificationsEnabled =
                        accountPreferences.getDefaultInboxNotificationsEnabled();

                folderPreferences.setNotificationsEnabled(notificationsEnabled);
            }

            accountPreferences.clearDefaultInboxNotificationsEnabled();
        }
    }

    private static class NotificationBuilders {
        public final NotificationCompat.Builder notifBuilder;
        public final NotificationCompat.WearableExtender wearableNotifBuilder;

        private NotificationBuilders(NotificationCompat.Builder notifBuilder,
                NotificationCompat.WearableExtender wearableNotifBuilder) {
            this.notifBuilder = notifBuilder;
            this.wearableNotifBuilder = wearableNotifBuilder;
        }

        public static NotificationBuilders of(NotificationCompat.Builder notifBuilder,
                NotificationCompat.WearableExtender wearableNotifBuilder) {
            return new NotificationBuilders(notifBuilder, wearableNotifBuilder);
        }
    }

    private static class ConfigResult {
        public String notificationTicker;
        public ContactIconInfo contactIconInfo;
    }

    public static class ContactIconInfo {
        public Bitmap icon;
        public Bitmap wearableBg;
    }
}
