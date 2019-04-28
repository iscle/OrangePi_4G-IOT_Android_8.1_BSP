/*
 * Copyright (c) 2016, The Android Open Source Project
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
package com.android.car.media.localmediaplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.service.media.MediaBrowserService;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class LocalMediaBrowserService extends MediaBrowserService {
    private static final String TAG = "LMBService";
    private static final String ROOT_ID = "__ROOT__";
    private static final String MEDIA_SESSION_TAG = "LOCAL_MEDIA_SESSION";

    static final String FOLDERS_ID = "__FOLDERS__";
    static final String ARTISTS_ID = "__ARTISTS__";
    static final String ALBUMS_ID = "__ALBUMS__";
    static final String GENRES_ID = "__GENRES__";

    static final String ACTION_PLAY = "com.android.car.media.localmediaplayer.ACTION_PLAY";
    static final String ACTION_PAUSE = "com.android.car.media.localmediaplayer.ACTION_PAUSE";
    static final String ACTION_NEXT = "com.android.car.media.localmediaplayer.ACTION_NEXT";
    static final String ACTION_PREV = "com.android.car.media.localmediaplayer.ACTION_PREV";

    private BrowserRoot mRoot = new BrowserRoot(ROOT_ID, null);
    List<MediaBrowser.MediaItem> mRootItems = new ArrayList<>();

    private DataModel mDataModel;
    private Player mPlayer;
    private MediaSession mSession;
    private String mLastCategory;

    private BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() == null) {
                return;
            }

            switch (intent.getAction()) {
                case ACTION_PLAY:
                    mPlayer.onPlay();
                    break;
                case ACTION_PAUSE:
                    mPlayer.onPause();
                    break;
                case ACTION_NEXT:
                    mPlayer.onSkipToNext();
                    break;
                case ACTION_PREV:
                    mPlayer.onSkipToPrevious();
                    break;
                default:
                    Log.w(TAG, "Ingoring intent with unknown action=" + intent);
            }
        }
    };

    private void addRootItems() {
        MediaDescription folders = new MediaDescription.Builder()
                .setMediaId(FOLDERS_ID)
                .setTitle(getString(R.string.folders_title))
                .setIconUri(Utils.getUriForResource(this, R.drawable.ic_folder))
                .build();
        mRootItems.add(new MediaBrowser.MediaItem(folders, MediaBrowser.MediaItem.FLAG_BROWSABLE));

        MediaDescription albums = new MediaDescription.Builder()
                .setMediaId(ALBUMS_ID)
                .setTitle(getString(R.string.albums_title))
                .setIconUri(Utils.getUriForResource(this, R.drawable.ic_album))
                .build();
        mRootItems.add(new MediaBrowser.MediaItem(albums, MediaBrowser.MediaItem.FLAG_BROWSABLE));

        MediaDescription artists = new MediaDescription.Builder()
                .setMediaId(ARTISTS_ID)
                .setTitle(getString(R.string.artists_title))
                .setIconUri(Utils.getUriForResource(this, R.drawable.ic_artist))
                .build();
        mRootItems.add(new MediaBrowser.MediaItem(artists, MediaBrowser.MediaItem.FLAG_BROWSABLE));

        MediaDescription genres = new MediaDescription.Builder()
                .setMediaId(GENRES_ID)
                .setTitle(getString(R.string.genres_title))
                .setIconUri(Utils.getUriForResource(this, R.drawable.ic_genre))
                .build();
        mRootItems.add(new MediaBrowser.MediaItem(genres, MediaBrowser.MediaItem.FLAG_BROWSABLE));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: This doesn't handle the case where the user revokes the permission very well, the
        // prompt will only show up once this service has been recreated which is non-deterministic.
        if (!Utils.hasRequiredPermissions(this)) {
            Intent intent = new Intent(this, PermissionsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        mDataModel = new DataModel(this);
        addRootItems();
        mSession = new MediaSession(this, MEDIA_SESSION_TAG);
        setSessionToken(mSession.getSessionToken());
        mPlayer = new Player(this, mSession, mDataModel);
        mSession.setCallback(mPlayer);
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mPlayer.maybeRestoreState();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PLAY);
        filter.addAction(ACTION_PAUSE);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PREV);
        registerReceiver(mNotificationReceiver, filter);
    }

    @Override
    public void onDestroy() {
        mPlayer.saveState();
        mPlayer.destroy();
        mSession.release();
        unregisterReceiver(mNotificationReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(String clientName, int clientUid, Bundle rootHints) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onGetRoot clientName=" + clientName);
        }
        return mRoot;
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onLoadChildren parentId=" + parentId);
        }

        switch (parentId) {
            case ROOT_ID:
                result.sendResult(mRootItems);
                mLastCategory = parentId;
                break;
            case FOLDERS_ID:
                mDataModel.onQueryByFolder(parentId, result);
                mLastCategory = parentId;
                break;
            case ALBUMS_ID:
                mDataModel.onQueryByAlbum(parentId, result);
                mLastCategory = parentId;
                break;
            case ARTISTS_ID:
                mDataModel.onQueryByArtist(parentId, result);
                mLastCategory = parentId;
                break;
            case GENRES_ID:
                mDataModel.onQueryByGenre(parentId, result);
                mLastCategory = parentId;
                break;
            default:
                mDataModel.onQueryByKey(mLastCategory, parentId, result);
        }
    }
}
