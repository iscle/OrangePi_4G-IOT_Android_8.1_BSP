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

import android.media.PlaybackParams;
import android.media.session.PlaybackState;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.dvr.data.RecordedProgram;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class DvrPlayer {
    private static final String TAG = "DvrPlayer";
    private static final boolean DEBUG = false;

    /**
     * The max rewinding speed supported by DVR player.
     */
    public static final int MAX_REWIND_SPEED = 256;
    /**
     * The max fast-forwarding speed supported by DVR player.
     */
    public static final int MAX_FAST_FORWARD_SPEED = 256;

    private static final long SEEK_POSITION_MARGIN_MS = TimeUnit.SECONDS.toMillis(2);
    private static final long REWIND_POSITION_MARGIN_MS = 32;  // Workaround value. b/29994826

    private RecordedProgram mProgram;
    private long mInitialSeekPositionMs;
    private final TvView mTvView;
    private DvrPlayerCallback mCallback;
    private OnAspectRatioChangedListener mOnAspectRatioChangedListener;
    private OnContentBlockedListener mOnContentBlockedListener;
    private OnTracksAvailabilityChangedListener mOnTracksAvailabilityChangedListener;
    private OnTrackSelectedListener mOnAudioTrackSelectedListener;
    private OnTrackSelectedListener mOnSubtitleTrackSelectedListener;
    private String mSelectedAudioTrackId;
    private String mSelectedSubtitleTrackId;
    private float mAspectRatio = Float.NaN;
    private int mPlaybackState = PlaybackState.STATE_NONE;
    private long mTimeShiftCurrentPositionMs;
    private boolean mPauseOnPrepared;
    private boolean mHasClosedCaption;
    private boolean mHasMultiAudio;
    private final PlaybackParams mPlaybackParams = new PlaybackParams();
    private final DvrPlayerCallback mEmptyCallback = new DvrPlayerCallback();
    private long mStartPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
    private boolean mTimeShiftPlayAvailable;

    public static class DvrPlayerCallback {
        /**
         * Called when the playback position is changed. The normal updating frequency is
         * around 1 sec., which is restricted to the implementation of
         * {@link android.media.tv.TvInputService}.
         */
        public void onPlaybackPositionChanged(long positionMs) { }
        /**
         * Called when the playback state or the playback speed is changed.
         */
        public void onPlaybackStateChanged(int playbackState, int playbackSpeed) { }
        /**
         * Called when the playback toward the end.
         */
        public void onPlaybackEnded() { }
    }

    public interface OnAspectRatioChangedListener {
        /**
         * Called when the Video's aspect ratio is changed.
         *
         * @param videoAspectRatio The aspect ratio of video. 0 stands for unknown ratios.
         *                         Listeners should handle it carefully.
         */
        void onAspectRatioChanged(float videoAspectRatio);
    }

    public interface OnContentBlockedListener {
        /**
         * Called when the Video's aspect ratio is changed.
         */
        void onContentBlocked(TvContentRating rating);
    }

    public interface OnTracksAvailabilityChangedListener {
        /**
         * Called when the Video's subtitle or audio tracks are changed.
         */
        void onTracksAvailabilityChanged(boolean hasClosedCaption, boolean hasMultiAudio);
    }

    public interface OnTrackSelectedListener {
        /**
         * Called when certain subtitle or audio track is selected.
         */
        void onTrackSelected(String selectedTrackId);
    }

    public DvrPlayer(TvView tvView) {
        mTvView = tvView;
        mTvView.setCaptionEnabled(true);
        mPlaybackParams.setSpeed(1.0f);
        setTvViewCallbacks();
        setCallback(null);
    }

    /**
     * Prepares playback.
     *
     * @param doPlay indicates DVR player do or do not start playback after media is prepared.
     */
    public void prepare(boolean doPlay) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "prepare()");
        if (mProgram == null) {
            throw new IllegalStateException("Recorded program not set");
        } else if (mPlaybackState != PlaybackState.STATE_NONE) {
            throw new IllegalStateException("Playback is already prepared");
        }
        mTvView.timeShiftPlay(mProgram.getInputId(), mProgram.getUri());
        mPlaybackState = PlaybackState.STATE_CONNECTING;
        mPauseOnPrepared = !doPlay;
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Resumes playback.
     */
    public void play() throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "play()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or video not ready yet");
        }
        switch (mPlaybackState) {
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                setPlaybackSpeed(1);
                break;
            default:
                mTvView.timeShiftResume();
        }
        mPlaybackState = PlaybackState.STATE_PLAYING;
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Pauses playback.
     */
    public void pause() throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "pause()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        switch (mPlaybackState) {
            case PlaybackState.STATE_FAST_FORWARDING:
            case PlaybackState.STATE_REWINDING:
                setPlaybackSpeed(1);
                // falls through
            case PlaybackState.STATE_PLAYING:
                mTvView.timeShiftPause();
                mPlaybackState = PlaybackState.STATE_PAUSED;
                break;
            default:
                break;
        }
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }

    /**
     * Fast-forwards playback with the given speed. If the given speed is larger than
     * {@value #MAX_FAST_FORWARD_SPEED}, uses {@value #MAX_FAST_FORWARD_SPEED}.
     */
    public void fastForward(int speed) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "fastForward()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed cannot be negative or 0");
        }
        if (mTimeShiftCurrentPositionMs >= mProgram.getDurationMillis() - SEEK_POSITION_MARGIN_MS) {
            return;
        }
        speed = Math.min(speed, MAX_FAST_FORWARD_SPEED);
        if (DEBUG) Log.d(TAG, "Let's play with speed: " + speed);
        setPlaybackSpeed(speed);
        mPlaybackState = PlaybackState.STATE_FAST_FORWARDING;
        mCallback.onPlaybackStateChanged(mPlaybackState, speed);
    }

    /**
     * Rewinds playback with the given speed. If the given speed is larger than
     * {@value #MAX_REWIND_SPEED}, uses {@value #MAX_REWIND_SPEED}.
     */
    public void rewind(int speed) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "rewind()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed cannot be negative or 0");
        }
        if (mTimeShiftCurrentPositionMs <= REWIND_POSITION_MARGIN_MS) {
            return;
        }
        speed = Math.min(speed, MAX_REWIND_SPEED);
        if (DEBUG) Log.d(TAG, "Let's play with speed: " + speed);
        setPlaybackSpeed(-speed);
        mPlaybackState = PlaybackState.STATE_REWINDING;
        mCallback.onPlaybackStateChanged(mPlaybackState, speed);
    }

    /**
     * Seeks playback to the specified position.
     */
    public void seekTo(long positionMs) throws IllegalStateException {
        if (DEBUG) Log.d(TAG, "seekTo()");
        if (!isPlaybackPrepared()) {
            throw new IllegalStateException("Recorded program not set or playback not started yet");
        }
        if (mProgram == null || mPlaybackState == PlaybackState.STATE_NONE) {
            return;
        }
        positionMs = getRealSeekPosition(positionMs, SEEK_POSITION_MARGIN_MS);
        if (DEBUG) Log.d(TAG, "Now: " + getPlaybackPosition() + ", shift to: " + positionMs);
        mTvView.timeShiftSeekTo(positionMs + mStartPositionMs);
        if (mPlaybackState == PlaybackState.STATE_FAST_FORWARDING ||
                mPlaybackState == PlaybackState.STATE_REWINDING) {
            mPlaybackState = PlaybackState.STATE_PLAYING;
            mTvView.timeShiftResume();
            mCallback.onPlaybackStateChanged(mPlaybackState, 1);
        }
    }

    /**
     * Resets playback.
     */
    public void reset() {
        if (DEBUG) Log.d(TAG, "reset()");
        mCallback.onPlaybackStateChanged(PlaybackState.STATE_NONE, 1);
        mPlaybackState = PlaybackState.STATE_NONE;
        mTvView.reset();
        mTimeShiftPlayAvailable = false;
        mStartPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
        mTimeShiftCurrentPositionMs = 0;
        mPlaybackParams.setSpeed(1.0f);
        mProgram = null;
        mSelectedAudioTrackId = null;
        mSelectedSubtitleTrackId = null;
    }

    /**
     * Sets callbacks for playback.
     */
    public void setCallback(DvrPlayerCallback callback) {
        if (callback != null) {
            mCallback = callback;
        } else {
            mCallback = mEmptyCallback;
        }
    }

    /**
     * Sets the listener to aspect ratio changing.
     */
    public void setOnAspectRatioChangedListener(OnAspectRatioChangedListener listener) {
        mOnAspectRatioChangedListener = listener;
    }

    /**
     * Sets the listener to content blocking.
     */
    public void setOnContentBlockedListener(OnContentBlockedListener listener) {
        mOnContentBlockedListener = listener;
    }

    /**
     * Sets the listener to tracks changing.
     */
    public void setOnTracksAvailabilityChangedListener(
            OnTracksAvailabilityChangedListener listener) {
        mOnTracksAvailabilityChangedListener = listener;
    }

    /**
     * Sets the listener to tracks of the given type being selected.
     *
     * @param trackType should be either {@link TvTrackInfo#TYPE_AUDIO}
     *                  or {@link TvTrackInfo#TYPE_SUBTITLE}.
     */
    public void setOnTrackSelectedListener(int trackType, OnTrackSelectedListener listener) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            mOnAudioTrackSelectedListener = listener;
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            mOnSubtitleTrackSelectedListener = listener;
        }
    }

    /**
     * Gets the listener to tracks of the given type being selected.
     */
    public OnTrackSelectedListener getOnTrackSelectedListener(int trackType) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            return mOnAudioTrackSelectedListener;
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            return mOnSubtitleTrackSelectedListener;
        }
        return null;
    }

    /**
     * Sets recorded programs for playback. If the player is playing another program, stops it.
     */
    public void setProgram(RecordedProgram program, long initialSeekPositionMs) {
        if (mProgram != null && mProgram.equals(program)) {
            return;
        }
        if (mPlaybackState != PlaybackState.STATE_NONE) {
            reset();
        }
        mInitialSeekPositionMs = initialSeekPositionMs;
        mProgram = program;
    }

    /**
     * Returns the recorded program now playing.
     */
    public RecordedProgram getProgram() {
        return mProgram;
    }

    /**
     * Returns the currrent playback posistion in msecs.
     */
    public long getPlaybackPosition() {
        return mTimeShiftCurrentPositionMs;
    }

    /**
     * Returns the playback speed currently used.
     */
    public int getPlaybackSpeed() {
        return (int) mPlaybackParams.getSpeed();
    }

    /**
     * Returns the playback state defined in {@link android.media.session.PlaybackState}.
     */
    public int getPlaybackState() {
        return mPlaybackState;
    }

    /**
     * Returns the subtitle tracks of the current playback.
     */
    public ArrayList<TvTrackInfo> getSubtitleTracks() {
        return new ArrayList<>(mTvView.getTracks(TvTrackInfo.TYPE_SUBTITLE));
    }

    /**
     * Returns the audio tracks of the current playback.
     */
    public ArrayList<TvTrackInfo> getAudioTracks() {
        return new ArrayList<>(mTvView.getTracks(TvTrackInfo.TYPE_AUDIO));
    }

    /**
     * Returns the ID of the selected track of the given type.
     */
    public String getSelectedTrackId(int trackType) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            return mSelectedAudioTrackId;
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            return mSelectedSubtitleTrackId;
        }
        return null;
    }

    /**
     * Returns if playback of the recorded program is started.
     */
    public boolean isPlaybackPrepared() {
        return mPlaybackState != PlaybackState.STATE_NONE
                && mPlaybackState != PlaybackState.STATE_CONNECTING;
    }

    /**
     * Selects the given track.
     *
     * @return ID of the selected track.
     */
    String selectTrack(int trackType, TvTrackInfo selectedTrack) {
        String oldSelectedTrackId = getSelectedTrackId(trackType);
        String newSelectedTrackId = selectedTrack == null ? null : selectedTrack.getId();
        if (!TextUtils.equals(oldSelectedTrackId, newSelectedTrackId)) {
            if (selectedTrack == null) {
                mTvView.selectTrack(trackType, null);
                return null;
            } else {
                List<TvTrackInfo> tracks = mTvView.getTracks(trackType);
                if (tracks != null && tracks.contains(selectedTrack)) {
                    mTvView.selectTrack(trackType, newSelectedTrackId);
                    return newSelectedTrackId;
                } else if (trackType == TvTrackInfo.TYPE_SUBTITLE && oldSelectedTrackId != null) {
                    // Track not found, disabled closed caption.
                    mTvView.selectTrack(trackType, null);
                    return null;
                }
            }
        }
        return oldSelectedTrackId;
    }

    private void setSelectedTrackId(int trackType, String trackId) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            mSelectedAudioTrackId = trackId;
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            mSelectedSubtitleTrackId = trackId;
        }
    }

    private void setPlaybackSpeed(int speed) {
        mPlaybackParams.setSpeed(speed);
        mTvView.timeShiftSetPlaybackParams(mPlaybackParams);
    }

    private long getRealSeekPosition(long seekPositionMs, long endMarginMs) {
        return Math.max(0, Math.min(seekPositionMs, mProgram.getDurationMillis() - endMarginMs));
    }

    private void setTvViewCallbacks() {
        mTvView.setTimeShiftPositionCallback(new TvView.TimeShiftPositionCallback() {
            @Override
            public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStartPositionChanged:" + timeMs);
                mStartPositionMs = timeMs;
                if (mTimeShiftPlayAvailable) {
                    resumeToWatchedPositionIfNeeded();
                }
            }

            @Override
            public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
                if (DEBUG) Log.d(TAG, "onTimeShiftCurrentPositionChanged: " + timeMs);
                if (!mTimeShiftPlayAvailable) {
                    // Workaround of b/31436263
                    return;
                }
                // Workaround of b/32211561, TIF won't report start position when TIS report
                // its start position as 0. In that case, we have to do the prework of playback
                // on the first time we get current position, and the start position should be 0
                // at that time.
                if (mStartPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                    mStartPositionMs = 0;
                    resumeToWatchedPositionIfNeeded();
                }
                timeMs -= mStartPositionMs;
                if (mPlaybackState == PlaybackState.STATE_REWINDING
                        && timeMs <= REWIND_POSITION_MARGIN_MS) {
                    play();
                } else {
                    mTimeShiftCurrentPositionMs = getRealSeekPosition(timeMs, 0);
                    mCallback.onPlaybackPositionChanged(mTimeShiftCurrentPositionMs);
                    if (timeMs >= mProgram.getDurationMillis()) {
                        pause();
                        mCallback.onPlaybackEnded();
                    }
                }
            }
        });
        mTvView.setCallback(new TvView.TvInputCallback() {
            @Override
            public void onTimeShiftStatusChanged(String inputId, int status) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStatusChanged:" + status);
                if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE
                        && mPlaybackState == PlaybackState.STATE_CONNECTING) {
                    mTimeShiftPlayAvailable = true;
                    if (mStartPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                        // onTimeShiftStatusChanged is sometimes called after
                        // onTimeShiftStartPositionChanged is called. In this case,
                        // resumeToWatchedPositionIfNeeded needs to be called here.
                        resumeToWatchedPositionIfNeeded();
                    }
                }
            }

            @Override
            public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
                boolean hasClosedCaption =
                        !mTvView.getTracks(TvTrackInfo.TYPE_SUBTITLE).isEmpty();
                boolean hasMultiAudio = mTvView.getTracks(TvTrackInfo.TYPE_AUDIO).size() > 1;
                if ((hasClosedCaption != mHasClosedCaption || hasMultiAudio != mHasMultiAudio)
                        && mOnTracksAvailabilityChangedListener != null) {
                    mOnTracksAvailabilityChangedListener
                            .onTracksAvailabilityChanged(hasClosedCaption, hasMultiAudio);
                }
                mHasClosedCaption = hasClosedCaption;
                mHasMultiAudio = hasMultiAudio;
            }

            @Override
            public void onTrackSelected(String inputId, int type, String trackId) {
                if (type == TvTrackInfo.TYPE_AUDIO || type == TvTrackInfo.TYPE_SUBTITLE) {
                    setSelectedTrackId(type, trackId);
                    OnTrackSelectedListener listener = getOnTrackSelectedListener(type);
                    if (listener != null) {
                        listener.onTrackSelected(trackId);
                    }
                } else if (type == TvTrackInfo.TYPE_VIDEO && trackId != null
                        && mOnAspectRatioChangedListener != null) {
                    List<TvTrackInfo> trackInfos = mTvView.getTracks(TvTrackInfo.TYPE_VIDEO);
                    if (trackInfos != null) {
                        for (TvTrackInfo trackInfo : trackInfos) {
                            if (trackInfo.getId().equals(trackId)) {
                                float videoAspectRatio;
                                int videoWidth = trackInfo.getVideoWidth();
                                int videoHeight = trackInfo.getVideoHeight();
                                if (videoWidth > 0 && videoHeight > 0) {
                                    videoAspectRatio = trackInfo.getVideoPixelAspectRatio()
                                            * trackInfo.getVideoWidth() / trackInfo.getVideoHeight();
                                } else {
                                    // Aspect ratio is unknown. Pass the message to listeners.
                                    videoAspectRatio = 0;
                                }
                                if (DEBUG) Log.d(TAG, "Aspect Ratio: " + videoAspectRatio);
                                if (mAspectRatio != videoAspectRatio || videoAspectRatio == 0) {
                                    mOnAspectRatioChangedListener
                                            .onAspectRatioChanged(videoAspectRatio);
                                    mAspectRatio = videoAspectRatio;
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onContentBlocked(String inputId, TvContentRating rating) {
                if (mOnContentBlockedListener != null) {
                    mOnContentBlockedListener.onContentBlocked(rating);
                }
            }
        });
    }

    private void resumeToWatchedPositionIfNeeded() {
        if (mInitialSeekPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
            mTvView.timeShiftSeekTo(getRealSeekPosition(mInitialSeekPositionMs,
                    SEEK_POSITION_MARGIN_MS) + mStartPositionMs);
            mInitialSeekPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME;
        }
        if (mPauseOnPrepared) {
            mTvView.timeShiftPause();
            mPlaybackState = PlaybackState.STATE_PAUSED;
            mPauseOnPrepared = false;
        } else {
            mTvView.timeShiftResume();
            mPlaybackState = PlaybackState.STATE_PLAYING;
        }
        mCallback.onPlaybackStateChanged(mPlaybackState, 1);
    }
}