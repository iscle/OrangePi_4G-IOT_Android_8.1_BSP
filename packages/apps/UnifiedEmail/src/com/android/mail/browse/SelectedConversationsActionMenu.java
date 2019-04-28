/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.mail.browse;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.ui.ControllableActivity;
import com.android.mail.ui.ConversationCheckedSet;
import com.android.mail.ui.ConversationListCallbacks;
import com.android.mail.ui.ConversationSetObserver;
import com.android.mail.ui.ConversationUpdater;
import com.android.mail.ui.DestructiveAction;
import com.android.mail.ui.FolderOperation;
import com.android.mail.ui.FolderSelectionDialog;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;

/**
 * A component that displays a custom view for an {@code ActionBar}'s {@code
 * ContextMode} specific to operating on a set of conversations.
 */
public class SelectedConversationsActionMenu implements ActionMode.Callback,
        ConversationSetObserver {

    private static final String LOG_TAG = LogTag.getLogTag();

    /**
     * The set of conversations to display the menu for.
     */
    protected final ConversationCheckedSet mCheckedSet;

    private final ControllableActivity mActivity;
    private final ConversationListCallbacks mListController;
    /**
     * Context of the activity. A dialog requires the context of an activity rather than the global
     * root context of the process. So mContext = mActivity.getApplicationContext() will fail.
     */
    private final Context mContext;

    @VisibleForTesting
    private ActionMode mActionMode;

    private boolean mActivated = false;

    /** Object that can update conversation state on our behalf. */
    private final ConversationUpdater mUpdater;

    private Account mAccount;

    private final Folder mFolder;

    private AccountObserver mAccountObserver;

    private MenuItem mDiscardOutboxMenuItem;

    public SelectedConversationsActionMenu(
            ControllableActivity activity, ConversationCheckedSet checkedSet, Folder folder) {
        mActivity = activity;
        mListController = activity.getListHandler();
        mCheckedSet = checkedSet;
        mAccountObserver = new AccountObserver() {
            @Override
            public void onChanged(Account newAccount) {
                mAccount = newAccount;
            }
        };
        mAccount = mAccountObserver.initialize(activity.getAccountController());
        mFolder = folder;
        mContext = mActivity.getActivityContext();
        mUpdater = activity.getConversationUpdater();
    }

    public boolean onActionItemClicked(MenuItem item) {
        return onActionItemClicked(mActionMode, item);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        boolean handled = true;
        // If the user taps a new menu item, commit any existing destructive actions.
        mListController.commitDestructiveActions(true);
        final int itemId = item.getItemId();

        Analytics.getInstance().sendMenuItemEvent(Analytics.EVENT_CATEGORY_MENU_ITEM, itemId,
                "cab_mode", 0);

        UndoCallback undoCallback = null;   // not applicable here (yet)
        if (itemId == R.id.delete) {
            LogUtils.i(LOG_TAG, "Delete selected from CAB menu");
            performDestructiveAction(R.id.delete, undoCallback);
        } else if (itemId == R.id.discard_drafts) {
            LogUtils.i(LOG_TAG, "Discard drafts selected from CAB menu");
            performDestructiveAction(R.id.discard_drafts, undoCallback);
        } else if (itemId == R.id.discard_outbox) {
            LogUtils.i(LOG_TAG, "Discard outbox selected from CAB menu");
            performDestructiveAction(R.id.discard_outbox, undoCallback);
        } else if (itemId == R.id.archive) {
            LogUtils.i(LOG_TAG, "Archive selected from CAB menu");
            performDestructiveAction(R.id.archive, undoCallback);
        } else if (itemId == R.id.remove_folder) {
            destroy(R.id.remove_folder, mCheckedSet.values(),
                    mUpdater.getDeferredRemoveFolder(mCheckedSet.values(), mFolder, true,
                            true, true, undoCallback));
        } else if (itemId == R.id.mute) {
            destroy(R.id.mute, mCheckedSet.values(), mUpdater.getBatchAction(R.id.mute,
                    undoCallback));
        } else if (itemId == R.id.report_spam) {
            destroy(R.id.report_spam, mCheckedSet.values(),
                    mUpdater.getBatchAction(R.id.report_spam, undoCallback));
        } else if (itemId == R.id.mark_not_spam) {
            // Currently, since spam messages are only shown in list with other spam messages,
            // marking a message not as spam is a destructive action
            destroy (R.id.mark_not_spam,
                    mCheckedSet.values(), mUpdater.getBatchAction(R.id.mark_not_spam,
                            undoCallback)) ;
        } else if (itemId == R.id.report_phishing) {
            destroy(R.id.report_phishing,
                    mCheckedSet.values(), mUpdater.getBatchAction(R.id.report_phishing,
                            undoCallback));
        } else if (itemId == R.id.read) {
            markConversationsRead(true);
        } else if (itemId == R.id.unread) {
            markConversationsRead(false);
        } else if (itemId == R.id.star) {
            starConversations(true);
        } else if (itemId == R.id.toggle_read_unread) {
            if (mActionMode != null) {
                markConversationsRead(mActionMode.getMenu().findItem(R.id.read).isVisible());
            }
        } else if (itemId == R.id.remove_star) {
            if (mFolder.isType(UIProvider.FolderType.STARRED)) {
                LogUtils.d(LOG_TAG, "We are in a starred folder, removing the star");
                performDestructiveAction(R.id.remove_star, undoCallback);
            } else {
                LogUtils.d(LOG_TAG, "Not in a starred folder.");
                starConversations(false);
            }
        } else if (itemId == R.id.move_to || itemId == R.id.change_folders) {
            boolean cantMove = false;
            Account acct = mAccount;
            // Special handling for virtual folders
            if (mFolder.supportsCapability(FolderCapabilities.IS_VIRTUAL)) {
                Uri accountUri = null;
                for (Conversation conv: mCheckedSet.values()) {
                    if (accountUri == null) {
                        accountUri = conv.accountUri;
                    } else if (!accountUri.equals(conv.accountUri)) {
                        // Tell the user why we can't do this
                        Toast.makeText(mContext, R.string.cant_move_or_change_labels,
                                Toast.LENGTH_LONG).show();
                        cantMove = true;
                        return handled;
                    }
                }
                if (!cantMove) {
                    // Get the actual account here, so that we display its folders in the dialog
                    acct = MailAppProvider.getAccountFromAccountUri(accountUri);
                }
            }
            if (!cantMove) {
                final FolderSelectionDialog dialog = FolderSelectionDialog.getInstance(
                        acct, mCheckedSet.values(), true, mFolder,
                        item.getItemId() == R.id.move_to);
                if (dialog != null) {
                    dialog.show(mActivity.getFragmentManager(), null);
                }
            }
        } else if (itemId == R.id.move_to_inbox) {
            new AsyncTask<Void, Void, Folder>() {
                @Override
                protected Folder doInBackground(final Void... params) {
                    // Get the "move to" inbox
                    return Utils.getFolder(mContext, mAccount.settings.moveToInbox,
                            true /* allowHidden */);
                }

                @Override
                protected void onPostExecute(final Folder moveToInbox) {
                    final List<FolderOperation> ops = Lists.newArrayListWithCapacity(1);
                    // Add inbox
                    ops.add(new FolderOperation(moveToInbox, true));
                    mUpdater.assignFolder(ops, mCheckedSet.values(), true,
                            true /* showUndo */, false /* isMoveTo */);
                }
            }.execute((Void[]) null);
        } else if (itemId == R.id.mark_important) {
            markConversationsImportant(true);
        } else if (itemId == R.id.mark_not_important) {
            if (mFolder.supportsCapability(UIProvider.FolderCapabilities.ONLY_IMPORTANT)) {
                performDestructiveAction(R.id.mark_not_important, undoCallback);
            } else {
                markConversationsImportant(false);
            }
        } else {
            handled = false;
        }
        return handled;
    }

    /**
     * Clear the selection and perform related UI changes to keep the state consistent.
     */
    private void clearChecked() {
        mCheckedSet.clear();
    }

    /**
     * Update the underlying list adapter and redraw the menus if necessary.
     */
    private void updateSelection() {
        mUpdater.refreshConversationList();
        if (mActionMode != null) {
            // Calling mActivity.invalidateOptionsMenu doesn't have the correct behavior, since
            // the action mode is not refreshed when activity's options menu is invalidated.
            // Since we need to refresh our own menu, it is easy to call onPrepareActionMode
            // directly.
            onPrepareActionMode(mActionMode, mActionMode.getMenu());
        }
    }

    private void performDestructiveAction(final int action, UndoCallback undoCallback) {
        final Collection<Conversation> conversations = mCheckedSet.values();
        final Settings settings = mAccount.settings;
        final boolean showDialog;
        // no confirmation dialog by default unless user preference or common sense dictates one
        if (action == R.id.discard_drafts) {
            // drafts are lost forever, so always confirm
            showDialog = true;
        } else if (settings != null && (action == R.id.archive || action == R.id.delete)) {
            showDialog = (action == R.id.delete) ? settings.confirmDelete : settings.confirmArchive;
        } else {
            showDialog = false;
        }
        if (showDialog) {
            mUpdater.makeDialogListener(action, true /* fromSelectedSet */, null /* undoCallback */);
            final int resId;
            if (action == R.id.delete) {
                resId = R.plurals.confirm_delete_conversation;
            } else if (action == R.id.discard_drafts) {
                resId = R.plurals.confirm_discard_drafts_conversation;
            } else {
                resId = R.plurals.confirm_archive_conversation;
            }
            final CharSequence message = Utils.formatPlural(mContext, resId, conversations.size());
            final ConfirmDialogFragment c = ConfirmDialogFragment.newInstance(message);
            c.displayDialog(mActivity.getFragmentManager());
        } else {
            // No need to show the dialog, just make a destructive action and destroy the
            // selected set immediately.
            // TODO(viki): Stop using the deferred action here. Use the registered action.
            destroy(action, conversations, mUpdater.getDeferredBatchAction(action, undoCallback));
        }
    }

    /**
     * Destroy these conversations through the conversation updater
     * @param actionId the ID of the action: R.id.archive, R.id.delete, ...
     * @param target conversations to destroy
     * @param action the action that performs the destruction
     */
    private void destroy(int actionId, final Collection<Conversation> target,
            final DestructiveAction action) {
        LogUtils.i(LOG_TAG, "About to remove %d converations", target.size());
        mUpdater.delete(actionId, target, action, true);
    }

    /**
     * Marks the read state of currently selected conversations (<b>and</b> the backing storage)
     * to the value provided here.
     * @param read is true if the conversations are to be marked as read, false if they are to be
     * marked unread.
     */
    private void markConversationsRead(boolean read) {
        final Collection<Conversation> targets = mCheckedSet.values();
        // The conversations are marked read but not viewed.
        mUpdater.markConversationsRead(targets, read, false);
        updateSelection();
    }

    /**
     * Marks the important state of currently selected conversations (<b>and</b> the backing
     * storage) to the value provided here.
     * @param important is true if the conversations are to be marked as important, false if they
     * are to be marked not important.
     */
    private void markConversationsImportant(boolean important) {
        final Collection<Conversation> target = mCheckedSet.values();
        final int priority = important ? UIProvider.ConversationPriority.HIGH
                : UIProvider.ConversationPriority.LOW;
        mUpdater.updateConversation(target, ConversationColumns.PRIORITY, priority);
        // Update the conversations in the selection too.
        for (final Conversation c : target) {
            c.priority = priority;
        }
        updateSelection();
    }

    /**
     * Marks the selected conversations with the star setting provided here.
     * @param star true if you want all the conversations to have stars, false if you want to remove
     * stars from all conversations
     */
    private void starConversations(boolean star) {
        final Collection<Conversation> target = mCheckedSet.values();
        mUpdater.updateConversation(target, ConversationColumns.STARRED, star);
        // Update the conversations in the selection too.
        for (final Conversation c : target) {
            c.starred = star;
        }
        updateSelection();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mCheckedSet.addObserver(this);
        final MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(R.menu.conversation_list_selection_actions_menu, menu);
        mActionMode = mode;
        updateCount();
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // Update the actionbar to select operations available on the current conversation.
        final Collection<Conversation> conversations = mCheckedSet.values();
        boolean showStar = false;
        boolean showMarkUnread = false;
        boolean showMarkImportant = false;
        boolean showMarkNotSpam = false;
        boolean showMarkAsPhishing = false;

        // TODO(shahrk): Clean up these dirty calls using Utils.setMenuItemPresent(...) or
        // in another way

        for (Conversation conversation : conversations) {
            if (!conversation.starred) {
                showStar = true;
            }
            if (conversation.read) {
                showMarkUnread = true;
            }
            if (!conversation.isImportant()) {
                showMarkImportant = true;
            }
            if (conversation.spam) {
                showMarkNotSpam = true;
            }
            if (!conversation.phishing) {
                showMarkAsPhishing = true;
            }
            if (showStar && showMarkUnread && showMarkImportant && showMarkNotSpam &&
                    showMarkAsPhishing) {
                break;
            }
        }
        final boolean canStar = mFolder != null && !mFolder.isTrash();
        final MenuItem star = menu.findItem(R.id.star);
        star.setVisible(showStar && canStar);
        final MenuItem unstar = menu.findItem(R.id.remove_star);
        unstar.setVisible(!showStar && canStar);
        final MenuItem read = menu.findItem(R.id.read);
        read.setVisible(!showMarkUnread);
        final MenuItem unread = menu.findItem(R.id.unread);
        unread.setVisible(showMarkUnread);

        // We only ever show one of:
        // 1) remove folder
        // 2) archive
        final MenuItem removeFolder = menu.findItem(R.id.remove_folder);
        final MenuItem moveTo = menu.findItem(R.id.move_to);
        final MenuItem moveToInbox = menu.findItem(R.id.move_to_inbox);
        final boolean showRemoveFolder = mFolder != null && mFolder.isType(FolderType.DEFAULT)
                && mFolder.supportsCapability(FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)
                && !mFolder.isProviderFolder()
                && mAccount.supportsCapability(AccountCapabilities.ARCHIVE);
        final boolean showMoveTo = mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.ALLOWS_REMOVE_CONVERSATION);
        final boolean showMoveToInbox = mFolder != null
                && mFolder.supportsCapability(FolderCapabilities.ALLOWS_MOVE_TO_INBOX);
        removeFolder.setVisible(showRemoveFolder);
        moveTo.setVisible(showMoveTo);
        moveToInbox.setVisible(showMoveToInbox);

        final MenuItem changeFolders = menu.findItem(R.id.change_folders);
        changeFolders.setVisible(mAccount.supportsCapability(
                UIProvider.AccountCapabilities.MULTIPLE_FOLDERS_PER_CONV));

        if (mFolder != null && showRemoveFolder) {
            removeFolder.setTitle(mActivity.getActivityContext().getString(R.string.remove_folder,
                    mFolder.name));
        }
        final MenuItem archive = menu.findItem(R.id.archive);
        if (archive != null) {
            archive.setVisible(
                    mAccount.supportsCapability(UIProvider.AccountCapabilities.ARCHIVE) &&
                    mFolder.supportsCapability(FolderCapabilities.ARCHIVE));
        }
        final MenuItem spam = menu.findItem(R.id.report_spam);
        spam.setVisible(!showMarkNotSpam
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_SPAM)
                && mFolder.supportsCapability(FolderCapabilities.REPORT_SPAM));
        final MenuItem notSpam = menu.findItem(R.id.mark_not_spam);
        notSpam.setVisible(showMarkNotSpam &&
                mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_SPAM) &&
                mFolder.supportsCapability(FolderCapabilities.MARK_NOT_SPAM));
        final MenuItem phishing = menu.findItem(R.id.report_phishing);
        phishing.setVisible(showMarkAsPhishing &&
                mAccount.supportsCapability(UIProvider.AccountCapabilities.REPORT_PHISHING) &&
                mFolder.supportsCapability(FolderCapabilities.REPORT_PHISHING));

        final MenuItem mute = menu.findItem(R.id.mute);
        if (mute != null) {
            mute.setVisible(mAccount.supportsCapability(UIProvider.AccountCapabilities.MUTE)
                    && (mFolder != null && mFolder.isInbox()));
        }
        final MenuItem markImportant = menu.findItem(R.id.mark_important);
        markImportant.setVisible(showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));
        final MenuItem markNotImportant = menu.findItem(R.id.mark_not_important);
        markNotImportant.setVisible(!showMarkImportant
                && mAccount.supportsCapability(UIProvider.AccountCapabilities.MARK_IMPORTANT));

        boolean shouldShowDiscardOutbox = mFolder != null && mFolder.isType(FolderType.OUTBOX);
        mDiscardOutboxMenuItem = menu.findItem(R.id.discard_outbox);
        if (mDiscardOutboxMenuItem != null) {
            mDiscardOutboxMenuItem.setVisible(shouldShowDiscardOutbox);
        }
        final boolean showDelete = mFolder != null && !mFolder.isType(FolderType.OUTBOX)
                && mFolder.supportsCapability(UIProvider.FolderCapabilities.DELETE);
        final MenuItem trash = menu.findItem(R.id.delete);
        trash.setVisible(showDelete);
        // We only want to show the discard drafts menu item if we are not showing the delete menu
        // item, and the current folder is a draft folder and the account supports discarding
        // drafts for a conversation
        final boolean showDiscardDrafts = !showDelete && mFolder != null && mFolder.isDraft() &&
                mAccount.supportsCapability(AccountCapabilities.DISCARD_CONVERSATION_DRAFTS);
        final MenuItem discardDrafts = menu.findItem(R.id.discard_drafts);
        if (discardDrafts != null) {
            discardDrafts.setVisible(showDiscardDrafts);
        }

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        // The action mode may have been destroyed due to this menu being deactivated, in which
        // case resources need not be cleaned up. However, if it was destroyed while this menu is
        // active, that implies the user hit "Done" in the top right, and resources need cleaning.
        if (mActivated) {
            destroy();
            // Only commit destructive actions if the user actually pressed
            // done; otherwise, this was handled when we toggled conversation
            // selection state.
            mActivity.getListHandler().commitDestructiveActions(true);
        }
    }

    @Override
    public void onSetPopulated(ConversationCheckedSet set) {
        // Noop. This object can only exist while the set is non-empty.
    }

    @Override
    public void onSetEmpty() {
        LogUtils.d(LOG_TAG, "onSetEmpty called.");
        destroy();
    }

    @Override
    public void onSetChanged(ConversationCheckedSet set) {
        // If the set is empty, the menu buttons are invalid and most like the menu will be cleaned
        // up. Avoid making any changes to stop flickering ("Add Star" -> "Remove Star") just
        // before hiding the menu.
        if (set.isEmpty()) {
            return;
        }
        updateCount();
    }

    /**
     * Updates the visible count of how many conversations are selected.
     */
    private void updateCount() {
        if (mActionMode != null) {
            mActionMode.setTitle(String.format("%d", mCheckedSet.size()));
        }
    }

    /**
     * Activates and shows this menu (essentially starting an {@link ActionMode}) if the selected
     * set is non-empty.
     */
    public void activate() {
        if (mCheckedSet.isEmpty()) {
            return;
        }
        mListController.onCabModeEntered();
        mActivated = true;
        if (mActionMode == null) {
            mActivity.startSupportActionMode(this);
        }
    }

    /**
     * De-activates and hides the menu (essentially disabling the {@link ActionMode}), but maintains
     * the selection conversation set, and internally updates state as necessary.
     */
    public void deactivate() {
        mListController.onCabModeExited();
        mActivated = false;
        if (mActionMode != null) {
            mActionMode.finish();
        }
    }

    @VisibleForTesting
    /**
     * Returns true if CAB mode is active.
     */
    public boolean isActivated() {
        return mActivated;
    }

    /**
     * Destroys and cleans up the resources associated with this menu.
     */
    private void destroy() {
        deactivate();
        mCheckedSet.removeObserver(this);
        clearChecked();
        mUpdater.refreshConversationList();
        if (mAccountObserver != null) {
            mAccountObserver.unregisterAndDestroy();
            mAccountObserver = null;
        }
    }
}
