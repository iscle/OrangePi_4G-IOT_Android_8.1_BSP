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
package com.android.compatibility.common.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A receiver that allows caller to wait for the broadcast synchronously. Notice that you should not
 * reuse the instance. Usage is typically like this:
 * <pre>
 *     BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(context, "action");
 *     try {
 *         receiver.register();
 *         Intent intent = receiver.awaitForBroadcast();
 *         // assert the intent
 *     } finally {
 *         receiver.unregisterQuietly();
 *     }
 * </pre>
 */
public class BlockingBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BlockingBroadcast";

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final BlockingQueue<Intent> mBlockingQueue;
    private final String mExpectedAction;
    private final Context mContext;

    public BlockingBroadcastReceiver(Context context, String expectedAction) {
        mContext = context;
        mExpectedAction = expectedAction;
        mBlockingQueue = new ArrayBlockingQueue<>(1);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mExpectedAction.equals(intent.getAction())) {
            mBlockingQueue.add(intent);
        }
    }

    public void register() {
        mContext.registerReceiver(this, new IntentFilter(mExpectedAction));
    }

    /**
     * Wait until the broadcast and return the received broadcast intent. {@code null} is returned
     * if no broadcast with expected action is received within 10 seconds.
     */
    public @Nullable Intent awaitForBroadcast() {
        try {
            return mBlockingQueue.poll(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForBroadcast get interrupted: ", e);
        }
        return null;
    }

    public void unregisterQuietly() {
        try {
            mContext.unregisterReceiver(this);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to unregister BlockingBroadcastReceiver: ", ex);
        }
    }
}