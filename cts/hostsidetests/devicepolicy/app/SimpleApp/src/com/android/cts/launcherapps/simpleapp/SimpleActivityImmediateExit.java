/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.util.Log;

/**
 * A simple activity which quits itself immediately after starting.
 */
public class SimpleActivityImmediateExit extends Activity {
    private final static String ACTIVITY_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.EXIT_ACTION";
    private static final String TAG = "SimpleActivityImmediateExit";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(TAG, "Created SimpleActivityImmediateExit.");
    }

    @Override
    public void onStart() {
        super.onStart();
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Notify any listener that this activity is about to end now.
        Intent reply = new Intent();
        reply.setAction(ACTIVITY_EXIT_ACTION);
        sendBroadcast(reply);
    }
}
