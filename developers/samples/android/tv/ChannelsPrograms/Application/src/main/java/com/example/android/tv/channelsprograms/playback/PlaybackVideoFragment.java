/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.tv.channelsprograms.playback;

import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.VideoFragment;
import android.support.v17.leanback.app.VideoFragmentGlueHost;
import android.support.v17.leanback.media.MediaPlayerAdapter;
import android.support.v17.leanback.media.PlaybackGlue;
import android.support.v17.leanback.widget.PlaybackControlsRow;
import android.util.Log;

import com.example.android.tv.channelsprograms.model.Movie;

/** Handles video playback with media controls. */
public class PlaybackVideoFragment extends VideoFragment {

    private static final String TAG = "VideoFragment";

    private SimplePlaybackTransportControlGlue<MediaPlayerAdapter> mMediaPlayerGlue;

    private long mChannelId;
    private long mStartingPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mChannelId = getActivity().getIntent().getLongExtra(PlaybackActivity.EXTRA_CHANNEL_ID, -1L);
        mStartingPosition =
                getActivity().getIntent().getLongExtra(PlaybackActivity.EXTRA_POSITION, -1L);
        final Movie movie =
                (Movie)
                        getActivity()
                                .getIntent()
                                .getSerializableExtra(PlaybackActivity.EXTRA_MOVIE);

        VideoFragmentGlueHost glueHost = new VideoFragmentGlueHost(PlaybackVideoFragment.this);

        mMediaPlayerGlue =
                new SimplePlaybackTransportControlGlue<>(
                        getActivity(), new MediaPlayerAdapter(getActivity()));
        mMediaPlayerGlue.setHost(glueHost);
        mMediaPlayerGlue.setRepeatMode(PlaybackControlsRow.RepeatAction.NONE);
        mMediaPlayerGlue.addPlayerCallback(
                new PlaybackGlue.PlayerCallback() {
                    WatchNextAdapter watchNextAdapter = new WatchNextAdapter();

                    @Override
                    public void onPlayStateChanged(PlaybackGlue glue) {
                        super.onPlayStateChanged(glue);
                        // TODO: step 10 update progress.
                        long position = mMediaPlayerGlue.getCurrentPosition();
                        long duration = mMediaPlayerGlue.getDuration();
                        watchNextAdapter.updateProgress(
                                getContext(), mChannelId, movie, position, duration);
                    }

                    @Override
                    public void onPlayCompleted(PlaybackGlue glue) {
                        super.onPlayCompleted(glue);
                        // TODO: step 11 remove watch next.
                        watchNextAdapter.removeFromWatchNext(
                                getContext(), mChannelId, movie.getId());
                    }
                });

        mMediaPlayerGlue.setTitle(movie.getTitle());
        mMediaPlayerGlue.setSubtitle(movie.getDescription());
        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(movie.getVideoUrl()));
        seekToStartingPosition();
        playWhenReady(mMediaPlayerGlue);
    }

    @Override
    public void onPause() {
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        super.onPause();
    }

    private void playWhenReady(final PlaybackGlue glue) {
        if (glue.isPrepared()) {
            glue.play();
        } else {
            glue.addPlayerCallback(
                    new PlaybackGlue.PlayerCallback() {
                        @Override
                        public void onPreparedStateChanged(PlaybackGlue glue) {
                            if (glue.isPrepared()) {
                                glue.removePlayerCallback(this);
                                glue.play();
                            }
                        }
                    });
        }
    }

    private void seekToStartingPosition() {
        // Skip ahead if given a starting position.
        if (mStartingPosition > -1L) {
            if (mMediaPlayerGlue.isPrepared()) {
                Log.d("VideoFragment", "Is prepped, seeking to " + mStartingPosition);
                mMediaPlayerGlue.seekTo(mStartingPosition);
            } else {
                mMediaPlayerGlue.addPlayerCallback(
                        new PlaybackGlue.PlayerCallback() {
                            @Override
                            public void onPreparedStateChanged(PlaybackGlue glue) {
                                super.onPreparedStateChanged(glue);
                                if (mMediaPlayerGlue.isPrepared()) {
                                    mMediaPlayerGlue.removePlayerCallback(this);
                                    Log.d(TAG, "In callback, seeking to " + mStartingPosition);
                                    mMediaPlayerGlue.seekTo(mStartingPosition);
                                }
                            }
                        });
            }
        }
    }
}
