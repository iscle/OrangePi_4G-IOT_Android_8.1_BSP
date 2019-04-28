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

import android.content.ComponentName;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import com.android.car.app.CarDrawerActivity;
import com.android.car.app.CarDrawerAdapter;
import com.android.car.media.MediaManager;
import com.android.car.media.MediaPlaybackModel;
import com.android.car.media.R;

/**
 * Manages drawer navigation and item selection.
 * <p>
 * Maintains separate MediaPlaybackModel for media browsing and control. Sets up root Drawer
 * adapter with root of media-browse tree (using MediaBrowserItemsFetcher). Supports switching the
 * rootAdapter to show the queue-items (using MediaQueueItemsFetcher).
 */
public class MediaDrawerController implements MediaDrawerAdapter.MediaFetchCallback,
        MediaItemOnClickListener {
    private static final String TAG = "MediaDrawerController";

    private static final String EXTRA_ICON_SIZE =
            "com.google.android.gms.car.media.BrowserIconSize";

    private final CarDrawerActivity mActivity;
    private final MediaPlaybackModel mMediaPlaybackModel;
    private MediaDrawerAdapter mRootAdapter;

    public MediaDrawerController(CarDrawerActivity activity) {
        mActivity = activity;
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_ICON_SIZE,
                mActivity.getResources().getDimensionPixelSize(R.dimen.car_list_item_icon_size));
        mMediaPlaybackModel = new MediaPlaybackModel(mActivity, extras);
        mMediaPlaybackModel.addListener(mModelListener);

        mRootAdapter = new MediaDrawerAdapter(mActivity);
        // Start with a empty title since we depend on the mMediaManagerListener callback to
        // know which app is being used and set the actual title there.
        mRootAdapter.setTitle("");
        mRootAdapter.setFetchCallback(this);

        // Kick off MediaBrowser/MediaController connection.
        mMediaPlaybackModel.start();
    }

    @Override
    public void onQueueItemClicked(MediaSession.QueueItem queueItem) {
        MediaController.TransportControls controls = mMediaPlaybackModel.getTransportControls();

        if (controls != null) {
            controls.skipToQueueItem(queueItem.getQueueId());
        }

        mActivity.closeDrawer();
    }

    @Override
    public void onMediaItemClicked(MediaBrowser.MediaItem item) {
        if (item.isBrowsable()) {
            MediaItemsFetcher fetcher;
            if (MediaBrowserItemsFetcher.PLAY_QUEUE_MEDIA_ID.equals(item.getMediaId())) {
                fetcher = createMediaQueueItemsFetcher();
            } else {
                fetcher = createMediaBrowserItemFetcher(item.getMediaId(),
                        false /* showQueueItem */);
            }
            setupAdapterAndSwitch(fetcher, item.getDescription().getTitle());
        } else if (item.isPlayable()) {
            MediaController.TransportControls controls = mMediaPlaybackModel.getTransportControls();
            if (controls != null) {
                controls.pause();
                controls.playFromMediaId(item.getMediaId(), item.getDescription().getExtras());
            }
            mActivity.closeDrawer();
        } else {
            Log.w(TAG, "Unknown item type; don't know how to handle!");
        }
    }

    @Override
    public void onFetchStart() {
        // Initially there will be no items and we don't want to show empty-list indicator
        // briefly until items are fetched.
        mActivity.showLoadingProgressBar(true);
    }

    @Override
    public void onFetchEnd() {
        mActivity.showLoadingProgressBar(false);
    }

    /**
     * Creates a new sub-level in the drawer and switches to that as the currently displayed view.
     *
     * @param fetcher The {@link MediaItemsFetcher} that is responsible for fetching the items to be
     *                displayed in the new view.
     * @param title The title text of the new view in the drawer.
     */
    private void setupAdapterAndSwitch(MediaItemsFetcher fetcher, CharSequence title) {
        MediaDrawerAdapter subAdapter = new MediaDrawerAdapter(mActivity);
        subAdapter.setFetcher(fetcher);
        subAdapter.setTitle(title);
        mActivity.switchToAdapter(subAdapter);
    }

    /**
     * Opens the drawer and displays the current playing queue of items. When the drawer is closed,
     * the view is switched back to the drawer root.
     */
    public void showPlayQueue() {
        mRootAdapter.setFetcher(createMediaQueueItemsFetcher());
        mRootAdapter.setTitle(mMediaPlaybackModel.getQueueTitle());
        mActivity.openDrawer();
        mRootAdapter.scrollToCurrent();
        mActivity.addDrawerListener(mQueueDrawerListener);
    }

    public void cleanup() {
        mActivity.removeDrawerListener(mQueueDrawerListener);
        mRootAdapter.cleanup();
        mMediaPlaybackModel.removeListener(mModelListener);
        mMediaPlaybackModel.stop();
    }

    /**
     * @return Adapter to display root items of MediaBrowse tree. {@link #showPlayQueue()} can
     *      be used to display items from the queue.
     */
    public CarDrawerAdapter getRootAdapter() {
        return mRootAdapter;
    }

    /**
     * Creates a {@link MediaBrowserItemsFetcher} that whose root is the given {@code mediaId}.
     */
    private MediaBrowserItemsFetcher createMediaBrowserItemFetcher(String mediaId,
            boolean showQueueItem) {
        return new MediaBrowserItemsFetcher(mActivity, mMediaPlaybackModel, this /* listener */,
                mediaId, showQueueItem);
    }

    /**
     * Creates a {@link MediaQueueItemsFetcher} that is responsible for fetching items in the user's
     * current play queue.
     */
    private MediaQueueItemsFetcher createMediaQueueItemsFetcher() {
        return new MediaQueueItemsFetcher(mActivity, mMediaPlaybackModel, this /* listener */);
    }

    /**
     * Creates a {@link MediaItemsFetcher} that will display the top-most level of the drawer.
     */
    private MediaItemsFetcher createRootMediaItemsFetcher() {
        return createMediaBrowserItemFetcher(mMediaPlaybackModel.getMediaBrowser().getRoot(),
                true /* showQueueItem */);
    }

    /**
     * A {@link android.support.v4.widget.DrawerLayout.DrawerListener} specifically to be used when
     * the play queue has been shown in the drawer. When the drawer is closed following this
     * display, this listener will reset the drawer to display the root view.
     */
    private final DrawerLayout.DrawerListener mQueueDrawerListener =
            new DrawerLayout.DrawerListener() {
        @Override
        public void onDrawerClosed(View drawerView) {
            mRootAdapter.setFetcher(createRootMediaItemsFetcher());
            mRootAdapter.setTitle(
                    MediaManager.getInstance(mActivity).getMediaClientName());
            mActivity.removeDrawerListener(this);
        }

        @Override
        public void onDrawerSlide(View drawerView, float slideOffset) {}
        @Override
        public void onDrawerOpened(View drawerView) {}
        @Override
        public void onDrawerStateChanged(int newState) {}
    };

    private final MediaPlaybackModel.Listener mModelListener =
            new MediaPlaybackModel.AbstractListener() {
        @Override
        public void onMediaAppChanged(@Nullable ComponentName currentName,
                @Nullable ComponentName newName) {
            // Only store MediaManager instance to a local variable when it is short lived.
            MediaManager mediaManager = MediaManager.getInstance(mActivity);
            mRootAdapter.setTitle(mediaManager.getMediaClientName());
        }

        @Override
        public void onMediaConnected() {
            mRootAdapter.setFetcher(createRootMediaItemsFetcher());
        }
    };
}
