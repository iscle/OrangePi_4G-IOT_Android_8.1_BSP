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
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

/**
 * Helper for sending a broadcast and waiting for a result from it.
 */
public final class SyncOrderedBroadcast {
    boolean mHasResult;
    int mReceivedCode;
    String mReceivedData;
    Bundle mReceivedExtras;

    final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            synchronized (SyncOrderedBroadcast.this) {
                mReceivedCode = getResultCode();
                mReceivedData = getResultData();
                mReceivedExtras = getResultExtras(false);
                mHasResult = true;
                SyncOrderedBroadcast.this.notifyAll();
            }
        }
    };

    public int sendAndWait(Context context, Intent broadcast, int initialCode,
            String initialData, Bundle initialExtras, long timeout) {
        mHasResult = false;
        context.sendOrderedBroadcast(broadcast, null, mReceiver,
                new Handler(context.getMainLooper()), initialCode, initialData, initialExtras);

        final long endTime = SystemClock.uptimeMillis() + timeout;

        synchronized (this) {
            while (!mHasResult) {
                final long now = SystemClock.uptimeMillis();
                if (now >= endTime) {
                    throw new IllegalStateException("Timed out waiting for broadcast " + broadcast);
                }
                try {
                    wait(endTime - now);
                } catch (InterruptedException e) {
                }
            }
            return mReceivedCode;
        }
    }

    public int getReceivedCode() {
        return mReceivedCode;
    }

    public String getReceivedData() {
        return mReceivedData;
    }

    public Bundle getReceivedExtras() {
        return mReceivedExtras;
    }
}
