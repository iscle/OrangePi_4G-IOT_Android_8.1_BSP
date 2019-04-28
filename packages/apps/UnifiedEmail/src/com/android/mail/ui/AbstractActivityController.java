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

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.database.Observable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import com.android.mail.ConversationListContext;
import com.android.mail.MailLogService;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.analytics.AnalyticsTimer;
import com.android.mail.browse.ConfirmDialogFragment;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationCursor.ConversationOperation;
import com.android.mail.browse.ConversationItemViewModel;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.ConversationPagerAdapter;
import com.android.mail.browse.ConversationPagerController;
import com.android.mail.browse.SelectedConversationsActionMenu;
import com.android.mail.browse.SyncErrorDialogFragment;
import com.android.mail.browse.UndoCallback;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.content.CursorCreator;
import com.android.mail.content.ObjectCursor;
import com.android.mail.content.ObjectCursorLoader;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.ConversationInfo;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderWatcher;
import com.android.mail.providers.MailAppProvider;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.AccountCursorExtraKeys;
import com.android.mail.providers.UIProvider.AutoAdvance;
import com.android.mail.providers.UIProvider.ConversationColumns;
import com.android.mail.providers.UIProvider.ConversationOperations;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.ui.ActionableToastBar.ActionClickedListener;
import com.android.mail.utils.ContentProviderTask;
import com.android.mail.utils.DrawIdler;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.MailObservable;
import com.android.mail.utils.NotificationActionUtils;
import com.android.mail.utils.Utils;
import com.android.mail.utils.VeiledAddressMatcher;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;


/**
 * This is an abstract implementation of the Activity Controller. This class
 * knows how to respond to menu items, state changes, layout changes, etc. It
 * weaves together the views and listeners, dispatching actions to the
 * respective underlying classes.
 * <p>
 * Even though this class is abstract, it should provide default implementations
 * for most, if not all the methods in the ActivityController interface. This
 * makes the task of the subclasses easier: OnePaneActivityController and
 * TwoPaneActivityController can be concise when the common functionality is in
 * AbstractActivityController.
 * </p>
 * <p>
 * In the Gmail codebase, this was called BaseActivityController
 * </p>
 */
public abstract class AbstractActivityController implements ActivityController,
        EmptyFolderDialogFragment.EmptyFolderDialogFragmentListener, View.OnClickListener {
    // Keys for serialization of various information in Bundles.
    /** Tag for {@link #mAccount} */
    private static final String SAVED_ACCOUNT = "saved-account";
    /** Tag for {@link #mFolder} */
    private static final String SAVED_FOLDER = "saved-folder";
    /** Tag for {@link #mCurrentConversation} */
    private static final String SAVED_CONVERSATION = "saved-conversation";
    /** Tag for {@link #mCheckedSet} */
    private static final String SAVED_SELECTED_SET = "saved-selected-set";
    /** Tag for {@link ActionableToastBar#getOperation()} */
    private static final String SAVED_TOAST_BAR_OP = "saved-toast-bar-op";
    /** Tag for {@link #mFolderListFolder} */
    private static final String SAVED_HIERARCHICAL_FOLDER = "saved-hierarchical-folder";
    /** Tag for {@link ConversationListContext#searchQuery} */
    private static final String SAVED_QUERY = "saved-query";
    /** Tag for {@link #mDialogAction} */
    private static final String SAVED_ACTION = "saved-action";
    /** Tag for {@link #mDialogFromSelectedSet} */
    private static final String SAVED_ACTION_FROM_SELECTED = "saved-action-from-selected";
    /** Tag for {@link #mDetachedConvUri} */
    private static final String SAVED_DETACHED_CONV_URI = "saved-detached-conv-uri";
    /** Key to store {@link #mInbox}. */
    private static final String SAVED_INBOX_KEY = "m-inbox";
    /** Key to store {@link #mConversationListScrollPositions} */
    private static final String SAVED_CONVERSATION_LIST_SCROLL_POSITIONS =
            "saved-conversation-list-scroll-positions";

    /** Tag used when loading a wait fragment */
    protected static final String TAG_WAIT = "wait-fragment";
    /** Tag used when loading a conversation list fragment. */
    public static final String TAG_CONVERSATION_LIST = "tag-conversation-list";
    /** Tag used when loading a custom fragment. */
    protected static final String TAG_CUSTOM_FRAGMENT = "tag-custom-fragment";

    /** Key to store an account in a bundle */
    private final String BUNDLE_ACCOUNT_KEY = "account";
    /** Key to store a folder in a bundle */
    private final String BUNDLE_FOLDER_KEY = "folder";
    /**
     * Key to set a flag for the ConversationCursorLoader to ignore any
     * initial load limit that may be set by the Account. Instead,
     * perform a full load instead of the full-stage load.
     */
    private final String BUNDLE_IGNORE_INITIAL_CONVERSATION_LIMIT_KEY =
            "ignore-initial-conversation-limit";

    protected Account mAccount;
    protected Folder mFolder;
    protected Folder mInbox;
    /** True when {@link #mFolder} is first shown to the user. */
    private boolean mFolderChanged = false;
    protected ActionBarController mActionBarController;
    protected final MailActivity mActivity;
    protected final Context mContext;
    private final FragmentManager mFragmentManager;
    protected final RecentFolderList mRecentFolderList;
    protected ConversationListContext mConvListContext;
    protected Conversation mCurrentConversation;
    protected MaterialSearchViewController mSearchViewController;
    /**
     * The hash of {@link #mCurrentConversation} in detached mode. 0 if we are not in detached mode.
     */
    private Uri mDetachedConvUri;

    /** A map of {@link Folder} {@link Uri} to scroll position in the conversation list. */
    private final Bundle mConversationListScrollPositions = new Bundle();

    /** A {@link android.content.BroadcastReceiver} that suppresses new e-mail notifications. */
    private SuppressNotificationReceiver mNewEmailReceiver = null;

    /** Handler for all our local runnables. */
    protected Handler mHandler = new Handler();

    /**
     * The current mode of the application. All changes in mode are initiated by
     * the activity controller. View mode changes are propagated to classes that
     * attach themselves as listeners of view mode changes.
     */
    protected final ViewMode mViewMode;
    protected ContentResolver mResolver;
    protected boolean mHaveAccountList = false;
    private AsyncRefreshTask mAsyncRefreshTask;

    private boolean mDestroyed;

    /** True if running on tablet */
    private final boolean mIsTablet;

    /**
     * Are we in a point in the Activity/Fragment lifecycle where it's safe to execute fragment
     * transactions? (including back stack manipulation)
     * <p>
     * Per docs in {@link FragmentManager#beginTransaction()}, this flag starts out true, switches
     * to false after {@link Activity#onSaveInstanceState}, and becomes true again in both onStart
     * and onResume.
     */
    private boolean mSafeToModifyFragments = true;

    private final Set<Uri> mCurrentAccountUris = Sets.newHashSet();
    protected ConversationCursor mConversationListCursor;
    private final DataSetObservable mConversationListObservable = new MailObservable("List");

    /** Runnable that checks the logging level to enable/disable the logging service. */
    private Runnable mLogServiceChecker = null;
    /** List of all accounts currently known to the controller. This is never null. */
    private Account[] mAllAccounts = new Account[0];

    private FolderWatcher mFolderWatcher;

    private boolean mIgnoreInitialConversationLimit;

    /**
     * Interface for actions that are deferred until after a load completes. This is for handling
     * user actions which affect cursors (e.g. marking messages read or unread) that happen before
     * that cursor is loaded.
     */
    private interface LoadFinishedCallback {
        void onLoadFinished();
    }

    /** The deferred actions to execute when mConversationListCursor load completes. */
    private final ArrayList<LoadFinishedCallback> mConversationListLoadFinishedCallbacks =
            new ArrayList<LoadFinishedCallback>();

    private RefreshTimerTask mConversationListRefreshTask;

    /** Listeners that are interested in changes to the current account. */
    private final DataSetObservable mAccountObservers = new MailObservable("Account");
    /** Listeners that are interested in changes to the recent folders. */
    private final DataSetObservable mRecentFolderObservers = new MailObservable("RecentFolder");
    /** Listeners that are interested in changes to the list of all accounts. */
    private final DataSetObservable mAllAccountObservers = new MailObservable("AllAccounts");
    /** Listeners that are interested in changes to the current folder. */
    private final DataSetObservable mFolderObservable = new MailObservable("CurrentFolder");
    /** Listeners that are interested in changes to the Folder or Account selection */
    private final DataSetObservable mFolderOrAccountObservers =
            new MailObservable("FolderOrAccount");

    /**
     * Selected conversations, if any.
     */
    private final ConversationCheckedSet mCheckedSet = new ConversationCheckedSet();

    private final int mFolderItemUpdateDelayMs;

    /** Keeps track of selected and unselected conversations */
    final protected ConversationPositionTracker mTracker;

    /**
     * Action menu associated with the selected set.
     */
    SelectedConversationsActionMenu mCabActionMenu;

    /** The compose button floating over the conversation/search lists */
    protected View mFloatingComposeButton;
    protected ActionableToastBar mToastBar;
    protected ConversationPagerController mPagerController;

    // This is split out from the general loader dispatcher because its loader doesn't return a
    // basic Cursor
    /** Handles loader callbacks to create a convesation cursor. */
    private final ConversationListLoaderCallbacks mListCursorCallbacks =
            new ConversationListLoaderCallbacks();

    /** Object that listens to all LoaderCallbacks that result in {@link Folder} creation. */
    private final FolderLoads mFolderCallbacks = new FolderLoads();
    /** Object that listens to all LoaderCallbacks that result in {@link Account} creation. */
    private final AccountLoads mAccountCallbacks = new AccountLoads();

    /**
     * Matched addresses that must be shielded from users because they are temporary. Even though
     * this is instantiated from settings, this matcher is valid for all accounts, and is expected
     * to live past the life of an account.
     */
    private final VeiledAddressMatcher mVeiledMatcher;

    protected static final String LOG_TAG = LogTag.getLogTag();

    // Loader constants: Accounts
    /**
     * The list of accounts. This loader is started early in the application life-cycle since
     * the list of accounts is central to all other data the application needs: unread counts for
     * folders, critical UI settings like show/hide checkboxes, ...
     * The loader is started when the application is created: both in
     * {@link #onCreate(Bundle)} and in {@link #onActivityResult(int, int, Intent)}. It is never
     * destroyed since the cursor is needed through the life of the application. When the list of
     * accounts changes, we notify {@link #mAllAccountObservers}.
     */
    private static final int LOADER_ACCOUNT_CURSOR = 0;

    /**
     * The current account. This loader is started when we have an account. The mail application
     * <b>needs</b> a valid account to function. As soon as we set {@link #mAccount},
     * we start a loader to observe for changes on the current account.
     * The loader is always restarted when an account is set in {@link #setAccount(Account)}.
     * When the current account object changes, we notify {@link #mAccountObservers}.
     * A possible performance improvement would be to listen purely on
     * {@link #LOADER_ACCOUNT_CURSOR}. The current account is guaranteed to be in the list,
     * and would avoid two updates when a single setting on the current account changes.
     */
    private static final int LOADER_ACCOUNT_UPDATE_CURSOR = 1;

    // Loader constants: Conversations

    /** The conversation cursor over the current conversation list. This loader provides
     * a cursor over conversation entries from a folder to display a conversation
     * list.
     * This loader is started when the user switches folders (in {@link #updateFolder(Folder)},
     * or when the controller is told that a folder/account change is imminent
     * (in {@link #preloadConvList(Account, Folder)}. The loader is maintained for the life of
     * the current folder. When the user switches folders, the old loader is destroyed and a new
     * one is created.
     *
     * When the conversation list changes, we notify {@link #mConversationListObservable}.
     */
    private static final int LOADER_CONVERSATION_LIST = 10;

    // Loader constants: misc
    /**
     * The loader that determines whether the Warm welcome tour should be displayed for the user.
     */
    public static final int LOADER_WELCOME_TOUR = 20;

    /**
     * The load which loads accounts for the welcome tour.
     */
    public static final int LOADER_WELCOME_TOUR_ACCOUNTS = 21;

    // Loader constants: Folders

    /** The current folder. This loader watches for updates to the current folder in a manner
     * analogous to the {@link #LOADER_ACCOUNT_UPDATE_CURSOR}. Updates to the current folder
     * might be due to server-side changes (unread count), or local changes (sync window or sync
     * status change).
     * The change of current folder calls {@link #updateFolder(Folder)}.
     * This is responsible for restarting a loader using the URI of the provided folder. When the
     * loader returns, the current folder is updated and consumers, if any, are notified.
     * When the current folder changes, we notify {@link #mFolderObservable}
     */
    private static final int LOADER_FOLDER_CURSOR = 30;

    /**
     * The list of recent folders. Recent folders are shown in the DrawerFragment. The recent
     * folders are tied to the current account being viewed. When the account is changed,
     * we restart this loader to retrieve the recent accounts. Recents are pre-populated for
     * phones historically, when they were displayed in the spinner. On the tablet,
     * they showed in the {@link FolderListFragment} and were not-populated.  The code to
     * pre-populate the recents is somewhat convoluted: when the loader returns a short list of
     * recent folders, it issues an update on the Recent Folder URI. The underlying provider then
     * does the appropriate thing to populate recent folders, and notify of a change on the cursor.
     * Recent folders are needed for the life of the current account.
     * When the recent folders change, we notify {@link #mRecentFolderObservers}.
     */
    private static final int LOADER_RECENT_FOLDERS = 31;
    /**
     * The primary inbox for the current account. The mechanism to load the default inbox for the
     * current account is (sadly) different from loading other folders. The method
     * {@link #loadAccountInbox()} is called, and it restarts this loader. When the loader returns
     * a valid cursor, we create a folder, call {@link #onFolderChanged{Folder)} eventually
     * calling {@link #updateFolder(Folder)} which starts a loader {@link #LOADER_FOLDER_CURSOR}
     * over the current folder.
     * When we have a valid cursor, we destroy this loader, This convoluted flow is historical.
     */
    private static final int LOADER_ACCOUNT_INBOX = 32;

    /**
     * The fake folder of search results for a term. When we search for a term,
     * a new activity is created with {@link Intent#ACTION_SEARCH}. For this new activity,
     * we start a loader which returns conversations that match the user-provided query.
     * We destroy the loader when we obtain a valid cursor since subsequent searches will create
     * a new activity.
     */
    private static final int LOADER_SEARCH = 33;
    /**
     * The initial folder at app start. When the application is launched from an intent that
     * specifies the initial folder (notifications/widgets/shortcuts),
     * then we extract the folder URI from the intent, but we cannot trust the folder object. Since
     * shortcuts and widgets persist past application update, they might have incorrect
     * information encoded in them. So, to obtain a {@link Folder} object from a {@link Uri},
     * we need to start another loader. Upon obtaining a valid cursor, the loader is destroyed.
     * An additional complication arises if we have to view a specific conversation within this
     * folder. This is the case when launching the app from a single conversation notification
     * or tapping on a specific conversation in the widget. In these cases, the conversation is
     * saved in {@link #mConversationToShow} and is retrieved when the loader returns.
     */
    public static final int LOADER_FIRST_FOLDER = 34;

    /**
     * Guaranteed to be the last loader ID used by the activity. Loaders are owned by Activity or
     * fragments, and within an activity, loader IDs need to be unique. A hack to ensure that the
     * {@link FolderWatcher} can create its folder loaders without clashing with the IDs of those
     * of the {@link AbstractActivityController}. Currently, the {@link FolderWatcher} is the only
     * other class that uses this activity's LoaderManager. If another class needs activity-level
     * loaders, consider consolidating the loaders in a central location: a UI-less fragment
     * perhaps.
     */
    public static final int LAST_LOADER_ID = 35;

    /**
     * Guaranteed to be the last loader ID used by the Fragment. Loaders are owned by Activity or
     * fragments, and within an activity, loader IDs need to be unique. Currently,
     * SectionedInboxTeaserView is the only class that uses the
     * {@link ConversationListFragment}'s LoaderManager.
     */
    public static final int LAST_FRAGMENT_LOADER_ID = 1000;

    /** Code returned after an account has been added. */
    private static final int ADD_ACCOUNT_REQUEST_CODE = 1;
    /** Code returned when the user has to enter the new password on an existing account. */
    private static final int REAUTHENTICATE_REQUEST_CODE = 2;
    /** Code returned when the previous activity needs to navigate to a different folder
     *  or account */
    private static final int CHANGE_NAVIGATION_REQUEST_CODE = 3;

    /** Code returned from voice search intent */
    public static final int VOICE_SEARCH_REQUEST_CODE = 4;

    public static final String EXTRA_FOLDER = "extra-folder";
    public static final String EXTRA_ACCOUNT = "extra-account";

    /** The pending destructive action to be carried out before swapping the conversation cursor.*/
    private DestructiveAction mPendingDestruction;
    protected AsyncRefreshTask mFolderSyncTask;
    private Folder mFolderListFolder;
    private final int mShowUndoBarDelay;
    private boolean mRecentsDataUpdated;
    /** A wait fragment we added, if any. */
    private WaitFragment mWaitFragment;
    /** True if we have results from a search query */
    protected boolean mHaveSearchResults = false;
    /** If a confirmation dialog is being show, the listener for the positive action. */
    private OnClickListener mDialogListener;
    /**
     * If a confirmation dialog is being show, the resource of the action: R.id.delete, etc.  This
     * is used to create a new {@link #mDialogListener} on orientation changes.
     */
    private int mDialogAction = -1;
    /**
     * If a confirmation dialog is being shown, this is true if the dialog acts on the selected set
     * and false if it acts on the currently selected conversation
     */
    private boolean mDialogFromSelectedSet;

    /** Which conversation to show, if started from widget/notification. */
    private Conversation mConversationToShow = null;

    /**
     * A temporary reference to the pending destructive action that was deferred due to an
     * auto-advance transition in progress.
     * <p>
     * In detail: when auto-advance triggers a mode change, we must wait until the transition
     * completes before executing the destructive action to ensure a smooth mode change transition.
     * This member variable houses the pending destructive action work to be run upon completion.
     */
    private Runnable mAutoAdvanceOp = null;

    protected DrawerLayout mDrawerContainer;
    protected View mDrawerPullout;
    protected ActionBarDrawerToggle mDrawerToggle;

    protected ListView mListViewForAnimating;
    protected boolean mHasNewAccountOrFolder;
    private boolean mConversationListLoadFinishedIgnored;
    private final MailDrawerListener mDrawerListener = new MailDrawerListener();
    private boolean mHideMenuItems;

    private final DrawIdler mDrawIdler = new DrawIdler();

    public static final String SYNC_ERROR_DIALOG_FRAGMENT_TAG = "SyncErrorDialogFragment";

    private final DataSetObserver mUndoNotificationObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            super.onChanged();

            if (mConversationListCursor != null) {
                mConversationListCursor.handleNotificationActions();
            }
        }
    };

    private final HomeButtonListener mHomeButtonListener = new HomeButtonListener();

    public AbstractActivityController(MailActivity activity, ViewMode viewMode) {
        mActivity = activity;
        mFragmentManager = mActivity.getFragmentManager();
        mViewMode = viewMode;
        mContext = activity.getApplicationContext();
        mRecentFolderList = new RecentFolderList(mContext);
        mTracker = new ConversationPositionTracker(this);
        // Allow the fragment to observe changes to its own selection set. No other object is
        // aware of the selected set.
        mCheckedSet.addObserver(this);

        final Resources r = mContext.getResources();
        mFolderItemUpdateDelayMs = r.getInteger(R.integer.folder_item_refresh_delay_ms);
        mShowUndoBarDelay = r.getInteger(R.integer.show_undo_bar_delay_ms);
        mVeiledMatcher = VeiledAddressMatcher.newInstance(activity.getResources());
        mIsTablet = Utils.useTabletUI(r);
        mConversationListLoadFinishedIgnored = false;
    }

    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.append("{");
        sb.append("mCurrentConversation=");
        sb.append(mCurrentConversation);
        appendToString(sb);
        sb.append("}");
        return sb.toString();
    }

    protected void appendToString(StringBuilder sb) {}

    public Account getCurrentAccount() {
        return mAccount;
    }

    public ConversationListContext getCurrentListContext() {
        return mConvListContext;
    }

    @Override
    public final ConversationCursor getConversationListCursor() {
        return mConversationListCursor;
    }

    /**
     * Check if the fragment is attached to an activity and has a root view.
     * @param in fragment to be checked
     * @return true if the fragment is valid, false otherwise
     */
    private static boolean isValidFragment(Fragment in) {
        return !(in == null || in.getActivity() == null || in.getView() == null);
    }

    /**
     * Get the conversation list fragment for this activity. If the conversation list fragment is
     * not attached, this method returns null.
     *
     * Caution! This method returns the {@link ConversationListFragment} after the fragment has been
     * added, <b>and</b> after the {@link FragmentManager} has run through its queue to add the
     * fragment. There is a non-trivial amount of time after the fragment is instantiated and before
     * this call returns a non-null value, depending on the {@link FragmentManager}. If you
     * need the fragment immediately after adding it, consider making the fragment an observer of
     * the controller and perform the task immediately on {@link Fragment#onActivityCreated(Bundle)}
     */
    protected ConversationListFragment getConversationListFragment() {
        final Fragment fragment = mFragmentManager.findFragmentByTag(TAG_CONVERSATION_LIST);
        if (isValidFragment(fragment)) {
            return (ConversationListFragment) fragment;
        }
        return null;
    }

    /**
     * Returns the folder list fragment attached with this activity. If no such fragment is attached
     * this method returns null.
     *
     * Caution! This method returns the {@link FolderListFragment} after the fragment has been
     * added, <b>and</b> after the {@link FragmentManager} has run through its queue to add the
     * fragment. There is a non-trivial amount of time after the fragment is instantiated and before
     * this call returns a non-null value, depending on the {@link FragmentManager}. If you
     * need the fragment immediately after adding it, consider making the fragment an observer of
     * the controller and perform the task immediately on {@link Fragment#onActivityCreated(Bundle)}
     */
    protected FolderListFragment getFolderListFragment() {
        final String drawerPulloutTag = mActivity.getString(R.string.drawer_pullout_tag);
        final Fragment fragment = mFragmentManager.findFragmentByTag(drawerPulloutTag);
        if (isValidFragment(fragment)) {
            return (FolderListFragment) fragment;
        }
        return null;
    }

    /**
     * Initialize the action bar. This is not visible to OnePaneController and
     * TwoPaneController so they cannot override this behavior.
     */
    private void initializeActionBar() {
        final ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar == null) {
            return;
        }

        mActionBarController = new ActionBarController(mContext);
        mActionBarController.initialize(mActivity, this, actionBar);
        actionBar.setShowHideAnimationEnabled(false);

        // init the action bar to allow the 'up' affordance.
        // any configurations that disallow 'up' should do that later.
        mActionBarController.setBackButton();
    }

    /**
     * Attach the action bar to the activity.
     */
    private void attachActionBar() {
        final ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            // Show a title
            final int mask = ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_HOME;
            actionBar.setDisplayOptions(mask, mask);
            mActionBarController.setViewModeController(mViewMode);
        }
    }

    /**
     * Returns whether the conversation list fragment is visible or not.
     * Different layouts will have their own notion on the visibility of
     * fragments, so this method needs to be overriden.
     *
     */
    protected abstract boolean isConversationListVisible();

    /**
     * If required, starts wait mode for the current account.
     */
    final void perhapsEnterWaitMode() {
        // If the account is not initialized, then show the wait fragment, since nothing can be
        // shown.
        if (mAccount.isAccountInitializationRequired()) {
            showWaitForInitialization();
            return;
        }

        final boolean inWaitingMode = inWaitMode();
        final boolean isSyncRequired = mAccount.isAccountSyncRequired();
        if (isSyncRequired) {
            if (inWaitingMode) {
                // Update the WaitFragment's account object
                updateWaitMode();
            } else {
                // Transition to waiting mode
                showWaitForInitialization();
            }
        } else if (inWaitingMode) {
            // Dismiss waiting mode
            hideWaitForInitialization();
        }
    }

    @Override
    public void switchToDefaultInboxOrChangeAccount(Account account) {
        LogUtils.d(LOG_TAG, "AAC.switchToDefaultAccount(%s)", account);
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
        final boolean firstLoad = mAccount == null;
        final boolean switchToDefaultInbox = !firstLoad && account.uri.equals(mAccount.uri);
        // If the active account has been clicked in the drawer, go to default inbox
        if (switchToDefaultInbox) {
            loadAccountInbox();
            return;
        }
        changeAccount(account);
    }

    public void changeAccount(Account account) {
        LogUtils.d(LOG_TAG, "AAC.changeAccount(%s)", account);
        // Is the account or account settings different from the existing account?
        final boolean firstLoad = mAccount == null;
        final boolean accountChanged = firstLoad || !account.uri.equals(mAccount.uri);

        // If nothing has changed, return early without wasting any more time.
        if (!accountChanged && !account.settingsDiffer(mAccount)) {
            return;
        }
        // We also don't want to do anything if the new account is null
        if (account == null) {
            LogUtils.e(LOG_TAG, "AAC.changeAccount(null) called.");
            return;
        }
        final String emailAddress = account.getEmailAddress();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                MailActivity.setNfcMessage(emailAddress);
            }
        });
        if (accountChanged) {
            commitDestructiveActions(false);
        }

        // Change the account here
        setAccount(account);
        // And carry out associated actions.
        cancelRefreshTask();
        if (accountChanged) {
            loadAccountInbox();
        }
        // Check if we need to force setting up an account before proceeding.
        if (mAccount != null && !Uri.EMPTY.equals(mAccount.settings.setupIntentUri)) {
            // Launch the intent!
            final Intent intent = new Intent(Intent.ACTION_EDIT);

            intent.setPackage(mContext.getPackageName());
            intent.setData(mAccount.settings.setupIntentUri);

            mActivity.startActivity(intent);
        }
    }

    /**
     * Adds a listener interested in change in the current account. If a class is storing a
     * reference to the current account, it should listen on changes, so it can receive updates to
     * settings. Must happen in the UI thread.
     */
    @Override
    public void registerAccountObserver(DataSetObserver obs) {
        mAccountObservers.registerObserver(obs);
    }

    /**
     * Removes a listener from receiving current account changes.
     * Must happen in the UI thread.
     */
    @Override
    public void unregisterAccountObserver(DataSetObserver obs) {
        mAccountObservers.unregisterObserver(obs);
    }

    @Override
    public void registerAllAccountObserver(DataSetObserver observer) {
        mAllAccountObservers.registerObserver(observer);
    }

    @Override
    public void unregisterAllAccountObserver(DataSetObserver observer) {
        mAllAccountObservers.unregisterObserver(observer);
    }

    @Override
    public Account[] getAllAccounts() {
        return mAllAccounts;
    }

    @Override
    public Account getAccount() {
        return mAccount;
    }

    @Override
    public void registerFolderOrAccountChangedObserver(final DataSetObserver observer) {
        mFolderOrAccountObservers.registerObserver(observer);
    }

    @Override
    public void unregisterFolderOrAccountChangedObserver(final DataSetObserver observer) {
        mFolderOrAccountObservers.unregisterObserver(observer);
    }

    /**
     * If the drawer is open, the function locks the drawer to the closed, thereby sliding in
     * the drawer to the left edge, disabling events, and refreshing it once it's either closed
     * or put in an idle state.
     */
    @Override
    public void closeDrawer(final boolean hasNewFolderOrAccount, Account nextAccount,
            Folder nextFolder) {
        if (!isDrawerEnabled()) {
            if (hasNewFolderOrAccount) {
                mFolderOrAccountObservers.notifyChanged();
            }
            return;
        }
        // If there are no new folders or accounts to switch to, just close the drawer
        if (!hasNewFolderOrAccount) {
            mDrawerContainer.closeDrawers();
            return;
        }
        // Otherwise, start preloading the conversation list for the new folder.
        if (nextFolder != null) {
            preloadConvList(nextAccount, nextFolder);
        }
        // Remember if the conversation list view is animating
        final ConversationListFragment conversationList = getConversationListFragment();
        if (conversationList != null) {
            mListViewForAnimating = conversationList.getListView();
        } else {
            // There is no conversation list to animate, so just set it to null
            mListViewForAnimating = null;
        }

        if (mDrawerContainer.isDrawerOpen(mDrawerPullout)) {
            // Lets the drawer listener update the drawer contents and notify the FolderListFragment
            mHasNewAccountOrFolder = true;
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            // Drawer is already closed, notify observers that is the case.
            if (hasNewFolderOrAccount) {
                mFolderOrAccountObservers.notifyChanged();
            }
        }
    }

    /**
     * Load the conversation list early for the given folder. This happens when some UI element
     * (usually the drawer) instructs the controller that an account change or folder change is
     * imminent. While the UI element is animating, the controller can preload the conversation
     * list for the default inbox of the account provided here or to the folder provided here.
     *
     * @param nextAccount The account which the app will switch to shortly, possibly null.
     * @param nextFolder The folder which the app will switch to shortly, possibly null.
     */
    protected void preloadConvList(Account nextAccount, Folder nextFolder) {
        // Fire off the conversation list loader for this account already with a fake
        // listener.
        final Bundle args = new Bundle(2);
        if (nextAccount != null) {
            args.putParcelable(BUNDLE_ACCOUNT_KEY, nextAccount);
        } else {
            args.putParcelable(BUNDLE_ACCOUNT_KEY, mAccount);
        }
        if (nextFolder != null) {
            args.putParcelable(BUNDLE_FOLDER_KEY, nextFolder);
        } else {
            LogUtils.e(LOG_TAG, new Error(), "AAC.preloadConvList(): Got an empty folder");
        }
        mFolder = null;
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.destroyLoader(LOADER_CONVERSATION_LIST);
        lm.initLoader(LOADER_CONVERSATION_LIST, args, mListCursorCallbacks);
    }

    /**
     * Initiates the async request to create a fake search folder, which returns conversations that
     * match the query term provided by the user. Returns immediately.
     * @param intent Intent that the app was started with. This intent contains the search query.
     */
    private void fetchSearchFolder(Intent intent) {
        final Bundle args = new Bundle(1);
        args.putString(ConversationListContext.EXTRA_SEARCH_QUERY, intent
                .getStringExtra(ConversationListContext.EXTRA_SEARCH_QUERY));
        mActivity.getLoaderManager().restartLoader(LOADER_SEARCH, args, mFolderCallbacks);
    }

    protected void onFolderChanged(Folder folder, final boolean force) {
        if (isDrawerEnabled()) {
            /** If the folder doesn't exist, or its parent URI is empty,
             * this is not a child folder */
            final boolean isTopLevel = Folder.isRoot(folder);
            final int mode = mViewMode.getMode();
            updateDrawerIndicator(mode, isTopLevel);
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

            mDrawerContainer.closeDrawers();
        }

        if (mFolder == null || !mFolder.equals(folder)) {
            // We are actually changing the folder, so exit cab mode
            exitCabMode();
        }

        final String query;
        if (folder != null && folder.isType(FolderType.SEARCH)) {
            query = mConvListContext.searchQuery;
        } else {
            query = null;
        }

        changeFolder(folder, query, force);
    }

    /**
     * Sets the folder state without changing view mode and without creating a list fragment, if
     * possible.
     * @param folder the folder whose list of conversations are to be shown
     * @param query the query string for a list of conversations matching a search
     */
    private void setListContext(Folder folder, String query) {
        updateFolder(folder);
        if (query != null) {
            mConvListContext = ConversationListContext.forSearchQuery(mAccount, mFolder, query);
        } else {
            mConvListContext = ConversationListContext.forFolder(mAccount, mFolder);
        }
        cancelRefreshTask();
    }

    /**
     * Changes the folder to the value provided here. This causes the view mode to change.
     * @param folder the folder to change to
     * @param query if non-null, this represents the search string that the folder represents.
     * @param force <code>true</code> to force a folder change, <code>false</code> to disallow
     *          changing to the current folder
     */
    private void changeFolder(Folder folder, String query, final boolean force) {
        if (!Objects.equal(mFolder, folder)) {
            commitDestructiveActions(false);
        }
        if (folder != null && (!folder.equals(mFolder) || force)
                || (mViewMode.getMode() != ViewMode.CONVERSATION_LIST)) {
            setListContext(folder, query);
            showConversationList(mConvListContext);
            // Touch the current folder: it is different, and it has been accessed.
            if (mFolder != null) {
                mRecentFolderList.touchFolder(mFolder, mAccount);
            }
        }
        resetActionBarIcon();
    }

    @Override
    public void onFolderSelected(Folder folder) {
        onFolderChanged(folder, false /* force */);
    }

    /**
     * Adds a listener interested in change in the recent folders. If a class is storing a
     * reference to the recent folders, it should listen on changes, so it can receive updates.
     * Must happen in the UI thread.
     */
    @Override
    public void registerRecentFolderObserver(DataSetObserver obs) {
        mRecentFolderObservers.registerObserver(obs);
    }

    /**
     * Removes a listener from receiving recent folder changes.
     * Must happen in the UI thread.
     */
    @Override
    public void unregisterRecentFolderObserver(DataSetObserver obs) {
        mRecentFolderObservers.unregisterObserver(obs);
    }

    @Override
    public RecentFolderList getRecentFolders() {
        return mRecentFolderList;
    }

    /**
     * Load the default inbox associated with the current account.
     */
    protected void loadAccountInbox() {
        boolean handled = false;
        if (mFolderWatcher != null) {
            final Folder inbox = mFolderWatcher.getDefaultInbox(mAccount);
            if (inbox != null) {
                onFolderChanged(inbox, false /* force */);
                handled = true;
            }
        }
        if (!handled) {
            LogUtils.d(LOG_TAG, "Starting a LOADER_ACCOUNT_INBOX for %s", mAccount);
            restartOptionalLoader(LOADER_ACCOUNT_INBOX, mFolderCallbacks, Bundle.EMPTY);
        }
        final int mode = mViewMode.getMode();
        if (mode == ViewMode.UNKNOWN || mode == ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION) {
            mViewMode.enterConversationListMode();
        }
    }

    @Override
    public void setFolderWatcher(FolderWatcher watcher) {
        mFolderWatcher = watcher;
    }

    /**
     * Marks the {@link #mFolderChanged} value if the newFolder is different from the existing
     * {@link #mFolder}. This should be called immediately <b>before</b> assigning newFolder to
     * mFolder.
     * @param newFolder the new folder we are switching to.
     */
    private void setHasFolderChanged(final Folder newFolder) {
        // We should never try to assign a null folder. But in the rare event that we do, we should
        // only set the bit when we have a valid folder, and null is not valid.
        if (newFolder == null) {
            return;
        }
        // If the previous folder was null, or if the two folders represent different data, then we
        // consider that the folder has changed.
        if (mFolder == null || !newFolder.equals(mFolder)) {
            mFolderChanged = true;
        }
    }

    /**
     * Sets the current folder if it is different from the object provided here. This method does
     * NOT notify the folder observers that a change has happened. Observers are notified when we
     * get an updated folder from the loaders, which will happen as a consequence of this method
     * (since this method starts/restarts the loaders).
     * @param folder The folder to assign
     */
    private void updateFolder(Folder folder) {
        if (folder == null || !folder.isInitialized()) {
            LogUtils.e(LOG_TAG, new Error(), "AAC.setFolder(%s): Bad input", folder);
            return;
        }
        if (folder.equals(mFolder)) {
            LogUtils.d(LOG_TAG, "AAC.setFolder(%s): Input matches mFolder", folder);
            return;
        }
        final boolean wasNull = mFolder == null;
        LogUtils.d(LOG_TAG, "AbstractActivityController.setFolder(%s)", folder.name);
        final LoaderManager lm = mActivity.getLoaderManager();
        // updateFolder is called from AAC.onLoadFinished() on folder changes.  We need to
        // ensure that the folder is different from the previous folder before marking the
        // folder changed.
        setHasFolderChanged(folder);
        mFolder = folder;

        // We do not need to notify folder observers yet. Instead we start the loaders and
        // when the load finishes, we will get an updated folder. Then, we notify the
        // folderObservers in onLoadFinished.
        mActionBarController.setFolder(mFolder);

        // Only when we switch from one folder to another do we want to restart the
        // folder and conversation list loaders (to trigger onCreateLoader).
        // The first time this runs when the activity is [re-]initialized, we want to re-use the
        // previous loader's instance and data upon configuration change (e.g. rotation).
        // If there was not already an instance of the loader, init it.
        if (lm.getLoader(LOADER_FOLDER_CURSOR) == null) {
            lm.initLoader(LOADER_FOLDER_CURSOR, Bundle.EMPTY, mFolderCallbacks);
        } else {
            lm.restartLoader(LOADER_FOLDER_CURSOR, Bundle.EMPTY, mFolderCallbacks);
        }
        if (!wasNull && lm.getLoader(LOADER_CONVERSATION_LIST) != null) {
            // If there was an existing folder AND we have changed
            // folders, we want to restart the loader to get the information
            // for the newly selected folder
            lm.destroyLoader(LOADER_CONVERSATION_LIST);
        }
        final Bundle args = new Bundle(2);
        args.putParcelable(BUNDLE_ACCOUNT_KEY, mAccount);
        args.putParcelable(BUNDLE_FOLDER_KEY, mFolder);
        args.putBoolean(BUNDLE_IGNORE_INITIAL_CONVERSATION_LIMIT_KEY,
                mIgnoreInitialConversationLimit);
        mIgnoreInitialConversationLimit = false;
        lm.initLoader(LOADER_CONVERSATION_LIST, args, mListCursorCallbacks);
    }

    @Override
    public Folder getFolder() {
        return mFolder;
    }

    @Override
    public Folder getHierarchyFolder() {
        return mFolderListFolder;
    }

    /**
     * Set the folder currently selected in the folder selection hierarchy fragments.
     */
    protected void setHierarchyFolder(Folder folder) {
        mFolderListFolder = folder;
    }

    /**
     * The mail activity calls other activities for two specific reasons:
     * <ul>
     *     <li>To add an account. And receives the result {@link #ADD_ACCOUNT_REQUEST_CODE}</li>
     *     <li>To update the password on a current account. The result {@link
     *     #REAUTHENTICATE_REQUEST_CODE} is received.</li>
     * </ul>
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ADD_ACCOUNT_REQUEST_CODE:
                // We were waiting for the user to create an account
                if (resultCode == Activity.RESULT_OK) {
                    // restart the loader to get the updated list of accounts
                    mActivity.getLoaderManager().initLoader(LOADER_ACCOUNT_CURSOR, Bundle.EMPTY,
                            mAccountCallbacks);
                } else {
                    // The user failed to create an account, just exit the app
                    mActivity.finish();
                }
                break;
            case REAUTHENTICATE_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // The user successfully authenticated, attempt to refresh the list
                    final Uri refreshUri = mFolder != null ? mFolder.refreshUri : null;
                    if (refreshUri != null) {
                        startAsyncRefreshTask(refreshUri);
                    }
                }
                break;
            case CHANGE_NAVIGATION_REQUEST_CODE:
                if (ViewMode.isSearchMode(mViewMode.getMode())) {
                    mActivity.setResult(resultCode, data);
                    mActivity.finish();
                } else if (resultCode == Activity.RESULT_OK && data != null) {
                    // We have have received a result that indicates we need to navigate to a
                    // different folder or account. This happens if someone navigates using the
                    // drawer on the search results activity.
                    final Folder folder = data.getParcelableExtra(EXTRA_FOLDER);
                    final Account account = data.getParcelableExtra(EXTRA_ACCOUNT);
                    if (folder != null) {
                        onFolderSelected(folder);
                        mViewMode.enterConversationListMode();
                    } else if (account != null) {
                        switchToDefaultInboxOrChangeAccount(account);
                        mViewMode.enterConversationListMode();
                    }
                }
                break;
            case VOICE_SEARCH_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    final ArrayList<String> matches = data.getStringArrayListExtra(
                            RecognizerIntent.EXTRA_RESULTS);
                    if (!matches.isEmpty()) {
                        // not sure how dependable the API is, but it's all we have.
                        // take the top choice.
                        mSearchViewController.onSearchPerformed(matches.get(0));
                    }
                }
                break;
        }
    }

    /**
     * Inform the conversation cursor that there has been a visibility change.
     * @param visible true if the conversation list is visible, false otherwise.
     */
    protected synchronized void informCursorVisiblity(boolean visible) {
        if (mConversationListCursor != null) {
            Utils.setConversationCursorVisibility(mConversationListCursor, visible, mFolderChanged);
            // We have informed the cursor. Subsequent visibility changes should not tell it that
            // the folder has changed.
            mFolderChanged = false;
        }
    }

    @Override
    public void onConversationListVisibilityChanged(boolean visible) {
        mFloatingComposeButton.setVisibility(
                !ViewMode.isSearchMode(mViewMode.getMode()) && visible ? View.VISIBLE : View.GONE);

        informCursorVisiblity(visible);
        commitAutoAdvanceOperation();

        // Notify special views
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null && convListFragment.getAnimatedAdapter() != null) {
            convListFragment.getAnimatedAdapter().onConversationListVisibilityChanged(visible);
        }
    }

    /**
     * Called when a conversation is visible. Child classes must call the super class implementation
     * before performing local computation.
     */
    @Override
    public void onConversationVisibilityChanged(boolean visible) {
        commitAutoAdvanceOperation();
    }

    /**
     * Commits any pending destructive action that was earlier deferred by an auto-advance
     * mode-change transition.
     */
    private void commitAutoAdvanceOperation() {
        if (mAutoAdvanceOp != null) {
            mAutoAdvanceOp.run();
            mAutoAdvanceOp = null;
        }
    }

    /**
     * Initialize development time logging. This can potentially log a lot of PII, and we don't want
     * to turn it on for shipped versions.
     */
    private void initializeDevLoggingService() {
        if (!MailLogService.DEBUG_ENABLED) {
            return;
        }
        // Check every 5 minutes.
        final int WAIT_TIME = 5 * 60 * 1000;
        // Start a runnable that periodically checks the log level and starts/stops the service.
        mLogServiceChecker = new Runnable() {
            /** True if currently logging. */
            private boolean mCurrentlyLogging = false;

            /**
             * If the logging level has been changed since the previous run, start or stop the
             * service.
             */
            private void startOrStopService() {
                // If the log level is already high, start the service.
                final Intent i = new Intent(mContext, MailLogService.class);
                final boolean loggingEnabled = MailLogService.isLoggingLevelHighEnough();
                if (mCurrentlyLogging == loggingEnabled) {
                    // No change since previous run, just return;
                    return;
                }
                if (loggingEnabled) {
                    LogUtils.e(LOG_TAG, "Starting MailLogService");
                    mContext.startService(i);
                } else {
                    LogUtils.e(LOG_TAG, "Stopping MailLogService");
                    mContext.stopService(i);
                }
                mCurrentlyLogging = loggingEnabled;
            }

            @Override
            public void run() {
                startOrStopService();
                mHandler.postDelayed(this, WAIT_TIME);
            }
        };
        // Start the runnable right away.
        mHandler.post(mLogServiceChecker);
    }

    /**
     * The application can be started from the following entry points:
     * <ul>
     *     <li>Launcher: you tap on the Gmail icon in the launcher. This is what most users think of
     *         as “Starting the app”.</li>
     *     <li>Shortcut: Users can make a shortcut to take them directly to a label.</li>
     *     <li>Widget: Shows the contents of a synced label, and allows:
     *     <ul>
     *         <li>Viewing the list (tapping on the title)</li>
     *         <li>Composing a new message (tapping on the new message icon in the title. This
     *         launches the {@link ComposeActivity}.
     *         </li>
     *         <li>Viewing a single message (tapping on a list element)</li>
     *     </ul>
     *
     *     </li>
     *     <li>Tapping on a notification:
     *     <ul>
     *         <li>Shows message list if more than one message</li>
     *         <li>Shows the conversation if the notification is for a single message</li>
     *     </ul>
     *     </li>
     *     <li>...and most importantly, the activity life cycle can tear down the application and
     *     restart it:
     *     <ul>
     *         <li>Rotate the application: it is destroyed and recreated.</li>
     *         <li>Navigate away, and return from recent applications.</li>
     *     </ul>
     *     </li>
     *     <li>Add a new account: fires off an intent to add an account,
     *     and returns in {@link #onActivityResult(int, int, android.content.Intent)} .</li>
     *     <li>Re-authenticate your account: again returns in onActivityResult().</li>
     *     <li>Composing can happen from many entry points: third party applications fire off an
     *     intent to compose email, and launch directly into the {@link ComposeActivity}
     *     .</li>
     * </ul>
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedState) {
        initializeActionBar();
        initializeDevLoggingService();
        // Allow shortcut keys to function for the ActionBar and menus.
        mActivity.setDefaultKeyMode(Activity.DEFAULT_KEYS_SHORTCUT);
        mResolver = mActivity.getContentResolver();
        mNewEmailReceiver = new SuppressNotificationReceiver();
        mRecentFolderList.initialize(mActivity);
        mVeiledMatcher.initialize(this);

        mFloatingComposeButton = mActivity.findViewById(R.id.compose_button);
        mFloatingComposeButton.setOnClickListener(this);

        if (isDrawerEnabled()) {
            mDrawerToggle = new ActionBarDrawerToggle(mActivity, mDrawerContainer,
                    R.string.drawer_open, R.string.drawer_close);
            mDrawerContainer.setDrawerListener(mDrawerListener);
            mDrawerContainer.setDrawerShadow(
                    mContext.getResources().getDrawable(R.drawable.drawer_shadow), Gravity.START);

            // Disable default drawer indicator as we are setting the drawer indicator icons.
            // TODO(shahrk): Once we can disable/enable drawer animation, go back to using
            // drawer indicators.
            mDrawerToggle.setDrawerIndicatorEnabled(false);
            mDrawerToggle.setHomeAsUpIndicator(R.drawable.ic_menu_wht_24dp);
        } else {
            final ActionBar ab = mActivity.getSupportActionBar();
            ab.setHomeAsUpIndicator(R.drawable.ic_menu_wht_24dp);
            ab.setHomeActionContentDescription(R.string.drawer_open);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        // All the individual UI components listen for ViewMode changes. This
        // simplifies the amount of logic in the AbstractActivityController, but increases the
        // possibility of timing-related bugs.
        mViewMode.addListener(this);
        mPagerController = new ConversationPagerController(mActivity, this);
        mToastBar = findActionableToastBar(mActivity);
        attachActionBar();

        mDrawIdler.setRootView(mActivity.getWindow().getDecorView());

        final Intent intent = mActivity.getIntent();

        mSearchViewController = new MaterialSearchViewController(mActivity, this, intent,
                savedState);
        addConversationListLayoutListener(mSearchViewController);

        // Immediately handle a clean launch with intent, and any state restoration
        // that does not rely on restored fragments or loader data
        // any state restoration that relies on those can be done later in
        // onRestoreInstanceState, once fragments are up and loader data is re-delivered
        if (savedState != null) {
            if (savedState.containsKey(SAVED_ACCOUNT)) {
                setAccount((Account) savedState.getParcelable(SAVED_ACCOUNT));
            }
            if (savedState.containsKey(SAVED_FOLDER)) {
                final Folder folder = savedState.getParcelable(SAVED_FOLDER);
                final String query = savedState.getString(SAVED_QUERY, null);
                setListContext(folder, query);
            }
            if (savedState.containsKey(SAVED_ACTION)) {
                mDialogAction = savedState.getInt(SAVED_ACTION);
            }
            mDialogFromSelectedSet = savedState.getBoolean(SAVED_ACTION_FROM_SELECTED, false);
            mViewMode.handleRestore(savedState);
        } else if (intent != null) {
            handleIntent(intent);
        }
        // Create the accounts loader; this loads the account switch spinner.
        mActivity.getLoaderManager().initLoader(LOADER_ACCOUNT_CURSOR, Bundle.EMPTY,
                mAccountCallbacks);
    }

    /**
     * @param activity the activity that has been inflated
     * @return the Actionable Toast Bar defined within the activity
     */
    protected ActionableToastBar findActionableToastBar(MailActivity activity) {
        return (ActionableToastBar) activity.findViewById(R.id.toast_bar);
    }

    @Override
    public void onPostCreate(Bundle savedState) {
        if (!isDrawerEnabled()) {
            return;
        }
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();

        mHideMenuItems = isDrawerEnabled() && mDrawerContainer.isDrawerOpen(mDrawerPullout);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isDrawerEnabled()) {
            mDrawerToggle.onConfigurationChanged(newConfig);
        }
    }

    /**
     * This controller listens for clicks on items in the floating action bar.
     *
     * @param view the item that was clicked in the floating action bar
     */
    @Override
    public void onClick(View view) {
        final int viewId = view.getId();
        if (viewId == R.id.compose_button) {
            ComposeActivity.compose(mActivity.getActivityContext(), getAccount());
        } else if (viewId == android.R.id.home) {
            // TODO: b/16627877
            handleUpPress();
        }
    }

    /**
     * If drawer is open/visible (even partially), close it.
     */
    protected void closeDrawerIfOpen() {
        if (!isDrawerEnabled()) {
            return;
        }
        if(mDrawerContainer.isDrawerOpen(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
        }
    }

    @Override
    public void onStart() {
        mSafeToModifyFragments = true;

        NotificationActionUtils.registerUndoNotificationObserver(mUndoNotificationObserver);

        if (mViewMode.getMode() != ViewMode.UNKNOWN) {
            Analytics.getInstance().sendView("MainActivity" + mViewMode.toString());
        }
    }

    @Override
    public void onRestart() {
        final DialogFragment fragment = (DialogFragment)
                mFragmentManager.findFragmentByTag(SYNC_ERROR_DIALOG_FRAGMENT_TAG);
        if (fragment != null) {
            fragment.dismiss();
        }
        // When the user places the app in the background by pressing "home",
        // dismiss the toast bar. However, since there is no way to determine if
        // home was pressed, just dismiss any existing toast bar when restarting
        // the app.
        if (mToastBar != null) {
            mToastBar.hide(false, false /* actionClicked */);
        }
    }

    @Override
    public Dialog onCreateDialog(int id, Bundle bundle) {
        return null;
    }

    @Override
    public final boolean onCreateOptionsMenu(Menu menu) {
        if (mViewMode.isAdMode()) {
            return false;
        }
        final MenuInflater inflater = mActivity.getMenuInflater();
        inflater.inflate(mActionBarController.getOptionsMenuId(), menu);
        mActionBarController.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public final boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public abstract boolean doesActionChangeConversationListVisibility(int action);

    /**
     * Helper function that determines if we should associate an undo callback with
     * the current menu action item
     * @param actionId the id of the action
     * @return the appropriate callback handler, or null if not applicable
     */
    private UndoCallback getUndoCallbackForDestructiveActionsWithAutoAdvance(
            int actionId, final Conversation conv) {
        // We associated the undoCallback if the user is going to perform an action on the current
        // conversation, causing the current conversation to be removed from view and replacing it
        // with another (via Auto Advance). The undoCallback will bring the removed conversation
        // back into the view if the action is undone.
        final Collection<Conversation> convCol = Conversation.listOf(conv);
        final boolean isApplicableForReshow = mAccount != null &&
                mAccount.settings != null &&
                mTracker != null &&
                // ensure that we will show another conversation due to Auto Advance
                mTracker.getNextConversation(
                        mAccount.settings.getAutoAdvanceSetting(), convCol) != null &&
                // ensure that we are performing the action from conversation view
                isCurrentConversationInView(convCol) &&
                // check for the appropriate destructive actions
                doesActionRemoveCurrentConversationFromView(actionId);
        return (isApplicableForReshow) ?
            new UndoCallback() {
                @Override
                public void performUndoCallback() {
                    showConversation(conv);
                }
            } : null;
    }

    /**
     * Check if the provided action will remove the active conversation from view
     * @param actionId the applied action
     * @return true if it will remove the conversation from view, false otherwise
     */
    private boolean doesActionRemoveCurrentConversationFromView(int actionId) {
        return actionId == R.id.archive ||
                actionId == R.id.delete ||
                actionId == R.id.discard_outbox ||
                actionId == R.id.remove_folder ||
                actionId == R.id.report_spam ||
                actionId == R.id.report_phishing ||
                actionId == R.id.move_to;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        /*
         * The action bar home/up action should open or close the drawer.
         * mDrawerToggle will take care of this.
         */
        if (isDrawerEnabled() && mDrawerToggle.onOptionsItemSelected(item)) {
            Analytics.getInstance().sendEvent(Analytics.EVENT_CATEGORY_MENU_ITEM, "drawer_toggle",
                    null, 0);
            return true;
        }

        Analytics.getInstance().sendMenuItemEvent(Analytics.EVENT_CATEGORY_MENU_ITEM,
                item.getItemId(), "action_bar/" + mViewMode.getModeString(), 0);

        final int id = item.getItemId();
        LogUtils.d(LOG_TAG, "AbstractController.onOptionsItemSelected(%d) called.", id);
        /** This is NOT a batch action. */
        final boolean isBatch = false;
        final Collection<Conversation> target = Conversation.listOf(mCurrentConversation);
        final Settings settings = (mAccount == null) ? null : mAccount.settings;
        // The user is choosing a new action; commit whatever they had been
        // doing before. Don't animate if we are launching a new screen.
        commitDestructiveActions(!doesActionChangeConversationListVisibility(id));
        final UndoCallback undoCallback = getUndoCallbackForDestructiveActionsWithAutoAdvance(
                id, mCurrentConversation);

        // Menu items that are targetted, only perform if there actually is a target and the
        // cursor is showing the target in the list.
        boolean handled = false;
        if (target.size() > 0 &&
                ConversationCursor.isCursorReadyToShow(getConversationListCursor())) {
            handled = true;
            if (id == R.id.archive) {
                final boolean showDialog = (settings != null && settings.confirmArchive);
                confirmAndDelete(id, target, showDialog, R.plurals.confirm_archive_conversation,
                        undoCallback);
            } else if (id == R.id.remove_folder) {
                delete(R.id.remove_folder, target,
                        getDeferredRemoveFolder(target, mFolder, true, isBatch, true, undoCallback),
                        isBatch);
            } else if (id == R.id.delete) {
                final boolean showDialog = (settings != null && settings.confirmDelete);
                confirmAndDelete(id, target, showDialog, R.plurals.confirm_delete_conversation,
                        undoCallback);
            } else if (id == R.id.discard_drafts) {
                // drafts are lost forever, so always confirm
                confirmAndDelete(id, target, true /* showDialog */,
                        R.plurals.confirm_discard_drafts_conversation, undoCallback);
            } else if (id == R.id.discard_outbox) {
                // discard in outbox means we discard the failed message and save them in drafts
                delete(id, target, getDeferredAction(id, target, isBatch, undoCallback), isBatch);
            } else if (id == R.id.mark_important) {
                updateConversation(Conversation.listOf(mCurrentConversation),
                        ConversationColumns.PRIORITY, UIProvider.ConversationPriority.HIGH);
            } else if (id == R.id.mark_not_important) {
                if (mFolder != null && mFolder.isImportantOnly()) {
                    delete(R.id.mark_not_important, target,
                            getDeferredAction(R.id.mark_not_important, target, isBatch, undoCallback),
                            isBatch);
                } else {
                    updateConversation(target, ConversationColumns.PRIORITY,
                            UIProvider.ConversationPriority.LOW);
                }
            } else if (id == R.id.mute) {
                delete(R.id.mute, target, getDeferredAction(R.id.mute, target, isBatch, undoCallback),
                        isBatch);
            } else if (id == R.id.report_spam) {
                delete(R.id.report_spam, target,
                        getDeferredAction(R.id.report_spam, target, isBatch, undoCallback),
                        isBatch);
            } else if (id == R.id.mark_not_spam) {
                // Currently, since spam messages are only shown in list with
                // other spam messages,
                // marking a message not as spam is a destructive action
                delete(R.id.mark_not_spam, target,
                        getDeferredAction(R.id.mark_not_spam, target, isBatch, undoCallback),
                        isBatch);
            } else if (id == R.id.report_phishing) {
                delete(R.id.report_phishing, target,
                        getDeferredAction(R.id.report_phishing, target, isBatch, undoCallback),
                        isBatch);
            } else if (id == R.id.move_to || id == R.id.change_folders) {
                final FolderSelectionDialog dialog = FolderSelectionDialog.getInstance(mAccount,
                        target, isBatch, mFolder, id == R.id.move_to);
                if (dialog != null) {
                    dialog.show(mActivity.getFragmentManager(), null);
                }
            } else if (id == R.id.move_to_inbox) {
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
                        assignFolder(ops, target, true, true /* showUndo */, false /* isMoveTo */);
                    }
                }.execute((Void[]) null);
            } else {
                handled = false;
            }
        }

        // Not handled by the targetted menu items, check the general ones.
        if (!handled) {
            handled = true;
            if (id == android.R.id.home) {
                handleUpPress();
            } else if (id == R.id.compose) {
                ComposeActivity.compose(mActivity.getActivityContext(), mAccount);
            } else if (id == R.id.refresh) {
                requestFolderRefresh();
            } else if (id == R.id.toggle_drawer) {
                toggleDrawerState();
            } else if (id == R.id.settings) {
                Utils.showSettings(mActivity.getActivityContext(), mAccount);
            } else if (id == R.id.help_info_menu_item) {
                mActivity.showHelp(mAccount, mViewMode.getMode());
            } else if (id == R.id.empty_trash) {
                showEmptyDialog();
            } else if (id == R.id.empty_spam) {
                showEmptyDialog();
            } else if (id == R.id.search) {
                mSearchViewController.showSearchActionBar(
                        MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
            } else {
                handled = false;
            }
        }

        // If the controller didn't handle this event, check the CAB menu if it's active.
        // This is necessary because keyboard shortcuts don't seem to check CAB menus.
        if (!handled && mCabActionMenu != null && mCabActionMenu.isActivated() &&
                    mCabActionMenu.onActionItemClicked(item)) {
            handled = true;
        }

        return handled;
    }

    /**
     * Opens an {@link EmptyFolderDialogFragment} for the current folder.
     */
    private void showEmptyDialog() {
        if (mFolder != null) {
            final EmptyFolderDialogFragment fragment =
                    EmptyFolderDialogFragment.newInstance(mFolder.totalCount, mFolder.type);
            fragment.setListener(this);
            fragment.show(mActivity.getFragmentManager(), EmptyFolderDialogFragment.FRAGMENT_TAG);
        }
    }

    @Override
    public void onFolderEmptied() {
        emptyFolder();
    }

    /**
     * Performs the work of emptying the currently visible folder.
     */
    private void emptyFolder() {
        if (mConversationListCursor != null) {
            mConversationListCursor.emptyFolder();
        }
    }

    private void attachEmptyFolderDialogFragmentListener() {
        final EmptyFolderDialogFragment fragment =
                (EmptyFolderDialogFragment) mActivity.getFragmentManager()
                        .findFragmentByTag(EmptyFolderDialogFragment.FRAGMENT_TAG);

        if (fragment != null) {
            fragment.setListener(this);
        }
    }

    /**
     * Toggles the drawer pullout. If it was open (Fully extended), the
     * drawer will be closed. Otherwise, the drawer will be opened. This should
     * only be called when used with a toggle item. Other cases should be handled
     * explicitly with just closeDrawers() or openDrawer(View drawerView);
     */
    protected void toggleDrawerState() {
        if (!isDrawerEnabled()) {
            return;
        }
        if(mDrawerContainer.isDrawerOpen(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
        } else {
            mDrawerContainer.openDrawer(mDrawerPullout);
        }
    }

    @Override
    public final boolean onBackPressed() {
        if (isDrawerEnabled() && mDrawerContainer.isDrawerVisible(mDrawerPullout)) {
            mDrawerContainer.closeDrawers();
            return true;
        } else if (mSearchViewController.handleBackPress()) {
            return true;
        // If we're in CAB mode, let the activity handle onBackPressed.
        // It will handle closing CAB mode for us.
        } else if (mCabActionMenu != null && mCabActionMenu.isActivated()) {
            return false;
        }

        return handleBackPress();
    }

    protected abstract boolean handleBackPress();

    protected abstract boolean handleUpPress();

    @Override
    public void updateConversation(Collection<Conversation> target, ContentValues values) {
        mConversationListCursor.updateValues(target, values);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            boolean value) {
        mConversationListCursor.updateBoolean(target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            int value) {
        mConversationListCursor.updateInt(target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void updateConversation(Collection <Conversation> target, String columnName,
            String value) {
        mConversationListCursor.updateString(target, columnName, value);
        refreshConversationList();
    }

    @Override
    public void markConversationMessagesUnread(final Conversation conv,
            final Set<Uri> unreadMessageUris, final byte[] originalConversationInfo) {
        onPreMarkUnread();

        // locally mark conversation unread (the provider is supposed to propagate message unread
        // to conversation unread)
        conv.read = false;
        if (mConversationListCursor == null) {
            LogUtils.d(LOG_TAG, "markConversationMessagesUnread(id=%d), deferring", conv.id);

            mConversationListLoadFinishedCallbacks.add(new LoadFinishedCallback() {
                @Override
                public void onLoadFinished() {
                    doMarkConversationMessagesUnread(conv, unreadMessageUris,
                            originalConversationInfo);
                }
            });
        } else {
            LogUtils.d(LOG_TAG, "markConversationMessagesUnread(id=%d), performing", conv.id);
            doMarkConversationMessagesUnread(conv, unreadMessageUris, originalConversationInfo);
        }
    }

    /**
     * Hook to do stuff before actually marking a conversation unread (only called from within
     * conversation view). Most configurations do the default behavior of popping out of
     * CV to go back to TL.
     *
     */
    protected void onPreMarkUnread() {
        // The only caller of this method is the conversation view, from where marking unread should
        // take you back to list mode in most cases. Two-pane view is the exception.
        showConversation(null);
    }

    private void doMarkConversationMessagesUnread(Conversation conv, Set<Uri> unreadMessageUris,
            byte[] originalConversationInfo) {
        // Only do a granular 'mark unread' if a subset of messages are unread
        final int unreadCount = (unreadMessageUris == null) ? 0 : unreadMessageUris.size();
        final int numMessages = conv.getNumMessages();
        final boolean subsetIsUnread = (numMessages > 1 && unreadCount > 0
                && unreadCount < numMessages);

        LogUtils.d(LOG_TAG, "markConversationMessagesUnread(conv=%s)"
                + ", numMessages=%d, unreadCount=%d, subsetIsUnread=%b",
                conv, numMessages, unreadCount, subsetIsUnread);
        if (!subsetIsUnread) {
            // Conversations are neither marked read, nor viewed, and we don't want to show
            // the next conversation.
            LogUtils.d(LOG_TAG, ". . doing full mark unread");
            markConversationsRead(Collections.singletonList(conv), false, false, false);
        } else {
            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
                final ConversationInfo info = ConversationInfo.fromBlob(originalConversationInfo);
                LogUtils.d(LOG_TAG, ". . doing subset mark unread, originalConversationInfo = %s",
                        info);
            }
            mConversationListCursor.setConversationColumn(conv.uri, ConversationColumns.READ, 0);

            // Locally update conversation's conversationInfo to revert to original version
            if (originalConversationInfo != null) {
                mConversationListCursor.setConversationColumn(conv.uri,
                        ConversationColumns.CONVERSATION_INFO, originalConversationInfo);
            }

            // applyBatch with each CPO as an UPDATE op on each affected message uri
            final ArrayList<ContentProviderOperation> ops = Lists.newArrayList();
            String authority = null;
            for (Uri messageUri : unreadMessageUris) {
                if (authority == null) {
                    authority = messageUri.getAuthority();
                }
                ops.add(ContentProviderOperation.newUpdate(messageUri)
                        .withValue(UIProvider.MessageColumns.READ, 0)
                        .build());
                LogUtils.d(LOG_TAG, ". . Adding op: read=0, uri=%s", messageUri);
            }
            LogUtils.d(LOG_TAG, ". . operations = %s", ops);
            new ContentProviderTask() {
                @Override
                protected void onPostExecute(Result result) {
                    if (result.exception != null) {
                        LogUtils.e(LOG_TAG, result.exception, "ContentProviderTask() ERROR.");
                    } else {
                        LogUtils.d(LOG_TAG, "ContentProviderTask(): success %s",
                                Arrays.toString(result.results));
                    }
                }
            }.run(mResolver, authority, ops);
        }
    }

    /**
     * Mark a single conversation 'seen', which is a combination of 'viewed' and 'read'. In some
     * configurations (peek mode), this operation may be prevented and the method will return false.
     *
     * @param conv the conversation to mark seen
     * @return true if the operation was a success
     */
    @Override
    public boolean markConversationSeen(Conversation conv) {
        if (isCurrentConversationJustPeeking()) {
            LogUtils.i(LOG_TAG, "AAC is in peek mode, not marking seen. conv=%s", conv);
            return false;
        } else {
            markConversationsRead(Arrays.asList(conv), true /* read */, true /* viewed */);
            return true;
        }
    }

    @Override
    public void markConversationsRead(final Collection<Conversation> targets, final boolean read,
            final boolean viewed) {
        LogUtils.d(LOG_TAG, "markConversationsRead(targets=%s)", targets.toArray());

        if (mConversationListCursor == null) {
            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)) {
                LogUtils.d(LOG_TAG, "markConversationsRead(targets=%s), deferring",
                        targets.toArray());
            }
            mConversationListLoadFinishedCallbacks.add(new LoadFinishedCallback() {
                @Override
                public void onLoadFinished() {
                    markConversationsRead(targets, read, viewed, true);
                }
            });
        } else {
            // We want to show the next conversation if we are marking unread.
            markConversationsRead(targets, read, viewed, true);
        }
    }

    private void markConversationsRead(final Collection<Conversation> targets, final boolean read,
            final boolean markViewed, final boolean showNext) {
        LogUtils.d(LOG_TAG, "performing markConversationsRead");
        // Auto-advance if requested and the current conversation is being marked unread
        if (showNext && !read) {
            final Runnable operation = new Runnable() {
                @Override
                public void run() {
                    markConversationsRead(targets, read, markViewed, showNext);
                }
            };

            if (!showNextConversation(targets, operation)) {
                // This method will be called again if the user selects an autoadvance option
                return;
            }
        }

        final int size = targets.size();
        final List<ConversationOperation> opList = new ArrayList<ConversationOperation>(size);
        for (final Conversation target : targets) {
            final ContentValues value = new ContentValues(4);
            value.put(ConversationColumns.READ, read);

            // We never want to mark unseen here, but we do want to mark it seen
            if (read || markViewed) {
                value.put(ConversationColumns.SEEN, Boolean.TRUE);
            }

            // The mark read/unread/viewed operations do not show an undo bar
            value.put(ConversationOperations.Parameters.SUPPRESS_UNDO, true);
            if (markViewed) {
                value.put(ConversationColumns.VIEWED, true);
            }
            final ConversationInfo info = target.conversationInfo;
            final boolean changed = info.markRead(read);
            if (changed) {
                value.put(ConversationColumns.CONVERSATION_INFO, info.toBlob());
            }
            opList.add(mConversationListCursor.getOperationForConversation(
                    target, ConversationOperation.UPDATE, value));
            // Update the local conversation objects so they immediately change state.
            target.read = read;
            if (markViewed) {
                target.markViewed();
            }
        }
        mConversationListCursor.updateBulkValues(opList);
    }

    /**
     * Auto-advance to a different conversation if the currently visible conversation in
     * conversation mode is affected (deleted, marked unread, etc.).
     *
     * <p>Does nothing if outside of conversation mode.</p>
     *
     * @param target the set of conversations being deleted/marked unread
     */
    @Override
    public void showNextConversation(final Collection<Conversation> target) {
        showNextConversation(target, null);
    }

    /**
     * Helper function to determine if the provided set of conversations is in view
     * @param target set of conversations that we are interested in
     * @return true if they are in view, false otherwise
     */
    private boolean isCurrentConversationInView(final Collection<Conversation> target) {
        final int viewMode = mViewMode.getMode();
        return (viewMode == ViewMode.CONVERSATION
                || viewMode == ViewMode.SEARCH_RESULTS_CONVERSATION)
                && Conversation.contains(target, mCurrentConversation);
    }

    /**
     * Auto-advance to a different conversation if the currently visible conversation in
     * conversation mode is affected (deleted, marked unread, etc.).
     *
     * <p>Does nothing if outside of conversation mode.</p>
     * <p>
     * Clients may pass an operation to execute on the target that this method will run after
     * auto-advance is complete. The operation, if provided, may run immediately, or it may run
     * later, or not at all. Reasons it may run later include:
     * <ul>
     * <li>the auto-advance setting is uninitialized and we need to wait for the user to set it</li>
     * <li>auto-advance in this configuration requires a mode change, and we need to wait for the
     * mode change transition to finish</li>
     * </ul>
     * <p>If the current conversation is not in the target collection, this method will do nothing,
     * and will not execute the operation.
     *
     * @param target the set of conversations being deleted/marked unread
     * @param operation (optional) the operation to execute after advancing
     * @return <code>false</code> if this method handled or will execute the operation,
     * <code>true</code> otherwise.
     */
    private boolean showNextConversation(final Collection<Conversation> target,
            final Runnable operation) {
        if (isCurrentConversationInView(target)) {
            final int autoAdvanceSetting = mAccount.settings.getAutoAdvanceSetting();

            // If we don't have one set, but we're here, just take the default
            final int autoAdvance = (autoAdvanceSetting == AutoAdvance.UNSET) ?
                    AutoAdvance.DEFAULT : autoAdvanceSetting;

            // Set mAutoAdvanceOp *before* showConversation() to ensure that it runs when the
            // transition doesn't run (i.e. it "completes" immediately).
            mAutoAdvanceOp = operation;
            doShowNextConversation(target, autoAdvance);
            return (mAutoAdvanceOp == null);
        }

        return true;
    }

    /**
     * Do the actual work of selecting a next conversation to show and showing it. Two-pane
     * overrides this in landscape to prefer peeking rather than staring at an empty CV pane when
     * auto-advance=LIST.
     *
     * @param target conversations being destroyed, of which the current convo is one
     * @param autoAdvance auto-advance pref value
     */
    protected void doShowNextConversation(Collection<Conversation> target, int autoAdvance) {
        final Conversation next = mTracker.getNextConversation(autoAdvance, target);
        LogUtils.d(LOG_TAG, "showNextConversation: showing %s next.", next);
        showConversation(next);
    }

    @Override
    public void starMessage(ConversationMessage msg, boolean starred) {
        if (msg.starred == starred) {
            return;
        }

        msg.setStarredInConversation(starred);

        // locally propagate the change to the owning conversation
        // (figure the provider will properly propagate the change when it commits it)
        //
        // when unstarring, only propagate the change if this was the only message starred
        final boolean conversationStarred = starred || msg.isConversationStarred();
        final Conversation conv = msg.getConversation();
        if (conversationStarred != conv.starred) {
            conv.starred = conversationStarred;
            mConversationListCursor.setConversationColumn(conv.uri,
                    ConversationColumns.STARRED, conversationStarred);
        }

        final ContentValues values = new ContentValues(1);
        values.put(UIProvider.MessageColumns.STARRED, starred ? 1 : 0);

        new ContentProviderTask.UpdateTask() {
            @Override
            protected void onPostExecute(Result result) {
                // TODO: handle errors?
            }
        }.run(mResolver, msg.uri, values, null /* selection*/, null /* selectionArgs */);
    }

    @Override
    public void requestFolderRefresh() {
        if (mFolder == null) {
            return;
        }
        final ConversationListFragment convList = getConversationListFragment();
        if (convList == null) {
            // This could happen if this account is in initial sync (user
            // is seeing the "your mail will appear shortly" message)
            return;
        }
        convList.showSyncStatusBar();

        if (mAsyncRefreshTask != null) {
            mAsyncRefreshTask.cancel(true);
        }
        mAsyncRefreshTask = new AsyncRefreshTask(mContext, mFolder.refreshUri);
        mAsyncRefreshTask.execute();
    }

    /**
     * Confirm (based on user's settings) and delete a conversation from the conversation list and
     * from the database.
     * @param actionId the ID of the menu item that caused the delete: R.id.delete, R.id.archive...
     * @param target the conversations to act upon
     * @param showDialog true if a confirmation dialog is to be shown, false otherwise.
     * @param confirmResource the resource ID of the string that is shown in the confirmation dialog
     */
    private void confirmAndDelete(int actionId, final Collection<Conversation> target,
            boolean showDialog, int confirmResource, UndoCallback undoCallback) {
        final boolean isBatch = false;
        if (showDialog) {
            makeDialogListener(actionId, isBatch, undoCallback);
            final CharSequence message = Utils.formatPlural(mContext, confirmResource,
                    target.size());
            final ConfirmDialogFragment c = ConfirmDialogFragment.newInstance(message);
            c.displayDialog(mActivity.getFragmentManager());
        } else {
            delete(0, target, getDeferredAction(actionId, target, isBatch, undoCallback), isBatch);
        }
    }

    @Override
    public void delete(final int actionId, final Collection<Conversation> target,
                       final DestructiveAction action, final boolean isBatch) {
        // Order of events is critical! The Conversation View Fragment must be
        // notified of the next conversation with showConversation(next) *before* the
        // conversation list
        // fragment has a chance to delete the conversation, animating it away.

        // Update the conversation fragment if the current conversation is
        // deleted.
        final Runnable operation = new Runnable() {
            @Override
            public void run() {
                delete(actionId, target, action, isBatch);
            }
        };

        showNextConversation(target, operation);

        // If the conversation is in the selected set, remove it from the set.
        // Batch selections are cleared in the end of the action, so not done for batch actions.
        if (!isBatch) {
            for (final Conversation conv : target) {
                if (mCheckedSet.contains(conv)) {
                    mCheckedSet.toggle(conv);
                }
            }
        }
        // The conversation list deletes and performs the action if it exists.
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            LogUtils.i(LOG_TAG, "AAC.requestDelete: ListFragment is handling delete.");
            convListFragment.requestDelete(actionId, target, action);
            return;
        }
        // No visible UI element handled it on our behalf. Perform the action
        // ourself.
        LogUtils.i(LOG_TAG, "ACC.requestDelete: performing remove action ourselves");
        action.performAction();
    }

    /**
     * Requests that the action be performed and the UI state is updated to reflect the new change.
     * @param action the action to be performed, specified as a menu id: R.id.archive, ...
     */
    private void requestUpdate(final DestructiveAction action) {
        action.performAction();
        refreshConversationList();
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog, Bundle bundle) {
        // TODO(viki): Auto-generated method stub
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mActionBarController.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onPause() {
        mHaveAccountList = false;
        enableNotifications();
    }

    @Override
    public void onResume() {
        // Register the receiver that will prevent the status receiver from
        // displaying its notification icon as long as we're running.
        // The SupressNotificationReceiver will block the broadcast if we're looking at the folder
        // that the notification was received for.
        disableNotifications();

        mSafeToModifyFragments = true;

        attachEmptyFolderDialogFragmentListener();

        // Invalidating the options menu so that when we make changes in settings,
        // the changes will always be updated in the action bar/options menu/
        mActivity.invalidateOptionsMenu();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mViewMode.handleSaveInstanceState(outState);
        if (mAccount != null) {
            outState.putParcelable(SAVED_ACCOUNT, mAccount);
        }
        if (mFolder != null) {
            outState.putParcelable(SAVED_FOLDER, mFolder);
        }
        // If this is a search activity, let's store the search query term as well.
        if (ConversationListContext.isSearchResult(mConvListContext)) {
            outState.putString(SAVED_QUERY, mConvListContext.searchQuery);
        }
        if (mCurrentConversation != null && mViewMode.isConversationMode()) {
            outState.putParcelable(SAVED_CONVERSATION, mCurrentConversation);
        }
        if (!mCheckedSet.isEmpty()) {
            outState.putParcelable(SAVED_SELECTED_SET, mCheckedSet);
        }
        if (mToastBar.getVisibility() == View.VISIBLE) {
            outState.putParcelable(SAVED_TOAST_BAR_OP, mToastBar.getOperation());
        }
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            convListFragment.getAnimatedAdapter().onSaveInstanceState(outState);
        }
        // If there is a dialog being shown, save the state so we can create a listener for it.
        if (mDialogAction != -1) {
            outState.putInt(SAVED_ACTION, mDialogAction);
            outState.putBoolean(SAVED_ACTION_FROM_SELECTED, mDialogFromSelectedSet);
        }
        if (mDetachedConvUri != null) {
            outState.putParcelable(SAVED_DETACHED_CONV_URI, mDetachedConvUri);
        }

        outState.putParcelable(SAVED_HIERARCHICAL_FOLDER, mFolderListFolder);
        mSafeToModifyFragments = false;

        outState.putParcelable(SAVED_INBOX_KEY, mInbox);

        outState.putBundle(SAVED_CONVERSATION_LIST_SCROLL_POSITIONS,
                mConversationListScrollPositions);

        mSearchViewController.saveState(outState);
    }

    /**
     * @see #mSafeToModifyFragments
     */
    protected boolean safeToModifyFragments() {
        return mSafeToModifyFragments;
    }

    @Override
    public void executeSearch(String query) {
        AnalyticsTimer.getInstance().trackStart(AnalyticsTimer.SEARCH_TO_LIST);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEARCH);
        intent.putExtra(ConversationListContext.EXTRA_SEARCH_QUERY, query);
        intent.putExtra(Utils.EXTRA_ACCOUNT, mAccount);
        intent.setComponent(mActivity.getComponentName());
        mSearchViewController.showSearchActionBar(
                MaterialSearchViewController.SEARCH_VIEW_STATE_GONE);
        // Call startActivityForResult here so we can tell if we have navigated to a different folder
        // or account from search results.
        mActivity.startActivityForResult(intent, CHANGE_NAVIGATION_REQUEST_CODE);
    }

    @Override
    public void onStop() {
        NotificationActionUtils.unregisterUndoNotificationObserver(mUndoNotificationObserver);
    }

    @Override
    public void onDestroy() {
        // stop listening to the cursor on e.g. configuration changes
        if (mConversationListCursor != null) {
            mConversationListCursor.removeListener(this);
        }
        mDrawIdler.setListener(null);
        mDrawIdler.setRootView(null);
        // unregister the ViewPager's observer on the conversation cursor
        mPagerController.onDestroy();
        mActionBarController.onDestroy();
        mRecentFolderList.destroy();
        mDestroyed = true;
        mHandler.removeCallbacks(mLogServiceChecker);
        mLogServiceChecker = null;
        mSearchViewController.onDestroy();
    }

    /**
     * Set the Action Bar icon according to the mode. The Action Bar icon can contain a back button
     * or not. The individual controller is responsible for changing the icon based on the mode.
     */
    protected abstract void resetActionBarIcon();

    /**
     * {@inheritDoc} Subclasses must override this to listen to mode changes
     * from the ViewMode. Subclasses <b>must</b> call the parent's
     * onViewModeChanged since the parent will handle common state changes.
     */
    @Override
    public void onViewModeChanged(int newMode) {
        // When we step away from the conversation mode, we don't have a current conversation
        // anymore. Let's blank it out so clients calling getCurrentConversation are not misled.
        if (!ViewMode.isConversationMode(newMode)) {
            setCurrentConversation(null);
        }

        // If the viewmode is not set, preserve existing icon.
        if (newMode != ViewMode.UNKNOWN) {
            resetActionBarIcon();
        }

        if (isDrawerEnabled()) {
            /** If the folder doesn't exist, or its parent URI is empty,
             * this is not a child folder */
            final boolean isTopLevel = Folder.isRoot(mFolder);
            updateDrawerIndicator(newMode, isTopLevel);
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            closeDrawerIfOpen();
        }
    }

    /**
     * Update the drawer indicator to either be the burger or the back arrow.
     * @param viewMode the current view mode
     * @param isTopLevel true if the current folder is not a child
     */
    private void updateDrawerIndicator(final int viewMode, final boolean isTopLevel) {
        // Show burger if we're either in conversation list or folder list mode.
        if (isDrawerEnabled() && !ViewMode.isSearchMode(viewMode)
            && (viewMode == ViewMode.CONVERSATION_LIST  && isTopLevel)) {
            mDrawerToggle.setHomeAsUpIndicator(R.drawable.ic_menu_wht_24dp);

        // Otherwise, show the back arrow for the indicator.
        } else {
            mDrawerToggle.setHomeAsUpIndicator(R.drawable.ic_arrow_back_wht_24dp_with_rtl);
        }
    }

    public void disablePagerUpdates() {
        mPagerController.stopListening();
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }

    @Override
    public void commitDestructiveActions(boolean animate) {
        ConversationListFragment fragment = getConversationListFragment();
        if (fragment != null) {
            fragment.commitDestructiveActions(animate);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        final ConversationListFragment convList = getConversationListFragment();
        // hasFocus already ensures that the window is in focus, so we don't need to call
        // AAC.isFragmentVisible(convList) here.
        if (hasFocus && convList != null && convList.isVisible()) {
            // The conversation list is visible.
            informCursorVisiblity(true);
        }
    }

    /**
     * Set the account, and carry out all the account-related changes that rely on this.
     * @param account new account to set to.
     */
    private void setAccount(Account account) {
        if (account == null) {
            LogUtils.w(LOG_TAG, new Error(),
                    "AAC ignoring null (presumably invalid) account restoration");
            return;
        }
        LogUtils.d(LOG_TAG, "AbstractActivityController.setAccount(): account = %s", account.uri);
        mAccount = account;

        Analytics.getInstance().setEmail(account.getEmailAddress(), account.getType());

        // Only change AAC state here. Do *not* modify any other object's state. The object
        // should listen on account changes.
        restartOptionalLoader(LOADER_RECENT_FOLDERS, mFolderCallbacks, Bundle.EMPTY);
        mActivity.invalidateOptionsMenu();
        disableNotificationsOnAccountChange(mAccount);
        restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR, mAccountCallbacks, Bundle.EMPTY);
        // The Mail instance can be null during test runs.
        final MailAppProvider instance = MailAppProvider.getInstance();
        if (instance != null) {
            instance.setLastViewedAccount(mAccount.uri.toString());
        }
        if (account.settings == null) {
            LogUtils.w(LOG_TAG, new Error(), "AAC ignoring account with null settings.");
            return;
        }
        mAccountObservers.notifyChanged();
        perhapsEnterWaitMode();
    }

    /**
     * Restore the state from the previous bundle. Subclasses should call this
     * method from the parent class, since it performs important UI
     * initialization.
     *
     * @param savedState previous state
     */
    @Override
    public void onRestoreInstanceState(Bundle savedState) {
        mDetachedConvUri = savedState.getParcelable(SAVED_DETACHED_CONV_URI);
        if (savedState.containsKey(SAVED_CONVERSATION)) {
            // Open the conversation.
            final Conversation conversation = savedState.getParcelable(SAVED_CONVERSATION);
            restoreConversation(conversation);
        }

        if (savedState.containsKey(SAVED_TOAST_BAR_OP)) {
            ToastBarOperation op = savedState.getParcelable(SAVED_TOAST_BAR_OP);
            if (op != null) {
                if (op.getType() == ToastBarOperation.UNDO) {
                    onUndoAvailable(op);
                } else if (op.getType() == ToastBarOperation.ERROR) {
                    onError(mFolder, true);
                }
            }
        }
        mFolderListFolder = savedState.getParcelable(SAVED_HIERARCHICAL_FOLDER);
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            convListFragment.getAnimatedAdapter().onRestoreInstanceState(savedState);
        }
        /*
         * Restore the state of selected conversations. This needs to be done after the correct mode
         * is set and the action bar is fully initialized. If not, several key pieces of state
         * information will be missing, and the split views may not be initialized correctly.
         */
        restoreSelectedConversations(savedState);
        // Order is important!!!
        // The dialog listener needs to happen *after* the selected set is restored.

        // If there has been an orientation change, and we need to recreate the listener for the
        // confirm dialog fragment (delete/archive/...), then do it here.
        if (mDialogAction != -1) {
            makeDialogListener(mDialogAction, mDialogFromSelectedSet,
                    getUndoCallbackForDestructiveActionsWithAutoAdvance(
                            mDialogAction, mCurrentConversation));
        }

        mInbox = savedState.getParcelable(SAVED_INBOX_KEY);

        mConversationListScrollPositions.clear();
        mConversationListScrollPositions.putAll(
                savedState.getBundle(SAVED_CONVERSATION_LIST_SCROLL_POSITIONS));
    }

    /**
     * Handle an intent to open the app. This method is called only when there is no saved state,
     * so we need to set state that wasn't set before. It is correct to change the viewmode here
     * since it has not been previously set.
     *
     * This method is called for a subset of the reasons mentioned in
     * {@link #onCreate(android.os.Bundle)}. Notably, this is called when launching the app from
     * notifications, widgets, and shortcuts.
     * @param intent intent passed to the activity.
     */
    private void handleIntent(Intent intent) {
        LogUtils.d(LOG_TAG, "IN AAC.handleIntent. action=%s", intent.getAction());
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            if (intent.hasExtra(Utils.EXTRA_ACCOUNT)) {
                setAccount(Account.newInstance(intent.getStringExtra(Utils.EXTRA_ACCOUNT)));
            }
            if (mAccount == null) {
                return;
            }
            final boolean isConversationMode = intent.hasExtra(Utils.EXTRA_CONVERSATION);

            if (intent.getBooleanExtra(Utils.EXTRA_FROM_NOTIFICATION, false)) {
                Analytics.getInstance().setEmail(mAccount.getEmailAddress(), mAccount.getType());
                Analytics.getInstance().sendEvent("notification_click",
                        isConversationMode ? "conversation" : "conversation_list", null, 0);
            }

            if (isConversationMode && mViewMode.getMode() == ViewMode.UNKNOWN) {
                mViewMode.enterConversationMode();
            } else {
                mViewMode.enterConversationListMode();
            }
            // Put the folder and conversation, and ask the loader to create this folder.
            final Bundle args = new Bundle();

            final Uri folderUri;
            if (intent.hasExtra(Utils.EXTRA_FOLDER_URI)) {
                folderUri = intent.getParcelableExtra(Utils.EXTRA_FOLDER_URI);
            } else if (intent.hasExtra(Utils.EXTRA_FOLDER)) {
                final Folder folder =
                        Folder.fromString(intent.getStringExtra(Utils.EXTRA_FOLDER));
                folderUri = folder.folderUri.fullUri;
            } else {
                final Bundle extras = intent.getExtras();
                LogUtils.d(LOG_TAG, "Couldn't find a folder URI in the extras: %s",
                        extras == null ? "null" : extras.toString());
                folderUri = mAccount.settings.defaultInbox;
            }

            // Check if we should load all conversations instead of using
            // the default behavior which loads an initial subset.
            mIgnoreInitialConversationLimit =
                    intent.getBooleanExtra(Utils.EXTRA_IGNORE_INITIAL_CONVERSATION_LIMIT, false);

            args.putParcelable(Utils.EXTRA_FOLDER_URI, folderUri);
            args.putParcelable(Utils.EXTRA_CONVERSATION,
                    intent.getParcelableExtra(Utils.EXTRA_CONVERSATION));
            restartOptionalLoader(LOADER_FIRST_FOLDER, mFolderCallbacks, args);
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            if (intent.hasExtra(Utils.EXTRA_ACCOUNT)) {
                mHaveSearchResults = false;
                // Save this search query for future suggestions
                final String query = intent.getStringExtra(SearchManager.QUERY);
                mSearchViewController.saveRecentQuery(query);
                setAccount((Account) intent.getParcelableExtra(Utils.EXTRA_ACCOUNT));
                fetchSearchFolder(intent);
                if (shouldEnterSearchConvMode()) {
                    mViewMode.enterSearchResultsConversationMode();
                } else {
                    mViewMode.enterSearchResultsListMode();
                }
            } else {
                LogUtils.e(LOG_TAG, "Missing account extra from search intent.  Finishing");
                mActivity.finish();
            }
        }
        if (mAccount != null) {
            restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR, mAccountCallbacks, Bundle.EMPTY);
        }
    }

    /**
     * Returns true if we should enter conversation mode with search.
     */
    protected final boolean shouldEnterSearchConvMode() {
        return mHaveSearchResults && shouldShowFirstConversation();
    }

    /**
     * Copy any selected conversations stored in the saved bundle into our selection set,
     * triggering {@link ConversationSetObserver} callbacks as our selection set changes.
     *
     */
    private void restoreSelectedConversations(Bundle savedState) {
        if (savedState == null) {
            mCheckedSet.clear();
            return;
        }
        final ConversationCheckedSet selectedSet = savedState.getParcelable(SAVED_SELECTED_SET);
        if (selectedSet == null || selectedSet.isEmpty()) {
            mCheckedSet.clear();
            return;
        }

        // putAll will take care of calling our registered onSetPopulated method
        mCheckedSet.putAll(selectedSet);
    }

    protected void restoreConversation(Conversation conversation) {
        if (conversation != null && conversation.position < 0) {
            // Set the position to 0 on this conversation, as we don't know where it is
            // in the list
            conversation.position = 0;
        }
        showConversation(conversation);
    }

    /**
     * Show the conversation provided in the arguments. It is safe to pass a null conversation
     * object, which is a signal to back out of conversation view mode.
     * Child classes must call super.showConversation() <b>before</b> their own implementations.
     * @param conversation the conversation to be shown, or null if we want to back out to list
     *                     mode.
     * onLoadFinished(Loader, Cursor) on any callback.
     */
    protected void showConversation(Conversation conversation) {
        showConversation(conversation, false /* shouldAnimate */);
    }

    /**
     * Helper method to allow for conversation view animation control. Implementing classes should
     * directly override this to handle the animation.
     * @param conversation
     * @param shouldAnimate true if we want to animate the conversation in, false otherwise
     */
    protected void showConversation(Conversation conversation, boolean shouldAnimate) {
        showConversationWithPeek(conversation, false /* peek */);
    }

    protected void showConversationWithPeek(Conversation conversation, boolean peek) {
        if (conversation != null) {
            Utils.sConvLoadTimer.start();
        }

        MailLogService.log("AbstractActivityController", "showConversation(%s)", conversation);
        // Set the current conversation just in case it wasn't already set.
        setCurrentConversation(conversation);
    }

    /**
     * Show the wait for account initialization mode.
     * Children can override this method, but they must call super.showWaitForInitialization().
     */
    protected void showWaitForInitialization() {
        mViewMode.enterWaitingForInitializationMode();
        mWaitFragment = WaitFragment.newInstance(mAccount, true /* expectingMessages */);
    }

    private void updateWaitMode() {
        final FragmentManager manager = mActivity.getFragmentManager();
        final WaitFragment waitFragment =
                (WaitFragment)manager.findFragmentByTag(TAG_WAIT);
        if (waitFragment != null) {
            waitFragment.updateAccount(mAccount);
        }
    }

    /**
     * Remove the "Waiting for Initialization" fragment. Child classes are free to override this
     * method, though they must call the parent implementation <b>after</b> they do anything.
     */
    protected void hideWaitForInitialization() {
        mWaitFragment = null;
    }

    /**
     * Use the instance variable and the wait fragment's tag to get the wait fragment.  This is
     * far superior to using the value of mWaitFragment, which might be invalid or might refer
     * to a fragment after it has been destroyed.
     * @return a wait fragment that is already attached to the activity, if one exists
     */
    protected final WaitFragment getWaitFragment() {
        final FragmentManager manager = mActivity.getFragmentManager();
        final WaitFragment waitFrag = (WaitFragment) manager.findFragmentByTag(TAG_WAIT);
        if (waitFrag != null) {
            // The Fragment Manager knows better, so use its instance.
            mWaitFragment = waitFrag;
        }
        return mWaitFragment;
    }

    /**
     * Returns true if we are waiting for the account to sync, and cannot show any folders or
     * conversation for the current account yet.
     */
    private boolean inWaitMode() {
        final WaitFragment waitFragment = getWaitFragment();
        if (waitFragment != null) {
            final Account fragmentAccount = waitFragment.getAccount();
            return fragmentAccount != null && fragmentAccount.uri.equals(mAccount.uri) &&
                    mViewMode.getMode() == ViewMode.WAITING_FOR_ACCOUNT_INITIALIZATION;
        }
        return false;
    }

    /**
     * Show the conversation List with the list context provided here. On certain layouts, this
     * might show more than just the conversation list. For instance, on tablets this might show
     * the conversations along with the conversation list.
     * @param listContext context providing information on what conversation list to display.
     */
    protected abstract void showConversationList(ConversationListContext listContext);

    @Override
    public void onConversationSelected(Conversation conversation, boolean inLoaderCallbacks) {
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null && convListFragment.getAnimatedAdapter() != null) {
            convListFragment.getAnimatedAdapter().onConversationSelected();
        }
        // Only animate destructive actions if we are going to be showing the
        // conversation list when we show the next conversation.
        commitDestructiveActions(mIsTablet);
        showConversation(conversation, true /* shouldAnimate */);
    }

    @Override
    public final void onCabModeEntered() {
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null && convListFragment.getAnimatedAdapter() != null) {
            convListFragment.getAnimatedAdapter().onCabModeEntered();
        }
    }

    @Override
    public final void onCabModeExited() {
        final ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null && convListFragment.getAnimatedAdapter() != null) {
            convListFragment.getAnimatedAdapter().onCabModeExited();
        }
    }

    @Override
    public Conversation getCurrentConversation() {
        return mCurrentConversation;
    }

    /**
     * Set the current conversation. This is the conversation on which all actions are performed.
     * Do not modify mCurrentConversation except through this method, which makes it easy to
     * perform common actions associated with changing the current conversation.
     * @param conversation new conversation to view. Passing null indicates that we are backing
     *                     out to conversation list mode.
     */
    @Override
    public void setCurrentConversation(Conversation conversation) {
        // The controller should come out of detached mode if a new conversation is viewed, or if
        // we are going back to conversation list mode.
        if (mDetachedConvUri != null && (conversation == null
                || !mDetachedConvUri.equals(conversation.uri))) {
            clearDetachedMode();
        }

        // Must happen *before* setting mCurrentConversation because this sets
        // conversation.position if a cursor is available.
        mTracker.initialize(conversation);
        mCurrentConversation = conversation;

        if (mCurrentConversation != null) {
            mActionBarController.setCurrentConversation(mCurrentConversation);
            mActivity.invalidateOptionsMenu();
        }
    }

    /**
     * Invoked by {@link ConversationPagerAdapter} when a new page in the ViewPager is selected.
     *
     * @param conversation the conversation of the now currently visible fragment
     *
     */
    @Override
    public void onConversationViewSwitched(Conversation conversation) {
        setCurrentConversation(conversation);
    }

    @Override
    public boolean isCurrentConversationJustPeeking() {
        return false;
    }

    /**
     * {@link LoaderManager} currently has a bug in
     * {@link LoaderManager#restartLoader(int, Bundle, android.app.LoaderManager.LoaderCallbacks)}
     * where, if a previous onCreateLoader returned a null loader, this method will NPE. Work around
     * this bug by destroying any loaders that may have been created as null (essentially because
     * they are optional loads, and may not apply to a particular account).
     * <p>
     * A simple null check before restarting a loader will not work, because that would not
     * give the controller a chance to invalidate UI corresponding the prior loader result.
     *
     * @param id loader ID to safely restart
     * @param handler the LoaderCallback which will handle this loader ID.
     * @param args arguments, if any, to be passed to the loader. Use {@link Bundle#EMPTY} if no
     *             arguments need to be specified.
     */
    private void restartOptionalLoader(int id, LoaderManager.LoaderCallbacks handler, Bundle args) {
        final LoaderManager lm = mActivity.getLoaderManager();
        lm.destroyLoader(id);
        lm.restartLoader(id, args, handler);
    }

    @Override
    public void registerConversationListObserver(DataSetObserver observer) {
        mConversationListObservable.registerObserver(observer);
    }

    @Override
    public void unregisterConversationListObserver(DataSetObserver observer) {
        try {
            mConversationListObservable.unregisterObserver(observer);
        } catch (IllegalStateException e) {
            // Log instead of crash
            LogUtils.e(LOG_TAG, e, "unregisterConversationListObserver called for an observer that "
                    + "hasn't been registered");
        }
    }

    @Override
    public void registerFolderObserver(DataSetObserver observer) {
        mFolderObservable.registerObserver(observer);
    }

    @Override
    public void unregisterFolderObserver(DataSetObserver observer) {
        try {
            mFolderObservable.unregisterObserver(observer);
        } catch (IllegalStateException e) {
            // Log instead of crash
            LogUtils.e(LOG_TAG, e, "unregisterFolderObserver called for an observer that "
                    + "hasn't been registered");
        }
    }

    @Override
    public void registerConversationLoadedObserver(DataSetObserver observer) {
        mPagerController.registerConversationLoadedObserver(observer);
    }

    @Override
    public void unregisterConversationLoadedObserver(DataSetObserver observer) {
        try {
            mPagerController.unregisterConversationLoadedObserver(observer);
        } catch (IllegalStateException e) {
            // Log instead of crash
            LogUtils.e(LOG_TAG, e, "unregisterConversationLoadedObserver called for an observer "
                    + "that hasn't been registered");
        }
    }

    /**
     * Returns true if the number of accounts is different, or if the current account has
     * changed. This method is meant to filter frequent changes to the list of
     * accounts, and only return true if the new list is substantially different from the existing
     * list. Returning true is safe here, it leads to more work in creating the
     * same account list again.
     * @param accountCursor the cursor which points to all the accounts.
     * @return true if the number of accounts is changed or current account missing from the list.
     */
    private boolean accountsUpdated(ObjectCursor<Account> accountCursor) {
        // Check to see if the current account hasn't been set, or the account cursor is empty
        if (mAccount == null || !accountCursor.moveToFirst()) {
            return true;
        }

        // Check to see if the number of accounts are different, from the number we saw on the last
        // updated
        if (mCurrentAccountUris.size() != accountCursor.getCount()) {
            return true;
        }

        // Check to see if the account list is different or if the current account is not found in
        // the cursor.
        boolean foundCurrentAccount = false;
        do {
            final Account account = accountCursor.getModel();
            if (!foundCurrentAccount && mAccount.uri.equals(account.uri)) {
                if (mAccount.settingsDiffer(account)) {
                    // Settings changed, and we don't need to look any further.
                    return true;
                }
                foundCurrentAccount = true;
            }
            // Is there a new account that we do not know about?
            if (!mCurrentAccountUris.contains(account.uri)) {
                return true;
            }
        } while (accountCursor.moveToNext());

        // As long as we found the current account, the list hasn't been updated
        return !foundCurrentAccount;
    }

    /**
     * Updates accounts for the app. If the current account is missing, the first
     * account in the list is set to the current account (we <em>have</em> to choose something).
     *
     * @param accounts cursor into the AccountCache
     * @return true if the update was successful, false otherwise
     */
    private boolean updateAccounts(ObjectCursor<Account> accounts) {
        if (accounts == null || !accounts.moveToFirst()) {
            return false;
        }

        final Account[] allAccounts = Account.getAllAccounts(accounts);
        // A match for the current account's URI in the list of accounts.
        Account currentFromList = null;

        // Save the uris for the accounts and find the current account in the updated cursor.
        mCurrentAccountUris.clear();
        for (final Account account : allAccounts) {
            LogUtils.d(LOG_TAG, "updateAccounts(%s)", account);
            mCurrentAccountUris.add(account.uri);
            if (mAccount != null && account.uri.equals(mAccount.uri)) {
                currentFromList = account;
            }
        }

        // 1. current account is already set and is in allAccounts:
        //    1a. It has changed -> load the updated account.
        //    1b. It is unchanged -> no-op
        // 2. current account is set and is not in allAccounts -> pick first (acct was deleted?)
        // 3. saved preference has an account -> pick that one
        // 4. otherwise just pick first

        boolean accountChanged = false;
        /// Assume case 4, initialize to first account, and see if we can find anything better.
        Account newAccount = allAccounts[0];
        if (currentFromList != null) {
            // Case 1: Current account exists but has changed
            if (!currentFromList.equals(mAccount)) {
                newAccount = currentFromList;
                accountChanged = true;
            }
            // Case 1b: else, current account is unchanged: nothing to do.
        } else {
            // Case 2: Current account is not in allAccounts, the account needs to change.
            accountChanged = true;
            if (mAccount == null) {
                // Case 3: Check for last viewed account, and check if it exists in the list.
                final String lastAccountUri = MailAppProvider.getInstance().getLastViewedAccount();
                if (lastAccountUri != null) {
                    for (final Account account : allAccounts) {
                        if (lastAccountUri.equals(account.uri.toString())) {
                            newAccount = account;
                            break;
                        }
                    }
                }
            }
        }
        if (accountChanged) {
            changeAccount(newAccount);
        }

        // Whether we have updated the current account or not, we need to update the list of
        // accounts in the ActionBar.
        mAllAccounts = allAccounts;
        mAllAccountObservers.notifyChanged();
        return (allAccounts.length > 0);
    }

    private void disableNotifications() {
        mNewEmailReceiver.activate(mContext, this);
    }

    private void enableNotifications() {
        mNewEmailReceiver.deactivate();
    }

    private void disableNotificationsOnAccountChange(Account account) {
        // If the new mail suppression receiver is activated for a different account, we want to
        // activate it for the new account.
        if (mNewEmailReceiver.activated() &&
                !mNewEmailReceiver.notificationsDisabledForAccount(account)) {
            // Deactivate the current receiver, otherwise multiple receivers may be registered.
            mNewEmailReceiver.deactivate();
            mNewEmailReceiver.activate(mContext, this);
        }
    }

    /**
     * Destructive actions on Conversations. This class should only be created by controllers, and
     * clients should only require {@link DestructiveAction}s, not specific implementations of the.
     * Only the controllers should know what kind of destructive actions are being created.
     */
    public class ConversationAction implements DestructiveAction {
        /**
         * The action to be performed. This is specified as the resource ID of the menu item
         * corresponding to this action: R.id.delete, R.id.report_spam, etc.
         */
        private final int mAction;
        /** The action will act upon these conversations */
        private final Collection<Conversation> mTarget;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;
        /** Whether this is an action on the currently selected set. */
        private final boolean mIsSelectedSet;

        private UndoCallback mCallback;

        /**
         * Create a listener object.
         * @param action action is one of four constants: R.id.y_button (archive),
         * R.id.delete , R.id.mute, and R.id.report_spam.
         * @param target Conversation that we want to apply the action to.
         * @param isBatch whether the conversations are in the currently selected batch set.
         */
        public ConversationAction(int action, Collection<Conversation> target, boolean isBatch) {
            mAction = action;
            mTarget = ImmutableList.copyOf(target);
            mIsSelectedSet = isBatch;
        }

        @Override
        public void setUndoCallback(UndoCallback undoCallback) {
            mCallback = undoCallback;
        }

        /**
         * The action common to child classes. This performs the action specified in the constructor
         * on the conversations given here.
         */
        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            boolean undoEnabled = mAccount.supportsCapability(AccountCapabilities.UNDO);

            // Are we destroying the currently shown conversation? Show the next one.
            if (LogUtils.isLoggable(LOG_TAG, LogUtils.DEBUG)){
                LogUtils.d(LOG_TAG, "ConversationAction.performAction():"
                        + "\nmTarget=%s\nCurrent=%s",
                        Conversation.toString(mTarget), mCurrentConversation);
            }

            if (mConversationListCursor == null) {
                LogUtils.e(LOG_TAG, "null ConversationCursor in ConversationAction.performAction():"
                        + "\nmTarget=%s\nCurrent=%s",
                        Conversation.toString(mTarget), mCurrentConversation);
                return;
            }

            if (mAction == R.id.archive) {
                LogUtils.d(LOG_TAG, "Archiving");
                mConversationListCursor.archive(mTarget, mCallback);
            } else if (mAction == R.id.delete) {
                LogUtils.d(LOG_TAG, "Deleting");
                mConversationListCursor.delete(mTarget, mCallback);
                if (mFolder.supportsCapability(FolderCapabilities.DELETE_ACTION_FINAL)) {
                    undoEnabled = false;
                }
            } else if (mAction == R.id.mute) {
                LogUtils.d(LOG_TAG, "Muting");
                if (mFolder.supportsCapability(FolderCapabilities.DESTRUCTIVE_MUTE)) {
                    for (Conversation c : mTarget) {
                        c.localDeleteOnUpdate = true;
                    }
                }
                mConversationListCursor.mute(mTarget, mCallback);
            } else if (mAction == R.id.report_spam) {
                LogUtils.d(LOG_TAG, "Reporting spam");
                mConversationListCursor.reportSpam(mTarget, mCallback);
            } else if (mAction == R.id.mark_not_spam) {
                LogUtils.d(LOG_TAG, "Marking not spam");
                mConversationListCursor.reportNotSpam(mTarget, mCallback);
            } else if (mAction == R.id.report_phishing) {
                LogUtils.d(LOG_TAG, "Reporting phishing");
                mConversationListCursor.reportPhishing(mTarget, mCallback);
            } else if (mAction == R.id.remove_star) {
                LogUtils.d(LOG_TAG, "Removing star");
                // Star removal is destructive in the Starred folder.
                mConversationListCursor.updateBoolean(mTarget, ConversationColumns.STARRED,
                        false);
            } else if (mAction == R.id.mark_not_important) {
                LogUtils.d(LOG_TAG, "Marking not-important");
                // Marking not important is destructive in a mailbox
                // containing only important messages
                if (mFolder != null && mFolder.isImportantOnly()) {
                    for (Conversation conv : mTarget) {
                        conv.localDeleteOnUpdate = true;
                    }
                }
                mConversationListCursor.updateInt(mTarget, ConversationColumns.PRIORITY,
                        UIProvider.ConversationPriority.LOW);
            } else if (mAction == R.id.discard_drafts) {
                LogUtils.d(LOG_TAG, "Discarding draft messages");
                // Discarding draft messages is destructive in a "draft" mailbox
                if (mFolder != null && mFolder.isDraft()) {
                    for (Conversation conv : mTarget) {
                        conv.localDeleteOnUpdate = true;
                    }
                }
                mConversationListCursor.discardDrafts(mTarget);
                // We don't support undoing discarding drafts
                undoEnabled = false;
            } else if (mAction == R.id.discard_outbox) {
                LogUtils.d(LOG_TAG, "Discarding failed messages in Outbox");
                mConversationListCursor.moveFailedIntoDrafts(mTarget);
                undoEnabled = false;
            }
            if (undoEnabled && mTarget.size() > 0) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onUndoAvailable(new ToastBarOperation(mTarget.size(), mAction,
                                ToastBarOperation.UNDO, mIsSelectedSet, mFolder));
                    }
                }, mShowUndoBarDelay);
            }
            refreshConversationList();
            if (mIsSelectedSet) {
                mCheckedSet.clear();
            }
        }

        /**
         * Returns true if this action has been performed, false otherwise.
         *
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    // Called from the FolderSelectionDialog after a user is done selecting folders to assign the
    // conversations to.
    @Override
    public final void assignFolder(Collection<FolderOperation> folderOps,
            Collection<Conversation> target, boolean batch, boolean showUndo,
            final boolean isMoveTo) {
        // Actions are destructive only when the current folder can be un-assigned from and
        // when the list of folders contains the current folder.
        final boolean isDestructive = mFolder
                .supportsCapability(FolderCapabilities.ALLOWS_REMOVE_CONVERSATION)
                && FolderOperation.isDestructive(folderOps, mFolder);
        LogUtils.d(LOG_TAG, "onFolderChangesCommit: isDestructive = %b", isDestructive);
        if (isDestructive) {
            for (final Conversation c : target) {
                c.localDeleteOnUpdate = true;
            }
        }
        final DestructiveAction folderChange;
        final UndoCallback undoCallback = isMoveTo ?
                getUndoCallbackForDestructiveActionsWithAutoAdvance(R.id.move_to,
                        mCurrentConversation)
                : null;
        // Update the UI elements depending no their visibility and availability
        // TODO(viki): Consolidate this into a single method requestDelete.
        if (isDestructive) {
            /*
             * If this is a MOVE operation, we want the action folder to be the destination folder.
             * Otherwise, we want it to be the current folder.
             *
             * A set of folder operations is a move if there are exactly two operations: an add and
             * a remove.
             */
            final Folder actionFolder;
            if (folderOps.size() != 2) {
                actionFolder = mFolder;
            } else {
                Folder addedFolder = null;
                boolean hasRemove = false;
                for (final FolderOperation folderOperation : folderOps) {
                    if (folderOperation.mAdd) {
                        addedFolder = folderOperation.mFolder;
                    } else {
                        hasRemove = true;
                    }
                }

                if (hasRemove && addedFolder != null) {
                    actionFolder = addedFolder;
                } else {
                    actionFolder = mFolder;
                }
            }

            folderChange = getDeferredFolderChange(target, folderOps, isDestructive,
                    batch, showUndo, isMoveTo, actionFolder, undoCallback);
            delete(0, target, folderChange, batch);
        } else {
            folderChange = getFolderChange(target, folderOps, isDestructive,
                    batch, showUndo, false /* isMoveTo */, mFolder, undoCallback);
            requestUpdate(folderChange);
        }
    }

    @Override
    public final void onRefreshRequired() {
        if (isAnimating()) {
            final ConversationListFragment f = getConversationListFragment();
            LogUtils.w(ConversationCursor.LOG_TAG,
                    "onRefreshRequired: delay until animating done. cursor=%s adapter=%s",
                    mConversationListCursor, (f != null) ? f.getAnimatedAdapter() : null);
            return;
        }
        // Refresh the query in the background
        if (mConversationListCursor.isRefreshRequired()) {
            mConversationListCursor.refresh();
        }
    }

    @Override
    public boolean isAnimating() {
        boolean isAnimating = false;
        ConversationListFragment convListFragment = getConversationListFragment();
        if (convListFragment != null) {
            isAnimating = convListFragment.isAnimating();
        }
        return isAnimating;
    }

    /**
     * Called when the {@link ConversationCursor} is changed or has new data in it.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public final void onRefreshReady() {
        LogUtils.d(LOG_TAG, "Received refresh ready callback for folder %s",
                mFolder != null ? mFolder.id : "-1");

        if (mDestroyed) {
            LogUtils.i(LOG_TAG, "ignoring onRefreshReady on destroyed AAC");
            return;
        }

        if (!isAnimating()) {
            // Swap cursors
            mConversationListCursor.sync();
        } else {
            // (CLF guaranteed to be non-null due to check in isAnimating)
            LogUtils.w(LOG_TAG,
                    "AAC.onRefreshReady suppressing sync() due to animation. cursor=%s aa=%s",
                    mConversationListCursor, getConversationListFragment().getAnimatedAdapter());
        }
        mTracker.onCursorUpdated();
        perhapsShowFirstConversation();
    }

    @Override
    public final void onDataSetChanged() {
        updateConversationListFragment();
        mConversationListObservable.notifyChanged();
        mCheckedSet.validateAgainstCursor(mConversationListCursor);
    }

    /**
     * If the Conversation List Fragment is visible, updates the fragment.
     */
    private void updateConversationListFragment() {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList != null) {
            refreshConversationList();
            if (isFragmentVisible(convList)) {
                informCursorVisiblity(true);
            }
        }
    }

    /**
     * This class handles throttled refresh of the conversation list
     */
    static class RefreshTimerTask extends TimerTask {
        final Handler mHandler;
        final AbstractActivityController mController;

        RefreshTimerTask(AbstractActivityController controller, Handler handler) {
            mHandler = handler;
            mController = controller;
        }

        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    LogUtils.d(LOG_TAG, "Delay done... calling onRefreshRequired");
                    mController.onRefreshRequired();
                }});
        }
    }

    /**
     * Cancel the refresh task, if it's running
     */
    private void cancelRefreshTask () {
        if (mConversationListRefreshTask != null) {
            mConversationListRefreshTask.cancel();
            mConversationListRefreshTask = null;
        }
    }

    @Override
    public void onAnimationEnd(AnimatedAdapter animatedAdapter) {
        if (animatedAdapter != null) {
            LogUtils.i(LOG_TAG, "AAC.onAnimationEnd. cursor=%s adapter=%s", mConversationListCursor,
                    animatedAdapter);
        }
        if (mConversationListCursor == null) {
            LogUtils.e(LOG_TAG, "null ConversationCursor in onAnimationEnd");
            return;
        }
        if (mConversationListCursor.isRefreshReady()) {
            LogUtils.i(ConversationCursor.LOG_TAG, "Stopped animating: try sync");
            onRefreshReady();
        }

        if (mConversationListCursor.isRefreshRequired()) {
            LogUtils.i(ConversationCursor.LOG_TAG, "Stopped animating: refresh");
            mConversationListCursor.refresh();
        }
        if (mRecentsDataUpdated) {
            mRecentsDataUpdated = false;
            mRecentFolderObservers.notifyChanged();
        }
    }

    @Override
    public void onSetEmpty() {
        // There are no selected conversations. Ensure that the listener and its associated actions
        // are blanked out.
        setListener(null, -1);
    }

    @Override
    public void onSetPopulated(ConversationCheckedSet set) {
        mCabActionMenu = new SelectedConversationsActionMenu(mActivity, set, mFolder);
        if (mViewMode.isListMode() || (mIsTablet && mViewMode.isConversationMode())) {
            enableCabMode();
        }
    }

    @Override
    public void onSetChanged(ConversationCheckedSet set) {
        // Do nothing. We don't care about changes to the set.
    }

    @Override
    public ConversationCheckedSet getCheckedSet() {
        return mCheckedSet;
    }

    /**
     * Disable the Contextual Action Bar (CAB). The selected set is not changed.
     */
    protected void disableCabMode() {
        // Commit any previous destructive actions when entering/ exiting CAB mode.
        commitDestructiveActions(true);
        if (mCabActionMenu != null) {
            mCabActionMenu.deactivate();
        }
    }

    /**
     * Re-enable the CAB menu if required. The selection set is not changed.
     */
    protected void enableCabMode() {
        if (mCabActionMenu != null &&
                !(isDrawerEnabled() && mDrawerContainer.isDrawerOpen(mDrawerPullout))) {
            mCabActionMenu.activate();
        }
    }

    /**
     * Re-enable CAB mode only if we have an active selection
     */
    protected void maybeEnableCabMode() {
        if (!mCheckedSet.isEmpty()) {
            if (mCabActionMenu != null) {
                mCabActionMenu.activate();
            }
        }
    }

    /**
     * Unselect conversations and exit CAB mode.
     */
    protected final void exitCabMode() {
        mCheckedSet.clear();
    }

    @Override
    public void startSearch() {
        if (mAccount == null) {
            // We cannot search if there is no account. Drop the request to the floor.
            LogUtils.d(LOG_TAG, "AbstractActivityController.startSearch(): null account");
            return;
        }
        if (mAccount.supportsSearch()) {
            mSearchViewController.showSearchActionBar(
                    MaterialSearchViewController.SEARCH_VIEW_STATE_VISIBLE);
        } else {
            Toast.makeText(mActivity.getActivityContext(), mActivity.getActivityContext()
                    .getString(R.string.search_unsupported), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (mToastBar != null && !mToastBar.isEventInToastBar(event)) {
                // if the toast bar is still animating, ignore this attempt to hide it
                if (mToastBar.isAnimating()) {
                    return;
                }

                // if the toast bar has not been seen long enough, ignore this attempt to hide it
                if (mToastBar.cannotBeHidden()) {
                    return;
                }

                // hide the toast bar
                mToastBar.hide(true /* animated */, false /* actionClicked */);
            }
        }
    }

    @Override
    public void onConversationSeen() {
        mPagerController.onConversationSeen();
    }

    @Override
    public boolean isInitialConversationLoading() {
        return mPagerController.isInitialConversationLoading();
    }

    /**
     * Check if the fragment given here is visible. Checking {@link Fragment#isVisible()} is
     * insufficient because that doesn't check if the window is currently in focus or not.
     */
    private boolean isFragmentVisible(Fragment in) {
        return in != null && in.isVisible() && mActivity.hasWindowFocus();
    }

    /**
     * This class handles callbacks that create a {@link ConversationCursor}.
     */
    private class ConversationListLoaderCallbacks implements
        LoaderManager.LoaderCallbacks<ConversationCursor> {

        @Override
        public Loader<ConversationCursor> onCreateLoader(int id, Bundle args) {
            final Account account = args.getParcelable(BUNDLE_ACCOUNT_KEY);
            final Folder folder = args.getParcelable(BUNDLE_FOLDER_KEY);
            final boolean ignoreInitialConversationLimit =
                    args.getBoolean(BUNDLE_IGNORE_INITIAL_CONVERSATION_LIMIT_KEY, false);
            if (account == null || folder == null) {
                return null;
            }
            return new ConversationCursorLoader(mActivity, account,
                    folder.conversationListUri, folder.getTypeDescription(),
                    ignoreInitialConversationLimit);
        }

        @Override
        public void onLoadFinished(Loader<ConversationCursor> loader, ConversationCursor data) {
            LogUtils.d(LOG_TAG,
                    "IN AAC.ConversationCursor.onLoadFinished, data=%s loader=%s this=%s",
                    data, loader, this);
            if (isDestroyed()) {
                return;
            }
            if (isDrawerEnabled() && mDrawerListener.getDrawerState() != DrawerLayout.STATE_IDLE) {
                LogUtils.d(LOG_TAG, "ConversationListLoaderCallbacks.onLoadFinished: ignoring.");
                mConversationListLoadFinishedIgnored = true;
                return;
            }
            // Clear our all pending destructive actions before swapping the conversation cursor
            destroyPending(null);
            mConversationListCursor = data;
            mConversationListCursor.addListener(AbstractActivityController.this);
            mDrawIdler.setListener(mConversationListCursor);
            mTracker.onCursorUpdated();
            mConversationListObservable.notifyChanged();
            // Handle actions that were deferred until after the conversation list was loaded.
            for (LoadFinishedCallback callback : mConversationListLoadFinishedCallbacks) {
                callback.onLoadFinished();
            }
            mConversationListLoadFinishedCallbacks.clear();

            final ConversationListFragment convList = getConversationListFragment();
            if (isFragmentVisible(convList)) {
                // The conversation list is already listening to list changes and gets notified
                // in the mConversationListObservable.notifyChanged() line above. We only need to
                // check and inform the cursor of the change in visibility here.
                informCursorVisiblity(true);
            }
            perhapsShowFirstConversation();
        }

        @Override
        public void onLoaderReset(Loader<ConversationCursor> loader) {
            LogUtils.d(LOG_TAG,
                    "IN AAC.ConversationCursor.onLoaderReset, data=%s loader=%s this=%s",
                    mConversationListCursor, loader, this);

            if (mConversationListCursor != null) {
                // Unregister the listener
                mConversationListCursor.removeListener(AbstractActivityController.this);
                mDrawIdler.setListener(null);
                mConversationListCursor = null;

                // Inform anyone who is interested about the change
                mTracker.onCursorUpdated();
                mConversationListObservable.notifyChanged();
            }
        }
    }

    /**
     * Class to perform {@link LoaderManager.LoaderCallbacks} for creating {@link Folder} objects.
     */
    private class FolderLoads implements LoaderManager.LoaderCallbacks<ObjectCursor<Folder>> {
        @Override
        public Loader<ObjectCursor<Folder>> onCreateLoader(int id, Bundle args) {
            final String[] everything = UIProvider.FOLDERS_PROJECTION;
            switch (id) {
                case LOADER_FOLDER_CURSOR:
                    LogUtils.d(LOG_TAG, "LOADER_FOLDER_CURSOR created");
                    final ObjectCursorLoader<Folder> loader = new
                            ObjectCursorLoader<Folder>(
                            mContext, mFolder.folderUri.fullUri, everything, Folder.FACTORY);
                    loader.setUpdateThrottle(mFolderItemUpdateDelayMs);
                    return loader;
                case LOADER_RECENT_FOLDERS:
                    LogUtils.d(LOG_TAG, "LOADER_RECENT_FOLDERS created");
                    if (mAccount != null && mAccount.recentFolderListUri != null
                            && !mAccount.recentFolderListUri.equals(Uri.EMPTY)) {
                        return new ObjectCursorLoader<Folder>(mContext,
                                mAccount.recentFolderListUri, everything, Folder.FACTORY);
                    }
                    break;
                case LOADER_ACCOUNT_INBOX:
                    LogUtils.d(LOG_TAG, "LOADER_ACCOUNT_INBOX created");
                    final Uri defaultInbox = Settings.getDefaultInboxUri(mAccount.settings);
                    final Uri inboxUri = defaultInbox.equals(Uri.EMPTY) ?
                            mAccount.folderListUri : defaultInbox;
                    LogUtils.d(LOG_TAG, "Loading the default inbox: %s", inboxUri);
                    if (inboxUri != null) {
                        return new ObjectCursorLoader<Folder>(mContext, inboxUri,
                                everything, Folder.FACTORY);
                    }
                    break;
                case LOADER_SEARCH:
                    LogUtils.d(LOG_TAG, "LOADER_SEARCH created");
                    return Folder.forSearchResults(mAccount,
                            args.getString(ConversationListContext.EXTRA_SEARCH_QUERY),
                            // We can just use current time as a unique identifier for this search
                            Long.toString(SystemClock.uptimeMillis()),
                            mActivity.getActivityContext());
                case LOADER_FIRST_FOLDER:
                    LogUtils.d(LOG_TAG, "LOADER_FIRST_FOLDER created");
                    final Uri folderUri = args.getParcelable(Utils.EXTRA_FOLDER_URI);
                    mConversationToShow = args.getParcelable(Utils.EXTRA_CONVERSATION);
                    if (mConversationToShow != null && mConversationToShow.position < 0){
                        mConversationToShow.position = 0;
                    }
                    return new ObjectCursorLoader<Folder>(mContext, folderUri,
                            everything, Folder.FACTORY);
                default:
                    LogUtils.wtf(LOG_TAG, "FolderLoads.onCreateLoader(%d) for invalid id", id);
                    return null;
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<ObjectCursor<Folder>> loader, ObjectCursor<Folder> data) {
            if (data == null) {
                LogUtils.e(LOG_TAG, "Received null cursor from loader id: %d", loader.getId());
            }
            if (isDestroyed()) {
                return;
            }
            switch (loader.getId()) {
                case LOADER_FOLDER_CURSOR:
                    if (data != null && data.moveToFirst()) {
                        final Folder folder = data.getModel();
                        setHasFolderChanged(folder);
                        mFolder = folder;
                        mFolderObservable.notifyChanged();
                    } else {
                        LogUtils.d(LOG_TAG, "Unable to get the folder %s",
                                mFolder != null ? mFolder.name : "");
                    }
                    break;
                case LOADER_RECENT_FOLDERS:
                    // Few recent folders and we are running on a phone? Populate the default
                    // recents. The number of default recent folders is at least 2: every provider
                    // has at least two folders, and the recent folder count never decreases.
                    // Having a single recent folder is an erroneous case, and we can gracefully
                    // recover by populating default recents. The default recents will not stomp on
                    // the existing value: it will be shown in addition to the default folders:
                    // the max number of recent folders is more than 1+num(defaultRecents).
                    if (data != null && data.getCount() <= 1 && !mIsTablet) {
                        final class PopulateDefault extends AsyncTask<Uri, Void, Void> {
                            @Override
                            protected Void doInBackground(Uri... uri) {
                                // Asking for an update on the URI and ignore the result.
                                final ContentResolver resolver = mContext.getContentResolver();
                                resolver.update(uri[0], null, null, null);
                                return null;
                            }
                        }
                        final Uri uri = mAccount.defaultRecentFolderListUri;
                        LogUtils.v(LOG_TAG, "Default recents at %s", uri);
                        new PopulateDefault().execute(uri);
                        break;
                    }
                    LogUtils.v(LOG_TAG, "Reading recent folders from the cursor.");
                    mRecentFolderList.loadFromUiProvider(data);
                    if (isAnimating()) {
                        mRecentsDataUpdated = true;
                    } else {
                        mRecentFolderObservers.notifyChanged();
                    }
                    break;
                case LOADER_ACCOUNT_INBOX:
                    if (data != null && !data.isClosed() && data.moveToFirst()) {
                        final Folder inbox = data.getModel();
                        onFolderChanged(inbox, false /* force */);
                        // Just want to get the inbox, don't care about updates to it
                        // as this will be tracked by the folder change listener.
                        mActivity.getLoaderManager().destroyLoader(LOADER_ACCOUNT_INBOX);
                    } else {
                        LogUtils.d(LOG_TAG, "Unable to get the account inbox for account %s",
                                mAccount != null ? mAccount.getEmailAddress() : "");
                    }
                    break;
                case LOADER_SEARCH:
                    if (data != null && data.getCount() > 0) {
                        data.moveToFirst();
                        final Folder search = data.getModel();
                        updateFolder(search);
                        mConvListContext = ConversationListContext.forSearchQuery(mAccount, mFolder,
                                mActivity.getIntent()
                                        .getStringExtra(UIProvider.SearchQueryParameters.QUERY));
                        showConversationList(mConvListContext);
                        mActivity.invalidateOptionsMenu();
                        mHaveSearchResults = search.totalCount > 0;
                        mActivity.getLoaderManager().destroyLoader(LOADER_SEARCH);
                    } else {
                        LogUtils.e(LOG_TAG, "Null/empty cursor returned by LOADER_SEARCH loader");
                    }
                    break;
                case LOADER_FIRST_FOLDER:
                    if (data == null || data.getCount() <=0 || !data.moveToFirst()) {
                        return;
                    }
                    final Folder folder = data.getModel();
                    boolean handled = false;
                    if (folder != null) {
                        onFolderChanged(folder, false /* force */);
                        handled = true;
                    }
                    if (mConversationToShow != null) {
                        // Open the conversation.
                        showConversation(mConversationToShow);
                        handled = true;
                    }
                    if (!handled) {
                        // We have an account, but nothing else: load the default inbox.
                        loadAccountInbox();
                    }
                    mConversationToShow = null;
                    // And don't run this anymore.
                    mActivity.getLoaderManager().destroyLoader(LOADER_FIRST_FOLDER);
                    break;
            }
        }

        @Override
        public void onLoaderReset(Loader<ObjectCursor<Folder>> loader) {
        }
    }

    /**
     * Class to perform {@link LoaderManager.LoaderCallbacks} for creating {@link Account} objects.
     */
    private class AccountLoads implements LoaderManager.LoaderCallbacks<ObjectCursor<Account>> {
        final String[] mProjection = UIProvider.ACCOUNTS_PROJECTION;
        final CursorCreator<Account> mFactory = Account.FACTORY;

        @Override
        public Loader<ObjectCursor<Account>> onCreateLoader(int id, Bundle args) {
            switch (id) {
                case LOADER_ACCOUNT_CURSOR:
                    LogUtils.d(LOG_TAG,  "LOADER_ACCOUNT_CURSOR created");
                    return new ObjectCursorLoader<Account>(mContext,
                            MailAppProvider.getAccountsUri(), mProjection, mFactory);
                case LOADER_ACCOUNT_UPDATE_CURSOR:
                    LogUtils.d(LOG_TAG,  "LOADER_ACCOUNT_UPDATE_CURSOR created");
                    return new ObjectCursorLoader<Account>(mContext, mAccount.uri, mProjection,
                            mFactory);
                default:
                    LogUtils.wtf(LOG_TAG, "Got an id  (%d) that I cannot create!", id);
                    break;
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<ObjectCursor<Account>> loader,
                ObjectCursor<Account> data) {
            if (data == null) {
                LogUtils.e(LOG_TAG, "Received null cursor from loader id: %d", loader.getId());
            }
            if (isDestroyed()) {
                return;
            }
            switch (loader.getId()) {
                case LOADER_ACCOUNT_CURSOR:
                    // We have received an update on the list of accounts.
                    if (data == null) {
                        // Nothing useful to do if we have no valid data.
                        break;
                    }
                    final long count = data.getCount();
                    if (count == 0) {
                        // If an empty cursor is returned, the MailAppProvider is indicating that
                        // no accounts have been specified.  We want to navigate to the
                        // "add account" activity that will handle the intent returned by the
                        // MailAppProvider

                        // If the MailAppProvider believes that all accounts have been loaded,
                        // and the account list is still empty, we want to prompt the user to add
                        // an account.
                        final Bundle extras = data.getExtras();
                        final boolean accountsLoaded =
                                extras.getInt(AccountCursorExtraKeys.ACCOUNTS_LOADED) != 0;

                        if (accountsLoaded) {
                            final Intent noAccountIntent = MailAppProvider.getNoAccountIntent
                                    (mContext);
                            if (noAccountIntent != null) {
                                mActivity.startActivityForResult(noAccountIntent,
                                        ADD_ACCOUNT_REQUEST_CODE);
                            }
                        }
                    } else {
                        final boolean accountListUpdated = accountsUpdated(data);
                        if (!mHaveAccountList || accountListUpdated) {
                            mHaveAccountList = updateAccounts(data);
                        }
                        Analytics.getInstance().setCustomDimension(Analytics.CD_INDEX_ACCOUNT_COUNT,
                                Long.toString(count));
                    }
                    break;
                case LOADER_ACCOUNT_UPDATE_CURSOR:
                    // We have received an update for current account.
                    if (data != null && data.moveToFirst()) {
                        final Account updatedAccount = data.getModel();
                        // Make sure that this is an update for the current account
                        if (updatedAccount.uri.equals(mAccount.uri)) {
                            final Settings previousSettings = mAccount.settings;

                            // Update the controller's reference to the current account
                            mAccount = updatedAccount;
                            LogUtils.d(LOG_TAG, "AbstractActivityController.onLoadFinished(): "
                                    + "mAccount = %s", mAccount.uri);

                            // Only notify about a settings change if something differs
                            if (!Objects.equal(mAccount.settings, previousSettings)) {
                                mAccountObservers.notifyChanged();
                            }
                            perhapsEnterWaitMode();
                        } else {
                            LogUtils.e(LOG_TAG, "Got update for account: %s with current account:"
                                    + " %s", updatedAccount.uri, mAccount.uri);
                            // We need to restart the loader, so the correct account information
                            // will be returned.
                            restartOptionalLoader(LOADER_ACCOUNT_UPDATE_CURSOR, this, Bundle.EMPTY);
                        }
                    }
                    break;
            }
        }

        @Override
        public void onLoaderReset(Loader<ObjectCursor<Account>> loader) {
            // Do nothing. In onLoadFinished() we copy the relevant data from the cursor.
        }
    }

    /**
     * Updates controller state based on search results and shows first conversation if required.
     * Be sure to call the super-implementation if overriding.
     */
    protected void perhapsShowFirstConversation() {
        mHaveSearchResults = Intent.ACTION_SEARCH.equals(mActivity.getIntent().getAction())
                && mConversationListCursor.getCount() > 0;
    }

    /**
     * Destroy the pending {@link DestructiveAction} till now and assign the given action as the
     * next destructive action..
     * @param nextAction the next destructive action to be performed. This can be null.
     */
    private void destroyPending(DestructiveAction nextAction) {
        // If there is a pending action, perform that first.
        if (mPendingDestruction != null) {
            mPendingDestruction.performAction();
        }
        mPendingDestruction = nextAction;
    }

    /**
     * Register a destructive action with the controller. This performs the previous destructive
     * action as a side effect. This method is final because we don't want the child classes to
     * embellish this method any more.
     * @param action the action to register.
     */
    private void registerDestructiveAction(DestructiveAction action) {
        // TODO(viki): This is not a good idea. The best solution is for clients to request a
        // destructive action from the controller and for the controller to own the action. This is
        // a half-way solution while refactoring DestructiveAction.
        destroyPending(action);
    }

    @Override
    public final DestructiveAction getBatchAction(int action, UndoCallback undoCallback) {
        final DestructiveAction da = new ConversationAction(action, mCheckedSet.values(), true);
        da.setUndoCallback(undoCallback);
        registerDestructiveAction(da);
        return da;
    }

    @Override
    public final DestructiveAction getDeferredBatchAction(int action, UndoCallback undoCallback) {
        return getDeferredAction(action, mCheckedSet.values(), true, undoCallback);
    }

    /**
     * Get a destructive action for a menu action. This is a temporary method,
     * to control the profusion of {@link DestructiveAction} classes that are
     * created. Please do not copy this paradigm.
     * @param action the resource ID of the menu action: R.id.delete, for
     *            example
     * @param target the conversations to act upon.
     * @return a {@link DestructiveAction} that performs the specified action.
     */
    private DestructiveAction getDeferredAction(int action, Collection<Conversation> target,
            boolean batch, UndoCallback callback) {
        ConversationAction cAction = new ConversationAction(action, target, batch);
        cAction.setUndoCallback(callback);
        return cAction;
    }

    /**
     * Class to change the folders that are assigned to a set of conversations. This is destructive
     * because the user can remove the current folder from the conversation, in which case it has
     * to be animated away from the current folder.
     */
    private class FolderDestruction implements DestructiveAction {
        private final Collection<Conversation> mTarget;
        private final ArrayList<FolderOperation> mFolderOps = new ArrayList<FolderOperation>();
        private final boolean mIsDestructive;
        /** Whether this destructive action has already been performed */
        private boolean mCompleted;
        private final boolean mIsSelectedSet;
        private final boolean mShowUndo;
        private final int mAction;
        private final Folder mActionFolder;

        private UndoCallback mUndoCallback;

        /**
         * Create a new folder destruction object to act on the given conversations.
         * @param target conversations to act upon.
         * @param actionFolder the {@link Folder} being acted upon, used for displaying the undo bar
         */
        private FolderDestruction(final Collection<Conversation> target,
                final Collection<FolderOperation> folders, boolean isDestructive, boolean isBatch,
                boolean showUndo, int action, final Folder actionFolder) {
            mTarget = ImmutableList.copyOf(target);
            mFolderOps.addAll(folders);
            mIsDestructive = isDestructive;
            mIsSelectedSet = isBatch;
            mShowUndo = showUndo;
            mAction = action;
            mActionFolder = actionFolder;
        }

        @Override
        public void setUndoCallback(UndoCallback undoCallback) {
            mUndoCallback = undoCallback;
        }

        @Override
        public void performAction() {
            if (isPerformed()) {
                return;
            }
            if (mIsDestructive && mShowUndo && mTarget.size() > 0) {
                ToastBarOperation undoOp = new ToastBarOperation(mTarget.size(), mAction,
                        ToastBarOperation.UNDO, mIsSelectedSet, mActionFolder);
                onUndoAvailable(undoOp);
            }
            // For each conversation, for each operation, add/ remove the
            // appropriate folders.
            ArrayList<ConversationOperation> ops = new ArrayList<ConversationOperation>();
            ArrayList<Uri> folderUris;
            ArrayList<Boolean> adds;
            for (Conversation target : mTarget) {
                HashMap<Uri, Folder> targetFolders = Folder.hashMapForFolders(target
                        .getRawFolders());
                folderUris = new ArrayList<Uri>();
                adds = new ArrayList<Boolean>();
                if (mIsDestructive) {
                    target.localDeleteOnUpdate = true;
                }
                for (FolderOperation op : mFolderOps) {
                    folderUris.add(op.mFolder.folderUri.fullUri);
                    adds.add(op.mAdd ? Boolean.TRUE : Boolean.FALSE);
                    if (op.mAdd) {
                        targetFolders.put(op.mFolder.folderUri.fullUri, op.mFolder);
                    } else {
                        targetFolders.remove(op.mFolder.folderUri.fullUri);
                    }
                }
                ops.add(mConversationListCursor.getConversationFolderOperation(target,
                        folderUris, adds, targetFolders.values(), mUndoCallback));
            }
            if (mConversationListCursor != null) {
                mConversationListCursor.updateBulkValues(ops);
            }
            refreshConversationList();
            if (mIsSelectedSet) {
                mCheckedSet.clear();
            }
        }

        /**
         * Returns true if this action has been performed, false otherwise.
         *
         */
        private synchronized boolean isPerformed() {
            if (mCompleted) {
                return true;
            }
            mCompleted = true;
            return false;
        }
    }

    public final DestructiveAction getFolderChange(Collection<Conversation> target,
            Collection<FolderOperation> folders, boolean isDestructive, boolean isBatch,
            boolean showUndo, final boolean isMoveTo, final Folder actionFolder,
            UndoCallback undoCallback) {
        final DestructiveAction da = getDeferredFolderChange(target, folders, isDestructive,
                isBatch, showUndo, isMoveTo, actionFolder, undoCallback);
        registerDestructiveAction(da);
        return da;
    }

    public final DestructiveAction getDeferredFolderChange(Collection<Conversation> target,
            Collection<FolderOperation> folders, boolean isDestructive, boolean isBatch,
            boolean showUndo, final boolean isMoveTo, final Folder actionFolder,
            UndoCallback undoCallback) {
        final DestructiveAction fd = new FolderDestruction(target, folders, isDestructive, isBatch,
                showUndo, isMoveTo ? R.id.move_folder : R.id.change_folders, actionFolder);
        fd.setUndoCallback(undoCallback);
        return fd;
    }

    @Override
    public final DestructiveAction getDeferredRemoveFolder(Collection<Conversation> target,
            Folder toRemove, boolean isDestructive, boolean isBatch,
            boolean showUndo, UndoCallback undoCallback) {
        Collection<FolderOperation> folderOps = new ArrayList<FolderOperation>();
        folderOps.add(new FolderOperation(toRemove, false));
        final DestructiveAction da = new FolderDestruction(target, folderOps, isDestructive, isBatch,
                showUndo, R.id.remove_folder, mFolder);
        da.setUndoCallback(undoCallback);
        return da;
    }

    @Override
    public final void refreshConversationList() {
        final ConversationListFragment convList = getConversationListFragment();
        if (convList == null) {
            return;
        }
        convList.requestListRefresh();
    }

    protected final ActionClickedListener getUndoClickedListener(
            final AnimatedAdapter listAdapter) {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                if (mAccount.undoUri != null) {
                    // NOTE: We might want undo to return the messages affected, in which case
                    // the resulting cursor might be interesting...
                    // TODO: Use UIProvider.SEQUENCE_QUERY_PARAMETER to indicate the set of
                    // commands to undo
                    if (mConversationListCursor != null) {
                        mConversationListCursor.undo(
                                mActivity.getActivityContext(), mAccount.undoUri);
                    }
                    if (listAdapter != null) {
                        listAdapter.setUndo(true);
                    }
                }
            }
        };
    }

    /**
     * Shows an error toast in the bottom when a folder was not fetched successfully.
     * @param folder the folder which could not be fetched.
     * @param replaceVisibleToast if true, this should replace any currently visible toast.
     */
    protected final void showErrorToast(final Folder folder, boolean replaceVisibleToast) {

        final ActionClickedListener listener;
        final int actionTextResourceId;
        final int lastSyncResult = folder.lastSyncResult;
        switch (UIProvider.getResultFromLastSyncResult(lastSyncResult)) {
            case UIProvider.LastSyncResult.CONNECTION_ERROR:
                // The sync status that caused this failure.
                final int syncStatus = UIProvider.getStatusFromLastSyncResult(lastSyncResult);
                // Show: User explicitly pressed the refresh button and there is no connection
                // Show: The first time the user enters the app and there is no connection
                //       TODO(viki): Implement this.
                // Reference: http://b/7202801
                final boolean showToast = (syncStatus & UIProvider.SyncStatus.USER_REFRESH) != 0;
                // Don't show: Already in the app; user switches to a synced label
                // Don't show: In a live label and a background sync fails
                final boolean avoidToast = !showToast && (folder.syncWindow > 0
                        || (syncStatus & UIProvider.SyncStatus.BACKGROUND_SYNC) != 0);
                if (avoidToast) {
                    return;
                }
                listener = getRetryClickedListener(folder);
                actionTextResourceId = R.string.retry;
                break;
            case UIProvider.LastSyncResult.AUTH_ERROR:
                listener = getSignInClickedListener();
                actionTextResourceId = R.string.signin;
                break;
            case UIProvider.LastSyncResult.SECURITY_ERROR:
                return; // Currently we do nothing for security errors.
            case UIProvider.LastSyncResult.STORAGE_ERROR:
                listener = getStorageErrorClickedListener();
                actionTextResourceId = R.string.info;
                break;
            case UIProvider.LastSyncResult.INTERNAL_ERROR:
                listener = getInternalErrorClickedListener();
                actionTextResourceId = R.string.report;
                break;
            default:
                return;
        }
        mToastBar.show(listener,
                Utils.getSyncStatusText(mActivity.getActivityContext(), lastSyncResult),
                actionTextResourceId,
                replaceVisibleToast,
                true /* autohide */,
                new ToastBarOperation(1, 0, ToastBarOperation.ERROR, false, folder));
    }

    private ActionClickedListener getRetryClickedListener(final Folder folder) {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                final Uri uri = folder.refreshUri;

                if (uri != null) {
                    startAsyncRefreshTask(uri);
                }
            }
        };
    }

    private ActionClickedListener getSignInClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                promptUserForAuthentication(mAccount);
            }
        };
    }

    private ActionClickedListener getStorageErrorClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                showStorageErrorDialog();
            }
        };
    }

    private void showStorageErrorDialog() {
        DialogFragment fragment = (DialogFragment)
                mFragmentManager.findFragmentByTag(SYNC_ERROR_DIALOG_FRAGMENT_TAG);
        if (fragment == null) {
            fragment = SyncErrorDialogFragment.newInstance();
        }
        fragment.show(mFragmentManager, SYNC_ERROR_DIALOG_FRAGMENT_TAG);
    }

    private ActionClickedListener getInternalErrorClickedListener() {
        return new ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                Utils.sendFeedback(mActivity, mAccount, true /* reportingProblem */);
            }
        };
    }

    @Override
    public void onFooterViewLoadMoreClick(Folder folder) {
        if (folder != null && folder.loadMoreUri != null) {
            startAsyncRefreshTask(folder.loadMoreUri);
        }
    }

    private void startAsyncRefreshTask(Uri uri) {
        if (mFolderSyncTask != null) {
            mFolderSyncTask.cancel(true);
        }
        mFolderSyncTask = new AsyncRefreshTask(mActivity.getActivityContext(), uri);
        mFolderSyncTask.execute();
    }

    private void promptUserForAuthentication(Account account) {
        if (account != null && !Utils.isEmpty(account.reauthenticationIntentUri)) {
            final Intent authenticationIntent =
                    new Intent(Intent.ACTION_VIEW, account.reauthenticationIntentUri);
            mActivity.startActivityForResult(authenticationIntent, REAUTHENTICATE_REQUEST_CODE);
        }
    }

    @Override
    public void onAccessibilityStateChanged() {
        // Clear the cache of objects.
        ConversationItemViewModel.onAccessibilityUpdated();
        // Re-render the list if it exists.
        final ConversationListFragment frag = getConversationListFragment();
        if (frag != null) {
            AnimatedAdapter adapter = frag.getAnimatedAdapter();
            if (adapter != null) {
                adapter.notifyDataSetInvalidated();
            }
        }
    }

    @Override
    public void makeDialogListener (final int action, final boolean isBatch,
            UndoCallback undoCallback) {
        final Collection<Conversation> target;
        if (isBatch) {
            target = mCheckedSet.values();
        } else {
            LogUtils.d(LOG_TAG, "Will act upon %s", mCurrentConversation);
            target = Conversation.listOf(mCurrentConversation);
        }
        final DestructiveAction destructiveAction = getDeferredAction(action, target, isBatch,
                undoCallback);
        mDialogAction = action;
        mDialogFromSelectedSet = isBatch;
        mDialogListener = new AlertDialog.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                delete(action, target, destructiveAction, isBatch);
                // Afterwards, let's remove references to the listener and the action.
                setListener(null, -1);
            }
        };
    }

    @Override
    public AlertDialog.OnClickListener getListener() {
        return mDialogListener;
    }

    /**
     * Sets the listener for the positive action on a confirmation dialog.  Since only a single
     * confirmation dialog can be shown, this overwrites the previous listener.  It is safe to
     * unset the listener; in which case action should be set to -1.
     * @param listener the listener that will perform the task for this dialog's positive action.
     * @param action the action that created this dialog.
     */
    private void setListener(AlertDialog.OnClickListener listener, final int action){
        mDialogListener = listener;
        mDialogAction = action;
    }

    @Override
    public VeiledAddressMatcher getVeiledAddressMatcher() {
        return mVeiledMatcher;
    }

    @Override
    public void setDetachedMode() {
        // Tell the conversation list not to select anything.
        final ConversationListFragment frag = getConversationListFragment();
        if (frag != null) {
            frag.setChoiceNone();
        } else if (mIsTablet) {
            // How did we ever land here? Detached mode, and no CLF on tablet???
            LogUtils.e(LOG_TAG, "AAC.setDetachedMode(): CLF = null!");
        }
        mDetachedConvUri = mCurrentConversation.uri;
    }

    private void clearDetachedMode() {
        // Tell the conversation list to go back to its usual selection behavior.
        final ConversationListFragment frag = getConversationListFragment();
        if (frag != null) {
            frag.revertChoiceMode();
        } else if (mIsTablet) {
            // How did we ever land here? Detached mode, and no CLF on tablet???
            LogUtils.e(LOG_TAG, "AAC.clearDetachedMode(): CLF = null on tablet!");
        }
        mDetachedConvUri = null;
    }

    @Override
    public boolean shouldPreventListSwipesEntirely() {
        return false;
    }

    @Override
    public DrawerController getDrawerController() {
        return mDrawerListener;
    }

    private class MailDrawerListener extends Observable<DrawerLayout.DrawerListener>
            implements DrawerLayout.DrawerListener, DrawerController {
        private int mDrawerState;
        private float mOldSlideOffset;

        public MailDrawerListener() {
            mDrawerState = DrawerLayout.STATE_IDLE;
            mOldSlideOffset = 0.f;
        }

        @Override
        public boolean isDrawerEnabled() {
            return AbstractActivityController.this.isDrawerEnabled();
        }

        @Override
        public void registerDrawerListener(DrawerLayout.DrawerListener l) {
            registerObserver(l);
        }

        @Override
        public void unregisterDrawerListener(DrawerLayout.DrawerListener l) {
            unregisterObserver(l);
        }

        @Override
        public boolean isDrawerOpen() {
            return isDrawerEnabled() && mDrawerContainer.isDrawerOpen(mDrawerPullout);
        }

        @Override
        public boolean isDrawerVisible() {
            return isDrawerEnabled() && mDrawerContainer.isDrawerVisible(mDrawerPullout);
        }

        @Override
        public void toggleDrawerState() {
            AbstractActivityController.this.toggleDrawerState();
        }

        @Override
        public void onDrawerOpened(View drawerView) {
            mDrawerToggle.onDrawerOpened(drawerView);

            for (DrawerLayout.DrawerListener l : mObservers) {
                l.onDrawerOpened(drawerView);
            }
        }

        @Override
        public void onDrawerClosed(View drawerView) {
            mDrawerToggle.onDrawerClosed(drawerView);
            if (mHasNewAccountOrFolder) {
                refreshDrawer();
            }

            // When closed, we want to use either the burger, or up, based on where we are
            final int mode = mViewMode.getMode();
            final boolean isTopLevel = Folder.isRoot(mFolder);
            updateDrawerIndicator(mode, isTopLevel);

            for (DrawerLayout.DrawerListener l : mObservers) {
                l.onDrawerClosed(drawerView);
            }
        }

        /**
         * As part of the overriden function, it will animate the alpha of the conversation list
         * view along with the drawer sliding when we're in the process of switching accounts or
         * folders. Note, this is the same amount of work done as {@link ValueAnimator#ofFloat}.
         */
        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {
            mDrawerToggle.onDrawerSlide(drawerView, slideOffset);
            if (mHasNewAccountOrFolder && mListViewForAnimating != null) {
                mListViewForAnimating.setAlpha(slideOffset);
            }

            // This code handles when to change the visibility of action items
            // based on drawer state. The basic logic is that right when we
            // open the drawer, we hide the action items. We show the action items
            // when the drawer closes. However, due to the animation of the drawer closing,
            // to make the reshowing of the action items feel right, we make the items visible
            // slightly sooner.
            //
            // However, to make the animating behavior work properly, we have to know whether
            // we're animating open or closed. Only if we're animating closed do we want to
            // show the action items early. We save the last slide offset so that we can compare
            // the current slide offset to it to determine if we're opening or closing.
            if (mDrawerState == DrawerLayout.STATE_SETTLING) {
                if (mHideMenuItems && slideOffset < 0.15f && mOldSlideOffset > slideOffset) {
                    mHideMenuItems = false;
                    mActivity.supportInvalidateOptionsMenu();
                    maybeEnableCabMode();
                } else if (!mHideMenuItems && slideOffset > 0.f && mOldSlideOffset < slideOffset) {
                    mHideMenuItems = true;
                    mActivity.supportInvalidateOptionsMenu();
                    disableCabMode();
                }
            } else {
                if (mHideMenuItems && Float.compare(slideOffset, 0.f) == 0) {
                    mHideMenuItems = false;
                    mActivity.supportInvalidateOptionsMenu();
                    maybeEnableCabMode();
                } else if (!mHideMenuItems && slideOffset > 0.f) {
                    mHideMenuItems = true;
                    mActivity.supportInvalidateOptionsMenu();
                    disableCabMode();
                }
            }

            mOldSlideOffset = slideOffset;

            for (DrawerLayout.DrawerListener l : mObservers) {
                l.onDrawerSlide(drawerView, slideOffset);
            }
        }

        /**
         * This condition here should only be called when the drawer is stuck in a weird state
         * and doesn't register the onDrawerClosed, but shows up as idle. Make sure to refresh
         * and, more importantly, unlock the drawer when this is the case.
         */
        @Override
        public void onDrawerStateChanged(int newState) {
            LogUtils.d(LOG_TAG, "AAC onDrawerStateChanged %d", newState);
            mDrawerState = newState;
            mDrawerToggle.onDrawerStateChanged(mDrawerState);

            for (DrawerLayout.DrawerListener l : mObservers) {
                l.onDrawerStateChanged(newState);
            }

            if (mViewMode.isSearchMode()) {
                return;
            }
            if (mDrawerState == DrawerLayout.STATE_IDLE) {
                if (mHasNewAccountOrFolder) {
                    refreshDrawer();
                }
                if (mConversationListLoadFinishedIgnored) {
                    mConversationListLoadFinishedIgnored = false;
                    final Bundle args = new Bundle();
                    args.putParcelable(BUNDLE_ACCOUNT_KEY, mAccount);
                    args.putParcelable(BUNDLE_FOLDER_KEY, mFolder);
                    mActivity.getLoaderManager().initLoader(
                            LOADER_CONVERSATION_LIST, args, mListCursorCallbacks);
                }
            }
        }

        /**
         * If we've reached a stable drawer state, unlock the drawer for usage, clear the
         * conversation list, and finish end actions. Also, make
         * {@link #mHasNewAccountOrFolder} false to reflect we're done changing.
         */
        public void refreshDrawer() {
            mHasNewAccountOrFolder = false;
            mDrawerContainer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            ConversationListFragment conversationList = getConversationListFragment();
            if (conversationList != null) {
                conversationList.clear();
            }
            mFolderOrAccountObservers.notifyChanged();
        }

        /**
         * Returns the most recent update of the {@link DrawerLayout}'s state provided
         * by {@link #onDrawerStateChanged(int)}.
         * @return The {@link DrawerLayout}'s current state. One of
         * {@link DrawerLayout#STATE_DRAGGING}, {@link DrawerLayout#STATE_IDLE},
         * or {@link DrawerLayout#STATE_SETTLING}.
         */
        public int getDrawerState() {
            return mDrawerState;
        }
    }

    @Override
    public boolean isDrawerPullEnabled() {
        return true;
    }

    @Override
    public boolean shouldHideMenuItems() {
        return mHideMenuItems;
    }

    protected void navigateUpFolderHierarchy() {
        new AsyncTask<Void, Void, Folder>() {
            @Override
            protected Folder doInBackground(final Void... params) {
                if (mInbox == null) {
                    // We don't have an inbox, but we need it
                    final Cursor cursor = mContext.getContentResolver().query(
                            mAccount.settings.defaultInbox, UIProvider.FOLDERS_PROJECTION, null,
                            null, null);

                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                mInbox = new Folder(cursor);
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }

                // Now try to load our parent
                final Folder folder;

                if (mFolder != null) {
                    Cursor cursor = null;
                    try {
                        cursor = mContext.getContentResolver().query(mFolder.parent,
                                UIProvider.FOLDERS_PROJECTION, null, null, null);

                        if (cursor == null || !cursor.moveToFirst()) {
                            // We couldn't load the parent, so use the inbox
                            folder = mInbox;
                        } else {
                            folder = new Folder(cursor);
                        }
                    } finally {
                        if (cursor != null) {
                            cursor.close();
                        }
                    }
                } else {
                    folder = mInbox;
                }

                return folder;
            }

            @Override
            protected void onPostExecute(final Folder result) {
                onFolderSelected(result);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[]) null);
    }

    @Override
    public Parcelable getConversationListScrollPosition(final String folderUri) {
        return mConversationListScrollPositions.getParcelable(folderUri);
    }

    @Override
    public void setConversationListScrollPosition(final String folderUri,
            final Parcelable savedPosition) {
        mConversationListScrollPositions.putParcelable(folderUri, savedPosition);
    }

    @Override
    public boolean setupEmptyIconView(Folder folder, boolean isEmpty) {
        return false;
    }

    @Override
    public View.OnClickListener getNavigationViewClickListener() {
        return mHomeButtonListener;
    }

    // TODO: Fold this into the outer class when b/16627877 is fixed
    private class HomeButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            handleUpPress();
        }
    }
}
