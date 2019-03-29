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
 * This activity starts another activity. Once the other activity gets terminated, this one will
 * terminate as well.
 */
public class SimpleActivityChainExit extends Activity {
    private static final String TAG = "SimpleActivityChainExit";
    // This action.
    private final static String ACTIVITY_CHAIN_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.CHAIN_EXIT_ACTION";
    // The action which will be called from here and then immediately exit again.
    private static final String SIMPLE_ACTIVITY_IMMEDIATE_EXIT = ".SimpleActivityImmediateExit";
    // Our package name.
    private static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    // Set to true once the activity was paused. Upon next resume the activity gets finished.
    private boolean mPaused = false;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.i(TAG, "Created SimpleActivityChainExit.");
    }

    @Override
    public void onStart() {
        super.onStart();
        // Start our second activity which will quit itself immediately giving back control.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_IMMEDIATE_EXIT);
        startActivityForResult(intent, 0);
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // We ignore any resumes coming in before we got at least paused once.
        if (mPaused) {
            // Since we were paused once we can finish ourselves now.
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Notify a listener that this activity will end now.
        Intent reply = new Intent();
        reply.setAction(ACTIVITY_CHAIN_EXIT_ACTION);
        sendBroadcast(reply);
    }
}
