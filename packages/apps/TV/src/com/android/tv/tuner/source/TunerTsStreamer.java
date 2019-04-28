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

package com.android.tv.tuner.source;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.upstream.DataSpec;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.ChannelScanFileParser;
import com.android.tv.tuner.TunerHal;
import com.android.tv.tuner.TunerPreferences;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.tvinput.EventDetector;
import com.android.tv.tuner.tvinput.EventDetector.EventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides MPEG-2 TS stream sources for channel playing from an underlying tuner device.
 */
public class TunerTsStreamer implements TsStreamer {
    private static final String TAG = "TunerTsStreamer";

    private static final int MIN_READ_UNIT = 1500;
    private static final int READ_BUFFER_SIZE = MIN_READ_UNIT * 10; // ~15KB
    private static final int CIRCULAR_BUFFER_SIZE = MIN_READ_UNIT * 20000;  // ~ 30MB
    private static final int TS_PACKET_SIZE = 188;

    private static final int READ_TIMEOUT_MS = 5000; // 5 secs.
    private static final int BUFFER_UNDERRUN_SLEEP_MS = 10;
    private static final int READ_ERROR_STREAMING_ENDED = -1;
    private static final int READ_ERROR_BUFFER_OVERWRITTEN = -2;

    private final Object mCircularBufferMonitor = new Object();
    private final byte[] mCircularBuffer = new byte[CIRCULAR_BUFFER_SIZE];
    private long mBytesFetched;
    private final AtomicLong mLastReadPosition = new AtomicLong();
    private boolean mStreaming;

    private final TunerHal mTunerHal;
    private TunerChannel mChannel;
    private Thread mStreamingThread;
    private final EventDetector mEventDetector;
    private final List<Pair<EventListener, Boolean>> mEventListenerActions = new ArrayList<>();

    private final TsStreamWriter mTsStreamWriter;
    private String mChannelNumber;

    public static class TunerDataSource extends TsDataSource {
        private final TunerTsStreamer mTsStreamer;
        private final AtomicLong mLastReadPosition = new AtomicLong(0);
        private long mStartBufferedPosition;

        private TunerDataSource(TunerTsStreamer tsStreamer) {
            mTsStreamer = tsStreamer;
            mStartBufferedPosition = tsStreamer.getBufferedPosition();
        }

        @Override
        public long getBufferedPosition() {
            return mTsStreamer.getBufferedPosition() - mStartBufferedPosition;
        }

        @Override
        public long getLastReadPosition() {
            return mLastReadPosition.get();
        }

        @Override
        public void shiftStartPosition(long offset) {
            SoftPreconditions.checkState(mLastReadPosition.get() == 0);
            SoftPreconditions.checkArgument(0 <= offset && offset <= getBufferedPosition());
            mStartBufferedPosition += offset;
        }

        @Override
        public long open(DataSpec dataSpec) throws IOException {
            mLastReadPosition.set(0);
            return C.LENGTH_UNBOUNDED;
        }

        @Override
        public void close() {
        }

        @Override
        public int read(byte[] buffer, int offset, int readLength) throws IOException {
            int ret = mTsStreamer.readAt(mStartBufferedPosition + mLastReadPosition.get(), buffer,
                    offset, readLength);
            if (ret > 0) {
                mLastReadPosition.addAndGet(ret);
            } else if (ret == READ_ERROR_BUFFER_OVERWRITTEN) {
                long currentPosition = mStartBufferedPosition + mLastReadPosition.get();
                long endPosition = mTsStreamer.getBufferedPosition();
                long diff = ((endPosition - currentPosition + TS_PACKET_SIZE - 1) / TS_PACKET_SIZE)
                        * TS_PACKET_SIZE;
                Log.w(TAG, "Demux position jump by overwritten buffer: " + diff);
                mStartBufferedPosition = currentPosition + diff;
                mLastReadPosition.set(0);
                return 0;
            }
            return ret;
        }
    }
    /**
     * Creates {@link TsStreamer} for playing or recording the specified channel.
     * @param tunerHal the HAL for tuner device
     * @param eventListener the listener for channel & program information
     */
    public TunerTsStreamer(TunerHal tunerHal, EventListener eventListener, Context context) {
        mTunerHal = tunerHal;
        mEventDetector = new EventDetector(mTunerHal);
        if (eventListener != null) {
            mEventDetector.registerListener(eventListener);
        }
        mTsStreamWriter = context != null && TunerPreferences.getStoreTsStream(context) ?
                new TsStreamWriter(context) : null;
    }

    public TunerTsStreamer(TunerHal tunerHal, EventListener eventListener) {
        this(tunerHal, eventListener, null);
    }

    @Override
    public boolean startStream(TunerChannel channel) {
        if (mTunerHal.tune(channel.getFrequency(), channel.getModulation(),
                channel.getDisplayNumber(false))) {
            if (channel.hasVideo()) {
                mTunerHal.addPidFilter(channel.getVideoPid(),
                        TunerHal.FILTER_TYPE_VIDEO);
            }
            boolean audioFilterSet = false;
            for (Integer audioPid : channel.getAudioPids()) {
                if (!audioFilterSet) {
                    mTunerHal.addPidFilter(audioPid, TunerHal.FILTER_TYPE_AUDIO);
                    audioFilterSet = true;
                } else {
                    // FILTER_TYPE_AUDIO overrides the previous filter for audio. We use
                    // FILTER_TYPE_OTHER from the secondary one to get the all audio tracks.
                    mTunerHal.addPidFilter(audioPid, TunerHal.FILTER_TYPE_OTHER);
                }
            }
            mTunerHal.addPidFilter(channel.getPcrPid(),
                    TunerHal.FILTER_TYPE_PCR);
            if (mEventDetector != null) {
                mEventDetector.startDetecting(channel.getFrequency(), channel.getModulation(),
                        channel.getProgramNumber());
            }
            mChannel = channel;
            mChannelNumber = channel.getDisplayNumber();
            synchronized (mCircularBufferMonitor) {
                if (mStreaming) {
                    Log.w(TAG, "Streaming should be stopped before start streaming");
                    return true;
                }
                mStreaming = true;
                mBytesFetched = 0;
                mLastReadPosition.set(0L);
            }
            if (mTsStreamWriter != null) {
                mTsStreamWriter.setChannel(mChannel);
                mTsStreamWriter.openFile();
            }
            mStreamingThread = new StreamingThread();
            mStreamingThread.start();
            Log.i(TAG, "Streaming started");
            return true;
        }
        return false;
    }

    @Override
    public boolean startStream(ChannelScanFileParser.ScanChannel channel) {
        if (mTunerHal.tune(channel.frequency, channel.modulation, null)) {
            mEventDetector.startDetecting(
                    channel.frequency, channel.modulation, EventDetector.ALL_PROGRAM_NUMBERS);
            synchronized (mCircularBufferMonitor) {
                if (mStreaming) {
                    Log.w(TAG, "Streaming should be stopped before start streaming");
                    return true;
                }
                mStreaming = true;
                mBytesFetched = 0;
                mLastReadPosition.set(0L);
            }
            mStreamingThread = new StreamingThread();
            mStreamingThread.start();
            Log.i(TAG, "Streaming started");
            return true;
        }
        return false;
    }

    /**
     * Blocks the current thread until the streaming thread stops. In rare cases when the tuner
     * device is overloaded this can take a while, but usually it returns pretty quickly.
     */
    @Override
    public void stopStream() {
        mChannel = null;
        synchronized (mCircularBufferMonitor) {
            mStreaming = false;
            mCircularBufferMonitor.notifyAll();
        }

        try {
            if (mStreamingThread != null) {
                mStreamingThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (mTsStreamWriter != null) {
            mTsStreamWriter.closeFile(true);
            mTsStreamWriter.setChannel(null);
        }
    }

    @Override
    public TsDataSource createDataSource() {
        return new TunerDataSource(this);
    }

    /**
     * Returns incomplete channel lists which was scanned so far. Incomplete channel means
     * the channel whose channel information is not complete or is not well-formed.
     * @return {@link List} of {@link TunerChannel}
     */
    public List<TunerChannel> getMalFormedChannels() {
        return mEventDetector.getMalFormedChannels();
    }

    /**
     * Returns the current {@link TunerHal} which provides MPEG-TS stream for TunerTsStreamer.
     * @return {@link TunerHal}
     */
    public TunerHal getTunerHal() {
        return mTunerHal;
    }

    /**
     * Returns the current tuned channel for TunerTsStreamer.
     * @return {@link TunerChannel}
     */
    public TunerChannel getChannel() {
        return mChannel;
    }

    /**
     * Returns the current buffered position from tuner.
     * @return the current buffered position
     */
    public long getBufferedPosition() {
        synchronized (mCircularBufferMonitor) {
            return mBytesFetched;
        }
    }

    public String getStreamerInfo() {
        return "Channel: " + mChannelNumber + ", Streaming: " + mStreaming;
    }

    public void registerListener(EventListener listener) {
        if (mEventDetector != null && listener != null) {
            synchronized (mEventListenerActions) {
                mEventListenerActions.add(new Pair<>(listener, true));
            }
        }
    }

    public void unregisterListener(EventListener listener) {
        if (mEventDetector != null) {
            synchronized (mEventListenerActions) {
                mEventListenerActions.add(new Pair(listener, false));
            }
        }
    }

    private class StreamingThread extends Thread {
        @Override
        public void run() {
            // Buffers for streaming data from the tuner and the internal buffer.
            byte[] dataBuffer = new byte[READ_BUFFER_SIZE];

            while (true) {
                synchronized (mCircularBufferMonitor) {
                    if (!mStreaming) {
                        break;
                    }
                }

                if (mEventDetector != null) {
                    synchronized (mEventListenerActions) {
                        for (Pair listenerAction : mEventListenerActions) {
                            EventListener listener = (EventListener) listenerAction.first;
                            if ((boolean) listenerAction.second) {
                                mEventDetector.registerListener(listener);
                            } else {
                                mEventDetector.unregisterListener(listener);
                            }
                        }
                        mEventListenerActions.clear();
                    }
                }

                int bytesWritten = mTunerHal.readTsStream(dataBuffer, dataBuffer.length);
                if (bytesWritten <= 0) {
                    try {
                        // When buffer is underrun, we sleep for short time to prevent
                        // unnecessary CPU draining.
                        sleep(BUFFER_UNDERRUN_SLEEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    continue;
                }

                if (mTsStreamWriter != null) {
                    mTsStreamWriter.writeToFile(dataBuffer, bytesWritten);
                }

                if (mEventDetector != null) {
                    mEventDetector.feedTSStream(dataBuffer, 0, bytesWritten);
                }
                synchronized (mCircularBufferMonitor) {
                    int posInBuffer = (int) (mBytesFetched % CIRCULAR_BUFFER_SIZE);
                    int bytesToCopyInFirstPass = bytesWritten;
                    if (posInBuffer + bytesToCopyInFirstPass > mCircularBuffer.length) {
                        bytesToCopyInFirstPass = mCircularBuffer.length - posInBuffer;
                    }
                    System.arraycopy(dataBuffer, 0, mCircularBuffer, posInBuffer,
                            bytesToCopyInFirstPass);
                    if (bytesToCopyInFirstPass < bytesWritten) {
                        System.arraycopy(dataBuffer, bytesToCopyInFirstPass, mCircularBuffer, 0,
                                bytesWritten - bytesToCopyInFirstPass);
                    }
                    mBytesFetched += bytesWritten;
                    mCircularBufferMonitor.notifyAll();
                }
            }

            Log.i(TAG, "Streaming stopped");
        }
    }

    /**
     * Reads data from internal buffer.
     * @param pos the position to read from
     * @param buffer to read
     * @param offset start position of the read buffer
     * @param amount number of bytes to read
     * @return number of read bytes when successful, {@code -1} otherwise
     * @throws IOException
     */
    public int readAt(long pos, byte[] buffer, int offset, int amount) throws IOException {
        while (true) {
            synchronized (mCircularBufferMonitor) {
                if (!mStreaming) {
                    return READ_ERROR_STREAMING_ENDED;
                }
                if (mBytesFetched - CIRCULAR_BUFFER_SIZE > pos) {
                    Log.w(TAG, "Demux is requesting the data which is already overwritten.");
                    return READ_ERROR_BUFFER_OVERWRITTEN;
                }
                if (mBytesFetched < pos + amount) {
                    try {
                        mCircularBufferMonitor.wait(READ_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    // Try again to prevent starvation.
                    // Give chances to read from other threads.
                    continue;
                }
                int startPos = (int) (pos % CIRCULAR_BUFFER_SIZE);
                int endPos = (int) ((pos + amount) % CIRCULAR_BUFFER_SIZE);
                int firstLength = (startPos > endPos ? CIRCULAR_BUFFER_SIZE : endPos) - startPos;
                System.arraycopy(mCircularBuffer, startPos, buffer, offset, firstLength);
                if (firstLength < amount) {
                    System.arraycopy(mCircularBuffer, 0, buffer, offset + firstLength,
                            amount - firstLength);
                }
                mCircularBufferMonitor.notifyAll();
                return amount;
            }
        }
    }
}
