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

package android.assist.testapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class LifecycleActivity extends Activity {
    private static final String TAG = "LifecycleActivity";

    private BroadcastReceiver mReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "LifecycleActivity created");
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("android.intent.action.hide_lifecycle_activity")) {
                    finish();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.hide_lifecycle_activity");
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Activity has resumed");
        sendBroadcast(new Intent("android.intent.action.lifecycle_hasResumed"));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.i(TAG, "Activity focus changed: " + hasFocus);
        if (hasFocus) {
            sendBroadcast(new Intent("android.intent.action.lifecycle_hasFocus"));
        } else {
            sendBroadcast(new Intent("android.intent.action.lifecycle_lostFocus"));
        }
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "activity was paused");
        sendBroadcast(new Intent("android.intent.action.lifecycle_onpause"));
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "activity was stopped");
        sendBroadcast(new Intent("android.intent.action.lifecycle_onstop"));
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "activity was destroyed");
        sendBroadcast(new Intent("android.intent.action.lifecycle_ondestroy"));
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}
