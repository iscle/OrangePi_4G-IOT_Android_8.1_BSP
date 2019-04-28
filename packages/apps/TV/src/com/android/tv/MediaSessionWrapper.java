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

package com.android.tv;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.Utils;

/**
 * A wrapper class for {@link MediaSession} to support common operations on media sessions for
 * {@link MainActivity}.
 */
class MediaSessionWrapper {
    private static final String MEDIA_SESSION_TAG = "com.android.tv.mediasession";
    private static PlaybackState MEDIA_SESSION_STATE_PLAYING = new PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .build();
    private static PlaybackState MEDIA_SESSION_STATE_STOPPED = new PlaybackState.Builder()
            .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f)
            .build();

    private final Context mContext;
    private final MediaSession mMediaSession;
    private int mNowPlayingCardWidth;
    private int mNowPlayingCardHeight;

    MediaSessionWrapper(Context context) {
        mContext = context;
        mMediaSession = new MediaSession(context, MEDIA_SESSION_TAG);
        mMediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent) {
                // Consume the media button event here. Should not send it to other apps.
                return true;
            }
        });
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mNowPlayingCardWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.notif_card_img_max_width);
        mNowPlayingCardHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.notif_card_img_height);
    }

    /**
     * Sets playback state.
     *
     * @param isPlaying {@code true} if TV is playing, otherwise {@code false}.
     */
    void setPlaybackState(boolean isPlaying) {
        if (isPlaying) {
            mMediaSession.setActive(true);
            // setPlaybackState() has to be called after calling setActive(). b/31933276
            mMediaSession.setPlaybackState(MEDIA_SESSION_STATE_PLAYING);
        } else if (mMediaSession.isActive()) {
            mMediaSession.setPlaybackState(MEDIA_SESSION_STATE_STOPPED);
            mMediaSession.setActive(false);
        }
    }

    /**
     * Updates media session according to the current TV playback status.
     *
     * @param blocked {@code true} if the current channel is blocked, either by user settings or
     *                the current program's content ratings.
     * @param currentChannel The currently playing channel.
     * @param currentProgram The currently playing program.
     */
    void update(boolean blocked, Channel currentChannel, Program currentProgram) {
        if (currentChannel == null) {
            setPlaybackState(false);
            return;
        }

        // If the channel is blocked, display a lock and a short text on the Now Playing Card
        if (blocked) {
            Bitmap art = BitmapFactory.decodeResource(mContext.getResources(),
                    R.drawable.ic_message_lock_preview);
            updateMediaMetadata(mContext.getResources()
                    .getString(R.string.channel_banner_locked_channel_title), art);
            setPlaybackState(true);
            return;
        }

        String cardTitleText = null;
        String posterArtUri = null;
        if (currentProgram != null) {
            cardTitleText = currentProgram.getTitle();
            posterArtUri = currentProgram.getPosterArtUri();
        }
        if (TextUtils.isEmpty(cardTitleText)) {
            cardTitleText = getChannelName(currentChannel);
        }
        updateMediaMetadata(cardTitleText, null);
        if (posterArtUri == null) {
            posterArtUri = TvContract.buildChannelLogoUri(currentChannel.getId()).toString();
        }
        updatePosterArt(currentChannel, currentProgram, cardTitleText, null, posterArtUri);
        setPlaybackState(true);
    }

    /**
     * Releases the media session.
     *
     * @see MediaSession#release()
     */
    void release() {
        mMediaSession.release();
    }

    private String getChannelName(Channel channel) {
        if (channel.isPassthrough()) {
            TvInputInfo input = TvApplication.getSingletons(mContext).getTvInputManagerHelper()
                    .getTvInputInfo(channel.getInputId());
            return Utils.loadLabel(mContext, input);
        } else {
            return channel.getDisplayName();
        }
    }

    private void updatePosterArt(Channel currentChannel, Program currentProgram,
            String cardTitleText, @Nullable Bitmap posterArt, @Nullable String posterArtUri) {
        if (posterArt != null) {
            updateMediaMetadata(cardTitleText, posterArt);
        } else if (posterArtUri != null) {
            ImageLoader.loadBitmap(mContext, posterArtUri, mNowPlayingCardWidth,
                    mNowPlayingCardHeight, new ProgramPosterArtCallback(this, currentChannel,
                            currentProgram, cardTitleText));
        } else {
            updateMediaMetadata(cardTitleText, R.drawable.default_now_card);
        }
    }

    private void updateMediaMetadata(final String title, final Bitmap posterArt) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... arg0) {
                MediaMetadata.Builder builder = new MediaMetadata.Builder();
                builder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
                if (posterArt != null) {
                    builder.putBitmap(MediaMetadata.METADATA_KEY_ART, posterArt);
                }
                mMediaSession.setMetadata(builder.build());
                return null;
            }
        }.execute();
    }

    private void updateMediaMetadata(final String title, final int imageResId) {
        new AsyncTask<Void, Void, Void> () {
            @Override
            protected Void doInBackground(Void... arg0) {
                MediaMetadata.Builder builder = new MediaMetadata.Builder();
                builder.putString(MediaMetadata.METADATA_KEY_TITLE, title);
                Bitmap posterArt =
                        BitmapFactory.decodeResource(mContext.getResources(), imageResId);
                if (posterArt != null) {
                    builder.putBitmap(MediaMetadata.METADATA_KEY_ART, posterArt);
                }
                mMediaSession.setMetadata(builder.build());
                return null;
            }
        }.execute();
    }

    private static class ProgramPosterArtCallback extends
            ImageLoader.ImageLoaderCallback<MediaSessionWrapper> {
        private final Channel mChannel;
        private final Program mProgram;
        private final String mCardTitleText;

        ProgramPosterArtCallback(MediaSessionWrapper sessionWrapper, Channel channel,
                Program program, String cardTitleText) {
            super(sessionWrapper);
            mChannel = channel;
            mProgram = program;
            mCardTitleText = cardTitleText;
        }

        @Override
        public void onBitmapLoaded(MediaSessionWrapper sessionWrapper, @Nullable Bitmap posterArt) {
            if (((MainActivity) sessionWrapper.mContext).isNowPlayingProgram(mChannel, mProgram)) {
                sessionWrapper.updatePosterArt(mChannel, mProgram, mCardTitleText, posterArt, null);
            }
        }
    }
}
