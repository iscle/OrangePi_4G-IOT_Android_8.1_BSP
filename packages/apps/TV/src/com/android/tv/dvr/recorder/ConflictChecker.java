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

package com.android.tv.dvr.recorder;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.OnTvViewChannelChangeListener;
import com.android.tv.MainActivity;
import com.android.tv.TvApplication;
import com.android.tv.common.WeakHandler;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrUiHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Checking the runtime conflict of DVR recording.
 * <p>
 * This class runs only while the {@link MainActivity} is resumed and holds the upcoming conflicts.
 */
@TargetApi(Build.VERSION_CODES.N)
@MainThread
public class ConflictChecker {
    private static final String TAG = "ConflictChecker";
    private static final boolean DEBUG = false;

    private static final int MSG_CHECK_CONFLICT = 1;

    private static final long CHECK_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30);

    /**
     * To show watch conflict dialog, the start time of the earliest conflicting schedule should be
     * less than or equal to this time.
     */
    private static final long MAX_WATCH_CONFLICT_CHECK_TIME_MS = TimeUnit.MINUTES.toMillis(5);
    /**
     * To show watch conflict dialog, the start time of the earliest conflicting schedule should be
     * greater than or equal to this time.
     */
    private static final long MIN_WATCH_CONFLICT_CHECK_TIME_MS = TimeUnit.SECONDS.toMillis(30);

    private final MainActivity mMainActivity;
    private final ChannelDataManager mChannelDataManager;
    private final DvrScheduleManager mScheduleManager;
    private final InputSessionManager mSessionManager;
    private final ConflictCheckerHandler mHandler = new ConflictCheckerHandler(this);

    private final List<ScheduledRecording> mUpcomingConflicts = new ArrayList<>();
    private final Set<OnUpcomingConflictChangeListener> mOnUpcomingConflictChangeListeners =
            new ArraySet<>();
    private final Map<Long, List<ScheduledRecording>> mCheckedConflictsMap = new HashMap<>();

    private final ScheduledRecordingListener mScheduledRecordingListener =
            new ScheduledRecordingListener() {
        @Override
        public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
            if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: " + scheduledRecordings);
            mHandler.sendEmptyMessage(MSG_CHECK_CONFLICT);
        }

        @Override
        public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
            if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: " + scheduledRecordings);
            mHandler.sendEmptyMessage(MSG_CHECK_CONFLICT);
        }

        @Override
        public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
            if (DEBUG) Log.d(TAG, "onScheduledRecordingStatusChanged: " + scheduledRecordings);
            mHandler.sendEmptyMessage(MSG_CHECK_CONFLICT);
        }
    };

    private final OnTvViewChannelChangeListener mOnTvViewChannelChangeListener =
            new OnTvViewChannelChangeListener() {
                @Override
                public void onTvViewChannelChange(@Nullable Uri channelUri) {
                    mHandler.sendEmptyMessage(MSG_CHECK_CONFLICT);
                }
            };

    private boolean mStarted;

    public ConflictChecker(MainActivity mainActivity) {
        mMainActivity = mainActivity;
        ApplicationSingletons appSingletons = TvApplication.getSingletons(mainActivity);
        mChannelDataManager = appSingletons.getChannelDataManager();
        mScheduleManager = appSingletons.getDvrScheduleManager();
        mSessionManager = appSingletons.getInputSessionManager();
    }

    /**
     * Starts checking the conflict.
     */
    public void start() {
        if (mStarted) {
            return;
        }
        mStarted = true;
        mHandler.sendEmptyMessage(MSG_CHECK_CONFLICT);
        mScheduleManager.addScheduledRecordingListener(mScheduledRecordingListener);
        mSessionManager.addOnTvViewChannelChangeListener(mOnTvViewChannelChangeListener);
    }

    /**
     * Stops checking the conflict.
     */
    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        mSessionManager.removeOnTvViewChannelChangeListener(mOnTvViewChannelChangeListener);
        mScheduleManager.removeScheduledRecordingListener(mScheduledRecordingListener);
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Returns the upcoming conflicts.
     */
    public List<ScheduledRecording> getUpcomingConflicts() {
        return new ArrayList<>(mUpcomingConflicts);
    }

    /**
     * Adds a {@link OnUpcomingConflictChangeListener}.
     */
    public void addOnUpcomingConflictChangeListener(OnUpcomingConflictChangeListener listener) {
        mOnUpcomingConflictChangeListeners.add(listener);
    }

    /**
     * Removes the {@link OnUpcomingConflictChangeListener}.
     */
    public void removeOnUpcomingConflictChangeListener(OnUpcomingConflictChangeListener listener) {
        mOnUpcomingConflictChangeListeners.remove(listener);
    }

    private void notifyUpcomingConflictChanged() {
        for (OnUpcomingConflictChangeListener l : mOnUpcomingConflictChangeListeners) {
            l.onUpcomingConflictChange();
        }
    }

    /**
     * Remembers the user's decision to record while watching the channel.
     */
    public void setCheckedConflictsForChannel(long mChannelId, List<ScheduledRecording> conflicts) {
        mCheckedConflictsMap.put(mChannelId, new ArrayList<>(conflicts));
    }

    void onCheckConflict() {
        // Checks the conflicting schedules and setup the next re-check time.
        // If there are upcoming conflicts soon, it opens the conflict dialog.
        if (DEBUG) Log.d(TAG, "Handling MSG_CHECK_CONFLICT");
        mHandler.removeMessages(MSG_CHECK_CONFLICT);
        mUpcomingConflicts.clear();
        if (!mScheduleManager.isInitialized()
                || !mChannelDataManager.isDbLoadFinished()) {
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_CONFLICT, CHECK_RETRY_PERIOD_MS);
            notifyUpcomingConflictChanged();
            return;
        }
        if (mSessionManager.getCurrentTvViewChannelUri() == null) {
            // As MainActivity is not using a tuner, no need to check the conflict.
            notifyUpcomingConflictChanged();
            return;
        }
        Uri channelUri = mSessionManager.getCurrentTvViewChannelUri();
        if (TvContract.isChannelUriForPassthroughInput(channelUri)) {
            notifyUpcomingConflictChanged();
            return;
        }
        long channelId = ContentUris.parseId(channelUri);
        Channel channel = mChannelDataManager.getChannel(channelId);
        // The conflicts caused by watching the channel.
        List<ScheduledRecording> conflicts = mScheduleManager
                .getConflictingSchedulesForWatching(channel.getId());
        long earliestToCheck = Long.MAX_VALUE;
        long currentTimeMs = System.currentTimeMillis();
        for (ScheduledRecording schedule : conflicts) {
            long startTimeMs = schedule.getStartTimeMs();
            if (startTimeMs < currentTimeMs + MIN_WATCH_CONFLICT_CHECK_TIME_MS) {
                // The start time of the upcoming conflict remains less than the minimum
                // check time.
                continue;
            }
            if (startTimeMs > currentTimeMs + MAX_WATCH_CONFLICT_CHECK_TIME_MS) {
                // The start time of the upcoming conflict remains greater than the
                // maximum check time. Setup the next re-check time.
                long nextCheckTimeMs = startTimeMs - MAX_WATCH_CONFLICT_CHECK_TIME_MS;
                if (earliestToCheck > nextCheckTimeMs) {
                    earliestToCheck = nextCheckTimeMs;
                }
            } else {
                // Found upcoming conflicts which will start soon.
                mUpcomingConflicts.add(schedule);
                // The schedule will be removed from the "upcoming conflict" when the
                // recording is almost started.
                long nextCheckTimeMs = startTimeMs - MIN_WATCH_CONFLICT_CHECK_TIME_MS;
                if (earliestToCheck > nextCheckTimeMs) {
                    earliestToCheck = nextCheckTimeMs;
                }
            }
        }
        if (earliestToCheck != Long.MAX_VALUE) {
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_CONFLICT,
                    earliestToCheck - currentTimeMs);
        }
        if (DEBUG) Log.d(TAG, "upcoming conflicts: " + mUpcomingConflicts);
        notifyUpcomingConflictChanged();
        if (!mUpcomingConflicts.isEmpty()
                && !DvrUiHelper.isChannelWatchConflictDialogShown(mMainActivity)) {
            // Don't show the conflict dialog if the user already knows.
            List<ScheduledRecording> checkedConflicts = mCheckedConflictsMap.get(
                    channel.getId());
            if (checkedConflicts == null
                    || !checkedConflicts.containsAll(mUpcomingConflicts)) {
                DvrUiHelper.showChannelWatchConflictDialog(mMainActivity, channel);
            }
        }
    }

    private static class ConflictCheckerHandler extends WeakHandler<ConflictChecker> {
        ConflictCheckerHandler(ConflictChecker conflictChecker) {
            super(conflictChecker);
        }

        @Override
        protected void handleMessage(Message msg, @NonNull ConflictChecker conflictChecker) {
            switch (msg.what) {
                case MSG_CHECK_CONFLICT:
                    conflictChecker.onCheckConflict();
                    break;
            }
        }
    }

    /**
     * A listener for the change of upcoming conflicts.
     */
    public interface OnUpcomingConflictChangeListener {
        void onUpcomingConflictChange();
    }
}
