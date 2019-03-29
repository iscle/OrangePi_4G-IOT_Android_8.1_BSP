/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.hardware.cts.helpers.sensoroperations;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.cts.helpers.reporting.ISensorTestNode;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import java.util.concurrent.TimeUnit;

/**
 * An {@link SensorOperation} which performs another {@link SensorOperation} and then wakes up
 * after a specified period of time and waits for the child operation to complete.
 * <p>
 * This operation can be used to allow the device to go to sleep and wake it up after a specified
 * period of time. After the device wakes up, this operation will hold a wake lock until the child
 * operation finishes. This operation will not force the device into suspend, so if another
 * operation is holding a wake lock, the device will stay awake.  Also, if the child operation
 * finishes before the specified period, this operation return when the child operation finishes
 * but wake the device one time at the specified period.
 * </p>
 */
public class AlarmOperation extends SensorOperation {
    private static final String ACTION = "AlarmOperationAction";
    private static final String WAKE_LOCK_TAG = "AlarmOperationWakeLock";

    private final SensorOperation mOperation;
    private final Context mContext;
    private final long mSleepDuration;
    private final TimeUnit mTimeUnit;

    private boolean mCompleted = false;
    private WakeLock mWakeLock = null;

    /**
     * Constructor for {@link DelaySensorOperation}
     *
     * @param operation the child {@link SensorOperation} to perform after the delay
     * @param context the context used to access the alarm manager
     * @param sleepDuration the amount of time to sleep
     * @param timeUnit the unit of the duration
     */
    public AlarmOperation(
            SensorOperation operation,
            Context context,
            long sleepDuration,
            TimeUnit timeUnit) {
        super(operation.getStats());
        mOperation = operation;
        mContext = context;
        mSleepDuration = sleepDuration;
        mTimeUnit = timeUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        // Start alarm
        IntentFilter intentFilter = new IntentFilter(ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                acquireWakeLock();
            }
        };
        mContext.registerReceiver(receiver, intentFilter);

        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        long wakeupTimeMs = (System.currentTimeMillis()
                + TimeUnit.MILLISECONDS.convert(mSleepDuration, mTimeUnit));
        Intent intent = new Intent(ACTION);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
        am.setExact(AlarmManager.RTC_WAKEUP, wakeupTimeMs, pendingIntent);

        // Execute operation
        try {
            mOperation.execute(asTestNode(parent));
        } finally {
            releaseWakeLock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AlarmOperation clone() {
        return new AlarmOperation(mOperation, mContext, mSleepDuration, mTimeUnit);
    }

    /**
     * Method that acquires a wake lock if a wake lock has not already been acquired and if the
     * operation has not yet completed.
     */
    private synchronized void acquireWakeLock() {
        // Don't acquire wake lock if the operation has already completed.
        if (mCompleted || mWakeLock != null) {
            return;
        }
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
    }

    /**
     * Method that releases the wake lock if it has been acquired.
     */
    private synchronized void releaseWakeLock() {
        mCompleted = true;
        if (mWakeLock != null) {
            mWakeLock.release();
        }
        mWakeLock = null;
    }
}
