/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.dvr.ui.playback;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.tv.TvContract;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.util.ImageLoader;
import com.android.tv.util.TimeShiftUtils;
import com.android.tv.util.Utils;

class DvrPlaybackMediaSessionHelper {
    private static final String TAG = "DvrPlaybackMediaSessionHelper";
    private static final boolean DEBUG = false;

    private int mNowPlayingCardWidth;
    private int mNowPlayingCardHeight;
    private int mSpeedLevel;
    private long mProgramDurationMs;

    private Activity mActivity;
    private DvrPlayer mDvrPlayer;
    private MediaSession mMediaSession;
    private final DvrWatchedPositionManager mDvrWatchedPositionManager;
    private final ChannelDataManager mChannelDataManager;

    public DvrPlaybackMediaSessionHelper(Activity activity, String mediaSessionTag,
            DvrPlayer dvrPlayer, DvrPlaybackOverlayFragment overlayFragment) {
        mActivity = activity;
        mDvrPlayer = dvrPlayer;
        mDvrWatchedPositionManager =
                TvApplication.getSingletons(activity).getDvrWatchedPositionManager();
        mChannelDataManager = TvApplication.getSingletons(activity).getChannelDataManager();
        mDvrPlayer.setCallback(new DvrPlayer.DvrPlayerCallback() {
            @Override
            public void onPlaybackStateChanged(int playbackState, int playbackSpeed) {
                updateMediaSessionPlaybackState();
            }

            @Override
            public void onPlaybackPositionChanged(long positionMs) {
                updateMediaSessionPlaybackState();
                if (mDvrPlayer.isPlaybackPrepared()) {
                    mDvrWatchedPositionManager
                            .setWatchedPosition(mDvrPlayer.getProgram().getId(), positionMs);
                }
            }

            @Override
            public void onPlaybackEnded() {
                // TODO: Deal with watched over recordings in DVR library
                RecordedProgram nextEpisode =
                        overlayFragment.getNextEpisode(mDvrPlayer.getProgram());
                if (nextEpisode == null) {
                    mDvrPlayer.reset();
                    mActivity.finish();
                } else {
                    Intent intent = new Intent(activity, DvrPlaybackActivity.class);
                    intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, nextEpisode.getId());
                    mActivity.startActivity(intent);
                }
            }
        });
        initializeMediaSession(mediaSessionTag);
    }

    /**
     * Stops DVR player and release media session.
     */
    public void release() {
        if (mDvrPlayer != null) {
            mDvrPlayer.reset();
        }
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
    }

    /**
     * Updates media session's playback state and speed.
     */
    public void updateMediaSessionPlaybackState() {
        mMediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(mDvrPlayer.getPlaybackState(), mDvrPlayer.getPlaybackPosition(),
                        mSpeedLevel).build());
    }

    /**
     * Sets the recorded program for playback.
     *
     * @param program The recorded program to play. {@code null} to reset the DVR player.
     */
    public void setupPlayback(RecordedProgram program, long seekPositionMs) {
        if (program != null) {
            mDvrPlayer.setProgram(program, seekPositionMs);
            setupMediaSession(program);
        } else {
            mDvrPlayer.reset();
            mMediaSession.setActive(false);
        }
    }

    /**
     * Returns the recorded program now playing.
     */
    public RecordedProgram getProgram() {
        return mDvrPlayer.getProgram();
    }

    /**
     * Checks if the recorded program is the same as now playing one.
     */
    public boolean isCurrentProgram(RecordedProgram program) {
        return program != null && program.equals(getProgram());
    }

    /**
     * Returns playback state.
     */
    public int getPlaybackState() {
        return mDvrPlayer.getPlaybackState();
    }

    /**
     * Returns the underlying DVR player.
     */
    public DvrPlayer getDvrPlayer() {
        return mDvrPlayer;
    }

    private void initializeMediaSession(String mediaSessionTag) {
        mMediaSession = new MediaSession(mActivity, mediaSessionTag);
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mNowPlayingCardWidth = mActivity.getResources()
                .getDimensionPixelSize(R.dimen.notif_card_img_max_width);
        mNowPlayingCardHeight = mActivity.getResources()
                .getDimensionPixelSize(R.dimen.notif_card_img_height);
        mMediaSession.setCallback(new MediaSessionCallback());
        mActivity.setMediaController(
                new MediaController(mActivity, mMediaSession.getSessionToken()));
        updateMediaSessionPlaybackState();
    }

    private void setupMediaSession(RecordedProgram program) {
        mProgramDurationMs = program.getDurationMillis();
        String cardTitleText = program.getTitle();
        if (TextUtils.isEmpty(cardTitleText)) {
            Channel channel = mChannelDataManager.getChannel(program.getChannelId());
            cardTitleText = (channel != null) ? channel.getDisplayName()
                    : mActivity.getString(R.string.no_program_information);
        }
        final MediaMetadata currentMetadata = updateMetadataTextInfo(program.getId(), cardTitleText,
                program.getDescription(), mProgramDurationMs);
        String posterArtUri = program.getPosterArtUri();
        if (posterArtUri == null) {
            posterArtUri = TvContract.buildChannelLogoUri(program.getChannelId()).toString();
        }
        updatePosterArt(program, currentMetadata, null, posterArtUri);
        mMediaSession.setActive(true);
    }

    private void updatePosterArt(RecordedProgram program, MediaMetadata currentMetadata,
            @Nullable Bitmap posterArt, @Nullable String posterArtUri) {
        if (posterArt != null) {
            updateMetadataImageInfo(program, currentMetadata, posterArt, 0);
        } else if (posterArtUri != null) {
            ImageLoader.loadBitmap(mActivity, posterArtUri, mNowPlayingCardWidth,
                    mNowPlayingCardHeight,
                    new ProgramPosterArtCallback(mActivity, program, currentMetadata));
        } else {
            updateMetadataImageInfo(program, currentMetadata, null, R.drawable.default_now_card);
        }
    }

    private class ProgramPosterArtCallback extends
            ImageLoader.ImageLoaderCallback<Activity> {
        private final RecordedProgram mRecordedProgram;
        private final MediaMetadata mCurrentMetadata;

        public ProgramPosterArtCallback(Activity activity, RecordedProgram program,
                MediaMetadata metadata) {
            super(activity);
            mRecordedProgram = program;
            mCurrentMetadata = metadata;
        }

        @Override
        public void onBitmapLoaded(Activity activity, @Nullable Bitmap posterArt) {
            if (isCurrentProgram(mRecordedProgram)) {
                updatePosterArt(mRecordedProgram, mCurrentMetadata, posterArt, null);
            }
        }
    }

    private MediaMetadata updateMetadataTextInfo(final long programId, final String title,
            final String subtitle, final long duration) {
        MediaMetadata.Builder builder = new MediaMetadata.Builder();
        builder.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(programId))
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration);
        if (subtitle != null) {
            builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
        }
        MediaMetadata metadata = builder.build();
        mMediaSession.setMetadata(metadata);
        return metadata;
    }

    private void updateMetadataImageInfo(final RecordedProgram program,
            final MediaMetadata currentMetadata, final Bitmap posterArt, final int imageResId) {
        if (mMediaSession != null && (posterArt != null || imageResId != 0)) {
            MediaMetadata.Builder builder = new MediaMetadata.Builder(currentMetadata);
            if (posterArt != null) {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ART, posterArt);
                mMediaSession.setMetadata(builder.build());
            } else {
                new AsyncTask<Void, Void, Bitmap>() {
                    @Override
                    protected Bitmap doInBackground(Void... arg0) {
                        return BitmapFactory.decodeResource(mActivity.getResources(), imageResId);
                    }

                    @Override
                    protected void onPostExecute(Bitmap programPosterArt) {
                        if (mMediaSession != null && programPosterArt != null
                                && isCurrentProgram(program)) {
                            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, programPosterArt);
                            mMediaSession.setMetadata(builder.build());
                        }
                    }
                }.execute();
            }
        }
    }

    // An event was triggered by MediaController.TransportControls and must be handled here.
    // Here we update the media itself to act on the event that was triggered.
    private class MediaSessionCallback extends MediaSession.Callback {
        @Override
        public void onPrepare() {
            if (!mDvrPlayer.isPlaybackPrepared()) {
                mDvrPlayer.prepare(true);
            }
        }

        @Override
        public void onPlay() {
            if (mDvrPlayer.isPlaybackPrepared()) {
                mDvrPlayer.play();
            }
        }

        @Override
        public void onPause() {
            if (mDvrPlayer.isPlaybackPrepared()) {
                mDvrPlayer.pause();
            }
        }

        @Override
        public void onFastForward() {
            if (!mDvrPlayer.isPlaybackPrepared()) {
                return;
            }
            if (mDvrPlayer.getPlaybackState() == PlaybackState.STATE_FAST_FORWARDING) {
                if (mSpeedLevel < TimeShiftUtils.MAX_SPEED_LEVEL) {
                    mSpeedLevel++;
                } else {
                    return;
                }
            } else {
                mSpeedLevel = 0;
            }
            mDvrPlayer.fastForward(
                    TimeShiftUtils.getPlaybackSpeed(mSpeedLevel, mProgramDurationMs));
        }

        @Override
        public void onRewind() {
            if (!mDvrPlayer.isPlaybackPrepared()) {
                return;
            }
            if (mDvrPlayer.getPlaybackState() == PlaybackState.STATE_REWINDING) {
                if (mSpeedLevel < TimeShiftUtils.MAX_SPEED_LEVEL) {
                    mSpeedLevel++;
                } else {
                    return;
                }
            } else {
                mSpeedLevel = 0;
            }
            mDvrPlayer.rewind(TimeShiftUtils.getPlaybackSpeed(mSpeedLevel, mProgramDurationMs));
        }

        @Override
        public void onSeekTo(long positionMs) {
            if (mDvrPlayer.isPlaybackPrepared()) {
                mDvrPlayer.seekTo(positionMs);
            }
        }
    }
}
