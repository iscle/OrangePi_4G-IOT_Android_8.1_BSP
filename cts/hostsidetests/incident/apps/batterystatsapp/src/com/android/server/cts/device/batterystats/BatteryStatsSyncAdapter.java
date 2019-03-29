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
package com.android.server.cts.device.batterystats;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import org.junit.Assert;

import javax.annotation.concurrent.GuardedBy;

/**
 * Sync adapter for the sync test.
 */
public class BatteryStatsSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "BatteryStatsSyncAdapter";

    private static final int TIMEOUT_SECONDS = 60 * 2;

    private static final Object sLock = new Object();

    /**
     * # of total syncs happened; used to wait until a request sync finishes.
     */
    @GuardedBy("sLock")
    private static int sSyncCount;

    public BatteryStatsSyncAdapter(Context context) {
        // No need for auto-initialization because we set isSyncable in the test anyway.
        super(context, /* autoInitialize= */ false);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        synchronized (sLock) {
            sSyncCount++;
            Log.i(TAG, "onPerformSync: count -> " + sSyncCount);
            sLock.notifyAll();
        }
    }

    /**
     * Returns the current sync count.
     */
    private static int getSyncCount() {
        synchronized (sLock) {
            return sSyncCount;
        }
    }

    /**
     * Wait until the sync count reaches the given value.
     */
    private static void waitUntilSyncCount(int expectCount) throws Exception {
        final long timeout = SystemClock.elapsedRealtime() + (TIMEOUT_SECONDS * 1000);

        synchronized (sLock) {
            for (;;) {
                Log.i(TAG, "waitUntilSyncCount: current count=" + sSyncCount);
                if (sSyncCount >= expectCount) {
                    return;
                }
                final long sleep = timeout - SystemClock.elapsedRealtime();
                if (sleep <= 0) {
                    break;
                }
                sLock.wait(sleep);
            }
        }
        Assert.fail("Sync didn't happen.");
    }

    /**
     * Request a sync on the given account, and wait for it.
     */
    public static void requestSync(Account account) throws Exception {
        final int startCount = BatteryStatsSyncAdapter.getSyncCount();

        final Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);

        ContentResolver.requestSync(account, BatteryStatsProvider.AUTHORITY, extras);

        waitUntilSyncCount(startCount + 1);
    }

    /**
     * Cancel all pending sync requests on the given account.
     */
    public static void cancelPendingSyncs(Account account) throws Exception {
        final long timeout = SystemClock.elapsedRealtime() + (TIMEOUT_SECONDS * 1000);

        ContentResolver.cancelSync(account, BatteryStatsProvider.AUTHORITY);

        for (;;) {
            if (!ContentResolver.isSyncPending(account, BatteryStatsProvider.AUTHORITY)
                && !ContentResolver.isSyncActive(account, BatteryStatsProvider.AUTHORITY)) {
                return;
            }
            final long sleep = timeout - SystemClock.elapsedRealtime();
            if (sleep <= 0) {
                break;
            }
            Thread.sleep(sleep);
        }
        Assert.fail("Couldn't cancel pending sync.");
    }
}
