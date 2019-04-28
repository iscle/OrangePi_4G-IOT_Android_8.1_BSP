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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.support.annotation.Nullable;
import android.util.Log;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MediaItemsFetcher} implementation that fetches items from a specific {@link MediaBrowser}
 * node.
 * <p>
 * It optionally supports surfacing the Media app's queue as the last item.
 */
class MediaBrowserItemsFetcher implements MediaItemsFetcher {
    private static final String TAG = "Media.BrowserFetcher";

    /**
     * An id that can be returned from {@link MediaBrowser.MediaItem#getMediaId()} to indicate that
     * a {@link android.media.browse.MediaBrowser.MediaItem} representing the play queue has been
     * clicked.
     */
    static final String PLAY_QUEUE_MEDIA_ID = "com.android.car.media.drawer.PLAY_QUEUE";

    private final Context mContext;
    private final MediaPlaybackModel mMediaPlaybackModel;
    private final String mMediaId;
    private final boolean mShowQueueItem;
    private final MediaItemOnClickListener mItemClickListener;
    private ItemsUpdatedCallback mCallback;
    private List<MediaBrowser.MediaItem> mItems = new ArrayList<>();
    private boolean mQueueAvailable;

    MediaBrowserItemsFetcher(Context context, MediaPlaybackModel model,
            MediaItemOnClickListener listener, String mediaId, boolean showQueueItem) {
        mContext = context;
        mMediaPlaybackModel = model;
        mItemClickListener = listener;
        mMediaId = mediaId;
        mShowQueueItem = showQueueItem;
    }

    @Override
    public void start(ItemsUpdatedCallback callback) {
        mCallback = callback;
        updateQueueAvailability();
        mMediaPlaybackModel.getMediaBrowser().subscribe(mMediaId, mSubscriptionCallback);
        mMediaPlaybackModel.addListener(mModelListener);
    }

    private final MediaPlaybackModel.Listener mModelListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            updateQueueAvailability();
        }
        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
            updateQueueAvailability();
        }
    };

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
        new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaBrowser.MediaItem> children) {
                mItems.clear();
                mItems.addAll(children);
                mCallback.onItemsUpdated();
            }

            @Override
            public void onError(String parentId) {
                Log.e(TAG, "Error loading children of: " + mMediaId);
                mItems.clear();
                mCallback.onItemsUpdated();
            }
        };

    private void updateQueueAvailability() {
        if (mShowQueueItem && !mMediaPlaybackModel.getQueue().isEmpty()) {
            mQueueAvailable = true;
        }
    }

    @Override
    public int getItemCount() {
        int size = mItems.size();
        if (mQueueAvailable) {
            size++;
        }
        return size;
    }

    @Override
    public boolean usesSmallLayout(int position) {
        if (mQueueAvailable && position == mItems.size()) {
            return true;
        }
        return MediaItemsFetcher.usesSmallLayout(mItems.get(position).getDescription());
    }

    @Override
    public void populateViewHolder(DrawerItemViewHolder holder, int position) {
        if (mQueueAvailable && position == mItems.size()) {
            holder.getTitle().setText(mMediaPlaybackModel.getQueueTitle());
            return;
        }
        MediaBrowser.MediaItem item = mItems.get(position);
        MediaItemsFetcher.populateViewHolderFrom(holder, item.getDescription());

        if (holder.getRightIcon() == null) {
            return;
        }

        if (item.isBrowsable()) {
            int iconColor = mContext.getColor(R.color.car_tint);
            Drawable drawable = mContext.getDrawable(R.drawable.ic_chevron_right);
            drawable.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            holder.getRightIcon().setImageDrawable(drawable);
        } else {
            holder.getRightIcon().setImageDrawable(null);
        }
    }

    @Override
    public void onItemClick(int position) {
        if (mItemClickListener == null) {
            return;
        }

        MediaBrowser.MediaItem item = mQueueAvailable && position == mItems.size()
                ? createPlayQueueMediaItem()
                : mItems.get(position);

        mItemClickListener.onMediaItemClicked(item);
    }

    /**
     * Creates and returns a {@link android.media.browse.MediaBrowser.MediaItem} that represents an
     * entry for the play queue. A play queue media item will have a media id of
     * {@link #PLAY_QUEUE_MEDIA_ID} and is {@link MediaBrowser.MediaItem#FLAG_BROWSABLE}.
     */
    private MediaBrowser.MediaItem createPlayQueueMediaItem() {
        MediaDescription description = new MediaDescription.Builder()
                .setMediaId(PLAY_QUEUE_MEDIA_ID)
                .setTitle(mMediaPlaybackModel.getQueueTitle())
                .build();

        return new MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_BROWSABLE);
    }

    @Override
    public void cleanup() {
        mMediaPlaybackModel.removeListener(mModelListener);
        mMediaPlaybackModel.getMediaBrowser().unsubscribe(mMediaId);
        mCallback = null;
    }

    @Override
    public int getScrollPosition() {
        return MediaItemsFetcher.DONT_SCROLL;
    }
}
