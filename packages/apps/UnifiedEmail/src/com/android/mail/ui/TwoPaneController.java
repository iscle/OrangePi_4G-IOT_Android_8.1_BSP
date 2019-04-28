/*******************************************************************************
 *      Copyright (C) 2012 Google Inc.
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

package com.android.mail.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v7.app.ActionBar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.ConversationListIcon;
import com.android.mail.utils.EmptyStateUtils;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;

/**
 * Controller for two-pane Mail activity. Two Pane is used for tablets, where screen real estate
 * abounds.
 */
public final class TwoPaneController extends AbstractActivityController implements
        ConversationViewFrame.DownEventListener {

    private static final String SAVED_MISCELLANEOUS_VIEW = "saved-miscellaneous-view";
    private static final String SAVED_MISCELLANEOUS_VIEW_TRANSACTION_ID =
            "saved-miscellaneous-view-transaction-id";
    private static final String SAVED_PEEK_MODE = "saved-peeking";
    private static final String SAVED_PEEKING_CONVERSATION = "saved-peeking-conv";

    private TwoPaneLayout mLayout;
    private ImageView mEmptyCvView;
    private List<TwoPaneLayout.ConversationListLayoutListener> mConversationListLayoutListeners =
            Lists.newArrayList();

    /**
     * 2-pane, in wider configurations, allows peeking at a conversation view without having the
     * conversation marked-as-read as far as read/unread state goes.<br>
     * <br>
     * This flag applies to {@link AbstractActivityController#mCurrentConversation} and indicates
     * that the current conversation, if set, is in a 'peeking' state. If there is no current
     * conversation, peeking is implied (in certain view configurations) and this value is
     * meaningless.
     */
    private boolean mCurrentConversationJustPeeking;

    /**
     * When rotating from land->port->back to land while peeking at a conversation, typically we
     * would lose the pointer to the conversation being seen in portrait (because in port, we're in
     * TL mode so conv=null). This is bad if we ever want to go back to landscape, since the user
     * expectation is that the original peek conversation should appear.
     * <br>
     * <p>So save the previous peeking conversation (if any) when restoring in portrait so that a
     * future landscape restore can load it up.
     */
    private Conversation mSavedPeekingConversation;

    /**
     * The conversation to show (and any extra information about its presentation, like how it was
     * triggered). Kept here during a transition animation to take effect afterwards.
     */
    private ToShow mToShow;

    // For keyboard-focused conversations, we'll put it in a separate runnable.
    private static final int FOCUSED_CONVERSATION_DELAY_MS = 500;
    private final Runnable mFocusedConversationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mActivity.isFinishing()) {
                showCurrentConversationInPager();
            }
        }
    };

    /**
     * Used to determine whether onViewModeChanged should skip a potential
     * fragment transaction that would remove a miscellaneous view.
     */
    private boolean mSavedMiscellaneousView = false;

    private boolean mIsTabletLandscape;

    public TwoPaneController(MailActivity activity, ViewMode viewMode) {
        super(activity, viewMode);
    }

    @Override
    protected void appendToString(StringBuilder sb) {
        sb.append(" mPeeking=");
        sb.append(mCurrentConversationJustPeeking);
        sb.append(" mSavedPeekConv=");
        sb.append(mSavedPeekingConversation);
        if (mToShow != null) {
            sb.append(" mToShow.conv=");
            sb.append(mToShow.conversation);
            sb.append(" mToShow.dueToKeyboard=");
            sb.append(mToShow.dueToKeyboard);
        }
        sb.append(" mLayout=");
        sb.append(mLayout);
    }

    @Override
    public boolean isCurrentConversationJustPeeking() {
        return mCurrentConversationJustPeeking;
    }

    private boolean isHidingConversationList() {
        return (mViewMode.isConversationMode() || mViewMode.isAdMode()) &&
                !mLayout.shouldShowPreviewPanel();
    }

    /**
     * Display the conversation list fragment.
     */
    private void initializeConversationListFragment() {
        if (Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())) {
            if (shouldEnterSearchConvMode()) {
                mViewMode.enterSearchResultsConversationMode();
            } else {
                mViewMode.enterSearchResultsListMode();
            }
        }
        renderConversationList();
    }

    /**
     * Render the conversation list in the correct pane.
     */
    private void renderConversationList() {
        if (mActivity == null) {
            return;
        }
        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        // Use cross fading animation.
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        final ConversationListFragment conversationListFragment =
                ConversationListFragment.newInstance(mConvListContext);
        fragmentTransaction.replace(R.id.conversation_list_place_holder, conversationListFragment,
                TAG_CONVERSATION_LIST);
        fragmentTransaction.commitAllowingStateLoss();
        // Set default navigation here once the ConversationListFragment is created.
        conversationListFragment.setNextFocusStartId(
                getClfNextFocusStartId());
    }

    @Override
    public boolean doesActionChangeConversationListVisibility(final int action) {
        if (action == R.id.settings
                || action == R.id.compose
                || action == R.id.help_info_menu_item
                || action == R.id.feedback_menu_item) {
            return true;
        }

        return false;
    }

    @Override
    protected boolean isConversationListVisible() {
        return !mLayout.isConversationListCollapsed();
    }

    @Override
    protected void showConversationList(ConversationListContext listContext) {
        initializeConversationListFragment();
    }

    @Override
    public @LayoutRes int getContentViewResource() {
        return R.layout.two_pane_activity;
    }

    @Override
    public void onCreate(Bundle savedState) {
        mLayout = (TwoPaneLayout) mActivity.findViewById(R.id.two_pane_activity);
        mEmptyCvView = (ImageView) mActivity.findViewById(R.id.conversation_pane_no_message_view);
        if (mLayout == null) {
            // We need the layout for everything. Crash/Return early if it is null.
            LogUtils.wtf(LOG_TAG, "mLayout is null!");
            return;
        }
        mLayout.setController(this);
        mActivity.getWindow().setBackgroundDrawable(null);
        mIsTabletLandscape = mActivity.getResources().getBoolean(R.bool.is_tablet_landscape);

        final FolderListFragment flf = getFolderListFragment();
        flf.setMiniDrawerEnabled(true);
        flf.setMinimized(true);

        if (savedState != null) {
            mSavedMiscellaneousView = savedState.getBoolean(SAVED_MISCELLANEOUS_VIEW, false);
            mMiscellaneousViewTransactionId =
                    savedState.getInt(SAVED_MISCELLANEOUS_VIEW_TRANSACTION_ID, -1);
        }

        // 2-pane layout is the main listener of view mode changes, and issues secondary
        // notifications upon animation completion:
        // (onConversationVisibilityChanged, onConversationListVisibilityChanged)
        mViewMode.addListener(mLayout);

        super.onCreate(savedState);

        // Restore peek-related state *after* the super-implementation naively restores view mode.
        if (savedState != null) {
            mCurrentConversationJustPeeking = savedState.getBoolean(SAVED_PEEK_MODE,
                    false /* defaultValue */);
            mSavedPeekingConversation = savedState.getParcelable(SAVED_PEEKING_CONVERSATION);
            // do the remaining restore work in restoreConversation()
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mFocusedConversationRunnable);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(SAVED_MISCELLANEOUS_VIEW, mMiscellaneousViewTransactionId >= 0);
        outState.putInt(SAVED_MISCELLANEOUS_VIEW_TRANSACTION_ID, mMiscellaneousViewTransactionId);
        outState.putBoolean(SAVED_PEEK_MODE, mCurrentConversationJustPeeking);
        outState.putParcelable(SAVED_PEEKING_CONVERSATION, mSavedPeekingConversation);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && !mLayout.isConversationListCollapsed()) {
            // The conversation list is visible.
            informCursorVisiblity(true);
        }
    }

    @Override
    protected void restoreConversation(Conversation conversation) {
        // When handling restoration as part of rotation, if the destination orientation doesn't
        // support peek (i.e. portrait), remap the view mode to list-mode if previously peeking.
        // We still want to keep the peek state around in case the user rotates back to
        // landscape, in which case the app should remember that peek mode was on and which
        // conversation to peek at.
        if (mCurrentConversationJustPeeking && !mIsTabletLandscape
                && mViewMode.isConversationMode()) {
            LogUtils.i(LOG_TAG, "restoring peek to port orientation");

            // Restore the pager saved state, extract the Fragments out of it, kill each one
            // manually, and finally tear down the pager and go back to the list.
            //
            // Need to tear down the restored CV fragments or else they will leak since the
            // fragment manager will have a reference to them but nobody else does.
            // normally, CPC.show() connects the new pager to the restored fragments, so a future
            // CPC.hide() correctly clears them.

            mPagerController.show(mAccount, mFolder, conversation, false /* changeVisibility */,
                    null /* pagerAnimationListener */);
            mPagerController.killRestoredFragments();
            mPagerController.hide(false /* changeVisibility */);

            // but first, save off the conversation in a separate slot for later restoration if
            // we then end up back in peek mode
            mSavedPeekingConversation = conversation;

            mViewMode.enterConversationListMode();
        } else if (mCurrentConversationJustPeeking && mIsTabletLandscape) {
            showConversationWithPeek(conversation, true /* peek */);
        } else {
            super.restoreConversation(conversation);
        }
    }

    @Override
    public void switchToDefaultInboxOrChangeAccount(Account account) {
        if (mViewMode.isSearchMode()) {
            // We are in an activity on top of the main navigation activity.
            // We need to return to it with a result code that indicates it should navigate to
            // a different folder.
            final Intent intent = new Intent();
            intent.putExtra(AbstractActivityController.EXTRA_ACCOUNT, account);
            mActivity.setResult(Activity.RESULT_OK, intent);
            mActivity.finish();
            return;
        }
        if (mViewMode.getMode() != ViewMode.CONVERSATION_LIST) {
            mViewMode.enterConversationListMode();
        }
        super.switchToDefaultInboxOrChangeAccount(account);
    }

    @Override
    public void onFolderSelected(Folder folder) {
        // It's possible that we are not in conversation list mode
        if (mViewMode.isSearchMode()) {
            // We are in an activity on top of the main navigation activity.
            // We need to return to it with a result code that indicates it should navigate to
            // a different folder.
            final Intent intent = new Intent();
            intent.putExtra(AbstractActivityController.EXTRA_FOLDER, folder);
            mActivity.setResult(Activity.RESULT_OK, intent);
            mActivity.finish();
            return;
        } else if (mViewMode.getMode() != ViewMode.CONVERSATION_LIST) {
            mViewMode.enterConversationListMode();
        }

        setHierarchyFolder(folder);
        super.onFolderSelected(folder);
    }

    public boolean isDrawerOpen() {
        final FolderListFragment flf = getFolderListFragment();
        return flf != null && !flf.isMinimized();
    }

    @Override
    protected void toggleDrawerState() {
        final FolderListFragment flf = getFolderListFragment();
        if (flf == null) {
            LogUtils.w(LOG_TAG, "no drawer to toggle open/closed");
            return;
        }

        setDrawerState(!flf.isMinimized());
    }

    protected void setDrawerState(boolean minimized) {
        final FolderListFragment flf = getFolderListFragment();
        if (flf == null) {
            LogUtils.w(LOG_TAG, "no drawer to toggle open/closed");
            return;
        }

        flf.animateMinimized(minimized);
        mLayout.animateDrawer(minimized);
        resetActionBarIcon();

        final ConversationListFragment clf = getConversationListFragment();
        if (clf != null) {
            clf.setNextFocusStartId(getClfNextFocusStartId());

            final SwipeableListView list = clf.getListView();
            if (list != null) {
                if (minimized) {
                    list.stopPreventingSwipes();
                } else {
                    list.preventSwipesEntirely();
                }
            }
        }
    }

    /** START TPL DRAWER DRAG CALLBACKS **/
    protected void onDrawerDragStarted() {
        final FolderListFragment flf = getFolderListFragment();
        if (flf == null) {
            LogUtils.w(LOG_TAG, "no drawer to toggle open/closed");
            return;
        }

        flf.onDrawerDragStarted();
    }

    protected void onDrawerDrag(float percent) {
        final FolderListFragment flf = getFolderListFragment();
        if (flf == null) {
            LogUtils.w(LOG_TAG, "no drawer to toggle open/closed");
            return;
        }

        flf.onDrawerDrag(percent);
    }

    protected void onDrawerDragEnded(boolean minimized) {
        // On drag completion animate the drawer to the final state.
        setDrawerState(minimized);
    }
    /** END TPL DRAWER DRAG CALLBACKS **/

    @Override
    public boolean shouldPreventListSwipesEntirely() {
        return isDrawerOpen();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mCurrentConversation != null) {
            if (mCurrentConversationJustPeeking) {
                Utils.setMenuItemPresent(menu, R.id.read, !mCurrentConversation.read);
                Utils.setMenuItemPresent(menu, R.id.inside_conversation_unread,
                        mCurrentConversation.read);
            } else {
                // in normal conv mode, always hide the extra 'mark-read' item
                Utils.setMenuItemPresent(menu, R.id.read, false);
            }
        }
    }

    @Override
    public void onViewModeChanged(int newMode) {
        if (!mSavedMiscellaneousView && mMiscellaneousViewTransactionId >= 0) {
            final FragmentManager fragmentManager = mActivity.getFragmentManager();
            fragmentManager.popBackStackImmediate(mMiscellaneousViewTransactionId,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
            mMiscellaneousViewTransactionId = -1;
        }
        mSavedMiscellaneousView = false;

        super.onViewModeChanged(newMode);
        if (newMode != ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION) {
            // Clear the wait fragment
            hideWaitForInitialization();
        }
        // In conversation mode, if the conversation list is not visible, then the user cannot
        // see the selected conversations. Disable the CAB mode while leaving the selected set
        // untouched.
        // When the conversation list is made visible again, try to enable the CAB
        // mode if any conversations are selected.
        if (newMode == ViewMode.CONVERSATION || newMode == ViewMode.CONVERSATION_LIST
                || ViewMode.isAdMode(newMode)) {
            enableOrDisableCab();
        }
    }

    private @IdRes int getClfNextFocusStartId() {
        return (isDrawerOpen()) ? android.R.id.list : R.id.mini_drawer;
    }

    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        super.onConversationVisibilityChanged(visible);
        if (!visible) {
            mPagerController.hide(false /* changeVisibility */);
        } else if (mToShow != null) {
            if (mToShow.dueToKeyboard) {
                mHandler.removeCallbacks(mFocusedConversationRunnable);
                mHandler.postDelayed(mFocusedConversationRunnable, FOCUSED_CONVERSATION_DELAY_MS);
            } else {
                showCurrentConversationInPager();
            }
        }

        // Change visibility of the empty view
        if (mIsTabletLandscape) {
            mEmptyCvView.setVisibility(visible ? View.GONE : View.VISIBLE);
        }
    }

    private void showCurrentConversationInPager() {
        if (mToShow != null) {
            mPagerController.show(mAccount, mFolder, mToShow.conversation,
                    false /* changeVisibility */, null /* pagerAnimationListener */);
            mToShow = null;
        }
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        super.onConversationListVisibilityChanged(visible);
        enableOrDisableCab();
    }

    @Override
    public void resetActionBarIcon() {
        final ActionBar ab = mActivity.getSupportActionBar();
        final boolean isChildFolder = getFolder() != null && !Utils.isEmpty(getFolder().parent);
        if (isHidingConversationList() || isChildFolder) {
            ab.setHomeAsUpIndicator(R.drawable.ic_arrow_back_wht_24dp_with_rtl);
            ab.setHomeActionContentDescription(0 /* system default */);
        } else {
            ab.setHomeAsUpIndicator(R.drawable.ic_menu_wht_24dp);
            ab.setHomeActionContentDescription(
                    isDrawerOpen() ? R.string.drawer_close : R.string.drawer_open);
        }
    }

    /**
     * Enable or disable the CAB mode based on the visibility of the conversation list fragment.
     */
    private void enableOrDisableCab() {
        if (mLayout.isConversationListCollapsed()) {
            disableCabMode();
        } else {
            enableCabMode();
        }
    }

    @Override
    public void onSetPopulated(ConversationCheckedSet set) {
        super.onSetPopulated(set);

        boolean showSenderImage =
                (mAccount.settings.convListIcon == ConversationListIcon.SENDER_IMAGE);
        if (!showSenderImage && mViewMode.isListMode()) {
            getConversationListFragment().setChoiceNone();
        }
    }

    @Override
    public void onSetEmpty() {
        super.onSetEmpty();

        boolean showSenderImage =
                (mAccount.settings.convListIcon == ConversationListIcon.SENDER_IMAGE);
        if (!showSenderImage && mViewMode.isListMode()) {
            getConversationListFragment().revertChoiceMode();
        }
    }

    @Override
    protected void showConversationWithPeek(Conversation conversation, boolean peek) {
        showConversation(conversation, peek, false /* fromKeyboard */);
    }

    private boolean isCurrentlyPeeking() {
        return mViewMode.isConversationMode() && mCurrentConversationJustPeeking
                && mCurrentConversation != null;
    }

    private void showConversation(Conversation conversation, boolean peek, boolean fromKeyboard) {
        // transition from peek mode to normal mode if we're already peeking at this convo
        // and this was a request to switch to normal mode
        if (!peek && conversation != null && conversation.equals(mCurrentConversation)
                && transitionFromPeekToNormalMode()) {
            LogUtils.i(LOG_TAG, "peek->normal: marking current CV seen. conv=%s",
                    mCurrentConversation);
            return;
        }

        // Make sure that we set the peeking flag before calling super (since some functionality
        // in super depends on the flag.
        mCurrentConversationJustPeeking = peek;
        super.showConversationWithPeek(conversation, peek);

        // 2-pane can ignore inLoaderCallbacks because it doesn't use
        // FragmentManager.popBackStack().

        if (mActivity == null) {
            return;
        }
        if (conversation == null) {
            handleBackPress(true /* preventClose */);
            return;
        }
        // If conversation list is not visible, then the user cannot see the CAB mode, so exit it.
        // This is needed here (in addition to during viewmode changes) because orientation changes
        // while viewing a conversation don't change the viewmode: the mode stays
        // ViewMode.CONVERSATION and yet the conversation list goes in and out of visibility.
        enableOrDisableCab();

        // When a mode change is required, wait for onConversationVisibilityChanged(), the signal
        // that the mode change animation has finished, before rendering the conversation.
        mToShow = new ToShow(conversation, fromKeyboard);

        final int mode = mViewMode.getMode();
        LogUtils.i(LOG_TAG, "IN TPC.showConv, oldMode=%s conv=%s", mViewMode, mToShow.conversation);
        if (mode == ViewMode.SEARCH_RESULTS_LIST || mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
            mViewMode.enterSearchResultsConversationMode();
        } else {
            mViewMode.enterConversationMode();
        }
        // load the conversation immediately if we're already in conversation mode
        if (!mLayout.isModeChangePending()) {
            onConversationVisibilityChanged(true);
        } else {
            LogUtils.i(LOG_TAG, "TPC.showConversation will wait for TPL.animationEnd to show!");
        }
    }

    /**
     * @return success=true, else false if we aren't peeking
     */
    private boolean transitionFromPeekToNormalMode() {
        final boolean shouldTransition = isCurrentlyPeeking();
        if (shouldTransition) {
            mCurrentConversationJustPeeking = false;
            markConversationSeen(mCurrentConversation);
        }
        return shouldTransition;
    }

    @Override
    public void onConversationSelected(Conversation conversation, boolean inLoaderCallbacks) {
        // close the drawer when the user opens CV from the list
        if (isDrawerOpen()) {
            toggleDrawerState();
        }
        super.onConversationSelected(conversation, inLoaderCallbacks);
        if (!mCurrentConversationJustPeeking) {
            // Shift the focus to the conversation in landscape mode.
            mPagerController.focusPager();
        }
    }

    @Override
    public void onConversationFocused(Conversation conversation) {
        if (mIsTabletLandscape) {
            showConversation(conversation, true /* peek */, true /* fromKeyboard */);
        }
    }

    @Override
    public void setCurrentConversation(Conversation conversation) {
        // Order is important! We want to calculate different *before* the superclass changes
        // mCurrentConversation, so before super.setCurrentConversation().
        final long oldId = mCurrentConversation != null ? mCurrentConversation.id : -1;
        final long newId = conversation != null ? conversation.id : -1;
        final boolean different = oldId != newId;

        if (different) {
            LogUtils.i(LOG_TAG, "TPC.setCurrentConv w/ new conv. new=%s old=%s newPeek=%s",
                    conversation, mCurrentConversation, mCurrentConversationJustPeeking);
        }

        // This call might change mCurrentConversation.
        super.setCurrentConversation(conversation);

        final ConversationListFragment convList = getConversationListFragment();
        if (different && convList != null && conversation != null) {
            if (mCurrentConversationJustPeeking) {
                convList.clearChoicesAndActivated();
                convList.setSelected(conversation);
            } else {
                convList.setActivated(conversation, different);
            }
        }
    }

    @Override
    public void onConversationViewSwitched(Conversation conversation) {
        // swiping on CV to flip through CV pages should reset the peeking flag; the next
        // conversation should be marked read when visible
        //
        // it's also possible to get here when the dataset changes and the current CV is
        // repositioned in the dataset, so make sure the current conv is actually being switched
        // before clearing the peek state
        if (!Objects.equal(conversation, mCurrentConversation)) {
            LogUtils.i(LOG_TAG, "CPA reported a page change. resetting peek to false. new conv=%s",
                    conversation);
            mCurrentConversationJustPeeking = false;
        }
        super.onConversationViewSwitched(conversation);
    }

    @Override
    protected void doShowNextConversation(Collection<Conversation> target, int autoAdvance) {
        // in portrait, and in landscape when auto-advance is set, do the regular thing
        if (!isTwoPaneLandscape() || autoAdvance != AutoAdvance.LIST) {
            super.doShowNextConversation(target, autoAdvance);
            return;
        }

        // special case for two-pane landscape with LIST auto-advance: prefer to peek at the
        // next-oldest conversation instead. showConversation() will resort to an empty CV pane when
        // destroying the very last conversation.
        final Conversation next = mTracker.getNextConversation(AutoAdvance.OLDER, target);
        LogUtils.i(LOG_TAG, "showNextConversation(2P-land): showing %s next.", next);
        showConversationWithPeek(next, true /* peek */);
    }

    @Override
    protected void showWaitForInitialization() {
        super.showWaitForInitialization();

        FragmentTransaction fragmentTransaction = mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.replace(R.id.conversation_list_place_holder, getWaitFragment(), TAG_WAIT);
        fragmentTransaction.commitAllowingStateLoss();
    }

    @Override
    protected void hideWaitForInitialization() {
        final WaitFragment waitFragment = getWaitFragment();
        if (waitFragment == null) {
            // We aren't showing a wait fragment: nothing to do
            return;
        }
        // Remove the existing wait fragment from the back stack.
        final FragmentTransaction fragmentTransaction =
                mActivity.getFragmentManager().beginTransaction();
        fragmentTransaction.remove(waitFragment);
        fragmentTransaction.commitAllowingStateLoss();
        super.hideWaitForInitialization();
        if (mViewMode.isWaitingForSync()) {
            // We should come out of wait mode and display the account inbox.
            loadAccountInbox();
        }
    }

    /**
     * Up works as follows:
     * 1) If the user is in a conversation and:
     *  a) the conversation list is hidden (portrait mode), shows the conv list and
     *  stays in conversation view mode.
     *  b) the conversation list is shown, goes back to conversation list mode.
     * 2) If the user is in search results, up exits search.
     * mode and returns the user to whatever view they were in when they began search.
     * 3) If the user is in conversation list mode, there is no up.
     */
    @Override
    public boolean handleUpPress() {
        if (isHidingConversationList()) {
            handleBackPress();
        } else {
            final boolean isTopLevel = Folder.isRoot(mFolder);

            if (isTopLevel) {
                // Show the drawer.
                toggleDrawerState();
            } else {
                navigateUpFolderHierarchy();
            }
        }

        return true;
    }

    @Override
    public boolean handleBackPress() {
        return handleBackPress(false /* preventClose */);
    }

    private boolean handleBackPress(boolean preventClose) {
        // Clear any visible undo bars.
        mToastBar.hide(false, false /* actionClicked */);
        if (isDrawerOpen()) {
            toggleDrawerState();
        } else {
            popView(preventClose);
        }
        return true;
    }

    /**
     * Pops the "view stack" to the last screen the user was viewing.
     *
     * @param preventClose Whether to prevent closing the app if the stack is empty.
     */
    protected void popView(boolean preventClose) {
        // If the user is in search query entry mode, or the user is viewing
        // search results, exit
        // the mode.
        int mode = mViewMode.getMode();
        if (mode == ViewMode.SEARCH_RESULTS_LIST) {
            mActivity.finish();
        } else if (ViewMode.isConversationMode(mode) || mViewMode.isAdMode()) {
            // die if in two-pane landscape and the back button was pressed
            if (isTwoPaneLandscape() && !preventClose) {
                mActivity.finish();
            } else if (mode == ViewMode.SEARCH_RESULTS_CONVERSATION) {
                mViewMode.enterSearchResultsListMode();
            } else {
                mViewMode.enterConversationListMode();
            }
        } else {
            // The Folder List fragment can be null for monkeys where we get a back before the
            // folder list has had a chance to initialize.
            final FolderListFragment folderList = getFolderListFragment();
            if (mode == ViewMode.CONVERSATION_LIST && folderList != null
                    && !Folder.isRoot(mFolder)) {
                // If the user navigated via the left folders list into a child folder,
                // back should take the user up to the parent folder's conversation list.
                navigateUpFolderHierarchy();
            // Otherwise, if we are in the conversation list but not in the default
            // inbox and not on expansive layouts, we want to switch back to the default
            // inbox. This fixes b/9006969 so that on smaller tablets where we have this
            // hybrid one and two-pane mode, we will return to the inbox. On larger tablets,
            // we will instead exit the app.
            } else if (!preventClose) {
                // There is nothing else to pop off the stack.
                mActivity.finish();
            }
        }
    }

    @Override
    protected void onPreMarkUnread() {
        // stay in CV when marking unread in two-pane mode
        if (isTwoPaneLandscape()) {
            // TODO: need to update the list item state to switch from activated to peeking
            mCurrentConversationJustPeeking = true;
            mActivity.supportInvalidateOptionsMenu();
        } else {
            super.onPreMarkUnread();
        }
    }

    @Override
    protected void perhapsShowFirstConversation() {
        super.perhapsShowFirstConversation();
        if (!mViewMode.isAdMode() && mCurrentConversation == null && isTwoPaneLandscape()
                && mConversationListCursor.getCount() > 0) {
            final Conversation conv;

            // restore the saved peeking conversation if present from the previous rotation
            if (mCurrentConversationJustPeeking && mSavedPeekingConversation != null) {
                conv = mSavedPeekingConversation;
                mSavedPeekingConversation = null;
                LogUtils.i(LOG_TAG, "peeking at saved conv=%s", conv);
            } else {
                mConversationListCursor.moveToPosition(0);
                conv = mConversationListCursor.getConversation();
                conv.position = 0;
                LogUtils.i(LOG_TAG, "peeking at default/zeroth conv=%s", conv);
            }

            showConversationWithPeek(conv, true /* peek */);
        }
    }

    @Override
    public boolean shouldShowFirstConversation() {
        return mLayout.shouldShowPreviewPanel();
    }

    @Override
    public void onUndoAvailable(ToastBarOperation op) {
        final int mode = mViewMode.getMode();
        final ConversationListFragment convList = getConversationListFragment();

        switch (mode) {
            case ViewMode.SEARCH_RESULTS_LIST:
            case ViewMode.CONVERSATION_LIST:
            case ViewMode.SEARCH_RESULTS_CONVERSATION:
            case ViewMode.CONVERSATION:
                if (convList != null) {
                    mToastBar.show(getUndoClickedListener(convList.getAnimatedAdapter()),
                            Utils.convertHtmlToPlainText
                                (op.getDescription(mActivity.getActivityContext())),
                            R.string.undo,
                            true /* replaceVisibleToast */,
                            true /* autohide */,
                            op);
                }
        }
    }

    @Override
    public void onError(final Folder folder, boolean replaceVisibleToast) {
        showErrorToast(folder, replaceVisibleToast);
    }

    @Override
    public boolean isDrawerEnabled() {
        // two-pane has its own drawer-like thing that expands inline from a minimized state.
        return false;
    }

    @Override
    public int getFolderListViewChoiceMode() {
        // By default, we want to allow one item to be selected in the folder list
        return ListView.CHOICE_MODE_SINGLE;
    }

    private int mMiscellaneousViewTransactionId = -1;

    @Override
    public void launchFragment(final Fragment fragment, final int selectPosition) {
        final int containerViewId = TwoPaneLayout.MISCELLANEOUS_VIEW_ID;

        final FragmentManager fragmentManager = mActivity.getFragmentManager();
        if (fragmentManager.findFragmentByTag(TAG_CUSTOM_FRAGMENT) == null) {
            final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.replace(containerViewId, fragment, TAG_CUSTOM_FRAGMENT);
            mMiscellaneousViewTransactionId = fragmentTransaction.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        }

        if (selectPosition >= 0) {
            getConversationListFragment().setRawActivated(selectPosition, true);
        }
    }

    @Override
    public boolean shouldBlockTouchEvents() {
        return isDrawerOpen();
    }

    @Override
    public void onConversationViewFrameTapped() {
        // handle a tap on CV by closing the drawer if open
        if (isDrawerOpen()) {
            toggleDrawerState();
        }
    }

    @Override
    public void onConversationViewTouchDown() {
        final boolean handled = transitionFromPeekToNormalMode();
        if (handled) {
            LogUtils.i(LOG_TAG, "TPC: tap on CV triggered peek->normal, marking seen. conv=%s",
                    mCurrentConversation);
        }
    }

    @Override
    public boolean onInterceptKeyFromCV(int keyCode, KeyEvent keyEvent, boolean navigateAway) {
        // Override left/right key presses in landscape mode.
        if (navigateAway) {
            if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                ConversationListFragment clf = getConversationListFragment();
                if (clf != null) {
                    clf.getListView().requestFocus();
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isTwoPaneLandscape() {
        return mIsTabletLandscape;
    }

    @Override
    public boolean shouldShowSearchBarByDefault(int viewMode) {
        return viewMode == ViewMode.SEARCH_RESULTS_LIST ||
                (mIsTabletLandscape && viewMode == ViewMode.SEARCH_RESULTS_CONVERSATION);
    }

    @Override
    public boolean shouldShowSearchMenuItem() {
        final int mode = mViewMode.getMode();
        return mode == ViewMode.CONVERSATION_LIST ||
                (mIsTabletLandscape && mode == ViewMode.CONVERSATION);
    }

    @Override
    public void addConversationListLayoutListener(
            TwoPaneLayout.ConversationListLayoutListener listener) {
        mConversationListLayoutListeners.add(listener);
    }

    public List<TwoPaneLayout.ConversationListLayoutListener> getConversationListLayoutListeners() {
        return mConversationListLayoutListeners;
    }

    @Override
    public boolean setupEmptyIconView(Folder folder, boolean isEmpty) {
        if (mIsTabletLandscape) {
            if (!isEmpty) {
                mEmptyCvView.setImageResource(R.drawable.ic_empty_default);
            } else {
                EmptyStateUtils.bindEmptyFolderIcon(mEmptyCvView, folder);
            }
            return true;
        }
        return false;
    }

    /**
     * The conversation to show (and other associated bits) when performing a TL->CV transition.
     *
     */
    private static class ToShow {
        public final Conversation conversation;
        public final boolean dueToKeyboard;

        public ToShow(Conversation c, boolean fromKeyboard) {
            conversation = c;
            dueToKeyboard = fromKeyboard;
        }

    }

}
