package com.android.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.tv.common.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * A class to manage watched history.
 *
 * <p>When there is no access to watched table of TvProvider,
 * this class is used to build up watched history and to compute recent channels.
 * <p>Note that this class is not thread safe. Please use this on one thread.
 */
public class WatchedHistoryManager {
    private final static String TAG = "WatchedHistoryManager";
    private final static boolean DEBUG = false;

    private static final int MAX_HISTORY_SIZE = 10000;
    private static final String PREF_KEY_LAST_INDEX = "last_index";
    private static final long MIN_DURATION_MS = TimeUnit.SECONDS.toMillis(10);

    private final List<WatchedRecord> mWatchedHistory = new ArrayList<>();
    private final List<WatchedRecord> mPendingRecords = new ArrayList<>();
    private long mLastIndex;
    private boolean mStarted;
    private boolean mLoaded;
    private SharedPreferences mSharedPreferences;
    private final OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener =
            new OnSharedPreferenceChangeListener() {
                @Override
                @MainThread
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    if (key.equals(PREF_KEY_LAST_INDEX)) {
                        final long lastIndex = mSharedPreferences.getLong(PREF_KEY_LAST_INDEX, -1);
                        if (lastIndex <= mLastIndex) {
                            return;
                        }
                        // onSharedPreferenceChanged is always called in a main thread.
                        // onNewRecordAdded will be called in the same thread as the thread
                        // which created this instance.
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                for (long i = mLastIndex + 1; i <= lastIndex; ++i) {
                                    WatchedRecord record = decode(
                                            mSharedPreferences.getString(getSharedPreferencesKey(i),
                                                    null));
                                    if (record != null) {
                                        mWatchedHistory.add(record);
                                        if (mListener != null) {
                                            mListener.onNewRecordAdded(record);
                                        }
                                    }
                                }
                                mLastIndex = lastIndex;
                            }
                        });
                    }
                }
            };

    private final Context mContext;
    private Listener mListener;
    private final int mMaxHistorySize;
    private final Handler mHandler;

    public WatchedHistoryManager(Context context) {
        this(context, MAX_HISTORY_SIZE);
    }

    @VisibleForTesting
    WatchedHistoryManager(Context context, int maxHistorySize) {
        mContext = context.getApplicationContext();
        mMaxHistorySize = maxHistorySize;
        mHandler = new Handler();
    }

    /**
     * Starts the manager. It loads history data from {@link SharedPreferences}.
     */
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    loadWatchedHistory();
                    return null;
                }

                @Override
                protected void onPostExecute(Void params) {
                    onLoadFinished();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            loadWatchedHistory();
            onLoadFinished();
        }
    }

    @WorkerThread
    private void loadWatchedHistory() {
        mSharedPreferences = mContext.getSharedPreferences(
                SharedPreferencesUtils.SHARED_PREF_WATCHED_HISTORY, Context.MODE_PRIVATE);
        mLastIndex = mSharedPreferences.getLong(PREF_KEY_LAST_INDEX, -1);
        if (mLastIndex >= 0 && mLastIndex < mMaxHistorySize) {
            for (int i = 0; i <= mLastIndex; ++i) {
                WatchedRecord record =
                        decode(mSharedPreferences.getString(getSharedPreferencesKey(i),
                                null));
                if (record != null) {
                    mWatchedHistory.add(record);
                }
            }
        } else if (mLastIndex >= mMaxHistorySize) {
            for (long i = mLastIndex - mMaxHistorySize + 1; i <= mLastIndex; ++i) {
                WatchedRecord record = decode(mSharedPreferences.getString(
                        getSharedPreferencesKey(i), null));
                if (record != null) {
                    mWatchedHistory.add(record);
                }
            }
        }
    }

    private void onLoadFinished() {
        mLoaded = true;
        if (DEBUG) {
            Log.d(TAG, "Loaded: size=" + mWatchedHistory.size() + " index=" + mLastIndex);
        }
        if (!mPendingRecords.isEmpty()) {
            Editor editor = mSharedPreferences.edit();
            for (WatchedRecord record : mPendingRecords) {
                mWatchedHistory.add(record);
                ++mLastIndex;
                editor.putString(getSharedPreferencesKey(mLastIndex), encode(record));
            }
            editor.putLong(PREF_KEY_LAST_INDEX, mLastIndex).apply();
            mPendingRecords.clear();
        }
        if (mListener != null) {
            mListener.onLoadFinished();
        }
        mSharedPreferences.registerOnSharedPreferenceChangeListener(
                mOnSharedPreferenceChangeListener);
    }

    @VisibleForTesting
    public boolean isLoaded() {
        return mLoaded;
    }

    /**
     * Logs the record of the watched channel.
     */
    public void logChannelViewStop(Channel channel, long endTime, long duration) {
        if (duration < MIN_DURATION_MS) {
            return;
        }
        WatchedRecord record = new WatchedRecord(channel.getId(), endTime - duration, duration);
        if (mLoaded) {
            if (DEBUG) Log.d(TAG, "Log a watched record. " + record);
            mWatchedHistory.add(record);
            ++mLastIndex;
            mSharedPreferences.edit()
                    .putString(getSharedPreferencesKey(mLastIndex), encode(record))
                    .putLong(PREF_KEY_LAST_INDEX, mLastIndex)
                    .apply();
            if (mListener != null) {
                mListener.onNewRecordAdded(record);
            }
        } else {
            mPendingRecords.add(record);
        }
    }

    /**
     * Sets {@link Listener}.
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Returns watched history in the ascending order of time. In other words, the first element
     * is the oldest and the last element is the latest record.
     */
    @NonNull
    public List<WatchedRecord> getWatchedHistory() {
        return Collections.unmodifiableList(mWatchedHistory);
    }

    @VisibleForTesting
    WatchedRecord getRecord(int reverseIndex) {
        return mWatchedHistory.get(mWatchedHistory.size() - 1 - reverseIndex);
    }

    @VisibleForTesting
    WatchedRecord getRecordFromSharedPreferences(int reverseIndex) {
        long lastIndex = mSharedPreferences.getLong(PREF_KEY_LAST_INDEX, -1);
        long index = lastIndex - reverseIndex;
        return decode(mSharedPreferences.getString(getSharedPreferencesKey(index), null));
    }

    private String getSharedPreferencesKey(long index) {
        return Long.toString(index % mMaxHistorySize);
    }

    public static class WatchedRecord {
        public final long channelId;
        public final long watchedStartTime;
        public final long duration;

        WatchedRecord(long channelId, long watchedStartTime, long duration) {
            this.channelId = channelId;
            this.watchedStartTime = watchedStartTime;
            this.duration = duration;
        }

        @Override
        public String toString() {
            return "WatchedRecord: id=" + channelId + ",watchedStartTime=" + watchedStartTime
                    + ",duration=" + duration;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof WatchedRecord) {
                WatchedRecord that = (WatchedRecord) o;
                return Objects.equals(channelId, that.channelId)
                        && Objects.equals(watchedStartTime, that.watchedStartTime)
                        && Objects.equals(duration, that.duration);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(channelId, watchedStartTime, duration);
        }
    }

    @VisibleForTesting
    String encode(WatchedRecord record) {
        return record.channelId + " " + record.watchedStartTime + " " + record.duration;
    }

    @VisibleForTesting
    WatchedRecord decode(String encodedString) {
        try (Scanner scanner = new Scanner(encodedString)) {
            long channelId = scanner.nextLong();
            long watchedStartTime = scanner.nextLong();
            long duration = scanner.nextLong();
            return new WatchedRecord(channelId, watchedStartTime, duration);
        } catch (Exception e) {
            return null;
        }
    }

    public interface Listener {
        /**
         * Called when history is loaded.
         */
        void onLoadFinished();
        void onNewRecordAdded(WatchedRecord watchedRecord);
    }
}
