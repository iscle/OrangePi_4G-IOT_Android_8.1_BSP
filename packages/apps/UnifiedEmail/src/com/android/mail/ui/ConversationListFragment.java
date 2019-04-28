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

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.annotation.IdRes;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.analytics.AnalyticsTimer;
import com.android.mail.browse.ConversationCursor;
import com.android.mail.browse.ConversationItemView;
import com.android.mail.browse.ConversationItemViewModel;
import com.android.mail.browse.ConversationListFooterView;
import com.android.mail.browse.ToggleableItem;
import com.android.mail.providers.Account;
import com.android.mail.providers.AccountObserver;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Folder;
import com.android.mail.providers.FolderObserver;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.providers.UIProvider.AccountCapabilities;
import com.android.mail.providers.UIProvider.ConversationListIcon;
import com.android.mail.providers.UIProvider.FolderCapabilities;
import com.android.mail.providers.UIProvider.Swipe;
import com.android.mail.ui.SwipeableListView.ListItemSwipedListener;
import com.android.mail.ui.SwipeableListView.ListItemsRemovedListener;
import com.android.mail.ui.SwipeableListView.SwipeListener;
import com.android.mail.ui.ViewMode.ModeChangeListener;
import com.android.mail.utils.KeyboardUtils;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;
import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;

import static android.view.View.OnKeyListener;

/**
 * The conversation list UI component.
 */
public final class ConversationListFragment extends Fragment implements
        OnItemLongClickListener, ModeChangeListener, ListItemSwipedListener, OnRefreshListener,
        SwipeListener, OnKeyListener, AdapterView.OnItemClickListener, View.OnClickListener,
        AbsListView.OnScrollListener {
    /** Key used to pass data to {@link ConversationListFragment}. */
    private static final String CONVERSATION_LIST_KEY = "conversation-list";
    /** Key used to keep track of the scroll state of the list. */
    private static final String LIST_STATE_KEY = "list-state";

    private static final String LOG_TAG = LogTag.getLogTag();
    /** Key used to save the ListView choice mode, since ListView doesn't save it automatically! */
    private static final String CHOICE_MODE_KEY = "choice-mode-key";

    // True if we are on a tablet device
    private static boolean mTabletDevice;

    // Delay before displaying the loading view.
    private static int LOADING_DELAY_MS;
    // Minimum amount of time to keep the loading view displayed.
    private static int MINIMUM_LOADING_DURATION;

    /**
     * Frequency of update of timestamps. Initialized in
     * {@link #onCreate(Bundle)} and final afterwards.
     */
    private static int TIMESTAMP_UPDATE_INTERVAL = 0;

    private ControllableActivity mActivity;

    // Control state.
    private ConversationListCallbacks mCallbacks;

    private final Handler mHandler = new Handler();

    // The internal view objects.
    private SwipeableListView mListView;

    private View mSearchHeaderView;
    private TextView mSearchResultCountTextView;

    /**
     * Current Account being viewed
     */
    private Account mAccount;
    /**
     * Current folder being viewed.
     */
    private Folder mFolder;

    /**
     * A simple method to update the timestamps of conversations periodically.
     */
    private Runnable mUpdateTimestampsRunnable = null;

    private ConversationListContext mViewContext;

    private AnimatedAdapter mListAdapter;

    private ConversationListFooterView mFooterView;
    private ConversationListEmptyView mEmptyView;
    private View mSecurityHoldView;
    private TextView mSecurityHoldText;
    private View mSecurityHoldButton;
    private View mLoadingView;
    private ErrorListener mErrorListener;
    private FolderObserver mFolderObserver;
    private DataSetObserver mConversationCursorObserver;

    private ConversationCheckedSet mCheckedSet;
    private final AccountObserver mAccountObserver = new AccountObserver() {
        @Override
        public void onChanged(Account newAccount) {
            mAccount = newAccount;
            setSwipeAction();
        }
    };
    private ConversationUpdater mUpdater;
    /** Hash of the Conversation Cursor we last obtained from the controller. */
    private int mConversationCursorHash;
    // The number of items in the last known ConversationCursor
    private int mConversationCursorLastCount;
    // State variable to keep track if we just loaded a new list, used for analytics only
    // True if NO DATA has returned, false if we either partially or fully loaded the data
    private boolean mInitialCursorLoading;

    private @IdRes int mNextFocusStartId;
    // Tracks if a onKey event was initiated from the listview (received ACTION_DOWN before
    // ACTION_UP). If not, the listview only receives ACTION_UP.
    private boolean mKeyInitiatedFromList;

    // Default color id for what background should be while idle
    private int mDefaultListBackgroundColor;

    /** Duration, in milliseconds, of the CAB mode (peek icon) animation. */
    private static long sSelectionModeAnimationDuration = -1;

    // Let's ensure that we are only showing one out of the three views at once
    private void showListView() {
        setupEmptyIcon(false);
        mListView.setVisibility(View.VISIBLE);
        mEmptyView.setVisibility(View.INVISIBLE);
        mLoadingView.setVisibility(View.INVISIBLE);
        mSecurityHoldView.setVisibility(View.INVISIBLE);
    }

    private void showSecurityHoldView() {
        setupEmptyIcon(false);
        mListView.setVisibility(View.INVISIBLE);
        mEmptyView.setVisibility(View.INVISIBLE);
        mLoadingView.setVisibility(View.INVISIBLE);
        setupSecurityHoldView();
        mSecurityHoldView.setVisibility(View.VISIBLE);
    }

    private void showEmptyView() {
        // If the callbacks didn't set up the empty icon, then we should show it in the empty view.
        final boolean shouldShowIcon = !setupEmptyIcon(true);
        mEmptyView.setupEmptyText(mFolder, mViewContext.searchQuery,
                mListAdapter.getBidiFormatter(), shouldShowIcon);
        mListView.setVisibility(View.INVISIBLE);
        mEmptyView.setVisibility(View.VISIBLE);
        mLoadingView.setVisibility(View.INVISIBLE);
        mSecurityHoldView.setVisibility(View.INVISIBLE);
    }

    private void showLoadingView() {
        setupEmptyIcon(false);
        mListView.setVisibility(View.INVISIBLE);
        mEmptyView.setVisibility(View.INVISIBLE);
        mLoadingView.setVisibility(View.VISIBLE);
        mSecurityHoldView.setVisibility(View.INVISIBLE);
    }

    private boolean setupEmptyIcon(boolean isEmpty) {
        return mCallbacks != null && mCallbacks.setupEmptyIconView(mFolder, isEmpty);
    }

    private void setupSecurityHoldView() {
        mSecurityHoldText.setText(getString(R.string.security_hold_required_text,
                mAccount.getDisplayName()));
    }

    private final Runnable mLoadingViewRunnable = new FragmentRunnable("LoadingRunnable", this) {
        @Override
        public void go() {
            if (!isCursorReadyToShow()) {
                mCanTakeDownLoadingView = false;
                showLoadingView();
                mHandler.removeCallbacks(mHideLoadingRunnable);
                mHandler.postDelayed(mHideLoadingRunnable, MINIMUM_LOADING_DURATION);
            }
            mLoadingViewPending = false;
        }
    };

    private final Runnable mHideLoadingRunnable = new FragmentRunnable("CancelLoading", this) {
        @Override
        public void go() {
            mCanTakeDownLoadingView = true;
            if (isCursorReadyToShow()) {
                hideLoadingViewAndShowContents();
            }
        }
    };

    // Keep track of if we are waiting for the loading view. This variable is also used to check
    // if the cursor corresponding to the current folder loaded (either partially or completely).
    private boolean mLoadingViewPending;
    private boolean mCanTakeDownLoadingView;

    /**
     * If <code>true</code>, we have restored (or attempted to restore) the list's scroll position
     * from when we were last on this conversation list.
     */
    private boolean mScrollPositionRestored = false;
    private MailSwipeRefreshLayout mSwipeRefreshWidget;

    /**
     * Constructor needs to be public to handle orientation changes and activity
     * lifecycle events.
     */
    public ConversationListFragment() {
        super();
    }

    @Override
    public void onBeginSwipe() {
        mSwipeRefreshWidget.setEnabled(false);
    }

    @Override
    public void onEndSwipe() {
        mSwipeRefreshWidget.setEnabled(true);
    }

    private class ConversationCursorObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            onConversationListStatusUpdated();
        }
    }

    /**
     * Creates a new instance of {@link ConversationListFragment}, initialized
     * to display conversation list context.
     */
    public static ConversationListFragment newInstance(ConversationListContext viewContext) {
        final ConversationListFragment fragment = new ConversationListFragment();
        final Bundle args = new Bundle(1);
        args.putBundle(CONVERSATION_LIST_KEY, viewContext.toBundle());
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Show the header if the current conversation list is showing search
     * results.
     */
    private void updateSearchResultHeader(int count) {
        if (mActivity == null || mSearchHeaderView == null) {
            return;
        }
        mSearchResultCountTextView.setText(
                getResources().getString(R.string.search_results_loaded, count));
    }

    @Override
    public void onActivityCreated(Bundle savedState) {
        super.onActivityCreated(savedState);
        mLoadingViewPending = false;
        mCanTakeDownLoadingView = true;
        if (sSelectionModeAnimationDuration < 0) {
            sSelectionModeAnimationDuration = getResources().getInteger(
                    R.integer.conv_item_view_cab_anim_duration);
        }

        // Strictly speaking, we get back an android.app.Activity from
        // getActivity. However, the
        // only activity creating a ConversationListContext is a MailActivity
        // which is of type
        // ControllableActivity, so this cast should be safe. If this cast
        // fails, some other
        // activity is creating ConversationListFragments. This activity must be
        // of type
        // ControllableActivity.
        final Activity activity = getActivity();
        if (!(activity instanceof ControllableActivity)) {
            LogUtils.e(LOG_TAG, "ConversationListFragment expects only a ControllableActivity to"
                    + "create it. Cannot proceed.");
        }
        mActivity = (ControllableActivity) activity;
        // Since we now have a controllable activity, load the account from it,
        // and register for
        // future account changes.
        mAccount = mAccountObserver.initialize(mActivity.getAccountController());
        mCallbacks = mActivity.getListHandler();
        mErrorListener = mActivity.getErrorListener();
        // Start off with the current state of the folder being viewed.
        final LayoutInflater inflater = LayoutInflater.from(mActivity.getActivityContext());
        mFooterView = (ConversationListFooterView) inflater.inflate(
                R.layout.conversation_list_footer_view, null);
        mFooterView.setClickListener(mActivity);
        final ConversationCursor conversationCursor = getConversationListCursor();
        final LoaderManager manager = getLoaderManager();

        // TODO: These special views are always created, doesn't matter whether they will
        // be shown or not, as we add more views this will get more expensive. Given these are
        // tips that are only shown once to the user, we should consider creating these on demand.
        final ConversationListHelper helper = mActivity.getConversationListHelper();
        final List<ConversationSpecialItemView> specialItemViews = helper != null ?
                ImmutableList.copyOf(helper.makeConversationListSpecialViews(
                        activity, mActivity, mAccount))
                : null;
        if (specialItemViews != null) {
            // Attach to the LoaderManager
            for (final ConversationSpecialItemView view : specialItemViews) {
                view.bindFragment(manager, savedState);
            }
        }

        mListAdapter = new AnimatedAdapter(mActivity.getApplicationContext(), conversationCursor,
                mActivity.getCheckedSet(), mActivity, mListView, specialItemViews);
        mListAdapter.addFooter(mFooterView);
        // Show search result header only if we are in search mode
        final boolean showSearchHeader = ConversationListContext.isSearchResult(mViewContext);
        if (showSearchHeader) {
            mSearchHeaderView = inflater.inflate(R.layout.search_results_view, null);
            mSearchResultCountTextView = (TextView)
                    mSearchHeaderView.findViewById(R.id.search_result_count_view);
            mListAdapter.addHeader(mSearchHeaderView);
        }

        mListView.setAdapter(mListAdapter);
        mCheckedSet = mActivity.getCheckedSet();
        mListView.setCheckedSet(mCheckedSet);
        mListAdapter.setFooterVisibility(false);
        mFolderObserver = new FolderObserver(){
            @Override
            public void onChanged(Folder newFolder) {
                onFolderUpdated(newFolder);
            }
        };
        mFolderObserver.initialize(mActivity.getFolderController());
        mConversationCursorObserver = new ConversationCursorObserver();
        mUpdater = mActivity.getConversationUpdater();
        mUpdater.registerConversationListObserver(mConversationCursorObserver);
        mTabletDevice = Utils.useTabletUI(mActivity.getApplicationContext().getResources());

        // Shadow mods to TL require background changes and scroll listening to avoid overdraw
        mDefaultListBackgroundColor =
                getResources().getColor(R.color.conversation_list_background_color);
        getView().setBackgroundColor(mDefaultListBackgroundColor);
        mListView.setOnScrollListener(this);

        // The onViewModeChanged callback doesn't get called when the mode
        // object is created, so
        // force setting the mode manually this time around.
        onViewModeChanged(mActivity.getViewMode().getMode());
        mActivity.getViewMode().addListener(this);
        if (mActivity.getListHandler().shouldPreventListSwipesEntirely()) {
            mListView.preventSwipesEntirely();
        } else {
            mListView.stopPreventingSwipes();
        }

        if (mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }
        mConversationCursorHash = (conversationCursor == null) ? 0 : conversationCursor.hashCode();
        // Belt and suspenders here; make sure we do any necessary sync of the
        // ConversationCursor
        if (conversationCursor != null && conversationCursor.isRefreshReady()) {
            conversationCursor.sync();
        }

        // On a phone we never highlight a conversation, so the default is to select none.
        // On a tablet, we highlight a SINGLE conversation in landscape conversation view.
        int choice = getDefaultChoiceMode(mTabletDevice);
        if (savedState != null) {
            // Restore the choice mode if it was set earlier, or NONE if creating a fresh view.
            // Choice mode here represents the current conversation only. CAB mode does not rely on
            // the platform: checked state is a local variable {@link ConversationItemView#mChecked}
            choice = savedState.getInt(CHOICE_MODE_KEY, choice);
            if (savedState.containsKey(LIST_STATE_KEY)) {
                // TODO: find a better way to unset the selected item when restoring
                mListView.clearChoices();
            }
        }
        setChoiceMode(choice);

        // Show list and start loading list.
        showList();
        ToastBarOperation pendingOp = mActivity.getPendingToastOperation();
        if (pendingOp != null) {
            // Clear the pending operation
            mActivity.setPendingToastOperation(null);
            mActivity.onUndoAvailable(pendingOp);
        }
    }

    /**
     * Returns the default choice mode for the list based on whether the list is displayed on tablet
     * or not.
     * @param isTablet
     * @return
     */
    private final static int getDefaultChoiceMode(boolean isTablet) {
        return isTablet ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE;
    }

    public AnimatedAdapter getAnimatedAdapter() {
        return mListAdapter;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Initialize fragment constants from resources
        final Resources res = getResources();
        TIMESTAMP_UPDATE_INTERVAL = res.getInteger(R.integer.timestamp_update_interval);
        LOADING_DELAY_MS = res.getInteger(R.integer.conversationview_show_loading_delay);
        MINIMUM_LOADING_DURATION = res.getInteger(R.integer.conversationview_min_show_loading);
        mUpdateTimestampsRunnable = new Runnable() {
            @Override
            public void run() {
                mListView.invalidateViews();
                mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
            }
        };

        // Get the context from the arguments
        final Bundle args = getArguments();
        mViewContext = ConversationListContext.forBundle(args.getBundle(CONVERSATION_LIST_KEY));
        mAccount = mViewContext.account;

        setRetainInstance(false);
    }

    @Override
    public String toString() {
        final String s = super.toString();
        if (mViewContext == null) {
            return s;
        }
        final StringBuilder sb = new StringBuilder(s);
        sb.setLength(sb.length() - 1);
        sb.append(" mListAdapter=");
        sb.append(mListAdapter);
        sb.append(" folder=");
        sb.append(mViewContext.folder);
        if (mListView != null) {
            sb.append(" selectedPos=");
            sb.append(mListView.getSelectedConversationPosDebug());
            sb.append(" listSelectedPos=");
            sb.append(mListView.getSelectedItemPosition());
            sb.append(" isListInTouchMode=");
            sb.append(mListView.isInTouchMode());
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View rootView = inflater.inflate(R.layout.conversation_list, null);
        mEmptyView = (ConversationListEmptyView) rootView.findViewById(R.id.empty_view);
        mSecurityHoldView = rootView.findViewById(R.id.security_hold_view);
        mSecurityHoldText = (TextView) rootView.findViewById(R.id.security_hold_text);
        mSecurityHoldButton = rootView.findViewById(R.id.security_hold_button);
        mSecurityHoldButton.setOnClickListener(this);
        mLoadingView = rootView.findViewById(R.id.conversation_list_loading_view);
        mListView = (SwipeableListView) rootView.findViewById(R.id.conversation_list_view);
        mListView.setHeaderDividersEnabled(false);
        mListView.setOnItemLongClickListener(this);
        mListView.enableSwipe(mAccount.supportsCapability(AccountCapabilities.UNDO));
        mListView.setListItemSwipedListener(this);
        mListView.setSwipeListener(this);
        mListView.setOnKeyListener(this);
        mListView.setOnItemClickListener(this);

        // For tablets, the default left focus is the mini-drawer
        if (mTabletDevice && mNextFocusStartId == 0) {
            mNextFocusStartId = R.id.mini_drawer;
        }
        setNextFocusStartOnList();

        // enable animateOnLayout (equivalent of setLayoutTransition) only for >=JB (b/14302062)
        if (Utils.isRunningJellybeanOrLater()) {
            ((ViewGroup) rootView.findViewById(R.id.conversation_list_parent_frame))
                    .setLayoutTransition(new LayoutTransition());
        }

        // By default let's show the list view
        showListView();

        if (savedState != null && savedState.containsKey(LIST_STATE_KEY)) {
            mListView.onRestoreInstanceState(savedState.getParcelable(LIST_STATE_KEY));
        }
        mSwipeRefreshWidget =
                (MailSwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_widget);
        mSwipeRefreshWidget.setColorScheme(R.color.swipe_refresh_color1,
                R.color.swipe_refresh_color2,
                R.color.swipe_refresh_color3, R.color.swipe_refresh_color4);
        mSwipeRefreshWidget.setOnRefreshListener(this);
        mSwipeRefreshWidget.setScrollableChild(mListView);

        return rootView;
    }

    /**
     * Sets the choice mode of the list view
     */
    private final void setChoiceMode(int choiceMode) {
        mListView.setChoiceMode(choiceMode);
    }

    /**
     * Tell the list to select nothing.
     */
    public final void setChoiceNone() {
        // On a phone, the default choice mode is already none, so nothing to do.
        if (!mTabletDevice) {
            return;
        }
        clearChoicesAndActivated();
        setChoiceMode(ListView.CHOICE_MODE_NONE);
    }

    /**
     * Tell the list to get out of selecting none.
     */
    public final void revertChoiceMode() {
        // On a phone, the default choice mode is always none, so nothing to do.
        if (!mTabletDevice) {
            return;
        }
        setChoiceMode(getDefaultChoiceMode(mTabletDevice));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {

        // Clear the list's adapter
        mListAdapter.destroy();
        mListView.setAdapter(null);

        mActivity.getViewMode().removeListener(this);
        if (mFolderObserver != null) {
            mFolderObserver.unregisterAndDestroy();
            mFolderObserver = null;
        }
        if (mConversationCursorObserver != null) {
            mUpdater.unregisterConversationListObserver(mConversationCursorObserver);
            mConversationCursorObserver = null;
        }
        mAccountObserver.unregisterAndDestroy();
        getAnimatedAdapter().cleanup();
        super.onDestroyView();
    }

    /**
     * There are three binary variables, which determine what we do with a
     * message. checkbEnabled: Whether check boxes are enabled or not (forced
     * true on tablet) cabModeOn: Whether CAB mode is currently on or not.
     * pressType: long or short tap (There is a third possibility: phone or
     * tablet, but they have <em>identical</em> behavior) The matrix of
     * possibilities is:
     * <p>
     * Long tap: Always toggle selection of conversation. If CAB mode is not
     * started, then start it.
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |   Select   |     Select
     *    List mode |   Select   |     Select
     *
     * </pre>
     *
     * Reference: http://b/issue?id=6392199
     * <p>
     * {@inheritDoc}
     */
    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // Ignore anything that is not a conversation item. Could be a footer.
        if (!(view instanceof ConversationItemView)) {
            return false;
        }
        return ((ConversationItemView) view).toggleCheckedState("long_press");
    }

    /**
     * See the comment for
     * {@link #onItemLongClick(AdapterView, View, int, long)}.
     * <p>
     * Short tap behavior:
     *
     * <pre>
     *              | Checkboxes | No Checkboxes
     *    ----------+------------+---------------
     *    CAB mode  |    Peek    |     Select
     *    List mode |    Peek    |      Peek
     * </pre>
     *
     * Reference: http://b/issue?id=6392199
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        onListItemSelected(view, position);
    }

    private void onListItemSelected(View view, int position) {
        if (view instanceof ToggleableItem) {
            final boolean showSenderImage =
                    (mAccount.settings.convListIcon == ConversationListIcon.SENDER_IMAGE);
            final boolean inCabMode = !mCheckedSet.isEmpty();
            if (!showSenderImage && inCabMode) {
                ((ToggleableItem) view).toggleCheckedState();
            } else {
                if (inCabMode) {
                    // this is a peek.
                    Analytics.getInstance().sendEvent("peek", null, null, mCheckedSet.size());
                }
                AnalyticsTimer.getInstance().trackStart(AnalyticsTimer.OPEN_CONV_VIEW_FROM_LIST);
                viewConversation(position);
            }
        } else {
            // Ignore anything that is not a conversation item. Could be a footer.
            // If we are using a keyboard, the highlighted item is the parent;
            // otherwise, this is a direct call from the ConverationItemView
            return;
        }
        // When a new list item is clicked, commit any existing leave behind
        // items. Wait until we have opened the desired conversation to cause
        // any position changes.
        commitDestructiveActions(Utils.useTabletUI(mActivity.getActivityContext().getResources()));
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (view instanceof  SwipeableListView) {
            SwipeableListView list = (SwipeableListView) view;
            // Don't need to handle ENTER because it's auto-handled as a "click".
            if (KeyboardUtils.isKeycodeDirectionEnd(keyCode, ViewUtils.isViewRtl(list))) {
                if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                    if (mKeyInitiatedFromList) {
                        int currentPos = list.getSelectedItemPosition();
                        if (currentPos < 0) {
                            // Find the activated item if the focused item is non-existent.
                            // This can happen when the user transitions from touch mode.
                            currentPos = list.getCheckedItemPosition();
                        }
                        if (currentPos >= 0) {
                            // We don't use onListItemSelected because right arrow should always
                            // view the conversation even in CAB/no_sender_image mode.
                            viewConversation(currentPos);
                            commitDestructiveActions(Utils.useTabletUI(
                                    mActivity.getActivityContext().getResources()));
                        }
                    }
                    mKeyInitiatedFromList = false;
                } else if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    mKeyInitiatedFromList = true;
                }
                return true;
            } else if ((keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN) &&
                    keyEvent.getAction() == KeyEvent.ACTION_UP) {
                final int position = list.getSelectedItemPosition();
                if (position >= 0) {
                    final Object item = getAnimatedAdapter().getItem(position);
                    if (item != null && item instanceof ConversationCursor) {
                        final Conversation conv = ((ConversationCursor) item).getConversation();
                        mCallbacks.onConversationFocused(conv);
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (!isCursorReadyToShow()) {
            // If the cursor got reset, let's reset the analytics state variable and show the list
            // view since we are waiting for load again
            mInitialCursorLoading = true;
            showListView();
        }

        final ConversationCursor conversationCursor = getConversationListCursor();
        if (conversationCursor != null) {
            conversationCursor.handleNotificationActions();

            restoreLastScrolledPosition();
        }

        mCheckedSet.addObserver(mConversationSetObserver);
    }

    @Override
    public void onPause() {
        super.onPause();

        mCheckedSet.removeObserver(mConversationSetObserver);

        saveLastScrolledPosition();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mListView != null) {
            outState.putParcelable(LIST_STATE_KEY, mListView.onSaveInstanceState());
            outState.putInt(CHOICE_MODE_KEY, mListView.getChoiceMode());
        }

        if (mListAdapter != null) {
            mListAdapter.saveSpecialItemInstanceState(outState);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mHandler.postDelayed(mUpdateTimestampsRunnable, TIMESTAMP_UPDATE_INTERVAL);
        Analytics.getInstance().sendView("ConversationListFragment");
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mUpdateTimestampsRunnable);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        if (mTabletDevice) {
            if (ViewMode.isListMode(newMode)) {
                // There are no checked conversations when in conversation list mode.
                clearChoicesAndActivated();
            }
        }
    }

    public boolean isAnimating() {
        final AnimatedAdapter adapter = getAnimatedAdapter();
        if (adapter != null && adapter.isAnimating()) {
            return true;
        }
        final boolean isScrolling = (mListView != null && mListView.isScrolling());
        if (isScrolling) {
            LogUtils.i(LOG_TAG, "CLF.isAnimating=true due to scrolling");
        }
        return isScrolling;
    }

    protected void clearChoicesAndActivated() {
        final int currentChecked = mListView.getCheckedItemPosition();
        if (currentChecked != ListView.INVALID_POSITION) {
            mListView.setItemChecked(currentChecked, false);
        }
    }

    /**
     * Handles a request to show a new conversation list, either from a search
     * query or for viewing a folder. This will initiate a data load, and hence
     * must be called on the UI thread.
     */
    private void showList() {
        mInitialCursorLoading = true;
        onFolderUpdated(mActivity.getFolderController().getFolder());
        onConversationListStatusUpdated();

        // try to get an order-of-magnitude sense for message count within folders
        // (N.B. this count currently isn't working for search folders, since their counts stream
        // in over time in pieces.)
        final Folder f = mViewContext.folder;
        if (f != null) {
            final long countLog;
            if (f.totalCount > 0) {
                countLog = (long) Math.log10(f.totalCount);
            } else {
                countLog = 0;
            }
            Analytics.getInstance().sendEvent("view_folder", f.getTypeDescription(),
                    Long.toString(countLog), f.totalCount);
        }
    }

    /**
     * View the message at the given position.
     *
     * @param position The position of the conversation in the list (as opposed to its position
     *        in the cursor)
     */
    private void viewConversation(final int position) {
        LogUtils.d(LOG_TAG, "ConversationListFragment.viewConversation(%d)", position);

        final Object item = getAnimatedAdapter().getItem(position);
        if (item != null && item instanceof ConversationCursor) {
            final ConversationCursor cursor = (ConversationCursor) item;
            final Conversation conv = cursor.getConversation();
        /*
         * The cursor position may be different than the position method parameter because of
         * special views in the list.
         */
            conv.position = cursor.getPosition();
            setActivated(conv, true);
            mCallbacks.onConversationSelected(conv, false /* inLoaderCallbacks */);
        } else {
            LogUtils.e(LOG_TAG,
                    "unable to open conv at cursor pos=%s item=%s getPositionOffset=%s",
                    position, item, getAnimatedAdapter().getPositionOffset(position));
        }
    }

    /**
     * Sets the checked conversation to the position given here.
     * @param conversation the activated conversation.
     * @param different if the currently checked conversation is different from the one provided
     * here.  This is a difference in conversations, not a difference in positions. For example, a
     * conversation at position 2 can move to position 4 as a result of new mail.
     */
    public void setActivated(final Conversation conversation, boolean different) {
        if (mListView.getChoiceMode() == ListView.CHOICE_MODE_NONE || conversation == null) {
            return;
        }

        final int cursorPosition = conversation.position;
        final int position = cursorPosition + mListAdapter.getPositionOffset(cursorPosition);
        setRawActivated(position, different);
        setRawSelected(conversation, position);
    }

    /**
     * Set the selected conversation (used by the framework to indicate current focus in the list).
     * @param conversation the selected conversation.
     */
    public void setSelected(final Conversation conversation) {
        if (mListView.getChoiceMode() == ListView.CHOICE_MODE_NONE || conversation == null) {
            return;
        }

        final int cursorPosition = conversation.position;
        final int position = cursorPosition + mListAdapter.getPositionOffset(cursorPosition);
        setRawSelected(conversation, position);
    }

    /**
     * Set the selected conversation (used by the framework to indicate current focus in the list).
     * @param position The position of the item in the list
     */
    private void setRawSelected(Conversation conversation, final int position) {
        final View selectedView = mListView.getChildAt(
                position - mListView.getFirstVisiblePosition());
        // Don't do anything if the view is already selected.
        if (!(selectedView != null && selectedView.isSelected())) {
            final int firstVisible = mListView.getFirstVisiblePosition();
            final int lastVisible = mListView.getLastVisiblePosition();
            // Check if the view is off the screen
            if (selectedView == null || position < firstVisible || position > lastVisible) {
                mListView.setSelection(position);
            } else {
                // If the view is on screen, we call setSelectionFromTop with a top offset. This
                // prevents the list from stupidly scrolling the item to the top because
                // setSelection calls setSelectionFromTop with y = 0.
                mListView.setSelectionFromTop(position, selectedView.getTop());
            }
            mListView.setSelectedConversation(conversation);
        }
    }

    /**
     * Sets the activated conversation to the position given here.
     * @param position The position of the item in the list
     * @param different if the currently activated conversation is different from the one provided
     * here.  This is a difference in conversations, not a difference in positions. For example, a
     * conversation at position 2 can move to position 4 as a result of new mail.
     */
    public void setRawActivated(final int position, final boolean different) {
        if (mListView.getChoiceMode() == ListView.CHOICE_MODE_NONE) {
            return;
        }

        if (different) {
            mListView.smoothScrollToPosition(position);
        }
        // Internally setItemChecked will set the activated bit if the item does not implement
        // the Checkable interface. We use checked state to indicated CAB selection mode.
        mListView.setItemChecked(position, true);
    }

    /**
     * Returns the cursor associated with the conversation list.
     * @return
     */
    private ConversationCursor getConversationListCursor() {
        return mCallbacks != null ? mCallbacks.getConversationListCursor() : null;
    }

    /**
     * Request a refresh of the list. No sync is carried out and none is
     * promised.
     */
    public void requestListRefresh() {
        mListAdapter.notifyDataSetChanged();
    }

    /**
     * Change the UI to delete the conversations provided and then call the
     * {@link DestructiveAction} provided here <b>after</b> the UI has been
     * updated.
     * @param conversations
     * @param action
     */
    public void requestDelete(int actionId, final Collection<Conversation> conversations,
            final DestructiveAction action) {
        for (Conversation conv : conversations) {
            conv.localDeleteOnUpdate = true;
        }
        final ListItemsRemovedListener listener = new ListItemsRemovedListener() {
            @Override
            public void onListItemsRemoved() {
                action.performAction();
            }
        };
        if (mListView.getSwipeAction() == actionId) {
            if (!mListView.destroyItems(conversations, listener)) {
                // The listView failed to destroy the items, perform the action manually
                LogUtils.e(LOG_TAG, "ConversationListFragment.requestDelete: " +
                        "listView failed to destroy items.");
                action.performAction();
            }
            return;
        }
        // Delete the local delete items (all for now) and when done,
        // update...
        mListAdapter.delete(conversations, listener);
    }

    public void onFolderUpdated(Folder folder) {
        if (!isCursorReadyToShow()) {
            // Wait a bit before showing either the empty or loading view. If the messages are
            // actually local, it's disorienting to see this appear on every folder transition.
            // If they aren't, then it will likely take more than 200 milliseconds to load, and
            // then we'll see the loading view.
            if (!mLoadingViewPending) {
                mHandler.postDelayed(mLoadingViewRunnable, LOADING_DELAY_MS);
                mLoadingViewPending = true;
            }
        }

        mFolder = folder;
        setSwipeAction();

        // Update enabled state of swipe to refresh.
        mSwipeRefreshWidget.setEnabled(!ConversationListContext.isSearchResult(mViewContext));

        if (mFolder == null) {
            return;
        }
        mListAdapter.setFolder(mFolder);
        mFooterView.setFolder(mFolder);
        if (!mFolder.wasSyncSuccessful()) {
            mErrorListener.onError(mFolder, false);
        }

        // Update the sync status bar with sync results if needed
        checkSyncStatus();

        // Blow away conversation items cache.
        ConversationItemViewModel.onFolderUpdated(mFolder);
    }

    /**
     * Updates the footer visibility and updates the conversation cursor
     */
    public void onConversationListStatusUpdated() {
        // Also change the cursor here.
        onCursorUpdated();

        if (isCursorReadyToShow() && mCanTakeDownLoadingView) {
            hideLoadingViewAndShowContents();
        }
    }

    private void hideLoadingViewAndShowContents() {
        final ConversationCursor cursor = getConversationListCursor();
        final boolean showFooter = mFooterView.updateStatus(cursor);
        // Update the sync status bar with sync results if needed
        checkSyncStatus();
        mListAdapter.setFooterVisibility(showFooter);
        mLoadingViewPending = false;
        mHandler.removeCallbacks(mLoadingViewRunnable);

        // Even though cursor might be empty, the list adapter might have teasers/footers.
        // So we check the list adapter count if the cursor is fully/partially loaded.
        if (mAccount.securityHold != 0) {
            showSecurityHoldView();
        } else if (mListAdapter.getCount() == 0) {
            showEmptyView();
        } else {
            showListView();
        }
    }

    private void setSwipeAction() {
        int swipeSetting = Settings.getSwipeSetting(mAccount.settings);
        if (swipeSetting == Swipe.DISABLED
                || !mAccount.supportsCapability(AccountCapabilities.UNDO)
                || (mFolder != null && mFolder.isTrash())) {
            mListView.enableSwipe(false);
        } else {
            final int action;
            mListView.enableSwipe(true);
            if (mFolder == null) {
                action = R.id.remove_folder;
            } else {
                switch (swipeSetting) {
                    // Try to respect user's setting as best as we can and default to doing nothing
                    case Swipe.DELETE:
                        // Delete in Outbox means discard failed message and put it in draft
                        if (mFolder.isType(UIProvider.FolderType.OUTBOX)) {
                            action = R.id.discard_outbox;
                        } else {
                            action = R.id.delete;
                        }
                        break;
                    case Swipe.ARCHIVE:
                        // Special case spam since it shouldn't remove spam folder label on swipe
                        if (mAccount.supportsCapability(AccountCapabilities.ARCHIVE)
                                && !mFolder.isSpam()) {
                            if (mFolder.supportsCapability(FolderCapabilities.ARCHIVE)) {
                                action = R.id.archive;
                                break;
                            } else if (mFolder.supportsCapability
                                    (FolderCapabilities.CAN_ACCEPT_MOVED_MESSAGES)) {
                                action = R.id.remove_folder;
                                break;
                            }
                        }

                        /*
                         * If we get here, we don't support archive, on either the account or the
                         * folder, so we want to fall through to swipe doing nothing
                         */
                        //$FALL-THROUGH$
                    default:
                        mListView.enableSwipe(false);
                        action = 0; // Use default value so setSwipeAction essentially has no effect
                        break;
                }
            }
            mListView.setSwipeAction(action);
        }
        mListView.setCurrentAccount(mAccount);
        mListView.setCurrentFolder(mFolder);
    }

    /**
     * Changes the conversation cursor in the list and sets checked position if none is set.
     */
    private void onCursorUpdated() {
        if (mCallbacks == null || mListAdapter == null) {
            return;
        }
        // Check against the previous cursor here and see if they are the same. If they are, then
        // do a notifyDataSetChanged.
        final ConversationCursor newCursor = mCallbacks.getConversationListCursor();

        if (newCursor == null && mListAdapter.getCursor() != null) {
            // We're losing our cursor, so save our scroll position
            saveLastScrolledPosition();
        }

        mListAdapter.swapCursor(newCursor);
        // When the conversation cursor is *updated*, we get back the same instance. In that
        // situation, CursorAdapter.swapCursor() silently returns, without forcing a
        // notifyDataSetChanged(). So let's force a call to notifyDataSetChanged, since an updated
        // cursor means that the dataset has changed.
        final int newCursorHash = (newCursor == null) ? 0 : newCursor.hashCode();
        if (mConversationCursorHash == newCursorHash && mConversationCursorHash != 0) {
            mListAdapter.notifyDataSetChanged();
        }
        mConversationCursorHash = newCursorHash;

        updateAnalyticsData(newCursor);
        if (newCursor != null) {
            final int newCursorCount = newCursor.getCount();
            updateSearchResultHeader(newCursorCount);
            if (newCursorCount > 0) {
                newCursor.markContentsSeen();
                restoreLastScrolledPosition();
            }
        }

        // If a current conversation is available, and none is activated in the list, then ask
        // the list to select the current conversation.
        final Conversation conv = mCallbacks.getCurrentConversation();
        final boolean currentConvIsPeeking = mCallbacks.isCurrentConversationJustPeeking();
        if (conv != null && !currentConvIsPeeking) {
            if (mListView.getChoiceMode() != ListView.CHOICE_MODE_NONE
                    && mListView.getCheckedItemPosition() == -1) {
                setActivated(conv, true);
            }
        }
    }

    public void commitDestructiveActions(boolean animate) {
        if (mListView != null) {
            mListView.commitDestructiveActions(animate);

        }
    }

    @Override
    public void onListItemSwiped(Collection<Conversation> conversations) {
        mUpdater.showNextConversation(conversations);
    }

    private void checkSyncStatus() {
        if (mFolder != null && mFolder.isSyncInProgress()) {
            LogUtils.d(LOG_TAG, "CLF.checkSyncStatus still syncing");
            // Still syncing, ignore
        } else {
            // Finished syncing:
            LogUtils.d(LOG_TAG, "CLF.checkSyncStatus done syncing");
            mSwipeRefreshWidget.setRefreshing(false);
        }
    }

    /**
     * Displays the indefinite progress bar indicating a sync is in progress.  This
     * should only be called if user manually requested a sync, and not for background syncs.
     */
    protected void showSyncStatusBar() {
        mSwipeRefreshWidget.setRefreshing(true);
    }

    /**
     * Clears all items in the list.
     */
    public void clear() {
        mListView.setAdapter(null);
    }

    private final ConversationSetObserver mConversationSetObserver = new ConversationSetObserver() {
        @Override
        public void onSetPopulated(final ConversationCheckedSet set) {
            // Disable the swipe to refresh widget.
            mSwipeRefreshWidget.setEnabled(false);
        }

        @Override
        public void onSetEmpty() {
            mSwipeRefreshWidget.setEnabled(true);
        }

        @Override
        public void onSetChanged(final ConversationCheckedSet set) {
            // Do nothing
        }
    };

    private void saveLastScrolledPosition() {
        if (mFolder == null || mFolder.conversationListUri == null ||
                mListAdapter.getCursor() == null) {
            // If you save your scroll position in an empty list, you're gonna have a bad time
            return;
        }

        final Parcelable savedState = mListView.onSaveInstanceState();

        mActivity.getListHandler().setConversationListScrollPosition(
                mFolder.conversationListUri.toString(), savedState);
    }

    private void restoreLastScrolledPosition() {
        // Scroll to our previous position, if necessary
        if (!mScrollPositionRestored && mFolder != null) {
            final String key = mFolder.conversationListUri.toString();
            final Parcelable savedState = mActivity.getListHandler()
                    .getConversationListScrollPosition(key);
            if (savedState != null) {
                mListView.onRestoreInstanceState(savedState);
            }
            mScrollPositionRestored = true;
        }
    }

    /* (non-Javadoc)
     * @see android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener#onRefresh()
     */
    @Override
    public void onRefresh() {
        Analytics.getInstance().sendEvent(Analytics.EVENT_CATEGORY_MENU_ITEM, "swipe_refresh", null,
                0);

        // This will call back to showSyncStatusBar():
        mActivity.getFolderController().requestFolderRefresh();

        // Clear list adapter state out of an abundance of caution.
        // There is a class of bugs where an animation that should have finished doesn't (maybe
        // it didn't start, or it didn't finish), and the list gets stuck pretty much forever.
        // Clearing the state here is in line with user expectation for 'refresh'.
        getAnimatedAdapter().clearAnimationState();
        // possibly act on the now-cleared state
        mActivity.onAnimationEnd(mListAdapter);
    }

    /**
     * Extracted function that handles Analytics state and logging updates for each new cursor
     * @param newCursor the new cursor pointer
     */
    private void updateAnalyticsData(ConversationCursor newCursor) {
        if (newCursor != null) {
            // Check if the initial data returned yet
            if (mInitialCursorLoading) {
                // This marks the very first time the cursor with the data the user sees returned.
                // We either have a cursor in LOADING state with cursor's count > 0, OR the cursor
                // completed loading.
                // Use this point to log the appropriate timing information that depends on when
                // the conversation list view finishes loading
                if (isCursorReadyToShow()) {
                    if (newCursor.getCount() == 0) {
                        Analytics.getInstance().sendEvent("empty_state", "post_label_change",
                                mFolder.getTypeDescription(), 0);
                    }
                    AnalyticsTimer.getInstance().logDuration(AnalyticsTimer.COLD_START_LAUNCHER,
                            true /* isDestructive */, "cold_start_to_list", "from_launcher", null);
                    // Don't need null checks because the activity, controller, and folder cannot
                    // be null in this case
                    if (mActivity.getFolderController().getFolder().isSearch()) {
                        AnalyticsTimer.getInstance().logDuration(AnalyticsTimer.SEARCH_TO_LIST,
                                true /* isDestructive */, "search_to_list", null, null);
                    }

                    mInitialCursorLoading = false;
                }
            } else {
                // Log the appropriate events that happen after the initial cursor is loaded
                if (newCursor.getCount() == 0 && mConversationCursorLastCount > 0) {
                    Analytics.getInstance().sendEvent("empty_state", "post_delete",
                            mFolder.getTypeDescription(), 0);
                }
            }

            // We save the count here because for folders that are empty, multiple successful
            // cursor loads will occur with size of 0. Thus we don't want to emit any false
            // positive post_delete events.
            mConversationCursorLastCount = newCursor.getCount();
        } else {
            mConversationCursorLastCount = 0;
        }
    }

    /**
     * Helper function to determine if the current cursor is ready to populate the UI
     * Since we extracted the functionality into a static function in ConversationCursor,
     * this function remains for the sole purpose of readability.
     * @return
     */
    private boolean isCursorReadyToShow() {
        return ConversationCursor.isCursorReadyToShow(getConversationListCursor());
    }

    public SwipeableListView getListView() {
        return mListView;
    }

    public void setNextFocusStartId(@IdRes int id) {
        mNextFocusStartId = id;
        setNextFocusStartOnList();
    }

    private void setNextFocusStartOnList() {
        if (mListView != null && mNextFocusStartId != 0) {
            // Since we manually handle right navigation from the list, let's just always set both
            // the default left and right navigation to the left id so that whenever the framework
            // handles one of these directions, it will go to the left side regardless of RTL.
            mListView.setNextFocusLeftId(mNextFocusStartId);
            mListView.setNextFocusRightId(mNextFocusStartId);
        }
    }

    public void onClick(View view) {
        if (view == mSecurityHoldButton) {
            final String accountSecurityUri = mAccount.accountSecurityUri;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(accountSecurityUri));
            startActivity(intent);
        }
    }

    @Override
    public final void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        mListView.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    /**
     * Used with SwipeableListView to change conv_list backgrounds to work around shadow elevation
     * issues causing and overdraw problems due to static backgrounds.
     *
     * @param view
     * @param scrollState
     */
    @Override
    public void onScrollStateChanged(final AbsListView view, final int scrollState) {
        mListView.onScrollStateChanged(view, scrollState);

        final View rootView = getView();

        // It seems that the list view is reading the scroll state, but the onCreateView has not
        // yet finished and the root view is null, so check that
        if (rootView != null) {
            // If not scrolling, assign default background - white for tablet, transparent for phone
            if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                rootView.setBackgroundColor(mDefaultListBackgroundColor);

                // Otherwise, list is scrolling, so remove background (corresponds to 0 input)
            } else {
                rootView.setBackgroundResource(0);
            }
        }
    }
}
