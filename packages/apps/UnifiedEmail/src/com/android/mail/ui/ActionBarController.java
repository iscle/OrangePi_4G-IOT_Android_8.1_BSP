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

package com.android.mail.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderObserver;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;

/**
 * Controller to manage the various states of the {@link android.app.ActionBar}.
 */
public class ActionBarController implements ViewMode.ModeChangeListener {

    private final Context mContext;

    protected ActionBar mActionBar;
    protected ControllableActivity mActivity;
    protected ActivityController mController;
    /**
     * The current mode of the ActionBar and Activity
     */
    private ViewMode mViewModeController;

    /**
     * The account currently being shown
     */
    private Account mAccount;
    /**
     * The folder currently being shown
     */
    private Folder mFolder;

    private MenuItem mEmptyTrashItem;
    private MenuItem mEmptySpamItem;

    /** True if the current device is a tablet, false otherwise. */
    protected final boolean mIsOnTablet;
    private Conversation mCurrentConversation;

    public static final String LOG_TAG = LogTag.getLogTag();

    private FolderObserver mFolderObserver;

    /** Updates the resolver and tells it the most recent account. */
    private final class UpdateProvider extends AsyncTask<Bundle, Void, Void> {
        final Uri mAccount;
        final ContentResolver mResolver;
        public UpdateProvider(Uri account, ContentResolver resolver) {
            mAccount = account;
            mResolver = resolver;
        }

        @Override
        protected Void doInBackground(Bundle... params) {
            mResolver.call(mAccount, UIProvider.AccountCallMethods.SET_CURRENT_ACCOUNT,
                    mAccount.toString(), params[0]);
            return null;
        }
    }

    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            updateAccount(newAccount);
        }
    };

    public ActionBarController(Context context) {
        mContext = context;
        mIsOnTablet = Utils.useTabletUI(context.getResources());
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        mEmptyTrashItem = menu.findItem(R.id.empty_trash);
        mEmptySpamItem = menu.findItem(R.id.empty_spam);

        // the menu should be displayed if the mode is known
        return getMode() != ViewMode.UNKNOWN;
    }

    public int getOptionsMenuId() {
        switch (getMode()) {
            case ViewMode.UNKNOWN:
                return R.menu.conversation_list_menu;
            case ViewMode.CONVERSATION:
                return R.menu.conversation_actions;
            case ViewMode.CONVERSATION_LIST:
                return R.menu.conversation_list_menu;
            case ViewMode.SEARCH_RESULTS_LIST:
                return R.menu.conversation_list_search_results_actions;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                return R.menu.conversation_actions;
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                return R.menu.wait_mode_actions;
        }
        LogUtils.wtf(LOG_TAG, "Menu requested for unknown view mode");
        return R.menu.conversation_list_menu;
    }

    public void initialize(ControllableActivity activity, ActivityController callback,
            ActionBar actionBar) {
        mActionBar = actionBar;
        mController = callback;
        mActivity = activity;

        mFolderObserver = new FolderObserver() {
            @Override
            public void onChanged(Folder newFolder) {
                onFolderUpdated(newFolder);
            }
        };
        // Return values are purposely discarded. Initialization happens quite early, and we don't
        // have a valid folder, or a valid list of accounts.
        mFolderObserver.initialize(mController);
        updateAccount(mAccountObserver.initialize(activity.getAccountController()));
    }

    private void updateAccount(Account account) {
        final boolean accountChanged = mAccount == null || !mAccount.uri.equals(account.uri);
        mAccount = account;
        if (mAccount != null && accountChanged) {
            final ContentResolver resolver = mActivity.getActivityContext().getContentResolver();
            final Bundle bundle = new Bundle(1);
            bundle.putParcelable(UIProvider.SetCurrentAccountColumns.ACCOUNT, account);
            final UpdateProvider updater = new UpdateProvider(mAccount.uri, resolver);
            updater.execute(bundle);
            setFolderAndAccount();
        }
    }

    /**
     * Called by the owner of the ActionBar to change the current folder.
     */
    public void setFolder(Folder folder) {
        mFolder = folder;
        setFolderAndAccount();
    }

    public void onDestroy() {
        if (mFolderObserver != null) {
            mFolderObserver.unregisterAndDestroy();
            mFolderObserver = null;
        }
        mAccountObserver.unregisterAndDestroy();
    }

    @Override
    public void onViewModeChanged(int newMode) {
        final boolean mIsTabletLandscape =
                mContext.getResources().getBoolean(R.bool.is_tablet_landscape);

        mActivity.supportInvalidateOptionsMenu();
        // Check if we are either on a phone, or in Conversation mode on tablet. For these, the
        // recent folders is enabled.
        switch (getMode()) {
            case ViewMode.UNKNOWN:
                break;
            case ViewMode.CONVERSATION_LIST:
                showNavList();
                break;
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setEmptyMode();
                break;
            case ViewMode.CONVERSATION:
                // If on tablet landscape, show current folder instead of emptying the action bar
                if (mIsTabletLandscape) {
                    mActionBar.setDisplayHomeAsUpEnabled(true);
                    showNavList();
                    break;
                }
                // Otherwise, fall through to default behavior, shared with Ads ViewMode.
            case ViewMode.AD:
                mActionBar.setDisplayHomeAsUpEnabled(true);
                setEmptyMode();
                break;
            case ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION:
                // We want the user to be able to switch accounts while waiting for an account
                // to sync.
                showNavList();
                break;
        }
    }

    protected int getMode() {
        if (mViewModeController != null) {
            return mViewModeController.getMode();
        } else {
            return ViewMode.UNKNOWN;
        }
    }

    /**
     * Helper function to ensure that the menu items that are prone to variable changes and race
     * conditions are properly set to the correct visibility
     */
    public void validateVolatileMenuOptionVisibility() {
        Utils.setMenuItemPresent(mEmptyTrashItem, mAccount != null && mFolder != null
                && mAccount.supportsCapability(AccountCapabilities.EMPTY_TRASH)
                && mFolder.isTrash() && mFolder.totalCount > 0
                && (mController.getConversationListCursor() == null
                || mController.getConversationListCursor().getCount() > 0));
        Utils.setMenuItemPresent(mEmptySpamItem, mAccount != null && mFolder != null
                && mAccount.supportsCapability(AccountCapabilities.EMPTY_SPAM)
                && mFolder.isType(FolderType.SPAM) && mFolder.totalCount > 0
                && (mController.getConversationListCursor() == null
                || mController.getConversationListCursor().getCount() > 0));
    }

    public void onPrepareOptionsMenu(Menu menu) {
        menu.setQwertyMode(true);
        // We start out with every option enabled. Based on the current view, we disable actions
        // that are possible.
        LogUtils.d(LOG_TAG, "ActionBarView.onPrepareOptionsMenu().");

        if (mController.shouldHideMenuItems()) {
            // Shortcut: hide all menu items if the drawer is shown
            final int size = menu.size();

            for (int i = 0; i < size; i++) {
                final MenuItem item = menu.getItem(i);
                item.setVisible(false);
            }
            return;
        }
        validateVolatileMenuOptionVisibility();

        switch (getMode()) {
            case ViewMode.CONVERSATION:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
                // We update the ActionBar options when we are entering conversation view because
                // waiting for the AbstractConversationViewFragment to do it causes duplicate icons
                // to show up during the time between the conversation is selected and the fragment
                // is added.
                setConversationModeOptions(menu);
                break;
            case ViewMode.CONVERSATION_LIST:
            case ViewMode.SEARCH_RESULTS_LIST:
                // The search menu item should only be visible for non-tablet devices
                Utils.setMenuItemPresent(menu, R.id.search,
                        mAccount.supportsSearch() && !mIsOnTablet);
        }

        return;
    }

    /**
     * Put the ActionBar in List navigation mode.
     */
    private void showNavList() {
        setTitleModeFlags(ActionBar.DISPLAY_SHOW_TITLE);
        setFolderAndAccount();
    }

    private void setTitle(String title) {
        if (!TextUtils.equals(title, mActionBar.getTitle())) {
            mActionBar.setTitle(title);
        }
    }

    /**
     * Set the actionbar mode to empty: no title, no subtitle, no custom view.
     */
    protected void setEmptyMode() {
        // Disable title/subtitle and the custom view by setting the bitmask to all off.
        setTitleModeFlags(0);
    }

    /**
     * Removes the back button from being shown
     */
    public void removeBackButton() {
        if (mActionBar == null) {
            return;
        }
        // Remove the back button but continue showing an icon.
        final int mask = ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME;
        mActionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME, mask);
        mActionBar.setHomeButtonEnabled(false);
    }

    public void setBackButton() {
        if (mActionBar == null) {
            return;
        }
        // Show home as up, and show an icon.
        final int mask = ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME;
        mActionBar.setDisplayOptions(mask, mask);
        mActionBar.setHomeButtonEnabled(true);
    }

    /**
     * Uses the current state to update the current folder {@link #mFolder} and the current
     * account {@link #mAccount} shown in the actionbar. Also updates the actionbar subtitle to
     * momentarily display the unread count if it has changed.
     */
    private void setFolderAndAccount() {
        // Very little can be done if the actionbar or activity is null.
        if (mActionBar == null || mActivity == null) {
            return;
        }
        if (ViewMode.isWaitingForSync(getMode())) {
            // Account is not synced: clear title and update the subtitle.
            setTitle("");
            return;
        }
        // Check if we should be changing the actionbar at all, and back off if not.
        final boolean isShowingFolder = mIsOnTablet || ViewMode.isListMode(getMode());
        if (!isShowingFolder) {
            // It isn't necessary to set the title in this case, as the title view will
            // be hidden
            return;
        }
        if (mFolder == null) {
            // Clear the action bar title.  We don't want the app name to be shown while
            // waiting for the folder query to finish
            setTitle("");
            return;
        }
        setTitle(mFolder.name);
    }


    /**
     * Notify that the folder has changed.
     */
    public void onFolderUpdated(Folder folder) {
        if (folder == null) {
            return;
        }
        /** True if we are changing folders. */
        mFolder = folder;
        setFolderAndAccount();
        // make sure that we re-validate the optional menu items
        validateVolatileMenuOptionVisibility();
    }

    /**
     * Sets the actionbar mode: Pass it an integer which contains each of these values, perhaps
     * OR'd together: {@link ActionBar#DISPLAY_SHOW_CUSTOM} and
     * {@link ActionBar#DISPLAY_SHOW_TITLE}. To disable all, pass a zero.
     * @param enabledFlags
     */
    private void setTitleModeFlags(int enabledFlags) {
        final int mask = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM;
        mActionBar.setDisplayOptions(enabledFlags, mask);
    }

    public void setCurrentConversation(Conversation conversation) {
        mCurrentConversation = conversation;
    }

    //We need to do this here instead of in the fragment
    public void setConversationModeOptions(Menu menu) {
        if (mCurrentConversation == null) {
            return;
        }
        final boolean showMarkImportant = !mCurrentConversation.isImportant();
        Utils.setMenuItemPresent(menu, R.id.mark_important, showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        Utils.setMenuItemPresent(menu, R.id.mark_not_important, !showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        final boolean isOutbox = mFolder.isType(FolderType.OUTBOX);
        final boolean showDiscardOutbox = mFolder != null && isOutbox;
        Utils.setMenuItemPresent(menu, R.id.discard_outbox, showDiscardOutbox);
        final boolean showDelete = !isOutbox && mFolder != null &&
                mFolder.supportsCapability(UIProvider.FolderCapabilities.DELETE);
        Utils.setMenuItemPresent(menu, R.id.delete, showDelete);
        // We only want to show the discard drafts menu item if we are not showing the delete menu
        // item, and the current folder is a draft folder and the account supports discarding
        // drafts for a conversation
        final boolean showDiscardDrafts = !showDelete && mFolder != null && mFolder.isDraft() &&
                mAccount.supportsCapability(AccountCapabilities.DISCARD_CONVERSATION_DRAFTS);
        Utils.setMenuItemPresent(menu, R.id.discard_drafts, showDiscardDrafts);
        final boolean archiveVisible = mAccount.supportsCapability(AccountCapabilities.ARCHIVE)
                && mFolder != null && mFolder.supportsCapability(FolderCapabilities.ARCHIVE)
                && !mFolder.isTrash();
        Utils.setMenuItemPresent(menu, R.id.archive, archiveVisible);
        Utils.setMenuItemPresent(menu, R.id.remove_folder, !archiveVisible && mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && !mFolder.isProviderFolder()
                && mAccount.supportsCapability(AccountCapabilities.ARCHIVE));
        Utils.setMenuItemPresent(menu, R.id.move_to, mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.ALLOWS_REMOVE_CONVERSATION));
        Utils.setMenuItemPresent(menu, R.id.move_to_inbox, mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.ALLOWS_MOVE_TO_INBOX));
        Utils.setMenuItemPresent(menu, R.id.change_folders, mAccount.supportsCapability(
                UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV));

        final MenuItem removeFolder = menu.findItem(R.id.remove_folder);
        if (mFolder != null && removeFolder != null) {
            removeFolder.setTitle(mActivity.getApplicationContext().getString(
                    R.string.remove_folder, mFolder.name));
        }
        Utils.setMenuItemPresent(menu, R.id.report_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM)
                        && !mCurrentConversation.spam);
        Utils.setMenuItemPresent(menu, R.id.mark_not_spam,
                mAccount.supportsCapability(AccountCapabilities.REPORT_SPAM) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.MARK_NOT_SPAM)
                        && mCurrentConversation.spam);
        Utils.setMenuItemPresent(menu, R.id.report_phishing,
                mAccount.supportsCapability(AccountCapabilities.REPORT_PHISHING) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.REPORT_PHISHING)
                        && !mCurrentConversation.phishing);
        Utils.setMenuItemPresent(menu, R.id.mute,
                mAccount.supportsCapability(AccountCapabilities.MUTE) && mFolder != null
                        && mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)
                        && !mCurrentConversation.muted);
    }

    public void setViewModeController(ViewMode viewModeController) {
        mViewModeController = viewModeController;
        mViewModeController.addListener(this);
    }

    public Context getContext() {
        return mContext;
    }
}
