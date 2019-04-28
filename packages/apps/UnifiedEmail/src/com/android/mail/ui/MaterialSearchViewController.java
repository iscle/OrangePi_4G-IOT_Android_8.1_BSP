/*
 * Copyright (C) 2014 Google Inc.
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
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import com.android.mail.ConversationListContext;
import com.android.mail.R;
import com.android.mail.providers.SearchRecentSuggestionsProvider;
import com.android.mail.utils.ViewUtils;

import java.util.Locale;

/**
 * Controller for interactions between ActivityController and our custom search views.
 */
public class MaterialSearchViewController implements ViewMode.ModeChangeListener,
        TwoPaneLayout.ConversationListLayoutListener {
    private static final long FADE_IN_OUT_DURATION_MS = 150;

    // The controller is not in search mode. Both search action bar and the suggestion list
    // are not visible to the user.
    public static final int SEARCH_VIEW_STATE_GONE = 0;
    // The controller is actively in search (as in the action bar is focused and the user can type
    // into the search query). Both the search action bar and the suggestion list are visible.
    public static final int SEARCH_VIEW_STATE_VISIBLE = 1;
    // The controller is in a search ViewMode but not actively searching. This is relevant when
    // we have to show the search actionbar on top while the user is not interacting with it.
    public static final int SEARCH_VIEW_STATE_ONLY_ACTIONBAR = 2;

    private static final String EXTRA_CONTROLLER_STATE = "extraSearchViewControllerViewState";

    private MailActivity mActivity;
    private ActivityController mController;

    private SearchRecentSuggestionsProvider mSuggestionsProvider;

    private MaterialSearchActionView mSearchActionView;
    private MaterialSearchSuggestionsList mSearchSuggestionList;

    private int mViewMode;
    private int mControllerState;
    private int mEndXCoordForTabletLandscape;

    private boolean mSavePending;
    private boolean mDestroyProvider;

    public MaterialSearchViewController(MailActivity activity, ActivityController controller,
            Intent intent, Bundle savedInstanceState) {
        mActivity = activity;
        mController = controller;

        final Intent voiceIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        final boolean supportVoice =
                voiceIntent.resolveActivity(mActivity.getPackageManager()) != null;

        mSuggestionsProvider = mActivity.getSuggestionsProvider();
        mSearchSuggestionList = (MaterialSearchSuggestionsList) mActivity.findViewById(
                R.id.search_overlay_view);
        mSearchSuggestionList.setController(this, mSuggestionsProvider);
        mSearchActionView = (MaterialSearchActionView) mActivity.findViewById(
                R.id.search_actionbar_view);
        mSearchActionView.setController(this, intent.getStringExtra(
                ConversationListContext.EXTRA_SEARCH_QUERY), supportVoice);

        if (savedInstanceState != null && savedInstanceState.containsKey(EXTRA_CONTROLLER_STATE)) {
            mControllerState = savedInstanceState.getInt(EXTRA_CONTROLLER_STATE);
        }

        mActivity.getViewMode().addListener(this);
    }

    /**
     * This controller should not be used after this is called.
     */
    public void onDestroy() {
        mDestroyProvider = mSavePending;
        if (!mSavePending) {
            mSuggestionsProvider.cleanup();
        }
        mActivity.getViewMode().removeListener(this);
        mActivity = null;
        mController = null;
        mSearchActionView = null;
        mSearchSuggestionList = null;
    }

    public void saveState(Bundle outState) {
        outState.putInt(EXTRA_CONTROLLER_STATE, mControllerState);
    }

    @Override
    public void onViewModeChanged(int newMode) {
        final int oldMode = mViewMode;
        mViewMode = newMode;
        // Never animate visibility changes that are caused by view state changes.
        if (mController.shouldShowSearchBarByDefault(mViewMode)) {
            showSearchActionBar(SEARCH_VIEW_STATE_ONLY_ACTIONBAR, false /* animate */);
        } else if (oldMode == ViewMode.UNKNOWN) {
            showSearchActionBar(mControllerState, false /* animate */);
        } else {
            showSearchActionBar(SEARCH_VIEW_STATE_GONE, false /* animate */);
        }
    }

    @Override
    public void onConversationListLayout(int xEnd, boolean drawerOpen) {
        // Only care about the first layout
        if (mEndXCoordForTabletLandscape != xEnd) {
            // This is called when we get into tablet landscape mode
            mEndXCoordForTabletLandscape = xEnd;
            if (ViewMode.isSearchMode(mViewMode)) {
                final int defaultVisibility = mController.shouldShowSearchBarByDefault(mViewMode) ?
                        View.VISIBLE : View.GONE;
                setViewVisibilityAndAlpha(mSearchActionView,
                        drawerOpen ? View.INVISIBLE : defaultVisibility);
            }
            adjustViewForTwoPaneLandscape();
        }
    }

    public boolean handleBackPress() {
        final boolean shouldShowSearchBar = mController.shouldShowSearchBarByDefault(mViewMode);
        if (shouldShowSearchBar && mSearchSuggestionList.isShown()) {
            showSearchActionBar(SEARCH_VIEW_STATE_ONLY_ACTIONBAR);
            return true;
        } else if (!shouldShowSearchBar && mSearchActionView.isShown()) {
            showSearchActionBar(SEARCH_VIEW_STATE_GONE);
            return true;
        }
        return false;
    }

    /**
     * Set the new visibility state of the search controller.
     * @param state the new view state, must be one of the following options:
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_ONLY_ACTIONBAR},
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_VISIBLE},
     *   {@link MaterialSearchViewController#SEARCH_VIEW_STATE_GONE},
     */
    public void showSearchActionBar(int state) {
        // By default animate the visibility changes
        showSearchActionBar(state, true /* animate */);
    }

    /**
     * @param animate if true, the search bar and suggestion list will fade in/out of view.
     */
    public void showSearchActionBar(int state, boolean animate) {
        mControllerState = state;

        // ACTIONBAR is only applicable in search mode
        final boolean onlyActionBar = state == SEARCH_VIEW_STATE_ONLY_ACTIONBAR &&
                mController.shouldShowSearchBarByDefault(mViewMode);
        final boolean isStateVisible = state == SEARCH_VIEW_STATE_VISIBLE;

        final boolean isSearchBarVisible = isStateVisible || onlyActionBar;

        final int searchBarVisibility = isSearchBarVisible ? View.VISIBLE : View.GONE;
        final int suggestionListVisibility = isStateVisible ? View.VISIBLE : View.GONE;
        if (animate) {
            fadeInOutView(mSearchActionView, searchBarVisibility);
            fadeInOutView(mSearchSuggestionList, suggestionListVisibility);
        } else {
            setViewVisibilityAndAlpha(mSearchActionView, searchBarVisibility);
            setViewVisibilityAndAlpha(mSearchSuggestionList, suggestionListVisibility);
        }
        mSearchActionView.focusSearchBar(isStateVisible);

        final boolean useDefaultColor = !isSearchBarVisible || shouldAlignWithTl();
        final int statusBarColor = useDefaultColor ? R.color.mail_activity_status_bar_color :
                R.color.search_status_bar_color;
        ViewUtils.setStatusBarColor(mActivity, statusBarColor);

        // Specific actions for each view state
        if (onlyActionBar) {
            adjustViewForTwoPaneLandscape();
        } else if (isStateVisible) {
            // Set to default layout/assets
            mSearchActionView.adjustViewForTwoPaneLandscape(false /* do not align */, 0);
        } else {
            // For non-search view mode, clear the query term for search
            if (!ViewMode.isSearchMode(mViewMode)) {
                mSearchActionView.clearSearchQuery();
            }
        }
    }

    /**
     * Helper function to fade in/out the provided view by animating alpha.
     */
    private void fadeInOutView(final View v, final int visibility) {
        if (visibility == View.VISIBLE) {
            v.setVisibility(View.VISIBLE);
            v.animate()
                    .alpha(1f)
                    .setDuration(FADE_IN_OUT_DURATION_MS)
                    .setListener(null);
        } else {
            v.animate()
                    .alpha(0f)
                    .setDuration(FADE_IN_OUT_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            v.setVisibility(visibility);
                        }
                    });
        }
    }

    /**
     * Sets the view's visibility and alpha so that we are guaranteed that alpha = 1 when the view
     * is visible, and alpha = 0 otherwise.
     */
    private void setViewVisibilityAndAlpha(View v, int visibility) {
        v.setVisibility(visibility);
        if (visibility == View.VISIBLE) {
            v.setAlpha(1f);
        } else {
            v.setAlpha(0f);
        }
    }

    private boolean shouldAlignWithTl() {
        return mController.isTwoPaneLandscape() &&
                mControllerState == SEARCH_VIEW_STATE_ONLY_ACTIONBAR &&
                ViewMode.isSearchMode(mViewMode);
    }

    private void adjustViewForTwoPaneLandscape() {
        // Try to adjust if the layout happened already
        if (mEndXCoordForTabletLandscape != 0) {
            mSearchActionView.adjustViewForTwoPaneLandscape(shouldAlignWithTl(),
                    mEndXCoordForTabletLandscape);
        }
    }

    public void onQueryTextChanged(String query) {
        mSearchSuggestionList.setQuery(query);
    }

    public void onSearchCanceled() {
        // Special case search mode
        if (ViewMode.isSearchMode(mViewMode)) {
            mActivity.setResult(Activity.RESULT_OK);
            mActivity.finish();
        } else {
            mSearchActionView.clearSearchQuery();
            showSearchActionBar(SEARCH_VIEW_STATE_GONE);
        }
    }

    public void onSearchPerformed(String query) {
        query = query.trim();
        if (!TextUtils.isEmpty(query)) {
            mSearchActionView.clearSearchQuery();
            mController.executeSearch(query);
        }
    }

    public void onVoiceSearch() {
        final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().getLanguage());

        // Some devices do not support the voice-to-speech functionality.
        try {
            mActivity.startActivityForResult(intent,
                    AbstractActivityController.VOICE_SEARCH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            final String toast =
                    mActivity.getResources().getString(R.string.voice_search_not_supported);
            Toast.makeText(mActivity, toast, Toast.LENGTH_LONG).show();
        }
    }

    public void saveRecentQuery(String query) {
        new SaveRecentQueryTask().execute(query);
    }

    // static asynctask to save the query in the background.
    private class SaveRecentQueryTask extends AsyncTask<String, Void, Void> {

        @Override
        protected void onPreExecute() {
            mSavePending = true;
        }

        @Override
        protected Void doInBackground(String... args) {
            mSuggestionsProvider.saveRecentQuery(args[0]);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mDestroyProvider) {
                mSuggestionsProvider.cleanup();
                mDestroyProvider = false;
            }
            mSavePending = false;
        }
    }
}
