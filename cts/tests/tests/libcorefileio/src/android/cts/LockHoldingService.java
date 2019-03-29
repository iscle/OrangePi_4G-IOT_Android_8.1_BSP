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
 * limitations under the License
 */

package android.cts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.concurrent.ExecutionException;

import static android.cts.FileChannelInterProcessLockTest.ChannelType;
import static android.cts.FileChannelInterProcessLockTest.LockType;

/**
 * A Service that listens for commands from the FileChannelInterProcessLockTest to acquire locks of
 * different types. It exists to test the behavior when file locks are acquired/released across
 * multiple processes.
 */
public class LockHoldingService extends Service {

    /**
     *  The key of the Bundle extra used to record a time after a lock is released by the service.
     */
    static final String LOCK_DEFINITELY_RELEASED_TIMESTAMP = "lockReleasedTimestamp";

    /**
     * The key of the Bundle extra used to record just before the lock is released by the service.
     */
    static final String LOCK_NOT_YET_RELEASED_TIMESTAMP = "lockNotReleasedTimestamp";

    /**
     * The key of the Bundle extra used to send general notifications to the test.
     */
    static final String NOTIFICATION_KEY = "notification";

    /**
     * The value for the notification sent to the test after the service starts.
     */
    static final String NOTIFICATION_START = "onStart";

    /**
     * The value for the notification sent to the test just before the service stops.
     */
    static final String NOTIFICATION_STOP = "onStop";

    /**
     * The value for the notification sent to the test after the lock is acquired.
     */
    static final String NOTIFICATION_LOCK_HELD = "lockHeld";

    /**
     * The value for the notification sent to the test after the lock is released
     */
    static final String NOTIFICATION_LOCK_RELEASED = "lockReleased";

    /**
     * The key of the Bundle extra used to send time for which the service should wait before
     * releasing the lock.
     */
    static final String TIME_TO_HOLD_LOCK_KEY = "timeToHoldLock";

    /**
     * The key of the Bundle extra used for the type of lock to be held.
     */
    static final String LOCK_TYPE_KEY = "lockType";

    /**
     * The key of the Bundle extra used for the type of the channel that acquires the lock.
     */
    static final String CHANNEL_TYPE_KEY = "channelType";

    /**
     * The key of the Bundle extra used to let he service know whether to release the lock after
     * some time.
     */
    static final String LOCK_BEHAVIOR_RELEASE_AND_NOTIFY_KEY = "releaseAndNotify";

    static final String ACTION_TYPE_FOR_INTENT_COMMUNICATION
            = "android.cts.CtsLibcoreFileIOTestCases";

    final String LOG_MESSAGE_TAG = "CtsLibcoreFileIOTestCases";

    private FileLock fileLock = null;

    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        try {
            if (intent.getBooleanExtra(LOCK_BEHAVIOR_RELEASE_AND_NOTIFY_KEY, false)) {
                acquireLockAndThenWaitThenRelease(intent);
            } else {
                acquireLock(intent);
            }
        } catch (Exception e) {
            Log.e(LOG_MESSAGE_TAG, "Exception acquire lock", e);
        }
        return START_STICKY;
    }

    /**
     * Acquires the lock asked by the test indefinitely.
     */
    private void acquireLock(Intent intent) throws IOException,
            InterruptedException, ExecutionException {
        LockType lockType = (LockType) intent.getSerializableExtra(LOCK_TYPE_KEY);
        ChannelType channelType = (ChannelType) intent.getSerializableExtra(CHANNEL_TYPE_KEY);

        // Acquire the lock based on the information contained in the intent received.
        this.fileLock = FileChannelInterProcessLockTest.acquire(lockType, channelType);
        Intent responseIntent = new Intent()
                .setPackage("android.libcorefileio.cts")
                .putExtra(NOTIFICATION_KEY, NOTIFICATION_LOCK_HELD)
                .setAction(ACTION_TYPE_FOR_INTENT_COMMUNICATION);
        sendBroadcast(responseIntent);
    }

    /**
     * Acquires and holds the lock for a time specified by the test. Sends a broadcast message after
     * releasing the lock.
     */
    private void acquireLockAndThenWaitThenRelease(Intent intent)
            throws IOException, InterruptedException, ExecutionException {
        long lockHoldTimeMillis = intent.getLongExtra(TIME_TO_HOLD_LOCK_KEY, 0);

        // Acquire the lock.
        LockType lockType = (LockType) intent.getSerializableExtra(LOCK_TYPE_KEY);
        ChannelType channelType = (ChannelType) intent.getSerializableExtra(CHANNEL_TYPE_KEY);
        this.fileLock = FileChannelInterProcessLockTest.acquire(lockType, channelType);

        // Signal the lock is now held.
        Intent heldIntent = new Intent()
                .setPackage("android.libcorefileio.cts")
                .putExtra(NOTIFICATION_KEY, NOTIFICATION_LOCK_HELD)
                .setAction(ACTION_TYPE_FOR_INTENT_COMMUNICATION);
        sendBroadcast(heldIntent);

        Thread.sleep(lockHoldTimeMillis);

        long lockNotReleasedTimestamp = System.currentTimeMillis();

        // Release the lock
        fileLock.release();

        long lockReleasedTimestamp = System.currentTimeMillis();

        // Signal the lock is released and some information about timing.
        Intent releaseIntent = new Intent()
                .setPackage("android.libcorefileio.cts")
                .putExtra(NOTIFICATION_KEY, NOTIFICATION_LOCK_RELEASED)
                .putExtra(LOCK_NOT_YET_RELEASED_TIMESTAMP, lockNotReleasedTimestamp)
                .putExtra(LOCK_DEFINITELY_RELEASED_TIMESTAMP, lockReleasedTimestamp)
                .setAction(ACTION_TYPE_FOR_INTENT_COMMUNICATION);
        sendBroadcast(releaseIntent);
    }

    @Override
    public void onDestroy() {
        try {
            if (fileLock != null) {
                fileLock.release();
            }
        } catch (IOException e) {
            Log.e(LOG_MESSAGE_TAG, e.getMessage());
        }
        Intent intent = new Intent()
                .setPackage("android.libcorefileio.cts")
                .putExtra(NOTIFICATION_KEY, NOTIFICATION_STOP)
                .setAction(ACTION_TYPE_FOR_INTENT_COMMUNICATION);
        sendBroadcast(intent);
    }
}
