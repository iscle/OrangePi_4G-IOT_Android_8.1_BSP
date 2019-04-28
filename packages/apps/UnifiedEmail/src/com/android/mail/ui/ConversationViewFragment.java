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
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.IdRes;
import android.support.annotation.Nullable;
import android.support.v4.text.BidiFormatter;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.android.emailcommon.mail.Address;
import com.android.mail.FormattedDateBuilder;
import com.android.mail.R;
import com.android.mail.analytics.Analytics;
import com.android.mail.analytics.AnalyticsTimer;
import com.android.mail.browse.ConversationContainer;
import com.android.mail.browse.ConversationContainer.OverlayPosition;
import com.android.mail.browse.ConversationFooterView.ConversationFooterCallbacks;
import com.android.mail.browse.ConversationMessage;
import com.android.mail.browse.ConversationOverlayItem;
import com.android.mail.browse.ConversationViewAdapter;
import com.android.mail.browse.ConversationViewAdapter.ConversationFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageFooterItem;
import com.android.mail.browse.ConversationViewAdapter.MessageHeaderItem;
import com.android.mail.browse.ConversationViewAdapter.SuperCollapsedBlockItem;
import com.android.mail.browse.ConversationViewHeader;
import com.android.mail.browse.ConversationWebView;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreator;
import com.android.mail.browse.InlineAttachmentViewIntentBuilderCreatorHolder;
import com.android.mail.browse.MailWebView.ContentSizeChangeListener;
import com.android.mail.browse.MessageCursor;
import com.android.mail.browse.MessageFooterView;
import com.android.mail.browse.MessageHeaderView;
import com.android.mail.browse.ScrollIndicatorsView;
import com.android.mail.browse.SuperCollapsedBlock;
import com.android.mail.browse.WebViewContextMenu;
import com.android.mail.compose.ComposeActivity;
import com.android.mail.content.ObjectCursor;
import com.android.mail.print.PrintUtils;
import com.android.mail.providers.Account;
import com.android.mail.providers.Conversation;
import com.android.mail.providers.Message;
import com.android.mail.providers.Settings;
import com.android.mail.providers.UIProvider;
import com.android.mail.ui.ConversationViewState.ExpansionState;
import com.android.mail.utils.ConversationViewUtils;
import com.android.mail.utils.KeyboardUtils;
import com.android.mail.utils.LogTag;
import com.android.mail.utils.LogUtils;
import com.android.mail.utils.Utils;
import com.android.mail.utils.ViewUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The conversation view UI component.
 */
public class ConversationViewFragment extends AbstractConversationViewFragment implements
        SuperCollapsedBlock.OnClickListener, OnLayoutChangeListener,
        MessageHeaderView.MessageHeaderViewCallbacks, MessageFooterView.MessageFooterCallbacks,
        WebViewContextMenu.Callbacks, ConversationFooterCallbacks, View.OnKeyListener {

    private static final String LOG_TAG = LogTag.getLogTag();
    public static final String LAYOUT_TAG = "ConvLayout";

    /**
     * Difference in the height of the message header whose details have been expanded/collapsed
     */
    private int mDiff = 0;

    /**
     * Default value for {@link #mLoadWaitReason}. Conversation load will happen immediately.
     */
    private final int LOAD_NOW = 0;
    /**
     * Value for {@link #mLoadWaitReason} that means we are offscreen and waiting for the visible
     * conversation to finish loading before beginning our load.
     * <p>
     * When this value is set, the fragment should register with {@link ConversationListCallbacks}
     * to know when the visible conversation is loaded. When it is unset, it should unregister.
     */
    private final int LOAD_WAIT_FOR_INITIAL_CONVERSATION = 1;
    /**
     * Value for {@link #mLoadWaitReason} used when a conversation is too heavyweight to load at
     * all when not visible (e.g. requires network fetch, or too complex). Conversation load will
     * wait until this fragment is visible.
     */
    private final int LOAD_WAIT_UNTIL_VISIBLE = 2;

    // Default scroll distance when the user tries to scroll with up/down
    private final int DEFAULT_VERTICAL_SCROLL_DISTANCE_PX = 50;

    // Keyboard navigation
    private KeyboardNavigationController mNavigationController;
    // Since we manually control navigation for most of the conversation view due to problems
    // with two-pane layout but still rely on the system for SOME navigation, we need to keep track
    // of the view that had focus when KeyEvent.ACTION_DOWN was fired. This is because we only
    // manually change focus on KeyEvent.ACTION_UP (to prevent holding down the DOWN button and
    // lagging the app), however, the view in focus might have changed between ACTION_UP and
    // ACTION_DOWN since the system might have handled the ACTION_DOWN and moved focus.
    private View mOriginalKeyedView;
    private int mMaxScreenHeight;
    private int mTopOfVisibleScreen;

    protected ConversationContainer mConversationContainer;

    protected ConversationWebView mWebView;

    private ViewGroup mTopmostOverlay;

    private ConversationViewProgressController mProgressController;

    private ActionableToastBar mNewMessageBar;
    private ActionableToastBar.ActionClickedListener mNewMessageBarActionListener;

    protected HtmlConversationTemplates mTemplates;

    private final MailJsBridge mJsBridge = new MailJsBridge();

    protected ConversationViewAdapter mAdapter;

    protected boolean mViewsCreated;
    // True if we attempted to render before the views were laid out
    // We will render immediately once layout is done
    private boolean mNeedRender;

    /**
     * Temporary string containing the message bodies of the messages within a super-collapsed
     * block, for one-time use during block expansion. We cannot easily pass the body HTML
     * into JS without problematic escaping, so hold onto it momentarily and signal JS to fetch it
     * using {@link MailJsBridge}.
     */
    private String mTempBodiesHtml;

    private int  mMaxAutoLoadMessages;

    protected int mSideMarginPx;

    /**
     * If this conversation fragment is not visible, and it's inappropriate to load up front,
     * this is the reason we are waiting. This flag should be cleared once it's okay to load
     * the conversation.
     */
    private int mLoadWaitReason = LOAD_NOW;

    private boolean mEnableContentReadySignal;

    private ContentSizeChangeListener mWebViewSizeChangeListener;

    private float mWebViewYPercent;

    /**
     * Has loadData been called on the WebView yet?
     */
    private boolean mWebViewLoadedData;

    private long mWebViewLoadStartMs;

    private final Map<String, String> mMessageTransforms = Maps.newHashMap();

    private final DataSetObserver mLoadedObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            getHandler().post(new FragmentRunnable("delayedConversationLoad",
                    ConversationViewFragment.this) {
                @Override
                public void go() {
                    LogUtils.d(LOG_TAG, "CVF load observer fired, this=%s",
                            ConversationViewFragment.this);
                    handleDelayedConversationLoad();
                }
            });
        }
    };

    private final Runnable mOnProgressDismiss = new FragmentRunnable("onProgressDismiss", this) {
        @Override
        public void go() {
            LogUtils.d(LOG_TAG, "onProgressDismiss go() - isUserVisible() = %b", isUserVisible());
            if (isUserVisible()) {
                onConversationSeen();
            }
            mWebView.onRenderComplete();
        }
    };

    private static final boolean DEBUG_DUMP_CONVERSATION_HTML = false;
    private static final boolean DISABLE_OFFSCREEN_LOADING = false;
    private static final boolean DEBUG_DUMP_CURSOR_CONTENTS = false;

    private static final String BUNDLE_KEY_WEBVIEW_Y_PERCENT =
            ConversationViewFragment.class.getName() + "webview-y-percent";

    private BidiFormatter mBidiFormatter;

    /**
     * Contains a mapping between inline image attachments and their local message id.
     */
    private Map<String, String> mUrlToMessageIdMap;

    /**
     * Constructor needs to be public to handle orientation changes and activity lifecycle events.
     */
    public ConversationViewFragment() {}

    /**
     * Creates a new instance of {@link ConversationViewFragment}, initialized
     * to display a conversation with other parameters inherited/copied from an existing bundle,
     * typically one created using {@link #makeBasicArgs}.
     */
    public static ConversationViewFragment newInstance(Bundle existingArgs,
            Conversation conversation) {
        ConversationViewFragment f = new ConversationViewFragment();
        Bundle args = new Bundle(existingArgs);
        args.putParcelable(ARG_CONVERSATION, conversation);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onAccountChanged(Account newAccount, Account oldAccount) {
        // if overview mode has changed, re-render completely (no need to also update headers)
        if (isOverviewMode(newAccount) != isOverviewMode(oldAccount)) {
            setupOverviewMode();
            final MessageCursor c = getMessageCursor();
            if (c != null) {
                renderConversation(c);
            } else {
                // Null cursor means this fragment is either waiting to load or in the middle of
                // loading. Either way, a future render will happen anyway, and the new setting
                // will take effect when that happens.
            }
            return;
        }

        // settings may have been updated; refresh views that are known to
        // depend on settings
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        LogUtils.d(LOG_TAG, "IN CVF.onActivityCreated, this=%s visible=%s", this, isUserVisible());
        super.onActivityCreated(savedInstanceState);

        if (mActivity == null || mActivity.isFinishing()) {
            // Activity is finishing, just bail.
            return;
        }

        Context context = getContext();
        mTemplates = new HtmlConversationTemplates(context);

        final FormattedDateBuilder dateBuilder = new FormattedDateBuilder(context);

        mNavigationController = mActivity.getKeyboardNavigationController();

        mAdapter = new ConversationViewAdapter(mActivity, this,
                getLoaderManager(), this, this, getContactInfoSource(), this, this,
                getListController(), this, mAddressCache, dateBuilder, mBidiFormatter, this);
        mConversationContainer.setOverlayAdapter(mAdapter);

        // set up snap header (the adapter usually does this with the other ones)
        mConversationContainer.getSnapHeader().initialize(
                this, mAddressCache, this, getContactInfoSource(),
                mActivity.getAccountController().getVeiledAddressMatcher());

        final Resources resources = getResources();
        mMaxAutoLoadMessages = resources.getInteger(R.integer.max_auto_load_messages);

        mSideMarginPx = resources.getDimensionPixelOffset(
                R.dimen.conversation_message_content_margin_side);

        mUrlToMessageIdMap = new ArrayMap<String, String>();
        final InlineAttachmentViewIntentBuilderCreator creator =
                InlineAttachmentViewIntentBuilderCreatorHolder.
                getInlineAttachmentViewIntentCreator();
        final WebViewContextMenu contextMenu = new WebViewContextMenu(getActivity(),
                creator.createInlineAttachmentViewIntentBuilder(mAccount,
                mConversation != null ? mConversation.id : -1));
        contextMenu.setCallbacks(this);
        mWebView.setOnCreateContextMenuListener(contextMenu);

        // set this up here instead of onCreateView to ensure the latest Account is loaded
        setupOverviewMode();

        // Defer the call to initLoader with a Handler.
        // We want to wait until we know which fragments are present and their final visibility
        // states before going off and doing work. This prevents extraneous loading from occurring
        // as the ViewPager shifts about before the initial position is set.
        //
        // e.g. click on item #10
        // ViewPager.setAdapter() actually first loads #0 and #1 under the assumption that #0 is
        // the initial primary item
        // Then CPC immediately sets the primary item to #10, which tears down #0/#1 and sets up
        // #9/#10/#11.
        getHandler().post(new FragmentRunnable("showConversation", this) {
            @Override
            public void go() {
                showConversation();
            }
        });

        if (mConversation != null && mConversation.conversationBaseUri != null &&
                !Utils.isEmpty(mAccount.accountCookieQueryUri)) {
            // Set the cookie for this base url
            new SetCookieTask(getContext(), mConversation.conversationBaseUri.toString(),
                    mAccount.accountCookieQueryUri).execute();
        }

        // Find the height of the screen for manually scrolling the webview via keyboard.
        final Rect screen = new Rect();
        mActivity.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);
        mMaxScreenHeight = screen.bottom;
        mTopOfVisibleScreen = screen.top + mActivity.getSupportActionBar().getHeight();
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        mWebViewClient = createConversationWebViewClient();

        if (savedState != null) {
            mWebViewYPercent = savedState.getFloat(BUNDLE_KEY_WEBVIEW_Y_PERCENT);
        }

        mBidiFormatter = BidiFormatter.getInstance();
    }

    protected ConversationWebViewClient createConversationWebViewClient() {
        return new ConversationWebViewClient(mAccount);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.conversation_view, container, false);
        mConversationContainer = (ConversationContainer) rootView
                .findViewById(R.id.conversation_container);
        mConversationContainer.setAccountController(this);

        mTopmostOverlay =
                (ViewGroup) mConversationContainer.findViewById(R.id.conversation_topmost_overlay);
        mTopmostOverlay.setOnKeyListener(this);
        inflateSnapHeader(mTopmostOverlay, inflater);
        mConversationContainer.setupSnapHeader();

        setupNewMessageBar();

        mProgressController = new ConversationViewProgressController(this, getHandler());
        mProgressController.instantiateProgressIndicators(rootView);

        mWebView = (ConversationWebView)
                mConversationContainer.findViewById(R.id.conversation_webview);

        mWebView.addJavascriptInterface(mJsBridge, "mail");
        // On JB or newer, we use the 'webkitAnimationStart' DOM event to signal load complete
        // Below JB, try to speed up initial render by having the webview do supplemental draws to
        // custom a software canvas.
        // TODO(mindyp):
        //PAGE READINESS SIGNAL FOR JELLYBEAN AND NEWER
        // Notify the app on 'webkitAnimationStart' of a simple dummy element with a simple no-op
        // animation that immediately runs on page load. The app uses this as a signal that the
        // content is loaded and ready to draw, since WebView delays firing this event until the
        // layers are composited and everything is ready to draw.
        // This signal does not seem to be reliable, so just use the old method for now.
        final boolean isJBOrLater = Utils.isRunningJellybeanOrLater();
        final boolean isUserVisible = isUserVisible();
        mWebView.setUseSoftwareLayer(!isJBOrLater);
        mEnableContentReadySignal = isJBOrLater && isUserVisible;
        mWebView.onUserVisibilityChanged(isUserVisible);
        mWebView.setWebViewClient(mWebViewClient);
        final WebChromeClient wcc = new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    LogUtils.e(LOG_TAG, "JS: %s (%s:%d) f=%s", consoleMessage.message(),
                            consoleMessage.sourceId(), consoleMessage.lineNumber(),
                            ConversationViewFragment.this);
                } else {
                    LogUtils.i(LOG_TAG, "JS: %s (%s:%d) f=%s", consoleMessage.message(),
                            consoleMessage.sourceId(), consoleMessage.lineNumber(),
                            ConversationViewFragment.this);
                }
                return true;
            }
        };
        mWebView.setWebChromeClient(wcc);

        final WebSettings settings = mWebView.getSettings();

        final ScrollIndicatorsView scrollIndicators =
                (ScrollIndicatorsView) rootView.findViewById(R.id.scroll_indicators);
        scrollIndicators.setSourceView(mWebView);

        settings.setJavaScriptEnabled(true);

        ConversationViewUtils.setTextZoom(getResources(), settings);

        if (Utils.isRunningLOrLater()) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true /* accept */);
        }

        mViewsCreated = true;
        mWebViewLoadedData = false;

        return rootView;
    }

    protected void inflateSnapHeader(ViewGroup topmostOverlay, LayoutInflater inflater) {
        inflater.inflate(R.layout.conversation_topmost_overlay_items, topmostOverlay, true);
    }

    protected void setupNewMessageBar() {
        mNewMessageBar = (ActionableToastBar) mConversationContainer.findViewById(
                R.id.new_message_notification_bar);
        mNewMessageBarActionListener = new ActionableToastBar.ActionClickedListener() {
            @Override
            public void onActionClicked(Context context) {
                onNewMessageBarClick();
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mConversationContainer.setOverlayAdapter(null);
        mAdapter = null;
        resetLoadWaiting(); // be sure to unregister any active load observer
        mViewsCreated = false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putFloat(BUNDLE_KEY_WEBVIEW_Y_PERCENT, calculateScrollYPercent());
    }

    private float calculateScrollYPercent() {
        final float p;
        if (mWebView == null) {
            // onCreateView hasn't been called, return 0 as the user hasn't scrolled the view.
            return 0;
        }

        final int scrollY = mWebView.getScrollY();
        final int viewH = mWebView.getHeight();
        final int webH = (int) (mWebView.getContentHeight() * mWebView.getScale());

        if (webH == 0 || webH <= viewH) {
            p = 0;
        } else if (scrollY + viewH >= webH) {
            // The very bottom is a special case, it acts as a stronger anchor than the scroll top
            // at that point.
            p = 1.0f;
        } else {
            p = (float) scrollY / webH;
        }
        return p;
    }

    private void resetLoadWaiting() {
        if (mLoadWaitReason == LOAD_WAIT_FOR_INITIAL_CONVERSATION) {
            getListController().unregisterConversationLoadedObserver(mLoadedObserver);
        }
        mLoadWaitReason = LOAD_NOW;
    }

    @Override
    protected void markUnread() {
        super.markUnread();
        // Ignore unsafe calls made after a fragment is detached from an activity
        final ControllableActivity activity = (ControllableActivity) getActivity();
        if (activity == null) {
            LogUtils.w(LOG_TAG, "ignoring markUnread for conv=%s", mConversation.id);
            return;
        }

        if (mViewState == null) {
            LogUtils.i(LOG_TAG, "ignoring markUnread for conv with no view state (%d)",
                    mConversation.id);
            return;
        }
        activity.getConversationUpdater().markConversationMessagesUnread(mConversation,
                mViewState.getUnreadMessageUris(), mViewState.getConversationInfo());
    }

    @Override
    public void onUserVisibleHintChanged() {
        final boolean userVisible = isUserVisible();
        LogUtils.d(LOG_TAG, "ConversationViewFragment#onUserVisibleHintChanged(), userVisible = %b",
                userVisible);

        if (!userVisible) {
            mProgressController.dismissLoadingStatus();
        } else if (mViewsCreated) {
            String loadTag = null;
            final boolean isInitialLoading;
            if (mActivity != null) {
                isInitialLoading = mActivity.getConversationUpdater()
                    .isInitialConversationLoading();
            } else {
                isInitialLoading = true;
            }

            if (getMessageCursor() != null) {
                LogUtils.d(LOG_TAG, "Fragment is now user-visible, onConversationSeen: %s", this);
                if (!isInitialLoading) {
                    loadTag = "preloaded";
                }
                onConversationSeen();
            } else if (isLoadWaiting()) {
                LogUtils.d(LOG_TAG, "Fragment is now user-visible, showing conversation: %s", this);
                if (!isInitialLoading) {
                    loadTag = "load_deferred";
                }
                handleDelayedConversationLoad();
            }

            if (loadTag != null) {
                // pager swipes are visibility transitions to 'visible' except during initial
                // pager load on A) enter conversation mode B) rotate C) 2-pane conv-mode list-tap
              Analytics.getInstance().sendEvent("pager_swipe", loadTag,
                      getCurrentFolderTypeDesc(), 0);
            }
        }

        if (mWebView != null) {
            mWebView.onUserVisibilityChanged(userVisible);
        }
    }

    /**
     * Will either call initLoader now to begin loading, or set {@link #mLoadWaitReason} and do
     * nothing (in which case you should later call {@link #handleDelayedConversationLoad()}).
     */
    private void showConversation() {
        final int reason;

        if (isUserVisible()) {
            LogUtils.i(LOG_TAG,
                    "SHOWCONV: CVF is user-visible, immediately loading conversation (%s)", this);
            reason = LOAD_NOW;
            timerMark("CVF.showConversation");
        } else {
            final boolean disableOffscreenLoading = DISABLE_OFFSCREEN_LOADING
                    || Utils.isLowRamDevice(getContext())
                    || (mConversation != null && (mConversation.isRemote
                            || mConversation.getNumMessages() > mMaxAutoLoadMessages));

            // When not visible, we should not immediately load if either this conversation is
            // too heavyweight, or if the main/initial conversation is busy loading.
            if (disableOffscreenLoading) {
                reason = LOAD_WAIT_UNTIL_VISIBLE;
                LogUtils.i(LOG_TAG, "SHOWCONV: CVF waiting until visible to load (%s)", this);
            } else if (getListController().isInitialConversationLoading()) {
                reason = LOAD_WAIT_FOR_INITIAL_CONVERSATION;
                LogUtils.i(LOG_TAG, "SHOWCONV: CVF waiting for initial to finish (%s)", this);
                getListController().registerConversationLoadedObserver(mLoadedObserver);
            } else {
                LogUtils.i(LOG_TAG,
                        "SHOWCONV: CVF is not visible, but no reason to wait. loading now. (%s)",
                        this);
                reason = LOAD_NOW;
            }
        }

        mLoadWaitReason = reason;
        if (mLoadWaitReason == LOAD_NOW) {
            startConversationLoad();
        }
    }

    private void handleDelayedConversationLoad() {
        resetLoadWaiting();
        startConversationLoad();
    }

    private void startConversationLoad() {
        mWebView.setVisibility(View.VISIBLE);
        loadContent();
        // TODO(mindyp): don't show loading status for a previously rendered
        // conversation. Ielieve this is better done by making sure don't show loading status
        // until XX ms have passed without loading completed.
        mProgressController.showLoadingStatus(isUserVisible());
    }

    /**
     * Can be overridden in case a subclass needs to load something other than
     * the messages of a conversation.
     */
    protected void loadContent() {
        getLoaderManager().initLoader(MESSAGE_LOADER, Bundle.EMPTY, getMessageLoaderCallbacks());
    }

    private void revealConversation() {
        timerMark("revealing conversation");
        mProgressController.dismissLoadingStatus(mOnProgressDismiss);
        if (isUserVisible()) {
            AnalyticsTimer.getInstance().logDuration(AnalyticsTimer.OPEN_CONV_VIEW_FROM_LIST,
                    true /* isDestructive */, "open_conversation", "from_list", null);
        }
    }

    private boolean isLoadWaiting() {
        return mLoadWaitReason != LOAD_NOW;
    }

    private void renderConversation(MessageCursor messageCursor) {
        final String convHtml = renderMessageBodies(messageCursor, mEnableContentReadySignal);
        timerMark("rendered conversation");

        if (DEBUG_DUMP_CONVERSATION_HTML) {
            java.io.FileWriter fw = null;
            try {
                fw = new java.io.FileWriter(getSdCardFilePath());
                fw.write(convHtml);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            } finally {
                if (fw != null) {
                    try {
                        fw.close();
                    } catch (java.io.IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // save off existing scroll position before re-rendering
        if (mWebViewLoadedData) {
            mWebViewYPercent = calculateScrollYPercent();
        }

        mWebView.loadDataWithBaseURL(mBaseUri, convHtml, "text/html", "utf-8", null);
        mWebViewLoadedData = true;
        mWebViewLoadStartMs = SystemClock.uptimeMillis();
    }

    protected String getSdCardFilePath() {
        return "/sdcard/conv" + mConversation.id + ".html";
    }

    /**
     * Populate the adapter with overlay views (message headers, super-collapsed blocks, a
     * conversation header), and return an HTML document with spacer divs inserted for all overlays.
     *
     */
    protected String renderMessageBodies(MessageCursor messageCursor,
            boolean enableContentReadySignal) {
        int pos = -1;

        LogUtils.d(LOG_TAG, "IN renderMessageBodies, fragment=%s", this);
        boolean allowNetworkImages = false;

        // TODO: re-use any existing adapter item state (expanded, details expanded, show pics)

        // Walk through the cursor and build up an overlay adapter as you go.
        // Each overlay has an entry in the adapter for easy scroll handling in the container.
        // Items are not necessarily 1:1 in cursor and adapter because of super-collapsed blocks.
        // When adding adapter items, also add their heights to help the container later determine
        // overlay dimensions.

        // When re-rendering, prevent ConversationContainer from laying out overlays until after
        // the new spacers are positioned by WebView.
        mConversationContainer.invalidateSpacerGeometry();

        mAdapter.clear();

        // re-evaluate the message parts of the view state, since the messages may have changed
        // since the previous render
        final ConversationViewState prevState = mViewState;
        mViewState = new ConversationViewState(prevState);

        // N.B. the units of height for spacers are actually dp and not px because WebView assumes
        // a pixel is an mdpi pixel, unless you set device-dpi.

        // add a single conversation header item
        final int convHeaderPos = mAdapter.addConversationHeader(mConversation);
        final int convHeaderPx = measureOverlayHeight(convHeaderPos);

        mTemplates.startConversation(mWebView.getViewportWidth(),
                mWebView.screenPxToWebPx(mSideMarginPx), mWebView.screenPxToWebPx(convHeaderPx));

        int collapsedStart = -1;
        ConversationMessage prevCollapsedMsg = null;

        final boolean alwaysShowImages = shouldAlwaysShowImages();

        boolean prevSafeForImages = alwaysShowImages;

        boolean hasDraft = false;
        while (messageCursor.moveToPosition(++pos)) {
            final ConversationMessage msg = messageCursor.getMessage();

            final boolean safeForImages = alwaysShowImages ||
                    msg.alwaysShowImages || prevState.getShouldShowImages(msg);
            allowNetworkImages |= safeForImages;

            final Integer savedExpanded = prevState.getExpansionState(msg);
            final int expandedState;
            if (savedExpanded != null) {
                if (ExpansionState.isSuperCollapsed(savedExpanded) && messageCursor.isLast()) {
                    // override saved state when this is now the new last message
                    // this happens to the second-to-last message when you discard a draft
                    expandedState = ExpansionState.EXPANDED;
                } else {
                    expandedState = savedExpanded;
                }
            } else {
                // new messages that are not expanded default to being eligible for super-collapse
                if (msg.starred || !msg.read || messageCursor.isLast()) {
                    expandedState = ExpansionState.EXPANDED;
                } else if (messageCursor.isFirst()) {
                    expandedState = ExpansionState.COLLAPSED;
                } else {
                    expandedState = ExpansionState.SUPER_COLLAPSED;
                    hasDraft |= msg.isDraft();
                }
            }
            mViewState.setShouldShowImages(msg, prevState.getShouldShowImages(msg));
            mViewState.setExpansionState(msg, expandedState);

            // save off "read" state from the cursor
            // later, the view may not match the cursor (e.g. conversation marked read on open)
            // however, if a previous state indicated this message was unread, trust that instead
            // so "mark unread" marks all originally unread messages
            mViewState.setReadState(msg, msg.read && !prevState.isUnread(msg));

            // We only want to consider this for inclusion in the super collapsed block if
            // 1) The we don't have previous state about this message  (The first time that the
            //    user opens a conversation)
            // 2) The previously saved state for this message indicates that this message is
            //    in the super collapsed block.
            if (ExpansionState.isSuperCollapsed(expandedState)) {
                // contribute to a super-collapsed block that will be emitted just before the
                // next expanded header
                if (collapsedStart < 0) {
                    collapsedStart = pos;
                }
                prevCollapsedMsg = msg;
                prevSafeForImages = safeForImages;

                // This line puts the from address in the address cache so that
                // we get the sender image for it if it's in a super-collapsed block.
                getAddress(msg.getFrom());
                continue;
            }

            // resolve any deferred decisions on previous collapsed items
            if (collapsedStart >= 0) {
                if (pos - collapsedStart == 1) {
                    // Special-case for a single collapsed message: no need to super-collapse it.
                    renderMessage(prevCollapsedMsg, false /* expanded */, prevSafeForImages);
                } else {
                    renderSuperCollapsedBlock(collapsedStart, pos - 1, hasDraft);
                }
                hasDraft = false; // reset hasDraft
                prevCollapsedMsg = null;
                collapsedStart = -1;
            }

            renderMessage(msg, ExpansionState.isExpanded(expandedState), safeForImages);
        }

        final MessageHeaderItem lastHeaderItem = getLastMessageHeaderItem();
        final int convFooterPos = mAdapter.addConversationFooter(lastHeaderItem);
        final int convFooterPx = measureOverlayHeight(convFooterPos);

        mWebView.getSettings().setBlockNetworkImage(!allowNetworkImages);

        final boolean applyTransforms = shouldApplyTransforms();

        // If the conversation has specified a base uri, use it here, otherwise use mBaseUri
        return mTemplates.endConversation(mWebView.screenPxToWebPx(convFooterPx), mBaseUri,
                mConversation.getBaseUri(mBaseUri),
                mWebView.getViewportWidth(), mWebView.getWidthInDp(mSideMarginPx),
                enableContentReadySignal, isOverviewMode(mAccount), applyTransforms,
                applyTransforms);
    }

    private MessageHeaderItem getLastMessageHeaderItem() {
        int pos = mAdapter.getCount();
        while (--pos >= 0) {
            final ConversationOverlayItem item = mAdapter.getItem(pos);
            if (item instanceof MessageHeaderItem) {
                return (MessageHeaderItem) item;
            }
        }
        LogUtils.wtf(LOG_TAG, "No message header found");
        return null;
    }

    private void renderSuperCollapsedBlock(int start, int end, boolean hasDraft) {
        final int blockPos = mAdapter.addSuperCollapsedBlock(start, end, hasDraft);
        final int blockPx = measureOverlayHeight(blockPos);
        mTemplates.appendSuperCollapsedHtml(start, mWebView.screenPxToWebPx(blockPx));
    }

    private void renderMessage(ConversationMessage msg, boolean expanded, boolean safeForImages) {

        final int headerPos = mAdapter.addMessageHeader(msg, expanded,
                mViewState.getShouldShowImages(msg));
        final MessageHeaderItem headerItem = (MessageHeaderItem) mAdapter.getItem(headerPos);

        final int footerPos = mAdapter.addMessageFooter(headerItem);

        // Measure item header and footer heights to allocate spacers in HTML
        // But since the views themselves don't exist yet, render each item temporarily into
        // a host view for measurement.
        final int headerPx = measureOverlayHeight(headerPos);
        final int footerPx = measureOverlayHeight(footerPos);

        mTemplates.appendMessageHtml(msg, expanded, safeForImages,
                mWebView.screenPxToWebPx(headerPx), mWebView.screenPxToWebPx(footerPx));
        timerMark("rendered message");
    }

    private String renderCollapsedHeaders(MessageCursor cursor,
            SuperCollapsedBlockItem blockToReplace) {
        final List<ConversationOverlayItem> replacements = Lists.newArrayList();

        mTemplates.reset();

        final boolean alwaysShowImages = (mAccount != null) &&
                (mAccount.settings.showImages == Settings.ShowImages.ALWAYS);

        // In devices with non-integral density multiplier, screen pixels translate to non-integral
        // web pixels. Keep track of the error that occurs when we cast all heights to int
        float error = 0f;
        boolean first = true;
        for (int i = blockToReplace.getStart(), end = blockToReplace.getEnd(); i <= end; i++) {
            cursor.moveToPosition(i);
            final ConversationMessage msg = cursor.getMessage();

            final MessageHeaderItem header = ConversationViewAdapter.newMessageHeaderItem(
                    mAdapter, mAdapter.getDateBuilder(), msg, false /* expanded */,
                    alwaysShowImages || mViewState.getShouldShowImages(msg));
            final MessageFooterItem footer = mAdapter.newMessageFooterItem(mAdapter, header);

            final int headerPx = measureOverlayHeight(header);
            final int footerPx = measureOverlayHeight(footer);
            error += mWebView.screenPxToWebPxError(headerPx)
                    + mWebView.screenPxToWebPxError(footerPx);

            // When the error becomes greater than 1 pixel, make the next header 1 pixel taller
            int correction = 0;
            if (error >= 1) {
                correction = 1;
                error -= 1;
            }

            mTemplates.appendMessageHtml(msg, false /* expanded */,
                    alwaysShowImages || msg.alwaysShowImages,
                    mWebView.screenPxToWebPx(headerPx) + correction,
                    mWebView.screenPxToWebPx(footerPx));
            replacements.add(header);
            replacements.add(footer);

            mViewState.setExpansionState(msg, ExpansionState.COLLAPSED);
        }

        mAdapter.replaceSuperCollapsedBlock(blockToReplace, replacements);
        mAdapter.notifyDataSetChanged();

        return mTemplates.emit();
    }

    protected int measureOverlayHeight(int position) {
        return measureOverlayHeight(mAdapter.getItem(position));
    }

    /**
     * Measure the height of an adapter view by rendering an adapter item into a temporary
     * host view, and asking the view to immediately measure itself. This method will reuse
     * a previous adapter view from {@link ConversationContainer}'s scrap views if one was generated
     * earlier.
     * <p>
     * After measuring the height, this method also saves the height in the
     * {@link ConversationOverlayItem} for later use in overlay positioning.
     *
     * @param convItem adapter item with data to render and measure
     * @return height of the rendered view in screen px
     */
    private int measureOverlayHeight(ConversationOverlayItem convItem) {
        final int type = convItem.getType();

        final View convertView = mConversationContainer.getScrapView(type);
        final View hostView = mAdapter.getView(convItem, convertView, mConversationContainer,
                true /* measureOnly */);
        if (convertView == null) {
            mConversationContainer.addScrapView(type, hostView);
        }

        final int heightPx = mConversationContainer.measureOverlay(hostView);
        convItem.setHeight(heightPx);
        convItem.markMeasurementValid();

        return heightPx;
    }

    @Override
    public void onConversationViewHeaderHeightChange(int newHeight) {
        final int h = mWebView.screenPxToWebPx(newHeight);

        mWebView.loadUrl(String.format("javascript:setConversationHeaderSpacerHeight(%s);", h));
    }

    // END conversation header callbacks

    // START conversation footer callbacks

    @Override
    public void onConversationFooterHeightChange(int newHeight) {
        final int h = mWebView.screenPxToWebPx(newHeight);

        mWebView.loadUrl(String.format("javascript:setConversationFooterSpacerHeight(%s);", h));
    }

    // END conversation footer callbacks

    // START message header callbacks
    @Override
    public void setMessageSpacerHeight(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // update message HTML spacer height
        final int h = mWebView.screenPxToWebPx(newSpacerHeightPx);
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer h=%dwebPx (%dscreenPx)", h,
                newSpacerHeightPx);
        mWebView.loadUrl(String.format("javascript:setMessageHeaderSpacerHeight('%s', %s);",
                mTemplates.getMessageDomId(item.getMessage()), h));
    }

    @Override
    public void setMessageExpanded(MessageHeaderItem item, int newSpacerHeightPx) {
        mConversationContainer.invalidateSpacerGeometry();

        // show/hide the HTML message body and update the spacer height
        final int h = mWebView.screenPxToWebPx(newSpacerHeightPx);
        LogUtils.i(LAYOUT_TAG, "setting HTML spacer expanded=%s h=%dwebPx (%dscreenPx)",
                item.isExpanded(), h, newSpacerHeightPx);
        mWebView.loadUrl(String.format("javascript:setMessageBodyVisible('%s', %s, %s);",
                mTemplates.getMessageDomId(item.getMessage()), item.isExpanded(), h));

        mViewState.setExpansionState(item.getMessage(),
                item.isExpanded() ? ExpansionState.EXPANDED : ExpansionState.COLLAPSED);
    }

    @Override
    public void showExternalResources(final Message msg) {
        mViewState.setShouldShowImages(msg, true);
        mWebView.getSettings().setBlockNetworkImage(false);
        mWebView.loadUrl("javascript:unblockImages(['" + mTemplates.getMessageDomId(msg) + "']);");
    }

    @Override
    public void showExternalResources(final String senderRawAddress) {
        mWebView.getSettings().setBlockNetworkImage(false);

        final Address sender = getAddress(senderRawAddress);
        if (sender == null) {
            // Don't need to unblock any images
            return;
        }
        final MessageCursor cursor = getMessageCursor();

        final List<String> messageDomIds = new ArrayList<>();

        int pos = -1;
        while (cursor.moveToPosition(++pos)) {
            final ConversationMessage message = cursor.getMessage();
            if (sender.equals(getAddress(message.getFrom()))) {
                message.alwaysShowImages = true;

                mViewState.setShouldShowImages(message, true);
                messageDomIds.add(mTemplates.getMessageDomId(message));
            }
        }

        final String url = String.format(
                "javascript:unblockImages(['%s']);", TextUtils.join("','", messageDomIds));
        mWebView.loadUrl(url);
    }

    @Override
    public boolean supportsMessageTransforms() {
        return true;
    }

    @Override
    public String getMessageTransforms(final Message msg) {
        final String domId = mTemplates.getMessageDomId(msg);
        return (domId == null) ? null : mMessageTransforms.get(domId);
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    // END message header callbacks

    @Override
    public void showUntransformedConversation() {
        super.showUntransformedConversation();
        final MessageCursor cursor = getMessageCursor();
        if  (cursor != null) {
            renderConversation(cursor);
        }
    }

    @Override
    public void onSuperCollapsedClick(SuperCollapsedBlockItem item) {
        MessageCursor cursor = getMessageCursor();
        if (cursor == null || !mViewsCreated) {
            return;
        }

        mTempBodiesHtml = renderCollapsedHeaders(cursor, item);
        mWebView.loadUrl("javascript:replaceSuperCollapsedBlock(" + item.getStart() + ")");
        mConversationContainer.focusFirstMessageHeader();
    }

    private void showNewMessageNotification(NewMessagesInfo info) {
        mNewMessageBar.show(mNewMessageBarActionListener, info.getNotificationText(), R.string.show,
                true /* replaceVisibleToast */, false /* autohide */, null /* ToastBarOperation */);
    }

    private void onNewMessageBarClick() {
        mNewMessageBar.hide(true, true);

        renderConversation(getMessageCursor()); // mCursor is already up-to-date
                                                // per onLoadFinished()
    }

    private static OverlayPosition[] parsePositions(final int[] topArray, final int[] bottomArray) {
        final int len = topArray.length;
        final OverlayPosition[] positions = new OverlayPosition[len];
        for (int i = 0; i < len; i++) {
            positions[i] = new OverlayPosition(topArray[i], bottomArray[i]);
        }
        return positions;
    }

    protected @Nullable Address getAddress(String rawFrom) {
        return Utils.getAddress(mAddressCache, rawFrom);
    }

    private void ensureContentSizeChangeListener() {
        if (mWebViewSizeChangeListener == null) {
            mWebViewSizeChangeListener = new ContentSizeChangeListener() {
                @Override
                public void onHeightChange(int h) {
                    // When WebKit says the DOM height has changed, re-measure
                    // bodies and re-position their headers.
                    // This is separate from the typical JavaScript DOM change
                    // listeners because cases like NARROW_COLUMNS text reflow do not trigger DOM
                    // events.
                    mWebView.loadUrl("javascript:measurePositions();");
                }
            };
        }
        mWebView.setContentSizeChangeListener(mWebViewSizeChangeListener);
    }

    public static boolean isOverviewMode(Account acct) {
        return acct.settings.isOverviewMode();
    }

    private void setupOverviewMode() {
        // for now, overview mode means use the built-in WebView zoom and disable custom scale
        // gesture handling
        final boolean overviewMode = isOverviewMode(mAccount);
        final WebSettings settings = mWebView.getSettings();
        final WebSettings.LayoutAlgorithm layout;
        settings.setUseWideViewPort(overviewMode);
        settings.setSupportZoom(overviewMode);
        settings.setBuiltInZoomControls(overviewMode);
        settings.setLoadWithOverviewMode(overviewMode);
        if (overviewMode) {
            settings.setDisplayZoomControls(false);
            layout = WebSettings.LayoutAlgorithm.NORMAL;
        } else {
            layout = WebSettings.LayoutAlgorithm.NARROW_COLUMNS;
        }
        settings.setLayoutAlgorithm(layout);
    }

    @Override
    public ConversationMessage getMessageForClickedUrl(String url) {
        final String domMessageId = mUrlToMessageIdMap.get(url);
        if (domMessageId == null) {
            return null;
        }
        final MessageCursor messageCursor = getMessageCursor();
        if (messageCursor == null) {
            return null;
        }
        final String messageId = mTemplates.getMessageIdForDomId(domMessageId);
        return messageCursor.getMessageForId(Long.parseLong(messageId));
    }

    /**
     * Determines if we should intercept the left/right key event generated by the hardware
     * keyboard so the framework won't handle directional navigation for us.
     */
    private boolean shouldInterceptLeftRightEvents(@IdRes int id, boolean isLeft, boolean isRight,
            boolean twoPaneLand) {
        return twoPaneLand && (id == R.id.conversation_topmost_overlay ||
                id == R.id.upper_header ||
                id == R.id.super_collapsed_block ||
                id == R.id.message_footer ||
                (id == R.id.overflow && isRight) ||
                (id == R.id.reply_button && isLeft) ||
                (id == R.id.forward_button && isRight));
    }

    /**
     * Indicates if the direction with the provided id should navigate away from the conversation
     * view. Note that this is only applicable in two-pane landscape mode.
     */
    private boolean shouldNavigateAway(@IdRes int id, boolean isLeft, boolean twoPaneLand) {
        return twoPaneLand && isLeft && (id == R.id.conversation_topmost_overlay ||
                id == R.id.upper_header ||
                id == R.id.super_collapsed_block ||
                id == R.id.message_footer ||
                id == R.id.reply_button);
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            mOriginalKeyedView = view;
        }

        if (mOriginalKeyedView != null) {
            final int id = mOriginalKeyedView.getId();
            final boolean isRtl = ViewUtils.isViewRtl(mOriginalKeyedView);
            final boolean isActionUp = keyEvent.getAction() == KeyEvent.ACTION_UP;
            final boolean isStart = KeyboardUtils.isKeycodeDirectionStart(keyCode, isRtl);
            final boolean isEnd = KeyboardUtils.isKeycodeDirectionEnd(keyCode, isRtl);
            final boolean isUp = keyCode == KeyEvent.KEYCODE_DPAD_UP;
            final boolean isDown = keyCode == KeyEvent.KEYCODE_DPAD_DOWN;

            // First we run the event by the controller
            // We manually check if the view+direction combination should shift focus away from the
            // conversation view to the thread list in two-pane landscape mode.
            final boolean isTwoPaneLand = mNavigationController.isTwoPaneLandscape();
            final boolean navigateAway = shouldNavigateAway(id, isStart, isTwoPaneLand);
            if (mNavigationController.onInterceptKeyFromCV(keyCode, keyEvent, navigateAway)) {
                return true;
            }

            // If controller didn't handle the event, check directional interception.
            if ((isStart || isEnd) && shouldInterceptLeftRightEvents(
                    id, isStart, isEnd, isTwoPaneLand)) {
                return true;
            } else if (isUp || isDown) {
                // We don't do anything on up/down for overlay
                if (id == R.id.conversation_topmost_overlay) {
                    return true;
                }

                // We manually handle up/down navigation through the overlay items because the
                // system's default isn't optimal for two-pane landscape since it's not a real list.
                final View next = mConversationContainer.getNextOverlayView(mOriginalKeyedView,
                        isDown);
                if (next != null) {
                    focusAndScrollToView(next);
                } else if (!isActionUp) {
                    // Scroll in the direction of the arrow if next view isn't found.
                    final int currentY = mWebView.getScrollY();
                    if (isUp && currentY > 0) {
                        mWebView.scrollBy(0,
                                -Math.min(currentY, DEFAULT_VERTICAL_SCROLL_DISTANCE_PX));
                    } else if (isDown) {
                        final int webviewEnd = (int) (mWebView.getContentHeight() *
                                mWebView.getScale());
                        final int currentEnd = currentY + mWebView.getHeight();
                        if (currentEnd < webviewEnd) {
                            mWebView.scrollBy(0, Math.min(webviewEnd - currentEnd,
                                    DEFAULT_VERTICAL_SCROLL_DISTANCE_PX));
                        }
                    }
                }
                return true;
            }

            // Finally we handle the special keys
            if (keyCode == KeyEvent.KEYCODE_BACK && id != R.id.conversation_topmost_overlay) {
                if (isActionUp) {
                    mTopmostOverlay.requestFocus();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_ENTER &&
                    id == R.id.conversation_topmost_overlay) {
                if (isActionUp) {
                    mWebView.scrollTo(0, 0);
                    mConversationContainer.focusFirstMessageHeader();
                }
                return true;
            }
        }
        return false;
    }

    private void focusAndScrollToView(View v) {
        // Make sure that v is in view
        final int[] coords = new int[2];
        v.getLocationOnScreen(coords);
        final int bottom = coords[1] + v.getHeight();
        if (bottom > mMaxScreenHeight) {
            mWebView.scrollBy(0, bottom - mMaxScreenHeight);
        } else if (coords[1] < mTopOfVisibleScreen) {
            mWebView.scrollBy(0, coords[1] - mTopOfVisibleScreen);
        }
        v.requestFocus();
    }

    public class ConversationWebViewClient extends AbstractConversationWebViewClient {
        public ConversationWebViewClient(Account account) {
            super(account);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            // try to locate the message associated with the url
            final ConversationMessage cm = getMessageForClickedUrl(url);
            if (cm != null) {
                // try to load the url assuming it is a cid url
                final Uri uri = Uri.parse(url);
                final WebResourceResponse response = loadCIDUri(uri, cm);
                if (response != null) {
                    return response;
                }
            }

            // otherwise, attempt the default handling
            return super.shouldInterceptRequest(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            // Ignore unsafe calls made after a fragment is detached from an activity.
            // This method needs to, for example, get at the loader manager, which needs
            // the fragment to be added.
            if (!isAdded() || !mViewsCreated) {
                LogUtils.d(LOG_TAG, "ignoring CVF.onPageFinished, url=%s fragment=%s", url,
                        ConversationViewFragment.this);
                return;
            }

            LogUtils.d(LOG_TAG, "IN CVF.onPageFinished, url=%s fragment=%s wv=%s t=%sms", url,
                    ConversationViewFragment.this, view,
                    (SystemClock.uptimeMillis() - mWebViewLoadStartMs));

            ensureContentSizeChangeListener();

            if (!mEnableContentReadySignal) {
                revealConversation();
            }

            final Set<String> emailAddresses = Sets.newHashSet();
            final List<Address> cacheCopy;
            synchronized (mAddressCache) {
                cacheCopy = ImmutableList.copyOf(mAddressCache.values());
            }
            for (Address addr : cacheCopy) {
                emailAddresses.add(addr.getAddress());
            }
            final ContactLoaderCallbacks callbacks = getContactInfoSource();
            callbacks.setSenders(emailAddresses);
            getLoaderManager().restartLoader(CONTACT_LOADER, Bundle.EMPTY, callbacks);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return mViewsCreated && super.shouldOverrideUrlLoading(view, url);
        }
    }

    /**
     * NOTE: all public methods must be listed in the proguard flags so that they can be accessed
     * via reflection and not stripped.
     *
     */
    private class MailJsBridge {
        @JavascriptInterface
        public void onWebContentGeometryChange(final int[] overlayTopStrs,
                final int[] overlayBottomStrs) {
            try {
                getHandler().post(new FragmentRunnable("onWebContentGeometryChange",
                        ConversationViewFragment.this) {
                    @Override
                    public void go() {
                        if (!mViewsCreated) {
                            LogUtils.d(LOG_TAG, "ignoring webContentGeometryChange because views"
                                    + " are gone, %s", ConversationViewFragment.this);
                            return;
                        }
                        mConversationContainer.onGeometryChange(
                                parsePositions(overlayTopStrs, overlayBottomStrs));
                        if (mDiff != 0) {
                            // SCROLL!
                            int scale = (int) (mWebView.getScale() / mWebView.getInitialScale());
                            if (scale > 1) {
                                mWebView.scrollBy(0, (mDiff * (scale - 1)));
                            }
                            mDiff = 0;
                        }
                    }
                });
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onWebContentGeometryChange");
            }
        }

        @JavascriptInterface
        public String getTempMessageBodies() {
            try {
                if (!mViewsCreated) {
                    return "";
                }

                final String s = mTempBodiesHtml;
                mTempBodiesHtml = null;
                return s;
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.getTempMessageBodies");
                return "";
            }
        }

        @JavascriptInterface
        public String getMessageBody(String domId) {
            try {
                final MessageCursor cursor = getMessageCursor();
                if (!mViewsCreated || cursor == null) {
                    return "";
                }

                int pos = -1;
                while (cursor.moveToPosition(++pos)) {
                    final ConversationMessage msg = cursor.getMessage();
                    if (TextUtils.equals(domId, mTemplates.getMessageDomId(msg))) {
                        return HtmlConversationTemplates.wrapMessageBody(msg.getBodyAsHtml());
                    }
                }

                return "";

            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.getMessageBody");
                return "";
            }
        }

        @JavascriptInterface
        public String getMessageSender(String domId) {
            try {
                final MessageCursor cursor = getMessageCursor();
                if (!mViewsCreated || cursor == null) {
                    return "";
                }

                int pos = -1;
                while (cursor.moveToPosition(++pos)) {
                    final ConversationMessage msg = cursor.getMessage();
                    if (TextUtils.equals(domId, mTemplates.getMessageDomId(msg))) {
                        final Address address = getAddress(msg.getFrom());
                        if (address != null) {
                            return address.getAddress();
                        } else {
                            // Fall through to return an empty string
                            break;
                        }
                    }
                }

                return "";

            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.getMessageSender");
                return "";
            }
        }

        @JavascriptInterface
        public void onContentReady() {
            try {
                getHandler().post(new FragmentRunnable("onContentReady",
                        ConversationViewFragment.this) {
                    @Override
                    public void go() {
                        try {
                            if (mWebViewLoadStartMs != 0) {
                                LogUtils.i(LOG_TAG, "IN CVF.onContentReady, f=%s vis=%s t=%sms",
                                        ConversationViewFragment.this,
                                        isUserVisible(),
                                        (SystemClock.uptimeMillis() - mWebViewLoadStartMs));
                            }
                            revealConversation();
                        } catch (Throwable t) {
                            LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onContentReady");
                            // Still try to show the conversation.
                            revealConversation();
                        }
                    }
                });
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onContentReady");
            }
        }

        @JavascriptInterface
        public float getScrollYPercent() {
            try {
                return mWebViewYPercent;
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.getScrollYPercent");
                return 0f;
            }
        }

        @JavascriptInterface
        public void onMessageTransform(String messageDomId, String transformText) {
            try {
                LogUtils.i(LOG_TAG, "TRANSFORM: (%s) %s", messageDomId, transformText);
                mMessageTransforms.put(messageDomId, transformText);
                onConversationTransformed();
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onMessageTransform");
            }
        }

        @JavascriptInterface
        public void onInlineAttachmentsParsed(final String[] urls, final String[] messageIds) {
            try {
                getHandler().post(new FragmentRunnable("onInlineAttachmentsParsed",
                        ConversationViewFragment.this) {
                    @Override
                    public void go() {
                        try {
                            for (int i = 0, size = urls.length; i < size; i++) {
                                mUrlToMessageIdMap.put(urls[i], messageIds[i]);
                            }
                        } catch (ArrayIndexOutOfBoundsException e) {
                            LogUtils.e(LOG_TAG, e,
                                    "Number of urls does not match number of message ids - %s:%s",
                                    urls.length, messageIds.length);
                        }
                    }
                });
            } catch (Throwable t) {
                LogUtils.e(LOG_TAG, t, "Error in MailJsBridge.onInlineAttachmentsParsed");
            }
        }
    }

    private class NewMessagesInfo {
        int count;
        int countFromSelf;

        /**
         * Return the display text for the new message notification overlay. It will be formatted
         * appropriately for a single new message vs. multiple new messages.
         *
         * @return display text
         */
        public String getNotificationText() {
            return getResources().getQuantityString(R.plurals.new_incoming_messages, count, count);
        }
    }

    @Override
    public void onMessageCursorLoadFinished(Loader<ObjectCursor<ConversationMessage>> loader,
            MessageCursor newCursor, MessageCursor oldCursor) {
        /*
         * what kind of changes affect the MessageCursor? 1. new message(s) 2.
         * read/unread state change 3. deleted message, either regular or draft
         * 4. updated message, either from self or from others, updated in
         * content or state or sender 5. star/unstar of message (technically
         * similar to #1) 6. other label change Use MessageCursor.hashCode() to
         * sort out interesting vs. no-op cursor updates.
         */

        if (oldCursor != null && !oldCursor.isClosed()) {
            final NewMessagesInfo info = getNewIncomingMessagesInfo(newCursor);

            if (info.count > 0) {
                // don't immediately render new incoming messages from other
                // senders
                // (to avoid a new message from losing the user's focus)
                LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                        + ", holding cursor for new incoming message (%s)", this);
                showNewMessageNotification(info);
                return;
            }

            final int oldState = oldCursor.getStateHashCode();
            final boolean changed = newCursor.getStateHashCode() != oldState;

            if (!changed) {
                final boolean processedInPlace = processInPlaceUpdates(newCursor, oldCursor);
                if (processedInPlace) {
                    LogUtils.i(LOG_TAG, "CONV RENDER: processed update(s) in place (%s)", this);
                } else {
                    LogUtils.i(LOG_TAG, "CONV RENDER: uninteresting update"
                            + ", ignoring this conversation update (%s)", this);
                }
                return;
            } else if (info.countFromSelf == 1) {
                // Special-case the very common case of a new cursor that is the same as the old
                // one, except that there is a new message from yourself. This happens upon send.
                final boolean sameExceptNewLast = newCursor.getStateHashCode(1) == oldState;
                if (sameExceptNewLast) {
                    LogUtils.i(LOG_TAG, "CONV RENDER: update is a single new message from self"
                            + " (%s)", this);
                    newCursor.moveToLast();
                    processNewOutgoingMessage(newCursor.getMessage());
                    return;
                }
            }
            // cursors are different, and not due to an incoming message. fall
            // through and render.
            LogUtils.i(LOG_TAG, "CONV RENDER: conversation updated"
                    + ", but not due to incoming message. rendering. (%s)", this);

            if (DEBUG_DUMP_CURSOR_CONTENTS) {
                LogUtils.i(LOG_TAG, "old cursor: %s", oldCursor.getDebugDump());
                LogUtils.i(LOG_TAG, "new cursor: %s", newCursor.getDebugDump());
            }
        } else {
            LogUtils.i(LOG_TAG, "CONV RENDER: initial render. (%s)", this);
            timerMark("message cursor load finished");
        }

        renderContent(newCursor);
    }

    protected void renderContent(MessageCursor messageCursor) {
        // if layout hasn't happened, delay render
        // This is needed in addition to the showConversation() delay to speed
        // up rotation and restoration.
        if (mConversationContainer.getWidth() == 0) {
            mNeedRender = true;
            mConversationContainer.addOnLayoutChangeListener(this);
        } else {
            renderConversation(messageCursor);
        }
    }

    private NewMessagesInfo getNewIncomingMessagesInfo(MessageCursor newCursor) {
        final NewMessagesInfo info = new NewMessagesInfo();

        int pos = -1;
        while (newCursor.moveToPosition(++pos)) {
            final Message m = newCursor.getMessage();
            if (!mViewState.contains(m)) {
                LogUtils.i(LOG_TAG, "conversation diff: found new msg: %s", m.uri);

                final Address from = getAddress(m.getFrom());
                // distinguish ours from theirs
                // new messages from the account owner should not trigger a
                // notification
                if (from == null || mAccount.ownsFromAddress(from.getAddress())) {
                    LogUtils.i(LOG_TAG, "found message from self: %s", m.uri);
                    info.countFromSelf++;
                    continue;
                }

                info.count++;
            }
        }
        return info;
    }

    private boolean processInPlaceUpdates(MessageCursor newCursor, MessageCursor oldCursor) {
        final Set<String> idsOfChangedBodies = Sets.newHashSet();
        final List<Integer> changedOverlayPositions = Lists.newArrayList();

        boolean changed = false;

        int pos = 0;
        while (true) {
            if (!newCursor.moveToPosition(pos) || !oldCursor.moveToPosition(pos)) {
                break;
            }

            final ConversationMessage newMsg = newCursor.getMessage();
            final ConversationMessage oldMsg = oldCursor.getMessage();

            // We are going to update the data in the adapter whenever any input fields change.
            // This ensures that the Message object that ComposeActivity uses will be correctly
            // aligned with the most up-to-date data.
            if (!newMsg.isEqual(oldMsg)) {
                mAdapter.updateItemsForMessage(newMsg, changedOverlayPositions);
                LogUtils.i(LOG_TAG, "msg #%d (%d): detected field(s) change. sendingState=%s",
                        pos, newMsg.id, newMsg.sendingState);
            }

            // update changed message bodies in-place
            if (!TextUtils.equals(newMsg.bodyHtml, oldMsg.bodyHtml) ||
                    !TextUtils.equals(newMsg.bodyText, oldMsg.bodyText)) {
                // maybe just set a flag to notify JS to re-request changed bodies
                idsOfChangedBodies.add('"' + mTemplates.getMessageDomId(newMsg) + '"');
                LogUtils.i(LOG_TAG, "msg #%d (%d): detected body change", pos, newMsg.id);
            }

            pos++;
        }


        if (!changedOverlayPositions.isEmpty()) {
            // notify once after the entire adapter is updated
            mConversationContainer.onOverlayModelUpdate(changedOverlayPositions);
            changed = true;
        }

        final ConversationFooterItem footerItem = mAdapter.getFooterItem();
        if (footerItem != null) {
            footerItem.invalidateMeasurement();
        }
        if (!idsOfChangedBodies.isEmpty()) {
            mWebView.loadUrl(String.format("javascript:replaceMessageBodies([%s]);",
                    TextUtils.join(",", idsOfChangedBodies)));
            changed = true;
        }

        return changed;
    }

    private void processNewOutgoingMessage(ConversationMessage msg) {
        // Temporarily remove the ConversationFooterItem and its view.
        // It will get re-added right after the new message is added.
        final ConversationFooterItem footerItem = mAdapter.removeFooterItem();
        // if no footer, just skip the work for it. The rest should be fine to do.
        if (footerItem != null) {
            mConversationContainer.removeViewAtAdapterIndex(footerItem.getPosition());
        } else {
            LogUtils.i(LOG_TAG, "footer item not found");
        }

        mTemplates.reset();
        // this method will add some items to mAdapter, but we deliberately want to avoid notifying
        // adapter listeners (i.e. ConversationContainer) until onWebContentGeometryChange is next
        // called, to prevent N+1 headers rendering with N message bodies.
        renderMessage(msg, true /* expanded */, msg.alwaysShowImages);
        mTempBodiesHtml = mTemplates.emit();

        if (footerItem != null) {
            footerItem.setLastMessageHeaderItem(getLastMessageHeaderItem());
            footerItem.invalidateMeasurement();
            mAdapter.addItem(footerItem);
        }

        mViewState.setExpansionState(msg, ExpansionState.EXPANDED);
        // FIXME: should the provider set this as initial state?
        mViewState.setReadState(msg, false /* read */);

        // From now until the updated spacer geometry is returned, the adapter items are mismatched
        // with the existing spacers. Do not let them layout.
        mConversationContainer.invalidateSpacerGeometry();

        mWebView.loadUrl("javascript:appendMessageHtml();");
    }

    private static class SetCookieTask extends AsyncTask<Void, Void, Void> {
        private final Context mContext;
        private final String mUri;
        private final Uri mAccountCookieQueryUri;
        private final ContentResolver mResolver;

        /* package */ SetCookieTask(Context context, String baseUri, Uri accountCookieQueryUri) {
            mContext = context;
            mUri = baseUri;
            mAccountCookieQueryUri = accountCookieQueryUri;
            mResolver = context.getContentResolver();
        }

        @Override
        public Void doInBackground(Void... args) {
            // First query for the cookie string from the UI provider
            final Cursor cookieCursor = mResolver.query(mAccountCookieQueryUri,
                    UIProvider.ACCOUNT_COOKIE_PROJECTION, null, null, null);
            if (cookieCursor == null) {
                return null;
            }

            try {
                if (cookieCursor.moveToFirst()) {
                    final String cookie = cookieCursor.getString(
                            cookieCursor.getColumnIndex(UIProvider.AccountCookieColumns.COOKIE));

                    if (cookie != null) {
                        final CookieSyncManager csm =
                                CookieSyncManager.createInstance(mContext);
                        CookieManager.getInstance().setCookie(mUri, cookie);
                        csm.sync();
                    }
                }

            } finally {
                cookieCursor.close();
            }


            return null;
        }
    }

    @Override
    public void onConversationUpdated(Conversation conv) {
        final ConversationViewHeader headerView = (ConversationViewHeader) mConversationContainer
                .findViewById(R.id.conversation_header);
        mConversation = conv;
        if (headerView != null) {
            headerView.onConversationUpdated(conv);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right,
            int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        boolean sizeChanged = mNeedRender
                && mConversationContainer.getWidth() != 0;
        if (sizeChanged) {
            mNeedRender = false;
            mConversationContainer.removeOnLayoutChangeListener(this);
            renderConversation(getMessageCursor());
        }
    }

    @Override
    public void setMessageDetailsExpanded(MessageHeaderItem i, boolean expanded, int heightBefore) {
        mDiff = (expanded ? 1 : -1) * Math.abs(i.getHeight() - heightBefore);
    }

    /**
     * @return {@code true} because either the Print or Print All menu item is shown in GMail
     */
    @Override
    protected boolean shouldShowPrintInOverflow() {
        return true;
    }

    @Override
    protected void printConversation() {
        PrintUtils.printConversation(mActivity.getActivityContext(), getMessageCursor(),
                mAddressCache, mConversation.getBaseUri(mBaseUri), true /* useJavascript */);
    }

    @Override
    protected void handleReply() {
        final MessageHeaderItem item = getLastMessageHeaderItem();
        if (item != null) {
            final ConversationMessage msg = item.getMessage();
            if (msg != null) {
                ComposeActivity.reply(getActivity(), mAccount, msg);
            }
        }
    }

    @Override
    protected void handleReplyAll() {
        final MessageHeaderItem item = getLastMessageHeaderItem();
        if (item != null) {
            final ConversationMessage msg = item.getMessage();
            if (msg != null) {
                ComposeActivity.replyAll(getActivity(), mAccount, msg);
            }
        }
    }
}
