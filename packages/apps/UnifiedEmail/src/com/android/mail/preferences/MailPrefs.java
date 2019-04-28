/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.mail.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.StringDef;
import android.text.TextUtils;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;
import com.android.mail.widget.BaseWidgetProvider;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A high-level API to store and retrieve unified mail preferences.
 * <p>
 * This will serve as an eventual replacement for Gmail's Persistence class.
 */
public final class MailPrefs extends VersionedPrefs {

    public static final boolean SHOW_EXPERIMENTAL_PREFS = false;

    private static final String PREFS_NAME = "UnifiedEmail";

    private static MailPrefs sInstance;

    private final int mSnapHeaderDefault;

    public static final class PreferenceKeys {
        private static final String MIGRATED_VERSION = "migrated-version";

        public static final String WIDGET_ACCOUNT_PREFIX = "widget-account-";

        /** Hidden preference to indicate what version a "What's New" dialog was last shown for. */
        public static final String WHATS_NEW_LAST_SHOWN_VERSION = "whats-new-last-shown-version";

        /**
         * A boolean that, if <code>true</code>, means we should default all replies to "reply all"
         */
        public static final String DEFAULT_REPLY_ALL = "default-reply-all";
        /**
         * A boolean that, if <code>true</code>, means we should allow conversation list swiping
         */
        public static final String CONVERSATION_LIST_SWIPE = "conversation-list-swipe";

        /** A string indicating the user's removal action preference. */
        public static final String REMOVAL_ACTION = "removal-action";

        /** Hidden preference used to cache the active notification set */
        private static final String CACHED_ACTIVE_NOTIFICATION_SET =
                "cache-active-notification-set";

        /**
         * A string indicating whether the conversation photo teaser has been previously
         * shown and dismissed. This is the third version of it (thus the three at the end).
         * Previous versions: "conversation-photo-teaser-shown"
         * and "conversation-photo-teaser-shown-two".
         */
        private static final String
                CONVERSATION_PHOTO_TEASER_SHOWN = "conversation-photo-teaser-shown-three";

        public static final String DISPLAY_IMAGES = "display_images";
        public static final String DISPLAY_IMAGES_PATTERNS = "display_sender_images_patterns_set";


        public static final String SHOW_SENDER_IMAGES = "conversation-list-sender-image";

        public static final String
                LONG_PRESS_TO_SELECT_TIP_SHOWN = "long-press-to-select-tip-shown";

        /** @deprecated attachment previews have been removed; avoid future key name conflicts */
        public static final String EXPERIMENT_AP_PARALLAX_SPEED_ALTERNATIVE = "ap-parallax-speed";

        /** @deprecated attachment previews have been removed; avoid future key name conflicts */
        public static final String EXPERIMENT_AP_PARALLAX_DIRECTION_ALTERNATIVE
                = "ap-parallax-direction";

        public static final String GLOBAL_SYNC_OFF_DISMISSES = "num-of-dismisses-auto-sync-off";
        public static final String AIRPLANE_MODE_ON_DISMISSES = "num-of-dismisses-airplane-mode-on";

        public static final String AUTO_ADVANCE_MODE = "auto-advance-mode";

        public static final String CONFIRM_DELETE = "confirm-delete";
        public static final String CONFIRM_ARCHIVE = "confirm-archive";
        public static final String CONFIRM_SEND = "confirm-send";

        public static final String CONVERSATION_OVERVIEW_MODE = "conversation-overview-mode";

        public static final String ALWAYS_LAUNCH_GMAIL_FROM_EMAIL_TOMBSTONE =
                "always-launch-gmail-from-email-tombstone";

        public static final String SNAP_HEADER_MODE = "snap-header-mode";

        public static final String RECENT_ACCOUNTS = "recent-accounts";

        public static final String REQUIRED_SANITIZER_VERSION_NUMBER =
                "required-sanitizer-version-number";

        public static final String MIGRATION_STATE = "migration-state";

        /**
         * The time in epoch ms when the number of accounts in the app was reported to analytics.
         */
        public static final String ANALYTICS_NB_ACCOUNT_LATEST_REPORT =
                "analytics-send-nb_accounts-epoch";

        // State indicating that no migration has yet occurred.
        public static final int MIGRATION_STATE_NONE = 0;
        // State indicating that we have migrated imap and pop accounts, but not
        // Exchange accounts.
        public static final int MIGRATION_STATE_IMAP_POP = 1;
        // State indicating that we have migrated all accounts.
        public static final int MIGRATION_STATE_ALL = 2;

        public static final ImmutableSet<String> BACKUP_KEYS =
                new ImmutableSet.Builder<String>()
                .add(DEFAULT_REPLY_ALL)
                .add(CONVERSATION_LIST_SWIPE)
                .add(REMOVAL_ACTION)
                .add(DISPLAY_IMAGES)
                .add(DISPLAY_IMAGES_PATTERNS)
                .add(SHOW_SENDER_IMAGES)
                .add(LONG_PRESS_TO_SELECT_TIP_SHOWN)
                .add(AUTO_ADVANCE_MODE)
                .add(CONFIRM_DELETE)
                .add(CONFIRM_ARCHIVE)
                .add(CONFIRM_SEND)
                .add(CONVERSATION_OVERVIEW_MODE)
                .add(SNAP_HEADER_MODE)
                .build();
    }

    public static final class ConversationListSwipeActions {
        public static final String ARCHIVE = "archive";
        public static final String DELETE = "delete";
        public static final String DISABLED = "disabled";
    }

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({
            RemovalActions.ARCHIVE,
            RemovalActions.DELETE
    })
    public @interface RemovalActionTypes {}
    public static final class RemovalActions {
        public static final String ARCHIVE = "archive";
        public static final String DELETE = "delete";
        @Deprecated
        public static final String ARCHIVE_AND_DELETE = "archive-and-delete";
    }

    public static synchronized MailPrefs get(final Context c) {
        if (sInstance == null) {
            sInstance = new MailPrefs(c, PREFS_NAME);
        }
        return sInstance;
    }

    @VisibleForTesting
    public MailPrefs(final Context c, final String prefsName) {
        super(c, prefsName);
        mSnapHeaderDefault = c.getResources().getInteger(R.integer.prefDefault_snapHeader);
    }

    @Override
    protected void performUpgrade(final int oldVersion, final int newVersion) {
        if (oldVersion > newVersion) {
            throw new IllegalStateException(
                    "You appear to have downgraded your app. Please clear app data.");
        } else if (oldVersion == newVersion) {
            return;
        }
    }

    @Override
    protected boolean canBackup(final String key) {
        return PreferenceKeys.BACKUP_KEYS.contains(key);
    }

    @Override
    protected boolean hasMigrationCompleted() {
        return getSharedPreferences().getInt(PreferenceKeys.MIGRATED_VERSION, 0)
                >= CURRENT_VERSION_NUMBER;
    }

    @Override
    protected void setMigrationComplete() {
        getEditor().putInt(PreferenceKeys.MIGRATED_VERSION, CURRENT_VERSION_NUMBER).commit();
    }

    public boolean isWidgetConfigured(int appWidgetId) {
        return getSharedPreferences().contains(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId);
    }

    public void configureWidget(int appWidgetId, Account account, final String folderUri) {
        if (account == null) {
            LogUtils.e(LOG_TAG, "Cannot configure widget with null account");
            return;
        }
        getEditor().putString(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                createWidgetPreferenceValue(account, folderUri)).apply();
    }

    public String getWidgetConfiguration(int appWidgetId) {
        return getSharedPreferences().getString(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + appWidgetId,
                null);
    }

    private static String createWidgetPreferenceValue(Account account, String folderUri) {
        return account.uri.toString() + BaseWidgetProvider.ACCOUNT_FOLDER_PREFERENCE_SEPARATOR
                + folderUri;

    }

    public void clearWidgets(int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            getEditor().remove(PreferenceKeys.WIDGET_ACCOUNT_PREFIX + id);
        }
        getEditor().apply();
    }

    /** If <code>true</code>, we should default all replies to "reply all" rather than "reply" */
    public boolean getDefaultReplyAll() {
        return getSharedPreferences().getBoolean(PreferenceKeys.DEFAULT_REPLY_ALL, false);
    }

    public void setDefaultReplyAll(final boolean replyAll) {
        getEditor().putBoolean(PreferenceKeys.DEFAULT_REPLY_ALL, replyAll).apply();
        notifyBackupPreferenceChanged();
    }

    /**
     * Returns a string indicating the preferred removal action.
     * Should be one of the {@link RemovalActions}.
     */
    public String getRemovalAction(final boolean supportsArchive) {
        if (!supportsArchive) {
            return RemovalActions.DELETE;
        }

        final SharedPreferences sharedPreferences = getSharedPreferences();
        final String removalAction =
                sharedPreferences.getString(PreferenceKeys.REMOVAL_ACTION, null);
        if (TextUtils.equals(removalAction, RemovalActions.ARCHIVE_AND_DELETE)) {
            return RemovalActions.ARCHIVE;
        }
        return sharedPreferences.getString(PreferenceKeys.REMOVAL_ACTION,
                RemovalActions.ARCHIVE);
    }

    /**
     * Sets the removal action preference.
     * @param removalAction The preferred {@link RemovalActions}.
     */
    public void setRemovalAction(final @RemovalActionTypes String removalAction) {
        getEditor().putString(PreferenceKeys.REMOVAL_ACTION, removalAction).apply();
        notifyBackupPreferenceChanged();
    }

    /**
     * Gets a boolean indicating whether conversation list swiping is enabled.
     */
    public boolean getIsConversationListSwipeEnabled() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getBoolean(PreferenceKeys.CONVERSATION_LIST_SWIPE, true);
    }

    public void setConversationListSwipeEnabled(final boolean enabled) {
        getEditor().putBoolean(PreferenceKeys.CONVERSATION_LIST_SWIPE, enabled).apply();
        notifyBackupPreferenceChanged();
    }

    /**
     * Gets the action to take (one of the values from {@link UIProvider.Swipe}) when an item in the
     * conversation list is swiped.
     *
     * @param allowArchive <code>true</code> if Archive is an acceptable action (this will affect
     *        the default return value)
     */
    public int getConversationListSwipeActionInteger(final boolean allowArchive) {
        final boolean swipeEnabled = getIsConversationListSwipeEnabled();
        final boolean archive = !RemovalActions.DELETE.equals(getRemovalAction(allowArchive));

        if (swipeEnabled) {
            return archive ? UIProvider.Swipe.ARCHIVE : UIProvider.Swipe.DELETE;
        }

        return UIProvider.Swipe.DISABLED;
    }

    /**
     * Returns the previously cached notification set
     */
    public Set<String> getActiveNotificationSet() {
        return getSharedPreferences()
                .getStringSet(PreferenceKeys.CACHED_ACTIVE_NOTIFICATION_SET, null);
    }

    /**
     * Caches the current notification set.
     */
    public void cacheActiveNotificationSet(final Set<String> notificationSet) {
        getEditor().putStringSet(PreferenceKeys.CACHED_ACTIVE_NOTIFICATION_SET, notificationSet)
                .apply();
    }

    /**
     * Returns whether the teaser has been shown before
     */
    public boolean isConversationPhotoTeaserAlreadyShown() {
        return getSharedPreferences()
                .getBoolean(PreferenceKeys.CONVERSATION_PHOTO_TEASER_SHOWN, false);
    }

    /**
     * Notify that we have shown the teaser
     */
    public void setConversationPhotoTeaserAlreadyShown() {
        getEditor().putBoolean(PreferenceKeys.CONVERSATION_PHOTO_TEASER_SHOWN, true).apply();
    }

    /**
     * Returns whether the tip has been shown before
     */
    public boolean isLongPressToSelectTipAlreadyShown() {
        // Using an int instead of boolean here in case we need to reshow the tip (don't have
        // to use a new preference name).
        return getSharedPreferences()
                .getInt(PreferenceKeys.LONG_PRESS_TO_SELECT_TIP_SHOWN, 0) > 0;
    }

    public void setLongPressToSelectTipAlreadyShown() {
        getEditor().putInt(PreferenceKeys.LONG_PRESS_TO_SELECT_TIP_SHOWN, 1).apply();
        notifyBackupPreferenceChanged();
    }

    public void setSenderWhitelist(Set<String> addresses) {
        getEditor().putStringSet(PreferenceKeys.DISPLAY_IMAGES, addresses).apply();
        notifyBackupPreferenceChanged();
    }
    public void setSenderWhitelistPatterns(Set<String> patterns) {
        getEditor().putStringSet(PreferenceKeys.DISPLAY_IMAGES_PATTERNS, patterns).apply();
        notifyBackupPreferenceChanged();
    }

    /**
     * Returns whether or not an email address is in the whitelist of senders to show images for.
     * This method reads the entire whitelist, so if you have multiple emails to check, you should
     * probably call getSenderWhitelist() and check membership yourself.
     *
     * @param sender raw email address ("foo@bar.com")
     * @return whether we should show pictures for this sender
     */
    public boolean getDisplayImagesFromSender(String sender) {
        boolean displayImages = getSenderWhitelist().contains(sender);
        if (!displayImages) {
            final SharedPreferences sharedPreferences = getSharedPreferences();
            // Check the saved email address patterns to determine if this pattern matches
            final Set<String> defaultPatternSet = Collections.emptySet();
            final Set<String> currentPatterns = sharedPreferences.getStringSet(
                        PreferenceKeys.DISPLAY_IMAGES_PATTERNS, defaultPatternSet);
            for (String pattern : currentPatterns) {
                displayImages = Pattern.compile(pattern).matcher(sender).matches();
                if (displayImages) {
                    break;
                }
            }
        }

        return displayImages;
    }


    public void setDisplayImagesFromSender(String sender, List<Pattern> allowedPatterns) {
        if (allowedPatterns != null) {
            // Look at the list of patterns where we want to allow a particular class of
            // email address
            for (Pattern pattern : allowedPatterns) {
                if (pattern.matcher(sender).matches()) {
                    // The specified email address matches one of the social network patterns.
                    // Save the pattern itself
                    final Set<String> currentPatterns = getSenderWhitelistPatterns();
                    final String patternRegex = pattern.pattern();
                    if (!currentPatterns.contains(patternRegex)) {
                        // Copy strings to a modifiable set
                        final Set<String> updatedPatterns = Sets.newHashSet(currentPatterns);
                        updatedPatterns.add(patternRegex);
                        setSenderWhitelistPatterns(updatedPatterns);
                    }
                    return;
                }
            }
        }
        final Set<String> whitelist = getSenderWhitelist();
        if (!whitelist.contains(sender)) {
            // Storing a JSONObject is slightly more nice in that maps are guaranteed to not have
            // duplicate entries, but using a Set as intermediate representation guarantees this
            // for us anyway. Also, using maps to represent sets forces you to pick values for
            // them, and that's weird.
            final Set<String> updatedList = Sets.newHashSet(whitelist);
            updatedList.add(sender);
            setSenderWhitelist(updatedList);
        }
    }

    private Set<String> getSenderWhitelist() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        final Set<String> defaultAddressSet = Collections.emptySet();
        return sharedPreferences.getStringSet(PreferenceKeys.DISPLAY_IMAGES, defaultAddressSet);
    }


    private Set<String> getSenderWhitelistPatterns() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        final Set<String> defaultPatternSet = Collections.emptySet();
        return sharedPreferences.getStringSet(PreferenceKeys.DISPLAY_IMAGES_PATTERNS,
                defaultPatternSet);
    }

    public void clearSenderWhiteList() {
        final SharedPreferences.Editor editor = getEditor();
        editor.putStringSet(PreferenceKeys.DISPLAY_IMAGES, Collections.EMPTY_SET);
        editor.putStringSet(PreferenceKeys.DISPLAY_IMAGES_PATTERNS, Collections.EMPTY_SET);
        editor.apply();
    }

    public void setShowSenderImages(boolean enable) {
        getEditor().putBoolean(PreferenceKeys.SHOW_SENDER_IMAGES, enable).apply();
        notifyBackupPreferenceChanged();
    }

    public boolean getShowSenderImages() {
        final SharedPreferences sharedPreferences = getSharedPreferences();
        return sharedPreferences.getBoolean(PreferenceKeys.SHOW_SENDER_IMAGES, true);
    }

    public int getNumOfDismissesForAutoSyncOff() {
        return getSharedPreferences().getInt(PreferenceKeys.GLOBAL_SYNC_OFF_DISMISSES, 0);
    }

    public void resetNumOfDismissesForAutoSyncOff() {
        final int value = getSharedPreferences().getInt(
                PreferenceKeys.GLOBAL_SYNC_OFF_DISMISSES, 0);
        if (value != 0) {
            getEditor().putInt(PreferenceKeys.GLOBAL_SYNC_OFF_DISMISSES, 0).apply();
        }
    }

    public void incNumOfDismissesForAutoSyncOff() {
        final int value = getSharedPreferences().getInt(
                PreferenceKeys.GLOBAL_SYNC_OFF_DISMISSES, 0);
        getEditor().putInt(PreferenceKeys.GLOBAL_SYNC_OFF_DISMISSES, value + 1).apply();
    }

    public void setConfirmDelete(final boolean confirmDelete) {
        getEditor().putBoolean(PreferenceKeys.CONFIRM_DELETE, confirmDelete).apply();
        notifyBackupPreferenceChanged();
    }

    public boolean getConfirmDelete() {
        return getSharedPreferences().getBoolean(PreferenceKeys.CONFIRM_DELETE, false);
    }

    public void setConfirmArchive(final boolean confirmArchive) {
        getEditor().putBoolean(PreferenceKeys.CONFIRM_ARCHIVE, confirmArchive).apply();
        notifyBackupPreferenceChanged();
    }

    public boolean getConfirmArchive() {
        return getSharedPreferences().getBoolean(PreferenceKeys.CONFIRM_ARCHIVE, false);
    }

    public void setConfirmSend(final boolean confirmSend) {
        getEditor().putBoolean(PreferenceKeys.CONFIRM_SEND, confirmSend).apply();
        notifyBackupPreferenceChanged();
    }

    public boolean getConfirmSend() {
        return getSharedPreferences().getBoolean(PreferenceKeys.CONFIRM_SEND, false);
    }

    public void setAutoAdvanceMode(final int mode) {
        getEditor().putInt(PreferenceKeys.AUTO_ADVANCE_MODE, mode).apply();
        notifyBackupPreferenceChanged();
    }

    public int getAutoAdvanceMode() {
        return getSharedPreferences()
                .getInt(PreferenceKeys.AUTO_ADVANCE_MODE, UIProvider.AutoAdvance.DEFAULT);
    }

    public void setConversationOverviewMode(final boolean overviewMode) {
        getEditor().putBoolean(PreferenceKeys.CONVERSATION_OVERVIEW_MODE, overviewMode).apply();
    }

    public boolean getConversationOverviewMode() {
        return getSharedPreferences()
                .getBoolean(PreferenceKeys.CONVERSATION_OVERVIEW_MODE, true);
    }

    public boolean isConversationOverviewModeSet() {
        return getSharedPreferences().contains(PreferenceKeys.CONVERSATION_OVERVIEW_MODE);
    }

    public void setAlwaysLaunchGmailFromEmailTombstone(final boolean alwaysLaunchGmail) {
        getEditor()
                .putBoolean(PreferenceKeys.ALWAYS_LAUNCH_GMAIL_FROM_EMAIL_TOMBSTONE,
                        alwaysLaunchGmail)
                .apply();
    }

    public boolean getAlwaysLaunchGmailFromEmailTombstone() {
        return getSharedPreferences()
                .getBoolean(PreferenceKeys.ALWAYS_LAUNCH_GMAIL_FROM_EMAIL_TOMBSTONE, false);
    }

    public void setSnapHeaderMode(final int snapHeaderMode) {
        getEditor().putInt(PreferenceKeys.SNAP_HEADER_MODE, snapHeaderMode).apply();
    }

    public int getSnapHeaderMode() {
        return getSharedPreferences()
                .getInt(PreferenceKeys.SNAP_HEADER_MODE, mSnapHeaderDefault);
    }

    public int getSnapHeaderDefault() {
        return mSnapHeaderDefault;
    }

    public int getMigrationState() {
        return getSharedPreferences()
                .getInt(PreferenceKeys.MIGRATION_STATE, PreferenceKeys.MIGRATION_STATE_NONE);
    }

    public void setMigrationState(final int state) {
        getEditor().putInt(PreferenceKeys.MIGRATION_STATE, state).apply();
    }

    public Set<String> getRecentAccounts() {
        return getSharedPreferences().getStringSet(PreferenceKeys.RECENT_ACCOUNTS, null);
    }

    public void setRecentAccounts(Set<String> recentAccounts) {
        getEditor().putStringSet(PreferenceKeys.RECENT_ACCOUNTS, recentAccounts).apply();
    }

    /**
     * Returns the minimum version number of the {@link com.android.mail.utils.HtmlSanitizer} which
     * is trusted. If the version of the HtmlSanitizer does not meet or exceed this value,
     * sanitization will be deemed untrustworthy and emails will be displayed in a sandbox that does
     * not allow script execution.
     */
    public int getRequiredSanitizerVersionNumber() {
        return getSharedPreferences().getInt(PreferenceKeys.REQUIRED_SANITIZER_VERSION_NUMBER, 1);
    }

    /**
     * @param versionNumber the minimum version number of the
     *      {@link com.android.mail.utils.HtmlSanitizer} which produces trusted output
     */
    public void setRequiredSanitizerVersionNumber(int versionNumber) {
        getEditor().putInt(PreferenceKeys.REQUIRED_SANITIZER_VERSION_NUMBER, versionNumber).apply();
    }

    /**
     * Returns the latest time the number of accounts in the application was sent to Analyitcs.
     * @return the datetime in epoch milliseconds.
     */
    public long getNbAccountsLatestReport() {
        return getSharedPreferences().getLong(PreferenceKeys.ANALYTICS_NB_ACCOUNT_LATEST_REPORT, 0);
    }

    /**
     * Set the latest time the number of accounts in the application was sent to Analytics.
     */
    public void setNbAccountsLatestReport(long timeMs) {
        getEditor().putLong(
                PreferenceKeys.ANALYTICS_NB_ACCOUNT_LATEST_REPORT, timeMs);
    }
}
