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
package com.android.car.stream.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import com.android.car.apps.common.BitmapDownloader;
import com.android.car.apps.common.BitmapWorkerOptions;
import com.android.car.stream.R;

/**
 * An service which connects to {@link MediaStateManager} for media updates (playback state and
 * metadata) and notifies listeners for these changes.
 * <p/>
 */
public class MediaPlaybackMonitor implements MediaStateManager.Listener {
    protected static final String TAG = "MediaPlaybackMonitor";

    // MSG for metadata update handler
    private static final int MSG_UPDATE_METADATA = 1;
    private static final int MSG_IMAGE_DOWNLOADED = 2;
    private static final int MSG_NEW_ALBUM_ART_RECEIVED = 3;

    public interface MediaPlaybackMonitorListener {
        void onPlaybackStateChanged(PlaybackState state);

        void onMetadataChanged(String title, String text, Bitmap art, int color, String appName);

        void onAlbumArtUpdated(Bitmap albumArt);

        void onNewAppConnected();

        void removeMediaStreamCard();
    }

    private static final String[] PREFERRED_BITMAP_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
    };

    private static final String[] PREFERRED_URI_ORDER = {
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    };

    private MediaMetadata mCurrentMetadata;
    private MediaStatusUpdateHandler mMediaStatusUpdateHandler;
    private MediaAppInfo mCurrentMediaAppInfo;
    private MediaPlaybackMonitorListener mMonitorListener;

    private Context mContext;

    private final int mIconSize;

    public MediaPlaybackMonitor(Context context, @NonNull MediaPlaybackMonitorListener callback) {
        mContext = context;
        mMonitorListener = callback;
        mIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.stream_media_icon_size);
    }

    public final void start() {
        mMediaStatusUpdateHandler = new MediaStatusUpdateHandler();
    }

    public final void stop() {
        if (mMediaStatusUpdateHandler != null) {
            mMediaStatusUpdateHandler.removeCallbacksAndMessages(null);
            mMediaStatusUpdateHandler = null;
        }
    }

    @Override
    public void onMediaSessionConnected(PlaybackState state, MediaMetadata metaData,
            MediaAppInfo appInfo) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "MediaSession onConnected called");
        }

        // If the current media app is not the same as the new media app, reset
        // the media app in MediaStreamManager
        if (mCurrentMediaAppInfo == null
                || !mCurrentMediaAppInfo.getPackageName().equals(appInfo.getPackageName())) {
            mMonitorListener.onNewAppConnected();
            if (mMediaStatusUpdateHandler != null) {
                mMediaStatusUpdateHandler.removeCallbacksAndMessages(null);
            }
            mCurrentMediaAppInfo = appInfo;
        }

        if (metaData != null) {
            onMetadataChanged(metaData);
        }

        if (state != null) {
            onPlaybackStateChanged(state);
        }
    }

    @Override
    public void onPlaybackStateChanged(@Nullable PlaybackState state) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onPlaybackStateChanged called " + state.getState());
        }

        if (state == null) {
            Log.w(TAG, "playback state is null in onPlaybackStateChanged");
            mMonitorListener.removeMediaStreamCard();
            return;
        }

        if (mMonitorListener != null) {
            mMonitorListener.onPlaybackStateChanged(state);
        }
    }

    @Override
    public void onMetadataChanged(@Nullable MediaMetadata metadata) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onMetadataChanged called");
        }
        if (metadata == null) {
            mMonitorListener.removeMediaStreamCard();
            return;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "received " + metadata.getDescription());
        }
        // Compare the new metadata and the last we have posted notification for. If both
        // metadata and album art are the same, just ignore and return. If the album art is new,
        // update the stream item with the new album art.
        MediaDescription currentDescription = mCurrentMetadata == null ?
                null : mCurrentMetadata.getDescription();

        if (!MediaUtils.isSameMediaDescription(metadata.getDescription(), currentDescription)) {
            Message msg =
                    mMediaStatusUpdateHandler.obtainMessage(MSG_UPDATE_METADATA, metadata);
            // Remove obsolete notifications  in the queue.
            mMediaStatusUpdateHandler.removeMessages(MSG_UPDATE_METADATA);
            mMediaStatusUpdateHandler.sendMessage(msg);
        } else {
            Bitmap newBitmap = metadata.getDescription().getIconBitmap();
            if (newBitmap == null) {
                return;
            }
            if (newBitmap.sameAs(mMediaStatusUpdateHandler.getCurrentIcon())) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Received duplicate metadata, ignoring...");
                }
            } else {
                // same metadata, but new album art
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Received metadata with new album art");
                }
                Message msg = mMediaStatusUpdateHandler
                        .obtainMessage(MSG_NEW_ALBUM_ART_RECEIVED, newBitmap);
                mMediaStatusUpdateHandler.removeMessages(MSG_NEW_ALBUM_ART_RECEIVED);
                mMediaStatusUpdateHandler.sendMessage(msg);
            }
        }
    }

    @Override
    public void onSessionDestroyed() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Media session destroyed");
        }
        mMonitorListener.removeMediaStreamCard();
    }

    private class BitmapCallback extends BitmapDownloader.BitmapCallback {
        final private int mSeq;

        public BitmapCallback(int seq) {
            mSeq = seq;
        }

        @Override
        public void onBitmapRetrieved(Bitmap bitmap) {
            if (mMediaStatusUpdateHandler == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "The callback comes after we finish");
                }
                return;
            }
            Message msg = mMediaStatusUpdateHandler.obtainMessage(MSG_IMAGE_DOWNLOADED,
                    mSeq, 0, bitmap);
            mMediaStatusUpdateHandler.sendMessage(msg);
        }
    }

    private class MediaStatusUpdateHandler extends Handler {
        private int mSeq = 0;
        private BitmapCallback mCallback;
        private MediaMetadata mMetadata;
        private String mTitle;
        private String mSubtitle;
        private Bitmap mIcon;
        private Uri mIconUri;
        private final BitmapDownloader mDownloader = BitmapDownloader.getInstance(mContext);

        private void extractMetadata(MediaMetadata metadata) {
            if (metadata == mMetadata) {
                // We are up to date and must return here, because we've already recycled the bitmap
                // inside it.
                return;
            }
            // keep a reference so we know which metadata we have stored.
            mMetadata = metadata;
            MediaDescription description = metadata.getDescription();
            mTitle = description.getTitle() == null ? null : description.getTitle().toString();
            mSubtitle = description.getSubtitle() == null ?
                    null : description.getSubtitle().toString();
            final Bitmap originalBitmap = getMetadataBitmap(metadata);
            if (originalBitmap != null) {
                mIcon = originalBitmap;
            } else {
                mIcon = null;
            }
            mIconUri = getMetadataIconUri(metadata);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Album Art Uri: " + mIconUri);
            }
        }

        private Uri getMetadataIconUri(MediaMetadata metadata) {
            // Get the best Uri we can find
            for (int i = 0; i < PREFERRED_URI_ORDER.length; i++) {
                String iconUri = metadata.getString(PREFERRED_URI_ORDER[i]);
                if (!TextUtils.isEmpty(iconUri)) {
                    return Uri.parse(iconUri);
                }
            }
            return null;
        }

        private Bitmap getMetadataBitmap(MediaMetadata metadata) {
            // Get the best art bitmap we can find
            for (int i = 0; i < PREFERRED_BITMAP_ORDER.length; i++) {
                Bitmap bitmap = metadata.getBitmap(PREFERRED_BITMAP_ORDER[i]);
                if (bitmap != null) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Retrieved bitmap type: " + PREFERRED_BITMAP_ORDER[i]
                                + " w: " + bitmap.getWidth()
                                + " h: " + bitmap.getHeight());
                    }
                    return bitmap;
                }
            }
            return null;
        }

        public Bitmap getCurrentIcon() {
            return mIcon;
        }

        @Override
        public void handleMessage(Message msg) {
            MediaAppInfo mediaAppInfo = mCurrentMediaAppInfo;
            int color = mediaAppInfo.getMediaClientAccentColor();
            String appName = mediaAppInfo.getAppName();
            switch (msg.what) {
                case MSG_UPDATE_METADATA:
                    mSeq++;
                    MediaMetadata metadata = (MediaMetadata) msg.obj;
                    if (metadata == null) {
                        Log.w(TAG, "media metadata is null!");
                        return;
                    }
                    extractMetadata(metadata);
                    if (mCallback != null) {
                        // it's ok to cancel a callback that has already been called, the downloader
                        // will just ignore the operation.
                        mDownloader.cancelDownload(mCallback);
                        mCallback = null;
                    }
                    if (mIcon != null) {
                        mMonitorListener.onMetadataChanged(mTitle, mSubtitle, mIcon,
                                color, appName);
                    } else if (mIconUri != null) {
                        mCallback = new BitmapCallback(mSeq);
                        mDownloader.getBitmap(
                                new BitmapWorkerOptions.Builder(mContext)
                                        .resource(mIconUri).width(mIconSize)
                                        .height(mIconSize).build(), mCallback);
                    } else {
                        mMonitorListener.onMetadataChanged(mTitle, mSubtitle, mIcon,
                                color, appName);
                    }
                    // Only set mCurrentMetadata after we have updated the listener (if the
                    // bitmap is downloaded asynchronously, that is fine too. The stream card will
                    // be posted, when image is downloaded.)
                    mCurrentMetadata = metadata;
                    break;

                case MSG_IMAGE_DOWNLOADED:
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Image downloaded...");
                    }
                    int seq = msg.arg1;
                    Bitmap bitmap = (Bitmap) msg.obj;
                    if (seq == mSeq) {
                        mMonitorListener.onMetadataChanged(mTitle, mSubtitle, bitmap, color, appName);
                    }
                    break;

                case MSG_NEW_ALBUM_ART_RECEIVED:
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Received a new album art...");
                    }
                    Bitmap newAlbumArt = (Bitmap) msg.obj;
                    mMonitorListener.onAlbumArtUpdated(newAlbumArt);
                    break;
                default:
            }
        }
    }
}
