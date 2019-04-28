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

package com.android.tv.tuner.exoplayer.audio;

import android.media.MediaCodec;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.exoplayer.CodecCounters;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.MediaClock;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;
import com.android.tv.tuner.exoplayer.ffmpeg.FfmpegDecoderClient;
import com.android.tv.tuner.tvinput.TunerDebug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Decodes and renders DTV audio. Supports MediaCodec based decoding, passthrough playback and
 * ffmpeg based software decoding (AC3, MP2).
 */
public class MpegTsDefaultAudioTrackRenderer extends TrackRenderer implements MediaClock {
    public static final int MSG_SET_VOLUME = 10000;
    public static final int MSG_SET_AUDIO_TRACK = MSG_SET_VOLUME + 1;
    public static final int MSG_SET_PLAYBACK_SPEED = MSG_SET_VOLUME + 2;

    // ATSC/53 allows sample rate to be only 48Khz.
    // One AC3 sample has 1536 frames, and its duration is 32ms.
    public static final long AC3_SAMPLE_DURATION_US = 32000;

    // TODO: Check whether DVB broadcasting uses sample rate other than 48Khz.
    // MPEG-1 audio Layer II and III has 1152 frames per sample.
    // 1152 frames duration is 24ms when sample rate is 48Khz.
    static final long MP2_SAMPLE_DURATION_US = 24000;

    // This is around 150ms, 150ms is big enough not to under-run AudioTrack,
    // and  150ms is also small enough to fill the buffer rapidly.
    static int BUFFERED_SAMPLES_IN_AUDIOTRACK = 5;
    public static final long INITIAL_AUDIO_BUFFERING_TIME_US =
            BUFFERED_SAMPLES_IN_AUDIOTRACK * AC3_SAMPLE_DURATION_US;


    private static final String TAG = "MpegTsDefaultAudioTrac";
    private static final boolean DEBUG = false;

    /**
     * Interface definition for a callback to be notified of
     * {@link com.google.android.exoplayer.audio.AudioTrack} error.
     */
    public interface EventListener {
        void onAudioTrackInitializationError(AudioTrack.InitializationException e);
        void onAudioTrackWriteError(AudioTrack.WriteException e);
    }

    private static final int DEFAULT_INPUT_BUFFER_SIZE = 16384 * 2;
    private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 1024*1024;
    private static final int MONITOR_DURATION_MS = 1000;
    private static final int AC3_HEADER_BITRATE_OFFSET = 4;
    private static final int MP2_HEADER_BITRATE_OFFSET = 2;
    private static final int MP2_HEADER_BITRATE_MASK = 0xfc;

    // Keep this as static in order to prevent new framework AudioTrack creation
    // while old AudioTrack is being released.
    private static final AudioTrackWrapper AUDIO_TRACK = new AudioTrackWrapper();
    private static final long KEEP_ALIVE_AFTER_EOS_DURATION_MS = 3000;

    // Ignore AudioTrack backward movement if duration of movement is below the threshold.
    private static final long BACKWARD_AUDIO_TRACK_MOVE_THRESHOLD_US = 3000;

    // AudioTrack position cannot go ahead beyond this limit.
    private static final long CURRENT_POSITION_FROM_PTS_LIMIT_US = 1000000;

    // Since MediaCodec processing and AudioTrack playing add delay,
    // PTS interpolated time should be delayed reasonably when AudioTrack is not used.
    private static final long ESTIMATED_TRACK_RENDERING_DELAY_US = 500000;

    private final MediaCodecSelector mSelector;

    private final CodecCounters mCodecCounters;
    private final SampleSource.SampleSourceReader mSource;
    private final MediaFormatHolder mFormatHolder;
    private final EventListener mEventListener;
    private final Handler mEventHandler;
    private final AudioTrackMonitor mMonitor;
    private final AudioClock mAudioClock;
    private final boolean mAc3Passthrough;
    private final boolean mSoftwareDecoderAvailable;

    private MediaFormat mFormat;
    private SampleHolder mSampleHolder;
    private String mDecodingMime;
    private boolean mFormatConfigured;
    private int mSampleSize;
    private final ByteBuffer mOutputBuffer;
    private AudioDecoder mAudioDecoder;
    private boolean mOutputReady;
    private int mTrackIndex;
    private boolean mSourceStateReady;
    private boolean mInputStreamEnded;
    private boolean mOutputStreamEnded;
    private long mEndOfStreamMs;
    private long mCurrentPositionUs;
    private int mPresentationCount;
    private long mPresentationTimeUs;
    private long mInterpolatedTimeUs;
    private long mPreviousPositionUs;
    private boolean mIsStopped;
    private boolean mEnabled = true;
    private boolean mIsMuted;
    private ArrayList<Integer> mTracksIndex;
    private boolean mUseFrameworkDecoder;

    public MpegTsDefaultAudioTrackRenderer(
            SampleSource source,
            MediaCodecSelector selector,
            Handler eventHandler,
            EventListener listener,
            boolean hasSoftwareAudioDecoder,
            boolean usePassthrough) {
        mSource = source.register();
        mSelector = selector;
        mEventHandler = eventHandler;
        mEventListener = listener;
        mTrackIndex = -1;
        mOutputBuffer = ByteBuffer.allocate(DEFAULT_OUTPUT_BUFFER_SIZE);
        mFormatHolder = new MediaFormatHolder();
        AUDIO_TRACK.restart();
        mCodecCounters = new CodecCounters();
        mMonitor = new AudioTrackMonitor();
        mAudioClock = new AudioClock();
        mTracksIndex = new ArrayList<>();
        mAc3Passthrough = usePassthrough;
        mSoftwareDecoderAvailable = hasSoftwareAudioDecoder && FfmpegDecoderClient.isAvailable();
    }

    @Override
    protected MediaClock getMediaClock() {
        return this;
    }

    private boolean handlesMimeType(String mimeType) {
        return mimeType.equals(MimeTypes.AUDIO_AC3)
                || mimeType.equals(MimeTypes.AUDIO_E_AC3)
                || mimeType.equals(MimeTypes.AUDIO_MPEG_L2)
                || MediaCodecAudioDecoder.supportMimeType(mSelector, mimeType);
    }

    @Override
    protected boolean doPrepare(long positionUs) throws ExoPlaybackException {
        boolean sourcePrepared = mSource.prepare(positionUs);
        if (!sourcePrepared) {
            return false;
        }
        for (int i = 0; i < mSource.getTrackCount(); i++) {
            String mimeType = mSource.getFormat(i).mimeType;
            if (MimeTypes.isAudio(mimeType) && handlesMimeType(mimeType)) {
                if (mTrackIndex < 0) {
                    mTrackIndex = i;
                }
                mTracksIndex.add(i);
            }
        }

        // TODO: Check this case. Source does not have the proper mime type.
        return true;
    }

    @Override
    protected int getTrackCount() {
        return mTracksIndex.size();
    }

    @Override
    protected MediaFormat getFormat(int track) {
        Assertions.checkArgument(track >= 0 && track < mTracksIndex.size());
        return mSource.getFormat(mTracksIndex.get(track));
    }

    @Override
    protected void onEnabled(int track, long positionUs, boolean joining) {
        Assertions.checkArgument(track >= 0 && track < mTracksIndex.size());
        mTrackIndex = mTracksIndex.get(track);
        mSource.enable(mTrackIndex, positionUs);
        seekToInternal(positionUs);
    }

    @Override
    protected void onDisabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AUDIO_TRACK.resetSessionId();
        }
        clearDecodeState();
        mFormat = null;
        mSource.disable(mTrackIndex);
    }

    @Override
    protected void onReleased() {
        releaseDecoder();
        AUDIO_TRACK.release();
        mSource.release();
    }

    @Override
    protected boolean isEnded() {
        return mOutputStreamEnded && AUDIO_TRACK.isEnded();
    }

    @Override
    protected boolean isReady() {
        return AUDIO_TRACK.isReady() || (mFormat != null && (mSourceStateReady || mOutputReady));
    }

    private void seekToInternal(long positionUs) {
        mMonitor.reset(MONITOR_DURATION_MS);
        mSourceStateReady = false;
        mInputStreamEnded = false;
        mOutputStreamEnded = false;
        mPresentationTimeUs = positionUs;
        mPresentationCount = 0;
        mPreviousPositionUs = 0;
        mCurrentPositionUs = Long.MIN_VALUE;
        mInterpolatedTimeUs = Long.MIN_VALUE;
        mAudioClock.setPositionUs(positionUs);
    }

    @Override
    protected void seekTo(long positionUs) {
        mSource.seekToUs(positionUs);
        AUDIO_TRACK.reset();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // resetSessionId() will create a new framework AudioTrack instead of reusing old one.
            AUDIO_TRACK.resetSessionId();
        }
        seekToInternal(positionUs);
        clearDecodeState();
    }

    @Override
    protected void onStarted() {
        AUDIO_TRACK.play();
        mAudioClock.start();
        mIsStopped = false;
    }

    @Override
    protected void onStopped() {
        AUDIO_TRACK.pause();
        mAudioClock.stop();
        mIsStopped = true;
    }

    @Override
    protected void maybeThrowError() throws ExoPlaybackException {
        try {
            mSource.maybeThrowError();
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
    }

    @Override
    protected void doSomeWork(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
        mMonitor.maybeLog();
        try {
            if (mEndOfStreamMs != 0) {
                // Ensure playback stops, after EoS was notified.
                // Sometimes MediaCodecTrackRenderer does not fetch EoS timely
                // after EoS was notified here long before.
                long diff = SystemClock.elapsedRealtime() - mEndOfStreamMs;
                if (diff >= KEEP_ALIVE_AFTER_EOS_DURATION_MS && !mIsStopped) {
                    throw new ExoPlaybackException("Much time has elapsed after EoS");
                }
            }
            boolean continueBuffering = mSource.continueBuffering(mTrackIndex, positionUs);
            if (mSourceStateReady != continueBuffering) {
                mSourceStateReady = continueBuffering;
                if (DEBUG) {
                    Log.d(TAG, "mSourceStateReady: " + String.valueOf(mSourceStateReady));
                }
            }
            long discontinuity = mSource.readDiscontinuity(mTrackIndex);
            if (discontinuity != SampleSource.NO_DISCONTINUITY) {
                AUDIO_TRACK.handleDiscontinuity();
                mPresentationTimeUs = discontinuity;
                mPresentationCount = 0;
                clearDecodeState();
                return;
            }
            if (mFormat == null) {
                readFormat();
                return;
            }

            if (mAudioDecoder != null) {
                mAudioDecoder.maybeInitDecoder(mFormat);
            }
            // Process only one sample at a time for doSomeWork() when using FFmpeg decoder.
            if (processOutput()) {
                if (!mOutputReady) {
                    while (feedInputBuffer()) {
                        if (mOutputReady) break;
                    }
                }
            }
            mCodecCounters.ensureUpdated();
        } catch (IOException e) {
            throw new ExoPlaybackException(e);
        }
    }

    private void ensureAudioTrackInitialized() {
        if (!AUDIO_TRACK.isInitialized()) {
            try {
                if (DEBUG) {
                    Log.d(TAG, "AudioTrack initialized");
                }
                AUDIO_TRACK.initialize();
            } catch (AudioTrack.InitializationException e) {
                Log.e(TAG, "Error on AudioTrack initialization", e);
                notifyAudioTrackInitializationError(e);

                // Do not throw exception here but just disabling audioTrack to keep playing
                // video without audio.
                AUDIO_TRACK.setStatus(false);
            }
            if (getState() == TrackRenderer.STATE_STARTED) {
                if (DEBUG) {
                    Log.d(TAG, "AudioTrack played");
                }
                AUDIO_TRACK.play();
            }
        }
    }

    private void clearDecodeState() {
        mOutputReady = false;
        if (mAudioDecoder != null) {
            mAudioDecoder.resetDecoderState(mDecodingMime);
        }
        AUDIO_TRACK.reset();
    }

    private void releaseDecoder() {
        if (mAudioDecoder != null) {
            mAudioDecoder.release();
        }
    }

    private void readFormat() throws IOException, ExoPlaybackException {
        int result = mSource.readData(mTrackIndex, mCurrentPositionUs,
                mFormatHolder, mSampleHolder);
        if (result == SampleSource.FORMAT_READ) {
            onInputFormatChanged(mFormatHolder);
        }
    }

    private MediaFormat convertMediaFormatToRaw(MediaFormat format) {
        return MediaFormat.createAudioFormat(
                format.trackId,
                MimeTypes.AUDIO_RAW,
                format.bitrate,
                format.maxInputSize,
                format.durationUs,
                format.channelCount,
                format.sampleRate,
                format.initializationData,
                format.language);
    }

    private void onInputFormatChanged(MediaFormatHolder formatHolder)
            throws ExoPlaybackException {
        String mimeType = formatHolder.format.mimeType;
        mUseFrameworkDecoder = MediaCodecAudioDecoder.supportMimeType(mSelector, mimeType);
        if (mUseFrameworkDecoder) {
            mAudioDecoder = new MediaCodecAudioDecoder(mSelector);
            mFormat = formatHolder.format;
            mAudioDecoder.maybeInitDecoder(mFormat);
            mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DISABLED);
        } else if (mSoftwareDecoderAvailable
                && (MimeTypes.AUDIO_MPEG_L2.equalsIgnoreCase(mimeType)
                        || MimeTypes.AUDIO_AC3.equalsIgnoreCase(mimeType) && !mAc3Passthrough)) {
            releaseDecoder();
            mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
            mSampleHolder.ensureSpaceForWrite(DEFAULT_INPUT_BUFFER_SIZE);
            mAudioDecoder = FfmpegDecoderClient.getInstance();
            mDecodingMime = mimeType;
            mFormat = convertMediaFormatToRaw(formatHolder.format);
        } else {
            mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_DIRECT);
            mSampleHolder.ensureSpaceForWrite(DEFAULT_INPUT_BUFFER_SIZE);
            mFormat = formatHolder.format;
            releaseDecoder();
        }
        mFormatConfigured = true;
        mMonitor.setEncoding(mimeType);
        if (DEBUG && !mUseFrameworkDecoder) {
            Log.d(TAG, "AudioTrack was configured to FORMAT: " + mFormat.toString());
        }
        clearDecodeState();
        if (!mUseFrameworkDecoder) {
            AUDIO_TRACK.reconfigure(mFormat.getFrameworkMediaFormatV16(), 0);
        }
    }

    private void onSampleSizeChanged(int sampleSize) {
        if (DEBUG) {
            Log.d(TAG, "Sample size was changed to : " + sampleSize);
        }
        clearDecodeState();
        int audioBufferSize = sampleSize * BUFFERED_SAMPLES_IN_AUDIOTRACK;
        mSampleSize = sampleSize;
        AUDIO_TRACK.reconfigure(mFormat.getFrameworkMediaFormatV16(), audioBufferSize);
    }

    private void onOutputFormatChanged(android.media.MediaFormat format) {
        if (DEBUG) {
            Log.d(TAG, "AudioTrack was configured to FORMAT: " + format.toString());
        }
        AUDIO_TRACK.reconfigure(format, 0);
    }

    private boolean feedInputBuffer() throws IOException, ExoPlaybackException {
        if (mInputStreamEnded) {
            return false;
        }

        if (mUseFrameworkDecoder) {
            boolean indexChanged =
                    ((MediaCodecAudioDecoder) mAudioDecoder).getInputIndex()
                            == MediaCodecAudioDecoder.INDEX_INVALID;
            if (indexChanged) {
                mSampleHolder.data = mAudioDecoder.getInputBuffer();
                if (mSampleHolder.data != null) {
                    mSampleHolder.clearData();
                } else {
                    return false;
                }
            }
        } else {
            mSampleHolder.data.clear();
            mSampleHolder.size = 0;
        }
        int result =
                mSource.readData(mTrackIndex, mPresentationTimeUs, mFormatHolder, mSampleHolder);
        switch (result) {
            case SampleSource.NOTHING_READ: {
                return false;
            }
            case SampleSource.FORMAT_READ: {
                Log.i(TAG, "Format was read again");
                onInputFormatChanged(mFormatHolder);
                return true;
            }
            case SampleSource.END_OF_STREAM: {
                Log.i(TAG, "End of stream from SampleSource");
                mInputStreamEnded = true;
                return false;
            }
            default: {
                if (mSampleHolder.size != mSampleSize
                        && mFormatConfigured
                        && !mUseFrameworkDecoder) {
                    onSampleSizeChanged(mSampleHolder.size);
                }
                mSampleHolder.data.flip();
                if (!mUseFrameworkDecoder) {
                    if (MimeTypes.AUDIO_MPEG_L2.equalsIgnoreCase(mDecodingMime)) {
                        mMonitor.addPts(
                            mSampleHolder.timeUs,
                            mOutputBuffer.position(),
                            mSampleHolder.data.get(MP2_HEADER_BITRATE_OFFSET)
                                & MP2_HEADER_BITRATE_MASK);
                    } else {
                        mMonitor.addPts(
                            mSampleHolder.timeUs,
                            mOutputBuffer.position(),
                            mSampleHolder.data.get(AC3_HEADER_BITRATE_OFFSET) & 0xff);
                    }
                }
                if (mAudioDecoder != null) {
                    mAudioDecoder.decode(mSampleHolder);
                    if (mUseFrameworkDecoder) {
                        int outputIndex =
                                ((MediaCodecAudioDecoder) mAudioDecoder).getOutputIndex();
                        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            onOutputFormatChanged(mAudioDecoder.getOutputFormat());
                            return true;
                        } else if (outputIndex < 0) {
                            return true;
                        }
                        if (((MediaCodecAudioDecoder) mAudioDecoder).maybeDecodeOnlyIndex()) {
                            AUDIO_TRACK.handleDiscontinuity();
                            return true;
                        }
                    }
                    ByteBuffer outputBuffer = mAudioDecoder.getDecodedSample();
                    long presentationTimeUs = mAudioDecoder.getDecodedTimeUs();
                    decodeDone(outputBuffer, presentationTimeUs);
                } else {
                    decodeDone(mSampleHolder.data, mSampleHolder.timeUs);
                }
                return true;
            }
        }
    }

    private boolean processOutput() throws ExoPlaybackException {
        if (mOutputStreamEnded) {
            return false;
        }
        if (!mOutputReady) {
            if (mInputStreamEnded) {
                mOutputStreamEnded = true;
                mEndOfStreamMs = SystemClock.elapsedRealtime();
                return false;
            }
            return true;
        }

        ensureAudioTrackInitialized();
        int handleBufferResult;
        try {
            // To reduce discontinuity, interpolate presentation time.
            if (MimeTypes.AUDIO_MPEG_L2.equalsIgnoreCase(mDecodingMime)) {
                mInterpolatedTimeUs = mPresentationTimeUs
                    + mPresentationCount * MP2_SAMPLE_DURATION_US;
            } else if (!mUseFrameworkDecoder) {
                mInterpolatedTimeUs = mPresentationTimeUs
                    + mPresentationCount * AC3_SAMPLE_DURATION_US;
            } else {
                mInterpolatedTimeUs = mPresentationTimeUs;
            }
            handleBufferResult =
                    AUDIO_TRACK.handleBuffer(
                            mOutputBuffer, 0, mOutputBuffer.limit(), mInterpolatedTimeUs);
        } catch (AudioTrack.WriteException e) {
            notifyAudioTrackWriteError(e);
            throw new ExoPlaybackException(e);
        }
        if ((handleBufferResult & AudioTrack.RESULT_POSITION_DISCONTINUITY) != 0) {
            Log.i(TAG, "Play discontinuity happened");
            mCurrentPositionUs = Long.MIN_VALUE;
        }
        if ((handleBufferResult & AudioTrack.RESULT_BUFFER_CONSUMED) != 0) {
            mCodecCounters.renderedOutputBufferCount++;
            mOutputReady = false;
            if (mUseFrameworkDecoder) {
                ((MediaCodecAudioDecoder) mAudioDecoder).releaseOutputBuffer();
            }
            return true;
        }
        return false;
    }

    @Override
    protected long getDurationUs() {
        return mSource.getFormat(mTrackIndex).durationUs;
    }

    @Override
    protected long getBufferedPositionUs() {
        long pos = mSource.getBufferedPositionUs();
        return pos == UNKNOWN_TIME_US || pos == END_OF_TRACK_US
                ? pos : Math.max(pos, getPositionUs());
    }

    @Override
    public long getPositionUs() {
        if (!AUDIO_TRACK.isInitialized()) {
            return mAudioClock.getPositionUs();
        } else if (!AUDIO_TRACK.isEnabled()) {
            if (mInterpolatedTimeUs > 0 && !mUseFrameworkDecoder) {
                return mInterpolatedTimeUs - ESTIMATED_TRACK_RENDERING_DELAY_US;
            }
            return mPresentationTimeUs;
        }
        long audioTrackCurrentPositionUs = AUDIO_TRACK.getCurrentPositionUs(isEnded());
        if (audioTrackCurrentPositionUs == AudioTrack.CURRENT_POSITION_NOT_SET) {
            mPreviousPositionUs = 0L;
            if (DEBUG) {
                long oldPositionUs = Math.max(mCurrentPositionUs, 0);
                long currentPositionUs = Math.max(mPresentationTimeUs, mCurrentPositionUs);
                Log.d(TAG, "Audio position is not set, diff in us: "
                        + String.valueOf(currentPositionUs - oldPositionUs));
            }
            mCurrentPositionUs = Math.max(mPresentationTimeUs, mCurrentPositionUs);
        } else {
            if (mPreviousPositionUs
                    > audioTrackCurrentPositionUs + BACKWARD_AUDIO_TRACK_MOVE_THRESHOLD_US) {
                Log.e(TAG, "audio_position BACK JUMP: "
                        + (mPreviousPositionUs - audioTrackCurrentPositionUs));
                mCurrentPositionUs = audioTrackCurrentPositionUs;
            } else {
                mCurrentPositionUs = Math.max(mCurrentPositionUs, audioTrackCurrentPositionUs);
            }
            mPreviousPositionUs = audioTrackCurrentPositionUs;
        }
        long upperBound = mPresentationTimeUs + CURRENT_POSITION_FROM_PTS_LIMIT_US;
        if (mCurrentPositionUs > upperBound) {
            mCurrentPositionUs = upperBound;
        }
        return mCurrentPositionUs;
    }

    private void decodeDone(ByteBuffer outputBuffer, long presentationTimeUs) {
        if (outputBuffer == null || mOutputBuffer == null) {
            return;
        }
        if (presentationTimeUs < 0) {
            Log.e(TAG, "decodeDone - invalid presentationTimeUs");
            return;
        }

        if (TunerDebug.ENABLED) {
            TunerDebug.setAudioPtsUs(presentationTimeUs);
        }

        mOutputBuffer.clear();
        Assertions.checkState(mOutputBuffer.remaining() >= outputBuffer.limit());

        mOutputBuffer.put(outputBuffer);
        if (presentationTimeUs == mPresentationTimeUs) {
            mPresentationCount++;
        } else {
            mPresentationCount = 0;
            mPresentationTimeUs = presentationTimeUs;
        }
        mOutputBuffer.flip();
        mOutputReady = true;
    }

    private void notifyAudioTrackInitializationError(final AudioTrack.InitializationException e) {
        if (mEventHandler == null || mEventListener == null) {
            return;
        }
        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                mEventListener.onAudioTrackInitializationError(e);
            }
        });
    }

    private void notifyAudioTrackWriteError(final AudioTrack.WriteException e) {
        if (mEventHandler == null || mEventListener == null) {
            return;
        }
        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                mEventListener.onAudioTrackWriteError(e);
            }
        });
    }

    @Override
    public void handleMessage(int messageType, Object message) throws ExoPlaybackException {
        switch (messageType) {
            case MSG_SET_VOLUME:
                float volume = (Float) message;
                // Workaround: we cannot mute the audio track by setting the volume to 0, we need to
                // disable the AUDIO_TRACK for this intent. However, enabling/disabling audio track
                // whenever volume is being set might cause side effects, therefore we only handle
                // "explicit mute operations", i.e., only after certain non-zero volume has been
                // set, the subsequent volume setting operations will be consider as mute/un-mute
                // operations and thus enable/disable the audio track.
                if (mIsMuted && volume > 0) {
                    mIsMuted = false;
                    if (mEnabled) {
                        setStatus(true);
                    }
                } else if (!mIsMuted && volume == 0) {
                    mIsMuted = true;
                    if (mEnabled) {
                        setStatus(false);
                    }
                }
                AUDIO_TRACK.setVolume(volume);
                break;
            case MSG_SET_AUDIO_TRACK:
                mEnabled = (Integer) message == 1;
                setStatus(mEnabled);
                break;
            case MSG_SET_PLAYBACK_SPEED:
                mAudioClock.setPlaybackSpeed((Float) message);
                break;
            default:
                super.handleMessage(messageType, message);
        }
    }

    private void setStatus(boolean enabled) {
        if (enabled == AUDIO_TRACK.isEnabled()) {
            return;
        }
        if (!enabled) {
            // mAudioClock can be different from getPositionUs. In order to sync them,
            // we set mAudioClock.
            mAudioClock.setPositionUs(getPositionUs());
        }
        AUDIO_TRACK.setStatus(enabled);
        if (enabled) {
            // When AUDIO_TRACK is enabled, we need to clear AUDIO_TRACK and seek to
            // the current position. If not, AUDIO_TRACK has the obsolete data.
            seekTo(mAudioClock.getPositionUs());
        }
    }
}
