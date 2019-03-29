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

package com.android.cts.launcherapps.simpleapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Test being able to start a service (with no background check restrictions) as soon as
 * an activity is created.
 */
public class SimpleActivityStartService extends Activity {
    private static final String TAG = "SimpleActivityStartService";

    public static String ACTION_SIMPLE_ACTIVITY_START_SERVICE_RESULT =
            "com.android.cts.launcherapps.simpleapp.SimpleActivityStartService.RESULT";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        attemptStartService();
        finish();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    void attemptStartService() {
        Intent reply = new Intent(ACTION_SIMPLE_ACTIVITY_START_SERVICE_RESULT);
        reply.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        Intent serviceIntent = getIntent().getParcelableExtra("service");
        try {
            startService(serviceIntent);
        } catch (IllegalStateException e) {
            reply.putExtra("result", Activity.RESULT_CANCELED);
            sendBroadcast(reply);
            return;
        }
        reply.putExtra("result", Activity.RESULT_FIRST_USER);
        sendBroadcast(reply);
    }
}
