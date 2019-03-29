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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Used by BatteryStatsValidationTest.
 */
@RunWith(AndroidJUnit4.class)
public class BatteryStatsProcessStateTests extends BatteryStatsDeviceTestBase {
    private static final String TAG = "BatteryStatsProcessStateTests";

    @Test
    public void testForegroundService() throws Exception {
        Intent intent = new Intent();
        intent.setClass(mContext, SimpleForegroundService.class);
        Log.i(TAG, "Starting foreground service.");
        mContext.startForegroundService(intent);
        Thread.sleep(3000);
    }

    @Test
    public void testActivity() throws Exception {
        Intent intent = new Intent();
        intent.setClass(mContext, SimpleActivity.class);
        mContext.startActivity(intent);
    }
}
