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
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.support.annotation.Nullable;
import com.android.car.app.DrawerItemViewHolder;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link MediaItemsFetcher} implementation that fetches items from the {@link MediaController}'s
 * currently playing queue.
 */
class MediaQueueItemsFetcher implements MediaItemsFetcher {
    private final Handler mHandler = new Handler();
    private final Context mContext;
    private final MediaItemOnClickListener mClickListener;
    private MediaPlaybackModel mMediaPlaybackModel;
    private ItemsUpdatedCallback mCallback;
    private List<MediaSession.QueueItem> mItems = new ArrayList<>();

    MediaQueueItemsFetcher(Context context, MediaPlaybackModel model,
            MediaItemOnClickListener listener) {
        mContext = context;
        mMediaPlaybackModel = model;
        mClickListener = listener;
    }

    @Override
    public void start(ItemsUpdatedCallback callback) {
        mCallback = callback;
        if (mMediaPlaybackModel != null) {
            mMediaPlaybackModel.addListener(mListener);
            updateItemsFrom(mMediaPlaybackModel.getQueue());
        }
        // Inform client of current items. Invoke async to avoid re-entrancy issues.
        mHandler.post(mCallback::onItemsUpdated);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public boolean usesSmallLayout(int position) {
        return MediaItemsFetcher.usesSmallLayout(mItems.get(position).getDescription());
    }

    @Override
    public void populateViewHolder(DrawerItemViewHolder holder, int position) {
        MediaSession.QueueItem item = mItems.get(position);
        MediaItemsFetcher.populateViewHolderFrom(holder, item.getDescription());

        if (holder.getRightIcon() == null) {
            return;
        }

        if (item.getQueueId() == getActiveQueueItemId()) {
            int primaryColor = mMediaPlaybackModel.getPrimaryColor();
            Drawable drawable =
                    mContext.getDrawable(R.drawable.ic_music_active);
            drawable.setColorFilter(primaryColor, PorterDuff.Mode.SRC_IN);
            holder.getRightIcon().setImageDrawable(drawable);
        } else {
            holder.getRightIcon().setImageBitmap(null);
        }
    }

    @Override
    public void onItemClick(int position) {
        if (mClickListener != null) {
            mClickListener.onQueueItemClicked(mItems.get(position));
        }
    }

    @Override
    public void cleanup() {
        mMediaPlaybackModel.removeListener(mListener);
    }

    @Override
    public int getScrollPosition() {
        long activeId = getActiveQueueItemId();
        // A linear scan isn't really the best thing to do for large lists but we suspect that
        // the queue isn't going to be very long anyway so we can just do the trivial thing. If
        // it starts becoming a problem, we can build an index over the ids.
        for (int position = 0; position < mItems.size(); position++) {
            MediaSession.QueueItem item = mItems.get(position);
            if (item.getQueueId() == activeId) {
                return position;
            }
        }
        return MediaItemsFetcher.DONT_SCROLL;
    }

    private void updateItemsFrom(List<MediaSession.QueueItem> queue) {
        mItems.clear();
        mItems.addAll(queue);
    }

    private long getActiveQueueItemId() {
        if (mMediaPlaybackModel != null) {
            PlaybackState playbackState = mMediaPlaybackModel.getPlaybackState();
            if (playbackState != null) {
                return playbackState.getActiveQueueItemId();
            }
        }
        return MediaSession.QueueItem.UNKNOWN_ID;
    }

    private final MediaPlaybackModel.Listener mListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            updateItemsFrom(queue);
            mCallback.onItemsUpdated();
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            // Since active playing item may have changed, force re-draw of queue items.
            mCallback.onItemsUpdated();
        }

        @Override
        public void onSessionDestroyed(CharSequence destroyedMediaClientName) {
            onQueueChanged(Collections.emptyList());
        }
    };
}
