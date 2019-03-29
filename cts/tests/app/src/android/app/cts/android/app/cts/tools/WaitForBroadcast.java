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

package android.app.cts.android.app.cts.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

/**
 * Helper to wait for a broadcast to be sent.
 */
public class WaitForBroadcast {
    final Context mContext;

    String mWaitingAction;
    boolean mHasResult;
    Intent mReceivedIntent;

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            synchronized (WaitForBroadcast.this) {
                mReceivedIntent = intent;
                mHasResult = true;
                WaitForBroadcast.this.notifyAll();
            }
        }
    };

    public WaitForBroadcast(Context context) {
        mContext = context;
    }

    public void prepare(String action) {
        if (mWaitingAction != null) {
            throw new IllegalStateException("Already prepared");
        }
        mWaitingAction = action;
        IntentFilter filter = new IntentFilter();
        filter.addAction(action);
        mContext.registerReceiver(mReceiver, filter);
    }

    public Intent doWait(long timeout) {
        final long endTime = SystemClock.uptimeMillis() + timeout;

        synchronized (this) {
            while (!mHasResult) {
                final long now = SystemClock.uptimeMillis();
                if (now >= endTime) {
                    String action = mWaitingAction;
                    cleanup();
                    throw new IllegalStateException("Timed out waiting for broadcast " + action);
                }
                try {
                    wait(endTime - now);
                } catch (InterruptedException e) {
                }
            }
            cleanup();
            return mReceivedIntent;
        }
    }

    void cleanup() {
        if (mWaitingAction != null) {
            mContext.unregisterReceiver(mReceiver);
            mWaitingAction = null;
        }
    }
}
