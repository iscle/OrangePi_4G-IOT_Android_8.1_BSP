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

package com.android.documentsui.clipping;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.VisibleForTesting;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.android.documentsui.base.Files;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;

/**
 * Provides support for storing lists of documents identified by Uri.
 *
 * This class uses a ring buffer to recycle clip file slots, to mitigate the issue of clip file
 * deletions. Below is the directory layout:
 * [cache dir]
 *      - [dir] 1
 *      - [dir] 2
 *      - ... to {@link #NUM_OF_SLOTS}
 * When a clip data is actively being used:
 * [cache dir]
 *      - [dir] 1
 *          - [file] primary
 *          - [symlink] 1 > primary # copying to location X
 *          - [symlink] 2 > primary # copying to location Y
 */
public final class ClipStorage implements ClipStore {

    public static final int NO_SELECTION_TAG = -1;

    public static final String PREF_NAME = "ClipStoragePref";

    @VisibleForTesting
    static final int NUM_OF_SLOTS = 20;

    private static final String TAG = "ClipStorage";

    private static final long STALENESS_THRESHOLD = TimeUnit.DAYS.toMillis(2);

    private static final String NEXT_AVAIL_SLOT = "NextAvailableSlot";
    private static final String PRIMARY_DATA_FILE_NAME = "primary";

    private static final byte[] LINE_SEPARATOR = System.lineSeparator().getBytes();

    private final File mOutDir;
    private final SharedPreferences mPref;

    private final File[] mSlots = new File[NUM_OF_SLOTS];
    private int mNextSlot;

    /**
     * @param outDir see {@link #prepareStorage(File)}.
     */
    public ClipStorage(File outDir, SharedPreferences pref) {
        assert(outDir.isDirectory());
        mOutDir = outDir;
        mPref = pref;

        mNextSlot = mPref.getInt(NEXT_AVAIL_SLOT, 0);
    }

    /**
     * Tries to get the next available clip slot. It's guaranteed to return one. If none of
     * slots is available, it returns the next slot of the most recently returned slot by this
     * method.
     *
     * <p>This is not a perfect solution, but should be enough for most regular use. There are
     * several situations this method may not work:
     * <ul>
     *     <li>Making {@link #NUM_OF_SLOTS} - 1 times of large drag and drop or moveTo/copyTo/delete
     *     operations after cutting a primary clip, then the primary clip is overwritten.</li>
     *     <li>Having more than {@link #NUM_OF_SLOTS} queued jumbo file operations, one or more clip
     *     file may be overwritten.</li>
     * </ul>
     *
     * Implementations should take caution to serialize access.
     */
    @VisibleForTesting
    synchronized int claimStorageSlot() {
        int curSlot = mNextSlot;
        for (int i = 0; i < NUM_OF_SLOTS; ++i, curSlot = (curSlot + 1) % NUM_OF_SLOTS) {
            createSlotFileObject(curSlot);

            if (!mSlots[curSlot].exists()) {
                break;
            }

            // No file or only primary file exists, we deem it available.
            if (mSlots[curSlot].list().length <= 1) {
                break;
            }
            // This slot doesn't seem available, but still need to check if it's a legacy of
            // service being killed or a service crash etc. If it's stale, it's available.
            else if (checkStaleFiles(curSlot)) {
                break;
            }
        }

        prepareSlot(curSlot);

        mNextSlot = (curSlot + 1) % NUM_OF_SLOTS;
        mPref.edit().putInt(NEXT_AVAIL_SLOT, mNextSlot).commit();
        return curSlot;
    }

    private boolean checkStaleFiles(int pos) {
        File slotData = toSlotDataFile(pos);

        // No need to check if the file exists. File.lastModified() returns 0L if the file doesn't
        // exist.
        return slotData.lastModified() + STALENESS_THRESHOLD <= System.currentTimeMillis();
    }

    private void prepareSlot(int pos) {
        assert(mSlots[pos] != null);

        Files.deleteRecursively(mSlots[pos]);
        mSlots[pos].mkdir();
        assert(mSlots[pos].isDirectory());
    }

    /**
     * Returns a writer. Callers must close the writer when finished.
     */
    private Writer createWriter(int slot) throws IOException {
        File file = toSlotDataFile(slot);
        return new Writer(file);
    }

    @Override
    public synchronized File getFile(int slot) throws IOException {
        createSlotFileObject(slot);

        File primary = toSlotDataFile(slot);

        String linkFileName = Integer.toString(mSlots[slot].list().length);
        File link = new File(mSlots[slot], linkFileName);

        try {
            Os.symlink(primary.getAbsolutePath(), link.getAbsolutePath());
        } catch (ErrnoException e) {
            e.rethrowAsIOException();
        }
        return link;
    }

    @Override
    public ClipStorageReader createReader(File file) throws IOException {
        assert(file.getParentFile().getParentFile().equals(mOutDir));
        return new ClipStorageReader(file);
    }

    private File toSlotDataFile(int pos) {
        assert(mSlots[pos] != null);
        return new File(mSlots[pos], PRIMARY_DATA_FILE_NAME);
    }

    private void createSlotFileObject(int pos) {
        if (mSlots[pos] == null) {
            mSlots[pos] = new File(mOutDir, Integer.toString(pos));
        }
    }

    /**
     * Provides initialization of the clip data storage directory.
     */
    public static File prepareStorage(File cacheDir) {
        File clipDir = getClipDir(cacheDir);
        clipDir.mkdir();

        assert(clipDir.isDirectory());
        return clipDir;
    }

    private static File getClipDir(File cacheDir) {
        return new File(cacheDir, "clippings");
    }

    public static final class Writer implements Closeable {

        private final FileOutputStream mOut;
        private final FileLock mLock;

        private Writer(File file) throws IOException {
            assert(!file.exists());

            mOut = new FileOutputStream(file);

            // Lock the file here so copy tasks would wait until everything is flushed to disk
            // before start to run.
            mLock = mOut.getChannel().lock();
        }

        public void write(Uri uri) throws IOException {
            mOut.write(uri.toString().getBytes());
            mOut.write(LINE_SEPARATOR);
        }

        @Override
        public void close() throws IOException {
            if (mLock != null) {
                mLock.release();
            }

            if (mOut != null) {
                mOut.close();
            }
        }
    }

    @Override
    public int persistUris(Iterable<Uri> uris) {
        int slot = claimStorageSlot();
        persistUris(uris, slot);
        return slot;
    }

    @VisibleForTesting
    void persistUris(Iterable<Uri> uris, int slot) {
        new PersistTask(this, uris, slot).execute();
    }

    /**
     * An {@link AsyncTask} that persists doc uris in {@link ClipStorage}.
     */
    private static final class PersistTask extends AsyncTask<Void, Void, Void> {

        private final ClipStorage mClipStore;
        private final Iterable<Uri> mUris;
        private final int mSlot;

        PersistTask(ClipStorage clipStore, Iterable<Uri> uris, int slot) {
            mClipStore = clipStore;
            mUris = uris;
            mSlot = slot;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try(Writer writer = mClipStore.createWriter(mSlot)){
                for (Uri uri: mUris) {
                    assert(uri != null);
                    writer.write(uri);
                }
            } catch (IOException e) {
                Log.e(TAG, "Caught exception trying to write jumbo clip to disk.", e);
            }

            return null;
        }
    }
}
