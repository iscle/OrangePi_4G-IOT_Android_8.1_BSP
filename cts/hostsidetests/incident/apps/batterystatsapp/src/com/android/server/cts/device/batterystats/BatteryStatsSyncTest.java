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
import android.content.ContentResolver;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BatteryStatsSyncTest {
    private static final String TAG = "BatteryStatsSyncTest";

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    @Before
    public void setUp() {
        BatteryStatsAuthenticator.removeAllAccounts(getContext());
    }

    @After
    public void tearDown() {
        BatteryStatsAuthenticator.removeAllAccounts(getContext());
    }

    /**
     * Run a sync N times and make sure it shows up in the battery stats.
     */
    @Test
    public void testRunSyncs() throws Exception {
        final Account account = BatteryStatsAuthenticator.getTestAccount();

        // Create the test account.
        BatteryStatsAuthenticator.ensureTestAccount(getContext());

        // Just force set is syncable.
        ContentResolver.setMasterSyncAutomatically(true);
        ContentResolver.setIsSyncable(account, BatteryStatsProvider.AUTHORITY, 1);

        // Cancel the initial sync.
        BatteryStatsSyncAdapter.cancelPendingSyncs(account);

        final int NUM_SYNC = 10;

        // Run syncs.
        for (int i = 0; i < NUM_SYNC; i++) {
            BatteryStatsSyncAdapter.requestSync(account);
            Thread.sleep(1000);
        }
    }
}
