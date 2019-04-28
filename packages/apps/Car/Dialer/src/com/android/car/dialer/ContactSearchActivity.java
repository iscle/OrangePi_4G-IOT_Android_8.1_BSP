/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.dialer;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.android.car.view.CarLayoutManager;

/**
 * An activity that manages contact searching. This activity will display the result of a search
 * as well as show the details of a contact when that contact is clicked.
 */
public class ContactSearchActivity extends Activity {
    private static final String CONTENT_FRAGMENT_TAG = "CONTENT_FRAGMENT_TAG";
    private static final int ANIMATION_DURATION_MS = 100;

    /**
     * A delay before actually starting a contact search. This ensures that there are not too many
     * queries happening when the user is still typing.
     */
    private static final int CONTACT_SEARCH_DELAY = 400;

    private final Handler mHandler = new Handler();
    private Runnable mCurrentSearch;

    private View mSearchContainer;
    private EditText mSearchField;

    private float mContainerElevation;
    private ValueAnimator mRemoveElevationAnimator;

    /**
     * Whether or not it is safe to make transactions on the {@link android.app.FragmentManager}.
     * This variable prevents a possible exception when calling commit() on the FragmentManager.
     *
     * <p>The default value is {@code true} because it is only after
     * {@link #onSaveInstanceState(Bundle)} that fragment commits are not allowed.
     */
    private boolean mAllowFragmentCommits = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contact_search_activity);

        mSearchContainer = findViewById(R.id.search_container);
        mSearchField = findViewById(R.id.search_field);

        mSearchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (!(getCurrentFragment() instanceof ContactResultsFragment)) {
                    showContactResultList(s.toString());
                    return;
                }

                // Cancel any pending searches.
                if (mCurrentSearch != null) {
                    mHandler.removeCallbacks(mCurrentSearch);
                }

                // Queue up a new search. This will be cancelled if the user types within the
                // time frame specified by CONTACT_SEARCH_DELAY.
                mCurrentSearch = new SearchRunnable(s.toString());
                mHandler.postDelayed(mCurrentSearch, CONTACT_SEARCH_DELAY);
            }
        });

        mContainerElevation = getResources()
                .getDimension(R.dimen.search_container_elevation);

        mRemoveElevationAnimator = ValueAnimator.ofFloat(mContainerElevation, 0.f);
        mRemoveElevationAnimator
                .setDuration(ANIMATION_DURATION_MS)
                .addUpdateListener(animation -> mSearchContainer.setElevation(
                        (float) animation.getAnimatedValue()));

        findViewById(R.id.back).setOnClickListener(v -> finish());
        findViewById(R.id.clear).setOnClickListener(v -> {
            mSearchField.getText().clear();

            Fragment currentFragment = getCurrentFragment();
            if (currentFragment instanceof ContactResultsFragment) {
                ((ContactResultsFragment) currentFragment).clearResults();
            }
        });

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    /**
     * Inspects the Action within the given intent and loads up the appropriate fragment based on
     * this.
     */
    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            showContactResultList(null /* query */);
            return;
        }

        switch (intent.getAction()) {
            case Intent.ACTION_SEARCH:
                showContactResultList(intent.getStringExtra(SearchManager.QUERY));
                break;

            case TelecomIntents.ACTION_SHOW_CONTACT_DETAILS:
                // Hide the keyboard so there's room on the screen for the detail view.
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mSearchField.getWindowToken(), 0);
                Uri contactUri = Uri.parse(intent.getStringExtra(
                        TelecomIntents.CONTACT_LOOKUP_URI_EXTRA));
                setContentFragment(ContactDetailsFragment.newInstance(contactUri,
                        new ContactScrollListener()));
                break;

            default:
                showContactResultList(null /* query */);
        }
    }

    /**
     * Displays the fragment that will show the results of a search. The given query is used as
     * the initial search to populate the list.
     */
    private void showContactResultList(@Nullable String query) {
        // Check that the result list is not already being displayed. If it is, then simply set the
        // search query.
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof ContactResultsFragment) {
            ((ContactResultsFragment) currentFragment).setSearchQuery(query);
            return;
        }

        setContentFragment(ContactResultsFragment.newInstance(new ContactScrollListener(), query));
    }

    /**
     * Sets the fragment that will be shown as the main content of this Activity.
     */
    private void setContentFragment(Fragment fragment) {
        if (!mAllowFragmentCommits) {
            return;
        }

        // The search panel might have elevation added to it, so remove it when the fragment
        // changes since any lists in it will be reset to the top.
        resetSearchPanelElevation();

        getFragmentManager().beginTransaction()
                .setCustomAnimations(R.animator.fade_in, R.animator.fade_out)
                .replace(R.id.content_fragment_container, fragment, CONTENT_FRAGMENT_TAG)
                .commitNow();
    }

    /**
     * Returns the fragment that is currently being displayed as the content view.
     */
    @Nullable
    private Fragment getCurrentFragment() {
        return getFragmentManager().findFragmentByTag(CONTENT_FRAGMENT_TAG);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Fragment commits are not allowed once the Activity's state has been saved. Once
        // onStart() has been called, the FragmentManager should now allow commits.
        mAllowFragmentCommits = true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // A transaction can only be committed with this method prior to its containing activity
        // saving its state.
        mAllowFragmentCommits = false;
        super.onSaveInstanceState(outState);
    }

    /**
     * Checks if {@link #mSearchContainer} has an elevation set on it and if it does, animates the
     * removal of this elevation.
     */
    private void resetSearchPanelElevation() {
        if (mSearchContainer.getElevation() != 0.f) {
            mRemoveElevationAnimator.start();
        }
    }

    /**
     * A {@link Runnable} that will execute a contact search with the given {@link #mSearchQuery}.
     */
    private class SearchRunnable implements Runnable {
        private final String mSearchQuery;

        public SearchRunnable(String searchQuery) {
            mSearchQuery = searchQuery;
        }

        @Override
        public void run() {
            Fragment currentFragment = getCurrentFragment();
            if (currentFragment instanceof ContactResultsFragment) {
                ((ContactResultsFragment) currentFragment).setSearchQuery(mSearchQuery);
            }
        }
    }

    /**
     * Listener for scrolls in a fragment that has a list. It will will add elevation on the
     * container holding the search field. This elevation will give the illusion of the list
     * scrolling under that container.
     */
    public class ContactScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            // Assuming CarLayoutManager is the layout manager as all car applications should be
            // using a PagedListView.
            CarLayoutManager layoutManager = (CarLayoutManager) recyclerView.getLayoutManager();

            if (layoutManager.isAtTop()) {
                resetSearchPanelElevation();
            } else {
                // No animation needed when adding the elevation because the scroll masks the adding
                // of the elevation.
                mSearchContainer.setElevation(mContainerElevation);
            }
        }
    }
}
