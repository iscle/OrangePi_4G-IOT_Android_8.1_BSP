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

package com.android.tv.recommendation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputManager.TvInputCallback;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.android.tv.TvApplication;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.data.WatchedHistoryManager;
import com.android.tv.util.PermissionUtils;
import com.android.tv.util.TvUriMatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RecommendationDataManager implements WatchedHistoryManager.Listener {
    private static final int MSG_START = 1000;
    private static final int MSG_STOP = 1001;
    private static final int MSG_UPDATE_CHANNELS = 1002;
    private static final int MSG_UPDATE_WATCH_HISTORY = 1003;
    private static final int MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED = 1004;
    private static final int MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED = 1005;

    private static final int MSG_FIRST = MSG_START;
    private static final int MSG_LAST = MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED;

    private static RecommendationDataManager sManager;
    private final ContentObserver mContentObserver;
    private final Map<Long, ChannelRecord> mChannelRecordMap = new ConcurrentHashMap<>();
    private final Map<Long, ChannelRecord> mAvailableChannelRecordMap = new ConcurrentHashMap<>();

    private final Context mContext;
    private boolean mStarted;
    private boolean mCancelLoadTask;
    private boolean mChannelRecordMapLoaded;
    private int mIndexWatchChannelId = -1;
    private int mIndexProgramTitle = -1;
    private int mIndexProgramStartTime = -1;
    private int mIndexProgramEndTime = -1;
    private int mIndexWatchStartTime = -1;
    private int mIndexWatchEndTime = -1;
    private TvInputManager mTvInputManager;
    private final Set<String> mInputs = new HashSet<>();

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final Handler mMainHandler;
    @Nullable
    private WatchedHistoryManager mWatchedHistoryManager;
    private final ChannelDataManager mChannelDataManager;
    private final ChannelDataManager.Listener mChannelDataListener =
            new ChannelDataManager.Listener() {
        @Override
        @MainThread
        public void onLoadFinished() {
            updateChannelData();
        }

        @Override
        @MainThread
        public void onChannelListUpdated() {
            updateChannelData();
        }

        @Override
        @MainThread
        public void onChannelBrowsableChanged() {
            updateChannelData();
        }
    };

    // For thread safety, this variable is handled only on main thread.
    private final List<Listener> mListeners = new ArrayList<>();

    /**
     * Gets instance of RecommendationDataManager, and adds a {@link Listener}.
     * The listener methods will be called in the same thread as its caller of the method.
     * Note that {@link #release(Listener)} should be called when this manager is not needed
     * any more.
     */
    public synchronized static RecommendationDataManager acquireManager(
            Context context, @NonNull Listener listener) {
        if (sManager == null) {
            sManager = new RecommendationDataManager(context);
        }
        sManager.addListener(listener);
        return sManager;
    }

    private final TvInputCallback mInternalCallback =
            new TvInputCallback() {
                @Override
                public void onInputStateChanged(String inputId, int state) { }

                @Override
                public void onInputAdded(String inputId) {
                    if (!mStarted) {
                        return;
                    }
                    mInputs.add(inputId);
                    if (!mChannelRecordMapLoaded) {
                        return;
                    }
                    boolean channelRecordMapChanged = false;
                    for (ChannelRecord channelRecord : mChannelRecordMap.values()) {
                        if (channelRecord.getChannel().getInputId().equals(inputId)) {
                            channelRecord.setInputRemoved(false);
                            mAvailableChannelRecordMap.put(channelRecord.getChannel().getId(),
                                    channelRecord);
                            channelRecordMapChanged = true;
                        }
                    }
                    if (channelRecordMapChanged
                            && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
                    }
                }

                @Override
                public void onInputRemoved(String inputId) {
                    if (!mStarted) {
                        return;
                    }
                    mInputs.remove(inputId);
                    if (!mChannelRecordMapLoaded) {
                        return;
                    }
                    boolean channelRecordMapChanged = false;
                    for (ChannelRecord channelRecord : mChannelRecordMap.values()) {
                        if (channelRecord.getChannel().getInputId().equals(inputId)) {
                            channelRecord.setInputRemoved(true);
                            mAvailableChannelRecordMap.remove(channelRecord.getChannel().getId());
                            channelRecordMapChanged = true;
                        }
                    }
                    if (channelRecordMapChanged
                            && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
                    }
                }

                @Override
                public void onInputUpdated(String inputId) { }
            };

    private RecommendationDataManager(Context context) {
        mContext = context.getApplicationContext();
        mHandlerThread = new HandlerThread("RecommendationDataManager");
        mHandlerThread.start();
        mHandler = new RecommendationHandler(mHandlerThread.getLooper(), this);
        mMainHandler = new RecommendationMainHandler(Looper.getMainLooper(), this);
        mContentObserver = new RecommendationContentObserver(mHandler);
        mChannelDataManager = TvApplication.getSingletons(mContext).getChannelDataManager();
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
    }

    /**
     * Removes the {@link Listener}, and releases RecommendationDataManager
     * if there are no listeners remained.
     */
    public void release(@NonNull final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                removeListener(listener);
                if (mListeners.size() == 0) {
                    stop();
                }
            }
        });
    }

    /**
     * Returns a {@link ChannelRecord} corresponds to the channel ID {@code ChannelId}.
     */
    public ChannelRecord getChannelRecord(long channelId) {
        return mAvailableChannelRecordMap.get(channelId);
    }

    /**
     * Returns the number of channels registered in ChannelRecord map.
     */
    public int getChannelRecordCount() {
        return mAvailableChannelRecordMap.size();
    }

    /**
     * Returns a Collection of ChannelRecords.
     */
    public Collection<ChannelRecord> getChannelRecords() {
        return Collections.unmodifiableCollection(mAvailableChannelRecordMap.values());
    }

    @MainThread
    private void start() {
        mHandler.sendEmptyMessage(MSG_START);
        mChannelDataManager.addListener(mChannelDataListener);
        if (mChannelDataManager.isDbLoadFinished()) {
            updateChannelData();
        }
    }

    @MainThread
    private void stop() {
        for (int what = MSG_FIRST; what <= MSG_LAST; ++what) {
            mHandler.removeMessages(what);
        }
        mChannelDataManager.removeListener(mChannelDataListener);
        mHandler.sendEmptyMessage(MSG_STOP);
        mHandlerThread.quitSafely();
        mMainHandler.removeCallbacksAndMessages(null);
        sManager = null;
    }

    @MainThread
    private void updateChannelData() {
        mHandler.removeMessages(MSG_UPDATE_CHANNELS);
        mHandler.obtainMessage(MSG_UPDATE_CHANNELS, mChannelDataManager.getBrowsableChannelList())
                .sendToTarget();
    }

    private void addListener(Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mListeners.add(listener);
            }
        });
    }

    @MainThread
    private void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    private void onStart() {
        if (!mStarted) {
            mStarted = true;
            mCancelLoadTask = false;
            if (!PermissionUtils.hasAccessWatchedHistory(mContext)) {
                mWatchedHistoryManager = new WatchedHistoryManager(mContext);
                mWatchedHistoryManager.setListener(this);
                mWatchedHistoryManager.start();
            } else {
                mContext.getContentResolver().registerContentObserver(
                        TvContract.WatchedPrograms.CONTENT_URI, true, mContentObserver);
                mHandler.obtainMessage(MSG_UPDATE_WATCH_HISTORY,
                        TvContract.WatchedPrograms.CONTENT_URI)
                        .sendToTarget();
            }
            mTvInputManager = (TvInputManager) mContext.getSystemService(Context.TV_INPUT_SERVICE);
            mTvInputManager.registerCallback(mInternalCallback, mHandler);
            for (TvInputInfo input : mTvInputManager.getTvInputList()) {
                mInputs.add(input.getId());
            }
        }
        if (mChannelRecordMapLoaded) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED);
        }
    }

    private void onStop() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        mCancelLoadTask = true;
        mChannelRecordMap.clear();
        mAvailableChannelRecordMap.clear();
        mInputs.clear();
        mTvInputManager.unregisterCallback(mInternalCallback);
        mStarted = false;
    }

    @WorkerThread
    private void onUpdateChannels(List<Channel> channels) {
        boolean isChannelRecordMapChanged = false;
        Set<Long> removedChannelIdSet = new HashSet<>(mChannelRecordMap.keySet());
        // Builds removedChannelIdSet.
        for (Channel channel : channels) {
            if (updateChannelRecordMapFromChannel(channel)) {
                isChannelRecordMapChanged = true;
            }
            removedChannelIdSet.remove(channel.getId());
        }

        if (!removedChannelIdSet.isEmpty()) {
            for (Long channelId : removedChannelIdSet) {
                mChannelRecordMap.remove(channelId);
                if (mAvailableChannelRecordMap.remove(channelId) != null) {
                    isChannelRecordMapChanged = true;
                }
            }
        }
        if (isChannelRecordMapChanged && mChannelRecordMapLoaded
                && !mHandler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED);
        }
    }

    @WorkerThread
    private void onLoadWatchHistory(Uri uri) {
        List<WatchedProgram> history = new ArrayList<>();
        try (Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToLast()) {
                do {
                    if (mCancelLoadTask) {
                        return;
                    }
                    history.add(createWatchedProgramFromWatchedProgramCursor(cursor));
                } while (cursor.moveToPrevious());
            }
        }
        for (WatchedProgram watchedProgram : history) {
            final ChannelRecord channelRecord =
                    updateChannelRecordFromWatchedProgram(watchedProgram);
            if (mChannelRecordMapLoaded && channelRecord != null) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Listener l : mListeners) {
                            l.onNewWatchLog(channelRecord);
                        }
                    }
                });
            }
        }
        if (!mChannelRecordMapLoaded) {
            mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED);
        }
    }

    private WatchedProgram convertFromWatchedHistoryManagerRecords(
            WatchedHistoryManager.WatchedRecord watchedRecord) {
        long endTime = watchedRecord.watchedStartTime + watchedRecord.duration;
        Program program = new Program.Builder()
                .setChannelId(watchedRecord.channelId)
                .setTitle("")
                .setStartTimeUtcMillis(watchedRecord.watchedStartTime)
                .setEndTimeUtcMillis(endTime)
                .build();
        return new WatchedProgram(program, watchedRecord.watchedStartTime, endTime);
    }

    @Override
    public void onLoadFinished() {
        for (WatchedHistoryManager.WatchedRecord record
                : mWatchedHistoryManager.getWatchedHistory()) {
            updateChannelRecordFromWatchedProgram(
                    convertFromWatchedHistoryManagerRecords(record));
        }
        mHandler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED);
    }

    @Override
    public void onNewRecordAdded(WatchedHistoryManager.WatchedRecord watchedRecord) {
        final ChannelRecord channelRecord = updateChannelRecordFromWatchedProgram(
                convertFromWatchedHistoryManagerRecords(watchedRecord));
        if (mChannelRecordMapLoaded && channelRecord != null) {
            runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    for (Listener l : mListeners) {
                        l.onNewWatchLog(channelRecord);
                    }
                }
            });
        }
    }

    private WatchedProgram createWatchedProgramFromWatchedProgramCursor(Cursor cursor) {
        // Have to initiate the indexes of WatchedProgram Columns.
        if (mIndexWatchChannelId == -1) {
            mIndexWatchChannelId = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_CHANNEL_ID);
            mIndexProgramTitle = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_TITLE);
            mIndexProgramStartTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_START_TIME_UTC_MILLIS);
            mIndexProgramEndTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_END_TIME_UTC_MILLIS);
            mIndexWatchStartTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS);
            mIndexWatchEndTime = cursor.getColumnIndex(
                    TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS);
        }

        Program program = new Program.Builder()
                .setChannelId(cursor.getLong(mIndexWatchChannelId))
                .setTitle(cursor.getString(mIndexProgramTitle))
                .setStartTimeUtcMillis(cursor.getLong(mIndexProgramStartTime))
                .setEndTimeUtcMillis(cursor.getLong(mIndexProgramEndTime))
                .build();

        return new WatchedProgram(program,
                cursor.getLong(mIndexWatchStartTime),
                cursor.getLong(mIndexWatchEndTime));
    }

    private void onNotifyChannelRecordMapLoaded() {
        mChannelRecordMapLoaded = true;
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (Listener l : mListeners) {
                    l.onChannelRecordLoaded();
                }
            }
        });
    }

    private void onNotifyChannelRecordMapChanged() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (Listener l : mListeners) {
                    l.onChannelRecordChanged();
                }
            }
        });
    }

    /**
     * Returns true if ChannelRecords are added into mChannelRecordMap or removed from it.
     */
    private boolean updateChannelRecordMapFromChannel(Channel channel) {
        if (!channel.isBrowsable()) {
            mChannelRecordMap.remove(channel.getId());
            return mAvailableChannelRecordMap.remove(channel.getId()) != null;
        }
        ChannelRecord channelRecord = mChannelRecordMap.get(channel.getId());
        boolean inputRemoved = !mInputs.contains(channel.getInputId());
        if (channelRecord == null) {
            ChannelRecord record = new ChannelRecord(mContext, channel, inputRemoved);
            mChannelRecordMap.put(channel.getId(), record);
            if (!inputRemoved) {
                mAvailableChannelRecordMap.put(channel.getId(), record);
                return true;
            }
            return false;
        }
        boolean oldInputRemoved = channelRecord.isInputRemoved();
        channelRecord.setChannel(channel, inputRemoved);
        return oldInputRemoved != inputRemoved;
    }

    private ChannelRecord updateChannelRecordFromWatchedProgram(WatchedProgram program) {
        ChannelRecord channelRecord = null;
        if (program != null && program.getWatchEndTimeMs() != 0L) {
            channelRecord = mChannelRecordMap.get(program.getProgram().getChannelId());
            if (channelRecord != null
                    && channelRecord.getLastWatchEndTimeMs() < program.getWatchEndTimeMs()) {
                channelRecord.logWatchHistory(program);
            }
        }
        return channelRecord;
    }

    private class RecommendationContentObserver extends ContentObserver {
        public RecommendationContentObserver(Handler handler) {
            super(handler);
        }

        @SuppressLint("SwitchIntDef")
        @Override
        public void onChange(final boolean selfChange, final Uri uri) {
            switch (TvUriMatcher.match(uri)) {
                case TvUriMatcher.MATCH_WATCHED_PROGRAM_ID:
                    if (!mHandler.hasMessages(MSG_UPDATE_WATCH_HISTORY,
                            TvContract.WatchedPrograms.CONTENT_URI)) {
                        mHandler.obtainMessage(MSG_UPDATE_WATCH_HISTORY, uri).sendToTarget();
                    }
                    break;
            }
        }
    }

    private void runOnMainThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mMainHandler.post(r);
        }
    }

    /**
     * A listener interface to receive notification about the recommendation data.
     *
     * @MainThread
     */
    public interface Listener {
        /**
         * Called when loading channel record map from database is finished.
         * It will be called after RecommendationDataManager.start() is finished.
         *
         * <p>Note that this method is called on the main thread.
         */
        void onChannelRecordLoaded();

        /**
         * Called when a new watch log is added into the corresponding channelRecord.
         *
         * <p>Note that this method is called on the main thread.
         *
         * @param channelRecord The channel record corresponds to the new watch log.
         */
        void onNewWatchLog(ChannelRecord channelRecord);

        /**
         * Called when the channel record map changes.
         *
         * <p>Note that this method is called on the main thread.
         */
        void onChannelRecordChanged();
    }

    private static class RecommendationHandler extends WeakHandler<RecommendationDataManager> {
        public RecommendationHandler(@NonNull Looper looper, RecommendationDataManager ref) {
            super(looper, ref);
        }

        @Override
        public void handleMessage(Message msg, @NonNull RecommendationDataManager dataManager) {
            switch (msg.what) {
                case MSG_START:
                    dataManager.onStart();
                    break;
                case MSG_STOP:
                    if (dataManager.mStarted) {
                        dataManager.onStop();
                    }
                    break;
                case MSG_UPDATE_CHANNELS:
                    if (dataManager.mStarted) {
                        dataManager.onUpdateChannels((List<Channel>) msg.obj);
                    }
                    break;
                case MSG_UPDATE_WATCH_HISTORY:
                    if (dataManager.mStarted) {
                        dataManager.onLoadWatchHistory((Uri) msg.obj);
                    }
                    break;
                case MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED:
                    if (dataManager.mStarted) {
                        dataManager.onNotifyChannelRecordMapLoaded();
                    }
                    break;
                case MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED:
                    if (dataManager.mStarted) {
                        dataManager.onNotifyChannelRecordMapChanged();
                    }
                    break;
            }
        }
    }

    private static class RecommendationMainHandler extends WeakHandler<RecommendationDataManager> {
        public RecommendationMainHandler(@NonNull Looper looper, RecommendationDataManager ref) {
            super(looper, ref);
        }

        @Override
        protected void handleMessage(Message msg, @NonNull RecommendationDataManager referent) { }
    }
}
