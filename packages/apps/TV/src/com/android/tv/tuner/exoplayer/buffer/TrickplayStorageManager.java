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

import android.content.Context;
import android.os.AsyncTask;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.util.Pair;

import com.android.tv.common.SoftPreconditions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

/**
 * Manages Trickplay storage.
 */
public class TrickplayStorageManager implements BufferManager.StorageManager {
    // TODO: Support multi-sessions.
    private static final String BUFFER_DIR = "timeshift";

    // Copied from android.provider.Settings.Global (hidden fields)
    private static final String
            SYS_STORAGE_THRESHOLD_PERCENTAGE = "sys_storage_threshold_percentage";
    private static final String
            SYS_STORAGE_THRESHOLD_MAX_BYTES = "sys_storage_threshold_max_bytes";

    // Copied from android.os.StorageManager
    private static final int DEFAULT_THRESHOLD_PERCENTAGE = 10;
    private static final long DEFAULT_THRESHOLD_MAX_BYTES = 500L * 1024 * 1024;

    private static AsyncTask<Void, Void, Void> sLastCacheCleanUpTask;
    private static File sBufferDir;
    private static long sStorageBufferBytes;

    private final long mMaxBufferSize;

    private static void initParamsIfNeeded(Context context, @NonNull File path) {
        // TODO: Support multi-sessions.
        SoftPreconditions.checkState(
                sBufferDir == null || sBufferDir.equals(path));
        if (path.equals(sBufferDir)) {
            return;
        }
        sBufferDir = path;
        long lowPercentage = Settings.Global.getInt(context.getContentResolver(),
                SYS_STORAGE_THRESHOLD_PERCENTAGE, DEFAULT_THRESHOLD_PERCENTAGE);
        long lowPercentageToBytes = path.getTotalSpace() * lowPercentage / 100;
        long maxLowBytes = Settings.Global.getLong(context.getContentResolver(),
                SYS_STORAGE_THRESHOLD_MAX_BYTES, DEFAULT_THRESHOLD_MAX_BYTES);
        sStorageBufferBytes = Math.min(lowPercentageToBytes, maxLowBytes);
    }

    public TrickplayStorageManager(Context context, @NonNull File baseDir, long maxBufferSize) {
        initParamsIfNeeded(context, new File(baseDir, BUFFER_DIR));
        sBufferDir.mkdirs();
        mMaxBufferSize = maxBufferSize;
        clearStorage();
    }

    private void clearStorage() {
        long now = System.currentTimeMillis();
        if (sLastCacheCleanUpTask != null) {
            sLastCacheCleanUpTask.cancel(true);
        }
        sLastCacheCleanUpTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (isCancelled()) {
                    return null;
                }
                File files[] = sBufferDir.listFiles();
                if (files == null || files.length == 0) {
                    return null;
                }
                for (File file : files) {
                    if (isCancelled()) {
                        break;
                    }
                    long lastModified = file.lastModified();
                    if (lastModified != 0 && lastModified < now) {
                        file.delete();
                    }
                }
                return null;
            }
        };
        sLastCacheCleanUpTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public File getBufferDir() {
        return sBufferDir;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean reachedStorageMax(long bufferSize, long pendingDelete) {
        return bufferSize - pendingDelete > mMaxBufferSize;
    }

    @Override
    public boolean hasEnoughBuffer(long pendingDelete) {
        return sBufferDir.getUsableSpace() + pendingDelete >= sStorageBufferBytes;
    }

    @Override
    public List<BufferManager.TrackFormat> readTrackInfoFiles(boolean isAudio) {
        return null;
    }

    @Override
    public ArrayList<BufferManager.PositionHolder> readIndexFile(String trackId) {
        return null;
    }

    @Override
    public void writeTrackInfoFiles(List<BufferManager.TrackFormat> formatList, boolean isAudio) {
    }

    @Override
    public void writeIndexFile(String trackName,
            SortedMap<Long, Pair<SampleChunk, Integer>> index) {
    }

}
