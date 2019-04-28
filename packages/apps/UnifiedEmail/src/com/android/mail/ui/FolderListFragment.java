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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Loader;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.bitmap.BitmapCache;
import com.android.bitmap.UnrefedBitmapCache;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.bitmap.AccountAvatarDrawable;
import com.android.mail.bitmap.ContactResolver;
import com.android.mail.browse.MergedAdapter;
import com.android.mail.content.ObjectCursor;
import com.android.mail.content.ObjectCursorLoader;
import com.android.mail.drawer.DrawerItem;
import com.android.mail.drawer.FooterItem;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.AllAccountObserver;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderObserver;
import com.android.mail.providers.FolderWatcher;
import com.android.mail.providers.RecentFolderObserver;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.FolderType;
import com.android.mail.utils.FolderUri;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This fragment shows the list of folders and the list of accounts. Prior to June 2013,
 * the mail application had a spinner in the top action bar. Now, the list of accounts is displayed
 * in a drawer along with the list of folders.
 *
 * This class has the following use-cases:
 * <ul>
 *     <li>
 *         Show a list of accounts and a divided list of folders. In this case, the list shows
 *         Accounts, Inboxes, Recent Folders, All folders, Help, and Feedback.
 *         Tapping on Accounts takes the user to the default Inbox for that account. Tapping on
 *         folders switches folders. Tapping on Help takes the user to HTML help pages. Tapping on
 *         Feedback takes the user to a screen for submitting text and a screenshot of the
 *         application to a feedback system.
 *         This is created through XML resources as a {@link DrawerFragment}. Since it is created
 *         through resources, it receives all arguments through callbacks.
 *     </li>
 *     <li>
 *         Show a list of folders for a specific level. At the top-level, this shows Inbox, Sent,
 *         Drafts, Starred, and any user-created folders. For providers that allow nested folders,
 *         this will only show the folders at the top-level.
 *         <br /> Tapping on a parent folder creates a new fragment with the child folders at
 *         that level.
 *     </li>
 *     <li>
 *         Shows a list of folders that can be turned into widgets/shortcuts. This is used by the
 *         {@link FolderSelectionActivity} to allow the user to create a shortcut or widget for
 *         any folder for a given account.
 *     </li>
 * </ul>
 */
public class FolderListFragment extends ListFragment implements
        LoaderManager.LoaderCallbacks<ObjectCursor<Folder>>,
        FolderWatcher.UnreadCountChangedListener {
    private static final String LOG_TAG = LogTag.getLogTag();
    // Duration to fade alpha from 0 to 1 and vice versa.
    private static final long DRAWER_FADE_VELOCITY_MS_PER_ALPHA = TwoPaneLayout.SLIDE_DURATION_MS;

    /** The parent activity */
    protected ControllableActivity mActivity;
    /** The underlying list view */
    private ListView mListView;
    /** URI that points to the list of folders for the current account. */
    private Uri mFolderListUri;
    /**
     * True if you want a divided FolderList. A divided folder list shows the following groups:
     * Inboxes, Recent Folders, All folders.
     *
     * An undivided FolderList shows all folders without any divisions and without recent folders.
     * This is true only for the drawer: for all others it is false.
     */
    protected boolean mIsDivided = false;
    /**
     * True if the folder list belongs to a folder selection activity (one account only)
     * and the footer should not show.
     */
    protected boolean mIsFolderSelectionActivity = true;
    /** An {@link ArrayList} of {@link FolderType}s to exclude from displaying. */
    private ArrayList<Integer> mExcludedFolderTypes;
    /** Object that changes folders on our behalf. */
    private FolderSelector mFolderChanger;
    /** Object that changes accounts on our behalf */
    private AccountController mAccountController;
    private DrawerController mDrawerController;

    /** The currently selected folder (the folder being viewed).  This is never null. */
    private FolderUri mSelectedFolderUri = FolderUri.EMPTY;
    /**
     * The current folder from the controller.  This is meant only to check when the unread count
     * goes out of sync and fixing it.
     */
    private Folder mCurrentFolderForUnreadCheck;
    /** Parent of the current folder, or null if the current folder is not a child. */
    private Folder mParentFolder;

    private static final int FOLDER_LIST_LOADER_ID = 0;
    /** Loader id for the list of all folders in the account */
    private static final int ALL_FOLDER_LIST_LOADER_ID = 1;
    /** Key to store {@link #mParentFolder}. */
    private static final String ARG_PARENT_FOLDER = "arg-parent-folder";
    /** Key to store {@link #mFolderListUri}. */
    private static final String ARG_FOLDER_LIST_URI = "arg-folder-list-uri";
    /** Key to store {@link #mExcludedFolderTypes} */
    private static final String ARG_EXCLUDED_FOLDER_TYPES = "arg-excluded-folder-types";

    private static final String BUNDLE_LIST_STATE = "flf-list-state";
    private static final String BUNDLE_SELECTED_FOLDER = "flf-selected-folder";
    private static final String BUNDLE_SELECTED_ITEM_TYPE = "flf-selected-item-type";
    private static final String BUNDLE_SELECTED_TYPE = "flf-selected-type";
    private static final String BUNDLE_INBOX_PRESENT = "flf-inbox-present";

    /** Number of avatars to we whould like to fit in the avatar cache */
    private static final int IMAGE_CACHE_COUNT = 10;
    /**
     * This is the fractional portion of the total cache size above that's dedicated to non-pooled
     * bitmaps. (This is basically the portion of cache dedicated to GIFs.)
     */
    private static final float AVATAR_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION = 0f;
    /** Each string has upper estimate of 50 bytes, so this cache would be 5KB. */
    private static final int AVATAR_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY = 100;


    /** Adapter used by the list that wraps both the folder adapter and the accounts adapter. */
    private MergedAdapter<ListAdapter> mMergedAdapter;
    /** Adapter containing the list of accounts. */
    private AccountsAdapter mAccountsAdapter;
    /** Adapter containing the list of folders and, optionally, headers and the wait view. */
    private FolderListFragmentCursorAdapter mFolderAdapter;
    /** Adapter containing the Help and Feedback views */
    private FooterAdapter mFooterAdapter;
    /** Observer to wait for changes to the current folder so we can change the selected folder */
    private FolderObserver mFolderObserver = null;
    /** Listen for account changes. */
    private AccountObserver mAccountObserver = null;
    /** Listen to changes to selected folder or account */
    private FolderOrAccountListener mFolderOrAccountListener = null;
    /** Listen to changes to list of all accounts */
    private AllAccountObserver mAllAccountsObserver = null;
    /**
     * Type of currently selected folder: {@link DrawerItem#FOLDER_INBOX},
     * {@link DrawerItem#FOLDER_RECENT} or {@link DrawerItem#FOLDER_OTHER}.
     * Set as {@link DrawerItem#UNSET} to begin with, as there is nothing selected yet.
     */
    private int mSelectedDrawerItemCategory = DrawerItem.UNSET;

    /** The FolderType of the selected folder {@link FolderType} */
    private int mSelectedFolderType = FolderType.INBOX;
    /** The current account according to the controller */
    protected Account mCurrentAccount;
    /** The account we will change to once the drawer (if any) is closed */
    private Account mNextAccount = null;
    /** The folder we will change to once the drawer (if any) is closed */
    private Folder mNextFolder = null;
    /** Watcher for tracking and receiving unread counts for mail */
    private FolderWatcher mFolderWatcher = null;
    private boolean mRegistered = false;

    private final DrawerStateListener mDrawerListener = new DrawerStateListener();

    private BitmapCache mImagesCache;
    private ContactResolver mContactResolver;

    private boolean mInboxPresent;

    private boolean mMiniDrawerEnabled;
    private boolean mIsMinimized;
    protected MiniDrawerView mMiniDrawerView;
    private MiniDrawerAccountsAdapter mMiniDrawerAccountsAdapter;
    // use the same dimen as AccountItemView to participate in recycling
    // TODO: but Material account switcher doesn't recycle...
    private int mMiniDrawerAvatarDecodeSize;

    private AnimatorListenerAdapter mMiniDrawerFadeOutListener;
    private AnimatorListenerAdapter mListViewFadeOutListener;
    private AnimatorListenerAdapter mMiniDrawerFadeInListener;
    private AnimatorListenerAdapter mListViewFadeInListener;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public FolderListFragment() {
        super();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(super.toString());
        sb.setLength(sb.length() - 1);
        sb.append(" folder=");
        sb.append(mFolderListUri);
        sb.append(" parent=");
        sb.append(mParentFolder);
        sb.append(" adapterCount=");
        sb.append(mMergedAdapter != null ? mMergedAdapter.getCount() : -1);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Creates a new instance of {@link FolderListFragment}, initialized
     * to display the folder and its immediate children.
     * @param folder parent folder whose children are shown
     *
     */
    public static FolderListFragment ofTree(Folder folder) {
        final FolderListFragment fragment = new FolderListFragment();
        fragment.setArguments(getBundleFromArgs(folder, folder.childFoldersListUri, null));
        return fragment;
    }

    /**
     * Creates a new instance of {@link FolderListFragment}, initialized
     * to display the top level: where we have no parent folder, but we have a list of folders
     * from the account.
     * @param folderListUri the URI which contains all the list of folders
     * @param excludedFolderTypes A list of {@link FolderType}s to exclude from displaying
     */
    public static FolderListFragment ofTopLevelTree(Uri folderListUri,
            final ArrayList<Integer> excludedFolderTypes) {
        final FolderListFragment fragment = new FolderListFragment();
        fragment.setArguments(getBundleFromArgs(null, folderListUri, excludedFolderTypes));
        return fragment;
    }

    /**
     * Construct a bundle that represents the state of this fragment.
     *
     * @param parentFolder non-null for trees, the parent of this list
     * @param folderListUri the URI which contains all the list of folders
     * @param excludedFolderTypes if non-null, this indicates folders to exclude in lists.
     * @return Bundle containing parentFolder, divided list boolean and
     *         excluded folder types
     */
    private static Bundle getBundleFromArgs(Folder parentFolder, Uri folderListUri,
            final ArrayList<Integer> excludedFolderTypes) {
        final Bundle args = new Bundle(3);
        if (parentFolder != null) {
            args.putParcelable(ARG_PARENT_FOLDER, parentFolder);
        }
        if (folderListUri != null) {
            args.putString(ARG_FOLDER_LIST_URI, folderListUri.toString());
        }
        if (excludedFolderTypes != null) {
            args.putIntegerArrayList(ARG_EXCLUDED_FOLDER_TYPES, excludedFolderTypes);
        }
        return args;
    }

    @Override
    public void onActivityCreated(Bundle savedState) {
        super.onActivityCreated(savedState);
        // Strictly speaking, we get back an android.app.Activity from getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity which is of type
        // ControllableActivity, so this cast should be safe. If this cast fails, some other
        // activity is creating ConversationListFragments. This activity must be of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (!(activity instanceof ControllableActivity)) {
            LogUtils.wtf(LOG_TAG, "FolderListFragment expects only a ControllableActivity to" +
                    "create it. Cannot proceed.");
            return;
        }
        mActivity = (ControllableActivity) activity;

        mMiniDrawerAvatarDecodeSize =
                getResources().getDimensionPixelSize(R.dimen.account_avatar_dimension);

        final int avatarSize = getActivity().getResources().getDimensionPixelSize(
                R.dimen.account_avatar_dimension);

        mImagesCache = new UnrefedBitmapCache(Utils.isLowRamDevice(getActivity()) ?
                0 : avatarSize * avatarSize * IMAGE_CACHE_COUNT,
                AVATAR_IMAGES_PREVIEWS_CACHE_NON_POOLED_FRACTION,
                AVATAR_IMAGES_PREVIEWS_CACHE_NULL_CAPACITY);
        mContactResolver = new ContactResolver(getActivity().getContentResolver(),
                mImagesCache);

        if (mMiniDrawerEnabled) {
            setupMiniDrawerAccountsAdapter();
            mMiniDrawerView.setController(this);
            // set up initial state
            setMinimized(isMinimized());
        } else {
            mMiniDrawerView.setVisibility(View.GONE);
        }

        final FolderController controller = mActivity.getFolderController();
        // Listen to folder changes in the future
        mFolderObserver = new FolderObserver() {
            @Override
            public void onChanged(Folder newFolder) {
                setSelectedFolder(newFolder);
            }
        };
        final Folder currentFolder;
        if (controller != null) {
            // Only register for selected folder updates if we have a controller.
            currentFolder = mFolderObserver.initialize(controller);
            mCurrentFolderForUnreadCheck = currentFolder;
        } else {
            currentFolder = null;
        }

        // Initialize adapter for folder/hierarchical list.  Note this relies on
        // mActivity being initialized.
        final Folder selectedFolder;
        if (mParentFolder != null) {
            mFolderAdapter = new HierarchicalFolderListAdapter(null, mParentFolder);
            selectedFolder = mActivity.getHierarchyFolder();
        } else {
            mFolderAdapter = new FolderAdapter(mIsDivided);
            selectedFolder = currentFolder;
        }

        mAccountsAdapter = newAccountsAdapter();
        mFooterAdapter = new FooterAdapter();

        // Is the selected folder fresher than the one we have restored from a bundle?
        if (selectedFolder != null
                && !selectedFolder.folderUri.equals(mSelectedFolderUri)) {
            setSelectedFolder(selectedFolder);
        }

        // Assign observers for current account & all accounts
        final AccountController accountController = mActivity.getAccountController();
        mAccountObserver = new AccountObserver() {
            @Override
            public void onChanged(Account newAccount) {
                setSelectedAccount(newAccount);
            }
        };
        mFolderChanger = mActivity.getFolderSelector();
        if (accountController != null) {
            mAccountController = accountController;
            // Current account and its observer.
            setSelectedAccount(mAccountObserver.initialize(accountController));
            // List of all accounts and its observer.
            mAllAccountsObserver = new AllAccountObserver(){
                @Override
                public void onChanged(Account[] allAccounts) {
                    if (!mRegistered && mAccountController != null) {
                        // TODO(viki): Round-about way of setting the watcher. http://b/8750610
                        mAccountController.setFolderWatcher(mFolderWatcher);
                        mRegistered = true;
                    }
                    mFolderWatcher.updateAccountList(getAllAccounts());
                    rebuildAccountList();
                }
            };
            mAllAccountsObserver.initialize(accountController);

            mFolderOrAccountListener = new FolderOrAccountListener();
            mAccountController.registerFolderOrAccountChangedObserver(mFolderOrAccountListener);

            final DrawerController dc = mActivity.getDrawerController();
            if (dc != null) {
                dc.registerDrawerListener(mDrawerListener);
            }
        }

        mDrawerController = mActivity.getDrawerController();

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        mListView.setChoiceMode(getListViewChoiceMode());

        mMergedAdapter = new MergedAdapter<>();
        if (mAccountsAdapter != null) {
            mMergedAdapter.setAdapters(mAccountsAdapter, mFolderAdapter, mFooterAdapter);
        } else {
            mMergedAdapter.setAdapters(mFolderAdapter, mFooterAdapter);
        }

        mFolderWatcher = new FolderWatcher(mActivity, this);
        mFolderWatcher.updateAccountList(getAllAccounts());

        setListAdapter(mMergedAdapter);
    }

    public BitmapCache getBitmapCache() {
        return mImagesCache;
    }

    public ContactResolver getContactResolver() {
        return mContactResolver;
    }

    public void toggleDrawerState() {
        if (mDrawerController != null) {
            mDrawerController.toggleDrawerState();
        }
    }

    /**
     * Set the instance variables from the arguments provided here.
     * @param args bundle of arguments with keys named ARG_*
     */
    private void setInstanceFromBundle(Bundle args) {
        if (args == null) {
            return;
        }
        mParentFolder = args.getParcelable(ARG_PARENT_FOLDER);
        final String folderUri = args.getString(ARG_FOLDER_LIST_URI);
        if (folderUri != null) {
            mFolderListUri = Uri.parse(folderUri);
        }
        mExcludedFolderTypes = args.getIntegerArrayList(ARG_EXCLUDED_FOLDER_TYPES);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedState) {
        setInstanceFromBundle(getArguments());

        final View rootView = inflater.inflate(R.layout.folder_list, container, false);
        mListView = (ListView) rootView.findViewById(android.R.id.list);
        mListView.setEmptyView(null);
        mListView.setDivider(null);
        addListHeader(inflater, rootView, mListView);
        if (savedState != null && savedState.containsKey(BUNDLE_LIST_STATE)) {
            mListView.onRestoreInstanceState(savedState.getParcelable(BUNDLE_LIST_STATE));
        }
        if (savedState != null && savedState.containsKey(BUNDLE_SELECTED_FOLDER)) {
            mSelectedFolderUri =
                    new FolderUri(Uri.parse(savedState.getString(BUNDLE_SELECTED_FOLDER)));
            mSelectedDrawerItemCategory = savedState.getInt(BUNDLE_SELECTED_ITEM_TYPE);
            mSelectedFolderType = savedState.getInt(BUNDLE_SELECTED_TYPE);
        } else if (mParentFolder != null) {
            mSelectedFolderUri = mParentFolder.folderUri;
            // No selected folder type required for hierarchical lists.
        }
        if (savedState != null) {
            mInboxPresent = savedState.getBoolean(BUNDLE_INBOX_PRESENT, true);
        } else {
            mInboxPresent = true;
        }

        mMiniDrawerView = (MiniDrawerView) rootView.findViewById(R.id.mini_drawer);

        // Create default animator listeners
        mMiniDrawerFadeOutListener = new FadeAnimatorListener(mMiniDrawerView, true /* fadeOut */);
        mListViewFadeOutListener = new FadeAnimatorListener(mListView, true /* fadeOut */);
        mMiniDrawerFadeInListener = new FadeAnimatorListener(mMiniDrawerView, false /* fadeOut */);
        mListViewFadeInListener = new FadeAnimatorListener(mListView, false /* fadeOut */);

        return rootView;
    }

    protected void addListHeader(LayoutInflater inflater, View rootView, ListView list) {
        // Default impl does nothing
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListView != null) {
            outState.putParcelable(BUNDLE_LIST_STATE, mListView.onSaveInstanceState());
        }
        if (mSelectedFolderUri != null) {
            outState.putString(BUNDLE_SELECTED_FOLDER, mSelectedFolderUri.toString());
        }
        outState.putInt(BUNDLE_SELECTED_ITEM_TYPE, mSelectedDrawerItemCategory);
        outState.putInt(BUNDLE_SELECTED_TYPE, mSelectedFolderType);
        outState.putBoolean(BUNDLE_INBOX_PRESENT, mInboxPresent);
    }

    @Override
    public void onDestroyView() {
        if (mFolderAdapter != null) {
            mFolderAdapter.destroy();
        }
        // Clear the adapter.
        setListAdapter(null);
        if (mFolderObserver != null) {
            mFolderObserver.unregisterAndDestroy();
            mFolderObserver = null;
        }
        if (mAccountObserver != null) {
            mAccountObserver.unregisterAndDestroy();
            mAccountObserver = null;
        }
        if (mAllAccountsObserver != null) {
            mAllAccountsObserver.unregisterAndDestroy();
            mAllAccountsObserver = null;
        }
        if (mFolderOrAccountListener != null && mAccountController != null) {
            mAccountController.unregisterFolderOrAccountChangedObserver(mFolderOrAccountListener);
            mFolderOrAccountListener = null;
        }
        super.onDestroyView();

        if (mActivity != null) {
            final DrawerController dc = mActivity.getDrawerController();
            if (dc != null) {
                dc.unregisterDrawerListener(mDrawerListener);
            }
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        viewFolderOrChangeAccount(position);
    }

    private Folder getDefaultInbox(Account account) {
        if (account == null || mFolderWatcher == null) {
            return null;
        }
        return mFolderWatcher.getDefaultInbox(account);
    }

    protected int getUnreadCount(Account account) {
        if (account == null || mFolderWatcher == null) {
            return 0;
        }
        return mFolderWatcher.getUnreadCount(account);
    }

    protected void changeAccount(final Account account) {
        // Switching accounts takes you to the default inbox for that account.
        mSelectedDrawerItemCategory = DrawerItem.FOLDER_INBOX;
        mSelectedFolderType = FolderType.INBOX;
        mNextAccount = account;
        mAccountController.closeDrawer(true, mNextAccount, getDefaultInbox(mNextAccount));
        Analytics.getInstance().sendEvent("switch_account", "drawer_account_switch", null, 0);
    }

    /**
     * Display the conversation list from the folder at the position given.
     * @param position a zero indexed position into the list.
     */
    protected void viewFolderOrChangeAccount(int position) {
        // Get the ListView's adapter
        final Object item = getListView().getAdapter().getItem(position);
        LogUtils.d(LOG_TAG, "viewFolderOrChangeAccount(%d): %s", position, item);
        final Folder folder;
        @DrawerItem.DrawerItemCategory int itemCategory = DrawerItem.UNSET;

        if (item instanceof DrawerItem) {
            final DrawerItem drawerItem = (DrawerItem) item;
            // Could be a folder or account or footer
            final @DrawerItem.DrawerItemType int itemType = drawerItem.getType();
            if (itemType == DrawerItem.VIEW_ACCOUNT) {
                // Account, so switch.
                folder = null;
                onAccountSelected(drawerItem.mAccount);
            } else if (itemType == DrawerItem.VIEW_FOLDER) {
                // Folder type, so change folders only.
                folder = drawerItem.mFolder;
                mSelectedDrawerItemCategory = itemCategory = drawerItem.mItemCategory;
                mSelectedFolderType = folder.type;
                LogUtils.d(LOG_TAG, "FLF.viewFolderOrChangeAccount folder=%s, type=%d",
                        folder, mSelectedDrawerItemCategory);
            } else if (itemType == DrawerItem.VIEW_FOOTER_HELP ||
                    itemType == DrawerItem.VIEW_FOOTER_SETTINGS) {
                folder = null;
                drawerItem.onClick(null /* unused */);
            } else {
                // Do nothing.
                LogUtils.d(LOG_TAG, "FolderListFragment: viewFolderOrChangeAccount():"
                        + " Clicked on unset item in drawer. Offending item is " + item);
                return;
            }
        } else if (item instanceof Folder) {
            folder = (Folder) item;
        } else {
            // Don't know how we got here.
            LogUtils.wtf(LOG_TAG, "viewFolderOrChangeAccount(): invalid item");
            folder = null;
        }
        if (folder != null) {
            final String label = (itemCategory == DrawerItem.FOLDER_RECENT) ? "recent" : "normal";
            onFolderSelected(folder, label);
        }
    }

    public void onFolderSelected(Folder folder, String analyticsLabel) {
        // Go to the conversation list for this folder.
        if (!folder.folderUri.equals(mSelectedFolderUri)) {
            mNextFolder = folder;
            mAccountController.closeDrawer(true /** hasNewFolderOrAccount */,
                    null /** nextAccount */,
                    folder /** nextFolder */);

            Analytics.getInstance().sendEvent("switch_folder", folder.getTypeDescription(),
                    analyticsLabel, 0);

        } else {
            // Clicked on same folder, just close drawer
            mAccountController.closeDrawer(false /** hasNewFolderOrAccount */,
                    null /** nextAccount */,
                    folder /** nextFolder */);
        }
    }

    public void onAccountSelected(Account account) {
        // Only reset the cache if the account has changed.
        if (mCurrentAccount == null || account == null ||
                !mCurrentAccount.getEmailAddress().equals(account.getEmailAddress())) {
            mActivity.resetSenderImageCache();
        }

        if (account != null && mSelectedFolderUri.equals(account.settings.defaultInbox)) {
            // We're already in the default inbox for account,
            // just close the drawer (no new target folders/accounts)
            mAccountController.closeDrawer(false, mNextAccount,
                    getDefaultInbox(mNextAccount));
        } else {
            changeAccount(account);
        }
    }

    @Override
    public Loader<ObjectCursor<Folder>> onCreateLoader(int id, Bundle args) {
        final Uri folderListUri;
        if (id == FOLDER_LIST_LOADER_ID) {
            if (mFolderListUri != null) {
                // Folder trees, they specify a URI at construction time.
                folderListUri = mFolderListUri;
            } else {
                // Drawers get the folder list from the current account.
                folderListUri = mCurrentAccount.folderListUri;
            }
        } else if (id == ALL_FOLDER_LIST_LOADER_ID) {
            folderListUri = mCurrentAccount.allFolderListUri;
        } else {
            LogUtils.wtf(LOG_TAG, "FLF.onCreateLoader() with weird type");
            return null;
        }
        return new ObjectCursorLoader<>(mActivity.getActivityContext(), folderListUri,
                UIProvider.FOLDERS_PROJECTION, Folder.FACTORY);
    }

    @Override
    public void onLoadFinished(Loader<ObjectCursor<Folder>> loader, ObjectCursor<Folder> data) {
        if (mFolderAdapter != null) {
            if (loader.getId() == FOLDER_LIST_LOADER_ID) {
                mFolderAdapter.setCursor(data);

                if (mMiniDrawerEnabled) {
                    mMiniDrawerView.refresh();
                }

            } else if (loader.getId() == ALL_FOLDER_LIST_LOADER_ID) {
                mFolderAdapter.setAllFolderListCursor(data);
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<ObjectCursor<Folder>> loader) {
        if (mFolderAdapter != null) {
            if (loader.getId() == FOLDER_LIST_LOADER_ID) {
                mFolderAdapter.setCursor(null);
            } else if (loader.getId() == ALL_FOLDER_LIST_LOADER_ID) {
                mFolderAdapter.setAllFolderListCursor(null);
            }
        }
    }

    /**
     *  Returns the sorted list of accounts. The AAC always has the current list, sorted by
     *  frequency of use.
     * @return a list of accounts, sorted by frequency of use
     */
    public Account[] getAllAccounts() {
        if (mAllAccountsObserver != null) {
            return mAllAccountsObserver.getAllAccounts();
        }
        return new Account[0];
    }

    protected AccountsAdapter newAccountsAdapter() {
        return new AccountsAdapter();
    }

    @Override
    public void onUnreadCountChange() {
        if (mAccountsAdapter != null) {
            mAccountsAdapter.notifyDataSetChanged();
        }
    }

    public boolean isMiniDrawerEnabled() {
        return mMiniDrawerEnabled;
    }

    public void setMiniDrawerEnabled(boolean enabled) {
        mMiniDrawerEnabled = enabled;
        setMinimized(isMinimized()); // init visual state
    }

    public boolean isMinimized() {
        return mMiniDrawerEnabled && mIsMinimized;
    }

    public void setMinimized(boolean minimized) {
        if (!mMiniDrawerEnabled) {
            return;
        }

        mIsMinimized = minimized;

        if (isMinimized()) {
            mMiniDrawerView.setVisibility(View.VISIBLE);
            mMiniDrawerView.setAlpha(1f);
            mListView.setVisibility(View.INVISIBLE);
            mListView.setAlpha(0f);
        } else {
            mMiniDrawerView.setVisibility(View.INVISIBLE);
            mMiniDrawerView.setAlpha(0f);
            mListView.setVisibility(View.VISIBLE);
            mListView.setAlpha(1f);
        }
    }

    public void animateMinimized(boolean minimized) {
        if (!mMiniDrawerEnabled) {
            return;
        }

        mIsMinimized = minimized;

        Utils.enableHardwareLayer(mMiniDrawerView);
        Utils.enableHardwareLayer(mListView);
        if (mIsMinimized) {
            // From the current state (either maximized or partially dragged) to minimized.
            final float startAlpha = mListView.getAlpha();
            final long duration = (long) (startAlpha * DRAWER_FADE_VELOCITY_MS_PER_ALPHA);
            mMiniDrawerView.setVisibility(View.VISIBLE);

            // Animate the mini-drawer to fade in.
            mMiniDrawerView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(mMiniDrawerFadeInListener);
            // Animate the list view to fade out.
            mListView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setListener(mListViewFadeOutListener);
        } else {
            // From the current state (either minimized or partially dragged) to maximized.
            final float startAlpha = mMiniDrawerView.getAlpha();
            final long duration = (long) (startAlpha * DRAWER_FADE_VELOCITY_MS_PER_ALPHA);
            mListView.setVisibility(View.VISIBLE);
            mListView.requestFocus();

            // Animate the mini-drawer to fade out.
            mMiniDrawerView.animate()
                    .alpha(0f)
                    .setDuration(duration)
                    .setListener(mMiniDrawerFadeOutListener);
            // Animate the list view to fade in.
            mListView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(mListViewFadeInListener);
        }
    }

    public void onDrawerDragStarted() {
        Utils.enableHardwareLayer(mMiniDrawerView);
        Utils.enableHardwareLayer(mListView);
        // The drawer drag will always end with animating the drawers to their final states, so
        // the animation will remove the hardware layer upon completion.
    }

    public void onDrawerDrag(float percent) {
        mMiniDrawerView.setAlpha(1f - percent);
        mListView.setAlpha(percent);
        mMiniDrawerView.setVisibility(View.VISIBLE);
        mListView.setVisibility(View.VISIBLE);
    }

    /**
     * Interface for all cursor adapters that allow setting a cursor and being destroyed.
     */
    private interface FolderListFragmentCursorAdapter extends ListAdapter {
        /** Update the folder list cursor with the cursor given here. */
        void setCursor(ObjectCursor<Folder> cursor);
        ObjectCursor<Folder> getCursor();
        /** Update the all folder list cursor with the cursor given here. */
        void setAllFolderListCursor(ObjectCursor<Folder> cursor);
        /** Remove all observers and destroy the object. */
        void destroy();
        /** Notifies the adapter that the data has changed. */
        void notifyDataSetChanged();
    }

    /**
     * An adapter for flat folder lists.
     */
    private class FolderAdapter extends BaseAdapter implements FolderListFragmentCursorAdapter {

        private final RecentFolderObserver mRecentFolderObserver = new RecentFolderObserver() {
            @Override
            public void onChanged() {
                if (!isCursorInvalid()) {
                    rebuildFolderList();
                }
            }
        };
        /** No resource used for string header in folder list */
        private static final int BLANK_HEADER_RESOURCE = -1;
        /** Cache of most recently used folders */
        private final RecentFolderList mRecentFolders;
        /** True if the list is divided, false otherwise. See the comment on
         * {@link FolderListFragment#mIsDivided} for more information */
        private final boolean mIsDivided;
        /** All the items */
        private List<DrawerItem> mItemList = new ArrayList<>();
        /** Cursor into the folder list. This might be null. */
        private ObjectCursor<Folder> mCursor = null;
        /** Cursor into the all folder list. This might be null. */
        private ObjectCursor<Folder> mAllFolderListCursor = null;

        /**
         * Creates a {@link FolderAdapter}. This is a list of all the accounts and folders.
         *
         * @param isDivided true if folder list is flat, false if divided by label group. See
         *                   the comments on {@link #mIsDivided} for more information
         */
        public FolderAdapter(boolean isDivided) {
            super();
            mIsDivided = isDivided;
            final RecentFolderController controller = mActivity.getRecentFolderController();
            if (controller != null && mIsDivided) {
                mRecentFolders = mRecentFolderObserver.initialize(controller);
            } else {
                mRecentFolders = null;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DrawerItem item = (DrawerItem) getItem(position);
            final View view = item.getView(convertView, parent);
            final @DrawerItem.DrawerItemType int type = item.getType();
            final boolean isSelected =
                    item.isHighlighted(mSelectedFolderUri, mSelectedDrawerItemCategory);
            if (type == DrawerItem.VIEW_FOLDER) {
                mListView.setItemChecked((mAccountsAdapter != null ?
                        mAccountsAdapter.getCount() : 0) +
                        position + mListView.getHeaderViewsCount(), isSelected);
            }
            // If this is the current folder, also check to verify that the unread count
            // matches what the action bar shows.
            if (type == DrawerItem.VIEW_FOLDER
                    && isSelected
                    && (mCurrentFolderForUnreadCheck != null)
                    && item.mFolder.unreadCount != mCurrentFolderForUnreadCheck.unreadCount) {
                ((FolderItemView) view).overrideUnreadCount(
                        mCurrentFolderForUnreadCheck.unreadCount);
            }
            return view;
        }

        @Override
        public int getViewTypeCount() {
            // Accounts, headers, folders (all parts of drawer view types)
            return DrawerItem.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position) {
            return ((DrawerItem) getItem(position)).getType();
        }

        @Override
        public int getCount() {
            return mItemList.size();
        }

        @Override
        public boolean isEnabled(int position) {
            final DrawerItem drawerItem = ((DrawerItem) getItem(position));
            return drawerItem != null && drawerItem.isItemEnabled();
        }

        @Override
        public boolean areAllItemsEnabled() {
            // We have headers and thus some items are not enabled.
            return false;
        }

        /**
         * Returns all the recent folders from the list given here. Safe to call with a null list.
         * @param recentList a list of all recently accessed folders.
         * @return a valid list of folders, which are all recent folders.
         */
        private List<Folder> getRecentFolders(RecentFolderList recentList) {
            final List<Folder> folderList = new ArrayList<>();
            if (recentList == null) {
                return folderList;
            }
            // Get all recent folders, after removing system folders.
            for (final Folder f : recentList.getRecentFolderList(null)) {
                if (!f.isProviderFolder()) {
                    folderList.add(f);
                }
            }
            return folderList;
        }

        /**
         * Responsible for verifying mCursor, and ensuring any recalculate
         * conditions are met. Also calls notifyDataSetChanged once it's finished
         * populating {@link com.android.mail.ui.FolderListFragment.FolderAdapter#mItemList}
         */
        private void rebuildFolderList() {
            final boolean oldInboxPresent = mInboxPresent;
            mItemList = recalculateListFolders();
            if (mAccountController != null && mInboxPresent && !oldInboxPresent) {
                // We didn't have an inbox folder before, but now we do. This can occur when
                // setting up a new account. We automatically create the "starred" virtual
                // virtual folder, but we won't create the inbox until it gets synced.
                // This means that we'll start out looking at the "starred" folder, and the
                // user will need to manually switch to the inbox. See b/13793316
                mAccountController.switchToDefaultInboxOrChangeAccount(mCurrentAccount);
            }
            // Ask the list to invalidate its views.
            notifyDataSetChanged();
        }

        /**
         * Recalculates the system, recent and user label lists.
         * This method modifies all the three lists on every single invocation.
         */
        private List<DrawerItem> recalculateListFolders() {
            final List<DrawerItem> itemList = new ArrayList<>();
            // If we are waiting for folder initialization, we don't have any kinds of folders,
            // just the "Waiting for initialization" item. Note, this should only be done
            // when we're waiting for account initialization or initial sync.
            if (isCursorInvalid()) {
                if(!mCurrentAccount.isAccountReady()) {
                    itemList.add(DrawerItem.ofWaitView(mActivity));
                }
                return itemList;
            }
            if (mIsDivided) {
                //Choose an adapter for a divided list with sections
                return recalculateDividedListFolders(itemList);
            } else {
                // Adapter for a flat list. Everything is a FOLDER_OTHER, and there are no headers.
                return recalculateFlatListFolders(itemList);
            }
        }

        // Recalculate folder list intended to be flat (no hearders or sections shown).
        // This is commonly used for the widget or other simple folder selections
        private List<DrawerItem> recalculateFlatListFolders(List<DrawerItem> itemList) {
            final List<DrawerItem> inboxFolders = new ArrayList<>();
            final List<DrawerItem> allFoldersList = new ArrayList<>();
            do {
                final Folder f = mCursor.getModel();
                if (!isFolderTypeExcluded(f)) {
                    // Prioritize inboxes
                    if (f.isInbox()) {
                        inboxFolders.add(DrawerItem.ofFolder(
                                mActivity, f, DrawerItem.FOLDER_OTHER));
                    } else {
                        allFoldersList.add(
                                DrawerItem.ofFolder(mActivity, f, DrawerItem.FOLDER_OTHER));
                    }
                }
            } while (mCursor.moveToNext());
            itemList.addAll(inboxFolders);
            itemList.addAll(allFoldersList);
            return itemList;
        }

        // Recalculate folder list divided by sections (inboxes, recents, all, etc...)
        // This is primarily used by the drawer
        private List<DrawerItem> recalculateDividedListFolders(List<DrawerItem> itemList) {
            final List<DrawerItem> allFoldersList = new ArrayList<>();
            final List<DrawerItem> inboxFolders = new ArrayList<>();
            do {
                final Folder f = mCursor.getModel();
                if (!isFolderTypeExcluded(f)) {
                    if (f.isInbox()) {
                        inboxFolders.add(DrawerItem.ofFolder(
                                mActivity, f, DrawerItem.FOLDER_INBOX));
                    } else {
                        allFoldersList.add(DrawerItem.ofFolder(
                                mActivity, f, DrawerItem.FOLDER_OTHER));
                    }
                }
            } while (mCursor.moveToNext());

            // If we have the all folder list, verify that the current folder exists
            boolean currentFolderFound = false;
            if (mAllFolderListCursor != null) {
                final String folderName = mSelectedFolderUri.toString();
                LogUtils.d(LOG_TAG, "Checking if all folder list contains %s", folderName);

                if (mAllFolderListCursor.moveToFirst()) {
                    LogUtils.d(LOG_TAG, "Cursor for %s seems reasonably valid", folderName);
                    do {
                        final Folder f = mAllFolderListCursor.getModel();
                        if (!isFolderTypeExcluded(f)) {
                            if (f.folderUri.equals(mSelectedFolderUri)) {
                                LogUtils.d(LOG_TAG, "Found %s !", folderName);
                                currentFolderFound = true;
                            }
                        }
                    } while (!currentFolderFound && mAllFolderListCursor.moveToNext());
                }

                // The search folder will not be found here because it is excluded from the drawer.
                // Don't switch off from the current folder if it's search.
                if (!currentFolderFound && !Folder.isType(FolderType.SEARCH, mSelectedFolderType)
                        && mSelectedFolderUri != FolderUri.EMPTY
                        && mCurrentAccount != null && mAccountController != null
                        && mAccountController.isDrawerPullEnabled()) {
                    LogUtils.d(LOG_TAG, "Current folder (%1$s) has disappeared for %2$s",
                            folderName, mCurrentAccount.getEmailAddress());
                    changeAccount(mCurrentAccount);
                }
            }

            mInboxPresent = (inboxFolders.size() > 0);

            // Add all inboxes (sectioned Inboxes included) before recent folders.
            addFolderDivision(itemList, inboxFolders, BLANK_HEADER_RESOURCE);

            // Add recent folders next.
            addRecentsToList(itemList);

            // Add the remaining folders.
            addFolderDivision(itemList, allFoldersList, R.string.all_folders_heading);

            return itemList;
        }

        /**
         * Given a list of folders as {@link DrawerItem}s, add them as a group.
         * Passing in a non-0 integer for the resource will enable a header.
         *
         * @param destination List of drawer items to populate
         * @param source List of drawer items representing folders to add to the drawer
         * @param headerStringResource
         *            {@link FolderAdapter#BLANK_HEADER_RESOURCE} if no header text
         *            is required, or res-id otherwise. The integer is interpreted as the string
         *            for the header's title.
         */
        private void addFolderDivision(List<DrawerItem> destination, List<DrawerItem> source,
                int headerStringResource) {
            if (source.size() > 0) {
                if(headerStringResource != BLANK_HEADER_RESOURCE) {
                    destination.add(DrawerItem.ofHeader(mActivity, headerStringResource));
                } else {
                    destination.add(DrawerItem.ofBlankHeader(mActivity));
                }
                destination.addAll(source);
            }
        }

        /**
         * Add recent folders to the list in order as acquired by the {@link RecentFolderList}.
         *
         * @param destination List of drawer items to populate
         */
        private void addRecentsToList(List<DrawerItem> destination) {
            // If there are recent folders, add them.
            final List<Folder> recentFolderList = getRecentFolders(mRecentFolders);

            // Remove any excluded folder types
            if (mExcludedFolderTypes != null) {
                final Iterator<Folder> iterator = recentFolderList.iterator();
                while (iterator.hasNext()) {
                    if (isFolderTypeExcluded(iterator.next())) {
                        iterator.remove();
                    }
                }
            }

            if (recentFolderList.size() > 0) {
                destination.add(DrawerItem.ofHeader(mActivity, R.string.recent_folders_heading));
                // Recent folders are not queried for position.
                for (Folder f : recentFolderList) {
                    destination.add(DrawerItem.ofFolder(mActivity, f, DrawerItem.FOLDER_RECENT));
                }
            }
        }

        /**
         * Check if the cursor provided is valid.
         * @return True if cursor is invalid, false otherwise
         */
        private boolean isCursorInvalid() {
            return mCursor == null || mCursor.isClosed()|| mCursor.getCount() <= 0
                    || !mCursor.moveToFirst();
        }

        @Override
        public void setCursor(ObjectCursor<Folder> cursor) {
            mCursor = cursor;
            rebuildAccountList();
            rebuildFolderList();
        }

        @Override
        public ObjectCursor<Folder> getCursor() {
            return mCursor;
        }

        @Override
        public void setAllFolderListCursor(final ObjectCursor<Folder> cursor) {
            mAllFolderListCursor = cursor;
            rebuildAccountList();
            rebuildFolderList();
        }

        @Override
        public Object getItem(int position) {
            // Is there an attempt made to access outside of the drawer item list?
            if (position >= mItemList.size()) {
                return null;
            } else {
                return mItemList.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public final void destroy() {
            mRecentFolderObserver.unregisterAndDestroy();
        }
    }

    private class HierarchicalFolderListAdapter extends ArrayAdapter<Folder>
            implements FolderListFragmentCursorAdapter {

        private static final int PARENT = 0;
        private static final int CHILD = 1;
        private final FolderUri mParentUri;
        private final Folder mParent;

        public HierarchicalFolderListAdapter(ObjectCursor<Folder> c, Folder parentFolder) {
            super(mActivity.getActivityContext(), R.layout.folder_item);
            mParent = parentFolder;
            mParentUri = parentFolder.folderUri;
            setCursor(c);
        }

        @Override
        public int getViewTypeCount() {
            // Child and Parent
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            final Folder f = getItem(position);
            return f.folderUri.equals(mParentUri) ? PARENT : CHILD;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final FolderItemView folderItemView;
            final Folder folder = getItem(position);

            if (convertView != null) {
                folderItemView = (FolderItemView) convertView;
            } else {
                folderItemView = (FolderItemView) LayoutInflater.from(
                        mActivity.getActivityContext()).inflate(R.layout.folder_item, null);
            }
            folderItemView.bind(folder, mParentUri);

            if (folder.folderUri.equals(mSelectedFolderUri)) {
                final ListView listView = getListView();
                listView.setItemChecked((mAccountsAdapter != null ?
                        mAccountsAdapter.getCount() : 0) +
                        position + listView.getHeaderViewsCount(), true);
                // If this is the current folder, also check to verify that the unread count
                // matches what the action bar shows.
                final boolean unreadCountDiffers = (mCurrentFolderForUnreadCheck != null)
                        && folder.unreadCount != mCurrentFolderForUnreadCheck.unreadCount;
                if (unreadCountDiffers) {
                    folderItemView.overrideUnreadCount(mCurrentFolderForUnreadCheck.unreadCount);
                }
            }
            Folder.setFolderBlockColor(folder, folderItemView.findViewById(R.id.color_block));
            Folder.setIcon(folder, (ImageView) folderItemView.findViewById(R.id.folder_icon));
            return folderItemView;
        }

        @Override
        public void setCursor(ObjectCursor<Folder> cursor) {
            clear();
            if (mParent != null) {
                add(mParent);
            }
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                do {
                    add(cursor.getModel());
                } while (cursor.moveToNext());
            }
        }

        @Override
        public ObjectCursor<Folder> getCursor() {
            throw new UnsupportedOperationException("drawers don't have hierarchical folders");
        }

        @Override
        public void setAllFolderListCursor(final ObjectCursor<Folder> cursor) {
            // Not necessary in HierarchicalFolderListAdapter
        }

        @Override
        public void destroy() {
            // Do nothing.
        }
    }

    public void rebuildAccountList() {
        if (!mIsFolderSelectionActivity) {
            if (mAccountsAdapter != null) {
                mAccountsAdapter.setAccounts(buildAccountListDrawerItems());
            }
            if (mMiniDrawerAccountsAdapter != null) {
                mMiniDrawerAccountsAdapter.setAccounts(getAllAccounts(), mCurrentAccount);
            }
        }
    }

    protected static class AccountsAdapter extends BaseAdapter {

        private List<DrawerItem> mAccounts;

        public AccountsAdapter() {
            mAccounts = new ArrayList<>();
        }

        public void setAccounts(List<DrawerItem> accounts) {
            mAccounts = accounts;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAccounts.size();
        }

        @Override
        public Object getItem(int position) {
            // Is there an attempt made to access outside of the drawer item list?
            if (position >= mAccounts.size()) {
                return null;
            } else {
                return mAccounts.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final DrawerItem item = (DrawerItem) getItem(position);
            return item.getView(convertView, parent);
        }
    }

    /**
     * Builds the drawer items for the list of accounts.
     */
    private List<DrawerItem> buildAccountListDrawerItems() {
        final Account[] allAccounts = getAllAccounts();
        final List<DrawerItem> accountList = new ArrayList<>(allAccounts.length);
        // Add all accounts and then the current account
        final Uri currentAccountUri = getCurrentAccountUri();
        for (final Account account : allAccounts) {
            final int unreadCount = getUnreadCount(account);
            accountList.add(DrawerItem.ofAccount(mActivity, account, unreadCount,
                    currentAccountUri.equals(account.uri), mImagesCache, mContactResolver));
        }
        if (mCurrentAccount == null) {
            LogUtils.wtf(LOG_TAG, "buildAccountListDrawerItems() with null current account.");
        }
        return accountList;
    }

    private Uri getCurrentAccountUri() {
        return mCurrentAccount == null ? Uri.EMPTY : mCurrentAccount.uri;
    }

    protected String getCurrentAccountEmailAddress() {
        return mCurrentAccount == null ? "" : mCurrentAccount.getEmailAddress();
    }

    protected MergedAdapter<ListAdapter> getMergedAdapter() {
        return mMergedAdapter;
    }

    public ObjectCursor<Folder> getFoldersCursor() {
        return (mFolderAdapter != null) ? mFolderAdapter.getCursor() : null;
    }

    private class FooterAdapter extends BaseAdapter {

        private final List<DrawerItem> mFooterItems = Lists.newArrayList();

        private FooterAdapter() {
            update();
        }

        @Override
        public int getCount() {
            return mFooterItems.size();
        }

        @Override
        public DrawerItem getItem(int position) {
            return mFooterItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            // Accounts, headers, folders (all parts of drawer view types)
            return DrawerItem.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getType();
        }

        /**
         * @param convertView a view, possibly null, to be recycled.
         * @param parent the parent hosting this view.
         * @return a view for the footer item displaying the given text and image.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getItem(position).getView(convertView, parent);
        }

        /**
         * Recomputes the footer drawer items depending on whether the current account
         * is populated with URIs that navigate to appropriate destinations.
         */
        private void update() {
            // if the parent activity shows a drawer, these items should participate in that drawer
            // (if it shows a *pane* they should *not* participate in that pane)
            if (mIsFolderSelectionActivity) {
                return;
            }

            mFooterItems.clear();

            if (mCurrentAccount != null) {
                mFooterItems.add(DrawerItem.ofSettingsItem(mActivity, mCurrentAccount,
                        mDrawerListener));
            }

            if (mCurrentAccount != null && !Utils.isEmpty(mCurrentAccount.helpIntentUri)) {
                mFooterItems.add(DrawerItem.ofHelpItem(mActivity, mCurrentAccount,
                        mDrawerListener));
            }

            if (!mFooterItems.isEmpty()) {
                mFooterItems.add(0, DrawerItem.ofBlankHeader(mActivity));
                mFooterItems.add(DrawerItem.ofBottomSpace(mActivity));
            }

            notifyDataSetChanged();
        }
    }

    /**
     * Sets the currently selected folder safely.
     * @param folder the folder to change to. It is an error to pass null here.
     */
    private void setSelectedFolder(Folder folder) {
        if (folder == null) {
            mSelectedFolderUri = FolderUri.EMPTY;
            mCurrentFolderForUnreadCheck = null;
            LogUtils.e(LOG_TAG, "FolderListFragment.setSelectedFolder(null) called!");
            return;
        }

        final boolean viewChanged =
                !FolderItemView.areSameViews(folder, mCurrentFolderForUnreadCheck);

        // There are two cases in which the folder type is not set by this class.
        // 1. The activity starts up: from notification/widget/shortcut/launcher. Then we have a
        //    folder but its type was never set.
        // 2. The user backs into the default inbox. Going 'back' from the conversation list of
        //    any folder will take you to the default inbox for that account. (If you are in the
        //    default inbox already, back exits the app.)
        // In both these cases, the selected folder type is not set, and must be set.
        if (mSelectedDrawerItemCategory == DrawerItem.UNSET || (mCurrentAccount != null
                && folder.folderUri.equals(mCurrentAccount.settings.defaultInbox))) {
            mSelectedDrawerItemCategory =
                    folder.isInbox() ? DrawerItem.FOLDER_INBOX : DrawerItem.FOLDER_OTHER;
            mSelectedFolderType = folder.type;
        }

        mCurrentFolderForUnreadCheck = folder;
        mSelectedFolderUri = folder.folderUri;
        if (viewChanged) {
            if (mFolderAdapter != null) {
                mFolderAdapter.notifyDataSetChanged();
            }
            if (mMiniDrawerView != null) {
                mMiniDrawerView.refresh();
            }
        }
    }

    public boolean isSelectedFolder(@NonNull Folder folder) {
        return folder.folderUri.equals(mSelectedFolderUri);
    }

    /**
     * Sets the current account to the one provided here.
     * @param account the current account to set to.
     */
    private void setSelectedAccount(Account account) {
        final boolean changed = (account != null) && (mCurrentAccount == null
                || !mCurrentAccount.uri.equals(account.uri));
        mCurrentAccount = account;
        if (changed) {
            // Verify that the new account supports sending application feedback
            updateFooterItems();
            // We no longer have proper folder objects. Let the new ones come in
            mFolderAdapter.setCursor(null);
            // If currentAccount is different from the one we set, restart the loader. Look at the
            // comment on {@link AbstractActivityController#restartOptionalLoader} to see why we
            // don't just do restartLoader.
            final LoaderManager manager = getLoaderManager();
            manager.destroyLoader(FOLDER_LIST_LOADER_ID);
            manager.restartLoader(FOLDER_LIST_LOADER_ID, Bundle.EMPTY, this);
            manager.destroyLoader(ALL_FOLDER_LIST_LOADER_ID);
            manager.restartLoader(ALL_FOLDER_LIST_LOADER_ID, Bundle.EMPTY, this);
            // An updated cursor causes the entire list to refresh. No need to refresh the list.
            // But we do need to blank out the current folder, since the account might not be
            // synced.
            mSelectedFolderUri = FolderUri.EMPTY;
            mCurrentFolderForUnreadCheck = null;

            // also set/update the mini-drawer
            if (mMiniDrawerAccountsAdapter != null) {
                mMiniDrawerAccountsAdapter.setAccounts(getAllAccounts(), mCurrentAccount);
            }

        } else if (account == null) {
            // This should never happen currently, but is a safeguard against a very incorrect
            // non-null account -> null account transition.
            LogUtils.e(LOG_TAG, "FLF.setSelectedAccount(null) called! Destroying existing loader.");
            final LoaderManager manager = getLoaderManager();
            manager.destroyLoader(FOLDER_LIST_LOADER_ID);
            manager.destroyLoader(ALL_FOLDER_LIST_LOADER_ID);
        }
    }

    private void updateFooterItems() {
        mFooterAdapter.update();
    }

    /**
     * Checks if the specified {@link Folder} is a type that we want to exclude from displaying.
     */
    private boolean isFolderTypeExcluded(final Folder folder) {
        if (mExcludedFolderTypes == null) {
            return false;
        }

        for (final int excludedType : mExcludedFolderTypes) {
            if (folder.isType(excludedType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the choice mode to use for the {@link ListView}
     */
    protected int getListViewChoiceMode() {
        return mAccountController.getFolderListViewChoiceMode();
    }


    /**
     * Drawer listener for footer functionality to react to drawer state.
     */
    public class DrawerStateListener implements DrawerLayout.DrawerListener {

        private FooterItem mPendingFooterClick;

        public void setPendingFooterClick(FooterItem itemClicked) {
            mPendingFooterClick = itemClicked;
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {}

        @Override
        public void onDrawerOpened(View drawerView) {}

        @Override
        public void onDrawerClosed(View drawerView) {
            if (mPendingFooterClick != null) {
                mPendingFooterClick.onFooterClicked();
                mPendingFooterClick = null;
            }
        }

        @Override
        public void onDrawerStateChanged(int newState) {}

    }

    private class FolderOrAccountListener extends DataSetObserver {

        @Override
        public void onChanged() {
            // First, check if there's a folder to change to
            if (mNextFolder != null) {
                mFolderChanger.onFolderSelected(mNextFolder);
                mNextFolder = null;
            }
            // Next, check if there's an account to change to
            if (mNextAccount != null) {
                mAccountController.switchToDefaultInboxOrChangeAccount(mNextAccount);
                mNextAccount = null;
            }
        }
    }

    @Override
    public ListAdapter getListAdapter() {
        // Ensures that we get the adapter with the header views.
        throw new UnsupportedOperationException("Use getListView().getAdapter() instead "
                + "which accounts for any header or footer views.");
    }

    protected class MiniDrawerAccountsAdapter extends BaseAdapter {

        private List<Account> mAccounts = new ArrayList<>();

        public void setAccounts(Account[] accounts, Account currentAccount) {
            mAccounts.clear();
            if (currentAccount == null) {
                notifyDataSetChanged();
                return;
            }
            mAccounts.add(currentAccount);
            // TODO: sort by most recent accounts
            for (final Account account : accounts) {
                if (!account.getEmailAddress().equals(currentAccount.getEmailAddress())) {
                    mAccounts.add(account);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mAccounts.size();
        }

        @Override
        public Object getItem(int position) {
            // Is there an attempt made to access outside of the drawer item list?
            if (position >= mAccounts.size()) {
                return null;
            } else {
                return mAccounts.get(position);
            }
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final ImageView iv = convertView != null ? (ImageView) convertView :
                    (ImageView) LayoutInflater.from(getActivity()).inflate(
                    R.layout.mini_drawer_recent_account_item, parent, false /* attachToRoot */);
            final MiniDrawerAccountItem item = new MiniDrawerAccountItem(iv);
            item.setupDrawable();
            item.setAccount(mAccounts.get(position));
            iv.setTag(item);
            return iv;
        }

        private class MiniDrawerAccountItem implements View.OnClickListener {
            private Account mAccount;
            private AccountAvatarDrawable mDrawable;
            public final ImageView view;

            public MiniDrawerAccountItem(ImageView iv) {
                view = iv;
                view.setOnClickListener(this);
            }

            public void setupDrawable() {
                mDrawable = new AccountAvatarDrawable(getResources(), getBitmapCache(),
                        getContactResolver());
                mDrawable.setDecodeDimensions(mMiniDrawerAvatarDecodeSize,
                        mMiniDrawerAvatarDecodeSize);
                view.setImageDrawable(mDrawable);
            }

            public void setAccount(Account acct) {
                mAccount = acct;
                mDrawable.bind(mAccount.getSenderName(), mAccount.getEmailAddress());
                String contentDescription = mAccount.getDisplayName();
                if (TextUtils.isEmpty(contentDescription)) {
                    contentDescription = mAccount.getEmailAddress();
                }
                view.setContentDescription(contentDescription);
            }

            @Override
            public void onClick(View v) {
                onAccountSelected(mAccount);
            }
        }
    }

    protected void setupMiniDrawerAccountsAdapter() {
        mMiniDrawerAccountsAdapter = new MiniDrawerAccountsAdapter();
    }

    protected ListAdapter getMiniDrawerAccountsAdapter() {
        return mMiniDrawerAccountsAdapter;
    }

    private static class FadeAnimatorListener extends AnimatorListenerAdapter {
        private boolean mCanceled;
        private final View mView;
        private final boolean mFadeOut;

        FadeAnimatorListener(View v, boolean fadeOut) {
            mView = v;
            mFadeOut = fadeOut;
        }

        @Override
        public void onAnimationStart(Animator animation) {
            if (!mFadeOut) {
                mView.setVisibility(View.VISIBLE);
            }
            mCanceled = false;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mCanceled = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCanceled) {
                // Only need to set visibility to INVISIBLE for fade-out and not fade-in.
                if (mFadeOut) {
                    mView.setVisibility(View.INVISIBLE);
                }
                // If the animation is canceled, then the next animation onAnimationEnd will disable
                // the hardware layer.
                mView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        }
    }

}
