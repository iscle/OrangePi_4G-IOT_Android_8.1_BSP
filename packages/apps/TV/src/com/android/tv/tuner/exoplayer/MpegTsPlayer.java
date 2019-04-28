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

package com.android.tv.tuner.exoplayer;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec.CryptoException;
import android.media.PlaybackParams;
import android.os.Handler;
import android.support.annotation.IntDef;
import android.view.Surface;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer.DecoderInitializationException;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.upstream.DataSource;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.data.Cea708Data;
import com.android.tv.tuner.data.Cea708Data.CaptionEvent;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.exoplayer.audio.MpegTsDefaultAudioTrackRenderer;
import com.android.tv.tuner.exoplayer.audio.MpegTsMediaCodecAudioTrackRenderer;
import com.android.tv.tuner.source.TsDataSource;
import com.android.tv.tuner.source.TsDataSourceManager;
import com.android.tv.tuner.tvinput.EventDetector;
import com.android.tv.tuner.tvinput.TunerDebug;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** MPEG-2 TS stream player implementation using ExoPlayer. */
public class MpegTsPlayer
        implements ExoPlayer.Listener,
                MediaCodecVideoTrackRenderer.EventListener,
                MpegTsDefaultAudioTrackRenderer.EventListener,
                MpegTsMediaCodecAudioTrackRenderer.Ac3EventListener {
    private int mCaptionServiceNumber = Cea708Data.EMPTY_SERVICE_NUMBER;

    /**
     * Interface definition for building specific track renderers.
     */
    public interface RendererBuilder {
        void buildRenderers(MpegTsPlayer mpegTsPlayer, DataSource dataSource,
                boolean hasSoftwareAudioDecoder, RendererBuilderCallback callback);
    }

    /**
     * Interface definition for {@link RendererBuilder#buildRenderers} to notify the result.
     */
    public interface RendererBuilderCallback {
        void onRenderers(String[][] trackNames, TrackRenderer[] renderers);
        void onRenderersError(Exception e);
    }

    /**
     * Interface definition for a callback to be notified of changes in player state.
     */
    public interface Listener {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(Exception e);
        void onVideoSizeChanged(int width, int height,
                float pixelWidthHeightRatio);
        void onDrawnToSurface(MpegTsPlayer player, Surface surface);
        void onAudioUnplayable();
        void onSmoothTrickplayForceStopped();
    }

    /**
     * Interface definition for a callback to be notified of changes on video display.
     */
    public interface VideoEventListener {
        /**
         * Notifies the caption event.
         */
        void onEmitCaptionEvent(CaptionEvent event);

        /**
         * Notifies clearing up whole closed caption event.
         */
        void onClearCaptionEvent();

        /**
         * Notifies the discovered caption service number.
         */
        void onDiscoverCaptionServiceNumber(int serviceNumber);
    }

    public static final int RENDERER_COUNT = 3;
    public static final int MIN_BUFFER_MS = 0;
    public static final int MIN_REBUFFER_MS = 500;

    @IntDef({TRACK_TYPE_VIDEO, TRACK_TYPE_AUDIO, TRACK_TYPE_TEXT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TrackType {}
    public static final int TRACK_TYPE_VIDEO = 0;
    public static final int TRACK_TYPE_AUDIO = 1;
    public static final int TRACK_TYPE_TEXT = 2;

    @IntDef({RENDERER_BUILDING_STATE_IDLE, RENDERER_BUILDING_STATE_BUILDING,
        RENDERER_BUILDING_STATE_BUILT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RendererBuildingState {}
    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    private static final float MAX_SMOOTH_TRICKPLAY_SPEED = 9.0f;
    private static final float MIN_SMOOTH_TRICKPLAY_SPEED = 0.1f;

    private final RendererBuilder mRendererBuilder;
    private final ExoPlayer mPlayer;
    private final Handler mMainHandler;
    private final AudioCapabilities mAudioCapabilities;
    private final TsDataSourceManager mSourceManager;

    private Listener mListener;
    @RendererBuildingState private int mRendererBuildingState;

    private Surface mSurface;
    private TsDataSource mDataSource;
    private InternalRendererBuilderCallback mBuilderCallback;
    private TrackRenderer mVideoRenderer;
    private TrackRenderer mAudioRenderer;
    private Cea708TextTrackRenderer mTextRenderer;
    private final Cea708TextTrackRenderer.CcListener mCcListener;
    private VideoEventListener mVideoEventListener;
    private boolean mTrickplayRunning;
    private float mVolume;

    /**
     * Creates MPEG2-TS stream player.
     *
     * @param rendererBuilder the builder of track renderers
     * @param handler the handler for the playback events in track renderers
     * @param sourceManager the manager for {@link DataSource}
     * @param capabilities the {@link AudioCapabilities} of the current device
     * @param listener the listener for playback state changes
     */
    public MpegTsPlayer(RendererBuilder rendererBuilder, Handler handler,
            TsDataSourceManager sourceManager, AudioCapabilities capabilities,
            Listener listener) {
        mRendererBuilder = rendererBuilder;
        mPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT, MIN_BUFFER_MS, MIN_REBUFFER_MS);
        mPlayer.addListener(this);
        mMainHandler = handler;
        mAudioCapabilities = capabilities;
        mRendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        mCcListener = new MpegTsCcListener();
        mSourceManager = sourceManager;
        mListener = listener;
    }

    /**
     * Sets the video event listener.
     *
     * @param videoEventListener the listener for video events
     */
    public void setVideoEventListener(VideoEventListener videoEventListener) {
        mVideoEventListener = videoEventListener;
    }

    /**
     * Sets the closed caption service number.
     *
     * @param captionServiceNumber the service number of CEA-708 closed caption
     */
    public void setCaptionServiceNumber(int captionServiceNumber) {
        mCaptionServiceNumber = captionServiceNumber;
        if (mTextRenderer != null) {
            mPlayer.sendMessage(mTextRenderer,
                    Cea708TextTrackRenderer.MSG_SERVICE_NUMBER, mCaptionServiceNumber);
        }
    }

    /**
     * Sets the surface for the player.
     *
     * @param surface the {@link Surface} to render video
     */
    public void setSurface(Surface surface) {
        mSurface = surface;
        pushSurface(false);
    }

    /**
     * Returns the current surface of the player.
     */
    public Surface getSurface() {
        return mSurface;
    }

    /**
     * Clears the surface and waits until the surface is being cleaned.
     */
    public void blockingClearSurface() {
        mSurface = null;
        pushSurface(true);
    }

    /**
     * Creates renderers and {@link DataSource} and initializes player.
     * @param context a {@link Context} instance
     * @param channel to play
     * @param hasSoftwareAudioDecoder {@code true} if there is connected software decoder
     * @param eventListener for program information which will be scanned from MPEG2-TS stream
     * @return true when everything is created and initialized well, false otherwise
     */
    public boolean prepare(Context context, TunerChannel channel, boolean hasSoftwareAudioDecoder,
            EventDetector.EventListener eventListener) {
        TsDataSource source = null;
        if (channel != null) {
            source = mSourceManager.createDataSource(context, channel, eventListener);
            if (source == null) {
                return false;
            }
        }
        mDataSource = source;
        if (mRendererBuildingState == RENDERER_BUILDING_STATE_BUILT) {
            mPlayer.stop();
        }
        if (mBuilderCallback != null) {
            mBuilderCallback.cancel();
        }
        mRendererBuildingState = RENDERER_BUILDING_STATE_BUILDING;
        mBuilderCallback = new InternalRendererBuilderCallback();
        mRendererBuilder.buildRenderers(this, source, hasSoftwareAudioDecoder, mBuilderCallback);
        return true;
    }

    /**
     * Returns {@link TsDataSource} which provides MPEG2-TS stream.
     */
    public TsDataSource getDataSource() {
        return mDataSource;
    }

    private void onRenderers(TrackRenderer[] renderers) {
        mBuilderCallback = null;
        for (int i = 0; i < RENDERER_COUNT; i++) {
            if (renderers[i] == null) {
                // Convert a null renderer to a dummy renderer.
                renderers[i] = new DummyTrackRenderer();
            }
        }
        mVideoRenderer = renderers[TRACK_TYPE_VIDEO];
        mAudioRenderer = renderers[TRACK_TYPE_AUDIO];
        mTextRenderer = (Cea708TextTrackRenderer) renderers[TRACK_TYPE_TEXT];
        mTextRenderer.setCcListener(mCcListener);
        mPlayer.sendMessage(
                mTextRenderer, Cea708TextTrackRenderer.MSG_SERVICE_NUMBER, mCaptionServiceNumber);
        mRendererBuildingState = RENDERER_BUILDING_STATE_BUILT;
        pushSurface(false);
        mPlayer.prepare(renderers);
        pushTrackSelection(TRACK_TYPE_VIDEO, true);
        pushTrackSelection(TRACK_TYPE_AUDIO, true);
        pushTrackSelection(TRACK_TYPE_TEXT, true);
    }

    private void onRenderersError(Exception e) {
        mBuilderCallback = null;
        mRendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        if (mListener != null) {
            mListener.onError(e);
        }
    }

    /**
     * Sets the player state to pause or play.
     *
     * @param playWhenReady sets the player state to being ready to play when {@code true},
     *                      sets the player state to being paused when {@code false}
     *
     */
    public void setPlayWhenReady(boolean playWhenReady) {
        mPlayer.setPlayWhenReady(playWhenReady);
        stopSmoothTrickplay(false);
    }

    /**
     * Returns true, if trickplay is supported.
     */
    public boolean supportSmoothTrickPlay(float playbackSpeed) {
        return playbackSpeed > MIN_SMOOTH_TRICKPLAY_SPEED
                && playbackSpeed < MAX_SMOOTH_TRICKPLAY_SPEED;
    }

    /**
     * Starts trickplay. It'll be reset, if {@link #seekTo} or {@link #setPlayWhenReady} is called.
     */
    public void startSmoothTrickplay(PlaybackParams playbackParams) {
        SoftPreconditions.checkState(supportSmoothTrickPlay(playbackParams.getSpeed()));
        mPlayer.setPlayWhenReady(true);
        mTrickplayRunning = true;
        if (mAudioRenderer instanceof MpegTsDefaultAudioTrackRenderer) {
            mPlayer.sendMessage(
                    mAudioRenderer,
                    MpegTsDefaultAudioTrackRenderer.MSG_SET_PLAYBACK_SPEED,
                    playbackParams.getSpeed());
        } else {
            mPlayer.sendMessage(mAudioRenderer,
                    MediaCodecAudioTrackRenderer.MSG_SET_PLAYBACK_PARAMS,
                    playbackParams);
        }
    }

    private void stopSmoothTrickplay(boolean calledBySeek) {
        if (mTrickplayRunning) {
            mTrickplayRunning = false;
            if (mAudioRenderer instanceof MpegTsDefaultAudioTrackRenderer) {
                mPlayer.sendMessage(
                        mAudioRenderer, MpegTsDefaultAudioTrackRenderer.MSG_SET_PLAYBACK_SPEED,
                        1.0f);
            } else {
                mPlayer.sendMessage(mAudioRenderer,
                        MediaCodecAudioTrackRenderer.MSG_SET_PLAYBACK_PARAMS,
                        new PlaybackParams().setSpeed(1.0f));
            }
            if (!calledBySeek) {
                mPlayer.seekTo(mPlayer.getCurrentPosition());
            }
        }
    }

    /**
     * Seeks to the specified position of the current playback.
     *
     * @param positionMs the specified position in milli seconds.
     */
    public void seekTo(long positionMs) {
        mPlayer.seekTo(positionMs);
        stopSmoothTrickplay(true);
    }

    /**
     * Releases the player.
     */
    public void release() {
        if (mDataSource != null) {
            mSourceManager.releaseDataSource(mDataSource);
            mDataSource = null;
        }
        if (mBuilderCallback != null) {
            mBuilderCallback.cancel();
            mBuilderCallback = null;
        }
        mRendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        mSurface = null;
        mListener = null;
        mPlayer.release();
    }

    /**
     * Returns the current status of the player.
     */
     public int getPlaybackState() {
        if (mRendererBuildingState == RENDERER_BUILDING_STATE_BUILDING) {
            return ExoPlayer.STATE_PREPARING;
        }
        return mPlayer.getPlaybackState();
    }

    /**
     * Returns {@code true} when the player is prepared to play, {@code false} otherwise.
     */
    public boolean isPrepared()  {
        int state = getPlaybackState();
        return state == ExoPlayer.STATE_READY || state == ExoPlayer.STATE_BUFFERING;
    }

    /**
     * Returns {@code true} when the player is being ready to play, {@code false} otherwise.
     */
    public boolean isPlaying() {
        int state = getPlaybackState();
        return (state == ExoPlayer.STATE_READY || state == ExoPlayer.STATE_BUFFERING)
                && mPlayer.getPlayWhenReady();
    }

    /**
     * Returns {@code true} when the player is buffering, {@code false} otherwise.
     */
    public boolean isBuffering() {
        return getPlaybackState() == ExoPlayer.STATE_BUFFERING;
    }

    /**
     * Returns the current position of the playback in milli seconds.
     */
    public long getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    /**
     * Returns the total duration of the playback.
     */
    public long getDuration() {
        return mPlayer.getDuration();
    }

    /**
     * Returns {@code true} when the player is being ready to play,
     * {@code false} when the player is paused.
     */
    public boolean getPlayWhenReady() {
        return mPlayer.getPlayWhenReady();
    }

    /**
     * Sets the volume of the audio.
     *
     * @param volume see also {@link AudioTrack#setVolume(float)}
     */
    public void setVolume(float volume) {
        mVolume = volume;
        if (mAudioRenderer instanceof MpegTsDefaultAudioTrackRenderer) {
            mPlayer.sendMessage(mAudioRenderer, MpegTsDefaultAudioTrackRenderer.MSG_SET_VOLUME,
                    volume);
        } else {
            mPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME,
                    volume);
        }
    }

    /**
     * Enables or disables audio and closed caption.
     *
     * @param enable enables the audio and closed caption when {@code true}, disables otherwise.
     */
    public void setAudioTrackAndClosedCaption(boolean enable) {
        if (mAudioRenderer instanceof MpegTsDefaultAudioTrackRenderer) {
            mPlayer.sendMessage(mAudioRenderer, MpegTsDefaultAudioTrackRenderer.MSG_SET_AUDIO_TRACK,
                    enable ? 1 : 0);
        } else {
            mPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME,
                    enable ? mVolume : 0.0f);
        }
        mPlayer.sendMessage(mTextRenderer, Cea708TextTrackRenderer.MSG_ENABLE_CLOSED_CAPTION,
            enable);
    }

    /**
     * Returns {@code true} when AC3 audio can be played, {@code false} otherwise.
     */
    public boolean isAc3Playable() {
        return mAudioCapabilities != null
                && mAudioCapabilities.supportsEncoding(AudioFormat.ENCODING_AC3);
    }

    /**
     * Notifies when the audio cannot be played by the current device.
     */
    public void onAudioUnplayable() {
        if (mListener != null) {
            mListener.onAudioUnplayable();
        }
    }

    /**
     * Returns {@code true} if the player has any video track, {@code false} otherwise.
     */
    public boolean hasVideo() {
        return mPlayer.getTrackCount(TRACK_TYPE_VIDEO) > 0;
    }

    /**
     * Returns {@code true} if the player has any audio trock, {@code false} otherwise.
     */
    public boolean hasAudio() {
        return mPlayer.getTrackCount(TRACK_TYPE_AUDIO) > 0;
    }

    /**
     * Returns the number of tracks exposed by the specified renderer.
     */
    public int getTrackCount(int rendererIndex) {
        return mPlayer.getTrackCount(rendererIndex);
    }

    /**
     * Selects a track for the specified renderer.
     */
    public void setSelectedTrack(int rendererIndex, int trackIndex) {
        if (trackIndex >= getTrackCount(rendererIndex)) {
            return;
        }
        mPlayer.setSelectedTrack(rendererIndex, trackIndex);
    }

    /**
     * Returns the index of the currently selected track for the specified renderer.
     *
     * @param rendererIndex The index of the renderer.
     * @return The selected track. A negative value or a value greater than or equal to the renderer's
     *     track count indicates that the renderer is disabled.
     */
    public int getSelectedTrack(int rendererIndex) {
        return mPlayer.getSelectedTrack(rendererIndex);
    }

    /**
     * Returns the format of a track.
     *
     * @param rendererIndex The index of the renderer.
     * @param trackIndex The index of the track.
     * @return The format of the track.
     */
    public MediaFormat getTrackFormat(int rendererIndex, int trackIndex) {
        return mPlayer.getTrackFormat(rendererIndex, trackIndex);
    }

    /**
     * Gets the main handler of the player.
     */
    /* package */ Handler getMainHandler() {
        return mMainHandler;
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int state) {
        if (mListener == null) {
            return;
        }
        mListener.onStateChanged(playWhenReady, state);
        if (state == ExoPlayer.STATE_READY && mPlayer.getTrackCount(TRACK_TYPE_VIDEO) > 0
                && playWhenReady) {
            MediaFormat format = mPlayer.getTrackFormat(TRACK_TYPE_VIDEO, 0);
            mListener.onVideoSizeChanged(format.width,
                    format.height, format.pixelWidthHeightRatio);
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException exception) {
        mRendererBuildingState = RENDERER_BUILDING_STATE_IDLE;
        if (mListener != null) {
            mListener.onError(exception);
        }
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
            float pixelWidthHeightRatio) {
        if (mListener != null) {
            mListener.onVideoSizeChanged(width, height, pixelWidthHeightRatio);
        }
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs,
            long initializationDurationMs) {
        // Do nothing.
    }

    @Override
    public void onDecoderInitializationError(DecoderInitializationException e) {
        // Do nothing.
    }

    @Override
    public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
        if (mListener != null) {
            mListener.onAudioUnplayable();
        }
    }

    @Override
    public void onAudioTrackWriteError(AudioTrack.WriteException e) {
        // Do nothing.
    }

    @Override
    public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs,
            long elapsedSinceLastFeedMs) {
        // Do nothing.
    }

    @Override
    public void onCryptoError(CryptoException e) {
        // Do nothing.
    }

    @Override
    public void onPlayWhenReadyCommitted() {
        // Do nothing.
    }

    @Override
    public void onDrawnToSurface(Surface surface) {
        if (mListener != null) {
            mListener.onDrawnToSurface(this, surface);
        }
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
        TunerDebug.notifyVideoFrameDrop(count, elapsed);
        if (mTrickplayRunning && mListener != null) {
            mListener.onSmoothTrickplayForceStopped();
        }
    }

    @Override
    public void onAudioTrackSetPlaybackParamsError(IllegalArgumentException e) {
        if (mTrickplayRunning && mListener != null) {
            mListener.onSmoothTrickplayForceStopped();
        }
    }

    private void pushSurface(boolean blockForSurfacePush) {
        if (mRendererBuildingState != RENDERER_BUILDING_STATE_BUILT) {
            return;
        }

        if (blockForSurfacePush) {
            mPlayer.blockingSendMessage(
                    mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
        } else {
            mPlayer.sendMessage(
                    mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, mSurface);
        }
    }

    private void pushTrackSelection(@TrackType int type, boolean allowRendererEnable) {
        if (mRendererBuildingState != RENDERER_BUILDING_STATE_BUILT) {
            return;
        }
        mPlayer.setSelectedTrack(type, allowRendererEnable ? 0 : -1);
    }

    private class MpegTsCcListener implements Cea708TextTrackRenderer.CcListener {

        @Override
        public void emitEvent(CaptionEvent captionEvent) {
            if (mVideoEventListener != null) {
                mVideoEventListener.onEmitCaptionEvent(captionEvent);
            }
        }

        @Override
        public void clearCaption() {
            if (mVideoEventListener != null) {
                mVideoEventListener.onClearCaptionEvent();
            }
        }

        @Override
        public void discoverServiceNumber(int serviceNumber) {
            if (mVideoEventListener != null) {
                mVideoEventListener.onDiscoverCaptionServiceNumber(serviceNumber);
            }
        }
    }

    private class InternalRendererBuilderCallback implements RendererBuilderCallback {
        private boolean canceled;

        public void cancel() {
            canceled = true;
        }

        @Override
        public void onRenderers(String[][] trackNames, TrackRenderer[] renderers) {
            if (!canceled) {
                MpegTsPlayer.this.onRenderers(renderers);
            }
        }

        @Override
        public void onRenderersError(Exception e) {
            if (!canceled) {
                MpegTsPlayer.this.onRenderersError(e);
            }
        }
    }
}