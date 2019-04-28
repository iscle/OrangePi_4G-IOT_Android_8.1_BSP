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

package com.android.tv.search;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.SearchFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.SearchBar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.PermissionUtils;

import java.util.List;

public class ProgramGuideSearchFragment extends SearchFragment {
    private static final String TAG = "ProgramGuideSearch";
    private static final boolean DEBUG = false;
    private static final int SEARCH_RESULT_MAX = 10;

    private final Presenter mPresenter = new Presenter() {
        @Override
        public Presenter.ViewHolder onCreateViewHolder(ViewGroup viewGroup) {
            if (DEBUG) Log.d(TAG, "onCreateViewHolder");

            ImageCardView cardView = new ImageCardView(mMainActivity);
            cardView.setFocusable(true);
            cardView.setFocusableInTouchMode(true);
            cardView.setMainImageAdjustViewBounds(false);

            Resources res = mMainActivity.getResources();
            cardView.setMainImageDimensions(
                    res.getDimensionPixelSize(R.dimen.card_image_layout_width),
                    res.getDimensionPixelSize(R.dimen.card_image_layout_height));

            return new Presenter.ViewHolder(cardView);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object o) {
            ImageCardView cardView = (ImageCardView) viewHolder.view;
            LocalSearchProvider.SearchResult result = (LocalSearchProvider.SearchResult) o;
            if (DEBUG) Log.d(TAG, "onBindViewHolder result:" + result);

            cardView.setTitleText(result.title);
            if (!TextUtils.isEmpty(result.imageUri)) {
                ImageLoader.loadBitmap(mMainActivity, result.imageUri, mMainCardWidth,
                        mMainCardHeight, createImageLoaderCallback(cardView));
            } else {
                cardView.setMainImage(mMainActivity.getDrawable(R.drawable.ic_launcher));
            }
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
            // Do nothing here.
        }
    };

    private static ImageLoader.ImageLoaderCallback<ImageCardView> createImageLoaderCallback(
            ImageCardView cardView) {
        return new ImageLoader.ImageLoaderCallback<ImageCardView>(cardView) {
            @Override
            public void onBitmapLoaded(ImageCardView cardView, Bitmap bitmap) {
                cardView.setMainImage(
                        new BitmapDrawable(cardView.getContext().getResources(), bitmap));
            }
        };
    }

    private final SearchResultProvider mSearchResultProvider = new SearchResultProvider() {
        @Override
        public ObjectAdapter getResultsAdapter() {
            return mResultAdapter;
        }

        @Override
        public boolean onQueryTextChange(String query) {
            searchAndRefresh(query);
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            searchAndRefresh(query);
            return true;
        }
    };

    private final OnItemViewClickedListener mItemClickedListener = new OnItemViewClickedListener() {
        @Override
        public void onItemClicked(Presenter.ViewHolder viewHolder, Object o, RowPresenter
                .ViewHolder viewHolder1, Row row) {
            LocalSearchProvider.SearchResult result = (LocalSearchProvider.SearchResult) o;
            mMainActivity.getFragmentManager().popBackStack();
            mMainActivity.tuneToChannel(
                    mMainActivity.getChannelDataManager().getChannel(result.channelId));
        }
    };

    private final ArrayObjectAdapter mResultAdapter =
            new ArrayObjectAdapter(new ListRowPresenter());
    private MainActivity mMainActivity;
    private SearchInterface mSearch;
    private int mMainCardWidth;
    private int mMainCardHeight;
    private SearchTask mSearchTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainActivity = (MainActivity) getActivity();
        if (PermissionUtils.hasAccessAllEpg(mMainActivity)) {
            mSearch = new TvProviderSearch(mMainActivity);
        } else {
            mSearch = new DataManagerSearch(mMainActivity);
        }
        Resources res = getResources();
        mMainCardWidth = res.getDimensionPixelSize(R.dimen.card_image_layout_width);
        mMainCardHeight = res.getDimensionPixelSize(R.dimen.card_image_layout_height);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        v.setBackgroundResource(R.color.program_guide_scrim);

        setBadgeDrawable(mMainActivity.getDrawable(R.drawable.ic_launcher));
        setSearchResultProvider(mSearchResultProvider);
        setOnItemViewClickedListener(mItemClickedListener);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        SearchBar searchBar = (SearchBar) getView().findViewById(R.id.lb_search_bar);
        searchBar.setSearchQuery("");
        mResultAdapter.clear();
    }

    private void searchAndRefresh(String query) {
        // TODO: Search directly from program data manager for performance.
        // TODO: Search upcoming programs.
        if (mSearchTask != null) {
            mSearchTask.cancel(true);
        }
        mSearchTask = new SearchTask(query);
        mSearchTask.execute();
    }

    private class SearchTask extends
            AsyncTask<Void, Void, List<LocalSearchProvider.SearchResult>> {
        private final String mQuery;

        public SearchTask(String query) {
            mQuery = query;
        }

        @Override
        protected List<LocalSearchProvider.SearchResult> doInBackground(Void... params) {
            return mSearch.search(mQuery, SEARCH_RESULT_MAX,
                    TvProviderSearch.ACTION_TYPE_AMBIGUOUS);
        }

        @Override
        protected void onPostExecute(List<LocalSearchProvider.SearchResult> results) {
            super.onPostExecute(results);
            mResultAdapter.clear();

            if (DEBUG) {
                Log.d(TAG, "searchAndRefresh query=" + mQuery
                        + " results=" + ((results == null) ? 0 : results.size()));
            }

            if (results == null || results.size() == 0) {
                HeaderItem header =
                        new HeaderItem(0, mMainActivity.getString(R.string
                                .search_result_no_result));
                ArrayObjectAdapter resultsAdapter = new ArrayObjectAdapter(mPresenter);
                mResultAdapter.add(new ListRow(header, resultsAdapter));
            } else {
                HeaderItem header =
                        new HeaderItem(0, mMainActivity.getString(R.string
                                .search_result_title));
                ArrayObjectAdapter resultsAdapter = new ArrayObjectAdapter(mPresenter);
                resultsAdapter.addAll(0, results);
                mResultAdapter.add(new ListRow(header, resultsAdapter));
            }
            mSearchTask = null;
        }
    }
}
