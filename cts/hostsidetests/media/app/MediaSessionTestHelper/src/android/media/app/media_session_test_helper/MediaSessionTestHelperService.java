/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.app.media_session_test_helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.media.cts.MediaSessionTestHelperConstants;

/**
 * Service of the media session's host-side CTS helper.
 * <p>This is the foreground service to prevent this process from being killed by the OOM killer
 * while the host-side tests are running.
 */
public class MediaSessionTestHelperService extends Service {
    private static final String TAG = "MediaSessionTestHelperService";

    private static final int NOTIFICATION_ID = 100;
    private static final String NOTIFICATION_CHANNEL = TAG;

    private MediaSession mMediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        // Build notification UI to make this a foreground service.
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL,
                getString(R.string.label), NotificationManager.IMPORTANCE_DEFAULT);
        manager.createNotificationChannel(notificationChannel);

        Notification notification = new Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle(getString(R.string.label)).build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (!TextUtils.equals(intent.getAction(), MediaSessionTestHelperConstants.ACTION_CONTROL)) {
            Log.e(TAG, "Invalid action " + intent.getAction() + ". Test may fail");
            return START_STICKY;
        }
        int flag = intent.getIntExtra(MediaSessionTestHelperConstants.EXTRA_CONTROL_COMMAND, 0);
        if ((flag & MediaSessionTestHelperConstants.FLAG_CREATE_MEDIA_SESSION) != 0) {
            if (mMediaSession == null) {
                mMediaSession = new MediaSession(this, TAG);
            }
        }
        if (mMediaSession != null) {
            if ((flag & MediaSessionTestHelperConstants.FLAG_SET_MEDIA_SESSION_ACTIVE) != 0) {
                mMediaSession.setActive(true);
            }
            if ((flag & MediaSessionTestHelperConstants.FLAG_SET_MEDIA_SESSION_INACTIVE) != 0) {
                mMediaSession.setActive(false);
            }
        }
        if ((flag & MediaSessionTestHelperConstants.FLAG_RELEASE_MEDIA_SESSION) != 0) {
            releaseMediaSession();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // It's not a bind service.
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseMediaSession();
    }

    private void releaseMediaSession() {
        if (mMediaSession != null) {
            mMediaSession.release();
            mMediaSession = null;
        }
    }
}
