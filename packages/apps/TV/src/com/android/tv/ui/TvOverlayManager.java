/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.OnBackStackChangedListener;
import android.content.Intent;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;

import com.android.tv.ApplicationSingletons;
import com.android.tv.ChannelTuner;
import com.android.tv.MainActivity;
import com.android.tv.MainActivity.KeyHandlerResultType;
import com.android.tv.R;
import com.android.tv.TimeShiftManager;
import com.android.tv.TvApplication;
import com.android.tv.TvOptionsManager;
import com.android.tv.analytics.Tracker;
import com.android.tv.common.WeakHandler;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.common.ui.setup.OnActionClickListener;
import com.android.tv.common.ui.setup.SetupFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dialog.DvrHistoryDialogFragment;
import com.android.tv.dialog.FullscreenDialogFragment;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dialog.RecentlyWatchedDialogFragment;
import com.android.tv.dialog.SafeDismissDialogFragment;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.ui.browse.DvrBrowseActivity;
import com.android.tv.guide.ProgramGuide;
import com.android.tv.license.LicenseDialogFragment;
import com.android.tv.menu.Menu;
import com.android.tv.menu.Menu.MenuShowReason;
import com.android.tv.menu.MenuRowFactory;
import com.android.tv.menu.MenuView;
import com.android.tv.onboarding.NewSourcesFragment;
import com.android.tv.onboarding.SetupSourcesFragment;
import com.android.tv.search.ProgramGuideSearchFragment;
import com.android.tv.ui.TvTransitionManager.SceneType;
import com.android.tv.ui.sidepanel.SideFragmentManager;
import com.android.tv.ui.sidepanel.parentalcontrols.RatingsFragment;
import com.android.tv.util.TvInputManagerHelper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * A class responsible for the life cycle and event handling of the pop-ups over TV view.
 */
@UiThread
public class TvOverlayManager {
    private static final String TAG = "TvOverlayManager";
    private static final boolean DEBUG = false;
    private static final String INTRO_TRACKER_LABEL = "Intro dialog";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {FLAG_HIDE_OVERLAYS_DEFAULT, FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION,
                    FLAG_HIDE_OVERLAYS_KEEP_SCENE, FLAG_HIDE_OVERLAYS_KEEP_DIALOG,
                    FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS, FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY,
                    FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE, FLAG_HIDE_OVERLAYS_KEEP_MENU,
                    FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT})
    private @interface HideOverlayFlag {}
    // FLAG_HIDE_OVERLAYs must be bitwise exclusive.
    public static final int FLAG_HIDE_OVERLAYS_DEFAULT =                 0b000000000;
    public static final int FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION =       0b000000010;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SCENE =              0b000000100;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_DIALOG =             0b000001000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS =        0b000010000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY = 0b000100000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE =      0b001000000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_MENU =               0b010000000;
    public static final int FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT =           0b100000000;

    private static final int MSG_OVERLAY_CLOSED = 1000;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true,
            value = {OVERLAY_TYPE_NONE, OVERLAY_TYPE_MENU, OVERLAY_TYPE_SIDE_FRAGMENT,
                    OVERLAY_TYPE_DIALOG, OVERLAY_TYPE_GUIDE, OVERLAY_TYPE_SCENE_CHANNEL_BANNER,
                    OVERLAY_TYPE_SCENE_INPUT_BANNER, OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH,
                    OVERLAY_TYPE_SCENE_SELECT_INPUT, OVERLAY_TYPE_FRAGMENT})
    private @interface TvOverlayType {}
    // OVERLAY_TYPEs must be bitwise exclusive.
    /**
     * The overlay type which indicates that there are no overlays.
     */
    private static final int OVERLAY_TYPE_NONE =                        0b000000000;
    /**
     * The overlay type for menu.
     */
    private static final int OVERLAY_TYPE_MENU =                        0b000000001;
    /**
     * The overlay type for the side fragment.
     */
    private static final int OVERLAY_TYPE_SIDE_FRAGMENT =               0b000000010;
    /**
     * The overlay type for dialog fragment.
     */
    private static final int OVERLAY_TYPE_DIALOG =                      0b000000100;
    /**
     * The overlay type for program guide.
     */
    private static final int OVERLAY_TYPE_GUIDE =                       0b000001000;
    /**
     * The overlay type for channel banner.
     */
    private static final int OVERLAY_TYPE_SCENE_CHANNEL_BANNER =        0b000010000;
    /**
     * The overlay type for input banner.
     */
    private static final int OVERLAY_TYPE_SCENE_INPUT_BANNER =          0b000100000;
    /**
     * The overlay type for keypad channel switch view.
     */
    private static final int OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH = 0b001000000;
    /**
     * The overlay type for select input view.
     */
    private static final int OVERLAY_TYPE_SCENE_SELECT_INPUT =          0b010000000;
    /**
     * The overlay type for fragment other than the side fragment and dialog fragment.
     */
    private static final int OVERLAY_TYPE_FRAGMENT =                    0b100000000;
    // Used for the padded print of the overlay type.
    private static final int NUM_OVERLAY_TYPES = 9;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW, UPDATE_CHANNEL_BANNER_REASON_TUNE,
            UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST, UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO,
            UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK,
            UPDATE_CHANNEL_BANNER_REASON_UPDATE_STREAM_INFO})
    private @interface ChannelBannerUpdateReason {}
    /**
     * Updates channel banner because the channel banner is forced to show.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW = 1;
    /**
     * Updates channel banner because of tuning.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_TUNE = 2;
    /**
     * Updates channel banner because of fast tuning.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST = 3;
    /**
     * Updates channel banner because of info updating.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_UPDATE_INFO = 4;
    /**
     * Updates channel banner because the current watched channel is locked or unlocked.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK = 5;
    /**
     * Updates channel banner because of stream info updating.
     */
    public static final int UPDATE_CHANNEL_BANNER_REASON_UPDATE_STREAM_INFO = 6;

    private static final String FRAGMENT_TAG_SETUP_SOURCES = "tag_setup_sources";
    private static final String FRAGMENT_TAG_NEW_SOURCES = "tag_new_sources";

    private static final Set<String> AVAILABLE_DIALOG_TAGS = new HashSet<>();
    static {
        AVAILABLE_DIALOG_TAGS.add(RecentlyWatchedDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(DvrHistoryDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(PinDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(FullscreenDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(LicenseDialogFragment.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(RatingsFragment.AttributionItem.DIALOG_TAG);
        AVAILABLE_DIALOG_TAGS.add(HalfSizedDialogFragment.DIALOG_TAG);
    }

    private final MainActivity mMainActivity;
    private final ChannelTuner mChannelTuner;
    private final TvTransitionManager mTransitionManager;
    private final ChannelDataManager mChannelDataManager;
    private final TvInputManagerHelper mInputManager;
    private final Menu mMenu;
    private final TunableTvView mTvView;
    private final SideFragmentManager mSideFragmentManager;
    private final ProgramGuide mProgramGuide;
    private final ChannelBannerView mChannelBannerView;
    private final KeypadChannelSwitchView mKeypadChannelSwitchView;
    private final SelectInputView mSelectInputView;
    private final ProgramGuideSearchFragment mSearchFragment;
    private final Tracker mTracker;
    private SafeDismissDialogFragment mCurrentDialog;
    private boolean mSetupFragmentActive;
    private boolean mNewSourcesFragmentActive;
    private boolean mChannelBannerHiddenBySideFragment;
    private final Handler mHandler = new TvOverlayHandler(this);

    private @TvOverlayType int mOpenedOverlays;

    private final List<Runnable> mPendingActions = new ArrayList<>();
    private final Queue<PendingDialogAction> mPendingDialogActionQueue = new LinkedList<>();

    private OnBackStackChangedListener mOnBackStackChangedListener;

    public TvOverlayManager(MainActivity mainActivity, ChannelTuner channelTuner,
            TunableTvView tvView, TvOptionsManager optionsManager,
            KeypadChannelSwitchView keypadChannelSwitchView, ChannelBannerView channelBannerView,
            InputBannerView inputBannerView, SelectInputView selectInputView,
            ViewGroup sceneContainer, ProgramGuideSearchFragment searchFragment) {
        mMainActivity = mainActivity;
        mChannelTuner = channelTuner;
        ApplicationSingletons singletons = TvApplication.getSingletons(mainActivity);
        mChannelDataManager = singletons.getChannelDataManager();
        mInputManager = singletons.getTvInputManagerHelper();
        mTvView = tvView;
        mChannelBannerView = channelBannerView;
        mKeypadChannelSwitchView = keypadChannelSwitchView;
        mSelectInputView = selectInputView;
        mSearchFragment = searchFragment;
        mTracker = singletons.getTracker();
        mTransitionManager = new TvTransitionManager(mainActivity, sceneContainer,
                channelBannerView, inputBannerView, mKeypadChannelSwitchView, selectInputView);
        mTransitionManager.setListener(new TvTransitionManager.Listener() {
            @Override
            public void onSceneChanged(int fromScene, int toScene) {
                // Call onOverlayOpened first so that the listener can know that a new scene
                // will be opened when the onOverlayClosed is called.
                if (toScene != TvTransitionManager.SCENE_TYPE_EMPTY) {
                    onOverlayOpened(convertSceneToOverlayType(toScene));
                }
                if (fromScene != TvTransitionManager.SCENE_TYPE_EMPTY) {
                    onOverlayClosed(convertSceneToOverlayType(fromScene));
                }
            }
        });
        // Menu
        MenuView menuView = (MenuView) mainActivity.findViewById(R.id.menu);
        mMenu = new Menu(mainActivity, tvView, optionsManager, menuView,
                new MenuRowFactory(mainActivity, tvView),
                new Menu.OnMenuVisibilityChangeListener() {
                    @Override
                    public void onMenuVisibilityChange(boolean visible) {
                        if (visible) {
                            onOverlayOpened(OVERLAY_TYPE_MENU);
                        } else {
                            onOverlayClosed(OVERLAY_TYPE_MENU);
                        }
                    }
                });
        mMenu.setChannelTuner(mChannelTuner);
        // Side Fragment
        mSideFragmentManager = new SideFragmentManager(mainActivity,
                new Runnable() {
                    @Override
                    public void run() {
                        onOverlayOpened(OVERLAY_TYPE_SIDE_FRAGMENT);
                        hideOverlays(FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        showChannelBannerIfHiddenBySideFragment();
                        onOverlayClosed(OVERLAY_TYPE_SIDE_FRAGMENT);
                    }
                });
        // Program Guide
        Runnable preShowRunnable = new Runnable() {
            @Override
            public void run() {
                onOverlayOpened(OVERLAY_TYPE_GUIDE);
            }
        };
        Runnable postHideRunnable = new Runnable() {
            @Override
            public void run() {
                onOverlayClosed(OVERLAY_TYPE_GUIDE);
            }
        };
        DvrDataManager dvrDataManager = CommonFeatures.DVR.isEnabled(mainActivity)
                ? singletons.getDvrDataManager() : null;
        mProgramGuide = new ProgramGuide(mainActivity, channelTuner,
                singletons.getTvInputManagerHelper(), mChannelDataManager,
                singletons.getProgramDataManager(), dvrDataManager,
                singletons.getDvrScheduleManager(), singletons.getTracker(), preShowRunnable,
                postHideRunnable);
        mMainActivity.addOnActionClickListener(new OnActionClickListener() {
            @Override
            public boolean onActionClick(String category, int id, Bundle params) {
                switch (category) {
                    case SetupSourcesFragment.ACTION_CATEGORY:
                        switch (id) {
                            case SetupMultiPaneFragment.ACTION_DONE:
                                closeSetupFragment(true);
                                return true;
                            case SetupSourcesFragment.ACTION_ONLINE_STORE:
                                mMainActivity.showMerchantCollection();
                                return true;
                            case SetupSourcesFragment.ACTION_SETUP_INPUT: {
                                String inputId = params.getString(
                                        SetupSourcesFragment.ACTION_PARAM_KEY_INPUT_ID);
                                TvInputInfo input = mInputManager.getTvInputInfo(inputId);
                                mMainActivity.startSetupActivity(input, true);
                                return true;
                            }
                        }
                        break;
                    case NewSourcesFragment.ACTION_CATEOGRY:
                        switch (id) {
                            case NewSourcesFragment.ACTION_SETUP:
                                closeNewSourcesFragment(false);
                                showSetupFragment();
                                return true;
                            case NewSourcesFragment.ACTION_SKIP:
                                // Don't remove the fragment because new fragment will be replaced
                                // with this fragment.
                                closeNewSourcesFragment(true);
                                return true;
                        }
                        break;
                }
                return false;
            }
        });
    }

    /**
     * A method to release all the allocated resources or unregister listeners.
     * This is called from {@link MainActivity#onDestroy}.
     */
    public void release() {
        mMenu.release();
        mHandler.removeCallbacksAndMessages(null);
        if (mKeypadChannelSwitchView != null) {
            mKeypadChannelSwitchView.setChannels(null);
        }
    }

    /**
     * Returns the instance of {@link Menu}.
     */
    public Menu getMenu() {
        return mMenu;
    }

    /**
     * Returns the instance of {@link SideFragmentManager}.
     */
    public SideFragmentManager getSideFragmentManager() {
        return mSideFragmentManager;
    }

    /**
     * Returns the currently opened dialog.
     */
    public SafeDismissDialogFragment getCurrentDialog() {
        return mCurrentDialog;
    }

    /**
     * Checks whether the setup fragment is active or not.
     */
    public boolean isSetupFragmentActive() {
        // "getSetupSourcesFragment() != null" doesn't return the correct state. That's because,
        // when we call showSetupFragment(), we need to put off showing the fragment until the side
        // fragment is closed. Until then, getSetupSourcesFragment() returns null. So we need
        // to keep additional variable which indicates if showSetupFragment() is called.
        return mSetupFragmentActive;
    }

    private Fragment getSetupSourcesFragment() {
        return mMainActivity.getFragmentManager().findFragmentByTag(FRAGMENT_TAG_SETUP_SOURCES);
    }

    /**
     * Checks whether the new sources fragment is active or not.
     */
    public boolean isNewSourcesFragmentActive() {
        // See the comment in "isSetupFragmentActive".
        return mNewSourcesFragmentActive;
    }

    private Fragment getNewSourcesFragment() {
        return mMainActivity.getFragmentManager().findFragmentByTag(FRAGMENT_TAG_NEW_SOURCES);
    }

    /**
     * Returns the instance of {@link ProgramGuide}.
     */
    public ProgramGuide getProgramGuide() {
        return mProgramGuide;
    }

    /**
     * Shows the main menu.
     */
    public void showMenu(@MenuShowReason int reason) {
        if (mChannelTuner != null && mChannelTuner.areAllChannelsLoaded()) {
            mMenu.show(reason);
        }
    }

    /**
     * Shows the play controller of the menu if the playback is paused.
     */
    public boolean showMenuWithTimeShiftPauseIfNeeded() {
        if (mMainActivity.getTimeShiftManager().isPaused()) {
            showMenu(Menu.REASON_PLAY_CONTROLS_PAUSE);
            return true;
        }
        return false;
    }

    /**
     * Shows the given dialog.
     */
    public void showDialogFragment(String tag, SafeDismissDialogFragment dialog,
            boolean keepSidePanelHistory) {
        showDialogFragment(tag, dialog, keepSidePanelHistory, false);
    }

    public void showDialogFragment(String tag, SafeDismissDialogFragment dialog,
            boolean keepSidePanelHistory, boolean keepProgramGuide) {
        int flags = FLAG_HIDE_OVERLAYS_KEEP_DIALOG;
        if (keepSidePanelHistory) {
            flags |= FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY;
        }
        if (keepProgramGuide) {
            flags |= FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE;
        }
        hideOverlays(flags);
        // A tag for dialog must be added to AVAILABLE_DIALOG_TAGS to make it launchable from TV.
        if (!AVAILABLE_DIALOG_TAGS.contains(tag)) {
            return;
        }

        // Do not open two dialogs at the same time.
        if (mCurrentDialog != null) {
            mPendingDialogActionQueue.offer(new PendingDialogAction(tag, dialog,
                    keepSidePanelHistory, keepProgramGuide));
            return;
        }

        mCurrentDialog = dialog;
        dialog.show(mMainActivity.getFragmentManager(), tag);

        // Calling this from SafeDismissDialogFragment.onCreated() might be late
        // because it takes time for onCreated to be called
        // and next key events can be handled by MainActivity, not Dialog.
        onOverlayOpened(OVERLAY_TYPE_DIALOG);
    }

    /**
     * Should be called by {@link MainActivity} when the currently browsable channels are updated.
     */
    public void onBrowsableChannelsUpdated() {
        mKeypadChannelSwitchView.setChannels(mChannelTuner.getBrowsableChannelList());
    }

    private void runAfterSideFragmentsAreClosed(final Runnable runnable) {
        if (mSideFragmentManager.isSidePanelVisible()) {
            // When the side panel is closing, it closes all the fragments, so the new fragment
            // should be opened after the side fragment becomes invisible.
            final FragmentManager manager = mMainActivity.getFragmentManager();
            mOnBackStackChangedListener = new OnBackStackChangedListener() {
                @Override
                public void onBackStackChanged() {
                    if (manager.getBackStackEntryCount() == 0) {
                        manager.removeOnBackStackChangedListener(this);
                        mOnBackStackChangedListener = null;
                        runnable.run();
                    }
                }
            };
            manager.addOnBackStackChangedListener(mOnBackStackChangedListener);
        } else {
            runnable.run();
        }
    }

    private void showFragment(final Fragment fragment, final String tag) {
        hideOverlays(FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT);
        onOverlayOpened(OVERLAY_TYPE_FRAGMENT);
        runAfterSideFragmentsAreClosed(new Runnable() {
            @Override
            public void run() {
                if (DEBUG) Log.d(TAG, "showFragment(" + fragment + ")");
                mMainActivity.getFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, fragment, tag).commit();
            }
        });
    }

    private void closeFragment(String fragmentTagToRemove) {
        if (DEBUG) Log.d(TAG, "closeFragment(" + fragmentTagToRemove + ")");
        onOverlayClosed(OVERLAY_TYPE_FRAGMENT);
        if (fragmentTagToRemove != null) {
            Fragment fragmentToRemove = mMainActivity.getFragmentManager()
                    .findFragmentByTag(fragmentTagToRemove);
            if (fragmentToRemove == null) {
                // If the fragment has not been added to the fragment manager yet, just remove the
                // listener not to add the fragment. This is needed because the side fragment is
                // closed asynchronously.
                mMainActivity.getFragmentManager().removeOnBackStackChangedListener(
                        mOnBackStackChangedListener);
                mOnBackStackChangedListener = null;
            } else {
                mMainActivity.getFragmentManager().beginTransaction().remove(fragmentToRemove)
                        .commit();
            }
        }
    }

    /**
     * Shows setup dialog.
     */
    public void showSetupFragment() {
        if (DEBUG) Log.d(TAG, "showSetupFragment");
        mSetupFragmentActive = true;
        SetupSourcesFragment setupFragment = new SetupSourcesFragment();
        setupFragment.enableFragmentTransition(SetupFragment.FRAGMENT_ENTER_TRANSITION
                | SetupFragment.FRAGMENT_EXIT_TRANSITION | SetupFragment.FRAGMENT_RETURN_TRANSITION
                | SetupFragment.FRAGMENT_REENTER_TRANSITION);
        setupFragment.setFragmentTransition(SetupFragment.FRAGMENT_EXIT_TRANSITION, Gravity.END);
        showFragment(setupFragment, FRAGMENT_TAG_SETUP_SOURCES);
    }

    // Set removeFragment to false only when the new fragment is going to be shown.
    private void closeSetupFragment(boolean removeFragment) {
        if (DEBUG) Log.d(TAG, "closeSetupFragment");
        if (!mSetupFragmentActive) {
            return;
        }
        mSetupFragmentActive = false;
        closeFragment(removeFragment ? FRAGMENT_TAG_SETUP_SOURCES : null);
        if (mChannelDataManager.getChannelCount() == 0) {
            if (DEBUG) Log.d(TAG, "Finishing MainActivity because there are no channels.");
            mMainActivity.finish();
        }
    }

    /**
     * Shows new sources dialog.
     */
    public void showNewSourcesFragment() {
        if (DEBUG) Log.d(TAG, "showNewSourcesFragment");
        mNewSourcesFragmentActive = true;
        showFragment(new NewSourcesFragment(), FRAGMENT_TAG_NEW_SOURCES);
    }

    // Set removeFragment to false only when the new fragment is going to be shown.
    private void closeNewSourcesFragment(boolean removeFragment) {
        if (DEBUG) Log.d(TAG, "closeNewSourcesFragment");
        if (!mNewSourcesFragmentActive) {
            return;
        }
        mNewSourcesFragmentActive = false;
        closeFragment(removeFragment ? FRAGMENT_TAG_NEW_SOURCES : null);
    }

    /**
     * Shows DVR manager.
     */
    public void showDvrManager() {
        Intent intent = new Intent(mMainActivity, DvrBrowseActivity.class);
        mMainActivity.startActivity(intent);
    }

    /**
     * Shows intro dialog.
     */
    public void showIntroDialog() {
        if (DEBUG) Log.d(TAG,"showIntroDialog");
        showDialogFragment(FullscreenDialogFragment.DIALOG_TAG,
                FullscreenDialogFragment.newInstance(R.layout.intro_dialog, INTRO_TRACKER_LABEL),
                false);
    }

    /**
     * Shows recently watched dialog.
     */
    public void showRecentlyWatchedDialog() {
        showDialogFragment(RecentlyWatchedDialogFragment.DIALOG_TAG,
                new RecentlyWatchedDialogFragment(), false);
    }

    /**
     * Shows DVR history dialog.
     */
    public void showDvrHistoryDialog() {
        showDialogFragment(DvrHistoryDialogFragment.DIALOG_TAG,
                new DvrHistoryDialogFragment(), false);
    }

    /**
     * Shows banner view.
     */
    public void showBanner() {
        mTransitionManager.goToChannelBannerScene();
    }

    /**
     * Pops up the KeypadChannelSwitchView with the given key input event.
     *
     * @param keyCode A key code of the key event.
     */
    public void showKeypadChannelSwitch(int keyCode) {
        if (mChannelTuner.areAllChannelsLoaded()) {
            hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SCENE
                    | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                    | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG
                    | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT);
            mTransitionManager.goToKeypadChannelSwitchScene();
            mKeypadChannelSwitchView.onNumberKeyUp(keyCode - KeyEvent.KEYCODE_0);
        }
    }

    /**
     * Shows select input view.
     */
    public void showSelectInputView() {
        hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SCENE);
        mTransitionManager.goToSelectInputScene();
    }

    /**
     * Initializes animators if animators are not initialized yet.
     */
    public void initAnimatorIfNeeded() {
        mTransitionManager.initIfNeeded();
    }

    /**
     * It is called when a SafeDismissDialogFragment is destroyed.
     */
    public void onDialogDestroyed() {
        mCurrentDialog = null;
        PendingDialogAction action = mPendingDialogActionQueue.poll();
        if (action == null) {
            onOverlayClosed(OVERLAY_TYPE_DIALOG);
        } else {
            action.run();
        }
    }

    /**
     * Shows the program guide.
     */
    public void showProgramGuide() {
        mProgramGuide.show(new Runnable() {
            @Override
            public void run() {
                hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE);
            }
        });
    }

    /**
     * Shows/hides the program guide according to it's hidden or shown now.
     *
     * @return {@code true} if program guide is going to be shown, otherwise {@code false}.
     */
    public boolean toggleProgramGuide() {
        if (mProgramGuide.isActive()) {
            mProgramGuide.onBackPressed();
            return false;
        } else {
            showProgramGuide();
            return true;
        }
    }

    /**
     * Sets blocking content rating of the currently playing TV channel.
     */
    public void setBlockingContentRating(TvContentRating rating) {
        if (!mMainActivity.isChannelChangeKeyDownReceived()) {
            mChannelBannerView.setBlockingContentRating(rating);
            updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK);
        }
    }

    /**
     * Hides all the opened overlays according to the flags.
     */
    // TODO: Add test for this method.
    public void hideOverlays(@HideOverlayFlag int flags) {
        if (mMainActivity.needToKeepSetupScreenWhenHidingOverlay()) {
            flags |= FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT;
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_DIALOG) != 0) {
            // Keeps the dialog.
        } else {
            if (mCurrentDialog != null) {
                if (mCurrentDialog instanceof PinDialogFragment) {
                    // We don't want any OnPinCheckedListener is triggered to prevent any possible
                    // side effects. Dismisses the dialog silently.
                    ((PinDialogFragment) mCurrentDialog).dismissSilently();
                } else {
                    mCurrentDialog.dismiss();
                }
            }
            mPendingDialogActionQueue.clear();
            mCurrentDialog = null;
        }
        boolean withAnimation = (flags & FLAG_HIDE_OVERLAYS_WITHOUT_ANIMATION) == 0;

        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT) == 0) {
            Fragment setupSourcesFragment = getSetupSourcesFragment();
            Fragment newSourcesFragment = getNewSourcesFragment();
            if (mSetupFragmentActive) {
                if (!withAnimation && setupSourcesFragment != null) {
                    setupSourcesFragment.setReturnTransition(null);
                    setupSourcesFragment.setExitTransition(null);
                }
                closeSetupFragment(true);
            }
            if (mNewSourcesFragmentActive) {
                if (!withAnimation && newSourcesFragment != null) {
                    newSourcesFragment.setReturnTransition(null);
                    newSourcesFragment.setExitTransition(null);
                }
                closeNewSourcesFragment(true);
            }
        }

        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_MENU) != 0) {
            // Keeps the menu.
        } else {
            mMenu.hide(withAnimation);
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SCENE) != 0) {
            // Keeps the current scene.
        } else {
            mTransitionManager.goToEmptyScene(withAnimation);
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS) != 0) {
            // Keeps side panels.
        } else if (mSideFragmentManager.isActive()) {
            if ((flags & FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANEL_HISTORY) != 0) {
                mSideFragmentManager.hideSidePanel(withAnimation);
            } else {
                mSideFragmentManager.hideAll(withAnimation);
            }
        }
        if ((flags & FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE) != 0) {
            // Keep the program guide.
        } else {
            mProgramGuide.hide();
        }
    }

    /**
     * Returns true, if a main view needs to hide informational text. Specifically, when overlay
     * UIs except banner is shown, the informational text needs to be hidden for clean UI.
     */
    public boolean needHideTextOnMainView() {
        return mSideFragmentManager.isActive()
                || getMenu().isActive()
                || mTransitionManager.isKeypadChannelSwitchActive()
                || mTransitionManager.isSelectInputActive()
                || mSetupFragmentActive
                || mNewSourcesFragmentActive;
    }

    /**
     * Updates and shows channel banner if it's needed.
     */
    public void updateChannelBannerAndShowIfNeeded(@ChannelBannerUpdateReason int reason) {
        if(DEBUG) Log.d(TAG, "updateChannelBannerAndShowIfNeeded(reason=" + reason + ")");
        if (mMainActivity.isChannelChangeKeyDownReceived()
                && reason != UPDATE_CHANNEL_BANNER_REASON_TUNE
                && reason != UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST) {
            // Tuning is still ongoing, no need to update banner for other reasons
            return;
        }
        if (!mChannelTuner.isCurrentChannelPassthrough()) {
            int lockType = ChannelBannerView.LOCK_NONE;
            if (reason == UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST) {
                if (mMainActivity.getParentalControlSettings().isParentalControlsEnabled()
                        && mMainActivity.getCurrentChannel().isLocked()) {
                    lockType = ChannelBannerView.LOCK_CHANNEL_INFO;
                } else {
                    // Do not show detailed program information while fast-tuning.
                    lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
                }
            } else if (reason == UPDATE_CHANNEL_BANNER_REASON_TUNE) {
                if (mMainActivity.getParentalControlSettings().isParentalControlsEnabled()) {
                    if (mMainActivity.getCurrentChannel().isLocked()) {
                        lockType = ChannelBannerView.LOCK_CHANNEL_INFO;
                    } else {
                        // If parental control is turned on,
                        // assumes that program is locked by default and waits for onContentAllowed.
                        lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
                    }
                }
            } else if (mTvView.isScreenBlocked()) {
                lockType = ChannelBannerView.LOCK_CHANNEL_INFO;
            } else if (mTvView.isContentBlocked() || (mMainActivity.getParentalControlSettings()
                    .isParentalControlsEnabled() && !mTvView.isVideoOrAudioAvailable())) {
                // If the parental control is enabled, do not show the program detail until the
                // video becomes available.
                lockType = ChannelBannerView.LOCK_PROGRAM_DETAIL;
            }
            // If lock type is not changed, we don't need to update channel banner by parental
            // control.
            int previousLockType = mChannelBannerView.setLockType(lockType);
            if (previousLockType == lockType
                    && reason == UPDATE_CHANNEL_BANNER_REASON_LOCK_OR_UNLOCK) {
                return;
            } else if (reason == UPDATE_CHANNEL_BANNER_REASON_UPDATE_STREAM_INFO) {
                mChannelBannerView.updateStreamInfo(mTvView);
                // If parental control is enabled, we shows program description when the video is
                // available, instead of tuning. Therefore we need to check it here if the program
                // description is previously hidden by parental control.
                if (previousLockType == ChannelBannerView.LOCK_PROGRAM_DETAIL &&
                        lockType != ChannelBannerView.LOCK_PROGRAM_DETAIL) {
                    mChannelBannerView.updateViews(false);
                }
            } else {
                mChannelBannerView.updateViews(reason == UPDATE_CHANNEL_BANNER_REASON_TUNE
                        || reason == UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST);
            }
        }
        boolean needToShowBanner = (reason == UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW
                || reason == UPDATE_CHANNEL_BANNER_REASON_TUNE
                || reason == UPDATE_CHANNEL_BANNER_REASON_TUNE_FAST);
        if (needToShowBanner && !mMainActivity.willShowOverlayUiWhenResume()
                && getCurrentDialog() == null
                && !isSetupFragmentActive()
                && !isNewSourcesFragmentActive()) {
            if (mChannelTuner.getCurrentChannel() == null) {
                mChannelBannerHiddenBySideFragment = false;
            } else if (getSideFragmentManager().isActive()) {
                mChannelBannerHiddenBySideFragment = true;
            } else {
                mChannelBannerHiddenBySideFragment = false;
                showBanner();
            }
        }
    }

    @TvOverlayType private int convertSceneToOverlayType(@SceneType int sceneType) {
        switch (sceneType) {
            case TvTransitionManager.SCENE_TYPE_CHANNEL_BANNER:
                return OVERLAY_TYPE_SCENE_CHANNEL_BANNER;
            case TvTransitionManager.SCENE_TYPE_INPUT_BANNER:
                return OVERLAY_TYPE_SCENE_INPUT_BANNER;
            case TvTransitionManager.SCENE_TYPE_KEYPAD_CHANNEL_SWITCH:
                return OVERLAY_TYPE_SCENE_KEYPAD_CHANNEL_SWITCH;
            case TvTransitionManager.SCENE_TYPE_SELECT_INPUT:
                return OVERLAY_TYPE_SCENE_SELECT_INPUT;
            case TvTransitionManager.SCENE_TYPE_EMPTY:
            default:
                return OVERLAY_TYPE_NONE;
        }
    }

    private void onOverlayOpened(@TvOverlayType int overlayType) {
        if (DEBUG) Log.d(TAG, "Overlay opened:  " + toBinaryString(overlayType));
        mOpenedOverlays |= overlayType;
        if (DEBUG) Log.d(TAG, "Opened overlays: " + toBinaryString(mOpenedOverlays));
        mHandler.removeMessages(MSG_OVERLAY_CLOSED);
        mMainActivity.updateKeyInputFocus();
    }

    private void onOverlayClosed(@TvOverlayType int overlayType) {
        if (DEBUG) Log.d(TAG, "Overlay closed:  " + toBinaryString(overlayType));
        mOpenedOverlays &= ~overlayType;
        if (DEBUG) Log.d(TAG, "Opened overlays: " + toBinaryString(mOpenedOverlays));
        mHandler.removeMessages(MSG_OVERLAY_CLOSED);
        mMainActivity.updateKeyInputFocus();
        // Show the main menu again if there are no pop-ups or banners only.
        // The main menu should not be shown when the activity is in paused state.
        boolean menuAboutToShow = false;
        if (canExecuteCloseAction()) {
            menuAboutToShow = mMainActivity.getTimeShiftManager().isPaused();
            mHandler.sendEmptyMessage(MSG_OVERLAY_CLOSED);
        }
        // Don't set screen name to main if the overlay closing is a banner
        // or if a non banner overlay is still open
        // or if we just opened the menu
        if (overlayType != OVERLAY_TYPE_SCENE_CHANNEL_BANNER
                && overlayType != OVERLAY_TYPE_SCENE_INPUT_BANNER
                && isOnlyBannerOrNoneOpened()
                && !menuAboutToShow) {
            mTracker.sendScreenView(MainActivity.SCREEN_NAME);
        }
    }

    /**
     * Shows the channel banner if it was hidden from the side fragment.
     *
     * <p>When the side fragment is visible, showing the channel banner should be put off until the
     * side fragment is closed even though the channel changes.
     */
    private void showChannelBannerIfHiddenBySideFragment() {
        if (mChannelBannerHiddenBySideFragment) {
            updateChannelBannerAndShowIfNeeded(UPDATE_CHANNEL_BANNER_REASON_FORCE_SHOW);
        }
    }

    private String toBinaryString(int value) {
        return String.format("0b%" + NUM_OVERLAY_TYPES + "s", Integer.toBinaryString(value))
                .replace(' ', '0');
    }

    private boolean canExecuteCloseAction() {
        return mMainActivity.isActivityResumed() && isOnlyBannerOrNoneOpened();
    }

    private boolean isOnlyBannerOrNoneOpened() {
        return (mOpenedOverlays & ~OVERLAY_TYPE_SCENE_CHANNEL_BANNER
                & ~OVERLAY_TYPE_SCENE_INPUT_BANNER) == 0;
    }

    /**
     * Runs a given {@code action} after all the overlays are closed.
     */
    public void runAfterOverlaysAreClosed(Runnable action) {
        if (canExecuteCloseAction()) {
            action.run();
        } else {
            mPendingActions.add(action);
        }
    }

    /**
     * Handles the onUserInteraction event of the {@link MainActivity}.
     */
    public void onUserInteraction() {
        if (mSideFragmentManager.isActive()) {
            mSideFragmentManager.scheduleHideAll();
        } else if (mMenu.isActive()) {
            mMenu.scheduleHide();
        } else if (mProgramGuide.isActive()) {
            mProgramGuide.scheduleHide();
        }
    }

    /**
     * Handles the onKeyDown event of the {@link MainActivity}.
     */
    @KeyHandlerResultType public int onKeyDown(int keyCode, KeyEvent event) {
        if (mCurrentDialog != null) {
            // Consumes the keys while a Dialog is creating.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        // Handle media key here because it is related to the menu.
        if (isMediaStartKey(keyCode)) {
            // Consumes the keys which may trigger system's default music player.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mMenu.isActive() || mSideFragmentManager.isActive() || mProgramGuide.isActive()
                || mSetupFragmentActive || mNewSourcesFragmentActive) {
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mTransitionManager.isKeypadChannelSwitchActive()) {
            return mKeypadChannelSwitchView.onKeyDown(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        if (mTransitionManager.isSelectInputActive()) {
            return mSelectInputView.onKeyDown(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        return MainActivity.KEY_EVENT_HANDLER_RESULT_PASSTHROUGH;
    }

    /**
     * Handles the onKeyUp event of the {@link MainActivity}.
     */
    @KeyHandlerResultType public int onKeyUp(int keyCode, KeyEvent event) {
        // Handle media key here because it is related to the menu.
        if (isMediaStartKey(keyCode)) {
            // The media key should not be passed up to the system in any cases.
            if (mCurrentDialog != null || mProgramGuide.isActive()
                    || mSideFragmentManager.isActive()
                    || mSearchFragment.isVisible()
                    || mTransitionManager.isKeypadChannelSwitchActive()
                    || mTransitionManager.isSelectInputActive()
                    || mSetupFragmentActive
                    || mNewSourcesFragmentActive) {
                // Do not handle media key when any pop-ups which can handle keys are active.
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            TimeShiftManager timeShiftManager = mMainActivity.getTimeShiftManager();
            if (!timeShiftManager.isAvailable()) {
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            switch (keyCode) {
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    timeShiftManager.play();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PLAY);
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    timeShiftManager.pause();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    timeShiftManager.togglePlayPause();
                    showMenu(Menu.REASON_PLAY_CONTROLS_PLAY_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    timeShiftManager.rewind();
                    showMenu(Menu.REASON_PLAY_CONTROLS_REWIND);
                    break;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    timeShiftManager.fastForward();
                    showMenu(Menu.REASON_PLAY_CONTROLS_FAST_FORWARD);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                    timeShiftManager.jumpToPrevious();
                    showMenu(Menu.REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
                    timeShiftManager.jumpToNext();
                    showMenu(Menu.REASON_PLAY_CONTROLS_JUMP_TO_NEXT);
                    break;
                default:
                    // Does nothing.
                    break;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (keyCode == KeyEvent.KEYCODE_I || keyCode == KeyEvent.KEYCODE_TV_INPUT) {
            if (mTransitionManager.isSelectInputActive()) {
                mSelectInputView.onKeyUp(keyCode, event);
            } else {
                showSelectInputView();
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mCurrentDialog != null) {
            // Consumes the keys while a Dialog is showing.
            // This can be happen while a Dialog isn't created yet.
            return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
        }
        if (mProgramGuide.isActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mProgramGuide.onBackPressed();
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mSideFragmentManager.isActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || mSideFragmentManager.isHideKeyForCurrentPanel(keyCode)) {
                mSideFragmentManager.popSideFragment();
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mMenu.isActive() || mTransitionManager.isSceneActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                TimeShiftManager timeShiftManager = mMainActivity.getTimeShiftManager();
                if (timeShiftManager.isPaused()) {
                    timeShiftManager.play();
                }
                hideOverlays(TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG
                        | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            if (mMenu.isActive()) {
                if (KeypadChannelSwitchView.isChannelNumberKey(keyCode)) {
                    showKeypadChannelSwitch(keyCode);
                    return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
                }
                return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
            }
        }
        if (mTransitionManager.isKeypadChannelSwitchActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mTransitionManager.goToEmptyScene(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return mKeypadChannelSwitchView.onKeyUp(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        if (mTransitionManager.isSelectInputActive()) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mTransitionManager.goToEmptyScene(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return mSelectInputView.onKeyUp(keyCode, event) ?
                    MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED
                    : MainActivity.KEY_EVENT_HANDLER_RESULT_NOT_HANDLED;
        }
        if (mSetupFragmentActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeSetupFragment(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        if (mNewSourcesFragmentActive) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                closeNewSourcesFragment(true);
                return MainActivity.KEY_EVENT_HANDLER_RESULT_HANDLED;
            }
            return MainActivity.KEY_EVENT_HANDLER_RESULT_DISPATCH_TO_OVERLAY;
        }
        return MainActivity.KEY_EVENT_HANDLER_RESULT_PASSTHROUGH;
    }

    /**
     * Checks whether the given {@code keyCode} can start the system's music app or not.
     */
    private static boolean isMediaStartKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                return true;
        }
        return false;
    }

    private static class TvOverlayHandler extends WeakHandler<TvOverlayManager> {
        TvOverlayHandler(TvOverlayManager ref) {
            super(ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull TvOverlayManager tvOverlayManager) {
            switch (msg.what) {
                case MSG_OVERLAY_CLOSED:
                    if (!tvOverlayManager.canExecuteCloseAction()) {
                        return;
                    }
                    if (tvOverlayManager.showMenuWithTimeShiftPauseIfNeeded()) {
                        return;
                    }
                    if (!tvOverlayManager.mPendingActions.isEmpty()) {
                        Runnable action = tvOverlayManager.mPendingActions.get(0);
                        tvOverlayManager.mPendingActions.remove(action);
                        action.run();
                    }
                    break;
            }
        }
    }

    private class PendingDialogAction {
        private final String mTag;
        private final SafeDismissDialogFragment mDialog;
        private final boolean mKeepSidePanelHistory;
        private final boolean mKeepProgramGuide;

        PendingDialogAction(String tag, SafeDismissDialogFragment dialog,
                boolean keepSidePanelHistory, boolean keepProgramGuide) {
            mTag = tag;
            mDialog = dialog;
            mKeepSidePanelHistory = keepSidePanelHistory;
            mKeepProgramGuide = keepProgramGuide;
        }

        void run() {
            showDialogFragment(mTag, mDialog, mKeepSidePanelHistory, mKeepProgramGuide);
        }
    }
}
