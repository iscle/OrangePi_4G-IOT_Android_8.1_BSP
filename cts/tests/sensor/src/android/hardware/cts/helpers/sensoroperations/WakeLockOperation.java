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

import android.content.Context;
import android.hardware.cts.helpers.reporting.ISensorTestNode;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

/**
 * An {@link SensorOperation} which holds a wake-lock while performing another
 * {@link SensorOperation}.
 */
public class WakeLockOperation extends SensorOperation {
    private static final String TAG = "WakeLockOperation";

    private final SensorOperation mOperation;
    private final Context mContext;
    private final int mWakeLockFlags;

    /**
     * Constructor for {@link WakeLockOperation}.
     *
     * @param operation the child {@link SensorOperation} to perform after the delay
     * @param context the context used to access the power manager
     * @param wakeLockFlags the flags used when acquiring the wake-lock
     */
    public WakeLockOperation(SensorOperation operation, Context context, int wakeLockFlags) {
        super(operation.getStats());
        mOperation = operation;
        mContext = context;
        mWakeLockFlags = wakeLockFlags;
    }

    /**
     * Constructor for {@link WakeLockOperation}.
     *
     * @param operation the child {@link SensorOperation} to perform after the delay
     * @param context the context used to access the power manager
     */
    public WakeLockOperation(SensorOperation operation, Context context) {
        this(operation, context, PowerManager.PARTIAL_WAKE_LOCK);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        WakeLock wakeLock = pm.newWakeLock(mWakeLockFlags, TAG);
        wakeLock.acquire();
        try {
            mOperation.execute(asTestNode(parent));
        } finally {
            wakeLock.release();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SensorOperation clone() {
        return new WakeLockOperation(mOperation.clone(), mContext, mWakeLockFlags);
    }
}
