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

package com.android.tv.menu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.data.ProgramDataManager;

import java.util.List;

/**
 * A poster image prefetcher to show the program poster art in the Channels row faster.
 */
public class ChannelsPosterPrefetcher {
    private static final String TAG = "PosterPrefetcher";
    private static final boolean DEBUG = false;
    private static final int MSG_PREFETCH_IMAGE = 1000;
    private static final int ONDEMAND_POSTER_PREFETCH_DELAY_MILLIS = 500;  // 500 milliseconds

    private final ProgramDataManager mProgramDataManager;
    private final ChannelsRowAdapter mChannelsAdapter;
    private final int mPosterArtWidth;
    private final int mPosterArtHeight;
    private final Context mContext;
    private final Handler mHandler = new PrefetchHandler(this);

    private boolean isCanceled;

    /**
     * Create {@link ChannelsPosterPrefetcher} object with given parameters.
     */
    public ChannelsPosterPrefetcher(Context context, ProgramDataManager programDataManager,
            ChannelsRowAdapter adapter) {
        mProgramDataManager = programDataManager;
        mChannelsAdapter = adapter;
        mPosterArtWidth = context.getResources().getDimensionPixelSize(
                R.dimen.card_image_layout_width);
        mPosterArtHeight = context.getResources().getDimensionPixelSize(
                R.dimen.card_image_layout_height);
        mContext = context.getApplicationContext();
    }

    /**
     * Start prefetching of program poster art of recommendation.
     */
    public void prefetch() {
        SoftPreconditions.checkState(!isCanceled, TAG, "Prefetch called after cancel was called.");
        if (isCanceled) {
            return;
        }
        if (DEBUG) Log.d(TAG, "startPrefetching()");
        /*
         * When a user browse channels, this method could be called many times. We don't need to
         * prefetch the intermediate channels. So ignore previous schedule.
         */
        mHandler.removeMessages(MSG_PREFETCH_IMAGE);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PREFETCH_IMAGE),
                ONDEMAND_POSTER_PREFETCH_DELAY_MILLIS);
    }

    /**
     * Cancels pending and current prefetch requests.
     */
    public void cancel() {
        isCanceled = true;
        mHandler.removeCallbacksAndMessages(null);
    }

    @MainThread // ProgramDataManager.getCurrentProgram must be called from the main thread
    private void doPrefetchImages() {
        if (DEBUG) Log.d(TAG, "doPrefetchImages() started");

        // This executes on the main thread, but since the item list is expected to be about 5 items
        // and ImageLoader spawns an async task so this is fast enough. 1 ms in local testing.
        List<ChannelsRowItem> items = mChannelsAdapter.getItemList();
        if (items != null) {
            for (ChannelsRowItem item : items) {
                if (isCanceled) {
                    return;
                }
                Channel channel = item.getChannel();
                if (!Channel.isValid(channel)) {
                    continue;
                }
                channel.prefetchImage(mContext, Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO,
                        mPosterArtWidth, mPosterArtHeight);
                Program program = mProgramDataManager.getCurrentProgram(channel.getId());
                if (program != null) {
                    program.prefetchPosterArt(mContext, mPosterArtWidth, mPosterArtHeight);
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "doPrefetchImages() finished. ImageLoader may still have async tasks for "
                            + "channels " + items);
        }
    }

    private static class PrefetchHandler extends WeakHandler<ChannelsPosterPrefetcher> {
        public PrefetchHandler(ChannelsPosterPrefetcher ref) {
            // doPrefetchImages must be called from the main thread.
            super(Looper.getMainLooper(), ref);
        }

        @Override
        @MainThread
        public void handleMessage(Message msg, @NonNull ChannelsPosterPrefetcher prefetcher) {
            switch (msg.what) {
                case MSG_PREFETCH_IMAGE:
                    prefetcher.doPrefetchImages();
                    break;
            }
        }
    }
}
