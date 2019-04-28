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

import android.app.SearchManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mail.R;
import com.android.mail.providers.SearchRecentSuggestionsProvider;
import com.google.common.collect.Lists;

import java.util.List;

/**
 * Custom quantum-styled search view that overlays the main activity.
 */
public class MaterialSearchSuggestionsList extends LinearLayout
        implements AdapterView.OnItemClickListener, View.OnClickListener {
    private MaterialSearchViewController mController;
    private SearchRecentSuggestionsProvider mSuggestionsProvider;
    private List<SuggestionItem> mSuggestions = Lists.newArrayList();
    private String mQuery;

    private MaterialSearchViewListAdapter mAdapter;
    private QuerySuggestionsTask mQueryTask;

    public MaterialSearchSuggestionsList(Context context) {
        super(context);
    }

    public MaterialSearchSuggestionsList(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // PUBLIC API
    public void setController(MaterialSearchViewController controller,
            SearchRecentSuggestionsProvider suggestionsProvider) {
        mController = controller;
        mSuggestionsProvider = suggestionsProvider;
    }

    public void setQuery(String query) {
        mQuery = query;
        if (mQueryTask != null) {
            mQueryTask.cancel(true);
        }
        mQueryTask = new QuerySuggestionsTask();
        mQueryTask.execute(query);
    }

    // PRIVATE API
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final ListView listView = (ListView) findViewById(R.id.search_overlay_suggestion_list);
        listView.setOnItemClickListener(this);
        final View dummyHolder = findViewById(R.id.search_overlay_scrim);
        dummyHolder.setOnClickListener(this);

        // set up the adapter
        mAdapter = new MaterialSearchViewListAdapter(getContext(), R.layout.search_suggestion_item);
        listView.setAdapter(mAdapter);
    }

    @Override
    public void setVisibility(int visibility) {
        if (!isShown() && visibility == VISIBLE) {
            // When we go from gone to visible, re-query for suggestions in case they changed.
            setQuery(mQuery);
        }
        super.setVisibility(visibility);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        mController.onSearchPerformed(mSuggestions.get(position).suggestion);
    }

    @Override
    public void onClick(View view) {
        mController.showSearchActionBar(
                MaterialSearchViewController.SEARCH_VIEW_STATE_ONLY_ACTIONBAR);
    }

    // Background task for querying the suggestions list
    private class QuerySuggestionsTask extends AsyncTask<String, Void, List<SuggestionItem>> {
        @Override
        protected List<SuggestionItem> doInBackground(String... strings) {
            String query = strings[0];
            if (query == null) {
                query = "";
            }

            Cursor c = null;
            final List<SuggestionItem> result = Lists.newArrayList();
            try {
                c = mSuggestionsProvider.query(query);

                if (c != null && c.moveToFirst()) {
                    final int textIndex = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_QUERY);
                    final int iconIndex = c.getColumnIndex(SearchManager.SUGGEST_COLUMN_ICON_1);
                    do {
                        final String suggestion = c.getString(textIndex);
                        final Uri iconUri = Uri.parse(c.getString(iconIndex));
                        result.add(new SuggestionItem(suggestion, iconUri));
                    } while (c.moveToNext());
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            return result;
        }

        @Override
        protected void onPostExecute(List<SuggestionItem> strings) {
            if (!isCancelled()) {
                // Should not have any race conditions here since we cancel the previous asynctask
                // before starting the new one. It's unlikely that the new task finishes fast enough
                // to get to onPostExecute when this one is in addAll.
                mSuggestions.clear();
                mSuggestions.addAll(strings);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    private static class SuggestionItem {
        final String suggestion;
        final Uri icon;

        public SuggestionItem(String s, Uri i) {
            suggestion = s;
            icon = i;
        }
    }

    // Custom adapter to populate our list
    private class MaterialSearchViewListAdapter extends BaseAdapter {
        private final Context mContext;
        private final int mResId;
        private LayoutInflater mInflater;

        public MaterialSearchViewListAdapter(Context context, int resource) {
            super();
            mContext = context;
            mResId = resource;
        }

        private LayoutInflater getInflater() {
            if (mInflater == null) {
                mInflater = LayoutInflater.from(mContext);
            }
            return mInflater;
        }

        @Override
        public int getCount() {
            return mSuggestions.size();
        }

        @Override
        public Object getItem(int i) {
            return mSuggestions.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getInflater().inflate(mResId, parent, false);
            }

            final SuggestionItem item = mSuggestions.get(position);
            final TextView text =
                    (TextView) convertView.findViewById(R.id.search_overlay_item_text);
            text.setText(item.suggestion);
            text.setContentDescription(getResources().getString(R.string.search_suggestion_desc,
                    item.suggestion));
            ((ImageView) convertView.findViewById(R.id.search_overlay_item_icon))
                    .setImageURI(item.icon);

            return convertView;
        }
    }
}
