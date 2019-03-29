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

import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.KEY_ACTION;
import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.KEY_REQUEST_CODE;
import static com.android.server.cts.device.batterystats.BatteryStatsBgVsFgActions.doAction;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

/** An service (to be run as a background process) which performs one of a number of actions. */
public class BatteryStatsBackgroundService extends IntentService {
    private static final String TAG = BatteryStatsBackgroundService.class.getSimpleName();

    public BatteryStatsBackgroundService() {
        super(BatteryStatsBackgroundService.class.getName());
    }

    @Override
    public void onHandleIntent(Intent intent) {
        String action = intent.getStringExtra(KEY_ACTION);
        String requestCode = intent.getStringExtra(KEY_REQUEST_CODE);
        Log.i(TAG, "Starting " + action + " from background service as request " + requestCode);

        // Check that app is in background; crash if it isn't.
        BatteryStatsBgVsFgActions.checkAppState(this, true, action, requestCode);

        doAction(this, action, requestCode);
    }
}
