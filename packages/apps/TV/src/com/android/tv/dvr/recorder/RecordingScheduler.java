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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager.TvInputCallback;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Range;

import com.android.tv.ApplicationSingletons;
import com.android.tv.InputSessionManager;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.ChannelDataManager.Listener;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.OnDvrScheduleLoadFinishedListener;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.WritableDvrDataManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.util.Clock;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The core class to manage DVR schedule and run recording task.
 **
 * <p> This class is responsible for:
 * <ul>
 *     <li>Sending record commands to TV inputs</li>
 *     <li>Resolving conflicting schedules, handling overlapping recording time durations, etc.</li>
 * </ul>
 *
 * <p>This should be a singleton associated with application's main process.
 */
@RequiresApi(Build.VERSION_CODES.N)
@MainThread
public class RecordingScheduler extends TvInputCallback implements ScheduledRecordingListener {
    private static final String TAG = "RecordingScheduler";
    private static final boolean DEBUG = false;

    private static final String HANDLER_THREAD_NAME = "RecordingScheduler";
    private final static long SOON_DURATION_IN_MS = TimeUnit.MINUTES.toMillis(1);
    @VisibleForTesting final static long MS_TO_WAKE_BEFORE_START = TimeUnit.SECONDS.toMillis(30);

    private final Looper mLooper;
    private final InputSessionManager mSessionManager;
    private final WritableDvrDataManager mDataManager;
    private final DvrManager mDvrManager;
    private final ChannelDataManager mChannelDataManager;
    private final TvInputManagerHelper mInputManager;
    private final Context mContext;
    private final Clock mClock;
    private final AlarmManager mAlarmManager;

    private final Map<String, InputTaskScheduler> mInputSchedulerMap = new ArrayMap<>();
    private long mLastStartTimePendingMs;

    private OnDvrScheduleLoadFinishedListener mDvrScheduleLoadListener =
            new OnDvrScheduleLoadFinishedListener() {
                @Override
                public void onDvrScheduleLoadFinished() {
                    mDataManager.removeDvrScheduleLoadFinishedListener(this);
                    if (isDbLoaded()) {
                        updateInternal();
                    }
                }
            };

    private Listener mChannelDataLoadListener = new Listener() {
        @Override
        public void onLoadFinished() {
            mChannelDataManager.removeListener(this);
            if (isDbLoaded()) {
                updateInternal();
            }
        }

        @Override
        public void onChannelListUpdated() { }

        @Override
        public void onChannelBrowsableChanged() { }
    };

    /**
     * Creates a scheduler to schedule alarms for scheduled recordings and create recording tasks.
     * This method should be only called once in the life-cycle of the application.
     */
    public static RecordingScheduler createScheduler(Context context) {
        SoftPreconditions.checkState(
                TvApplication.getSingletons(context).getRecordingScheduler() == null);
        HandlerThread handlerThread = new HandlerThread(HANDLER_THREAD_NAME);
        handlerThread.start();
        ApplicationSingletons singletons = TvApplication.getSingletons(context);
        return new RecordingScheduler(handlerThread.getLooper(),
                singletons.getDvrManager(), singletons.getInputSessionManager(),
                (WritableDvrDataManager) singletons.getDvrDataManager(),
                singletons.getChannelDataManager(), singletons.getTvInputManagerHelper(), context,
                Clock.SYSTEM, (AlarmManager) context.getSystemService(Context.ALARM_SERVICE));
    }

    @VisibleForTesting
    RecordingScheduler(Looper looper, DvrManager dvrManager, InputSessionManager sessionManager,
            WritableDvrDataManager dataManager, ChannelDataManager channelDataManager,
            TvInputManagerHelper inputManager, Context context, Clock clock,
            AlarmManager alarmManager) {
        mLooper = looper;
        mDvrManager = dvrManager;
        mSessionManager = sessionManager;
        mDataManager = dataManager;
        mChannelDataManager = channelDataManager;
        mInputManager = inputManager;
        mContext = context;
        mClock = clock;
        mAlarmManager = alarmManager;
        mDataManager.addScheduledRecordingListener(this);
        mInputManager.addCallback(this);
        if (isDbLoaded()) {
            updateInternal();
        } else {
            if (!mDataManager.isDvrScheduleLoadFinished()) {
                mDataManager.addDvrScheduleLoadFinishedListener(mDvrScheduleLoadListener);
            }
            if (!mChannelDataManager.isDbLoadFinished()) {
                mChannelDataManager.addListener(mChannelDataLoadListener);
            }
        }
    }

    /**
     * Start recording that will happen soon, and set the next alarm time.
     */
    public void updateAndStartServiceIfNeeded() {
        if (DEBUG) Log.d(TAG, "update and start service if needed");
        if (isDbLoaded()) {
            updateInternal();
        } else {
            // updateInternal will be called when DB is loaded. Start DvrRecordingService to
            // prevent process being killed before that.
            DvrRecordingService.startForegroundService(mContext, false);
        }
    }

    private void updateInternal() {
        boolean recordingSoon = updatePendingRecordings();
        updateNextAlarm();
        if (recordingSoon) {
            // Start DvrRecordingService to protect upcoming recording task from being killed.
            DvrRecordingService.startForegroundService(mContext, true);
        } else {
            DvrRecordingService.stopForegroundIfNotRecording();
        }
    }

    private boolean updatePendingRecordings() {
        List<ScheduledRecording> scheduledRecordings = mDataManager
                .getScheduledRecordings(new Range<>(mLastStartTimePendingMs,
                                mClock.currentTimeMillis() + SOON_DURATION_IN_MS),
                        ScheduledRecording.STATE_RECORDING_NOT_STARTED);
        for (ScheduledRecording r : scheduledRecordings) {
            scheduleRecordingSoon(r);
        }
        // update() may be called multiple times, under this situation, pending recordings may be
        // already updated thus scheduledRecordings may have a size of 0. Therefore we also have to
        // check mLastStartTimePendingMs to check if we have upcoming recordings and prevent the
        // recording service being wrongly pushed back to background in updateInternal().
        return scheduledRecordings.size() > 0
                || (mLastStartTimePendingMs > mClock.currentTimeMillis()
                && mLastStartTimePendingMs < mClock.currentTimeMillis() + SOON_DURATION_IN_MS);
    }

    private boolean isDbLoaded() {
        return mDataManager.isDvrScheduleLoadFinished() && mChannelDataManager.isDbLoadFinished();
    }

    @Override
    public void onScheduledRecordingAdded(ScheduledRecording... schedules) {
        if (DEBUG) Log.d(TAG, "added " + Arrays.asList(schedules));
        if (!isDbLoaded()) {
            return;
        }
        handleScheduleChange(schedules);
    }

    @Override
    public void onScheduledRecordingRemoved(ScheduledRecording... schedules) {
        if (DEBUG) Log.d(TAG, "removed " + Arrays.asList(schedules));
        if (!isDbLoaded()) {
            return;
        }
        boolean needToUpdateAlarm = false;
        for (ScheduledRecording schedule : schedules) {
            InputTaskScheduler inputTaskScheduler = mInputSchedulerMap.get(schedule.getInputId());
            if (inputTaskScheduler != null) {
                inputTaskScheduler.removeSchedule(schedule);
                needToUpdateAlarm = true;
            }
        }
        if (needToUpdateAlarm) {
            updateNextAlarm();
        }
    }

    @Override
    public void onScheduledRecordingStatusChanged(ScheduledRecording... schedules) {
        if (DEBUG) Log.d(TAG, "state changed " + Arrays.asList(schedules));
        if (!isDbLoaded()) {
            return;
        }
        // Update the recordings.
        for (ScheduledRecording schedule : schedules) {
            InputTaskScheduler inputTaskScheduler = mInputSchedulerMap.get(schedule.getInputId());
            if (inputTaskScheduler != null) {
                inputTaskScheduler.updateSchedule(schedule);
            }
        }
        handleScheduleChange(schedules);
    }

    private void handleScheduleChange(ScheduledRecording... schedules) {
        boolean needToUpdateAlarm = false;
        for (ScheduledRecording schedule : schedules) {
            if (schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                if (startsWithin(schedule, SOON_DURATION_IN_MS)) {
                    scheduleRecordingSoon(schedule);
                } else {
                    needToUpdateAlarm = true;
                }
            }
        }
        if (needToUpdateAlarm) {
            updateNextAlarm();
        }
    }

    private void scheduleRecordingSoon(ScheduledRecording schedule) {
        TvInputInfo input = Utils.getTvInputInfoForInputId(mContext, schedule.getInputId());
        if (input == null) {
            Log.e(TAG, "Can't find input for " + schedule);
            mDataManager.changeState(schedule, ScheduledRecording.STATE_RECORDING_FAILED);
            return;
        }
        if (!input.canRecord() || input.getTunerCount() <= 0) {
            Log.e(TAG, "TV input doesn't support recording: " + input);
            mDataManager.changeState(schedule, ScheduledRecording.STATE_RECORDING_FAILED);
            return;
        }
        InputTaskScheduler inputTaskScheduler = mInputSchedulerMap.get(input.getId());
        if (inputTaskScheduler == null) {
            inputTaskScheduler = new InputTaskScheduler(mContext, input, mLooper,
                    mChannelDataManager, mDvrManager, mDataManager, mSessionManager, mClock);
            mInputSchedulerMap.put(input.getId(), inputTaskScheduler);
        }
        inputTaskScheduler.addSchedule(schedule);
        if (mLastStartTimePendingMs < schedule.getStartTimeMs()) {
            mLastStartTimePendingMs = schedule.getStartTimeMs();
        }
    }

    private void updateNextAlarm() {
        long nextStartTime = mDataManager.getNextScheduledStartTimeAfter(
                Math.max(mLastStartTimePendingMs, mClock.currentTimeMillis()));
        if (nextStartTime != DvrDataManager.NEXT_START_TIME_NOT_FOUND) {
            long wakeAt = nextStartTime - MS_TO_WAKE_BEFORE_START;
            if (DEBUG) Log.d(TAG, "Set alarm to record at " + wakeAt);
            Intent intent = new Intent(mContext, DvrStartRecordingReceiver.class);
            PendingIntent alarmIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
            // This will cancel the previous alarm.
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wakeAt, alarmIntent);
        } else {
            if (DEBUG) Log.d(TAG, "No future recording, alarm not set");
        }
    }

    @VisibleForTesting
    boolean startsWithin(ScheduledRecording scheduledRecording, long durationInMs) {
        return mClock.currentTimeMillis() >= scheduledRecording.getStartTimeMs() - durationInMs;
    }

    // No need to remove input task schedule worker when the input is removed. If the input is
    // removed temporarily, the scheduler should keep the non-started schedules.
    @Override
    public void onInputUpdated(String inputId) {
        InputTaskScheduler inputTaskScheduler = mInputSchedulerMap.get(inputId);
        if (inputTaskScheduler != null) {
            inputTaskScheduler.updateTvInputInfo(Utils.getTvInputInfoForInputId(mContext, inputId));
        }
    }

    @Override
    public void onTvInputInfoUpdated(TvInputInfo input) {
        InputTaskScheduler inputTaskScheduler = mInputSchedulerMap.get(input.getId());
        if (inputTaskScheduler != null) {
            inputTaskScheduler.updateTvInputInfo(input);
        }
    }
}
