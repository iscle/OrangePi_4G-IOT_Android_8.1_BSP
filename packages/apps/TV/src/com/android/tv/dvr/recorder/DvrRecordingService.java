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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.OnRecordingSessionChangeListener;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.dvr.WritableDvrDataManager;
import com.android.tv.util.Clock;
import com.android.tv.util.RecurringRunner;

/**
 * DVR recording service. This service should be a foreground service and send a notification
 * to users to do long-running recording task.
 *
 * <p>This service is waken up when there's a scheduled recording coming soon and at boot completed
 * since schedules have to be loaded from databases in order to set new recording alarms, which
 * might take a long time.
 */
@RequiresApi(Build.VERSION_CODES.N)
public class DvrRecordingService extends Service {
    private static final String TAG = "DvrRecordingService";
    private static final boolean DEBUG = false;

    private static final String DVR_NOTIFICATION_CHANNEL_ID = "dvr_notification_channel";
    private static final int ONGOING_NOTIFICATION_ID = 1;
    @VisibleForTesting static final String EXTRA_START_FOR_RECORDING = "start_for_recording";

    private static DvrRecordingService sInstance;
    private NotificationChannel mNotificationChannel;
    private String mContentTitle;
    private String mContentTextRecording;
    private String mContentTextLoading;

    /**
     * Starts the service in foreground.
     *
     * @param startForRecording {@code true} if there are upcoming recordings in
     *                          {@link RecordingScheduler#SOON_DURATION_IN_MS} and the service is
     *                          started in foreground for those recordings.
     */
    @MainThread
    static void startForegroundService(Context context, boolean startForRecording) {
        if (sInstance == null) {
            Intent intent = new Intent(context, DvrRecordingService.class);
            intent.putExtra(EXTRA_START_FOR_RECORDING, startForRecording);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } else {
            sInstance.startForeground(startForRecording);
        }
    }

    @MainThread
    static void stopForegroundIfNotRecording() {
        if (sInstance != null) {
            sInstance.stopForegroundIfNotRecordingInternal();
        }
    }

    private RecurringRunner mReaperRunner;
    private InputSessionManager mSessionManager;

    @VisibleForTesting boolean mIsRecording;
    private boolean mForeground;

    @VisibleForTesting final OnRecordingSessionChangeListener mOnRecordingSessionChangeListener =
            new OnRecordingSessionChangeListener() {
                @Override
                public void onRecordingSessionChange(final boolean create, final int count) {
                    mIsRecording = count > 0;
                    if (create) {
                        startForeground(true);
                    } else {
                        stopForegroundIfNotRecordingInternal();
                    }
                }
            };

    @Override
    public void onCreate() {
        TvApplication.setCurrentRunningProcess(this, true);
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate();
        SoftPreconditions.checkFeatureEnabled(this, CommonFeatures.DVR, TAG);
        sInstance = this;
        ApplicationSingletons singletons = TvApplication.getSingletons(this);
        WritableDvrDataManager dataManager =
                (WritableDvrDataManager) singletons.getDvrDataManager();
        mSessionManager = singletons.getInputSessionManager();
        mSessionManager.addOnRecordingSessionChangeListener(mOnRecordingSessionChangeListener);
        mReaperRunner = new RecurringRunner(this, java.util.concurrent.TimeUnit.DAYS.toMillis(1),
                new ScheduledProgramReaper(dataManager, Clock.SYSTEM), null);
        mReaperRunner.start();
        mContentTitle = getString(R.string.dvr_notification_content_title);
        mContentTextRecording = getString(R.string.dvr_notification_content_text_recording);
        mContentTextLoading = getString(R.string.dvr_notification_content_text_loading);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand (" + intent + "," + flags + "," + startId + ")");
        if (intent != null) {
            startForeground(intent.getBooleanExtra(EXTRA_START_FOR_RECORDING, false));
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy");
        mReaperRunner.stop();
        mSessionManager.removeRecordingSessionChangeListener(mOnRecordingSessionChangeListener);
        sInstance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @VisibleForTesting
    protected void stopForegroundIfNotRecordingInternal() {
        if (mForeground && !mIsRecording) {
            stopForeground();
        }
    }

    private void startForeground(boolean hasUpcomingRecording) {
        if (!mForeground || hasUpcomingRecording) {
            // We may need to update notification for upcoming recordings.
            mForeground = true;
            startForegroundInternal(hasUpcomingRecording);
        }
    }

    private void stopForeground() {
        stopForegroundInternal();
        mForeground = false;
    }

    @VisibleForTesting
    protected void startForegroundInternal(boolean hasUpcomingRecording) {
        // STOPSHIP: Replace the content title with real UX strings
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(mContentTitle)
                .setContentText(hasUpcomingRecording ? mContentTextRecording : mContentTextLoading)
                .setSmallIcon(R.drawable.ic_dvr);
        Notification notification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                builder.setChannelId(DVR_NOTIFICATION_CHANNEL_ID).build() : builder.build();
        startForeground(ONGOING_NOTIFICATION_ID, notification);
    }

    @VisibleForTesting
    protected void stopForegroundInternal() {
        stopForeground(true);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // STOPSHIP: Replace the channel name with real UX strings
            mNotificationChannel = new NotificationChannel(DVR_NOTIFICATION_CHANNEL_ID,
                    getString(R.string.dvr_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(mNotificationChannel);
        }
    }
}
