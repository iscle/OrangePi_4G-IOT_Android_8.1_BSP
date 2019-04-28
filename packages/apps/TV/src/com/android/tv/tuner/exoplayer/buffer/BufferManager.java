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

import android.media.MediaFormat;
import android.os.ConditionVariable;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer.SampleHolder;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.tuner.exoplayer.SampleExtractor;
import com.android.tv.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Manages {@link SampleChunk} objects.
 * <p>
 * The buffer manager can be disabled, while running, if the write throughput to the associated
 * external storage is detected to be lower than a threshold {@code MINIMUM_DISK_WRITE_SPEED_MBPS}".
 * This leads to restarting playback flow.
 */
public class BufferManager {
    private static final String TAG = "BufferManager";
    private static final boolean DEBUG = false;

    // Constants for the disk write speed checking
    private static final long MINIMUM_WRITE_SIZE_FOR_SPEED_CHECK =
            10L * 1024 * 1024;  // Checks for every 10M disk write
    private static final int MINIMUM_SAMPLE_SIZE_FOR_SPEED_CHECK = 15 * 1024;
    private static final int MAXIMUM_SPEED_CHECK_COUNT = 5;  // Checks only 5 times
    private static final int MINIMUM_DISK_WRITE_SPEED_MBPS = 3;  // 3 Megabytes per second

    private final SampleChunk.SampleChunkCreator mSampleChunkCreator;
    // Maps from track name to a map which maps from starting position to {@link SampleChunk}.
    private final Map<String, SortedMap<Long, Pair<SampleChunk, Integer>>> mChunkMap =
            new ArrayMap<>();
    private final Map<String, Long> mStartPositionMap = new ArrayMap<>();
    private final Map<String, ChunkEvictedListener> mEvictListeners = new ArrayMap<>();
    private final StorageManager mStorageManager;
    private long mBufferSize = 0;
    private final EvictChunkQueueMap mPendingDelete = new EvictChunkQueueMap();
    private final SampleChunk.ChunkCallback mChunkCallback = new SampleChunk.ChunkCallback() {
        @Override
        public void onChunkWrite(SampleChunk chunk) {
            mBufferSize += chunk.getSize();
        }

        @Override
        public void onChunkDelete(SampleChunk chunk) {
            mBufferSize -= chunk.getSize();
        }
    };

    private int mMinSampleSizeForSpeedCheck = MINIMUM_SAMPLE_SIZE_FOR_SPEED_CHECK;
    private long mTotalWriteSize;
    private long mTotalWriteTimeNs;
    private float mWriteBandwidth = 0.0f;
    private volatile int mSpeedCheckCount;

    public interface ChunkEvictedListener {
        void onChunkEvicted(String id, long createdTimeMs);
    }
    /**
     * Handles I/O
     * between BufferManager and {@link SampleExtractor}.
     */
    public interface SampleBuffer {

        /**
         * Initializes SampleBuffer.
         * @param Ids track identifiers for storage read/write.
         * @param mediaFormats meta-data for each track.
         * @throws IOException
         */
        void init(@NonNull List<String> Ids,
                  @NonNull List<com.google.android.exoplayer.MediaFormat> mediaFormats)
                throws IOException;

        /**
         * Selects the track {@code index} for reading sample data.
         */
        void selectTrack(int index);

        /**
         * Deselects the track at {@code index},
         * so that no more samples will be read from the track.
         */
        void deselectTrack(int index);

        /**
         * Writes sample to storage.
         *
         * @param index track index
         * @param sample sample to write at storage
         * @param conditionVariable notifies the completion of writing sample.
         * @throws IOException
         */
        void writeSample(int index, SampleHolder sample, ConditionVariable conditionVariable)
                throws IOException;

        /**
         * Checks whether storage write speed is slow.
         */
        boolean isWriteSpeedSlow(int sampleSize, long writeDurationNs);

        /**
         * Handles when write speed is slow.
         * @throws IOException
         */
        void handleWriteSpeedSlow() throws IOException;

        /**
         * Sets the flag when EoS was reached.
         */
        void setEos();

        /**
         * Reads the next sample in the track at index {@code track} into {@code sampleHolder},
         * returning {@link com.google.android.exoplayer.SampleSource#SAMPLE_READ}
         * if it is available.
         * If the next sample is not available,
         * returns {@link com.google.android.exoplayer.SampleSource#NOTHING_READ}.
         */
        int readSample(int index, SampleHolder outSample);

        /**
         * Seeks to the specified time in microseconds.
         */
        void seekTo(long positionUs);

        /**
         * Returns an estimate of the position up to which data is buffered.
         */
        long getBufferedPositionUs();

        /**
         * Returns whether there is buffered data.
         */
        boolean continueBuffering(long positionUs);

        /**
         * Cleans up and releases everything.
         * @throws IOException
         */
        void release() throws IOException;
    }

    /**
     * A Track format which will be loaded and saved from the permanent storage for recordings.
     */
    public static class TrackFormat {

        /**
         * The track id for the specified track. The track id will be used as a track identifier
         * for recordings.
         */
        public final String trackId;

        /**
         * The {@link MediaFormat} for the specified track.
         */
        public final MediaFormat format;

        /**
         * Creates TrackFormat.
         * @param trackId
         * @param format
         */
        public TrackFormat(String trackId, MediaFormat format) {
            this.trackId = trackId;
            this.format = format;
        }
    }

    /**
     * A Holder for a sample position which will be loaded from the index file for recordings.
     */
    public static class PositionHolder {

        /**
         * The current sample position in microseconds.
         * The position is identical to the PTS(presentation time stamp) of the sample.
         */
        public final long positionUs;

        /**
         * Base sample position for the current {@link SampleChunk}.
         */
        public final long basePositionUs;

        /**
         * The file offset for the current sample in the current {@link SampleChunk}.
         */
        public final int offset;

        /**
         * Creates a holder for a specific position in the recording.
         * @param positionUs
         * @param offset
         */
        public PositionHolder(long positionUs, long basePositionUs, int offset) {
            this.positionUs = positionUs;
            this.basePositionUs = basePositionUs;
            this.offset = offset;
        }
    }

    /**
     * Storage configuration and policy manager for {@link BufferManager}
     */
    public interface StorageManager {

        /**
         * Provides eligible storage directory for {@link BufferManager}.
         *
         * @return a directory to save buffer(chunks) and meta files
         */
        File getBufferDir();

        /**
         * Informs whether the storage is used for persistent use. (eg. dvr recording/play)
         *
         * @return {@code true} if stored files are persistent
         */
        boolean isPersistent();

        /**
         * Informs whether the storage usage exceeds pre-determined size.
         *
         * @param bufferSize the current total usage of Storage in bytes.
         * @param pendingDelete the current storage usage which will be deleted in near future by
         *                      bytes
         * @return {@code true} if it reached pre-determined max size
         */
        boolean reachedStorageMax(long bufferSize, long pendingDelete);

        /**
         * Informs whether the storage has enough remained space.
         *
         * @param pendingDelete the current storage usage which will be deleted in near future by
         *                      bytes
         * @return {@code true} if it has enough space
         */
        boolean hasEnoughBuffer(long pendingDelete);

        /**
         * Reads track name & {@link MediaFormat} from storage.
         *
         * @param isAudio {@code true} if it is for audio track
         * @return {@link List} of TrackFormat
         */
        List<TrackFormat> readTrackInfoFiles(boolean isAudio);

        /**
         * Reads key sample positions for each written sample from storage.
         *
         * @param trackId track name
         * @return indexes of the specified track
         * @throws IOException
         */
        ArrayList<PositionHolder> readIndexFile(String trackId) throws IOException;

        /**
         * Writes track information to storage.
         *
         * @param formatList {@list List} of TrackFormat
         * @param isAudio {@code true} if it is for audio track
         * @throws IOException
         */
        void writeTrackInfoFiles(List<TrackFormat> formatList, boolean isAudio)
                throws IOException;

        /**
         * Writes index file to storage.
         *
         * @param trackName track name
         * @param index {@link SampleChunk} container
         * @throws IOException
         */
        void writeIndexFile(String trackName, SortedMap<Long, Pair<SampleChunk, Integer>> index)
                throws IOException;
    }

    private static class EvictChunkQueueMap {
        private final Map<String, LinkedList<SampleChunk>> mEvictMap = new ArrayMap<>();
        private long mSize;

        private void init(String key) {
            mEvictMap.put(key, new LinkedList<>());
        }

        private void add(String key, SampleChunk chunk) {
            LinkedList<SampleChunk> queue = mEvictMap.get(key);
            if (queue != null) {
                mSize += chunk.getSize();
                queue.add(chunk);
            }
        }

        private SampleChunk poll(String key, long startPositionUs) {
            LinkedList<SampleChunk> queue = mEvictMap.get(key);
            if (queue != null) {
                SampleChunk chunk = queue.peek();
                if (chunk != null && chunk.getStartPositionUs() < startPositionUs) {
                    mSize -= chunk.getSize();
                    return queue.poll();
                }
            }
            return null;
        }

        private long getSize() {
            return mSize;
        }

        private void release() {
            for (Map.Entry<String, LinkedList<SampleChunk>> entry : mEvictMap.entrySet()) {
                for (SampleChunk chunk : entry.getValue()) {
                    SampleChunk.IoState.release(chunk, true);
                }
            }
            mEvictMap.clear();
            mSize = 0;
        }
    }

    public BufferManager(StorageManager storageManager) {
        this(storageManager, new SampleChunk.SampleChunkCreator());
    }

    public BufferManager(StorageManager storageManager,
            SampleChunk.SampleChunkCreator sampleChunkCreator) {
        mStorageManager = storageManager;
        mSampleChunkCreator = sampleChunkCreator;
    }

    public void registerChunkEvictedListener(String id, ChunkEvictedListener listener) {
        mEvictListeners.put(id, listener);
    }

    public void unregisterChunkEvictedListener(String id) {
        mEvictListeners.remove(id);
    }

    private static String getFileName(String id, long positionUs) {
        return String.format(Locale.ENGLISH, "%s_%016x.chunk", id, positionUs);
    }

    /**
     * Creates a new {@link SampleChunk} for caching samples if it is needed.
     *
     * @param id the name of the track
     * @param positionUs current position to write a sample in micro seconds.
     * @param samplePool {@link SamplePool} for the fast creation of samples.
     * @param currentChunk the current {@link SampleChunk} to write, {@code null} when to create
     *                     a new {@link SampleChunk}.
     * @param currentOffset the current offset to write.
     * @return returns the created {@link SampleChunk}.
     * @throws IOException
     */
    public SampleChunk createNewWriteFileIfNeeded(String id, long positionUs, SamplePool samplePool,
            SampleChunk currentChunk, int currentOffset) throws IOException {
        if (!maybeEvictChunk()) {
            throw new IOException("Not enough storage space");
        }
        SortedMap<Long, Pair<SampleChunk, Integer>> map = mChunkMap.get(id);
        if (map == null) {
            map = new TreeMap<>();
            mChunkMap.put(id, map);
            mStartPositionMap.put(id, positionUs);
            mPendingDelete.init(id);
        }
        if (currentChunk == null) {
            File file = new File(mStorageManager.getBufferDir(), getFileName(id, positionUs));
            SampleChunk sampleChunk = mSampleChunkCreator
                    .createSampleChunk(samplePool, file, positionUs, mChunkCallback);
            map.put(positionUs, new Pair(sampleChunk, 0));
            return sampleChunk;
        } else {
            map.put(positionUs, new Pair(currentChunk, currentOffset));
            return null;
        }
    }

    /**
     * Loads a track using {@link BufferManager.StorageManager}.
     *
     * @param trackId the name of the track.
     * @param samplePool {@link SamplePool} for the fast creation of samples.
     * @throws IOException
     */
    public void loadTrackFromStorage(String trackId, SamplePool samplePool) throws IOException {
        ArrayList<PositionHolder> keyPositions = mStorageManager.readIndexFile(trackId);
        long startPositionUs = keyPositions.size() > 0 ? keyPositions.get(0).positionUs : 0;

        SortedMap<Long, Pair<SampleChunk, Integer>> map = mChunkMap.get(trackId);
        if (map == null) {
            map = new TreeMap<>();
            mChunkMap.put(trackId, map);
            mStartPositionMap.put(trackId, startPositionUs);
            mPendingDelete.init(trackId);
        }
        SampleChunk chunk = null;
        long basePositionUs = -1;
        for (PositionHolder position: keyPositions) {
            if (position.basePositionUs != basePositionUs) {
                chunk = mSampleChunkCreator.loadSampleChunkFromFile(samplePool,
                        mStorageManager.getBufferDir(), getFileName(trackId, position.positionUs),
                        position.positionUs, mChunkCallback, chunk);
                basePositionUs = position.basePositionUs;
            }
            map.put(position.positionUs, new Pair(chunk, position.offset));
        }
    }

    /**
     * Finds a {@link SampleChunk} for the specified track name and the position.
     *
     * @param id the name of the track.
     * @param positionUs the position.
     * @return returns the found {@link SampleChunk}.
     */
    public Pair<SampleChunk, Integer> getReadFile(String id, long positionUs) {
        SortedMap<Long, Pair<SampleChunk, Integer>> map = mChunkMap.get(id);
        if (map == null) {
            return null;
        }
        Pair<SampleChunk, Integer> ret;
        SortedMap<Long, Pair<SampleChunk, Integer>> headMap = map.headMap(positionUs + 1);
        if (!headMap.isEmpty()) {
            ret = headMap.get(headMap.lastKey());
        } else {
            ret = map.get(map.firstKey());
        }
        return ret;
    }

    /**
     * Evicts chunks which are ready to be evicted for the specified track
     *
     * @param id the specified track
     * @param earlierThanPositionUs the start position of the {@link SampleChunk}
     *                   should be earlier than
     */
    public void evictChunks(String id, long earlierThanPositionUs) {
        SampleChunk chunk = null;
        while ((chunk = mPendingDelete.poll(id, earlierThanPositionUs)) != null) {
            SampleChunk.IoState.release(chunk, !mStorageManager.isPersistent())  ;
        }
    }

    /**
     * Returns the start position of the specified track in micro seconds.
     *
     * @param id the specified track
     */
    public long getStartPositionUs(String id) {
        Long ret = mStartPositionMap.get(id);
        return ret == null ? 0 : ret;
    }

    private boolean maybeEvictChunk() {
        long pendingDelete = mPendingDelete.getSize();
        while (mStorageManager.reachedStorageMax(mBufferSize, pendingDelete)
                || !mStorageManager.hasEnoughBuffer(pendingDelete)) {
            if (mStorageManager.isPersistent()) {
                // Since chunks are persistent, we cannot evict chunks.
                return false;
            }
            SortedMap<Long, Pair<SampleChunk, Integer>> earliestChunkMap = null;
            SampleChunk earliestChunk = null;
            String earliestChunkId = null;
            for (Map.Entry<String, SortedMap<Long, Pair<SampleChunk, Integer>>> entry :
                    mChunkMap.entrySet()) {
                SortedMap<Long, Pair<SampleChunk, Integer>> map = entry.getValue();
                if (map.isEmpty()) {
                    continue;
                }
                SampleChunk chunk = map.get(map.firstKey()).first;
                if (earliestChunk == null
                        || chunk.getCreatedTimeMs() < earliestChunk.getCreatedTimeMs()) {
                    earliestChunkMap = map;
                    earliestChunk = chunk;
                    earliestChunkId = entry.getKey();
                }
            }
            if (earliestChunk == null) {
                break;
            }
            mPendingDelete.add(earliestChunkId, earliestChunk);
            earliestChunkMap.remove(earliestChunk.getStartPositionUs());
            if (DEBUG) {
                Log.d(TAG, String.format("bufferSize = %d; pendingDelete = %b; "
                                + "earliestChunk size = %d; %s@%d (%s)",
                        mBufferSize, pendingDelete, earliestChunk.getSize(), earliestChunkId,
                        earliestChunk.getStartPositionUs(),
                        Utils.toIsoDateTimeString(earliestChunk.getCreatedTimeMs())));
            }
            ChunkEvictedListener listener = mEvictListeners.get(earliestChunkId);
            if (listener != null) {
                listener.onChunkEvicted(earliestChunkId, earliestChunk.getCreatedTimeMs());
            }
            pendingDelete = mPendingDelete.getSize();
        }
        for (Map.Entry<String, SortedMap<Long, Pair<SampleChunk, Integer>>> entry :
                mChunkMap.entrySet()) {
            SortedMap<Long, Pair<SampleChunk, Integer>> map = entry.getValue();
            if (map.isEmpty()) {
                continue;
            }
            mStartPositionMap.put(entry.getKey(), map.firstKey());
        }
        return true;
    }

    /**
     * Reads track information which includes {@link MediaFormat}.
     *
     * @return returns all track information which is found by {@link BufferManager.StorageManager}.
     * @throws IOException
     */
    public List<TrackFormat> readTrackInfoFiles() throws IOException {
        List<TrackFormat> trackFormatList = new ArrayList<>();
        trackFormatList.addAll(mStorageManager.readTrackInfoFiles(false));
        trackFormatList.addAll(mStorageManager.readTrackInfoFiles(true));
        if (trackFormatList.isEmpty()) {
            throw new IOException("No track information to load");
        }
        return trackFormatList;
    }

    /**
     * Writes track information and index information for all tracks.
     *
     * @param audios list of audio track information
     * @param videos list of audio track information
     * @throws IOException
     */
    public void writeMetaFiles(List<TrackFormat> audios, List<TrackFormat> videos)
            throws IOException {
        if (audios.isEmpty() && videos.isEmpty()) {
            throw new IOException("No track information to save");
        }
        if (!audios.isEmpty()) {
            mStorageManager.writeTrackInfoFiles(audios, true);
            for (TrackFormat trackFormat : audios) {
                SortedMap<Long, Pair<SampleChunk, Integer>> map =
                        mChunkMap.get(trackFormat.trackId);
                if (map == null) {
                    throw new IOException("Audio track index missing");
                }
                mStorageManager.writeIndexFile(trackFormat.trackId, map);
            }
        }
        if (!videos.isEmpty()) {
            mStorageManager.writeTrackInfoFiles(videos, false);
            for (TrackFormat trackFormat : videos) {
                SortedMap<Long, Pair<SampleChunk, Integer>> map =
                        mChunkMap.get(trackFormat.trackId);
                if (map == null) {
                    throw new IOException("Video track index missing");
                }
                mStorageManager.writeIndexFile(trackFormat.trackId, map);
            }
        }
    }

    /**
     * Releases all the resources.
     */
    public void release() {
        try {
            mPendingDelete.release();
            for (Map.Entry<String, SortedMap<Long, Pair<SampleChunk, Integer>>> entry :
                    mChunkMap.entrySet()) {
                SampleChunk toRelease = null;
                for (Pair<SampleChunk, Integer> positions : entry.getValue().values()) {
                    if (toRelease != positions.first) {
                        toRelease = positions.first;
                        SampleChunk.IoState.release(toRelease, !mStorageManager.isPersistent());
                    }
                }
            }
            mChunkMap.clear();
        } catch (ConcurrentModificationException | NullPointerException e) {
            // TODO: remove this after it it confirmed that race condition issues are resolved.
            // b/32492258, b/32373376
            SoftPreconditions.checkState(false, "Exception on BufferManager#release: ",
                    e.toString());
        }
    }

    private void resetWriteStat(float writeBandwidth) {
        mWriteBandwidth = writeBandwidth;
        mTotalWriteSize = 0;
        mTotalWriteTimeNs = 0;
    }

    /**
     * Adds a disk write sample size to calculate the average disk write bandwidth.
     */
    public void addWriteStat(long size, long timeNs) {
        if (size >= mMinSampleSizeForSpeedCheck) {
            mTotalWriteSize += size;
            mTotalWriteTimeNs += timeNs;
        }
    }

    /**
     * Returns if the average disk write bandwidth is slower than
     * threshold {@code MINIMUM_DISK_WRITE_SPEED_MBPS}.
     */
    public boolean isWriteSlow() {
        if (mTotalWriteSize < MINIMUM_WRITE_SIZE_FOR_SPEED_CHECK) {
            return false;
        }

        // Checks write speed for only MAXIMUM_SPEED_CHECK_COUNT times to ignore outliers
        // by temporary system overloading during the playback.
        if (mSpeedCheckCount > MAXIMUM_SPEED_CHECK_COUNT) {
            return false;
        }
        mSpeedCheckCount++;
        float megabytePerSecond = calculateWriteBandwidth();
        resetWriteStat(megabytePerSecond);
        if (DEBUG) {
            Log.d(TAG, "Measured disk write performance: " + megabytePerSecond + "MBps");
        }
        return megabytePerSecond < MINIMUM_DISK_WRITE_SPEED_MBPS;
    }

    /**
     * Returns recent write bandwidth in MBps. If recent bandwidth is not available,
     * returns {float -1.0f}.
     */
    public float getWriteBandwidth() {
        return mWriteBandwidth == 0.0f ? -1.0f : mWriteBandwidth;
    }

    private float calculateWriteBandwidth() {
        if (mTotalWriteTimeNs == 0) {
            return -1;
        }
        return ((float) mTotalWriteSize * 1000 / mTotalWriteTimeNs);
    }

    /**
     * Returns if {@link BufferManager} has checked the write speed,
     * which is suitable for Trickplay.
     */
    @VisibleForTesting
    public boolean hasSpeedCheckDone() {
        return mSpeedCheckCount > 0;
    }

    /**
     * Sets minimum sample size for write speed check.
     * @param sampleSize minimum sample size for write speed check.
     */
    @VisibleForTesting
    public void setMinimumSampleSizeForSpeedCheck(int sampleSize) {
        mMinSampleSizeForSpeedCheck = sampleSize;
    }
}
