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
 * limitations under the License.
 */

package com.android.tv.tuner.exoplayer.buffer;

import android.media.MediaCodec;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.util.MimeTypes;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.exoplayer.buffer.RecordingSampleBuffer.BufferReason;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles all {@link SampleChunk} I/O operations.
 * An I/O dedicated thread handles all I/O operations for synchronization.
 */
public class SampleChunkIoHelper implements Handler.Callback {
    private static final String TAG = "SampleChunkIoHelper";

    private static final int MAX_READ_BUFFER_SAMPLES = 3;
    private static final int READ_RESCHEDULING_DELAY_MS = 10;

    private static final int MSG_OPEN_READ = 1;
    private static final int MSG_OPEN_WRITE = 2;
    private static final int MSG_CLOSE_READ = 3;
    private static final int MSG_CLOSE_WRITE = 4;
    private static final int MSG_READ = 5;
    private static final int MSG_WRITE = 6;
    private static final int MSG_RELEASE = 7;

    private final long mSampleChunkDurationUs;
    private final int mTrackCount;
    private final List<String> mIds;
    private final List<MediaFormat> mMediaFormats;
    private final @BufferReason int mBufferReason;
    private final BufferManager mBufferManager;
    private final SamplePool mSamplePool;
    private final IoCallback mIoCallback;

    private Handler mIoHandler;
    private final ConcurrentLinkedQueue<SampleHolder> mReadSampleBuffers[];
    private final ConcurrentLinkedQueue<SampleHolder> mHandlerReadSampleBuffers[];
    private final long[] mWriteIndexEndPositionUs;
    private final long[] mWriteChunkEndPositionUs;
    private final SampleChunk.IoState[] mReadIoStates;
    private final SampleChunk.IoState[] mWriteIoStates;
    private final Set<Integer> mSelectedTracks = new ArraySet<>();
    private long mBufferDurationUs = 0;
    private boolean mWriteEnded;
    private boolean mErrorNotified;
    private boolean mFinished;

    /**
     * A Callback for I/O events.
     */
    public static abstract class IoCallback {

        /**
         * Called when there is no sample to read.
         */
        public void onIoReachedEos() {
        }

        /**
         * Called when there is an irrecoverable error during I/O.
         */
        public void onIoError() {
        }
    }

    private class IoParams {
        private final int index;
        private final long positionUs;
        private final SampleHolder sample;
        private final ConditionVariable conditionVariable;
        private final ConcurrentLinkedQueue<SampleHolder> readSampleBuffer;

        private IoParams(int index, long positionUs, SampleHolder sample,
                ConditionVariable conditionVariable,
                ConcurrentLinkedQueue<SampleHolder> readSampleBuffer) {
            this.index = index;
            this.positionUs = positionUs;
            this.sample = sample;
            this.conditionVariable = conditionVariable;
            this.readSampleBuffer = readSampleBuffer;
        }
    }

    /**
     * Creates {@link SampleChunk} I/O handler.
     *
     * @param ids track names
     * @param mediaFormats {@link android.media.MediaFormat} for each track
     * @param bufferReason reason to be buffered
     * @param bufferManager manager of {@link SampleChunk} collections
     * @param samplePool allocator for a sample
     * @param ioCallback listeners for I/O events
     */
    public SampleChunkIoHelper(List<String> ids, List<MediaFormat> mediaFormats,
            @BufferReason int bufferReason, BufferManager bufferManager, SamplePool samplePool,
            IoCallback ioCallback) {
        mTrackCount = ids.size();
        mIds = ids;
        mMediaFormats = mediaFormats;
        mBufferReason = bufferReason;
        mBufferManager = bufferManager;
        mSamplePool = samplePool;
        mIoCallback = ioCallback;

        mReadSampleBuffers = new ConcurrentLinkedQueue[mTrackCount];
        mHandlerReadSampleBuffers = new ConcurrentLinkedQueue[mTrackCount];
        mWriteIndexEndPositionUs = new long[mTrackCount];
        mWriteChunkEndPositionUs = new long[mTrackCount];
        mReadIoStates = new SampleChunk.IoState[mTrackCount];
        mWriteIoStates = new SampleChunk.IoState[mTrackCount];

        // Small chunk duration for live playback will give more fine grained storage usage
        // and eviction handling for trickplay.
        mSampleChunkDurationUs =
                bufferReason == RecordingSampleBuffer.BUFFER_REASON_LIVE_PLAYBACK ?
                        RecordingSampleBuffer.MIN_SEEK_DURATION_US :
                        RecordingSampleBuffer.RECORDING_CHUNK_DURATION_US;
        for (int i = 0; i < mTrackCount; ++i) {
            mWriteIndexEndPositionUs[i] = RecordingSampleBuffer.MIN_SEEK_DURATION_US;
            mWriteChunkEndPositionUs[i] = mSampleChunkDurationUs;
            mReadIoStates[i] = new SampleChunk.IoState();
            mWriteIoStates[i] = new SampleChunk.IoState();
        }
    }

    /**
     * Prepares and initializes for I/O operations.
     *
     * @throws IOException
     */
    public void init() throws IOException {
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mIoHandler = new Handler(handlerThread.getLooper(), this);
        if (mBufferReason == RecordingSampleBuffer.BUFFER_REASON_RECORDED_PLAYBACK) {
            for (int i = 0; i < mTrackCount; ++i) {
                mBufferManager.loadTrackFromStorage(mIds.get(i), mSamplePool);
            }
            mWriteEnded = true;
        } else {
            for (int i = 0; i < mTrackCount; ++i) {
                mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_OPEN_WRITE, i));
            }
        }
    }

    /**
     * Reads a sample if it is available.
     *
     * @param index track index
     * @return {@code null} if a sample is not available, otherwise returns a sample
     */
    public SampleHolder readSample(int index) {
        SampleHolder sample = mReadSampleBuffers[index].poll();
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_READ, index));
        return sample;
    }

    /**
     * Writes a sample.
     *
     * @param index track index
     * @param sample to write
     * @param conditionVariable which will be wait until the write is finished
     * @throws IOException
     */
    public void writeSample(int index, SampleHolder sample,
            ConditionVariable conditionVariable) throws IOException {
        if (mErrorNotified) {
            throw new IOException("Storage I/O error happened");
        }
        conditionVariable.close();
        IoParams params = new IoParams(index, 0, sample, conditionVariable, null);
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_WRITE, params));
    }

    /**
     * Starts read from the specified position.
     *
     * @param index track index
     * @param positionUs the specified position
     */
    public void openRead(int index, long positionUs) {
        // Old mReadSampleBuffers may have a pending read.
        mReadSampleBuffers[index] = new ConcurrentLinkedQueue<>();
        IoParams params = new IoParams(index, positionUs, null, null, mReadSampleBuffers[index]);
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_OPEN_READ, params));
    }

    /**
     * Closes read from the specified track.
     *
     * @param index track index
     */
    public void closeRead(int index) {
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_CLOSE_READ, index));
    }

    /**
     * Notifies writes are finished.
     */
    public void closeWrite() {
        mIoHandler.sendEmptyMessage(MSG_CLOSE_WRITE);
    }

    /**
     * Finishes I/O operations and releases all the resources.
     * @throws IOException
     */
    public void release() throws IOException {
        if (mIoHandler == null) {
            return;
        }
        // Finishes all I/O operations.
        ConditionVariable conditionVariable = new ConditionVariable();
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_RELEASE, conditionVariable));
        conditionVariable.block();

        for (int i = 0; i < mTrackCount; ++i) {
            mBufferManager.unregisterChunkEvictedListener(mIds.get(i));
        }
        try {
            if (mBufferReason == RecordingSampleBuffer.BUFFER_REASON_RECORDING && mTrackCount > 0) {
                // Saves meta information for recording.
                List<BufferManager.TrackFormat> audios = new LinkedList<>();
                List<BufferManager.TrackFormat> videos = new LinkedList<>();
                for (int i = 0; i < mTrackCount; ++i) {
                    android.media.MediaFormat format =
                            mMediaFormats.get(i).getFrameworkMediaFormatV16();
                    format.setLong(android.media.MediaFormat.KEY_DURATION, mBufferDurationUs);
                    if (MimeTypes.isAudio(mMediaFormats.get(i).mimeType)) {
                        audios.add(new BufferManager.TrackFormat(mIds.get(i), format));
                    } else if (MimeTypes.isVideo(mMediaFormats.get(i).mimeType)) {
                        videos.add(new BufferManager.TrackFormat(mIds.get(i), format));
                    }
                }
                mBufferManager.writeMetaFiles(audios, videos);
            }
        } finally {
            mBufferManager.release();
            mIoHandler.getLooper().quitSafely();
        }
    }

    @Override
    public boolean handleMessage(Message message) {
        if (mFinished) {
            return true;
        }
        releaseEvictedChunks();
        try {
            switch (message.what) {
                case MSG_OPEN_READ:
                    doOpenRead((IoParams) message.obj);
                    return true;
                case MSG_OPEN_WRITE:
                    doOpenWrite((int) message.obj);
                    return true;
                case MSG_CLOSE_READ:
                    doCloseRead((int) message.obj);
                    return true;
                case MSG_CLOSE_WRITE:
                    doCloseWrite();
                    return true;
                case MSG_READ:
                    doRead((int) message.obj);
                    return true;
                case MSG_WRITE:
                    doWrite((IoParams) message.obj);
                    // Since only write will increase storage, eviction will be handled here.
                    return true;
                case MSG_RELEASE:
                    doRelease((ConditionVariable) message.obj);
                    return true;
            }
        } catch (IOException e) {
            mIoCallback.onIoError();
            mErrorNotified = true;
            Log.e(TAG, "IoException happened", e);
            return true;
        }
        return false;
    }

    private void doOpenRead(IoParams params) throws IOException {
        int index = params.index;
        mIoHandler.removeMessages(MSG_READ, index);
        Pair<SampleChunk, Integer> readPosition =
                mBufferManager.getReadFile(mIds.get(index), params.positionUs);
        if (readPosition == null) {
            String errorMessage = "Chunk ID:" + mIds.get(index) + " pos:" + params.positionUs
                    + "is not found";
            SoftPreconditions.checkNotNull(readPosition, TAG, errorMessage);
            throw new IOException(errorMessage);
        }
        mSelectedTracks.add(index);
        mReadIoStates[index].openRead(readPosition.first, (long) readPosition.second);
        if (mHandlerReadSampleBuffers[index] != null) {
            SampleHolder sample;
            while ((sample = mHandlerReadSampleBuffers[index].poll()) != null) {
                mSamplePool.releaseSample(sample);
            }
        }
        mHandlerReadSampleBuffers[index] = params.readSampleBuffer;
        mIoHandler.sendMessage(mIoHandler.obtainMessage(MSG_READ, index));
    }

    private void doOpenWrite(int index) throws IOException {
        SampleChunk chunk = mBufferManager.createNewWriteFileIfNeeded(mIds.get(index), 0,
                mSamplePool, null, 0);
        mWriteIoStates[index].openWrite(chunk);
    }

    private void doCloseRead(int index) {
        mSelectedTracks.remove(index);
        if (mHandlerReadSampleBuffers[index] != null) {
            SampleHolder sample;
            while ((sample = mHandlerReadSampleBuffers[index].poll()) != null) {
                mSamplePool.releaseSample(sample);
            }
        }
        mIoHandler.removeMessages(MSG_READ, index);
    }

    private void doRead(int index) throws IOException {
        mIoHandler.removeMessages(MSG_READ, index);
        if (mHandlerReadSampleBuffers[index].size() >= MAX_READ_BUFFER_SAMPLES) {
            // If enough samples are buffered, try again few moments later hoping that
            // buffered samples are consumed.
            mIoHandler.sendMessageDelayed(
                    mIoHandler.obtainMessage(MSG_READ, index), READ_RESCHEDULING_DELAY_MS);
        } else {
            if (mReadIoStates[index].isReadFinished()) {
                for (int i = 0; i < mTrackCount; ++i) {
                    if (!mReadIoStates[i].isReadFinished()) {
                        return;
                    }
                }
                mIoCallback.onIoReachedEos();
                return;
            }
            SampleHolder sample = mReadIoStates[index].read();
            if (sample != null) {
                mHandlerReadSampleBuffers[index].offer(sample);
            } else {
                // Read reached write but write is not finished yet --- wait a few moments to
                // see if another sample is written.
                mIoHandler.sendMessageDelayed(
                        mIoHandler.obtainMessage(MSG_READ, index),
                        READ_RESCHEDULING_DELAY_MS);
            }
        }
    }

    private void doWrite(IoParams params) throws IOException {
        try {
            if (mWriteEnded) {
                SoftPreconditions.checkState(false);
                return;
            }
            int index = params.index;
            SampleHolder sample = params.sample;
            SampleChunk nextChunk = null;
            if ((sample.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                if (sample.timeUs > mBufferDurationUs) {
                    mBufferDurationUs = sample.timeUs;
                }
                if (sample.timeUs >= mWriteIndexEndPositionUs[index]) {
                    SampleChunk currentChunk = sample.timeUs >= mWriteChunkEndPositionUs[index] ?
                            null : mWriteIoStates[params.index].getChunk();
                    int currentOffset = (int) mWriteIoStates[params.index].getOffset();
                    nextChunk = mBufferManager.createNewWriteFileIfNeeded(
                            mIds.get(index), mWriteIndexEndPositionUs[index], mSamplePool,
                            currentChunk, currentOffset);
                    mWriteIndexEndPositionUs[index] =
                            ((sample.timeUs / RecordingSampleBuffer.MIN_SEEK_DURATION_US) + 1) *
                                    RecordingSampleBuffer.MIN_SEEK_DURATION_US;
                    if (nextChunk != null) {
                        mWriteChunkEndPositionUs[index] =
                                ((sample.timeUs / mSampleChunkDurationUs) + 1)
                                        * mSampleChunkDurationUs;
                    }
                }
            }
            mWriteIoStates[params.index].write(params.sample, nextChunk);
        } finally {
            params.conditionVariable.open();
        }
    }

    private void doCloseWrite() throws IOException {
        if (mWriteEnded) {
            return;
        }
        mWriteEnded = true;
        boolean readFinished = true;
        for (int i = 0; i < mTrackCount; ++i) {
            readFinished = readFinished && mReadIoStates[i].isReadFinished();
            mWriteIoStates[i].closeWrite();
        }
        if (readFinished) {
            mIoCallback.onIoReachedEos();
        }
    }

    private void doRelease(ConditionVariable conditionVariable) {
        mIoHandler.removeCallbacksAndMessages(null);
        mFinished = true;
        conditionVariable.open();
        mSelectedTracks.clear();
    }

    private void releaseEvictedChunks() {
        if (mBufferReason != RecordingSampleBuffer.BUFFER_REASON_LIVE_PLAYBACK
                || mSelectedTracks.isEmpty()) {
            return;
        }
        long currentStartPositionUs = Long.MAX_VALUE;
        for (int trackIndex : mSelectedTracks) {
            currentStartPositionUs = Math.min(currentStartPositionUs,
                    mReadIoStates[trackIndex].getStartPositionUs());
        }
        for (int i = 0; i < mTrackCount; ++i) {
            long evictEndPositionUs = Math.min(mBufferManager.getStartPositionUs(mIds.get(i)),
                    currentStartPositionUs);
            mBufferManager.evictChunks(mIds.get(i), evictEndPositionUs);
        }
    }
}