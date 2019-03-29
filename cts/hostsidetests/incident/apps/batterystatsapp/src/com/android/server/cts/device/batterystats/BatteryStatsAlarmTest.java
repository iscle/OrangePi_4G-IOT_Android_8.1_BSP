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

import static org.junit.Assert.assertTrue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class BatteryStatsAlarmTest {
    private static final String TAG = "BatteryStatsAlarmTest";

    /**
     * Set and fire a wakeup alarm 3 times.
     */
    @Test
    public void testAlarms() throws Exception {
        final int NUM_ALARMS = 3;

        final Context context = InstrumentationRegistry.getContext();

        final Intent intent = new Intent("com.android.server.cts.device.batterystats.ALARM");
        final IntentFilter inf = new IntentFilter(intent.getAction());

        final CountDownLatch latch = new CountDownLatch(NUM_ALARMS);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received: " + intent);
                latch.countDown();
            }}, inf);

        final AlarmManager alm = context.getSystemService(AlarmManager.class);
        for (int i = 0; i < NUM_ALARMS; i++) {
            alm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + (i + 1) * 1000,
                    PendingIntent.getBroadcast(context, i, intent, 0));
        }
        assertTrue("Didn't receive all broadcasts.", latch.await(60 * 1000, TimeUnit.SECONDS));
    }
}
