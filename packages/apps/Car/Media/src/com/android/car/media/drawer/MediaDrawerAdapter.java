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
package com.android.car.media.drawer;

import android.content.Context;
import android.support.annotation.Nullable;
import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.app.DrawerItemViewHolder;

/**
 * Subclass of CarDrawerAdapter used by the Media app.
 * <p>
 * This adapter delegates actual fetching of items (and other operations) to a
 * {@link MediaItemsFetcher}. The current fetcher being used can be updated at runtime.
 */
class MediaDrawerAdapter extends CarDrawerAdapter {
    private final CarDrawerActivity mActivity;
    private MediaItemsFetcher mCurrentFetcher;
    private MediaFetchCallback mFetchCallback;
    private int mCurrentScrollPosition;

    /**
     * Interface for a callback object that will be notified of changes to the fetch status of
     * items in a media drawer.
     */
    interface MediaFetchCallback {
        /**
         * Called when a fetch for items starts.
         */
        void onFetchStart();

        /**
         * Called when a fetch for items ends.
         */
        void onFetchEnd();
    }

    MediaDrawerAdapter(CarDrawerActivity activity) {
        super(activity, true /* showDisabledListOnEmpty */);
        mActivity = activity;
    }

    /**
     * Sets the object to be notified of changes to the fetching of items in the media drawer.
     */
    void setFetchCallback(@Nullable MediaFetchCallback callback) {
        mFetchCallback = callback;
    }

    /**
     * Switch the {@link MediaItemsFetcher} being used to fetch items. The new fetcher is kicked-off
     * and the drawer's content's will be updated to show newly loaded items. Any old fetcher is
     * cleaned up and released.
     *
     * @param fetcher New {@link MediaItemsFetcher} to use for display Drawer items.
     */
    void setFetcher(MediaItemsFetcher fetcher) {
        if (mCurrentFetcher != null) {
            mCurrentFetcher.cleanup();
        }
        mCurrentFetcher = fetcher;
        mCurrentFetcher.start(() -> {
            if (mFetchCallback != null) {
                mFetchCallback.onFetchEnd();
            }
            notifyDataSetChanged();
        });

        if (mFetchCallback != null) {
            mFetchCallback.onFetchStart();
        }
    }

    @Override
    protected int getActualItemCount() {
        return mCurrentFetcher != null ? mCurrentFetcher.getItemCount() : 0;
    }

    @Override
    protected boolean usesSmallLayout(int position) {
        return mCurrentFetcher.usesSmallLayout(position);
    }

    @Override
    protected void populateViewHolder(DrawerItemViewHolder holder, int position) {
        if (mCurrentFetcher == null) {
            return;
        }

        mCurrentFetcher.populateViewHolder(holder, position);
        scrollToCurrent();
    }

    @Override
    public void onItemClick(int position) {
        if (mCurrentFetcher != null) {
            mCurrentFetcher.onItemClick(position);
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        if (mCurrentFetcher != null) {
            mCurrentFetcher.cleanup();
            mCurrentFetcher = null;
        }
        mFetchCallback = null;
    }

    public void scrollToCurrent() {
        if (mCurrentFetcher == null) {
            return;
        }
        int scrollPosition = mCurrentFetcher.getScrollPosition();
        if (scrollPosition != MediaItemsFetcher.DONT_SCROLL
                && mCurrentScrollPosition != scrollPosition) {
            mActivity.scrollToPosition(scrollPosition);
            mCurrentScrollPosition = scrollPosition;
        }
    }
}
