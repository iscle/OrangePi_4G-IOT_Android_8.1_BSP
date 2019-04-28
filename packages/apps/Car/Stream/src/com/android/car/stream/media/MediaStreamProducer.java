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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.util.Log;
import android.view.KeyEvent;
import com.android.car.stream.StreamCard;
import com.android.car.stream.StreamProducer;

/**
 * Produces {@link StreamCard} on media playback or metadata changes.
 */
public class MediaStreamProducer extends StreamProducer
        implements MediaPlaybackMonitor.MediaPlaybackMonitorListener {
    private static final String TAG = "MediaStreamProducer";

    private MediaPlaybackMonitor mPlaybackMonitor;
    private MediaStateManager mMediaStateManager;
    private MediaKeyReceiver mMediaKeyReceiver;
    private MediaConverter mConverter;

    private StreamCard mCurrentMediaStreamCard;

    private boolean mHasReceivedPlaybackState;
    private boolean mHasReceivedMetadata;

    // Current playback state of the media session.
    private boolean mIsPlaying;
    private boolean mHasPause;
    private boolean mCanSkipToNext;
    private boolean mCanSkipToPrevious;

    private String mTitle;
    private String mSubtitle;
    private Bitmap mAlbumArt;
    private int mAppAccentColor;
    private String mAppName;

    public MediaStreamProducer(Context context) {
        super(context);
        mConverter = new MediaConverter(context);
    }

    @Override
    public void start() {
        super.start();
        mPlaybackMonitor = new MediaPlaybackMonitor(mContext,
                MediaStreamProducer.this /* MediaPlaybackMonitorListener */);
        mPlaybackMonitor.start();

        mMediaKeyReceiver = new MediaKeyReceiver();
        mContext.registerReceiver(mMediaKeyReceiver,
                new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

        mMediaStateManager = new MediaStateManager(mContext);
        mMediaStateManager.addListener(mPlaybackMonitor);
        mMediaStateManager.start();
    }

    @Override
    public void stop() {
        mPlaybackMonitor.stop();
        mMediaStateManager.destroy();

        mPlaybackMonitor = null;
        mMediaStateManager = null;

        mContext.unregisterReceiver(mMediaKeyReceiver);
        mMediaKeyReceiver = null;
        super.stop();
    }

    private class MediaKeyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
                KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event == null) {
                    return;
                }
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Received media key " + event.getKeyCode());
                }
                mMediaStateManager.dispatchMediaButton(event);
            }
        }
    }

    public void onPlaybackStateChanged(PlaybackState state) {
        //Some media apps tend to spam playback state changes. Check if the playback state changes
        // are relevant. If it is the same, don't bother updating and posting to the stream.
        if (isDuplicatePlaybackState(state)) {
            return;
        }

        int playbackState = state.getState();
        mHasPause = ((state.getActions() & PlaybackState.ACTION_PAUSE) != 0);
        if (!mHasPause) {
            mHasPause = ((state.getActions() & PlaybackState.ACTION_PLAY_PAUSE) != 0);
        }
        mCanSkipToNext = ((state.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        mCanSkipToPrevious = ((state.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);
        if (playbackState == PlaybackState.STATE_PLAYING
                || playbackState == PlaybackState.STATE_BUFFERING) {
            mIsPlaying = true;
        }  else {
            mIsPlaying = false;
        }
        mHasReceivedPlaybackState = true;
        maybeUpdateStreamCard();
    }

    private void maybeUpdateStreamCard() {
        if (mHasReceivedPlaybackState && mHasReceivedMetadata) {
            mCurrentMediaStreamCard = mConverter.convert(mTitle, mSubtitle, mAlbumArt,
                    mAppAccentColor, mAppName, mCanSkipToNext, mCanSkipToPrevious,
                    mHasPause, mIsPlaying);
            if (mCurrentMediaStreamCard == null) {
                Log.w(TAG, "Media Card was not created");
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Media Card posted");
            }
            postCard(mCurrentMediaStreamCard);
        }
    }

    public void onMetadataChanged(String title, String subtitle, Bitmap albumArt, int color,
            String appName) {
        //Some media apps tend to spam metadata state changes. Check if the playback state changes
        // are relevant. If it is the same, don't bother updating and posting to the stream.
        if (isSameString(title, mTitle)
                && isSameString(subtitle, mSubtitle)
                && isSameBitmap(albumArt, albumArt)
                && color == mAppAccentColor
                && isSameString(appName, mAppName)) {
            return;
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Update notification.");
        }

        mTitle = title;
        mSubtitle = subtitle;
        mAlbumArt = albumArt;
        mAppAccentColor = color;
        mAppName = appName;

        mHasReceivedMetadata = true;
        maybeUpdateStreamCard();
    }

    private boolean isDuplicatePlaybackState(PlaybackState state) {
        if (!mHasReceivedPlaybackState) {
            return false;
        }
        int playbackState = state.getState();

        boolean hasPause
                = ((state.getActions() & PlaybackState.ACTION_PAUSE) != 0);
        boolean canSkipToNext
                = ((state.getActions() & PlaybackState.ACTION_SKIP_TO_NEXT) != 0);
        boolean canSkipToPrevious
                = ((state.getActions() & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0);

        boolean isPlaying = playbackState == PlaybackState.STATE_PLAYING
                || playbackState == PlaybackState.STATE_BUFFERING;

        return (hasPause == mHasPause
                && canSkipToNext == mCanSkipToNext
                && canSkipToPrevious == mCanSkipToPrevious
                && isPlaying == mIsPlaying);
    }

    @Override
    public void onAlbumArtUpdated(Bitmap albumArt) {
        mAlbumArt = albumArt;
        maybeUpdateStreamCard();
    }

    @Override
    public void onNewAppConnected() {
        mHasReceivedMetadata = false;
        mHasReceivedPlaybackState = false;
        removeCard(mCurrentMediaStreamCard);
        mCurrentMediaStreamCard = null;

        // clear out all existing values
        mTitle = null;
        mSubtitle = null;
        mAlbumArt = null;
        mAppName = null;
        mAppAccentColor = 0;
        mCanSkipToNext = false;
        mCanSkipToPrevious = false;
        mHasPause = false;
        mIsPlaying = false;
        mIsPlaying = false;
    }

    @Override
    public void removeMediaStreamCard() {
        removeCard(mCurrentMediaStreamCard);
        mCurrentMediaStreamCard = null;
    }

    private boolean isSameBitmap(Bitmap bmp1, Bitmap bmp2) {
        return bmp1 == null
                ? bmp2 == null : (bmp1 == bmp2 && bmp1.getGenerationId() == bmp2.getGenerationId());
    }

    private boolean isSameString(CharSequence str1, CharSequence str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }
}
