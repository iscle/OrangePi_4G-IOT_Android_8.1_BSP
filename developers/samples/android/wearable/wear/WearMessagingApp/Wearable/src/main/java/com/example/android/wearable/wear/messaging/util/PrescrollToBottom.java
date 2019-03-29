/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.wearable.wear.messaging.util;

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.ViewTreeObserver;

/** Prescrolls to the last element in the adapter before draw and removes itself. */
public class PrescrollToBottom implements ViewTreeObserver.OnPreDrawListener {

    private static final String TAG = "PrescrollToBottom";

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;

    public PrescrollToBottom(RecyclerView recyclerView, RecyclerView.Adapter adapter) {
        mRecyclerView = recyclerView;
        mAdapter = adapter;
    }

    @Override
    public boolean onPreDraw() {
        boolean shouldScroll = mAdapter.getItemCount() > 2;
        if (shouldScroll) {
            Log.d(TAG, "Smooth scrolling after draw to position " + mAdapter.getItemCount());
            mRecyclerView.smoothScrollToPosition(mAdapter.getItemCount());
        }
        // always remove the listener so that we do not get multiple
        // instances of it attached when it is not needed
        mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
        return !shouldScroll;
    }
}
