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

import android.net.Uri;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Pair;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource.EventListener;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.android.tv.tuner.exoplayer.audio.MpegTsDefaultAudioTrackRenderer;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.exoplayer.buffer.RecordingSampleBuffer;
import com.android.tv.tuner.exoplayer.buffer.SimpleSampleBuffer;
import com.android.tv.tuner.tvinput.PlaybackBufferListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A class that extracts samples from a live broadcast stream while storing the sample on the disk.
 * For demux, this class relies on {@link com.google.android.exoplayer.extractor.ts.TsExtractor}.
 */
public class ExoPlayerSampleExtractor implements SampleExtractor {
    private static final String TAG = "ExoPlayerSampleExtracto";

    private static final int INVALID_TRACK_INDEX = -1;
    private final HandlerThread mSourceReaderThread;
    private final long mId;

    private final Handler.Callback mSourceReaderWorker;

    private BufferManager.SampleBuffer mSampleBuffer;
    private Handler mSourceReaderHandler;
    private volatile boolean mPrepared;
    private AtomicBoolean mOnCompletionCalled = new AtomicBoolean();
    private IOException mExceptionOnPrepare;
    private List<MediaFormat> mTrackFormats;
    private int mVideoTrackIndex = INVALID_TRACK_INDEX;
    private boolean mVideoTrackMet;
    private long mBaseSamplePts = Long.MIN_VALUE;
    private HashMap<Integer, Long> mLastExtractedPositionUsMap = new HashMap<>();
    private final List<Pair<Integer, SampleHolder>> mPendingSamples = new LinkedList<>();
    private OnCompletionListener mOnCompletionListener;
    private Handler mOnCompletionListenerHandler;
    private IOException mError;

    public ExoPlayerSampleExtractor(Uri uri, final DataSource source, BufferManager bufferManager,
            PlaybackBufferListener bufferListener, boolean isRecording) {
        // It'll be used as a timeshift file chunk name's prefix.
        mId = System.currentTimeMillis();

        EventListener eventListener = new EventListener() {
            @Override
            public void onLoadError(IOException error) {
                mError = error;
            }
        };

        mSourceReaderThread = new HandlerThread("SourceReaderThread");
        mSourceReaderWorker = new SourceReaderWorker(new ExtractorMediaSource(uri,
                new com.google.android.exoplayer2.upstream.DataSource.Factory() {
                    @Override
                    public com.google.android.exoplayer2.upstream.DataSource createDataSource() {
                        // Returns an adapter implementation for ExoPlayer V2 DataSource interface.
                        return new com.google.android.exoplayer2.upstream.DataSource() {
                            @Override
                            public long open(DataSpec dataSpec) throws IOException {
                                return source.open(
                                        new com.google.android.exoplayer.upstream.DataSpec(
                                                dataSpec.uri, dataSpec.postBody,
                                                dataSpec.absoluteStreamPosition, dataSpec.position,
                                                dataSpec.length, dataSpec.key, dataSpec.flags));
                            }

                            @Override
                            public int read(byte[] buffer, int offset, int readLength)
                                    throws IOException {
                                return source.read(buffer, offset, readLength);
                            }

                            @Override
                            public Uri getUri() {
                                return null;
                            }

                            @Override
                            public void close() throws IOException {
                                source.close();
                            }
                        };
                    }
                },
                new ExoPlayerExtractorsFactory(),
                // Do not create a handler if we not on a looper. e.g. test.
                Looper.myLooper() != null ? new Handler() : null, eventListener));
        if (isRecording) {
            mSampleBuffer = new RecordingSampleBuffer(bufferManager, bufferListener, false,
                    RecordingSampleBuffer.BUFFER_REASON_RECORDING);
        } else {
            if (bufferManager == null) {
                mSampleBuffer = new SimpleSampleBuffer(bufferListener);
            } else {
                mSampleBuffer = new RecordingSampleBuffer(bufferManager, bufferListener, true,
                        RecordingSampleBuffer.BUFFER_REASON_LIVE_PLAYBACK);
            }
        }
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener, Handler handler) {
        mOnCompletionListener = listener;
        mOnCompletionListenerHandler = handler;
    }

    private class SourceReaderWorker implements Handler.Callback, MediaPeriod.Callback {
        public static final int MSG_PREPARE = 1;
        public static final int MSG_FETCH_SAMPLES = 2;
        public static final int MSG_RELEASE = 3;
        private static final int RETRY_INTERVAL_MS = 50;

        private final MediaSource mSampleSource;
        private MediaPeriod mMediaPeriod;
        private SampleStream[] mStreams;
        private boolean[] mTrackMetEos;
        private boolean mMetEos = false;
        private long mCurrentPosition;
        private DecoderInputBuffer mDecoderInputBuffer;
        private SampleHolder mSampleHolder;
        private boolean mPrepareRequested;

        public SourceReaderWorker(MediaSource sampleSource) {
            mSampleSource = sampleSource;
            mSampleSource.prepareSource(null, false, new MediaSource.Listener() {
                @Override
                public void onSourceInfoRefreshed(Timeline timeline, Object manifest) {
                    // Dynamic stream change is not supported yet. b/28169263
                    // For now, this will cause EOS and playback reset.
                }
            });
            mDecoderInputBuffer = new DecoderInputBuffer(
                    DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
            mSampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
        }

        MediaFormat convertFormat(Format format) {
            if (format.sampleMimeType.startsWith("audio/")) {
                return MediaFormat.createAudioFormat(format.id, format.sampleMimeType,
                        format.bitrate, format.maxInputSize,
                        com.google.android.exoplayer.C.UNKNOWN_TIME_US, format.channelCount,
                        format.sampleRate, format.initializationData, format.language,
                        format.pcmEncoding);
            } else if (format.sampleMimeType.startsWith("video/")) {
                return MediaFormat.createVideoFormat(
                        format.id, format.sampleMimeType, format.bitrate, format.maxInputSize,
                        com.google.android.exoplayer.C.UNKNOWN_TIME_US, format.width, format.height,
                        format.initializationData, format.rotationDegrees,
                        format.pixelWidthHeightRatio, format.projectionData, format.stereoMode);
            } else if (format.sampleMimeType.endsWith("/cea-608")
                    || format.sampleMimeType.startsWith("text/")) {
                return MediaFormat.createTextFormat(
                        format.id, format.sampleMimeType, format.bitrate,
                        com.google.android.exoplayer.C.UNKNOWN_TIME_US, format.language);
            } else {
                return MediaFormat.createFormatForMimeType(
                        format.id, format.sampleMimeType, format.bitrate,
                        com.google.android.exoplayer.C.UNKNOWN_TIME_US);
            }
        }

        @Override
        public void onPrepared(MediaPeriod mediaPeriod) {
            if (mMediaPeriod == null) {
                // This instance is already released while the extractor is preparing.
                return;
            }
            TrackSelection.Factory selectionFactory = new FixedTrackSelection.Factory();
            TrackGroupArray trackGroupArray = mMediaPeriod.getTrackGroups();
            TrackSelection[] selections = new TrackSelection[trackGroupArray.length];
            for (int i = 0; i < selections.length; ++i) {
                selections[i] = selectionFactory.createTrackSelection(trackGroupArray.get(i), 0);
            }
            boolean retain[] = new boolean[trackGroupArray.length];
            boolean reset[] = new boolean[trackGroupArray.length];
            mStreams = new SampleStream[trackGroupArray.length];
            mMediaPeriod.selectTracks(selections, retain, mStreams, reset, 0);
            if (mTrackFormats == null) {
                int trackCount = trackGroupArray.length;
                mTrackMetEos = new boolean[trackCount];
                List<MediaFormat> trackFormats = new ArrayList<>();
                int videoTrackCount = 0;
                for (int i = 0; i < trackCount; i++) {
                    Format format = trackGroupArray.get(i).getFormat(0);
                    if (format.sampleMimeType.startsWith("video/")) {
                        videoTrackCount++;
                        mVideoTrackIndex = i;
                    }
                    trackFormats.add(convertFormat(format));
                }
                if (videoTrackCount > 1) {
                    // Disable dropping samples when there are multiple video tracks.
                    mVideoTrackIndex = INVALID_TRACK_INDEX;
                }
                mTrackFormats = trackFormats;
                List<String> ids = new ArrayList<>();
                for (int i = 0; i < mTrackFormats.size(); i++) {
                    ids.add(String.format(Locale.ENGLISH, "%s_%x", Long.toHexString(mId), i));
                }
                try {
                    mSampleBuffer.init(ids, mTrackFormats);
                } catch (IOException e) {
                    // In this case, we will not schedule any further operation.
                    // mExceptionOnPrepare will be notified to ExoPlayer, and ExoPlayer will
                    // call release() eventually.
                    mExceptionOnPrepare = e;
                    return;
                }
                mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                mPrepared = true;
            }
        }

        @Override
        public void onContinueLoadingRequested(MediaPeriod source) {
            source.continueLoading(mCurrentPosition);
        }

        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case MSG_PREPARE:
                    if (!mPrepareRequested) {
                        mPrepareRequested = true;
                        mMediaPeriod = mSampleSource.createPeriod(0,
                                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE), 0);
                        mMediaPeriod.prepare(this);
                        try {
                            mMediaPeriod.maybeThrowPrepareError();
                        } catch (IOException e) {
                            mError = e;
                        }
                    }
                    return true;
                case MSG_FETCH_SAMPLES:
                    boolean didSomething = false;
                    ConditionVariable conditionVariable = new ConditionVariable();
                    int trackCount = mStreams.length;
                    for (int i = 0; i < trackCount; ++i) {
                        if (!mTrackMetEos[i] && C.RESULT_NOTHING_READ
                                != fetchSample(i, mSampleHolder, conditionVariable)) {
                            if (mMetEos) {
                                // If mMetEos was on during fetchSample() due to an error,
                                // fetching from other tracks is not necessary.
                                break;
                            }
                            didSomething = true;
                        }
                    }
                    mMediaPeriod.continueLoading(mCurrentPosition);
                    if (!mMetEos) {
                        if (didSomething) {
                            mSourceReaderHandler.sendEmptyMessage(MSG_FETCH_SAMPLES);
                        } else {
                            mSourceReaderHandler.sendEmptyMessageDelayed(MSG_FETCH_SAMPLES,
                                    RETRY_INTERVAL_MS);
                        }
                    } else {
                        notifyCompletionIfNeeded(false);
                    }
                    return true;
                case MSG_RELEASE:
                    if (mMediaPeriod != null) {
                        mSampleSource.releasePeriod(mMediaPeriod);
                        mSampleSource.releaseSource();
                        mMediaPeriod = null;
                    }
                    cleanUp();
                    mSourceReaderHandler.removeCallbacksAndMessages(null);
                    return true;
            }
            return false;
        }

        private int fetchSample(int track, SampleHolder sample,
                ConditionVariable conditionVariable) {
            FormatHolder dummyFormatHolder = new FormatHolder();
            mDecoderInputBuffer.clear();
            int ret = mStreams[track].readData(dummyFormatHolder, mDecoderInputBuffer);
            if (ret == C.RESULT_BUFFER_READ
                    // Double-check if the extractor provided the data to prevent NPE. b/33758354
                    && mDecoderInputBuffer.data != null) {
                if (mCurrentPosition < mDecoderInputBuffer.timeUs) {
                    mCurrentPosition = mDecoderInputBuffer.timeUs;
                }
                try {
                    Long lastExtractedPositionUs = mLastExtractedPositionUsMap.get(track);
                    if (lastExtractedPositionUs == null) {
                        mLastExtractedPositionUsMap.put(track, mDecoderInputBuffer.timeUs);
                    } else {
                        mLastExtractedPositionUsMap.put(track,
                                Math.max(lastExtractedPositionUs, mDecoderInputBuffer.timeUs));
                    }
                    queueSample(track, conditionVariable);
                } catch (IOException e) {
                    mLastExtractedPositionUsMap.clear();
                    mMetEos = true;
                    mSampleBuffer.setEos();
                }
            } else if (ret == C.RESULT_END_OF_INPUT) {
                mTrackMetEos[track] = true;
                for (int i = 0; i < mTrackMetEos.length; ++i) {
                    if (!mTrackMetEos[i]) {
                        break;
                    }
                    if (i == mTrackMetEos.length - 1) {
                        mMetEos = true;
                        mSampleBuffer.setEos();
                    }
                }
            }
            // TODO: Handle C.RESULT_FORMAT_READ for dynamic resolution change. b/28169263
            return ret;
        }

        private void queueSample(int index, ConditionVariable conditionVariable)
                throws IOException {
            if (mVideoTrackIndex != INVALID_TRACK_INDEX) {
                if (!mVideoTrackMet) {
                    if (index != mVideoTrackIndex) {
                        SampleHolder sample =
                                new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
                        mSampleHolder.flags =
                                (mDecoderInputBuffer.isKeyFrame()
                                        ? com.google.android.exoplayer.C.SAMPLE_FLAG_SYNC : 0)
                                | (mDecoderInputBuffer.isDecodeOnly()
                                        ? com.google.android.exoplayer.C.SAMPLE_FLAG_DECODE_ONLY
                                        : 0);
                        sample.timeUs = mDecoderInputBuffer.timeUs;
                        sample.size = mDecoderInputBuffer.data.position();
                        sample.ensureSpaceForWrite(sample.size);
                        mDecoderInputBuffer.flip();
                        sample.data.position(0);
                        sample.data.put(mDecoderInputBuffer.data);
                        sample.data.flip();
                        mPendingSamples.add(new Pair<>(index, sample));
                        return;
                    }
                    mVideoTrackMet = true;
                    mBaseSamplePts =
                            mDecoderInputBuffer.timeUs
                                    - MpegTsDefaultAudioTrackRenderer
                                            .INITIAL_AUDIO_BUFFERING_TIME_US;
                    for (Pair<Integer, SampleHolder> pair : mPendingSamples) {
                        if (pair.second.timeUs >= mBaseSamplePts) {
                            mSampleBuffer.writeSample(pair.first, pair.second, conditionVariable);
                        }
                    }
                    mPendingSamples.clear();
                } else {
                    if (mDecoderInputBuffer.timeUs < mBaseSamplePts
                            && mVideoTrackIndex != index) {
                        return;
                    }
                }
            }
            // Copy the decoder input to the sample holder.
            mSampleHolder.clearData();
            mSampleHolder.flags =
                    (mDecoderInputBuffer.isKeyFrame()
                            ? com.google.android.exoplayer.C.SAMPLE_FLAG_SYNC : 0)
                    | (mDecoderInputBuffer.isDecodeOnly()
                            ? com.google.android.exoplayer.C.SAMPLE_FLAG_DECODE_ONLY : 0);
            mSampleHolder.timeUs = mDecoderInputBuffer.timeUs;
            mSampleHolder.size = mDecoderInputBuffer.data.position();
            mSampleHolder.ensureSpaceForWrite(mSampleHolder.size);
            mDecoderInputBuffer.flip();
            mSampleHolder.data.position(0);
            mSampleHolder.data.put(mDecoderInputBuffer.data);
            mSampleHolder.data.flip();
            long writeStartTimeNs = SystemClock.elapsedRealtimeNanos();
            mSampleBuffer.writeSample(index, mSampleHolder, conditionVariable);

            // Checks whether the storage has enough bandwidth for recording samples.
            if (mSampleBuffer.isWriteSpeedSlow(mSampleHolder.size,
                    SystemClock.elapsedRealtimeNanos() - writeStartTimeNs)) {
                mSampleBuffer.handleWriteSpeedSlow();
            }
        }
    }

    @Override
    public void maybeThrowError() throws IOException {
        if (mError != null) {
            IOException e = mError;
            mError = null;
            throw e;
        }
    }

    @Override
    public boolean prepare() throws IOException {
        if (!mSourceReaderThread.isAlive()) {
            mSourceReaderThread.start();
            mSourceReaderHandler = new Handler(mSourceReaderThread.getLooper(),
                    mSourceReaderWorker);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_PREPARE);
        }
        if (mExceptionOnPrepare != null) {
            throw mExceptionOnPrepare;
        }
        return mPrepared;
    }

    @Override
    public List<MediaFormat> getTrackFormats() {
        return mTrackFormats;
    }

    @Override
    public void getTrackMediaFormat(int track, MediaFormatHolder outMediaFormatHolder) {
        outMediaFormatHolder.format = mTrackFormats.get(track);
        outMediaFormatHolder.drmInitData = null;
    }

    @Override
    public void selectTrack(int index) {
        mSampleBuffer.selectTrack(index);
    }

    @Override
    public void deselectTrack(int index) {
        mSampleBuffer.deselectTrack(index);
    }

    @Override
    public long getBufferedPositionUs() {
        return mSampleBuffer.getBufferedPositionUs();
    }

    @Override
    public boolean continueBuffering(long positionUs) {
        return mSampleBuffer.continueBuffering(positionUs);
    }

    @Override
    public void seekTo(long positionUs) {
        mSampleBuffer.seekTo(positionUs);
    }

    @Override
    public int readSample(int track, SampleHolder sampleHolder) {
        return mSampleBuffer.readSample(track, sampleHolder);
    }

    @Override
    public void release() {
        if (mSourceReaderThread.isAlive()) {
            mSourceReaderHandler.removeCallbacksAndMessages(null);
            mSourceReaderHandler.sendEmptyMessage(SourceReaderWorker.MSG_RELEASE);
            mSourceReaderThread.quitSafely();
            // Return early in this case so that session worker can start working on the next
            // request as early as it can. The clean up will be done in the reader thread while
            // handling MSG_RELEASE.
        } else {
            cleanUp();
        }
    }

    private void cleanUp() {
        boolean result = true;
        try {
            if (mSampleBuffer != null) {
                mSampleBuffer.release();
                mSampleBuffer = null;
            }
        } catch (IOException e) {
            result = false;
        }
        notifyCompletionIfNeeded(result);
        setOnCompletionListener(null, null);
    }

    private void notifyCompletionIfNeeded(final boolean result) {
        if (!mOnCompletionCalled.getAndSet(true)) {
            final OnCompletionListener listener = mOnCompletionListener;
            final long lastExtractedPositionUs = getLastExtractedPositionUs();
            if (mOnCompletionListenerHandler != null && mOnCompletionListener != null) {
                mOnCompletionListenerHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onCompletion(result, lastExtractedPositionUs);
                    }
                });
            }
        }
    }

    private long getLastExtractedPositionUs() {
        long lastExtractedPositionUs = Long.MIN_VALUE;
        for (Map.Entry<Integer, Long> entry : mLastExtractedPositionUsMap.entrySet()) {
            if (mVideoTrackIndex != entry.getKey()) {
                lastExtractedPositionUs = Math.max(lastExtractedPositionUs, entry.getValue());
            }
        }
        if (lastExtractedPositionUs == Long.MIN_VALUE) {
            lastExtractedPositionUs = com.google.android.exoplayer.C.UNKNOWN_TIME_US;
        }
        return lastExtractedPositionUs;
    }
}
