/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.providers.calendar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@LargeTest
public class CalendarProviderBroadcastReceiverTest extends AndroidTestCase {
    /**
     * Actually send the broadcast and make sure the provider won't crash.
     */
    public void testBroadcastToRealProvider() throws Exception {
        final Intent intent = CalendarAlarmManager.getCheckNextAlarmIntent(getContext(),
                /* removeAlarms=*/ false);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        final AtomicInteger resultCodeReceiver = new AtomicInteger(Integer.MIN_VALUE);
        final CountDownLatch latch = new CountDownLatch(1);

        getContext().sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                resultCodeReceiver.set(getResultCode());

                latch.countDown();
            }
        }, null, 0, null, null);

        assertTrue("Didn't receive the result.", latch.await(1, TimeUnit.MINUTES));
        assertEquals(Activity.RESULT_OK, resultCodeReceiver.get());
    }
}
