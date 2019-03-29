/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.verifier.vr;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class MockVrActivity extends Activity {
    private static final String TAG = "MockVrActivity";
    static final int EVENT_DELAY_MS = 1000;
    private boolean mDoSecondIntent;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        try {
            setVrModeEnabled(true, new ComponentName(this, MockVrListenerService.class));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not set VR mode: " + e);
        }
        mDoSecondIntent = getIntent().getBooleanExtra(
                VrListenerVerifierActivity.EXTRA_LAUNCH_SECOND_INTENT, false);
        mHandler = new Handler();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume called.");

        super.onResume();
        if (mDoSecondIntent) {
            mDoSecondIntent = false;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    MockVrActivity.this.startActivity(new Intent(MockVrActivity.this,
                            MockVrActivity2.class));
                }
            }, EVENT_DELAY_MS);
        } else {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    MockVrActivity.this.finish();
                }
            }, EVENT_DELAY_MS);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        Log.i(TAG, "onWindowFocusChanged called with " + hasFocus);
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause called.");
        super.onPause();
    }
}
