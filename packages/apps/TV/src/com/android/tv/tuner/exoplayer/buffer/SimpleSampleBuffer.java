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

package com.android.tv.tuner.exoplayer.buffer;

import android.os.ConditionVariable;

import android.support.annotation.NonNull;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.tvinput.PlaybackBufferListener;
import com.android.tv.tuner.exoplayer.SampleExtractor;

import java.io.IOException;
import java.util.List;

/**
 * Handles I/O for {@link SampleExtractor} when
 * physical storage based buffer is not used. Trickplay is disabled.
 */
public class SimpleSampleBuffer implements BufferManager.SampleBuffer {
    private final SamplePool mSamplePool = new SamplePool();
    private SampleQueue[] mPlayingSampleQueues;
    private long mLastBufferedPositionUs = C.UNKNOWN_TIME_US;

    private volatile boolean mEos;

    public SimpleSampleBuffer(PlaybackBufferListener bufferListener) {
        if (bufferListener != null) {
            // Disables trickplay.
            bufferListener.onBufferStateChanged(false);
        }
    }

    @Override
    public synchronized void init(@NonNull List<String> ids,
            @NonNull List<MediaFormat> mediaFormats) {
        int trackCount = ids.size();
        mPlayingSampleQueues = new SampleQueue[trackCount];
        for (int i = 0; i < trackCount; i++) {
            mPlayingSampleQueues[i] = null;
        }
    }

    @Override
    public void setEos() {
        mEos = true;
    }

    private boolean reachedEos() {
        return mEos;
    }

    @Override
    public void selectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] == null) {
                mPlayingSampleQueues[index] = new SampleQueue(mSamplePool);
            } else {
                mPlayingSampleQueues[index].clear();
            }
        }
    }

    @Override
    public void deselectTrack(int index) {
        synchronized (this) {
            if (mPlayingSampleQueues[index] != null) {
                mPlayingSampleQueues[index].clear();
                mPlayingSampleQueues[index] = null;
            }
        }
    }

    @Override
    public synchronized long getBufferedPositionUs() {
        Long result = null;
        for (SampleQueue queue : mPlayingSampleQueues) {
            if (queue == null) {
                continue;
            }
            Long lastQueuedSamplePositionUs = queue.getLastQueuedPositionUs();
            if (lastQueuedSamplePositionUs == null) {
                // No sample has been queued.
                result = mLastBufferedPositionUs;
                continue;
            }
            if (result == null || result > lastQueuedSamplePositionUs) {
                result = lastQueuedSamplePositionUs;
            }
        }
        if (result == null) {
            return mLastBufferedPositionUs;
        }
        return (mLastBufferedPositionUs = result);
    }

    @Override
    public synchronized int readSample(int track, SampleHolder sampleHolder) {
        SampleQueue queue = mPlayingSampleQueues[track];
        SoftPreconditions.checkNotNull(queue);
        int result = queue == null ? SampleSource.NOTHING_READ : queue.dequeueSample(sampleHolder);
        if (result != SampleSource.SAMPLE_READ && reachedEos()) {
            return SampleSource.END_OF_STREAM;
        }
        return result;
    }

    @Override
    public void writeSample(int index, SampleHolder sample,
            ConditionVariable conditionVariable) throws IOException {
        sample.data.position(0).limit(sample.size);
        SampleHolder sampleToQueue = mSamplePool.acquireSample(sample.size);
        sampleToQueue.size = sample.size;
        sampleToQueue.clearData();
        sampleToQueue.data.put(sample.data);
        sampleToQueue.timeUs = sample.timeUs;
        sampleToQueue.flags = sample.flags;

        synchronized (this) {
            if (mPlayingSampleQueues[index] != null) {
                mPlayingSampleQueues[index].queueSample(sampleToQueue);
            }
        }
    }

    @Override
    public boolean isWriteSpeedSlow(int sampleSize, long durationNs) {
        // Since SimpleSampleBuffer write samples only to memory (not to physical storage),
        // write speed is always fine.
        return false;
    }

    @Override
    public void handleWriteSpeedSlow() {
        // no-op
    }

    @Override
    public synchronized boolean continueBuffering(long positionUs) {
        for (SampleQueue queue : mPlayingSampleQueues) {
            if (queue == null) {
                continue;
            }
            if (queue.getLastQueuedPositionUs() == null
                    || positionUs > queue.getLastQueuedPositionUs()) {
                // No more buffered data.
                return false;
            }
        }
        return true;
    }

    @Override
    public void seekTo(long positionUs) {
        // Not used.
    }

    @Override
    public void release() {
        // Not used.
    }
}
