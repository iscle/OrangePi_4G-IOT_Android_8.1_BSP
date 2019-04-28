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
 * limitations under the License
 */

package com.android.tv.dvr.recorder;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvRecordingClient.RecordingCallback;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.RecordingSession;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.WritableDvrDataManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.recorder.InputTaskScheduler.HandlerWrapper;
import com.android.tv.util.Clock;
import com.android.tv.util.Utils;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * A Handler that actually starts and stop a recording at the right time.
 *
 * <p>This is run on the looper of thread named {@value DvrRecordingService#HANDLER_THREAD_NAME}.
 * There is only one looper so messages must be handled quickly or start a separate thread.
 */
@WorkerThread
@TargetApi(Build.VERSION_CODES.N)
public class RecordingTask extends RecordingCallback implements Handler.Callback,
        DvrManager.Listener {
    private static final String TAG = "RecordingTask";
    private static final boolean DEBUG = false;

    /**
     * Compares the end time in ascending order.
     */
    public static final Comparator<RecordingTask> END_TIME_COMPARATOR
            = new Comparator<RecordingTask>() {
        @Override
        public int compare(RecordingTask lhs, RecordingTask rhs) {
            return Long.compare(lhs.getEndTimeMs(), rhs.getEndTimeMs());
        }
    };

    /**
     * Compares ID in ascending order.
     */
    public static final Comparator<RecordingTask> ID_COMPARATOR
            = new Comparator<RecordingTask>() {
        @Override
        public int compare(RecordingTask lhs, RecordingTask rhs) {
            return Long.compare(lhs.getScheduleId(), rhs.getScheduleId());
        }
    };

    /**
     * Compares the priority in ascending order.
     */
    public static final Comparator<RecordingTask> PRIORITY_COMPARATOR
            = new Comparator<RecordingTask>() {
        @Override
        public int compare(RecordingTask lhs, RecordingTask rhs) {
            return Long.compare(lhs.getPriority(), rhs.getPriority());
        }
    };

    @VisibleForTesting
    static final int MSG_INITIALIZE = 1;
    @VisibleForTesting
    static final int MSG_START_RECORDING = 2;
    @VisibleForTesting
    static final int MSG_STOP_RECORDING = 3;
    /**
     * Message to update schedule.
     */
    public static final int MSG_UDPATE_SCHEDULE = 4;

    /**
     * The time when the start command will be sent before the recording starts.
     */
    public static final long RECORDING_EARLY_START_OFFSET_MS = TimeUnit.SECONDS.toMillis(3);
    /**
     * If the recording starts later than the scheduled start time or ends before the scheduled end
     * time, it's considered as clipped.
     */
    private static final long CLIPPED_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5);

    @VisibleForTesting
    enum State {
        NOT_STARTED,
        SESSION_ACQUIRED,
        CONNECTION_PENDING,
        CONNECTED,
        RECORDING_STARTED,
        RECORDING_STOP_REQUESTED,
        FINISHED,
        ERROR,
        RELEASED,
    }
    private final InputSessionManager mSessionManager;
    private final DvrManager mDvrManager;
    private final Context mContext;

    private final WritableDvrDataManager mDataManager;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private RecordingSession mRecordingSession;
    private Handler mHandler;
    private ScheduledRecording mScheduledRecording;
    private final Channel mChannel;
    private State mState = State.NOT_STARTED;
    private final Clock mClock;
    private boolean mStartedWithClipping;
    private Uri mRecordedProgramUri;
    private boolean mCanceled;

    RecordingTask(Context context, ScheduledRecording scheduledRecording, Channel channel,
            DvrManager dvrManager, InputSessionManager sessionManager,
            WritableDvrDataManager dataManager, Clock clock) {
        mContext = context;
        mScheduledRecording = scheduledRecording;
        mChannel = channel;
        mSessionManager = sessionManager;
        mDataManager = dataManager;
        mClock = clock;
        mDvrManager = dvrManager;

        if (DEBUG) Log.d(TAG, "created recording task " + mScheduledRecording);
    }

    public void setHandler(Handler handler) {
        mHandler = handler;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (DEBUG) Log.d(TAG, "handleMessage " + msg);
        SoftPreconditions.checkState(msg.what == HandlerWrapper.MESSAGE_REMOVE || mHandler != null,
                TAG, "Null handler trying to handle " + msg);
        try {
            switch (msg.what) {
                case MSG_INITIALIZE:
                    handleInit();
                    break;
                case MSG_START_RECORDING:
                    handleStartRecording();
                    break;
                case MSG_STOP_RECORDING:
                    handleStopRecording();
                    break;
                case MSG_UDPATE_SCHEDULE:
                    handleUpdateSchedule((ScheduledRecording) msg.obj);
                    break;
                case HandlerWrapper.MESSAGE_REMOVE:
                    mHandler.removeCallbacksAndMessages(null);
                    mHandler = null;
                    release();
                    return false;
                default:
                    SoftPreconditions.checkArgument(false, TAG, "unexpected message type " + msg);
                    break;
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Error processing message " + msg + "  for " + mScheduledRecording, e);
            failAndQuit();
        }
        return false;
    }

    @Override
    public void onDisconnected(String inputId) {
        if (DEBUG) Log.d(TAG, "onDisconnected(" + inputId + ")");
        if (mRecordingSession != null && mState != State.FINISHED) {
            failAndQuit();
        }
    }

    @Override
    public void onConnectionFailed(String inputId) {
        if (DEBUG) Log.d(TAG, "onConnectionFailed(" + inputId + ")");
        if (mRecordingSession != null) {
            failAndQuit();
        }
    }

    @Override
    public void onTuned(Uri channelUri) {
        if (DEBUG) Log.d(TAG, "onTuned");
        if (mRecordingSession == null) {
            return;
        }
        mState = State.CONNECTED;
        if (mHandler == null || !sendEmptyMessageAtAbsoluteTime(MSG_START_RECORDING,
                mScheduledRecording.getStartTimeMs() - RECORDING_EARLY_START_OFFSET_MS)) {
            failAndQuit();
        }
    }

    @Override
    public void onRecordingStopped(Uri recordedProgramUri) {
        if (DEBUG) Log.d(TAG, "onRecordingStopped");
        if (mRecordingSession == null) {
            return;
        }
        mRecordedProgramUri = recordedProgramUri;
        mState = State.FINISHED;
        int state = ScheduledRecording.STATE_RECORDING_FINISHED;
        if (mStartedWithClipping || mScheduledRecording.getEndTimeMs() - CLIPPED_THRESHOLD_MS
                > mClock.currentTimeMillis()) {
            state = ScheduledRecording.STATE_RECORDING_CLIPPED;
        }
        updateRecordingState(state);
        sendRemove();
        if (mCanceled) {
            removeRecordedProgram();
        }
    }

    @Override
    public void onError(int reason) {
        if (DEBUG) Log.d(TAG, "onError reason " + reason);
        if (mRecordingSession == null) {
            return;
        }
        switch (reason) {
            case TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE:
                mMainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (TvApplication.getSingletons(mContext).getMainActivityWrapper()
                                .isResumed()) {
                            ScheduledRecording scheduledRecording = mDataManager
                                    .getScheduledRecording(mScheduledRecording.getId());
                            if (scheduledRecording != null) {
                                Toast.makeText(mContext.getApplicationContext(),
                                        mContext.getString(R.string
                                        .dvr_error_insufficient_space_description_one_recording,
                                        scheduledRecording.getProgramDisplayTitle(mContext)),
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        } else {
                            Utils.setRecordingFailedReason(mContext.getApplicationContext(),
                                    TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
                            Utils.addFailedScheduledRecordingInfo(mContext.getApplicationContext(),
                                    mScheduledRecording.getProgramDisplayTitle(mContext));
                        }
                    }
                });
                // Pass through
            default:
                failAndQuit();
                break;
        }
    }

    private void handleInit() {
        if (DEBUG) Log.d(TAG, "handleInit " + mScheduledRecording);
        if (mScheduledRecording.getEndTimeMs() < mClock.currentTimeMillis()) {
            Log.w(TAG, "End time already past, not recording " + mScheduledRecording);
            failAndQuit();
            return;
        }
        if (mChannel == null) {
            Log.w(TAG, "Null channel for " + mScheduledRecording);
            failAndQuit();
            return;
        }
        if (mChannel.getId() != mScheduledRecording.getChannelId()) {
            Log.w(TAG, "Channel" + mChannel + " does not match scheduled recording "
                    + mScheduledRecording);
            failAndQuit();
            return;
        }

        String inputId = mChannel.getInputId();
        mRecordingSession = mSessionManager.createRecordingSession(inputId,
                "recordingTask-" + mScheduledRecording.getId(), this,
                mHandler, mScheduledRecording.getEndTimeMs());
        mState = State.SESSION_ACQUIRED;
        mDvrManager.addListener(this, mHandler);
        mRecordingSession.tune(inputId, mChannel.getUri());
        mState = State.CONNECTION_PENDING;
    }

    private void failAndQuit() {
        if (DEBUG) Log.d(TAG, "failAndQuit");
        updateRecordingState(ScheduledRecording.STATE_RECORDING_FAILED);
        mState = State.ERROR;
        sendRemove();
    }

    private void sendRemove() {
        if (DEBUG) Log.d(TAG, "sendRemove");
        if (mHandler != null) {
            mHandler.sendMessageAtFrontOfQueue(mHandler.obtainMessage(
                    HandlerWrapper.MESSAGE_REMOVE));
        }
    }

    private void handleStartRecording() {
        if (DEBUG) Log.d(TAG, "handleStartRecording " + mScheduledRecording);
        long programId = mScheduledRecording.getProgramId();
        mRecordingSession.startRecording(programId == ScheduledRecording.ID_NOT_SET ? null
                : TvContract.buildProgramUri(programId));
        updateRecordingState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS);
        // If it starts late, it's clipped.
        if (mScheduledRecording.getStartTimeMs() + CLIPPED_THRESHOLD_MS
                < mClock.currentTimeMillis()) {
            mStartedWithClipping = true;
        }
        mState = State.RECORDING_STARTED;

        if (!sendEmptyMessageAtAbsoluteTime(MSG_STOP_RECORDING,
                mScheduledRecording.getEndTimeMs())) {
            failAndQuit();
        }
    }

    private void handleStopRecording() {
        if (DEBUG) Log.d(TAG, "handleStopRecording " + mScheduledRecording);
        mRecordingSession.stopRecording();
        mState = State.RECORDING_STOP_REQUESTED;
    }

    private void handleUpdateSchedule(ScheduledRecording schedule) {
        mScheduledRecording = schedule;
        // Check end time only. The start time is checked in InputTaskScheduler.
        if (schedule.getEndTimeMs() != mScheduledRecording.getEndTimeMs()) {
            if (mRecordingSession != null) {
                mRecordingSession.setEndTimeMs(schedule.getEndTimeMs());
            }
            if (mState == State.RECORDING_STARTED) {
                mHandler.removeMessages(MSG_STOP_RECORDING);
                if (!sendEmptyMessageAtAbsoluteTime(MSG_STOP_RECORDING, schedule.getEndTimeMs())) {
                    failAndQuit();
                }
            }
        }
    }

    @VisibleForTesting
    State getState() {
        return mState;
    }

    private long getScheduleId() {
        return mScheduledRecording.getId();
    }

    /**
     * Returns the priority.
     */
    public long getPriority() {
        return mScheduledRecording.getPriority();
    }

    /**
     * Returns the start time of the recording.
     */
    public long getStartTimeMs() {
        return mScheduledRecording.getStartTimeMs();
    }

    /**
     * Returns the end time of the recording.
     */
    public long getEndTimeMs() {
        return mScheduledRecording.getEndTimeMs();
    }

    private void release() {
        if (mRecordingSession != null) {
            mSessionManager.releaseRecordingSession(mRecordingSession);
            mRecordingSession = null;
        }
        mDvrManager.removeListener(this);
    }

    private boolean sendEmptyMessageAtAbsoluteTime(int what, long when) {
        long now = mClock.currentTimeMillis();
        long delay = Math.max(0L, when - now);
        if (DEBUG) {
            Log.d(TAG, "Sending message " + what + " with a delay of " + delay / 1000
                    + " seconds to arrive at " + Utils.toIsoDateTimeString(when));
        }
        return mHandler.sendEmptyMessageDelayed(what, delay);
    }

    private void updateRecordingState(@ScheduledRecording.RecordingState int state) {
        if (DEBUG) Log.d(TAG, "Updating the state of " + mScheduledRecording + " to " + state);
        mScheduledRecording = ScheduledRecording.buildFrom(mScheduledRecording).setState(state)
                .build();
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                ScheduledRecording schedule = mDataManager.getScheduledRecording(
                        mScheduledRecording.getId());
                if (schedule == null) {
                    // Schedule has been deleted. Delete the recorded program.
                    removeRecordedProgram();
                } else  {
                    // Update the state based on the object in DataManager in case when it has been
                    // updated. mScheduledRecording will be updated from
                    // onScheduledRecordingStateChanged.
                    mDataManager.updateScheduledRecording(ScheduledRecording.buildFrom(schedule)
                            .setState(state).build());
                }
            }
        });
    }

    @Override
    public void onStopRecordingRequested(ScheduledRecording recording) {
        if (recording.getId() != mScheduledRecording.getId()) {
            return;
        }
        stop();
    }

    /**
     * Starts the task.
     */
    public void start() {
        mHandler.sendEmptyMessage(MSG_INITIALIZE);
    }

    /**
     * Stops the task.
     */
    public void stop() {
        if (DEBUG) Log.d(TAG, "stop");
        switch (mState) {
            case RECORDING_STARTED:
                mHandler.removeMessages(MSG_STOP_RECORDING);
                handleStopRecording();
                break;
            case RECORDING_STOP_REQUESTED:
                // Do nothing
                break;
            case NOT_STARTED:
            case SESSION_ACQUIRED:
            case CONNECTION_PENDING:
            case CONNECTED:
            case FINISHED:
            case ERROR:
            case RELEASED:
            default:
                sendRemove();
                break;
        }
    }

    /**
     * Cancels the task
     */
    public void cancel() {
        if (DEBUG) Log.d(TAG, "cancel");
        mCanceled = true;
        stop();
        removeRecordedProgram();
    }

    /**
     * Clean up the task.
     */
    public void cleanUp() {
        if (mState == State.RECORDING_STARTED || mState == State.RECORDING_STOP_REQUESTED) {
            updateRecordingState(ScheduledRecording.STATE_RECORDING_FAILED);
        }
        release();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "(" + mScheduledRecording + ")";
    }

    private void removeRecordedProgram() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (mRecordedProgramUri != null) {
                    mDvrManager.removeRecordedProgram(mRecordedProgramUri);
                }
            }
        });
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mMainThreadHandler.post(runnable);
        }
    }
}
