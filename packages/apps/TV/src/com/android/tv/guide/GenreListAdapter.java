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

package com.android.tv.guide;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.tv.R;
import com.android.tv.data.GenreItems;

import java.util.List;

/**
 * Adapts the genre items obtained from {@link GenreItems} to the program guide side panel.
 */
class GenreListAdapter extends RecyclerView.Adapter<GenreListAdapter.GenreRowHolder> {
    private static final String TAG = "GenreListAdapter";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final ProgramManager mProgramManager;
    private final ProgramGuide mProgramGuide;
    private String[] mGenreLabels;

    GenreListAdapter(Context context, ProgramManager programManager, ProgramGuide guide) {
        mContext = context;
        mProgramManager = programManager;
        mProgramManager.addListener(new ProgramManager.ListenerAdapter() {
            @Override
            public void onGenresUpdated() {
                mGenreLabels = GenreItems.getLabels(mContext);
                notifyDataSetChanged();
            }
        });
        mProgramGuide = guide;
    }

    @Override
    public int getItemCount() {
        List<Integer> filteredGenreIds = mProgramManager.getFilteredGenreIds();
        if (filteredGenreIds == null) {
            // Genre item would be available after program manager builds genre filter.
            return 0;
        }
        return filteredGenreIds.size();
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.program_guide_side_panel_row;
    }

    @Override
    public void onBindViewHolder(GenreRowHolder holder, int position) {
        List<Integer> filteredGenreIds = mProgramManager.getFilteredGenreIds();
        int genreId = filteredGenreIds.get(position);
        holder.onBind(genreId, mGenreLabels[genreId]);
    }

    @Override
    public GenreRowHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        itemView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                // Animation is not meaningful now, skip it.
                view.getStateListAnimator().jumpToCurrentState();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                // Do nothing
            }
        });
        return new GenreRowHolder(itemView, mProgramGuide);
    }

    static class GenreRowHolder extends RecyclerView.ViewHolder implements
            View.OnFocusChangeListener {
        private final ProgramGuide mProgramGuide;
        private int mGenreId;

        @MainThread
        GenreRowHolder(View itemView, ProgramGuide programGuide) {
            super(itemView);
            mProgramGuide = programGuide;
        }

        public void onBind(int genreId, String genreLabel) {
            mGenreId = genreId;

            TextView textView = (TextView) itemView;
            textView.setText(genreLabel);

            itemView.setOnFocusChangeListener(this);
        }

        @Override
        public void onFocusChange(View view, boolean hasFocus) {
            if (hasFocus) {
                if (DEBUG) {
                    Log.d(TAG, "onFocusChanged " + ((TextView) view).getText()
                            + "(" + mGenreId + ") hasFocus");
                }
                mProgramGuide.requestGenreChange(mGenreId);
            }
        }
    }
}
