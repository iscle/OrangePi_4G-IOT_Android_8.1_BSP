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

package com.android.tv.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.AnyThread;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.LruCache;

import com.android.tv.common.MemoryManageable;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.util.AsyncDbTask;
import com.android.tv.util.Clock;
import com.android.tv.util.MultiLongSparseArray;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@MainThread
public class ProgramDataManager implements MemoryManageable {
    private static final String TAG = "ProgramDataManager";
    private static final boolean DEBUG = false;

    // To prevent from too many program update operations at the same time, we give random interval
    // between PERIODIC_PROGRAM_UPDATE_MIN_MS and PERIODIC_PROGRAM_UPDATE_MAX_MS.
    private static final long PERIODIC_PROGRAM_UPDATE_MIN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long PERIODIC_PROGRAM_UPDATE_MAX_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long PROGRAM_PREFETCH_UPDATE_WAIT_MS = TimeUnit.SECONDS.toMillis(5);
    // TODO: need to optimize consecutive DB updates.
    private static final long CURRENT_PROGRAM_UPDATE_WAIT_MS = TimeUnit.SECONDS.toMillis(5);
    @VisibleForTesting
    static final long PROGRAM_GUIDE_SNAP_TIME_MS = TimeUnit.MINUTES.toMillis(30);
    @VisibleForTesting
    static final long PROGRAM_GUIDE_MAX_TIME_RANGE = TimeUnit.DAYS.toMillis(2);

    // TODO: Use TvContract constants, once they become public.
    private static final String PARAM_START_TIME = "start_time";
    private static final String PARAM_END_TIME = "end_time";
    // COLUMN_CHANNEL_ID, COLUMN_END_TIME_UTC_MILLIS are added to detect duplicated programs.
    // Duplicated programs are always consecutive by the sorting order.
    private static final String SORT_BY_TIME = Programs.COLUMN_START_TIME_UTC_MILLIS + ", "
            + Programs.COLUMN_CHANNEL_ID + ", " + Programs.COLUMN_END_TIME_UTC_MILLIS;

    private static final int MSG_UPDATE_CURRENT_PROGRAMS = 1000;
    private static final int MSG_UPDATE_ONE_CURRENT_PROGRAM = 1001;
    private static final int MSG_UPDATE_PREFETCH_PROGRAM = 1002;

    private final Clock mClock;
    private final ContentResolver mContentResolver;
    private boolean mStarted;
    // Updated only on the main thread.
    private volatile boolean mCurrentProgramsLoadFinished;
    private ProgramsUpdateTask mProgramsUpdateTask;
    private final LongSparseArray<UpdateCurrentProgramForChannelTask> mProgramUpdateTaskMap =
            new LongSparseArray<>();
    private final Map<Long, Program> mChannelIdCurrentProgramMap = new ConcurrentHashMap<>();
    private final MultiLongSparseArray<OnCurrentProgramUpdatedListener>
            mChannelId2ProgramUpdatedListeners = new MultiLongSparseArray<>();
    private final Handler mHandler;
    private final Set<Listener> mListeners = new ArraySet<>();

    private final ContentObserver mProgramObserver;

    private boolean mPrefetchEnabled;
    private long mProgramPrefetchUpdateWaitMs;
    private long mLastPrefetchTaskRunMs;
    private ProgramsPrefetchTask mProgramsPrefetchTask;
    private Map<Long, ArrayList<Program>> mChannelIdProgramCache = new HashMap<>();

    // Any program that ends prior to this time will be removed from the cache
    // when a channel's current program is updated.
    // Note that there's no limit for end time.
    private long mPrefetchTimeRangeStartMs;

    private boolean mPauseProgramUpdate = false;
    private final LruCache<Long, Program> mZeroLengthProgramCache = new LruCache<>(10);

    @MainThread
    public ProgramDataManager(Context context) {
        this(context.getContentResolver(), Clock.SYSTEM, Looper.myLooper());
    }

    @VisibleForTesting
    ProgramDataManager(ContentResolver contentResolver, Clock time, Looper looper) {
        mClock = time;
        mContentResolver = contentResolver;
        mHandler = new MyHandler(looper);
        mProgramObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                if (!mHandler.hasMessages(MSG_UPDATE_CURRENT_PROGRAMS)) {
                    mHandler.sendEmptyMessage(MSG_UPDATE_CURRENT_PROGRAMS);
                }
                if (isProgramUpdatePaused()) {
                    return;
                }
                if (mPrefetchEnabled) {
                    // The delay time of an existing MSG_UPDATE_PREFETCH_PROGRAM could be quite long
                    // up to PROGRAM_GUIDE_SNAP_TIME_MS. So we need to remove the existing message
                    // and send MSG_UPDATE_PREFETCH_PROGRAM again.
                    mHandler.removeMessages(MSG_UPDATE_PREFETCH_PROGRAM);
                    mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
                }
            }
        };
        mProgramPrefetchUpdateWaitMs = PROGRAM_PREFETCH_UPDATE_WAIT_MS;
    }

    @VisibleForTesting
    ContentObserver getContentObserver() {
        return mProgramObserver;
    }

    /**
     * Set the program prefetch update wait which gives the delay to query all programs from DB
     * to prevent from too frequent DB queries.
     * Default value is {@link #PROGRAM_PREFETCH_UPDATE_WAIT_MS}
     */
    @VisibleForTesting
    void setProgramPrefetchUpdateWait(long programPrefetchUpdateWaitMs) {
        mProgramPrefetchUpdateWaitMs = programPrefetchUpdateWaitMs;
    }

    /**
     * Starts the manager.
     */
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        // Should be called directly instead of posting MSG_UPDATE_CURRENT_PROGRAMS message
        // to the handler. If not, another DB task can be executed before loading current programs.
        handleUpdateCurrentPrograms();
        if (mPrefetchEnabled) {
            mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
        }
        mContentResolver.registerContentObserver(Programs.CONTENT_URI,
                true, mProgramObserver);
    }

    /**
     * Stops the manager. It clears manager states and runs pending DB operations. Added listeners
     * aren't automatically removed by this method.
     */
    @VisibleForTesting
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mContentResolver.unregisterContentObserver(mProgramObserver);
        mHandler.removeCallbacksAndMessages(null);

        clearTask(mProgramUpdateTaskMap);
        cancelPrefetchTask();
        if (mProgramsUpdateTask != null) {
            mProgramsUpdateTask.cancel(true);
            mProgramsUpdateTask = null;
        }
    }

    @AnyThread
    public boolean isCurrentProgramsLoadFinished() {
        return mCurrentProgramsLoadFinished;
    }

    /** Returns the current program at the specified channel. */
    @AnyThread
    public Program getCurrentProgram(long channelId) {
        return mChannelIdCurrentProgramMap.get(channelId);
    }

    /** Returns all the current programs. */
    @AnyThread
    public List<Program> getCurrentPrograms() {
        return new ArrayList<>(mChannelIdCurrentProgramMap.values());
    }

    /**
     * Reloads program data.
     */
    public void reload() {
        if (!mHandler.hasMessages(MSG_UPDATE_CURRENT_PROGRAMS)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_CURRENT_PROGRAMS);
        }
        if (mPrefetchEnabled && !mHandler.hasMessages(MSG_UPDATE_PREFETCH_PROGRAM)) {
            mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
        }
    }

    /**
     * A listener interface to receive notification on program data retrieval from DB.
     */
    public interface Listener {
        /**
         * Called when a Program data is now available through getProgram()
         * after the DB operation is done which wasn't before.
         * This would be called only if fetched data is around the selected program.
         **/
        void onProgramUpdated();
    }

    /**
     * Adds the {@link Listener}.
     */
    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    /**
     * Removes the {@link Listener}.
     */
    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Enables or Disables program prefetch.
     */
    public void setPrefetchEnabled(boolean enable) {
        if (mPrefetchEnabled == enable) {
            return;
        }
        if (enable) {
            mPrefetchEnabled = true;
            mLastPrefetchTaskRunMs = 0;
            if (mStarted) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
            }
        } else {
            mPrefetchEnabled = false;
            cancelPrefetchTask();
            mChannelIdProgramCache.clear();
            mHandler.removeMessages(MSG_UPDATE_PREFETCH_PROGRAM);
        }
    }

    /**
     * Returns the programs for the given channel which ends after the given start time.
     *
     * <p> Prefetch should be enabled to call it.
     *
     * @return {@link List} with Programs. It may includes dummy program if the entry needs DB
     *         operations to get.
     */
    public List<Program> getPrograms(long channelId, long startTime) {
        SoftPreconditions.checkState(mPrefetchEnabled, TAG, "Prefetch is disabled.");
        ArrayList<Program> cachedPrograms = mChannelIdProgramCache.get(channelId);
        if (cachedPrograms == null) {
            return Collections.emptyList();
        }
        int startIndex = getProgramIndexAt(cachedPrograms, startTime);
        return Collections.unmodifiableList(
                cachedPrograms.subList(startIndex, cachedPrograms.size()));
    }

    // Returns the index of program that is played at the specified time.
    // If there isn't, return the first program among programs that starts after the given time
    // if returnNextProgram is {@code true}.
    private int getProgramIndexAt(List<Program> programs, long time) {
        Program key = mZeroLengthProgramCache.get(time);
        if (key == null) {
            key = createDummyProgram(time, time);
            mZeroLengthProgramCache.put(time, key);
        }
        int index = Collections.binarySearch(programs, key);
        if (index < 0) {
            index = -(index + 1); // change it to index to be added.
            if (index > 0 && isProgramPlayedAt(programs.get(index - 1), time)) {
                // A program is played at that time.
                return index - 1;
            }
            return index;
        }
        return index;
    }

    private boolean isProgramPlayedAt(Program program, long time) {
        return program.getStartTimeUtcMillis() <= time && time <= program.getEndTimeUtcMillis();
    }

    /**
     * Adds the listener to be notified if current program is updated for a channel.
     *
     * @param channelId A channel ID to get notified. If it's {@link Channel#INVALID_ID}, the
     *            listener would be called whenever a current program is updated.
     */
    public void addOnCurrentProgramUpdatedListener(
            long channelId, OnCurrentProgramUpdatedListener listener) {
        mChannelId2ProgramUpdatedListeners
                .put(channelId, listener);
    }

    /**
     * Removes the listener previously added by
     * {@link #addOnCurrentProgramUpdatedListener(long, OnCurrentProgramUpdatedListener)}.
     */
    public void removeOnCurrentProgramUpdatedListener(
            long channelId, OnCurrentProgramUpdatedListener listener) {
        mChannelId2ProgramUpdatedListeners
                .remove(channelId, listener);
    }

    private void notifyCurrentProgramUpdate(long channelId, Program program) {
        for (OnCurrentProgramUpdatedListener listener : mChannelId2ProgramUpdatedListeners
                .get(channelId)) {
            listener.onCurrentProgramUpdated(channelId, program);
        }
        for (OnCurrentProgramUpdatedListener listener : mChannelId2ProgramUpdatedListeners
                .get(Channel.INVALID_ID)) {
            listener.onCurrentProgramUpdated(channelId, program);
        }
    }

    private void updateCurrentProgram(long channelId, Program program) {
        Program previousProgram = program == null ? mChannelIdCurrentProgramMap.remove(channelId)
                : mChannelIdCurrentProgramMap.put(channelId, program);
        if (!Objects.equals(program, previousProgram)) {
            if (mPrefetchEnabled) {
                removePreviousProgramsAndUpdateCurrentProgramInCache(channelId, program);
            }
            notifyCurrentProgramUpdate(channelId, program);
        }

        long delayedTime;
        if (program == null) {
            delayedTime = PERIODIC_PROGRAM_UPDATE_MIN_MS
                    + (long) (Math.random() * (PERIODIC_PROGRAM_UPDATE_MAX_MS
                            - PERIODIC_PROGRAM_UPDATE_MIN_MS));
        } else {
            delayedTime = program.getEndTimeUtcMillis() - mClock.currentTimeMillis();
        }
        mHandler.sendMessageDelayed(mHandler.obtainMessage(
                MSG_UPDATE_ONE_CURRENT_PROGRAM, channelId), delayedTime);
    }

    private void removePreviousProgramsAndUpdateCurrentProgramInCache(
            long channelId, Program currentProgram) {
        SoftPreconditions.checkState(mPrefetchEnabled, TAG, "Prefetch is disabled.");
        if (!Program.isValid(currentProgram)) {
            return;
        }
        ArrayList<Program> cachedPrograms = mChannelIdProgramCache.remove(channelId);
        if (cachedPrograms == null) {
            return;
        }
        ListIterator<Program> i = cachedPrograms.listIterator();
        while (i.hasNext()) {
            Program cachedProgram = i.next();
            if (cachedProgram.getEndTimeUtcMillis() <= mPrefetchTimeRangeStartMs) {
                // Remove previous programs which will not be shown in program guide.
                i.remove();
                continue;
            }

            if (cachedProgram.getEndTimeUtcMillis() <= currentProgram
                    .getStartTimeUtcMillis()) {
                // Keep the programs that ends earlier than current program
                // but later than mPrefetchTimeRangeStartMs.
                continue;
            }

            // Update dummy program around current program if any.
            if (cachedProgram.getStartTimeUtcMillis() < currentProgram
                    .getStartTimeUtcMillis()) {
                // The dummy program starts earlier than the current program. Adjust its end time.
                i.set(createDummyProgram(cachedProgram.getStartTimeUtcMillis(),
                        currentProgram.getStartTimeUtcMillis()));
                i.add(currentProgram);
            } else {
                i.set(currentProgram);
            }
            if (currentProgram.getEndTimeUtcMillis() < cachedProgram.getEndTimeUtcMillis()) {
                // The dummy program ends later than the current program. Adjust its start time.
                i.add(createDummyProgram(currentProgram.getEndTimeUtcMillis(),
                        cachedProgram.getEndTimeUtcMillis()));
            }
            break;
        }
        if (cachedPrograms.isEmpty()) {
            // If all the cached programs finish before mPrefetchTimeRangeStartMs, the
            // currentProgram would not have a chance to be inserted to the cache.
            cachedPrograms.add(currentProgram);
        }
        mChannelIdProgramCache.put(channelId, cachedPrograms);
    }

    private void handleUpdateCurrentPrograms() {
        if (mProgramsUpdateTask != null) {
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_CURRENT_PROGRAMS,
                    CURRENT_PROGRAM_UPDATE_WAIT_MS);
            return;
        }
        clearTask(mProgramUpdateTaskMap);
        mHandler.removeMessages(MSG_UPDATE_ONE_CURRENT_PROGRAM);
        mProgramsUpdateTask = new ProgramsUpdateTask(mContentResolver, mClock.currentTimeMillis());
        mProgramsUpdateTask.executeOnDbThread();
    }

    private class ProgramsPrefetchTask
            extends AsyncDbTask<Void, Void, Map<Long, ArrayList<Program>>> {
        private final long mStartTimeMs;
        private final long mEndTimeMs;

        private boolean mSuccess;

        public ProgramsPrefetchTask() {
            long time = mClock.currentTimeMillis();
            mStartTimeMs = Utils
                    .floorTime(time - PROGRAM_GUIDE_SNAP_TIME_MS, PROGRAM_GUIDE_SNAP_TIME_MS);
            mEndTimeMs = mStartTimeMs + PROGRAM_GUIDE_MAX_TIME_RANGE;
            mSuccess = false;
        }

        @Override
        protected Map<Long, ArrayList<Program>> doInBackground(Void... params) {
            Map<Long, ArrayList<Program>> programMap = new HashMap<>();
            if (DEBUG) {
                Log.d(TAG, "Starts programs prefetch. " + Utils.toTimeString(mStartTimeMs) + "-"
                        + Utils.toTimeString(mEndTimeMs));
            }
            Uri uri = Programs.CONTENT_URI.buildUpon()
                    .appendQueryParameter(PARAM_START_TIME, String.valueOf(mStartTimeMs))
                    .appendQueryParameter(PARAM_END_TIME, String.valueOf(mEndTimeMs)).build();
            final int RETRY_COUNT = 3;
            Program lastReadProgram = null;
            for (int retryCount = RETRY_COUNT; retryCount > 0; retryCount--) {
                if (isProgramUpdatePaused()) {
                    return null;
                }
                programMap.clear();
                try (Cursor c = mContentResolver.query(uri, Program.PROJECTION, null, null,
                        SORT_BY_TIME)) {
                    if (c == null) {
                        continue;
                    }
                    while (c.moveToNext()) {
                        int duplicateCount = 0;
                        if (isCancelled()) {
                            if (DEBUG) {
                                Log.d(TAG, "ProgramsPrefetchTask canceled.");
                            }
                            return null;
                        }
                        Program program = Program.fromCursor(c);
                        if (Program.isDuplicate(program, lastReadProgram)) {
                            duplicateCount++;
                            continue;
                        } else {
                            lastReadProgram = program;
                        }
                        ArrayList<Program> programs = programMap.get(program.getChannelId());
                        if (programs == null) {
                            programs = new ArrayList<>();
                            programMap.put(program.getChannelId(), programs);
                        }
                        programs.add(program);
                        if (duplicateCount > 0) {
                            Log.w(TAG, "Found " + duplicateCount + " duplicate programs");
                        }
                    }
                    mSuccess = true;
                    break;
                } catch (IllegalStateException e) {
                    if (DEBUG) {
                        Log.d(TAG, "Database is changed while querying. Will retry.");
                    }
                } catch (SecurityException e) {
                    Log.d(TAG, "Security exception during program data query", e);
                }
            }
            if (DEBUG) {
                Log.d(TAG, "Ends programs prefetch for " + programMap.size() + " channels");
            }
            return programMap;
        }

        @Override
        protected void onPostExecute(Map<Long, ArrayList<Program>> programs) {
            mProgramsPrefetchTask = null;
            if (isProgramUpdatePaused()) {
                // ProgramsPrefetchTask will run again once setPauseProgramUpdate(false) is called.
                return;
            }
            long nextMessageDelayedTime;
            if (mSuccess) {
                mChannelIdProgramCache = programs;
                notifyProgramUpdated();
                long currentTime = mClock.currentTimeMillis();
                mLastPrefetchTaskRunMs = currentTime;
                nextMessageDelayedTime =
                        Utils.floorTime(mLastPrefetchTaskRunMs + PROGRAM_GUIDE_SNAP_TIME_MS,
                                PROGRAM_GUIDE_SNAP_TIME_MS) - currentTime;
            } else {
                nextMessageDelayedTime = PERIODIC_PROGRAM_UPDATE_MIN_MS;
            }
            if (!mHandler.hasMessages(MSG_UPDATE_PREFETCH_PROGRAM)) {
                mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PREFETCH_PROGRAM,
                        nextMessageDelayedTime);
            }
        }
    }

    private void notifyProgramUpdated() {
        for (Listener listener : mListeners) {
            listener.onProgramUpdated();
        }
    }

    private class ProgramsUpdateTask extends AsyncDbTask.AsyncQueryTask<List<Program>> {
        public ProgramsUpdateTask(ContentResolver contentResolver, long time) {
            super(contentResolver, Programs.CONTENT_URI.buildUpon()
                            .appendQueryParameter(PARAM_START_TIME, String.valueOf(time))
                            .appendQueryParameter(PARAM_END_TIME, String.valueOf(time)).build(),
                    Program.PROJECTION, null, null, SORT_BY_TIME);
        }

        @Override
        public List<Program> onQuery(Cursor c) {
            final List<Program> programs = new ArrayList<>();
            if (c != null) {
                int duplicateCount = 0;
                Program lastReadProgram = null;
                while (c.moveToNext()) {
                    if (isCancelled()) {
                        return programs;
                    }
                    Program program = Program.fromCursor(c);
                    if (Program.isDuplicate(program, lastReadProgram)) {
                        duplicateCount++;
                        continue;
                    } else {
                        lastReadProgram = program;
                    }
                    programs.add(program);
                }
                if (duplicateCount > 0) {
                    Log.w(TAG, "Found " + duplicateCount + " duplicate programs");
                }
            }
            return programs;
        }

        @Override
        protected void onPostExecute(List<Program> programs) {
            if (DEBUG) Log.d(TAG, "ProgramsUpdateTask done");
            mProgramsUpdateTask = null;
            if (programs != null) {
                Set<Long> removedChannelIds = new HashSet<>(mChannelIdCurrentProgramMap.keySet());
                for (Program program : programs) {
                    long channelId = program.getChannelId();
                    updateCurrentProgram(channelId, program);
                    removedChannelIds.remove(channelId);
                }
                for (Long channelId : removedChannelIds) {
                    if (mPrefetchEnabled) {
                        mChannelIdProgramCache.remove(channelId);
                    }
                    mChannelIdCurrentProgramMap.remove(channelId);
                    notifyCurrentProgramUpdate(channelId, null);
                }
            }
            mCurrentProgramsLoadFinished = true;
        }
    }

    private class UpdateCurrentProgramForChannelTask extends AsyncDbTask.AsyncQueryTask<Program> {
        private final long mChannelId;
        private UpdateCurrentProgramForChannelTask(ContentResolver contentResolver, long channelId,
                long time) {
            super(contentResolver, TvContract.buildProgramsUriForChannel(channelId, time, time),
                    Program.PROJECTION, null, null, SORT_BY_TIME);
            mChannelId = channelId;
        }

        @Override
        public Program onQuery(Cursor c) {
            Program program = null;
            if (c != null && c.moveToNext()) {
                program = Program.fromCursor(c);
            }
            return program;
        }

        @Override
        protected void onPostExecute(Program program) {
            mProgramUpdateTaskMap.remove(mChannelId);
            updateCurrentProgram(mChannelId, program);
        }
    }

    private class MyHandler extends Handler {
        public MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_CURRENT_PROGRAMS:
                    handleUpdateCurrentPrograms();
                    break;
                case MSG_UPDATE_ONE_CURRENT_PROGRAM: {
                    long channelId = (Long) msg.obj;
                    UpdateCurrentProgramForChannelTask oldTask = mProgramUpdateTaskMap
                            .get(channelId);
                    if (oldTask != null) {
                        oldTask.cancel(true);
                    }
                    UpdateCurrentProgramForChannelTask
                            task = new UpdateCurrentProgramForChannelTask(
                            mContentResolver, channelId, mClock.currentTimeMillis());
                    mProgramUpdateTaskMap.put(channelId, task);
                    task.executeOnDbThread();
                    break;
                }
                case MSG_UPDATE_PREFETCH_PROGRAM: {
                    if (isProgramUpdatePaused()) {
                        return;
                    }
                    if (mProgramsPrefetchTask != null) {
                        mHandler.sendEmptyMessageDelayed(msg.what, mProgramPrefetchUpdateWaitMs);
                        return;
                    }
                    long delayMillis = mLastPrefetchTaskRunMs + mProgramPrefetchUpdateWaitMs
                            - mClock.currentTimeMillis();
                    if (delayMillis > 0) {
                        mHandler.sendEmptyMessageDelayed(MSG_UPDATE_PREFETCH_PROGRAM, delayMillis);
                    } else {
                        mProgramsPrefetchTask = new ProgramsPrefetchTask();
                        mProgramsPrefetchTask.executeOnDbThread();
                    }
                    break;
                }
            }
        }
    }

    /**
     * Pause program update.
     * Updating program data will result in UI refresh,
     * but UI is fragile to handle it so we'd better disable it for a while.
     *
     * <p> Prefetch should be enabled to call it.
     */
    public void setPauseProgramUpdate(boolean pauseProgramUpdate) {
        SoftPreconditions.checkState(mPrefetchEnabled, TAG, "Prefetch is disabled.");
        if (mPauseProgramUpdate && !pauseProgramUpdate) {
            if (!mHandler.hasMessages(MSG_UPDATE_PREFETCH_PROGRAM)) {
                // MSG_UPDATE_PRFETCH_PROGRAM can be empty
                // if prefetch task is launched while program update is paused.
                // Update immediately in that case.
                mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
            }
        }
        mPauseProgramUpdate = pauseProgramUpdate;
    }

    private boolean isProgramUpdatePaused() {
        // Although pause is requested, we need to keep updating if cache is empty.
        return mPauseProgramUpdate && !mChannelIdProgramCache.isEmpty();
    }

    /**
     * Sets program data prefetch time range.
     * Any program data that ends before the start time will be removed from the cache later.
     * Note that there's no limit for end time.
     *
     * <p> Prefetch should be enabled to call it.
     */
    public void setPrefetchTimeRange(long startTimeMs) {
        SoftPreconditions.checkState(mPrefetchEnabled, TAG, "Prefetch is disabled.");
        if (mPrefetchTimeRangeStartMs > startTimeMs) {
            // Fetch the programs immediately to re-create the cache.
            if (!mHandler.hasMessages(MSG_UPDATE_PREFETCH_PROGRAM)) {
                mHandler.sendEmptyMessage(MSG_UPDATE_PREFETCH_PROGRAM);
            }
        }
        mPrefetchTimeRangeStartMs = startTimeMs;
    }

    private void clearTask(LongSparseArray<UpdateCurrentProgramForChannelTask> tasks) {
        for (int i = 0; i < tasks.size(); i++) {
            tasks.valueAt(i).cancel(true);
        }
        tasks.clear();
    }

    private void cancelPrefetchTask() {
        if (mProgramsPrefetchTask != null) {
            mProgramsPrefetchTask.cancel(true);
            mProgramsPrefetchTask = null;
        }
    }

    // Create dummy program which indicates data isn't loaded yet so DB query is required.
    private Program createDummyProgram(long startTimeMs, long endTimeMs) {
        return new Program.Builder()
                .setChannelId(Channel.INVALID_ID)
                .setStartTimeUtcMillis(startTimeMs)
                .setEndTimeUtcMillis(endTimeMs).build();
    }

    @Override
    public void performTrimMemory(int level) {
        mChannelId2ProgramUpdatedListeners.clearEmptyCache();
    }
}
